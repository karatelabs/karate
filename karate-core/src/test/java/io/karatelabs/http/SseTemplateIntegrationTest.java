package io.karatelabs.http;

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;
import io.karatelabs.markup.HxDialect;
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.MarkupConfig;
import io.karatelabs.markup.ResourceResolver;
import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SSE with template-rendered HTML fragments.
 * Tests the P11 pattern: server renders HTML fragment, sends via SSE,
 * HTMX sse-swap consumes it on the client.
 * Also tests the P12 pattern: server sends JSON data via SSE for Alpine.js.
 */
class SseTemplateIntegrationTest {

    /**
     * P11: Server renders HTML fragments and sends them as SSE event data.
     * HTMX sse-swap replaces DOM elements with the received HTML.
     */
    @Test
    void testSseWithRenderedHtmlFragment() throws Exception {
        // Render HTML fragments using processString as entry point with th:insert to a file fragment.
        // This is the production pattern: inline template references classpath components.
        MarkupConfig markupConfig = new MarkupConfig();
        markupConfig.setResolver(new RootResourceResolver("classpath:markup"));
        markupConfig.setEngineSupplier(Engine::new);
        Markup markup = Markup.init(markupConfig, new HxDialect(markupConfig));

        String readyBadge = markup.processString(
                "<span th:insert=\"~{components/badge :: badge}\" th:with=\"state: 'ready'\"></span>", Map.of());
        String errorBadge = markup.processString(
                "<span th:insert=\"~{components/badge :: badge}\" th:with=\"state: 'error'\"></span>", Map.of());

        // SSE handler sends server-rendered HTML fragments (P11 pattern)
        SseHandler sseHandler = (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                try {
                    connection.send("state", readyBadge);
                    Thread.sleep(50);
                    connection.send("state", errorBadge);
                    connection.close();
                } catch (Exception e) {
                    connection.close();
                }
            });
        };

        // Template handler serves the SSE-consuming page
        ServerConfig config = new ServerConfig().csrfEnabled(false);
        ResourceResolver resolver = (path, caller) -> {
            if ("sse-demo.html".equals(path) || "sse-demo".equals(path)) {
                return Resource.text("""
                    <div hx-ext="sse" sse-connect="/sse/status">
                      <div id="badge" sse-swap="state">loading...</div>
                    </div>
                    """);
            }
            return null;
        };
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);

        HttpServer server = HttpServer.start(0, handler, sseHandler);
        try {
            int port = server.getPort();

            // Verify template page is served
            HttpURLConnection pageConn = (HttpURLConnection) URI.create("http://localhost:" + port + "/sse-demo").toURL().openConnection();
            assertEquals(200, pageConn.getResponseCode());
            String pageContent;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pageConn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                pageContent = sb.toString();
            }
            assertTrue(pageContent.contains("sse-connect"), "Page should have SSE markup");
            assertTrue(pageContent.contains("sse-swap=\"state\""), "Page should have sse-swap target");

            // Verify SSE stream delivers HTML fragments
            HttpURLConnection sseConn = (HttpURLConnection) URI.create("http://localhost:" + port + "/sse/status").toURL().openConnection();
            sseConn.setRequestProperty("Accept", "text/event-stream");
            sseConn.setConnectTimeout(5000);
            sseConn.setReadTimeout(5000);

            assertEquals(200, sseConn.getResponseCode());
            assertEquals("text/event-stream", sseConn.getContentType());

            List<String> eventNames = new ArrayList<>();
            List<String> eventData = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sseConn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event: ")) {
                        eventNames.add(line.substring(7));
                    } else if (line.startsWith("data: ")) {
                        eventData.add(line.substring(6));
                    }
                }
            }

            assertEquals(2, eventNames.size(), "Event names: " + eventNames + ", data: " + eventData);
            assertEquals("state", eventNames.get(0));
            assertEquals("state", eventNames.get(1));

            assertTrue(eventData.get(0).contains("bg-success"), "First badge should be success: " + eventData.get(0));
            assertTrue(eventData.get(0).contains(">ready<"), "First badge text: " + eventData.get(0));
            assertTrue(eventData.get(1).contains("bg-danger"), "Second badge should be danger: " + eventData.get(1));
            assertTrue(eventData.get(1).contains(">error<"), "Second badge text: " + eventData.get(1));

        } finally {
            server.stopAndWait();
        }
    }

    /**
     * P12: Server sends JSON data via SSE for client-side rendering with Alpine.js.
     */
    @Test
    void testSseWithJsonData() throws Exception {
        SseHandler sseHandler = (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                try {
                    // Send iteration progress as JSON (P12 pattern)
                    connection.send("iteration", "{\"index\":1,\"status\":\"pass\",\"duration\":42}");
                    connection.send("iteration", "{\"index\":2,\"status\":\"fail\",\"duration\":108}");
                    connection.send("iteration", "{\"index\":3,\"status\":\"pass\",\"duration\":25}");
                    connection.close();
                } catch (Exception e) {
                    connection.close();
                }
            });
        };

        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("fallback");
            return response;
        }, sseHandler);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + server.getPort() + "/sse/progress").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            List<String> eventNames = new ArrayList<>();
            List<String> eventData = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event: ")) eventNames.add(line.substring(7));
                    else if (line.startsWith("data: ")) eventData.add(line.substring(6));
                }
            }

            assertEquals(3, eventData.size());
            // All events named "iteration"
            assertTrue(eventNames.stream().allMatch("iteration"::equals));
            // Verify JSON is valid and contains expected fields
            assertTrue(eventData.get(0).contains("\"status\":\"pass\""));
            assertTrue(eventData.get(1).contains("\"status\":\"fail\""));
            assertTrue(eventData.get(2).contains("\"duration\":25"));
        } finally {
            server.stopAndWait();
        }
    }

    /**
     * Tests that named SSE events map correctly to HTMX sse-swap targets.
     * Multiple event types on the same connection — each maps to a different DOM target.
     */
    @Test
    void testSseMultipleNamedEvents() throws Exception {
        MarkupConfig mc = new MarkupConfig();
        mc.setResolver(new RootResourceResolver("classpath:markup"));
        mc.setEngineSupplier(Engine::new);
        Markup markup = Markup.init(mc, new HxDialect(mc));

        SseHandler sseHandler = (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                // Different event types for different DOM targets
                String badge = markup.processString(
                        "<span class=\"badge bg-success\">ready</span>", Map.of());
                connection.send("state", badge);

                connection.send("count", "<span>42 sessions</span>");

                String error = "<div class=\"alert alert-danger\">Connection lost</div>";
                connection.send("error", error);
                connection.close();
            });
        };

        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("ok");
            return response;
        }, sseHandler);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + server.getPort() + "/sse/dashboard").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            List<String> eventNames = new ArrayList<>();
            List<String> eventData = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event: ")) eventNames.add(line.substring(7));
                    else if (line.startsWith("data: ")) eventData.add(line.substring(6));
                }
            }

            assertEquals(3, eventNames.size());
            assertEquals("state", eventNames.get(0));
            assertEquals("count", eventNames.get(1));
            assertEquals("error", eventNames.get(2));

            // Each event carries appropriate HTML for its sse-swap target
            assertTrue(eventData.get(0).contains("bg-success"));
            assertTrue(eventData.get(1).contains("42 sessions"));
            assertTrue(eventData.get(2).contains("alert-danger"));
        } finally {
            server.stopAndWait();
        }
    }

    /**
     * Tests rendering a template fragment file (not inline string) as SSE data.
     * Uses the components/badge.html fragment from test resources.
     */
    @Test
    void testSseWithFragmentFile() throws Exception {
        MarkupConfig mc = new MarkupConfig();
        mc.setResolver(new RootResourceResolver("classpath:markup"));
        mc.setEngineSupplier(Engine::new);
        Markup markup = Markup.init(mc, new HxDialect(mc));

        SseHandler sseHandler = (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                // Render fragment from file — same as components/badge.html
                String html = markup.processString(
                        "<span th:insert=\"~{components/badge :: badge}\" th:with=\"state: 'ready'\"></span>",
                        Map.of());
                connection.send("badge", html);
                connection.close();
            });
        };

        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("ok");
            return response;
        }, sseHandler);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + server.getPort() + "/sse/badge").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            List<String> eventData = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) eventData.add(line.substring(6));
                }
            }

            assertEquals(1, eventData.size());
            assertTrue(eventData.get(0).contains("bg-success"), "Badge should be success: " + eventData.get(0));
            assertTrue(eventData.get(0).contains("ready"), "Badge text should be ready: " + eventData.get(0));
        } finally {
            server.stopAndWait();
        }
    }

}

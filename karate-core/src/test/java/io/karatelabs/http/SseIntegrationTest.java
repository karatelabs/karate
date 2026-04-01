package io.karatelabs.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Server-Sent Events support.
 */
class SseIntegrationTest {

    static HttpServer server;
    static int port;

    @BeforeAll
    static void beforeAll() {
        // Normal handler for non-SSE requests
        java.util.function.Function<HttpRequest, HttpResponse> handler = request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("normal response");
            return response;
        };
        // SSE handler sends 3 events then closes
        SseHandler sseHandler = (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                try {
                    connection.send("message", "event-1");
                    connection.send("message", "event-2");
                    connection.send("message", "event-3");
                    connection.close();
                } catch (Exception e) {
                    connection.close();
                }
            });
        };
        server = HttpServer.start(0, handler, sseHandler);
        port = server.getPort();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stopAndWait();
        }
    }

    @Test
    void testSseReceivesEvents() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/events").toURL().openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("text/event-stream", conn.getContentType());

        List<String> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    events.add(line.substring(6));
                }
            }
        }

        assertEquals(3, events.size());
        assertEquals("event-1", events.get(0));
        assertEquals("event-2", events.get(1));
        assertEquals("event-3", events.get(2));
    }

    @Test
    void testNonSseRequestStillWorks() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/test").toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("normal response", reader.readLine());
        }
    }

    @Test
    void testSseEventFormat() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/events").toURL().openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        // Each event should have "event: message" line followed by "data: ..." line
        assertTrue(lines.contains("event: message"));
        assertTrue(lines.contains("data: event-1"));
    }

    @Test
    void testSseMultilineData() throws Exception {
        HttpServer multilineServer = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("fallback");
            return response;
        }, (request, connection) -> {
            Thread.ofVirtual().start(() -> {
                connection.send("update", "line1\nline2\nline3");
                connection.close();
            });
        });

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + multilineServer.getPort() + "/events").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            List<String> dataLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        dataLines.add(line.substring(6));
                    }
                }
            }

            assertEquals(3, dataLines.size());
            assertEquals("line1", dataLines.get(0));
            assertEquals("line2", dataLines.get(1));
            assertEquals("line3", dataLines.get(2));
        } finally {
            multilineServer.stopAndWait();
        }
    }

    @Test
    void testSseClientDisconnect() throws Exception {
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        HttpServer disconnectServer = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("fallback");
            return response;
        }, (request, connection) -> {
            connection.onDisconnect(disconnectLatch::countDown);
            Thread.ofVirtual().start(() -> {
                // Keep sending until client disconnects
                while (connection.isOpen()) {
                    connection.sendComment("keepalive");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        });

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + disconnectServer.getPort() + "/events").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            // Read one comment then disconnect
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                assertNotNull(line);
                assertTrue(line.startsWith(": keepalive"));
            }
            conn.disconnect();

            // Verify the disconnect callback was called
            assertTrue(disconnectLatch.await(5, TimeUnit.SECONDS));
        } finally {
            disconnectServer.stopAndWait();
        }
    }

    @Test
    void testSseWithoutHandler() throws Exception {
        // Server with no SSE handler — SSE requests should get normal response
        HttpServer noSseServer = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            response.setBody("normal only");
            return response;
        });

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + noSseServer.getPort() + "/test").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            assertEquals(200, conn.getResponseCode());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                assertEquals("normal only", reader.readLine());
            }
        } finally {
            noSseServer.stopAndWait();
        }
    }

}

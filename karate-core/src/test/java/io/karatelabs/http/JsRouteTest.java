package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for jsRoute() — JS handlers routed from paths OUTSIDE the single
 * apiPrefix (e.g. a download endpoint inside a gated page prefix). The
 * original request path is preserved so the handler can use
 * request.pathMatches() to extract parameters.
 */
class JsRouteTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .csrfEnabled(false)
                .jsRoute("/team/export", "routed/export.js")
                .jsRoute("/files/{id}/data", "routed/export.js");

        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testJsRouteOutsideApiPrefix() {
        HttpResponse response = harness.get("/team/export");
        assertEquals(200, response.getStatus());
        assertEquals("export-ok", response.getBodyString());
        assertTrue(response.getHeader("Content-Type").startsWith("text/csv"));
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
    }

    @Test
    void testJsRoutePreservesPathForParams() {
        HttpResponse response = harness.get("/files/42/data");
        assertEquals(200, response.getStatus());
        assertEquals("export-ok:42", response.getBodyString());
    }

    @Test
    void testApiPrefixKeepsPriorityAndBehavior() {
        // the classic apiPrefix lane is untouched by jsRoute registration
        HttpResponse response = harness.get("/api/todos");
        assertEquals(200, response.getStatus());
    }

    @Test
    void testUnroutedPathFallsThroughToTemplates() {
        HttpResponse response = harness.get("/items");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Items List"));
    }

    @Test
    void testUnmatchedPathStill404s() {
        HttpResponse response = harness.get("/team/other");
        assertEquals(404, response.getStatus());
    }
}

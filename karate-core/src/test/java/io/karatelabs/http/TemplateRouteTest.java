package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for templateRoute() with path parameter extraction in templates.
 * Verifies that templates can use request.pathMatches() and request.pathParams
 * when served via route patterns.
 */
class TemplateRouteTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .csrfEnabled(false)
                .templateRoute("/items/{id}/edit", "detail.html")
                .templateRoute("/items/{id}", "detail.html");

        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testPathParamExtractedInTemplate() {
        HttpResponse response = harness.get("/items/42");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        // detail.html should extract itemId=42 via request.pathMatches('/items/{id}')
        assertTrue(body.contains("Item: 42"), "Template should render path param");
        assertTrue(body.contains("id=\"item-id\""));
    }

    @Test
    void testPathParamWithDifferentValues() {
        HttpResponse r1 = harness.get("/items/abc-123");
        assertTrue(r1.getBodyString().contains("Item: abc-123"));

        HttpResponse r2 = harness.get("/items/999");
        assertTrue(r2.getBodyString().contains("Item: 999"));
    }

    @Test
    void testMoreSpecificRouteMatchesFirst() {
        // /items/42/edit should match the more specific route first
        HttpResponse response = harness.get("/items/42/edit");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Item: 42"));
        assertTrue(body.contains("id=\"edit-mode\""), "Should be in edit mode");
        assertFalse(body.contains("id=\"view-mode\""));
    }

    @Test
    void testLessSpecificRouteIsViewMode() {
        HttpResponse response = harness.get("/items/42");
        String body = response.getBodyString();
        assertTrue(body.contains("id=\"view-mode\""), "Should be in view mode");
        assertFalse(body.contains("id=\"edit-mode\""));
    }

    @Test
    void testExistingTemplateNotOverridden() {
        // /items matches items.html (existing file) — should NOT use the route
        HttpResponse response = harness.get("/items");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Items List"),
                "Existing items.html should render, not detail.html via route");
    }

    @Test
    void testUnmatchedPathReturns404() {
        // /unknown doesn't match any file or route → 404
        HttpResponse response = harness.get("/unknown/page");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testDirectTemplateFileStillWorks() {
        // /form matches form.html directly — normal template rendering
        HttpResponse response = harness.get("/form");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("id=\"main-form\""));
    }

}

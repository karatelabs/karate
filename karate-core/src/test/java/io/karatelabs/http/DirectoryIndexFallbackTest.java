package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /foo with no foo.html falls back to foo/index.html — standard web-server convention.
 * Lets apps group related pages under a common prefix without registering a templateRoute.
 */
class DirectoryIndexFallbackTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .csrfEnabled(false);
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testRequestForDirectoryFallsBackToIndexHtml() {
        // demo/reports/index.html exists, demo/reports.html does not
        HttpResponse response = harness.get("/reports");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("id=\"reports-index\""),
                "should render reports/index.html when reports.html is absent");
    }

    @Test
    void testRegularTemplateStillPrecedesFallback() {
        // form.html exists at root — fallback never considered
        HttpResponse response = harness.get("/form");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("id=\"main-form\""));
    }
}

package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that custom 404 and 500 error templates render with access to
 * configured globalVariables (plus request/response/context/session),
 * not just a minimal {error} or {path} map.
 */
class ErrorTemplateTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .csrfEnabled(false)
                .errorTemplate404("errors/404.html")
                .errorTemplate500("errors/500.html")
                .globalVariables(Map.of("appName", "MyApp"));

        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void test500TemplateReceivesErrorAndGlobals() {
        // trigger-error.html deliberately throws — handleError kicks in
        HttpResponse response = harness.get("/trigger-error");
        assertEquals(500, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("id=\"error-500\""), "custom 500 template should render");
        assertTrue(body.contains("trigger-error"), "outer error message should reference the failing template");
        assertTrue(body.contains(">MyApp<"), "globalVariables should be available in 500 template");
        assertTrue(body.contains("/trigger-error"), "request should be available for path access");
    }

    @Test
    void test404TemplateReceivesPathAndGlobals() {
        HttpResponse response = harness.get("/this-does-not-exist");
        assertEquals(404, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("id=\"error-404\""), "custom 404 template should render");
        assertTrue(body.contains("/this-does-not-exist"), "missing path should be available as 'path'");
        assertTrue(body.contains(">MyApp<"), "globalVariables should be available in 404 template");
    }
}

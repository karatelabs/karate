package io.karatelabs.http;

import io.karatelabs.markup.Markup;
import io.karatelabs.markup.RootResourceResolver;
import io.karatelabs.test.LogSilencer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fallback 500 response body for template errors:
 * <ul>
 *   <li>{@code devMode=true}  — body carries the formatted template-error block
 *       (line/col + source context) so devs don't have to scroll the server log.</li>
 *   <li>{@code devMode=false} — body is opaque {@code "Internal Server Error"} —
 *       template paths and source lines must not leak.</li>
 * </ul>
 */
class TemplateErrorResponseTest {

    private static InMemoryTestHarness harness(boolean devMode) {
        ServerConfig config = new ServerConfig()
                .devMode(devMode)
                .csrfEnabled(false);
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        return new InMemoryTestHarness(new ServerRequestHandler(config, resolver));
    }

    @Test
    void testDevModeIncludesFormattedTemplateError() {
        HttpResponse response = LogSilencer.silenced(Markup.class, () -> harness(true).get("/trigger-error"));
        assertEquals(500, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Internal Server Error"),
                "preamble line must be present: " + body);
        assertTrue(body.contains("TEMPLATE ERROR"),
                "devMode body must include the Markup-formatted block: " + body);
        assertTrue(body.contains("trigger-error"),
                "formatted block must reference the failing template: " + body);
        assertTrue(body.contains("deliberate test failure"),
                "root-cause message must surface in the body: " + body);
    }

    @Test
    void testProdModeBodyIsOpaque() {
        HttpResponse response = LogSilencer.silenced(Markup.class, () -> harness(false).get("/trigger-error"));
        assertEquals(500, response.getStatus());
        String body = response.getBodyString();
        assertEquals("Internal Server Error", body.trim());
        assertFalse(body.contains("trigger-error"),
                "prod body must not leak template path: " + body);
        assertFalse(body.contains("TEMPLATE ERROR"),
                "prod body must not leak the formatted block: " + body);
        assertFalse(body.contains("deliberate test failure"),
                "prod body must not leak the root-cause message: " + body);
    }
}

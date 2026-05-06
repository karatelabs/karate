package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * K5 — {@code context.actions} POST-handler registry.
 *
 * <p>Each test posts to {@code /actions} (backed by {@code demo/actions.html})
 * which registers three handlers: {@code addUser} (flash success + session
 * record), {@code suspend} (uses {@code request.param} directly), and
 * {@code boom} (throws — must land in flash.error).
 *
 * <p>Coverage:
 * <ul>
 *   <li>POST with a registered action → handler fires; {@code form} arg is
 *       auto-decoded from {@code request.paramJson('form')}.</li>
 *   <li>POST with a non-form-shape action → handler reads
 *       {@code request.param} directly via closure.</li>
 *   <li>POST with no matching action → silent no-op (no error, no flash).</li>
 *   <li>GET → no dispatch.</li>
 *   <li>Handler throws → exception lands in {@code context.flash.error}.</li>
 * </ul>
 */
class RequestActionsTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .apiPrefix("/api/")
                .devMode(true)
                .csrfEnabled(false);
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testRegisteredActionFiresOnPost() {
        // Form-shaped POST: action=addUser, form='{"email":"a@b.com","role":"admin"}'
        HttpResponse r = postForm("/actions",
                "action=addUser&form=" + urlEncoded("{\"email\":\"a@b.com\",\"role\":\"admin\"}"));
        assertEquals(200, r.getStatus(), "page must render: " + r.getBodyString());
        String body = r.getBodyString();
        // The flash text is built from form.email by the handler — proves
        // both that the handler fired AND that the form arg was auto-decoded.
        assertTrue(body.contains("Added a@b.com"),
                "addUser handler must fire and form arg must be auto-decoded: " + body);
    }

    @Test
    void testHandlerReadsRequestDirectly() {
        // suspend handler doesn't take 'form' — it reads request.param('userId')
        HttpResponse r = postForm("/actions", "action=suspend&userId=u-99");
        String body = r.getBodyString();
        // Flash text is hard-coded by the suspend handler — proves it fired
        // without needing a 'form' arg (handler reads request.param via closure).
        assertTrue(body.contains("Suspended."),
                "suspend handler must fire even without a 'form' param: " + body);
    }

    @Test
    void testUnknownActionIsNoOp() {
        HttpResponse r = postForm("/actions", "action=doesNotExist");
        String body = r.getBodyString();
        assertFalse(body.contains("flash-error"),
                "unknown action must not surface an error: " + body);
        assertFalse(body.contains("flash-success"),
                "unknown action must not surface a success: " + body);
    }

    @Test
    void testGetDoesNotDispatch() {
        HttpResponse r = harness.get("/actions?action=addUser");
        String body = r.getBodyString();
        assertFalse(body.contains("flash-success"),
                "GET must not dispatch even if action param is present: " + body);
    }

    @Test
    void testHandlerExceptionLandsInFlashError() {
        HttpResponse r = postForm("/actions", "action=boom");
        String body = r.getBodyString();
        assertTrue(body.contains("handler-failed"),
                "thrown handler must surface message in flash.error: " + body);
    }

    @Test
    void testBracketNotationStillWorksForNonIdentifierNames() {
        // The preferred style is dot notation (context.actions.foo = ...) but
        // bracket notation must still work for action names with hyphens,
        // numeric prefixes, or other non-identifier shapes.
        HttpResponse r = postForm("/actions", "action=hyphen-action");
        String body = r.getBodyString();
        assertTrue(body.contains("Hyphen."),
                "bracket-registered handler with hyphen name must dispatch: " + body);
    }

    private static String urlEncoded(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static HttpResponse postForm(String path, String formBody) {
        return harness.request()
                .path(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(formBody)
                .post();
    }
}

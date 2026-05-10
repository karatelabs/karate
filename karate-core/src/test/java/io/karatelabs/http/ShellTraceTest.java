package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies shell-trace and end-to-end fragment-trace plumbing through
 * the HTTP layer. The Markup-level trace tests in {@code MarkupTest} cover
 * {@code th:insert} / {@code th:replace} wrapping at the engine layer. This
 * test exercises the boundary that those can't reach: the
 * {@code ServerConfig.shellTemplate(...)} wrap performed in
 * {@link ServerRequestCycle}, which interpolates the page-content HTML into
 * the shell via {@code th:utext="content"} (not via {@code th:insert}).
 *
 * <p>Trace contract for the shell layer:
 * <ul>
 *   <li>devTrace OFF → zero markers in the response body.</li>
 *   <li>devTrace ON + devMode ON → content interpolated by the shell is
 *       wrapped with {@code <!-- ka:fragment-begin (shell) <template> ... -->}
 *       and matching end comments.</li>
 *   <li>devTrace ON but devMode OFF → no markers (production safety gate).</li>
 * </ul>
 */
class ShellTraceTest {

    static InMemoryTestHarness traceOnHarness;
    static InMemoryTestHarness traceOffHarness;
    static InMemoryTestHarness devModeOffHarness;

    @BeforeAll
    static void beforeAll() {
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");

        ServerConfig traceOn = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .devTrace(true)
                .csrfEnabled(false)
                .shellTemplate("layout.html");
        traceOnHarness = new InMemoryTestHarness(new ServerRequestHandler(traceOn, resolver));

        ServerConfig traceOff = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(true)
                .devTrace(false)
                .csrfEnabled(false)
                .shellTemplate("layout.html");
        traceOffHarness = new InMemoryTestHarness(new ServerRequestHandler(traceOff, resolver));

        ServerConfig devModeOff = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .devMode(false)
                .devTrace(true) // requested but should be ignored — gate is devMode
                .csrfEnabled(false)
                .shellTemplate("layout.html");
        devModeOffHarness = new InMemoryTestHarness(new ServerRequestHandler(devModeOff, resolver));
    }

    @Test
    void testShellTraceWrapsPageContent() {
        HttpResponse response = traceOnHarness.get("/");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("ka:fragment-begin (shell) index.html"),
                "shell-trace begin must surface the page template name: " + body);
        assertTrue(body.contains("ka:fragment-end (shell) index.html"),
                "shell-trace end must pair with begin: " + body);
        // Comments must wrap the actual page content
        int begin = body.indexOf("ka:fragment-begin (shell) index.html");
        int contentSlot = body.indexOf("id=\"main-content\"");
        int end = body.indexOf("ka:fragment-end (shell) index.html");
        assertTrue(contentSlot >= 0 && contentSlot < begin,
                "comments must sit inside the shell's main-content slot");
        assertTrue(begin < end, "begin must precede end: begin=" + begin + " end=" + end);
    }

    @Test
    void testShellTraceOffEmitsNoComments() {
        HttpResponse response = traceOffHarness.get("/");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertFalse(body.contains("ka:fragment-begin (shell)"),
                "trace must be a no-op when devTrace is false: " + body);
    }

    @Test
    void testShellTraceRequiresDevMode() {
        // devTrace=true, devMode=false → no trace (production safety gate)
        HttpResponse response = devModeOffHarness.get("/");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertFalse(body.contains("ka:fragment-begin (shell)"),
                "trace must be a no-op when devMode is false even if devTrace is true: " + body);
    }

    @Test
    void testShellTraceSkippedForHtmxRequest() {
        // HX-Request is not shell-wrapped — ergo no shell-trace either. Verifies
        // the trace doesn't leak into HTMX fragment swaps.
        HttpResponse response = traceOnHarness.request()
                .path("/")
                .header("HX-Request", "true")
                .get();
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertFalse(body.contains("ka:fragment-begin (shell)"),
                "HTMX fragment responses must not carry a shell-trace: " + body);
    }
}

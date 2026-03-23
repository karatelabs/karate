package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive e2e test for the demo web app.
 * Tests all server-side capabilities as a regression guard.
 * Covers: request, response, session, context, templates, routing, HTMX.
 */
class DemoAppTest {

    static InMemoryTestHarness harness;
    static InMemorySessionStore sessionStore;
    static List<String> logMessages;

    @BeforeAll
    static void beforeAll() {
        sessionStore = new InMemorySessionStore();
        logMessages = new ArrayList<>();

        ServerConfig config = new ServerConfig()
                .sessionStore(sessionStore)
                .sessionExpirySeconds(600)
                .apiPrefix("/api/")
                .staticPrefix("/pub/")
                .devMode(true)
                .csrfEnabled(false)  // Disable for demo tests (CSRF tested separately)
                .logHandler(message -> logMessages.add(message));

        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @BeforeEach
    void setUp() {
        sessionStore.clear();
        logMessages.clear();
    }

    private HttpResponse get(String path) {
        return harness.get(path);
    }

    private HttpResponse getWithCookie(String path, String cookie) {
        return harness.request().path(path).header("Cookie", cookie).get();
    }

    private HttpResponse getAjax(String path) {
        return harness.request().path(path).header("HX-Request", "true").get();
    }

    private HttpResponse getAjaxWithCookie(String path, String cookie) {
        return harness.request()
                .path(path)
                .header("HX-Request", "true")
                .header("Cookie", cookie)
                .get();
    }

    private HttpResponse post(String path, String body) {
        return harness.request()
                .path(path)
                .body(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post();
    }

    private HttpResponse postWithCookie(String path, String body, String cookie) {
        return harness.request()
                .path(path)
                .body(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .post();
    }

    private HttpResponse postJson(String path, String json) {
        return harness.request()
                .path(path)
                .body(json)
                .header("Content-Type", "application/json")
                .post();
    }

    private String extractSessionCookie(HttpResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        if (setCookie == null) return null;
        return setCookie.split(";")[0]; // "karate.sid=xyz"
    }

    // =================================================================================================================
    // Template Routing Tests
    // =================================================================================================================

    @Test
    void testRootTemplate() {
        HttpResponse response = get("/");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Demo App"));
        assertTrue(response.getHeader("Content-Type").contains("text/html"));
    }

    @Test
    void testNamedTemplate() {
        HttpResponse response = get("/items");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Items List"));
    }

    @Test
    void testTemplateNotFound() {
        HttpResponse response = get("/nonexistent");
        assertEquals(404, response.getStatus());
    }

    // =================================================================================================================
    // Request Object Tests
    // =================================================================================================================

    @Test
    void testRequestGet() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        assertTrue(body.contains("id=\"method-get\""));
        assertFalse(body.contains("id=\"method-post\""));
    }

    @Test
    void testRequestPost() {
        HttpResponse response = post("/form", "name=test&email=test@test.com&age=25");
        String body = response.getBodyString();
        // POST should show form data
        assertTrue(body.contains("id=\"submitted-data\""));
        assertTrue(body.contains("test@test.com"));
    }

    @Test
    void testRequestAjax() {
        HttpResponse response = getAjax("/");
        String body = response.getBodyString();
        assertTrue(body.contains("id=\"ajax-request\""));
        assertFalse(body.contains("id=\"full-request\""));
    }

    @Test
    void testRequestNotAjax() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        assertFalse(body.contains("id=\"ajax-request\""));
        assertTrue(body.contains("id=\"full-request\""));
    }

    @Test
    void testRequestParam() {
        HttpResponse response = get("/api/items?id=1");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Apple"));
    }

    @Test
    void testRequestParamInt() {
        // paramInt is used in form.html for age
        HttpResponse response = post("/form", "name=John&email=john@test.com&age=30");
        String body = response.getBodyString();
        assertTrue(body.contains(">30<")); // Age displayed as number
    }

    // =================================================================================================================
    // Response Object Tests
    // =================================================================================================================

    @Test
    void testResponseBody() {
        HttpResponse response = get("/api/items");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Apple"));
        assertTrue(body.contains("Banana"));
    }

    @Test
    void testResponseStatus() {
        HttpResponse response = get("/api/items?id=999");
        assertEquals(404, response.getStatus());
        assertTrue(response.getBodyString().contains("not found"));
    }

    @Test
    void testResponseHeaders() {
        HttpResponse response = get("/");
        assertNotNull(response.getHeader("Content-Type"));
        assertTrue(response.getHeader("Content-Type").contains("text/html"));
    }

    // =================================================================================================================
    // Session Object Tests
    // =================================================================================================================

    @Test
    void testSessionInit() {
        HttpResponse response = get("/api/session?action=init");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("sessionId"));

        // Check cookie set
        String cookie = extractSessionCookie(response);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("karate.sid="));
    }

    @Test
    void testSessionPersistence() {
        // First request - init session
        HttpResponse r1 = get("/api/session?action=init");
        String cookie = extractSessionCookie(r1);

        // Second request - set value
        HttpResponse r2 = getWithCookie("/api/session?action=set&key=user&value=john", cookie);
        assertTrue(r2.getBodyString().contains("john"));

        // Third request - get value
        HttpResponse r3 = getWithCookie("/api/session?action=get&key=user", cookie);
        assertTrue(r3.getBodyString().contains("john"));
    }

    @Test
    void testSessionClose() {
        // Create session
        HttpResponse r1 = get("/api/session?action=init");
        String cookie = extractSessionCookie(r1);
        assertNotNull(cookie);

        // Close session
        HttpResponse r2 = getWithCookie("/api/session?action=close", cookie);
        assertTrue(r2.getBodyString().contains("closed"));
    }

    @Test
    void testSessionInTemplate() {
        // Visit index to create session with visitCount
        HttpResponse r1 = get("/");
        String cookie = extractSessionCookie(r1);
        assertTrue(r1.getBodyString().contains("Visit count:"));
        assertTrue(r1.getBodyString().contains(">1<")); // First visit

        // Second visit should increment
        HttpResponse r2 = getWithCookie("/", cookie);
        assertTrue(r2.getBodyString().contains(">2<")); // Second visit
    }

    // =================================================================================================================
    // Context Object Tests
    // =================================================================================================================

    @Test
    void testContextLog() {
        HttpResponse response = get("/api/session?action=log&message=hello");
        assertTrue(response.getBodyString().contains("logged"));
        assertTrue(logMessages.stream().anyMatch(m -> m.contains("hello")));
    }

    @Test
    void testContextRedirect() {
        HttpResponse response = get("/api/session?action=redirect&target=/items");
        assertEquals(302, response.getStatus());
        assertEquals("/items", response.getHeader("Location"));
    }

    @Test
    void testContextUuid() {
        HttpResponse response = get("/api/session?action=uuid");
        String body = response.getBodyString();
        assertTrue(body.contains("uuid"));
        // UUID format check: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(body.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"));
    }

    @Test
    void testContextUuidInTemplate() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // UUID should be rendered in the template
        assertTrue(body.contains("id=\"uuid\""));
        // Check it contains a UUID-like string
        assertTrue(body.matches("(?s).*id=\"uuid\"[^>]*>[0-9a-f-]{36}<.*"));
    }

    @Test
    void testContextToJson() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // The items.html template uses context.toJson
        assertTrue(body.contains("id=\"json-output\""));
        assertTrue(body.contains("Apple")); // JSON should contain item names
    }

    @Test
    void testContextRead() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // The items.html template uses context.read('header.html')
        assertTrue(body.contains("id=\"read-check\""));
        assertTrue(body.contains("Read succeeded"));
    }

    @Test
    void testContextTemplate() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // context.template should be displayed
        assertTrue(body.contains("index.html") || body.contains("Template:"));
    }

    @Test
    void testContextSessionId() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // Session ID should be displayed
        assertTrue(body.contains("Session ID:"));
    }

    // =================================================================================================================
    // Template Attribute Tests
    // =================================================================================================================

    @Test
    void testThText() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // th:text should render text
        assertTrue(body.contains("Demo App"));
    }

    @Test
    void testThIf() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // th:if="${request.get}" should render for GET
        assertTrue(body.contains("id=\"method-get\""));
    }

    @Test
    void testThUnless() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // th:unless="${request.ajax}" should render for non-AJAX
        assertTrue(body.contains("id=\"full-request\""));
    }

    @Test
    void testThEach() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // th:each should iterate over items
        assertTrue(body.contains("Apple"));
        assertTrue(body.contains("Banana"));
        assertTrue(body.contains("Cherry"));
    }

    @Test
    void testThEachWithIndex() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // th:each with index should render iteration info
        assertTrue(body.contains("(first)"));
        assertTrue(body.contains("(last)"));
        assertTrue(body.contains("id=\"indexed-list\""));
    }

    @Test
    void testThReplace() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // th:replace should include header fragment
        assertTrue(body.contains("id=\"main-nav\""));
    }

    @Test
    void testThAction() {
        HttpResponse response = get("/form");
        String body = response.getBodyString();
        // th:action should set form action
        assertTrue(body.contains("id=\"main-form\""));
    }

    // =================================================================================================================
    // HTMX Attribute Tests
    // =================================================================================================================

    @Test
    void testKaGet() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:get should convert to hx-get
        assertTrue(body.contains("hx-get=\"/api/items\""));
    }

    @Test
    void testKaDelete() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:delete should convert to hx-delete
        assertTrue(body.contains("hx-delete=\"/api/items\""));
    }

    @Test
    void testKaVals() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:vals should convert to hx-vals with JSON
        assertTrue(body.contains("hx-vals='"));
    }

    @Test
    void testKaTarget() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:target should convert to hx-target
        assertTrue(body.contains("hx-target=\""));
    }

    @Test
    void testKaSwap() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:swap should convert to hx-swap
        assertTrue(body.contains("hx-swap=\"outerHTML\""));
    }

    @Test
    void testKaConfirm() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // ka:confirm should convert to hx-confirm
        assertTrue(body.contains("hx-confirm=\""));
    }

    @Test
    void testKaPost() {
        HttpResponse response = get("/form");
        String body = response.getBodyString();
        // ka:post="this" should convert to hx-post with template path
        assertTrue(body.contains("hx-post=\"/form\""));
    }

    @Test
    void testKaIndicator() {
        HttpResponse response = get("/form");
        String body = response.getBodyString();
        // ka:indicator should convert to hx-indicator
        assertTrue(body.contains("hx-indicator=\"#spinner\""));
    }

    // =================================================================================================================
    // API Routing Tests
    // =================================================================================================================

    @Test
    void testApiGetAll() {
        HttpResponse response = get("/api/items");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Apple"));
        assertTrue(body.contains("Banana"));
        assertTrue(body.contains("Cherry"));
    }

    @Test
    void testApiGetOne() {
        HttpResponse response = get("/api/items?id=2");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Banana"));
    }

    @Test
    void testApiNotFound() {
        HttpResponse response = get("/api/nonexistent");
        assertEquals(404, response.getStatus());
    }

    // =================================================================================================================
    // Static File Tests
    // =================================================================================================================

    @Test
    void testStaticJs() {
        HttpResponse response = get("/pub/app.js");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Demo app loaded"));
        assertTrue(response.getHeader("Content-Type").contains("javascript"));
    }

    @Test
    void testStaticNotFound() {
        HttpResponse response = get("/pub/nonexistent.js");
        assertEquals(404, response.getStatus());
    }

    // =================================================================================================================
    // AJAX/Partial Rendering Tests
    // =================================================================================================================

    @Test
    void testFullPageRender() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // Full page should include header and h1
        assertTrue(body.contains("Items List"));
        assertTrue(body.contains("id=\"main-nav\""));
    }

    @Test
    void testPartialRender() {
        // First init session
        HttpResponse r1 = get("/");
        String cookie = extractSessionCookie(r1);

        // AJAX request should return partial content
        HttpResponse response = getAjaxWithCookie("/items", cookie);
        String body = response.getBodyString();
        // Should NOT include h1 (skipped for AJAX)
        assertFalse(body.contains("<h1>Items List</h1>"));
        // Should still include items
        assertTrue(body.contains("Apple"));
    }

    // =================================================================================================================
    // Form Submission Tests
    // =================================================================================================================

    @Test
    void testFormPostAndDisplay() {
        HttpResponse response = post("/form", "name=John+Doe&email=john@example.com&age=42");
        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("John Doe"));
        assertTrue(body.contains("john@example.com"));
        assertTrue(body.contains("42"));
        assertTrue(body.contains("Form submitted successfully"));
    }

    @Test
    void testFormPostWithRedirect() {
        HttpResponse response = post("/form", "name=Test&email=test@test.com&age=25&redirect=true");
        assertEquals(302, response.getStatus());
        assertEquals("/", response.getHeader("Location"));
    }

    @Test
    void testFormDataInSession() {
        // First submit form
        HttpResponse r1 = post("/form", "name=SessionTest&email=sess@test.com&age=35");
        String cookie = extractSessionCookie(r1);
        assertNotNull(cookie);

        // Second request should show session data
        HttpResponse r2 = getWithCookie("/form", cookie);
        String body = r2.getBodyString();
        assertTrue(body.contains("id=\"session-data\""));
        assertTrue(body.contains("SessionTest"));
    }

    // =================================================================================================================
    // Script Scope Tests
    // =================================================================================================================

    @Test
    void testKaScopeGlobal() {
        HttpResponse response = get("/");
        String body = response.getBodyString();
        // ka:scope="global" should execute and set _.appName
        assertTrue(body.contains("Demo App"));
    }

    @Test
    void testUnderscoreVariable() {
        HttpResponse response = get("/items");
        String body = response.getBodyString();
        // _.items should be accessible in th:each
        assertTrue(body.contains("Apple"));
        assertTrue(body.contains("Banana"));
    }

    @Test
    void testSessionSyncAfterContextInit() {
        // Test that 'session' variable is available after context.init()
        // The index.html template calls context.init() and then uses session directly
        HttpResponse response = get("/");
        String cookie = extractSessionCookie(response);
        // Session should have been created and cookie set
        assertNotNull(cookie, "Session cookie should be set after context.init()");
        // The template should be able to use 'session' directly after init
        // (visit count is stored in session.visitCount)
        assertTrue(response.getBodyString().contains(">1<"), "Visit count should be 1");
    }

}

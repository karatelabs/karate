package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for requestFilter and Session.TEMPORARY support in ServerRequestHandler.
 * Validates v1 parity: contextFactory + requestValidator patterns are achievable via requestFilter.
 */
class RequestFilterTest {

    static InMemorySessionStore sessionStore;

    InMemoryTestHarness harness;

    @BeforeEach
    void setUp() {
        sessionStore = new InMemorySessionStore();
    }

    private InMemoryTestHarness createHarness(ServerConfig config) {
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        return new InMemoryTestHarness(handler);
    }

    private ServerConfig baseConfig() {
        return new ServerConfig()
                .sessionStore(sessionStore)
                .sessionExpirySeconds(600)
                .devMode(true)
                .csrfEnabled(false);
    }

    private String extractSessionCookie(HttpResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        if (setCookie == null) return null;
        return setCookie.split(";")[0];
    }

    // ========== requestFilter: Blocking Requests ==========

    @Test
    void testFilterBlocksUnauthorized() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/api/") && context.getSession() == null) {
                        HttpResponse resp = new HttpResponse();
                        resp.setStatus(401);
                        resp.setBody("Unauthorized");
                        return resp;
                    }
                    return null; // continue
                });
        harness = createHarness(config);

        HttpResponse response = harness.get("/api/items");
        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getBodyString());
    }

    @Test
    void testFilterAllowsAuthenticatedRequest() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/api/") && context.getSession() == null) {
                        HttpResponse resp = new HttpResponse();
                        resp.setStatus(401);
                        resp.setBody("Unauthorized");
                        return resp;
                    }
                    return null;
                });
        harness = createHarness(config);

        // First create a session via the template route
        HttpResponse r1 = harness.get("/");
        String cookie = extractSessionCookie(r1);
        assertNotNull(cookie);

        // API request with session cookie should pass the filter
        HttpResponse r2 = harness.request()
                .path("/api/items")
                .header("Cookie", cookie)
                .get();
        assertEquals(200, r2.getStatus());
        assertTrue(r2.getBodyString().contains("Apple"));
    }

    @Test
    void testFilterBlocksAdminRoute() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/admin")) {
                        Session session = context.getSession();
                        if (session == null) {
                            HttpResponse resp = new HttpResponse();
                            resp.setStatus(403);
                            resp.setBody("Forbidden");
                            return resp;
                        }
                        Object user = session.getMember("user");
                        if (user == null) {
                            HttpResponse resp = new HttpResponse();
                            resp.setStatus(403);
                            resp.setBody("Forbidden: not logged in");
                            return resp;
                        }
                    }
                    return null;
                });
        harness = createHarness(config);

        // No session — should be 403
        HttpResponse response = harness.get("/admin");
        assertEquals(403, response.getStatus());
    }

    @Test
    void testFilterDoesNotAffectPublicPages() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/admin")) {
                        HttpResponse resp = new HttpResponse();
                        resp.setStatus(403);
                        resp.setBody("Forbidden");
                        return resp;
                    }
                    return null;
                });
        harness = createHarness(config);

        // Non-admin page should work fine
        HttpResponse response = harness.get("/");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Demo App"));
    }

    // ========== requestFilter: Session.TEMPORARY ==========

    @Test
    void testFilterSetsTemporarySession() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/api/webhook")) {
                        context.setSession(Session.TEMPORARY);
                    }
                    return null;
                });
        harness = createHarness(config);

        // Webhook request should not get a session cookie
        HttpResponse response = harness.get("/api/webhook");
        assertNull(extractSessionCookie(response), "Temporary session should not set a cookie");
    }

    @Test
    void testTemporarySessionNotPersisted() {
        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/api/webhook")) {
                        context.setSession(Session.TEMPORARY);
                    }
                    return null;
                });
        harness = createHarness(config);

        int storeSizeBefore = sessionStore.size();
        harness.get("/api/webhook");
        assertEquals(storeSizeBefore, sessionStore.size(),
                "Temporary session should not be saved to store");
    }

    // ========== CSRF: Temporary Session Bypass ==========

    @Test
    void testCsrfSkippedForTemporarySession() {
        ServerConfig config = new ServerConfig()
                .sessionStore(sessionStore)
                .devMode(true)
                .csrfEnabled(true)  // CSRF enabled
                .requestFilter((request, context) -> {
                    if (request.getPath().startsWith("/api/webhook")) {
                        context.setSession(Session.TEMPORARY);
                    }
                    return null;
                });
        harness = createHarness(config);

        // POST to webhook with temporary session — CSRF should be skipped
        HttpResponse response = harness.request()
                .path("/api/webhook")
                .body("{\"event\":\"test\"}")
                .header("Content-Type", "application/json")
                .post();
        // Should NOT get 403 CSRF error
        assertNotEquals(403, response.getStatus(),
                "CSRF should be skipped for temporary sessions");
    }

    @Test
    void testCsrfEnforcedForNormalSession() {
        ServerConfig config = new ServerConfig()
                .sessionStore(sessionStore)
                .devMode(true)
                .csrfEnabled(true);
        harness = createHarness(config);

        // Create a session first
        HttpResponse r1 = harness.get("/");
        String cookie = extractSessionCookie(r1);
        assertNotNull(cookie);

        // POST with session but no CSRF token — should get 403
        HttpResponse r2 = harness.request()
                .path("/form")
                .body("name=test")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .post();
        assertEquals(403, r2.getStatus(), "CSRF should be enforced for normal sessions");
    }

    // ========== requestFilter: Receives Loaded Session ==========

    @Test
    void testFilterReceivesLoadedSession() {
        final boolean[] sessionSeen = {false};

        ServerConfig config = baseConfig()
                .requestFilter((request, context) -> {
                    if (context.getSession() != null) {
                        Object user = context.getSession().getMember("user");
                        if ("admin".equals(user)) {
                            sessionSeen[0] = true;
                        }
                    }
                    return null;
                });
        harness = createHarness(config);

        // Create session and set user
        HttpResponse r1 = harness.get("/api/session?action=init");
        String cookie = extractSessionCookie(r1);
        harness.request()
                .path("/api/session?action=set&key=user&value=admin")
                .header("Cookie", cookie)
                .get();

        // Third request — filter should see the loaded session with user=admin
        harness.request()
                .path("/any-page")
                .header("Cookie", cookie)
                .get();

        assertTrue(sessionSeen[0], "Filter should see the session loaded from cookie");
    }

    // ========== Session.TEMPORARY Unit Tests ==========

    @Test
    void testSessionTemporaryIsTemporary() {
        assertTrue(Session.TEMPORARY.isTemporary());
        assertNull(Session.TEMPORARY.getId());
    }

    @Test
    void testSessionInMemoryIsNotTemporary() {
        Session session = Session.inMemory();
        assertFalse(session.isTemporary());
        assertNotNull(session.getId());
    }

    // ========== Template Route Tests ==========

    @Test
    void testUnknownPathReturns404() {
        ServerConfig config = baseConfig();
        harness = createHarness(config);

        HttpResponse response = harness.get("/sessions/abc123");
        assertEquals(404, response.getStatus());
    }

    @Test
    void testTemplateRouteMatchesPattern() {
        ServerConfig config = baseConfig()
                .templateRoute("/sessions/{id}", "index.html");
        harness = createHarness(config);

        HttpResponse response = harness.get("/sessions/abc123");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Demo App"));
    }

    @Test
    void testMultipleTemplateRoutes() {
        ServerConfig config = baseConfig()
                .templateRoute("/sessions/{id}/report", "items.html")
                .templateRoute("/sessions/{id}", "index.html");
        harness = createHarness(config);

        // More specific route matches first
        HttpResponse r1 = harness.get("/sessions/abc/report");
        assertEquals(200, r1.getStatus());
        assertTrue(r1.getBodyString().contains("Items List"));

        // Less specific route
        HttpResponse r2 = harness.get("/sessions/abc");
        assertEquals(200, r2.getStatus());
        assertTrue(r2.getBodyString().contains("Demo App"));
    }

    @Test
    void testRouteDoesNotOverrideExistingTemplate() {
        ServerConfig config = baseConfig()
                .templateRoute("/items/{id}", "index.html");
        harness = createHarness(config);

        // /items exists as items.html — file takes priority over route
        // (route pattern /items/{id} won't match /items since segment counts differ)
        HttpResponse response = harness.get("/items");
        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Items List"));
    }

    @Test
    void testNoRouteMatchReturns404() {
        ServerConfig config = baseConfig()
                .templateRoute("/sessions/{id}", "index.html");
        harness = createHarness(config);

        // /unknown/path doesn't match any route or file → 404
        HttpResponse response = harness.get("/unknown/path");
        assertEquals(404, response.getStatus());
    }

}

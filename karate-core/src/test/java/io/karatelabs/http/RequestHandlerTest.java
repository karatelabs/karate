package io.karatelabs.http;

import io.karatelabs.common.Resource;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.SimpleObject;
import io.karatelabs.markup.ResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RequestHandler using InMemoryTestHarness.
 * No real HTTP server needed - tests handler logic directly.
 */
class RequestHandlerTest {

    static InMemoryTestHarness harness;
    static InMemorySessionStore sessionStore;
    static Map<String, String> resources;

    @BeforeAll
    static void beforeAll() {
        sessionStore = new InMemorySessionStore();
        resources = new HashMap<>();

        // Set up test resources
        resources.put("index.html", "<html><body>Welcome</body></html>");
        resources.put("signin.html", "<html><body>Sign In Page</body></html>");
        resources.put("api/hello.js", "response.body = { message: 'hello ' + request.param('name') }");
        resources.put("api/session.js", "context.init(); response.body = { sessionId: context.sessionId }");
        resources.put("api/check-session.js", "response.body = { hasSession: !!session, sessionId: context.sessionId }");
        resources.put("api/redirect.js", "context.redirect('/other')");
        resources.put("pub/app.js", "console.log('app');");
        resources.put("pub/style.css", "body { color: red; }");

        ResourceResolver resolver = (path, caller) -> {
            String content = resources.get(path);
            if (content == null) {
                return null; // Let RequestHandler handle 404
            }
            return Resource.text(content);
        };

        ServerConfig config = new ServerConfig()
                .sessionStore(sessionStore)
                .sessionExpirySeconds(600)
                .apiPrefix("/api/")
                .staticPrefix("/pub/");

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @BeforeEach
    void setUp() {
        sessionStore.clear();
    }

    private HttpResponse get(String path) {
        return harness.get(path);
    }

    private HttpResponse getWithCookie(String path, String cookie) {
        return harness.request()
                .path(path)
                .header("Cookie", cookie)
                .get();
    }

    // Template routing tests

    @Test
    void testRootTemplate() {
        HttpResponse response = get("/");

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Welcome"));
        assertTrue(response.getHeader("Content-Type").contains("text/html"));
    }

    @Test
    void testNamedTemplate() {
        HttpResponse response = get("/signin");

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("Sign In"));
    }

    @Test
    void testTemplateNotFound() {
        HttpResponse response = get("/nonexistent");

        assertEquals(404, response.getStatus());
        assertTrue(response.getBodyString().contains("Not Found"));
    }

    // API routing tests

    @Test
    void testApiRoute() {
        HttpResponse response = get("/api/hello?name=world");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("hello"));
        assertTrue(body.contains("world"));
    }

    @Test
    void testApiSession() {
        HttpResponse response = get("/api/session");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("sessionId"));

        // Check session cookie was set
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("karate.sid="));

        // Session should be created in store
        assertEquals(1, sessionStore.size());
    }

    @Test
    void testApiRedirect() {
        // In-memory harness doesn't follow redirects, so we see the 302 directly
        HttpResponse response = get("/api/redirect");

        assertEquals(302, response.getStatus());
        assertEquals("/other", response.getHeader("Location"));
    }

    @Test
    void testApiNotFound() {
        HttpResponse response = get("/api/nonexistent");

        assertEquals(404, response.getStatus());
    }

    // Static file routing tests

    @Test
    void testStaticJs() {
        HttpResponse response = get("/pub/app.js");

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("console.log"));
        String contentType = response.getHeader("Content-Type");
        assertTrue(contentType.contains("javascript") || contentType.contains("application/javascript"));
    }

    @Test
    void testStaticCss() {
        HttpResponse response = get("/pub/style.css");

        assertEquals(200, response.getStatus());
        assertTrue(response.getBodyString().contains("color: red"));
        String contentType = response.getHeader("Content-Type");
        assertTrue(contentType.contains("css") || contentType.contains("text/css"));
    }

    @Test
    void testStaticNotFound() {
        HttpResponse response = get("/pub/nonexistent.js");

        assertEquals(404, response.getStatus());
    }

    // Session tests

    @Test
    void testSessionPersistence() {
        // First request creates session
        HttpResponse response1 = get("/api/session");
        String setCookie = response1.getHeader("Set-Cookie");
        assertNotNull(setCookie);

        // Extract session ID from cookie
        String sessionCookie = setCookie.split(";")[0]; // "karate.sid=xyz"

        // Second request with cookie should use same session (doesn't call init)
        HttpResponse response2 = getWithCookie("/api/check-session", sessionCookie);

        // Should not create new session (still 1 in store)
        assertEquals(1, sessionStore.size());
        assertTrue(response2.getBodyString().contains("hasSession"));
    }

    @Test
    void testSessionExpired() {
        // Create a session
        Session session = sessionStore.create(1); // 1 second expiry
        String sessionId = session.getId();

        // Wait for expiry
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {
        }

        // Request with expired session cookie should not find session
        String cookie = "karate.sid=" + sessionId;
        HttpResponse response = getWithCookie("/", cookie);

        // Should still get 200 (just no session)
        assertEquals(200, response.getStatus());
    }

    // Security headers tests

    @Test
    void testSecurityHeadersOnHtmlResponse() {
        HttpResponse response = get("/");

        assertEquals(200, response.getStatus());
        assertTrue(response.getHeader("Content-Type").contains("text/html"));

        // Security headers should be applied to HTML responses
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
    }

    @Test
    void testSecurityHeadersNotOnApiResponse() {
        HttpResponse response = get("/api/hello?name=test");

        assertEquals(200, response.getStatus());

        // Security headers should NOT be applied to non-HTML responses
        assertNull(response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("X-Frame-Options"));
    }

    @Test
    void testSecurityHeadersNotOnStaticJs() {
        HttpResponse response = get("/pub/app.js");

        assertEquals(200, response.getStatus());

        // Security headers should NOT be applied to static JS
        assertNull(response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("X-Frame-Options"));
    }

    // CSRF validation tests

    @Test
    void testCsrfValidationBlocksPostWithoutToken() {
        // Create a session first
        HttpResponse sessionResponse = get("/api/session");
        String setCookie = sessionResponse.getHeader("Set-Cookie");
        String sessionCookie = setCookie.split(";")[0];

        // POST without CSRF token should be blocked
        HttpResponse response = harness.request()
                .path("/api/hello")
                .header("Cookie", sessionCookie)
                .post();

        assertEquals(403, response.getStatus());
        assertTrue(response.getBodyString().contains("CSRF"));
    }

    @Test
    void testCsrfValidationAllowsPostWithValidToken() {
        // Create a session and get the CSRF token
        Session session = sessionStore.create(600);
        String csrfToken = CsrfProtection.getOrCreateToken(session);
        String sessionCookie = "karate.sid=" + session.getId();

        // POST with valid CSRF token should succeed
        HttpResponse response = harness.request()
                .path("/api/hello?name=test")
                .header("Cookie", sessionCookie)
                .header("X-CSRF-Token", csrfToken)
                .post();

        assertEquals(200, response.getStatus());
    }

    @Test
    void testCsrfValidationAllowsPostWithHtmxHeader() {
        // Create a session and get the CSRF token
        Session session = sessionStore.create(600);
        String csrfToken = CsrfProtection.getOrCreateToken(session);
        String sessionCookie = "karate.sid=" + session.getId();

        // POST with HX-CSRF-Token header should succeed
        HttpResponse response = harness.request()
                .path("/api/hello?name=test")
                .header("Cookie", sessionCookie)
                .header("HX-CSRF-Token", csrfToken)
                .post();

        assertEquals(200, response.getStatus());
    }

    @Test
    void testCsrfValidationAllowsGetRequests() {
        // GET requests should not require CSRF token
        HttpResponse response = get("/api/hello?name=world");

        assertEquals(200, response.getStatus());
    }

    @Test
    void testCsrfValidationAllowsPostWithoutSession() {
        // POST without session should be allowed (e.g., signin page)
        // This is important for signin/signup pages that need to work without a session
        HttpResponse response = harness.request()
                .path("/api/hello?name=world")
                .post();

        // Should succeed - no session means nothing to protect from CSRF
        assertEquals(200, response.getStatus());
    }

    @Test
    void testCsrfValidationBlocksInvalidToken() {
        // Create a session
        Session session = sessionStore.create(600);
        CsrfProtection.getOrCreateToken(session); // Generate the real token
        String sessionCookie = "karate.sid=" + session.getId();

        // POST with invalid CSRF token should be blocked
        HttpResponse response = harness.request()
                .path("/api/hello")
                .header("Cookie", sessionCookie)
                .header("X-CSRF-Token", "invalid-token")
                .post();

        assertEquals(403, response.getStatus());
    }

    // Path traversal protection tests

    @Test
    void testPathTraversalBlockedOnStatic() {
        HttpResponse response = get("/pub/../../../etc/passwd");

        assertEquals(403, response.getStatus());
        assertTrue(response.getBodyString().contains("Forbidden"));
    }

    @Test
    void testPathTraversalBlockedOnApi() {
        HttpResponse response = get("/api/../../../etc/passwd");

        assertEquals(403, response.getStatus());
        assertTrue(response.getBodyString().contains("Forbidden"));
    }

    @Test
    void testPathTraversalBlockedOnTemplate() {
        HttpResponse response = get("/../../../etc/passwd");

        assertEquals(403, response.getStatus());
        assertTrue(response.getBodyString().contains("Forbidden"));
    }

    @Test
    void testEncodedPathTraversalBlocked() {
        // %2e%2e = ".."
        HttpResponse response = get("/pub/%2e%2e/%2e%2e/etc/passwd");

        assertEquals(403, response.getStatus());
        assertTrue(response.getBodyString().contains("Forbidden"));
    }

    // ========== Flash Message Tests ==========

    @Test
    void testFlashMessagesSurviveRedirect() {
        // Set up resources
        Map<String, String> testResources = new HashMap<>();
        // First page sets flash and redirects
        testResources.put("set-flash.html", """
                <script ka:scope="global">
                  context.init();
                  context.flash.success = 'Item created successfully!';
                  context.flash.itemId = 42;
                  context.redirect('/show-flash');
                </script>
                """);
        // Second page displays flash
        testResources.put("show-flash.html", """
                <html>
                <body>
                <div th:if="context.flash.success" class="alert" th:text="context.flash.success">Message</div>
                <span th:if="context.flash.itemId" th:text="context.flash.itemId">ID</span>
                </body>
                </html>
                """);

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        InMemorySessionStore testSessionStore = new InMemorySessionStore();
        ServerConfig config = new ServerConfig()
                .sessionStore(testSessionStore);

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // First request - sets flash and redirects
        HttpResponse response1 = testHarness.get("/set-flash");
        assertEquals(302, response1.getStatus());
        assertEquals("/show-flash", response1.getHeader("Location"));

        // Get session cookie
        String setCookie = response1.getHeader("Set-Cookie");
        assertNotNull(setCookie, "Session cookie should be set");
        String sessionCookie = setCookie.split(";")[0];

        // Second request - should see flash message
        HttpResponse response2 = testHarness.request()
                .path("/show-flash")
                .header("Cookie", sessionCookie)
                .get();

        assertEquals(200, response2.getStatus());
        String body = response2.getBodyString();
        assertTrue(body.contains("Item created successfully!"), "Should contain flash success message");
        assertTrue(body.contains("42"), "Should contain flash itemId");
    }

    @Test
    void testFlashMessagesOnlyShowOnce() {
        // Set up resources
        Map<String, String> testResources = new HashMap<>();
        testResources.put("index.html", """
                <html>
                <body>
                <div th:if="context.flash.message" th:text="context.flash.message">Message</div>
                <p>Welcome</p>
                </body>
                </html>
                """);

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        InMemorySessionStore testSessionStore = new InMemorySessionStore();
        ServerConfig config = new ServerConfig()
                .sessionStore(testSessionStore);

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // Create session and store flash message directly
        Session session = testSessionStore.create(600);
        Map<String, Object> flashData = new HashMap<>();
        flashData.put("message", "One-time notification!");
        session.putMember(ServerMarkupContext.FLASH_SESSION_KEY, flashData);
        String sessionCookie = "karate.sid=" + session.getId();

        // First request - should see flash message
        HttpResponse response1 = testHarness.request()
                .path("/")
                .header("Cookie", sessionCookie)
                .get();

        assertTrue(response1.getBodyString().contains("One-time notification!"),
                "First request should show flash message");

        // Second request - flash should be gone
        HttpResponse response2 = testHarness.request()
                .path("/")
                .header("Cookie", sessionCookie)
                .get();

        assertFalse(response2.getBodyString().contains("One-time notification!"),
                "Second request should NOT show flash message");
        assertTrue(response2.getBodyString().contains("Welcome"),
                "Page content should still render");
    }

    // ========== Global Variables Tests ==========

    /**
     * Utility object implementing SimpleObject to expose methods as JsCallable.
     * This pattern allows Java methods to be called from JavaScript/templates.
     */
    static class TestUtils implements SimpleObject {

        @Override
        public Object jsGet(String name) {
            return switch (name) {
                case "uppercase" -> (JavaCallable) (ctx, args) -> {
                    if (args.length > 0 && args[0] != null) {
                        return args[0].toString().toUpperCase();
                    }
                    return "";
                };
                case "formatPrice" -> (JavaCallable) (ctx, args) -> {
                    if (args.length > 0 && args[0] instanceof Number n) {
                        return String.format("$%.2f", n.doubleValue());
                    }
                    return "$0.00";
                };
                case "greet" -> (JavaCallable) (ctx, args) -> {
                    String name1 = args.length > 0 && args[0] != null ? args[0].toString() : "World";
                    return "Hello, " + name1 + "!";
                };
                case "appName" -> "TestApp";
                default -> null;
            };
        }

        @Override
        public Collection<String> jsKeys() {
            return List.of("uppercase", "formatPrice", "greet", "appName");
        }
    }

    @Test
    void testGlobalVariablesInApiHandler() {
        // Create utils object
        TestUtils utils = new TestUtils();

        // Set up resources with API that uses utils
        Map<String, String> testResources = new HashMap<>();
        testResources.put("api/test.js", "response.body = { greeting: utils.greet('Karate'), price: utils.formatPrice(99.5) }");

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        // Configure server with globalVariables
        ServerConfig config = new ServerConfig()
                .apiPrefix("/api/")
                .globalVariables(Map.of("utils", utils));

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // Make request
        HttpResponse response = testHarness.get("/api/test");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Hello, Karate!"), "Should contain greeting from utils.greet()");
        assertTrue(body.contains("$99.50"), "Should contain formatted price from utils.formatPrice()");
    }

    @Test
    void testGlobalVariablesInTemplate() {
        // Create utils object
        TestUtils utils = new TestUtils();

        // Set up resources with template that uses utils
        Map<String, String> testResources = new HashMap<>();
        testResources.put("index.html", """
                <html>
                <body>
                <h1 th:text="utils.appName">App</h1>
                <p th:text="utils.greet('World')">Greeting</p>
                <span th:text="utils.formatPrice(42.99)">Price</span>
                </body>
                </html>
                """);

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        // Configure server with globalVariables
        ServerConfig config = new ServerConfig()
                .globalVariables(Map.of("utils", utils));

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // Make request
        HttpResponse response = testHarness.get("/");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("<h1>TestApp</h1>"), "Should contain app name from utils.appName");
        assertTrue(body.contains("Hello, World!"), "Should contain greeting from utils.greet()");
        assertTrue(body.contains("$42.99"), "Should contain formatted price from utils.formatPrice()");
    }

    @Test
    void testGlobalVariablesInServerScopeScript() {
        // Create utils object
        TestUtils utils = new TestUtils();

        // Set up resources with template that uses utils in ka:scope
        Map<String, String> testResources = new HashMap<>();
        testResources.put("index.html", """
                <html>
                <body>
                <script ka:scope="global">
                  _.message = utils.uppercase('hello world');
                  _.price = utils.formatPrice(19.99);
                </script>
                <p th:text="message">Message</p>
                <span th:text="price">Price</span>
                </body>
                </html>
                """);

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        // Configure server with globalVariables
        ServerConfig config = new ServerConfig()
                .globalVariables(Map.of("utils", utils));

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // Make request
        HttpResponse response = testHarness.get("/");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("HELLO WORLD"), "Should contain uppercased text from utils.uppercase()");
        assertTrue(body.contains("$19.99"), "Should contain formatted price from utils.formatPrice()");
    }

    @Test
    void testMultipleGlobalVariables() {
        // Create multiple utility objects
        TestUtils utils = new TestUtils();

        // Set up resources
        Map<String, String> testResources = new HashMap<>();
        testResources.put("api/test.js", """
                response.body = {
                    greeting: utils.greet(appName),
                    version: appVersion
                }
                """);

        ResourceResolver resolver = (path, caller) -> {
            String content = testResources.get(path);
            return content != null ? Resource.text(content) : null;
        };

        // Configure server with multiple globalVariables
        ServerConfig config = new ServerConfig()
                .apiPrefix("/api/")
                .globalVariables(Map.of(
                        "utils", utils,
                        "appName", "MyApp",
                        "appVersion", "2.0"
                ));

        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        InMemoryTestHarness testHarness = new InMemoryTestHarness(handler);

        // Make request
        HttpResponse response = testHarness.get("/api/test");

        assertEquals(200, response.getStatus());
        String body = response.getBodyString();
        assertTrue(body.contains("Hello, MyApp!"), "Should use appName in greeting");
        assertTrue(body.contains("2.0"), "Should contain appVersion");
    }

}

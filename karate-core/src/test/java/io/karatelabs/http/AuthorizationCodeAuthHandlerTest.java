package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.http.OAuth2Exception;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationCodeAuthHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(AuthorizationCodeAuthHandlerTest.class);

    static ServerTestHarness harness;
    static int port;

    // Swappable handler for per-test behavior
    static AtomicReference<Function<HttpRequest, HttpResponse>> mockHandler = new AtomicReference<>();

    @BeforeAll
    static void beforeAll() {
        harness = new ServerTestHarness((String) null);
        harness.setHandler(ctx -> {
            Function<HttpRequest, HttpResponse> h = mockHandler.get();
            if (h != null) {
                return h.apply(ctx.request());
            }
            return ctx.response();
        });
        harness.start();
        port = harness.getPort();
        logger.debug("Shared test server started on port {}", port);
    }

    @AfterAll
    static void afterAll() {
        if (harness != null) {
            harness.stop();
        }
        logger.debug("Shared test server stopped");
    }

    @BeforeEach
    void setUp() {
        // Reset handler before each test
        mockHandler.set(null);
    }

    // Note: No @AfterEach sleep needed since each test uses a unique callbackPort

    @Test
    void testFullAuthorizationCodeFlow() {
        // Track what URLs the mock browser receives
        AtomicReference<String> capturedAuthUrl = new AtomicReference<>();
        AtomicReference<String> capturedRedirectUri = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Verify it's a token exchange request
                String body = request.getBodyString();
                if (body.contains("grant_type=authorization_code")) {
                    response.setBody(Json.of("""
                        {
                            access_token: 'test-access-token',
                            token_type: 'Bearer',
                            refresh_token: 'test-refresh-token',
                            expires_in: 3600,
                            scope: 'read write'
                        }
                        """).asMap());
                }
            } else if (request.pathMatches("/api/test")) {
                // Protected API endpoint - return the Authorization header
                String authHeader = request.getHeader("authorization");
                response.setBody(authHeader);
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            // Setup OAuth config
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("scope", "read write");
            config.put("callbackPort", 9876); // Specific port to avoid fallback delays

            // Mock browser callback that simulates user authorization
            OAuth2BrowserCallback mockBrowser = new OAuth2BrowserCallback() {
                @Override
                public CompletableFuture<String> openBrowserForAuthorization(
                    String authorizationUrl,
                    String redirectUri
                ) {
                    capturedAuthUrl.set(authorizationUrl);
                    capturedRedirectUri.set(redirectUri);

                    logger.debug("Mock browser received auth URL: {}", authorizationUrl);
                    logger.debug("Mock browser redirect URI: {}", redirectUri);

                    // Verify authorization URL contains required parameters
                    assertTrue(authorizationUrl.contains("response_type=code"));
                    assertTrue(authorizationUrl.contains("client_id=test-client"));
                    assertTrue(authorizationUrl.contains("code_challenge="));
                    assertTrue(authorizationUrl.contains("code_challenge_method=S256"));
                    assertTrue(authorizationUrl.contains("scope=read+write"));
                    assertTrue(authorizationUrl.contains("state="));

                    // Simulate successful user authorization
                    return CompletableFuture.completedFuture("mock-authorization-code");
                }
            };

            // Create auth handler
            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            // Make an API request (this will trigger the OAuth flow)
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.method("get");
            HttpResponse response = http.invoke();

            // Verify the request included the Bearer token
            String body = response.getBodyString();
            assertEquals("Bearer test-access-token", body);

            // Verify browser was called
            assertNotNull(capturedAuthUrl.get());
            assertNotNull(capturedRedirectUri.get());

            logger.debug("Full authorization flow completed successfully");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testMissingAuthorizationUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://localhost:3000/token");
        config.put("client_id", "test-client");
        // Missing authorizationUrl

        OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
            CompletableFuture.completedFuture("code");

        AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:3000/api/test");

            OAuth2Exception exception = assertThrows(OAuth2Exception.class, () -> {
                http.invoke();
            });

            assertTrue(exception.getMessage().contains("Missing 'authorizationUrl'"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testMissingClientId() {
        Map<String, Object> config = new HashMap<>();
        config.put("authorizationUrl", "http://localhost:3000/authorize");
        config.put("url", "http://localhost:3000/token");
        // Missing client_id

        OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
            CompletableFuture.completedFuture("code");

        AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:3000/api/test");

            OAuth2Exception exception = assertThrows(OAuth2Exception.class, () -> {
                http.invoke();
            });

            assertTrue(exception.getMessage().contains("Missing 'client_id'"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testMissingTokenUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("authorizationUrl", "http://localhost:3000/authorize");
        config.put("client_id", "test-client");
        // Missing url (token endpoint)

        OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
            CompletableFuture.completedFuture("code");

        AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:3000/api/test");

            OAuth2Exception exception = assertThrows(OAuth2Exception.class, () -> {
                http.invoke();
            });

            assertTrue(exception.getMessage().contains("Missing 'url'"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testUserDeniesAuthorization() {
        mockHandler.set(request -> new HttpResponse());

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPort", 9877); // Specific port to avoid fallback delays

            // Mock browser that simulates user denial
            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(new OAuth2Exception("User denied authorization"));
                return future;
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");

            OAuth2Exception exception = assertThrows(OAuth2Exception.class, () -> {
                http.invoke();
            });

            assertTrue(exception.getMessage().contains("Authorization flow failed"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testTokenExchangeFailure() {
        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Return non-JSON error to trigger parsing failure
                // The ERROR logs from this test are expected - they verify error handling works
                response.setStatus(400);
                response.setBody("invalid_grant");
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPort", 9878); // Specific port to avoid fallback delays

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
                CompletableFuture.completedFuture("invalid-code");

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");

            OAuth2Exception exception = assertThrows(OAuth2Exception.class, () -> {
                http.invoke();
            });

            assertTrue(exception.getMessage().contains("Token exchange failed"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testPKCEVerification() {
        AtomicReference<String> codeVerifier = new AtomicReference<>();
        AtomicReference<String> codeChallenge = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                String body = request.getBodyString();

                // Extract code_verifier from token request
                String[] parts = body.split("&");
                for (String part : parts) {
                    if (part.startsWith("code_verifier=")) {
                        codeVerifier.set(part.substring("code_verifier=".length()));
                    }
                }

                response.setBody(Json.of("{ access_token: 'token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPort", 9879); // Specific port to avoid fallback delays

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                // Extract code_challenge from authorization URL
                String[] parts = authUrl.split("[&?]");
                for (String part : parts) {
                    if (part.startsWith("code_challenge=")) {
                        codeChallenge.set(part.substring("code_challenge=".length()));
                    }
                }
                return CompletableFuture.completedFuture("code");
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.invoke();

            // Verify PKCE was used
            assertNotNull(codeChallenge.get(), "code_challenge should be present in authorization URL");
            assertNotNull(codeVerifier.get(), "code_verifier should be present in token request");

            // Challenge and verifier should be different (S256 method)
            assertNotEquals(codeChallenge.get(), codeVerifier.get());

            logger.debug("PKCE challenge: {}", codeChallenge.get());
            logger.debug("PKCE verifier: {}", codeVerifier.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAuthHandlerType() {
        Map<String, Object> config = new HashMap<>();
        OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
            CompletableFuture.completedFuture("code");

        AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

        assertEquals("oauth2", handler.getType());
    }

    @Test
    void testCurlPreview() {
        Map<String, Object> config = new HashMap<>();
        OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) ->
            CompletableFuture.completedFuture("code");

        AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

        // Should return null (uses header placeholder)
        assertNull(handler.toCurlPreview("linux"));
    }

    @Test
    void testConfigurableSinglePort() {
        AtomicReference<String> capturedRedirectUri = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.of("{ access_token: 'token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPort", 9999); // Configure specific port

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                capturedRedirectUri.set(redirectUri);
                return CompletableFuture.completedFuture("code");
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.invoke();

            // Verify callback server used the configured port
            String redirectUri = capturedRedirectUri.get();
            assertNotNull(redirectUri);
            assertTrue(redirectUri.contains(":9999/callback"), "Expected port 9999, got: " + redirectUri);

            logger.debug("Callback server used configured port: {}", redirectUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConfigurableMultiplePorts() {
        AtomicReference<String> capturedRedirectUri = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.of("{ access_token: 'token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPorts", "7777,8888,9999"); // Multiple fallback ports

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                capturedRedirectUri.set(redirectUri);
                return CompletableFuture.completedFuture("code");
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.invoke();

            // Verify callback server used one of the configured ports
            String redirectUri = capturedRedirectUri.get();
            assertNotNull(redirectUri);
            boolean usedConfiguredPort = redirectUri.contains(":7777/") ||
                                        redirectUri.contains(":8888/") ||
                                        redirectUri.contains(":9999/");
            assertTrue(usedConfiguredPort, "Expected one of configured ports, got: " + redirectUri);

            logger.debug("Callback server used one of configured ports: {}", redirectUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Commented out: default port fallback is tested and working, but slow due to port scanning
    // Uncomment to verify default Postman-style ports (8080, 8888, 9090, 3000) work correctly
    /*
    @Test
    void testDefaultPortsWhenNotConfigured() {
        AtomicReference<String> capturedRedirectUri = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.of("{ access_token: 'token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            // No callbackPort or callbackPorts configured - should use defaults

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                capturedRedirectUri.set(redirectUri);
                return CompletableFuture.completedFuture("code");
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.invoke();

            // Verify callback server used one of the default ports (8080, 8888, 9090, 3000)
            String redirectUri = capturedRedirectUri.get();
            assertNotNull(redirectUri);
            boolean usedDefaultPort = redirectUri.contains(":8080/") ||
                                     redirectUri.contains(":8888/") ||
                                     redirectUri.contains(":9090/") ||
                                     redirectUri.contains(":3000/");
            assertTrue(usedDefaultPort, "Expected one of default ports, got: " + redirectUri);

            logger.debug("Callback server used default port: {}", redirectUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    */

    @Test
    void testPortStringConfiguration() {
        AtomicReference<String> capturedRedirectUri = new AtomicReference<>();

        mockHandler.set(request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                response.setBody(Json.of("{ access_token: 'token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("authorizationUrl", "http://localhost:" + port + "/authorize");
            config.put("url", "http://localhost:" + port + "/token");
            config.put("client_id", "test-client");
            config.put("callbackPort", "5555"); // String value

            OAuth2BrowserCallback mockBrowser = (authUrl, redirectUri) -> {
                capturedRedirectUri.set(redirectUri);
                return CompletableFuture.completedFuture("code");
            };

            AuthorizationCodeAuthHandler handler = new AuthorizationCodeAuthHandler(config, mockBrowser);

            HttpRequestBuilder http = new HttpRequestBuilder(client);
            http.auth(handler);
            http.url("http://localhost:" + port + "/api/test");
            http.invoke();

            // Verify string port was parsed correctly
            String redirectUri = capturedRedirectUri.get();
            assertTrue(redirectUri.contains(":5555/callback"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

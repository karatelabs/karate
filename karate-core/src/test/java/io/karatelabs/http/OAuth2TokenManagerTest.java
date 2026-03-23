package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2TokenManagerTest {

    static final Logger logger = LoggerFactory.getLogger(OAuth2TokenManagerTest.class);

    @Test
    void testStoreAndGetValidToken() {
        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://localhost:3000/token");
        config.put("client_id", "test-client");

        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // Initially no token
        assertNull(manager.getValidToken());

        // Store a fresh token
        long expiresAt = System.currentTimeMillis() + 3600000; // 1 hour
        OAuth2Token token = new OAuth2Token(
            "test-access-token",
            "Bearer",
            "test-refresh-token",
            expiresAt,
            "read write"
        );

        manager.storeToken(token);

        // Should be able to retrieve it
        OAuth2Token retrieved = manager.getValidToken();
        assertNotNull(retrieved);
        assertEquals("test-access-token", retrieved.getAccessToken());
        assertEquals("test-refresh-token", retrieved.getRefreshToken());
    }

    @Test
    void testGetValidTokenReturnsNullForExpiredToken() {
        Map<String, Object> config = new HashMap<>();
        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // Store an expired token
        long expiresAt = System.currentTimeMillis() - 1000; // 1 second ago
        OAuth2Token token = new OAuth2Token(
            "expired-token",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        manager.storeToken(token);

        // Should return null because token is expired
        assertNull(manager.getValidToken());
    }

    @Test
    void testGetValidTokenReturnsNullWithinExpiryBuffer() {
        Map<String, Object> config = new HashMap<>();
        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // Store a token expiring in 30 seconds (within 60-second buffer)
        long expiresAt = System.currentTimeMillis() + 30000;
        OAuth2Token token = new OAuth2Token(
            "about-to-expire",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        manager.storeToken(token);

        // Should return null because within expiry buffer
        assertNull(manager.getValidToken());
    }

    @Test
    void testClearToken() {
        Map<String, Object> config = new HashMap<>();
        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // Store a token
        long expiresAt = System.currentTimeMillis() + 3600000;
        OAuth2Token token = new OAuth2Token("test", "Bearer", "refresh", expiresAt, "read");
        manager.storeToken(token);

        assertNotNull(manager.getValidToken());

        // Clear it
        manager.clearToken();

        assertNull(manager.getValidToken());
    }

    @Test
    void testRefreshTokenSuccess() {
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Return a new token
                response.setBody(Json.of("""
                    {
                        access_token: 'new-access-token',
                        token_type: 'Bearer',
                        refresh_token: 'new-refresh-token',
                        expires_in: 7200,
                        scope: 'read write'
                    }
                    """).asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("url", "http://localhost:" + server.getPort() + "/token");
            config.put("client_id", "test-client");

            OAuth2TokenManager manager = new OAuth2TokenManager(config);

            // Store an expired token with refresh token
            long expiresAt = System.currentTimeMillis() - 1000;
            OAuth2Token oldToken = new OAuth2Token(
                "old-access-token",
                "Bearer",
                "old-refresh-token",
                expiresAt,
                "read"
            );
            manager.storeToken(oldToken);

            // Refresh the token
            HttpRequestBuilder builder = new HttpRequestBuilder(client);
            OAuth2Token newToken = manager.refreshToken(builder);

            assertNotNull(newToken);
            assertEquals("new-access-token", newToken.getAccessToken());
            assertEquals("new-refresh-token", newToken.getRefreshToken());
            assertFalse(newToken.isExpired());

            // Manager should store the new token
            OAuth2Token storedToken = manager.getValidToken();
            assertNotNull(storedToken);
            assertEquals("new-access-token", storedToken.getAccessToken());

            logger.debug("Token refreshed successfully: {}", newToken.getAccessToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testRefreshTokenWithClientSecret() {
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Verify client_secret was sent
                String body = request.getBodyString();
                assertTrue(body.contains("client_secret=test-secret"));

                response.setBody(Json.of("{ access_token: 'new-token', expires_in: 3600 }").asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("url", "http://localhost:" + server.getPort() + "/token");
            config.put("client_id", "test-client");
            config.put("client_secret", "test-secret"); // Confidential client

            OAuth2TokenManager manager = new OAuth2TokenManager(config);

            // Store an expired token with refresh token
            long expiresAt = System.currentTimeMillis() - 1000;
            OAuth2Token oldToken = new OAuth2Token(
                "old-token",
                "Bearer",
                "refresh-token",
                expiresAt,
                "read"
            );
            manager.storeToken(oldToken);

            // Refresh should include client_secret
            HttpRequestBuilder builder = new HttpRequestBuilder(client);
            OAuth2Token newToken = manager.refreshToken(builder);

            assertNotNull(newToken);
            assertEquals("new-token", newToken.getAccessToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testRefreshTokenWithoutRefreshToken() {
        Map<String, Object> config = new HashMap<>();
        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // Store a token WITHOUT refresh token
        long expiresAt = System.currentTimeMillis() + 3600000;
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            null, // No refresh token
            expiresAt,
            "read"
        );
        manager.storeToken(token);

        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder builder = new HttpRequestBuilder(client);
            OAuth2Token result = manager.refreshToken(builder);

            // Should return null - cannot refresh without refresh token
            assertNull(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testRefreshTokenWhenNoTokenStored() {
        Map<String, Object> config = new HashMap<>();
        OAuth2TokenManager manager = new OAuth2TokenManager(config);

        // No token stored
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder builder = new HttpRequestBuilder(client);
            OAuth2Token result = manager.refreshToken(builder);

            // Should return null - no token to refresh
            assertNull(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testRefreshTokenFailuresClearsToken() {
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Return error
                response.setStatus(400);
                response.setBody("invalid_grant");
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("url", "http://localhost:" + server.getPort() + "/token");
            config.put("client_id", "test-client");

            OAuth2TokenManager manager = new OAuth2TokenManager(config);

            // Store a token with refresh token
            long expiresAt = System.currentTimeMillis() - 1000;
            OAuth2Token oldToken = new OAuth2Token(
                "old-token",
                "Bearer",
                "invalid-refresh-token",
                expiresAt,
                "read"
            );
            manager.storeToken(oldToken);

            // Refresh should fail and throw exception
            HttpRequestBuilder builder = new HttpRequestBuilder(client);

            assertThrows(OAuth2Exception.class, () -> {
                manager.refreshToken(builder);
            });

            // Token should be cleared on failure
            assertNull(manager.getValidToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testMultipleRefreshes() {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            if (request.pathMatches("/token")) {
                // Return token with incrementing values
                String newAccessToken = "token-" + counter.incrementAndGet();

                response.setBody(Json.of(String.format("""
                    {
                        access_token: '%s',
                        token_type: 'Bearer',
                        refresh_token: 'refresh-%s',
                        expires_in: 3600
                    }
                    """, newAccessToken, newAccessToken)).asMap());
            }
            return response;
        });

        try (HttpClient client = new ApacheHttpClient()) {
            Map<String, Object> config = new HashMap<>();
            config.put("url", "http://localhost:" + server.getPort() + "/token");
            config.put("client_id", "test-client");

            OAuth2TokenManager manager = new OAuth2TokenManager(config);

            // Store initial token
            OAuth2Token token1 = new OAuth2Token(
                "token-1",
                "Bearer",
                "refresh-1",
                System.currentTimeMillis() - 1000,
                "read"
            );
            manager.storeToken(token1);

            // First refresh
            HttpRequestBuilder builder1 = new HttpRequestBuilder(client);
            OAuth2Token token2 = manager.refreshToken(builder1);
            assertNotNull(token2);
            assertTrue(token2.getAccessToken().startsWith("token-"));

            // Second refresh
            HttpRequestBuilder builder2 = new HttpRequestBuilder(client);
            OAuth2Token token3 = manager.refreshToken(builder2);
            assertNotNull(token3);
            assertTrue(token3.getAccessToken().startsWith("token-"));

            // Each refresh should give a different token
            assertNotEquals(token2.getAccessToken(), token3.getAccessToken());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

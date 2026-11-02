package io.karatelabs.http;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2TokenTest {

    static final Logger logger = LoggerFactory.getLogger(OAuth2TokenTest.class);

    @Test
    void testTokenFromMapWithAllFields() {
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", "test-access-token");
        data.put("token_type", "Bearer");
        data.put("refresh_token", "test-refresh-token");
        data.put("expires_in", 3600);
        data.put("scope", "read write");

        OAuth2Token token = OAuth2Token.fromMap(data);

        assertEquals("test-access-token", token.getAccessToken());
        assertEquals("Bearer", token.getTokenType());
        assertEquals("test-refresh-token", token.getRefreshToken());
        assertEquals("read write", token.getScope());
        assertTrue(token.hasRefreshToken());
        assertFalse(token.isExpired());
    }

    @Test
    void testTokenFromMapWithDefaults() {
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", "test-token");
        // No token_type, expires_in, refresh_token, or scope

        OAuth2Token token = OAuth2Token.fromMap(data);

        assertEquals("test-token", token.getAccessToken());
        assertEquals("Bearer", token.getTokenType()); // Default
        assertNull(token.getRefreshToken());
        assertNull(token.getScope());
        assertFalse(token.hasRefreshToken());
    }

    @Test
    void testTokenFromMapWithIntegerExpiresIn() {
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", "test-token");
        data.put("expires_in", Integer.valueOf(7200));

        long beforeCreation = System.currentTimeMillis();
        OAuth2Token token = OAuth2Token.fromMap(data);
        long afterCreation = System.currentTimeMillis();

        // Token should expire approximately 7200 seconds (2 hours) from now
        long expectedExpiryMin = beforeCreation + (7200 * 1000L);
        long expectedExpiryMax = afterCreation + (7200 * 1000L);

        assertTrue(token.getExpiresAt() >= expectedExpiryMin);
        assertTrue(token.getExpiresAt() <= expectedExpiryMax);
    }

    @Test
    void testTokenFromMapWithLongExpiresIn() {
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", "test-token");
        data.put("expires_in", Long.valueOf(1800));

        OAuth2Token token = OAuth2Token.fromMap(data);

        // Token should expire approximately 1800 seconds (30 minutes) from now
        long expiresIn = token.getExpiresInSeconds();
        assertTrue(expiresIn >= 1795 && expiresIn <= 1800);
    }

    @Test
    void testIsExpiredWithFreshToken() {
        long expiresAt = System.currentTimeMillis() + 3600000; // 1 hour from now
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        assertFalse(token.isExpired());
    }

    @Test
    void testIsExpiredWithExpiredToken() {
        long expiresAt = System.currentTimeMillis() - 1000; // 1 second ago
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        assertTrue(token.isExpired());
    }

    @Test
    void testIsExpiredWithin60SecondBuffer() {
        // issued an hour ago with 30 seconds left: inside the 60 second skew
        long now = System.currentTimeMillis();
        OAuth2Token token = new OAuth2Token("access-token", "Bearer", "refresh-token", now + 30000, "read", now - 3600000);
        assertTrue(token.isExpired());
    }

    @Test
    void testIsExpiredJustOutsideBuffer() {
        // Token expires in 70 seconds - should NOT be considered expired (outside 60 second buffer)
        long now = System.currentTimeMillis();
        OAuth2Token token = new OAuth2Token("access-token", "Bearer", "refresh-token", now + 70000, "read", now - 3600000);
        assertFalse(token.isExpired());
    }

    @Test
    void aShortLivedTokenIsRenewedATenthOfItsLifetimeEarly() {
        long now = System.currentTimeMillis();
        assertFalse(new OAuth2Token("a", "Bearer", null, now + 30000, null).isExpired(), "a fresh 30 second token is reused");
        assertTrue(new OAuth2Token("a", "Bearer", null, now + 2000, null, now - 28000).isExpired(), "with 2 of 30 seconds left it is renewed");
        assertFalse(new OAuth2Token("a", "Bearer", null, now + 5000, null, now - 25000).isExpired());
    }

    @Test
    void aHugeExpiresInSaturatesRatherThanOverflows() {
        OAuth2Token token = OAuth2Token.fromMap(Map.of("access_token", "a", "expires_in", Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, token.getExpiresAt());
        assertFalse(token.isExpired());
    }

    @Test
    void expiresInMayBeAStringOrAnyNumber() {
        long now = System.currentTimeMillis();
        assertTrue(Math.abs(OAuth2Token.fromMap(Map.of("access_token", "a", "expires_in", "120")).getExpiresAt() - now - 120000) < 5000);
        assertTrue(Math.abs(OAuth2Token.fromMap(Map.of("access_token", "a", "expires_in", 120.0)).getExpiresAt() - now - 120000) < 5000);
    }

    @Test
    void testHasRefreshTokenWithToken() {
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "refresh-token",
            System.currentTimeMillis() + 3600000,
            "read"
        );

        assertTrue(token.hasRefreshToken());
    }

    @Test
    void testHasRefreshTokenWithNullToken() {
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            null,
            System.currentTimeMillis() + 3600000,
            "read"
        );

        assertFalse(token.hasRefreshToken());
    }

    @Test
    void testHasRefreshTokenWithEmptyToken() {
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "",
            System.currentTimeMillis() + 3600000,
            "read"
        );

        assertFalse(token.hasRefreshToken());
    }

    @Test
    void testGetExpiresInSeconds() {
        long expiresAt = System.currentTimeMillis() + 3600000; // 1 hour from now
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        long expiresIn = token.getExpiresInSeconds();
        // Should be close to 3600 seconds (allowing for test execution time)
        assertTrue(expiresIn >= 3595 && expiresIn <= 3600);
    }

    @Test
    void testGetExpiresInSecondsForExpiredToken() {
        long expiresAt = System.currentTimeMillis() - 1000; // 1 second ago
        OAuth2Token token = new OAuth2Token(
            "access-token",
            "Bearer",
            "refresh-token",
            expiresAt,
            "read"
        );

        assertEquals(0, token.getExpiresInSeconds()); // Should return 0, not negative
    }

    @Test
    void testAllGetters() {
        long expiresAt = System.currentTimeMillis() + 3600000;
        OAuth2Token token = new OAuth2Token(
            "test-access",
            "Bearer",
            "test-refresh",
            expiresAt,
            "read write"
        );

        assertEquals("test-access", token.getAccessToken());
        assertEquals("Bearer", token.getTokenType());
        assertEquals("test-refresh", token.getRefreshToken());
        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals("read write", token.getScope());
    }
}

package io.karatelabs.http;

import java.util.Map;

public class OAuth2Token {

    private final String accessToken;
    private final String tokenType;
    private final String refreshToken;
    private final long expiresAt; // Absolute time (System.currentTimeMillis())
    private final String scope;

    public OAuth2Token(String accessToken, String tokenType, String refreshToken,
                       long expiresAt, String scope) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
    }

    /**
     * Create token from OAuth token response
     */
    public static OAuth2Token fromMap(Map<String, Object> data) {
        String accessToken = (String) data.get("access_token");
        String tokenType = (String) data.getOrDefault("token_type", "Bearer");
        String refreshToken = (String) data.get("refresh_token");

        // Handle both Integer and Long for expires_in
        Object expiresInObj = data.get("expires_in");
        int expiresIn = 3600; // Default 1 hour
        if (expiresInObj instanceof Integer) {
            expiresIn = (Integer) expiresInObj;
        } else if (expiresInObj instanceof Long) {
            expiresIn = ((Long) expiresInObj).intValue();
        }

        String scope = (String) data.get("scope");

        long expiresAt = System.currentTimeMillis() + (expiresIn * 1000L);

        return new OAuth2Token(accessToken, tokenType, refreshToken, expiresAt, scope);
    }

    /**
     * Check if token is expired (with 60 second buffer)
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= (expiresAt - 60000);
    }

    /**
     * Check if refresh token is available
     */
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    // Getters
    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiresAt() { return expiresAt; }
    public String getScope() { return scope; }

    public long getExpiresInSeconds() {
        long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
}

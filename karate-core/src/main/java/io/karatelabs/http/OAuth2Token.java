package io.karatelabs.http;

import java.util.Map;

public class OAuth2Token {

    private final String accessToken;
    private final String tokenType;
    private final String refreshToken;
    private final long expiresAt; // Absolute time (System.currentTimeMillis())
    private final String scope;
    private final long skewMillis;

    public OAuth2Token(String accessToken, String tokenType, String refreshToken,
                       long expiresAt, String scope) {
        this(accessToken, tokenType, refreshToken, expiresAt, scope, System.currentTimeMillis());
    }

    OAuth2Token(String accessToken, String tokenType, String refreshToken,
                long expiresAt, String scope, long issuedAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
        // renewed a little early: a tenth of its lifetime, at most a minute, so a short-lived token is still reused
        this.skewMillis = Math.max(0, Math.min(60000, (expiresAt - issuedAt) / 10));
    }

    /**
     * The token a token endpoint's answer carries. Only a 2xx whose {@code access_token} is a non-blank string is a
     * token; any other answer throws {@link OAuth2Exception}, so a test run fails the step that needed it rather than
     * send {@code Bearer null} or a token from an error answer. The message starts with {@code failed}, then names the
     * status and, unless {@code confidential}, the server's {@code error} and {@code error_description} or the start of
     * its answer.
     */
    public static OAuth2Token fromResponse(HttpResponse response, String failed, boolean confidential) {
        String body = response.getBodyString();
        Map<String, Object> data = null;
        try {
            io.karatelabs.common.Json json = io.karatelabs.common.Json.of(body);
            data = json.isObject() ? json.asMap() : null;
        } catch (Exception e) {
            // not JSON: reported below with the raw body
        }
        int status = response.getStatus();
        if (status >= 200 && status < 300 && data != null
                && data.get("access_token") instanceof String accessToken && !accessToken.isBlank()) {
            return fromMap(data);
        }
        if (confidential) {
            throw new OAuth2Exception(failed + "HTTP " + status);
        }
        String reason = data != null && data.get("error") != null
                ? data.get("error") + (data.get("error_description") == null ? "" : " - " + data.get("error_description"))
                : body == null || body.length() <= 200 ? body : body.substring(0, 200) + "...";
        throw new OAuth2Exception(failed + "HTTP " + status + ": " + reason);
    }

    /**
     * Create token from OAuth token response
     */
    public static OAuth2Token fromMap(Map<String, Object> data) {
        String accessToken = (String) data.get("access_token");
        String tokenType = (String) data.getOrDefault("token_type", "Bearer");
        String refreshToken = (String) data.get("refresh_token");

        long expiresIn = 3600; // RFC 6749 §5.1 makes expires_in optional: an hour when absent
        Object expiresInObj = data.get("expires_in");
        if (expiresInObj instanceof Number number) {
            expiresIn = number.longValue();
        } else if (expiresInObj instanceof String text) {
            try {
                expiresIn = Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                // not a number: keep the default
            }
        }

        String scope = (String) data.get("scope");

        long now = System.currentTimeMillis();
        // saturates rather than overflows for a huge expires_in
        long expiresAt = expiresIn > (Long.MAX_VALUE - now) / 1000 ? Long.MAX_VALUE : now + expiresIn * 1000;

        return new OAuth2Token(accessToken, tokenType, refreshToken, expiresAt, scope);
    }

    /** Expired, or within its skew of expiring: a tenth of the lifetime it was issued with, at most 60 seconds. */
    public boolean isExpired() {
        return System.currentTimeMillis() >= (expiresAt - skewMillis);
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

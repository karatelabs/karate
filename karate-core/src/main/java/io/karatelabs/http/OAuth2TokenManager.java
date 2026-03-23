package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Manages OAuth 2.0 token storage and refresh.
 */
public class OAuth2TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenManager.class);

    private final Map<String, Object> config;
    private OAuth2Token currentToken;

    public OAuth2TokenManager(Map<String, Object> config) {
        this.config = config;
    }

    /**
     * Get current valid token (null if expired or not available)
     */
    public OAuth2Token getValidToken() {
        if (currentToken == null || currentToken.isExpired()) {
            return null;
        }
        return currentToken;
    }

    /**
     * Store new token
     */
    public void storeToken(OAuth2Token token) {
        this.currentToken = token;
        logger.debug("Token stored, expires in {} seconds", token.getExpiresInSeconds());
    }

    /**
     * Refresh access token using refresh token
     * @return New token or null if refresh fails
     */
    public OAuth2Token refreshToken(HttpRequestBuilder builder) {
        if (currentToken == null || !currentToken.hasRefreshToken()) {
            logger.warn("Cannot refresh: no refresh token available");
            return null;
        }

        logger.debug("Refreshing OAuth token...");

        String tokenUrl = (String) config.get("url");
        builder.url(tokenUrl);
        builder.formField("grant_type", "refresh_token");
        builder.formField("refresh_token", currentToken.getRefreshToken());
        builder.formField("client_id", config.get("client_id"));

        // Optional client_secret (for confidential clients)
        if (config.containsKey("client_secret")) {
            builder.formField("client_secret", config.get("client_secret"));
        }

        builder.header("Accept", "application/json");

        try {
            HttpResponse response = builder.invoke("post");
            String body = response.getBodyString();
            int status = response.getStatus();

            if (status < 200 || status >= 300) {
                String errorMessage = parseOAuthError(body, status);
                logger.error("Token refresh failed: {}", errorMessage);
                currentToken = null;
                throw new OAuth2Exception(errorMessage);
            }

            Json json;
            try {
                json = Json.of(body);
            } catch (Exception e) {
                String errorMessage = "Token refresh failed: server returned invalid JSON response";
                logger.error(errorMessage);
                currentToken = null;
                throw new OAuth2Exception(errorMessage);
            }

            if (!json.isObject()) {
                String errorMessage = "Token refresh failed: expected JSON object but received " +
                    (json.isArray() ? "array" : "primitive value");
                logger.error(errorMessage);
                currentToken = null;
                throw new OAuth2Exception(errorMessage);
            }

            Map<String, Object> data = json.asMap();
            OAuth2Token newToken = OAuth2Token.fromMap(data);
            storeToken(newToken);
            logger.debug("Token refreshed successfully");
            return newToken;
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            String errorMessage = "Token refresh failed: " + e.getMessage();
            logger.error(errorMessage);
            currentToken = null;
            throw new OAuth2Exception(errorMessage, e);
        }
    }

    /**
     * Clear stored token
     */
    public void clearToken() {
        currentToken = null;
        logger.debug("Token cleared");
    }

    /**
     * Parse OAuth error response and return a user-friendly message
     */
    private String parseOAuthError(String body, int status) {
        if (body == null || body.isBlank()) {
            return "Token refresh failed: server returned HTTP " + status + " with empty response";
        }
        try {
            Json json = Json.of(body);
            if (json.isObject()) {
                Map<String, Object> data = json.asMap();
                String error = (String) data.get("error");
                String errorDescription = (String) data.get("error_description");
                if (error != null) {
                    if (errorDescription != null) {
                        return "Token refresh failed: " + error + " - " + errorDescription;
                    }
                    return "Token refresh failed: " + error;
                }
            }
        } catch (Exception ignored) {
            // Body is not valid JSON, use raw body in message
        }
        // Truncate long responses for readability
        String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
        return "Token refresh failed: server returned HTTP " + status + ": " + preview;
    }
}

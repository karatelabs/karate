package io.karatelabs.http;

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
        ClientCredentialsAuthHandler.clientAuthentication(builder, config);

        builder.header("Accept", "application/json");

        try {
            HttpResponse response = builder.invoke("post");
            OAuth2Token newToken;
            try {
                newToken = OAuth2Token.fromResponse(response, "Token refresh failed: ", false);
            } catch (OAuth2Exception e) {
                logger.error(e.getMessage());
                currentToken = null;
                throw e;
            }
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
}

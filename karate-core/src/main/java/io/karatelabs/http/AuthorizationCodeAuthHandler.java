package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 2.0 Authorization Code + PKCE Auth Handler.
 * Implements the authorization code flow with PKCE for public clients.
 */
public class AuthorizationCodeAuthHandler implements AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationCodeAuthHandler.class);

    private final Map<String, Object> config;
    private final OAuth2BrowserCallback browserCallback;
    private final OAuth2TokenManager tokenManager;

    public AuthorizationCodeAuthHandler(
        Map<String, Object> config,
        OAuth2BrowserCallback browserCallback
    ) {
        this.config = config;
        this.browserCallback = browserCallback;
        this.tokenManager = new OAuth2TokenManager(config);
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        OAuth2Token token = tokenManager.getValidToken();

        // Check if we need a new token
        if (token == null || token.isExpired()) {
            if (token != null && token.hasRefreshToken()) {
                logger.debug("Token expired, attempting refresh...");
                try {
                    token = tokenManager.refreshToken(builder.forkNewBuilder());
                } catch (OAuth2Exception e) {
                    logger.warn("Token refresh failed, performing full authorization flow");
                    token = null;
                }
            }

            if (token == null) {
                logger.debug("No valid token, starting authorization flow...");
                token = performAuthorizationFlow(builder);
            }
        }

        // Apply token to request
        builder.header("Authorization", "Bearer " + token.getAccessToken());
    }

    /**
     * Perform full authorization code flow with PKCE
     */
    private OAuth2Token performAuthorizationFlow(HttpRequestBuilder builder) {
        LocalCallbackServer callbackServer = null;

        try {
            // 1. Generate PKCE
            logger.debug("Generating PKCE...");
            PkceGenerator pkce = PkceGenerator.create();

            // 2. Start local callback server
            logger.debug("Starting local callback server...");
            callbackServer = new LocalCallbackServer();
            String redirectUri = startCallbackServer(callbackServer);

            // 3. Build authorization URL
            String authUrl = buildAuthorizationUrl(pkce, redirectUri);
            logger.debug("Authorization URL: {}", authUrl);

            // 4. Open browser and wait for authorization code
            logger.debug("Opening browser for user authorization...");
            CompletableFuture<String> browserFuture =
                browserCallback.openBrowserForAuthorization(authUrl, redirectUri);

            // Wait for code (with timeout)
            // If the browser callback returns a sentinel value, use the callback server's future instead
            // This supports system browser implementations that can't capture the code directly
            String browserResult = browserFuture.get(30, TimeUnit.SECONDS);
            String code;

            if ("USE_CALLBACK_SERVER".equals(browserResult)) {
                // System browser mode: wait for callback server to receive the code
                logger.debug("Browser opened, waiting for OAuth callback...");
                code = callbackServer.getCodeFuture().get(5, TimeUnit.MINUTES);
            } else {
                // WebView mode: browser callback captured the code directly
                code = browserResult;
            }

            logger.debug("Authorization code received");

            // 5. Exchange code for token
            OAuth2Token token = exchangeCodeForToken(
                builder.forkNewBuilder(),
                code,
                pkce.getVerifier(),
                redirectUri
            );

            // 6. Store token
            tokenManager.storeToken(token);

            return token;

        } catch (Exception e) {
            logger.error("Authorization flow failed: {}", e.getMessage());
            throw new OAuth2Exception("Authorization flow failed: " + e.getMessage(), e);
        } finally {
            if (callbackServer != null) {
                callbackServer.stop();
            }
        }
    }

    /**
     * Build authorization URL with all required parameters
     */
    private String buildAuthorizationUrl(PkceGenerator pkce, String redirectUri) {
        String authzEndpoint = (String) config.get("authorizationUrl");
        if (authzEndpoint == null) {
            throw new OAuth2Exception("Missing 'authorizationUrl' in OAuth config");
        }

        String clientId = (String) config.get("client_id");
        if (clientId == null) {
            throw new OAuth2Exception("Missing 'client_id' in OAuth config");
        }

        String scope = (String) config.getOrDefault("scope", "");

        StringBuilder url = new StringBuilder(authzEndpoint);
        url.append(authzEndpoint.contains("?") ? "&" : "?");
        url.append("response_type=code");
        url.append("&client_id=").append(urlEncode(clientId));
        url.append("&redirect_uri=").append(urlEncode(redirectUri));
        url.append("&code_challenge=").append(urlEncode(pkce.getChallenge()));
        url.append("&code_challenge_method=").append(pkce.getMethod());

        if (!scope.isEmpty()) {
            url.append("&scope=").append(urlEncode(scope));
        }

        // Add state for CSRF protection
        String state = generateState();
        url.append("&state=").append(urlEncode(state));

        return url.toString();
    }

    /**
     * Exchange authorization code for access token
     */
    private OAuth2Token exchangeCodeForToken(
        HttpRequestBuilder builder,
        String code,
        String codeVerifier,
        String redirectUri
    ) {
        String tokenUrl = (String) config.get("url");
        if (tokenUrl == null) {
            throw new OAuth2Exception("Missing 'url' (token endpoint) in OAuth config");
        }

        logger.debug("Exchanging authorization code for token...");

        builder.url(tokenUrl);
        builder.formField("grant_type", "authorization_code");
        builder.formField("code", code);
        builder.formField("redirect_uri", redirectUri);
        builder.formField("client_id", config.get("client_id"));
        builder.formField("code_verifier", codeVerifier);

        // Optional client_secret (for confidential clients)
        if (config.containsKey("client_secret")) {
            builder.formField("client_secret", config.get("client_secret"));
        }

        builder.header("Accept", "application/json");

        try {
            HttpResponse response = builder.invoke("post");
            String bodyString = response.getBodyString();
            Json json;
            try {
                json = Json.of(bodyString);
            } catch (Exception e) {
                throw new OAuth2Exception("Token endpoint returned invalid response: " + truncate(bodyString));
            }
            if (!json.isObject()) {
                throw new OAuth2Exception("Token endpoint returned unexpected response: " + truncate(bodyString));
            }
            Map<String, Object> data = json.asMap();
            if (data.containsKey("error")) {
                String error = String.valueOf(data.get("error"));
                String desc = data.containsKey("error_description") ? String.valueOf(data.get("error_description")) : null;
                throw new OAuth2Exception("Token request failed: " + error + (desc != null ? " - " + desc : ""));
            }

            logger.debug("Token exchange successful");
            return OAuth2Token.fromMap(data);

        } catch (OAuth2Exception e) {
            logger.error("Token exchange failed: {}", e.getMessage());
            throw new OAuth2Exception("Token exchange failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Token exchange failed: {}", e.getMessage());
            throw new OAuth2Exception("Token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Start callback server on configured or default ports
     */
    private String startCallbackServer(LocalCallbackServer server) {
        int[] ports = getConfiguredPorts();

        Exception lastException = null;
        for (int port : ports) {
            try {
                String redirectUri = server.start(port);
                logger.debug("Callback server started on port {}", port);
                return redirectUri;
            } catch (Exception e) {
                logger.warn("Port {} in use, trying next port", port);
                lastException = e;
            }
        }

        // If all configured ports fail, provide helpful error message
        throw new OAuth2Exception(
            "Could not start callback server on any configured port: " + java.util.Arrays.toString(ports) + ". " +
            "Please ensure at least one port is available, or configure different ports using 'callbackPort' or 'callbackPorts'. " +
            "These ports must be registered as redirect URIs with your OAuth provider.",
            lastException
        );
    }

    /**
     * Get configured callback ports or use defaults
     */
    private int[] getConfiguredPorts() {
        // Check for single port configuration
        Object callbackPort = config.get("callbackPort");
        if (callbackPort != null) {
            return new int[] { parsePort(callbackPort) };
        }

        // Check for multiple ports configuration
        Object callbackPorts = config.get("callbackPorts");
        if (callbackPorts != null) {
            return parsePortList(callbackPorts);
        }

        // Default ports (Postman-style)
        return new int[] { 8080, 8888, 9090, 3000 };
    }

    /**
     * Parse a single port value
     */
    private int parsePort(Object portValue) {
        if (portValue instanceof Integer) {
            return (Integer) portValue;
        }
        if (portValue instanceof String) {
            try {
                return Integer.parseInt((String) portValue);
            } catch (NumberFormatException e) {
                throw new OAuth2Exception("Invalid callbackPort: " + portValue);
            }
        }
        throw new OAuth2Exception("Invalid callbackPort type: " + portValue.getClass());
    }

    /**
     * Parse comma-separated port list
     */
    private int[] parsePortList(Object portsValue) {
        if (portsValue instanceof String) {
            String[] parts = ((String) portsValue).split(",");
            int[] ports = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    ports[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    throw new OAuth2Exception("Invalid port in callbackPorts: " + parts[i]);
                }
            }
            return ports;
        }
        throw new OAuth2Exception("Invalid callbackPorts type: " + portsValue.getClass());
    }

    private String generateState() {
        // Simple state generation - could be more sophisticated
        return java.util.UUID.randomUUID().toString();
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new OAuth2Exception("URL encoding failed", e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "(empty)";
        }
        if (value.length() <= 100) {
            return value;
        }
        return value.substring(0, 100) + "...";
    }

    @Override
    public String getType() {
        return "oauth2";
    }

    @Override
    public String toCurlPreview(String platform) {
        return null; // Use header placeholder
    }
}

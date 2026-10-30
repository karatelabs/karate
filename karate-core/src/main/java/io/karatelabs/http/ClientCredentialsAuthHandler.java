package io.karatelabs.http;

import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class ClientCredentialsAuthHandler implements OAuth2AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientCredentialsAuthHandler.class);

    private final Map<String, Object> config;
    private Map<String, Object> token;

    public ClientCredentialsAuthHandler(Map<String, Object> config) {
        this.config = config;
    }

    /** The config's token {@code url}, a relative one resolved against the main request's scheme and authority. */
    public static String tokenUrl(Map<String, Object> config, String mainRequestUrl) {
        String tokenUrl = (String) config.get("url");

        // If token URL is relative, resolve it using the main request's protocol and host
        if (tokenUrl != null && !tokenUrl.startsWith("http://") && !tokenUrl.startsWith("https://")) {
            if (mainRequestUrl != null) {
                try {
                    URI mainUri = new URI(mainRequestUrl);
                    String baseUrl = mainUri.getScheme() + "://" + mainUri.getAuthority();
                    // Ensure the token URL starts with /
                    if (!tokenUrl.startsWith("/")) {
                        tokenUrl = "/" + tokenUrl;
                    }
                    tokenUrl = baseUrl + tokenUrl;
                } catch (URISyntaxException e) {
                    logger.warn("failed to parse main request URL for relative token URL resolution: {}", e.getMessage());
                }
            }
        }
        return tokenUrl;
    }

    /** {@code client_authentication: 'header'}: the client authenticates with HTTP Basic, not in the form. */
    static boolean basicClientAuthentication(Map<String, Object> config) {
        return "header".equals(config.get("client_authentication"));
    }

    /** {@code Authorization: Basic} over the form-encoded client_id and client_secret (RFC 6749 §2.3.1). */
    static void basicClientAuthentication(HttpRequestBuilder builder, Map<String, Object> config) {
        String credentials = formEncode(config.get("client_id")) + ":" + formEncode(config.get("client_secret"));
        builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }

    private static String formEncode(Object value) {
        return value == null ? "" : URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
    }

    static Map<String, Object> requestToken(HttpRequestBuilder builder, Map<String, Object> config, String mainRequestUrl) {
        String tokenUrl = tokenUrl(config, mainRequestUrl);
        builder.url(tokenUrl);
        builder.formField("grant_type", "client_credentials");
        if (basicClientAuthentication(config)) {
            basicClientAuthentication(builder, config);
        } else {
            builder.formField("client_id", config.get("client_id"));
            builder.formField("client_secret", config.get("client_secret"));
        }
        if (config.containsKey("scope")) {
            builder.formField("scope", config.get("scope"));
        }
        builder.header("Accept", "application/json");
        try {
            HttpResponse response = builder.invoke("post");
            return Json.of(response.getBodyString()).asMap();
        } catch (Exception e) {
            logger.error("client credentials auth request failed: {}", e.getMessage());
            throw new RuntimeException("client credentials auth request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getAccessToken(HttpRequestBuilder builder) {
        if (token == null) {
            token = requestToken(builder.forkNewBuilder(), config, builder.getUrl());
        }
        return String.valueOf(token.get("access_token"));
    }

    @Override
    public String toCurlPreview(String platform) {
        // Return null to indicate we should use a header placeholder
        // Don't call toCurlArgument() as that would trigger token fetch
        return null;
    }

    public String getAuthorizationHeaderPreview() {
        return "Bearer <your-oauth2-access-token>";
    }

}

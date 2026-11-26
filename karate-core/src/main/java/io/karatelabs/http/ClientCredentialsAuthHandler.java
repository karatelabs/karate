package io.karatelabs.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClientCredentialsAuthHandler implements OAuth2AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientCredentialsAuthHandler.class);

    private final Map<String, Object> config;
    private final OAuth2TokenCache cache = new OAuth2TokenCache();
    Consumer<String> onCredential; // set by AuthHandlers.of
    boolean confidential; // set by AuthHandlers.of

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

    /** The client's credentials, as HTTP Basic or else in the form; a {@code client_secret} only when configured (a public client has none). */
    static void clientAuthentication(HttpRequestBuilder builder, Map<String, Object> config) {
        if (basicClientAuthentication(config)) {
            basicClientAuthentication(builder, config);
        } else {
            builder.formField("client_id", config.get("client_id"));
            if (config.containsKey("client_secret")) {
                builder.formField("client_secret", config.get("client_secret"));
            }
        }
    }

    private static String formEncode(Object value) {
        return value == null ? "" : URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
    }

    static OAuth2Token requestToken(HttpRequestBuilder builder, Map<String, Object> config, String mainRequestUrl,
                                    Consumer<String> onCredential, boolean confidential) {
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
        return postTokenRequest(builder, "OAuth2 client_credentials token request failed: ", e -> {
            logger.error("client credentials auth request failed: {}", e.getMessage());
            return new OAuth2Exception("client credentials auth request failed: " + e.getMessage(), e);
        }, onCredential, confidential);
    }

    /**
     * Posts the token request {@code http} holds and answers its token, as {@link OAuth2Token#fromResponse} reads it,
     * which {@code onCredential} receives, with the form-encoded refresh token, before it is returned.
     * {@code confidential} follows no redirect, whatever the client follows, logs the exchange without its headers or
     * body, and fails with the status alone and no cause; otherwise the request follows the client's redirect setting,
     * logs as any other, and a request that is not answered fails as {@code transportFailure} makes it.
     */
    static OAuth2Token postTokenRequest(HttpRequestBuilder http, String failed, Function<Exception, OAuth2Exception> transportFailure,
                                        Consumer<String> onCredential, boolean confidential) {
        http.header("Accept", "application/json");
        http.method("post");
        HttpResponse response;
        try {
            HttpRequest request = http.build();
            if (confidential) {
                request.setFollowRedirects(false);
                request.setLogDetails(false);
            }
            response = http.getClient().invoke(request);
        } catch (Exception e) {
            if (confidential) {
                throw new OAuth2Exception(failed + "the request was not answered");
            }
            throw transportFailure.apply(e);
        }
        OAuth2Token token = OAuth2Token.fromResponse(response, failed, confidential);
        String refresh = token.getRefreshToken();
        AuthHandlers.report(onCredential, token.getAccessToken(), refresh);
        if (refresh != null && !formEncode(refresh).equals(refresh)) {
            AuthHandlers.report(onCredential, formEncode(refresh));
        }
        return token;
    }

    @Override
    public OAuth2Token getToken(HttpRequestBuilder builder) {
        return cache.get(expired -> requestToken(builder.forkNewBuilder(), config, builder.getUrl(), onCredential, confidential));
    }

    @Override
    public String getAccessToken(HttpRequestBuilder builder) {
        return getToken(builder).getAccessToken();
    }

    @Override
    public void rejected(OAuth2Token rejected) {
        cache.rejected(rejected);
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

package io.karatelabs.http;

import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The OAuth2 resource owner password grant: {@code url} (the token endpoint), {@code username}, {@code password},
 * and optional {@code client_id}, {@code client_secret}, {@code client_authentication} ({@code header} sends the client's
 * credentials as HTTP Basic, {@code body} or absent in the form) and {@code scope}. The token is cached until it expires, then
 * refreshed with its refresh token if it has one, else (or if the refresh is refused) requested again.
 */
public class PasswordAuthHandler implements OAuth2AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(PasswordAuthHandler.class);

    private final Map<String, Object> config;
    private OAuth2Token token;

    public PasswordAuthHandler(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public String getAccessToken(HttpRequestBuilder builder) {
        if (token == null || token.isExpired()) {
            OAuth2Token next = null;
            if (token != null && token.hasRefreshToken()) {
                try {
                    next = requestToken(builder, form("refresh_token", "refresh_token", token.getRefreshToken()));
                    if (!next.hasRefreshToken()) {
                        next = new OAuth2Token(next.getAccessToken(), next.getTokenType(), token.getRefreshToken(), next.getExpiresAt(), next.getScope());
                    }
                } catch (OAuth2Exception e) {
                    logger.warn("token refresh failed, requesting a new token: {}", e.getMessage());
                }
            }
            if (next == null) {
                Map<String, Object> form = form("password", "username", config.get("username"));
                form.put("password", config.get("password"));
                if (config.get("scope") != null) {
                    form.put("scope", config.get("scope"));
                }
                next = requestToken(builder, form);
            }
            token = next;
        }
        return token.getAccessToken();
    }

    private Map<String, Object> form(String grantType, String key, Object value) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("grant_type", grantType);
        form.put(key, value);
        if (!ClientCredentialsAuthHandler.basicClientAuthentication(config)) {
            for (String client : new String[]{"client_id", "client_secret"}) {
                if (config.get(client) != null) {
                    form.put(client, config.get(client));
                }
            }
        }
        return form;
    }

    private OAuth2Token requestToken(HttpRequestBuilder builder, Map<String, Object> form) {
        HttpRequestBuilder http = builder.forkNewBuilder();
        http.url(ClientCredentialsAuthHandler.tokenUrl(config, builder.getUrl()));
        form.forEach(http::formField);
        if (ClientCredentialsAuthHandler.basicClientAuthentication(config)) {
            ClientCredentialsAuthHandler.basicClientAuthentication(http, config);
        }
        http.header("Accept", "application/json");
        String failed = "OAuth2 " + form.get("grant_type") + " token request failed: ";
        HttpResponse response;
        try {
            response = http.invoke("post");
        } catch (Exception e) {
            throw new OAuth2Exception(failed + e.getMessage(), e);
        }
        String body = response.getBodyString();
        Map<String, Object> data = null;
        try {
            Json json = Json.of(body);
            data = json.isObject() ? json.asMap() : null;
        } catch (Exception e) {
            // not JSON: reported below with the raw body
        }
        int status = response.getStatus();
        if (status < 200 || status >= 300 || data == null || data.get("access_token") == null) {
            String reason = data != null && data.get("error") != null
                    ? data.get("error") + (data.get("error_description") == null ? "" : " - " + data.get("error_description"))
                    : body == null || body.length() <= 200 ? body : body.substring(0, 200) + "...";
            throw new OAuth2Exception(failed + "HTTP " + status + ": " + reason);
        }
        return OAuth2Token.fromMap(data);
    }

}

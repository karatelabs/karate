/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builds the {@link AuthHandler} an {@code auth} map configures, the map {@code configure auth} takes: its
 * {@code type} and that type's fields.
 */
public final class AuthHandlers {

    private static final Set<String> TYPES = Set.of("basic", "bearer", "digest", "ntlm", "oauth2", "aws", "jwt",
            "oauth1", "hawk");

    private AuthHandlers() {
    }

    /** Whether {@link #of} builds a handler for an {@code auth} map of this {@code type}. */
    public static boolean supports(String type) {
        return type != null && TYPES.contains(type);
    }

    /**
     * A new handler for {@code auth}, or null when the map is null or empty, has no {@code type}, or names a type
     * {@link #supports} does not know. Nothing is cached: an {@code oauth2} handler keeps its own token, so a caller
     * that wants a token reused keeps the handler, and the handler lives as long as its caller holds it.
     *
     * @param url          the URL of the request the handler signs; a relative {@code oauth2} token URL resolves
     *                     against its scheme and authority
     * @param onCredential nullable; receives each value the handler can put on the wire, encoded forms included,
     *                     before any request or log line can carry it: a value known now (a token, a password, a
     *                     secret, Basic's username and its encoding) before this method returns, and one made later (an
     *                     OAuth2 access or refresh token, a signed JWT, a request signature) as soon as it is made
     */
    public static AuthHandler of(Map<String, Object> auth, String url, Consumer<String> onCredential) {
        return of(auth, url, onCredential, false);
    }

    /**
     * {@link #of(Map, String, Consumer)}, where {@code confidential} keeps an {@code oauth2} handler's token request
     * from following a redirect, whatever its client follows, and from logging its headers and body, which carry the
     * client secret and the token. Off, the token request follows the client's redirect setting and logs as any other.
     */
    public static AuthHandler of(Map<String, Object> auth, String url, Consumer<String> onCredential, boolean confidential) {
        if (auth == null || auth.isEmpty() || !(auth.get("type") instanceof String type) || !supports(type)) {
            return null;
        }
        return switch (type) {
            case "basic" -> {
                String username = (String) auth.get("username");
                String password = (String) auth.get("password");
                if (onCredential != null) {
                    report(onCredential, username, password, Base64.getEncoder()
                            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
                }
                yield new BasicAuthHandler(username, password);
            }
            case "bearer" -> {
                String token = (String) auth.get("token");
                report(onCredential, token);
                yield new BearerAuthHandler(token);
            }
            case "digest" -> {
                String password = (String) auth.get("password");
                report(onCredential, password);
                yield new DigestAuthHandler((String) auth.get("username"), password);
            }
            case "ntlm" -> {
                String password = (String) auth.get("password");
                report(onCredential, password);
                yield new NtlmAuthHandler((String) auth.get("username"), password,
                        (String) auth.get("domain"), (String) auth.get("workstation"));
            }
            case "oauth2" -> oauth2(oauth2Settings(auth, url), onCredential, confidential);
            case "aws" -> {
                report(onCredential, auth.get("secretKey"), auth.get("sessionToken"));
                AwsSigV4AuthHandler handler = new AwsSigV4AuthHandler(auth);
                handler.onCredential = onCredential;
                yield handler;
            }
            case "jwt" -> {
                report(onCredential, auth.get("secret"), auth.get("privateKey"));
                JwtAuthHandler handler = new JwtAuthHandler(auth);
                handler.onCredential = onCredential;
                yield handler;
            }
            case "oauth1" -> {
                Object token = auth.get("token");
                report(onCredential, auth.get("consumerSecret"), token, auth.get("tokenSecret"), auth.get("privateKey"));
                if (token != null && !OAuth1AuthHandler.encode(token.toString()).equals(token.toString())) {
                    report(onCredential, OAuth1AuthHandler.encode(token.toString()));
                }
                OAuth1AuthHandler handler = new OAuth1AuthHandler(auth);
                handler.onCredential = onCredential;
                yield handler;
            }
            case "hawk" -> {
                report(onCredential, auth.get("authKey"));
                HawkAuthHandler handler = new HawkAuthHandler(auth);
                handler.onCredential = onCredential;
                yield handler;
            }
            default -> null;
        };
    }

    /**
     * The token request an {@code oauth2} auth map makes for a request to {@code url}: equal settings fetch the same
     * token, so a caller that shares one handler across requests can key it on these.
     */
    public static Map<String, Object> oauth2Settings(Map<String, Object> auth, String url) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("url", auth.get("accessTokenUrl"));
        settings.put("client_id", auth.get("clientId"));
        settings.put("client_secret", auth.get("clientSecret"));
        if (auth.containsKey("scope")) {
            settings.put("scope", auth.get("scope"));
        }
        if (auth.containsKey("clientAuthentication")) {
            settings.put("client_authentication", auth.get("clientAuthentication"));
        }
        boolean password = "password".equals(auth.get("grantType"));
        if (password) {
            settings.put("username", auth.get("username"));
            settings.put("password", auth.get("password"));
        }
        settings.put("grant_type", password ? "password" : "client_credentials");
        // absolute, so equal settings name one endpoint whatever host the request goes to
        settings.put("url", ClientCredentialsAuthHandler.tokenUrl(settings, url));
        return settings;
    }

    private static OAuth2AuthHandler oauth2(Map<String, Object> settings, Consumer<String> onCredential, boolean confidential) {
        Object secret = settings.get("client_secret");
        Object password = settings.get("password");
        for (Object value : new Object[]{secret, password}) {
            report(onCredential, value);
            if (value != null && !formEncoded(value).equals(value.toString())) {
                report(onCredential, formEncoded(value));
            }
        }
        if (ClientCredentialsAuthHandler.basicClientAuthentication(settings)) {
            String pair = formEncoded(settings.get("client_id")) + ":" + formEncoded(secret);
            report(onCredential, Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
        }
        if ("password".equals(settings.get("grant_type"))) {
            PasswordAuthHandler handler = new PasswordAuthHandler(settings);
            handler.onCredential = onCredential;
            handler.confidential = confidential;
            return handler;
        }
        ClientCredentialsAuthHandler handler = new ClientCredentialsAuthHandler(settings);
        handler.onCredential = onCredential;
        handler.confidential = confidential;
        return handler;
    }

    private static String formEncoded(Object value) {
        return value == null ? "" : URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
    }

    /** Hands each non-empty value to {@code onCredential}, when there is one. */
    static void report(Consumer<String> onCredential, Object... values) {
        if (onCredential == null) {
            return;
        }
        for (Object value : values) {
            if (value != null && !value.toString().isEmpty()) {
                onCredential.accept(value.toString());
            }
        }
    }

}

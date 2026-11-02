/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

import io.karatelabs.common.Json;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * Hawk request signing (hapijs/hawk's {@code Authorization: Hawk id="…", ts="…", nonce="…", mac="…"}) as
 * postman-runtime's hawk authorizer signs: an HMAC ({@code algorithm} {@code sha256} or {@code sha1}, keyed by
 * {@code authKey}) over the timestamp, nonce, method, path and query, host, port, payload hash, {@code ext} and
 * {@code app}/{@code dlg}. {@code nonce} (six letters or digits) and {@code timestamp} (seconds) are generated per
 * request unless configured. {@code includePayloadHash} adds {@code hash}, over the body and the request's
 * {@code Content-Type}, when there is a body and it is not multipart. No {@code authId} or {@code authKey} sends no
 * header; another algorithm sends an empty one.
 */
public class HawkAuthHandler implements AuthHandler {

    private static final String NONCE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Object> config;
    private final Clock clock;

    public HawkAuthHandler(Map<String, Object> config) {
        this(config, Clock.systemUTC());
    }

    public HawkAuthHandler(Map<String, Object> config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    public String getType() {
        return "hawk";
    }

    @Override
    public boolean followRedirects() {
        return false;
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        String id = text("authId");
        String key = text("authKey");
        if (id.isEmpty() || key.isEmpty()) {
            return;
        }
        builder.removeHeader("Authorization");
        String hash = null;
        if (truthy(config.get("includePayloadHash"))) {
            String contentType = builder.getContentType();
            byte[] body = builder.getBody() == null ? new byte[0] : Json.toBytes(builder.getBody());
            boolean multipart = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
            if (body.length > 0 && !multipart) {
                hash = payloadHash(text("algorithm"), contentType, body);
            }
        }
        builder.header("Authorization", header(builder.getMethod(), builder.getUri(), hash));
    }

    /** The header for a request to {@code uri}; empty for an algorithm Hawk does not know. */
    String header(String method, String uri, String hash) {
        String algorithm = text("algorithm");
        if (!"sha1".equals(algorithm) && !"sha256".equals(algorithm)) {
            return "";
        }
        String timestamp = text("timestamp");
        if (leadingInt(timestamp) == 0) {
            timestamp = String.valueOf(clock.instant().getEpochSecond());
        }
        String nonce = text("nonce");
        if (nonce.isEmpty()) {
            nonce = nonce();
        }
        URI parsed = URI.create(uri);
        String resource = (parsed.getRawPath() == null || parsed.getRawPath().isEmpty() ? "/" : parsed.getRawPath())
                + (parsed.getRawQuery() == null ? "" : "?" + parsed.getRawQuery());
        int port = parsed.getPort() != -1 ? parsed.getPort() : "http".equalsIgnoreCase(parsed.getScheme()) ? 80 : 443;
        String ext = text("extraData");
        String app = text("app");
        String dlg = text("delegation");
        // hawk.1.header normalization; ext's escapes replace only the first of each, as postman-request's String.replace
        StringBuilder normalized = new StringBuilder("hawk.1.header\n").append(timestamp).append('\n').append(nonce)
                .append('\n').append(method.toUpperCase(Locale.ROOT)).append('\n').append(resource).append('\n')
                .append(parsed.getHost().toLowerCase(Locale.ROOT)).append('\n').append(port).append('\n')
                .append(hash == null ? "" : hash).append('\n');
        if (!ext.isEmpty()) {
            normalized.append(ext.replaceFirst("\\\\", "\\\\\\\\").replaceFirst("\n", "\\\\n"));
        }
        normalized.append('\n');
        if (!app.isEmpty()) {
            normalized.append(app).append('\n').append(dlg).append('\n');
        }
        String mac = Base64.getEncoder().encodeToString(JwtAuthHandler.hmac("HS" + bits(algorithm),
                text("authKey").getBytes(StandardCharsets.UTF_8), normalized.toString().getBytes(StandardCharsets.UTF_8)));
        StringBuilder header = new StringBuilder("Hawk id=\"").append(text("authId")).append("\", ts=\"").append(timestamp)
                .append("\", nonce=\"").append(nonce);
        if (hash != null) {
            header.append("\", hash=\"").append(hash);
        }
        if (!ext.isEmpty()) {
            header.append("\", ext=\"").append(ext.replace("\\", "\\\\").replace("\"", "\\\""));
        }
        header.append("\", mac=\"").append(mac).append('"');
        if (!app.isEmpty()) {
            header.append(", app=\"").append(app).append(dlg.isEmpty() ? "" : "\", dlg=\"" + dlg).append('"');
        }
        return header.toString();
    }

    /** hawk.1.payload: the content type's media type, lowercased, then the body. */
    static String payloadHash(String algorithm, String contentType, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-" + bits(algorithm));
            String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            digest.update(("hawk.1.payload\n" + type + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            digest.update((byte) '\n');
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new IllegalArgumentException("hawk: no digest for algorithm " + algorithm, e);
        }
    }

    private static String bits(String algorithm) {
        return "sha1".equals(algorithm) ? "1" : "256";
    }

    private String text(String key) {
        Object value = config.get(key);
        return value == null ? "" : value.toString();
    }

    /** lodash's parseInt: the leading integer of the text, 0 for none. */
    private static long leadingInt(String s) {
        String t = s.trim();
        int end = t.startsWith("-") || t.startsWith("+") ? 1 : 0;
        int start = end;
        while (end < t.length() && Character.isDigit(t.charAt(end))) {
            end++;
        }
        return end == start ? 0 : Long.parseLong(t.substring(0, end).replace("+", ""));
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return !s.isEmpty();
        }
        return o != null;
    }

    private static String nonce() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return sb.toString();
    }

}

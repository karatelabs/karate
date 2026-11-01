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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OAuth 1.0 (RFC 5849) request signing, signed at send time over the final method, URL, form body and body, as
 * postman-runtime's oauth1 authorizer signs: HMAC-SHA1/256/512 and PLAINTEXT with {@code consumerSecret} and
 * {@code tokenSecret}, RSA-SHA1/256/512 with a PEM {@code privateKey} (PKCS#1 or PKCS#8). The protocol params go in
 * the {@code Authorization} header ({@code addParamsTo: 'header'}, the default) or, for {@code 'query'}, in a form body
 * of a POST or PUT, else the query; any already there are replaced. A nonce and timestamp are generated per request
 * unless configured.
 */
public class OAuth1AuthHandler implements AuthHandler {

    static final List<String> METHODS = List.of("HMAC-SHA1", "HMAC-SHA256", "HMAC-SHA512",
            "RSA-SHA1", "RSA-SHA256", "RSA-SHA512", "PLAINTEXT");
    private static final Set<String> PROTOCOL_PARAMS = Set.of("oauth_consumer_key", "oauth_token",
            "oauth_signature_method", "oauth_timestamp", "oauth_nonce", "oauth_version", "oauth_signature",
            "oauth_callback", "oauth_verifier", "oauth_body_hash");
    private static final String NONCE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Object> config;
    private final String signatureMethod;
    private final boolean addEmpty;
    private final boolean query;
    private final PrivateKey privateKey;
    private final Clock clock;

    public OAuth1AuthHandler(Map<String, Object> config) {
        this(config, Clock.systemUTC());
    }

    public OAuth1AuthHandler(Map<String, Object> config, Clock clock) {
        this.config = config;
        this.clock = clock;
        addEmpty = flag(config.get("addEmptyParamsToSign"));
        String method = value("signatureMethod");
        signatureMethod = method == null ? "HMAC-SHA1" : method;
        if (!METHODS.contains(signatureMethod)) {
            throw new RuntimeException("auth type 'oauth1' signatureMethod must be one of " + String.join(", ", METHODS)
                    + ": " + signatureMethod);
        }
        Object to = config.get("addParamsTo");
        String addParamsTo = to == null || to.toString().isEmpty() ? "header" : to.toString();
        if (!"header".equals(addParamsTo) && !"query".equals(addParamsTo)) {
            throw new RuntimeException("auth type 'oauth1' addParamsTo must be 'header' or 'query': " + addParamsTo);
        }
        query = "query".equals(addParamsTo);
        required("consumerKey");
        if (signatureMethod.startsWith("RSA")) {
            privateKey = privateKey(required("privateKey"));
        } else {
            required("consumerSecret");
            privateKey = null;
        }
    }

    private String required(String key) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException("auth type 'oauth1' requires '" + key + "'");
        }
        return value;
    }

    /** The config value as text; a blank one is none unless {@code addEmptyParamsToSign}. */
    private String value(String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return !addEmpty && s.isBlank() ? null : s;
    }

    private static boolean flag(Object value) {
        return value instanceof Boolean b ? b : value != null && "true".equalsIgnoreCase(value.toString());
    }

    /** The base string and the protocol params as placed (the signature last), kept so a test can assert them. */
    record Signed(String baseString, List<String[]> params, String authorization) {

        String signature() {
            return params.getLast()[1];
        }

    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        builder.removeHeader("Authorization");
        String url = builder.getUrl();
        for (String name : PROTOCOL_PARAMS) {
            url = JwtAuthHandler.withoutParam(url, name);
            List<String> stale = builder.getParam(name);
            if (stale != null) {
                stale.clear();
            }
        }
        builder.url(url);
        String contentType = builder.getContentType();
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean form = type.startsWith("application/x-www-form-urlencoded");
        byte[] body = builder.getBody() == null ? new byte[0] : Json.toBytes(builder.getBody());
        List<String> formPairs = new ArrayList<>();
        if (form) {
            String text = new String(body, StandardCharsets.UTF_8);
            for (String pair : text.split("&")) {
                if (!pair.isEmpty() && !PROTOCOL_PARAMS.contains(decode(pair.split("=", 2)[0]))) {
                    formPairs.add(pair);
                }
            }
            if (!String.join("&", formPairs).equals(text)) {
                builder.body(String.join("&", formPairs).getBytes(StandardCharsets.UTF_8));
            }
        }
        List<String[]> bodyParams = new ArrayList<>();
        formPairs.forEach(pair -> bodyParams.add(decodePair(pair)));
        // the body hash extension: not for a form body, nor a multipart one, which postman-runtime does not hash
        byte[] hashed = form || type.startsWith("multipart/") ? null : body;
        String method = builder.getMethod();
        Signed signed = sign(method, builder.getUri(), bodyParams, hashed);
        if (!query) {
            builder.header("Authorization", signed.authorization());
            return;
        }
        List<String> encoded = new ArrayList<>();
        for (String[] param : signed.params()) {
            encoded.add(encode(param[0]) + "=" + encode(param[1]));
        }
        if (form && (method.contains("POST") || method.contains("PUT"))) {
            formPairs.addAll(encoded);
            builder.body(String.join("&", formPairs).getBytes(StandardCharsets.UTF_8));
        } else {
            int hash = url.indexOf('#');
            String fragment = hash < 0 ? "" : url.substring(hash);
            String base = hash < 0 ? url : url.substring(0, hash);
            builder.url(base + (base.indexOf('?') < 0 ? "?" : base.endsWith("?") || base.endsWith("&") ? "" : "&")
                    + String.join("&", encoded) + fragment);
        }
    }

    /**
     * Signs over {@code uri} (its query params read decoded) and the decoded form {@code bodyParams}; {@code body} is
     * hashed into {@code oauth_body_hash} when {@code includeBodyHash} is set and it is not null.
     */
    Signed sign(String method, String uri, List<String[]> bodyParams, byte[] body) {
        List<String[]> params = new ArrayList<>();
        add(params, "oauth_consumer_key", value("consumerKey"));
        add(params, "oauth_token", value("token"));
        add(params, "oauth_signature_method", signatureMethod);
        String timestamp = value("timestamp");
        add(params, "oauth_timestamp", timestamp == null || timestamp.isEmpty()
                ? String.valueOf(clock.instant().getEpochSecond()) : timestamp);
        String nonce = value("nonce");
        add(params, "oauth_nonce", nonce == null || nonce.isEmpty() ? nonce() : nonce);
        add(params, "oauth_version", config.containsKey("version") ? value("version") : "1.0");
        // extension params are signed only when given, whatever addEmptyParamsToSign says
        String hashAlgorithm = signatureMethod.contains("-") ? signatureMethod.substring(signatureMethod.indexOf('-') + 1) : null;
        if (body != null && hashAlgorithm != null && flag(config.get("includeBodyHash"))) {
            params.add(new String[]{"oauth_body_hash", Base64.getEncoder().encodeToString(digest(hashAlgorithm, body))});
        }
        for (String[] extension : new String[][]{{"oauth_callback", "callback"}, {"oauth_verifier", "verifier"}}) {
            String value = value(extension[1]);
            if (value != null && !value.isEmpty()) {
                params.add(new String[]{extension[0], value});
            }
        }
        URI parsed = URI.create(uri);
        List<String[]> all = new ArrayList<>(params);
        String rawQuery = parsed.getRawQuery();
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                if (!pair.isEmpty()) {
                    all.add(decodePair(pair));
                }
            }
        }
        all.addAll(bodyParams);
        List<String> normalized = new ArrayList<>();
        for (String[] param : all) {
            normalized.add(encode(param[0]) + "=" + encode(param[1]));
        }
        // sorted by name then value, both encoded: ' ' sorts before any character an encoded string holds
        normalized.sort(Comparator.comparing(s -> s.replaceFirst("=", " ")));
        String baseString = encode(method.toUpperCase(Locale.ROOT)) + "&" + encode(baseUri(parsed)) + "&"
                + encode(String.join("&", normalized));
        params.add(new String[]{"oauth_signature", signature(baseString)});
        return new Signed(baseString, params, authorization(params));
    }

    private void add(List<String[]> params, String name, String value) {
        if (addEmpty || (value != null && !value.isEmpty())) {
            params.add(new String[]{name, value == null ? "" : value});
        }
    }

    private static String baseUri(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port) + path;
    }

    private String authorization(List<String[]> params) {
        boolean raw = flag(config.get("disableHeaderEncoding"));
        List<String> parts = new ArrayList<>();
        Object realm = config.get("realm");
        if (realm != null && !realm.toString().isBlank()) {
            parts.add("realm=\"" + (raw ? realm : encode(realm.toString())) + "\"");
        }
        for (String[] param : params) {
            if (!param[1].isEmpty()) {
                String value = param[1].trim();
                parts.add((raw ? param[0] : encode(param[0])) + "=\"" + (raw ? value : encode(value)) + "\"");
            }
        }
        return "OAuth " + String.join(",", parts);
    }

    private String signature(String baseString) {
        byte[] data = baseString.getBytes(StandardCharsets.UTF_8);
        String bits = signatureMethod.substring(signatureMethod.indexOf('-') + 4);
        try {
            if (privateKey != null) {
                Signature signer = Signature.getInstance("SHA" + bits + "withRSA");
                signer.initSign(privateKey);
                signer.update(data);
                return Base64.getEncoder().encodeToString(signer.sign());
            }
            String secret = value("consumerSecret");
            String tokenSecret = value("tokenSecret");
            String key = encode(secret == null ? "" : secret) + "&" + encode(tokenSecret == null ? "" : tokenSecret);
            if ("PLAINTEXT".equals(signatureMethod)) {
                return key;
            }
            String name = "HmacSHA" + bits;
            Mac mac = Mac.getInstance(name);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), name));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (Exception e) {
            throw new RuntimeException("auth type 'oauth1' could not sign: " + e.getMessage(), e);
        }
    }

    private static byte[] digest(String algorithm, byte[] body) {
        try {
            return MessageDigest.getInstance(algorithm.replace("SHA", "SHA-")).digest(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String nonce() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return sb.toString();
    }

    /** A PEM RSA private key, PKCS#8 ({@code BEGIN PRIVATE KEY}) or PKCS#1 ({@code BEGIN RSA PRIVATE KEY}). */
    static PrivateKey privateKey(String pem) {
        boolean pkcs1 = pem.contains("-----BEGIN RSA PRIVATE KEY-----");
        if (!pkcs1 && !pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new RuntimeException("auth type 'oauth1' privateKey must be a PEM RSA private key");
        }
        String body = pem.replaceAll("-----(BEGIN|END) (RSA )?PRIVATE KEY-----|\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? pkcs8(der) : der));
        } catch (Exception e) {
            throw new RuntimeException("auth type 'oauth1' privateKey is not a valid RSA private key: " + e.getMessage(), e);
        }
    }

    /** Wraps a PKCS#1 RSAPrivateKey in a PKCS#8 PrivateKeyInfo: version 0, rsaEncryption, the key as an octet string. */
    private static byte[] pkcs8(byte[] pkcs1) {
        byte[] head = {0x02, 0x01, 0x00, 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d,
                0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        inner.writeBytes(head);
        inner.write(0x04);
        inner.writeBytes(derLength(pkcs1.length));
        inner.writeBytes(pkcs1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.writeBytes(derLength(inner.size()));
        out.writeBytes(inner.toByteArray());
        return out.toByteArray();
    }

    private static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }
        if (length < 0x100) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        if (length < 0x10000) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        }
        return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }

    static String encode(String s) {
        return AwsSigV4AuthHandler.encode(s);
    }

    /** A form or query pair decoded as postman-runtime decodes it: '+' is a space, a pair with no '=' has an empty value. */
    private static String[] decodePair(String pair) {
        int eq = pair.indexOf('=');
        return eq < 0 ? new String[]{decode(pair), ""} : new String[]{decode(pair.substring(0, eq)), decode(pair.substring(eq + 1))};
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    @Override
    public String getType() {
        return "oauth1";
    }

}

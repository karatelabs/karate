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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * AWS Signature Version 4: signs the request as it will go on the wire, so it runs after
 * every header, param and the body are final. Pure JDK crypto.
 */
public class AwsSigV4AuthHandler implements AuthHandler {

    static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    // hop-by-hop and proxy headers the transport rewrites or an intermediary strips, per AWS's
    // canonical-header rules; transfer-encoding is re-emitted by the client from the entity
    private static final Set<String> UNSIGNED_HEADERS = Set.of("authorization", "connection", "expect", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade",
            "user-agent", "x-amzn-trace-id");

    private final String accessKey;
    private final String secretKey;
    private final String sessionToken;
    private final String region;
    private final String service;
    private final Clock clock;

    public AwsSigV4AuthHandler(Map<String, Object> config) {
        this(config, Clock.systemUTC());
    }

    public AwsSigV4AuthHandler(Map<String, Object> config, Clock clock) {
        accessKey = required(config, "accessKey");
        secretKey = required(config, "secretKey");
        region = required(config, "region");
        service = required(config, "service");
        Object token = config.get("sessionToken");
        sessionToken = token == null || token.toString().isEmpty() ? null : token.toString();
        this.clock = clock;
    }

    private static String required(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || value.toString().isEmpty()) {
            throw new RuntimeException("auth type 'aws' requires '" + key + "'");
        }
        return value.toString();
    }

    /**
     * The headers signing adds (X-Amz-Date, X-Amz-Security-Token, x-amz-content-sha256 for s3,
     * Authorization) and the two intermediates, kept so a test can assert them byte-exact.
     */
    record Signed(Map<String, String> headers, String canonicalRequest, String stringToSign) {

        String authorization() {
            return headers.get("Authorization");
        }

    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        URI uri = URI.create(builder.getUri());
        String host = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        builder.header("Host", host);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : builder.getHeaders().keySet()) {
            List<String> values = builder.getHeaderValues(name);
            // the client writes a header as ISO-8859-1, so a wider value would be sent as '??'
            if (!UNSIGNED_HEADERS.contains(name.toLowerCase(Locale.ROOT)) && !values.stream().allMatch(AwsSigV4AuthHandler::ascii)) {
                throw new RuntimeException("auth type 'aws' cannot sign header '" + name + "': the value is not ASCII");
            }
            headers.put(name, values);
        }
        byte[] payload = builder.getBody() == null ? new byte[0] : Json.toBytes(builder.getBody());
        Signed signed = sign(builder.getMethod(), uri.getRawPath(), uri.getRawQuery(), headers, payload);
        signed.headers().forEach(builder::header);
    }

    private static boolean ascii(String value) {
        return value.chars().allMatch(c -> c < 0x80);
    }

    // the signature covers this URI, so the next hop would carry a stale one
    @Override
    public boolean followRedirects() {
        return false;
    }

    Signed sign(String method, String rawPath, String rawQuery, Map<String, List<String>> headers, byte[] payload) {
        String amzDate = AMZ_DATE.format(clock.instant());
        String dateStamp = amzDate.substring(0, 8);
        String payloadHash = hex(sha256(payload));
        Map<String, String> added = new LinkedHashMap<>();
        added.put("X-Amz-Date", amzDate);
        if (sessionToken != null) {
            added.put("X-Amz-Security-Token", sessionToken);
        }
        if ("s3".equals(service)) {
            added.put("x-amz-content-sha256", payloadHash);
        }
        TreeMap<String, String> canonicalHeaders = new TreeMap<>();
        headers.forEach((name, values) -> {
            String key = name.toLowerCase(Locale.ROOT);
            if (!UNSIGNED_HEADERS.contains(key)) {
                canonicalHeaders.merge(key, canonicalHeaderValue(values), (a, b) -> a + "," + b);
            }
        });
        added.forEach((name, value) -> canonicalHeaders.put(name.toLowerCase(Locale.ROOT), value));
        StringBuilder headerLines = new StringBuilder();
        canonicalHeaders.forEach((name, value) -> headerLines.append(name).append(':').append(value).append('\n'));
        String signedHeaders = String.join(";", canonicalHeaders.keySet());
        String canonicalRequest = method + '\n'
                + canonicalPath(rawPath) + '\n'
                + canonicalQuery(rawQuery) + '\n'
                + headerLines + '\n'
                + signedHeaders + '\n'
                + payloadHash;
        String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = ALGORITHM + '\n' + amzDate + '\n' + scope + '\n' + hex(sha256(utf8(canonicalRequest)));
        byte[] key = hmac(utf8("AWS4" + secretKey), dateStamp);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = hex(hmac(key, stringToSign));
        added.put("Authorization", ALGORITHM + " Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return new Signed(added, canonicalRequest, stringToSign);
    }

    private static String canonicalHeaderValue(List<String> values) {
        List<String> trimmed = new ArrayList<>(values.size());
        for (String value : values) {
            trimmed.add(String.join(" ", value.trim().split("\\s+")));
        }
        return String.join(",", trimmed);
    }

    // S3 signs the path as sent: no dot-segment normalization and no second encoding
    private String canonicalPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        if ("s3".equals(service)) {
            return rawPath;
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : rawPath.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                segments.pollLast();
            } else {
                segments.addLast(encode(segment));
            }
        }
        if (segments.isEmpty()) {
            return "/";
        }
        boolean trailingSlash = rawPath.endsWith("/") || rawPath.endsWith("/.") || rawPath.endsWith("/..");
        return "/" + String.join("/", segments) + (trailingSlash ? "/" : "");
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : part.substring(eq + 1);
            pairs.add(new String[]{encode(decode(key)), encode(decode(value))});
        }
        pairs.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));
        List<String> encoded = new ArrayList<>(pairs.size());
        for (String[] pair : pairs) {
            encoded.add(pair[0] + "=" + pair[1]);
        }
        return String.join("&", encoded);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean unreserved(byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')
                || b == '-' || b == '_' || b == '.' || b == '~';
    }

    static String encode(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : utf8(s)) {
            if (unreserved(b)) {
                sb.append((char) b);
            } else {
                sb.append('%').append(HexFormat.of().withUpperCase().toHexDigits(b));
            }
        }
        return sb.toString();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(utf8(data));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getType() {
        return "aws";
    }

}

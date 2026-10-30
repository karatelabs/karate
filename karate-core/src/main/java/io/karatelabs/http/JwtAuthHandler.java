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
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON Web Token (JWS compact form) signed per request config and sent as postman-runtime's jwt authorizer sends it:
 * {@code Authorization: <headerPrefix> <token>} ({@code headerPrefix} nil is {@code Bearer}, empty is none), or for
 * {@code addTokenTo: 'query'} the {@code queryParamKey} param (default {@code token}), replacing one already there.
 * The JOSE header is {@code header} with {@code alg} set to {@code algorithm}; {@code header} and {@code payload} are
 * JSON text or maps, serialized as JavaScript's {@code JSON.stringify} does so a token matches jose's byte for byte.
 * HS* sign with {@code secret} (UTF-8, or base64 when {@code isSecretBase64Encoded}); RS*, PS* and ES* with a PKCS#8
 * PEM {@code privateKey}. The token is signed once per handler; a signing failure throws with jose's message.
 */
public class JwtAuthHandler implements AuthHandler {

    private static final Set<String> ALGORITHMS = Set.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512",
            "PS256", "PS384", "PS512", "ES256", "ES384", "ES512");
    private static final String PKCS8_BEGIN = "-----BEGIN PRIVATE KEY-----";

    private final Map<String, Object> config;
    private final boolean query;
    private final String headerPrefix;
    private final String queryParamKey;
    private String token;

    public JwtAuthHandler(Map<String, Object> config) {
        this.config = config;
        Object to = config.get("addTokenTo");
        String addTokenTo = to == null || to.toString().isEmpty() ? "header" : to.toString();
        if (!"header".equals(addTokenTo) && !"query".equals(addTokenTo)) {
            throw new IllegalArgumentException("jwt addTokenTo must be 'header' or 'query': " + addTokenTo);
        }
        query = "query".equals(addTokenTo);
        Object prefix = config.get("headerPrefix");
        String p = prefix == null ? "Bearer" : prefix.toString();
        headerPrefix = p.isEmpty() ? "" : p + " ";
        Object key = config.get("queryParamKey");
        queryParamKey = key == null || key.toString().isEmpty() ? "token" : key.toString();
    }

    /** The signed token, signed on first use. */
    public synchronized String token() {
        if (token == null) {
            token = sign();
        }
        return token;
    }

    @Override
    public void apply(HttpRequestBuilder builder) {
        String jwt = token();
        if (query) {
            builder.url(withoutParam(builder.getUrl(), queryParamKey));
            List<String> existing = builder.getParam(queryParamKey);
            if (existing != null) {
                existing.clear();
            }
            builder.param(queryParamKey, jwt);
        } else {
            builder.removeHeader("Authorization");
            builder.header("Authorization", headerPrefix + jwt);
        }
    }

    @Override
    public String getType() {
        return "jwt";
    }

    private String sign() {
        String algorithm = text(config.get("algorithm"));
        if (algorithm == null || !ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException("invalid algorithm");
        }
        boolean hs = algorithm.startsWith("HS");
        Object secret = config.get("secret");
        Object privateKey = config.get("privateKey");
        if (hs && !(secret instanceof String s && !s.isEmpty())) {
            throw new IllegalArgumentException("Invalid secret key. Enter a valid key.");
        }
        if (!hs && !(privateKey instanceof String k && !k.isEmpty())) {
            throw new IllegalArgumentException("Invalid private key. Enter a valid key.");
        }
        Map<String, Object> header = header(config.get("header"), algorithm);
        Object payload = config.get("payload");
        if (payload instanceof String s) {
            payload = s.trim().isEmpty() ? Map.of() : parse(s.trim());
        }
        if (!(payload instanceof Map)) {
            throw new IllegalArgumentException("JWT Claims Set MUST be an object");
        }
        String input = base64Url(stringify(header).getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(stringify(payload).getBytes(StandardCharsets.UTF_8));
        byte[] data = input.getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        if (hs) {
            String s = (String) secret;
            byte[] key = truthy(config.get("isSecretBase64Encoded")) ? decodeBase64(s) : s.getBytes(StandardCharsets.UTF_8);
            signature = hmac(algorithm, key, data);
        } else {
            signature = signWithKey(algorithm, (String) privateKey, data);
        }
        return input + "." + base64Url(signature);
    }

    /** {@code {...header, alg}}: the header's own keys in order, {@code alg} in place or appended; a falsy header is none. */
    private static Map<String, Object> header(Object raw, String algorithm) {
        Object header = raw;
        if (raw instanceof String s) {
            header = s.trim().isEmpty() ? null : parse(s.trim());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (header instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        } else if (header instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                out.put(String.valueOf(i), list.get(i));
            }
        }
        out.put("alg", algorithm);
        return out;
    }

    private static Object parse(String json) {
        return Json.parseStrict(json, true);
    }

    static byte[] hmac(String algorithm, byte[] key, byte[] data) {
        try {
            String name = "HmacSHA" + algorithm.substring(2);
            Mac mac = Mac.getInstance(name);
            mac.init(new SecretKeySpec(key, name));
            return mac.doFinal(data);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static byte[] signWithKey(String algorithm, String pem, byte[] data) {
        if (!pem.startsWith(PKCS8_BEGIN)) {
            throw new IllegalArgumentException("\"pkcs8\" must be PKCS#8 formatted string");
        }
        String body = pem.substring(PKCS8_BEGIN.length()).replaceAll("-----END PRIVATE KEY-----|\\s", "");
        String bits = algorithm.substring(2);
        String family = algorithm.substring(0, 2);
        try {
            byte[] der = Base64.getDecoder().decode(body);
            PrivateKey key = privateKey(family, der);
            Signature signer;
            switch (family) {
                case "RS", "PS" -> {
                    if (!(key instanceof RSAPrivateKey rsa)) {
                        throw new IllegalArgumentException(algorithm + " requires an RSA private key");
                    }
                    if (rsa.getModulus().bitLength() < 2048) {
                        throw new IllegalArgumentException(algorithm + " requires key modulusLength to be 2048 bits or larger");
                    }
                    if ("RS".equals(family)) {
                        signer = Signature.getInstance("SHA" + bits + "withRSA");
                    } else {
                        String sha = "SHA-" + bits;
                        signer = Signature.getInstance("RSASSA-PSS");
                        signer.setParameter(new PSSParameterSpec(sha, "MGF1", new MGF1ParameterSpec(sha), Integer.parseInt(bits) / 8, 1));
                    }
                }
                default -> {
                    int fieldSize = "512".equals(bits) ? 521 : Integer.parseInt(bits);
                    if (!(key instanceof ECPrivateKey ec) || ec.getParams().getCurve().getField().getFieldSize() != fieldSize) {
                        throw new IllegalArgumentException(algorithm + " requires a P-" + fieldSize + " private key");
                    }
                    // JOSE wants r || s at fixed length, which the P1363 format is, rather than DER
                    signer = Signature.getInstance("SHA" + bits + "withECDSAinP1363Format");
                }
            }
            signer.initSign(key);
            signer.update(data);
            return signer.sign();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid private key: " + e.getMessage(), e);
        }
    }

    private static PrivateKey privateKey(String family, byte[] der) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        if ("ES".equals(family)) {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            return KeyFactory.getInstance("RSASSA-PSS").generatePrivate(spec);
        }
    }

    /** Node's lenient base64 (Buffer.from(s, 'base64')): url-safe letters accepted, other characters skipped, stops at '='. */
    static byte[] decodeBase64(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '=') {
                break;
            }
            if (c == '-') {
                sb.append('+');
            } else if (c == '_') {
                sb.append('/');
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/') {
                sb.append(c);
            }
        }
        if (sb.length() % 4 == 1) {
            sb.setLength(sb.length() - 1);
        }
        sb.append("==", 0, (4 - sb.length() % 4) % 4);
        return Base64.getDecoder().decode(sb.toString());
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return !s.isEmpty();
        }
        if (o instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return o != null;
    }

    private static String text(Object o) {
        return o == null ? null : o.toString();
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The URL without its {@code key} query params, matched decoded, the other pairs kept as written. */
    private static String withoutParam(String url, String key) {
        int q = url == null ? -1 : url.indexOf('?');
        if (q < 0) {
            return url;
        }
        int hash = url.indexOf('#', q);
        String fragment = hash < 0 ? "" : url.substring(hash);
        String query = hash < 0 ? url.substring(q + 1) : url.substring(q + 1, hash);
        StringBuilder out = new StringBuilder(url.substring(0, q));
        boolean first = true;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String decoded;
            try {
                decoded = URLDecoder.decode(name, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                decoded = name;
            }
            if (pair.isEmpty() || key.equals(decoded)) {
                continue;
            }
            out.append(first ? '?' : '&').append(pair);
            first = false;
        }
        return out.append(fragment).toString();
    }

    // JSON.stringify ---------------------------------------------------------------------------------------------------

    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            quote(s, sb);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Number n) {
            sb.append(number(n.doubleValue()));
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(list.get(i), sb);
            }
            sb.append(']');
        } else {
            quote(value.toString(), sb);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    boolean lone = Character.isHighSurrogate(c)
                            ? i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))
                            : Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || lone) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** A double as JavaScript's Number.prototype.toString writes it. */
    static String number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return "null";
        }
        if (d == 0) {
            return "0";
        }
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int k = digits.length();
        int n = k - shortest.scale(); // the decimal point's position after the first digit
        StringBuilder sb = new StringBuilder(d < 0 ? "-" : "");
        if (k <= n && n <= 21) {
            sb.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            sb.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            sb.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int e = n - 1;
            sb.append(digits.charAt(0));
            if (k > 1) {
                sb.append('.').append(digits, 1, k);
            }
            sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        }
        return sb.toString();
    }

}

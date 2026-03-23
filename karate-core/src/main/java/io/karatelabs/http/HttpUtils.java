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

import io.karatelabs.common.*;
import net.minidev.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public static final byte[] ZERO_BYTES = new byte[0];
    public static final int MEGABYTE = 1024 * 1024;

    public enum Method {

        GET("GET"),
        POST("POST"),
        PUT("PUT"),
        DELETE("DELETE"),
        HEAD("HEAD"),
        OPTIONS("OPTIONS"),
        TRACE("TRACE"),
        CONNECT("CONNECT"),
        PATCH("PATCH");

        public final String key;

        Method(String key) {
            this.key = key;
        }

    }

    public enum Header {

        ACCEPT("Accept"),
        ALLOW("Allow"),
        CACHE_CONTROL("Cache-Control"),
        CONTENT_LENGTH("Content-Length"),
        CONTENT_TYPE("Content-Type"),
        COOKIE("Cookie"),
        LOCATION("Location"),
        SET_COOKIE("Set-Cookie"),
        TRANSFER_ENCODING("Transfer-Encoding");

        public final String key;

        Header(String key) {
            this.key = key;
        }

        public static String[] keys() {
            return Arrays.stream(values()).map(v -> v.key).toArray(String[]::new);
        }

    }

    public static Charset parseContentTypeCharset(String mimeType) {
        Map<String, String> map = parseContentTypeParams(mimeType);
        if (map == null) {
            return null;
        }
        String cs = map.get("charset");
        if (cs == null) {
            return null;
        }
        return Charset.forName(cs);
    }

    public static Map<String, String> parseContentTypeParams(String mimeType) {
        List<String> items = StringUtils.split(mimeType, ';', false);
        int count = items.size();
        if (count <= 1) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>(count - 1);
        for (int i = 1; i < count; i++) {
            String item = items.get(i);
            int pos = item.indexOf('=');
            if (pos == -1) {
                continue;
            }
            String key = item.substring(0, pos).trim();
            String val = item.substring(pos + 1).trim();
            map.put(key, val);
        }
        return map;
    }

    public static Map<String, String> parseUriPattern(String pattern, String url) {
        int qpos = url.indexOf('?');
        if (qpos != -1) {
            url = url.substring(0, qpos);
        }
        List<String> leftList = StringUtils.split(pattern, '/', false);
        List<String> rightList = StringUtils.split(url, '/', false);
        int leftSize = leftList.size();
        int rightSize = rightList.size();
        if (rightSize != leftSize) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>(leftSize);
        for (int i = 0; i < leftSize; i++) {
            String left = leftList.get(i);
            String right = rightList.get(i);
            if (left.equals(right)) {
                continue;
            }
            if (left.startsWith("{") && left.endsWith("}")) {
                left = left.substring(1, left.length() - 1);
                map.put(left, right);
            } else {
                return null; // match failed
            }
        }
        return map;
    }

    public static String normaliseUriPath(String uri) {
        uri = uri.indexOf('?') == -1 ? uri : uri.substring(0, uri.indexOf('?'));
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        return uri;
    }

    public static Pair<String> parseUriIntoUrlBaseAndPath(String rawUri) {
        int pos = rawUri.indexOf('/');
        if (pos == -1) {
            return Pair.of(null, "");
        }
        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (uri.getHost() == null) {
            return Pair.of(null, rawUri);
        }
        String path = uri.getRawPath();
        pos = rawUri.lastIndexOf(path); // edge case that path is just "/"
        String urlBase = rawUri.substring(0, pos);
        return Pair.of(urlBase, rawUri.substring(pos));
    }

    public static Object fromBytes(byte[] bytes, boolean strict, ResourceType resourceType) {
        if (bytes == null) {
            return null;
        }
        // For binary content, return raw bytes directly (V1 compatibility)
        if (resourceType != null && resourceType.isBinary()) {
            return bytes;
        }
        String raw = FileUtils.toString(bytes);
        return fromString(raw, strict, resourceType);
    }

    public static Object fromString(String raw, boolean strict, ResourceType resourceType) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return raw;
        }
        if (resourceType != null && resourceType.isBinary()) {
            return raw;
        }
        switch (trimmed.charAt(0)) {
            case '{':
            case '[':
                if (strict) {
                    return Json.parseStrict(raw);
                }
                try {
                    Object parsed = JSONValue.parseKeepingOrder(raw);
                    // JSONValue returns null for invalid JSON instead of throwing
                    return parsed != null ? parsed : raw;
                } catch (Exception e) {
                    logger.trace("failed to parse json: {}", e.getMessage());
                    return raw;
                }
            case '<':
                // Try XML parsing for null, XML, or TEXT resourceTypes (V1 compatibility)
                // TEXT is included because servers may return XML with text/plain content-type
                if (resourceType == null || resourceType.isXml() || resourceType.isText()) {
                    try {
                        return Xml.toXmlDoc(raw);
                    } catch (Exception e) {
                        logger.trace("failed to parse xml: {}", e.getMessage());
                        if (strict) {
                            throw e;
                        }
                        return raw;
                    }
                } else {
                    return raw;
                }
            default:
                return raw;
        }
    }

    // ========== Query String Utilities ==========

    /**
     * Parse a URI path that may contain query parameters.
     * Returns a Pair where left is the path (without query string) and right is the params map.
     * <p>
     * Examples:
     * - "/test?name=john" -> Pair.of("/test", {name: [john]})
     * - "/test" -> Pair.of("/test", {})
     * - "/api/users?id=1&id=2" -> Pair.of("/api/users", {id: [1, 2]})
     */
    public static Pair<Object> parsePathAndParams(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        Map<String, List<String>> params = decoder.parameters();
        return Pair.of(path, params);
    }

    /**
     * Build a URI with query parameters from a path and params map.
     * <p>
     * Example: buildUri("/test", Map.of("name", List.of("john"))) -> "/test?name=john"
     */
    public static String buildUri(String path, Map<String, List<String>> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        QueryStringEncoder encoder = new QueryStringEncoder(path);
        params.forEach((name, values) -> {
            if (values != null) {
                values.forEach(value -> encoder.addParam(name, value));
            }
        });
        return encoder.toString();
    }

    /**
     * Extract just the path portion from a URI (strips query string).
     */
    public static String extractPath(String uri) {
        int qpos = uri.indexOf('?');
        return qpos == -1 ? uri : uri.substring(0, qpos);
    }

    /**
     * Extract just the query string from a URI (without the leading '?').
     * Returns null if no query string present.
     */
    public static String extractQueryString(String uri) {
        int qpos = uri.indexOf('?');
        return qpos == -1 ? null : uri.substring(qpos + 1);
    }

    /**
     * Parse query string parameters from a URI.
     * Returns empty map if no query string present.
     */
    public static Map<String, List<String>> parseQueryParams(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        return decoder.parameters();
    }

}

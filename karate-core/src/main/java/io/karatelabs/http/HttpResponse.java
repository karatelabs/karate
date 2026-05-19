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
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.ObjectLike;
import net.minidev.json.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse implements ObjectLike {

    private static final Logger logger = LoggerFactory.getLogger(HttpResponse.class);

    private int status = 200;
    private String statusText;
    private Map<String, List<String>> headers;
    private byte[] body;
    private ResourceType resourceType;
    private long startTime;
    private long responseTime;
    private int contentLength;
    private HttpRequest request;
    private int delay;

    public ResourceType getResourceType() {
        if (resourceType == null) {
            String contentType = getContentType();
            if (contentType != null) {
                resourceType = ResourceType.fromContentType(contentType);
            }
        }
        return resourceType;
    }

    public String getContentType() {
        return getHeader(HttpUtils.Header.CONTENT_TYPE.key);
    }

    public String getHeader(String name) {
        List<String> values = getHeaderValues(name);
        return values == null || values.isEmpty() ? null : values.getLast();
    }

    public List<String> getHeaderValues(String name) { // TOTO optimize
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Paired status setter — sets status code and reason text atomically.
     * Equivalent to {@link #setStatus(int)} followed by {@link #setStatusText(String)}.
     */
    public void setStatus(int status, String statusText) {
        this.status = status;
        this.statusText = statusText;
    }

    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setContentType(String contentType) {
        setHeader(HttpUtils.Header.CONTENT_TYPE.key, contentType);
    }

    public void setHeader(String name, List<String> values) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(name, values);
    }

    public HttpRequest getRequest() {
        return request;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setHeader(String name, String... values) {
        setHeader(name, Arrays.asList(values));
    }

    /**
     * Remove a header by name. Case-insensitive match — same convention as
     * {@link #getHeader(String)} / {@link #getHeaderValues(String)}. No-op if
     * the header is absent or no headers have been set yet.
     */
    public void removeHeader(String name) {
        if (headers == null) {
            return;
        }
        // case-insensitive removal — keep symmetry with read paths
        headers.entrySet().removeIf(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name));
    }

    @SuppressWarnings("unchecked")
    public void setHeaders(Map<String, ?> map) {
        if (map == null) {
            return;
        }
        map.forEach((k, v) -> {
            if (v instanceof List) {
                setHeader(k, (List<String>) v);
            } else if (v != null) {
                setHeader(k, v.toString());
            }
        });
    }

    public Object getBody() {
        return body;
    }

    public byte[] getBodyBytes() {
        return Json.toBytes(body);
    }

    public String getBodyString() {
        return FileUtils.toString(Json.toBytes(body));
    }

    /**
     * Canonical body setter. {@code type} drives the Content-Type header
     * (pass {@code null} if the caller will set Content-Type explicitly).
     * For text-based types ({@code text/*}, {@code application/json|xml}, etc.)
     * the default UTF-8 charset is appended — see {@link #applyCharset}.
     * The implicit-type-inference overloads were removed in favour of the
     * static factories ({@link #text}, {@link #json}, {@link #html}, ...).
     */
    public void setBody(byte[] body, ResourceType type) {
        this.body = body;
        this.resourceType = type;
        if (type != null) {
            setContentType(applyCharset(type));
        }
    }

    /**
     * Append the default charset to {@code type.contentType} when the type is
     * text-based. UTF-8 today; a future {@code ServerConfig} hook can override
     * the default (per-server, or via thread-local on the handler chain) without
     * changing the {@link ResourceType} enum — which is also the inbound-parsing
     * key and must stay charset-agnostic.
     */
    private static String applyCharset(ResourceType type) {
        return switch (type) {
            case TEXT, HTML, XML, JSON, JS, CSS -> type.contentType + "; charset=UTF-8";
            default -> type.contentType;
        };
    }

    /**
     * Convenience overload for {@link #setBody(byte[], ResourceType)} that
     * UTF-8-encodes the supplied String. Caller still passes the explicit
     * type — no hidden defaults.
     */
    public void setBody(String body, ResourceType type) {
        setBody(FileUtils.toBytes(body), type);
    }

    public Object getBodyConverted() {
        ResourceType rt = getResourceType(); // derive if needed
        if (rt != null && rt.isBinary()) {
            return body;
        }
        return HttpUtils.fromBytes(body, false, rt);
    }

    public long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * Parse cookies from Set-Cookie headers using Netty's cookie decoder.
     * Returns a map where each cookie name maps to a map with properties like 'value', 'domain', 'path', etc.
     * This matches V1 behavior where cookies are accessed as: responseCookies['name'].value
     */
    public Map<String, Map<String, Object>> getCookies() {
        List<String> setCookieHeaders = getHeaderValues(HttpUtils.Header.SET_COOKIE.key);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Map<String, Object>> cookies = new java.util.LinkedHashMap<>();
        for (String header : setCookieHeaders) {
            io.netty.handler.codec.http.cookie.Cookie cookie =
                    io.netty.handler.codec.http.cookie.ClientCookieDecoder.LAX.decode(header);
            if (cookie != null) {
                cookies.put(cookie.name(), Cookies.toMap(cookie));
            }
        }
        return cookies;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status);
        map.put("headers", headers);
        map.put("body", getBodyConverted());
        map.put("responseTime", responseTime);
        map.put("contentLength", contentLength);
        return map;
    }

    /**
     * Creates a skipped response for when a listener returns false from HTTP_ENTER.
     * The status is set to 0 to indicate the request was not actually made.
     */
    public static HttpResponse skipped(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setRequest(request);
        response.setStatus(0);
        response.setHeaders(java.util.Collections.emptyMap());
        return response;
    }

    /**
     * Returns true if this response represents a skipped request.
     * A request is skipped when a listener returns false from HTTP_ENTER event.
     */
    public boolean isSkipped() {
        return status == 0;
    }

    /**
     * 1-arg form: {@code response.header('X')} — returns the last value (String) or null.
     * 2-arg form: {@code response.header('X', 'v')} — sets the header. {@code null}
     * value removes the header. {@code List} value sets all values.
     */
    private JavaInvokable header() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("missing argument for header()");
            }
            String name = args[0] + "";
            if (args.length == 1) {
                return getHeader(name);
            }
            putHeaderValue(name, args[1]);
            return null;
        };
    }

    private JavaInvokable headerValues() {
        return args -> {
            if (args.length > 0) {
                return getHeaderValues(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for headerValues()");
            }
        };
    }

    /**
     * Coerce a JS value into the header storage shape ({@code List<String>}).
     * Shared by {@code header(name, value)} and the {@code headers[name] = v}
     * bracket-set path.
     */
    @SuppressWarnings("unchecked")
    void putHeaderValue(String name, Object value) {
        if (value == null) {
            removeHeader(name);
            return;
        }
        if (value instanceof List<?> list) {
            List<String> strList = new ArrayList<>(list.size());
            for (Object o : list) {
                strList.add(o == null ? null : o.toString());
            }
            setHeader(name, strList);
            return;
        }
        setHeader(name, value.toString());
    }

    /**
     * ObjectLike view over {@link #headers} for JS-side {@code response.headers[...]}
     * access. Reads return {@code List<String>} matching the historical raw-Map shape
     * (so {@code response.headers['X-Multi']} stays array-typed). Writes route through
     * {@link #putHeaderValue(String, Object)} so {@code String}, {@code List}, and
     * {@code null} all coerce correctly — replacing the silent type-corruption
     * that the old raw-Map exposure caused.
     */
    private ObjectLike headersView() {
        return new ObjectLike() {
            @Override
            public Object getMember(String name) {
                return getHeaderValues(name);
            }

            @Override
            public void putMember(String name, Object value) {
                putHeaderValue(name, value);
            }

            @Override
            public void removeMember(String name) {
                removeHeader(name);
            }

            @Override
            public Map<String, Object> toMap() {
                if (headers == null) {
                    return Collections.emptyMap();
                }
                return new LinkedHashMap<>(headers);
            }
        };
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "status" -> status;
            case "statusText" -> statusText;
            case "startTime" -> startTime;
            case "responseTime" -> responseTime;
            case "headers" -> headersView();
            case "header" -> header();
            case "headerValues" -> headerValues();
            case "body" -> getBodyConverted();
            case "bodyString" -> getBodyString();
            case "bodyBytes" -> Json.toBytes(body);
            case "request" -> request;
            default ->
                // logger.warn("get - unexpected key: {}", key);
                    null;
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putMember(String key, Object value) {
        switch (key) {
            case "body":
                applyJsBody(this, value);
                break;
            case "status":
                status = ((Number) value).intValue();
                break;
            case "headers":
                setHeaders((Map<String, Object>) value);
                break;
            default:
                logger.warn("put - unexpected key: {}", key);
        }
    }

    /**
     * Type-dispatched body assignment for dynamic call sites — mock dispatch
     * and the JS-mock {@code response.body = ...} path. Map/List → JSON,
     * Node → XML, String → TEXT, byte[] → null-type (caller controls
     * Content-Type for raw bytes — historical behaviour for raw bytes).
     * Production code that knows the body type should prefer one of the
     * static factories ({@link #json(Object)}, {@link #text(String)}, etc.).
     */
    public void setBodyDynamic(Object value) {
        applyJsBody(this, value);
    }

    private static void applyJsBody(HttpResponse r, Object value) {
        if (value == null) {
            r.setBody((byte[]) null, null);
        } else if (value instanceof byte[] bytes) {
            r.setBody(bytes, null);
        } else if (value instanceof String s) {
            r.setBody(s, ResourceType.TEXT);
        } else if (value instanceof Node xml) {
            r.setBody(Xml.toString(xml), ResourceType.XML);
        } else if (value instanceof Map<?, ?> || value instanceof List<?>) {
            r.setBody(FileUtils.toBytes(JSONValue.toJSONString(value)), ResourceType.JSON);
        } else {
            throw new RuntimeException("unsupported response body type: " + value);
        }
    }

    // ========== Static factories ==========
    // Build a response in one call. Status + body + Content-Type set atomically
    // — no order trap, no hidden setBody overloads.

    /** {@code 200 OK} with no body. */
    public static HttpResponse ok() {
        HttpResponse r = new HttpResponse();
        r.setStatus(200);
        return r;
    }

    /**
     * {@code 200 OK} with body type inferred (matches the JS-mock dispatch):
     * {@code String→text/plain}, {@code Map/List→application/json},
     * {@code Node→application/xml}, {@code byte[]→no Content-Type}.
     */
    public static HttpResponse ok(Object body) {
        HttpResponse r = ok();
        applyJsBody(r, body);
        return r;
    }

    /** {@code 200 OK} + {@code text/plain}. */
    public static HttpResponse text(String body) {
        return text(200, body);
    }

    /** Status + {@code text/plain}. */
    public static HttpResponse text(int status, String body) {
        HttpResponse r = new HttpResponse();
        r.setStatus(status);
        r.setBody(body, ResourceType.TEXT);
        return r;
    }

    /** {@code 200 OK} + {@code application/json}. */
    public static HttpResponse json(Object body) {
        return json(200, body);
    }

    /**
     * Status + {@code application/json}. {@code String} bodies are treated
     * as already-encoded JSON; everything else goes through {@code Json.toBytes}.
     */
    public static HttpResponse json(int status, Object body) {
        HttpResponse r = new HttpResponse();
        r.setStatus(status);
        if (body == null) {
            r.setBody(new byte[0], ResourceType.JSON);
        } else if (body instanceof String s) {
            r.setBody(s, ResourceType.JSON);
        } else if (body instanceof byte[] bytes) {
            r.setBody(bytes, ResourceType.JSON);
        } else {
            r.setBody(Json.toBytes(body), ResourceType.JSON);
        }
        return r;
    }

    /** {@code 200 OK} + {@code text/html}. */
    public static HttpResponse html(String body) {
        HttpResponse r = new HttpResponse();
        r.setStatus(200);
        r.setBody(body, ResourceType.HTML);
        return r;
    }

    /** {@code 200 OK} + {@code application/xml}. */
    public static HttpResponse xml(String body) {
        HttpResponse r = new HttpResponse();
        r.setStatus(200);
        r.setBody(body, ResourceType.XML);
        return r;
    }

    /** {@code 200 OK} + {@code application/octet-stream}. */
    public static HttpResponse binary(byte[] body) {
        HttpResponse r = new HttpResponse();
        r.setStatus(200);
        r.setBody(body, ResourceType.BINARY);
        return r;
    }

    /** {@code 204 No Content}. */
    public static HttpResponse noContent() {
        HttpResponse r = new HttpResponse();
        r.setStatus(204);
        return r;
    }

    /** {@code 302 Found} + {@code Location} header. */
    public static HttpResponse redirect(String location) {
        return redirect(302, location);
    }

    /** Custom redirect status (301 / 303 / 307 / 308) + {@code Location} header. */
    public static HttpResponse redirect(int status, String location) {
        HttpResponse r = new HttpResponse();
        r.setStatus(status);
        r.setHeader(HttpUtils.Header.LOCATION.key, location);
        return r;
    }

    /** Status + JSON body {@code {"error": message}}. {@code null} message becomes {@code ""}. */
    public static HttpResponse error(int status, String message) {
        HttpResponse r = new HttpResponse();
        r.setStatus(status);
        r.setBody(Json.toBytes(Map.of("error", message == null ? "" : message)), ResourceType.JSON);
        return r;
    }

    /** Shortcut for {@code error(400, message)}. */
    public static HttpResponse badRequest(String message) {
        return error(400, message);
    }

    /** Shortcut for {@code error(404, message)}. */
    public static HttpResponse notFound(String message) {
        return error(404, message);
    }

    @Override
    public void removeMember(String name) {
        logger.warn("remove() not implemented");
    }

}

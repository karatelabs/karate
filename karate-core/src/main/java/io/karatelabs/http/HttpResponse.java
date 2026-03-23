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

import java.util.Arrays;
import java.util.HashMap;
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

    public void setBody(byte[] body, ResourceType resourceType) {
        this.body = body;
        this.resourceType = resourceType;
        if (resourceType != null) {
            setContentType(resourceType.contentType);
        }
    }

    public void setBody(String body) {
        setBody(FileUtils.toBytes(body), ResourceType.TEXT);
    }

    public void setBody(Map<String, Object> body) {
        setBody(Json.toBytes(body), ResourceType.JSON);
    }

    public void setBody(List<Object> body) {
        setBody(Json.toBytes(body), ResourceType.JSON);
    }

    public void setBodyJson(String body) {
        setBody(FileUtils.toBytes(body), ResourceType.JSON);
    }

    public void setBodyXml(String body) {
        setBody(FileUtils.toBytes(body), ResourceType.XML);
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

    private JavaInvokable header() {
        return args -> {
            if (args.length > 0) {
                return getHeader(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for header()");
            }
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

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "status" -> status;
            case "statusText" -> statusText;
            case "startTime" -> startTime;
            case "responseTime" -> responseTime;
            case "headers" -> headers;
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
                if (value instanceof Map || value instanceof List) {
                    setBody(FileUtils.toBytes(JSONValue.toJSONString(value)), ResourceType.JSON);
                } else if (value instanceof Node xml) {
                    setBody(FileUtils.toBytes(Xml.toString(xml)), ResourceType.XML);
                } else if (value instanceof String s) {
                    setBody(s);
                } else if (value instanceof byte[] bytes) {
                    setBody(bytes, null);
                } else {
                    throw new RuntimeException("unsupported response body type: " + value);
                }
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

    @Override
    public void removeMember(String name) {
        logger.warn("remove() not implemented");
    }

}

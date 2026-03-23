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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Json;
import io.karatelabs.common.Pair;
import io.karatelabs.common.ResourceType;
import io.karatelabs.common.StringUtils;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.*;

public class HttpRequest implements SimpleObject {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequest.class);

    private String method;
    private String urlAndPath;
    private String urlBase;
    private String path;
    private String pathOriginal;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private byte[] body;
    private String bodyDisplay;
    private ResourceType resourceType;
    private Map<String, String> pathParams;
    private String pathPattern;
    private Map<String, List<Map<String, Object>>> multiParts;

    public void setUrl(String url) {
        urlAndPath = url;
        Pair<String> pair = HttpUtils.parseUriIntoUrlBaseAndPath(url);
        urlBase = pair.left;
        QueryStringDecoder qsd = new QueryStringDecoder(pair.right);
        setPath(qsd.path());
        setParams(qsd.parameters());
    }

    public String getUrlAndPath() {
        return urlAndPath != null ? urlAndPath : (urlBase != null ? urlBase : "") + path;
    }

    public void setPath(String path) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.charAt(0) != '/') { // mocks and synthetic situations
            path = "/" + path;
        }
        this.path = path;
        if (pathOriginal == null) {
            pathOriginal = path;
        }
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }

    public void setParams(Map<String, List<String>> params) {
        this.params = params;
    }

    public Map<String, List<String>> getParams() {
        return params;
    }

    public String getPath() {
        return path;
    }

    public String getPathRaw() {
        if (urlBase != null && urlAndPath != null) {
            if (urlAndPath.charAt(0) == '/') {
                return urlAndPath;
            } else {
                return urlAndPath.substring(urlBase.length());
            }
        } else {
            return path;
        }
    }

    public boolean pathMatches(String pattern) {
        Map<String, String> temp = HttpUtils.parseUriPattern(pattern, path);
        if (temp == null) {
            return false;
        }
        pathParams = temp;
        pathPattern = pattern;
        return true;
    }

    public Map<String, String> getPathParams() {
        return pathParams == null ? Collections.emptyMap() : pathParams;
    }

    public void putHeader(String name, String... values) {
        putHeader(name, Arrays.asList(values));
    }

    public void putHeader(String name, List<String> values) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        headers.put(name, values);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method.toUpperCase();
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyString() {
        return FileUtils.toString(body);
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getBodyDisplay() {
        return bodyDisplay;
    }

    public void setBodyDisplay(String bodyDisplay) {
        this.bodyDisplay = bodyDisplay;
    }

    public List<String> getHeaderValues(String name) { // TOTO optimize
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public List<String> getParamValues(String name) {
        return params == null ? null : params.get(name);
    }

    public String getParam(String name) {
        List<String> values = getParamValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    public void removeHeader(String name) {
        if (headers == null) {
            return;
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        headers.remove(name);
    }

    public String getHeader(String name) {
        List<String> values = getHeaderValues(name);
        return values == null || values.isEmpty() ? null : values.getLast();
    }

    public String getContentType() {
        return getHeader(HttpUtils.Header.CONTENT_TYPE.key);
    }

    /**
     * Parse cookies from the Cookie header(s).
     * Returns a map where each cookie name maps to a map with 'name' and 'value' keys.
     * This matches V1 behavior where cookies are accessed as: requestCookies['name'].value
     * Handles both single Cookie header with semicolon-separated values, and multiple Cookie headers.
     */
    public Map<String, Map<String, String>> getCookies() {
        List<String> cookieHeaders = getHeaderValues("Cookie");
        if (cookieHeaders == null || cookieHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, String>> cookies = new LinkedHashMap<>();
        for (String cookieHeader : cookieHeaders) {
            for (String part : cookieHeader.split(";")) {
                String trimmed = part.trim();
                int eqPos = trimmed.indexOf('=');
                if (eqPos > 0) {
                    String name = trimmed.substring(0, eqPos).trim();
                    String value = trimmed.substring(eqPos + 1).trim();
                    Map<String, String> cookie = new LinkedHashMap<>();
                    cookie.put("name", name);
                    cookie.put("value", value);
                    cookies.put(name, cookie);
                }
            }
        }
        return cookies;
    }

    public void setContentType(String contentType) {
        putHeader(HttpUtils.Header.CONTENT_TYPE.key, contentType);
    }

    public ResourceType getResourceType() {
        if (resourceType == null) {
            String contentType = getContentType();
            if (contentType != null) {
                resourceType = ResourceType.fromContentType(contentType);
            }
        }
        return resourceType;
    }

    /**
     * Returns true if this is an HTMX/AJAX request (has HX-Request header).
     * Used to determine if a partial or full page render is needed.
     */
    public boolean isAjax() {
        return getHeader("HX-Request") != null;
    }

    /**
     * Returns true if this request has multipart form data.
     */
    public boolean isMultiPart() {
        return multiParts != null;
    }

    /**
     * Get all multipart data.
     */
    public Map<String, List<Map<String, Object>>> getMultiParts() {
        return multiParts;
    }

    /**
     * Get a single multipart field by name.
     * Returns the first part with the given name, or null if not found.
     */
    public Map<String, Object> getMultiPart(String name) {
        if (multiParts == null) {
            return null;
        }
        List<Map<String, Object>> parts = multiParts.get(name);
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        return parts.get(0);
    }

    /**
     * Process the request body for form-urlencoded or multipart data.
     * For form-urlencoded, fields are merged into params.
     * For multipart, file uploads are stored in multiParts and fields in params.
     */
    public void processBody() {
        if (body == null) {
            return;
        }
        String contentType = getContentType();
        if (contentType == null) {
            return;
        }
        boolean multipart;
        if (contentType.startsWith("multipart")) {
            multipart = true;
            multiParts = new HashMap<>();
        } else if (contentType.contains("form-urlencoded")) {
            multipart = false;
        } else {
            return;
        }
        logger.trace("decoding content-type: {}", contentType);
        // Make params mutable if needed
        params = (params == null || params.isEmpty()) ? new HashMap<>() : new HashMap<>(params);
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path, Unpooled.wrappedBuffer(body));
        nettyRequest.headers().add("Content-Type", contentType);
        InterfaceHttpPostRequestDecoder decoder = multipart
                ? new HttpPostMultipartRequestDecoder(nettyRequest)
                : new HttpPostStandardRequestDecoder(nettyRequest);
        try {
            for (InterfaceHttpData part : decoder.getBodyHttpDatas()) {
                String name = part.getName();
                if (multipart && part instanceof FileUpload fup) {
                    String filename = fup.getFilename();
                    // Empty filename means it's a form field, not a file upload
                    if (filename == null || filename.isEmpty()) {
                        List<String> list = params.computeIfAbsent(name, k -> new ArrayList<>());
                        list.add(fup.getString(fup.getCharset()));
                    } else {
                        List<Map<String, Object>> list = multiParts.computeIfAbsent(name, k -> new ArrayList<>());
                        Map<String, Object> map = new HashMap<>();
                        list.add(map);
                        map.put("name", name);
                        map.put("filename", filename);
                        Charset charset = fup.getCharset();
                        if (charset != null) {
                            map.put("charset", charset.name());
                        }
                        map.put("contentType", fup.getContentType());
                        map.put("value", fup.get()); // bytes
                        String transferEncoding = fup.getContentTransferEncoding();
                        if (transferEncoding != null) {
                            map.put("transferEncoding", transferEncoding);
                        }
                    }
                } else {
                    // form-field, url-encoded if not multipart
                    Attribute attribute = (Attribute) part;
                    List<String> list = params.computeIfAbsent(name, k -> new ArrayList<>());
                    list.add(attribute.getValue());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            decoder.destroy();
        }
    }

    public Object getBodyConverted() {
        ResourceType rt = getResourceType(); // derive if needed
        if (rt != null && rt.isBinary()) {
            return body;
        }
        return HttpUtils.fromBytes(body, false, rt);
    }

    public Map<String, Object> toBlockData() {
        Map<String, Object> request = new HashMap<>();
        request.put("method", method);
        request.put("url", urlBase);
        if (path != null) {
            request.put("path", path);
        }
        List<Map<String, Object>> paramsList = new ArrayList<>();
        request.put("params", paramsList);
        if (params != null) {
            for (String name : params.keySet()) {
                List<String> values = params.get(name);
                if (values == null || values.isEmpty()) {
                    continue;
                }
                Map<String, Object> map = new HashMap<>();
                paramsList.add(map);
                map.put("name", name);
                map.put("value", String.join(",", values));
            }
        }
        List<Map<String, Object>> headersList = new ArrayList<>();
        request.put("headers", headersList);
        if (headers != null) {
            for (String name : headers.keySet()) {
                List<String> values = headers.get(name);
                if (values == null || values.isEmpty()) {
                    continue;
                }
                Map<String, Object> map = new HashMap<>();
                headersList.add(map);
                map.put("name", name);
                map.put("value", String.join(",", values));
            }
        }
        if (body != null) {
            request.put("body", getBodyConverted());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("request", request);
        return data;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("url", urlAndPath);
        map.put("urlBase", urlBase);
        map.put("path", path);
        map.put("pathRaw", getPathRaw());
        map.put("method", method);
        map.put("headers", headers);
        map.put("params", params);
        map.put("body", getBodyConverted());
        return map;
    }

    @Override
    public String toString() {
        return method + " " + urlAndPath;
    }

    public HttpRequestBuilder toHttpRequestBuilder(HttpClient client) {
        HttpRequestBuilder builder = new HttpRequestBuilder(client);
        builder.method(method);
        if (urlBase != null) {
            builder.url(urlBase);
        }
        if (path != null) {
            builder.path(path);
        }
        if (params != null) {
            builder.params(params);
        }
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    builder.header(name, values);
                }
            });
        }
        if (body != null) {
            builder.body(getBodyConverted());
        }
        return builder;
    }

    private JavaInvokable param() {
        return args -> {
            if (args.length > 0) {
                return getParam(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for param()");
            }
        };
    }

    private JavaInvokable paramValues() {
        return args -> {
            if (args.length > 0) {
                return getParamValues(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for paramValues()");
            }
        };
    }

    private JavaInvokable paramInt() {
        return args -> {
            if (args.length > 0) {
                String val = getParam(args[0] + "");
                return val == null ? null : Integer.parseInt(val);
            } else {
                throw new RuntimeException("missing argument for paramInt()");
            }
        };
    }

    private JavaInvokable paramJson() {
        return args -> {
            if (args.length > 0) {
                String val = getParam(args[0] + "");
                if (val == null) {
                    return null;
                }
                try {
                    return Json.of(val).value();
                } catch (Exception e) {
                    return val; // return raw string if not valid JSON
                }
            } else {
                throw new RuntimeException("missing argument for paramJson()");
            }
        };
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

    private JavaInvokable pathMatches() {
        return args -> {
            if (args.length > 0) {
                return pathMatches(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for pathMatches()");
            }
        };
    }

    private JavaInvokable multiPart() {
        return args -> {
            if (args.length > 0) {
                return getMultiPart(args[0] + "");
            } else {
                throw new RuntimeException("missing argument for multiPart()");
            }
        };
    }

    @Override
    public Object jsGet(String key) {
        switch (key) {
            case "method":
                return method;
            case "body":
                return getBodyConverted();
            case "bodyString":
                return getBodyString();
            case "bodyBytes":
                return body;
            case "url":
                return urlAndPath;
            case "urlBase":
                return urlBase;
            case "path":
                return path;
            case "pathRaw":
                return getPathRaw();
            case "params":
                return StringUtils.simplify(params, true);
            case "param":
                return param();
            case "paramInt":
                return paramInt();
            case "paramJson":
                return paramJson();
            case "paramValues":
                return paramValues();
            case "headers":
                return headers;
            case "header":
                return header();
            case "headerValues":
                return headerValues();
            case "pathMatches":
                return pathMatches();
            case "pathParams":
                return getPathParams();
            case "multiPart":
                return multiPart();
            case "multiParts":
                return multiParts;
            case "get":
            case "post":
            case "put":
            case "delete":
            case "patch":
            case "head":
            case "options":
            case "trace":
                return method.toLowerCase().equals(key);
            case "ajax":
                return isAjax();
            default:
                logger.warn("get - unexpected key: {}", key);
        }
        return null;
    }

}

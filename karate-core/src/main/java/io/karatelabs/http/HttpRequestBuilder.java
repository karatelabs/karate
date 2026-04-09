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
import io.karatelabs.common.ResourceType;
import io.karatelabs.common.StringUtils;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class HttpRequestBuilder implements SimpleObject {

    private String url;
    private String method;
    private List<String> paths;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private MultiPartBuilder multiPart;
    private Object body;
    private Set<Cookie> cookies;
    private String charset;
    private AuthHandler authHandler;
    private String retryUntil;

    private final HttpClient client;

    public HttpRequestBuilder(HttpClient client) {
        this.client = client;
    }

    public void reset() {
        // url = null;
        method = null;
        paths = null;
        params = null;
        headers = null;
        multiPart = null;
        body = null;
        cookies = null;
        charset = null;
        authHandler = null;
        retryUntil = null;
    }

    public HttpRequestBuilder copy() {
        HttpRequestBuilder hrb = new HttpRequestBuilder(client);
        hrb.url = url;
        hrb.method = method;
        hrb.paths = paths;
        hrb.params = params;
        hrb.headers = headers;
        hrb.multiPart = multiPart;
        hrb.body = body;
        hrb.cookies = cookies;
        hrb.charset = charset;
        hrb.authHandler = authHandler;
        hrb.retryUntil = retryUntil;
        return hrb;
    }

    public void restoreFrom(HttpRequestBuilder source) {
        this.url = source.url;
        this.method = source.method;
        this.paths = source.paths;
        this.params = source.params;
        this.headers = source.headers;
        this.multiPart = source.multiPart;
        this.body = source.body;
        this.cookies = source.cookies;
        this.charset = source.charset;
        this.authHandler = source.authHandler;
        this.retryUntil = source.retryUntil;
    }

    public HttpRequest build() {
        buildInternal();
        HttpRequest request = new HttpRequest();
        request.setMethod(method);
        request.setUrl(getUri());
        if (multiPart != null) {
            request.setBodyDisplay(multiPart.getBodyForDisplay());
        }
        if (body != null) {
            request.setBody(Json.toBytes(body));
        }
        request.setHeaders(headers);
        // Also set params on request so InMemoryHttpClient and others can access them
        if (params != null) {
            request.setParams(params);
        }
        return request;
    }

    public HttpResponse invoke() {
        HttpRequest request = build();
        if (client == null) {
            throw new RuntimeException("http client not set");
        }
        reset();
        HttpResponse response = client.invoke(request);
        response.setRequest(request);
        return response;
    }

    public HttpResponse invoke(String method) {
        this.method = method;
        return invoke();
    }

    public HttpResponse invoke(String method, Object body) {
        this.method = method;
        this.body = body;
        return invoke();
    }

    public HttpRequestBuilder charset(String charset) {
        this.charset = charset;
        return this;
    }

    public HttpRequestBuilder url(String value) {
        url = value;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public HttpRequestBuilder method(String method) {
        this.method = method;
        return this;
    }

    public HttpRequestBuilder paths(String... paths) {
        for (String path : paths) {
            path(path);
        }
        return this;
    }

    public HttpRequestBuilder path(String path) {
        if (path == null) {
            return this;
        }
        if (paths == null) {
            paths = new ArrayList<>();
        }
        paths.add(path);
        return this;
    }

    public List<String> getPaths() {
        return paths;
    }

    public Object getBody() {
        return body;
    }

    public HttpRequestBuilder body(Object body) {
        this.body = body;
        return this;
    }

    public HttpRequestBuilder bodyJson(String json) {
        this.body = Json.of(json).value();
        return this;
    }

    public HttpClient getClient() {
        return client;
    }

    public HttpRequestBuilder forkNewBuilder() {
        return new HttpRequestBuilder(client);
    }

    public Map<String, String> getHeaders() {
        if (headers == null) {
            return new LinkedHashMap<>(0);
        }
        Map<String, String> map = new LinkedHashMap<>(headers.size());
        headers.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                Object value = v.getFirst();
                if (value != null) {
                    map.put(k, value.toString());
                }
            }
        });
        return map;
    }

    public List<String> getHeaderValues(String name) {
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public String getHeader(String name) {
        List<String> list = getHeaderValues(name);
        if (list == null || list.isEmpty()) {
            return null;
        } else {
            return list.getFirst();
        }
    }

    public String getContentType() {
        return getHeader(HttpUtils.Header.CONTENT_TYPE.key);
    }

    public HttpRequestBuilder removeHeader(String name) {
        if (headers != null) {
            StringUtils.removeIgnoreKeyCase(headers, name);
        }
        return this;
    }

    public HttpRequestBuilder header(String name, String... values) {
        return header(name, Arrays.asList(values));
    }

    public HttpRequestBuilder header(String name, List<String> values) {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                name = key;
                break;
            }
        }
        headers.put(name, values);
        return this;
    }

    public HttpRequestBuilder header(String name, String value) {
        return header(name, Collections.singletonList(value));
    }

    @SuppressWarnings("unchecked")
    public HttpRequestBuilder headers(Map<String, Object> map) {
        map.forEach((k, v) -> {
            if (v instanceof List) {
                header(k, (List<String>) v);
            } else if (v != null) {
                header(k, v.toString());
            }
        });
        return this;
    }

    public HttpRequestBuilder contentType(String contentType) {
        if (contentType != null) {
            header(HttpUtils.Header.CONTENT_TYPE.key, contentType);
        }
        return this;
    }

    public List<String> getParam(String name) {
        if (params == null || name == null) {
            return null;
        }
        return params.get(name);
    }

    public HttpRequestBuilder param(String name, String... values) {
        return param(name, Arrays.asList(values));
    }

    public HttpRequestBuilder param(String name, List<String> values) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        List<String> notNullValues = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (!notNullValues.isEmpty()) {
            // Accumulate values for the same param name (like v1)
            List<String> existing = params.get(name);
            if (existing == null) {
                params.put(name, new ArrayList<>(notNullValues));
            } else {
                existing.addAll(notNullValues);
            }
        }
        return this;
    }

    public HttpRequestBuilder params(Map<String, List<String>> params) {
        this.params = params;
        return this;
    }

    public HttpRequestBuilder cookies(Collection<Map<String, Object>> cookies) {
        for (Map<String, Object> map : cookies) {
            cookie(map);
        }
        return this;
    }

    public HttpRequestBuilder cookie(Map<String, Object> map) {
        return cookie(Cookies.fromMap(map));
    }

    public HttpRequestBuilder cookie(Cookie cookie) {
        if (cookies == null) {
            cookies = new HashSet<>();
        }
        cookies.add(cookie);
        return this;
    }

    public HttpRequestBuilder cookie(String name, String value) {
        return cookie(new DefaultCookie(name, value));
    }

    public HttpRequestBuilder formField(String name, Object value) {
        if (multiPart == null) {
            multiPart = new MultiPartBuilder(false, charset);
        }
        multiPart.part(name, value);
        return this;
    }

    public HttpRequestBuilder multiPartJson(String json) {
        return multiPart(Json.of(json).value());
    }

    public HttpRequestBuilder multiPart(Map<String, Object> map) {
        if (multiPart == null) {
            multiPart = new MultiPartBuilder(true, charset);
        }
        multiPart.part(map);
        return this;
    }

    public HttpRequestBuilder auth(AuthHandler authHandler) {
        this.authHandler = authHandler;
        return this;
    }

    public HttpRequestBuilder retryUntil(String condition) {
        this.retryUntil = condition;
        return this;
    }

    public String getRetryUntil() {
        return retryUntil;
    }

    public boolean isRetry() {
        return retryUntil != null;
    }

    public String getUri() {
        try {
            URIBuilder builder;
            if (url == null) {
                builder = new URIBuilder();
            } else {
                builder = new URIBuilder(url);
            }
            if (params != null) {
                params.forEach((key, values) -> values.forEach(value -> builder.addParameter(key, value)));
            }
            if (paths != null) {
                List<String> segments = new ArrayList<>();
                for (String item : builder.getPathSegments()) {
                    if (!item.isEmpty()) {
                        segments.add(item);
                    }
                }
                Iterator<String> pathIterator = paths.iterator();
                while (pathIterator.hasNext()) {
                    String item = pathIterator.next();
                    if (!pathIterator.hasNext() && "/".equals(item)) { // preserve trailing slash
                        segments.add("");
                    } else {
                        segments.addAll(StringUtils.split(item, '/', true));
                    }
                }
                builder.setPathSegments(segments);
            }
            URI uri = builder.build();
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void buildInternal() {
        if (url == null) {
            throw new RuntimeException("incomplete http request, 'url' not set");
        }
        if (method == null) {
            if (multiPart != null && multiPart.isMultipart()) {
                method = "POST";
            } else {
                method = "GET";
            }
        }
        method = method.toUpperCase();
        if ("GET".equals(method) && multiPart != null) {
            Map<String, Object> parts = multiPart.getFormFields();
            if (parts != null) {
                parts.forEach((k, v) -> param(k, (String) v));
            }
            multiPart = null;
        }
        if (multiPart != null) {
            if (body == null) { // this is not-null only for a re-try, don't rebuild multi-part
                body = multiPart.build();
                String userContentType = getHeader(HttpUtils.Header.CONTENT_TYPE.key);
                if (userContentType != null) {
                    String boundary = multiPart.getBoundary();
                    if (boundary != null) {
                        contentType(userContentType + "; boundary=" + boundary);
                    }
                } else {
                    contentType(multiPart.getContentTypeHeader());
                }
            }
        }
        if (cookies != null && !cookies.isEmpty()) {
            List<String> cookieValues = new ArrayList<>(cookies.size());
            for (Cookie c : cookies) {
                String cookieValue = ClientCookieEncoder.LAX.encode(c);
                cookieValues.add(cookieValue);
            }
            header(HttpUtils.Header.COOKIE.key, StringUtils.join(cookieValues, "; "));
        }
        if (body != null) {
            if (multiPart == null) {
                String contentType = getContentType();
                if (contentType == null) {
                    ResourceType rt = ResourceType.fromObject(body);
                    if (rt != null) {
                        contentType = rt.contentType;
                    }
                }
                Charset cs = contentType == null ? null : HttpUtils.parseContentTypeCharset(contentType);
                if (cs == null) {
                    if (charset != null) {
                        // edge case, support setting content type to an empty string
                        contentType = StringUtils.trimToNull(contentType);
                        if (contentType != null) {
                            contentType = contentType + "; charset=" + charset;
                        }
                    }
                }
                contentType(contentType);
            }
        }
        if (authHandler != null) {
            authHandler.apply(this);
        }
    }

    private static final List<String> CURL_IGNORED_HEADERS = Arrays.asList(
            "accept-encoding",
            "connection",
            "host",
            "user-agent",
            "content-length"
    );

    /**
     * URL encodes a string for use in form data
     */
    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    public String toCurlCommand() {
        return toCurlCommand("sh");
    }

    public String toCurlCommand(String platform) {
        buildInternal();
        return buildCurlCommand(false, platform);
    }

    /**
     * Generate curl command preview without side effects (no network calls).
     * Useful for UI display where OAuth tokens would show placeholders.
     */
    public String toCurlCommandPreview() {
        return toCurlCommandPreview("sh");
    }

    public String toCurlCommandPreview(String platform) {
        return buildCurlCommand(true, platform);
    }

    private String buildCurlCommand(boolean preview, String platform) {
        if (platform == null) {
            platform = "sh";
        }
        final String effectivePlatform = platform;
        String lineContinuation = StringUtils.getLineContinuation(effectivePlatform);

        StringBuilder sb = new StringBuilder();
        sb.append("curl");

        // Determine method - use provided or default
        String curlMethod = method;
        if (curlMethod == null) {
            if (multiPart != null && multiPart.isMultipart()) {
                curlMethod = "POST";
            } else {
                curlMethod = "GET";
            }
        }
        sb.append(" -X ").append(curlMethod.toUpperCase());

        // Add URL
        String url = getUri();
        if (!StringUtils.isBlank(url)) {
            sb.append(lineContinuation).append("  ").append(StringUtils.shellEscapeForPlatform(url, effectivePlatform));
        }

        // Add auth if present
        final String authArgument;
        if (authHandler != null) {
            if (preview) {
                // Preview mode - use toCurlPreview() to avoid side effects
                authArgument = authHandler.toCurlPreview(effectivePlatform);
                if (authArgument != null) {
                    sb.append(lineContinuation).append("  ").append(authArgument);
                } else {
                    // Auth handler wants to use Authorization header
                    // For preview, apply it unless it requires network
                    if (!(authHandler instanceof ClientCredentialsAuthHandler)) {
                        // Safe to apply - doesn't need network
                        authHandler.apply(this);
                    }
                }
            } else {
                // Normal mode - use toCurlArgument()
                authArgument = authHandler.toCurlArgument(effectivePlatform);
                if (authArgument != null) {
                    sb.append(lineContinuation).append("  ").append(authArgument);
                }
            }
        } else {
            authArgument = null;
        }

        // Add headers
        if (headers != null && !headers.isEmpty()) {
            headers.forEach((name, values) -> {
                // Skip Authorization header if auth handler provided a curl argument
                boolean skipAuth = authArgument != null && "authorization".equals(name.toLowerCase());
                if (!skipAuth && !CURL_IGNORED_HEADERS.contains(name.toLowerCase())) {
                    if (values != null && !values.isEmpty()) {
                        values.forEach(value -> {
                            if (value != null) {
                                sb.append(lineContinuation).append("  ");
                                sb.append("-H ").append(StringUtils.shellEscapeForPlatform(name + ": " + value, effectivePlatform));
                            }
                        });
                    }
                }
            });
        }

        // In preview mode with OAuth that needs network, add placeholder header
        if (preview && authHandler != null && authArgument == null) {
            // Check if it's ClientCredentialsAuthHandler
            if (authHandler instanceof ClientCredentialsAuthHandler) {
                sb.append(lineContinuation).append("  ");
                sb.append("-H ").append(StringUtils.shellEscapeForPlatform("Authorization: Bearer <your-oauth2-access-token>", effectivePlatform));
            }
        }

        // Add body/data based on content type
        if (multiPart != null) {
            // Multipart form data - delegate to MultiPartBuilder
            String multiPartCommand = multiPart.toCurlCommand(effectivePlatform);
            if (!multiPartCommand.isEmpty()) {
                sb.append(lineContinuation).append("  ");
                // Replace line continuations in multipart command with platform-specific ones
                String shLineCont = StringUtils.getLineContinuation("sh");
                sb.append(multiPartCommand.replace(shLineCont, lineContinuation + "  "));
            }
        } else if (body != null) {
            // Handle body based on content type
            String contentType = getContentType();
            sb.append(lineContinuation).append("  ");

            // Check if body is binary data (byte array)
            if (body instanceof byte[]) {
                // For binary data, we can't easily represent it in curl without a file
                // Best we can do is indicate it's binary
                sb.append("-d ").append(StringUtils.shellEscapeForPlatform("[binary data]", effectivePlatform));
            } else {
                // For JSON or text, serialize and escape properly
                String bodyStr;
                if (body instanceof String) {
                    bodyStr = (String) body;
                } else {
                    bodyStr = Json.stringifyStrict(body);
                }
                sb.append("-d ").append(StringUtils.shellEscapeForPlatform(bodyStr, effectivePlatform));
            }
        } else if (params != null && !params.isEmpty() && !"GET".equals(curlMethod)) {
            // For non-GET requests with parameters but no body, treat as form data
            String contentType = getContentType();
            boolean isUrlEncoded = contentType != null &&
                contentType.toLowerCase().contains("application/x-www-form-urlencoded");

            sb.append(lineContinuation).append("  ");
            Iterator<Map.Entry<String, List<String>>> paramsIterator = params.entrySet().iterator();
            while (paramsIterator.hasNext()) {
                Map.Entry<String, List<String>> entry = paramsIterator.next();
                String name = entry.getKey();
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    Iterator<String> valuesIterator = values.iterator();
                    while (valuesIterator.hasNext()) {
                        String value = valuesIterator.next();
                        if (value != null) {
                            if (isUrlEncoded) {
                                sb.append("--data-urlencode ").append(StringUtils.shellEscapeForPlatform(name + "=" + value, effectivePlatform));
                            } else {
                                // Default to form data
                                sb.append("-d ").append(StringUtils.shellEscapeForPlatform(urlEncode(name) + "=" + urlEncode(value), effectivePlatform));
                            }
                            if (valuesIterator.hasNext() || paramsIterator.hasNext()) {
                                sb.append(lineContinuation).append("  ");
                            }
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    private JavaInvokable method() {
        return args -> {
            if (args.length > 1) {
                body = args[1];
            }
            if (args.length > 0) {
                method = args[0] + "";
            }
            return invoke();
        };
    }

    private JavaInvokable header() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("header() needs two arguments");
            }
            header(args[0] + "", args[1] + "");
            return this;
        };
    }

    private JavaInvokable param() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("param() needs two arguments");
            }
            param(args[0] + "", args[1] + "");
            return this;
        };
    }

    private JavaInvokable path() {
        return args -> {
            for (Object arg : args) {
                if (arg != null) {
                    path(arg + "");
                }
            }
            return this;
        };
    }

    private JavaInvokable body() {
        return args -> {
            if (args.length == 0) {
                throw new RuntimeException("body() needs at least one argument");
            }
            body(args[0]);
            return this;
        };
    }

    @Override
    public Object jsGet(String key) {
        switch (key) {
            case "get":
            case "post":
            case "put":
            case "delete":
            case "head":
            case "options":
            case "trace":
            case "connect":
            case "patch":
                return (JavaInvokable) args -> args.length > 0 ? invoke(key, args[0]) : invoke(key);
            case "method":
                return method();
            case "header":
                return header();
            case "param":
                return param();
            case "path":
                return path();
            case "body":
                return body();
        }
        System.err.println("http-request-builder no such key: " + key);
        return null;
    }

}

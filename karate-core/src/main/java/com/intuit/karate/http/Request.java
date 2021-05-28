/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsArray;
import com.intuit.karate.graal.JsValue;
import com.linecorp.armeria.common.RequestContext;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.toList;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Request implements ProxyObject {

    private static final Logger logger = LoggerFactory.getLogger(Request.class);

    private static final String PATH = "path";
    private static final String METHOD = "method";
    private static final String PARAM = "param";
    private static final String PARAMS = "params";
    private static final String HEADER = "header";
    private static final String HEADERS = "headers";
    private static final String PATH_PARAM = "pathParam";
    private static final String PATH_PARAMS = "pathParams";
    private static final String BODY = "body";
    private static final String MULTI_PART = "multiPart";
    private static final String MULTI_PARTS = "multiParts";
    private static final String JSON = "json";
    private static final String AJAX = "ajax";
    private static final String GET = "get";
    private static final String POST = "post";
    private static final String PUT = "put";
    private static final String DELETE = "delete";
    private static final String PATCH = "patch";
    private static final String HEAD = "head";
    private static final String CONNECT = "connect";
    private static final String OPTIONS = "options";
    private static final String TRACE = "trace";

    private static final String[] KEYS = new String[]{
        PATH, METHOD, PARAM, PARAMS, HEADER, HEADERS, PATH_PARAM, PATH_PARAMS, BODY, MULTI_PART, MULTI_PARTS, JSON, AJAX,
        GET, POST, PUT, DELETE, PATCH, HEAD, CONNECT, OPTIONS, TRACE
    };
    private static final Set<String> KEY_SET = new HashSet<>(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private String urlAndPath;
    private String urlBase;
    private String path;
    private String method;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private byte[] body;
    private Map<String, List<Map<String, Object>>> multiParts;
    private ResourceType resourceType;
    private String resourcePath;
    private String pathParam;
    private List pathParams = Collections.emptyList();
    private RequestContext requestContext;

    public RequestContext getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public boolean isAjax() {
        return getHeader(HttpConstants.HDR_HX_REQUEST) != null;
    }

    public boolean isMultiPart() {
        return multiParts != null;
    }

    public Map<String, List<Map<String, Object>>> getMultiParts() {
        return multiParts;
    }

    public List<String> getHeaderValues(String name) {
        return StringUtils.getIgnoreKeyCase(headers, name); // TODO optimize
    }

    public String getHeader(String name) {
        List<String> list = getHeaderValues(name);
        if (list == null || list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    public String getContentType() {
        return getHeader(HttpConstants.HDR_CONTENT_TYPE);
    }

    public List<Cookie> getCookies() {
        List<String> cookieValues = getHeaderValues(HttpConstants.HDR_COOKIE);
        if (cookieValues == null) {
             return Collections.emptyList();
        }
        return cookieValues.stream().map(ClientCookieDecoder.STRICT::decode).collect(toList());
    }

    public String getParam(String name) {
        List<String> values = getParamValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public List<String> getParamValues(String name) {
        if (params == null) {
            return null;
        }
        return params.get(name);
    }

    public String getPath() {
        return path;
    }

    public void setUrl(String url) {
        urlAndPath = url;
        StringUtils.Pair pair = HttpUtils.parseUriIntoUrlBaseAndPath(url);
        urlBase = pair.left;
        QueryStringDecoder qsd = new QueryStringDecoder(pair.right);
        setPath(qsd.path());
        setParams(qsd.parameters());
    }

    public String getUrlAndPath() {
        return urlAndPath;
    }

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }

    public void setPath(String path) {
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        this.path = path;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, List<String>> getParams() {
        return params == null ? Collections.emptyMap() : params;
    }

    public void setParams(Map<String, List<String>> params) {
        this.params = params;
    }

    public String getPathParam() {
        return pathParam;
    }

    public void setPathParam(String pathParam) {
        this.pathParam = pathParam;
    }

    public List getPathParams() {
        return pathParams;
    }

    public void setPathParams(List pathParams) {
        this.pathParams = pathParams;
    }

    public Map<String, List<String>> getHeaders() {
        return headers == null ? Collections.emptyMap() : headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getBodyAsString() {
        return body == null ? null : FileUtils.toString(body);
    }

    public Object getBodyConverted() {
        ResourceType rt = getResourceType(); // derive if needed
        if (rt != null && rt.isBinary()) {
            return body;
        }
        try {
            return JsValue.fromBytes(body, false, rt);
        } catch (Exception e) {
            logger.trace("failed to auto-convert response", e);
            return getBodyAsString();
        }
    }

    public boolean isForStaticResource() {
        if (!"GET".equals(method)) {
            return false;
        }
        ResourceType rt = getResourceType();
        return rt != null && !rt.isUrlEncodedOrMultipart();
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

    public Object getParamAsJsValue(String name) {
        String value = getParam(name);
        return value == null ? null : JsValue.fromStringSafe(value);
    }

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

    public Object getMultiPartAsJsValue(String name) {
        return JsValue.fromJava(getMultiPart(name));
    }

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
        params = (params == null || params.isEmpty()) ? new HashMap<>() : new HashMap<>(params); // since it may be immutable
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path, Unpooled.wrappedBuffer(body));
        request.headers().add(HttpConstants.HDR_CONTENT_TYPE, contentType);
        InterfaceHttpPostRequestDecoder decoder = multipart ? new HttpPostMultipartRequestDecoder(request) : new HttpPostStandardRequestDecoder(request);
        try {
            for (InterfaceHttpData part : decoder.getBodyHttpDatas()) {
                String name = part.getName();
                if (multipart && part instanceof FileUpload) {
                    List<Map<String, Object>> list = multiParts.computeIfAbsent(name, k -> new ArrayList<>());
                    Map<String, Object> map = new HashMap<>();
                    list.add(map);
                    FileUpload fup = (FileUpload) part;
                    map.put("name", name);
                    map.put("filename", fup.getFilename());
                    Charset charset = fup.getCharset();
                    if (charset != null) {
                        map.put("charset", charset.name());
                    }
                    String ct = fup.getContentType();
                    map.put("contentType", ct);
                    map.put("value", fup.get()); // bytes
                    String transferEncoding = fup.getContentTransferEncoding();
                    if (transferEncoding != null) {
                        map.put("transferEncoding", transferEncoding);
                    }
                } else { // form-field, url-encoded if not multipart
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

    @Override
    public Object getMember(String key) {
        switch (key) {
            case METHOD:
                return method;
            case BODY:
                return JsValue.fromJava(getBodyConverted());
            case PARAM:
                return (Function<String, String>) this::getParam;
            case JSON:
                return (Function<String, Object>) this::getParamAsJsValue;
            case AJAX:
                return isAjax();
            case PATH:
                return path;
            case PARAMS:
                return JsValue.fromJava(params);
            case PATH_PARAM:
                return pathParam;
            case PATH_PARAMS:
                return JsValue.fromJava(pathParams);
            case HEADER:
                return (Function<String, String>) this::getHeader;
            case HEADERS:
                return JsValue.fromJava(headers);
            case MULTI_PART:
                return (Function<String, Object>) this::getMultiPartAsJsValue;
            case MULTI_PARTS:
                return JsValue.fromJava(multiParts);
            case GET:
            case POST:
            case PUT:
            case DELETE:
            case PATCH:
            case HEAD:
            case CONNECT:
            case OPTIONS:
            case TRACE:
                return method.toLowerCase().equals(key);
            default:
                logger.warn("no such property on request object: {}", key);
                return null;
        }
    }

    @Override
    public Object getMemberKeys() {
        return KEY_ARRAY;
    }

    @Override
    public boolean hasMember(String key) {
        return KEY_SET.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        logger.warn("put not supported on request object: {} - {}", key, value);
    }

    @Override
    public String toString() {
        return method + " " + path;
    }

}

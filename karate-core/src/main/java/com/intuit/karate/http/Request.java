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
import com.intuit.karate.graal.JsList;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private static final String PARAM_INT = "paramInt";
    private static final String PARAM_BOOL = "paramBool";
    private static final String PARAM_OR = "paramOr";
    private static final String PARAM_OR_NULL = "paramOrNull";
    private static final String PARAMS = "params";
    private static final String HEADER = "header";
    private static final String HEADERS = "headers";
    private static final String HEADER_VALUES = "headerValues";
    private static final String HEADER_ENTRIES = "headerEntries";
    private static final String PATH_PARAM = "pathParam";
    private static final String PATH_PARAMS = "pathParams";
    private static final String PATH_MATCHES = "pathMatches";
    private static final String PATH_PATTERN = "pathPattern";
    private static final String BODY = "body";
    private static final String BODY_STRING = "bodyString";
    private static final String BODY_BYTES = "bodyBytes";
    private static final String MULTI_PART = "multiPart";
    private static final String MULTI_PARTS = "multiParts";
    private static final String JSON = "json";
    private static final String GET = "get";
    private static final String POST = "post";
    private static final String PUT = "put";
    private static final String DELETE = "delete";
    private static final String PATCH = "patch";
    private static final String HEAD = "head";
    private static final String CONNECT = "connect";
    private static final String OPTIONS = "options";
    private static final String TRACE = "trace";
    private static final String URL_BASE = "urlBase";
    private static final String URL = "url";
    private static final String PATH_RAW = "pathRaw";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";

    private static final String[] KEYS = new String[]{
        PATH, METHOD, PARAM, PARAM_INT, PARAM_BOOL, PARAM_OR, PARAM_OR_NULL, PARAMS,
        HEADER, HEADERS, HEADER_VALUES, HEADER_ENTRIES, PATH_PARAM, PATH_PARAMS, PATH_MATCHES, PATH_PATTERN,
        BODY, BODY_STRING, BODY_BYTES, MULTI_PART, MULTI_PARTS, JSON,
        GET, POST, PUT, DELETE, PATCH, HEAD, CONNECT, OPTIONS, TRACE, URL_BASE, URL, PATH_RAW, START_TIME, END_TIME
    };
    private static final Set<String> KEY_SET = new HashSet<>(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private long startTime = System.currentTimeMillis();
    private long endTime;
    private String urlAndPath;
    private String urlBase;
    private String pathOriginal;
    private String path;
    private String method;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private byte[] body;
    private Map<String, List<Map<String, Object>>> multiParts;
    private ResourceType resourceType;
    private String resourcePath;
    private Map<String, String> pathParams = Collections.emptyMap();
    private String pathPattern;
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

    public int getParamInt(String name) {
        String value = getParam(name);
        return value == null ? -1 : Integer.valueOf(value);
    }

    public boolean getParamBool(String name) {
        String value = getParam(name);
        return value == null ? false : Boolean.valueOf(value);
    }

    public String getParam(String name) {
        List<String> values = getParamValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public Object getParamOr(String name, Object value) {
        String temp = getParam(name);
        return StringUtils.isBlank(temp) ? value : temp;
    }

    public Object getParamOrNull(String name) {
        return getParamOr(name, null);
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

    public void setUrl(String url) {
        urlAndPath = url;
        StringUtils.Pair pair = HttpUtils.parseUriIntoUrlBaseAndPath(url);
        urlBase = pair.left;
        QueryStringDecoder qsd = new QueryStringDecoder(pair.right);
        setPath(qsd.path());
        setParams(qsd.parameters());
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }        

    public long getStartTime() {
        return startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getEndTime() {
        return endTime;
    }        

    public String getUrlAndPath() {
        return urlAndPath != null ? urlAndPath : (urlBase != null ? urlBase : "") + path;
    }

    public String getUrlBase() {
        return urlBase;
    }

    public void setUrlBase(String urlBase) {
        this.urlBase = urlBase;
    }

    public void setPath(String path) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (path.charAt(0) != '/') {
            path = "/" + path.substring(1);
        }
        this.path = path;
        if (pathOriginal == null) {
            pathOriginal = path;
        }
    }

    public String getPathOriginal() {
        return pathOriginal;
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

    public boolean pathMatches(String pattern) {
        Map<String, String> temp = HttpUtils.parseUriPattern(pattern, "/" + path);
        if (temp == null) {
            return false;
        }
        pathParams = temp;
        pathPattern = pattern;
        return true;
    }

    public void setParamCommaDelimited(String name, String value) {
        if (value == null) {
            return;
        }
        setParam(name, StringUtils.split(value, ',', false));
    }

    public void setParam(String name, Object value) {
        if (params == null) {
            params = new HashMap();
        }
        if (value == null) {
            params.put(name, null);
        } else if (value instanceof List) {
            List list = (List) value;
            List<String> values = new ArrayList(list.size());
            for (Object o : list) {
                values.add(o == null ? null : o.toString());
            }
            params.put(name, values);
        } else {
            params.put(name, Collections.singletonList(value.toString()));
        }
    }

    public Object getPathParam() {
        if (pathParams.isEmpty()) {
            return null;
        }
        return pathParams.values().iterator().next();
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public void setPathParams(Map<String, String> pathParams) {
        this.pathParams = pathParams;
    }

    public Map<String, List<String>> getHeaders() {
        return headers == null ? Collections.emptyMap() : headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public void setCookiesRaw(List<String> values) {
        if (values == null) {
            return;
        }
        if (headers == null) {
            headers = new HashMap();
        }
        headers.put(HttpConstants.HDR_COOKIE, values);
    }

    public void setHeaderCommaDelimited(String name, String value) {
        if (value == null) {
            return;
        }
        if (headers == null) {
            headers = new HashMap();
        }
        headers.put(name, StringUtils.split(value, ',', false));
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
        return JsValue.fromBytes(body, false, rt);
    }

    public boolean isHttpGetForStaticResource() {
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

    private static final String KEY = "key";
    private static final String VALUE = "value";

    private final Supplier HEADER_ENTRIES_FUNCTION = () -> {
        if (headers == null) {
            return JsList.EMPTY;
        }
        List list = new ArrayList(headers.size());
        headers.forEach((k, v) -> {
            if (v == null || v.isEmpty()) {
                // continue
            } else {
                Map map = new HashMap(2);
                map.put(KEY, k);
                map.put(VALUE, v.get(0));
                list.add(map);
            }
        });
        return JsValue.fromJava(list);
    };

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
            case BODY_STRING:
                return getBodyAsString();
            case BODY_BYTES:
                return body;
            case PARAM:
                return (Function<String, String>) this::getParam;
            case PARAM_INT:
                return (Function<String, Integer>) this::getParamInt;
            case PARAM_BOOL:
                return (Function<String, Boolean>) this::getParamBool;
            case PARAM_OR:
                return (BiFunction<String, Object, Object>) this::getParamOr;
            case PARAM_OR_NULL:
                return (Function<String, Object>) this::getParamOrNull;
            case JSON:
                return (Function<String, Object>) this::getParamAsJsValue;
            case PATH:
                return path;
            case PATH_RAW:
                return getPathRaw();
            case URL_BASE:
                return urlBase;
            case URL:
                return urlAndPath;
            case PARAMS:
                return JsValue.fromJava(params);
            case PATH_PARAM:
                return getPathParam();
            case PATH_PARAMS:
                return JsValue.fromJava(pathParams);
            case PATH_MATCHES:
                return (Function<String, Object>) this::pathMatches;
            case PATH_PATTERN:
                return pathPattern;
            case HEADER:
                return (Function<String, String>) this::getHeader;
            case HEADERS:
                return JsValue.fromJava(headers);
            case HEADER_VALUES:
                return (Function<String, List<String>>) this::getHeaderValues;
            case HEADER_ENTRIES:
                return HEADER_ENTRIES_FUNCTION;
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
            case START_TIME:
                return startTime;
            case END_TIME:
                return endTime;
            default:
                logger.warn("no such property on request object: {}", key);
                return null;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put(URL, urlAndPath);
        map.put(URL_BASE, urlBase);
        map.put(PATH, path);
        map.put(PATH_RAW, getPathRaw());
        map.put(METHOD, method);
        map.put(HEADER_ENTRIES, HEADER_ENTRIES_FUNCTION.get());
        map.put(BODY, getBodyConverted());
        return map;
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
        return method + " " + pathOriginal;
    }

}

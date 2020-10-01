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
package com.intuit.karate.server;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsArray;
import com.intuit.karate.graal.JsValue;
import com.linecorp.armeria.common.RequestContext;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private static final String PARAMS = "params";
    private static final String HEADER = "header";
    private static final String HEADERS = "headers";
    private static final String PARAM = "param";
    private static final String BODY = "body";
    private static final String PATH_PARAM = "pathParam";
    private static final String PATH_PARAMS = "pathParams";
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
        PATH, METHOD, PARAMS, HEADER, HEADERS, PARAM, BODY, PATH_PARAM, PATH_PARAMS, JSON, AJAX,
        GET, POST, PUT, DELETE, PATCH, HEAD, CONNECT, OPTIONS, TRACE
    };
    private static final Set<String> KEY_SET = new HashSet(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private boolean pathEmpty;
    private String path;
    private String method;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private byte[] body;
    private ResourceType resourceType;
    private String resourcePath;
    private String pathParam;
    private List pathParams = Collections.EMPTY_LIST;
    private RequestContext requestContext;

    public RequestContext getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }        
    
    private static final String HX_REQUEST = "HX-Request";
    
    public boolean isAjax() {
        return header(HX_REQUEST) != null;
    }

    public List<String> header(String name) { // TODO optimize
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public String param(String name) {
        if (params == null) {
            return null;
        }
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public boolean isPathEmpty() {
        return pathEmpty;
    }        

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        this.path = path;
        pathEmpty = path.isEmpty();
    }

    public ResourceType getResourceType() {
        return resourceType;
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
        return params == null ? Collections.EMPTY_MAP : params;
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
        return headers == null ? Collections.EMPTY_MAP : headers;
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

    public Object getBodyAsJsValue() {
        return JsValue.fromBytes(body);
    }
    
    public Object getParamAsJsValue(String name) {
        String value = param(name);
        return value == null ? null : JsValue.fromString(value);
    }

    public void processBody() {
        if (body == null) {
            return;
        }
        params = (params == null || params.isEmpty()) ? new HashMap() : new HashMap(params); // since it may be immutable
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path, Unpooled.wrappedBuffer(body));
        HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(request);
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next(); // TODO multipart
                Attribute attribute = (Attribute) data;
                params.put(attribute.getName(), Collections.singletonList(attribute.getValue()));
            }
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException eod) {
            // logger.debug("end of post decode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            decoder.destroy();
        }
    }
    
    private final Function<String, String> PARAM_FUNCTION = name -> param(name);

    private final Function<String, String> HEADER_FUNCTION = name -> {
        List<String> list = header(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    };    
    
    private final Function<String, Object> JSON_FUNCTION = name -> getParamAsJsValue(name);

    @Override
    public Object getMember(String key) {
        switch (key) {
            case METHOD:
                return method;
            case BODY:
                return getBodyAsJsValue();
            case PARAM:
                return PARAM_FUNCTION;
            case JSON:
                return JSON_FUNCTION;
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
                return HEADER_FUNCTION;
            case HEADERS:
                return JsValue.fromJava(headers);
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

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

import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsArray;
import com.intuit.karate.graal.JsValue;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.QueryParams;
import com.linecorp.armeria.common.QueryParamsBuilder;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.RequestHeadersBuilder;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class HttpClient implements ProxyObject {

    private static final Logger logger = LoggerFactory.getLogger(HttpClient.class);

    private static final String URL = "url";
    private static final String METHOD = "method";
    private static final String PATH = "path";
    private static final String PARAM = "param";
    private static final String PARAMS = "params";
    private static final String HEADER = "header";
    private static final String HEADERS = "headers";
    private static final String HEADER_FACTORY = "headerFactory";
    private static final String BODY = "body";
    private static final String INVOKE = "invoke";
    private static final String GET = "get";
    private static final String POST = "post";
    private static final String PUT = "put";
    private static final String DELETE = "delete";
    private static final String PATCH = "patch";
    private static final String HEAD = "head";
    private static final String CONNECT = "connect";
    private static final String OPTIONS = "options";
    private static final String TRACE = "trace";

    private static final byte[] ZERO_BYTES = new byte[0];

    private static final String[] KEYS = new String[]{
        URL, METHOD, PATH, PARAM, PARAMS, HEADER, HEADERS, BODY, INVOKE, HEADER_FACTORY,
        GET, POST, PUT, DELETE, PATCH, HEAD, CONNECT, OPTIONS, TRACE
    };
    private static final Set<String> KEY_SET = new HashSet(Arrays.asList(KEYS));
    private static final JsArray KEY_ARRAY = new JsArray(KEYS);

    private String url;
    private String method;
    private List<String> paths;
    private Map<String, List<String>> params;
    private Map<String, List<String>> headers;
    private Object body;
    private JsValue headerFactory;

    private final RequestContext requestContext;

    public HttpClient(RequestContext requestContext, String url) {
        this.requestContext = requestContext;
        this.url = url;
    }

    public HttpClient reset() {
        // url and headerFactory will be retained
        method = null;
        paths = null;
        params = null;
        headers = null;
        body = null;
        return this;
    }

    public HttpClient url(String value) {
        url = value;
        return this;
    }

    public HttpClient method(String value) {
        this.method = value.toUpperCase();
        return this;
    }

    public HttpClient path(String... values) {
        for (String path : values) {
            path(path);
        }
        return this;
    }

    public HttpClient path(String path) {
        if (path == null) {
            return this;
        }
        if (paths == null) {
            paths = new ArrayList<>();
        }
        paths.add(path);
        return this;
    }

    private String getPath() {
        String temp = "";
        if (paths == null) {
            return temp;
        }
        for (String path : paths) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!temp.isEmpty() && !temp.endsWith("/")) {
                temp = temp + "/";
            }
            temp = temp + path;
        }
        return temp;
    }

    public List<String> header(String name) { // TODO optimize
        return StringUtils.getIgnoreKeyCase(headers, name);
    }

    public HttpClient header(String name, String... values) {
        return header(name, Arrays.asList(values));
    }

    public HttpClient header(String name, List<String> values) {
        if (headers == null) {
            headers = new HashMap();
        }
        headers.put(name, values);
        return this;
    }

    public HttpClient headers(Value value) {
        JsValue jv = new JsValue(value);
        if (jv.isObject()) {
            headers(jv.getAsMap());
        } else {
            logger.warn("unexpected headers() argument: {}", value);
        }
        return this;
    }

    public HttpClient headers(Map<String, Object> headers) {
        headers.forEach((k, v) -> {
            if (v instanceof List) {
                header(k, (List) v);
            } else if (v != null) {
                header(k, v.toString());
            }
        });
        return this;
    }

    public List<String> param(String name) {
        if (params == null || name == null) {
            return null;
        }
        return params.get(name);
    }

    public HttpClient param(String name, String... values) {
        if (params == null) {
            params = new HashMap();
        }
        params.put(name, Arrays.asList(values));
        return this;
    }

    public Response invoke(String method, Object o) {
        method(method);
        this.body = o;
        return invoke();
    }

    public Response invoke(String method) {
        method(method);
        return invoke();
    }

    private static final String CONTENT_TYPE = "Content-Type";

    public Response invoke() {
        if (headerFactory != null) {
            if (headerFactory.isObject()) {
                headers(headerFactory.getAsMap());
            } else if (headerFactory.isFunction()) {
                Value value = headerFactory.getOriginal();
                Value result = value.execute();
                headers(result);
            } else {
                logger.warn("bad headerFactory: {}", headerFactory);
            }
        }
        if (method == null) {
            logger.warn("http method not set, defaulting to GET");
            method = "GET";
        }
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        String path = getPath();
        if (params != null) {
            QueryParamsBuilder qpb = QueryParams.builder();
            params.forEach((k, v) -> qpb.add(k, v));
            path = path + "?" + qpb.toQueryString();
        }
        WebClient webClient = WebClient.builder(url).decorator(new HttpClientLogger()).build();
        RequestHeadersBuilder rhb = RequestHeaders.builder(httpMethod, path);
        if (headers != null) {
            headers.forEach((k, v) -> rhb.add(k, v));
        }
        final byte[] bytes;
        if (body == null) {
            bytes = ZERO_BYTES;
        } else {
            if (header(CONTENT_TYPE) == null) {
                ResourceType rt = ResourceType.fromObject(body);
                if (rt != ResourceType.NONE) {
                    rhb.add(CONTENT_TYPE, rt.contentType);
                }
            }
            bytes = JsValue.toBytes(body);
        }
        AggregatedHttpResponse ahr;
        Callable<AggregatedHttpResponse> callable = () -> webClient.execute(rhb.build(), bytes).aggregate().join();
        ServiceRequestContext src = requestContext == null ? null : requestContext.root();
        try {
            if (src == null) {
                ahr = callable.call();
            } else {
                Future<AggregatedHttpResponse> future = src.blockingTaskExecutor().submit(callable);
                ahr = future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ResponseHeaders rh = ahr.headers();
        Map<String, List<String>> headers = rh.isEmpty() ? null : new LinkedHashMap(rh.size());
        for (AsciiString name : rh.names()) {
            headers.put(name.toString(), rh.getAll(name));
        }
        String contentType = ahr.contentType() == null ? null : ahr.contentType().nameWithoutParameters();
        ResourceType resourceType = ResourceType.fromContentType(contentType);
        byte[] body = ahr.content().isEmpty() ? null : ahr.content().array();
        Response response = new Response(ahr.status().code(), headers, body, resourceType);
        reset();
        return response;
    }

    private final VarArgFunction PATH_FUNCTION = args -> {
        if (args.length == 0) {
            return getPath();
        } else {
            for (Object o : args) {
                if (o != null) {
                    path(o.toString());
                }
            }
            return HttpClient.this;
        }
    };

    private static String toString(Object o) {
        return o == null ? null : o.toString();
    }

    private final VarArgFunction HEADER_FUNCTION = args -> {
        if (args.length == 1) {
            List<String> list = header(toString(args[0]));
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.get(0);
        } else {
            header(toString(args[0]), toString(args[1]));
            return HttpClient.this;
        }
    };

    private final VarArgFunction PARAM_FUNCTION = args -> {
        if (args.length == 1) {
            List<String> list = param(toString(args[0]));
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.get(0);
        } else {
            param(toString(args[0]), toString(args[1]));
            return HttpClient.this;
        }
    };

    private final VarArgFunction INVOKE_FUNCTION = args -> {
        switch (args.length) {
            case 0:
                return invoke();
            case 1:
                return invoke(args[0].toString());
            default:
                return invoke(args[0].toString(), args[1]);
        }
    };

    private final Supplier GET_FUNCTION = () -> invoke(GET);
    private final Function POST_FUNCTION = o -> invoke(POST, o);
    private final Function PUT_FUNCTION = o -> invoke(PUT, o);
    private final Function PATCH_FUNCTION = o -> invoke(PATCH, o);
    private final Supplier DELETE_FUNCTION = () -> invoke(DELETE);
    private final Function<String, Object> URL_FUNCTION = s -> url(s);

    @Override
    public Object getMember(String key) {
        switch (key) {
            case METHOD:
                return method;
            case PATH:
                return PATH_FUNCTION;
            case HEADER:
                return HEADER_FUNCTION;
            case HEADERS:
                return JsValue.fromJava(headers);
            case PARAM:
                return PARAM_FUNCTION;
            case PARAMS:
                return JsValue.fromJava(params);
            case BODY:
                return JsValue.fromJava(body);
            case INVOKE:
                return INVOKE_FUNCTION;
            case GET:
                return GET_FUNCTION;
            case POST:
                return POST_FUNCTION;
            case PUT:
                return PUT_FUNCTION;
            case PATCH:
                return PATCH_FUNCTION;
            case DELETE:
                return DELETE_FUNCTION;
            case URL:
                return URL_FUNCTION; // special case, support fluent api
            default:
                logger.warn("no such property on http object: {}", key);
                return null;
        }
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case METHOD:
                method = value.asString();
                break;
            case BODY:
                body = JsValue.toJava(value);
                break;
            case HEADERS:
                headers(value);
                break;
            case PARAMS:
                params = (Map) JsValue.toJava(value);
                break;
            case HEADER_FACTORY:
                headerFactory = new JsValue(value);
                break;
            case URL:
                url = value.asString();
                break;
            default:
                logger.warn("put not supported on http object: {} - {}", key, value);
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
    public String toString() {
        return url;
    }

}

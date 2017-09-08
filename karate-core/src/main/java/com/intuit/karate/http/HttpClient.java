/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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

import com.intuit.karate.exception.KarateException;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.XmlUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 * @param <T>
 */
public abstract class HttpClient<T> {

    public static final String APPLICATION_JSON = "application/json";
    public static final String APPLICATION_XML = "application/xml";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";
    public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";

    private static final String KARATE_HTTP_PROPERTIES = "karate-http.properties";

    protected HttpRequest request;

    /**
     * guaranteed to be called once if empty constructor was used
     *
     * @param config
     * @param context
     */
    public abstract void configure(HttpConfig config, ScriptContext context);

    protected abstract T getEntity(List<MultiPartItem> multiPartItems, String mediaType);

    protected abstract T getEntity(MultiValuedMap formFields, String mediaType);

    protected abstract T getEntity(InputStream stream, String mediaType);

    protected abstract T getEntity(String content, String mediaType);

    protected abstract void buildUrl(String url);

    protected abstract void buildPath(String path);

    protected abstract void buildParam(String name, Object... values);

    protected abstract void buildHeader(String name, Object value, boolean replace);

    protected abstract void buildCookie(Cookie cookie);

    protected abstract HttpResponse makeHttpRequest(T entity, long startTime);

    protected abstract String getRequestUri();

    private T getEntityInternal(ScriptValue body, String mediaType) {
        if (body.isJsonLike()) {
            if (mediaType == null) {
                mediaType = APPLICATION_JSON;
            }
            DocumentContext json = body.getAsJsonDocument();
            return getEntity(json.jsonString(), mediaType);
        } else if (body.isXml()) {
            Node node = body.getValue(Node.class);
            if (mediaType == null) {
                mediaType = APPLICATION_XML;
            }
            return getEntity(XmlUtils.toString(node), mediaType);           
        } else if (body.isStream()) {
            InputStream is = body.getValue(InputStream.class);
            if (mediaType == null) {
                mediaType = APPLICATION_OCTET_STREAM;
            }
            return getEntity(is, mediaType);           
        } else {
            if (mediaType == null) {
                mediaType = TEXT_PLAIN;
            }
            return getEntity(body.getAsString(), mediaType);          
        }
    }

    private T buildRequestInternal(HttpRequest request, ScriptContext context) {
        String method = request.getMethod();
        if (method == null) {
            String msg = "'method' is required to make an http call";
            context.logger.error(msg);
            throw new RuntimeException(msg);
        }
        method = method.toUpperCase();
        request.setMethod(method);
        this.request = request;
        String url = request.getUrl();
        if (url == null) {
            String msg = "url not set, please refer to the keyword documentation for 'url'";
            context.logger.error(msg);
            throw new RuntimeException(msg);
        }
        buildUrl(url);
        if (request.getPaths() != null) {
            for (String path : request.getPaths()) {
                buildPath(path);
            }
        }
        if (request.getParams() != null) {
            for (Map.Entry<String, List> entry : request.getParams().entrySet()) {
                buildParam(entry.getKey(), entry.getValue().toArray());
            }
        }
        if (request.getHeaders() != null) {
            for (Map.Entry<String, List> entry : request.getHeaders().entrySet()) {
                for (Object value : entry.getValue()) {
                    buildHeader(entry.getKey(), value, false);
                }
            }
        }
        Map<String, Object> configHeaders = context.getConfigHeaders().evalAsMap(context);
        if (configHeaders != null) {
            for (Map.Entry<String, Object> entry : configHeaders.entrySet()) {
                buildHeader(entry.getKey(), entry.getValue(), true);
            }
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies().values()) {
                buildCookie(cookie);
            }
        }
        Map<String, Object> configCookies = context.getConfigCookies().evalAsMap(context);
        for (Cookie cookie : Cookie.toCookies(configCookies)) {
            buildCookie(cookie);
        }       
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            String mediaType = request.getContentType();
            if (request.getMultiPartItems() != null) {
                if (mediaType == null) {
                    mediaType = MULTIPART_FORM_DATA;
                }
                return getEntity(request.getMultiPartItems(), mediaType);
            } else if (request.getFormFields() != null) {
                return getEntity(request.getFormFields(), APPLICATION_FORM_URLENCODED);
            } else {               
                ScriptValue body = request.getBody();
                if ((body == null || body.isNull())) {
                    if ("DELETE".equals(method)) {
                        return null; // traditional DELETE, we also support using a request body for DELETE
                    } else {
                        String msg = "request body is required for a " + method + ", please use the 'request' keyword";
                        throw new RuntimeException(msg);
                    }
                }
                if (context.isLogPrettyRequest() && context.logger.isDebugEnabled()) {
                    context.logger.debug("request:\n{}", body.getAsPrettyString());
                }
                return getEntityInternal(body, mediaType);
            }
        } else {
            if (request.getFormFields() != null) { // not POST, move form-fields to params
                for (Map.Entry<String, List> entry : request.getFormFields().entrySet()) {
                    buildParam(entry.getKey(), entry.getValue().toArray());
                }
            }             
            return null;
        }
    }

    protected static long getResponseTime(long startTime) {
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        return responseTime;
    }

    public HttpResponse invoke(HttpRequest request, ScriptContext context) {
        T body = buildRequestInternal(request, context);
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse response = makeHttpRequest(body, startTime);
            context.logger.debug("response time in milliseconds: {}", response.getTime());
            context.updateConfigCookies(response.getCookies());
            return response;
        } catch (Exception e) {
            long responseTime = getResponseTime(startTime);
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + getRequestUri();
            context.logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
    }        

    public static HttpClient construct(String className) {
        try {
            Class clazz = Class.forName(className);
            return (HttpClient) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpClient construct(HttpConfig config, ScriptContext context) {
        if (config.getClientInstance() != null) {
            return config.getClientInstance();
        }
        try {
            String className;
            if (config != null && config.getClientClass() != null) {
                className = config.getClientClass();
            } else {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(KARATE_HTTP_PROPERTIES);
                if (is == null) {
                    String msg = KARATE_HTTP_PROPERTIES + " not found";
                    throw new RuntimeException(msg);
                }
                Properties props = new Properties();
                props.load(is);
                className = props.getProperty("client.class");
            }
            HttpClient client = construct(className);
            client.configure(config, context);
            return client;
        } catch (Exception e) {
            String msg = "failed to construct class by name: " + e.getMessage() + ", aborting";
            throw new RuntimeException(msg);
        }
    }

}

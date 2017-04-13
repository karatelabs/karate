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

import com.intuit.karate.KarateException;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.XmlUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public abstract class HttpClient<T> {

    protected static final Logger logger = LoggerFactory.getLogger(HttpClient.class);
    
    protected static final String APPLICATION_JSON = "application/json";
    protected static final String APPLICATION_XML = "application/xml";
    protected static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    protected static final String TEXT_PLAIN = "text/plain";
    protected static final String MULTIPART_FORM_DATA = "multipart/form-data";
    protected static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    
    private static final String KARATE_HTTP_PROPERTIES = "karate-http.properties";
    
    protected HttpRequest request;

    public abstract void configure(HttpConfig config);

    protected abstract T getEntity(List<MultiPartItem> multiPartItems, String mediaType);
    
    protected abstract T getEntity(MultiValuedMap formFields, String mediaType);

    protected abstract T getEntity(InputStream stream, String mediaType);    
    
    protected abstract T getEntity(String content, String mediaType);
    
    protected abstract void buildUrl(String url);
    
    protected abstract void buildPath(String path);
    
    protected abstract void buildParam(String name, Object ... values);
    
    protected abstract void buildHeader(String name, Object value, boolean replace);
    
    protected abstract void buildCookie(Cookie cookie);
    
    protected abstract HttpResponse makeHttpRequest(String method, T entity, long startTime);
    
    protected abstract String getRequestUri();

    private T getEntityInternal(ScriptValue body, String mediaType) {
        switch (body.getType()) {
            case JSON:
                if (mediaType == null) {
                    mediaType = APPLICATION_JSON;
                }
                DocumentContext json = body.getValue(DocumentContext.class);
                return HttpClient.this.getEntity(json.jsonString(), mediaType);
            case MAP:
                if (mediaType == null) {
                    mediaType = APPLICATION_JSON;
                }
                Map<String, Object> map = body.getValue(Map.class);
                DocumentContext doc = JsonPath.parse(map);
                return HttpClient.this.getEntity(doc.jsonString(), mediaType);
            case XML:
                Node node = body.getValue(Node.class);
                if (mediaType == null) {
                    mediaType = APPLICATION_XML;
                }
                return HttpClient.this.getEntity(XmlUtils.toString(node), mediaType);
            case INPUT_STREAM:
                InputStream is = body.getValue(InputStream.class);
                if (mediaType == null) {
                    mediaType = APPLICATION_OCTET_STREAM;
                }
                return HttpClient.this.getEntity(is, mediaType);
            default:
                if (mediaType == null) {
                    mediaType = TEXT_PLAIN;
                }
                return HttpClient.this.getEntity(body.getAsString(), mediaType);
        }
    }
    
    private T buildRequestInternal(HttpRequest request, ScriptContext context) {
        String method = request.getMethod();
        if (method == null) {
            String msg = "'method' is required to make an http call";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        method = method.toUpperCase();
        request.setMethod(method);        
        this.request = request;
        String url = request.getUrl();
        if (url == null) {
            String msg = "url not set, please refer to the keyword documentation for 'url'";
            logger.error(msg);
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
        Map<String, Object> configuredHeaders = evalConfiguredHeaders(context);
        if (configuredHeaders != null) {
            for (Map.Entry<String, Object> entry : configuredHeaders.entrySet()) {
                buildHeader(entry.getKey(), entry.getValue(), true);
            }
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies().values()) {
                buildCookie(cookie);
            }
        }
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String mediaType = request.getContentType();
            if (request.getMultiPartItems() != null) {
                if (mediaType == null) {
                    mediaType = MULTIPART_FORM_DATA;
                }
                return HttpClient.this.getEntity(request.getMultiPartItems(), mediaType);
            } else if (request.getFormFields() != null) {
                return getEntity(request.getFormFields(), APPLICATION_FORM_URLENCODED);
            } else {
                ScriptValue body = request.getBody();
                if (body == null || body.isNull()) {
                    String msg = "request body is requred for a " + method + ", please use the 'request' keyword";
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }
                return getEntityInternal(body, mediaType);
            }
        } else {
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
            HttpResponse response = makeHttpRequest(request.getMethod(), body, startTime);
            logger.debug("response time in milliseconds: {}", response.getTime());
            return response;
        } catch (Exception e) {
            long responseTime = getResponseTime(startTime);
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + getRequestUri();
            logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
    }
    
    private static Map<String, Object> evalConfiguredHeaders(ScriptContext context) {
        ScriptValue headersValue = context.getConfiguredHeaders();
        switch (headersValue.getType()) {
            case JS_FUNCTION:
                ScriptObjectMirror som = headersValue.getValue(ScriptObjectMirror.class);
                ScriptValue sv = Script.evalFunctionCall(som, null, context);
                switch (sv.getType()) {
                    case JS_OBJECT:
                    case MAP:
                        return sv.getValue(Map.class);
                    default:
                        logger.trace("custom headers function returned: {}", sv);
                        return null;
                }
            case JSON:
                DocumentContext json = headersValue.getValue(DocumentContext.class);
                return json.read("$");
            default:
                logger.trace("configured 'headers' is not a map-like object or js function: {}", headersValue);
                return null;
        }        
    } 

    public static HttpClient construct() {        
        try {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(KARATE_HTTP_PROPERTIES);
            if (is == null) {
                String msg = KARATE_HTTP_PROPERTIES + " not found";
                logger.error(msg);
                throw new RuntimeException(msg);
            }
            Properties props = new Properties();
            props.load(is);
            String className = props.getProperty("client.class");
            logger.trace("loaded {}, init: {}", KARATE_HTTP_PROPERTIES, className);
            Class clazz = Class.forName(className);
            return (HttpClient) clazz.newInstance();
        } catch (Exception e) {
            String msg = "failed to construct class by name: " + e.getMessage() + ", aborting";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
    }
    
}

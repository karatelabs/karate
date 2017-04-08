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
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.XmlUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public abstract class HttpClient<T> {

    protected static final Logger logger = LoggerFactory.getLogger(HttpClient.class);

    public abstract void configure(HttpConfig config);

    public abstract Object getMultiPartEntity(List<MultiPartItem> items);
    
    public abstract Object getFormFieldsEntity(MultiValuedMap fields);

    public abstract T getRequestEntity(Object value, String mediaType);    
    
    public abstract void buildUrl(String url);
    
    public abstract void buildPath(String path);
    
    public abstract void buildParam(String name, Object ... values);
    
    public abstract void buildHeader(String name, Object value);
    
    public abstract void buildCookie(String name, String value);
    
    public abstract HttpResponse makeHttpRequest(String method, T entity, long startTime);
    
    public abstract String getUri();

    protected T getEntity(ScriptValue body, String mediaType) {
        switch (body.getType()) {
            case JSON:
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_JSON;
                }
                DocumentContext json = body.getValue(DocumentContext.class);
                return getRequestEntity(json.jsonString(), mediaType);
            case MAP:
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_JSON;
                }
                Map<String, Object> map = body.getValue(Map.class);
                DocumentContext doc = JsonPath.parse(map);
                return getRequestEntity(doc.jsonString(), mediaType);
            case XML:
                Node node = body.getValue(Node.class);
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_XML;
                }
                return getRequestEntity(XmlUtils.toString(node), mediaType);
            case INPUT_STREAM:
                InputStream is = body.getValue(InputStream.class);
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
                return getRequestEntity(is, mediaType);
            default:
                if (mediaType == null) {
                    mediaType = MediaType.TEXT_PLAIN;
                }
                return getRequestEntity(body.getAsString(), mediaType);
        }
    }
    
    protected T buildRequestBody(HttpRequest request, ScriptContext context) {
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
                    buildHeader(entry.getKey(), value);
                }
            }
        }
        Map<String, Object> configuredHeaders = HttpUtils.evalConfiguredHeaders(context);
        if (configuredHeaders != null) {
            for (Map.Entry<String, Object> entry : configuredHeaders.entrySet()) {
                buildHeader(entry.getKey(), null); // clear if already set
                buildHeader(entry.getKey(), entry.getValue());
            }
        }
        if (request.getCookies() != null) {
            for (Map.Entry<String, String> entry : request.getCookies().entrySet()) {
                buildCookie(entry.getKey(), entry.getValue());
            }
        }
        String method = request.getMethod();
        if (method == null) {
            String msg = "'method' is required to make an http call";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        method = method.toUpperCase();
        request.setMethod(method);
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String mediaType = request.getContentType();
            if (request.getMultiPartItems() != null) {
                if (mediaType == null) {
                    mediaType = MediaType.MULTIPART_FORM_DATA;
                }
                Object mpe = getMultiPartEntity(request.getMultiPartItems());
                return getRequestEntity(mpe, mediaType);
            } else if (request.getFormFields() != null) {
                Object ffe = getFormFieldsEntity(request.getFormFields());
                return getRequestEntity(ffe, MediaType.APPLICATION_FORM_URLENCODED);
            } else {
                ScriptValue body = request.getBody();
                if (body == null || body.isNull()) {
                    String msg = "request body is requred for a " + method + ", please use the 'request' keyword";
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }
                return getEntity(body, mediaType);
            }
        } else {
            return null;
        }
    }
    
    protected long getResponseTime(long startTime) {
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;        
        return responseTime;
    }
    
    public HttpResponse invoke(HttpRequest request, ScriptContext context) {
        T body = buildRequestBody(request, context);
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse response = makeHttpRequest(request.getMethod(), body, startTime);
            logger.debug("response time in milliseconds: {}", response.getTime());
            return response;
        } catch (Exception e) {
            long responseTime = getResponseTime(startTime);
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + getUri();
            logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
    }
    
}

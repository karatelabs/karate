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
package com.intuit.karate.runtime;

import com.intuit.karate.XmlUtils;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public abstract class ScenarioHttpClient<T> {

    protected static final String CONTENT_TYPE = "Content-Type";
    protected static final String APPLICATION_JSON = "application/json";
    protected static final String APPLICATION_XML = "application/xml";
    protected static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    protected static final String TEXT_PLAIN = "text/plain";
    protected static final String MULTIPART_FORM_DATA = "multipart/form-data";
    protected static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";

    protected ScenarioHttpBuilder request;

    protected ScenarioEngine getEngine() {
        return ScenarioEngine.LOCAL.get();
    }

    public abstract void configure(Config config);

    protected abstract T getEntity(List<MultiPartItem> multiPartItems, String mediaType);

    protected abstract T getEntity(MultiValuedMap formFields, String mediaType);

    protected abstract T getEntity(InputStream stream, String mediaType);

    protected abstract T getEntity(String content, String mediaType);

    protected abstract void buildUrl(String url);

    protected abstract void buildPath(String path);

    protected abstract void buildParam(String name, Object... values);

    protected abstract void buildHeader(String name, Object value, boolean replace);

    protected abstract void buildCookie(Cookie cookie);

    protected abstract HttpResponse makeHttpRequest(T entity);

    protected abstract String getRequestUri();

    private T getEntityInternal(Variable body, String mediaType) {
        if (body.isMap()) {
            if (mediaType == null) {
                mediaType = APPLICATION_JSON;
            }
            return getEntity(body.getAsString(), mediaType);
        } else if (body.isXml()) {
            Node node = body.getValue();
            if (mediaType == null) {
                mediaType = APPLICATION_XML;
            }
            return getEntity(XmlUtils.toString(node), mediaType);
        } else if (body.isBytes()) {
            byte[] bytes = body.getValue();
            InputStream is = new ByteArrayInputStream(bytes);
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

    private T buildRequestInternal(ScenarioHttpBuilder request) {
        ScenarioEngine engine = getEngine();
        String method = request.getMethod();
        if (method == null) {
            String msg = "'method' is required to make an http call";
            engine.logger.error(msg);
            throw new RuntimeException(msg);
        }
        method = method.toUpperCase();
        request.setMethod(method);
        this.request = request;
        boolean methodRequiresBody
                = "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
        String url = request.getUrl();
        if (url == null) {
            String msg = "url not set, please refer to the keyword documentation for 'url'";
            engine.logger.error(msg);
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
        if (request.getFormFields() != null && !methodRequiresBody) {
            // not POST, move form-fields to params
            for (Map.Entry<String, List> entry : request.getFormFields().entrySet()) {
                buildParam(entry.getKey(), entry.getValue().toArray());
            }

        }
        if (request.getHeaders() != null) {
            for (Map.Entry<String, List> entry : request.getHeaders().entrySet()) {
                for (Object value : entry.getValue()) {
                    if (entry.getValue() == null) { // TODO seems to happen for retry-until and __arg
                        continue;
                    }
                    buildHeader(entry.getKey(), value, false);
                }
            }
        }
        Map<String, Object> configHeaders = engine.config.getHeaders().evalAsMap();
        if (configHeaders != null) {
            for (Map.Entry<String, Object> entry : configHeaders.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue()); // update request for hooks, etc.
                buildHeader(entry.getKey(), entry.getValue(), true);
            }
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies().values()) {
                buildCookie(cookie);
            }
        }
        Map<String, Object> configCookies = engine.config.getCookies().evalAsMap();
        for (Cookie cookie : Cookie.toCookies(configCookies)) {
            request.setCookie(cookie); // update request for hooks, etc.
            buildCookie(cookie);
        }
        if (methodRequiresBody) {
            String mediaType = request.getContentType();
            if (configHeaders != null && configHeaders.containsKey(CONTENT_TYPE)) { // edge case if config headers had Content-Type
                mediaType = (String) configHeaders.get(CONTENT_TYPE);
            }
            if (request.getMultiPartItems() != null) {
                if (mediaType == null) {
                    mediaType = MULTIPART_FORM_DATA;
                }
                if (request.isRetry()) { // make streams re-readable
                    // TODO
                }
                return getEntity(request.getMultiPartItems(), mediaType);
            } else if (request.getFormFields() != null) {
                if (mediaType == null) {
                    mediaType = APPLICATION_FORM_URLENCODED;
                }
                return getEntity(request.getFormFields(), mediaType);
            } else {
                Variable body = request.getBody();
                if ((body == null || body.isNull())) {
                    if ("DELETE".equals(method)) {
                        return null; // traditional DELETE, we also support using a request body for DELETE
                    } else {
                        String msg = "request body is required for a " + method + ", please use the 'request' keyword";
                        throw new RuntimeException(msg);
                    }
                }
                return getEntityInternal(body, mediaType);
            }
        } else {
            return null;
        }
    }

    public HttpResponse invoke(ScenarioHttpBuilder request) {
        ScenarioEngine engine = getEngine();
        T body = buildRequestInternal(request);
        String perfEventName = null; // acts as a flag to report perf if not null
        if (engine.runtime.featureRuntime.isPerfMode()) {
            perfEventName = engine.runtime.featureRuntime.getPerfRuntime().getPerfEventName(request, engine);
        }
        try {
            HttpResponse response = makeHttpRequest(body);
            engine.updateConfigCookies(response.getCookies());
            if (perfEventName != null) {
                PerfEvent pe = new PerfEvent(response.getStartTime(), response.getEndTime(), perfEventName, response.getStatus());
                engine.capturePerfEvent(pe);
            }
            return response;
        } catch (Exception e) {
            // edge case when request building failed maybe because of malformed url
            long startTime = engine.getPrevRequest() == null ? System.currentTimeMillis() : engine.getPrevRequest().getStartTime();
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + getRequestUri();
            if (perfEventName != null) {
                PerfEvent pe = new PerfEvent(startTime, endTime, perfEventName, 0);
                engine.capturePerfEvent(pe);
                // failure flag and message should be set by ScenarioContext.logLastPerfEvent()
            }
            engine.logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
    }

}

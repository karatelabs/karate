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
package com.intuit.karate.mock.servlet;

import com.intuit.karate.Logger;
import com.intuit.karate.core.Config;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConstants;
import com.intuit.karate.http.HttpLogger;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Request;
import com.intuit.karate.http.Response;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class MockHttpClient implements HttpClient {

    private final ScenarioEngine engine;
    private final Logger logger;
    private final HttpLogger httpLogger;
    private final Servlet servlet;
    private final ServletContext servletContext;

    public MockHttpClient(ScenarioEngine engine, Servlet servlet, ServletContext servletContext) {
        this.engine = engine;
        logger = engine.logger;
        httpLogger = new HttpLogger(logger);
        this.servlet = servlet;
        this.servletContext = servletContext;
    }

    @Override
    public void setConfig(Config config) {
        // 
    }

    @Override
    public Config getConfig() {
        return engine.getConfig();
    }

    @Override
    public Response invoke(HttpRequest hr) {
        Request request = hr.toRequest();
        request.processBody();
        URI uri;
        try {
            uri = new URI(request.getUrlAndPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(request.getMethod(), uri)
                // Spring is decoding this using ISO 8859-1 instead of UTF-8, so here we explicitly set path info from
                // the URI which decoded it using UTF-8. This prevents Spring from having to decode it itself.
                .pathInfo(uri.getPath());
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((k, vals) -> builder.header(k, vals.toArray()));
            request.getCookies().forEach(c -> {
                Cookie cookie = new Cookie(c.name(), c.value());
                if (c.domain() != null) {
                    cookie.setDomain(c.domain());
                }
                if (c.path() != null) {
                    cookie.setPath(c.path());
                }
                cookie.setHttpOnly(c.isHttpOnly());
                cookie.setSecure(c.isSecure());
                cookie.setMaxAge((int) c.maxAge());
                builder.cookie(cookie);
            });
        }
        builder.content(request.getBody());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockHttpServletRequest req = builder.buildRequest(servletContext);
        if (request.isMultiPart()) {
            request.getMultiParts().forEach((name, v) -> {
                for (Map<String, Object> map : v) {
                    req.addPart(new MockPart(map));
                }
            });
            request.getParams().forEach((name, v) -> {
                for (String value : v) {
                    req.addParameter(name, value);
                }
            });
        }
        Map<String, List<String>> headers = toHeaders(toCollection(req.getHeaderNames()), name -> toCollection(req.getHeaders(name)));
        request.setHeaders(headers);
        httpLogger.logRequest(engine.getConfig(), hr);
        try {
            servlet.service(req, res);
            hr.setEndTimeMillis(System.currentTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        headers = toHeaders(res.getHeaderNames(), name -> res.getHeaders(name));
        javax.servlet.http.Cookie[] cookies = res.getCookies();
        List<String> cookieValues = new ArrayList<>(cookies.length);
        for (javax.servlet.http.Cookie c : cookies) {
            DefaultCookie dc = new DefaultCookie(c.getName(), c.getValue());
            dc.setDomain(c.getDomain());
            dc.setMaxAge(c.getMaxAge());
            dc.setSecure(c.getSecure());
            dc.setPath(c.getPath());
            dc.setHttpOnly(c.isHttpOnly());
            cookieValues.add(ServerCookieEncoder.STRICT.encode(dc));
        }
        if (!cookieValues.isEmpty()) {
            headers.put(HttpConstants.HDR_SET_COOKIE, cookieValues);
        }
        Response response = new Response(res.getStatus(), headers, res.getContentAsByteArray());
        httpLogger.logResponse(getConfig(), hr, response);
        return response;
    }

    private static Collection<String> toCollection(Enumeration<String> values) {
        List<String> list = new ArrayList<>();
        while (values.hasMoreElements()) {
            list.add(values.nextElement());
        }
        return list;
    }

    private static Map<String, List<String>> toHeaders(Collection<String> names, Function<String, Collection<String>> valuesFn) {
        Map<String, List<String>> map = new LinkedHashMap<>(names.size());
        for (String name : names) {
            Collection<String> values = valuesFn.apply(name);
            List<String> list = new ArrayList<>(values.size());
            for (String value : values) {
                list.add(value);
            }
            map.put(name, list);
        }
        return map;
    }

}

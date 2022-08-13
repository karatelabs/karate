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

import com.intuit.karate.resource.ResourceResolver;
import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ResponseBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseBuilder.class);
    
    private byte[] body;
    private Set<Cookie> cookies;
    private Map<String, List<String>> headers;
    private ResourceType resourceType;
    private final ServerConfig config;
    private final ResourceResolver resourceResolver;
    private final RequestCycle requestCycle;
    
    public ResponseBuilder(ServerConfig config, RequestCycle requestCycle) {
        this.config = config;
        resourceResolver = config.getResourceResolver();
        this.requestCycle = requestCycle;
        if (requestCycle != null) {
            headers = requestCycle.getResponse().getHeaders();
        }
    }
    
    public ResponseBuilder body(String body) {
        this.body = FileUtils.toBytes(body);
        return this;
    }
    
    public ResponseBuilder html(String body) {
        body(body);
        contentTypeHtml();
        return this;
    }
    
    public ResponseBuilder body(InputStream body) {
        this.body = FileUtils.toBytes(body);
        return this;
    }
    
    public ResponseBuilder locationHeader(String url) {
        return header(HttpConstants.HDR_LOCATION, url);
    }
    
    public ResponseBuilder contentTypeHtml() {
        resourceType = ResourceType.HTML;
        contentType(resourceType.contentType);
        return this;
    }
    
    public ResponseBuilder contentType(String contentType) {
        if (contentType != null) {
            header(HttpConstants.HDR_CONTENT_TYPE, contentType);
        }
        return this;
    }
    
    public ResponseBuilder cookie(String name, String value) {
        return cookie(name, value, false);
    }
    
    public ResponseBuilder sessionCookie(String value) {
        return cookie(config.getSessionCookieName(), value);
    }
    
    public ResponseBuilder deleteSessionCookie(String value) {
        return cookie(config.getSessionCookieName(), value, true);
    }
    
    private ResponseBuilder cookie(String name, String value, boolean delete) {
        DefaultCookie cookie = new DefaultCookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        if (delete) {
            cookie.setMaxAge(0);
        }
        if (cookies == null) {
            cookies = new HashSet();
        }
        cookies.add(cookie);
        return this;
    }
    
    public ResponseBuilder header(String name, String value) {
        if (headers == null) {
            headers = new LinkedHashMap();
        }
        headers.put(name, Collections.singletonList(value));
        return this;
    }
    
    public ResponseBuilder ajaxRedirect(String url) {
        header(HttpConstants.HDR_HX_REDIRECT, url);
        return this;
    }
    
    public ResponseBuilder session(Session session, boolean newSession) {
        if (session != null && newSession) {
            sessionCookie(session.getId());
        }
        return this;
    }
    
    public Response build() {
        Response response = requestCycle.getResponse();
        ServerContext context = requestCycle.getContext();
        if (context.isClosed()) {
            Session session = requestCycle.getSession();
            if (session != null && !session.isTemporary()) {
                deleteSessionCookie(session.getId());
            }
        }
        if (cookies != null) {
            cookies.forEach(c -> header(HttpConstants.HDR_SET_COOKIE, ServerCookieEncoder.LAX.encode(c)));
        }
        if (resourceType != null && resourceType.isHtml()) {
            if (context.getBodyAppends() != null) {
                String appends = StringUtils.join(context.getBodyAppends(), "\n");
                body = merge(body, FileUtils.toBytes(appends));
            }
        }
        if (context.getRedirectPath() != null) {
            locationHeader(context.getRedirectPath());
            response.setStatus(302);
        }        
        if (context.isApi()) {
            body = response.getBody();
            if (resourceType != null) {
                contentType(resourceType.contentType);
            } else if (body != null) {
                contentType(ResourceType.JSON.contentType);  // default, which can be over-ridden
            }
            Map<String, List<String>> apiHeaders = response.getHeaders();
            if (apiHeaders != null) {
                if (headers == null) {
                    headers = apiHeaders;
                } else {
                    headers.putAll(apiHeaders);
                }
            }
        }
        return buildWithStatus(response.getStatus());
    }
    
    private static byte[] merge(byte[] body, byte[] extra) {
        if (body == null) {
            body = new byte[0];
        }
        byte[] merged = new byte[body.length + extra.length];
        System.arraycopy(body, 0, merged, 0, body.length);
        System.arraycopy(extra, 0, merged, body.length, extra.length);
        return merged;
    }
    
    public Response buildStatic(Request request) { // TODO ETag header handling
        resourceType = request.getResourceType();
        if (resourceType == null) {
            resourceType = ResourceType.BINARY;
        }
        contentType(resourceType.contentType);
        try {
            InputStream is = resourceResolver.resolve(request.getResourcePath()).getStream();
            body(is);
            if (config.isNoCache()) {
                header(HttpConstants.HDR_CACHE_CONTROL, "max-age=0");
            } else {
                header(HttpConstants.HDR_CACHE_CONTROL, "max-age=31536000");
            }
        } catch (Exception e) {
            logger.error("local resource failed: {} - {}", request, e.toString());
        }
        return buildWithStatus(200);
    }
    
    public Response buildWithStatus(int status) {
        return new Response(status, headers, status == 204 ? null : body, resourceType);
    }
    
}

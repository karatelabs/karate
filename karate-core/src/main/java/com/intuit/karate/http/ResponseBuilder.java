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
import com.intuit.karate.JsonUtils;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
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

    public ResponseBuilder(ServerConfig config, RequestCycle rc) {
        this.config = config;
        resourceResolver = config.getResourceResolver();
        if (rc != null) {
            Response response = rc.getResponse();
            headers = response.getHeaders();
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

    public ResponseBuilder trigger(String json) {
        header(HttpConstants.HDR_HX_TRIGGER, JsonUtils.toStrictJson(json));
        return this;
    }

    public ResponseBuilder session(Session session, boolean newSession) {
        if (session != null && newSession) {
            sessionCookie(session.getId());
        }
        return this;
    }

    public Response build(RequestCycle rc) {
        Response response = rc.getResponse();
        ServerContext context = rc.getContext();
        String redirectPath = rc.getRedirectPath();
        if (redirectPath != null) {
            header(HttpConstants.HDR_HX_REDIRECT, redirectPath);
            return status(302);
        }
        List<Map<String, Object>> triggers = context.getResponseTriggers();
        if (triggers != null) {
            Map<String, Object> merged;
            if (triggers.size() == 1) {
                merged = triggers.get(0);
            } else {
                merged = new HashMap();
                for (Map<String, Object> trigger : triggers) {
                    merged.putAll(trigger);
                }
            }
            String json = JsonUtils.toJson(merged);
            header(HttpConstants.HDR_HX_TRIGGER, json);
        }
        if (resourceType != null && resourceType.isHtml() 
                && context.isAjax() && context.getAfterSettleScripts() != null) {
            StringBuilder sb = new StringBuilder();
            for (String js : context.getAfterSettleScripts()) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(js);
            }
            byte[] scriptBytes = FileUtils.toBytes("<script>" + sb.toString() + "</script>");
            if (body == null) {
                body = scriptBytes;
            } else {
                byte[] merged = new byte[body.length + scriptBytes.length];
                System.arraycopy(body, 0, merged, 0, body.length);
                System.arraycopy(scriptBytes, 0, merged, body.length, scriptBytes.length);
                body = merged;
            }
        }
        if (rc.isApi()) {
            resourceType = ResourceType.JSON;
            contentType(resourceType.contentType);
            body = response.getBody();
            Map<String, List<String>> apiHeaders = response.getHeaders();
            if (apiHeaders != null) {
                if (headers == null) {
                    headers = apiHeaders;
                } else {
                    headers.putAll(apiHeaders);
                }
            }
        }
        if (cookies != null) {
            cookies.forEach(c -> header(HttpConstants.HDR_SET_COOKIE, ServerCookieEncoder.LAX.encode(c)));
        }
        return status(response.getStatus());
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
            header(HttpConstants.HDR_CACHE_CONTROL, "max-age=31536000");
        } catch (Exception e) {
            logger.error("local resource failed: {} - {}", request, e.toString());
        }
        return status(200);
    }

    public Response status(int status) {
        return new Response(status, headers, body, resourceType);
    }

}

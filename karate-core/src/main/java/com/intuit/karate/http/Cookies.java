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

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Cookies {

    private static final Logger logger = LoggerFactory.getLogger(Cookies.class);

    private Cookies() {
        // only static methods               
    }

    public static final String NAME = "name";
    public static final String VALUE = "value";
    public static final String WRAP = "wrap";
    public static final String DOMAIN = "domain";
    public static final String PATH = "path";
    public static final String MAX_AGE = "max-age"; // only one with a hyphen
    public static final String SECURE = "secure";
    public static final String HTTP_ONLY = "httponly";
    public static final String SAME_SITE = "samesite";

    public static Map<String, Object> toMap(Cookie cookie) {
        Map<String, Object> map = new HashMap();
        map.put(NAME, cookie.name());
        map.put(VALUE, cookie.value());
        map.put(WRAP, cookie.wrap());
        map.put(DOMAIN, cookie.domain());
        map.put(PATH, cookie.path());
        map.put(MAX_AGE, cookie.maxAge());
        map.put(SECURE, cookie.isSecure());
        map.put(HTTP_ONLY, cookie.isHttpOnly());
        if (cookie instanceof DefaultCookie) {
            DefaultCookie dc = (DefaultCookie) cookie;
            if (dc.sameSite() != null) {
                map.put(SAME_SITE, dc.sameSite().name());
            }
        }
        return map;
    }

    public static Cookie fromMap(Map<String, Object> map) {
        String name = (String) map.get(NAME);
        String value = (String) map.get(VALUE);
        DefaultCookie cookie = new DefaultCookie(name, value);
        Boolean wrap = (Boolean) map.get(WRAP);
        if (wrap != null) {
            cookie.setWrap(wrap);
        }
        String domain = (String) map.get(DOMAIN);
        if (domain != null) {
            cookie.setDomain(domain);
        }
        String path = (String) map.get(PATH);
        if (path != null) {
            cookie.setPath(path);
        }
        Object maxAge = map.get(MAX_AGE);
        if (maxAge != null) {
            cookie.setMaxAge(Long.parseLong(maxAge + ""));
        }
        Boolean secure = (Boolean) map.get(SECURE);
        if (secure != null) {
            cookie.setSecure(secure);
        }
        Boolean httpOnly = (Boolean) map.get(HTTP_ONLY);
        if (httpOnly != null) {
            cookie.setHttpOnly(httpOnly);
        }
        String sameSite = (String) map.get(SAME_SITE);
        if (sameSite != null) {
            cookie.setSameSite(CookieHeaderNames.SameSite.valueOf(sameSite));
        }
        return cookie;
    }

    public static Map<String, Map> normalize(Object mapOrList) {
        Map<String, Map> cookies = new HashMap();
        if (mapOrList instanceof Map) {
            Map<String, Object> map = (Map) mapOrList;
            map.forEach((k, v) -> {
                if (v instanceof String) {
                    Map<String, Object> cookie = new HashMap(2);
                    cookie.put("name", k);
                    cookie.put("value", v);
                    cookies.put(k, cookie);
                } else if (v instanceof Map) {
                    Map<String, Object> cookie = (Map) v;
                    cookie.put("name", k);
                    cookies.put(k, cookie);
                }
            });
        } else if (mapOrList instanceof List) {
            List<Map> list = (List) mapOrList;
            list.forEach(map -> cookies.put((String) map.get("name"), map));
        }
        return cookies;
    }

}

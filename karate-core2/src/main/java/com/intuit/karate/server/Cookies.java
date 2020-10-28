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

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Cookies {

    private Cookies() {
        // only static methods               
    }

    private static final String NAME = "name";
    private static final String VALUE = "value";
    private static final String WRAP = "wrap";
    private static final String DOMAIN = "domain";
    private static final String PATH = "path";
    private static final String MAX_AGE = "max-age"; // only one with a hyphen
    private static final String SECURE = "secure";
    private static final String HTTP_ONLY = "httponly";
    private static final String SAME_SITE = "samesite";
    private static final String EXPIRES = "expires";
    public static final DateTimeFormatter DT_FMT_V1 = DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss z");
    public static final DateTimeFormatter DT_FMT_V2 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");
    public static final DateTimeFormatter DTFMTR_RFC1123 = new DateTimeFormatterBuilder().appendOptional(DT_FMT_V1).appendOptional(DT_FMT_V2).toFormatter();

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
        String maxAge = (String) map.get(MAX_AGE);
        if (maxAge != null) {
            cookie.setMaxAge(Long.parseLong(maxAge));
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
        String expirationDate = (String) map.get(EXPIRES);
        if (isCookieExpired(expirationDate))
        {
            // force cookie to expire.
            cookie.setMaxAge(0);
            cookie.setValue("");
        }
        return cookie;
    }

    private static boolean isCookieExpired(String expirationDate) {
        Date expiresDate = null;
        if (expirationDate != null) {
            try {
                expiresDate = Date.from(ZonedDateTime.parse(expirationDate, DTFMTR_RFC1123).toInstant());
            } catch (DateTimeParseException e) {
                System.out.println("cookie expires date parsing failed: {}" + e.getMessage());
            }
        }
        return expiresDate != null && !expiresDate.after(new Date());
    }

}

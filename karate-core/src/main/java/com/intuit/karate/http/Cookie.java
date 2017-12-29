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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Cookie extends LinkedHashMap<String, String> {
    
    public static final String NAME = "name";
    public static final String VALUE = "value";
    public static final String DOMAIN = "domain";
    public static final String PATH = "path";
    public static final String VERSION = "version";
    public static final String EXPIRES = "expires";
    public static final String MAX_AGE = "max-age";
    public static final String SECURE = "secure";
    public static final String PERSISTENT = "persistent";
    public static final String HTTP_ONLY = "http-only";    
    
    // cookies can be a map of maps, so some extra processing
    public static List<Cookie> toCookies(Map<String, Object> map) {
        if (map == null) {
            return Collections.EMPTY_LIST;
        }
        List<Cookie> cookies = new ArrayList(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object o = entry.getValue();
            if (o instanceof Map) {
                cookies.add(new Cookie((Map) o));
            } else if (o != null) {
                cookies.add(new Cookie(entry.getKey(), o.toString()));
            }
        }
        return cookies;
    }
    
    public Cookie(Map<String, String> map) {
        super(map);
    }
    
    public Cookie(String name, String value) {
        put(NAME, name);
        put(VALUE, value);
    }
    
    public String putIfValueNotNull(String key, String value) {
        return value == null ? null : put(key, value);
    }

    public String getName() {
        return get(NAME);
    }

    public String getValue() {
        return get(VALUE);
    }        
    
}

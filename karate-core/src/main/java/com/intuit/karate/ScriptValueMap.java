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
package com.intuit.karate;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScriptValueMap extends HashMap<String, ScriptValue> {

    public static final String VAR_RESPONSE = "response";
    public static final String VAR_RESPONSE_BYTES = "responseBytes";
    public static final String VAR_RESPONSE_COOKIES = "responseCookies";
    public static final String VAR_RESPONSE_HEADERS = "responseHeaders";
    public static final String VAR_RESPONSE_STATUS = "responseStatus";
    public static final String VAR_RESPONSE_DELAY = "responseDelay";
    public static final String VAR_RESPONSE_TIME = "responseTime";
    public static final String VAR_RESPONSE_TYPE = "responseType";

    public static final String VAR_REQUEST = "request";
    public static final String VAR_REQUEST_BYTES = "requestBytes";
    public static final String VAR_REQUEST_URL_BASE = "requestUrlBase";
    public static final String VAR_REQUEST_URI = "requestUri";
    public static final String VAR_REQUEST_METHOD = "requestMethod";
    public static final String VAR_REQUEST_HEADERS = "requestHeaders";
    public static final String VAR_REQUEST_PARAMS = "requestParams";
    public static final String VAR_REQUEST_BODY = "requestBody";
    public static final String VAR_REQUEST_TIME_STAMP = "requestTimeStamp";

    public ScriptValue put(String key, Object value) {
        ScriptValue sv = new ScriptValue(value);
        return super.put(key, sv);
    }

    public <T> T get(String key, Class<T> clazz) {
        ScriptValue sv = get(key);
        if (sv == null) {
            return null;
        }
        return sv.getValue(clazz);
    }

    public Map<String, Object> toPrimitiveMap() {
        return new ScriptObjectMap(this);
    }

    public ScriptValueMap copy(boolean deep) {
        // prevent json conversion failures for gatling weirdness
        boolean deepFixed = containsKey("__gatling") ? false : deep;
        ScriptValueMap copy = new ScriptValueMap();
        forEach((k, v) -> copy.put(k, deepFixed ? v.copy(true) : v));
        return copy;
    }

}

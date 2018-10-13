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
    public static final String VAR_RESPONSE_TIME = "responseTime";

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
        ScriptValueMap copy = new ScriptValueMap();
        forEach((k, v) -> copy.put(k, deep ? v.copy() : v));
        return copy;
    }

}

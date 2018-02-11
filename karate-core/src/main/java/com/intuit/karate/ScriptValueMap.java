package com.intuit.karate;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScriptValueMap extends HashMap<String, ScriptValue> {

    public static final String VAR_RESPONSE = "response";
    public static final String VAR_RESPONSE_COOKIES = VAR_RESPONSE+"Cookies";
    public static final String VAR_RESPONSE_HEADERS = VAR_RESPONSE+"Headers";
    public static final String VAR_RESPONSE_STATUS = VAR_RESPONSE+"Status";
    public static final String VAR_RESPONSE_TIME = VAR_RESPONSE+"Time";

    public static final String VAR_REQUEST = "request";
    public static final String VAR_REQUEST_URL_BASE = VAR_REQUEST+"UrlBase";
    public static final String VAR_REQUEST_URI = VAR_REQUEST+"Uri";
    public static final String VAR_REQUEST_METHOD = VAR_REQUEST+"Method";
    public static final String VAR_REQUEST_HEADERS = VAR_REQUEST+"Headers";
    public static final String VAR_REQUEST_PARAMS = VAR_REQUEST+"Params";
    public static final String VAR_REQUEST_BODY = VAR_REQUEST+"Body";

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

    public ScriptValueMap copy() {
        ScriptValueMap copy = new ScriptValueMap();
        forEach((k, v) -> copy.put(k, v));
        return copy;
    }

}

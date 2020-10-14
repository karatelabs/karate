package com.intuit.karate;

import com.intuit.karate.http.Cookie;

import java.util.HashMap;
import java.util.Map;

/**
 * Cookie Utility method for UI calls.
 */
public class UICookieUtils {

    /**
     * creates a map corresponding to cookie. This is used by appropriate drivers.
     * Karate Cookie Map is of type <String, String> vs the internal framwork expects appropriate types -
     * for ex boolean for secure and persistent keys.
     *
     * Solution is to use this utility or remove those keys from Karate Cookie Map.
     * @param karateCookie cookie
     * @return Map
     */
    public static Map<String,Object> convertCookieToActualMap(Cookie karateCookie)
    {
        Map<String,Object> cookieMap = new HashMap();
        cookieMap.put(Cookie.NAME, karateCookie.getName());
        cookieMap.put(Cookie.VALUE, karateCookie.getValue());
        cookieMap.put(Cookie.PATH, karateCookie.get(Cookie.PATH));
        cookieMap.put(Cookie.DOMAIN, karateCookie.get(Cookie.DOMAIN));
        cookieMap.put(Cookie.SECURE, Boolean.valueOf(karateCookie.get(Cookie.SECURE)));
        cookieMap.put(Cookie.PERSISTENT, Boolean.valueOf(karateCookie.get(Cookie.PERSISTENT)));
        return cookieMap;
    }
}

package com.intuit.karate;

import com.intuit.karate.http.Cookie;

import java.util.HashMap;
import java.util.Map;

public class UICookieUtils {

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

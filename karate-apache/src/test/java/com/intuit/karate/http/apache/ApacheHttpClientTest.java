package com.intuit.karate.http.apache;

import com.intuit.karate.CallContext;
import com.intuit.karate.Config;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.http.Cookie;
import org.apache.http.client.CookieStore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intuit.karate.http.Cookie.*;
import static com.intuit.karate.http.HttpClient.construct;
import static org.junit.Assert.assertEquals;

public class ApacheHttpClientTest {

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClientTest.class);

    private ScenarioContext getContext() {
        FeatureContext featureContext = FeatureContext.forEnv();
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }

    private Config getConfig() {
        return new Config();
    }

    private Map<String, String> getCookieMapWithExpiredDate() {
        ZonedDateTime currentDate = ZonedDateTime.now();
        Map<String, String> cookieMap = new LinkedHashMap<>();
        cookieMap.put(NAME, "testCookie");
        cookieMap.put(VALUE, "tck");
        cookieMap.put(DOMAIN, ".com");
        cookieMap.put(PATH, "/");
        cookieMap.put(EXPIRES,currentDate.minusDays(1).format(DTFMTR_RFC1123));
        return cookieMap;
    }

    private Map<String, String> getCookieMapWithNonExpiredDate() {
        ZonedDateTime currentDate = ZonedDateTime.now();
        Map<String, String> cookieMap = new LinkedHashMap<>();
        cookieMap.put(NAME, "testCookie");
        cookieMap.put(VALUE, "tck");
        cookieMap.put(DOMAIN, ".com");
        cookieMap.put(PATH, "/");
        cookieMap.put(EXPIRES, currentDate.plusDays(1).format(DTFMTR_RFC1123));
        return cookieMap;
    }

    @Test
    public void testExpiredCookieIsRemoved() throws NoSuchFieldException, IllegalAccessException {
        com.intuit.karate.http.Cookie c = new Cookie(getCookieMapWithExpiredDate());
        ApacheHttpClient httpClient = (ApacheHttpClient) construct(getConfig(), getContext());
        httpClient.buildCookie(c);

        Field cookieStoreField = httpClient.getClass().getDeclaredField("cookieStore");
        cookieStoreField.setAccessible(true);
        CookieStore fieldValue = (CookieStore) cookieStoreField.get(httpClient);
        assertEquals(0, fieldValue.getCookies().size());
    }

    @Test
    public void testNonExpiredCookieIsPersisted() throws NoSuchFieldException, IllegalAccessException {
        com.intuit.karate.http.Cookie c = new Cookie(getCookieMapWithNonExpiredDate());
        ApacheHttpClient httpClient = (ApacheHttpClient) construct(getConfig(), getContext());
        httpClient.buildCookie(c);

        Field cookieStoreField = httpClient.getClass().getDeclaredField("cookieStore");
        cookieStoreField.setAccessible(true);
        CookieStore fieldValue = (CookieStore) cookieStoreField.get(httpClient);
        assertEquals(1, fieldValue.getCookies().size());
    }
}



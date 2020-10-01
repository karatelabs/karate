package com.intuit.karate.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class JsonUtilsTest {

    static final Logger logger = LoggerFactory.getLogger(JsonUtilsTest.class);

    @Test
    void testParse() {
        String temp = JsonUtils.toStrictJson("{redirect:{url:'/index'}}");
        assertEquals(temp, "{\"redirect\":{\"url\":\"\\/index\"}}");
    }
    
    @Test
    void testDetect() {
        assertTrue(JsonUtils.isJson("{}"));
        assertTrue(JsonUtils.isJson("[]"));
        assertTrue(JsonUtils.isJson(" {}"));
        assertTrue(JsonUtils.isJson(" []"));
        assertFalse(JsonUtils.isJson(null));
        assertFalse(JsonUtils.isJson(""));
    }

}

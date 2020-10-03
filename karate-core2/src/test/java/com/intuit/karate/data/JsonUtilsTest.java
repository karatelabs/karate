package com.intuit.karate.data;

import com.intuit.karate.match.Match;
import com.intuit.karate.runtime.SimplePojo;
import java.util.Map;
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
    
    @Test
    void testBeanConversion() {
        SimplePojo pojo = new SimplePojo();
        String s = JsonUtils.toJson(pojo);
        assertEquals("{\"bar\":0,\"foo\":null}", s);
        Map<String, Object> map = new Json(pojo).asMap();
        assertTrue(Match.that(map).isEqualToJson("{ foo: null, bar: 0 }").pass);
    }

}

package com.intuit.karate.http;

import com.intuit.karate.core.ScenarioEngine;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class HttpRequestBuilderTest {
    
    static final Logger logger = LoggerFactory.getLogger(HttpRequestBuilderTest.class);
    
    HttpRequestBuilder http;
    
    @BeforeEach
    void beforeEach() {
        ScenarioEngine se = ScenarioEngine.forTempUse();
        http = new HttpRequestBuilder(HttpClientFactory.DEFAULT.create(se));
    }
    
    @Test
    void testUrlAndPath() {
        http.url("http://host/foo");
        assertEquals("http://host/foo", http.getUri());
        http.path("/bar");
        assertEquals("http://host/foo/bar", http.getUri());        
    }
    
    @Test
    void testUrlAndPathWithSlash() {
        http.url("http://host/foo/");
        assertEquals("http://host/foo/", http.getUri());
        http.path("/bar/");
        assertEquals("http://host/foo/bar", http.getUri());        
    }    
    
}

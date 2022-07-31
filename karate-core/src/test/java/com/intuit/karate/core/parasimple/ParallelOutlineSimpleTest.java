package com.intuit.karate.core.parasimple;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.http.HttpServer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelOutlineSimpleTest {
    
    static final Logger logger = LoggerFactory.getLogger(ParallelOutlineSimpleTest.class);

    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        MockHandler mock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/parallel/mock.feature"));
        server = HttpServer.handler(mock).build();
    }

    @Test
    void testParallelOutline() {
        Results results = Runner.path(
                "classpath:com/intuit/karate/core/parasimple/parallel-outline-simple.feature")
                .configDir("classpath:com/intuit/karate/core/parasimple")
                .systemProperty("server.port", server.getPort() + "")
                .parallel(3);
        assertEquals(0, results.getFeaturesFailed());
        assertEquals(0, results.getScenariosFailed());
        assertEquals(0, results.getFailCount());
    }    
    
}

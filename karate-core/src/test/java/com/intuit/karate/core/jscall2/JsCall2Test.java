package com.intuit.karate.core.jscall2;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.http.HttpServer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JsCall2Test {
    
    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        MockHandler mock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/jscall2/mock.feature"));
        server = HttpServer.handler(mock).build();
    }    

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/jscall2")
                .configDir("classpath:com/intuit/karate/core/jscall2")
                .systemProperty("server.port", server.getPort() + "")
                .parallel(5);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}

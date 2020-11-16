package com.intuit.karate.core.mock;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MockTest {

    static final Logger logger = LoggerFactory.getLogger(MockTest.class);

    static HttpServer startMockServer() {
        MockHandler mock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/mock/_mock.feature"));
        HttpServer server = new HttpServer(0, mock);
        System.setProperty("karate.server.port", server.getPort() + "");
        return server;
    }

    @BeforeAll
    static void beforeAll() {
        startMockServer();
    }

    @Test
    void testMock() {
        Results results = Runner.path("classpath:com/intuit/karate/core/mock")
                .configDir("classpath:com/intuit/karate/core/mock")
                .tags("~@ignore").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

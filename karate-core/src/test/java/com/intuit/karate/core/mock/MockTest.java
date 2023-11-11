package com.intuit.karate.core.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.MockServer;
import com.intuit.karate.http.HttpServer;
import java.util.concurrent.atomic.AtomicInteger;
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

    static final AtomicInteger count = new AtomicInteger(0);

    static HttpServer startMockServer() {
        MockServer server = MockServer.featurePaths(
                "classpath:com/intuit/karate/core/mock/_simple.feature",
                "classpath:com/intuit/karate/core/mock/_mock.feature")
                .pathPrefix("/") // ensure cli default works
                .interceptor((req, res, scenario) ->
                        logger.debug("interceptor has been called %s times"
                            .formatted(count.incrementAndGet())))
                .build();
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
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
        assertTrue(count.get() > 0);
    }

}

package com.intuit.karate.core.mock;

import com.intuit.karate.core.MockServer;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpServer;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MockRunner {

    static final Logger logger = LoggerFactory.getLogger(MockRunner.class);

    static HttpServer startMockServer() {
        MockServer server = MockServer
                .feature("classpath:com/intuit/karate/core/mock/_mock.feature")
                .http(0).build();
        System.setProperty("karate.server.port", server.getPort() + "");
        return server;
    }

    @BeforeAll
    static void beforeAll() {
        startMockServer();
    }

    Results results;

    private void run(String name) {
        results = Runner.path("classpath:com/intuit/karate/core/mock/" + name)
                .configDir("classpath:com/intuit/karate/core/mock")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

    @Test
    void testBinary() {
        run("binary.feature");
    }

}

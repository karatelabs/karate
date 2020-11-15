package com.intuit.karate.runtime.mock;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.runtime.MockHandler;
import java.io.File;
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
        File file = FileUtils.getFileRelativeTo(MockTest.class, "_mock.feature");
        MockHandler mock = new MockHandler(FeatureParser.parse(file));
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
        Results results = Runner.path("classpath:com/intuit/karate/runtime/mock")
                .configDir("classpath:com/intuit/karate/runtime/mock")
                .tags("~@ignore").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

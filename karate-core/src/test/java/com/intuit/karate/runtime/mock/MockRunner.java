package com.intuit.karate.runtime.mock;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.runtime.*;
import com.intuit.karate.server.HttpServer;
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
class MockRunner {

    static final Logger logger = LoggerFactory.getLogger(MockRunner.class);

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

    Results results;

    private void run(String name) {
        results = Runner.path("classpath:com/intuit/karate/runtime/mock/" + name)
                .configDir("classpath:com/intuit/karate/runtime/mock")
                .tags("~@ignore").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

    @Test
    void testBinary() {
        run("binary.feature");
    }

}

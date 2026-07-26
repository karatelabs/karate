package io.karatelabs.http.contenttype;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for issue #2977: a response with Content-Type
 * "text/plain;charset=UTF-8" whose body happens to start with '[' or '{'
 * (e.g. a TOML document) must NOT be auto-parsed as JSON.
 */
class ContentTypeTest {

    private static MockServer mockServer;

    @BeforeAll
    static void startMockServer() {
        mockServer = MockServer.feature("classpath:io/karatelabs/http/contenttype/mock-server.feature").start();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stopAndWait();
        }
    }

    @Test
    void testTextPlainBodyStartingWithBracketIsNotParsedAsJson() {
        SuiteResult result = Runner.path("classpath:io/karatelabs/http/contenttype/content-type.feature")
                .systemProperty("server.port", mockServer.getPort() + "")
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);
        assertEquals(0, result.getScenarioFailedCount(), String.join("\n", result.getErrors()));
    }
}

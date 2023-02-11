package demo;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.junit5.Karate;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class DemoRunner {

    @BeforeAll
    static void beforeAll() {
        TestBase.startServer();
    }

    @Karate.Test
    Karate testAbort() {
        // skip 'callSingle' in karate-config.js
        return Karate.run("classpath:demo/abort").karateEnv("mock");
    }

    @Test
    void testEncodingParallel() {
        Results results = Runner.path("classpath:demo/encoding")
                .outputCucumberJson(true)
                .parallel(5);
        DemoTestParallel.generateReport(results.getReportDir());
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}

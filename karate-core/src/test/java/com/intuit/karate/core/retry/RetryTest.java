package com.intuit.karate.core.retry;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.MockHandler;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.http.HttpServer;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class RetryTest {
    
    static final Logger logger = LoggerFactory.getLogger(RetryTest.class);
    
    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        MockHandler mock = new MockHandler(Feature.read("classpath:com/intuit/karate/core/parallel/mock.feature"));
        server = new HttpServer(0, mock);
    }    

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/retry/test.feature")
                .reportDir("target/retry-test")
                .parallel(1);
        assertEquals(1, results.getFailCount());
        List<ScenarioResult> failed = results.getScenarioResults().filter(sr -> sr.isFailed()).collect(Collectors.toList());
        assertEquals(1, failed.size());
        Scenario scenario = failed.get(0).getScenario();
        Step step = scenario.getSteps().get(0);
        assertEquals("assert 1 == 2", step.getText());
        step.setText("assert 2 == 2");
        ScenarioResult sr = results.getSuite().retryScenario(scenario);
        assertFalse(sr.isFailed());
        results = results.getSuite().updateResults(sr);
        assertEquals(0, results.getFailCount());
    }    
    
}

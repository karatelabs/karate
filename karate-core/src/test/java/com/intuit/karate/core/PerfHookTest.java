package com.intuit.karate.core;

import com.intuit.karate.PerfHook;
import com.intuit.karate.Runner;
import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.http.HttpRequest;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class PerfHookTest {

    static final Logger logger = LoggerFactory.getLogger(PerfHookTest.class);

    static MockServer server;

    @BeforeAll
    static void beforeAll() {
        server = MockServer
                .feature("classpath:com/intuit/karate/core/perf-mock.feature")
                .http(0).build();
        System.setProperty("karate.server.port", server.getPort() + "");
    }

    @BeforeEach
    void beforeEach() {
        eventName = null;
        featureResult = null;
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void testPerfHook1() {
        // run a passing scenario
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync(Runner.builder().tags("@name=pass"), "classpath:com/intuit/karate/core/perf.feature", arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertFalse(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 1);
        assertEquals(featureResult.getFailedCount(), 0);
        matchContains(featureResult.getVariables(), "{ configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook2() {
        // run a scenario which fails the status check
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync(Runner.builder().tags("@name=failStatus"), "classpath:com/intuit/karate/core/perf.feature", arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        matchContains(featureResult.getVariables(), "{ configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook3() {
        // run a scenario which fails the response match
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync(Runner.builder().tags("@name=failResponse"), "classpath:com/intuit/karate/core/perf.feature", arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        matchContains(featureResult.getVariables(), "{ configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook4() {
        // run a scenario without passing a required argument
        Runner.callAsync(Runner.builder().tags("@name=pass"), "classpath:com/intuit/karate/core/perf.feature", null, perfHook);
        assertNull(eventName);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        match(featureResult.getVariables(), "{ configSource: 'normal', functionFromKarateBase: '#notnull' }");
    }

    @Test
    void testPerfHook5() {
        // run a scenario which doesn't exist
        Runner.callAsync(Runner.builder().tags("@name=doesntExist"), "classpath:com/intuit/karate/core/perf.feature", null, perfHook);
        assertNull(eventName);
        assertNotNull(featureResult);
        assertTrue(featureResult.isEmpty());
        assertFalse(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 0);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 0);
        assertNull(featureResult.getVariables());
    }

    @Test
    void testPerfHook6() {
        // run a feature which doesn't exist
        String feature = "com/intuit/karate/core/doesntExist.feature";
        try {
            Runner.callAsync(Runner.builder(), "classpath:" + feature, null, perfHook);
            fail("we expected execution to fail");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "not found: " + feature);
        }
        assertNull(eventName);
        assertNull(featureResult);
    }

    String eventName;
    FeatureResult featureResult;
    PerfHook perfHook = new PerfHook() {

        @Override
        public String getPerfEventName(HttpRequest request, ScenarioRuntime sr) {
            return request.getUrl();
        }

        @Override
        public void reportPerfEvent(PerfEvent event) {
            eventName = event.getName();
            logger.debug("perf event: {}", eventName);
        }

        @Override
        public void submit(Runnable runnable) {
            logger.debug("submit called");
            runnable.run();
        }

        @Override
        public void afterFeature(FeatureResult fr) {
            featureResult = fr;
            logger.debug("afterFeature called");
        }

        @Override
        public void pause(Number millis) {
            
        }                

    };

}

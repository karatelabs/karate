package com.intuit.karate.core;

import com.intuit.karate.PerfHook;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

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
        // Run a passing scenario
        List<String> tags = Collections.singletonList("@name=pass");
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertFalse(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 1);
        assertEquals(featureResult.getFailedCount(), 0);
        match(featureResult.getVariables(), "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook2() {
        // Run a scenario which fails the status check
        List<String> tags = Collections.singletonList("@name=failStatus");
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        match(featureResult.getVariables(), "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook3() {
        // Run a scenario which fails the response match
        List<String> tags = Collections.singletonList("@name=failResponse");
        String bar = UUID.randomUUID().toString().replaceAll("-", "");
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        match(featureResult.getVariables(), "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook4() {
        // Run a scenario without passing a required argument
        List<String> tags = Collections.singletonList("@name=pass");
        Map<String, Object> arg = Collections.emptyMap();
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertNull(eventName);
        assertNotNull(featureResult);
        assertFalse(featureResult.isEmpty());
        assertTrue(featureResult.isFailed());
        assertEquals(featureResult.getScenarioCount(), 1);
        assertEquals(featureResult.getPassedCount(), 0);
        assertEquals(featureResult.getFailedCount(), 1);
        match(featureResult.getVariables(), "{ configSource: 'normal' }");
    }

    @Test
    void testPerfHook5() {
        // Run a scenario which doesn't exist
        List<String> tags = Collections.singletonList("@name=doesntExist");
        Map<String, Object> arg = Collections.emptyMap();
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
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
        // Run a feature which doesn't exist
        List<String> tags = Collections.emptyList();
        Map<String, Object> arg = Collections.emptyMap();
        String feature = "com/intuit/karate/core/doesntExist.feature";
        try {
            Runner.callAsync("classpath:" + feature, tags, arg, perfHook);
            fail("we expected execution to fail");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "not found: " + feature);
        }
        assertNull(eventName);
        assertNull(featureResult);
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
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
    };

}

package com.intuit.karate.core;

import com.intuit.karate.PerfHook;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        failed = null;
        vars = null;
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void testPerfHook1() {
        // Run a passing scenario
        List<String> tags = Collections.singletonList("@name=pass");
        String bar = "one";
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertFalse(failed);
        match(vars, "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook2() {
        // Run a scenario which fails the status check
        List<String> tags = Collections.singletonList("@name=failStatus");
        String bar = "two";
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertTrue(failed);
        match(vars, "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook3() {
        // Run a scenario which fails the response match
        List<String> tags = Collections.singletonList("@name=failResponse");
        String bar = "three";
        Map<String, Object> arg = Collections.singletonMap("bar", bar);
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=" + bar);
        assertTrue(failed);
        match(vars, "{ bar: '" + bar + "', responseHeaders: { content-type: ['application/json'], content-length: [#string], server: [#string], date: [#string] }, configSource: 'normal', responseStatus: 200, response: { foo: ['" + bar + "'] } }");
    }

    @Test
    void testPerfHook4() {
        // Run a scenario without passing a required argument
        List<String> tags = Collections.singletonList("@name=pass");
        Map<String, Object> arg = Collections.emptyMap();
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertNull(eventName);
        assertTrue(failed);
        match(vars, "{ configSource: 'normal' }");
    }

    @Test
    void testPerfHook5() {
        // Run a scenario which doesn't exist
        List<String> tags = Collections.singletonList("@name=doesntExist");
        Map<String, Object> arg = Collections.emptyMap();
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", tags, arg, perfHook);
        assertNull(eventName);
        assertFalse(failed);
        assertNull(vars);
    }

    @Test
    void testPerfHook6() {
        // Run a feature which doesn't exist
        List<String> tags = Collections.emptyList();
        Map<String, Object> arg = Collections.emptyMap();
        boolean pass = false;
        try {
            Runner.callAsync("classpath:com/intuit/karate/core/doesntExist.feature", tags, arg, perfHook);
        } catch (RuntimeException e) {
            pass = true;
        }
        assertTrue(pass);
        assertNull(eventName);
        assertNull(failed);
        assertNull(vars);
    }

        private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }
    
    String eventName;
    Boolean failed;
    Map<String, Object> vars;

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
        public void afterFeature(boolean failed, Map<String, Object> vars) {
            logger.debug("afterFeature called");
            PerfHookTest.this.failed = failed;
            PerfHookTest.this.vars = vars;
        }
    };

}

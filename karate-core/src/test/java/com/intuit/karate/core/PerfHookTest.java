package com.intuit.karate.core;

import com.intuit.karate.PerfHook;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpRequest;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
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

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void testPerfHook() {
        Map<String, Object> arg = Collections.singletonMap("foo", "bar");
        Runner.callAsync("classpath:com/intuit/karate/core/perf.feature", Collections.EMPTY_LIST, arg, perfHook);
        assertEquals(eventName, "http://localhost:" + server.getPort() + "/hello?foo=bar");
    }
    
    String eventName;

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
            logger.debug("afterFeature called");
        }
    };

}

package com.intuit.karate.runtime;

import static com.intuit.karate.runtime.RuntimeUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ScenarioRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioRuntimeTest.class);

    @Test
    void testDefAndMatch() {
        ScenarioRuntime sr = runScenario(
                "def a = 1 + 2",
                "match a == 3"
        );
        Variable a = sr.engine.eval("a");
        assertEquals(3, a.<Number>getValue());
        assertFalse(sr.result.isFailed());
        sr = runScenario(
                "def a = 1 + 2",
                "match a == 4"
        );
        assertTrue(sr.result.isFailed());
    }

}

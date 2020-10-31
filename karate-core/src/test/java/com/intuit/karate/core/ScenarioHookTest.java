package com.intuit.karate.core;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ScenarioHookTest {

    @Test
    void testStopIfScenarioHasNoTags() {
        String path = "classpath:com/intuit/karate/core/test-hook-notags.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeatureCount());
        assertEquals(1, results.getFailCount());
    }

    @Test
    void testHookForExamplesWithTags() {
        String path = "classpath:com/intuit/karate/core/test-hook-multiexample.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeatureCount());
        assertEquals(7, results.getScenarioCount());
        assertEquals(0, results.getFailCount());
    }

}

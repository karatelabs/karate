package com.intuit.karate.core.runner.hooks;

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
        String path = "classpath:com/intuit/karate/core/runner/hooks/test-hook-notags.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeaturesTotal());
        assertEquals(1, results.getFailCount());
    }

    @Test
    void testHookForExamplesWithTags() {
        String path = "classpath:com/intuit/karate/core/runner/hooks/test-hook-multiexample.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeaturesTotal());
        assertEquals(7, results.getScenariosTotal());
        assertEquals(0, results.getFailCount());
    }

}

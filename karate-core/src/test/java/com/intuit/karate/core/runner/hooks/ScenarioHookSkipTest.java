package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ScenarioHookSkipTest {

    @Test
    void testStopIfScenarioHasNoTags() {
        String path = "classpath:com/intuit/karate/core/runner/hooks/test-hook-skip.feature";
        Results results = Runner.path(path).hook(new SkipHook()).parallel(1);
        assertEquals(1, results.getFeaturesTotal());
        assertEquals(1, results.getScenariosPassed());
    }

}

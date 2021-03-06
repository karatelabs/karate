package com.intuit.karate.core.hooks;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.ScenarioRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HooksTest {

    @Test
    void testDynamicOutlineHook() {
        TestRuntimeHook testRuntimeHook = new TestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-dynamic-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);
        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void testDynamicOutlineHookOnlyOnceBeforeScenario() {
        TestRuntimeHook testRuntimeHook = new TestRuntimeHook() {
            @Override
            public boolean beforeScenario(ScenarioRuntime sr) {
                // only track the count the execution on the first scenario example
                // for unit tests only
                // but still execute the scenario!
                if (sr.scenario.isFirstExample()) {
                    return super.beforeScenario(sr);
                } else {
                    return true;
                }
            }

            @Override
            public void afterScenario(ScenarioRuntime sr) {
                // only track the count the execution on the last scenario example
                // for unit tests only
                if (sr.scenario.isLastExample()) {
                    super.afterScenario(sr);
                }
            }
        };
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-dynamic-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);
        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        // only one of each cause our logic is only counting the execution for the first Scenario Outline and last
        // meant to replicate if we wanted a beforeScenario() and an afterScenario() before all Scenario Outline execution and
        // a afterScenario() after all Scenario Outline(s) are executed
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void testDynamicOutlineHookNoStepExecution() {
        NoStepTestRuntimeHook testRuntimeHook = new NoStepTestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-dynamic-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        // yes it will fail because we're not executing steps so the background '* def cats' won't be evaluated
        assertEquals(1, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        // this unit test is also valuable to check that in the error case we are not executing these beforeBackground() / afterBackground() twice
        // potentially needed for parallel cases
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void testDynamicOutlineHookNoScenarioExecution() {
        NoScenarioTestRuntimeHook testRuntimeHook = new NoScenarioTestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-dynamic-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        // 3 because steps are added again to each scenario outline to execute again ...
        // probably wrongly for dynamic scenarios as those steps were already evaluated (but maybe context is not shared)
        // TODO: need to think it through
        assertEquals(3, testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void testDynamicOutlineHookNoFeatureExecution() {
        NoFeatureTestRuntimeHook testRuntimeHook = new NoFeatureTestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-dynamic-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        // TODO: inconsistency
        // if Hook sets the Feature as not selected for execution, we shouldn't run the background
        // but to know that we won't run we need to run the hook before the background so got to think about it
        // atm - background.selectedForExecution is returning true
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void testOutlineHookNoStepExecutionWithoutError() {
        NoStepTestRuntimeHook testRuntimeHook = new NoStepTestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        // not dynamic scenario, so background is not executed
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum());
    }

    // non-dynamic outline tests

    @Test
    void testOutlineHook() {
        TestRuntimeHook testRuntimeHook = new TestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void testOutlineHookOnlyOnceBeforeScenario() {
        TestRuntimeHook testRuntimeHook = new TestRuntimeHook() {
            @Override
            public boolean beforeScenario(ScenarioRuntime sr) {
                // only track the count the execution on the first scenario example
                // for unit tests only
                // but still execute the scenario!
                if (sr.scenario.isFirstExample()) {
                    return super.beforeScenario(sr);
                } else {
                    return true;
                }
            }

            @Override
            public void afterScenario(ScenarioRuntime sr) {
                // only track the count the execution on the last scenario example
                // for unit tests only
                if (sr.scenario.isLastExample()) {
                    super.afterScenario(sr);
                }
            }
        };
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-outline.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        // only one of each cause our logic is only counting the execution for the first Scenario Outline and last
        // meant to replicate if we wanted a beforeScenario() and an afterScenario() before all Scenario Outline execution and
        // a afterScenario() after all Scenario Outline(s) are executed
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

    @Test
    void testScenarioHook() {
        TestRuntimeHook testRuntimeHook = new TestRuntimeHook();
        Results results = Runner.path("classpath:com/intuit/karate/core/hooks/hook-scenario.feature")
                .hook(testRuntimeHook)
                .configDir("classpath:com/intuit/karate/core/hooks")
                .parallel(1);

        assertEquals(0, results.getFailCount());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeSuite").get("suite"));
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterSuite").get("suite"));

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeFeature").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterFeature").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("beforeBackground").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, testRuntimeHook.getRuntimeHookTracker().get("afterBackground").values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("beforeScenario").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, testRuntimeHook.getRuntimeHookTracker().get("afterScenario").values().stream().mapToInt(Integer::intValue).sum());

        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("beforeStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
        assertTrue(testRuntimeHook.getRuntimeHookTracker().get("afterStep").values().stream().mapToInt(Integer::intValue).sum() > 0);
    }

}

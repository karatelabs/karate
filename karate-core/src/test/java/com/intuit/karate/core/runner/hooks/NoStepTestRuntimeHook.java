package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;

public class NoStepTestRuntimeHook extends TestRuntimeHook {

    @Override
    public boolean beforeStep(Step step, ScenarioRuntime sr) {
        return false;
    }

    @Override
    public void afterStep(StepResult result, ScenarioRuntime sr) {
        // don't count if if not executing
    }
}

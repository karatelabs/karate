package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.core.ScenarioRuntime;

public class NoScenarioTestRuntimeHook extends TestRuntimeHook {

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        super.beforeScenario(sr);
        return false;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {
        // don't count if not executing
    }

}

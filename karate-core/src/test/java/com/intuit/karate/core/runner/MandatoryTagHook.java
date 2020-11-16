package com.intuit.karate.core.runner;

import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.core.Tag;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.RuntimeHook;
import com.intuit.karate.core.ScenarioRuntime;

/**
 *
 * @author pthomas3
 */
public class MandatoryTagHook implements RuntimeHook {

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        if (sr.caller.depth > 0) {
            return true; // only enforce tags for top-level scenarios (not called ones)
        }
        boolean found = false;
        for (Tag tag : sr.tags) {
            if ("testId".equals(tag.getName())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("testId tag not present at line: " + sr.scenario.getLine());
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {

    }

    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        return true;
    }

    @Override
    public void afterFeature(FeatureRuntime fr) {

    }

    @Override
    public void beforeSuite(SuiteRuntime sr) {

    }

    @Override
    public void afterSuite(SuiteRuntime sr) {

    }

    @Override
    public boolean beforeStep(Step step, ScenarioRuntime sr) {
        return true;
    }

    @Override
    public void afterStep(StepResult result, ScenarioRuntime sr) {

    }
}

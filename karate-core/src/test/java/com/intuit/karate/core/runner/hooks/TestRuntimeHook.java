package com.intuit.karate.core.runner.hooks;

import com.intuit.karate.RuntimeHook;
import com.intuit.karate.Suite;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;

import java.util.HashMap;
import java.util.Map;

public class TestRuntimeHook implements RuntimeHook {

    private final Map<String, Map<String, Integer>> runtimeHookTracker = new HashMap<>();

    public TestRuntimeHook() {
        runtimeHookTracker.put("beforeBackground", new HashMap<>());
        runtimeHookTracker.put("afterBackground", new HashMap<>());
        runtimeHookTracker.put("beforeScenario", new HashMap<>());
        runtimeHookTracker.put("afterScenario", new HashMap<>());
        runtimeHookTracker.put("beforeFeature", new HashMap<>());
        runtimeHookTracker.put("afterFeature", new HashMap<>());
        runtimeHookTracker.put("beforeStep", new HashMap<>());
        runtimeHookTracker.put("afterStep", new HashMap<>());
        runtimeHookTracker.put("beforeSuite", new HashMap<>());
        runtimeHookTracker.put("afterSuite", new HashMap<>());
    }

    public Map<String, Map<String, Integer>> getRuntimeHookTracker() {
        return this.runtimeHookTracker;
    }

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        runtimeHookTracker.get("beforeScenario").compute(sr.scenario.getName(), (key, count) -> count == null ? 1 : count + 1);
        return true;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {
        runtimeHookTracker.get("afterScenario").compute(sr.scenario.getName(), (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public boolean beforeFeature(FeatureRuntime fr) {
        runtimeHookTracker.get("beforeFeature").compute(fr.feature.getName(), (key, count) -> count == null ? 1 : count + 1);
        return true;
    }

    @Override
    public void afterFeature(FeatureRuntime fr) {
        runtimeHookTracker.get("afterFeature").compute(fr.feature.getName(), (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void beforeSuite(Suite suite) {
        runtimeHookTracker.get("beforeSuite").compute("suite", (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void afterSuite(Suite suite) {
        runtimeHookTracker.get("afterSuite").compute("suite", (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public boolean beforeStep(Step step, ScenarioRuntime sr) {
        runtimeHookTracker.get("beforeStep").compute("[ scenario = " + sr.scenario.getName() + " / step = " + step.getText() + " ]", (key, count) -> count == null ? 1 : count + 1);
        return true;
    }

    @Override
    public void afterStep(StepResult result, ScenarioRuntime sr) {
        runtimeHookTracker.get("afterStep").compute("[ scenario = " + sr.scenario.getName() + " / step = " + result.getStep().getText() + " ]", (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void beforeBackground(ScenarioRuntime sr) {
        runtimeHookTracker.get("beforeBackground").compute(sr.scenario.getName(), (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void afterBackground(ScenarioRuntime sr) {
        runtimeHookTracker.get("afterBackground").compute(sr.scenario.getName(), (key, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {

    }

    @Override
    public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {

    }
}

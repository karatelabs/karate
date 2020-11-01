/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.runtime;

import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class FeatureRuntime implements Runnable {

    public final SuiteRuntime suite;
    public final FeatureRuntime rootFeature;
    public final ScenarioCall caller;
    public final Feature feature;
    public final FeatureResult result;
    private final ScenarioGenerator scenarios;

    public final Map<String, ScenarioCall.Result> FEATURE_CACHE = new HashMap();

    private PerfRuntime perfRuntime;
    private Runnable next;

    public Path getParentPath() {
        return feature.getPath().getParent();
    }

    public Path getRootParentPath() {
        return rootFeature.getParentPath();
    }

    public Path getPath() {
        return feature.getPath();
    }

    public void setPerfRuntime(PerfRuntime perfRuntime) {
        this.perfRuntime = perfRuntime;
    }

    public boolean isPerfMode() {
        return perfRuntime != null;
    }

    public PerfRuntime getPerfRuntime() {
        return perfRuntime;
    }

    public void setCallArg(Map<String, Object> arg) {
        if (arg != null) {
            caller.setArg(new Variable(arg));
        }
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    public FeatureRuntime(SuiteRuntime suite, Feature feature) {
        this(suite, feature, ScenarioCall.none());
    }

    public FeatureRuntime(ScenarioCall call) {
        this(call.parentRuntime.featureRuntime.suite, call.feature, call);
    }

    private FeatureRuntime(SuiteRuntime suite, Feature feature, ScenarioCall parentCall) {
        this.suite = suite;
        this.feature = feature;
        this.caller = parentCall;
        this.rootFeature = parentCall.isNone() ? this : parentCall.parentRuntime.featureRuntime;
        result = new FeatureResult(suite.results, feature);
        scenarios = new ScenarioGenerator(this, feature.getSections().iterator());
    }

    private ScenarioRuntime currentScenario;

    public Variable getResultVariable() {
        if (currentScenario == null) {
            return Variable.NULL;
        }
        return new Variable(currentScenario.engine.getAllVariablesAsMap());
    }

    public Map<String, Object> getResult() {
        Variable var = getResultVariable();
        return var.isMap() ? var.getValue() : null;
    }

    private boolean beforeHookDone;

    private void beforeHook() {
        if (beforeHookDone) {
            return;
        }
        beforeHookDone = true;
        for (RuntimeHook hook : suite.hooks) {
            hook.beforeFeature(this);
        }
    }

    @Override
    public void run() {
        try {
            while (scenarios.hasNext()) {
                currentScenario = scenarios.next();
                if (currentScenario.isSelectedForExecution()) {
                    beforeHook();
                    currentScenario.run();
                    result.addResult(currentScenario.result);
                } else {
                    suite.logger.trace("excluded by tags: {}", currentScenario);
                }
            }
            result.sortScenarioResults();
            if (currentScenario != null) {
                currentScenario.engine.invokeAfterHookIfConfigured(true);
                result.setResultVariables(currentScenario.engine.getAllVariablesAsMap());
            }
            if (!result.isEmpty()) {
                for (RuntimeHook hook : suite.hooks) {
                    hook.afterFeature(this);
                }
            }
        } catch (Exception e) {
            suite.logger.error("feature runtime failed: {}", e.getMessage());
        } finally {
            if (next != null) {
                next.run();
            }
        }
    }

}

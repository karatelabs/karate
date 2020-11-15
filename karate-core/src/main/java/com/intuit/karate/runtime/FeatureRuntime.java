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

import com.intuit.karate.PerfHook;
import com.intuit.karate.Resource;
import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import java.io.File;
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
    public final ScenarioGenerator scenarios;

    public final Map<String, ScenarioCall.Result> FEATURE_CACHE = new HashMap();

    private PerfHook perfRuntime;
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

    public void setPerfRuntime(PerfHook perfRuntime) {
        this.perfRuntime = perfRuntime;
    }

    public boolean isPerfMode() {
        return perfRuntime != null;
    }

    public PerfHook getPerfRuntime() {
        return perfRuntime;
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    public static FeatureRuntime forTempUse() {
        SuiteRuntime sr = SuiteRuntime.forTempUse();
        File workingDir = new File(sr.buildDir);
        Resource resource = Resource.of(workingDir.toPath(), "Feature:\nScenario:\n");
        Feature feature = FeatureParser.parse(resource);
        return FeatureRuntime.of(sr, feature);
    }

    public static FeatureRuntime of(Feature feature) {
        return FeatureRuntime.of(new SuiteRuntime(), feature, null);
    }

    public static FeatureRuntime of(SuiteRuntime sr, Feature feature) {
        return FeatureRuntime.of(sr, feature, null);
    }

    public static FeatureRuntime of(SuiteRuntime sr, Feature feature, Map<String, Object> arg) {
        return new FeatureRuntime(sr, feature, ScenarioCall.none(arg));
    }

    public FeatureRuntime(ScenarioCall call) {
        this(call.parentRuntime.featureRuntime.suite, call.feature, call);
        result.setLoopIndex(call.getLoopIndex());
        if (call.arg != null && !call.arg.isNull()) {
            result.setCallArg(call.arg.getValue());
        }
    }

    private FeatureRuntime(SuiteRuntime suite, Feature feature, ScenarioCall caller) {
        this.suite = suite;
        this.feature = feature;
        this.caller = caller;
        this.rootFeature = caller.isNone() ? this : caller.parentRuntime.featureRuntime;
        result = new FeatureResult(suite.results, feature);
        scenarios = new ScenarioGenerator(this, feature.getSections().iterator());
    }

    private ScenarioRuntime lastExecutedScenario;

    public Variable getResultVariable() {
        if (lastExecutedScenario == null) {
            return Variable.NULL;
        }
        return new Variable(lastExecutedScenario.engine.getAllVariablesAsMap());
    }

    public Map<String, Object> getResult() {
        Variable var = getResultVariable();
        return var.isMap() ? var.getValue() : null;
    }

    private boolean beforeHookDone;
    private boolean beforeHookResult = true;

    // logic to run once only if there are runnable scenarios (selected by tag)
    public boolean beforeHook() {
        if (beforeHookDone) {
            return beforeHookResult;
        }
        beforeHookDone = true;
        for (RuntimeHook hook : suite.hooks) {
            beforeHookResult = beforeHookResult && hook.beforeFeature(this);
        }
        return beforeHookResult;
    }

    @Override
    public void run() {
        try {
            while (scenarios.hasNext()) {
                ScenarioRuntime sr = scenarios.next();
                if (sr.isSelectedForExecution()) {
                    if (!beforeHook()) {
                        suite.logger.info("before-feature hook returned [false], aborting: ", this);
                        break;
                    }
                    lastExecutedScenario = sr;
                    sr.run();
                } else {
                    suite.logger.trace("excluded by tags: {}", sr);
                }
            }
            stop();
        } catch (Exception e) {
            suite.logger.error("feature runtime failed: {}", e.getMessage());
        } finally {
            if (next != null) {
                next.run();
            }
        }
    }

    public void stop() {
        result.sortScenarioResults();
        if (lastExecutedScenario != null) {
            lastExecutedScenario.engine.invokeAfterHookIfConfigured(true);
            result.setResultVariables(lastExecutedScenario.engine.getAllVariablesAsMap());
        }
        if (!result.isEmpty()) {
            for (RuntimeHook hook : suite.hooks) {
                hook.afterFeature(this);
            }
        }
    }

    @Override
    public String toString() {
        return feature.toString();
    }

}

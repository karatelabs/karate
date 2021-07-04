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
package com.intuit.karate.core;

import com.intuit.karate.RuntimeHook;
import com.intuit.karate.PerfHook;
import com.intuit.karate.Suite;
import com.intuit.karate.resource.MemoryResource;
import com.intuit.karate.resource.Resource;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureRuntime implements Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(FeatureRuntime.class);

    public final Suite suite;
    public final FeatureRuntime rootFeature;
    public final ScenarioCall caller;
    public final Feature feature;
    public final Iterator<ScenarioRuntime> scenarios;
    public final PerfHook perfHook;
    public final FeatureResult result;

    private final ParallelProcessor<ScenarioRuntime> processor;

    public final Map<String, ScenarioCall.Result> CALLONCE_CACHE = new HashMap();

    private Runnable next;

    public Resource resolveFromThis(String path) {
        return feature.getResource().resolve(path);
    }

    public Resource resolveFromRoot(String path) {
        return rootFeature.feature.getResource().resolve(path);
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    public static FeatureRuntime forTempUse() {
        Suite sr = Suite.forTempUse();
        File workingDir = new File(sr.buildDir).getAbsoluteFile();
        Resource resource = new MemoryResource(workingDir, "Feature:\nScenario:\n");
        Feature feature = Feature.read(resource);
        return FeatureRuntime.of(sr, feature);
    }

    public static FeatureRuntime of(Feature feature) {
        return FeatureRuntime.of(new Suite(), feature, null);
    }

    public static FeatureRuntime of(Suite sr, Feature feature) {
        return FeatureRuntime.of(sr, feature, null);
    }

    public static FeatureRuntime of(Suite sr, Feature feature, Map<String, Object> arg) {
        return new FeatureRuntime(sr, feature, ScenarioCall.none(arg), null);
    }

    public static FeatureRuntime of(Suite sr, Feature feature, Map<String, Object> arg, PerfHook perfHook) {
        return new FeatureRuntime(sr, feature, ScenarioCall.none(arg), perfHook);
    }

    public FeatureRuntime(ScenarioCall call) {
        this(call.parentRuntime.featureRuntime.suite, call.feature, call, call.parentRuntime.featureRuntime.perfHook);
        result.setLoopIndex(call.getLoopIndex());
        result.setCallDepth(call.depth);
        if (call.arg != null && !call.arg.isNull()) {
            result.setCallArg(call.arg.getValue());
        }
    }

    private FeatureRuntime(Suite suite, Feature feature, ScenarioCall caller, PerfHook perfHook) {
        this.suite = suite;
        this.feature = feature;
        this.caller = caller;
        this.rootFeature = caller.isNone() ? this : caller.parentRuntime.featureRuntime;
        result = new FeatureResult(feature);
        scenarios = new ScenarioIterator(this).filterSelected().iterator();
        this.perfHook = perfHook;
        if (caller.isNone() && suite.parallel && perfHook == null) {
            processor = new ParallelProcessor<ScenarioRuntime>(
                    suite.scenarioExecutor,
                    scenarios,
                    suite.pendingTasks) {

                @Override
                public void process(ScenarioRuntime sr) {
                    processScenario(sr);
                }

                @Override
                public void onComplete() {
                    afterFeature();
                }

                @Override
                public boolean shouldRunSynchronously(ScenarioRuntime sr) {
                    return sr.tags.valuesFor("parallel").isAnyOf("false");
                }

            };
        } else {
            processor = null;
        }
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
        if (processor != null) {
            processor.execute();
        } else {
            if (!beforeHook()) {
                logger.info("before-feature hook returned [false], aborting: {}", this);
            } else {
                scenarios.forEachRemaining(this::processScenario);
            }
            afterFeature();
        }
    }

    private ScenarioRuntime lastExecutedScenario;

    private void processScenario(ScenarioRuntime sr) {
        if (beforeHook()) {
            lastExecutedScenario = sr;
            if (suite.jobManager != null) {
                CompletableFuture future = suite.jobManager.addChunk(sr);
                logger.info("waiting for job executor to process: {}", sr);
                future.join();
                logger.info("job executor completed processing: {}", sr);
            } else {
                sr.run();
            }
            // can be empty for distributed / job-server flows
            if (!sr.result.getStepResults().isEmpty()) {
                synchronized (result) {
                    result.addResult(sr.result);
                }
            }
        }
    }

    // extracted for junit5
    public void afterFeature() {
        result.sortScenarioResults();
        if (lastExecutedScenario != null) {
            lastExecutedScenario.engine.invokeAfterHookIfConfigured(true);
            result.setVariables(lastExecutedScenario.engine.getAllVariablesAsMap());
        }
        if (!result.isEmpty()) {
            for (RuntimeHook hook : suite.hooks) {
                hook.afterFeature(this);
            }
        }
        if (next != null) {
            next.run();
        }
    }

    @Override
    public String toString() {
        return feature.toString();
    }

}

/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import com.intuit.karate.Logger;
import java.util.Collections;
import java.util.Iterator;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureExecutionUnit implements Runnable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FeatureExecutionUnit.class);

    public final ExecutionContext exec;
    private final boolean parallelScenarios;

    private Iterator<ScenarioExecutionUnit> units;
    private Runnable next;

    public FeatureExecutionUnit(ExecutionContext exec) {
        this.exec = exec;
        parallelScenarios = exec.scenarioExecutor != null;
    }

    public Iterator<ScenarioExecutionUnit> getScenarioExecutionUnits() {
        return units;
    }

    public void init() {
        if (exec.callContext.executionHooks != null) {
            for (ExecutionHook hook : exec.callContext.executionHooks) {
                boolean hookResult;
                Feature feature = exec.featureContext.feature;
                try {
                    hookResult = hook.beforeFeature(feature, exec);
                } catch (Exception e) {
                    LOGGER.warn("execution hook beforeFeature failed, will skip: {} - {}", feature.getRelativePath(), e.getMessage());
                    hookResult = false;
                }
                if (hookResult == false) {
                    units = Collections.emptyIterator();
                }
            }
        }
        if (units == null) { // no hook failed
            units = exec.featureContext.feature.getScenarioExecutionUnits(exec);
        }
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    private ScenarioContext lastContextExecuted;

    @Override
    public void run() {
        if (units == null) {
            init();
        }
        Subscriber<ScenarioResult> subscriber = new Subscriber<ScenarioResult>() {
            @Override
            public void onNext(ScenarioResult result) {
                exec.result.addResult(result);
            }

            @Override
            public void onComplete() {
                stop();
                exec.result.sortScenarioResults();
                if (next != null) {
                    next.run();
                }
            }
        };
        ParallelProcessor<ScenarioExecutionUnit, ScenarioResult> processor = new ParallelProcessor<ScenarioExecutionUnit, ScenarioResult>(exec.scenarioExecutor, units) {
            @Override
            public Iterator<ScenarioResult> process(ScenarioExecutionUnit unit) {
                if (isSelected(unit) && !unit.result.isFailed()) { // can happen for dynamic scenario outlines with a failed background !
                    unit.run();
                    // we also hold a reference to the last scenario-context that executed
                    // for cases where the caller needs a result                
                    lastContextExecuted = unit.getContext();
                    return Collections.singletonList(unit.result).iterator();
                } else {
                    return Collections.emptyIterator();
                }
            }

            @Override
            public boolean shouldRunSynchronously(ScenarioExecutionUnit unit) {
                if (!parallelScenarios) {
                    return true;
                }
                Tags tags = unit.scenario.getTagsEffective();
                return tags.valuesFor("parallel").isAnyOf("false");
            }

        };
        processor.subscribe(subscriber);
    }

    // extracted for junit 5
    public void stop() {
        if (lastContextExecuted != null) {
            // set result map that caller will see
            exec.result.setResultVars(lastContextExecuted.vars);
            lastContextExecuted.invokeAfterHookIfConfigured(true);
        }
        if (exec.callContext.executionHooks != null) {
            for (ExecutionHook hook : exec.callContext.executionHooks) {
                try {
                    hook.afterFeature(exec.result, exec);
                } catch (Exception e) {
                    LOGGER.warn("execution hook afterFeature failed: {} - {}",
                            exec.featureContext.feature.getRelativePath(), e.getMessage());
                }
            }
        }
    }

    public boolean isSelected(ScenarioExecutionUnit unit) {
        return isSelected(exec.featureContext, unit.scenario, new Logger());
    }

    public static boolean isSelected(FeatureContext fc, Scenario scenario, Logger logger) {
        Feature feature = fc.feature;
        int callLine = feature.getCallLine();
        if (callLine != -1) {
            int sectionLine = scenario.getSection().getLine();
            int scenarioLine = scenario.getLine();
            if (callLine == sectionLine || callLine == scenarioLine) {
                logger.info("found scenario at line: {}", callLine);
                return true;
            }
            logger.trace("skipping scenario at line: {}, needed: {}", scenario.getLine(), callLine);
            return false;
        }
        String callName = feature.getCallName();
        if (callName != null) {
            if (scenario.getName().matches(callName)) {
                logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
                return true;
            }
            logger.trace("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
            return false;
        }
        Tags tags = scenario.getTagsEffective();
        String callTag = scenario.getFeature().getCallTag();
        if (callTag != null) {
            if (tags.contains(callTag)) {
                logger.info("scenario called at line: {} by tag: {}", scenario.getLine(), callTag);
                return true;
            }
            logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
            return false;
        }
        if (tags.evaluate(fc.tagSelector)) {
            logger.trace("matched scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
            return true;
        }
        logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
        return false;
    }

}

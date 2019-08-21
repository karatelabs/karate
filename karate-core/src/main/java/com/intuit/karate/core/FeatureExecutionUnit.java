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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureExecutionUnit implements Runnable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FeatureExecutionUnit.class);

    public final ExecutionContext exec;
    private final boolean parallelScenarios;

    private CountDownLatch latch;
    private List<ScenarioExecutionUnit> units;
    private List<ScenarioResult> results;
    private Runnable next;

    public FeatureExecutionUnit(ExecutionContext exec) {
        this.exec = exec;
        parallelScenarios = exec.scenarioExecutor != null;
    }

    public List<ScenarioExecutionUnit> getScenarioExecutionUnits() {
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
                    units = Collections.EMPTY_LIST;
                }
            }
        }
        if (units == null) { // no hook failed
            units = exec.featureContext.feature.getScenarioExecutionUnits(exec);
        }
        int count = units.size();
        results = new ArrayList(count);
        latch = new CountDownLatch(count);
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
        for (ScenarioExecutionUnit unit : units) {
            if (isSelected(unit) && run(unit)) {
                // unit.next should count down latch when done
            } else { // un-selected / failed scenario
                latch.countDown();
            }
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        stop();
        if (next != null) {
            next.run();
        }
    }

    public void stop() {
        // this is where the feature gets "populated" with stats
        // but best of all, the original order is retained
        for (ScenarioResult sr : results) {
            exec.result.addResult(sr);
        }
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

    public boolean run(ScenarioExecutionUnit unit) {
        // this is an elegant solution to retaining the order of scenarios 
        // in the final report - even if they run in parallel !            
        results.add(unit.result);
        if (unit.result.isFailed()) { // can happen for dynamic scenario outlines with a failed background !
            return false;
        }
        Tags tags = unit.scenario.getTagsEffective();
        unit.setNext(() -> {
            latch.countDown();
            // we also hold a reference to the last scenario-context that executed
            // for cases where the caller needs a result                
            lastContextExecuted = unit.getContext(); // IMPORTANT: will handle if actions is null
        });
        boolean sequential = !parallelScenarios || tags.valuesFor("parallel").isAnyOf("false");
        // main            
        if (sequential) {
            unit.run();
        } else {
            exec.scenarioExecutor.submit(unit);
        }
        return true;
    }

}

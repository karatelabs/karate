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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class FeatureExecutionUnit implements Runnable {

    public final ExecutionContext exec;
    private final Consumer<Runnable> SYSTEM;
    private final boolean parallelScenarios;

    private List<ScenarioExecutionUnit> units;
    private Iterator<ScenarioExecutionUnit> iterator;
    private List<ScenarioResult> results;
    private CountDownLatch latch;
    private Runnable next;

    public FeatureExecutionUnit(ExecutionContext exec) {
        this.exec = exec;
        parallelScenarios = exec.scenarioExecutor != null;
        SYSTEM = parallelScenarios ? r -> exec.scenarioExecutor.submit(r) : r -> r.run();
    }

    public List<ScenarioExecutionUnit> getScenarioExecutionUnits() {
        return units;
    }

    public void init(Logger logger) { // logger applies only if called from ui
        units = exec.featureContext.feature.getScenarioExecutionUnits(exec, logger);
        int count = units.size();
        results = new ArrayList(count);
        latch = new CountDownLatch(count);
        iterator = units.iterator();
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    private ScenarioContext lastContextExecuted;

    @Override
    public void run() {
        if (iterator == null) {
            init(null);
        }
        FeatureContext featureContext = exec.featureContext;
        String callName = featureContext.feature.getCallName();
        while (iterator.hasNext()) {
            ScenarioExecutionUnit unit = iterator.next();
            Scenario scenario = unit.scenario;
            if (callName != null) {
                if (!scenario.getName().matches(callName)) {
                    unit.logger.info("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
                    latch.countDown();
                    continue;
                }
                unit.logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
            }
            Tags tags = scenario.getTagsEffective();
            if (!tags.evaluate(featureContext.tagSelector)) {
                unit.logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                latch.countDown();
                continue;
            }
            String callTag = scenario.getFeature().getCallTag();
            if (callTag != null) {
                if (!tags.contains(callTag)) {
                    unit.logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
                    latch.countDown();
                    continue;
                }
                unit.logger.info("scenario called at line: {} by tag: {}", scenario.getLine(), callTag);
            }
            boolean sequential = !parallelScenarios || tags.valuesFor("parallel").isAnyOf("false");
            unit.setNext(() -> {
                latch.countDown();
                // we also hold a reference to the last scenario-context that executed
                // for cases where the caller needs a result
                lastContextExecuted = unit.getActions().context;
            });
            // this is an elegant solution to retaining the order of scenarios 
            // in the final report - even if they run in parallel !            
            results.add(unit.result);
            if (unit.result.isFailed()) { // can happen for dynamic scenario outlines with a failed background !
                latch.countDown();
                continue;
            }
            // main            
            if (sequential) {
                unit.run();
            } else { // submit and loop immediately
                SYSTEM.accept(unit);
            }
        }
        // wait for parallel scenario submissions to complete
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
        if (next != null) {
            next.run();
        }
    }

}

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
    
    public void init() {
        units = exec.featureContext.feature.getScenarioExecutionUnits(exec);
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
            init();
        }
        FeatureContext featureContext = exec.featureContext;
        String callName = featureContext.feature.getCallName();
        if (iterator.hasNext()) {
            ScenarioExecutionUnit unit = iterator.next();
            Scenario scenario = unit.scenario;
            if (callName != null) {
                if (!scenario.getName().matches(callName)) {
                    featureContext.logger.info("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
                    latch.countDown();
                    SYSTEM.accept(this);
                    return;
                }
                featureContext.logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
            }
            Tags tags = unit.tags;
            if (!tags.evaluate(featureContext.tagSelector)) {
                featureContext.logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                latch.countDown();
                SYSTEM.accept(this);
                return;
            }
            String callTag = scenario.getFeature().getCallTag();
            if (callTag != null) {
                if (!tags.contains(callTag)) {
                    featureContext.logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
                    latch.countDown();
                    SYSTEM.accept(this);
                    return;
                }
                featureContext.logger.info("scenario called at line: {} by tag: {}", scenario.getLine(), callTag);
            }
            boolean sequential = !parallelScenarios || tags.valuesFor("parallel").isAnyOf("false");
            unit.setNext(() -> {
                latch.countDown();
                // we also hold a reference to the last scenario-context that executed
                // for cases where the caller needs a result
                lastContextExecuted = unit.getActions().context;
                if (sequential) { // yield next scenario only when previous completes                    
                    // and execute one-by-one in sequence order 
                    SYSTEM.accept(this);
                }
            });
            // this is an elegant solution to retaining the order of scenarios 
            // in the final report - even if they run in parallel !            
            results.add(unit.result);
            // main
            SYSTEM.accept(unit);
            if (!sequential) {
                // loop immediately and submit all scenarios in parallel
                SYSTEM.accept(this);
            }
        } else {
            if (parallelScenarios) {
                // wait for parallel scenario submissions to complete
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    featureContext.logger.error("feature failed: {}", e.getMessage());
                }
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
            exec.appender.close();
            if (next != null) {
                next.run();
            }
        }
    }

}

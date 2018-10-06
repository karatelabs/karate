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

import com.intuit.karate.StepActions;
import com.intuit.karate.FeatureContext;
import com.intuit.karate.ScenarioContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author pthomas3
 */
public class FeatureExecutionUnit {

    private final ExecutionContext exec;
    private final int count;
    private final Iterator<Scenario> iterator;
    private final List<ScenarioResult> results;
    private final CountDownLatch latch;

    public FeatureExecutionUnit(ExecutionContext exec) {
        this.exec = exec;
        List<Scenario> scenarios = exec.featureContext.feature.getScenarios();
        count = scenarios.size();
        iterator = scenarios.iterator();
        results = new ArrayList(count);
        latch = exec.parallelScenarios ? new CountDownLatch(count) : null;
    }

    private ScenarioContext lastContextExecuted;

    public void submit(Runnable next) {
        FeatureContext featureContext = exec.featureContext;
        String callName = featureContext.feature.getCallName();
        if (iterator.hasNext()) {
            Scenario scenario = iterator.next();
            if (callName != null) {
                if (!scenario.getName().matches(callName)) {
                    featureContext.logger.info("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
                    if (latch != null) {
                        latch.countDown();
                    }
                    FeatureExecutionUnit.this.submit(next);
                    return;
                }
                featureContext.logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
            }
            Tags tags = new Tags(scenario.getTagsEffective());
            if (!tags.evaluate(featureContext.tagSelector)) {
                featureContext.logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                if (latch != null) {
                    latch.countDown();
                }
                FeatureExecutionUnit.this.submit(next);
                return;
            }
            String callTag = scenario.getFeature().getCallTag();
            if (callTag != null) {
                if (!tags.contains(callTag)) {
                    featureContext.logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
                    if (latch != null) {
                        latch.countDown();
                    }
                    FeatureExecutionUnit.this.submit(next);
                    return;
                }
                featureContext.logger.info("scenario called at line: {} by tag: {}", scenario.getLine(), callTag);
            }
            // this is where the script-context and vars are inited for a scenario
            // first we set the scenario metadata
            exec.callContext.setScenarioInfo(getScenarioInfo(scenario, featureContext));
            // then the tags metadata
            exec.callContext.setTags(tags);
            // karate-config.js will be processed here 
            // when the script-context constructor is called
            StepActions actions = new StepActions(featureContext, exec.callContext);
            ScenarioExecutionUnit unit = new ScenarioExecutionUnit(scenario, actions, exec);
            // this is an elegant solution to retaining the order of scenarios 
            // in the final report - even if they run in parallel !
            results.add(unit.result);
            unit.submit(() -> {
                // we also hold a reference to the last scenario-context that executed
                // for cases where the caller needs a result
                lastContextExecuted = actions.context;
                if (latch != null) {
                    latch.countDown();
                } else {
                    // yield next scenario only when previous completes
                    // and we execute one-by-one in sequence order                    
                    FeatureExecutionUnit.this.submit(next);
                }
            });
            if (latch != null) {
                // loop immediately and submit all scenarios in parallel
                FeatureExecutionUnit.this.submit(next);
            }
        } else {
            if (latch != null) {
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
                exec.result.setResultVars(lastContextExecuted.getVars());
                lastContextExecuted.invokeAfterHookIfConfigured(true);
            }
            exec.appender.close();
            next.run();
        }
    }

    private static ScenarioInfo getScenarioInfo(Scenario scenario, FeatureContext env) {
        ScenarioInfo info = new ScenarioInfo();
        info.setFeatureDir(env.feature.getPath().getParent().toString());
        info.setFeatureFileName(env.feature.getPath().getFileName().toString());
        info.setScenarioName(scenario.getName());
        info.setScenarioDescription(scenario.getDescription());
        info.setScenarioType(scenario.isOutline() ? ScenarioOutline.KEYWORD : Scenario.KEYWORD);
        return info;
    }

}

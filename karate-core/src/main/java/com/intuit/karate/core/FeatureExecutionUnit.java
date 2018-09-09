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
import com.intuit.karate.cucumber.ScenarioInfo;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author pthomas3
 */
public class FeatureExecutionUnit {

    private final ExecutionContext exec;
    private final Iterator<Scenario> iterator;

    public FeatureExecutionUnit(ExecutionContext exec) {
        this.exec = exec;
        iterator = exec.featureContext.feature.getScenarios().iterator();
    }

    public void submit(Runnable next) {
        if (iterator.hasNext()) {
            Scenario scenario = iterator.next();
            FeatureContext featureContext = exec.featureContext;
            String callName = featureContext.feature.getCallName();
            if (callName != null) {
                if (!scenario.getName().matches(callName)) {
                    featureContext.logger.info("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
                    FeatureExecutionUnit.this.submit(next);
                    return;
                } else {
                    featureContext.logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
                }
            }
            Tags tags = new Tags(scenario.getTagsEffective());
            if (!tags.evaluate(featureContext.tagSelector)) {
                featureContext.logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                FeatureExecutionUnit.this.submit(next);
                return;
            }
            String callTag = scenario.getFeature().getCallTag();
            if (callTag != null) {                
                if (!tags.contains(callTag)) {
                    featureContext.logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
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
            // we also hold a reference to the LAST scenario executed
            // for cases where the caller needs a result
            exec.result.setResultVars(actions.context.getVars());
            exec.system.accept(() -> {
                ScenarioExecutionUnit unit = new ScenarioExecutionUnit(scenario, actions, exec);
                unit.submit(() -> {
                    FeatureExecutionUnit.this.submit(next);
                });
            });
        } else {
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

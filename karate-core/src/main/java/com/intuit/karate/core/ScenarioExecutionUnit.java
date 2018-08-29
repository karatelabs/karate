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

import com.intuit.karate.LogAppender;
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements ExecutionUnit<Void> {

    private final Scenario scenario;
    private final StepDefs stepDefs;
    private final FeatureResult featureResult;
    private final LogAppender appender;

    private final Iterator<Step> iterator;

    private BackgroundResult backgroundResult;
    private ScenarioResult scenarioResult;

    private boolean stopped = false;
    private KarateException error;

    public ScenarioExecutionUnit(Scenario scenario, StepDefs stepDefs, ExecutionContext exec) {
        this.scenario = scenario;
        this.stepDefs = stepDefs;
        this.featureResult = exec.result;
        this.appender = exec.appender;
        iterator = scenario.getStepsIncludingBackground().iterator();
    }

    void addStepResult(StepResult stepResult) {
        if (stepResult.getStep().isBackground()) {
            if (backgroundResult == null) {
                backgroundResult = new BackgroundResult(scenario.getFeature().getBackground());                
            }
            backgroundResult.addStepResult(stepResult);
        } else {
            if (scenarioResult == null) {
                scenarioResult = new ScenarioResult(scenario);                
            }
            scenarioResult.addStepResult(stepResult);
        }
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<Void, KarateException> next) {
        // before-scenario hook
        if (stepDefs.callContext.executionHook != null) {
            try {
                stepDefs.callContext.executionHook.beforeScenario(scenario, stepDefs);
            } catch (Exception e) {
                String message = "scenario hook threw fatal error: " + e.getMessage();
                stepDefs.context.logger.error(message);
                featureResult.addError(e);
                next.accept(null, new KarateException(message, e));
                return;
            }
        }
        if (iterator.hasNext()) {
            Step step = iterator.next();
            if (stopped) {
                addStepResult(new StepResult(step, Result.skipped()));
                ScenarioExecutionUnit.this.submit(system, next);
            } else {
                system.accept(() -> {
                    StepExecutionUnit unit = new StepExecutionUnit(step, scenario, stepDefs);
                    unit.submit(system, (stepResult, e) -> {
                        addStepResult(stepResult);
                        // log appender collection for each step happens here
                        if (step.getDocString() == null) {
                            String log = StringUtils.trimToNull(appender.collect());
                            if (log != null) {
                                stepResult.putDocString(log);
                            }
                        }
                        if (stepResult.getResult().isAborted()) {
                            stopped = true;
                        }
                        if (e != null) { // failed 
                            stopped = true;
                            error = e;
                        }
                        ScenarioExecutionUnit.this.submit(system, next);
                    });
                });
            }
        } else {
            // these have to be done at the end after they are fully populated
            // else the feature-result will not "collect" stats correctly
            if (backgroundResult != null) {
                featureResult.addResult(backgroundResult);                
            }
            if (scenarioResult != null) {
                featureResult.addResult(scenarioResult);
            }
            // after-scenario hook
            if (stepDefs.callContext.executionHook != null) {
                stepDefs.callContext.executionHook.afterScenario(scenarioResult, stepDefs);
            }            
            next.accept(null, error);
        }
    }

}

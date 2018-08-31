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

import com.intuit.karate.StepDefs;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements ExecutionUnit<ScenarioResult> {

    private final Scenario scenario;
    private final StepDefs stepDefs;
    private final ExecutionContext exec;

    private final Iterator<Step> iterator;

    private BackgroundResult backgroundResult;
    private ScenarioResult scenarioResult;

    private boolean stopped = false;

    public ScenarioExecutionUnit(Scenario scenario, StepDefs stepDefs, ExecutionContext exec) {
        this.scenario = scenario;
        this.stepDefs = stepDefs;
        this.exec = exec;
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
    public void submit(Consumer<Runnable> system, Consumer<ScenarioResult> next) {
        if (iterator.hasNext()) {
            Step step = iterator.next();
            if (stopped) {
                addStepResult(new StepResult(step, Result.skipped()));
                ScenarioExecutionUnit.this.submit(system, next);
            } else {
                system.accept(() -> {
                    StepExecutionUnit unit = new StepExecutionUnit(step, stepDefs, exec);
                    unit.submit(system, stepResult -> {
                        addStepResult(stepResult);
                        Result result = stepResult.getResult();
                        if (result.isFailed() || result.isAborted()) {
                            stopped = true;
                        }
                        ScenarioExecutionUnit.this.submit(system, next);
                    });
                });
            }
        } else {
            // these have to be done at the end after they are fully populated
            // else the feature-result will not "collect" stats correctly
            if (backgroundResult != null) {
                exec.result.addResult(backgroundResult);                
            }
            if (scenarioResult != null) {
                exec.result.addResult(scenarioResult);
            }           
            next.accept(scenarioResult);
        }
    }

}

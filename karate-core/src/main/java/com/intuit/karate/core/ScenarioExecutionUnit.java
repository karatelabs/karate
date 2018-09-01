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
public class ScenarioExecutionUnit {

    private final StepDefs stepDefs;
    private final ExecutionContext exec;
    private final Iterator<Step> iterator;
    private final ScenarioResult result;

    private boolean stopped = false;

    public ScenarioExecutionUnit(Scenario scenario, StepDefs stepDefs, ExecutionContext exec) {
        this.stepDefs = stepDefs;
        this.exec = exec;
        this.result = new ScenarioResult(scenario);
        iterator = scenario.getStepsIncludingBackground().iterator();
    }

    public void submit(Consumer<ScenarioResult> next) {
        if (iterator.hasNext()) {
            Step step = iterator.next();
            if (stopped) {
                result.addStepResult(new StepResult(step, Result.skipped(), null));
                ScenarioExecutionUnit.this.submit(next);
            } else {
                exec.system.accept(() -> {
                    StepExecutionUnit unit = new StepExecutionUnit(step, stepDefs, exec);
                    unit.submit(stepResult -> {
                        result.addStepResult(stepResult);
                        if (stepResult.isStopped()) {
                            stopped = true;
                        }
                        ScenarioExecutionUnit.this.submit(next);
                    });
                });
            }
        } else {
            // this has to be done at the end after they are fully populated
            // else the feature-result will not "collect" stats correctly 
            exec.result.addResult(result);
            next.accept(result);
        }
    }

}

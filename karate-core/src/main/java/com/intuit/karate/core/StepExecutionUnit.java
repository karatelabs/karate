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
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class StepExecutionUnit implements ExecutionUnit<Result> {

    private final Step step;    
    private final Scenario scenario;
    private final StepDefs stepDefs;

    public StepExecutionUnit(Step step, Scenario scenario, StepDefs stepDefs) {
        this.step = step;        
        this.scenario = scenario;
        this.stepDefs = stepDefs;
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<Result, KarateException> next) {
        system.accept(() -> {
            String relativePath = scenario.getFeature().getRelativePath();
            Result result = Engine.execute(relativePath, step, stepDefs);
            if (result.isAborted()) {
                stepDefs.context.logger.debug("abort at {}:{}", relativePath, step.getLine());
                next.accept(result, null); // same flow as passed
            } else if (result.isFailed()) {
                String scenarioName = StringUtils.trimToNull(scenario.getName());
                String message = "called: " + relativePath;
                if (scenarioName != null) {
                    message = message + ", scenario: " + scenarioName;
                }
                message = message + ", line: " + step.getLine();
                KarateException error = new KarateException(message, result.getError());
                next.accept(result, error);
            } else { // passed
                next.accept(result, null);
            }
        });
    }

}

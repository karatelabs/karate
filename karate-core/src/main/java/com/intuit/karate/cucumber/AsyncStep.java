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
package com.intuit.karate.cucumber;

import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class AsyncStep implements AsyncAction<StepResult> {

    private final StepWrapper step;
    private final KarateBackend backend;

    public AsyncStep(StepWrapper step, KarateBackend backend) {
        this.step = step;
        this.backend = backend;
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<StepResult, KarateException> next) {
        system.accept(() -> {
            StepResult result = CucumberUtils.runCalledStep(step, backend);
            ScenarioWrapper scenario = step.getScenario();
            if (result.isAbort()) {
                backend.getEnv().logger.debug("abort at {}:{}", scenario.getFeature().getPath(), step.getStep().getLine());
                next.accept(result, null);
            } else if (!result.isPass()) {
                FeatureWrapper feature = scenario.getFeature();
                String scenarioName = StringUtils.trimToNull(scenario.getScenario().getGherkinModel().getName());
                String message = "called: " + feature.getPath();
                if (scenarioName != null) {
                    message = message + ", scenario: " + scenarioName;
                }
                message = message + ", line: " + step.getStep().getLine();
                KarateException error = new KarateException(message, result.getError());
                next.accept(null, error);
            } else {
                next.accept(result, null);
            }
        });
    }

}

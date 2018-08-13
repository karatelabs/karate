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
import com.intuit.karate.exception.KarateException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements ExecutionUnit<FeatureResult> {

    private final Scenario scenario;
    private final StepDefs stepDefs;
    private final FeatureResult featureResult;
    private final LogAppender appender;

    private boolean backgroundDone = false;
    private boolean stopped = false;

    public ScenarioExecutionUnit(Scenario scenario, StepDefs stepDefs, FeatureResult featureResult, LogAppender appender) {
        this.scenario = scenario;
        this.stepDefs = stepDefs;
        this.featureResult = featureResult;
        this.appender = appender;
        if (scenario.getFeature().getBackground() == null) {
            backgroundDone = true;
        }
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<FeatureResult, KarateException> next) {
        if (!backgroundDone) {
            backgroundDone = true;
            Background background = scenario.getFeature().getBackground();
            BackgroundResult backgroundResult = new BackgroundResult(background);
            featureResult.addResult(backgroundResult);
            system.accept(() -> {
                StepListExecutionUnit unit = new StepListExecutionUnit(background.getSteps(), scenario, stepDefs, backgroundResult, appender, stopped);
                unit.submit(system, (r, e) -> {
                    stopped = r;
                    ScenarioExecutionUnit.this.submit(system, next);
                });
            });
        } else {
            ScenarioResult scenarioResult = new ScenarioResult(scenario);
            featureResult.addResult(scenarioResult);
            system.accept(() -> {
                StepListExecutionUnit unit = new StepListExecutionUnit(scenario.getSteps(), scenario, stepDefs, scenarioResult, appender, stopped);
                unit.submit(system, (r, e) -> {                    
                    next.accept(featureResult, e);
                });
            });
        }
    }

}

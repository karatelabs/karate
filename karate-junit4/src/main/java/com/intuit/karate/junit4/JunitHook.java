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
package com.intuit.karate.junit4;

import com.intuit.karate.core.*;
import com.intuit.karate.http.HttpRequestBuilder;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import static org.junit.runner.Description.createTestDescription;

/**
 * @author pthomas3
 */
public class JunitHook implements ExecutionHook {

    public final Description description;

    private RunNotifier notifier;

    public JunitHook(Class clazz) {
        description = Description.createSuiteDescription(clazz);
    }

    public void setNotifier(RunNotifier notifier) {
        this.notifier = notifier;
    }

    private static String getFeatureName(Feature feature) {
        return feature.getResource().getFileNameWithoutExtension();
    }

    public static Description getScenarioDescription(Scenario scenario) {
        String featureName = getFeatureName(scenario.getFeature());
        return createTestDescription("Feature: " + featureName, "Scenario: " + scenario.getDisplayMeta() + ' ' + scenario.getName());
    }

    public Description getDescription() {
        return description;
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        // if dynamic scenario outline background or a call
        if (notifier == null || context.callDepth > 0) {
            return true;
        }
        notifier.fireTestStarted(getScenarioDescription(scenario));
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {
        // if dynamic scenario outline background or a call
        if (notifier == null || context.callDepth > 0) {
            return;
        }
        Description scenarioDescription = getScenarioDescription(result.getScenario());
        if (result.isFailed()) {
            notifier.fireTestFailure(new Failure(scenarioDescription, result.getError()));
        }
        // apparently this method should be always called
        // even if fireTestFailure was called
        notifier.fireTestFinished(scenarioDescription);
    }

    @Override
    public boolean beforeFeature(Feature feature) {
        return true;
    }

    @Override
    public void afterFeature(FeatureResult result) {

    }

    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }

    @Override
    public void reportPerfEvent(PerfEvent event) {

    }

}

/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.core.Scenario;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.ScenarioRuntime;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 *
 * @author pthomas3
 */
public class JunitHook implements RuntimeHook {

    private RunNotifier notifier;

    public void setNotifier(RunNotifier notifier) {
        this.notifier = notifier;
    }

    private static Description getScenarioDescription(Scenario scenario) {
        return Description.createTestDescription(scenario.getFeature().getNameForReport(), scenario.getRefIdAndName());
    }    

    @Override
    public boolean beforeScenario(ScenarioRuntime sr) {
        if (notifier == null || sr.caller.depth > 0) {
            return true;
        }
        notifier.fireTestStarted(getScenarioDescription(sr.scenario));
        return true;
    }

    @Override
    public void afterScenario(ScenarioRuntime sr) {
        // if dynamic scenario outline background or a call
        if (notifier == null || sr.caller.depth > 0) {
            return;
        }
        Description scenarioDescription = getScenarioDescription(sr.scenario);
        if (sr.isFailed()) {
            notifier.fireTestFailure(new Failure(scenarioDescription, sr.result.getError()));
        }
        // apparently this method should be always called
        // even if fireTestFailure was called
        notifier.fireTestFinished(scenarioDescription);
    }

}

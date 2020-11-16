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
package com.intuit.karate.core;

import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;

/**
 *
 * @author pthomas3
 */
public interface RuntimeHook {

    // return false if the scenario / item should be excluded from the test-run
    // throws RuntimeException (any) to abort    
    boolean beforeScenario(ScenarioRuntime sr);

    void afterScenario(ScenarioRuntime sr);

    boolean beforeFeature(FeatureRuntime fr);

    void afterFeature(FeatureRuntime fr);

    void beforeSuite(SuiteRuntime suite);

    void afterSuite(SuiteRuntime suite);

    boolean beforeStep(Step step, ScenarioRuntime sr);

    void afterStep(StepResult result, ScenarioRuntime sr);

}

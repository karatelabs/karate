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
package com.intuit.karate;

import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;

/**
 *
 * @author pthomas3
 */
public interface RuntimeHook {

    // return false if the scenario / item should be excluded from the test-run
    // throw RuntimeException (any) to abort    
    default boolean beforeScenario(ScenarioRuntime sr) {
        return true;
    }

    default void afterScenario(ScenarioRuntime sr) {

    }

    default boolean beforeFeature(FeatureRuntime fr) {
        return true;
    }

    default void afterFeature(FeatureRuntime fr) {

    }

    default void beforeSuite(Suite suite) {

    }

    default void afterSuite(Suite suite) {
        
    }

    default boolean beforeStep(Step step, ScenarioRuntime sr) {
        return true;
    }

    default void afterStep(StepResult result, ScenarioRuntime sr) {
        
    }

    // applicable only for Dynamic Scenario Outlines which have the need
    // to run background sections before executing the individual scenarios
    // to calculate the Examples table
    default void beforeBackground(ScenarioRuntime sr) {

    }

    default void afterBackground(ScenarioRuntime sr) {

    }

    default void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
        
    }
    
    default void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
        
    }

}

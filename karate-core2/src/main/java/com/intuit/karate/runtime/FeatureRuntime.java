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
package com.intuit.karate.runtime;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import java.nio.file.Path;

/**
 *
 * @author pthomas3
 */
public class FeatureRuntime implements Runnable {
    
    public final SuiteRuntime suite;
    public final FeatureRuntime rootFeature;
    public final ScenarioCall parentCall;
    public final Feature feature;
    public final FeatureResult result;
    private final ScenarioGenerator scenarios;
    
    public Path getParentPath() {
        return feature.getPath().getParent();
    }
    
    public Path getRootParentPath() {
        return rootFeature.getParentPath();
    }
    
    public Path getPath() {
        return feature.getPath();
    }

    public FeatureRuntime(SuiteRuntime suite, Feature feature, ScenarioCall parentCall) {
        this.suite = suite;
        this.feature = feature;
        this.parentCall = parentCall;
        this.rootFeature = parentCall.isNone() ? this : parentCall.parentRuntime.featureRuntime;
        result = new FeatureResult(suite.results, feature);
        scenarios = new ScenarioGenerator(this, feature.getSections().iterator());
    }

    @Override
    public void run() {
        while (scenarios.hasNext()) {
            ScenarioRuntime sr = scenarios.next();
            sr.run();
        }
    }

}

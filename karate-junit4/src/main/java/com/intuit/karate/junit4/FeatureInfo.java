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

import com.intuit.karate.CallContext;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioExecutionUnit;
import org.junit.runner.Description;

/**
 *
 * @author pthomas3
 */
public class FeatureInfo {

    public final Feature feature;
    public final ExecutionContext exec;
    public final Description description;
    public final FeatureExecutionUnit unit;

    private static String getFeatureName(Feature feature) {
        return "[" + feature.getResource().getFileNameWithoutExtension() + "]";
    }

    public static Description getScenarioDescription(Scenario scenario) {
        String featureName = getFeatureName(scenario.getFeature());
        return Description.createTestDescription(featureName, scenario.getDisplayMeta() + ' ' + scenario.getName());
    }

    public FeatureInfo(Feature feature, String tagSelector) {
        this.feature = feature;
        description = Description.createSuiteDescription(getFeatureName(feature), feature.getResource().getPackageQualifiedName());
        FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
        CallContext callContext = new CallContext(null, true);
        exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null);
        unit = new FeatureExecutionUnit(exec);
        unit.init();
        for (ScenarioExecutionUnit u : unit.getScenarioExecutionUnits()) {
            Description scenarioDescription = getScenarioDescription(u.scenario);
            description.addChild(scenarioDescription);
        }
    }

}

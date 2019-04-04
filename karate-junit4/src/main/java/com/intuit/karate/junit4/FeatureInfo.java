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
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.http.HttpRequestBuilder;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeatureInfo implements ExecutionHook, Serializable {

    public final Feature feature;
    public final ExecutionContext exec;
    public final Description description;
    public final FeatureExecutionUnit unit;
    private static List<FeatureInfo> featureInfoList = new LinkedList<>();

    private RunNotifier notifier;

    public static FeatureInfo getFeatureInfo(Feature feature, String tagSelector) {
        if (featureInfoList != null) {
            for (FeatureInfo f : featureInfoList){
                if (f.feature == feature){
                    return f;
                }
            }
        }
        return new FeatureInfo(feature,tagSelector);
    }

    public void setNotifier(RunNotifier notifier) {
        this.notifier = notifier;
    }

    private static String getFeatureName(Feature feature) {
        return "[" + feature.getResource().getFileNameWithoutExtension() + "]";
    }

    public static Description getScenarioDescription(Scenario scenario) {
        String featureName = getFeatureName(scenario.getFeature());
        return Description.createTestDescription(featureName, scenario.getDisplayMeta() + ' ' + scenario.getName());
    }

    public FeatureInfo(Feature feature, String tagSelector) {
        this.feature = feature;
        this.featureInfoList.add(this);
        getFeatureName(feature);

        description = Description.createTestDescription(getFeatureName(feature),feature.getResource().getPackageQualifiedName(),this);

        FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
        CallContext callContext = new CallContext(null, true, this);
        exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null);
        unit = new FeatureExecutionUnit(exec);
        unit.init(null);
        List<ScenarioExecutionUnit> scenarioExecutionUnits = unit.getScenarioExecutionUnits();
        for (ScenarioExecutionUnit u : unit.getScenarioExecutionUnits()) {
            Description scenarioDescription = getScenarioDescription(u.scenario);
            description.addChild(scenarioDescription);
        }
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        if (notifier == null) {
            return true;
        }
        if(scenario.getFeature() == feature) {
            notifier.fireTestStarted(getScenarioDescription(scenario));
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {
        if (notifier == null) { // dynamic scenario outline background
            return;
        }
        if(result.getScenario().getFeature() == feature) {
            Description scenarioDescription = getScenarioDescription(result.getScenario());
            if (result.isFailed()) {
                notifier.fireTestFailure(new Failure(scenarioDescription, result.getError()));
            }
            // apparently this method should be always called
            // even if fireTestFailure was called
            notifier.fireTestFinished(scenarioDescription);
        }
     }

    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }

    public Feature getFeature() {
        return feature;
    }

    @Override
    public void reportPerfEvent(PerfEvent event) {

    }

}

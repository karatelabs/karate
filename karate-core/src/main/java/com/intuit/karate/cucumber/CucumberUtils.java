/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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

import com.intuit.karate.exception.KarateException;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValueMap;
import cucumber.runtime.FeatureBuilder;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class CucumberUtils {

    private CucumberUtils() {
        // only static methods
    }   

    public static KarateBackend getBackendWithGlue(ScriptEnv env, ScriptContext parentContext, 
            Map<String, Object> callArg, boolean reuseParentConfig) {
        KarateBackend backend = new KarateBackend(env, parentContext, callArg, reuseParentConfig);
        ClassLoader defaultClassLoader = Thread.currentThread().getContextClassLoader();
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(defaultClassLoader));
        backend.loadGlue(glue, null);
        return backend;
    }

    public static CucumberFeature parse(String text) {
        final List<CucumberFeature> features = new ArrayList<>();
        final FeatureBuilder builder = new FeatureBuilder(features);
        Parser parser = new Parser(builder);
        parser.parse(text, "", 0);
        CucumberFeature cucumberFeature = features.get(0);
        cucumberFeature.setI18n(parser.getI18nLanguage());
        return cucumberFeature;
    }

    public static ScriptValueMap call(FeatureWrapper feature, ScriptContext parentContext, 
            Map<String, Object> callArg, boolean reuseParentConfig) {
        ScriptEnv env = feature.getEnv();
        KarateBackend backend = getBackendWithGlue(env, parentContext, callArg, reuseParentConfig);
        for (FeatureSection section : feature.getSections()) {
            if (section.isOutline()) {
                ScenarioOutlineWrapper outline = section.getScenarioOutline();
                for (ScenarioWrapper scenario : outline.getScenarios()) {
                    call(scenario, backend);
                }
            } else {
                call(section.getScenario(), backend);
            }
        }
        return backend.getStepDefs().getContext().getVars();
    }

    private static void call(ScenarioWrapper scenario, KarateBackend backend) {
        for (StepWrapper step : scenario.getSteps()) {
            StepResult result = step.run(backend);
            if (!result.isPass()) {
                ScriptEnv env = scenario.getFeature().getEnv();
                throw new KarateException("failed: " + env, result.getError());
            }
        }
    }

}

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
import cucumber.runtime.AmbiguousStepDefinitionsException;
import cucumber.runtime.FeatureBuilder;
import cucumber.runtime.Glue;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.StepDefinitionMatch;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.I18n;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
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

    public static void call(ScenarioWrapper scenario, KarateBackend backend) {
        for (StepWrapper step : scenario.getSteps()) {
            StepResult result = runStep(step, backend);
            if (!result.isPass()) {                
                throw new KarateException("failed: " + backend.getEnv(), result.getError());
            }
        }
    }
    
    public static StepResult runStep(StepWrapper step, KarateBackend backend) {
        FeatureWrapper wrapper = step.getScenario().getFeature();
        CucumberFeature feature = wrapper.getFeature();
        return runStep(wrapper.getPath(), step.getStep(), backend.getEnv().reporter, feature.getI18n(), backend.getGlue(), true);
    }    
    
    private static final DummyReporter DUMMY_REPORTER = new DummyReporter();
    
    // adapted from cucumber.runtime.Runtime.runStep
    public static StepResult runStep(String featurePath, Step step, Reporter reporter, I18n i18n, Glue glue, boolean called) {
        if (reporter == null) {
            reporter = DUMMY_REPORTER;
        }      
        StepDefinitionMatch match;
        Result result;
        try {
            match = glue.stepDefinitionMatch(featurePath, step, i18n);
        } catch (AmbiguousStepDefinitionsException e) {
            match = e.getMatches().get(0);
            result = new Result(Result.FAILED, 0L, e, KarateReporter.DUMMY_OBJECT);
            reportStep(reporter, step, match, result);
            return new StepResult(step, e);
        }
        if (match == null) {
            reportStep(reporter, step, Match.UNDEFINED, Result.UNDEFINED);
            return new StepResult(step, new KarateException("match undefined"));
        }
        String status = Result.PASSED;
        Throwable error = null;
        long startTime = System.nanoTime();
        try {
            match.runStep(i18n);
        } catch (Throwable t) {
            error = t;
            status = Result.FAILED;
        } finally {
            long duration = called ? 0 : System.nanoTime() - startTime;
            result = new Result(status, duration, error, KarateReporter.DUMMY_OBJECT);
            reportStep(reporter, step, match, result);
            return new StepResult(step, error);
        }        
    }
    
    private static void reportStep(Reporter reporter, Step step, Match match, Result result) {
        if (reporter instanceof KarateReporter) {
            KarateReporter karateReporter = (KarateReporter) reporter;
            karateReporter.karateStep(step);
        }        
        reporter.match(match);
        reporter.result(result);
    }

}

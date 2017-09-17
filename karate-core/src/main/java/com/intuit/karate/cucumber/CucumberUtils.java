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
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import cucumber.runtime.AmbiguousStepDefinitionsException;
import cucumber.runtime.FeatureBuilder;
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
    
    public static CucumberFeature parse(String text, String featurePath) {
        final List<CucumberFeature> features = new ArrayList<>();
        final FeatureBuilder builder = new FeatureBuilder(features);
        Parser parser = new Parser(builder);
        parser.parse(text, featurePath, 0);
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
        return runStep(wrapper.getPath(), step.getStep(), backend.getEnv().reporter, feature.getI18n(), backend, true);
    }    
    
    private static final DummyReporter DUMMY_REPORTER = new DummyReporter();
    
    // adapted from cucumber.runtime.Runtime.runStep
    public static StepResult runStep(String featurePath, Step step, Reporter reporter, I18n i18n, 
            KarateBackend backend, boolean called) {
        backend.beforeStep(featurePath, step);
        if (reporter == null) {
            reporter = DUMMY_REPORTER;
        }      
        StepDefinitionMatch match;
        try {
            match = backend.getGlue().stepDefinitionMatch(featurePath, step, i18n);
        } catch (AmbiguousStepDefinitionsException e) {
            match = e.getMatches().get(0);
            Result result = new Result(Result.FAILED, 0L, e, KarateReporter.DUMMY_OBJECT);
            return afterStep(reporter, step, match, result, e, featurePath, backend, called);
        }
        if (match == null) {
            return afterStep(reporter, step, Match.UNDEFINED, Result.UNDEFINED, 
                    new KarateException("syntax error: " + step.getName()),
                    featurePath, backend, called);
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
            Result result = new Result(status, duration, error, KarateReporter.DUMMY_OBJECT);
            return afterStep(reporter, step, match, result, error, featurePath, backend, called);
        }        
    }
    
    private static StepResult afterStep(Reporter reporter, Step step, Match match, Result result, 
            Throwable error, String feature, KarateBackend backend, boolean called) {
        boolean isKarateReporter = reporter instanceof KarateReporter;
        if (isKarateReporter) {
            if (error != null && backend.getVars() != null) { // dump variable state to log for convenience         
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, ScriptValue> entry : backend.getVars().entrySet()) {
                    sb.append(entry.getValue().toPrettyString(entry.getKey()));
                }
                backend.getEnv().logger.error("{}:{} - variable state:\n{}", feature, step.getLine(), sb);         
            }            
            KarateReporter karateReporter = (KarateReporter) reporter;
            karateReporter.karateStep(step); // this would also collect log output into a 'docstring'
        }
        if (!called || isKarateReporter) { // don't confuse cucumber native reporters with called steps
            reporter.match(match);
            reporter.result(result);            
        }
        StepResult stepResult = new StepResult(step, result, error);
        backend.afterStep(feature, stepResult);
        return stepResult;
    }

}

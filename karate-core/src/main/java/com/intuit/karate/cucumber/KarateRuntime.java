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

import cucumber.runtime.CucumberScenarioImpl;
import cucumber.runtime.CucumberStats;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.io.ResourceLoader;
import gherkin.I18n;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import java.util.Collections;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class KarateRuntime extends Runtime {  
    
    private final KarateBackend backend;
    private final CucumberStats stats;
    private CucumberScenarioImpl scenarioResult;
    private boolean failed;
    
    public KarateRuntime(ResourceLoader resourceLoader, ClassLoader classLoader, KarateBackend backend,
                   RuntimeOptions runtimeOptions, RuntimeGlue glue) { 
        super(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, glue);
        this.backend = backend;
        this.stats = new CucumberStats(runtimeOptions.isMonochrome());
    }
    
    private void addStepToCounterAndResult(Result result) {
        scenarioResult.add(result);
        stats.addStep(result);         
    }

    @Override
    public void runStep(String featurePath, Step step, Reporter reporter, I18n i18n) {
        if (failed) {
            if (reporter instanceof KarateReporter) { // simulate cucumber flow to keep json-formatter happy                
                ((KarateReporter) reporter).karateStep(step);
            }
            reporter.match(Match.UNDEFINED);
            addStepToCounterAndResult(Result.SKIPPED);
            reporter.result(Result.SKIPPED);
            return;
        }
        StepResult result = CucumberUtils.runStep(featurePath, step, reporter, i18n, backend, false);
        if (!result.isPass()) {
            addError(result.getError());
            failed = true; // skip remaining steps    
        }
        addStepToCounterAndResult(result.getResult());       
    } 
    
    @Override
    public void buildBackendWorlds(Reporter reporter, Set<Tag> tags, Scenario gherkinScenario) {
        backend.buildWorld();
        scenarioResult = new CucumberScenarioImpl(reporter, tags, gherkinScenario);
    }      

    @Override
    public void disposeBackendWorlds(String scenarioDesignation) {
        stats.addScenario(scenarioResult.getStatus(), scenarioDesignation);
        backend.disposeWorld();
        failed = false; // else a failed scenario results in all remaining ones in the feature being skipped !
    }

    @Override
    public void printSummary() {
        stats.printStats(System.out, false);
    }        
    
}

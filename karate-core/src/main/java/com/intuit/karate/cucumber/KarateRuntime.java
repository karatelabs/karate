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

import com.intuit.karate.Logger;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.http.HttpConfig;
import cucumber.runtime.CucumberScenarioImpl;
import cucumber.runtime.CucumberStats;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
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
    private boolean stopped;
    private ScriptContext prevContext;

    public KarateRuntime(KarateRuntimeOptions kro, KarateBackend backend, RuntimeGlue glue) {
        super(kro.getResourceLoader(), kro.getClassLoader(), Collections.singletonList(backend), kro.getRuntimeOptions(), glue);
        this.backend = backend;
        this.stats = new CucumberStats(kro.getRuntimeOptions().isMonochrome());
    }
    
    public Logger getLogger() {
        return backend.getEnv().logger;
    }

    private void addStepToCounterAndResult(Result result) {
        scenarioResult.add(result);
        stats.addStep(result);
    }

    @Override
    public void runStep(String featurePath, Step step, Reporter reporter, I18n i18n) {
        if (stopped) {
            Match match = Match.UNDEFINED;
            Result result = Result.SKIPPED;
            if (reporter instanceof KarateReporter) { // simulate cucumber flow to keep json-formatter happy
                // @pthomas3^^ (please review and post your comment on this change)
                // below call internally invokes reporter.match(match) and reporter.result(result) as
                // KarateReporterBase.karateStep() -> karateStepProceed() -> result() / match()
                // causing double invocation of reporter.match(match) and reporter.result(result)
                // because they were invoked below this if.  To avoid this, we should moved them to else block
                // TODO: remove this comment (meant to get clarification from pthomas3) before merging the PR
                ((KarateReporter) reporter).karateStep(step, match, result, backend.getCallContext(), backend.getStepDefs().getContext());
            } else {
                reporter.match(match);
                reporter.result(result);
            }
            addStepToCounterAndResult(result);
            return;
        }
        StepResult result = CucumberUtils.runStep(step, reporter, i18n, backend);
        if (!result.isPass() || result.isAbort()) {
            if (!result.isAbort()) {
                addError(result.getError());
                backend.setScenarioError(result.getError());
            }
            prevContext = backend.getStepDefs().getContext();
            stopped = true; // skip remaining steps
        }
        addStepToCounterAndResult(result.getResult());
    }

    @Override
    public void buildBackendWorlds(Reporter reporter, Set<Tag> tags, Scenario scenario) {
        backend.buildWorld();
        // tags only work at top-level, this does not apply to 'called' features
        CucumberUtils.resolveTagsAndTagValues(backend, tags);
        // 'karate.info' also does not apply to 'called' features
        CucumberUtils.initScenarioInfo(scenario, backend);
        scenarioResult = new CucumberScenarioImpl(reporter, tags, scenario);
    }

    @Override
    public void disposeBackendWorlds(String scenarioDesignation) {
        stats.addScenario(scenarioResult.getStatus(), scenarioDesignation);
        prevContext = backend.getStepDefs().getContext();
        invokeAfterHookIfConfigured(false);
        backend.disposeWorld();
        stopped = false; // else a failed scenario results in all remaining ones in the feature being skipped !
    }

    @Override
    public void printSummary() {
        stats.printStats(System.out, false);
    }       
    
    public void afterFeature() {
        invokeAfterHookIfConfigured(true);
    }
    
    private void invokeAfterHookIfConfigured(boolean afterFeature) {
        if (prevContext == null) { // edge case where there are zero scenarios, e.g. only a Background:
            ScriptEnv env = backend.getEnv();
            env.logger.warn("no runnable scenarios found: {}", env);
            return;
        }
        HttpConfig config = prevContext.getConfig();
        ScriptValue sv = afterFeature ? config.getAfterFeature() : config.getAfterScenario();
        if (sv.isFunction()) {
            try {
                sv.invokeFunction(prevContext);
            } catch (Exception e) {
                String prefix = afterFeature ? "afterFeature" : "afterScenario";
                prevContext.logger.warn("{} hook failed: {}", prefix, e.getMessage());
            }
        }
    }    

}

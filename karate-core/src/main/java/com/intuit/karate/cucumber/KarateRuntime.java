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

import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValue;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 * @author pthomas3
 */
public class KarateRuntime extends Runtime {

    private final KarateBackend backend;
    private final CucumberStats stats;
    private CucumberScenarioImpl scenarioResult;
    private boolean failed;
    private ScriptContext prevContext;

    public KarateRuntime(KarateRuntimeOptions kro, KarateBackend backend, RuntimeGlue glue) {
        super(kro.getResourceLoader(), kro.getClassLoader(), Collections.singletonList(backend), kro.getRuntimeOptions(), glue);
        this.backend = backend;
        this.stats = new CucumberStats(kro.getRuntimeOptions().isMonochrome());
    }

    private void addStepToCounterAndResult(Result result) {
        scenarioResult.add(result);
        stats.addStep(result);
    }

    @Override
    public void runStep(String featurePath, Step step, Reporter reporter, I18n i18n) {
        if (failed) {
            Match match = Match.UNDEFINED;
            Result result = Result.SKIPPED;
            if (reporter instanceof KarateReporter) { // simulate cucumber flow to keep json-formatter happy                
                ((KarateReporter) reporter).karateStep(step, match, result, backend.getCallContext());
            }
            reporter.match(match);
            addStepToCounterAndResult(result);
            reporter.result(result);
            return;
        }
        StepResult result = CucumberUtils.runStep(featurePath, step, reporter, i18n, backend);
        if (!result.isPass()) {
            addError(result.getError());
            backend.setScenarioError(result.getError());
            prevContext = backend.getStepDefs().getContext();
            failed = true; // skip remaining steps    
        }
        addStepToCounterAndResult(result.getResult());
    }

    private void resolveTagValues(Set<Tag> tags) {
        if (tags.isEmpty()) {
            backend.setTagValues(Collections.emptyMap());
            backend.setTags(Collections.emptyList());
            return;
        }
        Map<String, List<String>> tagValues = new LinkedHashMap(tags.size());
        Map<String, Integer> tagKeyLines = new HashMap(tags.size());
        List<String> rawTags = new ArrayList(tags.size());
        for (Tag tag : tags) {
            Integer line = tag.getLine();
            String name = tag.getName();
            List<String> values = new ArrayList();
            if (name.startsWith("@")) {
                name = name.substring(1);
            }
            rawTags.add(name);
            Integer prevTagLine = tagKeyLines.get(name);
            if (prevTagLine != null && prevTagLine > line) {
                continue; // skip tag with same name but lower line number, 
            }
            tagKeyLines.put(name, line);
            int pos = name.indexOf('=');
            if (pos != -1) {
                if (name.length() == pos + 1) { // edge case, @foo=
                    values.add("");
                } else {
                    String temp = name.substring(pos + 1);
                    for (String s : temp.split(",")) {
                        values.add(s);
                    }
                }
                name = name.substring(0, pos);
            }
            tagValues.put(name, values);
        }
        backend.setTagValues(tagValues);
        backend.setTags(rawTags);
    }

    @Override
    public void buildBackendWorlds(Reporter reporter, Set<Tag> tags, Scenario scenario) {
        backend.buildWorld();
        resolveTagValues(tags);
        ScenarioInfo info = new ScenarioInfo();
        ScriptEnv env = backend.getEnv();
        info.setFeatureDir(env.featureDir.getPath());
        info.setFeatureFileName(env.featureName);   
        info.setScenarioName(scenario.getName());
        info.setScenarioType(scenario.getKeyword()); // Scenario | Scenario Outline
        info.setScenarioDescription(scenario.getDescription());
        backend.setScenarioInfo(info);
        scenarioResult = new CucumberScenarioImpl(reporter, tags, scenario);
    }

    @Override
    public void disposeBackendWorlds(String scenarioDesignation) {
        stats.addScenario(scenarioResult.getStatus(), scenarioDesignation);
        prevContext = backend.getStepDefs().getContext();
        ScriptValue sv = prevContext.getAfterScenario();
        if (sv.isFunction()) {
            sv.invokeFunction(prevContext);            
        }
        backend.disposeWorld();        
        failed = false; // else a failed scenario results in all remaining ones in the feature being skipped !
    }

    @Override
    public void printSummary() {   
        stats.printStats(System.out, false);
    }
    
    public void afterFeature() {
        ScriptValue sv = prevContext.getAfterFeature();
        if (sv.isFunction()) {
            sv.invokeFunction(prevContext);            
        }        
    }

}

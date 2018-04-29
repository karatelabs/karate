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

import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.StepDefs;
import cucumber.runtime.Backend;
import cucumber.runtime.ClassFinder;
import cucumber.runtime.Glue;
import cucumber.runtime.UnreportedStepExecutor;
import cucumber.runtime.java.JavaBackend;
import cucumber.runtime.snippets.FunctionNameGenerator;
import gherkin.formatter.model.Step;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class KarateBackend implements Backend {

    private final JavaBackend backend;
    private final KarateObjectFactory objectFactory;
    private final CallContext callContext;
    private final String featurePath;
    private Glue glue;        

    public String getFeaturePath() {
        return featurePath;
    }    

    public void setTags(List<String> tags) {
        callContext.setTags(tags);
    }

    public void setTagValues(Map<String, List<String>> tagValues) {
        callContext.setTagValues(tagValues);
    }

    public void setScenarioInfo(ScenarioInfo scenarioInfo) {
        callContext.setScenarioInfo(scenarioInfo);
    }

    public void setScenarioError(Throwable error) {
        objectFactory.getStepDefs().getContext().setScenarioError(error);
    }

    public boolean isCalled() {
        return callContext.isCalled();
    }

    public CallContext getCallContext() {
        return callContext;
    }

    public ScriptEnv getEnv() {
        return objectFactory.getEnv();
    }

    public ScriptValueMap getVars() {
        return getStepDefs().getContext().getVars();
    }

    public KarateBackend(FeatureWrapper feature, CallContext callContext) {
        this.callContext = callContext;
        this.featurePath = feature.getPath();
        ClassFinder classFinder = new KarateClassFinder(feature.getEnv().fileClassLoader);
        objectFactory = new KarateObjectFactory(feature.getEnv(), callContext);
        backend = new JavaBackend(objectFactory, classFinder);
    }

    public KarateObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public StepDefs getStepDefs() {
        return objectFactory.getStepDefs();
    }

    public Glue getGlue() {
        return glue;
    }

    public String getCallingFeature() {
        if (callContext.parentContext != null) {
            return callContext.parentContext.getEnv().featureName;
        } else {
            return null;
        }
    }

    @Override
    public void loadGlue(Glue glue, List<String> NOT_USED) {
        this.glue = glue;
        Class glueCodeClass = StepDefs.class;
        for (Method method : glueCodeClass.getMethods()) {
            backend.loadGlue(glue, method, glueCodeClass);
        }
    }

    @Override
    public void setUnreportedStepExecutor(UnreportedStepExecutor executor) {
        backend.setUnreportedStepExecutor(executor);
    }

    @Override
    public void buildWorld() {
        backend.buildWorld();
    }

    @Override
    public void disposeWorld() {
        backend.disposeWorld();
    }

    @Override
    public String getSnippet(Step step, FunctionNameGenerator functionNameGenerator) {
        return backend.getSnippet(step, functionNameGenerator);
    }

}

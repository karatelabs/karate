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

/**
 *
 * @author pthomas3
 */
public class KarateBackend implements Backend {    
    
    private final JavaBackend backend;
    private final KarateObjectFactory objectFactory;
    private final CallContext callContext;
    private Glue glue;

    public void setTags(List<String> tags) {
        callContext.setTags(tags);
    }      
    
    public ScriptEnv getEnv() {
        return objectFactory.getEnv();
    }
    
    public ScriptValueMap getVars() {
        StepDefs stepDefs = getStepDefs();
        if (stepDefs == null) { // first step
            return null;
        }
        return stepDefs.getContext().getVars();
    }
    
    public void beforeStep(String feature, Step step) {
        getEnv().debug.beforeStep(feature, step.getLine(), step, getVars());
    }
    
    public void afterStep(String feature, StepResult result) {
        getEnv().debug.afterStep(feature, result.getStep().getLine(), result, getVars());
    }    
    
    public KarateBackend(ScriptEnv env, CallContext callContext) {
        this.callContext = callContext;
        ClassFinder classFinder = new KarateClassFinder(env.fileClassLoader);
        objectFactory = new KarateObjectFactory(env, callContext);
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

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
package com.intuit.karate;

import com.intuit.karate.cucumber.StepInterceptor;
import com.intuit.karate.cucumber.ScenarioInfo;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class CallContext {
    
    public final ScriptContext parentContext;
    public final int callDepth;
    public final Map<String, Object> callArg;
    public final boolean reuseParentContext;
    public final boolean evalKarateConfig;
    public final int loopIndex;
    public final String httpClientClass;
    public final StepInterceptor stepInterceptor;
    
    private List<String> tags;
    private Map<String, List<String>> tagValues;    
    private ScenarioInfo scenarioInfo;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setTagValues(Map<String, List<String>> tagValues) {
        this.tagValues = tagValues;
    }

    public Map<String, List<String>> getTagValues() {
        return tagValues;
    }        

    public void setScenarioInfo(ScenarioInfo scenarioInfo) {
        this.scenarioInfo = scenarioInfo;
    }

    public ScenarioInfo getScenarioInfo() {
        return scenarioInfo;
    }    
    
    public boolean isCalled() {
        return callDepth > 0;
    }
    
    public CallContext(ScriptContext parentContext, int callDepth, Map<String, Object> callArg, int loopIndex,
        boolean reuseParentContext, boolean evalKarateConfig, String httpClientClass, StepInterceptor stepInterceptor) {
        this.parentContext = parentContext;
        this.callDepth = callDepth;
        this.callArg = callArg;
        this.loopIndex = loopIndex;
        this.reuseParentContext = reuseParentContext;
        this.evalKarateConfig = evalKarateConfig;
        this.httpClientClass = httpClientClass;
        this.stepInterceptor = stepInterceptor == null ? new StepInterceptor() : stepInterceptor;
    }
    
}

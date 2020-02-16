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
package com.intuit.karate.core;

import com.intuit.karate.Results;
import com.intuit.karate.http.HttpRequestBuilder;

/**
 *
 * @author pthomas3
 */
public class MandatoryTagHook implements ExecutionHook {

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        if (context.getCallDepth() > 0) {
            return true; // only enforce tags for top-level scenarios (not called ones)
        }
        Tags tags = scenario.getTagsEffective();
        boolean found = false;
        for (Tag tag : tags) {
            if ("testId".equals(tag.getName())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("testId tag not present at line: " + scenario.getLine());
        }
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {

    }    

    @Override
    public boolean beforeFeature(Feature feature, ExecutionContext context) {
        return true;
    }

    @Override
    public void afterFeature(FeatureResult result, ExecutionContext context) {
        
    }    

    @Override
    public void beforeAll(Results results) {

    }

    @Override
    public void afterAll(Results results) {

    }        

    @Override
    public boolean beforeStep(Step step, ScenarioContext context) {
        return true;
    }

    @Override
    public void afterStep(StepResult result, ScenarioContext context) {

    }        
        
    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }    
    
    @Override
    public void reportPerfEvent(PerfEvent event) {
        
    }

}

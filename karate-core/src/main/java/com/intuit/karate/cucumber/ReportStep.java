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
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class ReportStep {
    
    private final Step step;  
    private final Match match;
    private final Result result;
    private final String log;
    private final CallContext callContext;
    private List<ReportStep> called;
        
    public ReportStep(Step step, Match match, Result result, String log, CallContext callContext) {
        this.step = step;
        this.match = match;
        this.result = result;
        this.log = log;
        this.callContext = callContext;
    }      
    
    public ReportStep addCalled(ReportStep step) {
        if (called == null) {
            called = new ArrayList();
        }
        called.add(step);
        return step;        
    }  
    
    public ReportStep addCalled(Step step, Match match, Result result, String log, CallContext callContext) {
        return addCalled(new ReportStep(step, match, result, log, callContext));
    }

    public List<ReportStep> getCalled() {
        return called;
    }

    public Match getMatch() {
        return match;
    }

    public Result getResult() {
        return result;
    }       

    public Step getStep() {
        return step;
    } 

    public String getLog() {
        return log;
    }

    public CallContext getCallContext() {
        return callContext;
    }     
    
}

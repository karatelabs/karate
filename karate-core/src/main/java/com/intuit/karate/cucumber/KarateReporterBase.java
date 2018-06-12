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
import com.intuit.karate.JsonUtils;
import com.intuit.karate.FileLogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import gherkin.formatter.model.DocString;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;

/**
 *
 * @author pthomas3
 */
public abstract class KarateReporterBase implements KarateReporter {

    protected String tempFilePath;
    protected FileLogAppender appender;  

    @Override
    public void setLogger(Logger logger) {
        appender = new FileLogAppender(tempFilePath, logger);
    }

    public static Result passed(long time) {
        return new Result(Result.PASSED, time, null, StepResult.DUMMY_OBJECT);
    }

    public static Result failed(long time, Throwable t) {
        return new Result(Result.FAILED, null, t, StepResult.DUMMY_OBJECT);
    }

    @Override // this is a hack to bring called feature steps into cucumber reports
    public void callBegin(FeatureWrapper feature, CallContext callContext) {
        appender.collect(); // clear log to suppress misleading stack trace from previous call if any
        DocString docString = null;
        if (callContext.callArg != null) {
            String json = JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(callContext.callArg));
            docString = new DocString("", json, 0);
        }
        String prefix = "call" + (callContext.loopIndex == -1 ? "" : "[" + callContext.loopIndex + "]");
        String featureName = StringUtils.trimToNull(feature.getFeature().getGherkinFeature().getName());
        String scenarioName = StringUtils.trimToNull(feature.getFirstScenarioName());
        if (featureName != null) {
            prefix = prefix + " [" + featureName + "]";
        }
        if (scenarioName != null) {
            prefix = prefix + " [" + scenarioName + "]";
        }
        Step step = new Step(null, "* ", prefix + " " + feature.getPath(), 0, null, docString);
        karateStep(step, Match.UNDEFINED, passed(0L), callContext);
    }    

    @Override // see the step() method for an explanation of this hack
    public void karateStep(Step step, Match match, Result result, CallContext callContext) {
        if (step.getDocString() == null) {
            String log = appender.collect();
            DocString docString = log.isEmpty() ? null : new DocString("", log, step.getLine());
            step = new Step(step.getComments(), step.getKeyword(), step.getName(), step.getLine(), step.getRows(), docString);
        }
        karateStepProceed(step, match, result, callContext);
    }
    
    @Override
    public void step(Step step) {
        // hack alert !
        // normally the cucumber formatter iterates over all steps before execution begins
        // we don't, and this actually speeds up things considerably, see also CucumberUtils.runStep()
        // now we can 'in-line' called feature steps in the final report, plus time stats - see StepWrapper.run()
        // the downside is that on failure, we don't show skipped steps (only in called features)
        // but really, this should not be a big concern for karate users
    }     

}

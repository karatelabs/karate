/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.runtime;

import com.intuit.karate.AssignType;
import com.intuit.karate.Config;
import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchType;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioRuntime implements Runnable {

    public final FeatureRuntime featureRuntime;
    public final ScenarioRuntime background;
    public final Scenario scenario;
    public final ScenarioActions actions;
    public final Logger logger = new Logger();
    public final ScenarioResult result;
    public final ScenarioEngine engine;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        this(featureRuntime, scenario, null);
    }

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario, ScenarioRuntime background) {
        this.featureRuntime = featureRuntime;
        this.scenario = scenario;
        if (background == null) {
            this.background = null;
            result = new ScenarioResult(scenario, null);
        } else {
            this.background = background;
            result = new ScenarioResult(scenario, background.result.getStepResults());
        }
        actions = new ScenarioActions(this);
        engine = new ScenarioEngine();
        // TODO caller
        // TODO config
    }

    public void addError(String message, Throwable t) {

    }

    public boolean isFailed() {
        return false;
    }

    private static final ThreadLocal<LogAppender> APPENDER = new ThreadLocal<LogAppender>() {
        @Override
        protected LogAppender initialValue() {
            String fileName = FileUtils.getBuildDir() + File.separator + Thread.currentThread().getName() + ".log";
            return new FileLogAppender(new File(fileName));
        }
    };

    public Step getCurrentStep() {
        return currentStep;
    }

    public boolean isStopped() {
        return stopped;
    }

    private List<Step> steps;
    private LogAppender appender;
    private StepResult lastStepResult;
    private Step currentStep;
    private boolean stopped;
    private int stepIndex;

    public void stepBack() {
        stopped = false;
        stepIndex -= 2;
        if (stepIndex < 0) {
            stepIndex = 0;
        }
    }

    public void stepReset() {
        stopped = false;
        stepIndex--;
        if (stepIndex < 0) { // maybe not required
            stepIndex = 0;
        }
    }

    public void stepProceed() {
        stopped = false;
    }

    private int nextStepIndex() {
        return stepIndex++;
    }

    @Override
    public void run() {
        if (appender == null) { // not perf, not ui
            appender = APPENDER.get();
        }
        logger.setAppender(appender);
        if (steps == null) {
            init();
        }
        try { // make sure we call next() even on crashes
            // and operate countdown latches, else we may hang the parallel runner
            if (steps == null) {
                init();
            }
            int count = steps.size();
            int index = 0;
            while ((index = nextStepIndex()) < count) {
                Step step = steps.get(index);
                lastStepResult = execute(step);
                if (lastStepResult == null) { // debug step-back !
                    continue;
                }
                result.addStepResult(lastStepResult);
                if (lastStepResult.isStopped()) {
                    stopped = true;
                }
            }
            stop();
        } catch (Exception e) {
            result.addError("scenario execution failed", e);
            logger.error("scenario execution failed: {}", e.getMessage());
        }
    }

    public void init() {
        engine.init();
        if (scenario.isDynamic()) {
            steps = scenario.getBackgroundSteps();
        } else {
            steps = background == null ? scenario.getStepsIncludingBackground() : scenario.getSteps();
            if (scenario.isOutline()) { // init examples row magic variables
                Map<String, Object> exampleData = scenario.getExampleData();
                engine.put("__row", exampleData);
                engine.put("__num", scenario.getExampleIndex());
                // TODO breaking change configure outlineVariablesAuto
                exampleData.forEach((k, v) -> engine.put(k, v));
            }
        }
        result.setThreadName(Thread.currentThread().getName());
        result.setStartTime(System.currentTimeMillis() - featureRuntime.suite.results.getStartTime());
    }

    // extracted for debug
    public StepResult execute(Step step) {
        Result stepResult = StepRuntime.execute(step, actions);
        StepResult sr = new StepResult(step, stepResult, null, null, null);
        // after step hooks
        return sr;
    }

    public void stop() {
        result.setEndTime(System.currentTimeMillis() - featureRuntime.suite.results.getStartTime());
        featureRuntime.result.addResult(result);
        // check if context is not-null
        // gatling clean up
        // after-scenario hook
        // embed collection for afterScenario
        // stop browser automation if running
        if (lastStepResult != null) {
            String stepLog = StringUtils.trimToNull(appender.collect());
            lastStepResult.appendToStepLog(stepLog);
        }
    }

    // these can get re-built or swapped, so cannot be final
    private Config config = new Config();

    //==========================================================================
    //
    public void call(boolean callonce, String line) {

    }

    public void assign(AssignType assignType, String name, String exp) {
        engine.assign(assignType, name, exp, false);
    }

    public void eval(String exp) {
        engine.eval(exp);
    }

    public void match(MatchType matchType, String expression, String path, String expected) {
        MatchResult mr = engine.match(matchType, expression, path, expected);
        if (!mr.pass) {
            logger.error("{}", mr);
            throw new KarateException(mr.message);
        }
    }

    public void set(String name, String path, String value) {

    }

    public void set(String name, String path, List<Map<String, String>> table) {

    }

    public void remove(String name, String path) {

    }

    public void table(String name, List<Map<String, String>> table) {

    }

    public void replace(String name, List<Map<String, String>> table) {

    }

    public void replace(String name, String token, String value) {

    }

    public void assertTrue(String expression) {
        if (!engine.assertTrue(expression)) {
            String message = "did not evaluate to 'true': " + expression;
            logger.error("{}", message);
            throw new KarateException(message);
        }
    }

    public void print(List<String> exps) {
        if (!config.isPrintEnabled()) {
            return;
        }
        String prev = ""; // handle rogue commas embedded in string literals
        StringBuilder sb = new StringBuilder();
        sb.append("[print]");
        for (String exp : exps) {
            if (!prev.isEmpty()) {
                exp = prev + StringUtils.trimToNull(exp);
            }
            if (exp == null) {
                sb.append("null");
            } else {
                Variable v = engine.getIfVariableReference(exp.trim()); // trim is important
                if (v == null) {
                    try {
                        v = engine.eval(exp);
                        prev = ""; // eval success, reset rogue comma detector
                    } catch (Exception e) {
                        prev = exp + ", ";
                        continue;
                    }
                }
                sb.append(' ').append(v.getAsPrettyString());
            }
        }
        logger.info("{}", sb);
    }

    public void configure(String key, String exp) {

    }

    public void url(String exp) {

    }

    public void path(List<String> paths) {

    }

    public void param(String name, List<String> values) {

    }

    public void params(String expr) {

    }

    public void cookie(String name, String value) {

    }

    public void cookies(String expr) {

    }

    public void header(String name, List<String> values) {

    }

    public void headers(String expr) {

    }

    public void formField(String name, List<String> values) {

    }

    public void formFields(String expr) {

    }

    public void request(String body) {

    }

    public void method(String method) {

    }

    public void retry(String until) {

    }

    public void soapAction(String action) {

    }

    public void multipartField(String name, String value) {

    }

    public void multipartFields(String expr) {

    }

    public void multipartFile(String name, String value) {

    }

    public void multipartFiles(String expr) {

    }

    public void status(int status) {

    }

    public void driver(String expression) {

    }

    public void robot(String expression) {

    }

}

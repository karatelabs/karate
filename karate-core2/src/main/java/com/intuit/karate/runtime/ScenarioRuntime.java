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
import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.match.MatchType;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class ScenarioRuntime implements Runnable {

    public final Logger logger = new Logger();

    public final FeatureRuntime featureRuntime;
    public final ScenarioRuntime background;
    public final ScenarioCall parentCall;
    public final Scenario scenario;
    public final ScenarioActions actions;
    public final ScenarioResult result;
    public final ScenarioEngine engine;
    public final ScenarioBridge bridge;
    public final ScenarioFileReader fileReader;
    public final Function<String, Object> readFunction;
    public final Collection<RuntimeHook> runtimeHooks;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        this(featureRuntime, scenario, null);
    }

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario, ScenarioRuntime background) {
        runtimeHooks = featureRuntime.suite.runtimeHooks;
        this.featureRuntime = featureRuntime;
        this.parentCall = featureRuntime.parentCall;
        if (parentCall.isNone()) {
            config = new Config();
            engine = new ScenarioEngine(this, new HashMap(), logger);
        } else if (parentCall.isGlobalScope()) {
            config = parentCall.parentRuntime.config;
            engine = new ScenarioEngine(this, parentCall.parentRuntime.engine.vars, logger);
        } else { // new, but clone and copy data
            config = new Config(parentCall.parentRuntime.config);
            engine = new ScenarioEngine(this, parentCall.parentRuntime.engine.copyVariables(false), logger);
        }
        this.scenario = scenario;
        if (background == null) {
            this.background = null;
            result = new ScenarioResult(scenario, null);
        } else {
            this.background = background;
            result = new ScenarioResult(scenario, background.result.getStepResults());
        }
        bridge = new ScenarioBridge(); // uses thread local to get "this"
        actions = new ScenarioActions(this);
        fileReader = new ScenarioFileReader(this);
        readFunction = s -> fileReader.readFile(s);
        if (featureRuntime.isPerfMode()) {
            appender = LogAppender.NO_OP;
        }
    }

    public boolean isFailed() {
        return error != null || result.isFailed();
    }

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
    private Throwable error;
    protected String jsEvalError;
    private boolean stopped;
    private boolean aborted;
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

    protected void logError(String message) {
        if (currentStep != null) {
            message = currentStep.getDebugInfo()
                    + "\n" + currentStep.toString()
                    + "\n" + message;
        }
        logger.error("{}", message);
    }

    @Override
    public void run() {
        try { // make sure we call afterRun() even on crashes
            // and operate countdown latches, else we may hang the parallel runner
            if (steps == null) {
                beforeRun();
            }
            int count = steps.size();
            int index = 0;
            while ((index = nextStepIndex()) < count) {
                currentStep = steps.get(index);
                lastStepResult = execute(currentStep);
                if (lastStepResult == null) { // debug step-back !
                    continue;
                }
                result.addStepResult(lastStepResult);
            }
        } catch (Exception e) {
            logError(e.getMessage());
        } finally {
            afterRun();
        }
    }

    protected static final ThreadLocal<ScenarioRuntime> LOCAL = new ThreadLocal<ScenarioRuntime>();

    private static final ThreadLocal<LogAppender> APPENDER = new ThreadLocal<LogAppender>() {
        @Override
        protected LogAppender initialValue() {
            String fileName = FileUtils.getBuildDir() + File.separator + Thread.currentThread().getName() + ".log";
            return new FileLogAppender(new File(fileName));
        }
    };

    public void beforeRun() {
        if (appender == null) { // not perf, not debug
            appender = APPENDER.get();
        }
        logger.setAppender(appender);
        LOCAL.set(this);
        engine.init();
        engine.setHiddenVariable(VariableNames.KARATE, bridge);
        engine.setHiddenVariable(VariableNames.READ, readFunction);
        if (scenario.isDynamic()) {
            steps = scenario.getBackgroundSteps();
        } else {
            steps = background == null ? scenario.getStepsIncludingBackground() : scenario.getSteps();
            if (scenario.isOutline()) { // init examples row magic variables
                Map<String, Object> exampleData = scenario.getExampleData();
                exampleData.forEach((k, v) -> engine.setVariable(k, v));
                engine.setVariable("__row", exampleData);
                engine.setVariable("__num", scenario.getExampleIndex());
                // TODO breaking change configure outlineVariablesAuto                
            }
            if (!parentCall.isNone()) {
                Variable arg = parentCall.getArg();
                engine.setVariable("__arg", arg);
                engine.setVariable("__loop", parentCall.getLoopIndex());
                if (arg != null && arg.isMap()) {
                    engine.setVariables(arg.getValue());
                }
            }
        }
        result.setThreadName(Thread.currentThread().getName());
        result.setStartTime(System.currentTimeMillis() - featureRuntime.suite.results.getStartTime());
        if (parentCall.isNone() && !parentCall.isKarateConfigDisabled()) {
            // evaluate config js, variables above will apply !
            evalConfigJs(featureRuntime.suite.karateBase);
            evalConfigJs(featureRuntime.suite.karateConfig);
            evalConfigJs(featureRuntime.suite.karateConfigEnv);
        }
    }

    private void evalConfigJs(String js) {
        if (js != null) {
            Variable fun = engine.evalKarateExpression(js);
            if (fun.isFunction()) {
                engine.setVariables(fun.evalAsMap());
            } else {
                logger.warn("config did not evaluate to js function: {}", js);
            }
        }
    }

    // extracted for debug
    public StepResult execute(Step step) {
        if (stopped) {
            Result stepResult;
            if (aborted && config.isAbortedStepsShouldPass()) {
                stepResult = Result.passed(0);
            } else {
                stepResult = Result.skipped();
            }
            StepResult sr = new StepResult(step, stepResult, null, null, null);
            // log / step visibility
            // after step hooks
            return sr;
        } else {
            Result stepResult = StepRuntime.execute(step, actions);
            // collect embeds
            // log / step visibility
            // collect log
            if (stepResult.isAborted()) { // we log only aborts for visibility
                aborted = true;
                stopped = true;
                logger.debug("abort at {}", step.getDebugInfo());
            } else if (stepResult.isFailed()) {
                stopped = true;
                error = stepResult.getError();
                if (jsEvalError != null) {
                    logError(jsEvalError);
                } else {
                    logError(error.getMessage());
                }
            }
            LOCAL.set(this); // restore, since a call may have switched this to a nested scenario
            StepResult sr = new StepResult(step, stepResult, null, null, null);
            // after step hooks           
            return sr;
        }
    }

    public void afterRun() {
        try {
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
        } catch (Exception e) {
            logError(e.getMessage());
        } finally {
            LOCAL.remove();
            // next
        }
    }

    // engine ==================================================================
    //
    public void call(boolean callOnce, String line) {
        Variable v = engine.call(callOnce, line, true);
        if (v.isMap()) {
            engine.setVariables(v.getValue());
        }
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
            logError(mr.message);
            throw new KarateException(mr.message);
        }
    }

    public void set(String name, String path, String exp) {
        engine.set(name, path, exp);
    }

    public void set(String name, String path, List<Map<String, String>> table) {
        engine.setViaTable(name, path, table);
    }

    public void remove(String name, String path) {
        engine.remove(name, path);
    }

    public void table(String name, List<Map<String, String>> table) {
        engine.table(name, table);
    }

    public void replace(String name, String token, String value) {
        engine.replace(name, token, value);
    }

    public void replace(String name, List<Map<String, String>> table) {
        engine.replaceTable(name, table);
    }

    public void assertTrue(String expression) {
        if (!engine.assertTrue(expression)) {
            String message = "did not evaluate to 'true': " + expression;
            logError(message);
            throw new KarateException(message);
        }
    }

    public void print(List<String> exps) {
        if (!config.isPrintEnabled()) {
            return;
        }
        engine.print(exps);
    }

    // gatling =================================================================
    //   
    private PerfEvent prevPerfEvent;

    public void logLastPerfEvent(String failureMessage) {
        if (prevPerfEvent != null && featureRuntime.isPerfMode()) {
            if (failureMessage != null) {
                prevPerfEvent.setFailed(true);
                prevPerfEvent.setMessage(failureMessage);
            }
            featureRuntime.getPerfRuntime().reportPerfEvent(prevPerfEvent);
        }
        prevPerfEvent = null;
    }

    public void capturePerfEvent(PerfEvent event) {
        logLastPerfEvent(null);
        prevPerfEvent = event;
    }

    // http ====================================================================
    //        
    private Config config;
    private ScenarioHttpClient http;
    private HttpRequest prevRequest;

    public HttpRequest getPrevRequest() {
        return prevRequest;
    }

    public Config getConfig() {
        return config;
    }

    public void configure(Config config) {
        this.config = config;
        http = ScenarioHttpClient.construct(config);
    }

    public void updateConfigCookies(Map<String, Cookie> cookies) {
        if (cookies == null) {
            return;
        }
        if (config.getCookies().isNull()) {
            config.setCookies(new Variable(cookies));
        } else {
            Map<String, Object> map = config.getCookies().evalAsMap();
            map.putAll(cookies);
            config.setCookies(new Variable(map));
        }
    }

    public void configure(String key, String exp) {
        Variable v = engine.evalKarateExpression(exp);
        configure(key, v);
    }

    public void configure(String key, Variable v) {
        key = StringUtils.trimToEmpty(key);
        // if next line returns true, http-client needs re-building
        if (config.configure(key, v)) {
            if (key.startsWith("httpClient")) { // special case
                http = ScenarioHttpClient.construct(config);
            } else {
                http.configure(config);
            }
        }
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

    // ui driver / robot =======================================================
    //
    public void driver(String expression) {

    }

    public void robot(String expression) {

    }

}

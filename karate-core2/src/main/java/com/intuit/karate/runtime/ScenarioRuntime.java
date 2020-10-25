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

import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public final Collection<RuntimeHook> runtimeHooks;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        this(featureRuntime, scenario, null);
    }

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario, ScenarioRuntime background) {
        runtimeHooks = featureRuntime.suite.runtimeHooks;
        this.featureRuntime = featureRuntime;
        this.parentCall = featureRuntime.parentCall;
        if (parentCall.isNone()) {
            engine = new ScenarioEngine(new Config(), this, new HashMap(), logger);
        } else if (parentCall.isSharedScope()) {
            Config config = parentCall.parentRuntime.engine.getConfig();
            engine = new ScenarioEngine(config, this, parentCall.parentRuntime.engine.vars, logger);
        } else { // new, but clone and copy data
            Config config = new Config(parentCall.parentRuntime.engine.getConfig());
            engine = new ScenarioEngine(config, this, parentCall.parentRuntime.engine.copyVariables(false), logger);
        }
        actions = new ScenarioActions(engine);
        this.scenario = scenario;
        if (background == null) {
            this.background = null;
            result = new ScenarioResult(scenario, null);
        } else {
            this.background = background;
            result = new ScenarioResult(scenario, background.result.getStepResults());
        }
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
    protected String evalJsError;
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

    public Map<String, Object> getScenarioInfo() {
        Map<String, Object> info = new HashMap(6);
        Path featurePath = featureRuntime.feature.getPath();
        if (featurePath != null) {
            info.put("featureDir", featurePath.getParent().toString());
            info.put("featureFileName", featurePath.getFileName().toString());
        }
        info.put("scenarioName", scenario.getName());
        info.put("scenarioDescription", scenario.getDescription());
        info.put("scenarioType", scenario.getKeyword());
        String errorMessage = error == null ? null : error.getMessage();
        info.put("errorMessage", errorMessage);
        return info;
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

    private static final ThreadLocal<LogAppender> APPENDER = new ThreadLocal<LogAppender>() {
        @Override
        protected LogAppender initialValue() {
            String fileName = FileUtils.getBuildDir() + File.separator + Thread.currentThread().getName() + ".log";
            return new FileLogAppender(new File(fileName));
        }
    };

    protected Map<String, Object> getMagicVariables() {
        Map<String, Object> map = new HashMap();
        if (scenario.isOutline()) { // init examples row magic variables
            Map<String, Object> exampleData = scenario.getExampleData();
            exampleData.forEach((k, v) -> map.put(k, v));
            map.put("__row", exampleData);
            map.put("__num", scenario.getExampleIndex());
            // TODO breaking change configure outlineVariablesAuto deprecated          
        }
        if (!parentCall.isNone()) {
            Variable arg = parentCall.getArg();
            map.put("__arg", arg);
            map.put("__loop", parentCall.getLoopIndex());
            if (arg != null && arg.isMap()) {
                map.putAll(arg.getValue());
            }
        }
        return map;
    }

    public void beforeRun() {
        if (appender == null) { // not perf, not debug
            appender = APPENDER.get();
        }
        logger.setAppender(appender);
        if (scenario.isDynamic()) {
            steps = scenario.getBackgroundSteps();
        } else {
            steps = background == null ? scenario.getStepsIncludingBackground() : scenario.getSteps();
            engine.magicVariables = getMagicVariables();
        }
        ScenarioEngine.LOCAL.set(engine);
        engine.init();
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
            if (aborted && engine.getConfig().isAbortedStepsShouldPass()) {
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
                if (evalJsError != null) {
                    logError(evalJsError);
                } else {
                    logError(error.getMessage());
                }
            }
            ScenarioEngine.LOCAL.set(engine); // restore, since a call may have switched this to a nested scenario
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
            ScenarioEngine.LOCAL.remove();
            // next
        }
    }

    @Override
    public String toString() {
        return scenario.toString();
    }

}

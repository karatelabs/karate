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
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
    public final ScenarioCall caller;
    public final Scenario scenario;
    public final Tags tags;
    public final ScenarioActions actions;
    public final ScenarioResult result;
    public final ScenarioEngine engine;
    public final boolean reportDisabled;
    public final Map<String, Object> magicVariables;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        this(featureRuntime, scenario, null);
    }

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario, ScenarioRuntime background) {
        this.featureRuntime = featureRuntime;
        this.caller = featureRuntime.caller;
        if (caller.isNone()) {
            engine = new ScenarioEngine(new Config(), this, new HashMap(), logger);
        } else if (caller.isSharedScope()) {
            Config config = caller.parentRuntime.engine.getConfig();
            Map<String, Variable> vars = caller.parentRuntime.engine.vars;
            engine = new ScenarioEngine(config, this, vars, logger);            
        } else { // new, but clone and copy data
            Config config = new Config(caller.parentRuntime.engine.getConfig());
            Map<String, Variable> vars = caller.parentRuntime.engine.copyVariables(false);
            engine = new ScenarioEngine(config, this, vars, logger);
        }
        actions = new ScenarioActions(engine);
        this.scenario = scenario;
        magicVariables = initMagicVariables(); // depends on scenario
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
        tags = Tags.merge(featureRuntime.feature.getTags(), scenario.getTags());
        reportDisabled = tags.valuesFor("report").isAnyOf("false");
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

    public LogAppender getAppender() {
        return appender;
    }

    public void embed(byte[] bytes, String contentType) {
        Embed embed = new Embed();
        embed.setBytes(bytes);
        embed.setMimeType(contentType);
        embed(embed);
    }

    public void embed(Embed embed) {
        if (embeds == null) {
            embeds = new ArrayList();
        }
        embeds.add(embed);
    }

    public void addCallResult(FeatureResult fr) {
        if (callResults == null) {
            callResults = new ArrayList();
        }
        callResults.add(fr);
    }

    private List<Step> steps;
    private LogAppender appender;
    private List<Embed> embeds;
    private List<FeatureResult> callResults;
    private StepResult currentStepResult;
    private Step currentStep;
    private Throwable error;
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

    public Result evalAsStep(String expression) {
        Step evalStep = new Step(scenario.getFeature(), scenario, scenario.getIndex() + 1);
        try {
            FeatureParser.updateStepFromText(evalStep, expression);
        } catch (Exception e) {
            return Result.failed(0, e, evalStep);
        }
        return StepRuntime.execute(evalStep, actions);
    }

    public boolean hotReload() {
        boolean success = false;
        Feature feature = scenario.getFeature();
        feature = FeatureParser.parse(feature.getResource());
        for (Step oldStep : steps) {
            Step newStep = feature.findStepByLine(oldStep.getLine());
            if (newStep == null) {
                continue;
            }
            String oldText = oldStep.getText();
            String newText = newStep.getText();
            if (!oldText.equals(newText)) {
                try {
                    FeatureParser.updateStepFromText(oldStep, newStep.getText());
                    logger.info("hot reloaded line: {} - {}", newStep.getLine(), newStep.getText());
                    success = true;
                } catch (Exception e) {
                    logger.warn("failed to hot reload step: {}", e.getMessage());
                }
            }
        }
        return success;
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
                execute(currentStep);
                if (currentStepResult != null) { // can be null if debug step-back or hook skip
                    result.addStepResult(currentStepResult);
                }
            }
        } catch (Exception e) {
            logError("scenario [run] failed\n" + e.getMessage());
            currentStepResult = result.addFakeStepResult("scenario [run] failed", e);
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

    private Map<String, Object> initMagicVariables() {
        Map<String, Object> map = new HashMap();
        if (caller.isNone()) { // if feature called via java api
            if (caller.arg != null && caller.arg.isMap()) {
                map.putAll(caller.arg.getValue());
            }
        } else {
            map.put("__arg", caller.arg);
            map.put("__loop", caller.getLoopIndex());
            if (caller.arg != null && caller.arg.isMap()) {
                map.putAll(caller.arg.getValue());
            }
        }
        if (scenario.isOutline() && !scenario.isDynamic()) { // init examples row magic variables
            Map<String, Object> exampleData = scenario.getExampleData();
            exampleData.forEach((k, v) -> map.put(k, v));
            map.put("__row", exampleData);
            map.put("__num", scenario.getExampleIndex());
            // TODO breaking change configure outlineVariablesAuto deprecated          
        }
        return map;
    }

    public void beforeRun() {
        String env = featureRuntime.suite.resolveEnv(); // this lazy-inits (one time) the suite env
        if (appender == null) { // not perf, not debug
            appender = APPENDER.get();
        }
        logger.setAppender(appender);
        if (scenario.isDynamic()) {
            steps = scenario.getBackgroundSteps();
        } else {
            steps = background == null ? scenario.getStepsIncludingBackground() : scenario.getSteps();
        }
        ScenarioEngine.set(engine);
        engine.init();
        result.setThreadName(Thread.currentThread().getName());
        result.setStartTime(System.currentTimeMillis() - featureRuntime.suite.results.getStartTime());
        if (caller.isNone() && !caller.isKarateConfigDisabled()) {
            // evaluate config js, variables above will apply !
            evalConfigJs(featureRuntime.suite.karateBase, "karate-base.js");
            evalConfigJs(featureRuntime.suite.karateConfig, "karate-config.js");
            evalConfigJs(featureRuntime.suite.karateConfigEnv, "karate-config-" + env + ".js");
        }
        featureRuntime.suite.resolveHooks().forEach(h -> h.beforeScenario(this));
    }

    private void evalConfigJs(String js, String name) {
        if (js == null) {
            return;
        }
        Variable fun = engine.evalKarateExpression(js);
        if (!fun.isJsFunction()) {
            logger.warn("not a valid js function: {}", name);
            return;
        }
        try {
            Map<String, Object> map = engine.getOrEvalAsMap(fun);
            engine.setVariables(map);
        } catch (Exception e) {
            logger.error("{} failed: {}", name, e.getMessage());
            KarateException ke = ScenarioEngine.fromJsEvalException(js, e);
            throw ke;
        }
    }

    // extracted for debug
    public StepResult execute(Step step) {
        boolean shouldExecute = true;
        for (RuntimeHook hook : featureRuntime.suite.hooks) {
            if (!hook.beforeStep(step, this)) {
                shouldExecute = false;
            }
        }
        if (!shouldExecute) {
            return null;
        }
        boolean hidden = reportDisabled || (step.isPrefixStar() && !step.isPrint() && !engine.getConfig().isShowAllSteps());
        if (stopped) {
            Result stepResult;
            if (aborted && engine.getConfig().isAbortedStepsShouldPass()) {
                stepResult = Result.passed(0);
            } else {
                stepResult = Result.skipped();
            }
            currentStepResult = new StepResult(step, stepResult, null, null, null);
            currentStepResult.setHidden(hidden);
            featureRuntime.suite.hooks.forEach(h -> h.afterStep(currentStepResult, this));
            return currentStepResult;
        } else {
            boolean showLog = !reportDisabled && engine.getConfig().isShowLog();
            Result stepResult = StepRuntime.execute(step, actions);
            if (stepResult.isAborted()) { // we log only aborts for visibility
                aborted = true;
                stopped = true;
                logger.debug("abort at {}", step.getDebugInfo());
            } else if (stepResult.isFailed()) {
                stopped = true;
                error = stepResult.getError();
                logError(error.getMessage());
            }
            String stepLog = StringUtils.trimToNull(appender.collect()); // make sure we collect after error logging
            currentStepResult = new StepResult(step, stepResult, stepLog, embeds, callResults);
            callResults = null;
            embeds = null;
            currentStepResult.setHidden(hidden);
            currentStepResult.setShowLog(showLog);
            featureRuntime.suite.hooks.forEach(h -> h.afterStep(currentStepResult, this));
            return currentStepResult;
        }
    }

    public void afterRun() {
        try {
            result.setEndTime(System.currentTimeMillis() - featureRuntime.suite.results.getStartTime());
            engine.logLastPerfEvent(result.getFailureMessageForDisplay());
            engine.invokeAfterHookIfConfigured(false);
            featureRuntime.suite.hooks.forEach(h -> h.afterScenario(this));
            if (currentStepResult == null) {
                currentStepResult = result.addFakeStepResult("no steps executed", null);
            }
            engine.stop(currentStepResult);
            if (embeds != null) {
                embeds.forEach(embed -> currentStepResult.addEmbed(embed));
                embeds = null;
            }
            if (callResults != null) {
                currentStepResult.addCallResults(callResults);
                callResults = null;
            }
            String stepLog = StringUtils.trimToNull(appender.collect());
            currentStepResult.appendToStepLog(stepLog);
        } catch (Exception e) {
            logError("scenario [cleanup] failed\n" + e.getMessage());
            currentStepResult = result.addFakeStepResult("scenario [cleanup] failed", e);
        } finally {
            featureRuntime.result.addResult(result);
            // don't remove, we may need this alive for afterFeature hooks
            // ScenarioEngine.remove();
        }
    }

    public boolean isSelectedForExecution() {
        Feature feature = scenario.getFeature();
        int callLine = feature.getCallLine();
        if (callLine != -1) {
            int sectionLine = scenario.getSection().getLine();
            int scenarioLine = scenario.getLine();
            if (callLine == sectionLine || callLine == scenarioLine) {
                logger.info("found scenario at line: {}", callLine);
                return true;
            }
            logger.trace("skipping scenario at line: {}, needed: {}", scenario.getLine(), callLine);
            return false;
        }
        String callName = feature.getCallName();
        if (callName != null) {
            if (scenario.getName().matches(callName)) {
                logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
                return true;
            }
            logger.trace("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
            return false;
        }
        String callTag = feature.getCallTag();
        if (callTag != null) {
            if (tags.contains(callTag)) {
                logger.info("scenario called at line: {} by tag: {}", scenario.getLine(), callTag);
                return true;
            }
            logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
            return false;
        }
        if (caller.isNone()) {
            if (tags.evaluate(featureRuntime.suite.tagSelector)) {
                logger.trace("matched scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                return true;
            }
            logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
            return false;
        } else {
            return true; // when called, all scenarios match by default
        }
    }

    @Override
    public String toString() {
        return scenario.toString();
    }

}

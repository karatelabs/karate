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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.ScenarioActions;
import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.shell.StringLogAppender;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pthomas3
 */
public class ScenarioRuntime implements Runnable {

    public final Logger logger;
    public final FeatureRuntime featureRuntime;
    public final ScenarioCall caller;
    public final Scenario scenario;
    public final Tags tags;
    public final ScenarioActions actions;
    public final ScenarioResult result;
    public final ScenarioEngine engine;
    public final boolean reportDisabled;
    public final Map<String, Object> magicVariables;
    public final boolean selectedForExecution;
    public final boolean perfMode;
    public final boolean dryRun;
    public final LogAppender logAppender;

    private boolean skipBackground;
    private boolean ignoringFailureSteps;

    public ScenarioRuntime(FeatureRuntime featureRuntime, Scenario scenario) {
        logger = new Logger();
        this.featureRuntime = featureRuntime;
        this.caller = featureRuntime.caller;
        perfMode = featureRuntime.perfHook != null;
        if (caller.isNone()) {
            logAppender = new StringLogAppender(false);
            engine = new ScenarioEngine(caller.getParentConfig(false), this, new HashMap(), logger);
        } else if (caller.isSharedScope()) {
            logAppender = caller.parentRuntime.logAppender;
            engine = new ScenarioEngine(caller.getParentConfig(false), this, caller.getParentVars(false), logger);
        } else { // new, but clone and copy data
            logAppender = caller.parentRuntime.logAppender;
            // in this case, parent variables are set via magic variables - see initMagicVariables()
            engine = new ScenarioEngine(caller.getParentConfig(true), this, new HashMap(), logger);
        }
        logger.setAppender(logAppender);
        actions = new ScenarioActions(engine);
        this.scenario = scenario;
        if (scenario.isDynamic() && !scenario.isOutlineExample()) { // from dynamic scenario iterator
            steps = Collections.emptyList();
            skipped = true; // ensures run() is a no-op
            magicVariables = Collections.emptyMap();
        } else {
            magicVariables = initMagicVariables();
        }
        result = new ScenarioResult(scenario);
        dryRun = featureRuntime.suite.dryRun;
        tags = scenario.getTagsEffective();
        reportDisabled = perfMode ? true : tags.valuesFor("report").isAnyOf("false");
        selectedForExecution = isSelectedForExecution(featureRuntime, scenario, tags);
    }

    private Map<String, Object> initMagicVariables() {
        // magic variables are only in the JS engine - [ see ScenarioEngine.init() ]
        // and not "visible" and tracked in ScenarioEngine.vars
        // one consequence is that they won't show up in the debug variables view
        // but more importantly don't get passed back to caller and float around, bloating memory        
        Map<String, Object> map = new HashMap();
        if (!caller.isNone()) {
            // karate principle: parent variables are always "visible"
            // so we inject the parent variables
            // but they will be over-written by what is local to this scenario
            if (!caller.isSharedScope()) {
                // shallow clone variables if not shared scope
                Map<String, Variable> copy = caller.getParentVars(true);
                copy.forEach((k, v) -> map.put(k, v.getValue()));
            }
            map.putAll(caller.parentRuntime.magicVariables);
            map.put("__arg", caller.arg == null ? null : caller.arg.getValue());
            map.put("__loop", caller.getLoopIndex());
        }
        if (scenario.isOutlineExample()) { // init examples row magic variables            
            Map<String, Object> exampleData = scenario.getExampleData();
            map.putAll(exampleData);
            map.put("__row", exampleData);
            map.put("__num", scenario.getExampleIndex());
        }
        return map;
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

    public boolean isSkipBackground() {
        return this.skipBackground;
    }

    public void setSkipBackground(boolean skipBackground) {
        this.skipBackground = skipBackground;
    }

    public String getEmbedFileName(ResourceType resourceType) {
        String extension = resourceType == null ? null : resourceType.getExtension();
        return scenario.getUniqueId() + "_" + System.currentTimeMillis() + (extension == null ? "" : "." + extension);
    }

    public Embed saveToFileAndCreateEmbed(byte[] bytes, ResourceType resourceType) {
        File file = new File(featureRuntime.suite.reportDir + File.separator + getEmbedFileName(resourceType));
        FileUtils.writeToFile(file, bytes);
        return new Embed(file, resourceType);
    }

    public Embed embed(byte[] bytes, ResourceType resourceType) {
        if (embeds == null) {
            embeds = new ArrayList();
        }
        Embed embed = saveToFileAndCreateEmbed(bytes, resourceType);
        embeds.add(embed);
        return embed;
    }

    public Embed embedVideo(File file) {
        StepResult stepResult = result.addFakeStepResult("[video]", null);
        Embed embed = saveToFileAndCreateEmbed(FileUtils.toBytes(file), ResourceType.MP4);
        stepResult.addEmbed(embed);
        return embed;
    }

    private List<FeatureResult> callResults;

    public void addCallResult(FeatureResult fr) {
        if (callResults == null) {
            callResults = new ArrayList();
        }
        callResults.add(fr);
    }

    public LogAppender getLogAppender() {
        return logAppender;
    }

    private List<Step> steps;
    private List<Embed> embeds;
    private StepResult currentStepResult;
    private Step currentStep;
    private Throwable error;
    private boolean configFailed;
    private boolean skipped; // beforeScenario hook only
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
        Step evalStep = new Step(scenario, -1);
        try {
            evalStep.parseAndUpdateFrom(expression);
        } catch (Exception e) {
            return Result.failed(0, e, evalStep);
        }
        return StepRuntime.execute(evalStep, actions);
    }

    public boolean hotReload() {
        boolean success = false;
        Feature feature = scenario.getFeature();
        feature = Feature.read(feature.getResource());
        for (Step oldStep : steps) {
            Step newStep = feature.findStepByLine(oldStep.getLine());
            if (newStep == null) {
                continue;
            }
            String oldText = oldStep.getText();
            String newText = newStep.getText();
            if (!oldText.equals(newText)) {
                try {
                    oldStep.parseAndUpdateFrom(newStep.getText());
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
        Map<String, Object> info = new HashMap(5);
        File featureFile = featureRuntime.featureCall.feature.getResource().getFile();
        if (featureFile != null) {
            info.put("featureDir", featureFile.getParent());
            info.put("featureFileName", featureFile.getName());
        }
        info.put("scenarioName", scenario.getName());
        info.put("scenarioDescription", scenario.getDescription());
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

    private void evalConfigJs(String js, String displayName) {
        if (js == null || configFailed) {
            return;
        }
        try {
            Variable fun = engine.evalJs("(" + js + ")");
            if (!fun.isJsFunction()) {
                logger.warn("not a valid js function: {}", displayName);
                return;
            }
            Map<String, Object> map = engine.getOrEvalAsMap(fun);
            engine.setVariables(map);
        } catch (Exception e) {
            String message = ">> " + scenario.getDebugInfo() + "\n>> " + displayName + " failed\n>> " + e.getMessage();
            error = JsEngine.fromJsEvalException(js, e, message);
            stopped = true;
            configFailed = true;
        }
    }

    private static boolean isSelectedForExecution(FeatureRuntime fr, Scenario scenario, Tags tags) {
        org.slf4j.Logger logger = FeatureRuntime.logger;
        Feature feature = scenario.getFeature();
        int callLine = fr.featureCall.callLine;
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
        String callName = fr.featureCall.callName;
        if (callName != null) {
            if (scenario.getName().matches(callName)) {
                logger.info("found scenario at line: {} - {}", scenario.getLine(), callName);
                return true;
            }
            logger.trace("skipping scenario at line: {} - {}, needed: {}", scenario.getLine(), scenario.getName(), callName);
            return false;
        }
        String callTag = fr.featureCall.callTag;
        if (callTag != null && (!fr.caller.isNone() || fr.perfHook != null)) {
            // only if this is a legit "call" or a gatling "call by tag"
            if (tags.contains(callTag)) {
                logger.info("{} - call by tag at line {}: {}", fr, scenario.getLine(), callTag);
                return true;
            }
            logger.trace("skipping scenario at line: {} with call by tag effective: {}", scenario.getLine(), callTag);
            return false;
        }
        if (fr.caller.isNone()) {
            if (tags.evaluate(fr.suite.tagSelector, fr.suite.env)) {
                logger.trace("matched scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
                return true;
            }
            logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tags.getTags());
            return false;
        } else {
            return true; // when called, tags are ignored, all scenarios will be run
        }
    }

    //==========================================================================
    //
    public void beforeRun() {
        if (featureRuntime.caller.isNone() && featureRuntime.suite.isAborted()) {
            skipped = true;
            return;
        }
        steps = skipBackground ? scenario.getSteps() : scenario.getStepsIncludingBackground();
        ScenarioEngine.set(engine);
        engine.init();
        result.setExecutorName(Thread.currentThread().getName());
        result.setStartTime(System.currentTimeMillis());
        if (!dryRun) {
            if (caller.isNone() && !caller.isKarateConfigDisabled()) {
                // evaluate config js, variables above will apply !
                evalConfigJs(featureRuntime.suite.karateBase, "karate-base.js");
                evalConfigJs(featureRuntime.suite.karateConfig, "karate-config.js");
                evalConfigJs(featureRuntime.suite.karateConfigEnv, "karate-config-" + featureRuntime.suite.env + ".js");
            }
            skipped = !featureRuntime.suite.hooks.stream()
                    .map(h -> h.beforeScenario(this))
                    .reduce(Boolean.TRUE, Boolean::logicalAnd);
            if (skipped) {
                logger.debug("beforeScenario hook returned false, will skip scenario: {}", scenario);
            } else {
                evaluateScenarioName();
            }
        }
    }

    @Override
    public void run() {
        try { // make sure we call afterRun() even on crashes
            // and operate countdown latches, else we may hang the parallel runner
            if (steps == null) {
                beforeRun();
            }
            if (skipped) {
                return;
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
            if (currentStepResult != null) {
                result.addStepResult(currentStepResult);
            }
            logError("scenario [run] failed\n" + StringUtils.throwableToString(e));
            currentStepResult = result.addFakeStepResult("scenario [run] failed", e);
        } finally {
            if (!skipped) {
                afterRun();
                if (isFailed() && engine.getConfig().isAbortSuiteOnFailure()) {
                    featureRuntime.suite.abort();
                }
            }
            if (caller.isNone()) {
                logAppender.close(); // reclaim memory
            }
        }
    }

    public StepResult execute(Step step) {
        if (!stopped && !dryRun) {
            boolean shouldExecute = true;
            for (RuntimeHook hook : featureRuntime.suite.hooks) {
                if (!hook.beforeStep(step, this)) {
                    shouldExecute = false;
                }
            }
            if (!shouldExecute) {
                return null;
            }
        }
        Result stepResult;
        final boolean executed = !stopped;
        if (stopped) {
            if (aborted && engine.getConfig().isAbortedStepsShouldPass()) {
                stepResult = Result.passed(0);
            } else if (configFailed) {
                stepResult = Result.failed(0, error, step);
            } else {
                stepResult = Result.skipped();
            }
        } else if (dryRun) {
            stepResult = Result.passed(0);
        } else {
            stepResult = StepRuntime.execute(step, actions);
        }
        currentStepResult = new StepResult(step, stepResult);
        if (stepResult.isAborted()) { // we log only aborts for visibility
            aborted = true;
            stopped = true;
            logger.debug("abort at {}", step.getDebugInfo());
        } else if (stepResult.isFailed()) {
            if (stepResult.getMatchingMethod() != null && engine.getConfig().getContinueOnStepFailureMethods().contains(stepResult.getMatchingMethod().method)) {
                stopped = false;
                ignoringFailureSteps = true;
                currentStepResult.setErrorIgnored(true);
            } else {
                stopped = true;
            }
            if (stopped && (!this.engine.getConfig().isContinueAfterContinueOnStepFailure() || !engine.isIgnoringStepErrors())) {
                error = stepResult.getError();
                logError(error.getMessage());
            }
        } else {
            boolean hidden = reportDisabled || (step.isPrefixStar() && !step.isPrint() && !engine.getConfig().isShowAllSteps());
            currentStepResult.setHidden(hidden);
        }
        addStepLogEmbedsAndCallResults();
        if (currentStepResult.isErrorIgnored()) {
            engine.setFailedReason(null);
        }
        if (!engine.isIgnoringStepErrors() && ignoringFailureSteps) {
            if (engine.getConfig().isContinueAfterContinueOnStepFailure()) {
                // continue execution and reset failed reason for engine to null
                engine.setFailedReason(null);
                ignoringFailureSteps = false;
            } else {
                // stop execution
                // keep failed reason for scenario as the last failed step that was ignored
                stopped = true;
            }
        }
        if (stepResult.isFailed()) {
            if (engine.driver != null) {
                engine.driver.onFailure(currentStepResult);
            }
            if (engine.robot != null) {
                engine.robot.onFailure(currentStepResult);
            }
        }
        if (executed && !dryRun) {
            featureRuntime.suite.hooks.forEach(h -> h.afterStep(currentStepResult, this));
        }
        return currentStepResult;
    }

    public void afterRun() {
        try {
            result.setEndTime(System.currentTimeMillis());
            engine.logLastPerfEvent(result.getFailureMessageForDisplay());
            if (currentStepResult == null) {
                currentStepResult = result.addFakeStepResult("no steps executed", null);
            }
            if (!dryRun) {
                engine.invokeAfterHookIfConfigured(false);
                featureRuntime.suite.hooks.forEach(h -> h.afterScenario(this));
                engine.stop(currentStepResult);
            }
            addStepLogEmbedsAndCallResults();
        } catch (Exception e) {
            error = e;
            logError("scenario [cleanup] failed\n" + e.getMessage());
            currentStepResult = result.addFakeStepResult("scenario [cleanup] failed", e);
        }
    }

    private void addStepLogEmbedsAndCallResults() {
        boolean showLog = !reportDisabled && engine.getConfig().isShowLog();
        String stepLog = logAppender.collect();
        if (showLog) {
            currentStepResult.appendToStepLog(stepLog);
            if (currentStepResult.isErrorIgnored()) {
                currentStepResult.appendToStepLog(currentStepResult.getErrorMessage());
            }
        }
        if (callResults != null) {
            currentStepResult.addCallResults(callResults);
            callResults = null;
        }
        if (embeds != null) {
            currentStepResult.addEmbeds(embeds);
            embeds = null;
        }
    }

    @Override
    public String toString() {
        return scenario.toString();
    }

    public void evaluateScenarioName() {
        String scenarioName = scenario.getName();
        boolean wrappedByBackTick = scenarioName != null
                && scenarioName.length() > 1
                && '`' == scenarioName.charAt(0)
                && '`' == scenarioName.charAt((scenarioName.length() - 1));
        boolean hasJavascriptPlaceholder = ScenarioEngine.hasJavaScriptPlacehoder(scenarioName);
        if (wrappedByBackTick || hasJavascriptPlaceholder) {
            String eval = scenarioName;
            if (!wrappedByBackTick) {
                eval = '`' + eval + '`';
            }
            String evaluatedScenarioName = engine.evalJs(eval).getAsString();
            scenario.setName(evaluatedScenarioName);
        }
    }

}

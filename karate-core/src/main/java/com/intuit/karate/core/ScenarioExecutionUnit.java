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

import com.intuit.karate.FileUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StepActions;
import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements Runnable {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ScenarioExecutionUnit.class);

    public final Scenario scenario;
    private final ExecutionContext exec;
    public final ScenarioResult result;
    private final boolean reportDisabled;
    private boolean executed = false;

    private Collection<ExecutionHook> hooks;
    private List<Step> steps;
    private StepActions actions;
    private boolean stopped = false;
    private boolean aborted = false;
    private StepResult lastStepResult;
    private Runnable next;
    private boolean last;
    private Step currentStep;

    private LogAppender appender;
    private Throwable error;

    public Throwable getError() {
        return error;
    }

    // for debug
    public Step getCurrentStep() {
        return currentStep;
    }

    private static final ThreadLocal<LogAppender> APPENDER = new ThreadLocal<LogAppender>() {
        @Override
        protected LogAppender initialValue() {
            String fileName = FileUtils.getBuildDir() + File.separator + Thread.currentThread().getName() + ".log";
            return new FileLogAppender(new File(fileName));
        }
    };

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, ExecutionContext exec) {
        this(scenario, results, exec, null);
    }

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results,
            ExecutionContext exec, ScenarioContext backgroundContext) {
        this.scenario = scenario;
        this.exec = exec;
        result = new ScenarioResult(scenario, results);
        if (backgroundContext != null) { // re-build for dynamic scenario
            ScenarioContext context = backgroundContext.copy();
            actions = new StepActions(context);
        }
        if (exec.callContext.perfMode) {
            appender = LogAppender.NO_OP;
        }
        Tags tags = scenario.getTagsEffective();
        reportDisabled = tags.valuesFor("report").isAnyOf("false");
    }

    public ScenarioContext getContext() {
        // null if dynamic scenario outline failed, see logic in feature
        return actions == null ? null : actions.context;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public StepActions getActions() {
        return actions;
    }

    public void setActions(StepActions actions) {
        this.actions = actions;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setNext(Runnable next) {
        this.next = next;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isLast() {
        return last;
    }

    public void init() {
        boolean initFailed = false;
        if (actions == null) {
            // karate-config.js will be processed here 
            // when the script-context constructor is called
            try {
                actions = new StepActions(exec.featureContext, exec.callContext, exec.classLoader, scenario, appender);
            } catch (Exception e) {
                initFailed = true;
                result.addError("scenario init failed", e);
            }
        } else { // dynamic scenario outline, hack to swap logger for current thread
            Logger logger = new Logger();
            logger.setAppender(appender);
            actions.context.setLogger(logger);
        }
        if (!initFailed) { // actions will be null otherwise
            // this flag is used to suppress logs in the http client if needed
            actions.context.setReportDisabled(reportDisabled);
            // this is not done in the constructor as we need to be on the "executor" thread
            hooks = exec.callContext.resolveHooks();
            // before-scenario hook, important: actions.context will be null if initFailed
            if (hooks != null) {
                try {
                    hooks.forEach(h -> h.beforeScenario(scenario, actions.context));
                } catch (Exception e) {
                    initFailed = true;
                    result.addError("beforeScenario hook failed", e);
                }
            }
        }
        if (initFailed) {
            steps = Collections.EMPTY_LIST;
        } else {
            if (scenario.isDynamic()) {
                steps = scenario.getBackgroundSteps();
            } else {
                if (scenario.isBackgroundDone()) {
                    steps = scenario.getSteps();
                } else {
                    steps = scenario.getStepsIncludingBackground();
                }
                if (scenario.isOutline()) { // init examples row magic variables
                    Map<String, Object> exampleData = scenario.getExampleData();
                    actions.context.vars.put("__row", exampleData);
                    actions.context.vars.put("__num", scenario.getExampleIndex());
                    if (actions.context.getConfig().isOutlineVariablesAuto()) {
                        exampleData.forEach((k, v) -> actions.context.vars.put(k, v));
                    }
                }
            }
        }
        result.setThreadName(Thread.currentThread().getName());
        result.setStartTime(System.currentTimeMillis() - exec.startTime);
    }

    // for karate ui
    public void reset(ScenarioContext context) {
        setExecuted(false);
        result.reset();
        actions = new StepActions(context);
    }

    private StepResult afterStep(StepResult result) {
        if (hooks != null) {
            hooks.forEach(h -> h.afterStep(result, actions.context));
        }
        return result;
    }

    // extracted for debug
    public StepResult execute(Step step) {
        currentStep = step;
        actions.context.setExecutionUnit(this);// just for deriving call stack        
        if (hooks != null) {
            boolean shouldExecute = true;
            for (ExecutionHook hook : hooks) {
                if (!hook.beforeStep(step, actions.context)) {
                    shouldExecute = false;
                }
            }
            if (!shouldExecute) {
                return null;
            }
        }
        boolean hidden = reportDisabled || (step.isPrefixStar() && !step.isPrint() && !actions.context.getConfig().isShowAllSteps());
        if (stopped) {
            Result stepResult;
            if (aborted && actions.context.getConfig().isAbortedStepsShouldPass()) {
                stepResult = Result.passed(0);
            } else {
                stepResult = Result.skipped();
            }
            StepResult sr = new StepResult(step, stepResult, null, null, null);
            sr.setHidden(hidden);
            return afterStep(sr);
        } else {
            Engine.THREAD_CONTEXT.set(actions.context);
            Result execResult = Engine.executeStep(step, actions);
            Engine.THREAD_CONTEXT.set(null);
            List<FeatureResult> callResults = actions.context.getAndClearCallResults();
            // embed collection for each step happens here
            List<Embed> embeds = actions.context.getAndClearEmbeds();
            if (execResult.isAborted()) { // we log only aborts for visibility
                aborted = true;
                actions.context.logger.debug("abort at {}", step.getDebugInfo());
            } else if (execResult.isFailed()) {
                error = execResult.getError();
            }
            // log appender collection for each step happens here
            String stepLog = StringUtils.trimToNull(appender.collect());
            boolean showLog = !reportDisabled && actions.context.getConfig().isShowLog();
            StepResult sr = new StepResult(step, execResult, stepLog, embeds, callResults);
            sr.setHidden(hidden);
            sr.setShowLog(showLog);
            return afterStep(sr);
        }
    }

    public void stop() {
        result.setEndTime(System.currentTimeMillis() - exec.startTime);
        if (actions != null) { // edge case if karate-config.js itself failed
            // gatling clean up
            actions.context.logLastPerfEvent(result.getFailureMessageForDisplay());
            // after-scenario hook
            actions.context.invokeAfterHookIfConfigured(false);
            if (hooks != null) {
                hooks.forEach(h -> h.afterScenario(result, actions.context));
            }
            // embed collection for afterScenario
            List<Embed> embeds = actions.context.getAndClearEmbeds();
            if (embeds != null) {
                embeds.forEach(embed -> lastStepResult.addEmbed(embed));
            }
            // stop browser automation if running
            actions.context.stop(lastStepResult);
        }
        if (lastStepResult != null) {
            String stepLog = StringUtils.trimToNull(appender.collect());
            lastStepResult.appendToStepLog(stepLog);
        }
    }

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
        if (stepIndex < 0) { // maybe not required, but debug is hard
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
            LOGGER.error("scenario execution failed: {}", e.getMessage());
        } finally {
            if (next != null) {
                next.run();
            }
        }
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

}

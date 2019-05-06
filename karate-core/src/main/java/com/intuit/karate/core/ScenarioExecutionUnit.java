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

import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.StepActions;
import com.intuit.karate.StringUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements Runnable {

    public final Scenario scenario;
    private final ExecutionContext exec;
    public final ScenarioResult result;
    private final LogAppender appender;
    public final Logger logger;
    private boolean executed = false;

    private List<Step> steps;
    private StepActions actions;
    private boolean stopped = false;
    private StepResult lastStepResult;
    private Runnable next;
    private boolean last;

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, ExecutionContext exec, Logger logger) {
        this(scenario, results, exec, null, logger);
    }
    
    private static final Map<String, Integer> FILE_HANDLE_COUNT = new HashMap();    

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results,
            ExecutionContext exec, ScenarioContext backgroundContext, Logger logger) {
        this.scenario = scenario;
        this.exec = exec;
        result = new ScenarioResult(scenario, results);
        if (logger == null) {
            logger = new Logger();
            if (scenario.getIndex() < 500) {
                if (exec.callContext.isCalled()) {                    
                    String featureName = exec.featureContext.packageQualifiedName;                    
                    Integer count = FILE_HANDLE_COUNT.get(featureName);
                    if (count == null) {
                        count = 0;                        
                    }
                    count = count + 1;                    
                    FILE_HANDLE_COUNT.put(featureName, count);                    
                    if (count < 500) {
                        // ensure no collisions for called features that are re-used across scenarios executing in parallel
                        appender = exec.getLogAppender(scenario.getUniqueId() + "_" + Thread.currentThread().getName(), logger);
                    } else { // this is a super-re-used feature, don't open any more files, same trade-off see below                        
                        appender = LogAppender.NO_OP;
                    }                    
                } else {
                    appender = exec.getLogAppender(scenario.getUniqueId(), logger);
                }                
            } else {
                // avoid creating log-files for scenario outlines beyond a limit
                // trade-off is we won't see inline logs in the html report                 
                appender = LogAppender.NO_OP;
            }
        } else {
            appender = LogAppender.NO_OP;
        }
        this.logger = logger;
        if (backgroundContext != null) { // re-build for dynamic scenario
            ScenarioInfo info = scenario.toInfo(exec.featureContext.feature.getPath());
            ScenarioContext context = backgroundContext.copy(info, logger);
            actions = new StepActions(context);
        }
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
                actions = new StepActions(exec.featureContext, exec.callContext, scenario, logger);
            } catch (Exception e) {
                initFailed = true;
                result.addError("scenario init failed", e);
            }
        }
        // before-scenario hook        
        if (!initFailed && actions.context.executionHooks != null) {
            try {
                for (ExecutionHook h : actions.context.executionHooks) {
                    h.beforeScenario(scenario, actions.context);
                }
            } catch (Exception e) {
                initFailed = true;
                result.addError("beforeScenario hook failed", e);
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
                    actions.context.vars.put("__row", scenario.getExampleData());
                    actions.context.vars.put("__num", scenario.getExampleIndex());
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

    // extracted for karate UI
    public StepResult execute(Step step) {
        boolean hidden = step.isPrefixStar() && !step.isPrint() && !actions.context.getConfig().isShowAllSteps();
        if (stopped) {
            return new StepResult(hidden, step, Result.skipped(), null, null, null);
        } else {
            Result execResult = Engine.executeStep(step, actions);
            List<FeatureResult> callResults = actions.context.getAndClearCallResults();
            // embed collection for each step happens here
            Embed embed = actions.context.getAndClearEmbed();
            if (execResult.isAborted()) { // we log only aborts for visibility
                actions.context.logger.debug("abort at {}", step.getDebugInfo());
            } else if (execResult.isFailed()) {
                actions.context.setScenarioError(execResult.getError());
            }
            // log appender collection for each step happens here
            String stepLog = StringUtils.trimToNull(appender.collect());
            boolean showLog = actions.context.getConfig().isShowLog();
            return new StepResult(hidden, step, execResult, showLog ? stepLog : null, embed, callResults);
        }
    }

    public void stop() {
        result.setEndTime(System.currentTimeMillis() - exec.startTime);
        if (actions != null) { // edge case if karate-config.js itself failed
            // gatling clean up
            actions.context.logLastPerfEvent(result.getFailureMessageForDisplay());
            // after-scenario hook
            actions.context.invokeAfterHookIfConfigured(false);
            if (actions.context.executionHooks != null) {
                actions.context.executionHooks.forEach(h -> h.afterScenario(result, actions.context));
            }
            // stop browser automation if running
            actions.context.stop();
        }
        if (lastStepResult != null) {
            String stepLog = StringUtils.trimToNull(appender.collect());
            lastStepResult.appendToStepLog(stepLog);
        }
        appender.close();
    }

    @Override
    public void run() {
        if (steps == null) {
            init();
        }
        for (Step step : steps) {
            lastStepResult = execute(step);
            result.addStepResult(lastStepResult);
            if (lastStepResult.isStopped()) {
                stopped = true;
            }
        }
        stop();
        if (next != null) {
            next.run();
        }
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

}

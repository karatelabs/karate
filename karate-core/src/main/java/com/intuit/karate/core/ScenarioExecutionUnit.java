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
import java.util.Iterator;
import java.util.List;

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
    private final boolean async;
    private boolean executed = false;

    private List<Step> steps;
    private Iterator<Step> iterator;
    private StepActions actions;
    private boolean stopped = false;
    private StepResult lastStepResult;
    private Runnable next;

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, ExecutionContext exec, Logger logger) {
        this(scenario, results, exec, null, logger);
    }

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results,
            ExecutionContext exec, ScenarioContext backgroundContext, Logger logger) {
        this.scenario = scenario;
        this.exec = exec;
        this.async = exec.callContext.perfMode && exec.callContext.callDepth == 0;
        result = new ScenarioResult(scenario, results);
        if (logger == null) {
            logger = new Logger();
            if (scenario.getIndex() < 500) {
                appender = exec.getLogAppender(scenario.getUniqueId(), logger);
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
        return actions.context;
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
        if (!initFailed && actions.context.executionHook != null) {
            try {
                actions.context.executionHook.beforeScenario(scenario, actions.context);
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
            } else if (scenario.isBackgroundDone()) {
                steps = scenario.getSteps();
            } else {
                steps = scenario.getStepsIncludingBackground();
            }
        }
        iterator = steps.iterator();
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
        // gatling clean up
        if (actions != null) { // edge case if karate-config.js itself failed
            actions.context.logLastPerfEvent(result.getFailureMessageForDisplay());
            // after-scenario hook
            actions.context.invokeAfterHookIfConfigured(false);
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
        if (iterator == null) {
            init();
        }
        if (iterator.hasNext()) {
            lastStepResult = execute(iterator.next());
            result.addStepResult(lastStepResult);
            if (lastStepResult.isStopped()) {
                stopped = true;
            }
            // this is self-recursion but step counts will never cause stack overflows
            // it is important that we preserve the order of step execution so this hack is necessary
            // and it gives us the async / non-blocking behavior we need for gatling
            if (async) {
                exec.system.accept(this);
            } else {
                run();
            }
        } else {
            stop();
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

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

import com.intuit.karate.StepActions;
import com.intuit.karate.StringUtils;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements Runnable {

    public final Tags tags;
    public final Scenario scenario;
    private final ExecutionContext exec;
    public final ScenarioResult result;
    private final Consumer<Runnable> SYSTEM;

    private List<Step> steps;
    private Iterator<Step> iterator;
    private StepActions actions;
    private Runnable next;
    private boolean stopped = false;
    private StepResult lastStepResult;

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, ExecutionContext exec) {
        this(scenario, results, exec, null);
    }

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, ExecutionContext exec, ScenarioContext context) {
        this.scenario = scenario;
        this.tags = new Tags(scenario.getTagsEffective());
        this.exec = exec;
        result = new ScenarioResult(scenario, results);
        SYSTEM = exec.callContext.perfMode ? exec.system : r -> r.run();
        if (context != null) {
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

    public void setNext(Runnable next) {
        this.next = next;
    }

    public void init() {
        if (actions == null) {
            // some metadata init needed for scenarios            
            Path featurePath = exec.featureContext.feature.getPath();
            exec.callContext.setScenarioInfo(scenario.toInfo(featurePath));
            exec.callContext.setTags(tags);
            // karate-config.js will be processed here 
            // when the script-context constructor is called          
            actions = new StepActions(exec.featureContext, exec.callContext);
        }
        // before-scenario hook
        boolean hookFailed = false;
        if (actions.context.executionHook != null) {
            try {
                actions.context.executionHook.beforeScenario(scenario, actions.context);
            } catch (Exception e) {
                hookFailed = true;
                result.addError("beforeScenario hook failed", e);
            }
        }
        if (hookFailed) {
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
            }
            // log appender collection for each step happens here
            String stepLog = StringUtils.trimToNull(exec.appender.collect());
            boolean showLog = actions.context.getConfig().isShowLog();
            return new StepResult(hidden, step, execResult, showLog ? stepLog : null, embed, callResults);
        }
    }

    public void stop() {
        result.setEndTime(System.currentTimeMillis() - exec.startTime);
        // gatling clean up            
        actions.context.logLastPerfEvent(result.getFailureMessageForDisplay());
        // after-scenario hook
        actions.context.invokeAfterHookIfConfigured(false);
        // stop browser automation if running
        actions.context.stop();
        if (lastStepResult != null) {
            String stepLog = StringUtils.trimToNull(exec.appender.collect());
            lastStepResult.appendToStepLog(stepLog);
        }
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
            SYSTEM.accept(this);
        } else {
            stop();
            if (next != null) {
                next.run();
            }
        }
    }

}

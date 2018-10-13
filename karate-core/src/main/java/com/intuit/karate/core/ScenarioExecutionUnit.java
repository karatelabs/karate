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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ScenarioExecutionUnit implements Runnable {

    public final Scenario scenario;
    public final Tags tags;
    public final StepActions actions;
    private final ExecutionContext exec;
    private final Iterator<Step> iterator;
    protected final ScenarioResult result;
    private final Consumer<Runnable> SYSTEM;
    
    private Runnable init;
    private Runnable next;
    private boolean started;
    private boolean stopped = false;

    public ScenarioExecutionUnit(Scenario scenario, List<StepResult> results, Tags tags, StepActions actions, ExecutionContext exec) {
        this.scenario = scenario;
        this.tags = tags;
        this.actions = actions;
        this.exec = exec;
        result = new ScenarioResult(scenario, results);
        SYSTEM = exec.callContext.perfMode ? exec.system : r -> r.run();
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
            iterator = Collections.emptyIterator();
        } else {
            if (scenario.isDynamic()) {
                iterator = scenario.getBackgroundSteps().iterator();
            } else if (scenario.isBackgroundDone()) {
                iterator = scenario.getSteps().iterator();
            } else {
                iterator = scenario.getStepsIncludingBackground().iterator();
            }
        }
    }

    public void setInit(Runnable init) {
        this.init = init;
    }        

    public void setNext(Runnable next) {
        this.next = next;
    }        

    @Override
    public void run() {
        if (!started) {
            result.setThreadName(Thread.currentThread().getName());
            result.setStartTime(System.currentTimeMillis() - exec.startTime);
            if (init != null) {
                init.run();
            }
            started = true;
        }
        if (iterator.hasNext()) {
            Step step = iterator.next();
            if (stopped) {
                result.addStepResult(new StepResult(step, Result.skipped(), null, null));
                SYSTEM.accept(this);
            } else {
                Result execResult = Engine.executeStep(step, actions);
                List<FeatureResult> callResults = actions.context.getAndClearCallResults();
                if (execResult.isAborted()) { // we log only aborts for visibility
                    actions.context.logger.debug("abort at {}", step.getDebugInfo());
                }
                // log appender collection for each step happens here
                String stepLog = StringUtils.trimToNull(exec.appender.collect());
                StepResult stepResult = new StepResult(step, execResult, stepLog, callResults);
                if (stepResult.isStopped()) {
                    stopped = true;
                }
                result.addStepResult(stepResult);
                SYSTEM.accept(this);
            }
        } else {            
            result.setEndTime(System.currentTimeMillis() - exec.startTime);
            // gatling clean up            
            actions.context.logLastPerfEvent(result.getFailureMessageForDisplay());
            // after-scenario hook
            actions.context.invokeAfterHookIfConfigured(false);
            // stop browser automation if running
            actions.context.stop();
            if (next != null) {
                next.run();
            }
        }
    }

}

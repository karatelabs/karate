/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.debug;

import com.intuit.karate.LogAppender;
import com.intuit.karate.Results;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.HttpRequestBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DebugThread implements ExecutionHook, LogAppender {

    private static final Logger logger = LoggerFactory.getLogger(DebugThread.class);

    public final long id;
    public final String name;
    public final Thread thread;
    public final Stack<Long> stack = new Stack();    
    private final Map<Integer, Boolean> stepModes = new HashMap();
    public final DapServerHandler handler;

    private boolean stepIn;
    private boolean stepBack;
    private boolean paused;
    private boolean interrupted;
    private boolean stopped;

    private final String appenderPrefix;
    private LogAppender appender = LogAppender.NO_OP;

    public DebugThread(Thread thread, DapServerHandler handler) {
        id = thread.getId();
        name = thread.getName();
        appenderPrefix = "[" + name + "] ";
        this.thread = thread;
        this.handler = handler;
    }

    protected void pause() {
        paused = true;
    }

    private boolean stop(String reason) {
        return stop(reason, null);
    }    
    
    private boolean stop(String reason, String description) {
        handler.stopEvent(id, reason, description);
        stopped = true;
        synchronized (this) {
            try {
                wait();
            } catch (Exception e) {
                logger.warn("thread error: {}", e.getMessage());
                interrupted = true;
                return false; // exit all the things
            }
        }        
        handler.continueEvent(id);
        // if we reached here - we have "resumed"
        if (stepBack) { // don't clear flag yet !
            ScenarioContext context = getContext();
            context.getExecutionUnit().stepBack();
            return false; // abort and do not execute step !
        }
        if (stopped) {
            ScenarioContext context = getContext();
            context.getExecutionUnit().stepReset();
            return false;
        }
        return true;
    }

    protected void resume() {
        stopped = false;
        for (DebugThread dt : handler.THREADS.values()) {
            synchronized (dt) {
                dt.notify();
            }
        }
    }

    @Override
    public boolean beforeScenario(Scenario scenario, ScenarioContext context) {
        long frameId = handler.nextFrameId();
        stack.push(frameId);
        handler.FRAMES.put(frameId, context);
        if (context.callDepth == 0) {
            handler.THREADS.put(id, this);
        }
        appender = context.appender;
        context.logger.setLogAppender(this); // wrap       
        return true;
    }

    @Override
    public void afterScenario(ScenarioResult result, ScenarioContext context) {
        stack.pop();
        if (context.callDepth == 0) {
            handler.THREADS.remove(id);
        }
        context.logger.setLogAppender(appender); // unwrap        
    }

    @Override
    public boolean beforeStep(Step step, ScenarioContext context) {
        if (interrupted) {
            return false;
        }
        if (paused) {
            paused = false;
            return stop("pause");
        } else if (stepBack) {
            stepBack = false;
            return stop("step");
        } else if (stepIn) {
            stepIn = false;
            return stop("step");
        } else if (isStepMode()) {
            return stop("step");
        } else {
            int line = step.getLine();
            if (handler.isBreakpoint(step, line)) {
                return stop("breakpoint");
            } else {
                return true;
            }
        }
    }

    @Override
    public void afterStep(StepResult result, ScenarioContext context) {
        if (result.getResult().isFailed()) {
            handler.output("*** step failed: " + result.getErrorMessage() + "\n");
            stop("exception", result.getErrorMessage());
        }
    }

    protected ScenarioContext getContext() {
        return handler.FRAMES.get(stack.peek());
    }

    protected DebugThread clearStepModes() {
        stepModes.clear();
        return this;
    }

    protected DebugThread step(boolean stepMode) {
        stepModes.put(stack.size(), stepMode);
        return this;
    }

    protected boolean isStepMode() {
        Boolean stepMode = stepModes.get(stack.size());
        return stepMode == null ? false : stepMode;
    }
    
    protected DebugThread stepIn(boolean stepIn) {
        this.stepIn = stepIn;
        return this;
    }

    protected DebugThread stepBack(boolean stepBack) {
        this.stepBack = stepBack;
        return this;
    }

    public LogAppender getAppender() {
        return appender;
    }

    public void setAppender(LogAppender appender) {
        this.appender = appender;
    }

    @Override
    public String collect() {
        return appender.collect();
    }

    @Override
    public void append(String text) {
        handler.output(appenderPrefix + text);
        appender.append(text);
    }

    @Override
    public void close() {

    }

    @Override
    public boolean beforeFeature(Feature feature, ExecutionContext context) {
        return true;
    }

    @Override
    public void afterFeature(FeatureResult result, ExecutionContext context) {

    }

    @Override
    public void beforeAll(Results results) {

    }

    @Override
    public void afterAll(Results results) {

    }

    @Override
    public String getPerfEventName(HttpRequestBuilder req, ScenarioContext context) {
        return null;
    }

    @Override
    public void reportPerfEvent(PerfEvent event) {

    }

}

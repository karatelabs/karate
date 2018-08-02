/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.ui;

import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author vmchukky
 */
public class RunService extends Service<Void> {

    AppSession session;
    StepPanel runUptoStep;
    ExecutorService singleThreadExecutor;
    static final String RUN_ALL_THREAD_NAME = "Karate-UI RunAll";
    static final String RUN_UPTO_THREAD_NAME_PREFIX = "Karate-UI RunUpto Step-";

    public RunService(AppSession session) {
        this.session = session;
        this.runUptoStep = runUptoStep;
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        setExecutor(singleThreadExecutor);
    }

    // passing null for runUptoStep executes complete feature
    public void runUptoStep(StepPanel runUptoStep) {
        // feels like a hack to pass parameters to Task (there must be some-other/better way)
        this.runUptoStep = runUptoStep;
        reset();
        restart();
    }

    @Override
    protected Task<Void> createTask() {
        return new RunTask(session, runUptoStep);
    }

    static class RunTask extends Task<Void> {
        final AppSession session;
        final StepPanel runUptoStep;

        public RunTask(AppSession session, StepPanel runUptoStep) {
            this.session = session;
            this.runUptoStep = runUptoStep;
        }

        @Override
        protected Void call() throws Exception {
            try {
                if (runUptoStep != null) {
                    Thread.currentThread().setName(RUN_UPTO_THREAD_NAME_PREFIX + (runUptoStep.getStepIndex() + 1));
                    runUptoStep.runAllUpto();
                } else {
                    Thread.currentThread().setName(RUN_ALL_THREAD_NAME);
                    session.featurePanel.action(AppAction.RUN);
                }
            } catch (Exception e) {
                session.backend.getEnv().logger.error("step execution paused.");
            }
            session.markRunStopped();
            return null;
        }
    }
}
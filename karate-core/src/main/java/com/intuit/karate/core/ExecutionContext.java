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

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class ExecutionContext {      

    public final Results results;
    public final long startTime;
    public final FeatureContext featureContext;
    public final CallContext callContext;
    public final FeatureResult result;
    public final Consumer<Runnable> system;
    public final ExecutorService scenarioExecutor;
    public final ClassLoader classLoader;

    private final File reportDir;

    public ExecutionContext(Results results, long startTime, FeatureContext featureContext, CallContext callContext, String reportDirString,
                            Consumer<Runnable> system, ExecutorService scenarioExecutor) {
        this(results, startTime, featureContext, callContext, reportDirString, system, scenarioExecutor, null);
    }

    public ExecutionContext(Results results, long startTime, FeatureContext featureContext, CallContext callContext, String reportDirString,
                            Consumer<Runnable> system, ExecutorService scenarioExecutor, ClassLoader classLoader) {
        this.results = results;
        this.scenarioExecutor = scenarioExecutor;
        this.startTime = startTime;
        result = new FeatureResult(results, featureContext.feature);
        this.featureContext = featureContext;
        this.callContext = callContext;
        this.classLoader = classLoader;
        if (callContext.perfMode) {
            reportDir = null;
        } else {
            if (reportDirString == null) {
                reportDirString = FileUtils.getBuildDir() + File.separator + "surefire-reports";
            }
            reportDir = new File(reportDirString);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }
        }
        if (system == null) {
            this.system = r -> r.run();
        } else {
            this.system = system;
        }
    }

}

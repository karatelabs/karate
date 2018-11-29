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
package com.intuit.karate;

import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Tags;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Runner {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Runner.class);

    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(clazz);
        return parallel(options.getTags(), options.getFeatures(), null, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, ExecutionHook hook, int threadCount, String reportDir) {
        String tagSelector = tags == null ? null : Tags.fromCucumberOptionsTags(tags);
        List<Resource> files = FileUtils.scanForFeatureFiles(paths, Thread.currentThread().getContextClassLoader());
        return parallel(tagSelector, files, hook, threadCount, reportDir);
    }

    public static Results parallel(String tagSelector, List<Resource> resources, int threadCount, String reportDir) {
        return parallel(tagSelector, resources, null, threadCount, reportDir);
    }

    public static Results parallel(String tagSelector, List<Resource> resources, ExecutionHook hook, int threadCount, String reportDir) {
        if (threadCount < 1) {
            threadCount = 1;
        }
        if (reportDir == null) {
            reportDir = Engine.getBuildDir() + File.separator + "surefire-reports";
            new File(reportDir).mkdirs();
        }
        final String finalReportDir = reportDir;
        logger.info("Karate version: {}", FileUtils.getKarateVersion());
        Results results = Results.startTimer(threadCount);
        ExecutorService featureExecutor = Executors.newFixedThreadPool(threadCount);
        ExecutorService scenarioExecutor = Executors.newWorkStealingPool(threadCount);
        int executedFeatureCount = 0;
        try {
            int count = resources.size();
            CountDownLatch latch = new CountDownLatch(count);
            List<FeatureResult> featureResults = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                Resource resource = resources.get(i);
                int index = i + 1;
                Feature feature = FeatureParser.parse(resource);
                FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
                CallContext callContext = CallContext.forAsync(feature, hook, null, false);
                ExecutionContext execContext = new ExecutionContext(results.getStartTime(), featureContext, callContext, reportDir, 
                        r -> featureExecutor.submit(r), scenarioExecutor);
                featureResults.add(execContext.result);
                FeatureExecutionUnit unit = new FeatureExecutionUnit(execContext);
                unit.setNext(() -> {
                    FeatureResult result = execContext.result;
                    if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags                   
                        File file = Engine.saveResultJson(finalReportDir, result, null);
                        Engine.saveResultXml(finalReportDir, result, null);
                        String status = result.isFailed() ? "fail" : "pass";
                        logger.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                        result.printStats(file.getPath());
                    } else {
                        results.addToSkipCount(1);
                        logger.info("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
                    }
                    latch.countDown();                    
                });
                featureExecutor.submit(unit);
            }
            latch.await();
            results.stopTimer();
            for (FeatureResult result : featureResults) {
                int scenarioCount = result.getScenarioCount();
                results.addToScenarioCount(scenarioCount);
                if (scenarioCount != 0) {
                    executedFeatureCount++;
                }
                results.addToFailCount(result.getFailedCount());
                results.addToTimeTaken(result.getDurationMillis());
                if (result.isFailed()) {
                    results.addToFailedList(result.getPackageQualifiedName(), result.getErrorMessages());
                }
                results.addScenarioResults(result.getScenarioResults());
            }
        } catch (Exception e) {
            logger.error("karate parallel runner failed: ", e.getMessage());
            results.setFailureReason(e);
        } finally {
            featureExecutor.shutdownNow();
            scenarioExecutor.shutdownNow();
        }
        results.setFeatureCount(executedFeatureCount);
        results.printStats(threadCount);
        Engine.saveStatsJson(reportDir, results, null);
        Engine.saveTimelineHtml(reportDir, results, null);
        results.setReportDir(reportDir);
        return results;
    }

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        CallContext callContext = new CallContext(vars, evalKarateConfig);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, callContext);
        if (result.isFailed()) {
            throw result.getErrorsCombined();
        }
        return result.getResultAsPrimitiveMap();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(file);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = FileUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(path);
        return runFeature(feature, vars, evalKarateConfig);
    }

    // this is called by karate-gatling !
    public static void callAsync(String path, Map<String, Object> arg, ExecutionHook hook, Consumer<Runnable> system, Runnable next) {
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        FeatureContext featureContext = new FeatureContext(null, feature, null);
        CallContext callContext = CallContext.forAsync(feature, hook, arg, true);
        ExecutionContext executionContext = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, system, null);
        FeatureExecutionUnit exec = new FeatureExecutionUnit(executionContext);
        exec.setNext(next);
        system.accept(exec);
    }

}

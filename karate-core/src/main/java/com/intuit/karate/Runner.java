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
import com.intuit.karate.debug.DapServer;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Runner.class);

    public static class Builder {

        Class optionsClass;
        int threadCount;
        String reportDir;
        String scenarioName;
        List<String> tags = new ArrayList();
        List<String> paths = new ArrayList();
        List<Resource> resources;
        Collection<ExecutionHook> hooks;

        public Builder path(String... paths) {
            this.paths.addAll(Arrays.asList(paths));
            return this;
        }
        
        public Builder path(List<String> paths) {
            if (paths != null) {
                this.paths.addAll(paths);
            }
            return this;
        }   
        
        public Builder tags(List<String> tags) {
            if (tags != null) {
                this.tags.addAll(tags);
            }
            return this;
        }        

        public Builder tags(String... tags) {
            this.tags.addAll(Arrays.asList(tags));
            return this;
        }

        public Builder resources(Collection<Resource> resources) {
            if (resources != null) {
                this.resources.addAll(resources);
            }
            return this;
        }

        public Builder resources(Resource... resources) {
            this.resources.addAll(Arrays.asList(resources));
            return this;
        }

        public Builder forClass(Class clazz) {
            this.optionsClass = clazz;
            return this;
        }

        public Builder reportDir(String dir) {
            this.reportDir = dir;
            return this;
        }

        public Builder scenarioName(String name) {
            this.scenarioName = name;
            return this;
        }

        public Builder hook(ExecutionHook hook) {
            if (hooks == null) {
                hooks = new ArrayList();
            }
            hooks.add(hook);
            return this;
        }
        
        String tagSelector() {
            return Tags.fromKarateOptionsTags(tags);
        }

        List<Resource> resources() {
            if (resources == null) {
                return FileUtils.scanForFeatureFiles(paths, Thread.currentThread().getContextClassLoader());
            }
            return resources;
        }

        public Results parallel(int threadCount) {
            this.threadCount = threadCount;
            return Runner.parallel(this);
        }

    }

    public static Builder path(String... paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }   
    
    public static Builder path(List<String> paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }     

    //==========================================================================
    //
    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(clazz);
        return parallel(options.getTags(), options.getFeatures(), options.getName(), null, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, null, threadCount, reportDir);
    }

    public static Results parallel(int threadCount, String... tagsOrPaths) {
        return parallel(null, threadCount, tagsOrPaths);
    }

    public static Results parallel(String reportDir, int threadCount, String... tagsOrPaths) {
        List<String> tags = new ArrayList();
        List<String> paths = new ArrayList();
        for (String s : tagsOrPaths) {
            s = StringUtils.trimToEmpty(s);
            if (s.startsWith("~") || s.startsWith("@")) {
                tags.add(s);
            } else {
                paths.add(s);
            }
        }
        return parallel(tags, paths, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, String scenarioName,
            List<ExecutionHook> hooks, int threadCount, String reportDir) {
        Builder options = new Builder();
        options.tags = tags;
        options.paths = paths;
        options.scenarioName = scenarioName;
        options.hooks = hooks;
        options.reportDir = reportDir;
        return options.parallel(threadCount);
    }

    public static Results parallel(List<Resource> resources, int threadCount, String reportDir) {
        Builder options = new Builder();
        options.resources = resources;
        options.reportDir = reportDir;
        return options.parallel(threadCount);
    }

    public static Results parallel(Builder options) {
        int threadCount = options.threadCount;
        if (threadCount < 1) {
            threadCount = 1;
        }
        String reportDir = options.reportDir;
        if (reportDir == null) {
            reportDir = FileUtils.getBuildDir() + File.separator + ScriptBindings.SUREFIRE_REPORTS;
            new File(reportDir).mkdirs();
        }
        final String finalReportDir = reportDir;
        Results results = Results.startTimer(threadCount);
        results.setReportDir(reportDir);
        if (options.hooks != null) {
            options.hooks.forEach(h -> h.beforeAll(results));
        }
        ExecutorService featureExecutor = Executors.newFixedThreadPool(threadCount, Executors.privilegedThreadFactory());
        ExecutorService scenarioExecutor = Executors.newWorkStealingPool(threadCount);
        List<Resource> resources = options.resources();
        try {
            int count = resources.size();
            CountDownLatch latch = new CountDownLatch(count);
            List<FeatureResult> featureResults = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                Resource resource = resources.get(i);
                int index = i + 1;
                Feature feature = FeatureParser.parse(resource);
                feature.setCallName(options.scenarioName);
                feature.setCallLine(resource.getLine());
                FeatureContext featureContext = new FeatureContext(null, feature, options.tagSelector());
                CallContext callContext = CallContext.forAsync(feature, options.hooks, null, false);
                ExecutionContext execContext = new ExecutionContext(results, results.getStartTime(), featureContext, callContext, reportDir,
                        r -> featureExecutor.submit(r), scenarioExecutor, Thread.currentThread().getContextClassLoader());
                featureResults.add(execContext.result);
                FeatureExecutionUnit unit = new FeatureExecutionUnit(execContext);
                unit.setNext(() -> {
                    FeatureResult result = execContext.result;
                    if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags                   
                        File file = Engine.saveResultJson(finalReportDir, result, null);
                        if (result.getScenarioCount() < 500) {
                            // TODO this routine simply cannot handle that size
                            Engine.saveResultXml(finalReportDir, result, null);
                        }
                        String status = result.isFailed() ? "fail" : "pass";
                        LOGGER.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                        result.printStats(file.getPath());
                    } else {
                        results.addToSkipCount(1);
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
                        }
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
                    results.incrementFeatureCount();
                }
                results.addToFailCount(result.getFailedCount());
                results.addToTimeTaken(result.getDurationMillis());
                if (result.isFailed()) {
                    results.addToFailedList(result.getPackageQualifiedName(), result.getErrorMessages());
                }
                results.addScenarioResults(result.getScenarioResults());
            }
        } catch (Exception e) {
            LOGGER.error("karate parallel runner failed: ", e.getMessage());
            results.setFailureReason(e);
        } finally {
            featureExecutor.shutdownNow();
            scenarioExecutor.shutdownNow();
        }
        results.printStats(threadCount);
        Engine.saveStatsJson(reportDir, results, null);
        Engine.saveTimelineHtml(reportDir, results, null);        
        if (options.hooks != null) {
            options.hooks.forEach(h -> h.afterAll(results));
        }        
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
        CallContext callContext = CallContext.forAsync(feature, Collections.singletonList(hook), arg, true);
        ExecutionContext executionContext = new ExecutionContext(null, System.currentTimeMillis(), featureContext, callContext, null, system, null);
        FeatureExecutionUnit exec = new FeatureExecutionUnit(executionContext);
        exec.setNext(next);
        system.accept(exec);
    }

}

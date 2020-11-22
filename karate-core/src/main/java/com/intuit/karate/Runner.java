/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlReport;
import com.intuit.karate.core.HtmlSummaryReport;
import com.intuit.karate.core.ParallelProcessor;
import com.intuit.karate.core.Subscriber;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.RuntimeHookFactory;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Runner {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Runner.class);

    public static Results parallel(Builder options) {
        Suite suite = new Suite(options);
        if (options.hooks != null) {
            options.hooks.forEach(h -> h.beforeSuite(suite));
        }
        ExecutorService featureExecutor = Executors.newWorkStealingPool(suite.threadCount);
        List<CompletableFuture> futures = new ArrayList();
        CompletableFuture latch = new CompletableFuture();
        Subscriber<CompletableFuture> subscriber = new Subscriber<CompletableFuture>() {
            @Override
            public void onNext(CompletableFuture result) {
                futures.add(result);
            }

            @Override
            public void onComplete() {
                latch.complete(Boolean.TRUE);
            }
        };
        int count = suite.features.size();
        Results results = suite.results;
        final List<FeatureResult> featureResults = new ArrayList(count);
        ParallelProcessor<Feature, CompletableFuture> processor
                = new ParallelProcessor<Feature, CompletableFuture>(featureExecutor, suite.features.iterator()) {
            int index = 0;

            @Override
            public Iterator<CompletableFuture> process(Feature feature) {
                CompletableFuture future = new CompletableFuture();
                try {
                    FeatureRuntime fr = FeatureRuntime.of(suite, feature);
                    fr.setNext(() -> {
                        featureResults.add(fr.result);
                        onFeatureDone(fr, ++index, count);
                        future.complete(Boolean.TRUE);
                    });
                    fr.run();
                } catch (Exception e) {
                    future.complete(Boolean.FALSE);
                    LOGGER.error("runner failed: {}", e.getMessage());
                    results.setFailureReason(e);
                }
                return Collections.singletonList(future).iterator();
            }
        };
        try {
            processor.subscribe(subscriber);
            latch.join();
            CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);
            CompletableFuture allFutures = CompletableFuture.allOf(futuresArray);
            LOGGER.info("waiting for {} parallel features to complete ...", futuresArray.length);
            if (options.timeoutMinutes > 0) {
                allFutures.get(options.timeoutMinutes, TimeUnit.MINUTES);
            } else {
                allFutures.join();
            }
            LOGGER.info("all features complete");
            suite.results.stopTimer();
            featureExecutor.shutdownNow();
            HtmlSummaryReport summary = new HtmlSummaryReport();
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
                if (!result.isEmpty()) {
                    HtmlFeatureReport.saveFeatureResult(suite.reportDir, result);
                    summary.addFeatureResult(result);
                }
            }
            // saving reports can in rare cases throw errors, so do within try block
            summary.save(suite.reportDir);
            results.printStats(suite.threadCount);
            Engine.saveStatsJson(suite.reportDir, results);
            HtmlReport.saveTimeline(suite.reportDir, results, null);
            if (options.hooks != null) {
                options.hooks.forEach(h -> h.afterSuite(suite));
            }
        } catch (Exception e) {
            LOGGER.error("runner failed: {}", e);
            results.setFailureReason(e);
        }
        return results;
    }

    private static void onFeatureDone(FeatureRuntime fr, int index, int count) {
        FeatureResult result = fr.result;
        Feature feature = fr.feature;
        String reportDir = fr.suite.reportDir;
        if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags
            try { // edge case that reports are not writable
                File file = Engine.saveResultJson(reportDir, result, null);
                if (result.getScenarioCount() < 500) {
                    // TODO this routine simply cannot handle that size
                    Engine.saveResultXml(reportDir, result, null);
                }
                String status = result.isFailed() ? "fail" : "pass";
                LOGGER.info("<<{}>> feature {} of {}: {}", status, index, count, feature);
                result.printStats(file.getPath());
            } catch (Exception e) {
                LOGGER.error("<<error>> unable to write report file(s): {}", e.getMessage());
                result.printStats(null);
            }
        } else {
            fr.suite.results.addToSkipCount(1);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("<<skip>> feature {} of {}: {}", index, count, feature);
            }
        }
    }

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        Suite suite = new Suite();
        FeatureRuntime featureRuntime = FeatureRuntime.of(suite, feature, vars);
        featureRuntime.caller.setKarateConfigDisabled(!evalKarateConfig);
        featureRuntime.run();
        FeatureResult result = featureRuntime.result;
        if (result.isFailed()) {
            throw result.getErrorsCombined();
        }
        return result.getResultVariables();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = Feature.read(file);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = ResourceUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = Feature.read(path);
        return runFeature(feature, vars, evalKarateConfig);
    }

    // this is called by karate-gatling !
    public static void callAsync(String path, List<String> tags, Map<String, Object> arg, PerfHook perf) {
        Builder builder = new Builder();
        builder.tags = tags;
        Suite suite = new Suite(builder); // sets tag selector
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        FeatureRuntime featureRuntime = FeatureRuntime.of(suite, feature, arg);
        featureRuntime.setPerfRuntime(perf);
        featureRuntime.setNext(() -> perf.afterFeature(featureRuntime.getResult()));
        perf.submit(featureRuntime);
    }

    //==========================================================================
    //
    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        return new Builder(clazz).reportDir(reportDir).parallel(threadCount);
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
            List<RuntimeHook> hooks, int threadCount, String reportDir) {
        Builder options = new Builder();
        options.tags = tags;
        options.paths = paths;
        options.scenarioName = scenarioName;
        options.hooks = hooks;
        options.reportDir = reportDir;
        return options.parallel(threadCount);
    }

    //==========================================================================
    //
    public static class Builder {

        Class optionsClass;
        ClassLoader classLoader;
        Logger logger;
        String env;
        File workingDir;
        String buildDir;
        String configDir;
        int threadCount;
        int timeoutMinutes;
        String reportDir;
        String scenarioName;
        List<String> tags;
        List<String> paths;
        List<Feature> features;
        String relativeTo;
        Collection<RuntimeHook> hooks;
        RuntimeHookFactory hookFactory;
        HttpClientFactory clientFactory;
        boolean forTempUse;
        Map<String, String> systemProperties;

        public List<Feature> resolveFeatures() {
            if (features == null) {
                if (paths != null && !paths.isEmpty()) {
                    if (relativeTo != null) {
                        paths = paths.stream().map(p -> {
                            if (!p.endsWith(".feature")) {
                                p = p + ".feature";
                            }
                            return relativeTo + "/" + p;
                        }).collect(Collectors.toList());
                    }
                } else if (relativeTo != null) {
                    paths = new ArrayList();
                    paths.add(relativeTo);
                }
                features = ResourceUtils.findFeatureFiles(paths);
            }
            if (scenarioName != null) {
                for (Feature feature : features) {
                    feature.setCallName(scenarioName);
                }
            }
            return features;
        }

        public Collection<RuntimeHook> resolveHooks() {
            if (hookFactory != null) {
                hook(hookFactory.create());
            }
            if (hooks == null) {
                hooks = Collections.EMPTY_LIST;
            }
            return hooks;
        }

        public String resolveEnv() {
            if (env == null) {
                env = StringUtils.trimToNull(System.getProperty(Constants.KARATE_ENV));
            }
            if (env != null) {
                logger.info("karate.env is: '{}'", env);
            }
            return env;
        }

        public Builder forTempUse() {
            forTempUse = true;
            return this;
        }

        private Builder() {
            this(new RunnerOptions());
        }

        public Builder(Class optionsClass) { // TODO deprecate this junit4 legacy
            this(RunnerOptions.fromAnnotationAndSystemProperties(null, null, optionsClass));
        }

        public Builder(RunnerOptions ro) {
            classLoader = Thread.currentThread().getContextClassLoader();
            logger = new Logger();
            workingDir = new File("");
            buildDir = FileUtils.getBuildDir();
            reportDir = buildDir + File.separator + Constants.SUREFIRE_REPORTS;
            threadCount = ro.getThreads();
            paths = ro.getFeatures();
            tags = ro.getTags();
            scenarioName = ro.getName();
            env = ro.getEnv();
        }

        //======================================================================
        //        
        public Builder classLoader(ClassLoader cl) {
            this.classLoader = cl;
            return this;
        }

        public Builder configDir(String dir) {
            this.configDir = dir;
            return this;
        }

        public Builder karateEnv(String env) {
            this.env = env;
            return this;
        }
        
        public Builder systemProperty(String key, String value) {
            if (systemProperties == null) {
                systemProperties = new HashMap();
            }
            systemProperties.put(key, value);
            return this;
        }       

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder workingDir(File value) {
            if (value != null) {
                this.workingDir = value;
            }
            return this;
        }

        public Builder buildDir(String value) {
            if (value != null) {
                this.buildDir = value;
            }
            return this;
        }

        public Builder relativeTo(Class clazz) {
            relativeTo = "classpath:" + ResourceUtils.toPathFromClassPathRoot(clazz);
            return this;
        }

        public Builder path(String... value) {
            path(Arrays.asList(value));
            return this;
        }

        public Builder path(List<String> value) {
            if (value != null) {
                if (paths == null) {
                    paths = new ArrayList();
                }
                paths.addAll(value);
            }
            return this;
        }

        public Builder tags(List<String> value) {
            if (value != null) {
                if (tags == null) {
                    tags = new ArrayList();
                }
                tags.addAll(value);
            }
            return this;
        }

        public Builder tags(String... tags) {
            tags(Arrays.asList(tags));
            return this;
        }

        public Builder features(Collection<Feature> value) {
            if (value != null) {
                if (features == null) {
                    features = new ArrayList();
                }
                features.addAll(value);
            }
            return this;
        }

        public Builder features(Feature... value) {
            return features(Arrays.asList(value));
        }

        public Builder reportDir(String value) {
            if (value != null) {
                this.reportDir = value;
            }
            return this;
        }

        public Builder scenarioName(String name) {
            this.scenarioName = name;
            return this;
        }

        public Builder timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder hook(RuntimeHook hook) {
            if (hooks == null) {
                hooks = new ArrayList();
            }
            hooks.add(hook);
            return this;
        }

        public Builder hookFactory(RuntimeHookFactory hookFactory) {
            this.hookFactory = hookFactory;
            return this;
        }

        public Builder clientFactory(HttpClientFactory clientFactory) {
            this.clientFactory = clientFactory;
            return this;
        }

        public Results parallel(int threadCount) {
            this.threadCount = threadCount;
            return Runner.parallel(this);
        }

        @Override
        public String toString() {
            return paths + "";
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

    public static Builder builder() {
        return new Runner.Builder();
    }

}

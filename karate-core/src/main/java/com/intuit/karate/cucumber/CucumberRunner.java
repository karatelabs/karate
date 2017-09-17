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
package com.intuit.karate.cucumber;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Script;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValueMap;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.formatter.Formatter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CucumberRunner {

    private static final Logger logger = LoggerFactory.getLogger(CucumberRunner.class);

    private final ClassLoader classLoader;
    private final RuntimeOptions runtimeOptions;
    private final ResourceLoader resourceLoader;
    private final List<CucumberFeature> features;

    public CucumberRunner(Class clazz) {
        classLoader = clazz.getClassLoader();
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        resourceLoader = new MultiLoader(classLoader);
        features = runtimeOptions.cucumberFeatures(resourceLoader);
    }

    public CucumberRunner(File file) {
        classLoader = Thread.currentThread().getContextClassLoader();        
        runtimeOptions = new RuntimeOptions(file.getPath());
        resourceLoader = new MultiLoader(classLoader);
        features = runtimeOptions.cucumberFeatures(resourceLoader);
    }

    public List<CucumberFeature> getFeatures() {
        return features;
    }

    public RuntimeOptions getRuntimeOptions() {
        return runtimeOptions;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * main cucumber bootstrap, common code for the default JUnit / TestNG
     * runner and more importantly for Karate-s custom runner - such as the
     * parallel one in the flow below, the ScriptEnv, the Backend and the
     * ObjectFactory used by the backend are created fresh for each Feature file
     * (and not re-used)
     */
    public KarateRuntime getRuntime(CucumberFeature feature, KarateReporter reporter) {
        File featureFile = FileUtils.resolveIfClassPath(feature.getPath());
        if (logger.isTraceEnabled()) {
            logger.debug("loading feature: {}", featureFile);
        }
        File featureDir = featureFile.getParentFile();
        ScriptEnv env = new ScriptEnv(null, featureDir, featureFile.getName(), classLoader, reporter);
        KarateBackend backend = new KarateBackend(env, null, null, false);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new KarateRuntime(resourceLoader, classLoader, backend, runtimeOptions, glue);
    }

    // only called for TestNG ?
    public void finish() {
        Formatter formatter = runtimeOptions.formatter(classLoader);
        formatter.done();
        formatter.close();
    }

    public void run(CucumberFeature feature, KarateReporter reporter) {
        KarateRuntime runtime = getRuntime(feature, reporter);
        feature.run(reporter, reporter, runtime);
    }

    private static KarateReporter getReporter(String reportDirPath, CucumberFeature feature) {
        File reportDir = new File(reportDirPath);
        try {
            reportDir.mkdirs();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String featurePath = feature.getPath();
        String featurePackagePath = featurePath.replace(File.separator, ".");
        if (featurePackagePath.endsWith(".feature")) {
            featurePackagePath = featurePackagePath.substring(0, featurePackagePath.length() - 8);
        }
        try {
            reportDirPath = reportDir.getPath() + File.separator;
            String reportPath = reportDirPath + "TEST-" + featurePackagePath + ".xml";
            return new KarateReporter(featurePackagePath, reportPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static KarateStats parallel(Class clazz, int threadCount) {
        return parallel(clazz, threadCount, "target/surefire-reports");
    }

    public static KarateStats parallel(Class clazz, int threadCount, String reportDir) {
        KarateStats stats = KarateStats.startTimer();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CucumberRunner runner = new CucumberRunner(clazz);
            List<CucumberFeature> features = runner.getFeatures();
            List<Callable<KarateReporter>> callables = new ArrayList<>(features.size());
            int count = features.size();
            for (int i = 0; i < count; i++) {
                int index = i + 1;
                CucumberFeature feature = features.get(i);
                callables.add(() -> {
                    String threadName = Thread.currentThread().getName();
                    KarateReporter reporter = getReporter(reportDir, feature);
                    if (logger.isTraceEnabled()) {
                        logger.trace(">>>> feature {} of {} on thread {}: {}", index, count, threadName, feature.getPath());
                    }
                    try {
                        runner.run(feature, reporter);
                        logger.info("<<<< feature {} of {} on thread {}: {}", index, count, threadName, feature.getPath());
                    } catch (Exception e) {
                        logger.error("karate xml/json generation failed for: {}", feature.getPath());
                        reporter.setFailureReason(e);
                    } finally { // try our best to close the report file gracefully so that report generation is not broken
                        reporter.done();
                    }
                    return reporter;
                });
            }            
            List<Future<KarateReporter>> futures = executor.invokeAll(callables);
            stats.stopTimer();            
            for (Future<KarateReporter> future : futures) {
                KarateReporter reporter = future.get(); // guaranteed to be not-null
                KarateJunitFormatter formatter = reporter.getJunitFormatter();
                if (reporter.getFailureReason() != null) {
                    logger.error("karate xml/json generation failed: {}", formatter.getFeaturePath());
                    logger.error("karate xml/json error stack trace", reporter.getFailureReason());
                }
                stats.addToTestCount(formatter.getTestCount());
                stats.addToFailCount(formatter.getFailCount());
                stats.addToSkipCount(formatter.getSkipCount());
                stats.addToTimeTaken(formatter.getTimeTaken());
                if (formatter.isFail()) {
                    stats.addToFailedList(formatter.getFeaturePath());
                }
            }            
        } catch (Exception e) {
            logger.error("karate parallel runner failed: ", e.getMessage());
            stats.setFailureReason(e);
        } finally {
            executor.shutdownNow();                        
        }
        stats.printStats(threadCount);
        return stats;
    }

    private static Map<String, Object> runFeature(File file, Map<String, Object> vars) {
        FeatureWrapper featureWrapper = FeatureWrapper.fromFile(file, Thread.currentThread().getContextClassLoader());
        ScriptValueMap scriptValueMap = CucumberUtils.call(featureWrapper, null, vars, false);
        return Script.simplify(scriptValueMap);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars) {
        File dir = com.intuit.karate.FileUtils.getDirContaining(relativeTo);
        File file = new File(dir.getPath() + File.separator + path);
        return runFeature(file, vars);
    }

    public static Map<String, Object> runClasspathFeature(String path, Map<String, Object> vars) {
        File file = new File("target/test-classes/" + path);
        return runFeature(file, vars);
    }

}

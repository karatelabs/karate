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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;
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
    private final List<FeatureFile> featureFiles;

    public CucumberRunner(Class clazz) {
        logger.debug("init test class: {}", clazz);
        classLoader = clazz.getClassLoader();
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        resourceLoader = new MultiLoader(classLoader);
        List<CucumberFeature> cfs = runtimeOptions.cucumberFeatures(resourceLoader);
        featureFiles = new ArrayList<>(cfs.size());
        for (CucumberFeature cf : cfs) {
            featureFiles.add(new FeatureFile(cf, new File(cf.getPath())));
        }
    }

    public CucumberRunner(File file) {
        logger.debug("init feature file: {}", file);
        classLoader = Thread.currentThread().getContextClassLoader();
        resourceLoader = new MultiLoader(classLoader);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(getClass());
        runtimeOptions = runtimeOptionsFactory.create();
        FeatureWrapper wrapper = FeatureWrapper.fromFile(file, classLoader);
        CucumberFeature feature = wrapper.getFeature();
        FeatureFile featureFile = new FeatureFile(feature, file);
        featureFiles = Collections.singletonList(featureFile);
    }

    public List<CucumberFeature> getFeatures() {
        List<CucumberFeature> list = new ArrayList<>(featureFiles.size());
        for (FeatureFile featureFile : featureFiles) {
            list.add(featureFile.feature);
        }
        return list;
    }

    public List<FeatureFile> getFeatureFiles() {
        return featureFiles;
    }

    public RuntimeOptions getRuntimeOptions() {
        return runtimeOptions;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public KarateRuntime getRuntime(CucumberFeature feature) {
        return getRuntime(new FeatureFile(feature, new File(feature.getPath())), null);
    }

    /**
     * main cucumber bootstrap, common code for the default JUnit / TestNG
     * runner and more importantly for Karate-s custom runner - such as the
     * parallel one in the flow below, the ScriptEnv, the Backend and the
     * ObjectFactory used by the backend are created fresh for each Feature file
     * (and not re-used)
     */
    public KarateRuntime getRuntime(FeatureFile featureFile, KarateReporter reporter) {
        File packageFile = featureFile.file;
        String featurePath;
        if (packageFile.exists()) { // loaded by karate
            featurePath = packageFile.getAbsolutePath();
        } else { // was loaded by cucumber-jvm, is relative to classpath
            String temp = packageFile.getPath().replace('\\', '/'); // fix for windows
            featurePath = classLoader.getResource(temp).getFile();
        }
        if (logger.isTraceEnabled()) {
            logger.debug("loading feature: {}", featurePath);
        }
        File featureDir = new File(featurePath).getParentFile();
        ScriptEnv env = new ScriptEnv(null, featureDir, packageFile.getName(), classLoader, reporter);
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

    public void run(FeatureFile featureFile, KarateReporter reporter) {
        KarateRuntime runtime = getRuntime(featureFile, reporter);
        featureFile.feature.run(reporter, reporter, runtime);
    }

    public void run(KarateReporter reporter) {
        for (FeatureFile featureFile : getFeatureFiles()) {
            run(featureFile, reporter);
        }
    }

    private static KarateReporter getReporter(String reportDirPath, FeatureFile featureFile) {
        File reportDir = new File(reportDirPath);
        try {
            FileUtils.forceMkdirParent(reportDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String featurePath = featureFile.feature.getPath();
        if (featurePath == null) {
            featurePath = featureFile.file.getPath();
        }
        featurePath = new File(featurePath).getPath(); // fix for windows
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
        CucumberRunner runner = new CucumberRunner(clazz);
        List<FeatureFile> featureFiles = runner.getFeatureFiles();
        List<Callable<KarateReporter>> callables = new ArrayList<>(featureFiles.size());
        int count = featureFiles.size();
        for (int i = 0; i < count; i++) {
            int index = i + 1;
            FeatureFile featureFile = featureFiles.get(i);
            callables.add(() -> {
                String threadName = Thread.currentThread().getName();
                KarateReporter reporter = getReporter(reportDir, featureFile);
                if (logger.isTraceEnabled()) {
                    logger.trace(">>>> feature {} of {} on thread {}: {}", index, count, threadName, featureFile.feature.getPath());
                }
                try {
                    try {
                        runner.run(featureFile, reporter);
                        logger.info("<<<< feature {} of {} on thread {}: {}", index, count, threadName, featureFile.feature.getPath());                    
                    } finally { // try our best to close the report file gracefully so that report generation is not broken
                        reporter.done();
                    }
                } catch (Exception e) {
                    logger.error("karate xml/json generation failed for: {}", featureFile.file);
                    reporter.setFailureReason(e);
                }
                return reporter;
            });
        }
        try {
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
            stats.printStats(threadCount);
            return stats;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

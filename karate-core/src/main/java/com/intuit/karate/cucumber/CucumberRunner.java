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

import com.intuit.karate.ScriptEnv;
import cucumber.runtime.Backend;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.StopWatch;
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
    
    public Runtime getRuntime(CucumberFeature feature) {
        return getRuntime(new FeatureFile(feature, new File(feature.getPath())));
    }

    public Runtime getRuntime(FeatureFile featureFile) {
        File packageFile = featureFile.file;
        String featurePath;
        if (packageFile.exists()) { // loaded by karate
            featurePath = packageFile.getAbsolutePath();
        } else { // was loaded by cucumber-jvm, is relative to classpath
            featurePath = classLoader.getResource(packageFile.getPath()).getFile();
        }
        logger.debug("loading feature: {}", featurePath);
        File featureDir = new File(featurePath).getParentFile();
        ScriptEnv env = new ScriptEnv(false, null, featureDir, packageFile.getName(), classLoader);        
        Backend backend = new KarateBackend(env, null, null);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new Runtime(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, StopWatch.SYSTEM, glue);
    }

    // only called for TestNG ?
    public void finish() {
        Formatter formatter = runtimeOptions.formatter(classLoader);
        formatter.done();
        formatter.close();
    }

    public void run(FeatureFile featureFile, KaratePrettyFormatter formatter) {
        Runtime runtime = getRuntime(featureFile);
        featureFile.feature.run(formatter, formatter, runtime);
    }

    public void run(KaratePrettyFormatter formatter) {
        for (FeatureFile featureFile : getFeatureFiles()) {
            run(featureFile, formatter);
        }
    }

    public static void parallel(Class clazz, int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CucumberRunner runner = new CucumberRunner(clazz);
        List<FeatureFile> featureFiles = runner.getFeatureFiles();
        List<Callable<KaratePrettyFormatter>> callables = new ArrayList<>(featureFiles.size());
        for (FeatureFile featureFile : featureFiles) {
            KaratePrettyFormatter formatter = new KaratePrettyFormatter();
            callables.add(() -> {
                runner.run(featureFile, formatter);
                return formatter;
            });
        }
        try {
            int scenariosRun = 0;
            int scenariosFailed = 0;
            List<Future<KaratePrettyFormatter>>
            futures = executor.invokeAll(callables);
            for (Future<KaratePrettyFormatter  > future : futures) {                
                KaratePrettyFormatter formatter = future.get();
                scenariosRun += formatter.getScenariosRun();
                scenariosFailed += formatter.getScenariosFailed();
                System.out.print(formatter.getBuffer());
            }
            System.out.println("==============================");
            System.out.println("Scenarios: " + scenariosRun + ", Failed: " + scenariosFailed);
            System.out.println("==============================");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

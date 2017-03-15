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
    private final List<CucumberFeature> features;

    public CucumberRunner(Class clazz) {
        logger.debug("init test class: {}", clazz);
        classLoader = clazz.getClassLoader();
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        resourceLoader = new MultiLoader(classLoader);
        features = runtimeOptions.cucumberFeatures(resourceLoader);
    }

    public CucumberRunner(File featureFile) {
        logger.debug("init feature file: {}", featureFile);
        classLoader = Thread.currentThread().getContextClassLoader();
        resourceLoader = new MultiLoader(classLoader);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(getClass());
        runtimeOptions = runtimeOptionsFactory.create();
        FeatureWrapper wrapper = FeatureWrapper.fromFile(featureFile, classLoader);
        features = Collections.singletonList(wrapper.getFeature());
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

    public Runtime getRuntime(CucumberFeature feature) {
        String featurePath = classLoader.getResource(feature.getPath()).getFile();
        logger.debug("loading feature: {}", featurePath);
        String featureDir = new File(featurePath).getParent();
        ScriptEnv env = ScriptEnv.init(new File(featureDir), classLoader);
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

    public void run(CucumberFeature feature, KaratePrettyFormatter formatter) {
        Runtime runtime = getRuntime(feature);
        feature.run(formatter, formatter, runtime);
    }

    public void run(KaratePrettyFormatter formatter) {
        for (CucumberFeature feature : getFeatures()) {
            run(feature, formatter);
        }
    }

    public static void parallel(Class clazz, int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CucumberRunner runner = new CucumberRunner(clazz);
        List<CucumberFeature> features = runner.getFeatures();
        List<Callable<KaratePrettyFormatter>> callables = new ArrayList<>(features.size());
        for (CucumberFeature feature : runner.getFeatures()) {
            KaratePrettyFormatter formatter = new KaratePrettyFormatter();
            callables.add(() -> {
                runner.run(feature, formatter);
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

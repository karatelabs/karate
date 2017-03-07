package com.intuit.karate.junit4;

import com.intuit.karate.ScriptEnv;
import com.intuit.karate.cucumber.KarateBackend;
import cucumber.runtime.Backend;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.RuntimeOptionsFactory;
import cucumber.runtime.StopWatch;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.junit.Assertions;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implementation adapted from cucumber.api.junit.Cucumber
 * 
 * @author pthomas3
 */
public class Karate extends ParentRunner<KarateFeatureRunner> {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);
    
    private final JUnitReporter reporter;
    private final List<KarateFeatureRunner> children;

    public Karate(Class clazz) throws InitializationError, IOException {
        super(clazz);
        logger.debug("test: {}", clazz);
        ClassLoader classLoader = clazz.getClassLoader();
        Assertions.assertNoCucumberAnnotatedMethods(clazz);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        RuntimeOptions runtimeOptions = runtimeOptionsFactory.create();
        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        final JUnitOptions junitOptions = new JUnitOptions(runtimeOptions.getJunitOptions());
        final List<CucumberFeature> cucumberFeatures = runtimeOptions.cucumberFeatures(resourceLoader);
        reporter = new JUnitReporter(runtimeOptions.reporter(classLoader), runtimeOptions.formatter(classLoader), runtimeOptions.isStrict(), junitOptions);
        children = new ArrayList<>(cucumberFeatures.size());        
        for (CucumberFeature cucumberFeature : cucumberFeatures) {
            String featurePath = classLoader.getResource(cucumberFeature.getPath()).getFile();
            logger.debug("feature: {}", featurePath);
            String featureDir = new File(featurePath).getParent();
            Runtime runtime = getRuntime(featureDir, resourceLoader, classLoader, runtimeOptions);
            children.add(new KarateFeatureRunner(cucumberFeature, runtime, reporter));            
        }        
    }      

    private Runtime getRuntime(String featureDir, ResourceLoader resourceLoader, ClassLoader classLoader, RuntimeOptions runtimeOptions) {
        ScriptEnv env = ScriptEnv.init(new File(featureDir), classLoader);
        Backend backend = new KarateBackend(env, null, null);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new Runtime(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, StopWatch.SYSTEM, glue);
    }
    
    @Override
    public List<KarateFeatureRunner> getChildren() {
        return children;
    }

    @Override
    protected Description describeChild(KarateFeatureRunner child) {
        return child.runner.getDescription();
    }

    @Override
    protected void runChild(KarateFeatureRunner child, RunNotifier notifier) {
        child.runner.run(notifier);
        child.runtime.printSummary();
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        reporter.done();
        reporter.close();
    }   

}

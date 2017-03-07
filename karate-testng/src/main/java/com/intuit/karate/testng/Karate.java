package com.intuit.karate.testng;

import com.intuit.karate.ScriptEnv;
import com.intuit.karate.cucumber.KarateBackend;
import cucumber.api.testng.CucumberExceptionWrapper;
import cucumber.api.testng.CucumberFeatureWrapperImpl;
import cucumber.api.testng.FeatureResultListener;
import cucumber.api.testng.TestNgReporter;
import cucumber.runtime.Backend;
import cucumber.runtime.CucumberException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implementation adapter from cucumber.api.testng.TestNGCucumberRunner
 *
 * @author pthomas3
 */
public class Karate {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);
    
    private final FeatureResultListener resultListener;
    private final List<CucumberFeature> features;
    private final RuntimeOptions runtimeOptions;
    private final ClassLoader classLoader;
    private final ResourceLoader resourceLoader;
    
    public Karate(Class clazz) {
        logger.debug("test: {}", clazz);
        classLoader = clazz.getClassLoader();
        resourceLoader = new MultiLoader(classLoader);
        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();
        TestNgReporter reporter = new TestNgReporter(System.out);
        resultListener = new FeatureResultListener(runtimeOptions.reporter(classLoader), runtimeOptions.isStrict());
        features = runtimeOptions.cucumberFeatures(resourceLoader);
    }   
    
    public void runFeature(CucumberFeature cucumberFeature) {
        String featurePath = classLoader.getResource(cucumberFeature.getPath()).getFile();
        logger.debug("feature: {}", featurePath);
        String featureDir = new File(featurePath).getParent(); 
        Runtime runtime = getRuntime(featureDir, resourceLoader, classLoader, runtimeOptions);
        resultListener.startFeature();
        cucumberFeature.run(
                runtimeOptions.formatter(classLoader),
                resultListener,
                runtime);
        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
        runtime.printSummary();
    } 
    
    public void finish() {
        Formatter formatter = runtimeOptions.formatter(classLoader);
        formatter.done();
        formatter.close();        
    }    
    
    private cucumber.runtime.Runtime getRuntime(String featureDir, ResourceLoader resourceLoader, ClassLoader classLoader, RuntimeOptions runtimeOptions) {
        ScriptEnv env = ScriptEnv.init(new File(featureDir), classLoader);
        Backend backend = new KarateBackend(env, null, null);
        RuntimeGlue glue = new RuntimeGlue(new UndefinedStepsTracker(), new LocalizedXStreams(classLoader));
        return new cucumber.runtime.Runtime(resourceLoader, classLoader, Collections.singletonList(backend), runtimeOptions, StopWatch.SYSTEM, glue);
    } 
    
    public Object[][] getFeatures() {
        try {
            List<Object[]> featuresList = new ArrayList<Object[]>(features.size());
            for (CucumberFeature feature : features) {
                featuresList.add(new Object[]{new CucumberFeatureWrapperImpl(feature)});
            }
            return featuresList.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e)}};
        }
    }    

}

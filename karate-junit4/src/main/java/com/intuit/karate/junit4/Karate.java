package com.intuit.karate.junit4;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.FeatureFile;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import java.io.IOException;
import java.util.ArrayList;
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
        CucumberRunner cr = new CucumberRunner(clazz);
        RuntimeOptions ro = cr.getRuntimeOptions();        
        List<CucumberFeature> cucumberFeatures = cr.getFeatures();
        ClassLoader cl = cr.getClassLoader();
        JUnitOptions junitOptions = new JUnitOptions(ro.getJunitOptions());
        reporter = new JUnitReporter(ro.reporter(cl), ro.formatter(cl), ro.isStrict(), junitOptions);
        children = new ArrayList<>(cucumberFeatures.size());        
        for (CucumberFeature feature : cucumberFeatures) {
            Runtime runtime = cr.getRuntime(feature);
            FeatureRunner runner = new FeatureRunner(feature, runtime, reporter);
            children.add(new KarateFeatureRunner(runner, runtime));            
        }        
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

package com.intuit.karate.junit4;

import com.intuit.karate.cucumber.KarateFeature;
import com.intuit.karate.cucumber.KarateHtmlReporter;
import com.intuit.karate.cucumber.KarateRuntimeOptions;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import gherkin.formatter.model.Feature;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

/**
 * implementation adapted from cucumber.api.junit.Cucumber
 * 
 * @author pthomas3
 */
public class Karate extends ParentRunner<KarateFeature> {
    
    private final List<KarateFeature> children;
    private final Map<String, Description> descriptions;
    
    private JUnitReporter reporter;
    private KarateHtmlReporter htmlReporter;    

    public Karate(Class clazz) throws InitializationError, IOException {
        super(clazz);
        // we have to repeat this step again later, for the sake of lazy init of the logger
        KarateRuntimeOptions kro = new KarateRuntimeOptions(clazz);
        children = KarateFeature.loadFeatures(kro);
        descriptions = new HashMap(children.size());
    }
    
    @Override
    public List<KarateFeature> getChildren() {
        // this seems to be called early in the life-cycle, which is why this is readied by the constructor
        return children;
    }        
    
    @Override
    protected Description describeChild(KarateFeature child) {
        Description description = descriptions.get(child.getFeature().getPath());
        if (description != null) {
            return description;
        }
        Feature feature = child.getFeature().getGherkinFeature();
        String name = feature.getKeyword() + ": " + feature.getName();
        return Description.createSuiteDescription(name, feature);
    }
    
    private void initReporters() {
        // we re-do the karate runtime, just so that the logger is fresh, else custom log appender collection fails
        KarateRuntimeOptions kro = new KarateRuntimeOptions(getTestClass().getJavaClass());
        RuntimeOptions ro = kro.getRuntimeOptions();
        ClassLoader cl = kro.getClassLoader();
        JUnitOptions junitOptions = new JUnitOptions(ro.getJunitOptions());
        htmlReporter = new KarateHtmlReporter(ro.reporter(cl), ro.formatter(cl));
        reporter = new JUnitReporter(htmlReporter, htmlReporter, ro.isStrict(), junitOptions);        
    }

    @Override
    protected void runChild(KarateFeature child, RunNotifier notifier) {
        if (reporter == null) {
            initReporters(); // deliberate lazy-init of html reporter and others
        }
        Runtime runtime = child.getRuntime(null);
        FeatureRunner runner;
        try {
            runner = new FeatureRunner(child.getFeature(), runtime, reporter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        htmlReporter.startKarateFeature(child.getFeature());
        runner.run(notifier);
        runtime.printSummary();
        htmlReporter.endKarateFeature();
        // not sure if this is needed, but possibly to make sure junit description is updated for failures etc
        descriptions.put(child.getFeature().getPath(), runner.getDescription());
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        reporter.done();
        reporter.close();
    }   

}

package com.intuit.karate.junit4;

import cucumber.runtime.Runtime;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import org.junit.runners.model.InitializationError;

/**
 *
 * @author pthomas3
 */
public class KarateFeatureRunner {
    
    protected final FeatureRunner runner;
    protected final Runtime runtime;
    protected final JUnitReporter reporter;
    
    public KarateFeatureRunner(CucumberFeature feature, Runtime runtime, JUnitReporter reporter) throws InitializationError {
        this.runner = new FeatureRunner(feature, runtime, reporter);
        this.runtime = runtime;
        this.reporter = reporter;
    }    
    
}

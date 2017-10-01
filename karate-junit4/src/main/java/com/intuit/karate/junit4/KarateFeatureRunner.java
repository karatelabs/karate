package com.intuit.karate.junit4;

import cucumber.runtime.Runtime;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import org.junit.runners.model.InitializationError;

/**
 *
 * @author pthomas3
 */
public class KarateFeatureRunner { // just because there's no getter for Runtime and CucumberFeature on FeatureRunner
    
    protected final CucumberFeature feature;
    protected final FeatureRunner runner;
    protected final Runtime runtime;
    
    public KarateFeatureRunner(CucumberFeature feature, FeatureRunner runner, Runtime runtime) throws InitializationError {
        this.feature = feature;
        this.runner = runner;        
        this.runtime = runtime;
    }    
    
}

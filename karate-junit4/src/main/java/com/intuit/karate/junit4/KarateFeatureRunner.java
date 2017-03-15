package com.intuit.karate.junit4;

import cucumber.runtime.Runtime;
import cucumber.runtime.junit.FeatureRunner;
import org.junit.runners.model.InitializationError;

/**
 *
 * @author pthomas3
 */
public class KarateFeatureRunner { // just because there's no getter for Runtime on FeatureRunner
    
    protected final FeatureRunner runner;
    protected final Runtime runtime;
    
    public KarateFeatureRunner(FeatureRunner runner, Runtime runtime) throws InitializationError {
        this.runner = runner;        
        this.runtime = runtime;
    }    
    
}

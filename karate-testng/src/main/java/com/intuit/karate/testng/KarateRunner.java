package com.intuit.karate.testng;

import cucumber.api.testng.CucumberFeatureWrapper;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * adapted from cucumber.api.testng.AbstractTestNGCucumberTests
 *  
 * @author pthomas3
 */
public abstract class KarateRunner {
    
    private Karate karate;
    
    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        karate = new Karate(this.getClass());
    }
    
    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        karate.finish();
    }    
    
    @Test(groups = "cucumber", description = "Runs Cucumber Feature", dataProvider = "features")
    public void feature(CucumberFeatureWrapper cucumberFeature) {
        karate.runFeature(cucumberFeature.getCucumberFeature());
    }
    
    @DataProvider
    public Object[][] features() {
        return karate.getFeatures();
    }    
    
}

package com.intuit.karate.testng;

import cucumber.api.CucumberOptions;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(plugin = {"pretty", "html:target/cucumber"}, tags = {"~@ignore"})
public class KarateTestngTest extends KarateRunner {
    
}

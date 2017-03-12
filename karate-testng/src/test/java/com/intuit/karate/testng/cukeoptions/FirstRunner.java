package com.intuit.karate.testng.cukeoptions;


import com.intuit.karate.testng.KarateRunner;
import cucumber.api.CucumberOptions;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:com/intuit/karate/testng/cukeoptions/first.feature")
public class FirstRunner extends KarateRunner {          
    
}

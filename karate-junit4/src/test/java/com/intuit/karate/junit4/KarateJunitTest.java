package com.intuit.karate.junit4;


import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(plugin = {"pretty", "html:target/cucumber", "junit:target/cucumber-junit.xml"}, tags = {"~@ignore"})
@RunWith(Karate.class)
public class KarateJunitTest {
    
}

package com.intuit.karate.junit4;


import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(plugin = {"pretty", "html:target/cucumber"}, tags = {"~@ignore"})
@RunWith(Karate.class)
public class KarateJunitTest {
    
}

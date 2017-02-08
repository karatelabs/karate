package com.intuit.karate.cucumber;

import com.intuit.karate.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@CucumberOptions(features = "classpath:com/intuit/karate/cucumber/second.feature")
public class SecondTest {
          
    
}

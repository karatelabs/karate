package com.intuit.karate.junit4.cukeoptions;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@CucumberOptions(plugin = {"pretty", "json:target/first.json"})
public class FirstTest {
    
}

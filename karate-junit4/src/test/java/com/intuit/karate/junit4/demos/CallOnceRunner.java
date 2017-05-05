package com.intuit.karate.junit4.demos;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:com/intuit/karate/junit4/demos/call-once.feature")
public class CallOnceRunner {

}
package com.intuit.karate.junit4.files;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:com/intuit/karate/junit4/files/working-dir.feature")
public class WorkingDirRunner {

}
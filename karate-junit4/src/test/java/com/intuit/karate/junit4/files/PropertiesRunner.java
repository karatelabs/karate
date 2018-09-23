package com.intuit.karate.junit4.files;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/files/properties.feature")
public class PropertiesRunner {

}
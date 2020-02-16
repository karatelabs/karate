package com.intuit.karate.junit4.config;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/config/config-env.feature")
public class ConfigRunner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "confenv");
    }
    
}

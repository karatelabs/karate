package com.intuit.karate.junit4.options;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/cukeoptions/second.feature")
public class SecondRunner {
          
    
}

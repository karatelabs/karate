package com.intuit.karate;

import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/client.feature")
public class ClientRunner {
    
}

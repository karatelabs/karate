package com.intuit.karate.junit4;


import com.intuit.karate.KarateOptions;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = "~@ignore", threads = 5)
@RunWith(Karate.class)
public class KarateJunitParallelTest {
    
}

package com.intuit.karate.junit4.demos;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/demos/outline-dynamic.feature")
public class OutlineDynamicRunner {
    
    @BeforeClass
    public static void beforeClass() {
        System.out.println("*** before class");
    }

}
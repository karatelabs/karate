package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainTestRunner {
    
    @Test
    public void testMain() {
        System.setProperty("karate.config.dir", "src/test/java");
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});                
    }
    
    @Test
    public void testScenarioName() {
        System.setProperty("karate.config.dir", "src/test/java");
        Main.main(new String[]{ 
            "-n", "^cors options method handling$",
            "src/test/java/com/intuit/karate/client.feature"});                
    } 
    
    @Test
    public void testScenarioLine() {
        System.setProperty("karate.config.dir", "src/test/java");
        Main.main(new String[]{"src/test/java/com/intuit/karate/client.feature:37"});                
    }     
    
}

package com.intuit.karate.cli;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainRunner {
    
    @Test
    public void testMain() {
        String command = "--plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^german xml$ --glue com.intuit.karate classpath:/dummy";
        System.setProperty("sun.java.command", command);
        Main.main(new String[]{});
    }
    
}

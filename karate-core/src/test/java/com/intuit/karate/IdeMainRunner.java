package com.intuit.karate;

import com.intuit.karate.cli.IdeMain;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class IdeMainRunner {        

    @Test
    void testCli() {
        IdeMain.main(new String[]{"-t", "~@skipme", "-T", "2", "classpath:com/intuit/karate/core/runner/multi-scenario.feature"});
    }
    
    @Test
    void testMain() {
        String command = "--plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^german xml$ --glue com.intuit.karate classpath:/dummy";
        System.setProperty("sun.java.command", command);
        IdeMain.main(new String[]{});
    }    

}

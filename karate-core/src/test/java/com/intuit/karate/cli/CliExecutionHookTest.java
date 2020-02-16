package com.intuit.karate.cli;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CliExecutionHookTest {
    
    @Test
    public void testCli() {
        Main.main(new String[]{"-t", "~@ignore", "-T", "2", "classpath:com/intuit/karate/multi-scenario.feature"});
    }
    
}

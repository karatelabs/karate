package com.intuit.karate.shell;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CommandTester {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandTester.class);
    
    @Test
    public void testWaitForKeyboard() {
        Command.waitForSocket(0);
    }
    
}

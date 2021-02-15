package com.intuit.karate.shell;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class CommandTester {

    static final Logger logger = LoggerFactory.getLogger(CommandTester.class);

    @Test
    void testWaitForKeyboard() {
        Command.waitForSocket(0);
    }

}

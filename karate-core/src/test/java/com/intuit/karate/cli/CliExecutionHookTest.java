package com.intuit.karate.cli;

import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class CliExecutionHookTest {

    @Test
    void testCli() {
        Main.main(new String[]{"-t", "~@ignore", "-T", "2", "classpath:com/intuit/karate/core/runner/multi-scenario.feature"});
    }

}

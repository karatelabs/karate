package com.intuit.karate;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MainRunner {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainRunner.class);
    
    @Test
    void testCli() {
        Main.main(new String[]{"-S"});
    }    
    
    @Test
    void testDebug() {
        String temp = "--threads=1 /Users/peter/dev/zcode/karate-todo/src/test/java/app/api/match/test.feature";
        Main main = Main.parseKarateOptionsAndQuotePath(temp);
    }
    
}

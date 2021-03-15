package com.intuit.karate;

import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class MainRunner {
    
    @Test
    void testCli() {
        Main.main(new String[]{"-S"});
    }    
    
}

package com.intuit.karate.ui;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class AppRunner {
    
    @Test
    public void testApp() {
        App.run("../karate-demo/src/test/java/demo/headers/headers.feature", "dev");
    }
    
}

package com.intuit.karate.ui;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class UiRunner {
    
    @Test    
    public void testDevUi() {
        App.run("src/test/java/com/intuit/karate/ui/test.feature", null);
    }      
    
}

package com.intuit.karate.junit4.demos;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class UiRunner {
    
    @Test    
    public void testUi() {
        App.run(null, null);
    }
    
}

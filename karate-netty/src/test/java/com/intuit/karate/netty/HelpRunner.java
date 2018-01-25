package com.intuit.karate.netty;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class HelpRunner {
    
    @Test
    public void testMain() {
        Main.main(new String[]{"-h"});        
    }
    
}

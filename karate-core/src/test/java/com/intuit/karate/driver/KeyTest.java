package com.intuit.karate.driver;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class KeyTest {
    
    @Test
    public void testKey() {
        assertTrue('a' < Key.INSTANCE.NULL);
    }
    
}

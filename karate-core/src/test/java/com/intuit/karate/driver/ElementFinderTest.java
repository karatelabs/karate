package com.intuit.karate.driver;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class ElementFinderTest {
    
    @Test
    public void testToJson() {
        String condition = ElementFinder.exitCondition("{^a}Foo");
        assertEquals("e.textContent.trim().includes('Foo') && e.tagName == 'A'", condition);
    }    
    
}

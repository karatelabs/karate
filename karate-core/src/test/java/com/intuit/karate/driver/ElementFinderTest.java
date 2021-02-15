package com.intuit.karate.driver;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ElementFinderTest {

    @Test
    void testToJson() {
        String condition = ElementFinder.exitCondition("{^a}Foo");
        assertEquals("e.textContent.trim().includes('Foo') && e.tagName == 'A'", condition);
    }

}

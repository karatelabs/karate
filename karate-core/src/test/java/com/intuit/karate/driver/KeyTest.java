package com.intuit.karate.driver;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class KeyTest {

    @Test
    void testKey() {
        assertTrue('a' < Key.INSTANCE.NULL);
    }

}

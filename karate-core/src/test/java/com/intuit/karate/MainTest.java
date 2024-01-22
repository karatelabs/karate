package com.intuit.karate;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author peter
 */
class MainTest {
    
    @Test
    void testNoDebug() {
        Main options = Main.parseKarateArgs(List.of());
        assertEquals(-1, options.debugPort);
    }    
    
    @Test
    void testDebug() {
        Main options = Main.parseKarateArgs(List.of("-d"));
        assertEquals(0, options.debugPort);
    }
    
}

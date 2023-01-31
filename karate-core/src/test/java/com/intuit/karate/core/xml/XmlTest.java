package com.intuit.karate.core.xml;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class XmlTest {
    
    @Test
    void testXml() {
        Results results = Runner.path("classpath:com/intuit/karate/core/xml").parallel(3);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());  
    }
    
}

package com.intuit.karate.junit4;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class KarateJunitTest {
    
    @Test
    public void testAll() {
        Results results = Runner
                .path("classpath:com/intuit/karate/junit4/demos")
                .path("classpath:com/intuit/karate/junit4/files")
                .path("classpath:com/intuit/karate/junit4/xml")
                .parallel(5);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());
    }    
    
}

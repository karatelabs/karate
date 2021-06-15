package com.intuit.karate.core;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class PrintTest {

    @Test
    void testPrint() {
        Results results = Runner.path("classpath:com/intuit/karate/core/print.feature").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

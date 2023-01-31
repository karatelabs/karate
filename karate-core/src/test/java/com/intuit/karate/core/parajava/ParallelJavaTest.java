package com.intuit.karate.core.parajava;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParallelJavaTest {

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/parajava")
                .configDir("classpath:com/intuit/karate/core/parajava")
                .karateEnv("foo")
                .parallel(5);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

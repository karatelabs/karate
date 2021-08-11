package com.intuit.karate.core.jscall;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsCallTest {

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:com/intuit/karate/core/jscall/js-call.feature")
                .configDir("classpath:com/intuit/karate/core/jscall")
                .parallel(5);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

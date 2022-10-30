package com.intuit.karate.core;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class PaymentsRunner {

    @Test
    void testPayments() {
        Results results = Runner.path("classpath:com/intuit/karate/core/payments.feature").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}

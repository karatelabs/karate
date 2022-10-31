package com.intuit.karate.core;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class CallResponseTest {

    @Test
    void testPayments() {
        MockServer server = MockServer
                .feature("classpath:com/intuit/karate/core/call-response-mock.feature")
                .http(0).build();        
        Results results = Runner.path("classpath:com/intuit/karate/core/call-response.feature")
                .systemProperty("server.port", server.getPort() + "")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
        server.stop();
    }

}

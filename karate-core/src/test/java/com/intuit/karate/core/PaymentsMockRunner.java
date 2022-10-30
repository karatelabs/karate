package com.intuit.karate.core;

import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class PaymentsMockRunner {

    @Test
    void startMock() {
        MockServer server = MockServer
                .feature("classpath:com/intuit/karate/core/payments-mock.feature")
                .pathPrefix("/api")
                .http(8080).build();
        server.waitSync();        
    }

}

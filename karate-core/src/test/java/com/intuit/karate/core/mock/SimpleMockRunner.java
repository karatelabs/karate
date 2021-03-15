package com.intuit.karate.core.mock;

import com.intuit.karate.core.MockServer;

/**
 *
 * @author pthomas3
 */
public class SimpleMockRunner {

    public static void main(String[] args) {
        MockServer server = MockServer
                .feature("src/test/java/com/intuit/karate/core/mock/_simple.feature")
                .http(8080)
                .watch(true)
                .build();
        server.waitSync();
    }

}

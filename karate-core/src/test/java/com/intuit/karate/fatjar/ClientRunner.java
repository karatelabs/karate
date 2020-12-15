package com.intuit.karate.fatjar;

import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ClientRunner {

    @Test
    void testClient() {
        Runner.runFeature("classpath:com/intuit/karate/fatjar/client.feature", null, true);
    }

}

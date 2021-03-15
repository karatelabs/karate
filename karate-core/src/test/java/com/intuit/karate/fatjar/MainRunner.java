package com.intuit.karate.fatjar;

import com.intuit.karate.Main;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class MainRunner {

    @Test
    void testMain() {
        Main.main(new String[]{"-m", "src/test/java/com/intuit/karate/fatjar/server.feature", "-p", "8080"});
    }

}

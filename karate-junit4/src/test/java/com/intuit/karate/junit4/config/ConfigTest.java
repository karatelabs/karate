package com.intuit.karate.junit4.config;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ConfigTest {
    
    @BeforeClass
    public static void beforeClass() {
        System.clearProperty("karate.env");
    }

    @Test
    public void testOverrideDir() {
        Results results = Runner.path("classpath:com/intuit/karate/junit4/config/config-dir.feature")
                .configDir("src/test/java/com/intuit/karate/junit4/config")
                .karateEnv("custom").parallel(1);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());
    }

    @Test
    public void testOverrideEnvAndDir() {
        Results results = Runner.path("classpath:com/intuit/karate/junit4/config/config-envdir.feature")
                .configDir("src/test/resources/conf")
                .karateEnv("confenvdir").parallel(1);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());

    }

    @Test
    public void testOverrideEnv() {
        Results results = Runner.path("classpath:com/intuit/karate/junit4/config/config-env.feature")
                .karateEnv("confenv").parallel(1);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());
    }

}

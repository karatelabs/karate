package com.intuit.karate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.intuit.karate.Runner.Builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemPropertiesTest {

    private static final String TEST_PROP = "testProperty";

    @AfterEach
    void clearTestProperty() {
        System.clearProperty(TEST_PROP);
    }

    @Test
    void testSystemPropertiesSetOnRunner() {
        Builder<?> builder = Runner.builder().systemProperty(TEST_PROP, "setOnRunner");
        builder.resolveAll();
        String propertyValue = builder.systemProperties.get(TEST_PROP);
        assertEquals(propertyValue, "setOnRunner");
        String jvmValue = System.getProperty(TEST_PROP);
        assertEquals(jvmValue, null);
    }

    @Test
    void testSystemPropertiesSetOnJVM() {
        System.setProperty(TEST_PROP, "setOnJVM"); // -DtestProperty=setOnJVM
        Builder<?> builder = Runner.builder();
        builder.resolveAll();
        String propertyValue = builder.systemProperties.get(TEST_PROP);
        assertEquals(propertyValue, "setOnJVM");
        String jvmValue = System.getProperty(TEST_PROP);
        assertEquals(jvmValue, "setOnJVM");
    }

    @Test
    void testPrecedenceOfSystemPropertiesSetOnRunnerAndJVM() {
        System.setProperty(TEST_PROP, "setOnJVM"); // -DtestProperty=setOnJVM
        Builder<?> builder = Runner.builder().systemProperty(TEST_PROP, "setOnRunner");
        builder.resolveAll();
        String propertyValue = builder.systemProperties.get(TEST_PROP);        
        assertEquals(propertyValue, "setOnJVM");
        String jvmValue = System.getProperty(TEST_PROP);
        assertEquals(jvmValue, "setOnJVM");
    }
    
}

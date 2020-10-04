package com.intuit.karate.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureRuntimeTest.class);

    private static FeatureRuntime run(String name) {
        return RuntimeUtils.runFeature("classpath:com/intuit/karate/runtime/" + name);
    }

    @Test
    void testPrint() {
        FeatureRuntime fr = run("print.feature");
        assertFalse(fr.result.isFailed());
    }
    
    @Test
    void testFail1() {
        FeatureRuntime fr = run("fail1.feature");
        assertTrue(fr.result.isFailed());
    }    

}

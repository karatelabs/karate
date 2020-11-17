package com.intuit.karate.core;

import com.intuit.karate.TestUtils;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureFailRunner {

    static final Logger logger = LoggerFactory.getLogger(FeatureFailRunner.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name);
        return fr;
    }

    @Test
    void testFailJs() {
        run("fail-js.feature");
        assertTrue(fr.result.isFailed());
    }

}

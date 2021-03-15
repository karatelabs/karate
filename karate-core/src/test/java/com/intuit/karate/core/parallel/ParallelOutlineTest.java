package com.intuit.karate.core.parallel;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ParallelOutlineTest {

    static final Logger logger = LoggerFactory.getLogger(ParallelOutlineTest.class);

    @Test
    void testParallelOutline() {
        Results results = Runner.path(
                "classpath:com/intuit/karate/core/parallel/parallel-outline-1.feature",
                "classpath:com/intuit/karate/core/parallel/parallel-outline-2.feature")
                .configDir("classpath:com/intuit/karate/core/parallel")
                .parallel(3);
        assertEquals(2, results.getFeaturesPassed());
        assertEquals(8, results.getScenariosPassed());
        assertEquals(0, results.getFailCount());
    }

}

package com.intuit.karate.junit4.demos;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@KarateOptions(features = {
    "classpath:com/intuit/karate/junit4/demos/fail-skipped.feature",
    "classpath:com/intuit/karate/junit4/demos/fail.feature"})
public class FailSkippedParallelRunner {

    @Test
    public void testParallel() {
        String karateOutputPath = "target/surefire-reports";
        Results results = Runner.parallel(getClass(), 1, karateOutputPath);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }

}

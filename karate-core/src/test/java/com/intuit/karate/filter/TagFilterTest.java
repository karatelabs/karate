package com.intuit.karate.filter;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author ssishtla
 */
@CucumberOptions(features = "classpath:com/intuit/karate/filter/tag-filter.feature")
public class TagFilterTest {

    @Test
    public void testParallel() throws Exception {
        String karateOutputPath = "target/surefire-reports";
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, karateOutputPath);
        if (stats != null) {
            assertEquals(0, stats.getFeatureCount());
            assertNotNull(stats.getFailureReason());
            assertTrue(stats.getFailureReason() instanceof TagFilterException);
            assertEquals("Feature: tag-filter.feature failed due to tag filtering", stats.getFailureReason().getMessage());
        } else {
            fail("Test was expecting an exception");
        }
    }
    
}

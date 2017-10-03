package com.intuit.karate;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.filter.TagFilterException;
import cucumber.api.CucumberOptions;
import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author ssishtla
 */
@CucumberOptions(features = "classpath:feature/tag-filter.feature")
public class TagFilterFeatureRunner {

    @Test
    public void testParallel() throws Exception {
        String karateOutputPath = "target/surefire-reports";
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, karateOutputPath);

        if(stats!=null) {
            Assert.assertEquals(0, stats.getFeatureCount());
            Assert.assertNotNull(stats.getFailureReason());
            Assert.assertTrue(stats.getFailureReason() instanceof TagFilterException);
            Assert.assertEquals("Feature: tag-filter.feature failed due to tag filtering",
                    stats.getFailureReason().getMessage());
        } else {
            Assert.fail("Test was expecting an exception");
        }
    }
}

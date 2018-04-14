package com.intuit.karate.filter;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import org.junit.Test;
import static org.junit.Assert.*;

@CucumberOptions(features = "classpath:com/intuit/karate/filter/tag-filter-multiscenario.feature", tags = {"@testId=5"})
public class TagFilterMultiScenarioTest {

    @Test
    public void testParallel() {
        String karateOutputPath = "target/surefire-reports";
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, karateOutputPath);
        if (stats != null) {
            assertEquals(1, stats.getFeatureCount());
            assertNull(stats.getFailureReason());
        } else {
            fail("test was expecting an exception");
        }
    }

}

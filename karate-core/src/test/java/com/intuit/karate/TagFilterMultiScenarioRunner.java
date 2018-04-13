package com.intuit.karate;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.filter.TagFilterException;
import cucumber.api.CucumberOptions;
import org.junit.Assert;
import org.junit.Test;

@CucumberOptions( tags = {"@testId=5"})
public class TagFilterMultiScenarioRunner {
    @Test
    public void testParallel() throws Exception {
        String karateOutputPath = "target/surefire-reports";
        KarateStats stats = CucumberRunner.parallel(getClass(), 5, karateOutputPath);

        if (stats != null) {
            //Assert.fail("Test is not expecting exception.");
        }

    }
}

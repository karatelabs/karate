package com.intuit.karate.junit4.cukeoptions;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public class ParallelWarnTest {
    
    @Test
    public void testParallel() {
        KarateStats stats = CucumberRunner.parallel(getClass(), 1);
    }
    
}

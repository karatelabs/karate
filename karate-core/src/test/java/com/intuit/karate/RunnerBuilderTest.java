package com.intuit.karate;

import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@KarateOptions(tags = {"~@ignore"})
public class RunnerBuilderTest {
    private boolean contains(String reportPath, String textToFind) {
        String contents = FileUtils.toString(new File(reportPath));
        return contents.contains(textToFind);
    }
    @Test
    public void testBuilderWithTestClass() {
        RunnerBuilder builder = new RunnerBuilder(getClass());
        Results results = builder.runParallel();
        assertEquals(2, results.getFailCount());
    }

    @Test
    public void testBuilderWithTestPath() {
        RunnerBuilder builder = new RunnerBuilder(Collections.singletonList("~@ignore"),"classpath:com/intuit/karate");
        Results results = builder.runParallel();
        assertEquals(2, results.getFailCount());
        String pathBase = "target/surefire-reports/com.intuit.karate.";
        assertTrue(contains(pathBase + "core.scenario.xml", "Then match b == { foo: 'bar'}"));
        assertTrue(contains(pathBase + "core.outline.xml", "Then assert a == 55"));
        assertTrue(contains(pathBase + "multi-scenario.xml", "Then assert a != 2"));
        // a scenario failure should not stop other features from running
        assertTrue(contains(pathBase + "multi-scenario-fail.xml", "Then assert a != 2 ........................................................ passed"));
        assertEquals(2, results.getFailedMap().size());
        assertTrue(results.getFailedMap().keySet().contains("com.intuit.karate.no-scenario-name"));
        assertTrue(results.getFailedMap().keySet().contains("com.intuit.karate.multi-scenario-fail"));
    }
}

package com.intuit.karate.core.runner;

import com.intuit.karate.FileUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class RunnerTest {

    static final Logger logger = LoggerFactory.getLogger(RunnerTest.class);

    boolean contains(String reportPath, String textToFind) {
        String contents = FileUtils.toString(new File(reportPath));
        return contents.contains(textToFind);
    }

    static String resultXml(String name) {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/runner/" + name);
        FeatureRuntime fr = FeatureRuntime.of(feature);
        fr.run();
        File file = ReportUtils.saveJunitXml("target", fr.result, null);
        return FileUtils.toString(file);
    }

    @Test
    void testScenario() throws Exception {
        String contents = resultXml("scenario.feature");
        assertTrue(contents.contains("Then match b == { foo: 'bar'}"));
    }

    @Test
    void testScenarioOutline() throws Exception {
        String contents = resultXml("outline.feature");
        assertTrue(contents.contains("When def a = 55"));
    }

    @Test
    void testParallel() {
        Results results = Runner.path(
                "classpath:com/intuit/karate/core/runner/multi-scenario-fail.feature",
                "classpath:com/intuit/karate/core/runner/no-scenario-name.feature",
                "classpath:com/intuit/karate/core/runner/scenario.feature",
                "classpath:com/intuit/karate/core/runner/outline.feature",
                "classpath:com/intuit/karate/core/runner/stackoverflow-error.feature"
        ).outputJunitXml(true).parallel(1);
        assertEquals(3, results.getFailCount());
        String pathBase = "target/karate-reports/com.intuit.karate.core.runner.";
        assertTrue(contains(pathBase + "scenario.xml", "Then match b == { foo: 'bar'}"));
        assertTrue(contains(pathBase + "outline.xml", "Then assert a == 55"));
        // a scenario failure should not stop other features from running
        assertTrue(contains(pathBase + "multi-scenario-fail.xml", "Then assert a != 2 ........................................................ passed"));
        assertEquals(3, results.getFailCount());
    }

    @Test
    void testRunningFeatureFromJavaApi() {
        Map<String, Object> result = Runner.runFeature(getClass(), "scenario.feature", null, true);
        assertEquals(1, result.get("a"));
        Map<String, Object> temp = (Map) result.get("b");
        assertEquals("bar", temp.get("foo"));
        assertEquals("normal", result.get("configSource"));
    }

    @Test
    void testRunningFeatureFailureFromJavaApi() {
        try {
            Runner.runFeature(getClass(), "multi-scenario-fail.feature", null, true);
            fail("expected exception to be thrown");
        } catch (Exception e) {
            assertTrue(e instanceof KarateException);
        }
    }

    @Test
    void testRunningFeatureFailureFromRunner() {
        Results results = Runner.path("classpath:com/intuit/karate/core/runner/multi-scenario-fail.feature").parallel(1);
        assertEquals(1, results.getFailCount());
    }

    @Test
    void testRunningRelativePathFeatureFromJavaApi() {
        Map<String, Object> result = Runner.runFeature("classpath:com/intuit/karate/core/runner/test-called.feature", null, true);
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
        assertEquals("normal", result.get("configSource"));
    }

    @Test
    void testCallerArg() throws Exception {
        String contents = resultXml("caller-arg.feature");
        assertFalse(contents.contains("failed"));
        assertTrue(contents.contains("* def result = call read('called-arg-null.feature')"));
    }
    
    @Test
    void testJavaApiWithArgAndConfig() {
        Map<String, Object> result = Runner.runFeature("classpath:com/intuit/karate/core/runner/run-arg.feature", Collections.singletonMap("foo", "hello"), true);
        assertEquals("hello world", result.get("message"));
        assertEquals("normal", result.get("configSource"));
    }  
    
    @Test
    void testJavaApiWithArgNoConfig() {
        Map<String, Object> result = Runner.runFeature("classpath:com/intuit/karate/core/runner/run-arg.feature", Collections.singletonMap("foo", "hello"), false);
        assertEquals("hello world", result.get("message"));
        assertEquals(null, result.get("configSource"));
    }     

}

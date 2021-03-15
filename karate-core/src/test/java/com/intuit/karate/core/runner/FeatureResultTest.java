package com.intuit.karate.core.runner;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Suite;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.FeatureRuntime;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vmchukky
 */
public class FeatureResultTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureResultTest.class);

    static FeatureResult result(String name) {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/runner/" + name);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(), feature);
        fr.run();
        return fr.result;
    }

    static String xml(FeatureResult result) {
        File file = ReportUtils.saveJunitXml("target", result, null);
        return FileUtils.toString(file);
    }

    @Test
    void testFailureMultiScenarioFeature() throws Exception {
        FeatureResult result = result("failed.feature");
        assertEquals(2, result.getFailedCount());
        assertEquals(3, result.getScenarioCount());
        String contents = xml(result);
        assertTrue(contents.contains("did not evaluate to 'true': a != 1"));
        assertTrue(contents.contains("did not evaluate to 'true': a == 3"));

        // failure1 should have first step as failure, and second step as skipped
        // TODO: generate the expected content string, below code puts a hard dependency
        // with KarateJunitFormatter$TestCase.addStepAndResultListing()
        assertTrue(contents.contains("Then assert a != 1 ........................................................ failed"));
        assertTrue(contents.contains("And assert a == 2 ......................................................... skipped"));

        // failure2 should have first step as passed, and second step as failed
        assertTrue(contents.contains("Then assert a != 2 ........................................................ passed"));
        assertTrue(contents.contains("And assert a == 3 ......................................................... failed"));

        // pass1 should have both steps as passed
        assertTrue(contents.contains("Then assert a != 4 ........................................................ passed"));
        assertTrue(contents.contains("And assert a != 5 ......................................................... passed"));
    }

    @Test
    void testAbortMultiScenarioFeature() throws Exception {
        FeatureResult result = result("aborted.feature");
        assertEquals(0, result.getFailedCount());
        assertEquals(4, result.getScenarioCount());
        String contents = xml(result);

        // skip-pass and skip-fail both should have all steps as skipped
        // TODO: generate the expected content string, below code puts a hard dependency
        // with KarateJunitFormatter$TestCase.addStepAndResultListing()
        assertTrue(contents.contains("* karate.abort() .......................................................... passed"));
        assertTrue(contents.contains("* assert a == 1 ........................................................... skipped"));
        assertTrue(contents.contains("* assert a == 2 ........................................................... skipped"));
        assertTrue(contents.contains("* assert a == 5 ........................................................... passed"));

        // noskip should have both steps as passed
        assertTrue(contents.contains("Then assert a != 3 ........................................................ passed"));
        assertTrue(contents.contains("And assert a != 4 ......................................................... passed"));
    }

    // has to be public, used by the feature
    public static void addLambdaFunctionToMap(Map<String, Object> map) {
        IntBinaryOperator plusOperation = (a, b) -> a + b;
        map.put("javaSum", plusOperation);
    }

    @Test
    void testLambdaFunctionsInScenarioFeature() throws Exception {
        FeatureResult result = result("caller-with-lambda-arg.feature");
        assertEquals(0, result.getFailedCount());
        List data = (List) result.getVariables().get("data");
        assertTrue(((Map) data.get(0)).get("javaSum") instanceof IntBinaryOperator);
    }

    @Test
    void testStackOverFlowError() {
        FeatureResult result = result("stackoverflow-error.feature");
        assertTrue(result.isFailed());
        assertTrue(result.getScenarioResults().get(0).getErrorMessage().contains("StackOverflowError"));
    }

    @Test
    void testScenarioOutlineXmlResult() {
        FeatureResult result = result("outline.feature");
        ReportUtils.saveJunitXml("target", result, "outline.xml");
    }

}

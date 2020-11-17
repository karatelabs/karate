package com.intuit.karate.core.parser;

import com.intuit.karate.Runner;
import com.intuit.karate.Suite;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.core.FeatureRuntime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureParserTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureParserTest.class);

    static FeatureResult execute(String name) {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/parser/" + name);
        Runner.Builder builder = Runner.builder();
        builder.tags("~@ignore");
        FeatureRuntime fr = FeatureRuntime.of(new Suite(builder), feature);
        fr.run();
        return fr.result;
    }
    
    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }    

    @Test
    void testEngineForSimpleFeature() {
        FeatureResult result = execute("test-simple.feature");
        Map<String, Object> map = result.toMap();
        match(map.get("tags"), "[{ name: '@foo', line: 1 }]");
        ScenarioResult sr = (ScenarioResult) result.getScenarioResults().get(0);
        map = sr.toMap();
        match(map.get("tags"), "[{ name: '@bar', line: 5 }]");
        Engine.saveResultJson("target", result, null);
        Engine.saveResultXml("target", result, null);
    }

    @Test
    void testEngineForSimpleFeatureWithBackground() {
        FeatureResult result = execute("test-simple-background.feature");
        assertEquals(1, result.getScenarioResults().size());
        Engine.saveResultJson("target", result, null);
        Engine.saveResultXml("target", result, null);
    }

    @Test
    void testEngineForError() {
        FeatureResult result = execute("test-error.feature");
        Engine.saveResultJson("target", result, null);
        Engine.saveResultXml("target", result, null);
    }

    @Test
    void testParsingFeatureDescription() {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/parser/test-simple.feature");
        assertEquals("the first line", feature.getName());
        assertEquals("and the second", feature.getDescription());
    }

    @Test
    void testFeatureWithIgnore() {
        FeatureResult result = execute("test-ignore-feature.feature");
        assertEquals(0, result.getScenarioResults().size());
    }

    @Test
    void testScenarioWithIgnore() {
        FeatureResult result = execute("test-ignore-scenario.feature");
        assertEquals(1, result.getScenarioResults().size());
    }

    @Test
    void testDefDocString() {
        FeatureResult result = execute("test-def-docstring.feature");
        for (StepResult step : result.getScenarioResults().get(0).getStepResults()) {
            assertEquals("passed", step.getResult().getStatus());
        }
        Map<String, Object> map = result.getResultVariables();
        match(map.get("backSlash"), "C:\\foo\\bar\\");
    }

    @Test
    void testSetTable() {
        FeatureResult result = execute("test-set-table.feature");
        Map<String, Object> map = result.getResultVariables();
        match(map.get("output"), "{ name: 'Bob', age: 2 }");
    }

    @Test
    void testEmptyFeature() {
        try {
            FeatureResult result = execute("test-empty.feature.txt");
            fail("we expected parsing to fail");
        } catch (Exception e) {
            String message = e.getMessage();
            assertTrue(e.getMessage().contains("mismatched input '<EOF>'"));
        }
    }

    @Test
    void testEmptyFirstLine() {
        FeatureResult result = execute("test-empty-first-line1.feature");
        Map<String, Object> map = result.getResultVariables();
        match(map.get("foo"), "bar");
        result = execute("test-empty-first-line2.feature");
        map = result.getResultVariables();
        match(map.get("foo"), "bar");
        result = execute("test-empty-first-line3.feature");
        map = result.getResultVariables();
        match(map.get("foo"), "bar");
    }

    @Test
    void testFeatureHeaderOnly() {
        FeatureResult result = execute("test-feature-header-only.feature");
    }

    @Test
    void testTablePipe() {
        FeatureResult result = execute("test-table-pipe.feature");
        Map<String, Object> map = result.getResultVariables();
        match(map.get("value"), "pi|pe");
    }

    @Test
    void testOutlineName() {
        FeatureResult result = execute("test-outline-name.feature");
        Map<String, Object> map = result.getResultVariables();
        match(map.get("name"), "Nyan");
        match(map.get("title"), "name is Nyan and age is 5");
    }

    @Test
    void testTagsMultiline() {
        FeatureResult result = execute("test-tags-multiline.feature");
        Map<String, Object> map = result.getResultVariables();
        Match.that(map.get("tags")).contains("[ 'tag1', 'tag2', 'tag3', 'tag4' ]").isTrue();
    }

    @Test
    void testEdgeCases() {
        FeatureResult result = execute("test-edge-cases.feature");
    }

    @Test
    void testOutlineDynamic() {
        FeatureResult result = execute("test-outline-dynamic.feature");
        assertEquals(2, result.getScenarioResults().size());
        Map<String, Object> map = result.getResultVariables();
        match(map.get("name"), "Nyan");
        match(map.get("title"), "name is Nyan and age is 7");
    }

    @Test
    void testStepEditing() throws Exception {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/parser/test-simple.feature");
        Step step = feature.getStep(0, -1, 0);
        assertEquals("def a = 1", step.getText());
        step.parseAndUpdateFrom("* def a = 2 - 1");
        assertEquals("def a = 2 - 1", step.getText());
    }

    @Test
    void testEmptyBackground() {
        FeatureResult result = execute("test-empty-background.feature");
        assertFalse(result.isFailed());
        Map<String, Object> map = result.getResultVariables();
        match(map.get("temp"), "['foo']");
    }

    @Test
    void testHide() {
        Feature feature = Feature.read("classpath:com/intuit/karate/core/parser/test-hide.feature");
        Step step = feature.getStep(0, -1, 0);
        assertTrue(step.isPrefixStar());
        assertFalse(step.isPrint());
        assertEquals("def a = 1", step.getText());
        step = feature.getStep(0, -1, 1);
        assertTrue(step.isPrefixStar());
        assertTrue(step.isPrint());
        assertEquals("print a", step.getText());
        step = feature.getStep(0, -1, 2);
        assertFalse(step.isPrefixStar());
        assertTrue(step.isPrint());
        assertEquals("print a", step.getText());
    }

    @Test
    void testComments() {
        FeatureResult result = execute("test-comments.feature");
        assertFalse(result.isFailed());
    }

}

package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Resource;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureEditTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureEditTest.class);

    static final Resource EMPTY = new Resource(Paths.get(""), "", -1, null);

    Feature parse(String name) {
        InputStream is = getClass().getResourceAsStream(name);
        String text = FileUtils.toString(is);
        return FeatureParser.parseText(new Feature(EMPTY), text);
    }

    void printLines(List<String> lines) {
        int count = lines.size();
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            logger.trace("{}: {}", i + 1, line);
        }
    }

    @Test
    void testScenario() {
        Feature feature = parse("scenario.feature");
        List<String> lines = feature.getLines();
        assertEquals(16, lines.size());
        assertEquals(1, feature.getSections().size());
        Background background = feature.getBackground();
        Step step = background.getSteps().get(0);
        assertEquals("def a = 1", step.getText());

        Scenario scenario = feature.getSections().get(0).getScenario();
        assertFalse(scenario.isOutline());
        assertEquals(8, scenario.getLine()); // scenario on line 8
        List<Step> steps = scenario.getSteps();
        assertEquals(3, steps.size());
        step = steps.get(0);
        assertEquals(9, step.getLine());
        step = steps.get(1);
        assertEquals(11, step.getLine());
    }

    @Test
    void testScenarioOutline() {
        Feature feature = parse("outline.feature");
        List<String> lines = feature.getLines();
        assertEquals(13, lines.size());
        assertEquals(1, feature.getSections().size());
        ScenarioOutline so = feature.getSections().get(0).getScenarioOutline();
        assertEquals(4, so.getScenarios().size());
        Scenario scenario = so.getScenarios().get(0);
        assertTrue(scenario.isOutline());
    }

    @Test
    void testInsert() {
        Feature feature = parse("scenario.feature");
        feature = feature.addLine(9, "Then assert 2 == 2");
        List<String> lines = feature.getLines();
        assertEquals(17, lines.size());
        assertEquals(1, feature.getSections().size());
    }

    @Test
    void testEdit() {
        Feature feature = parse("scenario.feature");
        Step step = feature.getSections().get(0).getScenario().getSteps().get(0);
        int line = step.getLine();
        feature = feature.replaceLines(line, line, "Then assert 2 == 2");
        assertEquals(1, feature.getSections().size());
    }

    @Test
    void testMultiLineEditDocString() {
        Feature feature = parse("scenario.feature");
        printLines(feature.getLines());
        Step step = feature.getSections().get(0).getScenario().getSteps().get(1);
        assertEquals("def b =", step.getText());
        assertEquals(11, step.getLine());
        assertEquals(14, step.getEndLine());
        feature = feature.replaceStep(step, "Then assert 2 == 2");
        List<String> lines = feature.getLines();
        printLines(lines);
        assertEquals(13, lines.size());
        assertEquals("# another comment", feature.getLines().get(9));
        assertEquals("Then assert 2 == 2", feature.getLines().get(10));
        assertEquals("Then match b == { foo: 'bar'}", feature.getLines().get(11));
        assertEquals(1, feature.getSections().size());
    }

    @Test
    void testMultiLineEditTable() {
        Feature feature = parse("table.feature");
        printLines(feature.getLines());
        Step step = feature.getSections().get(0).getScenario().getSteps().get(0);
        assertEquals("table cats", step.getText());
        assertEquals(4, step.getLine());
        assertEquals(8, step.getEndLine());
        feature = feature.replaceStep(step, "Then assert 2 == 2");
        List<String> lines = feature.getLines();
        printLines(lines);
        assertEquals(7, lines.size());
        assertEquals("Then assert 2 == 2", feature.getLines().get(3));
        assertEquals("* match cats == [{name: 'Bob', age: 2}, {name: 'Wild', age: 4}, {name: 'Nyan', age: 3}]", feature.getLines().get(5));
    }

    @Test
    void testIdentifyingStepWhichIsAnHttpCall() {
        String text = "Feature:\nScenario:\n*  method post";
        Feature feature = FeatureParser.parseText(new Feature(EMPTY), text);
        Step step = feature.getSections().get(0).getScenario().getSteps().get(0);
        logger.debug("step name: '{}'", step.getText());
        assertTrue(step.getText().startsWith("method"));
    }

}

package com.intuit.karate.cucumber;

import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptEnv;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CucumberUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(CucumberUtilsTest.class);
    
    private void printLines(List<String> lines) {
        int count = lines.size();
        for (int i = 0; i < count; i++) {
            String line = lines.get(i);
            logger.trace("{}: {}", i + 1, line);
        }        
    }
    
    private ScriptEnv getEnv() {
        return new ScriptEnv("dev", new File("."), null, getClass().getClassLoader(), null);
    }
    
    @Test
    public void testScenario() {
        ScriptEnv env = getEnv();
        String filename = "scenario.feature";
        InputStream is = getClass().getResourceAsStream(filename);
        FeatureWrapper fw = FeatureWrapper.fromStream(is, env, filename);
        List<String> lines = fw.getLines();
        printLines(lines);
        assertEquals(16, lines.size());
        assertEquals(1, fw.getSections().size());
        ScenarioWrapper sw = fw.getSections().get(0).getScenario();
        assertFalse(sw.isChild());    
        assertEquals(8, sw.getLine()); // scenario on line 8
        List<StepWrapper> steps = sw.getSteps();
        assertEquals(4, steps.size());
        StepWrapper step = steps.get(0);
        assertTrue(step.isBackground());
        String stepText = step.getPriorText();
        assertEquals("Feature: simple feature file\n\n# some comment\n\nBackground:", stepText);
        assertEquals(5, step.getStartLine());
        CallContext callContext = new CallContext(null, 0, null, -1, false, true);
        KarateBackend backend = CucumberUtils.getBackendWithGlue(env, callContext);
        assertTrue(CucumberUtils.runCalledStep(step, backend).isPass());
        
        step = steps.get(1); // first scenario (non-background) step
        assertFalse(step.isBackground());
        stepText = step.getPriorText();
        assertEquals("Scenario: test", stepText);
        assertEquals(8, step.getStartLine());        
        assertTrue(CucumberUtils.runCalledStep(step, backend).isPass());
        
        step = steps.get(2);
        stepText = step.getPriorText();
        assertEquals(1, step.getPriorTextLineCount());
        assertTrue(step.isPriorTextPresent()); 
        assertEquals("# another comment", stepText);      
        
        step = steps.get(3);
        stepText = step.getPriorText();
        assertNull(stepText);
        assertEquals(0, step.getPriorTextLineCount());
        assertFalse(step.isPriorTextPresent());      
    }
    
    @Test
    public void testScenarioOutline() {
        String filename = "outline.feature";
        InputStream is = getClass().getResourceAsStream(filename);
        ScriptEnv env = getEnv();
        FeatureWrapper fw = FeatureWrapper.fromStream(is, env, filename);
        List<String> lines = fw.getLines();
        printLines(lines);
        assertEquals(13, lines.size());
        assertEquals(1, fw.getSections().size());
        ScenarioOutlineWrapper sow = fw.getSections().get(0).getScenarioOutline();
        assertEquals(4, sow.getScenarios().size());
        ScenarioWrapper sw = sow.getScenarios().get(0);
        assertTrue(sw.isChild());
    } 
    
    @Test
    public void testInsert() {
        String filename = "scenario.feature";
        InputStream is = getClass().getResourceAsStream(filename);
        ScriptEnv env = getEnv();
        FeatureWrapper fw = FeatureWrapper.fromStream(is, env, filename);
        fw = fw.addLine(9, "Then assert 2 == 2");
        List<String> lines = fw.getLines();
        printLines(lines);
        assertEquals(16, lines.size());
        assertEquals(1, fw.getSections().size());
    }
    
    @Test
    public void testEdit() {
        String filename = "scenario.feature";
        InputStream is = getClass().getResourceAsStream(filename);
        ScriptEnv env = getEnv();
        FeatureWrapper fw = FeatureWrapper.fromStream(is, env, filename);
        printLines(fw.getLines());
        StepWrapper step = fw.getSections().get(0).getScenario().getSteps().get(0);
        int line = step.getStartLine();        
        fw = fw.replaceLines(line, line, "Then assert 2 == 2");
        List<String> lines = fw.getLines();
        printLines(lines);
        assertEquals(15, lines.size());
        assertEquals(1, fw.getSections().size());
    }

    @Test
    public void testMultiLineEdit() {
        String filename = "scenario.feature";
        InputStream is = getClass().getResourceAsStream(filename);
        ScriptEnv env = getEnv();
        FeatureWrapper fw = FeatureWrapper.fromStream(is, env, filename);
        printLines(fw.getLines());
        StepWrapper step = fw.getSections().get(0).getScenario().getSteps().get(2);        
        fw = fw.replaceStep(step, "Then assert 2 == 2");
        List<String> lines = fw.getLines();
        printLines(lines);
        assertEquals(12, lines.size());
        assertEquals("# another comment", fw.getLines().get(9));
        assertEquals("Then assert 2 == 2", fw.getLines().get(10));
        assertEquals("Then match b == { foo: 'bar'}", fw.getLines().get(11));
        assertEquals(1, fw.getSections().size());
    }  
    
    @Test
    public void testIdentifyingStepWhichIsAnHttpCall() {
        String text = "Feature:\nScenario:\n*  method post";
        ScriptEnv env = getEnv();
        FeatureWrapper fw = FeatureWrapper.fromString(text, env, "dummy.feature");
        printLines(fw.getLines());
        StepWrapper step = fw.getSections().get(0).getScenario().getSteps().get(0);
        logger.debug("step name: '{}'", step.getStep().getName());
        assertTrue(step.isHttpCall());
    }

}

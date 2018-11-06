/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate;

import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import java.io.File;
import java.util.Map;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = {"~@ignore"})
public class RunnerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(RunnerTest.class);
    
    private boolean contains(String reportPath, String textToFind) {
        String contents = FileUtils.toString(new File(reportPath));
        return contents.contains(textToFind);
    }
    
    private static String resultXml(String name) {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/" + name);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, null);
        File file = Engine.saveResultXml("target", result, null);
        return FileUtils.toString(file);        
    }    
    
    @Test 
    public void testScenario() throws Exception {
        String contents = resultXml("core/scenario.feature");
        assertTrue(contents.contains("Then match b == { foo: 'bar'}"));
    }
    
    @Test 
    public void testScenarioOutline() throws Exception {
        String contents = resultXml("core/outline.feature");
        assertTrue(contents.contains("When def a = 55"));
    }  
    
    @Test 
    public void testParallel() {
        Results results = Runner.parallel(getClass(), 1);
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
    
    @Test
    public void testRunningFeatureFromJavaApi() {
        Map<String, Object> result = Runner.runFeature(getClass(), "core/scenario.feature", null, true);
        assertEquals(1, result.get("a"));
        Map<String, Object> temp = (Map) result.get("b");
        assertEquals("bar", temp.get("foo"));
        assertEquals("someValue", result.get("someConfig"));
    }
    
    @Test
    public void testRunningRelativePathFeatureFromJavaApi() {
        Map<String, Object> result = Runner.runFeature("classpath:com/intuit/karate/test-called.feature", null, true);
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
        assertEquals("someValue", result.get("someConfig"));
    }

    @Test 
    public void testCallerArg() throws Exception {
        String contents = resultXml("caller-arg.feature");
        assertFalse(contents.contains("failed"));
        assertTrue(contents.contains("* def result = call read('called-arg-null.feature')"));
    }    
    
}

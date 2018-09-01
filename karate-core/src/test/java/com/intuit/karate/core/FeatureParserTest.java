/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.Match;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureParserTest {

    private static final Logger logger = LoggerFactory.getLogger(FeatureParserTest.class);

    private static FeatureResult execute(String name) {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/" + name);
        return Engine.execute("mock", feature, "not('@ignore')", null);
    }

    @Test
    public void testEngineForSimpleFeature() {
        FeatureResult result = execute("test-simple.feature");
        Map<String, Object> map = result.toMap();
        Match.equals(map.get("tags"), "[{ name: '@foo', line: 1 }]");        
        ScenarioResult sr = (ScenarioResult) result.getScenarioResults().get(0);
        map = sr.toMap();
        Match.equals(map.get("tags"), "[{ name: '@bar', line: 5 }]");
        Engine.saveResultJson("target", result);
        Engine.saveResultXml("target", result);
    }

    @Test
    public void testEngineForSimpleFeatureWithBackground() {
        FeatureResult result = execute("test-simple-background.feature");
        assertEquals(1, result.getScenarioResults().size());
        Engine.saveResultJson("target", result);
        Engine.saveResultXml("target", result);
    }
    
    @Test
    public void testEngineForError() {
        FeatureResult result = execute("test-error.feature");
        Engine.saveResultJson("target", result);
        Engine.saveResultXml("target", result);
    }    

    @Test
    public void testParsingFeatureDescription() {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/test-simple.feature");
        assertEquals("the first line", feature.getName());
        assertEquals("and the second", feature.getDescription());
    }

    @Test
    public void testFeatureWithIgnore() {
        FeatureResult result = execute("test-ignore-feature.feature");
        assertEquals(0, result.getScenarioResults().size());
    }

    @Test
    public void testScenarioWithIgnore() {
        FeatureResult result = execute("test-ignore-scenario.feature");
        assertEquals(1, result.getScenarioResults().size());
    }

    @Test
    public void testDefDocString() {
        FeatureResult result = execute("test-def-docstring.feature");
        for (StepResult step : result.getScenarioResults().get(0).getStepResults()) {
            assertEquals("passed", step.getResult().getStatus());
        }

    }
    
    @Test
    public void testSetTable() {
        FeatureResult result = execute("test-set-table.feature");
        Map<String, Object> map = result.getResultAsPrimitiveMap();
        Match.equals(map.get("output"), "{ name: 'Bob', age: 2 }");
    }    

}

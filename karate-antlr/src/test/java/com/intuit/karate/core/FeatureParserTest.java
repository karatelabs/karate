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

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import java.io.File;
import java.util.Collections;
import java.util.List;
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

    private static StepDefs stepDefs(Feature feature) {
        File file = feature.getFile();
        ScriptEnv env = ScriptEnv.forEnvTagsAndFeatureFile("mock", "not('@ignore')", file);
        return new StepDefs(env, new CallContext(null, true));
    }

    private static Feature feature(String name) {
        return FeatureParser.parse("classpath:com/intuit/karate/core/" + name);
    }

    private static FeatureResult execute(String name) {
        Feature feature = feature(name);
        return Engine.execute(feature, stepDefs(feature));
    }

    @Test
    public void testEngineForSimpleFeature() {
        FeatureResult result = execute("test-simple.feature");
        assertEquals(1, result.getElements().size());
        List<FeatureResult> results = Collections.singletonList(result);
        String json = JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(results));
        FileUtils.writeToFile(new File("target/test-simple.json"), json);
    }

    @Test
    public void testParsingFeatureDescription() {
        Feature feature = feature("test-simple.feature");
        assertEquals("the first line", feature.getName());
        assertEquals("and the second", feature.getDescription());
    }

    @Test
    public void testFeatureWithIgnore() {
        FeatureResult result = execute("test-ignore-feature.feature");
        assertEquals(0, result.getElements().size());
    }

    @Test
    public void testScenarioWithIgnore() {
        FeatureResult result = execute("test-ignore-scenario.feature");
        assertEquals(1, result.getElements().size());
    }

    @Test
    public void testDefDocString() {
        FeatureResult result = execute("test-def-docstring.feature");
        for (StepResult step : result.getElements().get(0).getSteps()) {
            assertEquals("passed", step.getResult().getStatus());
        }

    }

}

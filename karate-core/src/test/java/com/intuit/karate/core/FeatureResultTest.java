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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import net.minidev.json.JSONArray;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author vmchukky
 */
public class FeatureResultTest {

    private static final Logger logger = LoggerFactory.getLogger(FeatureResultTest.class);
    
    private static FeatureResult result(String name) {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/" + name);
        return Engine.executeFeatureSync(null, feature, null, null);     
    }     
    
    private static String xml(FeatureResult result) {
        File file = Engine.saveResultXml("target", result, null);
        return FileUtils.toString(file);           
    }

    @Test
    public void testFailureMultiScenarioFeature() throws Exception {
        FeatureResult result = result("failed.feature");
        assertEquals(2, result.getFailedCount());
        assertEquals(3, result.getScenarioCount());
        String contents = xml(result);
        assertTrue(contents.contains("assert evaluated to false: a != 1"));
        assertTrue(contents.contains("assert evaluated to false: a == 3"));

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
    public void testAbortMultiScenarioFeature() throws Exception {        
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

    /**
     * Function used on testLambdaFunctionsInScenarioFeature() unit test to demonstrate the inclusion
     * of a functional interface via the Java interloop called from the .feature file
     * @param map
     */
    public static void addLambdaFunctionToMap(Map<String, Object> map) {
        IntBinaryOperator plusOperation = (a, b) -> a + b;
        map.put("javaSum", plusOperation);
    }

    @Test
    public void testLambdaFunctionsInScenarioFeature() throws Exception {
        FeatureResult result = result("caller-with-lambda-arg.feature");
        assertEquals(0, result.getFailedCount());

        JSONArray dataArr = (JSONArray) result.getResultAsPrimitiveMap().get("data");
        assertTrue( ((Map) dataArr.get(0)).get("javaSum") instanceof IntBinaryOperator);
        System.out.println();
    }
}

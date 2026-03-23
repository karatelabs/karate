/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.gatling;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify Engine bindings work correctly for Gatling integration.
 */
public class EngineBindingsTest {

    @Test
    void testRunnerRunFeatureWithVariables() {
        // Start mock server
        CatsMockServer.start();

        // Build arg map like KarateFeatureAction does
        Map<String, Object> arg = new HashMap<>();

        // __gatling vars (from Gatling session)
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "TestKitty");
        gatlingVars.put("age", 3);
        arg.put("__gatling", gatlingVars);

        // __karate vars (from previous features)
        arg.put("__karate", new HashMap<>());

        // Run the feature with variables
        FeatureResult result = Runner.runFeature("classpath:features/cats-create.feature", arg);

        // Check result
        assertFalse(result.isFailed(), "Feature should pass: " + getErrorMessage(result));

        // Verify result variables are captured (for chaining)
        Map<String, Object> resultVars = result.getResultVariables();
        assertNotNull(resultVars, "Result variables should be captured");
        assertNotNull(resultVars.get("catId"), "catId should be in result variables");
    }

    private String getErrorMessage(FeatureResult result) {
        if (!result.isFailed()) return null;
        return result.getScenarioResults().stream()
                .filter(sr -> sr.isFailed())
                .map(sr -> sr.getError() != null ? sr.getError().getMessage() : sr.getFailureMessage())
                .findFirst()
                .orElse("Unknown error");
    }

    @Test
    void testCallArgVariablesAccessible() {
        // This test verifies that callArg variables are accessible in the engine
        // after ScenarioRuntime initialization

        Map<String, Object> arg = new HashMap<>();
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "TestKitty");
        arg.put("__gatling", gatlingVars);

        FeatureResult result = Runner.runFeature("classpath:features/test-arg.feature", arg);

        // The test feature will fail if __gatling is not accessible
        assertFalse(result.isFailed(), "Feature should pass: " + getErrorMessage(result));
    }

    @Test
    void testEngineBindingsWithMap() {
        Engine engine = new Engine();

        // Put a map like __gatling
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "TestCat");
        gatlingVars.put("age", 5);

        engine.put("__gatling", gatlingVars);

        // Test that __gatling is accessible
        Object gatling = engine.eval("__gatling");
        assertNotNull(gatling, "__gatling should be accessible");
        assertTrue(gatling instanceof Map, "__gatling should be a Map");

        // Test nested access
        Object name = engine.eval("__gatling.name");
        assertEquals("TestCat", name, "__gatling.name should be 'TestCat'");

        Object age = engine.eval("__gatling.age");
        assertEquals(5, age, "__gatling.age should be 5");
    }

    @Test
    void testEngineBindingsWithPutRootBinding() {
        Engine engine = new Engine();

        // Put a map using putRootBinding
        Map<String, Object> arg = new HashMap<>();
        Map<String, Object> gatlingVars = new HashMap<>();
        gatlingVars.put("name", "RootCat");
        arg.put("__gatling", gatlingVars);

        engine.putRootBinding("__arg", arg);
        engine.put("__gatling", gatlingVars);

        // Test __arg access
        Object argResult = engine.eval("__arg");
        assertNotNull(argResult, "__arg should be accessible");

        // Test __gatling access
        Object gatlingResult = engine.eval("__gatling");
        assertNotNull(gatlingResult, "__gatling should be accessible");

        // Test nested access
        Object name = engine.eval("__gatling.name");
        assertEquals("RootCat", name, "__gatling.name should be 'RootCat'");
    }
}

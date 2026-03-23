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
package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for karate-config.js loading.
 */
class ScenarioConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testConfigLoadsVariables() throws Exception {
        // Create a karate-config.js
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                baseUrl: 'http://localhost:8080',
                timeout: 5000
              };
            }
            fn();
            """);

        // Create a simple feature file
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Config Test
            Scenario: Use config variables
            * def url = baseUrl
            * match url == 'http://localhost:8080'
            * match timeout == 5000
            """);

        // Run with config
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .configDir(configFile.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertEquals(1, result.getScenarioCount());
        assertEquals(1, result.getScenarioPassedCount());
        assertEquals(0, result.getScenarioFailedCount());
    }

    @Test
    void testEnvSpecificConfig() throws Exception {
        // Create base config
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                baseUrl: 'http://localhost:8080',
                env: karate.env
              };
            }
            fn();
            """);

        // Create env-specific config
        Path devConfigFile = tempDir.resolve("karate-config-dev.js");
        Files.writeString(devConfigFile, """
            function fn() {
              return {
                baseUrl: 'http://dev.example.com'
              };
            }
            fn();
            """);

        // Create a feature file
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Env Config Test
            Scenario: Check env config
            * match baseUrl == 'http://dev.example.com'
            * match env == 'dev'
            """);

        // Run with env=dev
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .configDir(configFile.toString())
                .karateEnv("dev")
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "Suite should pass");
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void testMissingConfigIsIgnored() throws Exception {
        // Create a feature file that doesn't need config
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: No Config Test
            Scenario: Simple test
            * def a = 1 + 2
            * match a == 3
            """);

        // Run without config file (missing config should be ignored)
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .configDir(tempDir.resolve("nonexistent-config.js").toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed());
    }

    @Test
    void testConfigVariablesAvailableInFeature() throws Exception {
        // Create config that returns object
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            ({
              appName: 'TestApp',
              version: '1.0'
            })
            """);

        // Create feature
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Config Variables
            Scenario: Access config vars
            * match appName == 'TestApp'
            * match version == '1.0'
            """);

        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .configDir(configFile.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed());
    }

    // ========== Working Directory Fallback Tests (V2 Enhancement) ==========

    @Test
    void testConfigFromWorkingDirectory() throws Exception {
        // Create karate-config.js in the working directory (tempDir)
        // This simulates a user running Karate from a directory without classpath setup
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                fromWorkingDir: true,
                baseUrl: 'http://workingdir.example.com'
              };
            }
            """);

        // Create a feature file
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Working Dir Config Test
            Scenario: Config loaded from working directory
            * match fromWorkingDir == true
            * match baseUrl == 'http://workingdir.example.com'
            """);

        // Run WITHOUT specifying configPath - should use default 'classpath:karate-config.js'
        // which won't be found on classpath, but will be found in working directory
        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "Config should be loaded from working directory");
    }

    @Test
    void testCallFeatureFromConfigV1Style() throws Exception {
        // This tests the V1 karate-config.js pattern where the function is defined
        // but NOT called at the end - V2 should detect and invoke it automatically
        Path utilsFeature = tempDir.resolve("utils.feature");
        Files.writeString(utilsFeature, """
            @ignore
            Feature:
            Scenario:
            * def hello = function(){ return { helloVar: 'hello world' } }
            """);

        // V1-style config: function fn() defined but not called
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              var config = {
                configUtils: karate.call('utils.feature')
              };
              return config;
            }
            """);

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: V1-style Config Test
            Scenario: Call function from config-loaded feature
            * call configUtils.hello
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "V1-style config with karate.call should work");
    }

    @Test
    void testCallFeatureFromConfig() throws Exception {
        // Create a reusable feature that defines functions
        Path utilsFeature = tempDir.resolve("utils.feature");
        Files.writeString(utilsFeature, """
            @ignore
            Feature:
            Scenario:
            * def hello = function(){ return { helloVar: 'hello world' } }
            """);

        // Create karate-config.js that calls the utils feature
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                configUtils: karate.call('utils.feature')
              };
            }
            """);

        // Create a feature that uses the config-loaded utils
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Config Utils Test
            Scenario: Call function from config-loaded feature
            * call configUtils.hello
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "Config with karate.call should work");
    }

    @Test
    void testScenarioPropertyFromConfig() throws Exception {
        // V1 pattern: access karate.scenario in config
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              var config = {};
              config.data = karate.scenario;
              return config;
            }
            """);

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Scenario Property Test
            Scenario: my test scenario
            * match karate.scenario.name == 'my test scenario'
            * match data.sectionIndex == 0
            * match data.exampleIndex == -1
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "karate.scenario property should work in config");
    }

    @Test
    void testCallonceFromConfig() throws Exception {
        // V1 pattern: karate.callonce() in config to load utils once
        Path utilsFeature = tempDir.resolve("utils.feature");
        Files.writeString(utilsFeature, """
            @ignore
            Feature:
            Scenario:
            * def hello = function(name){ return 'hello ' + name }
            """);

        // Config that uses karate.callonce to load utils
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              var config = karate.callonce('utils.feature');
              return config;
            }
            """);

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Callonce Config Test
            Scenario: Use function from callonce-loaded feature
            * def result = hello('world')
            * match result == 'hello world'
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "karate.callonce() in config should work");
    }

    @Test
    void testEnvConfigFromWorkingDirectory() throws Exception {
        // Create base karate-config.js in working directory
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                env: karate.env,
                baseUrl: 'http://default.example.com'
              };
            }
            """);

        // Create env-specific config in working directory
        Path devConfig = tempDir.resolve("karate-config-staging.js");
        Files.writeString(devConfig, """
            function fn() {
              return {
                baseUrl: 'http://staging.example.com'
              };
            }
            """);

        // Create a feature file
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Env Config from Working Dir
            Scenario: Both configs loaded from working directory
            * match env == 'staging'
            * match baseUrl == 'http://staging.example.com'
            """);

        // Run with env=staging, no explicit configPath
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .karateEnv("staging")
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "Base and env configs should load from working directory");
    }

    // ========== karate-base.js Tests ==========

    @Test
    void testKarateBaseJsDefinesSharedFunctions() throws Exception {
        // Create karate-base.js that defines a shared function
        Path baseFile = tempDir.resolve("karate-base.js");
        Files.writeString(baseFile, """
            function fn() {
              return {
                functionFromKarateBase: function() { return 'fromKarateBase' }
              };
            }
            """);

        // Create karate-config.js that uses the function from karate-base.js
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                variableFromKarateBase: functionFromKarateBase()
              };
            }
            """);

        // Create a feature file that verifies the variable
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: karate-base.js Test
            Scenario: Variable from karate-base.js function
            * match variableFromKarateBase == 'fromKarateBase'
            """);

        // Run with configs from working directory
        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "karate-base.js function should be available to karate-config.js");
    }

    @Test
    void testKarateBaseJsWithEnvConfig() throws Exception {
        // Create karate-base.js with shared utility function
        Path baseFile = tempDir.resolve("karate-base.js");
        Files.writeString(baseFile, """
            function fn() {
              return {
                getEnvUrl: function(env) {
                  if (env == 'dev') return 'http://dev.example.com';
                  if (env == 'staging') return 'http://staging.example.com';
                  return 'http://localhost:8080';
                }
              };
            }
            """);

        // Create karate-config.js that uses the shared function
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return {
                baseUrl: getEnvUrl(karate.env)
              };
            }
            """);

        // Create a feature file
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: karate-base.js with Env
            Scenario: Shared function works with env
            * match baseUrl == 'http://dev.example.com'
            """);

        // Run with env=dev
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .karateEnv("dev")
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "karate-base.js functions should work with env config");
    }

    // ========== karate.configure() from JavaScript Tests ==========

    @Test
    void testConfigureFromJs() throws Exception {
        // Test that karate.configure() can be called from JavaScript (eval block)
        // This is the V1 pattern for programmatic configuration
        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Configure from JS
            Scenario: Call karate.configure from eval block
            * eval
            \"\"\"
            var config = {
              proxy: { uri: 'http://my-proxy.com:3128', nonProxyHosts: ['my-api.com'] }
            };
            karate.configure('proxy', config.proxy);
            \"\"\"
            """);

        SuiteResult result = runTestSuite(tempDir, featureFile.toString());

        assertTrue(result.isPassed(), "karate.configure() should work from JavaScript eval block");
    }

    // ========== Debug Support Tests ==========

    @Test
    void testConfigResourcePathForDebugging() throws Exception {
        // Test that config JS resources preserve their path for debugging
        Path configFile = tempDir.resolve("karate-config.js");
        Files.writeString(configFile, """
            function fn() {
              return { foo: 'bar' };
            }
            """);

        Path featureFile = tempDir.resolve("test.feature");
        Files.writeString(featureFile, """
            Feature: Debug Path Test
            Scenario: Simple test
            * match foo == 'bar'
            """);

        // Collect debug points
        var debugPoints = new java.util.ArrayList<String>();

        // Create a mock interceptor to capture debug point source paths
        io.karatelabs.js.RunInterceptor<Object> interceptor = new io.karatelabs.js.RunInterceptor<>() {
            @Override
            public Action beforeExecute(Object point) {
                return Action.PROCEED;
            }

            @Override
            public void afterExecute(Object point, Object result, Throwable error) {
            }

            @Override
            public Action waitForResume() {
                return Action.PROCEED;
            }
        };

        io.karatelabs.js.DebugPointFactory<Object> factory = (type, line, sourcePath, source, jsContext) -> {
            debugPoints.add("type=" + type + ", line=" + line + ", sourcePath=" + sourcePath);
            return new Object(); // dummy point
        };

        // Run with debug support
        SuiteResult result = Runner.builder()
                .path(featureFile.toString())
                .workingDir(tempDir)
                .debugSupport(interceptor, factory)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // Verify that at least one debug point has the config file path
        boolean foundConfigPath = debugPoints.stream()
                .anyMatch(dp -> dp.contains("karate-config.js"));
        assertTrue(foundConfigPath, "Debug points should include karate-config.js source path. Points: " + debugPoints);
    }

}

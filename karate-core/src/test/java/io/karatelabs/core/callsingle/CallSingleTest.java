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
package io.karatelabs.core.callsingle;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for karate.callSingle() - executes a file once per Suite with caching.
 * Verifies:
 * - Single execution even with parallel threads
 * - Thread-safe caching with proper locking
 * - Exception caching
 */
class CallSingleTest {

    @TempDir
    Path tempDir;

    @Test
    void testCallSingleBasic() throws Exception {
        // Create a feature that callSingle will execute
        Path singleFeature = tempDir.resolve("single.feature");
        Files.writeString(singleFeature, """
            Feature: Single
            Scenario:
            * def value = 42
            """);

        // Create caller feature that uses callSingle
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle
            * def result = karate.callSingle('single.feature')
            * match result.value == 42
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle basic should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleWithJs() throws Exception {
        // Create a JS file that returns a value
        Path jsFile = tempDir.resolve("setup.js");
        Files.writeString(jsFile, """
            function() {
                return { token: 'abc123', timestamp: Date.now() };
            }
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle with JS
            * def auth = karate.callSingle('setup.js')
            * match auth.token == 'abc123'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with JS should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleCachesResult() throws Exception {
        // Create a feature that uses a timestamp to prove caching
        Path singleFeature = tempDir.resolve("single.feature");
        Files.writeString(singleFeature, """
            Feature: Single
            Scenario:
            * def timestamp = java.lang.System.currentTimeMillis()
            """);

        // Create caller feature with two scenarios that both call callSingle
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: First call
            * def result1 = karate.callSingle('single.feature')
            * def ts1 = result1.timestamp

            Scenario: Second call (should get cached)
            * def result2 = karate.callSingle('single.feature')
            * def ts2 = result2.timestamp
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle caching should work: " + getFailureMessage(result));
        assertEquals(2, result.getScenarioCount());
    }

    @Test
    void testCallSingleParallelExecution() throws Exception {
        // Create a feature that simulates slow initialization
        // Uses a static counter to track how many times it's actually called
        Path counterFeature = tempDir.resolve("slow-init.feature");
        Files.writeString(counterFeature, """
            Feature: Slow Init
            Scenario:
            # Simulate slow operation
            * def sleepResult = java.lang.Thread.sleep(100)
            * def initValue = 'initialized-' + java.lang.System.currentTimeMillis()
            """);

        // Create a feature with multiple scenarios that all use callSingle
        Path parallelFeature = tempDir.resolve("parallel.feature");
        Files.writeString(parallelFeature, """
            Feature: Parallel Test

            Scenario: Thread 1
            * def result = karate.callSingle('slow-init.feature')
            * match result.initValue contains 'initialized-'

            Scenario: Thread 2
            * def result = karate.callSingle('slow-init.feature')
            * match result.initValue contains 'initialized-'

            Scenario: Thread 3
            * def result = karate.callSingle('slow-init.feature')
            * match result.initValue contains 'initialized-'

            Scenario: Thread 4
            * def result = karate.callSingle('slow-init.feature')
            * match result.initValue contains 'initialized-'
            """);

        // Run with 4 parallel threads
        long startTime = System.currentTimeMillis();
        SuiteResult result = Runner.builder()
                .path(parallelFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(4);
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(result.isPassed(), "Parallel callSingle should work: " + getFailureMessage(result));
        assertEquals(4, result.getScenarioCount());

        // If callSingle is working correctly, total time should be ~100ms (one execution)
        // not 400ms (4 sequential executions)
        // Allow some buffer for thread scheduling
        assertTrue(elapsed < 300, "Expected parallel execution in ~100ms, but took " + elapsed + "ms");
    }

    @Test
    void testCallSingleExceptionCaching() throws Exception {
        // Create a feature that throws an error
        Path failFeature = tempDir.resolve("fail.feature");
        Files.writeString(failFeature, """
            Feature: Fail
            Scenario:
            * def x = karate.fail('intentional error')
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: First call fails
            * def result = karate.callSingle('fail.feature')
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isFailed(), "callSingle with failing feature should fail");
    }

    @Test
    void testCallSingleWithArgument() throws Exception {
        // Create a feature that uses an argument
        Path singleFeature = tempDir.resolve("single.feature");
        Files.writeString(singleFeature, """
            Feature: Single with Arg
            Scenario:
            * def result = 'Hello ' + name
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle with arg
            * def response = karate.callSingle('single.feature', { name: 'World' })
            * match response.result == 'Hello World'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with argument should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleMultipleDifferentPaths() throws Exception {
        // Create two different features
        Path feature1 = tempDir.resolve("first.feature");
        Files.writeString(feature1, """
            Feature: First
            Scenario:
            * def value = 'first'
            """);

        Path feature2 = tempDir.resolve("second.feature");
        Files.writeString(feature2, """
            Feature: Second
            Scenario:
            * def value = 'second'
            """);

        // Create caller that uses callSingle for both
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle for multiple files
            * def result1 = karate.callSingle('first.feature')
            * def result2 = karate.callSingle('second.feature')
            * match result1.value == 'first'
            * match result2.value == 'second'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with multiple paths should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleCacheKeyDifferentiation() throws Exception {
        // Create a feature that uses the passed argument
        Path tokenFeature = tempDir.resolve("get-token.feature");
        Files.writeString(tokenFeature, """
            Feature: Get Token
            Scenario:
            * def token = 'token-for-' + username
            """);

        // Create caller that uses ?suffix for cache key differentiation
        // Same feature file, different args, different cache keys
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle with cache key suffix
            * def adminResult = karate.callSingle('get-token.feature?admin', { username: 'admin' })
            * def userResult = karate.callSingle('get-token.feature?user', { username: 'user' })
            * match adminResult.token == 'token-for-admin'
            * match userResult.token == 'token-for-user'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with ?suffix cache keys should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleWithoutSuffixSharesCache() throws Exception {
        // Create a feature that uses the passed argument
        Path tokenFeature = tempDir.resolve("get-token.feature");
        Files.writeString(tokenFeature, """
            Feature: Get Token
            Scenario:
            * def token = 'token-for-' + username
            """);

        // Create caller WITHOUT ?suffix - both calls share same cache
        // Second call returns cached result from first call (admin's token)
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Without suffix, cache is shared
            * def adminResult = karate.callSingle('get-token.feature', { username: 'admin' })
            * def userResult = karate.callSingle('get-token.feature', { username: 'user' })
            # Both return admin's token because cache is shared
            * match adminResult.token == 'token-for-admin'
            * match userResult.token == 'token-for-admin'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle without suffix should share cache: " + getFailureMessage(result));
    }

    // ========== Tests for callSingle from karate-config.js ==========

    @Test
    void testCallSingleFromConfig() throws Exception {
        // Create a setup feature that callSingle will execute (simulates auth/bootstrap)
        Path setupFeature = tempDir.resolve("bootstrap.feature");
        Files.writeString(setupFeature, """
            Feature: Bootstrap
            Scenario:
            * def authToken = 'token-' + java.lang.System.currentTimeMillis()
            * def baseUrl = 'http://localhost:8080'
            """);

        // Create karate-config.js that uses callSingle to bootstrap
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
            function fn() {
                var config = {};
                var bootstrap = karate.callSingle('bootstrap.feature');
                config.authToken = bootstrap.authToken;
                config.baseUrl = bootstrap.baseUrl;
                return config;
            }
            """);

        // Create a feature that uses the config values
        Path testFeature = tempDir.resolve("test.feature");
        Files.writeString(testFeature, """
            Feature: Test using config
            Scenario: Use bootstrapped values
            * match authToken contains 'token-'
            * match baseUrl == 'http://localhost:8080'
            """);

        SuiteResult result = Runner.builder()
                .path(testFeature.toString())
                .workingDir(tempDir)
                .configDir(configJs.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle from config should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleFromConfigWithJsFile() throws Exception {
        // Create a JS file that returns auth config (common pattern)
        Path authJs = tempDir.resolve("auth.js");
        Files.writeString(authJs, """
            function() {
                // Simulate expensive auth call that should only happen once
                karate.log('Performing authentication...');
                return {
                    token: 'bearer-abc123',
                    userId: 'user-42',
                    timestamp: new Date().toISOString()
                };
            }
            """);

        // Create karate-config.js that uses callSingle for auth
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
            function fn() {
                var auth = karate.callSingle('auth.js');
                return {
                    authToken: auth.token,
                    userId: auth.userId
                };
            }
            """);

        // Create a feature that uses the auth values
        Path testFeature = tempDir.resolve("test.feature");
        Files.writeString(testFeature, """
            Feature: Test with JS-based auth
            Scenario: Use auth from callSingle
            * match authToken == 'bearer-abc123'
            * match userId == 'user-42'
            """);

        SuiteResult result = Runner.builder()
                .path(testFeature.toString())
                .workingDir(tempDir)
                .configDir(configJs.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle from config with JS file should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleFromConfigParallelScenarios() throws Exception {
        // Reset the counter before test (reuse existing counter class)
        io.karatelabs.core.parallel.CallOnceCounter.reset();

        // Create a setup feature that increments the counter
        Path setupFeature = tempDir.resolve("counting-setup.feature");
        Files.writeString(setupFeature, """
            Feature: Counting Setup
            Scenario:
            * def Counter = Java.type('io.karatelabs.core.parallel.CallOnceCounter')
            * def count = Counter.incrementAndGet()
            * def sharedValue = 'initialized-once'
            """);

        // Create karate-config.js that uses callSingle
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
            function fn() {
                var setup = karate.callSingle('counting-setup.feature');
                return {
                    sharedValue: setup.sharedValue
                };
            }
            """);

        // Create a feature with multiple scenarios that all use the config
        Path testFeature = tempDir.resolve("parallel-test.feature");
        Files.writeString(testFeature, """
            Feature: Parallel test with shared config

            Scenario: Thread 1
            * match sharedValue == 'initialized-once'

            Scenario: Thread 2
            * match sharedValue == 'initialized-once'

            Scenario: Thread 3
            * match sharedValue == 'initialized-once'

            Scenario: Thread 4
            * match sharedValue == 'initialized-once'
            """);

        // Run with 4 parallel threads
        SuiteResult result = Runner.builder()
                .path(testFeature.toString())
                .workingDir(tempDir)
                .configDir(configJs.toString())
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(4);

        assertTrue(result.isPassed(), "Parallel scenarios with config callSingle should work: " + getFailureMessage(result));
        assertEquals(4, result.getScenarioCount());

        // Verify callSingle only executed once (counter incremented exactly once)
        int count = io.karatelabs.core.parallel.CallOnceCounter.get();
        assertEquals(1, count, "callSingle should execute exactly once across all parallel scenarios, but executed " + count + " times");
    }

    // ========== Tests for callSingle with tag selector ==========

    @Test
    void testCallSingleWithTagSelector() throws Exception {
        // Create a feature with multiple scenarios, each with a different tag
        Path taggedFeature = tempDir.resolve("tagged.feature");
        Files.writeString(taggedFeature, """
            Feature: Tagged Scenarios

            @admin
            Scenario: Admin setup
            * def role = 'admin'
            * def token = 'admin-token-123'

            @user
            Scenario: User setup
            * def role = 'user'
            * def token = 'user-token-456'

            @guest
            Scenario: Guest setup
            * def role = 'guest'
            * def token = 'guest-token-789'
            """);

        // Create caller that uses callSingle with tag selector
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller with tag selector
            Scenario: Use callSingle with @user tag
            * def result = karate.callSingle('tagged.feature@user')
            * match result.role == 'user'
            * match result.token == 'user-token-456'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with tag selector should work: " + getFailureMessage(result));
    }

    @Test
    void testCallSingleWithTagSelectorCaching() throws Exception {
        // Test that callSingle with different tag selectors use different cache keys
        Path taggedFeature = tempDir.resolve("multi-tagged.feature");
        Files.writeString(taggedFeature, """
            Feature: Multi Tagged

            @dev
            Scenario: Dev setup
            * def env = 'development'
            * def timestamp = java.lang.System.currentTimeMillis()

            @prod
            Scenario: Prod setup
            * def env = 'production'
            * def timestamp = java.lang.System.currentTimeMillis()
            """);

        // Create caller that uses callSingle with different tags
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Use callSingle with different tags
            * def devResult = karate.callSingle('multi-tagged.feature@dev')
            * def prodResult = karate.callSingle('multi-tagged.feature@prod')
            * match devResult.env == 'development'
            * match prodResult.env == 'production'
            """);

        SuiteResult result = Runner.builder()
                .path(callerFeature.toString())
                .workingDir(tempDir)
                .outputConsoleSummary(false)
                .outputHtmlReport(false)
                .backupOutputDir(false)
                .parallel(1);

        assertTrue(result.isPassed(), "callSingle with different tags should return different results: " + getFailureMessage(result));
    }

    // ========== Tests for callSingleCache (disk caching) ==========

    @Test
    void testCallSingleCacheToDisk() {
        // Clean up any existing cache file first
        File cacheDir = new File("target/callsingle-cache");
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }

        SuiteResult result = Runner.path("classpath:io/karatelabs/core/callsingle/cache/cache-test.feature")
                .configDir("classpath:io/karatelabs/core/callsingle/cache")
                .outputConsoleSummary(false)
                .parallel(1);

        assertEquals(0, result.getScenarioFailedCount(), String.join("\n", result.getErrors()));

        // Verify cache file was created
        File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".txt"));
        assertNotNull(cacheFiles, "Cache directory should contain files");
        assertTrue(cacheFiles.length > 0, "Should have at least one cache file");
    }

    private String getFailureMessage(SuiteResult result) {
        if (result.isPassed()) return "none";
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "unknown";
    }

}

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
 * Tests for feature resolution execution.
 */
class StepResolveTest {

    @TempDir
    Path tempDir;

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

    @Test
    void testCallFeatureWithResult() throws Exception {
        // Create sample features
        Files.writeString(tempDir.resolve("a.feature"), """
            Feature:
            @env=dev,test @high @fast
            Scenario: explicit lower env only
            * def result = 1
            @envnot=prod @fast
            Scenario: implicit lower env only
            * def result = 2
            """);

        Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/b.feature"), """
            Feature:
            @smoke
            Scenario: smoke test
            * def result = 3
            Scenario: no tags
            * def result = 4
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller Feature
            Scenario: resolve scenarios
            * def testEnv = karate.resolveScenarios({ env: 'test' })
            * match testEnv contains ['a.feature:3', 'a.feature:6']

            * def prodEnv = karate.resolveScenarios({ env: 'prod' })
            * match prodEnv !contains 'a.feature:6'

            * def onlySmoke = karate.resolveScenarios({ tags: '@smoke' })
            * match onlySmoke contains only 'sub/b.feature:3'

            * def withoutSmoke = karate.resolveScenarios({ tags: '~@smoke' })
            * match withoutSmoke !contains 'sub/b.feature:3'

            * def multiTag = karate.resolveScenarios({ tags: ['@high', '@fast'], env: 'dev' })
            * match multiTag contains only 'a.feature:3'

            * def withPath = karate.resolveScenarios({ path: 'sub', tags: '~@smoke' })
            * match withPath contains only 'sub/b.feature:5'
            
            * def multiPath = karate.resolveScenarios({ path: ['a.feature', 'sub/b.feature'] })
            * match multiPath contains ['a.feature:6', 'sub/b.feature:3']
            
            * def withWorkingDir = karate.resolveScenarios({ workingDir: '${root}', tags: '@smoke' })
            * match withWorkingDir contains only 'sub/b.feature:3'
            """.replace("${root}", tempDir.toString()));

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());
        assertTrue(result.isPassed(), "Suite should pass: " + getFailureMessage(result));
    }

}

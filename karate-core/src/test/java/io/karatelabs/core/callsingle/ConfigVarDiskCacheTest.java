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

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduction for issue #2933: when {@code karate.set(karate.callSingle(...))} is used to bulk-set
 * variables and the callSingle result is served from the on-disk cache (a subsequent JVM run within
 * the TTL), config-level {@code Java.type(...)} references defined in karate-config.js were wiped
 * from scope — the called feature's full binding set (which inherits the config ref) was serialized
 * to disk, read back as a broken value, and spread over the live ref by {@code karate.set}.
 * <p>
 * Caching only the callee's delta keeps inherited config refs out of the cache entirely, so they
 * survive a warm-cache run. Two suite runs share one cache dir: the first writes the disk cache, the
 * second (a fresh Suite, empty memory cache) reads it back.
 */
class ConfigVarDiskCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void configJavaTypeSurvivesWarmDiskCache() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        Files.writeString(tempDir.resolve("karate-config.js"), """
                function fn() {
                  var config = {};
                  // a config-level Java.type() reference — the thing that gets wiped on a warm run
                  config.idUtils = Java.type('java.util.UUID');
                  karate.configure('callSingleCache', { minutes: 5, dir: '%s' });
                  return config;
                }
                """.formatted(cacheDir.toAbsolutePath().toString().replace("\\", "/")));
        Files.writeString(tempDir.resolve("constants.feature"), """
                @ignore
                Feature: constants (callSingle target)
                Scenario:
                * def serviceName = 'TestService'
                * def baseUrl = 'https://example.com'
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: config Java.type survives karate.set(callSingle())

                  Background:
                    * karate.set( karate.callSingle('constants.feature') )
                    # idUtils comes from karate-config.js — must still be callable after the set
                    * def generatedId = idUtils.randomUUID() + ''

                  Scenario: config-level Java.type ref survives
                    * match serviceName == 'TestService'
                    * match generatedId == '#string'
                """);

        // First run: cold cache — writes the disk cache.
        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));

        // Second run: a fresh Suite with an empty memory cache hits the warm disk cache.
        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(),
                "warm disk-cache run must keep config-level Java.type() callable: " + failure(warm));
    }

    private SuiteResult runOnce(Path main) {
        return Runner.path(main.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputHtmlReport(false)
                .outputConsoleSummary(false)
                .parallel(1);
    }

    private static String failure(SuiteResult result) {
        StringBuilder sb = new StringBuilder();
        result.getFeatureResults().forEach(fr -> fr.getScenarioResults().forEach(sr -> {
            if (sr.isFailed()) {
                sb.append(sr.getScenario().getName()).append(" -> ").append(sr.getFailureMessage());
            }
        }));
        return sb.toString();
    }
}

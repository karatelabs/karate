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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The callSingle disk cache is a JSON file, so it can only hold what JSON can carry. A binary
 * variable in the result (a certificate or keystore read from a file, the raw bytes of a
 * response) is a {@code byte[]}, which has no JSON form: it used to be written out as its JVM
 * identity string and handed back to the warm run as that garbage string, with no failure to
 * point at. Such a result is now left out of the disk cache entirely — a warm run re-executes
 * the feature and gets its bytes, which is the whole point of caching a binary payload.
 */
class BinaryDiskCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void binaryResultIsNotDiskCachedAndSurvivesAWarmRun() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        Files.writeString(tempDir.resolve("creds.feature"), """
                @ignore
                Feature: creds (callSingle target)
                Scenario:
                * def certName = 'my-cert'
                * def certBytes = karate.toBytes([104, 105])
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: binary callSingle result survives the disk cache

                  Scenario: bytes stay bytes
                    * def creds = karate.callSingle('creds.feature')
                    * match creds.certName == 'my-cert'
                    * match karate.typeOf(creds.certBytes) == 'bytes'
                    * match karate.toString(creds.certBytes) == 'hi'
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        assertFalse(Files.exists(cacheDir.resolve("creds.feature.txt")),
                "a result holding bytes must not be written to the disk cache");

        // a fresh Suite has an empty memory cache, so this is the run that would have read
        // back whatever the cold run wrote
        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm run must still see real bytes: " + failure(warm));
    }

    @Test
    void plainJsonResultIsStillDiskCached() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        Files.writeString(tempDir.resolve("token.feature"), """
                @ignore
                Feature: token (callSingle target)
                Scenario:
                * def token = 'abc123'
                * def scopes = ['read', 'write']
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: a JSON-only callSingle result still uses the disk cache

                  Scenario: token comes back intact
                    * def auth = karate.callSingle('token.feature')
                    * match auth.token == 'abc123'
                    * match auth.scopes == ['read', 'write']
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        assertTrue(Files.exists(cacheDir.resolve("token.feature.txt")), "JSON-only result should be disk cached");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm disk-cache run should pass: " + failure(warm));
    }

    @Test
    void selfReferentialResultIsNotDiskCached() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        Files.writeString(tempDir.resolve("looped.feature"), """
                @ignore
                Feature: a result that points back at itself
                Scenario:
                * def node = { name: 'a' }
                * def other = { name: 'b' }
                * eval node.next = other
                * eval other.next = node
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: a cyclic callSingle result is not disk cached

                  Scenario: the loop is still a loop
                    * def result = karate.callSingle('looped.feature')
                    * match result.node.next.name == 'b'
                    * match result.node.next.next.name == 'a'
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        // the writer renders a back-edge as the string "[Circular]", which reads back as a
        // string value rather than as the loop it stands for
        assertFalse(Files.exists(cacheDir.resolve("looped.feature.txt")),
                "a result with a cycle must not be written to the disk cache");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm run must still see the loop: " + failure(warm));
    }

    @Test
    void nonJsonValueNestedInsideAListIsFound() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        // the only value that cannot round-trip is two levels down, so this is the walk, not
        // the top-level type check, doing the work
        Files.writeString(tempDir.resolve("nested.feature"), """
                @ignore
                Feature: a binary value buried in the result
                Scenario:
                * def attachments = [{ name: 'a.bin', data: '#(karate.toBytes([1, 2]))' }]
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: a nested binary value keeps the result out of the disk cache

                  Scenario: the bytes stay bytes
                    * def result = karate.callSingle('nested.feature')
                    * match karate.typeOf(result.attachments[0].data) == 'bytes'
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        assertFalse(Files.exists(cacheDir.resolve("nested.feature.txt")),
                "a binary value nested in the result must not be written to the disk cache");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm run must still see real bytes: " + failure(warm));
    }

    @Test
    void xmlResultIsNotDiskCached() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        // an XML variable writes out as its toString and would come back a string, so a
        // set-by-xpath on the warm run's value would fail
        Files.writeString(tempDir.resolve("soap.feature"), """
                @ignore
                Feature: an XML result
                Scenario:
                * xml envelope = '<root><a>1</a></root>'
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: an XML callSingle result is not disk cached

                  Scenario: the XML is still XML
                    * def result = karate.callSingle('soap.feature')
                    * def envelope = result.envelope
                    * set envelope /root/a = '2'
                    * match envelope == <root><a>2</a></root>
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        assertFalse(Files.exists(cacheDir.resolve("soap.feature.txt")),
                "an XML result must not be written to the disk cache");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm run must still see XML: " + failure(warm));
    }

    @Test
    void nonFiniteNumberResultIsNotDiskCached() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        // NaN and the infinities have no JSON form — the writer emits them bare, which is not
        // valid JSON, so the warm run reads back a string or fails to parse the file at all
        Files.writeString(tempDir.resolve("ratio.feature"), """
                @ignore
                Feature: a result holding NaN
                Scenario:
                * def ratio = 0 / 0
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: a NaN callSingle result is not disk cached

                  Scenario: NaN is still a number
                    * def result = karate.callSingle('ratio.feature')
                    * match karate.typeOf(result.ratio) == 'number'
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        assertFalse(Files.exists(cacheDir.resolve("ratio.feature.txt")),
                "a result holding NaN must not be written to the disk cache");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm run must still see a number: " + failure(warm));
    }

    @Test
    void resultWithAFunctionIsStillDiskCached() throws Exception {
        Path cacheDir = tempDir.resolve("cs-cache");
        writeConfig(cacheDir);
        Files.writeString(tempDir.resolve("helpers.feature"), """
                @ignore
                Feature: data plus a helper function
                Scenario:
                * def base = 'https://example.com'
                * def greet = function(x){ return 'hi ' + x }
                """);
        Path main = tempDir.resolve("main.feature");
        Files.writeString(main, """
                Feature: functions do not block the disk cache

                  Scenario: the data still comes back
                    * def result = karate.callSingle('helpers.feature')
                    * match result.base == 'https://example.com'
                """);

        SuiteResult cold = runOnce(main);
        assertTrue(cold.isPassed(), "cold run should pass: " + failure(cold));
        // JSON has no functions and dropping them is long-standing behavior, so a function
        // alongside plain data must not cost the whole result its disk cache
        assertTrue(Files.exists(cacheDir.resolve("helpers.feature.txt")),
                "a function in the result should not block disk caching");

        SuiteResult warm = runOnce(main);
        assertTrue(warm.isPassed(), "warm disk-cache run should pass: " + failure(warm));
    }

    private void writeConfig(Path cacheDir) throws Exception {
        Files.writeString(tempDir.resolve("karate-config.js"), """
                function fn() {
                  karate.configure('callSingleCache', { minutes: 5, dir: '%s' });
                  return {};
                }
                """.formatted(cacheDir.toAbsolutePath().toString().replace("\\", "/")));
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

/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * A step that uses an ext global the project never declared fails with the bare JS
 * {@code <name> is not defined} — which says nothing about the {@code boot.ext(...)} line that would
 * bind it. {@link ExtHint} names that line when (and only when) the ext is really on the classpath.
 */
class MissingExtGlobalHintTest {

    @TempDir
    Path tempDir;

    private String failureOf(String featureBody) {
        Path feature = tempDir.resolve("missing.feature");
        try {
            Files.writeString(feature, featureBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isFailed(), "the step must fail for there to be a hint");
        return result.getFeatureResults().get(0).getScenarioResults().get(0).getFailureMessage();
    }

    @Test
    void undeclaredExtGlobalNamesItsBootLine() {
        // no karate-boot.js at all — `dummy` resolves to the test-only io.karatelabs.ext.dummy.DummyExt
        String message = failureOf("""
                Feature: undeclared ext global

                Scenario: uses dummy without booting it
                * def x = dummy.echo('hi')
                """);
        assertTrue(message.contains("dummy is not defined"), message);
        assertTrue(message.contains("boot.ext('dummy')"), message);
        assertTrue(message.contains("karate-boot.js"), message);
    }

    @Test
    void anOrdinaryTypoStaysAnOrdinaryTypo() {
        // nothing on the classpath is named `nosuchext`/`nosuchexts` — say nothing rather than guess
        String message = failureOf("""
                Feature: plain typo

                Scenario: uses a name no ext binds
                * def x = nosuchext.echo('hi')
                """);
        assertTrue(message.contains("nosuchext is not defined"), message);
        assertFalse(message.contains("boot.ext("), message);
    }

    @Test
    void aDeclaredExtIsNotBlamedForAnUnrelatedFailure() throws Exception {
        // `dummy` IS bound here, so the ReferenceError is about something else entirely
        Files.writeString(tempDir.resolve("karate-boot.js"), "boot.ext('dummy');\n");
        String message = failureOf("""
                Feature: booted ext, unrelated failure

                Scenario: a genuinely undefined var
                * def x = dummy.echo(somethingElse)
                """);
        assertTrue(message.contains("somethingElse is not defined"), message);
        assertFalse(message.contains("boot.ext("), message);
    }

    @Test
    void aSingularGlobalResolvesToItsPluralExt() {
        // the global an ext binds is routinely the singular of the ext's own name (Rule ← `rules`),
        // so the probe has to try both inflections — this is the shape the friction was reported in
        String message = failureOf("""
                Feature: singular global, plural ext

                Scenario: uses Gadget without booting gadgets
                * def x = Gadget.echo('hi')
                """);
        assertTrue(message.contains("Gadget is not defined"), message);
        assertTrue(message.contains("boot.ext('gadgets')"), message);
    }
}

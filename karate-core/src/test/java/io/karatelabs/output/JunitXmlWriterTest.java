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
package io.karatelabs.output;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JunitXmlWriterTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        Console.setColorsEnabled(false);
    }

    @Test
    void testJunitXmlGeneration() throws Exception {
        Path feature = tempDir.resolve("test.feature");
        Files.writeString(feature, """
            Feature: JUnit XML Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1

            Scenario: Another passing
            * def b = 2
            * match b == 2
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed());

        // Verify per-feature XML file was created (named after packageQualifiedName)
        Path xmlPath = reportDir.resolve("junit-xml/test.xml");
        assertTrue(Files.exists(xmlPath), "JUnit XML file should exist");

        String xml = Files.readString(xmlPath);

        // Verify XML structure (per-feature, single <testsuite> root)
        assertTrue(xml.contains("<testsuite"));
        assertTrue(xml.contains("name=\"JUnit XML Test\""));
        assertTrue(xml.contains("tests=\"2\""));
        assertTrue(xml.contains("failures=\"0\""));
        assertTrue(xml.contains("<testcase"));
        assertTrue(xml.contains("name=\"Passing scenario\""));
        assertTrue(xml.contains("name=\"Another passing\""));
        assertTrue(xml.contains("</testsuite>"));
    }

    @Test
    void testJunitXmlWithFailures() throws Exception {
        Path feature = tempDir.resolve("failing.feature");
        Files.writeString(feature, """
            Feature: Failing Test

            Scenario: Passing scenario
            * def a = 1
            * match a == 1

            Scenario: Failing scenario
            * def b = 2
            * match b == 999
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isFailed());

        Path xmlPath = reportDir.resolve("junit-xml/failing.xml");
        assertTrue(Files.exists(xmlPath));

        String xml = Files.readString(xmlPath);

        // Verify failure counts
        assertTrue(xml.contains("tests=\"2\""));
        assertTrue(xml.contains("failures=\"1\""));

        // Verify failure element
        assertTrue(xml.contains("<failure"));
        assertTrue(xml.contains("message="));
        assertTrue(xml.contains("</failure>"));
    }

    @Test
    void testJunitXmlEscapesSpecialCharacters() throws Exception {
        Path feature = tempDir.resolve("special.feature");
        Files.writeString(feature, """
            Feature: Test with <special> & "characters"

            Scenario: Test with 'quotes'
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path xmlPath = reportDir.resolve("junit-xml/special.xml");
        String xml = Files.readString(xmlPath);

        // Verify special characters are escaped
        assertTrue(xml.contains("&lt;special&gt;"));
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&quot;characters&quot;"));
        assertTrue(xml.contains("&apos;quotes&apos;"));
    }

    @Test
    void testJunitXmlNotGeneratedByDefault() throws Exception {
        Path feature = tempDir.resolve("nojunit.feature");
        Files.writeString(feature, """
            Feature: No JUnit
            Scenario: Test
            * def a = 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputConsoleSummary(false)
                .parallel(1);

        // JUnit XML should NOT exist by default (per-feature file)
        assertFalse(Files.exists(reportDir.resolve("junit-xml/nojunit.xml")),
                "JUnit XML file should not exist when outputJunitXml is false");
    }

    @Test
    void testJunitXmlWithTags() throws Exception {
        Path feature = tempDir.resolve("tagged.feature");
        Files.writeString(feature, """
            @feature-tag
            Feature: Tagged Feature

            @smoke @api
            Scenario: Tagged scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path xmlPath = reportDir.resolve("junit-xml/tagged.xml");
        String xml = Files.readString(xmlPath);

        // Plain tags are aggregated into a single <property name="tags" value="@a,@b"/>
        // (Xray-style convention — see issue #2818). Feature-level tags are inherited.
        assertTrue(xml.contains("<properties>"));
        assertTrue(xml.contains("<property name=\"tags\" value=\"@feature-tag,@smoke,@api\"/>"));
        assertTrue(xml.contains("</properties>"));
    }

    @Test
    void testJunitXmlKeyValueTagsAsNamedProperties() throws Exception {
        // regression for https://github.com/karatelabs/karate/issues/2818
        // Xray (and other CI tools) key on the property *name*, so `@key=v1,v2` must map to
        // <property name="key" value="v1,v2"/> instead of the generic `name="tag"` form.
        Path feature = tempDir.resolve("xray.feature");
        Files.writeString(feature, """
            Feature: Xray Compatible

            @smoke
            @requirements=REQ-1,REQ-2
            @owner=alice
            Scenario: Tagged scenario
            * def a = 1
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path xmlPath = reportDir.resolve("junit-xml/xray.xml");
        String xml = Files.readString(xmlPath);

        assertTrue(xml.contains("<property name=\"requirements\" value=\"REQ-1,REQ-2\"/>"),
                "key=value tag should map to a named property (v1 parity for Xray)");
        assertTrue(xml.contains("<property name=\"owner\" value=\"alice\"/>"),
                "single-value key=value tag should also map to a named property");
        assertTrue(xml.contains("<property name=\"tags\" value=\"@smoke\"/>"),
                "plain tags should aggregate under name=\"tags\" (Xray convention)");
    }

    @Test
    void testJunitXmlStripsAnsiEscapeCodes() throws Exception {
        // regression for https://github.com/karatelabs/karate/issues/2799
        // step logs can contain raw ANSI codes (from HttpLogger) which break CI parsers
        // build ANSI at runtime via String.fromCharCode so the feature source has no ESC bytes
        // (if ESC were in the step text itself it'd leak into the XML regardless of log stripping)
        Path feature = tempDir.resolve("ansi.feature");
        Files.writeString(feature, """
            Feature: ANSI Test

            Scenario: With ANSI
            * def esc = String.fromCharCode(27)
            * karate.log(esc + '[31mRED-LOG' + esc + '[0m ok')
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path xmlPath = reportDir.resolve("junit-xml/ansi.xml");
        String xml = Files.readString(xmlPath);

        String esc = String.valueOf((char) 27);
        assertTrue(xml.contains("RED-LOG"), "log content should be retained");
        assertFalse(xml.contains(esc), "XML should not contain ANSI escape codes");
    }

    @Test
    void testJunitXmlWithStepLogs() throws Exception {
        Path feature = tempDir.resolve("logs.feature");
        Files.writeString(feature, """
            Feature: Step Logs

            Scenario: With print
            * def a = 1
            * print 'hello world'
            * match a == 1
            """);

        Path reportDir = tempDir.resolve("reports");

        Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        Path xmlPath = reportDir.resolve("junit-xml/logs.xml");
        String xml = Files.readString(xmlPath);

        // Verify step logs in system-out
        assertTrue(xml.contains("<system-out>"));
        assertTrue(xml.contains("a = 1"), "Should contain step text");
        // print step logs appear as actual content, not keyword
        assertTrue(xml.contains("hello world"), "Should contain print output");
        assertTrue(xml.contains("</system-out>"), "XML should contain </system-out>");
    }

    @Test
    void testJunitXmlWithSyntheticStep() throws Exception {
        // regression for https://github.com/karatelabs/karate/issues/2827
        // synthetic step results (null Step) — produced by @fail tag, lifecycle hooks,
        // and scenario init failures — used to NPE in appendStepLogs and silently drop
        // the entire feature's XML file.
        Path feature = tempDir.resolve("synthetic.feature");
        Files.writeString(feature, """
            Feature: Synthetic Step

            @fail
            Scenario: failing scenario expected to fail
            * def a = 1
            * match a == 999
            """);

        Path reportDir = tempDir.resolve("reports");

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reportDir)
                .outputJunitXml(true)
                .outputConsoleSummary(false)
                .parallel(1);

        // @fail flips: original FAIL becomes PASS at scenario level
        assertTrue(result.isPassed());

        Path xmlPath = reportDir.resolve("junit-xml/synthetic.xml");
        assertTrue(Files.exists(xmlPath), "JUnit XML must still be written when synthetic steps are present");

        String xml = Files.readString(xmlPath);
        assertTrue(xml.contains("<testsuite"));
        assertTrue(xml.contains("name=\"failing scenario expected to fail\""));
        // synthetic step renders as "* <description>" — no NPE, no orphan empty-prefix lines
        assertTrue(xml.toLowerCase().contains("expect test to fail"),
                "synthetic step description should appear in system-out");
    }

}

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
 * End-to-end Suite test for the Phase 2 ext-global path: a {@code karate-boot.js}
 * activates the test-only {@code dummy} ext (io.karatelabs.ext.dummy.DummyExt),
 * which registers a {@link io.karatelabs.js.SimpleObject} global. The feature then
 * exercises the global from scenario scope — proving the seed reaches the JS engine
 * before steps run.
 */
class DummyExtE2ETest {

    @TempDir
    Path tempDir;

    @Test
    void extGlobalVisibleInScenarioScope() throws Exception {
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            boot.ext('dummy');
            """);
        Path feature = tempDir.resolve("dummy.feature");
        Files.writeString(feature, """
            Feature: dummy ext global

            Scenario: global is in scope
            # method call — jsGet returns a JavaInvokable
            * def x = dummy.echo('hi')
            * match x == 'hi'
            # property setter then read — putMember / jsGet round-trip
            * dummy.state = 'mark'
            * match dummy.state == 'mark'
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed(), "dummy ext global should be usable from scenario scope");
        assertEquals(1, result.getScenarioPassedCount());
    }

    @Test
    void extGlobalVisibleToKarateConfig() throws Exception {
        // The seed must land before karate-config.js evaluates: config reads the
        // global and stashes a value the scenario asserts on.
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            boot.ext('dummy');
            """);
        Files.writeString(tempDir.resolve("karate-config.js"), """
            function fn() { return { fromConfig: dummy.echo('seeded') }; }
            """);
        Path feature = tempDir.resolve("cfg.feature");
        Files.writeString(feature, """
            Feature: dummy ext global in config

            Scenario: config saw the global
            * match fromConfig == 'seeded'
            """);

        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .configDir(tempDir.toString())
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .parallel(1);

        assertTrue(result.isPassed(), "ext global must be visible to karate-config.js");
    }

    @Test
    void extAssetsCopiedAndScriptSpliced() throws Exception {
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            boot.ext('dummy');
            """);
        Path feature = tempDir.resolve("assets.feature");
        Files.writeString(feature, """
            Feature: dummy ext assets

            Scenario: trivial
            * def x = 1
            """);

        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);
        assertTrue(result.isPassed());

        // Asset-copy: static/ prefix stripped → ext/dummy/ext.js, pages kept.
        assertTrue(Files.exists(reports.resolve("ext/dummy/ext.js")), "ext.js should be copied");
        assertTrue(Files.exists(reports.resolve("ext/dummy/ext.css")), "ext.css should be copied");
        assertTrue(Files.exists(reports.resolve("ext/dummy/pages/dummy.html")), "page should be copied");

        // Splice into <!-- KARATE_EXTS -->: summary page uses root-relative refs.
        String summary = Files.readString(reports.resolve("karate-summary.html"));
        assertTrue(summary.contains("<script src=\"ext/dummy/ext.js\" defer></script>"),
                "summary should reference ext.js");
        assertTrue(summary.contains("<link rel=\"stylesheet\" href=\"ext/dummy/ext.css\">"),
                "summary should reference ext.css");

        // nav.pages: the ext's page("nav.pages", "Dummy", "pages/dummy.html") renders
        // a topbar tab linking to the copied page (root-relative on the summary page).
        assertTrue(summary.contains("href=\"ext/dummy/pages/dummy.html\">Dummy</a>"),
                "summary nav should carry the ext page tab");
        assertFalse(summary.contains("<!-- KARATE_NAV -->"),
                "nav placeholder must be replaced, not left in the output");

        // Feature pages live under feature-html/, so refs carry the ../ prefix.
        Path featureHtml;
        try (var stream = Files.list(reports.resolve("feature-html"))) {
            featureHtml = stream.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
        }
        String featurePage = Files.readString(featureHtml);
        assertTrue(featurePage.contains("<script src=\"../ext/dummy/ext.js\" defer></script>"),
                "feature page should reference ext.js with ../ prefix");
        assertTrue(featurePage.contains("href=\"../ext/dummy/pages/dummy.html\">Dummy</a>"),
                "feature page nav tab should carry the ../ prefix");

        // Timeline page also renders the tab (root-relative).
        String timeline = Files.readString(reports.resolve("karate-timeline.html"));
        assertTrue(timeline.contains("href=\"ext/dummy/pages/dummy.html\">Dummy</a>"),
                "timeline nav should carry the ext page tab");
    }
}

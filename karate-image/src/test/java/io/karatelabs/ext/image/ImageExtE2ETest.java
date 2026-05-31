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
package io.karatelabs.ext.image;

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Suite test for the image ext: {@code karate-boot.js} activates
 * {@code image}, a feature drives the per-scenario global through the real
 * developer loop — establish a baseline, match against it, then detect a mismatch.
 * Exercises the per-scenario factory seeding ({@link ImageExt}), config flow from
 * boot, baseline resolution + auto-establish, the multi-part embed, and the
 * fail-on-mismatch semantics.
 */
class ImageExtE2ETest {

    // 3x3 solid blue square
    private static final String BLUE =
            "iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAD0lEQVR4AWPgSj4BQdhYAJxjCt5/e6nZAAAAAElFTkSuQmCC";
    // 3x3 blue square with one green pixel in the center → ~11.11% mismatch
    private static final String GREEN =
            "iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFUlEQVR4AWPgSj4BQSBWQAoXlAVBAJZuCmeg2F+9AAAAAElFTkSuQmCC";

    @TempDir
    Path tempDir;

    @Test
    void establishMatchAndMismatch() throws Exception {
        Path baselineDir = tempDir.resolve("baselines");
        Files.writeString(tempDir.resolve("karate-boot.js"), """
            const image = boot.ext('image');
            image.baselineDir = '%s';
            """.formatted(baselineDir.toString().replace("\\", "/")));

        Path feature = tempDir.resolve("image.feature");
        Files.writeString(feature, """
            Feature: image comparison ext

            Background:
            # blue as a Uint8Array (idiomatic binary → JsValue/getJavaValue path),
            # green as raw decoded bytes (List<Number> fallback path) — exercise both
            * def blue = new Uint8Array(java.util.Base64.getDecoder().decode('%s'))
            * def green = java.util.Base64.getDecoder().decode('%s')

            Scenario: establish, match, then inspect a mismatch without failing
            # no baseline yet → auto-established, passes
            * def established = image.compare('home', blue)
            * match established.baselineEstablished == true
            # same image vs the just-established baseline → matches
            * def same = image.compare('home', blue)
            * match same.pass == true
            # one-shot map form with a Uint8Array nested in the arg (exercises the
            # map path, where the nested latest isn't boundary-unwrapped)
            * def viaMap = image.compare({ name: 'home', latest: blue })
            * match viaMap.pass == true
            # different image, but failOnMismatch=false → returns a result to assert on
            * image.failOnMismatch = false
            * def diff = image.compare('home', green)
            * match diff.pass == false
            * match diff.isMismatch == true
            * assert diff.mismatchPercentage > 10

            Scenario: a mismatch fails the step by default
            * image.compare('other', blue)
            * image.compare('other', green)
            """.formatted(BLUE, GREEN));

        Path reports = tempDir.resolve("reports");
        SuiteResult result = Runner.path(feature.toString())
                .workingDir(tempDir)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);

        // First scenario passes; second fails on the default-fail mismatch.
        assertEquals(1, result.getScenarioPassedCount(), "establish/match/inspect scenario should pass");
        assertEquals(1, result.getScenarioFailedCount(), "default-fail mismatch scenario should fail");

        // Baselines were auto-established to disk.
        assertTrue(Files.exists(baselineDir.resolve("home.png")), "home baseline should be established");
        assertTrue(Files.exists(baselineDir.resolve("other.png")), "other baseline should be established");

        // The multi-part image-comparison embed reached the report.
        Path featureHtml;
        try (var stream = Files.list(reports.resolve("feature-html"))) {
            featureHtml = stream.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
        }
        String html = Files.readString(featureHtml);
        assertTrue(html.contains("image-comparison"), "report should carry the image-comparison embed");
        assertTrue(html.contains("ext/image/image.js"), "feature page should splice the image.js script");
        // The multi-part roles reached the wire (KARATE_DATA JSON) — baseline/latest/diff,
        // the contract the m3 lightbox reads (ImageApi emits 'latest', not 'current').
        assertTrue(html.contains("\"baseline\""), "embed should carry a baseline part");
        assertTrue(html.contains("\"latest\""), "embed should carry a latest part");
        assertTrue(html.contains("\"diff\""), "embed should carry a diff part (on the mismatch)");

        // Ext assets were copied + spliced (asset pipeline end-to-end). Files are named
        // after the ext (ext/image/image.js) so they're self-identifying in DevTools.
        Path extJs = reports.resolve("ext/image/image.js");
        assertTrue(Files.exists(extJs), "image.js should be copied");
        assertTrue(Files.exists(reports.resolve("ext/image/image.css")), "image.css should be copied");
        // The shipped image.js is the m3 lightbox, not the m2 stub: it registers the
        // 'image-comparison' renderer and builds the <dialog> lightbox. (The live DOM
        // is rendered client-side, so the dialog itself is verified by the manual smoke,
        // not a static parse — see the karate-image README.)
        String extJsSrc = Files.readString(extJs);
        assertTrue(extJsSrc.contains("registerEmbed('image-comparison'"),
                "ext.js should register the image-comparison renderer");
        assertTrue(extJsSrc.contains("ki-dialog") && extJsSrc.contains("showModal"),
                "ext.js should build the <dialog> lightbox (m3, not the m2 stub)");
    }
}

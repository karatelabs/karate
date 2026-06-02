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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@code demo/visual-demo.feature} — the developer-flow walkthrough of the image
 * ext (establish → match → regression → rebase). Two jobs at once:
 *
 * <ol>
 *   <li><b>Automated.</b> Asserts the whole flow, so the API + lightbox embed are
 *       locked end-to-end in CI.</li>
 *   <li><b>Demo / preview.</b> Writes a <em>persistent</em> HTML report to
 *       {@code karate-image/target/visual-demo/karate-reports} — open
 *       {@code feature-html/visual-demo.html} to see the m3 diff lightbox (thumbnail →
 *       &lt;dialog&gt; with side-by-side / slider / blink / onion-skin). This is the
 *       manual smoke step (see the karate-image README), runnable on demand.</li>
 * </ol>
 *
 * <p>This is the seed of the full m3(d) dev-flow test — it currently covers a core
 * slice of the v1 1→7 progression and will grow to the full set (custom per-name
 * options, outline / multi-name) when m3(d) lands.</p>
 */
class VisualDemoTest {

    @Test
    void runsTheVisualRegressionWalkthrough() throws Exception {
        // Run-local, writable workspace under target/ (gitignored, cleaned by `mvn
        // clean`) — a fresh baselineDir means the "establish" step always fires.
        Path work = Paths.get("target", "visual-demo").toAbsolutePath();
        deleteRecursively(work);
        Files.createDirectories(work);

        Path baselineDir = work.resolve("baselines");
        Files.writeString(work.resolve("karate-boot.js"), """
            const image = boot.ext('image');
            image.baselineDir = '%s';
            image.threshold = 0.0;
            image.report = 'all';
            """.formatted(baselineDir.toString().replace("\\", "/")));

        // Stage a per-name options file into the baseline dir. In a real project the
        // baselineDir is committed and holds both baselines and their <name>.json
        // tuning files side by side; here baselineDir is ephemeral (so "establish"
        // fires), so we seed the committed fixture. Scenario 5 relies on this being
        // auto-loaded by image.compare('home_mobile', ...).
        Files.createDirectories(baselineDir);
        try (var in = getClass().getResourceAsStream("/demo/options/home_mobile.json")) {
            Files.copy(java.util.Objects.requireNonNull(in, "home_mobile.json fixture missing"),
                    baselineDir.resolve("home_mobile.json"));
        }

        Path reports = work.resolve("karate-reports");
        SuiteResult result = Runner.path("classpath:demo/visual-demo.feature")
                .workingDir(work)
                .outputDir(reports)
                .outputConsoleSummary(false)
                .parallel(1);

        // Five scenarios pass: establish + match + (inspected) regression + rebase +
        // per-name-options tolerance.
        assertEquals(5, result.getScenarioPassedCount(), "all five demo scenarios should pass");
        assertEquals(0, result.getScenarioFailedCount(), "no scenario should fail");

        // Baselines were established (home rebased to v2; home_mobile from scenario 5).
        assertTrue(Files.exists(baselineDir.resolve("home.png")), "home baseline should exist");
        assertTrue(Files.exists(baselineDir.resolve("home_mobile.png")), "home_mobile baseline should exist");

        // A real report was written with the image-comparison embed (the regression's
        // baseline/latest/diff). The feature-page filename is derived from the resource
        // path, so locate it rather than hard-coding. Surface it for the manual demo.
        Path featureHtml;
        try (var stream = Files.list(reports.resolve("feature-html"))) {
            featureHtml = stream.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
        }
        assertTrue(Files.exists(featureHtml), "feature report should be written");
        String html = Files.readString(featureHtml);
        assertTrue(html.contains("image-comparison"), "report should carry the image-comparison embed");
        assertTrue(html.contains("ext/image/image.js"), "report should splice the image ext script");
        System.out.println("[visual-demo] open the report: " + featureHtml.toUri());
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}

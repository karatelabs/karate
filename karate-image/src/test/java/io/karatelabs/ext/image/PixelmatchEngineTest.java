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

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PixelmatchEngineTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int RED = 0xFFFF0000;

    private static Map<String, Object> opts(Object... params) {
        Map<String, Object> options = new HashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            options.put(params[i].toString(), params[i + 1]);
        }
        return options;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private static BufferedImage image(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, w, h, rgb);
        return img;
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                img.setRGB(xx, yy, rgb);
            }
        }
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // blue 3x3 square; latest has a green center pixel
    private static byte[] blueSquare() {
        return png(image(3, 3, BLUE));
    }

    private static byte[] blueSquareGreenCenter() {
        BufferedImage img = image(3, 3, BLUE);
        img.setRGB(1, 1, GREEN);
        return png(img);
    }

    @Test
    void testBasicMismatchPercentage() {
        Map<String, Object> result = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(), opts(), opts("engine", "pixelmatch"));

        // 3x3 = 9 pixels, 1 is different => ~11.11%
        assertEquals(11.11, round((double) result.get("mismatchPercentage")));
        assertEquals(11.11, round((double) result.get(ImageComparison.PIXELMATCH_MISMATCH_PERCENT)));
        assertEquals(Boolean.TRUE, result.get("isMismatch"));
        // no clusters => no raw/summary diagnostics
        assertFalse(result.containsKey(ImageComparison.PIXELMATCH_RAW_MISMATCH_PERCENT));
    }

    @Test
    void testMatchingThreshold() {
        // black vs near-black rgb(13,13,13): below the default OKLab matching threshold
        // (toe correction), but a diff when the matching threshold is turned way down
        byte[] black = png(image(1, 1, BLACK));
        byte[] nearBlack = png(image(1, 1, 0xFF0D0D0D));

        Map<String, Object> below = ImageComparison.run(
                black, nearBlack, opts(), opts("engine", "pixelmatch"));
        assertEquals(0.0, below.get("mismatchPercentage"));

        Map<String, Object> above = ImageComparison.run(
                black, nearBlack, opts("matchingThreshold", 0.001), opts("engine", "pixelmatch"));
        assertEquals(100.0, above.get("mismatchPercentage"));
    }

    @Test
    void testClustersIgnoreShiftedDivider() {
        // a 1px black divider moves down one row: classic rendering noise. Raw diff is
        // 2% of the image; the cluster verdict reports 0 significant and the step passes.
        BufferedImage baseline = image(200, 100, WHITE);
        fill(baseline, 0, 50, 200, 1, BLACK);
        BufferedImage latest = image(200, 100, WHITE);
        fill(latest, 0, 51, 200, 1, BLACK);

        Map<String, Object> result = ImageComparison.run(
                png(baseline), png(latest), opts("clusters", true), opts("engine", "pixelmatch"));

        assertEquals(0.0, result.get("mismatchPercentage"));
        assertNull(result.get("isMismatch"));
        assertEquals(2.0, round((double) result.get(ImageComparison.PIXELMATCH_RAW_MISMATCH_PERCENT)));
        assertTrue(((String) result.get(ImageComparison.PIXELMATCH_SUMMARY)).contains("rendering noise"));
        assertTrue(((List<?>) result.get(ImageComparison.PIXELMATCH_REGIONS)).isEmpty());
    }

    @Test
    void testClustersCatchRealChange() {
        BufferedImage baseline = image(200, 200, WHITE);
        BufferedImage latest = image(200, 200, WHITE);
        fill(latest, 90, 90, 20, 20, RED);

        Map<String, Object> result = ImageComparison.run(
                png(baseline), png(latest), opts("clusters", true), opts("engine", "pixelmatch"));

        // 400 significant pixels of 40000 = 1%
        assertEquals(1.0, result.get("mismatchPercentage"));
        assertEquals(Boolean.TRUE, result.get("isMismatch"));

        List<Map<String, Object>> regions = (List<Map<String, Object>>) result.get(ImageComparison.PIXELMATCH_REGIONS);
        assertEquals(1, regions.size());
        assertEquals("CORE", regions.get(0).get("reason"));
        assertEquals(90, regions.get(0).get("x"));
        assertEquals(90, regions.get(0).get("y"));
        assertEquals(20, regions.get(0).get("width"));
        assertEquals(20, regions.get(0).get("height"));
    }

    @Test
    void testClustersTuningMap() {
        // a single changed pixel is noise under the defaults (below minThinArea)...
        Map<String, Object> defaults = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(), opts("clusters", true), opts("engine", "pixelmatch"));
        assertEquals(0.0, defaults.get("mismatchPercentage"));

        // ...but a tuned minThinArea lets the vivid-on-flat safety net rescue it
        Map<String, Object> tuned = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(),
                opts("clusters", opts("minThinArea", 1)), opts("engine", "pixelmatch"));
        assertEquals(11.11, round((double) tuned.get("mismatchPercentage")));

        List<Map<String, Object>> regions = (List<Map<String, Object>>) tuned.get(ImageComparison.PIXELMATCH_REGIONS);
        assertEquals(1, regions.size());
        assertEquals("THIN_VIVID", regions.get(0).get("reason"));
    }

    @Test
    void testClustersSuiteWideDefaultAndPerCallOverride() {
        BufferedImage baseline = image(200, 100, WHITE);
        fill(baseline, 0, 50, 200, 1, BLACK);
        BufferedImage latest = image(200, 100, WHITE);
        fill(latest, 0, 51, 200, 1, BLACK);
        byte[] baselinePng = png(baseline);
        byte[] latestPng = png(latest);

        // suite-wide default (defaultOptions) enables the verdict
        Map<String, Object> suiteWide = ImageComparison.run(
                baselinePng, latestPng, opts(), opts("engine", "pixelmatch", "clusters", true));
        assertEquals(0.0, suiteWide.get("mismatchPercentage"));

        // per-call clusters=false wins over the suite-wide default
        Map<String, Object> overridden = ImageComparison.run(
                baselinePng, latestPng, opts("clusters", false), opts("engine", "pixelmatch", "clusters", true));
        assertEquals(2.0, round((double) overridden.get("mismatchPercentage")));
        assertFalse(overridden.containsKey(ImageComparison.PIXELMATCH_RAW_MISMATCH_PERCENT));
    }

    @Test
    void testClustersEnabledByNumericFlag() {
        // clusters: 1 out of a <name>.json file enables the verdict, same as true
        BufferedImage baseline = image(200, 100, WHITE);
        fill(baseline, 0, 50, 200, 1, BLACK);
        BufferedImage latest = image(200, 100, WHITE);
        fill(latest, 0, 51, 200, 1, BLACK);

        Map<String, Object> result = ImageComparison.run(
                png(baseline), png(latest), opts("clusters", 1), opts("engine", "pixelmatch"));

        assertEquals(0.0, result.get("mismatchPercentage"));
        assertEquals(2.0, round((double) result.get(ImageComparison.PIXELMATCH_RAW_MISMATCH_PERCENT)));
    }

    @Test
    void testDiffImageIsAlwaysResembles() {
        // pixelmatch never draws: when a report wants a diff image, the resemble engine
        // produces it (that is what the HTML lightbox and its live re-diff render)
        Map<String, Object> result = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(), opts(), opts("engine", "pixelmatch", "report", "all"));

        BufferedImage diff = (BufferedImage) result.get(ImageComparison.DIFF_IMAGE);
        assertNotNull(diff);
        assertEquals(3, diff.getWidth());
        assertEquals(3, diff.getHeight());
        // resemble's default error pixel color (pink), not pixelmatch's red
        assertEquals(0xFFFF00FF, diff.getRGB(1, 1));
        // the resemble fallback also surfaces its percentage for the reports
        assertTrue(result.containsKey(ImageComparison.RESEMBLE_MISMATCH_PERCENT));
        // ...but pass/fail still used pixelmatch's number (the only engine in the list)
        assertEquals(11.11, round((double) result.get("mismatchPercentage")));
    }

    @Test
    void testEngineComboKeepsResembleDiffImage() {
        Map<String, Object> result = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(), opts(), opts("engine", "resemble,pixelmatch", "report", "all"));

        BufferedImage diff = (BufferedImage) result.get(ImageComparison.DIFF_IMAGE);
        assertNotNull(diff);
        assertEquals(0xFFFF00FF, diff.getRGB(1, 1)); // resemble's diff, not overwritten
    }

    @Test
    void testNoReportNoDiffImage() {
        Map<String, Object> result = ImageComparison.run(
                blueSquare(), blueSquareGreenCenter(), opts(), opts("engine", "pixelmatch"));
        assertNull(result.get(ImageComparison.DIFF_IMAGE));
    }

    @Test
    void testIgnoredBoxesWithClusters() {
        // a real change inside an ignored box (e.g. a dynamic transaction id) contributes
        // nothing - not even to the raw diagnostic
        BufferedImage baseline = image(200, 200, WHITE);
        BufferedImage latest = image(200, 200, WHITE);
        fill(latest, 90, 90, 20, 20, RED);

        Map<String, Object> box = new HashMap<>();
        box.put("left", 90);
        box.put("right", 109);
        box.put("top", 90);
        box.put("bottom", 109);

        Map<String, Object> result = ImageComparison.run(
                png(baseline), png(latest),
                opts("clusters", true, "ignoredBoxes", List.of(box)),
                opts("engine", "pixelmatch"));

        assertEquals(0.0, result.get("mismatchPercentage"));
        assertEquals(0.0, result.get(ImageComparison.PIXELMATCH_RAW_MISMATCH_PERCENT));
        assertNull(result.get("isMismatch"));
    }

    @Test
    void testEngineCombination() {
        byte[] img = blueSquare();
        Map<String, Object> result = ImageComparison.run(
                img, img, opts(), opts("engine", "pixelmatch,resemble"));

        assertEquals(0.0, result.get("mismatchPercentage"));
        assertTrue(result.containsKey(ImageComparison.PIXELMATCH_MISMATCH_PERCENT));
        assertTrue(result.containsKey(ImageComparison.RESEMBLE_MISMATCH_PERCENT));
    }
}

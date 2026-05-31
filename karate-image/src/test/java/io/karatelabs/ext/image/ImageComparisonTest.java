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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ImageComparisonTest {
    // blue square with one pink pixel in the center
    private static final byte[] BP_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFklEQVR4AWPgSj4BQSDWf4b/UBYEAQCo6AunFOcG7wAAAABJRU5ErkJggg==");
    // blue square with one yellow pixel in the center
    private static final byte[] BY_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFUlEQVR4AWPgSj4BQSDW//8gEiEGAKnnC6fIccDRAAAAAElFTkSuQmCC");
    // blue square with one green pixel in the center
    private static final byte[] BG_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAFUlEQVR4AWPgSj4BQSBWQAoXlAVBAJZuCmeg2F+9AAAAAElFTkSuQmCC");
    // solid blue square
    private static final byte[] B_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAIAAADZSiLoAAAAD0lEQVR4AWPgSj4BQdhYAJxjCt5/e6nZAAAAAElFTkSuQmCC");
    // alpha square with one green pixel in the center
    private static final byte[] AG_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAEElEQVR4AWPAAAEpXP8xBAEhMwG+kYt51AAAAABJRU5ErkJggg==");
    // light blue (due to alpha channel) square with one pink pixel in the center
    private static final byte[] LBP_3x3_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAADCAYAAABWKLW/AAAAGElEQVR4AWPgSj7hA8Ngzn+G///hHBgGAB/sDwZQy9maAAAAAElFTkSuQmCC");
    // red pixel
    private static final String R_1x1_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQMAAAAl21bKAAAAA1BMVEX/AAAZ4gk3AAAACklEQVR4AWNiAAAABgADDe3ZwQAAAABJRU5ErkJggg==";
    private static final byte[] R_1x1_IMG = Base64.getDecoder().decode(R_1x1_BASE64);
    // red square
    private static final byte[] R_2x2_IMG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAADklEQVR4AWP4zwBCUAoAH+4D/cQhcooAAAAASUVORK5CYII=");


    private static Map<String, Object> opts(Object... params) {
        Map<String, Object> options = new HashMap<>();
        for (int i=0; i<params.length; i+=2) {
            options.put(params[i].toString(), params[i+1]);
        }
        return options;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    @ParameterizedTest
    @ValueSource(strings = {"resemble", "ssim"})
    void testIgnoredBoxes(String engine) {
        Map<String, Integer> box = new HashMap<>();
        box.put("left", 1);
        box.put("right", 2);
        box.put("top", 1);
        box.put("bottom", 2);

        Map<String, Object> result = ImageComparison.compare(
                B_3x3_IMG,
                BG_3x3_IMG,
                opts("ignoredBoxes", Collections.singletonList(box), "windowSize", 1),
                opts("engine", engine));

        assertEquals(0.0, result.get("mismatchPercentage"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"resemble", "ssim"})
    void testIgnoreColors(String engine) {
        Map<String, Object> result = ImageComparison.compare(
                B_3x3_IMG,
                BG_3x3_IMG,
                opts("ignoreColors", true, "windowSize", 1),
                opts("engine", engine));

        assertEquals(0.0, result.get("mismatchPercentage"));
    }

    @Test
    void testIgnoreAreasColoredWith() {
        Map<String, Integer> darkGreen = new HashMap<>();
        darkGreen.put("r", 80);
        darkGreen.put("g", 100);
        darkGreen.put("b", 10);

        Map<String, Object> result = ImageComparison.compare(
                B_3x3_IMG,
                BG_3x3_IMG,
                opts("ignoreAreasColoredWith", darkGreen),
                opts());

        assertEquals(0.0, result.get("mismatchPercentage"));
    }

    @Test
    void testFailureThresholdTriggered() {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(B_3x3_IMG, BG_3x3_IMG, opts(), opts()));

        double mismatchPercentage = (double)exception.data.get("mismatchPercentage");

        // 3x3 = 9 pixels, 1 is different => 1/9 = 0.111111... => ~11.11%
        assertEquals(11.11, round(mismatchPercentage));
    }

    @ParameterizedTest
    @CsvSource({
            "12, 0", // set options only
            "0, 12", // set config only
            "12, 1"  // set both and ensure we prefer options
    })
    void testSetFailureThreshold(double optionFailureThreshold, double configFailureThreshold) {
        Map<String, Object> result = ImageComparison.compare(
                B_3x3_IMG,
                BG_3x3_IMG,
                optionFailureThreshold == 0.0 ? opts() : opts("failureThreshold", optionFailureThreshold),
                configFailureThreshold == 0.0 ? opts() : opts("failureThreshold", configFailureThreshold));

        double mismatchPercentage = (double)result.get("mismatchPercentage");

        // 3x3 = 9 pixels, 1 is different => 1/9 = 0.111111... => ~11.11%
        assertEquals(11.11, round(mismatchPercentage));
    }

    @Test
    void testDataUrl() {
        Map<String, Object> result = ImageComparison.compare(R_1x1_IMG, R_1x1_IMG, opts(), opts());
        String dataUrl = "data:image/png;base64," + R_1x1_BASE64;

        assertEquals(dataUrl, result.get("baseline"));
        assertEquals(dataUrl, result.get("latest"));
    }

    @Test
    void testMissingBaseline() {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(null, R_1x1_IMG, opts(), opts()));

        assertTrue(exception.getMessage().contains("baseline image was empty or not found"));
        assertEquals(Boolean.TRUE, exception.data.get("isBaselineMissing"));
    }

    @Test
    void testScale() {
        Map<String, Object> result = ImageComparison.compare(R_1x1_IMG, R_2x2_IMG, opts(), opts("allowScaling", true));
        assertEquals(0.0, result.get("mismatchPercentage"));
    }

    @Test
    void testScaleMismatch() {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(R_1x1_IMG, R_2x2_IMG, opts(), opts()));

        assertTrue(exception.getMessage().contains("latest image dimensions != baseline image dimensions"));
        assertEquals(Boolean.TRUE, exception.data.get("isScaleMismatch"));
    }

    @Test
    void testInvalidEngine() {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(R_1x1_IMG, R_1x1_IMG, opts("engine", "ng"), opts()));

        assertTrue(exception.getMessage().contains("latest image differed from baseline more than allowable threshold"));
        assertEquals(Boolean.TRUE, exception.data.get("isMismatch"));
        assertEquals(100.0, exception.data.get("mismatchPercentage"));
    }

    static boolean checkImage(BufferedImage image, byte[] raw) throws IOException {
        BufferedImage rawImage = ImageIO.read(new ByteArrayInputStream(raw));
        int[] imgRGB = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        int[] rawRGB =  rawImage.getRGB(0, 0, rawImage.getWidth(), rawImage.getHeight(), null, 0, rawImage.getWidth());
        return Arrays.equals(imgRGB, rawRGB);
    }

    @Test
    void testDiffImage() throws IOException {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(
                        B_3x3_IMG,
                        BG_3x3_IMG,
                        opts(),
                        opts("report", "all")));

        assertTrue(checkImage((BufferedImage)exception.data.get("baselineImage"), B_3x3_IMG));
        assertTrue(checkImage((BufferedImage)exception.data.get("latestImage"), BG_3x3_IMG));
        assertTrue(checkImage((BufferedImage)exception.data.get("diffImage"), BP_3x3_IMG));
    }

    @Test
    void testNoReportDiffImageMissing() {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(
                        B_3x3_IMG,
                        BG_3x3_IMG,
                        opts(),
                        opts()));

        assertNull(exception.data.get("baselineImage"));
    }

    @Test
    void testNoMismatchDiffImageMissing() {
        Map<String, Object> result = ImageComparison.compare(
                R_1x1_IMG,
                R_1x1_IMG,
                opts(),
                opts("report", "mismatched"));

        assertNull(result.get("baselineImage"));
    }

    @Test
    void testNoMismatchDiffImage() throws IOException {
        Map<String, Object> result = ImageComparison.compare(
                R_1x1_IMG,
                R_1x1_IMG,
                opts(),
                opts("report", "all"));

        assertTrue(checkImage((BufferedImage)result.get("diffImage"), R_1x1_IMG));
    }

    @Test
    void testErrorColor() throws IOException {
        Map<String,Integer> yellow = Map.of(
                "red",    255,
                "green",  255,
                "blue",   0,
                "alpha",  255
        );

        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(
                        B_3x3_IMG,
                        BG_3x3_IMG,
                        opts("errorColor", yellow),
                        opts("report", "all")));

        assertTrue(checkImage((BufferedImage)exception.data.get("diffImage"), BY_3x3_IMG));
    }

    @Test
    void testErrorType() throws IOException {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(
                        B_3x3_IMG,
                        BG_3x3_IMG,
                        opts("errorType", "diffOnly"),
                        opts("report", "mismatched")));

        assertTrue(checkImage((BufferedImage)exception.data.get("diffImage"), AG_3x3_IMG));
    }

    @Test
    void testTransparency() throws IOException {
        ImageComparison.MismatchException exception = assertThrows(ImageComparison.MismatchException.class, () ->
                ImageComparison.compare(
                        B_3x3_IMG,
                        BG_3x3_IMG,
                        opts("transparency", 0.3),
                        opts("report", "mismatched")));

        assertTrue(checkImage((BufferedImage)exception.data.get("diffImage"), LBP_3x3_IMG));
    }
}

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

import io.karatelabs.gherkin.Scenario;

import java.awt.image.BufferedImage;
import java.util.Map;

import static io.karatelabs.core.ImageComparison.*;

public class ImageComparisonResult {
    public String[] uniqueIdParts;
    public String errorMessage;
    public double resembleMismatchPercentage;
    public double ssimMismatchPercentage;
    public BufferedImage baselineImage;
    public BufferedImage LatestImage;
    public BufferedImage diffImage;

    public static ImageComparisonResult fromMap(Scenario scenario, int stepLine, Map<String,Object> data) {
        if (data == null || data.get(DIFF_IMAGE) == null) {
            return null;
        }

        ImageComparisonResult result = new ImageComparisonResult();

        result.uniqueIdParts = new String[]{
                data.get("name") == null ? null : data.get("name").toString(),
                scenario.getFeature().getResource().getRelativePath() + ":" + stepLine,
                scenario.getExampleIndex() == -1 ? null : "example #" + scenario.getExampleIndex()
        };

        result.resembleMismatchPercentage = toPercent(data.get(RESEMBLE_MISMATCH_PERCENT), 0);
        result.ssimMismatchPercentage = toPercent(data.get(SSIM_MISMATCH_PERCENT), -1);
        result.baselineImage = (BufferedImage)data.get(BASELINE_IMAGE);
        result.LatestImage = (BufferedImage)data.get(LATEST_IMAGE);
        result.diffImage = (BufferedImage)data.get(DIFF_IMAGE);
        result.errorMessage = (String)data.get("error");

        return result;
    }

    private static double toPercent(Object val, double defaultVal) {
        if (val == null) {
            return defaultVal;
        }

        return Math.round((double)val * 100.0) / 100.0;
    }
}

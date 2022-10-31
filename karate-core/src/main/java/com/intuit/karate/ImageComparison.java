/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
package com.intuit.karate;

import io.github.t12y.resemble.Resemble;
import io.github.t12y.ssim.models.MSSIMMatrix;
import io.github.t12y.ssim.models.Matrix;
import io.github.t12y.ssim.models.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static io.github.t12y.ssim.SSIM.ssim;

public class ImageComparison {

    public static final String RESEMBLE = "resemble";
    public static final String SSIM = "ssim";

    private static final String[] IGNORED_BOX_KEYS = new String[]{"left", "right", "top", "bottom"};
    private static final String[] IGNORED_COLOR_KEYS = new String[]{"r", "g", "b"};

    static final Logger logger = LoggerFactory.getLogger(ImageComparison.class);

    private double[] baselinePixels;
    private double[] latestPixels;
    private int height;
    private int width;
    private double stopWhenMismatchIsLessThan;
    private double failureThreshold;
    private boolean baselineMissing;
    private boolean scaleMismatch;
    private String[] engines;
    private Map<String, Object> options;
    private Map<String, Object> result;

    private ImageComparison(byte[] baselineImg, byte[] latestImg, Map<String, Object> options, boolean allowScaling) {
        this.baselineMissing = baselineImg == null || baselineImg.length == 0;

        BufferedImage baselineImage;
        BufferedImage latestImage;

        try {
            latestImage = ImageIO.read(new ByteArrayInputStream(latestImg));
            baselineImage = baselineMissing ? latestImage : ImageIO.read(new ByteArrayInputStream(baselineImg));
        } catch (IOException e) {
            logger.error("image comparison failed while reading images: {}", e.getMessage());
            return;
        }

        this.height = baselineImage.getHeight();
        this.width = baselineImage.getWidth();
        this.options = options;

        int latestHeight = latestImage.getHeight();
        int latestWidth = latestImage.getWidth();

        if (width != latestWidth || height != latestHeight) {
            if (allowScaling) {
                Image scaledImage = latestImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                latestImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                latestImage.getGraphics().drawImage(scaledImage, 0, 0, null);
                latestHeight = height;
                latestWidth = width;
            } else {
                scaleMismatch = true;
            }
        }

        this.baselinePixels = unpackPixels(baselineImage.getRGB(0, 0, width, height, null, 0, width));
        this.latestPixels = unpackPixels(latestImage.getRGB(0, 0, latestWidth, latestHeight, null, 0, latestWidth));

        String latestDataUrl = getDataUrl(latestImg);
        String baselineDataUrl = baselineMissing ? latestDataUrl : getDataUrl(baselineImg);

        this.result = new HashMap<>();
        result.put("baseline", baselineDataUrl);
        result.put("latest", latestDataUrl);
    }

    private static String getDataUrl(byte[] img) {
        String format = "png";

        try {
            ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(img));
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(input);
                format = reader.getFormatName();
            }
        } catch (Exception e) {
            logger.error("image comparison failed to detect image type: {}", e.getMessage());
        }

        return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(img);
    }

    private void configure(Map<String, Object> defaultOptions) {
        String defaultEngine = asString(defaultOptions.get("engine"));
        if (defaultEngine == null) {
            defaultEngine = RESEMBLE;
        }
        result.put("defaultEngine", defaultEngine);

        double defaultFailureThreshold = toDouble(defaultOptions.get("failureThreshold"), 0.0);
        result.put("defaultFailureThreshold", defaultFailureThreshold);

        failureThreshold = getDouble("failureThreshold", defaultFailureThreshold);
        result.put("failureThreshold", failureThreshold);

        String engineConfig = getString("engine", defaultEngine).toLowerCase().replaceAll("[^a-z,|]", "");
        result.put("engine", engineConfig);

        if (engineConfig.contains("|")) {
            stopWhenMismatchIsLessThan = failureThreshold;
            engines = engineConfig.split("\\|");
        } else {
            stopWhenMismatchIsLessThan = -1.0; // don't stop
            engines = engineConfig.split(",");
        }
    }

    public static Map<String, Object> compare(byte[] baselineImg, byte[] latestImg, Map<String, Object> options,
            Map<String, Object> defaultOptions) throws MismatchException {

        boolean allowScaling = toBool(defaultOptions.get("allowScaling"));
        ImageComparison imageComparison = new ImageComparison(baselineImg, latestImg, options, allowScaling);
        imageComparison.configure(defaultOptions);

        if (imageComparison.baselineMissing) {
            imageComparison.result.put("isBaselineMissing", true);
            throw new MismatchException("baseline image was empty or not found", imageComparison.result);
        }

        if (imageComparison.scaleMismatch) {
            imageComparison.result.put("isScaleMismatch", true);
            throw new MismatchException("latest image dimensions != baseline image dimensions", imageComparison.result);
        }

        double mismatchPercentage = 100.0;

        for (String engine : imageComparison.engines) {
            double currentMismatchPercentage;
            switch (engine) {
                case RESEMBLE:
                    currentMismatchPercentage = imageComparison.execResemble();
                    break;
                case SSIM:
                    currentMismatchPercentage = imageComparison.execSSIM();
                    break;
                default:
                    logger.error("skipping unsupported image comparison engine: {}", engine);
                    continue;
            }

            if (currentMismatchPercentage <= mismatchPercentage) {
                mismatchPercentage = currentMismatchPercentage;
            }

            if (mismatchPercentage < imageComparison.stopWhenMismatchIsLessThan) {
                break;
            }
        }

        return imageComparison.checkMismatch(mismatchPercentage);
    }

    private Map<String, Object> checkMismatch(double mismatchPercentage) {
        result.put("mismatchPercentage", mismatchPercentage);

        if (mismatchPercentage <= 0.0 || mismatchPercentage < failureThreshold) {
            return result;
        }

        String msg = "latest image differed from baseline more than allowable threshold: "
                + mismatchPercentage + "% >= " + failureThreshold + "%";

        result.put("isMismatch", true);

        throw new MismatchException(msg, result);
    }

    private double execResemble() {
        double mismatchPercentage = Resemble.analyzeImages(baselinePixels, latestPixels, resembleOptions());
        result.put("resembleMismatchPercentage", mismatchPercentage);
        return mismatchPercentage;
    }

    private double execSSIM() {
        MSSIMMatrix ssimResult = ssim(
                new Matrix(height, width, baselinePixels),
                new Matrix(height, width, latestPixels),
                ssimOptions());

        double mismatchPercentage = (1.0 - ssimResult.mssim) * 100.0;
        result.put("ssimMismatchPercentage", mismatchPercentage);

        return mismatchPercentage;
    }

    private io.github.t12y.resemble.Options resembleOptions() {
        io.github.t12y.resemble.Options opts;

        String ignoreOption = getString("ignore", "less");
        switch (ignoreOption.toLowerCase().replaceAll("[^a-z]", "")) {
            case "nothing":
                opts = io.github.t12y.resemble.Options.ignoreNothing();
                break;
            case "less":
                opts = io.github.t12y.resemble.Options.ignoreLess();
                break;
            case "antialiasing":
                opts = io.github.t12y.resemble.Options.ignoreAntialiasing();
                break;
            case "colors":
                opts = io.github.t12y.resemble.Options.ignoreColors();
                break;
            case "alpha":
                opts = io.github.t12y.resemble.Options.ignoreAlpha();
                break;
            default:
                logger.error("invalid 'ignore' option for resemble engine: {}", ignoreOption);
                opts = io.github.t12y.resemble.Options.ignoreNothing();
        }

        opts.width = width;
        opts.height = height;

        opts.ignoredBoxes = getIgnoredBoxes();
        opts.ignoreAreasColoredWith = getIntArray(options.get("ignoreAreasColoredWith"), IGNORED_COLOR_KEYS);

        opts.ignoreColors = getBool("ignoreColors", opts.ignoreColors);
        opts.ignoreAntialiasing = getBool("ignoreAntialiasing", opts.ignoreAntialiasing);

        Object tolerancesObj = options.get("tolerances");
        if (tolerancesObj instanceof Map) {
            Map tolerances = (Map) tolerancesObj;
            opts.redTolerance = toDouble(tolerances.get("red"), opts.redTolerance);
            opts.greenTolerance = toDouble(tolerances.get("green"), opts.greenTolerance);
            opts.blueTolerance = toDouble(tolerances.get("blue"), opts.blueTolerance);
            opts.alphaTolerance = toDouble(tolerances.get("alpha"), opts.alphaTolerance);
            opts.minBrightness = toDouble(tolerances.get("minBrightness"), opts.minBrightness);
            opts.maxBrightness = toDouble(tolerances.get("maxBrightness"), opts.maxBrightness);
        }

        return opts;
    }

    private io.github.t12y.ssim.models.Options ssimOptions() {
        io.github.t12y.ssim.models.Options opts = io.github.t12y.ssim.models.Options.Defaults();

        opts.ssim = Options.SSIMImpl.valueOf(getString("ssim", asString(opts.ssim)));
        opts.rgb2grayVersion = Options.RGB2Gray.valueOf(getString("rgb2grayVersion", asString(opts.rgb2grayVersion)));

        opts.k1 = getDouble("k1", opts.k1);
        opts.k2 = getDouble("k2", opts.k2);

        opts.windowSize = getInt("windowSize", opts.windowSize);
        opts.bitDepth = getInt("bitDepth", opts.bitDepth);
        opts.maxSize = getInt("maxSize", opts.maxSize);

        opts.ignoredBoxes = getIgnoredBoxes();

        return opts;
    }

    private boolean getBool(String name, boolean defaultValue) {
        if (!options.containsKey(name)) {
            return defaultValue;
        }
        return toBool(options.get(name));
    }

    private static boolean toBool(Object obj) {
        if (obj == null) {
            return false;
        }
        return Boolean.parseBoolean(asString(obj));
    }

    private double getDouble(String name, double defaultValue) {
        return toDouble(options.get(name), defaultValue);
    }

    private static double toDouble(Object obj, double defaultValue) {
        if (!(obj instanceof Number)) {
            return defaultValue;
        }
        return ((Number) obj).doubleValue();
    }

    private int getInt(String name, int defaultValue) {
        Object val = options.get(name);
        if (!(val instanceof Number)) {
            return defaultValue;
        }
        return ((Number) val).intValue();
    }

    private String getString(String name, String defaultValue) {
        if (!options.containsKey(name)) {
            return defaultValue;
        }
        return asString(options.get(name));
    }

    private static String asString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    private int[][] getIgnoredBoxes() {
        Object boxes = options.get("ignoredBoxes");
        if (!(boxes instanceof Collection)) {
            return null;
        }

        List<int[]> ignoredBoxes = new ArrayList<>();
        for (Object boxObj : (Collection) boxes) {
            int[] ignoredBox = getIntArray(boxObj, IGNORED_BOX_KEYS);
            if (ignoredBox != null) {
                ignoredBoxes.add(ignoredBox);
            }
        }

        return ignoredBoxes.toArray(new int[0][]);
    }

    private static int[] getIntArray(Object obj, String[] keys) {
        if (!(obj instanceof Map)) {
            return null;
        }

        int[] vals = new int[keys.length];
        Map m = (Map) obj;

        for (int i = 0; i < keys.length; i++) {
            Object val = m.get(keys[i]);
            if (val instanceof Number) {
                vals[i] = ((Number) val).intValue();
            }
        }

        return vals;
    }

    // BufferedImage returns pixels packed into int[] in ARGB order -- we need *unpacked* RGBA to for Resemble / SSIM
    // see: https://developer.mozilla.org/en-US/docs/Web/API/ImageData
    private static double[] unpackPixels(int[] packed) {
        int packedLength = packed.length;
        double[] unpacked = new double[packedLength * 4];
        int unpackedIndex;
        int packedPixel;

        for (int i = 0; i < packedLength; i++) {
            packedPixel = packed[i];
            unpackedIndex = i * 4;

            unpacked[unpackedIndex] = (0xff & (packedPixel >> 16));
            unpacked[unpackedIndex + 1] = (0xff & (packedPixel >> 8));
            unpacked[unpackedIndex + 2] = (0xff & packedPixel);
            unpacked[unpackedIndex + 3] = (0xff & (packedPixel >>> 24));
        }

        return unpacked;
    }

    public static class MismatchException extends RuntimeException {

        public Map<String, Object> data;

        public MismatchException(String msg, Map<String, Object> data) {
            super(msg);
            data.put("error", getMessage());
            this.data = data;
        }
    }
    
}

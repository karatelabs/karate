/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.robot;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.WindowConstants;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameConverter;
import static org.bytedeco.opencv.global.opencv_core.findNonZero;
import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;
import static org.bytedeco.opencv.global.opencv_core.bitwise_not;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import org.bytedeco.opencv.opencv_core.AbstractScalar;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Point2f;
import org.bytedeco.opencv.opencv_core.Point2fVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class OpenCvUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenCvUtils.class);
    
    private OpenCvUtils() {
        // only static methods
    }
    
    public static Region find(int strictness, RobotBase robot, Region source, byte[] bytes, boolean resize) {
        Region found = find(strictness, robot, toMat(source.captureGreyScale()), read(bytes), resize);
        if (found == null) {
            return null;
        }
        return found.toAbsolute(source);
    }

    public static Region find(int strictness, RobotBase robot, Mat source, Mat target, boolean resize) {
        List<Region> found = find(strictness, false, robot, source, target, resize);
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0);
    }

    public static List<Region> findAll(int strictness, RobotBase robot, Region source, byte[] bytes, boolean resize) {
        List<Region> found = find(strictness, true, robot, toMat(source.captureGreyScale()), read(bytes), resize);
        List<Region> list = new ArrayList(found.size());
        for (Region r : found) {
            list.add(r.toAbsolute(source));
        }
        return list;
    }

    public static Mat rescale(Mat mat, double scale) {
        Mat resized = new Mat();
        resize(mat, resized, new Size(), scale, scale, CV_INTER_AREA);
        return resized;
    }

    private static final int TARGET_MINVAL_FACTOR = 150; // magic number, lower is stricter
    private static final int BLOCK_SIZE = 5;

    private static List<int[]> getPointsBelowThreshold(Mat src, double threshold) {
        Mat dst = new Mat();
        threshold(src, dst, threshold, 1, CV_THRESH_BINARY_INV);
        Mat non = new Mat();
        findNonZero(dst, non);
        int len = (int) non.total();
        int xPrev = -BLOCK_SIZE;
        int yPrev = -BLOCK_SIZE;
        int countPrev = 0;
        int xSum = 0;
        int ySum = 0;
        List<int[]> points = new ArrayList(len);
        for (int i = 0; i < len; i++) {
            Pointer ptr = non.ptr(i);
            Point p = new Point(ptr);
            int x = p.x();
            int y = p.y();
            int xDelta = Math.abs(x - xPrev);
            int yDelta = Math.abs(y - yPrev);
            if (xDelta < BLOCK_SIZE && yDelta < BLOCK_SIZE) {
                countPrev++;
                xSum += x;
                ySum += y;
            } else {
                if (countPrev > 0) {
                    int xFinal = Math.floorDiv(xSum, countPrev);
                    int yFinal = Math.floorDiv(ySum, countPrev);
                    // logger.debug("end: {}:{}", xFinal, yFinal);
                    points.add(new int[]{xFinal, yFinal});
                }
                xSum = x;
                ySum = y;
                countPrev = 1;
            }
            xPrev = x;
            yPrev = y;
        }
        if (countPrev > 0) {
            int xFinal = Math.floorDiv(xSum, countPrev);
            int yFinal = Math.floorDiv(ySum, countPrev);
            points.add(new int[]{xFinal, yFinal});
        }
        return points;
    }

    private static Region toRegion(RobotBase robot, int[] p, double scale, int targetWidth, int targetHeight) {
        int x = (int) Math.round(p[0] / scale);
        int y = (int) Math.round(p[1] / scale);
        int width = (int) Math.round(targetWidth / scale);
        int height = (int) Math.round(targetHeight / scale);
        return new Region(robot, x, y, width, height);
    }

    private static int[] templateAndMin(int strictness, double scale, Mat source, Mat target, Mat result) {
        Mat resized = scale == 1 ? source : rescale(source, scale);
        matchTemplate(resized, target, result, CV_TM_SQDIFF);
        DoublePointer minValPtr = new DoublePointer(1);
        DoublePointer maxValPtr = new DoublePointer(1);
        Point minPt = new Point();
        Point maxPt = new Point();
        minMaxLoc(result, minValPtr, maxValPtr, minPt, maxPt, null);
        int minVal = (int) minValPtr.get();
        int x = minPt.x();
        int y = minPt.y();
        return new int[]{x, y, minVal};
    }

    private static int collect(int strictness, List<Region> found, boolean findAll, RobotBase robot, Mat source, Mat target, double scale) {
        int targetWidth = target.cols();
        int targetHeight = target.rows();
        int targetMinVal = targetWidth * targetHeight * TARGET_MINVAL_FACTOR * strictness;  
        Mat result = new Mat();
        int[] minData = templateAndMin(strictness, scale, source, target, result);
        int minValue = minData[2];
        if (minValue > targetMinVal) {
            logger.debug("no match at scale {}, minVal: {} / {} at {}:{}", scale, minValue, targetMinVal, minData[0], minData[1]);
            if (robot != null && robot.debug) {
                Rect rect = new Rect(minData[0], minData[1], targetWidth, targetHeight);
                Mat temp = drawOnImage(source, rect, Scalar.RED);
                show(temp, scale + " " +  minData[0] + ":" + minData[1] + " " + minValue + " / " + targetMinVal);
            }
            return minData[2];
        }
        logger.debug("found match at scale {}, minVal: {} / {} at {}:{}", scale, minValue, targetMinVal, minData[0], minData[1]);
        if (findAll) {
            List<int[]> points = getPointsBelowThreshold(result, targetMinVal);
            for (int[] p : points) {
                Region region = toRegion(robot, p, scale, targetWidth, targetHeight);
                found.add(region);
            }
        } else {
            Region region = toRegion(robot, minData, scale, targetWidth, targetHeight);
            found.add(region);
        }
        return minValue;
    }

    public static List<Region> find(int strictness, boolean findAll, RobotBase robot, Mat source, Mat target, boolean resize) {
        List<Region> found = new ArrayList();
        collect(strictness, found, findAll, robot, source, target, 1);
        if (!found.isEmpty()) {
            return found;
        }
        int stepUp = collect(strictness, found, findAll, robot, source, target, 1.1);
        if (!found.isEmpty()) {
            return found;
        }
        int stepDown = collect(strictness, found, findAll, robot, source, target, 0.9);
        if (!found.isEmpty()) {
            return found;
        }
        boolean goUpFirst = stepUp < stepDown;
        for (int step = 2; step < 6; step++) {
            double scale = 1 + 0.1 * step * (goUpFirst ? 1 : -1);
            collect(strictness, found, findAll, robot, source, target, scale);
        }
        if (!findAll && !found.isEmpty()) {
            return found;
        }
        for (int step = 2; step < 6; step++) {
            double scale = 1 + 0.1 * step * (goUpFirst ? -1 : 1);
            collect(strictness, found, findAll, robot, source, target, scale);
        }        
        return found;
    }

    public static Mat loadAndShowOrExit(File file, int flags) {
        Mat image = read(file, flags);
        show(image, file.getName());
        return image;
    }

    public static BufferedImage readImageAsGreyScale(File file) {
        Mat mat = read(file, IMREAD_GRAYSCALE);
        return toBufferedImage(mat);
    }

    public static byte[] toBytes(BufferedImage img) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Mat read(File file) {
        return read(file, IMREAD_GRAYSCALE);
    }

    public static Mat read(byte[] bytes) {
        return read(bytes, IMREAD_GRAYSCALE);
    }

    public static Mat read(byte[] bytes, int flags) {
        Mat image = imdecode(new Mat(bytes), flags);
        if (image.empty()) {
            throw new RuntimeException("image decode failed");
        }
        return image;
    }

    public static Mat read(File file, int flags) {
        Mat image = imread(file.getAbsolutePath(), flags);
        if (image.empty()) {
            throw new RuntimeException("image not found: " + file.getAbsolutePath());
        }
        return image;
    }

    public static File save(BufferedImage image, File file) {
        try {
            ImageIO.write(image, "png", file);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void show(byte[] bytes, String title) {
        Mat mat = read(bytes);
        show(toBufferedImage(mat), title);
    }    

    public static void show(Mat mat, String title) {
        show(toBufferedImage(mat), title);
    }

    public static void show(Image image, String title) {
        CanvasFrame canvas = new CanvasFrame(title, 1);
        canvas.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        canvas.showImage(image);
    }

    public static void save(Mat image, File file) {
        imwrite(file.getAbsolutePath(), image);
    }

    public static Mat drawOnImage(Mat image, Point2fVector points) {
        Mat dest = image.clone();
        int radius = 5;
        Scalar red = new Scalar(0, 0, 255, 0);
        for (int i = 0; i < points.size(); i++) {
            Point2f p = points.get(i);
            circle(dest, new Point(Math.round(p.x()), Math.round(p.y())), radius, red);
        }
        return dest;
    }

    public static Mat drawOnImage(Mat image, Rect overlay, Scalar color) {
        Mat dest = image.clone();
        rectangle(dest, overlay, color);
        return dest;
    }
    
    public static Mat negative(Mat src) {
        Mat dest = new Mat();
        bitwise_not(src, dest);
        return dest;
    }
    
    public static Mat toMat(BufferedImage bi) {
        return Java2DFrameUtils.toMat(bi);
    }    

    public static BufferedImage toBufferedImage(Mat mat) {
        OpenCVFrameConverter.ToMat openCVConverter = new OpenCVFrameConverter.ToMat();
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
        return java2DConverter.convert(openCVConverter.convert(mat));
    }    
    
}

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

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tesseract.TessBaseAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class TesseractUtils {

    private static final Logger logger = LoggerFactory.getLogger(TesseractUtils.class);

    private TesseractUtils() {
        // only static methods
    }

    public static void process(Mat mat) {
        TessBaseAPI tess = new TessBaseAPI();
        if (tess.Init("tessdata", "eng") != 0) {
            throw new RuntimeException("tesseract init failed");
        }
        int width = mat.size().width();
        int height = mat.size().height();
        int channels = mat.channels();        
        int bytesPerLine = width * channels * (int) mat.elemSize1();
        tess.SetImage(mat.data().asBuffer(), width, height, channels, bytesPerLine);
        BytePointer outPtr = tess.GetUTF8Text();
        String out = outPtr.getString();
        outPtr.deallocate();
        logger.debug("text: {}", out);
    }

}

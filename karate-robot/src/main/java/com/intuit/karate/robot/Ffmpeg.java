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

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.File;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Ffmpeg implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Ffmpeg.class);

    private final FFmpegFrameRecorder recorder;
    private final java.awt.Robot robot;
    private final int width;
    private final int height;

    private BufferedImage capture() {
        Image image = robot.createScreenCapture(new Rectangle(0, 0, width, height));
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = bi.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        return bi;
    }

    public Ffmpeg() {
        try {
            Toolkit tk = Toolkit.getDefaultToolkit();
            width = tk.getScreenSize().width;
            height = tk.getScreenSize().height;
            robot = new java.awt.Robot();
            File file = new File("target/karate.mp4");
            recorder = FFmpegFrameRecorder.createDefault(file, width, height);
            recorder.setFrameRate(5);
            recorder.setPixelFormat(0);
            recorder.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int FRAME_INTERVAL = 200;
    
    @Override
    public void run() {
        int count = 0;
        try {
            while (count++ < 100) {
                long time = System.currentTimeMillis();
                BufferedImage bi = capture();
                Frame frame = Java2DFrameUtils.toFrame(bi);
                recorder.record(frame);
                long elapsed = System.currentTimeMillis() - time;
                if (elapsed < FRAME_INTERVAL) {
                    Thread.sleep(FRAME_INTERVAL - elapsed);
                } else {
                    logger.debug("slow: " + elapsed);
                }
            }
            recorder.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

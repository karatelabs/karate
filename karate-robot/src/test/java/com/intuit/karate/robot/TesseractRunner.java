package com.intuit.karate.robot;

import java.awt.image.BufferedImage;
import java.io.File;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robot.core.ChromeJavaRunner;

/**
 *
 * @author pthomas3
 */
public class TesseractRunner {

    private static final Logger logger = LoggerFactory.getLogger(TesseractRunner.class);

    @Test
    public void testTess() {
        // File source = new File("src/test/java/some-text.png");        
        RobotBase robot = ChromeJavaRunner.getRobot();
        Element window = robot.window("Safari");
        // window = robot.window("Preview");
        robot.delay(1000);
        BufferedImage bi = window.getRegion().captureGreyScale();
        Mat mat = OpenCvUtils.toMat(bi);
        Tesseract tess = new Tesseract(new File("tessdata"), "eng");
        tess.process(mat, false);
        String text = tess.getAllText();
        logger.debug("all text: {}", text);
        tess.highlightWords(robot, robot.screen, 20000);
    }

}

package com.intuit.karate.robot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.opencv.opencv_core.AbstractMat;
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
        List<Element> elements = new ArrayList();
        for (Tesseract.Word word : tess.getWords()) {
            Region region = new Region(robot, word.x, word.y, word.width, word.height);
            Element e = new ImageElement(region);
            elements.add(e);
        }
        RobotUtils.highlightAll(window.getRegion(), elements, 2000);
        List<int[]> list = tess.find(true, "Notifications");
        List<Element> es = new ArrayList(list.size());
        for (int[] b : list) {
            es.add(new ImageElement(new Region(robot, b[0], b[1], b[2], b[3])));
        }
        RobotUtils.highlightAll(robot.screen, es, 2000);
        // es.get(1).click();
    }

}

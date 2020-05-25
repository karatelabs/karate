package com.intuit.karate.robot;

import java.io.File;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class TesseractUtilsRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TesseractUtilsRunner.class);
    
    @Test
    public void testTess() {
        File source = new File("src/test/java/some-text.png");
        Mat mat = OpenCvUtils.read(source);
        TesseractUtils.process(mat);
    }    
    
}

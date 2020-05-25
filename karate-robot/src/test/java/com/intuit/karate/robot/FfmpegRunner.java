package com.intuit.karate.robot;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FfmpegRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(FfmpegRunner.class);

    @Test
    public void testOpenCv() {
        Ffmpeg ff = new Ffmpeg();
        ff.run();
    }    
    
}

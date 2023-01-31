package com.intuit.karate.robot;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FfmpegRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(FfmpegRunner.class);

    @Test
    void testOpenCv() {
        Ffmpeg ff = new Ffmpeg();
        ff.run();
    }    
    
}

package com.intuit.karate.robot;

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class RobotUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(RobotUtilsTest.class);

    @Test
    public void testOpenCv() {
        System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
        File target = new File("src/test/resources/search.png");
        File source = new File("src/test/resources/desktop01.png");
        Region region = RobotUtils.find(source, target);
        assertEquals(1605, region.x);
        assertEquals(1, region.y);
    }

}

package com.intuit.karate.robot.win;

import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.Robot;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robot.core.ChromeJavaRunner;

/**
 *
 * @author pthomas3
 */
public class WinRobotRunner {

    private static final Logger logger = LoggerFactory.getLogger(WinRobotRunner.class);

    @Test
    public void testRobot() {
        Robot bot = ChromeJavaRunner.getRobot();
        Element e = bot.getRoot();
        assertEquals("Desktop", e.getName());
    }

}

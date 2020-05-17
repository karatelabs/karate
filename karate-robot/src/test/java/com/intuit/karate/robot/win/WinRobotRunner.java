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
        Robot robot = ChromeJavaRunner.getRobot();
        Element e = robot.getRoot();
        assertEquals("Desktop", e.getName());
        Element win = robot.window("^NetBeans");
        assertNotNull(win);
        logger.debug("name: {}", win.getName());
    }

}

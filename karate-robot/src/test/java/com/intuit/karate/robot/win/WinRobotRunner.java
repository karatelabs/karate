package com.intuit.karate.robot.win;

import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.Robot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import robot.core.ChromeJavaRunner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

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

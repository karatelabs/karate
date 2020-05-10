package robot.core;

import com.intuit.karate.robot.Robot;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CaptureRunner {

    @Test
    public void testCapture() {
        Robot bot = ChromeJavaRunner.getRobot();
        // make sure Chrome is open
        bot.focusWindow(t -> t.contains("Chrome"));
        bot.delay(1000);
        bot.captureAndSaveAs("target/temp.png");
    }

}

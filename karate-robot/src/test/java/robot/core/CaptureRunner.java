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
        Robot bot = new Robot(ChromeJavaRunner.getContext());
        // make sure Chrome is open
        bot.switchTo(t -> t.contains("Chrome"));
        bot.delay(1000);
        bot.captureAndSave("target/temp.png");
    }
    
}

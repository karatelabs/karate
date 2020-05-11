package robot.core;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.robot.Element;
import com.intuit.karate.robot.Robot;
import com.intuit.karate.robot.RobotFactory;
import java.nio.file.Path;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeJavaRunner {
    
    public static Robot getRobot() {
        Path featureDir = FileUtils.getPathContaining(ChromeJavaRunner.class);
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        ScenarioContext context = new ScenarioContext(featureContext, callContext, null, null);
        return new RobotFactory().create(context, null);
    }    

    @Test
    public void testChrome() {                
        Robot bot = getRobot();
        // make sure Chrome is open
        bot.focusWindow(t -> t.contains("Chrome"));
        bot.input(Keys.META + "t");
        bot.input("karate dsl" + Keys.ENTER);
        Element img = bot.locateImage("tams.png");        
        img.highlight();
        img.click();
    }    

}

package robot.core;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.robot.Region;
import com.intuit.karate.robot.Robot;
import java.nio.file.Path;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeJavaRunner {
    
    public static ScenarioContext getContext() {
        Path featureDir = FileUtils.getPathContaining(ChromeJavaRunner.class);
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }    

    @Test
    public void testCalc() {        
        Robot bot = new Robot(getContext());
        // make sure Chrome is open
        bot.switchTo(t -> t.contains("Chrome"));
        bot.input(Keys.META, "t");
        bot.input("karate dsl" + Keys.ENTER);
        Region region = bot.find("tams.png");        
        region.highlight(2000);
        region.click();
    }    

}

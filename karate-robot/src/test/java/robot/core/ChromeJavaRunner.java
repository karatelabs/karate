package robot.core;

import com.intuit.karate.SuiteRuntime;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.driver.Keys;
import com.intuit.karate.robot.RobotBase;
import com.intuit.karate.robot.RobotFactory;
import com.intuit.karate.runtime.FeatureRuntime;
import com.intuit.karate.runtime.ScenarioRuntime;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeJavaRunner {

    public static RobotBase getRobot() {
        Feature feature = FeatureParser.parse("classpath:robot/core/dummy.feature");
        FeatureRuntime fr = FeatureRuntime.of(new SuiteRuntime(), feature);
        ScenarioRuntime sr = fr.scenarios.next();
        return (RobotBase) new RobotFactory().create(sr.engine, null);
    }

    @Test
    public void testChrome() {
        RobotBase bot = getRobot();
        // make sure Chrome is open
        bot.window(t -> t.contains("Chrome"));
        bot.input(Keys.META + "t");
        bot.input("karate dsl" + Keys.ENTER);
        bot.waitFor("tams.png").click();
    }

}

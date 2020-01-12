package robot.windows;

import com.intuit.karate.driver.Keys;
import com.intuit.karate.robot.Region;
import com.intuit.karate.robot.Robot;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeJavaRunner {

    @Test
    public void testCalc() {        
        Robot bot = new Robot();
        // make sure Chrome is open
        bot.switchTo(t -> t.contains("Chrome"));
        bot.input(Keys.META, "t");
        bot.input("karate dsl" + Keys.ENTER);
        bot.delay(1000);
        Region region = bot.find("src/test/resources/tams.png");        
        region.highlight(2000);
        region.click();
    }    

}

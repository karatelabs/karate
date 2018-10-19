package driver.demo;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class Demo01UiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/driver/demo/demo-01.feature", "mock");
    }     
    
}

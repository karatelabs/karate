package demo.outline;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DynamicUiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/demo/outline/dynamic.feature", "mock");
    } 
    
}

package demo.outline;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ExamplesUiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/demo/outline/examples.feature", "mock");
    } 
    
}

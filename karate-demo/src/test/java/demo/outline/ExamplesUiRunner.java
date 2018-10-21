package demo.outline;

import com.intuit.karate.ui.App2;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ExamplesUiRunner {
    
    @Test
    public void testApp() {
        App2.run("src/test/java/demo/outline/examples.feature", "mock");
    } 
    
}

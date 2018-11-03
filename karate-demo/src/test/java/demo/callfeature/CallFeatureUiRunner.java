package demo.callfeature;

import com.intuit.karate.ui.App2;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CallFeatureUiRunner {
    
    @Test
    public void testApp() {
        App2.run("src/test/java/demo/callfeature/call-feature.feature", "mock");
    }     
    
}

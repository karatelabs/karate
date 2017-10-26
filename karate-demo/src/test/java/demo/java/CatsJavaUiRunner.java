package demo.java;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CatsJavaUiRunner {
    
    @Test
    public void testApp() {
        App.run("src/test/java/demo/java/cats-java.feature", "dev");
    }    
    
}

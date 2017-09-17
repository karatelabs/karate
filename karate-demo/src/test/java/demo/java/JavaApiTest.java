package demo.java;

import com.intuit.karate.cucumber.CucumberRunner;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class JavaApiTest {
    
    @Test
    public void testCallingFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = CucumberRunner.runFeature(getClass(), "from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }
    
    @Test
    public void testCallingClasspathFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = CucumberRunner.runClasspathFeature("demo/java/from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }    
    
}

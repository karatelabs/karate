package demo.java;

import com.intuit.karate.Runner;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class JavaApiTest {
    
    @BeforeClass
    public static void beforeClass() {
        // skip 'callSingle' in karate-config.js
        System.setProperty("karate.env", "mock"); 
    }    
    
    @Test
    public void testCallingFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = Runner.runFeature(getClass(), "from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }
    
    @Test
    public void testCallingClasspathFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = Runner.runFeature("classpath:demo/java/from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }    
    
}

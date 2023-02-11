package demo.java;

import com.intuit.karate.Runner;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class JavaApiTest {
    
    @BeforeAll
    static void beforeAll() {
        // skip 'callSingle' in karate-config.js
        System.setProperty("karate.env", "mock"); 
    }    
    
    @Test
    void testCallingFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = Runner.runFeature(getClass(), "from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }
    
    @Test
    void testCallingClasspathFeatureFromJava() {
        Map<String, Object> args = new HashMap();
        args.put("name", "World");
        Map<String, Object> result = Runner.runFeature("classpath:demo/java/from-java.feature", args, true);
        assertEquals("Hello World", result.get("greeting"));
    }    
    
}

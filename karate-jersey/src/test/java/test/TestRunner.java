package test;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class TestRunner {  
    
    @Test
    public void testTest() {
        Results results = Runner.path("classpath:test/test.feature").parallel(1);
        assertTrue("failed", results.getFailCount() == 0);
    }
    
}

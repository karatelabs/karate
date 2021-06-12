package jobtest.simple;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleRunner {
    
    @Test
    void test() {
        Results results = Runner.path("classpath:jobtest/simple")
                .outputCucumberJson(true).parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }      
    
}

package jobtest.web;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class WebRunner {
    
    @Test
    void test() {
        Results results = Runner.path("classpath:jobtest/web").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }     
    
}

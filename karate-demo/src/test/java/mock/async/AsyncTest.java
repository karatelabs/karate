package mock.async;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.Test;
import static org.junit.Assert.*;

public class AsyncTest { 

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:mock/async/main.feature")
                .configDir("classpath:mock/async")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}

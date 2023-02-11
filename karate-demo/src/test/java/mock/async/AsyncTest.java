package mock.async;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncTest { 

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:mock/async/main.feature")
                .configDir("classpath:mock/async")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}

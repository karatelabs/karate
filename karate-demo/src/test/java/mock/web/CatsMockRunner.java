package mock.web;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class CatsMockRunner {

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:mock/web/cats-test.feature")
                .karateEnv("mock")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}

package mock.web;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CatsMockRunner {

    @Test
    public void testParallel() {
        Results results = Runner.path("classpath:mock/web/cats-test.feature")
                .karateEnv("mock")
                .parallel(1);
        assertTrue(results.getErrorMessages(), results.getFailCount() == 0);
    }

}

package demo.tags;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author peter
 */
class TagsRunner {

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:demo/tags")
                .configDir("classpath:demo/tags")
                .parallel(1);
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

}

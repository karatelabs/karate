package mock.web;

import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class CatsTestRunner {
    
    @Test
    void testMockOnPort8080() {
        Runner.runFeature(getClass(), "cats-test.feature", null, false);
    }

}

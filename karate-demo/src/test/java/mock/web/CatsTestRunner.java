package mock.web;

import com.intuit.karate.Runner;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CatsTestRunner {
    
    @Test
    public void testMockOnPort8080() {
        Runner.runFeature(getClass(), "cats-test.feature", null, false);
    }

}

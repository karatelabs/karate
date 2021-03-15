package regex;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class RegexRunner {
    
    @Test
    void testMock() {
        Results results = Runner.path("src/test/java/regex/regex.feature").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }    
    
}

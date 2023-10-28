package responseStatus;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ExamplesTest {
    @Test
    void testParallel() {
        Results results = Runner.path("classpath:responseStatus")
                .outputCucumberJson(true)
                .outputHtmlReport(true)
                .configDir("src/test/java/responseStatus")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}

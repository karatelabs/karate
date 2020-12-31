package axe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;

import org.junit.jupiter.api.Test;

public class AxeRunner {

    @Test
    public void axeSimpleTest() {
        Results results = Runner.path("classpath:axe/axe.feature")
                .parallel(1);
        assertEquals(0, results.getFailCount());
    }
}

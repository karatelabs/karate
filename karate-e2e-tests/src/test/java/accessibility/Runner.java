package accessibility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intuit.karate.Results;

import org.junit.jupiter.api.Test;

public class Runner {

    @Test
    public void axeSimpleTest() {
        Results results = com.intuit.karate.Runner.path("src/test/java/accessibility/axe.feature")
                .parallel(1);
        assertEquals(0, results.getFailCount());
    }
}

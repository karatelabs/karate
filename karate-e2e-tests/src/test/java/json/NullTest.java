package json;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NullTest {
    void shouldFail(String name) {
        Results results = Runner.path("src/test/java/json/" + name + ".feature").parallel(1);
        assertEquals(1, results.getFailCount(), results.getErrorMessages());
    }

    @Test
    void testEmptyObject() {
        shouldFail("empty-object");
    }

    @Test
    void testEmptyString() {
        shouldFail("empty-string");
    }

    @Test
    void testRecommend() {
        shouldFail("recommend");
    }
}

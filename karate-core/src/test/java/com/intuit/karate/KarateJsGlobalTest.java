package com.intuit.karate;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Bug: JavaScript function cannot access global var after executing feature in nested context.
 */
public class KarateJsGlobalTest {

    private Results run(String tag) {
        return Runner.path("classpath:js-global-test/JsVarAccess.feature")
                .tags(tag)
                .parallel(1);
    }

    @Test
    public void executeJsScenarioWithoutCallToNestedRunner() {
        Results results = run("@JsScenarioWithoutCallToNestedRunner");
        Assertions.assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

    @Test
    public void executeJsScenarioWithCallToNestedRunner() {
        Results results = run("@JsScenarioWithCallToNestedRunner");
        Assertions.assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}
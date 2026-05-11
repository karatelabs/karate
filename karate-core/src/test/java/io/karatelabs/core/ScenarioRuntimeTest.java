package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void testScenario() {
        HttpServer server = HttpServer.start(9000, request -> new HttpResponse());
        Resource root = Resource.path("src/test/resources/feature");
        KarateJs karate = new KarateJs(root);
        Feature feature = Feature.read(root.resolve("http-simple.feature"));
        Scenario scenario = feature.getSections().getFirst().getScenario();
        ScenarioRuntime runtime = new ScenarioRuntime(karate, scenario);
        runtime.call();
    }

    @Test
    void testFailTag() {
        // @fail tag inverts pass/fail: if scenario fails, it's considered passed
        ScenarioRuntime sr = runFeature("""
            Feature:
            @fail
            Scenario:
            * def a = 1 + 2
            * match a == 4
            """);
        // The match fails (3 != 4), but with @fail tag, it should pass
        assertPassed(sr);
    }

    @Test
    void testFailTagFailure() {
        // @fail tag inverts pass/fail: if scenario passes, it's considered failed
        ScenarioRuntime sr = runFeature("""
            Feature:
            @fail
            Scenario:
            * def a = 1 + 2
            * match a == 3
            """);
        // The match passes (3 == 3), but with @fail tag, it should fail
        assertFailedWith(sr, ScenarioResult.EXPECT_TEST_TO_FAIL_BECAUSE_OF_FAIL_TAG);
    }

    @Test
    void testKarateFail() {
        // karate.fail() should fail the scenario with custom message
        ScenarioRuntime sr = run("""
            * def before = true
            * karate.fail('test fail message')
            * def after = true
            """);
        assertFailedWith(sr, "test fail message");
        // 'before' should be defined, 'after' should not (execution stopped at fail)
        matchVar(sr, "before", true);
    }

    @Test
    void testScenarioNameInterpolationWithoutBackticks() {
        // v1 parity: ${...} placeholders in a scenario name should be evaluated
        // even when the name is not wrapped in backticks. Real-world example
        // surfaced by a v1 power user — name uses karate-config.js bindings.
        ScenarioRuntime sr = runFeature("""
            Feature:
            Scenario: market '${countryCode}': creation [Tracking ID: ${tracingId}]
            * def countryCode = 'DE'
            * def tracingId = 'trace-xyz'
            """);
        assertPassed(sr);
        assertEquals("market 'DE': creation [Tracking ID: trace-xyz]",
                sr.getScenario().getName());
    }

    @Test
    void testScenarioNameInterpolationWithMethodCall() {
        // Method call inside ${...} — mirrors user's currentMarket().countryCode case.
        ScenarioRuntime sr = runFeature("""
            Feature:
            Scenario: market '${currentMarket().countryCode}' check
            * def currentMarket = function(){ return { countryCode: 'DE' } }
            """);
        assertPassed(sr);
        assertEquals("market 'DE' check", sr.getScenario().getName());
    }

    @Test
    void testScenarioNameWithBacktickWrapStillWorks() {
        // Existing backtick path — regression guard.
        ScenarioRuntime sr = runFeature("""
            Feature:
            Scenario: `result is ${1 + 1}`
            * def x = 1
            """);
        assertPassed(sr);
        assertEquals("result is 2", sr.getScenario().getName());
    }

    @Test
    void testScenarioNameWithoutPlaceholderIsUnchanged() {
        // No ${...}, no backticks — name passes through verbatim, eval not attempted.
        ScenarioRuntime sr = runFeature("""
            Feature:
            Scenario: plain name with $ sign and {curly braces}
            * def x = 1
            """);
        assertPassed(sr);
        assertEquals("plain name with $ sign and {curly braces}",
                sr.getScenario().getName());
    }

    @Test
    void testScenarioNameInterpolationFailureKeepsOriginal() {
        // Eval throws (undefined variable) — original name kept, warning logged.
        ScenarioRuntime sr = runFeature("""
            Feature:
            Scenario: ref to ${undefinedVarThatBlowsUp}
            * def x = 1
            """);
        assertPassed(sr);
        assertEquals("ref to ${undefinedVarThatBlowsUp}",
                sr.getScenario().getName());
    }

    @Test
    void testHotReloadUpdatesStepText() throws Exception {
        Path featureFile = tempDir.resolve("reload.feature");
        Files.writeString(featureFile, """
                Feature:
                Scenario:
                * def x = 1
                """);
        Feature feature = Feature.read(Resource.from(featureFile));
        Scenario scenario = feature.getSections().getFirst().getScenario();
        KarateJs karate = new KarateJs(Resource.from(featureFile.getParent()));
        ScenarioRuntime runtime = new ScenarioRuntime(karate, scenario);
        runtime.call();
        Step step = scenario.getSteps().getFirst();
        assertEquals("x = 1", step.getText());

        Files.writeString(featureFile, """
                Feature:
                Scenario:
                * def x = 42
                """);
        assertTrue(runtime.hotReload());
        assertEquals("x = 42", step.getText());

        // second call with no on-disk change is a no-op
        assertFalse(runtime.hotReload());
    }

}

/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core;

import io.karatelabs.gherkin.Scenario;
import io.karatelabs.test.LogSilencer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A run-once call (callonce in all its forms, setupOnce, callSingle) that fails is not run
 * again: every later caller fails fast with the same reason, and the failure names the
 * scenario that ran it — in the message and, in the HTML report, as a link to that scenario's
 * card — since only that scenario's report holds the called feature's trace.
 */
class CachedFailureTest {

    @TempDir
    Path tempDir;

    private static final String FAILING_AUTH = """
            @ignore
            Feature: Auth
            Scenario:
            * match 'token' == 'expired'
            """;

    private SuiteResult run(Path feature, int threads) {
        return run(feature, threads, Runner.builder());
    }

    private SuiteResult run(Path feature, int threads, Runner.Builder builder) {
        return LogSilencer.silenced("karate.runtime", () -> builder
                .path(feature.toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .retainCallResults(true)
                .backupOutputDir(false)
                .parallel(threads));
    }

    private static List<ScenarioResult> scenarios(SuiteResult result) {
        return result.getFeatureResults().getFirst().getScenarioResults();
    }

    private static String where(ScenarioResult sr) {
        Scenario s = sr.getScenario();
        return "(cached failure, it ran in caller.feature" + s.getRefId() + " " + s.getName() + ") ";
    }

    private String reportPage() throws Exception {
        try (var pages = Files.list(tempDir.resolve("reports/feature-html"))) {
            return Files.readString(pages.filter(p -> p.getFileName().toString().startsWith("caller"))
                    .findFirst().orElseThrow());
        }
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    private static String link(ScenarioResult origin) {
        return "\"cachedFailureOrigin\":{\"featureHtmlName\":\"caller\",\"refId\":\""
                + origin.getScenario().getRefId() + "\"";
    }

    // a step that ran a feature call: what a replay must not have, since it runs nothing
    private static boolean ranACall(ScenarioResult sr) {
        return sr.getStepResults().stream().anyMatch(StepResult::hasCallResults);
    }

    /**
     * The shape a shared setup outage takes: every scenario's Background does a callonce
     * whose feature does a callSingle, and the callSingle fails. The setup runs once, in one
     * scenario, which alone holds the trace; the others fail at once, pointing there.
     */
    @Test
    void testAFailedCallonceRunsOnceAndTheRestPointToIt() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Files.writeString(tempDir.resolve("setup.feature"), """
                @ignore
                Feature: Setup
                Scenario:
                * def serviceName = 'demo'
                * def headers = karate.callSingle('auth.feature')
                """);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Background:
                * callonce read('setup.feature')

                Scenario Outline: endpoint <data>
                * print data

                Examples:
                | data |
                | ep1  |
                | ep2  |
                | ep3  |
                | ep4  |
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 4));
        assertEquals(4, scenarios.size());
        List<ScenarioResult> ran = scenarios.stream().filter(CachedFailureTest::ranACall).toList();
        assertEquals(1, ran.size(), "the failed callonce is not run again");
        ScenarioResult origin = ran.getFirst();
        assertFalse(origin.getFailureMessage().contains("cached failure"), origin.getFailureMessage());
        for (ScenarioResult sr : scenarios) {
            assertTrue(sr.isFailed());
            assertTrue(sr.getFailureMessage().contains("expired"), "each keeps the reason: " + sr.getFailureMessage());
            if (sr != origin) {
                assertTrue(sr.getFailureMessage().contains(where(origin)), sr.getFailureMessage());
            }
        }
        // each of the three replays links to the origin's card from its callonce step
        assertEquals(3, count(reportPage(), link(origin)));
    }

    @Test
    void testAFailedCallSingleIsReplayedNamingItsScenario() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: first
                * def auth = karate.callSingle('auth.feature')

                Scenario: second
                * def auth = karate.callSingle('auth.feature')
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        assertTrue(ranACall(scenarios.get(0)));
        assertFalse(ranACall(scenarios.get(1)));
        assertTrue(scenarios.get(1).getFailureMessage().contains(where(scenarios.get(0)) + "callSingle failed: auth.feature"),
                scenarios.get(1).getFailureMessage());
        assertEquals(1, count(reportPage(), link(scenarios.get(0))));
    }

    /**
     * callSingle is Suite-wide, so the scenario holding the trace can be in another feature: the
     * link must name the page the report wrote for that feature (a folder path becomes dots).
     */
    @Test
    void testACallSingleFailureFromAnotherFeatureLinksToItsPage() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        // one cache key for both: the same path string, whatever folder the caller is in
        String auth = "file:" + tempDir.resolve("auth.feature").toAbsolutePath().toString().replace('\\', '/');
        Files.writeString(tempDir.resolve("first.feature"), """
                Feature: First
                Scenario: runs it
                * def auth = karate.callSingle('%s')
                """.formatted(auth));
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("second.feature"), """
                Feature: Second
                Scenario: replays it
                * def auth = karate.callSingle('%s')
                """.formatted(auth));

        SuiteResult result = LogSilencer.silenced("karate.runtime", () -> Runner.builder()
                .path(tempDir.resolve("first.feature").toString(), sub.resolve("second.feature").toString())
                .workingDir(tempDir)
                .outputDir(tempDir.resolve("reports"))
                .outputConsoleSummary(false)
                .backupOutputDir(false)
                .parallel(1));

        assertEquals(2, result.getFeatureResults().size());
        Path pages = tempDir.resolve("reports/feature-html");
        String secondPage = Files.readString(pages.resolve("sub.second.html"));
        var matcher = Pattern.compile("\"cachedFailureOrigin\":\\{\"featureHtmlName\":\"([^\"]+)\",\"refId\":\"([^\"]+)\"")
                .matcher(secondPage);
        assertTrue(matcher.find(), "the replay in sub/second.feature links to the scenario that ran it");
        assertEquals("first", matcher.group(1));
        String firstPage = Files.readString(pages.resolve(matcher.group(1) + ".html"));
        assertTrue(firstPage.contains("\"refId\":\"" + matcher.group(2) + "\""), "the linked page holds the target card");
        // the badge names the feature too: [1:3] is also this page's own first scenario
        assertTrue(secondPage.contains("\"featureFileName\":\"first.feature\""), secondPage);
    }

    /**
     * The common bootstrap: karate-config.js does the callSingle. Config runs before the first
     * step, so the failure is the synthetic "Scenario execution failed" step every scenario
     * starts with: the first one's carries the called feature, the rest link to it.
     */
    @Test
    void testACallSingleFailureInKarateConfigLinksFromEveryOtherScenario() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Path configJs = tempDir.resolve("karate-config.js");
        Files.writeString(configJs, """
                function fn() {
                    var auth = karate.callSingle('auth.feature');
                    return { token: auth.token };
                }
                """);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: one
                * print token

                Scenario: two
                * print token

                Scenario: three
                * print token
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1, Runner.builder().configDir(configJs.toString())));
        ScenarioResult origin = scenarios.getFirst();
        assertTrue(ranACall(origin), "the config's call is attached to the scenario it ran in");
        for (ScenarioResult sr : scenarios.subList(1, 3)) {
            assertFalse(ranACall(sr));
            assertTrue(sr.getFailureMessage().contains(where(origin) + "callSingle failed: auth.feature"),
                    sr.getFailureMessage());
        }
        String page = reportPage();
        assertEquals(2, count(page, link(origin)));
        // the step is labeled by the config that failed; the error beside it carries the detail
        assertEquals(3, count(page, "\"text\":\"karate-config.js failed\""));
    }

    @Test
    void testEveryCallonceFormCachesItsFailure() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: def first
                * def auth = callonce read('auth.feature')

                Scenario: def second
                * def auth = callonce read('auth.feature')

                Scenario: js first
                * def auth = karate.callonce('auth.feature')

                Scenario: js second
                * def auth = karate.callonce('auth.feature')
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        for (int i = 0; i < 4; i += 2) {
            ScenarioResult first = scenarios.get(i);
            ScenarioResult second = scenarios.get(i + 1);
            assertTrue(ranACall(first), first.getScenario().getName());
            assertFalse(ranACall(second), second.getScenario().getName());
            assertTrue(second.getFailureMessage().contains(where(first)), second.getFailureMessage());
            assertTrue(second.getFailureMessage().contains("expired"), second.getFailureMessage());
        }
    }

    @Test
    void testAFailedSetupOnceIsCached() throws Exception {
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: first
                * def data = karate.setupOnce('missing')

                Scenario: second
                * def data = karate.setupOnce('missing')
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        assertTrue(scenarios.get(0).getFailureMessage().contains("no scenario found with @setup tag"));
        assertTrue(scenarios.get(1).getFailureMessage().contains(where(scenarios.get(0)) + "no scenario found"),
                scenarios.get(1).getFailureMessage());
    }

    // Java glue failing with an AssertionError (a JUnit assertion) is a step failure, so cached too
    @Test
    void testAnAssertionErrorFromJavaGlueIsCached() throws Exception {
        Files.writeString(tempDir.resolve("setup.js"), """
                function() { Java.type('org.junit.jupiter.api.Assertions').fail('glue says no') }
                """);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: first
                * def setup = karate.callSingle('setup.js')

                Scenario: second
                * def setup = karate.callSingle('setup.js')
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        assertTrue(scenarios.get(0).getFailureMessage().contains("glue says no"), scenarios.get(0).getFailureMessage());
        assertTrue(scenarios.get(1).getFailureMessage().contains(where(scenarios.get(0)) + "glue says no"),
                scenarios.get(1).getFailureMessage());
    }

    /**
     * A JS helper loaded in one scenario and invoked in another reports its calls under the
     * scenario that invoked it — so that is where a failed callSingle inside it ran, not the
     * scenario the helper's karate bridge was captured in.
     */
    @Test
    void testAFailureInASharedHelperNamesTheScenarioThatInvokedIt() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Files.writeString(tempDir.resolve("helper.js"), """
                function() { return { auth: function() { return karate.callSingle('auth.feature') } } }
                """);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Background:
                * def helper = karate.callSingle('helper.js').auth

                Scenario: loads
                * print 'only loads the helper'

                Scenario: invokes
                * def auth = helper()

                Scenario: replays
                * def auth = helper()
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        assertTrue(scenarios.get(0).isPassed());
        assertTrue(ranACall(scenarios.get(1)), "the invoking scenario holds the trace");
        assertTrue(scenarios.get(2).getFailureMessage().contains(where(scenarios.get(1))),
                scenarios.get(2).getFailureMessage());
    }

    /**
     * karate.setup() discards its @setup scenario's result, so a call failing in there left a
     * trace nowhere: the replay says it is cached but points at no scenario.
     */
    @Test
    void testAFailureInsideASetupScenarioNamesNoScenario() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                @setup
                Scenario:
                * def auth = karate.callSingle('auth.feature')

                Scenario: setup
                * def data = karate.setup()

                Scenario: replays
                * def auth = karate.callSingle('auth.feature')
                """);

        List<ScenarioResult> scenarios = scenarios(run(caller, 1));
        ScenarioResult replays = scenarios.getLast();
        assertTrue(replays.getFailureMessage().contains("(cached failure) callSingle failed: auth.feature"),
                replays.getFailureMessage());
        assertFalse(reportPage().contains("cachedFailureOrigin"));
    }

    /**
     * A host-shared callSingle cache (karate-gatling) outlives a Suite: a later Suite replays the
     * failure, but its report does not hold the trace, so it names the scenario without a link.
     */
    @Test
    void testAFailureFromAnotherSuiteIsNotLinked() throws Exception {
        Files.writeString(tempDir.resolve("auth.feature"), FAILING_AUTH);
        Path caller = tempDir.resolve("caller.feature");
        Files.writeString(caller, """
                Feature: Caller
                Scenario: only
                * def auth = karate.callSingle('auth.feature')
                """);
        Map<String, Object> cache = new ConcurrentHashMap<>();
        ReentrantLock lock = new ReentrantLock();

        ScenarioResult first = scenarios(run(caller, 1, Runner.builder().callSingleCache(cache, lock))).getFirst();
        assertTrue(ranACall(first));
        ScenarioResult second = scenarios(run(caller, 1, Runner.builder().callSingleCache(cache, lock))).getFirst();
        assertFalse(ranACall(second));
        assertTrue(second.getFailureMessage().contains(where(first)), second.getFailureMessage());
        assertFalse(reportPage().contains("cachedFailureOrigin"));
    }

}

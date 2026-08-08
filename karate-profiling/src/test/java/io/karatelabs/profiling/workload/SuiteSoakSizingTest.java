/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.profiling.workload;

import io.karatelabs.profiling.WorkloadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the soak generates must be what was asked for, and the digest's request count must match
 * the features on disk rather than a constant that agrees with itself.
 *
 * <p>Two failures this pins, both of which produce a run that looks fine:
 *
 * <ul>
 *   <li><b>A resized experiment.</b> The obvious sizing arithmetic floors twice, so
 *       {@code --iterations 15 --suites 4} once generated <em>40</em> scenarios — more work than
 *       was asked for, against a {@code --timeout} the operator sized from the number they typed.
 *       On a two-hour soak that is a killed child rather than a result.</li>
 *   <li><b>A drifted request count.</b> {@code REQUESTS_PER_SCENARIO} is coupled to the generated
 *       Gherkin by nothing but care, and the digest multiplies by it to decide whether the mock
 *       served everything. A test that fabricated both sides from the same constant would agree
 *       with itself forever, so this one counts the HTTP steps in the generated text.</li>
 * </ul>
 */
class SuiteSoakSizingTest {

    private static final String REPORTS = "karate.profiling.reports";
    private static final String SCENARIOS = "profiling.soak.scenarios";
    private static final String SUITES = "profiling.soak.suites";
    private static final String ALLOWED = "profiling.soak.allowedFailures";

    private SuiteSoakWorkload workload;

    @AfterEach
    void cleanup() {
        Stream.of(REPORTS, SCENARIOS, SUITES, ALLOWED).forEach(System::clearProperty);
        if (workload != null) {
            workload.teardown();
            workload = null;
        }
    }

    private SuiteSoakWorkload generate(long iterations) {
        System.setProperty(REPORTS, "all");
        workload = new SuiteSoakWorkload();
        workload.setup(new WorkloadContext(4, iterations, null, "https://mock.invalid:8443"));
        return workload;
    }

    private static List<Path> features(SuiteSoakWorkload workload) throws IOException {
        try (Stream<Path> paths = Files.list(workload.generatedFeaturesDir())) {
            return paths.filter(p -> p.getFileName().toString().startsWith("soak_")).sorted().toList();
        }
    }

    private static long count(String text, String needle) {
        long found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }

    @Test
    void testTheGeneratedSuiteIsExactlyTheSizeThatWasAskedFor() throws IOException {
        SuiteSoakWorkload w = generate(100);
        List<Path> features = features(w);
        assertEquals(10, features.size(), "100 scenarios at the default 10 per feature");
        long scenarios = 0;
        for (Path feature : features) {
            scenarios += count(Files.readString(feature), "\n  Scenario:");
        }
        assertEquals(100, scenarios, "the tree on disk must hold exactly --iterations scenarios");
        assertEquals(100, w.expectedPerSuite(), "and the workload must expect what it wrote");
    }

    @Test
    void testTheTotalIsSplitAcrossSuites() throws IOException {
        SuiteSoakWorkload w = generate(300);
        System.setProperty(SUITES, "3");
        // Re-generate now that the suite count is set: 300 total across 3 suites is 100 each.
        w.teardown();
        workload = new SuiteSoakWorkload();
        workload.setup(new WorkloadContext(4, 300, null, "https://mock.invalid:8443"));
        assertEquals(10, features(workload).size(), "100 per suite is 10 features, run 3 times");
        assertEquals(100, workload.expectedPerSuite());
    }

    /**
     * The count the digest reconciles against, counted off the generated text. If someone adds or
     * removes a request in the feature template, this fails here rather than showing up as a
     * "shortfall" in a two-hour soak's digest that sends the reader hunting a dropped packet.
     */
    @Test
    void testRequestsPerScenarioMatchesTheGeneratedFeatures() throws IOException {
        SuiteSoakWorkload w = generate(100);
        String feature = Files.readString(features(w).get(0));
        String auth = Files.readString(w.generatedFeaturesDir().resolve("auth.feature"));
        long scenarios = count(feature, "\n  Scenario:");
        long requestsInScenario = count(feature, "When method") / scenarios;
        long callsPerScenario = count(feature, "call read('auth.feature')") / scenarios;
        long requestsInCallee = count(auth, "When method");
        assertEquals(1, callsPerScenario, "one shared-feature call per scenario");
        assertEquals(SuiteSoakWorkload.REQUESTS_PER_SCENARIO,
                requestsInScenario + callsPerScenario * requestsInCallee,
                "the constant the digest reconciles with must equal the HTTP steps actually generated");
    }

    @Test
    void testACountThatDoesNotDivideIsRefusedRatherThanRounded() {
        System.setProperty(SUITES, "2");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generate(101));
        assertTrue(e.getMessage().contains("100"), "the refusal must name a count that works: " + e.getMessage());
        assertTrue(e.getMessage().contains("120"), "and the next one up: " + e.getMessage());
    }

    /** The case that used to run MORE than was asked: 15 across 4 suites became 40. */
    @Test
    void testATotalSmallerThanOneScenarioPerSuiteIsRefused() {
        System.setProperty(SUITES, "4");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> generate(15));
        assertTrue(e.getMessage().contains("40"), "40 is the smallest workable count here: " + e.getMessage());
    }

    @Test
    void testReportingOffIsRefusedForTheExperimentThatIsAboutReporting() {
        workload = new SuiteSoakWorkload();
        System.setProperty(REPORTS, "off");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> workload.setup(new WorkloadContext(4, 100, null, "https://mock.invalid:8443")));
        assertTrue(e.getMessage().contains("reports=all"), e.getMessage());
    }

    @Test
    void testNonsenseKnobsAreRefusedBeforeAnythingIsGenerated() {
        System.setProperty(SCENARIOS, "0");
        assertThrows(IllegalArgumentException.class, () -> generate(100),
                "zero scenarios per feature divides by zero a few lines later");
        cleanup();

        System.setProperty(SUITES, "-2");
        assertThrows(IllegalArgumentException.class, () -> generate(100));
        cleanup();

        // -1 reads as "unlimited" and did the opposite: it abandoned a run in which nothing failed.
        System.setProperty(ALLOWED, "-1");
        assertThrows(IllegalArgumentException.class, () -> generate(100));
    }
}

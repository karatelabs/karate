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

import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.Workload;
import io.karatelabs.profiling.WorkloadContext;

import java.time.Duration;

/**
 * Many sequential {@code karate.call()}s from one long scenario, across many scenarios in
 * a single suite.
 *
 * <p>Targets a retention chain that is append-only at every link:
 * <pre>
 *   SuiteResult.featureResults      (whole-run lifetime)
 *     └─ FeatureResult
 *          └─ ScenarioResult.stepResults
 *               └─ StepResult.callResults : List&lt;FeatureResult&gt;
 * </pre>
 * Every call keeps the callee's entire result tree, and nothing is released until the suite
 * finishes — so the live set scales with <em>total scenarios × calls per scenario</em>, not
 * with anything per-scenario. Call results here are deliberately never bound to a variable,
 * to keep the question about what is <em>kept</em> rather than what is returned.
 *
 * <p><b>The measurement is the scale sweep, not any single run.</b> One run tells you almost
 * nothing; a heap that stays flat as scenario count rises tells you there is no accumulation,
 * and one that climbs proportionally tells you there is:
 * <pre>
 *   etc/run.sh call-accumulation --iterations 500
 *   etc/run.sh call-accumulation --iterations 2000
 *   etc/run.sh call-accumulation --iterations 5000
 * </pre>
 *
 * <p>This workload owns its suite. Driving it the usual way — a fresh {@code Suite} per
 * harness iteration — would let every suite become garbage as soon as its iteration ended,
 * collecting the very accumulation being hunted and reporting all clear.
 */
public class CallAccumulationWorkload implements Workload {

    private static final String FEATURE = "classpath:workload/call-accumulation.feature";

    private WorkloadContext context;

    @Override
    public String name() {
        return "call-accumulation";
    }

    @Override
    public String describe() {
        return "60 sequential karate.call()s per scenario across one suite, results never bound. "
                + "Probes whether completed call results are retained for the whole run. Run at "
                + "several --iterations values and compare peak heap — the trend is the measurement.";
    }

    @Override
    public boolean drivesOwnConcurrency() {
        return true;
    }

    @Override
    public JvmConfig jvm() {
        return new JvmConfig("768m", JvmConfig.Gc.G1);
    }

    @Override
    public RunShape shape() {
        // --iterations means scenarios in the suite here, and --threads is the suite's own
        // parallelism. Timeout is explicit and generous: when this shape does exhaust the
        // heap it does not fail fast, it degenerates into a GC death spiral that would
        // otherwise run until the default cap.
        return new RunShape(16, 2000, null, Duration.ZERO, Duration.ofMinutes(10));
    }

    @Override
    public void setup(WorkloadContext context) {
        this.context = context;
    }

    @Override
    public void iterate(int vu, long iteration) {
        long scenarios = context.iterations() > 0 ? context.iterations() : 1000;
        // Reporting is off by default so the measurement is of execution, not of report
        // building. Turn it on with -Dkarate.profiling.reports=true: the HTML listener keeps
        // its own copy of every feature result, so "retention with reports on" is a different
        // — and more representative — question than "retention with reports off".
        boolean reports = Boolean.getBoolean("karate.profiling.reports");
        SuiteResult result = Runner.path(FEATURE)
                .systemProperty("profiling.scenarios", String.valueOf(scenarios))
                .outputHtmlReport(reports)
                .outputJsonLines(false)
                .outputJunitXml(false)
                .outputCucumberJson(false)
                .outputConsoleSummary(true)
                .parallel(context.threads());
        System.out.println("[workload] scenarios=" + result.getScenarioCount()
                + " passed=" + result.getScenarioPassedCount()
                + " failed=" + result.getScenarioFailedCount());
    }

}

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
import io.karatelabs.profiling.ReportCostListener;
import io.karatelabs.profiling.ReportMode;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.Workload;
import io.karatelabs.profiling.WorkloadContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The same total scenario count as {@code call-accumulation}, spread across MANY features
 * instead of concentrated in one.
 *
 * <p>This exists because {@code call-accumulation} is a single feature holding one
 * {@code Scenario Outline} of N rows, which makes feature-end and suite-end the same
 * instant. Any bounding strategy that releases at feature end scores exactly zero on it,
 * and any per-feature behaviour — the report listeners' queues, per-feature output files,
 * peak concurrent features — is exercised exactly once. Neither property is representative:
 * the ordinary Karate suite is many features of a few scenarios each.
 *
 * <p>Run the two as a pair. They are the two extremes of the same total work, and a
 * retention fix that moves one and not the other has told you precisely which bound it
 * achieved:
 * <pre>
 *   etc/run.sh call-accumulation --iterations 2000   # 1 feature  x 2000 scenarios
 *   etc/run.sh feature-spread    --iterations 2000   # 200 features x 10 scenarios
 * </pre>
 *
 * <p>Knobs, all system properties on the child JVM:
 * <ul>
 *   <li>{@code profiling.spread.scenarios} — scenarios per feature (default 10). Feature
 *       count is {@code --iterations} divided by this, so the total stays comparable.</li>
 *   <li>{@code profiling.spread.calls} — {@code karate.call()}s per scenario (default 60,
 *       matching call-accumulation so the two differ only in distribution).</li>
 *   <li>{@code karate.profiling.reports} — turn the report writers on.</li>
 *   <li>{@code karate.profiling.reportCost} — attach {@link ReportCostListener}.</li>
 * </ul>
 *
 * <p>Features are generated rather than committed because the interesting variable is how
 * many there are. A checked-in tree would fix that at one value and invite editing the
 * shape by hand, which is how two runs stop being comparable.
 */
public class FeatureSpreadWorkload implements Workload {

    private WorkloadContext context;
    private Path generatedDir;

    @Override
    public String name() {
        return "feature-spread";
    }

    @Override
    public String describe() {
        return "The same scenario count as call-accumulation spread over many features rather than "
                + "one. Distinguishes bounding that releases per feature from bounding that releases "
                + "per scenario, and is the only workload that exercises per-feature report writing "
                + "more than once. Run it alongside call-accumulation, never alone.";
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
        return new RunShape(16, 2000, null, Duration.ZERO, Duration.ofMinutes(10));
    }

    @Override
    public void setup(WorkloadContext context) {
        this.context = context;
        long totalScenarios = context.iterations() > 0 ? context.iterations() : 1000;
        int perFeature = Integer.getInteger("profiling.spread.scenarios", 10);
        int calls = Integer.getInteger("profiling.spread.calls", 60);
        int features = (int) Math.max(1, totalScenarios / perFeature);
        try {
            generatedDir = Files.createTempDirectory("karate-feature-spread-");
            for (int f = 0; f < features; f++) {
                Files.writeString(generatedDir.resolve("spread_" + f + ".feature"),
                        featureText(f, perFeature, calls));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("[workload] generated " + features + " features x " + perFeature
                + " scenarios x " + calls + " calls in " + generatedDir);
    }

    private static String featureText(int index, int scenarios, int calls) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature: spread feature ").append(index).append('\n');
        for (int s = 0; s < scenarios; s++) {
            sb.append("\n  Scenario: spread ").append(index).append(" scenario ").append(s).append('\n');
            sb.append("    * def someVar = 'test-value'\n");
            sb.append("    * def baseArray = karate.repeat(100, function(x){ return { id: x, ")
                    .append("name: 'record_' + x, email: someVar, status: someVar, ")
                    .append("attributes: { source: someVar, target: someVar } } })\n");
            for (int c = 0; c < calls; c++) {
                sb.append("    * karate.call('classpath:workload/call-accumulation-callee.feature')\n");
            }
            sb.append("    * def count = baseArray.length\n");
            sb.append("    * match count == 100\n");
        }
        return sb.toString();
    }

    @Override
    public void iterate(int vu, long iteration) {
        ReportMode reports = ReportMode.current();
        boolean reportCost = Boolean.getBoolean("karate.profiling.reportCost");
        System.out.println("[workload] reports=" + reports + " reportCost=" + reportCost);
        Runner.Builder builder = reports.applyTo(Runner.path(generatedDir.toString()))
                .outputConsoleSummary(false);
        ReportCostListener costListener = null;
        if (reportCost) {
            costListener = new ReportCostListener();
            builder.resultListener(costListener);
        }
        SuiteResult result = builder.parallel(context.threads());
        System.out.println("[workload] scenarios=" + result.getScenarioCount()
                + " passed=" + result.getScenarioPassedCount()
                + " failed=" + result.getScenarioFailedCount());
        if (costListener != null) {
            costListener.print(result.getDurationMillis(), context.threads());
        }
    }

    @Override
    public void teardown() {
        if (generatedDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(generatedDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort — a leftover temp dir is noise, not a failed measurement
                }
            });
        } catch (IOException ignored) {
            // as above
        }
    }

}

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
package io.karatelabs.profiling;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.output.CucumberJsonWriter;
import io.karatelabs.output.HtmlReportWriter;
import io.karatelabs.output.JunitXmlWriter;
import io.karatelabs.output.ResultListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Times the per-feature report work, split by whether Karate does it on the calling thread
 * today or defers it to a writer thread.
 *
 * <p>The question this answers is narrow and specific: the three report listeners each own a
 * single-thread executor with an <em>unbounded</em> queue, which is a place for whole-feature
 * payloads to pile up. Removing the executors and writing on the feature's own thread would
 * delete that hazard, but only if the deferred work is cheap enough that blocking a scenario
 * thread on it does not matter. That is a measurement, not an opinion, and this is the
 * measurement.
 *
 * <p>Two numbers decide it, and they are different questions:
 * <ul>
 *   <li><b>Per-feature deferred cost against per-feature execution time.</b> If rendering a
 *       feature's HTML costs a fraction of running it, doing it inline is free in practice —
 *       and it gains real parallelism, since it would then run on N feature threads rather
 *       than one writer thread.</li>
 *   <li><b>Total deferred cost against suite wall-clock.</b> This is the one that matters for
 *       memory. A single writer thread can only keep up while total deferred work is less
 *       than wall-clock. Once it exceeds it, the queue is growing for the whole run and the
 *       unbounded queue is holding whole-feature payloads — the leak, arriving by a different
 *       road than the one being hunted.</li>
 * </ul>
 *
 * <p>Work is repeated into a scratch directory rather than observed in place, so this can run
 * with the real report writers either on or off without the two fighting over output files.
 * That does mean the timings include work the real run also does — read them as the cost of
 * the operation, not as an addition to the run.
 */
public class ReportCostListener implements ResultListener {

    private final Path scratchDir;

    private final List<Long> featureExecMillis = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> htmlPrepareMicros = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> htmlRenderMicros = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> cucumberSerializeMicros = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> cucumberWriteMicros = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> junitSerializeMicros = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> junitWriteMicros = Collections.synchronizedList(new ArrayList<>());

    public ReportCostListener() {
        try {
            scratchDir = Files.createTempDirectory("karate-report-cost-");
        } catch (Exception e) {
            throw new IllegalStateException("could not create scratch dir", e);
        }
    }

    @Override
    public void onFeatureEnd(FeatureResult result) {
        result.sortScenarioResults();
        featureExecMillis.add(result.getDurationMillis());
        try {
            long t0 = System.nanoTime();
            Map<String, Object> featureData = HtmlReportWriter.prepareFeatureData(result, scratchDir);
            long t1 = System.nanoTime();
            HtmlReportWriter.renderFeatureHtml(featureData, scratchDir, Collections.emptyMap());
            long t2 = System.nanoTime();
            String cucumberName = CucumberJsonWriter.fileNameFor(result);
            String cucumberContent = CucumberJsonWriter.serializeFeature(result);
            long t3 = System.nanoTime();
            CucumberJsonWriter.writeSerialized(cucumberName, cucumberContent, scratchDir);
            long t4 = System.nanoTime();
            String junitName = JunitXmlWriter.fileNameFor(result);
            String junitContent = JunitXmlWriter.serializeFeature(result);
            long t5 = System.nanoTime();
            JunitXmlWriter.writeSerialized(junitName, junitContent, scratchDir);
            long t6 = System.nanoTime();
            htmlPrepareMicros.add((t1 - t0) / 1000);
            htmlRenderMicros.add((t2 - t1) / 1000);
            cucumberSerializeMicros.add((t3 - t2) / 1000);
            cucumberWriteMicros.add((t4 - t3) / 1000);
            junitSerializeMicros.add((t5 - t4) / 1000);
            junitWriteMicros.add((t6 - t5) / 1000);
        } catch (Exception e) {
            System.out.println("[report-cost] failed for " + result.getDisplayName() + ": " + e);
        }
    }

    /**
     * @param suiteWallMillis the suite's own elapsed time
     * @param threads         suite parallelism, i.e. how many threads would share the work if
     *                        the writers ran inline
     */
    public void print(long suiteWallMillis, int threads) {
        int features = featureExecMillis.size();
        if (features == 0) {
            System.out.println("[report-cost] no features observed");
            return;
        }
        System.out.println();
        System.out.println("[report-cost] features=" + features
                + " suiteWall=" + suiteWallMillis + "ms threads=" + threads);
        System.out.println("[report-cost] per-feature, microseconds:");
        line("feature exec (ms)  ", featureExecMillis);
        line("html prepare  (sync)", htmlPrepareMicros);
        line("html render   (ASYNC)", htmlRenderMicros);
        line("cucumber ser  (sync)", cucumberSerializeMicros);
        line("cucumber write(ASYNC)", cucumberWriteMicros);
        line("junit ser     (sync)", junitSerializeMicros);
        line("junit write   (ASYNC)", junitWriteMicros);

        long deferredTotalMicros = sum(htmlRenderMicros) + sum(cucumberWriteMicros) + sum(junitWriteMicros);
        long syncTotalMicros = sum(htmlPrepareMicros) + sum(cucumberSerializeMicros) + sum(junitSerializeMicros);
        long execTotalMillis = sum(featureExecMillis);
        System.out.println();
        System.out.printf("[report-cost] deferred work total : %8.1f ms  (%.2f%% of suite wall)%n",
                deferredTotalMicros / 1000.0, pct(deferredTotalMicros / 1000.0, suiteWallMillis));
        System.out.printf("[report-cost] already-sync total  : %8.1f ms  (%.2f%% of suite wall)%n",
                syncTotalMicros / 1000.0, pct(syncTotalMicros / 1000.0, suiteWallMillis));
        System.out.printf("[report-cost] feature exec total  : %8d ms  (across %d threads)%n",
                execTotalMillis, threads);
        System.out.printf("[report-cost] deferred / exec     : %.3f%%  <- cost of writing inline%n",
                pct(deferredTotalMicros / 1000.0, execTotalMillis));
        // The single writer thread is only keeping up while this stays below 1.0. Above it,
        // the queue grows for the whole run and holds a whole-feature payload per entry.
        System.out.printf("[report-cost] writer-thread load  : %.3f  (deferred / wall; >1 means the "
                        + "single executor thread cannot keep up and its queue grows unbounded)%n",
                suiteWallMillis > 0 ? (deferredTotalMicros / 1000.0) / suiteWallMillis : 0.0);
        System.out.println("[report-cost] scratch bytes written: " + scratchBytes() / (1024 * 1024) + " MB (deleting)");
        deleteScratch();
        System.out.println();
    }

    private long scratchBytes() {
        try (Stream<Path> paths = Files.walk(scratchDir)) {
            return paths.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * This listener re-renders every report format for every feature, so its scratch directory
     * grows to roughly the size of a full report run — on a call-heavy workload that is hundreds
     * of megabytes, and it is pure measurement waste the moment the timings are printed.
     */
    private void deleteScratch() {
        try (Stream<Path> paths = Files.walk(scratchDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort — a leftover scratch dir is noise, not a failed measurement
                }
            });
        } catch (IOException ignored) {
            // as above
        }
    }

    private static double pct(double part, double whole) {
        return whole > 0 ? part * 100.0 / whole : 0.0;
    }

    private static long sum(List<Long> values) {
        long total = 0;
        synchronized (values) {
            for (long v : values) {
                total += v;
            }
        }
        return total;
    }

    private static void line(String label, List<Long> values) {
        List<Long> copy;
        synchronized (values) {
            copy = new ArrayList<>(values);
        }
        Collections.sort(copy);
        System.out.printf("  %-22s p50=%8d  p90=%8d  p99=%8d  max=%8d  total=%10d%n",
                label, at(copy, 0.50), at(copy, 0.90), at(copy, 0.99), copy.get(copy.size() - 1),
                sum(copy));
    }

    private static long at(List<Long> sorted, double q) {
        int i = (int) Math.min(sorted.size() - 1, Math.round(q * (sorted.size() - 1)));
        return sorted.get(i);
    }

}

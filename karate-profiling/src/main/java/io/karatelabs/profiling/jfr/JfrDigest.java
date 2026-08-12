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
package io.karatelabs.profiling.jfr;

import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a binary recording into markdown a human — or an agent with a context budget —
 * can actually read.
 *
 * <p>Sections are fixed and appear in a fixed order, so two digests diff cleanly. That
 * property is the whole point: most questions this harness answers are "did this change
 * make it better or worse", which is a diff, not a reading.
 *
 * <p>Stack traces are collapsed to their topmost {@code io.karatelabs.*} frame. Without
 * that, every allocation panel is a wall of {@code java.util.HashMap.newNode} and says
 * nothing about which Karate code caused it.
 */
public final class JfrDigest {

    private static final String KARATE_PREFIX = "io.karatelabs.";
    private static final int TOP_N = 25;
    /**
     * Below this many jdk.OldObjectSample events, the retained tables are not evidence. A
     * one-hour soak at ~9,900 iterations/s produced 19, most with no reference chain even with
     * path-to-gc-roots=true.
     */
    private static final int RETAINED_SAMPLES_FOR_CONFIDENCE = 200;
    /**
     * {@code jdk.GarbageCollection.name} values that collect the young generation only. Anything
     * else touches the old generation, and only then is a rising heap-after-GC floor evidence of
     * retention rather than of promoted garbage nobody has collected yet.
     */
    private static final java.util.Set<String> YOUNG_ONLY_COLLECTORS =
            java.util.Set.of(
            // Verified against real jdk.GarbageCollection events on JDK 24, one recording per
            // collector, rather than assumed: G1 emits G1New/G1Old/G1Full, Parallel emits
            // ParallelScavenge/ParallelOld, Serial emits DefNew/SerialOld, and ZGC emits
            // "ZGC Minor"/"ZGC Major". "ZGC Minor" was missing here, so under --gc zgc every
            // minor collection counted as old-generation and the young-only warning was
            // suppressed in exactly the run that needed it. Names not on this list fall through
            // to "collects the old generation", which errs toward suppressing the warning —
            // Shenandoah lands there.
            "G1New", "ParallelScavenge", "DefNew", "ZGC Minor");

    /**
     * What the parent knows that the recording doesn't.
     *
     * @param extraJvmFlags the operator's {@code --jvm-flag} values, exactly as given. Passed in
     *                      rather than reconstructed from {@link #command()}: the reconstruction
     *                      worked by excluding everything the harness was known to add, so any
     *                      new harness flag would be mislabelled as an operator constraint, a
     *                      workload's own {@code jvmFlags()} already were, and an operator's
     *                      {@code -XX:+UseSomething} was dropped entirely by the {@code -XX:+Use}
     *                      exclusion meant for the collector. The exact list exists where the
     *                      command is built, so there is nothing to infer.
     */
    public record RunInfo(String workload, int exitCode, boolean timedOut, boolean heapDump,
                          RunShape shape, JvmConfig jvm, List<String> command,
                          List<String> extraJvmFlags) {
    }

    private JfrDigest() {
    }

    public static void write(Path runDir, RunInfo info) {
        StringBuilder md = new StringBuilder();
        md.append("# Profiling digest — ").append(info.workload()).append("\n\n");
        appendRunSummary(md, runDir, info);
        appendCpu(md, runDir);

        Path jfr = runDir.resolve("run.jfr");
        // A run forked without a recording is a timing run, not a broken one — tell the reader
        // calmly instead of prescribing chunk-repository rescue for a file that was never asked
        // for. Decided from the recorded command, which is what actually ran.
        boolean recordingRequested = info.command().stream()
                .anyMatch(argument -> argument.startsWith("-XX:StartFlightRecording"));
        if (!recordingRequested) {
            md.append("## No recording — timing run\n\n")
                    .append("This run was forked with `--no-jfr`, so there is no `run.jfr` and no ")
                    .append("allocation, CPU or GC panel. The run summary's `elapsed` and `cpu` rows ")
                    .append("are the measurement; use a JFR-enabled run of the same shape to diagnose.\n\n");
        } else if (!usable(jfr)) {
            appendMissingRecording(md, runDir, jfr);
        } else {
            try {
                Data data = read(jfr);
                appendAllocationBySite(md, data, info);
                appendHotMethods(md, data, info);
                appendLiveSet(md, runDir);
                appendHeapAfterGc(md, data);
                appendGcPauses(md, data);
                appendRetainedObjects(md, data, info);
            } catch (Exception e) {
                md.append("## Recording could not be parsed\n\n```\n").append(e).append("\n```\n\n")
                        .append("Try `jfr summary ").append(jfr.getFileName()).append("`, or rescue from the ")
                        .append("chunk repository:\n\n```bash\njfr assemble jfr-repo/ rescue.jfr\n```\n\n");
            }
        }
        appendTopClasses(md, runDir, info);
        appendSuiteOutcome(md, runDir);
        appendLoadProfile(md, runDir, info);

        try {
            Files.writeString(runDir.resolve("digest.md"), md.toString());
        } catch (IOException e) {
            System.err.println("[digest] could not write digest.md: " + e);
        }
    }

    // ---------------------------------------------------------------- panels

    /**
     * Whether the machine had room. Placed directly under the run summary because it is a
     * precondition for reading anything below it, not a result: a cell whose injector was pinned
     * has not measured the client it meant to, it has measured the machine — and two saturated
     * clients queued behind one server report the same throughput while proving nothing about
     * either. docs/PROFILING.md §10 carried this leg as "not recorded anywhere" until this panel.
     *
     * <p>Both numbers are <b>self-reported over the reporter's own window</b>, and the two windows
     * are not the same length. For an ordinary workload the child's opens at
     * {@code [child] measuring}, past warmup and most JIT. For a <b>self-driving</b> one — every
     * {@code gatling-*} workload — there is no such seam: the window wraps the whole simulation,
     * so it carries Gatling's engine boot and teardown, typically 30-40% of it against the mock's
     * load window. That makes the injector's {@code cores busy} a <b>lower bound</b> on its
     * load-window utilisation, which is the safe direction for a headroom check and the wrong one
     * to quote as a cost. See {@link io.karatelabs.profiling.SelfCpu}.
     */
    private static void appendCpu(StringBuilder md, Path runDir) {
        String summary = findChildSummary(runDir);
        String mockStats = io.karatelabs.profiling.LoadProfile.mockLine(runDir, "PROFILING-MOCK-STATS ");
        double childCpu = keyValueNanos(summary, io.karatelabs.profiling.SelfCpu.CPU_NANOS);
        double childWall = keyValueNanos(summary, io.karatelabs.profiling.SelfCpu.WALL_NANOS);
        double mockCpu = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "cpuNanosInWindow") / 1e9;
        double mockWall = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "loadWindowSeconds");
        if (childCpu < 0 && mockCpu < 0) {
            return;
        }
        // The CHILD's core count when it reported one, not this process's. They are normally the
        // same and were assumed to be — until --jvm-flag made -XX:ActiveProcessorCount=1 possible,
        // which is exactly the constrained run someone would take to test a saturation check. A
        // digest that divided a 1-core child's CPU by the parent's 16 would report 6% busy for a
        // pegged JVM. The fallback is this process's count, which is also what an old digest gets.
        int reportedCpus = (int) keyValueNumber(summary, "cpus");
        int cpus = reportedCpus > 0 ? reportedCpus : Runtime.getRuntime().availableProcessors();
        md.append("## CPU headroom\n\n");
        md.append("What each process burned over its own window. `cores` near ").append(cpus)
                .append(" means this run measured the machine rather than what it was pointed at, and a ")
                .append("throughput comparison taken there is not a comparison of clients.\n\n")
                .append("The two windows are not the same window — for a `gatling-*` workload the ")
                .append("injector's spans its whole simulation, engine boot included, while the ")
                .append("mock's covers only the load. So the injector row **understates** its ")
                .append("load-window utilisation: read it as a floor, read each row against `cpus`, ")
                .append("and never one row against the other.\n\n");
        // The mock reports its own core count. Absent (an older mock, or one that declined),
        // there is no honest denominator for its row and the percentage is omitted rather than
        // borrowed from the injector.
        int mockCpus = (int) io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "cpus");
        md.append("| process | CPU (core-s) | window (s) | cores busy | % of its own cpus |")
                .append("\n|---|---:|---:|---:|---:|\n");
        // Whether the mock shared these cores is not something this panel may assume. The
        // label used to read "co-located" unconditionally, which became a falsehood the day
        // --mock-url landed: every two-host digest asserted the mock was on this machine.
        boolean external = mockLine(runDir, "[external] attached to ") != null;
        cpuRow(md, "workload (the injector, " + cpus + " cpus)", childCpu, childWall, cpus);
        cpuRow(md, "mock (" + (external ? "REMOTE host" : "co-located")
                + (mockCpus > 0 ? ", " + mockCpus + " cpus" : ", core count unknown")
                + ")", mockCpu, mockWall, mockCpus);
        if (mockCpu >= 0 && !external) {
            md.append("\nThe mock shares these cores with the load driver, so its row **is** the ")
                    .append("co-location bias — a number rather than an argument.\n");
        } else if (mockCpu >= 0) {
            md.append("\nThe mock ran on its own host, so its row shows that host was idle rather ")
                    .append("than any bias here. Each percentage divides by that process's **own** ")
                    .append("core count, so the two rows are individually meaningful even when the ")
                    .append("hosts are different sizes")
                    .append(mockCpus > 0 ? ".\n"
                            : " — but this mock did not report a core count, so its percentage is ")
                    .append(mockCpus > 0 ? "" : "omitted and only the raw core-seconds can be read.\n");
        }
        md.append("\n");
    }

    /** Any line of mock.log starting with the prefix, or null. */
    private static String mockLine(Path runDir, String prefix) {
        try {
            Path log = runDir.resolve("mock.log");
            if (!Files.exists(log)) {
                return null;
            }
            for (String line : Files.readAllLines(log)) {
                if (line.startsWith(prefix)) {
                    return line;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * @param cpus this row's OWN core count, or -1 when it is not known. A percentage is only
     *             printed when the denominator belongs to the process that produced the CPU
     *             figure — the mock's row used to divide by the injector's core count, which is a
     *             different process and, with {@code --mock-url}, usually a different machine.
     */
    private static void cpuRow(StringBuilder md, String label, double cpuSeconds, double windowSeconds, int cpus) {
        if (cpuSeconds < 0) {
            return;
        }
        double cores = windowSeconds <= 0 ? -1 : cpuSeconds / windowSeconds;
        // Under a second the ratio is dominated by whichever sample the clocks landed on — a 2 ms
        // window has reported "98% of 10 cpus" off 0.02 core-seconds. Flagged rather than hidden.
        String caveat = windowSeconds > 0 && windowSeconds < 1 ? " ⚠ window under 1s — ratio unreliable" : "";
        md.append("| ").append(label).append(caveat).append(" | ").append(fixed(cpuSeconds, 2)).append(" | ")
                .append(windowSeconds <= 0 ? "?" : fixed(windowSeconds, 2)).append(" | ")
                .append(cores < 0 ? "?" : "**" + fixed(cores, 2) + "**").append(" | ")
                .append(cores < 0 || cpus <= 0 ? "n/a" : fixed(100 * cores / cpus, 0) + "%").append(" |\n");
    }

    /** Pull one plain {@code key=<number>} out of the child's summary line; -1 if absent. */
    private static double keyValueNumber(String summary, String key) {
        if (summary == null) {
            return -1;
        }
        for (String token : summary.split("\\s+")) {
            if (token.startsWith(key + "=")) {
                try {
                    return Double.parseDouble(token.substring(key.length() + 1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Pull one {@code key=<text>} out of a child line verbatim; {@code "?"} if absent. For the
     * fields that are not numbers — {@code suites=2/3} being the one that matters.
     */
    private static String keyValueText(String line, String key) {
        if (line == null) {
            return "?";
        }
        for (String token : line.split("\\s+")) {
            if (token.startsWith(key + "=")) {
                return token.substring(key.length() + 1);
            }
        }
        return "?";
    }

    /**
     * True only when the child explicitly said {@code key=true}. Absent reads as false, which is
     * what a digest built from a run predating the flag should say.
     */
    private static boolean keyValueFlag(String summary, String key) {
        if (summary == null) {
            return false;
        }
        for (String token : summary.split("\\s+")) {
            if (token.startsWith(key + "=")) {
                return Boolean.parseBoolean(token.substring(key.length() + 1));
            }
        }
        return false;
    }

    /** Pull one {@code key=<nanos>} out of the child's summary line, as seconds; -1 if absent. */
    private static double keyValueNanos(String summary, String key) {
        if (summary == null) {
            return -1;
        }
        for (String token : summary.split("\\s+")) {
            if (token.startsWith(key + "=")) {
                try {
                    long nanos = Long.parseLong(token.substring(key.length() + 1));
                    return nanos < 0 ? -1 : nanos / 1e9;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static String fixed(double value, int decimals) {
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
    }

    /**
     * JVM options on the child's command line that the harness does not emit itself — i.e. what
     * {@code --jvm-flag} added. Recognised by exclusion so that an option the harness later grows
     * shows up here rather than being silently hidden.
     */
    private static String extraJvmFlags(RunInfo info) {
        return info.extraJvmFlags() == null ? "" : String.join(" ", info.extraJvmFlags());
    }

    /** The classpath the child ran with, lifted back out of its recorded command line. */
    private static String childClasspath(RunInfo info) {
        List<String> command = info.command();
        int at = command.indexOf("-cp");
        return at >= 0 && at + 1 < command.size() ? command.get(at + 1) : "";
    }

    /**
     * What a Runner-suite workload actually executed, and whether all of it passed.
     *
     * <p><b>Without this the digest cannot say a suite run failed.</b> A {@code Suite} reports a
     * failed scenario in its result rather than by throwing, so the child's own summary line —
     * which counts thrown iterations — reads {@code errors=0} for a suite in which everything
     * failed. The digest would then describe heap, GC and retention for a two-hour run that
     * measured almost nothing, with no field anywhere saying so. The workload prints these counts
     * for exactly that reason; the panel exists so they survive into the one artifact
     * {@code collect.sh} pulls by default.
     *
     * <p>Cumulative and written per suite, so a run killed by its timeout still reports the totals
     * up to the last boundary it crossed.
     */
    static void appendSuiteOutcome(StringBuilder md, Path runDir) {
        String suite = childLine(runDir,
                io.karatelabs.profiling.workload.SuiteSoakWorkload.SUITE_PREFIX);
        if (suite == null) {
            return;
        }
        long scenarios = (long) keyValueNumber(suite, "scenarios");
        long expected = (long) keyValueNumber(suite, "expected");
        long passed = (long) keyValueNumber(suite, "passed");
        long failed = (long) keyValueNumber(suite, "failed");
        md.append("## Suite outcome\n\n");
        md.append("| | |\n|---|---:|\n");
        md.append("| suites completed | ").append(keyValueText(suite, "suites")).append(" |\n");
        md.append("| scenarios run / generated | ").append(scenarios).append(" / ")
                .append(expected < 0 ? "?" : Long.toString(expected)).append(" |\n");
        md.append("| passed / failed | ").append(passed).append(" / **").append(failed).append("** |\n");
        md.append("| reports | ").append(keyValueText(suite, "reports")).append(" |\n");
        md.append("| elapsed | ").append((long) keyValueNumber(suite, "elapsedMs")).append(" ms |\n\n");
        // Checked before failures, because it is the more deceptive of the two: a scenario that
        // never ran cannot fail, so every other signal in this digest — including the
        // reconciliation below, which would compare zero against zero and call it agreement —
        // reads healthy. karate-core drops Gherkin it cannot parse instead of rejecting the file,
        // so "the features are valid and hold no scenarios" is a reachable state.
        if (scenarios <= 0) {
            md.append("> **This run executed no scenarios at all, and nothing failed — because ")
                    .append("nothing ran.** Unparseable Gherkin is dropped rather than rejected, so a ")
                    .append("suite of valid-looking features can hold none. Every panel in this digest ")
                    .append("describes an idle JVM. Nothing here is a measurement.\n\n");
        } else if (expected > 0 && scenarios < expected) {
            md.append("> **").append(expected - scenarios).append(" generated scenarios never ran.** ")
                    .append("They did not fail — they were not executed, which is the failure mode that ")
                    .append("leaves every other signal looking healthy. This run measured less than it ")
                    .append("claims; find out what was dropped before reading anything else.\n\n");
        }
        if (failed > 0) {
            md.append("> **").append(failed).append(" of ").append(scenarios).append(" scenarios failed (")
                    .append(scenarios > 0 ? fixed(100.0 * failed / scenarios, 2) : "?").append("%).** ")
                    .append("A failed scenario stops early, so it allocates less, retains less and opens ")
                    .append("fewer connections than a passing one — every panel above is flattered by ")
                    .append("it, in proportion to that percentage. Find the cause before reading on.\n\n");
        }
    }

    /**
     * The load numbers, when the run drove one — throughput and the response-time distribution the
     * client actually saw, next to what the mock says it did. Two runs are compared by diffing
     * this panel; see docs/PROFILING.md §10 for what a comparison of it can and cannot support.
     */
    private static void appendLoadProfile(StringBuilder md, Path runDir, RunInfo info) {
        io.karatelabs.profiling.LoadProfile.Stats stats =
                io.karatelabs.profiling.LoadProfile.extract(runDir, childClasspath(info));
        String mockConfig = io.karatelabs.profiling.LoadProfile.mockLine(runDir, "PROFILING-MOCK-CONFIG ");
        String mockStats = io.karatelabs.profiling.LoadProfile.mockLine(runDir, "PROFILING-MOCK-STATS ");
        if (stats == null && mockStats == null) {
            return;
        }
        md.append("## Load profile\n\n");
        if (stats != null) {
            md.append("Response times as the **client** saw them, from Gatling's own report ")
                    .append("(rendered by the parent after the child exited, so it costs the run nothing).\n\n");
            md.append("| | |\n|---|---:|\n");
            md.append("| requests | ").append(stats.total()).append(" |\n");
            md.append("| ok / ko | ").append(stats.ok()).append(" / **").append(stats.ko()).append("** |\n");
            md.append("| req/s | ").append(stats.perSecond())
                    .append(" (Gatling divides by a **whole-second** duration — read the mock's ")
                    .append("`servedPerSecond` below instead) |\n");
            md.append("| min / mean / max | ").append(stats.min()).append(" / ").append(stats.mean())
                    .append(" / ").append(stats.max()).append(" ms |\n");
            md.append("| p50 / p75 / p95 / p99 | ").append(stats.p50()).append(" / ").append(stats.p75())
                    .append(" / ").append(stats.p95()).append(" / ").append(stats.p99()).append(" ms |\n");
            md.append("| std dev | ").append(stats.stdDev()).append(" ms |\n\n");
            if (stats.ko() > 0) {
                md.append("> **This run had failures, so it is not a slower run — it is a run with holes.** ")
                        .append("Failed requests leave the sample and take the slow ones with them, which ")
                        .append("flatters every percentile above. Find the cause before reading anything else.\n\n");
            }
        }
        if (mockStats != null) {
            md.append("What the **mock** says it did. Read `serviceMicros*` in one direction only: ")
                    .append("rising proves the mock was the bottleneck, flat proves nothing, because its ")
                    .append("clock starts at handler entry and cannot see the accept queue.\n\n");
            md.append("```json\n").append(mockStats).append("\n```\n\n");
            if (mockConfig != null) {
                md.append("Its settings — check `maxIdleConnections` is above the user count, and that the ")
                        .append("kernel gave you the backlog that was asked for:\n\n```json\n")
                        .append(mockConfig).append("\n```\n\n");
            }
        }
        appendReconciliation(md, runDir, info, stats, mockStats);
        appendPortPressure(md, mockStats);
    }

    /**
     * Three counts of the same work, from three places that cannot see each other: what the harness
     * asked for, what Gatling says it drove, and what the mock says it served.
     *
     * <p><b>Reported, never asserted</b> — this document's whole doctrine, and the right one here:
     * a mismatch is a finding, not a failure. It is worth printing because the failure it catches
     * is silent. A connection refused before the handler is entered never reaches the mock's error
     * counter, and the matrix's zero-KO rows were read as "nothing was dropped" when what they
     * actually established was "nothing that reached Gatling was dropped". Requests missing from
     * the middle count are the only thing that separates those.</p>
     *
     * <p><b>The Runner lane has no Gatling report, and used to get no reconciliation at all</b> —
     * this returned early on a null {@code stats} and the check silently did not exist for the one
     * workload that runs for hours. There the middle count is derived: a passing scenario makes a
     * known number of requests, so {@code passed x requests-per-scenario} is what the mock should
     * have served. It is exact only when nothing failed, which is why the suite panel above refuses
     * to be read past a non-zero failure count.
     */
    private static void appendReconciliation(StringBuilder md, Path runDir, RunInfo info,
                                             io.karatelabs.profiling.LoadProfile.Stats stats, String mockStats) {
        long served = (long) io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "served");
        if (served < 0) {
            return;
        }
        if (stats == null) {
            appendSuiteReconciliation(md, runDir, served);
            return;
        }
        long driven = stats.ok() + stats.ko();
        long iterations = info.shape().isDurationBounded() ? -1 : info.shape().iterations();
        md.append("**Reconciliation.** Gatling drove **").append(driven).append("** requests (")
                .append(stats.ok()).append(" ok + ").append(stats.ko()).append(" ko); the mock served **")
                .append(served).append("**");
        if (iterations > 0) {
            md.append(", over ").append(iterations).append(" requested iterations (")
                    .append(String.format("%.2f", driven / (double) iterations)).append(" requests each)");
        }
        md.append(driven == served
                        ? " — **they agree**, so nothing was dropped between the injector and the handler.\n\n"
                        : " — **they disagree by " + Math.abs(driven - served) + "**. Requests that fail before "
                        + "handler entry are invisible to the mock's error counter, so read this before "
                        + "reading anything else.\n\n");
    }

    /** The Runner-lane half of {@link #appendReconciliation}: expected requests against served. */
    static void appendSuiteReconciliation(StringBuilder md, Path runDir, long served) {
        String suite = childLine(runDir,
                io.karatelabs.profiling.workload.SuiteSoakWorkload.SUITE_PREFIX);
        if (suite == null) {
            return;
        }
        long expected = (long) keyValueNumber(suite, "requests");
        long failed = (long) keyValueNumber(suite, "failed");
        if (expected < 0) {
            return;
        }
        // Zero against zero is not agreement, it is two counts of nothing — and printing "they
        // agree" for it puts a green tick on a run that executed no scenarios. The suite panel
        // above says why; this one must not contradict it.
        if (expected == 0) {
            md.append("**Reconciliation.** No passing scenarios, so there is nothing to reconcile ")
                    .append("— the mock served ").append(served).append(". Read the suite outcome above.\n\n");
            return;
        }
        md.append("**Reconciliation.** The suite's passing scenarios account for **").append(expected)
                .append("** requests; the mock served **").append(served).append("**");
        if (failed > 0) {
            md.append(" — but ").append(failed).append(" scenario(s) failed, and a failed scenario's")
                    .append(" request count is unknown, so these two are not comparable. Fix the failures")
                    .append(" first.\n\n");
            return;
        }
        md.append(served == expected
                ? " — **they agree**, so nothing was dropped between the suite and the handler.\n\n"
                : " — **they disagree by " + Math.abs(served - expected) + "**. With zero failures the "
                + "counts should match exactly; a surplus at the mock means retries or an extra call "
                + "site, a shortfall means requests that never reached the handler.\n\n");
    }

    /**
     * The run's own connection rate against what this host can sustain.
     *
     * <p>The karate arm opens one connection per iteration — counted, not assumed — and every one
     * of them occupies an ephemeral port for a TIME_WAIT after it closes. Exceed the host's rate
     * and the arm stalls waiting for the range to recycle, which docs/PROFILING.md §10 notes
     * "reads as 'Karate is slower'". §10 also notes the matrix has never actually tested that
     * failure mode: the 0 ms tier ran at roughly eight times the sustainable rate and passed only
     * because the burst was short enough that the range never filled.
     *
     * <p>Reported rather than enforced, and the wording separates a burst from a sustained rate,
     * because a short run over the ceiling is fine and a long one is not — that distinction is the
     * finding, and collapsing it into a pass/fail would throw it away.
     */
    /**
     * The whole port bitmap the mock counts into ({@code MockStats.PORT_WORDS} x 64).
     *
     * <p>The platform-independent lid, and the reason this check cannot depend on host discovery
     * alone: {@code HostFacts} returns -1 for the ephemeral range on any platform it cannot read,
     * which made saturation impossible to detect there — so the misleading rate this exists to
     * suppress was still printed, on exactly the machines that could say least about the run.
     * A count approaching this ceiling has stopped measuring connections whatever the host is.
     */
    private static final double MOCK_PORT_CEILING = 65536;

    static void appendPortPressure(StringBuilder md, String mockStats) {
        appendPortPressure(md, mockStats, io.karatelabs.profiling.HostFacts.read());
    }

    static void appendPortPressure(StringBuilder md, String mockStats,
                                   io.karatelabs.profiling.HostFacts.Network network) {
        double ports = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "distinctPeerPorts");
        double window = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "loadWindowSeconds");
        if (ports <= 0 || window <= 0) {
            return;
        }
        double rate = ports / window;
        double ceiling = network.sustainableConnectionsPerSecond();
        // Distinct ports is a SET size, and the set has a hard lid: the mock counts them in a
        // 65,536-bit bitmap, the kernel draws from a range no larger, and tcp_tw_reuse recycles
        // ports deliberately. So on a long run the count stops tracking connections and starts
        // reporting the size of the range — at which point the derived rate is not slightly off,
        // it is meaningless. Measured: a two-hour soak that opened ~700,000 connections reported
        // 32,256 ports and therefore "5 connections/s" against an actual ~99/s. Saying so is the
        // whole fix; there is no better count available, because a reused port is indistinguishable
        // from a repeated request on the same connection from the server's side.
        // Fail closed on an unknown range: fall back to the mock's own ceiling rather than
        // deciding the count cannot be saturated. The alternative — what this did first — is that
        // a platform HostFacts cannot read prints the misleading rate unchallenged.
        long range = network.portRangeSize();
        double ceilingForSaturation = range > 0 ? range : MOCK_PORT_CEILING;
        // A high count is not enough: ports can only be recycled once a run outlives TIME_WAIT, so
        // below that window the count is provably EXACT however large it is. Without this term the
        // check regressed the harness's own 0 ms tier — 8,000 connections in 1.9 s, on a host whose
        // range makes 4,096 the threshold — refusing a correct rate, suppressing the "offered Nx
        // the sustainable rate, survived on brevity rather than margin" warning that only the
        // unsaturated path prints, and asserting recycling that had not happened. An unknown
        // TIME_WAIT cannot rule recycling out, so there the count ratio decides alone.
        double timeWait = network.timeWaitSeconds();
        boolean couldRecycle = timeWait <= 0 || window > timeWait;
        boolean saturated = couldRecycle && ports >= ceilingForSaturation / 4.0;
        if (saturated) {
            md.append("**Connections.** ").append((long) ports)
                    .append(" distinct client ports over ").append(fixed(window, 1))
                    .append("s — but that is **").append(Math.round(ports * 100.0 / ceilingForSaturation))
                    .append("% of ").append(range > 0
                            ? "this host's " + range + "-port range"
                            : "the mock's " + (long) MOCK_PORT_CEILING + "-port counting ceiling "
                            + "(this host's own range could not be read)")
                    .append(", so the count has saturated and is a LOWER BOUND on "
                            + "connections, not a measurement.** This run outlived TIME_WAIT (")
                    .append(fixed(timeWait, 0)).append("s), so ports have been recycled")
                    .append(network.reuseNote() == null ? "" : " — " + network.reuseNote())
                    .append(" and no rate can be derived from the count; a run "
                            + "of any length converges on the range size. Read the connection rate "
                            + "from the workload's own shape instead (connections per iteration x "
                            + "iterations / window). This host: ").append(network.describe())
                    .append(". Port **exhaustion** is still visible where it matters — as errors at "
                            + "the mock and a stall that reads as client slowness.\n\n");
            return;
        }
        md.append("**Connections.** ").append((long) ports).append(" distinct client ports over ")
                .append(fixed(window, 1)).append("s — **").append(fixed(rate, 0))
                .append(" connections/s**. This host: ").append(network.describe()).append(".");
        if (ceiling > 0 && rate > ceiling) {
            md.append(" **This run offered ").append(fixed(rate / ceiling, 1))
                    .append("x the sustainable rate.** It survived on brevity rather than margin: ")
                    .append((long) ports).append(" connections stayed under the ")
                    .append(network.portRangeSize()).append("-port range, so the range never filled. ")
                    .append("A longer run at this rate exhausts it, and the stall reads as the client ")
                    .append("being slow.");
        } else if (ceiling > 0) {
            md.append(" Under the ceiling, so port exhaustion is not in play here.");
        }
        md.append("\n\n");
    }

    /**
     * The descriptor rows, read over the loaded probes only.
     *
     * <p><b>Peak, not last.</b> Comparing the first reading with the last is how a leak hides: a
     * run whose descriptors climb toward {@code ulimit -n} all soak and fall back when the client
     * shuts down ends where it started, and a first-versus-last row calls that flat. The peak is
     * the number that cannot be walked back.
     *
     * <p>A tolerance, because descriptors jitter by a few on their own — JFR chunk rotation, a DNS
     * lookup — and a zero-tolerance comparison prints a confident leak claim from noise.
     */
    private static void appendDescriptors(StringBuilder md, List<long[]> loaded) {
        List<long[]> known = loaded.stream().filter(s -> s[2] >= 0).toList();
        if (known.isEmpty()) {
            return;
        }
        long firstFds = known.get(0)[2];
        long lastFds = known.get(known.size() - 1)[2];
        long peakFds = known.stream().mapToLong(s -> s[2]).max().orElse(firstFds);
        long tolerance = Math.max(4, firstFds / 20);
        row(md, "open fds, first / peak / last", firstFds + " / " + peakFds + " / " + lastFds
                + (peakFds > firstFds + tolerance ? " — **rising, investigate**" : " — flat"));
        long reclaimable = known.stream().filter(s -> s[3] >= 0)
                .mapToLong(s -> s[2] - s[3]).max().orElse(-1);
        if (reclaimable >= 0) {
            row(md, "closed by the probe's GC", reclaimable + " at most");
        }
        md.append("\n> **Descriptors are the other half of this panel.** An unreleased HTTP client "
                + "leaks a socket, and a socket is a descriptor long before it is a noticeable "
                + "number of bytes — so the live set can sit flat while the process walks toward "
                + "`ulimit -n`. These are sampled BEFORE the probe's forced collections: since "
                + "JDK 13 an unreachable socket is closed when it is collected, so a post-GC "
                + "reading would report the low-water mark of exactly the population a leak hunt "
                + "is looking for. The *closed by the probe's GC* row is that difference — a large "
                + "one means many sockets are held only until something collects them, which is a "
                + "finding in its own right even when the total is flat. A pooled run holds "
                + "descriptors on purpose, so a flat non-zero count is the healthy shape.\n");
    }

    /**
     * The value of a {@code -Dkey=value} in the child's command line, or null if it is absent.
     *
     * <p><b>The LAST match, because that is what the JVM used.</b> A property can appear twice —
     * {@code -Dkey=} passed through by the operator and then the harness's own canonical one — and
     * returning the first would make the digest record a value the run did not run with.
     */
    private static String flagValue(java.util.List<String> command, String prefix) {
        String found = null;
        for (String argument : command) {
            if (argument.startsWith(prefix)) {
                found = argument.substring(prefix.length());
            }
        }
        return found;
    }

    /**
     * The {@code build:} line from {@code run-meta.txt}, or null when there is none — runs taken
     * before the stamp existed legitimately have no line, and an absent row is the honest way to
     * say so rather than printing "(unknown)" as though the build had been looked up and lost.
     *
     * <p>Read from the sibling file rather than threaded through {@link RunInfo} so the value has
     * exactly one producer: whatever the run recorded is what the digest repeats.
     */
    private static String buildOf(Path runDir) {
        return metaOf(runDir, "build:");
    }

    /**
     * A labelled line from {@code run-meta.txt}, or null when absent, empty or {@code (none)} —
     * run-meta prints {@code (none)} so its layout is stable, but a digest row that says
     * "(none)" reads as information and is only noise.
     */
    private static String metaOf(Path runDir, String label) {
        Path meta = runDir.resolve("run-meta.txt");
        if (!Files.isReadable(meta)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(meta)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(label)) {
                    String value = line.substring(label.length()).trim();
                    return value.isEmpty() || value.equals("(none)") ? null : value;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static void appendRunSummary(StringBuilder md, Path runDir, RunInfo info) {
        RunShape shape = info.shape();
        md.append("## Run summary\n\n");
        md.append("| | |\n|---|---|\n");
        String childSummary = findChildSummary(runDir);
        row(md, "workload", info.workload());
        // The build that produced the run, echoed from run-meta.txt. `compare` reads digests and
        // nothing else, so a commit recorded only in run-meta.txt cannot reach a derived table —
        // and a results document written from those tables then has no way to say which source
        // produced them. That gap already cost one published-then-retracted provenance claim.
        String build = buildOf(runDir);
        if (build != null) {
            row(md, "build", build);
        }
        row(md, "outcome", outcome(info, childSummary));
        row(md, "exit code", String.valueOf(info.exitCode()));
        row(md, "threads", String.valueOf(shape.threads()));
        row(md, "bound", shape.isDurationBounded()
                ? "duration=" + RunShape.format(shape.duration())
                : "iterations=" + shape.iterations());
        // Only when the body-size tier is in use. An absent row means the default 34-byte payload,
        // which is what every figure published before this tier existed was measured against.
        String bodyBytes = flagValue(info.command(), "-Dkarate.profiling.bodyBytes=");
        if (bodyBytes != null) {
            row(md, "body bytes", bodyBytes);
        }
        // A pooled arm and an unpooled one are different client configurations, and the plan runs
        // both at the same tier. Recorded so `compare` can refuse to average them together.
        row(md, "pooled", String.valueOf(
                "true".equals(flagValue(info.command(), "-Dkarate.profiling.pooled="))));
        // The js-* A/B provenance, echoed from run-meta like `build` is: which karate-js build
        // ran, which cell of which matrix this run is, and which experimental -D properties
        // shaped it. `compare` reads digests and nothing else, so anything that decides whether
        // two runs may pair has to be here, not only in run-meta.
        String jsJar = metaOf(runDir, "js jar:");
        if (jsJar != null) {
            row(md, "js jar", jsJar);
        }
        String runTag = metaOf(runDir, "run tag:");
        if (runTag != null) {
            row(md, "run tag", runTag);
        }
        String sysprops = metaOf(runDir, "sysprops:");
        if (sysprops != null) {
            row(md, "sysprops", "`" + sysprops + "` — experiment configuration; two runs that"
                    + " differ here are different cells");
        }
        String jfrMode = metaOf(runDir, "jfr:");
        if (jfrMode != null && jfrMode.startsWith("off")) {
            row(md, "jfr", jfrMode);
        }
        // Only claim exclusion when the recording was actually delayed. A warmup under JFR's
        // one-second delay floor gets no delay, and a self-driving workload runs no warmup at
        // all, yet this row asserted "excluded" for both.
        boolean delayed = String.join(" ", info.command()).contains(",delay=");
        row(md, "warmup", RunShape.format(shape.warmup()) + warmupExclusion(runDir, shape, delayed));
        row(md, "-Xmx", info.jvm().xmx());
        row(md, "collector", info.jvm().gc().name().toLowerCase()
                + " → `" + String.join(" ", info.jvm().flags(Runtime.version().feature())) + "`");
        row(md, "jdk", Runtime.version().toString());
        int childCpus = (int) keyValueNumber(childSummary, "cpus");
        int hostCpus = Runtime.getRuntime().availableProcessors();
        row(md, "os / cpus", System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + " / " + (childCpus > 0 ? childCpus : hostCpus)
                + (childCpus > 0 && childCpus != hostCpus
                        ? " **(child sees " + childCpus + " of the host's " + hostCpus
                          + " — this run was deliberately constrained)**" : ""));
        // Any JVM option the operator added by hand. In the run summary rather than buried in
        // run-meta.txt because it is the strongest reason two digests are not comparable, and the
        // note below already tells the reader which fields must match before diffing.
        String extra = extraJvmFlags(info);
        if (!extra.isEmpty()) {
            row(md, "extra jvm flags", "`" + extra + "` — **a constrained run; do not diff this "
                    + "against an unconstrained one**");
        }
        // The measured window's own numbers, first-class rather than only inside the backticked
        // child summary below — `compare`'s elapsed-time lane reads these rows, and a table
        // derived from a log line is one format drift away from silently empty.
        double completed = keyValueNumber(childSummary, "completed");
        if (completed >= 0) {
            row(md, "completed", (long) completed + (shape.isDurationBounded() ? " iterations"
                    : " of " + shape.iterations() + " requested iterations"));
        }
        double elapsedMs = keyValueNumber(childSummary, "elapsedMs");
        if (elapsedMs >= 0) {
            row(md, "elapsed", (long) elapsedMs + " ms (measured window)");
        }
        double errors = keyValueNumber(childSummary, "errors");
        if (errors >= 0) {
            row(md, "errors", String.valueOf((long) errors));
        }
        double cpuSeconds = keyValueNanos(childSummary, io.karatelabs.profiling.SelfCpu.CPU_NANOS);
        if (cpuSeconds >= 0) {
            row(md, "cpu", fixed(cpuSeconds, 1) + " core-s over the measured window");
        }
        row(md, "heap dump", info.heapDump() ? "**yes — the run OOM'd**" : "no");
        md.append("\n");

        if (childSummary != null) {
            md.append("Child reported: `").append(childSummary).append("`\n\n");
        }
        md.append("> Compare digests only when `-Xmx`, `collector` and `jdk` match. "
                + "Differences in any of those change what the numbers below mean.\n\n");
    }

    /**
     * Whether the warmup was really kept out of the recording, with the number.
     *
     * <p>JFR's {@code delay=} is measured from <b>JVM launch</b>, and the child does not begin its
     * warmup until class loading, workload lookup and {@code setup()} are done. So a delay equal
     * to the warmup does not exclude the warmup — it excludes the first
     * {@code warmup} milliseconds of the <em>process</em>, and everything the child spent getting
     * started is subtracted from the allowance. With a 5 s warmup and 700 ms of startup, the last
     * ~700 ms of warmup is recorded; if setup outlasts the warmup, all of it is. The row used to
     * claim exclusion unconditionally, so two digests could carry different amounts of warmup
     * while both said "excluded".
     *
     * <p>The child reports its own uptime at the moment the measured window opens, so the shortfall
     * is arithmetic rather than an assumption.
     */
    private static String warmupExclusion(Path runDir, RunShape shape, boolean delayed) {
        if (shape.warmup().isZero()) {
            return " (none)";
        }
        if (!delayed) {
            return " (**NOT excluded** — no JFR delay was applied, so this warmup is inside the "
                    + "recording; JFR's minimum delay is 1s)";
        }
        String line = childLine(runDir, MEASURING_PREFIX);
        double sinceLaunch = keyValueNumber(line, "sinceJvmStartMs");
        if (sinceLaunch < 0) {
            return " (recording delayed past it — but `delay=` runs from JVM launch, so any child "
                    + "startup time ate into the exclusion; this run did not report how much)";
        }
        long overrun = (long) sinceLaunch - shape.warmup().toMillis();
        return overrun <= 0
                ? " (excluded — measurement began " + (long) sinceLaunch + " ms after JVM launch, "
                  + "within the " + shape.warmup().toMillis() + " ms delay)"
                : " (**" + overrun + " ms of it WAS recorded** — `delay=` runs from JVM launch and "
                  + "measurement began " + (long) sinceLaunch + " ms in, past the "
                  + shape.warmup().toMillis() + " ms delay. Raise `--warmup` above the child's "
                  + "startup cost, and do not diff this against a run with a different overrun)";
    }

    /**
     * Exit code alone cannot answer "did it OOM" — a swallowed worker error can still
     * exit 0. The heap dump is the primary signal; the child's own stdout is the backstop.
     */
    private static String outcome(RunInfo info, String childSummary) {
        if (info.timedOut()) {
            return "**TIMED OUT** — child was dumped and killed; see `jcmd-jfr-dump.log` and the "
                    + "thread dump at the end of `stdout.log`";
        }
        if (info.heapDump()) {
            return "**OOM** — heap dump written";
        }
        // Before the exit code, because a truncated run also exits non-zero and "completed with
        // errors" would be the less useful of the two readings.
        if (keyValueFlag(childSummary, "abortedInWarmup")) {
            return "**ABORTED IN WARMUP** — the warmup did not finish cleanly, so no measured "
                    + "window was opened. Nothing below describes a measurement";
        }
        if (keyValueFlag(childSummary, "truncated")) {
            // Deliberately not "a shorter window". Elapsed here is the requested window plus the
            // join grace, and JFR keeps recording until the JVM exits — so the window is the
            // wrong one, which is not the same as being a short one, and stragglers may have
            // contributed to it after the nominal end.
            return "**TRUNCATED** — workers were still running when the join deadline passed. The "
                    + "window below is not the one that was requested: it runs past the nominal "
                    + "end by up to the join grace, and unfinished work may have contributed to "
                    + "it. Do not compare this run against a completed one";
        }
        return info.exitCode() == 0 ? "completed" : "completed with errors";
    }

    /**
     * True when this run's own JVM flags turned the named event off — i.e. the panel below is
     * empty by instruction, not by observation. Every "no samples" message that does not check
     * this hands the reader a diagnosis of the wrong thing: {@code --soak} disables both samplers,
     * and the resulting panels used to blame virtual threads and an absent recording.
     */
    private static boolean eventDisabled(RunInfo info, String event) {
        return String.join(" ", info.command()).contains(event + "#enabled=false");
    }

    private static void appendAllocationBySite(StringBuilder md, Data data, RunInfo info) {
        md.append("## Allocation by site\n\n");
        md.append("`jdk.ObjectAllocationSample`, weighted bytes, collapsed to the topmost "
                + "`io.karatelabs.*` frame. This answers *what is churning*, not *what is retained* — "
                + "see the heap-after-GC series below before concluding anything about a leak. "
                + "Allocation sampling does attribute correctly across virtual threads.\n\n");
        if (data.allocationByClass.isEmpty()) {
            md.append(eventDisabled(info, "jdk.ObjectAllocationSample")
                    ? "_`jdk.ObjectAllocationSample` was **disabled for this run** — `--soak` turns "
                      + "it off, because over hours the sampler is a large part of the recording "
                      + "and a soak asks about retention rather than churn. This panel is empty by "
                      + "instruction; re-run without `--soak` to populate it._\n\n"
                    : "_No allocation samples in the recording._\n\n");
            return;
        }
        md.append("Total sampled weight: ").append(bytes(data.allocationTotal))
                .append(" over ").append(data.allocationSamples).append(" samples.\n\n");
        md.append("### By allocating site\n\n");
        table(md, "site", data.allocationBySite, data.allocationTotal, Unit.BYTES);
        md.append("### By allocated type\n\n");
        table(md, "type", data.allocationByClass, data.allocationTotal, Unit.BYTES);
    }

    private static void appendHotMethods(StringBuilder md, Data data, RunInfo info) {
        md.append("## Hot methods\n\n");
        md.append("> **Read with care.** `jdk.ExecutionSample` only walks platform threads, and "
                + "Karate runs every scenario on a virtual thread, so for any Runner-driven workload "
                + "this panel severely under-samples scenario code. A method missing from it proves "
                + "nothing. Prefer *Allocation by site* above. This panel is trustworthy for the mock "
                + "JVM (`--record mock`) and other platform-thread paths.\n\n");
        if (data.cpuBySite.isEmpty()) {
            md.append(eventDisabled(info, "jdk.ExecutionSample")
                    ? "_`jdk.ExecutionSample` was **disabled for this run** by `--soak`. This panel "
                      + "is empty by instruction, not because of virtual threads — note that under "
                      + "`--soak --record mock` that disable applies to the mock too, which is the "
                      + "one JVM this panel would have been trustworthy for._\n\n"
                    : "_No execution samples in the recording (see the caveat above — this is "
                      + "expected for virtual-thread workloads)._\n\n");
            return;
        }
        md.append("Samples: ").append(data.cpuSamples).append("\n\n");
        table(md, "site", data.cpuBySite, data.cpuSamples, Unit.COUNT);
    }

    /**
     * What actually survived a full collection, sampled through the run. This is the leak panel;
     * the heap-after-GC series below it is not, and believing otherwise cost a published
     * conclusion — see {@code Child.startLiveSetProbe}.
     *
     * <p>Only present for {@code --soak}, because the probe forces a stop-the-world GC and that
     * would distort any run whose question is throughput or pause time.
     */
    // Package-private for LiveSetPanelTest, which drives it with synthetic series: the
    // only way to prove the panel reports a RISE, since a healthy run cannot produce one.
    static void appendLiveSet(StringBuilder md, Path runDir) {
        List<long[]> series = new ArrayList<>();
        List<long[]> labelledFloors = new ArrayList<>();
        List<long[]> labelledPeaks = new ArrayList<>();
        int invalid = 0;
        try {
            for (String line : Files.readAllLines(runDir.resolve("stdout.log"))) {
                if (line.startsWith(LIVE_SET_PREFIX)) {
                    String rest = line.substring(LIVE_SET_PREFIX.length());
                    long elapsed = (long) keyValueNumber(rest, "elapsedMs");
                    long live = (long) keyValueNumber(rest, "liveBytes");
                    // -1 both when the platform will not report it and when the digest is reading
                    // a run that predates the field, which are the same thing for display: no
                    // column rather than a column of zeros that looks like a process holding
                    // nothing open.
                    long fds = (long) keyValueNumber(rest, "fds");
                    long fdsAfterGc = (long) keyValueNumber(rest, "fdsAfterGc");
                    // The last probe runs with the load stopped, so it is not part of the trend —
                    // it is the cleanest single number the run produces and the worst possible
                    // endpoint for a slope. Kept and shown, excluded from drift and peak.
                    long isFinal = rest.contains("final=true") ? 1 : 0;
                    // A reading that predates the valid= flag has no claim either way; only an
                    // explicit valid=false is treated as a failed collection.
                    if (rest.contains("valid=false")) {
                        invalid++;
                    }
                    if (elapsed >= 0 && live >= 0) {
                        long[] point = {elapsed, live, fds, fdsAfterGc, isFinal};
                        series.add(point);
                        // Boundary readings the workload asked for by name, so the digest does not
                        // have to guess which timed probe sat closest to a suite end.
                        String label = keyValueText(rest, "label");
                        if (label.endsWith("-floor")) {
                            labelledFloors.add(point);
                        } else if (label.endsWith("-peak")) {
                            labelledPeaks.add(point);
                        }
                    }
                }
            }
        } catch (IOException e) {
            return;
        }
        if (series.isEmpty()) {
            return;
        }
        md.append("## Live set (after forced full GC)\n\n");
        if (invalid > 0) {
            // Fail closed: no table, no drift, no series. The previous version printed this
            // warning and then drew every point anyway under the heading "This is the leak
            // panel" — which is the same authoritative-looking wrong answer the panel exists to
            // prevent, with a disclaimer on top that automation cannot read and a hurried human
            // will skip. There is nothing to plot: the readings are resident heap.
            md.append("> ⛔ **REFUSED — no live-set series is shown.** ").append(invalid)
                    .append(" of ").append(series.size())
                    .append(" probes forced a collection that did not happen, so the readings are "
                            + "whatever was resident (garbage included) and mean nothing about "
                            + "retention. The usual cause is `-XX:+DisableExplicitGC`, which makes "
                            + "`System.gc()` a no-op; `-XX:+ExplicitGCInvokesConcurrent` breaks the "
                            + "probe too and the child warns about both at startup. Re-run without "
                            + "them, or take two class histograms minutes apart:\n>\n"
                            + "> ```bash\n> jcmd <child-pid> GC.run && jcmd <child-pid> "
                            + "GC.class_histogram\n> ```\n\n");
            return;
        }
        md.append("**This is the leak panel.** Each row is the heap in use immediately after two "
                + "forced full collections, so it is what genuinely survived rather than what "
                + "happened to be resident. A rising series here is retention. A rising series in "
                + "*Heap after GC* below is not, on its own — that one includes promoted garbage "
                + "that a young-only collector never revisits.\n\n");
        // The trend is read over the LOADED probes only. The final probe is taken after the
        // workload has stopped, so it drops — on a validation run the live set fell 22.1 MB to
        // 20.1 MB and descriptors 200 to 131 purely from shutdown. Ending the slope there reports
        // that artifact as drift, and worse, hides a genuine climb that snapped back at teardown.
        List<long[]> loaded = series.stream().filter(s -> s[4] == 0).toList();
        if (loaded.isEmpty()) {
            loaded = series;
        }
        long first = loaded.get(0)[1];
        long last = loaded.get(loaded.size() - 1)[1];
        long min = loaded.stream().mapToLong(s -> s[1]).min().orElse(0);
        long max = loaded.stream().mapToLong(s -> s[1]).max().orElse(0);
        boolean haveFds = loaded.stream().anyMatch(s -> s[2] >= 0);
        List<Long> boundaries = suiteBoundaries(runDir);
        md.append("| | |\n|---|---|\n");
        row(md, "probes", series.size() + (loaded.size() != series.size()
                ? " (" + loaded.size() + " under load, 1 after shutdown)" : ""));
        row(md, "first / last", bytes(first) + " / " + bytes(last));
        row(md, "min / max", bytes(min) + " / " + bytes(max));
        if (boundaries.size() >= 2) {
            appendFloors(md, loaded, boundaries, labelledFloors, labelledPeaks);
        } else {
            row(md, "drift", (last >= first ? "+" : "") + bytes(last - first)
                    + (first > 0 ? " (" + Math.round((last - first) * 100.0 / first) + "%)" : "")
                    + (loaded.size() != series.size() ? ", under load" : ""));
        }
        if (haveFds) {
            appendDescriptors(md, loaded);
        }
        md.append("\n| elapsed | live set |").append(haveFds ? " open fds | after GC |\n|---|---|---|---|\n" : "\n|---|---|\n");
        for (long[] point : series) {
            md.append("| ").append(point[0] / 1000).append("s")
                    .append(point[4] == 1 ? " *(load stopped)*" : "")
                    .append(" | ").append(bytes(point[1])).append(" |");
            if (haveFds) {
                md.append(point[2] >= 0 ? " " + point[2] + " |" : " — |");
                md.append(point[3] >= 0 ? " " + point[3] + " |" : " — |");
            }
            md.append("\n");
        }
        md.append("\n");
    }

    /**
     * The highest live-set reading inside each suite's own window.
     *
     * <p>Distinct from the labelled {@code suite-N-peak} probe, which is taken once the suite has
     * returned and therefore reads only what the {@code SuiteResult} still holds. Both are real;
     * the boundary one is the designed retention a caller keeps, this one is what the heap
     * actually had to accommodate, and sizing wants the larger.
     *
     * <p>Empty unless every suite has at least one probe, so a series too coarse to segment prints
     * no row rather than a row with gaps in it. Its caller is gated on two or more boundaries, so
     * a single-suite run still shows only the global {@code min / max} — which is the same number
     * there, since one suite's segment is the whole loaded series.
     */
    private static List<Long> suitePeaks(List<long[]> loaded, List<Long> boundaries) {
        List<Long> peaks = new ArrayList<>();
        long start = -1;
        for (long boundary : boundaries) {
            long from = start;
            long max = loaded.stream().filter(p -> p[0] > from && p[0] <= boundary)
                    .mapToLong(p -> p[1]).max().orElse(-1);
            if (max < 0) {
                return List.of();
            }
            peaks.add(max);
            start = boundary;
        }
        return peaks;
    }

    /**
     * The elapsed time of each completed suite, from the workload's own cumulative markers.
     *
     * <p>Only a repeated-suites run produces more than one. The two clocks — the probe's and the
     * workload's — both start within milliseconds of the child's measured window, and the numbers
     * being separated are half-hour suites, so no reconciliation is needed beyond reading them.
     */
    private static List<Long> suiteBoundaries(Path runDir) {
        List<Long> boundaries = new ArrayList<>();
        try {
            String prefix = io.karatelabs.profiling.workload.SuiteSoakWorkload.SUITE_PREFIX;
            for (String line : Files.readAllLines(runDir.resolve("stdout.log"))) {
                if (line.startsWith(prefix)) {
                    long elapsed = (long) keyValueNumber(line.substring(prefix.length()), "elapsedMs");
                    if (elapsed >= 0) {
                        boundaries.add(elapsed);
                    }
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        return boundaries;
    }

    /**
     * The leak reading for a repeated-suites run: the floor each suite returns to.
     *
     * <p><b>Global drift is the wrong question here, and answering it produced a false positive on
     * the first run that used this shape.</b> Retention climbs within a suite <em>by design</em> —
     * results are held until the suite ends — and collapses at the boundary. Comparing the first
     * probe with the last therefore measures one suite's designed ramp and reports it as a leak:
     * on a four-suite soak that read <b>+207.7 MB (36%)</b> for a run whose floors were flat.
     *
     * <p>What a leak actually looks like is a floor that steps up. So the floors are what gets
     * compared: the first probe after each boundary, which is the live set once a suite's
     * retention has gone. Each carries a little of the next suite's ramp — the probe lands
     * wherever the timer puts it — so the comparison is deliberately tolerant rather than exact.
     */
    /**
     * How much of the next suite's ramp an inferred floor can be carrying: one probe interval of
     * this run's own within-suite slope.
     *
     * <p>Measured rather than assumed, because the slope is what differs between workloads and the
     * interval is a flag. Taken as the steepest consecutive rise inside a suite — the ramp is what
     * dominates there, and using the steepest keeps the resulting tolerance conservative.
     */
    private static long inferredFloorCarry(List<long[]> loaded, List<Long> boundaries) {
        long steepestPerMs = 0;
        long interval = 0;
        for (int i = 1; i < loaded.size(); i++) {
            long[] previous = loaded.get(i - 1);
            long[] point = loaded.get(i);
            long gap = point[0] - previous[0];
            if (gap <= 0) {
                continue;
            }
            interval = Math.max(interval, gap);
            // Pairs that straddle a boundary describe the release, not the ramp.
            boolean straddles = boundaries.stream().anyMatch(b -> b > previous[0] && b <= point[0]);
            if (!straddles && point[1] > previous[1]) {
                steepestPerMs = Math.max(steepestPerMs, (point[1] - previous[1]) / gap);
            }
        }
        return steepestPerMs * interval;
    }

    private static void appendFloors(StringBuilder md, List<long[]> loaded, List<Long> boundaries,
                                     List<long[]> labelledFloors, List<long[]> labelledPeaks) {
        // Labelled readings when the workload asked for them — the only way to get the boundary
        // exactly, since a boundary probe is printed just before the suite marker and so lands
        // fractionally on the wrong side of it for any timestamp rule. Inference stays as the
        // fallback for runs that predate the labels.
        List<long[]> floors = new ArrayList<>(labelledFloors);
        boolean inferred = floors.isEmpty();
        if (inferred) {
            for (long boundary : boundaries) {
                loaded.stream().filter(p -> p[0] > boundary).findFirst().ifPresent(floors::add);
            }
        }
        row(md, "suites", boundaries.size() + " — read the floors below, not the drift");
        List<Long> peaks = suitePeaks(loaded, boundaries);
        if (!peaks.isEmpty()) {
            StringBuilder peakList = new StringBuilder();
            for (long peak : peaks) {
                peakList.append(peakList.isEmpty() ? "" : " / ").append(bytes(peak));
            }
            row(md, "peak within each suite", peakList
                    + " *(highest probe in the segment — sampled, so a lower bound on the true peak)*");
        }
        if (!labelledPeaks.isEmpty() && labelledPeaks.size() == floors.size()) {
            StringBuilder released = new StringBuilder();
            for (int i = 0; i < floors.size(); i++) {
                released.append(released.isEmpty() ? "" : " / ")
                        .append(bytes(labelledPeaks.get(i)[1] - floors.get(i)[1]));
            }
            row(md, "released at each suite end", released.toString()
                    + " — what the suite held when it returned, minus what was left once it was"
                    + " dropped; both measured at the boundary, not interpolated");
        }
        if (floors.size() < 2) {
            row(md, "floors", "only " + floors.size() + " probe landed after a suite ended — the "
                    + "series is too coarse to compare floors; shorten `-Dkarate.profiling.liveSetSeconds`");
            return;
        }
        StringBuilder list = new StringBuilder();
        for (long[] floor : floors) {
            list.append(list.isEmpty() ? "" : " / ").append(bytes(floor[1]));
        }
        long firstFloor = floors.get(0)[1];
        long lastFloor = floors.get(floors.size() - 1)[1];
        long delta = lastFloor - firstFloor;
        // Same shape of tolerance as the descriptor row: a few percent of the floor, so a probe
        // landing a minute deeper into the next suite cannot read as retention.
        long tolerance = Math.max(4L << 20, firstFloor / 20);
        // An INFERRED floor is whichever timed probe happened to land first after the boundary, so
        // it carries up to one probe interval of the next suite's ramp — and a different amount per
        // suite. On E1's own shape (~78 KB/s of designed ramp, 300 s interval) that is ~23 MB of
        // placement noise against a 4-8 MB tolerance: enough to print "rising, investigate" for a
        // run whose floors are flat, which is the false positive this panel was rewritten to stop
        // making. So the fallback widens the tolerance by what the placement can actually carry,
        // measured from this run's own within-suite slope. Labelled floors sit ON the boundary and
        // carry nothing, so they keep the tight tolerance.
        long carry = inferred ? inferredFloorCarry(loaded, boundaries) : 0;
        tolerance += carry;
        row(md, "floor after each suite", list.toString()
                + (inferred ? " *(inferred from timed probes — ±" + bytes(carry) + " of placement)*"
                : " *(measured at the boundary)*"));
        row(md, "floor drift", (delta >= 0 ? "+" : "") + bytes(delta)
                + (firstFloor > 0 ? " (" + Math.round(delta * 100.0 / firstFloor) + "%)" : "")
                + (delta > tolerance
                ? " — **rising, investigate**: something survived a suite"
                // Deliberately not "nothing survived a suite". What was measured is that the drift
                // is inside the tolerance printed beside it; on a 600 MB floor that admits ~30 MB,
                // which four suites leaking 7 MB each would fit inside. Say the measurement, not
                // the absolute it is tempting to read it as.
                : " — within tolerance (" + bytes(tolerance) + "), so nothing detectably survived a suite"));
    }

    private static final String LIVE_SET_PREFIX = "PROFILING-LIVE-SET ";
    private static final String MEASURING_PREFIX = "PROFILING-MEASURING ";

    private static void appendHeapAfterGc(StringBuilder md, Data data) {
        md.append("## Heap after GC\n\n");
        md.append("`jdk.GCHeapSummary`, filtered to `when = \"After GC\"` — what the heap held "
                + "after each collection.\n\n");
        md.append("- constant floor, any sawtooth amplitude → **churn** (allocates a lot, retains nothing)\n");
        md.append("- rising floor → **retention, _only if the old generation was actually "
                + "collected_** — see the warning below before concluding anything\n");
        md.append("- flat floor then an abrupt cliff → **live mid-copy** (one in-flight structure "
                + "larger than the heap; not a leak at all)\n\n");
        // A ratio, not a boolean. One incidental old-generation collection -- a Metadata-threshold
        // full GC in hour one of an eight-hour run -- used to suppress this warning entirely for a
        // floor that was still 99.9% young-only, i.e. still meaningless as a live-set series.
        long totalCollections = data.youngOnlyCollections + data.oldGenCollections;
        boolean effectivelyYoungOnly = totalCollections > 0
                && data.oldGenCollections * 1000L < totalCollections;
        if (effectivelyYoungOnly) {
            // The false positive that cost a published conclusion. Stated at the top of the panel
            // rather than as a footnote, because the series below is the thing being misread.
            md.append("> ⚠️ **This is not a live-set series, and a rise in it is not a leak.** ")
                    .append(data.youngOnlyCollections).append(" of ").append(totalCollections)
                    .append(" collections in this run were **young-generation only** (")
                    .append(data.oldGenCollections)
                    .append(" touched the old generation) — far too few for this series to "
                            + "represent a live set. Promoted garbage accumulates in the old "
                            + "generation "
                            + "and this floor climbs in a straight line whether or not anything is "
                            + "retained.\n>\n"
                            + "> It is the normal state of a soak: a collector sized far above the "
                            + "live set never reaches the occupancy threshold that would start a "
                            + "cycle. Measured, a one-hour run showed this floor going 11.4 MB → "
                            + "27.8 MB across 104,980 young pauses while the true live set, taken "
                            + "after a forced full GC, stayed flat at ~7 MB.\n>\n"
                            + "> **Read the live-set panel instead** (`--soak` records one), or "
                            + "take two class histograms minutes apart:\n>\n"
                            + "> ```bash\n> jcmd <child-pid> GC.run && jcmd <child-pid> "
                            + "GC.class_histogram\n> ```\n\n");
        }
        if (data.heapAfterGc.isEmpty()) {
            md.append("_No GC heap summaries in the recording._\n\n");
            return;
        }
        List<Sample> series = data.heapAfterGc;
        long first = series.get(0).value();
        long last = series.get(series.size() - 1).value();
        long min = series.stream().mapToLong(s -> s.value()).min().orElse(0);
        long max = series.stream().mapToLong(s -> s.value()).max().orElse(0);
        md.append("| | |\n|---|---|\n");
        row(md, "collections", String.valueOf(series.size()));
        row(md, "first floor", bytes(first));
        row(md, "last floor", bytes(last));
        row(md, "min / max floor", bytes(min) + " / " + bytes(max));
        row(md, "drift", (last >= first ? "+" : "") + bytes(last - first)
                + (first > 0 ? " (" + Math.round((last - first) * 100.0 / first) + "%)" : ""));
        md.append("\n");
        md.append("### Series\n\n```\n");
        md.append(sparkline(series)).append("\n```\n\n");
        Instant base = series.get(0).at();
        md.append("| elapsed | heap after GC |\n|---|---|\n");
        for (Sample s : sampleEvenly(series, 20)) {
            md.append("| ").append(Duration.between(base, s.at()).toSeconds())
                    .append("s | ").append(bytes(s.value())).append(" |\n");
        }
        md.append("\n");
    }

    private static void appendGcPauses(StringBuilder md, Data data) {
        md.append("## GC pauses\n\n");
        if (data.gcByCause.containsKey("System.gc()")) {
            // The live-set probe's own collections are in here. On a soak they are the largest
            // pauses by a wide margin — a full GC against ~1-2 ms young pauses — so max and part
            // of the total describe the instrument rather than the workload.
            md.append("> **The `System.gc()` rows below are the live-set probe's own forced full "
                    + "collections, not the workload's.** They are far longer than young pauses, "
                    + "so `max` and part of `total pause` here measure the instrument. If pause "
                    + "behaviour is the question, read a run without `--soak`.\n\n");
        }
        if (data.gcPauses.isEmpty()) {
            md.append("_No garbage collections in the recording._\n\n");
            return;
        }
        List<Long> nanos = data.gcPauses.stream().sorted().toList();
        long total = nanos.stream().mapToLong(Long::longValue).sum();
        md.append("A rise in pause *frequency* rather than duration usually means allocation "
                + "pressure, not retention.\n\n");
        md.append("| | |\n|---|---|\n");
        row(md, "collections", String.valueOf(nanos.size()));
        row(md, "total pause", millis(total));
        row(md, "p50 / p95 / max", millis(percentile(nanos, 50)) + " / "
                + millis(percentile(nanos, 95)) + " / " + millis(nanos.get(nanos.size() - 1)));
        md.append("\n");
        if (!data.gcByCause.isEmpty()) {
            md.append("| cause | count |\n|---|---|\n");
            data.gcByCause.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> md.append("| ").append(e.getKey()).append(" | ")
                            .append(e.getValue()).append(" |\n"));
            md.append("\n");
        }
    }

    private static void appendRetainedObjects(StringBuilder md, Data data, RunInfo info) {
        md.append("## Retained objects\n\n");
        // Whether the chains are present is knowable from the child's own command line, and
        // telling an operator to re-run with a flag they already used is how a panel loses its
        // credibility. The first soak did exactly that.
        // ...and under --record mock the child carries no JFR flags at all, so "absent" here means
        // "this command line is not the one that made the recording" rather than "the operator did
        // not ask for chains". Saying nothing beats saying the wrong thing.
        String childCommand = String.join(" ", info.command());
        boolean gcRoots = childCommand.contains("path-to-gc-roots=true");
        boolean childWasRecorded = childCommand.contains("StartFlightRecording");
        md.append("`jdk.OldObjectSample` — JFR's leak profiler: objects that survived a collection, "
                + "attributed to the stack that **allocated** them. That is the allocator, not the "
                + (gcRoots
                        ? "holder — but this run enabled `--gc-roots`, so reference chains were "
                          + "recorded; read them from the raw recording with "
                          + "`jfr print --events jdk.OldObjectSample run.jfr`.\n\n"
                        : childWasRecorded
                                ? "holder; re-run with `--gc-roots` to get reference chains.\n\n"
                                : "holder. This recording was made by another JVM (`--record mock`), "
                                  + "so whether it carries reference chains cannot be read off the "
                                  + "child's command line — check the recording itself.\n\n"));
        if (data.retainedByClass.isEmpty()) {
            md.append("_No old-object samples in the recording. For a short run this is normal — "
                    + "nothing survived long enough to be sampled._\n\n");
            return;
        }
        md.append("**Samples: ").append(data.retainedSamples).append("**");
        // The count decides whether the tables below mean anything, so it leads rather than
        // appearing as a footnote. A one-hour soak at ~9,900 iterations/s produced 19 — and 19
        // rows rendered as tidy percentages read as a finding. They are not one.
        if (data.retainedSamples < RETAINED_SAMPLES_FOR_CONFIDENCE) {
            md.append(" — **too few to attribute anything.** `jdk.OldObjectSample` samples "
                    + "sparsely by design, and a long soak does not reliably produce more: this "
                    + "is a leak *detector's* output, not a leak *locator's*. The shares below "
                    + "are over a handful of objects, and long-lived infrastructure threads "
                    + "(the harness's own progress reporter, JFR's writers) crowd out the "
                    + "workload because they survive everything.\n\n"
                    + "**Decide whether there is a leak from the live-set panel above** (a "
                    + "`--soak` run records one), never from the heap-after-GC series, which "
                    + "cannot tell retention from promoted garbage. Then name it with a class "
                    + "histogram:**\n\n"
                    + "```bash\njcmd <child-pid> GC.run && jcmd <child-pid> GC.class_histogram\n"
                    + "```\n\nTwo of those, minutes apart, name what grew — which is what this "
                    + "panel is trying and failing to do.");
        }
        md.append("\n\n");
        md.append("### By type\n\n");
        table(md, "type", data.retainedByClass, data.retainedTotal, Unit.COUNT);
        md.append("### By allocating site\n\n");
        table(md, "site", data.retainedBySite, data.retainedTotal, Unit.COUNT);
    }

    /**
     * Deliberately delegated rather than computed. There is no JDK API or CLI that reads an
     * {@code .hprof} — {@code jhat} was removed in Java 9 and {@code jmap -histo} only works
     * against a live process — so a histogram here would mean either a hand-rolled hprof
     * parser or a new dependency. Neither is worth it while JFR's own retention events
     * answer the memory questions this harness exists for.
     */
    private static void appendTopClasses(StringBuilder md, Path runDir, RunInfo info) {
        md.append("## Top classes (heap dump)\n\n");
        if (!info.heapDump()) {
            md.append("_No heap dump — the run did not OOM._\n\n");
            return;
        }
        Path dump = runDir.resolve("heapdump.hprof");
        md.append("Heap dump written: `").append(dump.getFileName()).append("` (")
                .append(bytes(size(dump))).append(").\n\n");
        md.append("Not summarised here — no JDK API or CLI reads an `.hprof` file. Open it in "
                + "Eclipse MAT or VisualVM. What to look for, in order:\n\n");
        md.append("1. **Class histogram** — is one collection type dominant by object count?\n");
        md.append("2. **Dominator tree** — what holds the largest retained size?\n");
        md.append("3. **Where is it rooted?** A thread stack means live-mid-copy, not a leak. "
                + "Karate runs scenarios on virtual threads, so an unmounted one roots through "
                + "`jdk.internal.vm.StackChunk` instead — same finding, different shape.\n");
        md.append("4. If it is a deep self-recursion, read the size decay down the frames: "
                + "geometric decay means level N *contains* level N+1.\n\n");
    }

    // ---------------------------------------------------------------- reading

    private static Data read(Path jfr) throws IOException {
        Data data = new Data();
        try (RecordingFile file = new RecordingFile(jfr)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                switch (type) {
                    case "jdk.ObjectAllocationSample" -> {
                        long weight = longValue(event, "weight", 1);
                        data.allocationTotal += weight;
                        data.allocationSamples++;
                        data.allocationByClass.merge(className(event, "objectClass"), weight, Long::sum);
                        data.allocationBySite.merge(site(event.getStackTrace()), weight, Long::sum);
                    }
                    case "jdk.ExecutionSample" -> {
                        data.cpuSamples++;
                        data.cpuBySite.merge(site(event.getStackTrace()), 1L, Long::sum);
                    }
                    case "jdk.GCHeapSummary" -> {
                        if ("After GC".equals(stringValue(event, "when"))) {
                            data.heapAfterGc.add(new Sample(event.getStartTime(), longValue(event, "heapUsed", 0)));
                        }
                    }
                    case "jdk.GarbageCollection" -> {
                        data.gcPauses.add(event.getDuration().toNanos());
                        data.gcByCause.merge(stringValue(event, "cause"), 1L, Long::sum);
                        // Whether anything collected the old generation decides whether the
                        // heap-after-GC floor means what the panel says it means.
                        if (YOUNG_ONLY_COLLECTORS.contains(stringValue(event, "name"))) {
                            data.youngOnlyCollections++;
                        } else {
                            data.oldGenCollections++;
                        }
                    }
                    case "jdk.OldObjectSample" -> {
                        // One sample = one count. This used to weight each sample by
                        // lastKnownHeapUsage — which is the size of the WHOLE HEAP when the
                        // sample was taken, not the size of the sampled object — and then render
                        // the sums under a column headed "bytes". A one-hour soak reported
                        // 163.6 MB of jdk.internal.vm.StackChunk "retained" in a JVM whose live
                        // set was 27 MB, and the seven types summed to 271 MB for the same
                        // reason. It also silently weighted late samples above early ones, since
                        // the heap is larger later. jdk.OldObjectSample carries no object size,
                        // so count is the only honest unit.
                        data.retainedSamples++;
                        data.retainedTotal++;
                        data.retainedByClass.merge(oldObjectClass(event), 1L, Long::sum);
                        data.retainedBySite.merge(site(event.getStackTrace()), 1L, Long::sum);
                    }
                    default -> {
                    }
                }
            }
        }
        data.heapAfterGc.sort(java.util.Comparator.comparing(Sample::at));
        return data;
    }

    /**
     * The topmost Karate frame, or the topmost frame overall when the stack never enters
     * Karate code (JIT threads, GC helpers and the like).
     */
    private static String site(RecordedStackTrace stack) {
        if (stack == null || stack.getFrames().isEmpty()) {
            return "(no stack)";
        }
        List<RecordedFrame> frames = stack.getFrames();
        for (RecordedFrame frame : frames) {
            String cls = frame.getMethod().getType().getName();
            if (cls.startsWith(KARATE_PREFIX)) {
                return shorten(cls) + "." + frame.getMethod().getName();
            }
        }
        RecordedFrame top = frames.get(0);
        return "(non-karate) " + shorten(top.getMethod().getType().getName()) + "." + top.getMethod().getName();
    }

    private static String shorten(String className) {
        int last = className.lastIndexOf('.');
        return last < 0 ? className : className.substring(last + 1);
    }

    private static String className(RecordedEvent event, String field) {
        try {
            if (event.hasField(field) && event.getClass(field) != null) {
                return event.getClass(field).getName();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "(unknown)";
    }

    /**
     * The sampled object's type name.
     *
     * <p>Navigating straight to {@code object.type.name} matters: asking for
     * {@code object.type} hands back a whole {@code RecordedObject} whose {@code toString}
     * is a multi-line dump of the class, its package, its module and its loader — which
     * renders one table row per twenty lines and makes the panel unreadable. JFR also
     * stores names in internal form, so slashes are normalised to dots.
     */
    private static String oldObjectClass(RecordedEvent event) {
        try {
            Object name = event.getValue("object.type.name");
            if (name != null) {
                return name.toString().replace('/', '.');
            }
        } catch (Exception ignored) {
            // older/newer recordings may shape this differently
        }
        return className(event, "objectClass").replace('/', '.');
    }

    private static long longValue(RecordedEvent event, String field, long fallback) {
        try {
            return event.hasField(field) ? event.getLong(field) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringValue(RecordedEvent event, String field) {
        try {
            if (event.hasField(field)) {
                Object value = event.getValue(field);
                return value == null ? "(none)" : value.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "(none)";
    }

    // ---------------------------------------------------------------- helpers

    private static void appendMissingRecording(StringBuilder md, Path runDir, Path jfr) {
        md.append("## No usable recording\n\n");
        md.append("`run.jfr` is ").append(Files.exists(jfr) ? "empty" : "missing")
                .append(". The JVM died without flushing. Recover from the chunk repository:\n\n");
        md.append("```bash\ncd ").append(runDir).append("\njfr assemble jfr-repo/ rescue.jfr\n")
                .append("jfr summary rescue.jfr\n```\n\n");
        md.append("If that also fails, check `stdout.log` — a run killed by the OS OOM-killer, "
                + "or one halted by `-XX:+ExitOnOutOfMemoryError`, leaves nothing to assemble.\n\n");
    }

    /**
     * @param unit BYTES for weighted panels (allocation, retention), COUNT for sample
     *             counts (CPU). Getting this wrong renders "512 B" where "512 samples"
     *             was meant, so it is explicit rather than inferred from magnitude.
     */
    private static void table(StringBuilder md, String label, Map<String, Long> counts, long total, Unit unit) {
        md.append("| ").append(label).append(" | ").append(unit == Unit.BYTES ? "bytes" : "samples")
                .append(" | share |\n|---|---:|---:|\n");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .forEach(e -> md.append("| `").append(e.getKey()).append("` | ")
                        .append(unit == Unit.BYTES ? bytes(e.getValue()) : String.valueOf(e.getValue()))
                        .append(" | ").append(share(e.getValue(), total)).append(" |\n"));
        if (counts.size() > TOP_N) {
            md.append("\n_").append(counts.size() - TOP_N).append(" further entries omitted._\n");
        }
        md.append("\n");
    }

    private enum Unit {
        BYTES,
        COUNT
    }

    private static String share(long value, long total) {
        return total <= 0 ? "-" : String.format("%.1f%%", value * 100.0 / total);
    }

    private static String bytes(long value) {
        long abs = Math.abs(value);
        if (abs < 1024) {
            return value + " B";
        }
        if (abs < 1024 * 1024) {
            return String.format("%.1f KB", value / 1024.0);
        }
        if (abs < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", value / (1024.0 * 1024));
        }
        return String.format("%.2f GB", value / (1024.0 * 1024 * 1024));
    }

    private static String millis(long nanos) {
        return String.format("%.1f ms", nanos / 1_000_000.0);
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /** Crude but readable — the shape is the message, not the precision. */
    private static String sparkline(List<Sample> series) {
        char[] ramp = {'_', '.', ':', '-', '=', '+', '*', '#', '%', '@'};
        long max = series.stream().mapToLong(s -> s.value()).max().orElse(1);
        List<Sample> points = sampleEvenly(series, 60);
        StringBuilder sb = new StringBuilder();
        for (Sample s : points) {
            int level = max <= 0 ? 0 : (int) Math.min(ramp.length - 1, s.value() * ramp.length / (max + 1));
            sb.append(ramp[level]);
        }
        return sb + "   (floor over time, max = " + bytes(max) + ")";
    }

    private static List<Sample> sampleEvenly(List<Sample> series, int count) {
        if (series.size() <= count) {
            return series;
        }
        List<Sample> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(series.get((int) ((long) i * (series.size() - 1) / (count - 1))));
        }
        return out;
    }

    private static void row(StringBuilder md, String key, String value) {
        md.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static boolean usable(Path jfr) {
        return Files.exists(jfr) && size(jfr) > 0;
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    /** The last stdout line starting with {@code prefix}, without it; null if absent. */
    private static String childLine(Path runDir, String prefix) {
        try {
            List<String> lines = Files.readAllLines(runDir.resolve("stdout.log"));
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).startsWith(prefix)) {
                    return lines.get(i).substring(prefix.length());
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String findChildSummary(Path runDir) {
        try {
            List<String> lines = Files.readAllLines(runDir.resolve("stdout.log"));
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).startsWith("PROFILING-SUMMARY ")) {
                    return lines.get(i).substring("PROFILING-SUMMARY ".length());
                }
            }
        } catch (IOException ignored) {
            // no stdout.log, or unreadable
        }
        return null;
    }

    private static final class Data {
        long allocationTotal;
        long allocationSamples;
        final Map<String, Long> allocationByClass = new LinkedHashMap<>();
        final Map<String, Long> allocationBySite = new LinkedHashMap<>();
        long cpuSamples;
        final Map<String, Long> cpuBySite = new LinkedHashMap<>();
        final List<Sample> heapAfterGc = new ArrayList<>();
        final List<Long> gcPauses = new ArrayList<>();
        final Map<String, Long> gcByCause = new TreeMap<>();
        long retainedTotal;
        long retainedSamples;
        long youngOnlyCollections;
        long oldGenCollections;
        final Map<String, Long> retainedByClass = new LinkedHashMap<>();
        final Map<String, Long> retainedBySite = new LinkedHashMap<>();
    }

    /**
     * Raw event instant, not a pre-computed offset. JFR does not hand events back in
     * strict chronological order, so anchoring "elapsed" to whichever event happened to
     * be read first yields negative offsets. The series is sorted and rebased after the
     * whole recording has been read.
     */
    private record Sample(Instant at, long value) {
    }

}

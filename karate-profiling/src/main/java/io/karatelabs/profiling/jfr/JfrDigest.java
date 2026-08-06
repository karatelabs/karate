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

    /** What the parent knows that the recording doesn't. */
    public record RunInfo(String workload, int exitCode, boolean timedOut, boolean heapDump,
                          RunShape shape, JvmConfig jvm, List<String> command) {
    }

    private JfrDigest() {
    }

    public static void write(Path runDir, RunInfo info) {
        StringBuilder md = new StringBuilder();
        md.append("# Profiling digest — ").append(info.workload()).append("\n\n");
        appendRunSummary(md, runDir, info);
        appendCpu(md, runDir);

        Path jfr = runDir.resolve("run.jfr");
        if (!usable(jfr)) {
            appendMissingRecording(md, runDir, jfr);
        } else {
            try {
                Data data = read(jfr);
                appendAllocationBySite(md, data);
                appendHotMethods(md, data);
                appendHeapAfterGc(md, data);
                appendGcPauses(md, data);
                appendRetainedObjects(md, data);
            } catch (Exception e) {
                md.append("## Recording could not be parsed\n\n```\n").append(e).append("\n```\n\n")
                        .append("Try `jfr summary ").append(jfr.getFileName()).append("`, or rescue from the ")
                        .append("chunk repository:\n\n```bash\njfr assemble jfr-repo/ rescue.jfr\n```\n\n");
            }
        }
        appendTopClasses(md, runDir, info);
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
        int cpus = Runtime.getRuntime().availableProcessors();
        md.append("## CPU headroom\n\n");
        md.append("What each process burned over its own window. `cores` near ").append(cpus)
                .append(" means this run measured the machine rather than what it was pointed at, and a ")
                .append("throughput comparison taken there is not a comparison of clients.\n\n")
                .append("The two windows are not the same window — for a `gatling-*` workload the ")
                .append("injector's spans its whole simulation, engine boot included, while the ")
                .append("mock's covers only the load. So the injector row **understates** its ")
                .append("load-window utilisation: read it as a floor, read each row against `cpus`, ")
                .append("and never one row against the other.\n\n");
        md.append("| process | CPU (core-s) | window (s) | cores busy | % of ").append(cpus)
                .append(" cpus |\n|---|---:|---:|---:|---:|\n");
        cpuRow(md, "workload (the injector)", childCpu, childWall, cpus);
        cpuRow(md, "mock (co-located)", mockCpu, mockWall, cpus);
        if (mockCpu >= 0) {
            md.append("\nThe mock shares these cores with the load driver, so its row **is** the ")
                    .append("co-location bias — a number rather than an argument. On a two-host run it ")
                    .append("is what shows the mock host was idle.\n");
        }
        md.append("\n");
    }

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
                .append(cores < 0 ? "?" : fixed(100 * cores / Math.max(cpus, 1), 0) + "%").append(" |\n");
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

    /** The classpath the child ran with, lifted back out of its recorded command line. */
    private static String childClasspath(RunInfo info) {
        List<String> command = info.command();
        int at = command.indexOf("-cp");
        return at >= 0 && at + 1 < command.size() ? command.get(at + 1) : "";
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
        appendReconciliation(md, info, stats, mockStats);
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
     */
    private static void appendReconciliation(StringBuilder md, RunInfo info,
                                             io.karatelabs.profiling.LoadProfile.Stats stats, String mockStats) {
        long served = (long) io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "served");
        if (stats == null || served < 0) {
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
    private static void appendPortPressure(StringBuilder md, String mockStats) {
        double ports = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "distinctPeerPorts");
        double window = io.karatelabs.profiling.LoadProfile.mockNumber(mockStats, "loadWindowSeconds");
        if (ports <= 0 || window <= 0) {
            return;
        }
        io.karatelabs.profiling.HostFacts.Network network = io.karatelabs.profiling.HostFacts.read();
        double rate = ports / window;
        double ceiling = network.sustainableConnectionsPerSecond();
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

    private static void appendRunSummary(StringBuilder md, Path runDir, RunInfo info) {
        RunShape shape = info.shape();
        md.append("## Run summary\n\n");
        md.append("| | |\n|---|---|\n");
        row(md, "workload", info.workload());
        row(md, "outcome", outcome(info));
        row(md, "exit code", String.valueOf(info.exitCode()));
        row(md, "threads", String.valueOf(shape.threads()));
        row(md, "bound", shape.isDurationBounded()
                ? "duration=" + RunShape.format(shape.duration())
                : "iterations=" + shape.iterations());
        row(md, "warmup", RunShape.format(shape.warmup()) + " (excluded — recording is delayed past it)");
        row(md, "-Xmx", info.jvm().xmx());
        row(md, "collector", info.jvm().gc().name().toLowerCase()
                + " → `" + String.join(" ", info.jvm().flags(Runtime.version().feature())) + "`");
        row(md, "jdk", Runtime.version().toString());
        row(md, "os / cpus", System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + " / " + Runtime.getRuntime().availableProcessors());
        row(md, "heap dump", info.heapDump() ? "**yes — the run OOM'd**" : "no");
        md.append("\n");

        String summaryLine = findChildSummary(runDir);
        if (summaryLine != null) {
            md.append("Child reported: `").append(summaryLine).append("`\n\n");
        }
        md.append("> Compare digests only when `-Xmx`, `collector` and `jdk` match. "
                + "Differences in any of those change what the numbers below mean.\n\n");
    }

    /**
     * Exit code alone cannot answer "did it OOM" — a swallowed worker error can still
     * exit 0. The heap dump is the primary signal; the child's own stdout is the backstop.
     */
    private static String outcome(RunInfo info) {
        if (info.timedOut()) {
            return "**TIMED OUT** — child was dumped and killed; see `jcmd-jfr-dump.log` and the "
                    + "thread dump at the end of `stdout.log`";
        }
        if (info.heapDump()) {
            return "**OOM** — heap dump written";
        }
        return info.exitCode() == 0 ? "completed" : "completed with errors";
    }

    private static void appendAllocationBySite(StringBuilder md, Data data) {
        md.append("## Allocation by site\n\n");
        md.append("`jdk.ObjectAllocationSample`, weighted bytes, collapsed to the topmost "
                + "`io.karatelabs.*` frame. This answers *what is churning*, not *what is retained* — "
                + "see the heap-after-GC series below before concluding anything about a leak. "
                + "Allocation sampling does attribute correctly across virtual threads.\n\n");
        if (data.allocationByClass.isEmpty()) {
            md.append("_No allocation samples in the recording._\n\n");
            return;
        }
        md.append("Total sampled weight: ").append(bytes(data.allocationTotal))
                .append(" over ").append(data.allocationSamples).append(" samples.\n\n");
        md.append("### By allocating site\n\n");
        table(md, "site", data.allocationBySite, data.allocationTotal, Unit.BYTES);
        md.append("### By allocated type\n\n");
        table(md, "type", data.allocationByClass, data.allocationTotal, Unit.BYTES);
    }

    private static void appendHotMethods(StringBuilder md, Data data) {
        md.append("## Hot methods\n\n");
        md.append("> **Read with care.** `jdk.ExecutionSample` only walks platform threads, and "
                + "Karate runs every scenario on a virtual thread, so for any Runner-driven workload "
                + "this panel severely under-samples scenario code. A method missing from it proves "
                + "nothing. Prefer *Allocation by site* above. This panel is trustworthy for the mock "
                + "JVM (`--record mock`) and other platform-thread paths.\n\n");
        if (data.cpuBySite.isEmpty()) {
            md.append("_No execution samples in the recording (see the caveat above — this is "
                    + "expected for virtual-thread workloads)._\n\n");
            return;
        }
        md.append("Samples: ").append(data.cpuSamples).append("\n\n");
        table(md, "site", data.cpuBySite, data.cpuSamples, Unit.COUNT);
    }

    private static void appendHeapAfterGc(StringBuilder md, Data data) {
        md.append("## Heap after GC\n\n");
        md.append("`jdk.GCHeapSummary`, filtered to `when = \"After GC\"` — the live set that "
                + "survived each collection. **This is the panel that classifies the failure:**\n\n");
        md.append("- constant floor, any sawtooth amplitude → **churn** (allocates a lot, retains nothing)\n");
        md.append("- rising floor → **retention** (a leak)\n");
        md.append("- flat floor then an abrupt cliff → **live mid-copy** (one in-flight structure "
                + "larger than the heap; not a leak at all)\n\n");
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

    private static void appendRetainedObjects(StringBuilder md, Data data) {
        md.append("## Retained objects\n\n");
        md.append("`jdk.OldObjectSample` — JFR's leak profiler: objects that survived a collection, "
                + "attributed to the stack that **allocated** them. That is the allocator, not the "
                + "holder; re-run with `--gc-roots` to get reference chains.\n\n");
        if (data.retainedByClass.isEmpty()) {
            md.append("_No old-object samples in the recording. For a short run this is normal — "
                    + "nothing survived long enough to be sampled._\n\n");
            return;
        }
        md.append("### By type\n\n");
        table(md, "type", data.retainedByClass, data.retainedTotal, Unit.BYTES);
        md.append("### By allocating site\n\n");
        table(md, "site", data.retainedBySite, data.retainedTotal, Unit.BYTES);
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
                    }
                    case "jdk.OldObjectSample" -> {
                        // Weight by last-known heap usage where available, else count.
                        long weight = Math.max(1, longValue(event, "lastKnownHeapUsage", 1));
                        data.retainedTotal += weight;
                        data.retainedByClass.merge(oldObjectClass(event), weight, Long::sum);
                        data.retainedBySite.merge(site(event.getStackTrace()), weight, Long::sum);
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

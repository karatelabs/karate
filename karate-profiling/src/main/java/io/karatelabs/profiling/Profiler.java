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

import io.karatelabs.profiling.jfr.JfrDigest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CLI and forking parent. Does no measuring itself.
 *
 * <pre>
 * [parent]  etc/run.sh &lt;workload&gt;
 *    ├─ mock JVM      (no JFR)   → prints its port, then serves
 *    └─ workload JVM  (JFR on)   -Xmx… -XX:+UseG1GC -Dkarate.profiling.mockUrl=…
 *                                 └─ on exit → JfrDigest → digest.md
 * </pre>
 *
 * <p>Everything here exists so that a run is reproducible and its recording is
 * <em>usable</em>. The heap size and collector belong to the workload, not the shell.
 * The mock is a sibling process so it never pollutes the recording. And several JFR
 * details below are load-bearing rather than decorative — see {@link #jfrFlags}.
 */
public final class Profiler {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String DEFAULT_MAX_SIZE = "512m";
    private static final String SOAK_MAX_SIZE = "4g";
    /** Past this, a run without {@code --soak} outlives its own recording. See {@link #warnAboutLongRunShape}. */
    private static final Duration SOAK_ADVISED_BEYOND = Duration.ofMinutes(20);

    /**
     * Events a multi-hour run must not record. Every one is per-collection GC bookkeeping that
     * no panel reads; together they are ~99% of the file on an allocation-heavy workload.
     * Three are deliberately NOT here, and they are exactly the three a soak digest reads:
     * {@code jdk.GCHeapSummary} (the heap-after-GC floor — the entire leak detector),
     * {@code jdk.GarbageCollection} (the GC-pause panel), and {@code jdk.OldObjectSample}, which
     * is re-enabled explicitly below. The top-level {@code jdk.GCPhasePause} also survives, but
     * as a side effect of only naming the levels: no panel reads it. It is cheap — one event per
     * collection rather than the hundreds its sub-levels emit — so it is left alone.
     */
    private static final List<String> SOAK_DISABLED_EVENTS = List.of(
            "jdk.GCPhaseParallel", "jdk.GCPhasePauseLevel1", "jdk.GCPhasePauseLevel2",
            "jdk.GCPhasePauseLevel3", "jdk.GCPhasePauseLevel4",
            "jdk.PromoteObjectInNewPLAB", "jdk.PromoteObjectOutsidePLAB",
            "jdk.TenuringDistribution", "jdk.GCReferenceStatistics",
            "jdk.MetaspaceChunkFreeListSummary",
            "jdk.ObjectAllocationSample", "jdk.ExecutionSample", "jdk.NativeMethodSample");
    private static final int STACK_DEPTH = 128;

    public static void main(String[] args) throws Exception {
        List<String> argv = new ArrayList<>(List.of(args));
        if (argv.isEmpty() || argv.contains("--help") || argv.contains("-h")) {
            usage();
            return;
        }
        if (argv.get(0).equals("--list") || argv.get(0).equals("list")) {
            list();
            return;
        }
        if (argv.get(0).equals("compare")) {
            argv.remove(0);
            System.exit(Compare.main(argv));
        }
        if (argv.get(0).equals("run")) {
            argv.remove(0);
        }
        if (argv.isEmpty()) {
            usage();
            System.exit(2);
        }
        System.exit(run(argv));
    }

    private static void usage() {
        System.out.println("""
                usage: profiler [run] <workload> [flags]
                       profiler --list
                       profiler compare <run-dir>... (parity pairs, in run order)

                flags:
                  --threads N            concurrency (virtual threads)
                  --iterations N         iteration-bounded run
                  --duration 10m         duration-bounded run (mutually exclusive with --iterations)
                  --warmup 5s            excluded from the measured window
                  --timeout 15m          wall-clock cap; child is dumped and killed on expiry
                  --xmx 768m             child heap
                  --gc g1|zgc            collector (see docs/PROFILING.md)
                  --record workload|mock which JVM gets the recording
                  --mock feature|latency  which mock tier to fork (default feature)
                  --mock-latency 10ms     injected latency; implies --mock latency
                  --mock-url URL          use an already-running LatencyMock (e.g. another host)
                                          instead of forking one; see docs/PROFILING.md §10
                  --gc-roots             enable OldObjectSample reference chains (costly)
                  --soak                 for multi-hour runs: drop the allocation and CPU
                                         sampling so the recording spans the whole run instead
                                         of rolling. Keeps GC events and the leak profiler.
                  --jvm-flag -XX:...     extra child JVM option, repeatable. For deliberately
                                         constraining a run — e.g. -XX:ActiveProcessorCount=1 to
                                         make the JVM believe it has one core. Recorded in
                                         run-meta.txt and the digest, because a constrained run is
                                         not comparable with an unconstrained one.
                  -Dkey=value            passed through to the child JVM (repeatable)
                """);
    }

    private static void list() {
        System.out.println();
        for (Map.Entry<String, Workload> entry : Workloads.all().entrySet()) {
            Workload w = entry.getValue();
            RunShape shape = w.shape();
            JvmConfig jvm = w.jvm();
            System.out.println("  " + entry.getKey());
            System.out.println("      " + w.describe());
            System.out.println("      defaults: threads=" + shape.threads()
                    + (shape.isDurationBounded()
                    ? " duration=" + RunShape.format(shape.duration())
                    : " iterations=" + shape.iterations())
                    + " xmx=" + jvm.xmx() + " gc=" + jvm.gc().name().toLowerCase()
                    + (w.needsMock() ? " mock=yes" : ""));
            System.out.println();
        }
    }

    private static int run(List<String> argv) throws Exception {
        String name = argv.remove(0);
        Workload workload = Workloads.get(name);

        Args flags = Args.parse(argv);
        RunShape shape = workload.shape()
                .withThreads(flags.threads)
                .withIterations(flags.iterations)
                .withDuration(flags.duration)
                .withWarmup(flags.warmup)
                .withTimeout(flags.timeout);
        JvmConfig jvm = workload.jvm().withXmx(flags.xmx).withGc(flags.gc);
        warnAboutLongRunShape(shape, flags);
        boolean recordMock = "mock".equals(flags.record);
        // Mirrors Child's own rule: a self-driving workload runs one pass and no warmup, so
        // there is nothing for the recording to be delayed past.
        boolean warmupWillRun = !workload.drivesOwnConcurrency();

        Path runDir = Path.of("target", "profiling", name + "-" + LocalDateTime.now().format(STAMP));
        Files.createDirectories(runDir);
        System.out.println("[parent] run dir: " + runDir.toAbsolutePath());

        String classpath = resolveClasspath();
        Children children = new Children();
        Runtime.getRuntime().addShutdownHook(new Thread(children::killAll, "profiler-cleanup"));

        String mockUrl = null;
        if (workload.needsMock() && flags.mockUrl != null) {
            if (recordMock) {
                System.err.println("[parent] --record mock cannot profile a mock this process did "
                        + "not fork; run the recording on the mock host instead");
                return 2;
            }
            mockUrl = ExternalMock.attach(flags.mockUrl, runDir);
            System.out.println("[parent] using external mock at " + mockUrl);
        } else if (workload.needsMock()) {
            mockUrl = startMock(workload, classpath, runDir, children,
                    recordMock ? jfrFlags(runDir, shape, flags, warmupWillRun) : List.of(),
                    flags.mock, flags.mockLatency);
            System.out.println("[parent] mock ready at " + mockUrl);
        } else if (recordMock) {
            System.err.println("[parent] --record mock requested but " + name + " does not use a mock");
            return 2;
        }

        List<String> command = childCommand(classpath, name, shape, jvm, mockUrl,
                recordMock ? List.of() : jfrFlags(runDir, shape, flags, warmupWillRun), runDir, flags.systemProperties,
                workload.jvmFlags(), flags.jvmFlags);
        writeRunMeta(runDir, name, shape, jvm, command, mockUrl, mockTier(workload, flags), flags.jvmFlags);

        System.out.println("[parent] forking: " + String.join(" ", command));
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        children.add(child);

        Path stdoutLog = runDir.resolve("stdout.log");
        CompletableFuture<Void> pump = pump(child, stdoutLog);

        Duration timeout = shape.effectiveTimeout();
        System.out.println("[parent] timeout: " + RunShape.format(timeout));
        boolean finished = child.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        boolean timedOut = !finished;
        if (timedOut) {
            System.err.println("[parent] TIMED OUT after " + RunShape.format(timeout) + " — dumping and killing");
            rescue(child, runDir, stdoutLog);
            child.destroyForcibly();
            child.waitFor(30, TimeUnit.SECONDS);
        }
        pump.get(30, TimeUnit.SECONDS);
        int exit = child.exitValue();
        children.killAll();

        // The forked mock writes its own stats line on the way down; an external one has to be
        // asked, and only once the load has stopped.
        if (flags.mockUrl != null) {
            ExternalMock.collect(mockUrl, runDir);
        }

        boolean heapDump = Files.exists(runDir.resolve("heapdump.hprof"));
        System.out.println("[parent] child exited " + exit
                + (heapDump ? " (heap dump written — the run OOM'd)" : "")
                + (timedOut ? " (after timeout)" : ""));

        JfrDigest.write(runDir, new JfrDigest.RunInfo(name, exit, timedOut, heapDump, shape, jvm, command));
        System.out.println("[parent] digest: " + runDir.resolve("digest.md").toAbsolutePath());
        // The child's status is the run's status. This used to return `timedOut ? 1 : 0`, which
        // read the child's exit code, wrote it into the digest, printed it — and then threw it
        // away. A child that errored, OOM'd or truncated therefore reached run.sh, ssh and
        // matrix.sh as a success, and matrix.sh decides whether an arm counts by exactly that
        // status. Every downstream guarantee about rejecting a bad run stopped one process short
        // of the thing that reads it, with a digest saying TRUNCATED next to a green exit.
        //
        // Note this makes a deliberately-OOM'ing workload exit non-zero too. That is correct: the
        // run did OOM, and a caller that expects it is reading the digest, not the status.
        return timedOut || exit != 0 ? 1 : 0;
    }

    /**
     * JFR flags for the recorded JVM. Four details here are deliberate:
     *
     * <ul>
     *   <li>{@code repository=} keeps live chunks on disk, so {@code jfr assemble} can
     *       rescue a run whose {@code run.jfr} was never written.</li>
     *   <li>{@code maxsize=} is required — the default is 0, meaning unbounded, and a long
     *       soak at {@code settings=profile} produces a very large file. But note what a cap
     *       does to a soak: the recording <b>rolls</b>, discarding the oldest chunks. Measured,
     *       {@code scope-capture-bound} writes ~1.2 GB/hour at {@code settings=profile}, so the
     *       512 MB cap starts dropping history after ~25 minutes — and the heap-after-GC series
     *       over hours is the entire point of a soak. That is what {@code --soak} exists to fix,
     *       and it fixes it by recording less rather than by capping later.</li>
     *   <li>{@code delay=} is what actually makes "the measured window excludes warmup"
     *       true. Without it the recording starts at JVM launch and is dominated by class
     *       loading and JIT.</li>
     *   <li>{@code stackdepth=128} — the default 64 truncates Karate-through-JS stacks,
     *       which silently collapses distinct allocation sites into one.</li>
     * </ul>
     *
     * <p>Note what is <em>absent</em>: {@code -XX:+ExitOnOutOfMemoryError}. It halts the
     * JVM without flushing, leaving a zero-byte recording — on precisely the workloads
     * whose expected outcome is an OOM.
     */
    /**
     * The two ways a long run silently produces a digest that describes something other than the
     * run. Both are warnings rather than refusals — a deliberate short soak to test the harness is
     * legitimate, and so is a long iteration-bounded run whose operator knows the rate.
     *
     * <p><b>Without {@code --soak}</b> a recording is capped at {@link #DEFAULT_MAX_SIZE} and
     * rolls. At {@code settings=profile} on an allocation-heavy workload that is ~1.2 GB/h, so the
     * cap is reached in about 25 minutes and everything before it is discarded. The digest is
     * clean and describes the last 25 minutes of an eight-hour run without saying so — the exact
     * failure the {@code --soak} flag exists to prevent, which is worth nothing if the operator
     * has to already know to pass it.
     *
     * <p><b>Iteration-bounded runs</b> get a flat one-hour {@link RunShape#effectiveTimeout()},
     * because their runtime is unknown by construction. An operator who sized an iteration count
     * at "about an hour" from a throughput estimate is one bad estimate away from having the child
     * killed and its recording dumped mid-run — and the estimate is usually derived from a shorter
     * run at a warmer JIT state, so it errs optimistic.
     */
    private static void warnAboutLongRunShape(RunShape shape, Args flags) {
        if (shape.isDurationBounded() && !flags.soak
                && shape.duration().compareTo(SOAK_ADVISED_BEYOND) > 0) {
            System.out.println("[parent] WARNING: --duration " + RunShape.format(shape.duration())
                    + " without --soak. The recording is capped at " + DEFAULT_MAX_SIZE
                    + " and rolls, so the digest will describe roughly the last 25 minutes of the"
                    + " run and will not say so. Pass --soak.");
        }
        if (!shape.isDurationBounded() && flags.timeout == null) {
            System.out.println("[parent] note: iteration-bounded run, so --timeout defaults to a"
                    + " flat " + RunShape.format(shape.effectiveTimeout()) + ". If "
                    + shape.iterations() + " iterations might take longer than that, pass"
                    + " --timeout — the child is killed at the cap and the run is lost.");
        }
    }

    private static List<String> jfrFlags(Path runDir, RunShape shape, Args flags, boolean warmupWillRun) {
        // A soak records a different, much smaller set of events, because the default one
        // cannot span a soak at all. Two measurements got this right, both surprising:
        //
        //   1. The file is dominated by GC *internals*, not by sampling. On
        //      scope-capture-bound: jdk.GCPhaseParallel 1,123,457 events,
        //      PromoteObjectInNewPLAB 126,183, TenuringDistribution 55,560 — against the 7,408
        //      jdk.GCHeapSummary events a soak actually reads (with jdk.GarbageCollection for the
        //      pause panel and jdk.OldObjectSample for retention; those three, and nothing else).
        //      Thirty young collections a second times a few hundred phase events each is the
        //      whole 1.2 GB/h. Switching settings=profile to settings=default helps less than it
        //      looks: the phase events are on in both. It does turn the PLAB events off — those
        //      two are profile-only — but they are a tenth of the volume, so they are disabled
        //      explicitly as well rather than relied on.
        //
        //   2. The disable list has to be near-complete to matter. Disabling the two samplers
        //      alone moved 41 MB to 37 MB — a 10% dent, because the samplers were never the bulk.
        //      Only when the GC-internal events above went too did it reach ~2 MB.
        //
        // A correction, because the wrong version of it was published here and is worth naming:
        // this comment used to claim per-event settings are *silently ignored* in the
        // `-XX:StartFlightRecording=` form and need the JDK 14+ colon form. That is false. Both
        // forms honour them — measured on JDK 24 with a GC-churning workload: `=` recorded 15,300
        // jdk.TenuringDistribution events with no disable and 0 with one, and `:` recorded 14,760
        // and 0. The observation behind the false claim (a "disabled" event still appearing 7,740
        // times) was never explained; reading a stale run.jfr from a previous run directory is the
        // likeliest cause and would have produced exactly that. The colon form is kept because it
        // is the documented modern syntax, not because the other one is broken.
        //
        // Hence the explicit disable list. OldObjectSample goes back ON: it is the leak profiler,
        // it is cheap, and "who allocated what is still alive after eight hours" is the question a
        // soak asks.
        StringBuilder start;
        if (flags.soak) {
            start = new StringBuilder("-XX:StartFlightRecording:settings=default");
            for (String event : SOAK_DISABLED_EVENTS) {
                start.append(',').append(event).append("#enabled=false");
            }
            // stackTrace=true is not redundant. settings=default enables OldObjectSample
            // without stacks, and the first soak proved what that costs: the retained panel
            // reported types (StackChunk 37%, byte[] 26%) with "by allocating site: (no stack)
            // 100%" — a leak profiler that cannot say who allocated anything.
            start.append(",jdk.OldObjectSample#enabled=true,jdk.OldObjectSample#stackTrace=true");
        } else {
            start = new StringBuilder("-XX:StartFlightRecording=settings=profile");
        }
        long warmupMillis = shape.warmup().toMillis();
        // The delay exists to skip the warmup — so it is only correct when a warmup actually
        // runs. A workload that drives its own concurrency gets none (see Child), and delaying
        // the recording anyway silently discards the first N seconds of the real measurement,
        // or all of it when the run is shorter than the warmup: an empty run.jfr that reads as
        // a crashed JVM.
        //
        // Expressed in milliseconds rather than whole seconds. toSeconds() truncates, so a
        // 1500 ms warmup produced delay=1s and recorded its final 500 ms — the recording then
        // contains warmup while the digest says warmup is excluded.
        if (warmupWillRun && warmupMillis >= 1000) {
            start.append(",delay=").append(warmupMillis).append("ms");
        } else if (warmupWillRun && warmupMillis > 0) {
            // JFR's floor is one second, and below it the JVM refuses to start rather than
            // ignoring the flag — so there is no delay to apply and the warmup is inside the
            // recording. Say so, because the digest's warmup row claims the opposite.
            System.out.println("[parent] WARNING: --warmup " + RunShape.format(shape.warmup())
                    + " is below JFR's one-second minimum delay, so the warmup CANNOT be excluded"
                    + " from the recording — it is in there with the measurement. Use >= 1s.");
        }
        // A soak still gets a cap — unbounded is how a disk fills — but a far larger one,
        // because with the sampling events off the recording is small and the cap should never
        // be the thing that ends the history.
        start.append(",maxsize=").append(flags.soak ? SOAK_MAX_SIZE : DEFAULT_MAX_SIZE);
        if (flags.gcRoots) {
            start.append(",path-to-gc-roots=true");
        }
        start.append(",filename=").append(runDir.resolve("run.jfr").toAbsolutePath());

        String options = "-XX:FlightRecorderOptions:repository="
                + runDir.resolve("jfr-repo").toAbsolutePath() + ",stackdepth=" + STACK_DEPTH;

        return List.of(start.toString(), options,
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=" + runDir.resolve("heapdump.hprof").toAbsolutePath());
    }

    private static List<String> childCommand(String classpath, String name, RunShape shape,
                                             JvmConfig jvm, String mockUrl,
                                             List<String> jfr, Path runDir,
                                             List<String> systemProperties,
                                             List<String> workloadFlags,
                                             List<String> extraJvmFlags) {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Xmx" + jvm.xmx());
        command.addAll(jvm.flags(Runtime.version().feature()));
        command.addAll(workloadFlags);
        // After the harness's own JVM options and the workload's, so that when the same option is
        // given twice HotSpot takes the operator's. That is the point of the flag: it exists to
        // constrain a run deliberately, which means overriding what the harness chose.
        command.addAll(extraJvmFlags);
        command.addAll(jfr);
        command.addAll(systemProperties);
        command.add("-Dkarate.profiling.workload=" + name);
        command.add("-Dkarate.profiling.threads=" + shape.threads());
        if (shape.isDurationBounded()) {
            command.add("-Dkarate.profiling.duration=" + RunShape.format(shape.duration()));
        } else {
            command.add("-Dkarate.profiling.iterations=" + shape.iterations());
        }
        command.add("-Dkarate.profiling.warmup=" + RunShape.format(shape.warmup()));
        if (mockUrl != null) {
            command.add("-Dkarate.profiling.mockUrl=" + mockUrl);
        }
        command.add("-Dkarate.profiling.runDir=" + runDir.toAbsolutePath());
        command.add("-cp");
        command.add(classpath);
        command.add(Child.class.getName());
        return command;
    }

    /**
     * Forks the sibling mock. Two tiers, and they are different kinds of thing: the <b>feature</b>
     * mock is a <i>subject</i> — {@code --record mock} profiles gherkin matching and JS evaluation
     * in it — while {@link LatencyMock} is an <i>instrument</i>, cheap and self-measuring, for runs
     * where the mock must stay out of the way. See docs/PROFILING.md §10.
     */
    private static String startMock(Workload workload, String classpath, Path runDir,
                                    Children children, List<String> jfr,
                                    String tier, Duration latency) throws Exception {
        boolean instrument = "latency".equals(tier);
        if (!instrument && !"feature".equals(tier)) {
            throw new IllegalArgumentException("--mock must be 'feature' or 'latency', got: " + tier);
        }
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.addAll(jfr);
        command.add("-cp");
        command.add(classpath);
        if (instrument) {
            command.add(LatencyMock.class.getName());
            command.add("--latency");
            command.add(latency == null ? "0" : latency.toMillis() + "ms");
        } else {
            command.add(MockJvm.class.getName());
            command.add(workload.mockFeature());
        }

        Process mock = new ProcessBuilder(command).redirectErrorStream(true).start();
        children.add(mock);

        CompletableFuture<String> ready = new CompletableFuture<>();
        Path log = runDir.resolve("mock.log");
        Thread.ofVirtual().name("mock-pump").start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(mock.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(Files.newBufferedWriter(log), true)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                    if (line.startsWith(MockJvm.READY_PREFIX)) {
                        ready.complete(line.substring(MockJvm.READY_PREFIX.length()).trim());
                    }
                }
            } catch (IOException e) {
                ready.completeExceptionally(e);
            }
            ready.completeExceptionally(new IllegalStateException("mock exited before signalling ready"));
        });
        return ready.get(60, TimeUnit.SECONDS);
    }

    /** Forward child output to console and {@code stdout.log} at once. */
    private static CompletableFuture<Void> pump(Process child, Path log) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Thread.ofVirtual().name("child-pump").start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(Files.newBufferedWriter(log), true)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    out.println(line);
                }
                done.complete(null);
            } catch (IOException e) {
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    /**
     * Last rites for a hung child: capture where it is stuck and salvage the recording
     * before killing it. Without this a hang leaves nothing at all to look at — the digest
     * is only written on exit, so the operator is left waiting on a file that will never
     * appear.
     */
    private static void rescue(Process child, Path runDir, Path stdoutLog) {
        long pid = child.pid();
        jcmd(pid, stdoutLog, "Thread.print");
        jcmd(pid, runDir.resolve("jcmd-jfr-dump.log"),
                "JFR.dump", "filename=" + runDir.resolve("run.jfr").toAbsolutePath());
    }

    private static void jcmd(long pid, Path appendTo, String... command) {
        List<String> argv = new ArrayList<>();
        argv.add(Path.of(System.getProperty("java.home"), "bin", "jcmd").toString());
        argv.add(String.valueOf(pid));
        argv.addAll(List.of(command));
        try {
            Process p = new ProcessBuilder(argv)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(appendTo.toFile()))
                    .start();
            p.waitFor(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[parent] jcmd " + String.join(" ", command) + " failed: " + e);
        }
    }

    /**
     * Which mock served this run. PROFILING.md §7 records the gap this closes: "which mock a
     * gatling-http-* run used is not in run-meta.txt; the tell is mock.log" — provenance that
     * lived only in an artifact the disk-hygiene rules do not promise to keep, for a choice that
     * decides whether a cell's throughput means anything at all.
     */
    private static String mockTier(Workload workload, Args flags) {
        if (!workload.needsMock()) {
            return "(none)";
        }
        if (flags.mockUrl != null) {
            return "external (not forked by this run)";
        }
        return "latency".equals(flags.mock)
                ? "latency" + (flags.mockLatency == null ? "" : " (" + RunShape.format(flags.mockLatency) + " injected)")
                : "feature";
    }

    /**
     * How long since the previous run in this directory finished starting.
     *
     * <p>The 35-second gap between parity runs is not hygiene, it is a correctness condition: the
     * karate arm opens a connection per iteration and they sit in TIME_WAIT, so back-to-back runs
     * can meet the ephemeral-port ceiling for reasons that have nothing to do with Karate — and it
     * reads as "Karate is slower". Today that gap lives in an operator's shell script, which means
     * a digest cannot say whether it was honoured. Recording it makes the condition auditable after
     * the fact, which is the most a harness that deliberately reports rather than asserts should do.
     */
    private static String sincePreviousRun(Path runDir) {
        try (java.util.stream.Stream<Path> siblings = Files.list(runDir.getParent())) {
            String self = runDir.getFileName().toString();
            String selfStamp = HostFacts.stampOf(self);
            // Ordered by stamp, not by name: the names carry a workload prefix, so "call-…" would
            // otherwise always sort before "gatling-…" regardless of when either ran.
            String previous = siblings.map(p -> p.getFileName().toString())
                    .filter(n -> !HostFacts.stampOf(n).isEmpty())
                    .filter(n -> HostFacts.stampOf(n).compareTo(selfStamp) < 0)
                    .max(Comparator.comparing(HostFacts::stampOf))
                    .orElse(null);
            if (previous == null) {
                return "(first run in this directory)";
            }
            // The previous run's END, not its start. TIME_WAIT begins when a socket closes, so
            // start-to-start is the wrong interval and fails in the dangerous direction: a zero-gap
            // run after a 100-second one would report "1m40s" and no flag while every socket from
            // it is still fresh. digest.md is written last, so its mtime is that run's end — and if
            // it is missing (the run died), fall back to the stamp and say the figure is a floor.
            LocalDateTime previousEnd;
            String basis = "";
            Path previousDigest = runDir.getParent().resolve(previous).resolve("digest.md");
            if (Files.exists(previousDigest)) {
                previousEnd = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(previousDigest).toInstant(), java.time.ZoneId.systemDefault());
            } else {
                previousEnd = LocalDateTime.parse(HostFacts.stampOf(previous), STAMP);
                basis = " (from its start — it wrote no digest, so this is an upper bound on the gap)";
            }
            Duration gap = Duration.between(previousEnd,
                    LocalDateTime.parse(HostFacts.stampOf(self), STAMP));
            return RunShape.format(gap) + " after " + previous + " ended" + basis
                    + (gap.toSeconds() < 35
                    ? "  ← under the 35s TIME_WAIT gap the parity protocol asks for" : "");
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    private static void writeRunMeta(Path runDir, String name, RunShape shape, JvmConfig jvm,
                                     List<String> command, String mockUrl, String mockTier,
                                     List<String> extraJvmFlags) throws IOException {
        String meta = """
                workload:     %s
                threads:      %d
                bound:        %s
                warmup:       %s
                timeout:      %s
                xmx:          %s
                gc:           %s
                gc expansion: %s
                extra jvm:    %s
                mock:         %s
                mock url:     %s

                parent jdk:   %s (%s)
                child jdk:    same as parent
                os:           %s %s (%s)
                cpus:         %d
                network:      %s
                since prev:   %s

                child command:
                %s
                """.formatted(
                name,
                shape.threads(),
                shape.isDurationBounded()
                        ? "duration=" + RunShape.format(shape.duration())
                        : "iterations=" + shape.iterations(),
                RunShape.format(shape.warmup()),
                RunShape.format(shape.effectiveTimeout()),
                jvm.xmx(),
                jvm.gc().name().toLowerCase(),
                String.join(" ", jvm.flags(Runtime.version().feature())),
                extraJvmFlags.isEmpty() ? "(none)" : String.join(" ", extraJvmFlags),
                mockTier,
                mockUrl == null ? "(none)" : mockUrl,
                Runtime.version(), System.getProperty("java.vendor"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                HostFacts.read().describe(),
                sincePreviousRun(runDir),
                String.join(" \\\n  ", command));
        Files.writeString(runDir.resolve("run-meta.txt"), meta);
    }

    /**
     * The child needs an exact classpath, and the parent cannot borrow its own: under
     * {@code exec:java} this JVM is Maven's, with a custom classloader, so
     * {@code java.class.path} points at Maven rather than at us. {@code etc/run.sh}
     * materialises the real one with {@code dependency:build-classpath}.
     */
    private static String resolveClasspath() throws IOException {
        String override = System.getProperty("karate.profiling.classpath");
        if (override != null) {
            return override;
        }
        Path cpFile = Path.of("target", "cp.txt");
        if (!Files.exists(cpFile)) {
            throw new IllegalStateException("target/cp.txt not found — run via etc/run.sh, or pass "
                    + "-Dkarate.profiling.classpath=…");
        }
        return Path.of("target", "classes").toAbsolutePath() + java.io.File.pathSeparator
                + Files.readString(cpFile).trim();
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** Kills every child we started, however the parent is going down. */
    private static final class Children {

        private final List<Process> processes = new ArrayList<>();

        synchronized void add(Process process) {
            processes.add(process);
        }

        synchronized void killAll() {
            // Close stdin first and give each child a moment. Both mocks block on System.in.read()
            // and shut down when it returns EOF — and LatencyMock prints its stats line on that
            // path. destroy() is a SIGTERM, which kills the JVM before any of that runs, so going
            // straight to it threw away the numbers that say whether the mock stayed out of the
            // way: the whole reason the instrumented tier exists.
            for (Process process : processes) {
                if (process.isAlive()) {
                    try {
                        process.getOutputStream().close();
                    } catch (IOException e) {
                        // already gone, or never had a pipe — destroy() below handles it
                    }
                }
            }
            for (Process process : processes) {
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            for (Process process : processes) {
                if (process.isAlive()) {
                    process.destroy();
                    try {
                        if (!process.waitFor(10, TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                    }
                }
            }
            processes.clear();
        }

    }

    private static final class Args {

        Integer threads;
        Long iterations;
        Duration duration;
        Duration warmup;
        Duration timeout;
        String xmx;
        JvmConfig.Gc gc;
        String record = "workload";
        String mock = "feature";
        String mockUrl;
        Duration mockLatency;
        boolean gcRoots;
        boolean soak;
        final List<String> systemProperties = new ArrayList<>();
        /** Extra child JVM options, repeatable. See the usage text and childCommand. */
        final List<String> jvmFlags = new ArrayList<>();

        static Args parse(List<String> argv) {
            Args args = new Args();
            for (int i = 0; i < argv.size(); i++) {
                String flag = argv.get(i);
                switch (flag) {
                    case "--gc-roots" -> args.gcRoots = true;
                    case "--soak" -> args.soak = true;
                    case "--jvm-flag" -> args.jvmFlags.add(next(argv, ++i, flag));
                    case "--threads" -> args.threads = Integer.parseInt(next(argv, ++i, flag));
                    case "--iterations" -> args.iterations = Long.parseLong(next(argv, ++i, flag));
                    case "--duration" -> args.duration = RunShape.parseDuration(next(argv, ++i, flag));
                    case "--warmup" -> args.warmup = RunShape.parseDuration(next(argv, ++i, flag));
                    case "--timeout" -> args.timeout = RunShape.parseDuration(next(argv, ++i, flag));
                    case "--xmx" -> args.xmx = next(argv, ++i, flag);
                    case "--gc" -> args.gc = JvmConfig.Gc.parse(next(argv, ++i, flag));
                    case "--record" -> args.record = next(argv, ++i, flag);
                    case "--mock" -> args.mock = next(argv, ++i, flag);
                    case "--mock-url" -> args.mockUrl = next(argv, ++i, flag);
                    case "--mock-latency" -> {
                        args.mockLatency = RunShape.parseDuration(next(argv, ++i, flag));
                        // Asking for latency IS asking for the instrument — the feature mock has
                        // no such knob, and silently ignoring the flag is the kind of thing that
                        // gets discovered three runs into a matrix.
                        args.mock = "latency";
                    }
                    default -> {
                        // A bare -Dkey=value goes straight to the child. Cheap, and the
                        // difference between "I have a hypothesis" and "I can test it" —
                        // an experimental toggle in core needs no harness change to exercise.
                        if (flag.startsWith("-D")) {
                            args.systemProperties.add(flag);
                        } else {
                            throw new IllegalArgumentException("unknown flag: " + flag);
                        }
                    }
                }
            }
            return args;
        }

        private static String next(List<String> argv, int index, String flag) {
            if (index >= argv.size()) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return argv.get(index);
        }

    }

}

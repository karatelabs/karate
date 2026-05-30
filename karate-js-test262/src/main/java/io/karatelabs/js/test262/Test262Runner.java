package io.karatelabs.js.test262;

import io.karatelabs.js.Engine;
import io.karatelabs.parser.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * test262 conformance runner for karate-js.
 * <p>
 * Sequential, fresh {@link Engine} per test, per-test watchdog timeout. Every
 * run writes a fresh, self-contained {@code target/test262/run-<timestamp>/}
 * directory containing:
 * <ul>
 *   <li>{@code results.jsonl} — canonical, sorted-by-path, atomic-written at
 *       end-of-run.</li>
 *   <li>{@code results.jsonl.partial} — appended/flushed per test in run order;
 *       {@code tail -f} sees rows live; deleted on clean exit, kept on abort.</li>
 *   <li>{@code run-meta.json} — per-run context (test262 SHA, karate-js version
 *       + git SHA, JDK, OS, started/ended, counts, args).</li>
 *   <li>{@code progress.log} — startup banner, periodic {@code [progress]}
 *       lines, final {@code Summary} / {@code Aborted} line. FAIL detail
 *       lives only in {@code results.jsonl}; mirroring it here would
 *       duplicate without new signal.</li>
 * </ul>
 * The runner prints the resolved run-dir path on completion so the user (or
 * {@code etc/run.sh}) can pass it to {@code Test262Report --run-dir <path>}.
 * <p>
 * See {@code karate-js-test262/TEST262.md} for full docs.
 */
public final class Test262Runner {

    /** Lazily initialized so Logback's appender configuration runs AFTER
     *  {@link #RUN_DIR_PROP} is set in {@link #main}; a static-field
     *  initializer would trigger Logback before main() can set the
     *  property, leaving progress.log in the fallback location. */
    private static volatile Logger LOGGER;

    private static Logger logger() {
        Logger l = LOGGER;
        if (l == null) {
            l = LoggerFactory.getLogger(Test262Runner.class);
            LOGGER = l;
        }
        return l;
    }

    /** Periodic progress line cadence: every N tests OR every M seconds, whichever
     *  first. Dialed back from 500/5s after the watchdog + exec-retire combo proved
     *  reliable — full-suite runs no longer need a heartbeat every five seconds for
     *  hang diagnosis. */
    private static final int PROGRESS_EVERY_N_TESTS = 5_000;
    private static final long PROGRESS_EVERY_MS = 60_000L;

    /** Cap on per-FAIL stdout lines per run. Set low so even mid-size slices
     *  (50–200 fails) get a representative sample rather than the full dump —
     *  20 is enough to spot clusters; the canonical record is {@code results.jsonl}
     *  and systematic triage goes through the `jq` recipes in TEST262.md. After
     *  the cap, we emit a single {@code (… N more FAILs, see results.jsonl)} footer. */
    private static final int MAX_FAIL_STDOUT = 20;

    /** Logback file appender reads this system property; see logback.xml. Set
     *  before the first SLF4J log call so the appender is configured against
     *  the actual run dir, not the fallback default. */
    static final String RUN_DIR_PROP = "test262.run.dir";

    /** Pattern matching the {@code at <path>:<line>:<col>} suffix that
     *  {@code Node.toStringError} appends to engine error messages. */
    private static final Pattern AT_LINE_COL = Pattern.compile(
            "\\bat\\s+([^\\s:]+):(\\d+):(\\d+)\\b");

    /**
     * Print a progress / banner / summary line to stdout AND mirror it into
     * the log file ({@code <run-dir>/progress.log}). Per-test FAIL lines do
     * NOT go through here — they print directly to stdout and their full
     * detail lives in {@code results.jsonl}, so duplicating them into the
     * log would add noise without new signal.
     */
    private static void sayProgress(String line) {
        System.out.println(line);
        logger().info(line);
    }

    public static void main(String[] args) throws Exception {
        Cli cli = Cli.parse(args);

        if (!Files.isDirectory(cli.test262)) {
            System.err.println("test262 directory not found: " + cli.test262.toAbsolutePath());
            System.err.println("Run ./fetch-test262.sh (from the karate-js-test262 module directory) first.");
            System.exit(2);
        }
        if (!Files.isRegularFile(cli.expectations)) {
            System.err.println("expectations file not found: " + cli.expectations.toAbsolutePath());
            System.exit(2);
        }

        Expectations expectations = Expectations.load(cli.expectations);
        HarnessLoader harness = new HarnessLoader(cli.test262);

        if (cli.single != null) {
            // --single does no file writes; skip run-dir setup entirely.
            runSingle(cli, expectations, harness);
            return;
        }

        // Pick run dir BEFORE the first SLF4J log call — Logback's file
        // appender resolves ${test262.run.dir} at appender init, which
        // happens on first log call.
        Path runDir = cli.runDir != null
                ? cli.runDir
                : Paths.get("target/test262/run-" + timestamp());
        Files.createDirectories(runDir);
        System.setProperty(RUN_DIR_PROP, runDir.toString());

        runSuite(cli, expectations, harness, runDir);
    }

    private static String timestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
                .format(LocalDateTime.now());
    }

    /* ------------------------------- full suite ------------------------------- */

    private static void runSuite(Cli cli, Expectations expectations, HarnessLoader harness,
                                 Path runDir) throws Exception {
        Path testRoot = cli.test262.resolve("test");
        if (!Files.isDirectory(testRoot)) {
            System.err.println("test262 'test/' directory not found: " + testRoot);
            System.exit(2);
        }

        List<Path> all;
        try (Stream<Path> stream = Files.walk(testRoot)) {
            all = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".js"))
                    .filter(p -> !p.getFileName().toString().endsWith("_FIXTURE.js"))
                    .sorted()
                    .toList();
        }

        Pattern onlyRe = cli.only == null ? null : globToRegex(cli.only);

        Path results = runDir.resolve("results.jsonl");
        Path partial = runDir.resolve("results.jsonl.partial");
        Path runMeta = runDir.resolve("run-meta.json");

        // Startup banner — file appender only; the console already gets its own
        // startup context via Maven's stderr and the runner's output.
        logger().info("test262 runner starting  args={} only={} timeout-ms={} max-duration-ms={}  run-dir={}",
                cli.rawArgs, cli.only == null ? "-" : cli.only, cli.timeoutMs, cli.maxDurationMs, runDir);

        Instant started = Instant.now();
        long startedNanos = System.nanoTime();
        long maxDurationNanos = cli.maxDurationMs > 0 ? cli.maxDurationMs * 1_000_000L : Long.MAX_VALUE;

        int pass = 0, fail = 0, skip = 0;
        int processed = 0;
        int failStdout = 0;       // counts FAIL lines actually printed to stdout
        long lastProgressNanos = startedNanos;
        boolean aborted = false;

        // single-thread executor for the watchdog; recreated if a test times out so
        // a runaway test cannot stall subsequent tests by re-queuing behind it
        // (the engine does not poll Thread.interrupt(), so cancel(true) alone cannot
        // reclaim a stuck thread — we retire it and move on).
        ExecutorService exec = newInnerExec();

        // Live partial file: each result is appended and flushed as it is produced,
        // in submission order (NOT sorted). `tail -f` sees records as they arrive,
        // and on abrupt exit the file remains as forensics.
        BufferedWriter partialWriter = Files.newBufferedWriter(partial, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);

        try {
            for (Path abs : all) {
                String rel = cli.test262.relativize(abs).toString().replace('\\', '/');
                if (onlyRe != null && !onlyRe.matcher(rel).matches()) continue;

                if (System.nanoTime() - startedNanos > maxDurationNanos) {
                    aborted = true;
                    sayProgress("");
                    sayProgress(String.format("ABORTED: --max-duration %dms exceeded; writing partial results",
                            cli.maxDurationMs));
                    break;
                }

                ResultRecord r;

                Test262Case tc;
                try {
                    tc = Test262Case.load(cli.test262, abs);
                } catch (RuntimeException re) {
                    if (failStdout < MAX_FAIL_STDOUT) {
                        System.out.println("FAIL " + rel + " — load error: " + re.getMessage());
                        failStdout++;
                    }
                    appendPartial(partialWriter,
                            ResultRecord.fail(rel, "Harness", "failed to load: " + re.getMessage()));
                    fail++;
                    processed++;
                    lastProgressNanos = maybeProgressTick(processed, pass, fail, skip, startedNanos, lastProgressNanos);
                    continue;
                }

                String reason = expectations.matchSkip(tc);
                if (reason != null) {
                    appendPartial(partialWriter, ResultRecord.skip(rel, reason));
                    skip++;
                    processed++;
                    lastProgressNanos = maybeProgressTick(processed, pass, fail, skip, startedNanos, lastProgressNanos);
                    continue;
                }

                // Inline timeout handling so we can retire `exec` and install a fresh
                // one when a test hangs. Without this, the next test queues behind the
                // stuck inner thread and hits the per-test timeout itself — the whole
                // suite would slow to 10s/test from a single infinite-loop test.
                final Test262Case finalTc = tc;
                Future<ResultRecord> fut = exec.submit(() -> evaluate(finalTc, harness));
                try {
                    r = fut.get(cli.timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    fut.cancel(true);
                    exec.shutdownNow();
                    exec = newInnerExec();
                    r = ResultRecord.fail(rel, "Timeout",
                            "no completion within " + cli.timeoutMs + "ms");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    fut.cancel(true);
                    exec.shutdownNow();
                    exec = newInnerExec();
                    r = ResultRecord.fail(rel, "Interrupted", ie.getMessage());
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause() == null ? ee : ee.getCause();
                    r = ResultRecord.fail(rel, "Unknown",
                            ErrorUtils.firstLine(cause.getMessage(), 200));
                }

                // Dev-mode default: PASS rows are not written to results.jsonl
                // (or its .partial). The pass counter still increments and the
                // count is recorded in run-meta.json; HTML/diff tooling reads it
                // there. Opt into PASS rows with --full when you want a complete
                // record for archival or to drive the HTML report.
                if (cli.full || r.status() != ResultRecord.Status.PASS) {
                    appendPartial(partialWriter, r);
                }
                switch (r.status()) {
                    case PASS -> pass++;
                    case FAIL -> {
                        fail++;
                        // FAIL lines go to stdout only — the full record (with
                        // error_type, message, path) is in results.jsonl.partial,
                        // which is where a consumer should parse failures from.
                        // Capped at MAX_FAIL_STDOUT so a flood doesn't drown sub-
                        // agents / `tail -n` consumers; the cap is announced at end.
                        if (failStdout < MAX_FAIL_STDOUT) {
                            System.out.println("FAIL " + rel + " — "
                                    + (r.errorType() == null ? "" : r.errorType() + ": ")
                                    + (r.message() == null ? "" : r.message()));
                            failStdout++;
                        }
                    }
                    case SKIP -> skip++;
                }
                processed++;
                lastProgressNanos = maybeProgressTick(processed, pass, fail, skip, startedNanos, lastProgressNanos);
            }
        } finally {
            exec.shutdownNow();
            try { partialWriter.close(); } catch (IOException ignored) { /* best-effort */ }
        }

        Instant ended = Instant.now();

        // End-of-run: read the partial (run order) and atomic-write the
        // canonical results.jsonl sorted by path. JSONL starts with
        // {"path":"X",..., so lex sort == path sort.
        List<String> rows = Files.isRegularFile(partial)
                ? Files.readAllLines(partial, StandardCharsets.UTF_8)
                : new ArrayList<>();
        ResultWriter.sortAndWrite(results, rows);

        // Canonical file is in place; the live tap is redundant. Keep it on abort
        // so the stop point is forensically visible.
        if (!aborted) {
            try { Files.deleteIfExists(partial); } catch (IOException ignored) { /* best-effort */ }
        }

        RunMeta meta = new RunMeta(
                readTest262Sha(cli.test262),
                readKarateJsVersion(),
                readHeadSha(cli.test262.getParent() == null ? Path.of(".") : cli.test262.getParent().getParent()),
                RunMeta.detectJdk(),
                RunMeta.detectOs(),
                started, ended,
                new RunMeta.Counts(pass, fail, skip, pass + fail + skip),
                cli.rawArgs);
        meta.writeTo(runMeta);

        sayProgress("");
        if (fail > failStdout) {
            sayProgress(String.format("(… %d more FAILs not printed, see results.jsonl)",
                    fail - failStdout));
        }
        sayProgress(String.format("%s: %d pass / %d fail / %d skip / %d total in %s",
                aborted ? "Aborted" : "Summary",
                pass, fail, skip, pass + fail + skip,
                formatDuration(java.time.Duration.between(started, ended))));
        sayProgress("Run dir: " + runDir.toAbsolutePath());
        if (cli.full) {
            sayProgress("Report:  java io.karatelabs.js.test262.Test262Report --run-dir " + runDir);
        } else {
            sayProgress("(results.jsonl: FAIL/SKIP only — rerun with --full for PASS rows + HTML)");
        }
    }

    /**
     * Emit a [progress] line to stdout + log if enough tests or wall-clock time
     * have elapsed since the last one. Returns the new lastProgressNanos baseline.
     */
    private static long maybeProgressTick(int processed, int pass, int fail, int skip,
                                           long startedNanos, long lastProgressNanos) {
        long nowNanos = System.nanoTime();
        boolean count = processed % PROGRESS_EVERY_N_TESTS == 0;
        boolean time = (nowNanos - lastProgressNanos) >= PROGRESS_EVERY_MS * 1_000_000L;
        if (!count && !time) return lastProgressNanos;
        long elapsedMs = (nowNanos - startedNanos) / 1_000_000L;
        double rate = elapsedMs > 0 ? (processed * 1000.0 / elapsedMs) : 0.0;
        sayProgress(String.format("[progress] %d processed  pass %d  fail %d  skip %d  @ %.0f/s  elapsed %s",
                processed, pass, fail, skip, rate,
                formatDuration(java.time.Duration.ofMillis(elapsedMs))));
        return nowNanos;
    }

    private static ExecutorService newInnerExec() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test262-runner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Append one record's JSONL line + newline to the live partial file and
     * flush so a concurrent {@code tail -f} sees it immediately. The partial
     * is the only store of results during the run, so I/O failures are fatal:
     * this throws and aborts the suite rather than silently losing rows.
     */
    private static void appendPartial(BufferedWriter w, ResultRecord r) throws IOException {
        w.write(r.toJsonLine());
        w.newLine();
        w.flush();
    }

    /* ------------------------------ single-file ------------------------------ */

    private static void runSingle(Cli cli, Expectations expectations, HarnessLoader harness) throws Exception {
        Path abs = cli.test262.resolve(cli.single);
        if (!Files.isRegularFile(abs)) {
            System.err.println("test262 file not found: " + abs);
            System.exit(2);
        }
        Test262Case tc = Test262Case.load(cli.test262, abs);
        System.out.println("path:        " + tc.relativePath());
        if (cli.verbose >= 1) {
            System.out.println("description: " + (tc.metadata().description() == null ? "" : tc.metadata().description()));
            System.out.println("flags:       " + tc.metadata().flags());
            System.out.println("features:    " + tc.metadata().features());
            System.out.println("includes:    " + tc.metadata().includes());
            System.out.println("negative:    " + tc.metadata().negative());
        }
        String reason = expectations.matchSkip(tc);
        if (reason != null) {
            System.out.println("status:      SKIP — " + reason);
            return;
        }
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            ResultRecord r = runOne(tc, harness, exec, cli.timeoutMs);
            System.out.println("status:      " + r.status()
                    + (r.errorType() == null ? "" : " [" + r.errorType() + "]"));
            if (r.message() != null) {
                System.out.println("message:     " + r.message());
                String loc = extractSourceLocation(r.message());
                if (loc != null) System.out.println("location:    " + loc);
            }
            if (cli.verbose >= 2) {
                System.out.println();
                System.out.println("--- source ---");
                System.out.println(tc.source());
            }
        } finally {
            exec.shutdownNow();
        }
    }

    /**
     * Pull the {@code at <path>:<line>:<col>} suffix from a FAIL message,
     * if present. The engine appends this via {@code Node.toStringError};
     * surfacing it in {@code --single -vv} gives the user a single-frame
     * locator without re-reading the source.
     */
    static String extractSourceLocation(String message) {
        if (message == null) return null;
        Matcher m = AT_LINE_COL.matcher(message);
        return m.find() ? m.group(1) + ":" + m.group(2) + ":" + m.group(3) : null;
    }

    /* --------------------------- per-test execution --------------------------- */

    private static ResultRecord runOne(Test262Case tc, HarnessLoader harness, ExecutorService exec, long timeoutMs) {
        Future<ResultRecord> fut = exec.submit(() -> evaluate(tc, harness));
        try {
            return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            fut.cancel(true);
            return ResultRecord.fail(tc.relativePath(), "Timeout",
                    "no completion within " + timeoutMs + "ms");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResultRecord.fail(tc.relativePath(), "Interrupted", ie.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            return ResultRecord.fail(tc.relativePath(), "Unknown",
                    ErrorUtils.firstLine(cause.getMessage(), 200));
        }
    }

    private static ResultRecord evaluate(Test262Case tc, HarnessLoader harness) {
        String path = tc.relativePath();
        Test262Metadata.Negative neg = tc.metadata().negative();
        boolean isRaw = tc.metadata().raw();

        Engine engine = new Engine();
        // Route this engine's console.log into a per-test sink. The default is
        // System.out.println, which would interleave lines from tests that happen to
        // print during suite runs and pollute our own progress/FAIL output.
        engine.setOnConsoleLog(line -> { /* sink: intentionally discard */ });

        // INTERPRETING.md: raw tests must run in a pristine realm — no harness files,
        // no host-defined bindings. Non-raw tests get host bindings THEN harness helpers,
        // so that `sta.js` (which defines its own $DONOTEVALUATE) overrides any of ours.
        if (!isRaw) {
            try {
                engine.eval(HOST_BINDINGS_JS);
            } catch (RuntimeException e) {
                return ResultRecord.fail(path, "Harness",
                        "host bindings setup failed: " + ErrorUtils.firstLine(e.getMessage(), 200));
            }
            try {
                harness.primeEngine(engine, tc.metadata().includes());
            } catch (RuntimeException e) {
                return ResultRecord.fail(path, "Harness",
                        "harness load failed: " + ErrorUtils.firstLine(e.getMessage(), 200));
            }
        }

        // Evaluate the test source. Parse errors surface as ParserException (phase=parse);
        // anything else is phase=runtime.
        //
        // INTERPRETING.md: a `flags: [onlyStrict]` test is run with a "use strict"
        // directive prepended so the whole source parses and executes as strict code.
        // (raw tests never carry onlyStrict, but guard anyway — a raw test must run
        // verbatim with no injected prologue.)
        String source = tc.source();
        if (!isRaw && tc.metadata().flags().contains("onlyStrict")) {
            source = "\"use strict\";\n" + source;
        }
        try {
            engine.eval(source);
        } catch (ParserException pe) {
            return classifyNegative(path, neg, "parse", "SyntaxError", pe);
        } catch (RuntimeException re) {
            String type = ErrorUtils.classify(re);
            // Prefer the structured EngineException.getJsMessage() surface (no
            // host-side framing to parse). Falls back to the message-string path
            // for non-EngineException throwables (parser errors and host exceptions).
            String msg = ErrorUtils.firstLine(re, type, 200);
            if (neg != null) {
                if (!"parse".equals(neg.phase())
                        && (neg.type() == null || neg.type().equals(type) || type == null)) {
                    return ResultRecord.pass(path);
                }
                // A parse-phase negative test that parsed (we're in the runtime catch,
                // not the ParserException catch above) means the engine FAILED to detect
                // an early/parse error: the code ran and typically tripped the harness
                // $DONOTEVALUATE() marker. Bucket these as MissingParseError so the
                // unimplemented-early-error backlog is measurable and not conflated with
                // genuine engine crashes (NPE / StackOverflow), which stay Unknown.
                if ("parse".equals(neg.phase())) {
                    return ResultRecord.fail(path, "MissingParseError",
                            "expected parse-phase " + neg + " but parsed; ran and threw: " + msg);
                }
                return ResultRecord.fail(path, type == null ? "Unknown" : type,
                        "expected negative " + neg + " but got: " + msg);
            }
            return ResultRecord.fail(path, type == null ? "Unknown" : type, msg);
        }

        // eval succeeded
        if (neg != null) {
            // Same missing-early-error case as above, but the un-rejected source also
            // completed without tripping any marker — still a missing parse-phase error.
            if ("parse".equals(neg.phase())) {
                return ResultRecord.fail(path, "MissingParseError",
                        "expected parse-phase " + neg + " but parsed and completed normally");
            }
            return ResultRecord.fail(path, "ExpectedThrow",
                    "expected negative " + neg + " but test completed normally");
        }
        return ResultRecord.pass(path);
    }

    private static ResultRecord classifyNegative(String path, Test262Metadata.Negative neg,
                                                 String actualPhase, String actualType, RuntimeException re) {
        String msg = ErrorUtils.firstLine(re, actualType, 200);
        if (neg != null && actualPhase.equals(neg.phase())
                && (neg.type() == null || neg.type().equals(actualType))) {
            return ResultRecord.pass(path);
        }
        if (neg == null) {
            return ResultRecord.fail(path, actualType, msg);
        }
        return ResultRecord.fail(path, actualType,
                "expected negative " + neg + " but got " + actualPhase + "/" + actualType + ": " + msg);
    }

    /* ------------------------------- host bindings ------------------------------- */

    /**
     * Minimal host-defined bindings required by test262 INTERPRETING.md.
     * Installed before harness helpers and before the test source (non-raw tests only).
     * <p>
     * - {@code print(...args)} — communication sink. Writes to stdout.
     * - {@code $262.global} — best-effort reference to the global scope.
     * - {@code $262.evalScript(src)} — evaluates in the global realm via indirect eval.
     * - {@code $262.gc()} — no capability; throws (INTERPRETING.md permits this).
     * <p>
     * {@code $DONOTEVALUATE} is intentionally not defined here — {@code sta.js}
     * defines it and sta.js is loaded for every non-raw test. Raw tests that
     * use {@code $DONOTEVALUATE} get a ReferenceError, which is fine: they are
     * always parse-phase negative tests where the function is never reached.
     */
    private static final String HOST_BINDINGS_JS =
            "var print = function() {\n" +
            "  var s = '';\n" +
            "  for (var i = 0; i < arguments.length; i++) {\n" +
            "    if (i > 0) s += ' ';\n" +
            "    s += String(arguments[i]);\n" +
            "  }\n" +
            "  console.log(s);\n" +
            "};\n" +
            // `(0, eval)(src)` is the ES spec "indirect eval" form — it evaluates at
            // the realm's global scope (matching INTERPRETING.md's ParseScript +
            // ScriptEvaluation semantics).
            "var $262 = {\n" +
            "  global: (function(){ return this; })(),\n" +
            "  evalScript: function(src) { return (0, eval)(src); },\n" +
            "  gc: function() { throw new Error('gc not supported'); },\n" +
            "  detachArrayBuffer: function() { throw new Error('detachArrayBuffer not supported'); },\n" +
            "  createRealm: function() { throw new Error('createRealm not supported'); },\n" +
            "  agent: { start: function() { throw new Error('agents not supported'); } }\n" +
            "};\n";

    /* ------------------------------- helpers ------------------------------- */

    private static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                    if (i < glob.length() && glob.charAt(i) == '/') i++;
                    continue;
                }
                sb.append("[^/]*");
            } else if (c == '?') {
                sb.append('.');
            } else if ("\\.^$+()[]{}|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
            i++;
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    private static String readTest262Sha(Path test262Root) {
        Path head = test262Root.resolve(".git/HEAD");
        try {
            String h = Files.readString(head).strip();
            if (h.startsWith("ref: ")) {
                Path refFile = test262Root.resolve(".git").resolve(h.substring(5).strip());
                if (Files.isRegularFile(refFile)) return Files.readString(refFile).strip();
            }
            return h;
        } catch (IOException e) {
            return "";
        }
    }

    private static String readHeadSha(Path repoRoot) {
        if (repoRoot == null) return "";
        Path head = repoRoot.resolve(".git/HEAD");
        try {
            String h = Files.readString(head).strip();
            if (h.startsWith("ref: ")) {
                Path refFile = repoRoot.resolve(".git").resolve(h.substring(5).strip());
                if (Files.isRegularFile(refFile)) return Files.readString(refFile).strip().substring(0, 10);
            }
            return h.length() >= 10 ? h.substring(0, 10) : h;
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Read {@code karate-js}'s Maven version from its
     * {@code META-INF/maven/io.karatelabs/karate-js/pom.properties} resource on
     * the classpath. Maven writes this file into every JAR it builds, so the
     * value is reliably present when the runner picks up karate-js as an
     * installed dependency. Empty string when running from a build that
     * hasn't packaged karate-js (rare; shouldn't happen for normal use).
     */
    private static String readKarateJsVersion() {
        // Allow a system-property override for testing / overrides.
        String prop = System.getProperty("karate.js.version", "").trim();
        if (!prop.isEmpty()) return prop;
        try (InputStream in = Test262Runner.class.getResourceAsStream(
                "/META-INF/maven/io.karatelabs/karate-js/pom.properties")) {
            if (in == null) return "";
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String formatDuration(java.time.Duration d) {
        long total = d.toSeconds();
        long m = total / 60;
        long s = total % 60;
        return m + "m" + (s < 10 ? "0" : "") + s + "s";
    }
}

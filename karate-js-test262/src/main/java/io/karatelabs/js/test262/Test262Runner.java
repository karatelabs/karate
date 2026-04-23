package io.karatelabs.js.test262;

import io.karatelabs.js.Engine;
import io.karatelabs.parser.ParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * test262 conformance runner for karate-js.
 * <p>
 * Sequential, fresh {@link Engine} per test, per-test watchdog timeout.
 * Writes results as sorted JSONL to {@code target/results.jsonl} and per-run
 * context to {@code target/run-meta.json}. A timestamped session log is
 * mirrored to {@code target/test262/test262-*.log} via Logback.
 * <p>
 * See {@code karate-js-test262/TEST262.md} for full docs.
 */
public final class Test262Runner {

    private static final Logger logger = LoggerFactory.getLogger(Test262Runner.class);

    /** Periodic progress line cadence: every N tests OR every M seconds, whichever first. */
    private static final int PROGRESS_EVERY_N_TESTS = 500;
    private static final long PROGRESS_EVERY_MS = 5_000L;

    /**
     * Print a line to stdout AND mirror it into the session log file
     * (configured in {@code logback.xml}). This is the single output funnel
     * the runner uses for progress / FAIL / Summary / Aborted lines so
     * {@code tail -f target/test262/test262-*.log} is a live view of the run.
     */
    private static void say(String line) {
        System.out.println(line);
        logger.info(line);
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
            runSingle(cli, expectations, harness);
            return;
        }

        runSuite(cli, expectations, harness);
    }

    /* ------------------------------- full suite ------------------------------- */

    private static void runSuite(Cli cli, Expectations expectations, HarnessLoader harness) throws Exception {
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
        Set<String> resumeDone = cli.resume ? loadExistingPaths(cli.results) : Set.of();

        // Log the session banner to the file appender only — the console already
        // gets its own startup context via Maven's stderr and the runner's output.
        logger.info("test262 runner starting  args={} only={} timeout-ms={} max-duration-ms={} resume={}",
                cli.rawArgs, cli.only == null ? "-" : cli.only, cli.timeoutMs, cli.maxDurationMs, cli.resume);

        Instant started = Instant.now();
        long startedNanos = System.nanoTime();
        long maxDurationNanos = cli.maxDurationMs > 0 ? cli.maxDurationMs * 1_000_000L : Long.MAX_VALUE;

        int pass = 0, fail = 0, skip = 0;
        int processed = 0;
        long lastProgressNanos = startedNanos;
        List<ResultRecord> records = new ArrayList<>(all.size());
        boolean aborted = false;

        // single-thread executor for the watchdog; recreated if a test times out so
        // a runaway test cannot stall subsequent tests by re-queuing behind it
        // (the engine does not poll Thread.interrupt(), so cancel(true) alone cannot
        // reclaim a stuck thread — we retire it and move on).
        ExecutorService exec = newInnerExec();

        try {
            for (Path abs : all) {
                String rel = cli.test262.relativize(abs).toString().replace('\\', '/');
                if (onlyRe != null && !onlyRe.matcher(rel).matches()) continue;
                if (resumeDone.contains(rel)) continue;

                if (System.nanoTime() - startedNanos > maxDurationNanos) {
                    aborted = true;
                    say("");
                    say(String.format("ABORTED: --max-duration %dms exceeded; writing partial results",
                            cli.maxDurationMs));
                    break;
                }

                Test262Case tc;
                boolean loaded;
                try {
                    tc = Test262Case.load(cli.test262, abs);
                    loaded = true;
                } catch (RuntimeException re) {
                    tc = null;
                    loaded = false;
                    say("FAIL " + rel + " — load error: " + re.getMessage());
                    records.add(ResultRecord.fail(rel, "Harness", "failed to load: " + re.getMessage()));
                    fail++;
                }

                if (loaded) {
                    String reason = expectations.matchSkip(tc);
                    if (reason != null) {
                        records.add(ResultRecord.skip(rel, reason));
                        skip++;
                    } else {
                        // Inline timeout handling so we can retire `exec` and install a fresh
                        // one when a test hangs. Without this, the next test queues behind the
                        // stuck inner thread and hits the per-test timeout itself — the whole
                        // suite would slow to 10s/test from a single infinite-loop test.
                        ResultRecord r;
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

                        records.add(r);
                        switch (r.status()) {
                            case PASS -> pass++;
                            case FAIL -> {
                                fail++;
                                say("FAIL " + rel + " — "
                                        + (r.errorType() == null ? "" : r.errorType() + ": ")
                                        + (r.message() == null ? "" : r.message()));
                            }
                            case SKIP -> skip++;
                        }
                    }
                }
                processed++;

                // Periodic progress line — plain text, no \r. Every N tests OR every M seconds
                // of wall time since the last line, whichever fires first. Safe on TTYs and
                // piped/CI output alike; gives long runs an observable heartbeat without
                // needing a ticker thread.
                long nowNanos = System.nanoTime();
                if (processed % PROGRESS_EVERY_N_TESTS == 0
                        || (nowNanos - lastProgressNanos) >= PROGRESS_EVERY_MS * 1_000_000L) {
                    long elapsedMs = (nowNanos - startedNanos) / 1_000_000L;
                    double rate = elapsedMs > 0 ? (processed * 1000.0 / elapsedMs) : 0.0;
                    say(String.format("[progress] %d processed  pass %d  fail %d  skip %d  @ %.0f/s  elapsed %s",
                            processed, pass, fail, skip, rate, formatDuration(java.time.Duration.ofMillis(elapsedMs))));
                    lastProgressNanos = nowNanos;
                }
            }
        } finally {
            exec.shutdownNow();
        }

        Instant ended = Instant.now();

        // Re-read existing records if --resume so the committed JSONL still has everything.
        List<ResultRecord> toWrite = records;
        if (cli.resume && !resumeDone.isEmpty()) {
            final List<ResultRecord> finalRecords = records;
            List<ResultRecord> combined = new ArrayList<>(records);
            combined.addAll(loadExistingRecords(cli.results, rel -> !containsPath(finalRecords, rel)));
            toWrite = combined;
        }

        ResultWriter.write(cli.results, toWrite);

        RunMeta meta = new RunMeta(
                readTest262Sha(cli.test262),
                System.getProperty("karate.js.version", ""),
                readHeadSha(cli.test262.getParent() == null ? Path.of(".") : cli.test262.getParent().getParent()),
                RunMeta.detectJdk(),
                RunMeta.detectOs(),
                started, ended,
                new RunMeta.Counts(pass, fail, skip, pass + fail + skip),
                cli.rawArgs);
        meta.writeTo(cli.runMeta);

        say("");
        say(String.format("%s: %d pass / %d fail / %d skip / %d total in %s",
                aborted ? "Aborted" : "Summary",
                pass, fail, skip, pass + fail + skip,
                formatDuration(java.time.Duration.between(started, ended))));
        say("Results: " + cli.results.toAbsolutePath());
        say("Report:  java io.karatelabs.js.test262.Test262Report");
    }

    private static ExecutorService newInnerExec() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test262-runner");
            t.setDaemon(true);
            return t;
        });
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
            if (r.message() != null) System.out.println("message:     " + r.message());
            if (cli.verbose >= 2) {
                System.out.println();
                System.out.println("--- source ---");
                System.out.println(tc.source());
            }
        } finally {
            exec.shutdownNow();
        }
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
        try {
            engine.eval(tc.source());
        } catch (ParserException pe) {
            return classifyNegative(path, neg, "parse", "SyntaxError", pe);
        } catch (RuntimeException re) {
            String type = ErrorUtils.classify(re);
            String msg = ErrorUtils.firstLine(re.getMessage(), 200);
            if (neg != null) {
                if (!"parse".equals(neg.phase())
                        && (neg.type() == null || neg.type().equals(type) || type == null)) {
                    return ResultRecord.pass(path);
                }
                return ResultRecord.fail(path, type == null ? "Unknown" : type,
                        "expected negative " + neg + " but got: " + msg);
            }
            return ResultRecord.fail(path, type == null ? "Unknown" : type, msg);
        }

        // eval succeeded
        if (neg != null) {
            return ResultRecord.fail(path, "ExpectedThrow",
                    "expected negative " + neg + " but test completed normally");
        }
        return ResultRecord.pass(path);
    }

    private static ResultRecord classifyNegative(String path, Test262Metadata.Negative neg,
                                                 String actualPhase, String actualType, RuntimeException re) {
        String msg = ErrorUtils.firstLine(re.getMessage(), 200);
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

    private static Set<String> loadExistingPaths(Path jsonl) {
        if (!Files.isRegularFile(jsonl)) return Set.of();
        Set<String> out = new HashSet<>();
        try (BufferedReader br = Files.newBufferedReader(jsonl, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf("\"path\":\"");
                if (idx < 0) continue;
                int start = idx + "\"path\":\"".length();
                int end = line.indexOf('"', start);
                if (end > start) out.add(line.substring(start, end));
            }
        } catch (IOException e) {
            return Set.of();
        }
        return out;
    }

    private static List<ResultRecord> loadExistingRecords(Path jsonl, java.util.function.Predicate<String> keepByPath) {
        // Minimal parse to reuse prior records on --resume; we only need to echo them back.
        List<ResultRecord> out = new ArrayList<>();
        if (!Files.isRegularFile(jsonl)) return out;
        try (BufferedReader br = Files.newBufferedReader(jsonl, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String path = between(line, "\"path\":\"", "\"");
                if (path == null || !keepByPath.test(path)) continue;
                String status = between(line, "\"status\":\"", "\"");
                String errorType = between(line, "\"error_type\":\"", "\"");
                String message = between(line, "\"message\":\"", "\"");
                String reason = between(line, "\"reason\":\"", "\"");
                if (status == null) continue;
                out.add(new ResultRecord(
                        path,
                        ResultRecord.Status.valueOf(status),
                        errorType, message, reason));
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return out;
    }

    private static String between(String line, String left, String right) {
        int i = line.indexOf(left);
        if (i < 0) return null;
        int s = i + left.length();
        int e = line.indexOf(right, s);
        if (e < 0) return null;
        return line.substring(s, e);
    }

    private static boolean containsPath(List<ResultRecord> records, String path) {
        for (ResultRecord r : records) if (r.path().equals(path)) return true;
        return false;
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

    private static String formatDuration(java.time.Duration d) {
        long total = d.toSeconds();
        long m = total / 60;
        long s = total % 60;
        return m + "m" + (s < 10 ? "0" : "") + s + "s";
    }
}

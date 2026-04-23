package io.karatelabs.js.test262;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny hand-rolled flag parser for the runner. Keeps things predictable and
 * avoids pulling in a CLI library for a handful of flags.
 */
public final class Cli {

    public Path expectations = Paths.get("config/expectations.yaml");
    public Path test262 = Paths.get("test262");
    public Path results = Paths.get("results.jsonl");
    public Path runMeta = Paths.get("run-meta.json");
    public long timeoutMs = 10_000L;
    public long maxDurationMs = 0L; // 0 = unlimited
    public String only;         // glob; null = no restriction
    public String single;       // relative path to one test262 file; null = full suite
    public int verbose;         // 0, 1, 2
    public boolean resume;
    public final List<String> rawArgs = new ArrayList<>();

    public static Cli parse(String[] args) {
        Cli c = new Cli();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            c.rawArgs.add(a);
            if (needsValue(a)) {
                String v = args[++i];
                c.rawArgs.add(v);
                switch (a) {
                    case "--expectations"  -> c.expectations = Paths.get(v);
                    case "--test262"       -> c.test262 = Paths.get(v);
                    case "--results"       -> c.results = Paths.get(v);
                    case "--run-meta"      -> c.runMeta = Paths.get(v);
                    case "--timeout-ms"    -> c.timeoutMs = Long.parseLong(v);
                    case "--max-duration"  -> c.maxDurationMs = Long.parseLong(v);
                    case "--only"          -> c.only = v;
                    case "--single"        -> c.single = v;
                    default -> { /* unreachable: needsValue gates this */ }
                }
                continue;
            }
            switch (a) {
                case "--resume"     -> c.resume = true;
                case "-v"           -> c.verbose = Math.max(c.verbose, 1);
                case "-vv"          -> c.verbose = 2;
                case "-h", "--help" -> { printHelp(); System.exit(0); }
                default -> {
                    if (a.startsWith("-")) {
                        System.err.println("unknown flag: " + a);
                        printHelp();
                        System.exit(2);
                    }
                }
            }
        }
        return c;
    }

    private static boolean needsValue(String flag) {
        return switch (flag) {
            case "--expectations", "--test262", "--results", "--run-meta",
                 "--timeout-ms", "--max-duration", "--only", "--single" -> true;
            default -> false;
        };
    }

    public static void printHelp() {
        System.out.println("""
            karate-js-test262 runner

            Usage: java io.karatelabs.js.test262.Test262Runner [flags]

            Flags:
              --expectations <path>    YAML skip list (default: config/expectations.yaml)
              --test262 <path>         test262 clone root (default: test262)
              --results <path>         output JSONL path (default: results.jsonl)
              --run-meta <path>        output metadata JSON (default: run-meta.json)
              --timeout-ms <n>         per-test watchdog (default: 10000)
              --max-duration <ms>      overall wall-clock cap; writes partial results on hit (default: 0 = unlimited)
              --only <glob>            restrict to tests matching a path glob, e.g. 'test/language/**'
              --single <path>          run exactly one test (no file writes); use -vv to trace
              -v | -vv                 verbose output (--single mode only)
              --resume                 skip tests already present in the existing results.jsonl
              -h | --help              show this help

            Prerequisite: ./fetch-test262.sh (one-time)
            """);
    }
}

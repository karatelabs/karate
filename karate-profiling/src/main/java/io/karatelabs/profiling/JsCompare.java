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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the A/B table for the js-* family: two builds of karate-js, same workloads, elapsed
 * time per iteration. The sibling of {@link Compare}, deliberately separate — that class is
 * throughput-shaped and pairs by timestamp adjacency, and both properties are wrong here.
 *
 * <p><b>Pairing is by run tag, never by adjacency.</b> Every run carries
 * {@code --run-tag <matrix>:p<N>:<a|b>} (etc/ec2/js-matrix.sh writes it; a hand-run pair must
 * too), and a pair is exactly the {@code a} and {@code b} runs sharing a matrix, workload and
 * pair number. Adjacency pairing breaks the moment one run of a sweep fails: the sequence
 * closes over the hole and two runs from different pairs read as one, which is a wrong number
 * with nothing visibly wrong. A tag scheme cannot close over a hole — the orphan is reported
 * by name.
 *
 * <p>Arm semantics are fixed by the tag: <b>{@code a} is the base build, {@code b} the
 * candidate</b>, and every delta is (B − A) / A — negative means the candidate is faster.
 * "Which jar is which" is decided where the runs are launched, recorded in every digest's
 * {@code js jar} row, and never inferred from hashes.
 *
 * <p>Doctrine as elsewhere: everything reports, nothing asserts. Ineligible runs and broken
 * pairs are named, never silently dropped; the table prints the numbers an acceptance rule
 * needs and takes no view on them.
 */
final class JsCompare {

    /** How a js A/B run is recognised: the digest row the {@code --js-jar} flag writes. */
    static boolean isJsRun(Path dir) {
        String digest = readString(dir.resolve("digest.md"));
        return digest != null && digest.contains("\n| js jar | ");
    }

    record Run(Path dir, String stamp, String workload, String jsJar, String jsSha,
               String matrix, int pair, String armRole, String sysprops, String build,
               long iterationsRequested, long completed, long errors, long elapsedMs,
               int threads, String xmx, String collector, String jdk, boolean jfrOff,
               String outcome) {

        double msPerIteration() {
            return (double) elapsedMs / iterationsRequested;
        }

        /**
         * The first reason this run cannot enter a table, or null. Nothing that fails here is
         * averaged, and nothing here is a sentinel that could reach arithmetic.
         */
        String ineligible() {
            if (workload == null) {
                return "no workload row";
            }
            if (jsSha == null) {
                return "no sha256 in the js jar row";
            }
            if (matrix == null) {
                return "no run tag (expected --run-tag <matrix>:p<N>:<a|b>; pairing is by tag,"
                        + " never by adjacency)";
            }
            if (iterationsRequested <= 0) {
                return "no iteration bound (a duration-bounded run has no fixed denominator)";
            }
            if (elapsedMs <= 0) {
                return "no elapsed row";
            }
            if (errors != 0) {
                return errors < 0 ? "no errors row" : errors + " error(s) — the oracle or the"
                        + " engine failed, so the timing is void";
            }
            if (completed != iterationsRequested) {
                return "completed " + completed + " of " + iterationsRequested
                        + " requested iterations — an unequal denominator is not a slower run,"
                        + " it is a different run";
            }
            if (!"completed".equals(outcome)) {
                return "outcome is '" + outcome + "'";
            }
            return null;
        }

        /** What must match between two runs before their difference means anything. */
        String comparableShape() {
            return "threads=" + threads + " xmx=" + xmx + " collector=" + collector
                    + " jdk=" + jdk + " jfr=" + (jfrOff ? "off" : "on")
                    + " iterations=" + iterationsRequested;
        }

    }

    record Pair(Run a, Run b, String order) {

        double deltaPercent() {
            return (b.msPerIteration() - a.msPerIteration()) / a.msPerIteration() * 100.0;
        }

        double ratio() {
            return b.msPerIteration() / a.msPerIteration();
        }

    }

    static int main(List<Path> dirs) {
        List<Run> runs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path dir : dirs) {
            Run run = read(dir);
            if (run == null) {
                warnings.add("unreadable digest, skipped: " + dir.getFileName());
                continue;
            }
            String reason = run.ineligible();
            if (reason != null) {
                warnings.add("ineligible (" + reason + "): " + dir.getFileName());
                continue;
            }
            runs.add(run);
        }
        runs.sort(Comparator.comparing(Run::stamp));

        // (matrix, workload, pair) → the runs claiming that cell. Exactly one a and one b make
        // a pair; anything else is a broken cell and is reported as such.
        Map<String, List<Run>> cells = new LinkedHashMap<>();
        for (Run run : runs) {
            cells.computeIfAbsent(run.matrix() + " | " + run.workload() + " | p" + run.pair(),
                    k -> new ArrayList<>()).add(run);
        }
        // matrix → workload → pairs, preserving pair order.
        Map<String, Map<String, List<Pair>>> matrices = new LinkedHashMap<>();
        for (Map.Entry<String, List<Run>> cell : cells.entrySet()) {
            List<Run> claimants = cell.getValue();
            Run a = null;
            Run b = null;
            boolean duplicate = false;
            for (Run run : claimants) {
                if ("a".equals(run.armRole())) {
                    duplicate |= a != null;
                    a = run;
                } else {
                    duplicate |= b != null;
                    b = run;
                }
            }
            if (duplicate) {
                warnings.add("cell " + cell.getKey() + " has duplicate arms ("
                        + claimants.size() + " runs) — none paired; was a tag reused?");
                continue;
            }
            if (a == null || b == null) {
                Run orphan = a == null ? b : a;
                warnings.add("no partner for " + orphan.dir().getFileName() + " (cell "
                        + cell.getKey() + " has only arm " + orphan.armRole() + ")");
                continue;
            }
            if (!a.comparableShape().equals(b.comparableShape())) {
                warnings.add("cell " + cell.getKey() + " not paired — the arms differ in shape:"
                        + " a: " + a.comparableShape() + " vs b: " + b.comparableShape());
                continue;
            }
            String order = a.stamp().compareTo(b.stamp()) <= 0 ? "a→b" : "b→a";
            matrices.computeIfAbsent(a.matrix(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.workload(), k -> new ArrayList<>())
                    .add(new Pair(a, b, order));
        }

        StringBuilder out = new StringBuilder();
        int tables = 0;
        for (Map.Entry<String, Map<String, List<Pair>>> matrix : matrices.entrySet()) {
            tables += appendMatrix(out, matrix.getKey(), matrix.getValue(), warnings);
        }
        for (String warning : warnings) {
            out.append("\n> **check** — ").append(warning).append("\n");
        }
        System.out.print(out);
        if (tables == 0) {
            System.err.println("[compare] no js pairs formed from " + runs.size()
                    + " eligible runs — see the notes above");
            return 1;
        }
        return 0;
    }

    private static int appendMatrix(StringBuilder out, String matrix,
                                    Map<String, List<Pair>> byWorkload, List<String> warnings) {
        List<Pair> all = byWorkload.values().stream().flatMap(List::stream).toList();
        Run anyA = all.get(0).a();
        Run anyB = all.get(0).b();
        out.append("\n## js A/B — ").append(matrix).append("\n\n");
        out.append("- **A (base):** ").append(armDescription(all, Pair::a)).append("\n");
        out.append("- **B (candidate):** ").append(armDescription(all, Pair::b)).append("\n");
        out.append("- constants: threads=").append(anyA.threads()).append(", xmx=")
                .append(anyA.xmx()).append(", jdk=").append(anyA.jdk()).append(", jfr=")
                .append(anyA.jfrOff() ? "off" : "on").append(", build=")
                .append(anyA.build() == null ? "(unstamped)" : anyA.build()).append("\n");
        if (anyA.jsSha().equals(anyB.jsSha())
                && java.util.Objects.equals(anyA.sysprops(), anyB.sysprops())) {
            out.append("- **the arms are identical** (same jar, same sysprops) — this is a"
                    + " null control; its deltas are the noise floor, not an effect\n");
        }
        if (!anyA.jfrOff()) {
            out.append("- note: JFR was ON — recording cost tracks allocation rate, so two"
                    + " builds that allocate differently pay it differently. Timing tables are"
                    + " usually taken with --no-jfr; read this one as diagnostic.\n");
        }
        out.append("\n");

        // Per-row pair tables.
        Map<String, Double> meanRatioByWorkload = new LinkedHashMap<>();
        for (Map.Entry<String, List<Pair>> entry : byWorkload.entrySet()) {
            String workload = entry.getKey();
            List<Pair> pairs = entry.getValue();
            pairs.sort(Comparator.comparingInt(p -> p.a().pair()));
            out.append("#### ").append(workload).append(" — ").append(pairs.size())
                    .append(" pair(s), ").append(pairs.get(0).a().iterationsRequested())
                    .append(" iterations/run\n\n");
            out.append("| pair | order | A ms/iter | B ms/iter | B vs A |\n")
                    .append("|---|---|---:|---:|---:|\n");
            List<Double> deltas = new ArrayList<>();
            List<Double> ratios = new ArrayList<>();
            for (Pair pair : pairs) {
                deltas.add(pair.deltaPercent());
                ratios.add(pair.ratio());
                out.append("| p").append(pair.a().pair()).append(" | ").append(pair.order())
                        .append(" | ").append(Compare.fixed(pair.a().msPerIteration(), 4))
                        .append(" | ").append(Compare.fixed(pair.b().msPerIteration(), 4))
                        .append(" | ").append(Compare.signed(pair.deltaPercent()))
                        .append("% |\n");
            }
            double meanDelta = Compare.mean(deltas);
            double sd = Compare.sd(deltas);
            out.append("| **mean** | | | | **").append(Compare.signed(meanDelta))
                    .append("% ± ").append(Compare.fixed(sd, 2)).append("** |\n\n");
            long needed = Compare.neededPairs(deltas);
            if (pairs.size() >= 2 && needed > 0) {
                out.append("_Resolution: sd ").append(Compare.fixed(sd, 2)).append(" over ")
                        .append(pairs.size()).append(" pairs; ~").append(needed)
                        .append(" pairs would resolve this mean to half itself. With under 4")
                        .append(" pairs the sd itself is unstable — read the spread, not the")
                        .append(" second decimal._\n\n");
            }
            meanRatioByWorkload.put(workload, Compare.mean(ratios));
            if (pairs.size() % 2 == 1) {
                warnings.add(workload + " in " + matrix + " has an odd pair count ("
                        + pairs.size() + ") — alternation cancels linear drift only over an"
                        + " even count; the residual loads one arm by up to half a run slot");
            }
            for (Pair pair : pairs) {
                warnCheck(warnings, matrix, pair.a());
                warnCheck(warnings, matrix, pair.b());
            }
        }

        // Cross-row summary: geomean over the acceptance rows; the guard row reported beside
        // them, never inside. Missing rows are named — a geomean over four rows is not the
        // same number as one over five.
        out.append("#### Cross-row summary — ").append(matrix).append("\n\n");
        out.append("| row | mean B/A | mean delta |\n|---|---:|---:|\n");
        double logSum = 0;
        int counted = 0;
        List<String> missing = new ArrayList<>(List.of(ACCEPTANCE_ROWS));
        for (Map.Entry<String, Double> entry : meanRatioByWorkload.entrySet()) {
            boolean acceptance = missing.remove(entry.getKey());
            out.append("| ").append(entry.getKey()).append(acceptance ? "" : " _(guard)_")
                    .append(" | ").append(Compare.fixed(entry.getValue(), 4)).append(" | ")
                    .append(Compare.signed((entry.getValue() - 1) * 100)).append("% |\n");
            if (acceptance) {
                logSum += Math.log(entry.getValue());
                counted++;
            }
        }
        if (counted > 0) {
            out.append("| **geomean (").append(counted).append(" acceptance rows)** | **")
                    .append(Compare.fixed(Math.exp(logSum / counted), 4)).append("** | **")
                    .append(Compare.signed((Math.exp(logSum / counted) - 1) * 100))
                    .append("%** |\n");
        }
        out.append("\n");
        if (!missing.isEmpty()) {
            warnings.add(matrix + " is missing acceptance row(s) " + missing
                    + " — its geomean covers " + counted + " of " + ACCEPTANCE_ROWS.length
                    + " rows and is not comparable with a full one");
        }
        return byWorkload.size();
    }

    private static final String[] ACCEPTANCE_ROWS = {
            "js-arithmetic", "js-strings", "js-objects", "js-functions", "js-mixed"};

    /** One line naming an arm: jar + sysprops. Flags disagreement across the matrix loudly. */
    private static String armDescription(List<Pair> all, java.util.function.Function<Pair, Run> side) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Pair pair : all) {
            Run run = side.apply(pair);
            seen.add(run.jsJar() + (run.sysprops() == null ? "" : " with " + run.sysprops()));
        }
        return seen.size() == 1 ? seen.iterator().next()
                : "**INCONSISTENT across the matrix:** " + String.join(" / ", seen);
    }

    private static void warnCheck(List<String> warnings, String matrix, Run run) {
        if (run.elapsedMs() < 20_000) {
            warnings.add(run.dir().getFileName() + " measured only "
                    + Compare.fixed(run.elapsedMs() / 1000.0, 1) + " s — under ~20 s the window"
                    + " is startup-shaped; raise --iterations for " + run.workload());
        }
    }

    // ---------------------------------------------------------------- parsing

    private static Run read(Path dir) {
        String digest = readString(dir.resolve("digest.md"));
        if (digest == null) {
            return null;
        }
        String stamp = dir.getFileName().toString()
                .replaceAll(".*-(\\d{4}-\\d{2}-\\d{2}-\\d{6})$", "$1");
        String jsJar = rowValue(digest, "js jar");
        String tag = rowValue(digest, "run tag");
        String matrix = null;
        int pair = -1;
        String armRole = null;
        if (tag != null) {
            // <matrix>:p<N>:<a|b> — parsed from the end so the matrix id may itself contain
            // colons.
            int lastColon = tag.lastIndexOf(':');
            int prevColon = lastColon <= 0 ? -1 : tag.lastIndexOf(':', lastColon - 1);
            if (prevColon > 0) {
                String role = tag.substring(lastColon + 1);
                String pairPart = tag.substring(prevColon + 1, lastColon);
                if ((role.equals("a") || role.equals("b")) && pairPart.matches("p\\d+")) {
                    matrix = tag.substring(0, prevColon);
                    pair = Integer.parseInt(pairPart.substring(1));
                    armRole = role;
                }
            }
        }
        String sysprops = rowValue(digest, "sysprops");
        if (sysprops != null) {
            // The row carries a trailing explanation for human readers; the backticked span is
            // the value.
            int open = sysprops.indexOf('`');
            int close = sysprops.indexOf('`', open + 1);
            if (open >= 0 && close > open) {
                sysprops = sysprops.substring(open + 1, close);
            }
        }
        return new Run(dir, stamp,
                rowValue(digest, "workload"),
                jsJar,
                shaOf(jsJar),
                matrix, pair, armRole,
                sysprops,
                rowValue(digest, "build"),
                boundIterations(rowValue(digest, "bound")),
                firstLong(rowValue(digest, "completed")),
                firstLong(rowValue(digest, "errors")),
                firstLong(rowValue(digest, "elapsed")),
                (int) firstLong(rowValue(digest, "threads")),
                rowValue(digest, "-Xmx"),
                rowValue(digest, "collector"),
                rowValue(digest, "jdk"),
                rowValue(digest, "jfr") != null,
                rowValue(digest, "outcome"));
    }

    /** The value cell of a {@code | label | value |} run-summary row, or null. */
    private static String rowValue(String digest, String label) {
        String prefix = "| " + label + " | ";
        for (String line : digest.split("\n")) {
            if (line.startsWith(prefix) && line.endsWith(" |")) {
                return line.substring(prefix.length(), line.length() - 2).trim();
            }
        }
        return null;
    }

    /** The 16-hex token after {@code sha256} in the js jar row, or null. */
    private static String shaOf(String jsJarRow) {
        if (jsJarRow == null) {
            return null;
        }
        String[] tokens = jsJarRow.split("\\s+");
        for (int i = 0; i + 1 < tokens.length; i++) {
            if (tokens[i].equals("sha256")) {
                return tokens[i + 1];
            }
        }
        return null;
    }

    private static long boundIterations(String bound) {
        if (bound == null || !bound.startsWith("iterations=")) {
            return -1;
        }
        try {
            return Long.parseLong(bound.substring("iterations=".length()).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Leading integer of a row value ("250000 of 250000 requested…" → 250000), or -1. */
    private static long firstLong(String value) {
        if (value == null) {
            return -1;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return -1;
        }
        try {
            return Long.parseLong(value.substring(0, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readString(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private JsCompare() {
    }

}

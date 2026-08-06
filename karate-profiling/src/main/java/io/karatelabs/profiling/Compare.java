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
import java.util.Locale;
import java.util.Map;

/**
 * Turns a set of parity run directories into the table that goes in docs/PROFILING.md §10.
 *
 * <p><b>This exists because the alternative has already failed.</b> Every published parity number
 * was scraped by hand, and commit "the stats scrape skipped a decimal cell and shifted every
 * column" is what that cost on a matrix of eight runs. A matrix of twenty, taken on a machine
 * nobody is watching, will fail the same way and less visibly. The arithmetic below is the
 * arithmetic §10 documents; the point is that it now happens once, in a place that can be read and
 * corrected, rather than once per analysis in a shell history nobody keeps.
 *
 * <p><b>Throughput comes from the mock, never from Gatling.</b> Gatling divides its request count
 * by a duration rounded to whole seconds, which at these run lengths quantises the rate in steps of
 * several percent — enough to render two arms a second apart as the same figure, which is exactly
 * how the first matrix concluded the arms were identical. {@code MockStats.servedPerSecond} is the
 * same requests over a nanosecond window.
 *
 * <p><b>What it will not do is decide anything.</b> Rows that fail a check are marked and kept, not
 * dropped, and the spread is always printed next to the mean — a standard deviation larger than the
 * effect is the finding, not an inconvenience to average away. Nothing here asserts, which is the
 * same doctrine as the rest of the harness.
 *
 * <pre>
 * etc/run.sh compare target/profiling/gatling-http-*
 * </pre>
 */
public final class Compare {

    private Compare() {
    }

    /** Requests each arm's client makes per iteration, and the users driving them, from the run. */
    record Run(Path dir, String arm, int tierMillis, int users, long iterationsRequested,
               long served, double servedPerSecond, double sleepMicrosMean, long peakInFlight,
               long distinctPeerPorts, long ko, double injectorCores, double windowSeconds,
               double p50, double p99) {

        /**
         * Gatling splits the requested total across users and rounds up, so the run is a multiple
         * of the user count — the digest says so per run. Requests per iteration falls out of what
         * the mock actually served over that, rather than being assumed to be two.
         */
        double requestsPerIteration() {
            long actual = (long) (Math.ceil(iterationsRequested / (double) users) * users);
            return actual <= 0 ? 0 : served / (double) actual;
        }

        /**
         * Seconds one user spends on one iteration. A closed loop makes throughput exactly
         * {@code users / iteration time}, so this inverts the measured rate back into the
         * per-iteration serial time the comparison is actually about.
         */
        double iterationMillis() {
            double rate = servedPerSecond / requestsPerIteration();   // iterations per second
            return rate <= 0 ? 0 : users * 1000.0 / rate;
        }
    }

    static int main(List<String> argv) {
        List<Run> runs = new ArrayList<>();
        for (String arg : argv) {
            Path dir = Path.of(arg);
            Run run = read(dir);
            if (run == null) {
                System.err.println("[compare] skipped (no parity data): " + dir);
                continue;
            }
            runs.add(run);
        }
        if (runs.size() < 2) {
            System.err.println("[compare] need at least two parity runs, found " + runs.size());
            return 2;
        }
        // Directory names carry a sortable timestamp, and run order is what pairs them.
        runs.sort(Comparator.comparing(r -> r.dir().getFileName().toString()
                .replaceAll(".*-(\\d{4}-\\d{2}-\\d{2}-\\d{6})$", "$1")));

        Map<Integer, List<Pair>> byTier = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i + 1 < runs.size(); i++) {
            Run first = runs.get(i);
            Run second = runs.get(i + 1);
            if (first.arm().equals(second.arm())) {
                warnings.add("consecutive " + first.arm() + " runs, not paired: "
                        + first.dir().getFileName() + " then " + second.dir().getFileName());
                continue;
            }
            if (first.tierMillis() != second.tierMillis()) {
                warnings.add("tier changed mid-pair, not paired: " + first.dir().getFileName()
                        + " (" + first.tierMillis() + "ms) then " + second.dir().getFileName()
                        + " (" + second.tierMillis() + "ms)");
                continue;
            }
            Run plain = first.arm().equals("plain") ? first : second;
            Run karate = first.arm().equals("plain") ? second : first;
            byTier.computeIfAbsent(first.tierMillis(), k -> new ArrayList<>())
                    .add(new Pair(plain, karate, first.arm().charAt(0) + "→" + second.arm().charAt(0)));
            i++;   // consumed both
        }

        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, List<Pair>> tier : byTier.entrySet()) {
            appendTier(out, tier.getKey(), tier.getValue());
        }
        for (String warning : warnings) {
            out.append("\n> **unpaired** — ").append(warning).append("\n");
        }
        System.out.print(out);
        return 0;
    }

    record Pair(Run plain, Run karate, String order) {

        double deficitPercent() {
            return plain.servedPerSecond() <= 0 ? 0
                    : 100 * (plain.servedPerSecond() - karate.servedPerSecond()) / plain.servedPerSecond();
        }

        /** The window difference, per iteration. The conservative figure — see {@link #correctedMillis}. */
        double addedMillis() {
            return karate.iterationMillis() - plain.iterationMillis();
        }

        /**
         * The injected sleep is a real {@code Thread.sleep} whose overshoot varies with load, and
         * the mock reports what it actually took rather than the knob value. When it slept less on
         * one arm, that arm was handed iteration time the other did not get. §10 found this running
         * consistently in Karate's favour, which makes the uncorrected number the conservative one
         * and this the honest upper end of the range.
         */
        double correctedMillis() {
            double perIteration = karate.requestsPerIteration();
            return addedMillis() + perIteration * (plain.sleepMicrosMean() - karate.sleepMicrosMean()) / 1000.0;
        }
    }

    private static void appendTier(StringBuilder out, int tierMillis, List<Pair> pairs) {
        double iteration = pairs.isEmpty() ? 0 : pairs.get(0).plain().iterationMillis();
        out.append("\n#### ").append(tierMillis).append(" ms tier — ")
                .append(pairs.size()).append(pairs.size() == 1 ? " pair" : " pairs")
                .append(", ~").append(fixed(iteration, 0)).append(" ms iteration\n\n");
        out.append("| pair | order | plain req/s | karate req/s | karate deficit | added ms/iter | ")
                .append("sleep-corrected | injector cores (p/k) | run directories |\n")
                .append("|---:|---|---:|---:|---:|---:|---:|---:|---|\n");
        List<Double> deficits = new ArrayList<>();
        List<Double> added = new ArrayList<>();
        List<Double> corrected = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            deficits.add(pair.deficitPercent());
            added.add(pair.addedMillis());
            corrected.add(pair.correctedMillis());
            out.append("| ").append(i + 1).append(" | ").append(pair.order()).append(" | ")
                    .append(fixed(pair.plain().servedPerSecond(), 1)).append(" | ")
                    .append(fixed(pair.karate().servedPerSecond(), 1)).append(" | ")
                    .append(fixed(pair.deficitPercent(), 1)).append("% | ")
                    .append(signed(pair.addedMillis())).append(" | ")
                    .append(signed(pair.correctedMillis())).append(" | ")
                    .append(cores(pair.plain())).append(" / ").append(cores(pair.karate())).append(" | `")
                    .append(pair.plain().dir().getFileName()).append("` / `")
                    .append(pair.karate().dir().getFileName()).append("` |\n");
            checks(pair, i + 1, flags);
        }
        out.append("| **mean** | | **").append(fixed(mean(collect(pairs, true)), 1)).append("** | **")
                .append(fixed(mean(collect(pairs, false)), 1)).append("** | **")
                .append(fixed(mean(deficits), 1)).append("%** | **").append(signed(mean(added)))
                .append("** (sd ").append(fixed(sd(added), 2)).append(") | **")
                .append(signed(mean(corrected))).append("** (sd ").append(fixed(sd(corrected), 2))
                .append(") | | |\n");

        out.append("\n**Resolution.** sd ").append(fixed(sd(added), 2))
                .append(" ms against a mean of ").append(signed(mean(added))).append(" ms");
        if (sd(added) >= Math.abs(mean(added))) {
            out.append(" — **the spread is larger than the effect, so this tier is not resolved by ")
                    .append(pairs.size()).append(" pairs.** More pairs help only if the machine is ")
                    .append("quiet; if it is not, they will not converge at any count.\n");
        } else {
            out.append(" — the effect is above the spread at this pair count.\n");
        }
        for (String flag : flags) {
            out.append("\n> **check** — ").append(flag).append("\n");
        }
    }

    /**
     * The three acceptance checks §10 sets out, applied per pair rather than remembered. They are
     * reported, never enforced: a flagged row stays in the table and in the mean, because deciding
     * what a flag means is the reader's job and silently dropping a row is how a matrix comes to
     * look cleaner than the runs behind it.
     */
    private static void checks(Pair pair, int index, List<String> flags) {
        for (Run run : List.of(pair.plain(), pair.karate())) {
            String who = "pair " + index + " " + run.arm();
            if (run.ko() > 0) {
                flags.add(who + ": **" + run.ko() + " ko** — failed requests leave the sample and take "
                        + "the slow ones with them, so this row is not a slower point, it is a point "
                        + "with holes.");
            }
            if (run.peakInFlight() != run.users()) {
                flags.add(who + ": peak in-flight " + run.peakInFlight() + " against " + run.users()
                        + " users — the mock was not holding what the injector offered.");
            }
            if (run.injectorCores() > 0.8 * Runtime.getRuntime().availableProcessors()) {
                flags.add(who + ": injector at " + fixed(run.injectorCores(), 1) + " cores — near "
                        + "saturation, so this arm may be reporting the machine rather than the client.");
            }
        }
        if (pair.plain().served() != pair.karate().served()) {
            flags.add("pair " + index + ": arms served different request counts ("
                    + pair.plain().served() + " vs " + pair.karate().served() + ").");
        }
    }

    private static String cores(Run run) {
        return run.injectorCores() < 0 ? "?" : fixed(run.injectorCores(), 1);
    }

    private static List<Double> collect(List<Pair> pairs, boolean plain) {
        List<Double> values = new ArrayList<>();
        for (Pair pair : pairs) {
            values.add(plain ? pair.plain().servedPerSecond() : pair.karate().servedPerSecond());
        }
        return values;
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }

    /** Sample standard deviation — n-1, because these are pairs drawn from a process, not a census. */
    private static double sd(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double mean = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / (values.size() - 1));
    }

    private static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    /**
     * Everything comes out of {@code digest.md}, which is the artifact the disk-hygiene rule keeps:
     * recordings and reports are deleted after every result, so a comparison that needed them could
     * only ever be run once, immediately, by whoever took the runs.
     */
    private static Run read(Path dir) {
        String digest = readString(dir.resolve("digest.md"));
        if (digest == null) {
            return null;
        }
        String mockStats = section(digest, "\"served\":");
        if (mockStats == null) {
            return null;
        }
        String name = dir.getFileName().toString();
        String arm = name.contains("-karate-") ? "karate" : name.contains("-plain-") ? "plain" : null;
        if (arm == null) {
            return null;
        }
        String mockConfig = section(digest, "\"latencyMillis\":");
        return new Run(dir, arm,
                (int) LoadProfile.mockNumber(mockConfig, "latencyMillis"),
                (int) row(digest, "threads"),
                (long) rowIterations(digest),
                (long) LoadProfile.mockNumber(mockStats, "served"),
                LoadProfile.mockNumber(mockStats, "servedPerSecond"),
                LoadProfile.mockNumber(mockStats, "sleepMicrosMean"),
                (long) LoadProfile.mockNumber(mockStats, "peakInFlight"),
                (long) LoadProfile.mockNumber(mockStats, "distinctPeerPorts"),
                ko(digest),
                injectorCores(digest),
                LoadProfile.mockNumber(mockStats, "loadWindowSeconds"),
                percentile(digest, 0), percentile(digest, 3));
    }

    /** The JSON object on the line carrying a given key — the digest fences several of them. */
    private static String section(String digest, String key) {
        int at = digest.indexOf(key);
        if (at < 0) {
            return null;
        }
        int open = digest.lastIndexOf('{', at);
        int close = digest.indexOf('}', at);
        return open < 0 || close < 0 ? null : digest.substring(open, close + 1);
    }

    private static double row(String digest, String label) {
        int at = digest.indexOf("| " + label + " | ");
        if (at < 0) {
            return -1;
        }
        int from = at + label.length() + 5;
        int to = digest.indexOf(" |", from);
        try {
            return Double.parseDouble(digest.substring(from, to).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static double rowIterations(String digest) {
        int at = digest.indexOf("| bound | iterations=");
        if (at < 0) {
            return -1;
        }
        int from = at + "| bound | iterations=".length();
        int to = digest.indexOf(" |", from);
        try {
            return Double.parseDouble(digest.substring(from, to).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static long ko(String digest) {
        int at = digest.indexOf("| ok / ko | ");
        if (at < 0) {
            return -1;
        }
        int bold = digest.indexOf("**", at);
        int end = digest.indexOf("**", bold + 2);
        try {
            return Long.parseLong(digest.substring(bold + 2, end).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** The injector's row of the CPU headroom panel; -1 for a run taken before that panel existed. */
    private static double injectorCores(String digest) {
        int at = digest.indexOf("| workload (the injector) |");
        if (at < 0) {
            return -1;
        }
        int bold = digest.indexOf("**", at);
        int end = digest.indexOf("**", bold + 2);
        try {
            return Double.parseDouble(digest.substring(bold + 2, end).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static double percentile(String digest, int index) {
        int at = digest.indexOf("| p50 / p75 / p95 / p99 | ");
        if (at < 0) {
            return -1;
        }
        int from = at + "| p50 / p75 / p95 / p99 | ".length();
        String[] parts = digest.substring(from, digest.indexOf(" |", from)).trim().split(" / ");
        try {
            return index < parts.length ? Double.parseDouble(parts[index].replace(" ms", "").trim()) : -1;
        } catch (RuntimeException e) {
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

}

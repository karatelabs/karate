/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The load numbers a Gatling run produced — request counts, throughput and the response-time
 * distribution — extracted into the digest so two runs can be diffed as text instead of by opening
 * two HTML reports side by side. Step C of docs/PROFILING.md §10.
 *
 * <p><b>Why this is a forked pass and not a parse.</b> Gatling has written {@code simulation.log}
 * as a <i>binary</i> format since 3.12, so scraping it as text is not an option and reading it
 * means coding against Gatling's internal reader — the same version-fragility that already broke
 * this harness once when {@code Gatling.fromMap} was removed in 3.15. Instead the parent asks
 * Gatling itself to render its report ({@code -ro}, reports-only) and reads the numbers out of the
 * table it renders.</p>
 *
 * <p><b>It runs in the parent, after the child has exited.</b> The harness disables report
 * generation during the run ({@code -nr}) because it is a second pass over the whole simulation
 * log that allocates heavily — and would land in the digest as though it were load-driving cost.
 * That objection is about <i>when</i> the pass runs, not whether: run it afterwards, in a separate
 * JVM, and it contaminates nothing.</p>
 */
public final class LoadProfile {

    /** The global row of Gatling's stats table, which is server-rendered into index.html. */
    public record Stats(long total, long ok, long ko, long perSecond,
                 long min, long p50, long p75, long p95, long p99, long max,
                 long mean, long stdDev) {
    }

    private LoadProfile() {
    }

    /**
     * Renders the report for a completed run and returns its global stats, or null when there is
     * nothing to render or the pass fails. Never throws: a missing load profile makes the digest
     * less useful, and failing the run over it would throw away the recording as well.
     */
    public static Stats extract(Path runDir, String classpath) {
        Path gatlingDir = runDir.resolve("gatling");
        if (!Files.isDirectory(gatlingDir)) {
            return null;
        }
        try (var dirs = Files.list(gatlingDir)) {
            Path simulation = dirs.filter(Files::isDirectory).findFirst().orElse(null);
            if (simulation == null) {
                return null;
            }
            Path index = simulation.resolve("index.html");
            if (!Files.exists(index) && !render(gatlingDir, simulation.getFileName().toString(), classpath)) {
                return null;
            }
            return Files.exists(index) ? parse(Files.readString(index)) : null;
        } catch (Exception e) {
            System.err.println("[digest] could not extract the load profile: " + e);
            return null;
        }
    }

    private static boolean render(Path gatlingDir, String simulation, String classpath) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                // Gatling's report generator reflects into java.lang internals; without this it
                // dies with "module java.base does not open java.lang". Being a forked pass is
                // what lets the flag be added at all — the parent JVM is already running.
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-cp", classpath,
                "io.gatling.app.Gatling",
                "-ro", simulation,
                "-rf", gatlingDir.toAbsolutePath().toString()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(gatlingDir.resolve("reports.log").toFile()).start();
        return process.waitFor() == 0;
    }

    /**
     * Reads the first row of the rendered stats table. Gatling renders it server-side as plain
     * {@code <td>} cells, in a fixed column order: total, ok, ko, ko%, count/s, then min, 50th,
     * 75th, 95th, 99th, max, mean and standard deviation.
     */
    private static Stats parse(String html) {
        Matcher matcher = Pattern.compile("<td[^>]*>\\s*([0-9]+)\\s*</td>").matcher(html);
        List<Long> cells = new ArrayList<>();
        while (matcher.find() && cells.size() < 13) {
            cells.add(Long.parseLong(matcher.group(1)));
        }
        if (cells.size() < 13) {
            return null;
        }
        return new Stats(cells.get(0), cells.get(1), cells.get(2), cells.get(4),
                cells.get(5), cells.get(6), cells.get(7), cells.get(8), cells.get(9),
                cells.get(10), cells.get(11), cells.get(12));
    }

    /** The mock's own lines from {@code mock.log} — its config at startup and its stats at exit. */
    public static String mockLine(Path runDir, String prefix) {
        Path log = runDir.resolve("mock.log");
        if (!Files.exists(log)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
        } catch (IOException e) {
            // the digest is better off without it than failing over it
        }
        return null;
    }

}

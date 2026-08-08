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

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A class histogram of the live heap, taken from inside the child and written into the run
 * directory.
 *
 * <p><b>This exists because the one question the digest could not answer had to be answered by
 * hand.</b> The live-set panel says <i>how much</i> survived; nothing said <i>what</i>. Answering
 * it during a two-hour soak meant sshing to the injector, finding the child with
 * {@code jcmd -l | grep profiling.Child}, running {@code GC.class_histogram} twice at
 * hand-timed moments, and reading the result in a terminal — where it stayed, because
 * {@code collect.sh} pulls digests and the file was in a home directory that died with the host.
 * It worked: two histograms around a suite boundary named the retention exactly (1,312,560
 * {@code gherkin.Step} = scenarios x 15 steps; 397,354 {@code StepResult} = completed scenarios
 * x 15). It should not have needed a human with an ssh session and a stopwatch.
 *
 * <p><b>In-process, via the diagnostic-command MBean</b> — the same {@code GC.class_histogram}
 * jcmd invokes, without forking a JVM tool that may not be installed beside a JRE. It forces a
 * full collection first, so the counts are of live objects; that is a real pause, and the reason
 * this is taken at chosen moments rather than on a timer. The soak's own probe already forces two
 * full collections every five minutes, so a histogram beside one adds nothing new in kind.
 *
 * <p>Kept to the top entries. A full histogram is thousands of lines of classes with four
 * instances each; the answer is always in the head of it, and the file has to survive
 * {@code collect.sh} without being the reason someone stops collecting.
 */
public final class Histogram {

    /** Entries kept. Deep enough that the JDK's own noise cannot push a workload class out. */
    private static final int TOP_ENTRIES = 40;

    private Histogram() {
    }

    /**
     * Write {@code histogram-<label>.txt} into the run directory, or do nothing if the platform
     * declines.
     *
     * <p>Best-effort by construction: a histogram is a diagnostic, and a run that fails because a
     * diagnostic failed has traded a result for a detail. Failures print and are swallowed.
     *
     * @param label short, filename-safe — {@code suite-1-peak}, {@code suite-1-floor}
     */
    public static void capture(String label) {
        String runDir = System.getProperty("karate.profiling.runDir");
        if (runDir == null) {
            return;
        }
        try {
            String histogram = (String) ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "gcClassHistogram",
                    new Object[]{new String[0]},
                    new String[]{String[].class.getName()});
            Files.writeString(Path.of(runDir).resolve("histogram-" + label + ".txt"),
                    header(label) + trim(histogram),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[child] class histogram written: histogram-" + label + ".txt");
        } catch (Exception e) {
            System.out.println("[child] could not take a class histogram (" + label + "): " + e);
        }
    }

    private static String header(String label) {
        return "# class histogram: " + label + System.lineSeparator()
                + "# live objects after a forced full GC, top " + TOP_ENTRIES + " by retained bytes."
                + System.lineSeparator()
                + "# Diff two of these to name what grew; the Total line is the whole heap, not the top N."
                + System.lineSeparator() + System.lineSeparator();
    }

    /**
     * The head of the table plus its Total line — the tail is thousands of four-instance classes.
     * The total is kept deliberately: without it a reader cannot tell what share the top entries
     * account for, and would read a truncated table as the whole heap.
     */
    private static String trim(String histogram) {
        String[] lines = histogram.split("\\R");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        String total = null;
        for (String line : lines) {
            if (line.startsWith("Total")) {
                total = line;
                continue;
            }
            if (kept++ < TOP_ENTRIES + 3) { // + the two header lines and the rule
                out.append(line).append(System.lineSeparator());
            }
        }
        if (total != null) {
            out.append("...").append(System.lineSeparator()).append(total).append(System.lineSeparator());
        }
        return out.toString();
    }

}

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

import io.karatelabs.core.Runner;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which report writers a memory workload runs with.
 *
 * <p>A set rather than a boolean, because "reports on" is ambiguous and the ambiguity has
 * already cost a measurement. {@code Runner.Builder} defaults HTML to true and the other three
 * to false, so the config a user actually ships is {@code html} — but a design goal of
 * "HTML + Cucumber + JUnit + JSONL all enabled" is {@code all}, and the two are different
 * experiments. A run recorded as "reports=true" cannot be told apart afterwards.
 *
 * <p>Individual formats are selectable because the combinations are not monotonic: enabling
 * every format measured <em>lower</em> peak heap than enabling HTML alone, which is only
 * diagnosable one format at a time.
 *
 * <p>{@code -Dkarate.profiling.reports=off|all|html|jsonl|junit|cucumber}, comma-separated
 * for a subset.
 */
public final class ReportMode {

    private static final List<String> ALL_FORMATS = List.of("html", "jsonl", "junit", "cucumber");

    public static final ReportMode OFF = new ReportMode(Set.of());
    public static final ReportMode HTML = new ReportMode(Set.of("html"));
    public static final ReportMode ALL = new ReportMode(new LinkedHashSet<>(ALL_FORMATS));

    private final Set<String> formats;

    private ReportMode(Set<String> formats) {
        this.formats = formats;
    }

    public static ReportMode current() {
        String raw = System.getProperty("karate.profiling.reports", "off").trim().toLowerCase();
        switch (raw) {
            case "off", "false", "none":
                return OFF;
            // "true" meant HTML-only in every run recorded before this class existed; keep it
            // resolving that way so old runbook numbers stay comparable to new ones.
            case "html", "true":
                return HTML;
            case "all":
                return ALL;
            default:
                Set<String> parsed = new LinkedHashSet<>(Arrays.asList(raw.split("\\s*,\\s*")));
                parsed.removeIf(String::isEmpty);
                for (String f : parsed) {
                    if (!ALL_FORMATS.contains(f)) {
                        throw new IllegalArgumentException("unknown report format: " + f
                                + " — known: off, all, " + String.join(", ", ALL_FORMATS));
                    }
                }
                return new ReportMode(parsed);
        }
    }

    public boolean isOff() {
        return formats.isEmpty();
    }

    private boolean has(String format) {
        return formats.contains(format);
    }

    /** Apply this mode to a builder. */
    public Runner.Builder applyTo(Runner.Builder builder) {
        return builder
                .outputHtmlReport(has("html"))
                .outputJsonLines(has("jsonl"))
                .outputJunitXml(has("junit"))
                .outputCucumberJson(has("cucumber"))
                // Karate defaults this to true, which renames the previous karate-reports/ to a
                // timestamped backup instead of replacing it. That is right for a test run and
                // wrong for a sweep: a memory workload's report can be ~1 GB, and forty runs
                // filled a disk with backups nobody was going to read. The run being measured is
                // the only one that matters; the numbers live in the digest, not in the HTML.
                .backupOutputDir(false);
    }

    @Override
    public String toString() {
        return formats.isEmpty() ? "off" : String.join("+", formats);
    }

}

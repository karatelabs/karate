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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the js A/B comparator's pairing and arithmetic against synthetic digests — above all
 * the property the tag scheme exists for: <b>a missing run orphans its cell instead of
 * shifting the pairing</b>, which is the failure adjacency pairing cannot see.
 */
class JsCompareTest {

    @TempDir
    Path root;

    private int stamp = 0;

    /** A minimal js A/B digest with the rows JsCompare reads. */
    private Path digest(String workload, String tag, String sha, long iterations, long completed,
                        long errors, long elapsedMs, String sysprops) throws IOException {
        stamp++;
        Path dir = root.resolve(workload + "-2026-01-01-" + String.format("%06d", stamp));
        Files.createDirectories(dir);
        StringBuilder md = new StringBuilder("# Profiling digest — " + workload + "\n\n");
        md.append("| workload | ").append(workload).append(" |\n");
        md.append("| build | abc1234 |\n");
        md.append("| outcome | completed |\n");
        md.append("| threads | 1 |\n");
        md.append("| bound | iterations=").append(iterations).append(" |\n");
        md.append("| js jar | karate-js-").append(sha, 0, 7).append(".jar commit ")
                .append(sha.repeat(6), 0, 40).append(" sha256 ").append(sha.repeat(3), 0, 16)
                .append(" |\n");
        md.append("| run tag | ").append(tag).append(" |\n");
        if (sysprops != null) {
            md.append("| sysprops | `").append(sysprops).append("` — experiment configuration |\n");
        }
        md.append("| jfr | off (timing run — no recording, by design) |\n");
        md.append("| completed | ").append(completed).append(" of ").append(iterations)
                .append(" requested iterations |\n");
        md.append("| elapsed | ").append(elapsedMs).append(" ms (measured window) |\n");
        md.append("| errors | ").append(errors).append(" |\n");
        md.append("| -Xmx | 768m |\n");
        md.append("| collector | g1 → `-XX:+UseG1GC` |\n");
        md.append("| jdk | 24.0.2+12 |\n");
        Files.writeString(dir.resolve("digest.md"), md.toString());
        return dir;
    }

    private record Result(int exit, String out) {
    }

    private Result compare(List<Path> dirs) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            int exit = JsCompare.main(dirs);
            return new Result(exit, captured.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void pairsByTagAndDerivesTheDelta() throws IOException {
        List<Path> dirs = List.of(
                digest("js-functions", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null),
                digest("js-functions", "m1:p1:b", "2222222", 1000, 1000, 0, 27_000, null));
        Result result = compare(dirs);
        assertEquals(0, result.exit());
        // 30 ms/iter -> 27 ms/iter is -10%.
        assertTrue(result.out().contains("-10.00%"), result.out());
        assertTrue(result.out().contains("a→b"), result.out());
    }

    @Test
    void aMissingRunOrphansItsCellInsteadOfShiftingThePairing() throws IOException {
        // Intended: p1 = (a, b), p2 = (a, b) — but p1's b never produced a digest. Adjacency
        // would pair p1:a with p2:a or p2's runs across the hole; tags must not.
        List<Path> dirs = List.of(
                digest("js-functions", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null),
                digest("js-functions", "m1:p2:b", "2222222", 1000, 1000, 0, 27_000, null),
                digest("js-functions", "m1:p2:a", "1111111", 1000, 1000, 0, 30_000, null));
        Result result = compare(dirs);
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("no partner for"), result.out());
        assertTrue(result.out().contains("1 pair(s)"), result.out());
    }

    @Test
    void errorsOrShortCompletionRejectTheRunByName() throws IOException {
        List<Path> dirs = List.of(
                digest("js-mixed", "m1:p1:a", "1111111", 1000, 1000, 5, 30_000, null),
                digest("js-mixed", "m1:p1:b", "2222222", 1000, 990, 0, 27_000, null));
        Result result = compare(dirs);
        assertEquals(1, result.exit());
        assertTrue(result.out().contains("error(s)"), result.out());
        assertTrue(result.out().contains("completed 990 of 1000"), result.out());
    }

    @Test
    void duplicateTagsRefuseTheCell() throws IOException {
        List<Path> dirs = List.of(
                digest("js-strings", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null),
                digest("js-strings", "m1:p1:a", "1111111", 1000, 1000, 0, 30_500, null),
                digest("js-strings", "m1:p1:b", "2222222", 1000, 1000, 0, 27_000, null));
        Result result = compare(dirs);
        assertEquals(1, result.exit());
        assertTrue(result.out().contains("duplicate arms"), result.out());
    }

    @Test
    void identicalArmsReadAsANullControl() throws IOException {
        List<Path> dirs = List.of(
                digest("js-objects", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null),
                digest("js-objects", "m1:p1:b", "1111111", 1000, 1000, 0, 30_300, null));
        Result result = compare(dirs);
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("null control"), result.out());
    }

    @Test
    void syspropsSeparateCellsAndAreNamedPerArm() throws IOException {
        List<Path> dirs = List.of(
                digest("js-functions", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null),
                digest("js-functions", "m1:p1:b", "2222222", 1000, 1000, 0, 30_000,
                        "-Dsome.engine.flag=false"));
        Result result = compare(dirs);
        assertEquals(0, result.exit());
        assertTrue(result.out().contains("-Dsome.engine.flag=false"), result.out());
    }

    @Test
    void geomeanCoversTheFiveAcceptanceRowsAndFlagsGaps() throws IOException {
        List<Path> dirs = new ArrayList<>();
        for (String row : List.of("js-arithmetic", "js-strings", "js-objects", "js-functions",
                "js-mixed", "js-large-1k")) {
            dirs.add(digest(row, "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null));
            dirs.add(digest(row, "m1:p1:b", "2222222", 1000, 1000, 0, 27_000, null));
        }
        Result full = compare(dirs);
        assertEquals(0, full.exit());
        assertTrue(full.out().contains("geomean (5 acceptance rows)"), full.out());
        assertTrue(full.out().contains("0.9000"), full.out());
        assertTrue(full.out().contains("_(guard)_"), full.out());
        assertFalse(full.out().contains("missing acceptance row"), full.out());

        Result partial = compare(dirs.subList(0, 2));
        assertTrue(partial.out().contains("missing acceptance row"), partial.out());
    }

    @Test
    void mixedFamiliesAreRefusedAtTheDispatch() throws IOException {
        Path js = digest("js-functions", "m1:p1:a", "1111111", 1000, 1000, 0, 30_000, null);
        Path gatling = root.resolve("gatling-http-plain-2026-01-01-999999");
        Files.createDirectories(gatling);
        Files.writeString(gatling.resolve("digest.md"), "# Profiling digest — gatling-http-plain\n");
        assertEquals(2, Compare.main(List.of(js.toString(), gatling.toString())));
    }

}

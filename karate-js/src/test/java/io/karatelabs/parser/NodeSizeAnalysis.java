/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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
package io.karatelabs.parser;

import io.karatelabs.common.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Measures the actual per-{@link NodeType} child-count distribution that the parser
 * produces, so {@link NodeType#expectedChildren} can be tuned from data rather than
 * guessed. No engine instrumentation needed — it parses a corpus and reads
 * {@code node.size()} off each finished AST.
 *
 * <p>Two corpora are reported separately because they stress sizing in opposite
 * directions: RealisticBenchmark (many tiny expressions — the real Karate pattern)
 * tends to OVER-allocate container nodes, while EngineBenchmark (two 20KB scripts)
 * tends to UNDER-allocate them and pay {@code Arrays.copyOf} growth.
 *
 * <p>Run:
 * <pre>java -cp ... io.karatelabs.parser.NodeSizeAnalysis</pre>
 */
public class NodeSizeAnalysis {

    private static final int HIST = 256; // childCount histogram buckets [0..255], 255 = overflow

    static final class Stat {
        long count;
        long sum;
        int max;
        final long[] hist = new long[HIST];

        void record(int childCount) {
            count++;
            sum += childCount;
            if (childCount > max) {
                max = childCount;
            }
            hist[Math.min(childCount, HIST - 1)]++;
        }

        int percentile(double p) {
            long target = (long) Math.ceil(p * count);
            long acc = 0;
            for (int i = 0; i < HIST; i++) {
                acc += hist[i];
                if (acc >= target) {
                    return i;
                }
            }
            return HIST - 1;
        }
    }

    public static void main(String[] args) {
        List<String> realistic = new ArrayList<>(List.of(RealisticBenchmark.EXPRESSIONS));
        List<String> engine = List.of(EngineBenchmark.ARRAY_SCRIPT_20KB, EngineBenchmark.OBJECT_SCRIPT_20KB);

        report("RealisticBenchmark corpus (50 tiny Karate-like expressions)", realistic);
        report("EngineBenchmark corpus (array-heavy + object-heavy 20KB scripts)", engine);
    }

    private static void report(String title, List<String> sources) {
        Map<NodeType, Stat> stats = new EnumMap<>(NodeType.class);
        int parsed = 0, failed = 0;
        for (String src : sources) {
            try {
                Node ast = new JsParser(Resource.text(src)).parse();
                walk(ast, stats);
                parsed++;
            } catch (RuntimeException e) {
                failed++;
            }
        }

        // total non-token node instances, for frequency weighting
        long totalNodes = 0;
        for (Stat s : stats.values()) {
            totalNodes += s.count;
        }

        System.out.println("\n==================================================================");
        System.out.println(title);
        System.out.printf("parsed %d source(s), %d parse failure(s), %,d non-token nodes%n", parsed, failed, totalNodes);
        System.out.println("==================================================================");
        System.out.printf("%-22s %9s %5s %4s %4s %4s %4s %4s  %-4s %s%n",
                "NodeType", "count", "exp", "mean", "p50", "p95", "p99", "max", "grow%", "verdict");
        System.out.println("------------------------------------------------------------------");

        // sort by frequency-weighted impact: how many node-instances of this type
        stats.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<NodeType, Stat> e) -> e.getValue().count).reversed())
                .forEach(e -> {
                    NodeType t = e.getKey();
                    Stat s = e.getValue();
                    int exp = t.expectedChildren;
                    double mean = (double) s.sum / s.count;
                    int p50 = s.percentile(0.50);
                    int p95 = s.percentile(0.95);
                    int p99 = s.percentile(0.99);
                    // fraction of instances that outgrow the initial array (final > exp -> at least one copyOf)
                    long grew = 0;
                    for (int i = exp + 1; i < HIST; i++) {
                        grew += s.hist[i];
                    }
                    double growPct = 100.0 * grew / s.count;
                    String verdict = verdict(exp, p95, s.max, growPct);
                    System.out.printf("%-22s %9d %5d %4.1f %4d %4d %4d %4d  %3.0f%%  %s%n",
                            t, s.count, exp, mean, p50, p95, p99, s.max, growPct, verdict);
                });
    }

    private static String verdict(int exp, int p95, int max, double growPct) {
        if (growPct > 2.0) {
            return "UNDER  (grows; raise toward p95=" + p95 + ")";
        }
        if (exp >= 8 && p95 <= exp / 2 && max < exp) {
            return "OVER   (waste; lower toward p95=" + p95 + ")";
        }
        if (exp > p95 + 2 && max <= exp) {
            return "over   (slack; p95=" + p95 + ")";
        }
        return "ok";
    }

    private static void walk(Node node, Map<NodeType, Stat> stats) {
        if (node == null || node.isToken()) {
            return;
        }
        stats.computeIfAbsent(node.type, k -> new Stat()).record(node.size());
        for (int i = 0, n = node.size(); i < n; i++) {
            walk(node.get(i), stats);
        }
    }

}

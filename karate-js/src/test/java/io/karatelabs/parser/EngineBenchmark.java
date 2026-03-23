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

import io.karatelabs.js.Engine;

import java.util.Arrays;

/**
 * Benchmark focused on Engine eval performance for prototype optimization.
 * Uses 20KB array-heavy and object-heavy scripts for fast iteration.
 *
 * For JFR profiling, run with:
 *   java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=benchmark.jfr EngineBenchmark
 */
public class EngineBenchmark {

    private static final int RUNS = 10;
    private static final int PROFILING_DURATION_SECONDS = 30;

    // Pre-generated 20KB scripts for consistent benchmarking
    private static final String ARRAY_SCRIPT_20KB = generateArrayHeavyScript(20);
    private static final String OBJECT_SCRIPT_20KB = generateObjectHeavyScript(20);

    public static void main(String[] args) {
        boolean profilingMode = args.length > 0 && "profile".equals(args[0]);

        System.out.println("=== Engine Benchmark (Prototype Optimization) ===\n");

        System.out.printf("Array script size:  %,d bytes (%.1f KB)%n",
                ARRAY_SCRIPT_20KB.length(), ARRAY_SCRIPT_20KB.length() / 1024.0);
        System.out.printf("Object script size: %,d bytes (%.1f KB)%n",
                OBJECT_SCRIPT_20KB.length(), OBJECT_SCRIPT_20KB.length() / 1024.0);
        System.out.println();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 5; i++) {
            Engine engine = new Engine();
            engine.eval(ARRAY_SCRIPT_20KB);
            engine.eval(OBJECT_SCRIPT_20KB);
        }
        System.out.println();

        if (profilingMode) {
            // Extended profiling mode for JFR capture
            System.out.println("=== PROFILING MODE ===");
            System.out.printf("Running continuously for %d seconds...%n", PROFILING_DURATION_SECONDS);
            System.out.println("Attach JFR now if not already recording.\n");
            runProfilingLoop();
            return;
        }

        // Engine instantiation benchmark
        System.out.println("--- Engine Instantiation Benchmark ---");
        double instantiationTimeUs = runInstantiationBenchmark();
        System.out.printf("  Median: %.2f µs%n", instantiationTimeUs);
        System.out.println();

        // Array-heavy benchmark
        System.out.println("--- Array-Heavy 20KB Benchmark ---");
        double arrayTime = runBenchmark(ARRAY_SCRIPT_20KB);
        System.out.printf("  Median: %.2f ms (%.3f ms/KB)%n", arrayTime, arrayTime / 20.0);
        System.out.println();

        // Object-heavy benchmark
        System.out.println("--- Object-Heavy 20KB Benchmark ---");
        double objectTime = runBenchmark(OBJECT_SCRIPT_20KB);
        System.out.printf("  Median: %.2f ms (%.3f ms/KB)%n", objectTime, objectTime / 20.0);
        System.out.println();

        // Summary
        System.out.println("=== Summary ===");
        System.out.printf("Engine instantiation: %.2f µs%n", instantiationTimeUs);
        System.out.printf("Array 20KB:  %.2f ms%n", arrayTime);
        System.out.printf("Object 20KB: %.2f ms%n", objectTime);
        System.out.printf("Array/Object ratio: %.2fx%n", arrayTime / objectTime);
    }

    /**
     * Runs the benchmark continuously for PROFILING_DURATION_SECONDS.
     * This mode is designed for JFR profiling to capture enough samples.
     */
    private static void runProfilingLoop() {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (PROFILING_DURATION_SECONDS * 1000L);
        int iterations = 0;
        long totalArrayTime = 0;
        long totalObjectTime = 0;

        while (System.currentTimeMillis() < endTime) {
            Engine engine = new Engine();

            long t1 = System.nanoTime();
            engine.eval(ARRAY_SCRIPT_20KB);
            long t2 = System.nanoTime();
            engine.eval(OBJECT_SCRIPT_20KB);
            long t3 = System.nanoTime();

            totalArrayTime += (t2 - t1);
            totalObjectTime += (t3 - t2);
            iterations++;

            if (iterations % 10 == 0) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                System.out.printf("  [%ds] Completed %d iterations (avg array: %.2f ms, obj: %.2f ms)%n",
                        elapsed, iterations,
                        (totalArrayTime / iterations) / 1_000_000.0,
                        (totalObjectTime / iterations) / 1_000_000.0);
            }
        }

        System.out.println();
        System.out.println("=== Profiling Complete ===");
        System.out.printf("Total iterations: %d%n", iterations);
        System.out.printf("Avg array time:  %.2f ms%n", (totalArrayTime / iterations) / 1_000_000.0);
        System.out.printf("Avg object time: %.2f ms%n", (totalObjectTime / iterations) / 1_000_000.0);
    }

    private static double runInstantiationBenchmark() {
        double[] times = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            Engine engine = new Engine();
            long end = System.nanoTime();
            times[i] = (end - start) / 1_000.0; // microseconds
            System.out.printf("  Run %d: %.2f µs%n", i + 1, times[i]);
            engine.eval("1"); // prevent optimization from eliminating unused engine
        }
        Arrays.sort(times);
        return times[RUNS / 2]; // median
    }

    private static double runBenchmark(String script) {
        double[] times = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            Engine engine = new Engine();
            long start = System.nanoTime();
            engine.eval(script);
            long end = System.nanoTime();
            times[i] = (end - start) / 1_000_000.0;
            System.out.printf("  Run %d: %.2f ms%n", i + 1, times[i]);
        }
        Arrays.sort(times);
        return times[RUNS / 2]; // median
    }

    /**
     * Generates an array-method-heavy script of approximately the target size.
     * Exercises: filter, map, reduce, find, some, every, slice, concat, indexOf, includes
     */
    private static String generateArrayHeavyScript(int targetKB) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  var totalResult = 0;\n");

        int funcNum = 0;
        while (sb.length() < targetKB * 1024 - 200) {
            funcNum++;
            sb.append(String.format("""
              function processArray%d() {
                var data = [];
                for (var i = 0; i < 20; i++) {
                  data.push({ id: i + %d, value: i * %d, active: i %% 3 !== 0, score: i * 1.5 });
                }
                var filtered = data.filter(function(x) { return x.active; });
                var mapped = filtered.map(function(x) { return x.value * 2; });
                var sum = mapped.reduce(function(acc, v) { return acc + v; }, 0);
                var found = data.find(function(x) { return x.id === %d; });
                var hasActive = data.some(function(x) { return x.active; });
                var allPositive = mapped.every(function(x) { return x >= 0; });
                var sliced = data.slice(2, 8);
                var combined = sliced.concat(filtered);
                var idx = mapped.indexOf(mapped[3]);
                var has = data.filter(function(x) { return x.active; }).length > 0;
                return sum + (found ? found.value : 0) + (hasActive ? 1 : 0) + (allPositive ? 1 : 0) + combined.length + idx + (has ? 1 : 0);
              }
            """, funcNum, funcNum * 100, funcNum, funcNum * 100 + 5));
            sb.append(String.format("  totalResult += processArray%d();\n", funcNum));
        }

        sb.append("  return totalResult;\n");
        sb.append("})();\n");
        return sb.toString();
    }

    /**
     * Generates an object-method-heavy script of approximately the target size.
     * Exercises: Object.keys, Object.values, Object.entries, hasOwnProperty, toString
     */
    private static String generateObjectHeavyScript(int targetKB) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function() {\n");
        sb.append("  var totalResult = 0;\n");

        int funcNum = 0;
        while (sb.length() < targetKB * 1024 - 200) {
            funcNum++;
            sb.append(String.format("""
              function processObject%d() {
                var obj = {
                  id: %d,
                  name: 'item%d',
                  value: %d,
                  active: true,
                  score: %d.5,
                  nested: { a: 1, b: 2, c: 3 }
                };
                var keys = Object.keys(obj);
                var values = Object.values(obj);
                var entries = Object.entries(obj);
                var hasId = obj.hasOwnProperty('id');
                var hasName = obj.hasOwnProperty('name');
                var hasUnknown = obj.hasOwnProperty('unknown');
                var str = obj.toString();
                var nestedKeys = Object.keys(obj.nested);
                var nestedVals = Object.values(obj.nested);
                var assigned = Object.assign({}, obj, { extra: 'field' });
                var assignedKeys = Object.keys(assigned);
                return keys.length + values.length + entries.length + (hasId ? 1 : 0) + (hasName ? 1 : 0) + (hasUnknown ? 0 : 1) + str.length + nestedKeys.length + nestedVals.length + assignedKeys.length;
              }
            """, funcNum, funcNum, funcNum, funcNum * 10, funcNum));
            sb.append(String.format("  totalResult += processObject%d();\n", funcNum));
        }

        sb.append("  return totalResult;\n");
        sb.append("})();\n");
        return sb.toString();
    }

}

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

import java.util.HashMap;
import java.util.Map;

/**
 * Realistic benchmark simulating Karate feature file execution:
 * - One engine per "feature"
 * - Many small expressions (variable assignments, assertions, etc.)
 * - Variable bindings between expressions
 *
 * Run with JFR:
 *   java -XX:StartFlightRecording=duration=30s,filename=realistic.jfr,settings=profile RealisticBenchmark
 */
public class RealisticBenchmark {

    private static final int FEATURE_COUNT = 500;
    private static final int EXPRESSIONS_PER_FEATURE = 50;
    private static final int PROFILING_SECONDS = 30;

    // Typical Karate-like expressions - expanded for better coverage
    private static final String[] EXPRESSIONS = {
        // Variable assignments with various object sizes
        "var response = { status: 200, body: { id: 123, name: 'test' } }",
        "var headers = { 'Content-Type': 'application/json', 'Accept': '*/*' }",
        "var expected = { id: 123, name: 'test' }",
        "var large = { a: 1, b: 2, c: 3, d: 4, e: 5, f: 6, g: 7, h: 8 }",
        "var nested = { x: { y: { z: 1 } } }",

        // Property access chains
        "response.status",
        "response.body.id",
        "response.body.name",
        "headers['Content-Type']",
        "nested.x.y.z",

        // Comparisons (match-like)
        "response.status == 200",
        "response.body.id == expected.id",
        "response.body.name == expected.name",
        "typeof response.body == 'object'",
        "response.status >= 200 && response.status < 300",

        // Array operations with various sizes
        "var items = [1, 2, 3, 4, 5]",
        "var bigArray = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]",
        "items.length",
        "items.filter(function(x) { return x > 2 })",
        "items.map(function(x) { return x * 2 })",
        "items.includes(3)",
        "items.reduce(function(a, b) { return a + b }, 0)",
        "items.forEach(function(x) { })",

        // String operations
        "var str = 'hello world'",
        "str.length",
        "str.indexOf('world')",
        "str.toUpperCase()",
        "str.split(' ')",
        "str.substring(0, 5)",
        "str.replace('world', 'there')",

        // Object operations
        "Object.keys(response.body)",
        "Object.values(response.body)",
        "response.body.hasOwnProperty('id')",
        "Object.assign({}, response, { extra: true })",

        // Ternary conditionals
        "response.status == 200 ? 'success' : 'failure'",
        "items.length > 0 ? items[0] : null",
        "str ? str.toUpperCase() : ''",

        // Logical expressions
        "response.body.id > 100 && response.body.name.length > 0",
        "response.status == 200 || response.status == 201",
        "!response.error && response.body",

        // Functions
        "var fn = function(x) { return x + 1 }",
        "var fn2 = function(a, b, c) { return a + b + c }",
        "fn(5)",
        "fn(response.body.id)",

        // Math expressions
        "1 + 2 * 3",
        "response.body.id * 2 + 10",
        "(1 + 2) * (3 + 4)",

        // JSON-like operations
        "var config = { url: 'http://localhost', timeout: 5000 }",
        "config.url + '/api/test'",
    };

    public static void main(String[] args) {
        boolean profilingMode = args.length > 0 && "profile".equals(args[0]);

        System.out.println("=== Realistic Karate-like Benchmark ===\n");
        System.out.printf("Simulating %d features with %d expressions each%n",
                FEATURE_COUNT, EXPRESSIONS_PER_FEATURE);
        System.out.println();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 50; i++) {
            runFeature();
        }
        System.out.println();

        if (profilingMode) {
            System.out.println("=== PROFILING MODE ===");
            System.out.printf("Running for %d seconds...%n", PROFILING_SECONDS);
            runProfilingLoop();
            return;
        }

        // Benchmark
        System.out.println("Running benchmark...");
        long start = System.nanoTime();
        for (int i = 0; i < FEATURE_COUNT; i++) {
            runFeature();
        }
        long elapsed = System.nanoTime() - start;

        double totalMs = elapsed / 1_000_000.0;
        double perFeatureUs = (elapsed / FEATURE_COUNT) / 1_000.0;
        double perExprUs = (elapsed / (FEATURE_COUNT * EXPRESSIONS_PER_FEATURE)) / 1_000.0;

        System.out.println();
        System.out.println("=== Results ===");
        System.out.printf("Total time:          %.2f ms%n", totalMs);
        System.out.printf("Per feature:         %.2f µs%n", perFeatureUs);
        System.out.printf("Per expression:      %.2f µs%n", perExprUs);
        System.out.printf("Expressions/second:  %,.0f%n", 1_000_000.0 / perExprUs);
    }

    private static void runProfilingLoop() {
        long endTime = System.currentTimeMillis() + (PROFILING_SECONDS * 1000L);
        int iterations = 0;
        long totalTime = 0;

        while (System.currentTimeMillis() < endTime) {
            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                runFeature();
            }
            totalTime += System.nanoTime() - start;
            iterations += 100;

            if (iterations % 1000 == 0) {
                double avgUs = (totalTime / iterations) / 1000.0;
                System.out.printf("  %d features, avg %.1f µs/feature%n", iterations, avgUs);
            }
        }

        System.out.println();
        System.out.println("=== Profiling Complete ===");
        System.out.printf("Total features: %d%n", iterations);
        System.out.printf("Avg per feature: %.2f µs%n", (totalTime / iterations) / 1000.0);
    }

    private static void runFeature() {
        // New engine per feature (like Karate)
        Engine engine = new Engine();

        // Set up initial bindings (like karate object, config, etc.)
        engine.put("karate", createKarateObject());

        // Evaluate expressions (like steps in a feature)
        for (int i = 0; i < EXPRESSIONS_PER_FEATURE; i++) {
            String expr = EXPRESSIONS[i % EXPRESSIONS.length];
            engine.eval(expr);
        }
    }

    private static Map<String, Object> createKarateObject() {
        Map<String, Object> karate = new HashMap<>();
        karate.put("env", "test");
        karate.put("baseUrl", "http://localhost:8080");

        Map<String, Object> properties = new HashMap<>();
        properties.put("timeout", 5000);
        properties.put("retries", 3);
        karate.put("properties", properties);

        return karate;
    }
}

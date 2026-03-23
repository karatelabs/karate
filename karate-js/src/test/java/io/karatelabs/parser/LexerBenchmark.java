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
package io.karatelabs.parser;

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark for JsLexer performance. Run with:
 * mvn test -Dtest=LexerBenchmark -pl karate-js
 */
public class LexerBenchmark {

    // Representative JavaScript code samples
    private static final String JS_IDENTIFIERS_KEYWORDS = """
        function calculateTotal(items, taxRate, discount) {
            let total = 0;
            const multiplier = 1 + taxRate;
            for (let i = 0; i < items.length; i++) {
                const item = items[i];
                if (item.price > 0 && item.quantity > 0) {
                    total += item.price * item.quantity;
                } else if (item.isFree) {
                    continue;
                } else {
                    throw new Error('Invalid item');
                }
            }
            return total * multiplier - discount;
        }
        var result = calculateTotal(data, 0.08, 5.00);
        """;

    private static final String JS_STRINGS_AND_TEMPLATES = """
        const greeting = "Hello, World!";
        const name = 'John Doe';
        const message = `Welcome, ${name}! Your balance is $${balance.toFixed(2)}.`;
        const multiline = `
            This is a
            multiline template
            with ${nested} expressions
        `;
        const escaped = "She said \\"Hello\\" and he replied 'Hi'";
        const path = '/api/users/${userId}/profile';
        """;

    private static final String JS_NUMBERS_AND_OPERATORS = """
        let a = 123 + 456.789;
        let b = 0xFF + 0x1A2B;
        let c = 1e10 + 2.5e-3;
        let d = a * b / c % 100;
        let e = (a << 2) | (b >> 1) & 0xFF;
        let f = a === b ? c : d;
        let g = a !== b && c >= d || e <= f;
        let h = ++a + b-- * --c + d++;
        let i = a **= 2;
        let j = b ??= c || d;
        let k = obj?.prop?.nested ?? 'default';
        """;

    private static final String JS_OBJECTS_AND_ARRAYS = """
        const user = {
            id: 12345,
            name: "Alice",
            email: "alice@example.com",
            roles: ["admin", "user", "guest"],
            profile: {
                age: 30,
                city: "New York",
                settings: {
                    theme: "dark",
                    notifications: true,
                    preferences: {
                        language: "en",
                        timezone: "UTC"
                    }
                }
            },
            tags: [...existingTags, "new", "featured"],
            ...defaults
        };
        const [first, second, ...rest] = items;
        const { name: userName, profile: { age } } = user;
        """;

    private static final String JS_FUNCTIONS_AND_CLASSES = """
        const add = (a, b) => a + b;
        const multiply = (a, b) => {
            return a * b;
        };
        function processData(data, callback = () => {}) {
            try {
                const result = transform(data);
                callback(null, result);
            } catch (error) {
                callback(error, null);
            } finally {
                cleanup();
            }
        }
        const handler = async (event) => {
            const response = await fetch(url);
            return response.json();
        };
        """;

    private static final String JS_COMMENTS_AND_WHITESPACE = """
        // Single line comment
        /* Block comment */
        /**
         * Multi-line JSDoc comment
         * @param {string} name - The name
         * @returns {string} The greeting
         */
        function greet(name) {
            // Another comment
            return "Hello, " + name; /* inline */ // trailing
        }




        """;

    private static final String JS_REGEX = """
        const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$/;
        const urlPattern = /https?:\\/\\/[^\\s]+/gi;
        const datePattern = /\\d{4}-\\d{2}-\\d{2}/;
        if (/test/.test(str)) { console.log('match'); }
        const result = str.replace(/foo/g, 'bar');
        """;

    // Edge cases for thorough lexer testing
    private static final String JS_EDGE_CASES = """
        // Unicode identifiers
        const café = 'coffee';
        const naïve = true;
        const 日本語 = 'Japanese';
        const _$valid123 = 1;

        // BigInt literals
        const bigInt1 = 123n;
        const bigInt2 = 0xFFn;
        const bigInt3 = 0o777n;
        const bigInt4 = 0b1010n;
        const bigCalc = 9007199254740991n + 1n;

        // Numeric separators
        const million = 1_000_000;
        const bytes = 0xFF_FF_FF_FF;
        const binary = 0b1010_0001_1000_0101;
        const octal = 0o123_456;
        const float = 1_234.567_890;
        const exp = 1_2e3_4;

        // Private class fields
        class Counter {
            #count = 0;
            #privateMethod() { return this.#count; }
            get count() { return this.#count; }
            increment() { this.#count++; }
        }

        // Regex vs division ambiguity
        const a = 10 / 2 / 5;
        const b = /regex/g;
        const c = (x) / 2;
        const d = x / y / z;
        if (true) /regex/.test(s);
        return /regex/i;

        // Tricky string escapes
        const esc1 = "line1\\nline2\\ttab\\r\\0null";
        const esc2 = '\\x41\\x42\\x43';
        const esc3 = "\\u0048\\u0065\\u006C\\u006C\\u006F";
        const esc4 = '\\u{1F600}\\u{1F64F}';

        // Nested template literals
        const nested = `outer ${`inner ${value}`} outer`;
        const deep = `a ${`b ${`c ${x}`}`}`;
        const tagged = html`<div class="${cls}">${content}</div>`;

        // Optional chaining combinations
        const oc1 = obj?.prop;
        const oc2 = obj?.[expr];
        const oc3 = func?.();
        const oc4 = obj?.method?.()?.prop?.[0];

        // Nullish coalescing with assignment
        let nc1 = a ?? b ?? c;
        let nc2 ??= defaultValue;
        const nc3 = obj.prop ?? obj.fallback ?? 'default';

        // Spread in various contexts
        const arr = [...a, ...b, ...c];
        const obj = { ...x, ...y, key: value };
        func(...args);
        new Constructor(...params);

        // Destructuring edge cases
        const { a: { b: { c: deep } } } = obj;
        const [[[nested]]] = arr;
        const { 'special-key': value } = obj;
        const { [computed]: val } = obj;

        // Arrow function edge cases
        const f1 = x => x;
        const f2 = (x) => x;
        const f3 = (x, y) => x + y;
        const f4 = (x = 1, y = 2) => x + y;
        const f5 = ({ a, b }) => a + b;
        const f6 = ([x, y]) => x + y;
        const f7 = async x => await x;
        const f8 = async (x, y) => { await x; return y; };
        """;

    private static final String JS_MIXED_REALISTIC = """
        function UserService(config) {
            const API_URL = config.apiUrl || 'https://api.example.com';
            const cache = new Map();

            this.getUser = async function(userId) {
                if (cache.has(userId)) {
                    return cache.get(userId);
                }

                const response = await fetch(`${API_URL}/users/${userId}`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${config.token}`
                    }
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

                const user = await response.json();
                cache.set(userId, user);
                return user;
            };

            this.updateUser = async function(userId, updates) {
                const user = await this.getUser(userId);
                const merged = { ...user, ...updates, updatedAt: Date.now() };

                const response = await fetch(`${API_URL}/users/${userId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${config.token}`
                    },
                    body: JSON.stringify(merged)
                });

                if (response.ok) {
                    cache.set(userId, merged);
                    return merged;
                }
                throw new Error('Update failed');
            };
        }

        const service = new UserService({ apiUrl: 'https://api.test.com', token: 'abc123' });
        const user = await service.getUser(42);
        console.log(`User: ${user.name}, Email: ${user.email}`);
        """;

    // Executable samples for full eval benchmarking (no undefined references)
    private static final String JS_EVAL_ARITHMETIC = """
        let result = 0;
        for (let i = 0; i < 100; i++) {
            result += i * 2 + i / 2 - i % 7;
            result = result * 1.01;
        }
        result;
        """;

    private static final String JS_EVAL_STRINGS = """
        let s = '';
        for (let i = 0; i < 50; i++) {
            s += 'item' + i + ',';
        }
        s.split(',').length;
        """;

    private static final String JS_EVAL_OBJECTS = """
        let users = [];
        for (let i = 0; i < 50; i++) {
            users.push({
                id: i,
                name: 'user' + i,
                email: 'user' + i + '@test.com',
                active: i % 2 === 0,
                scores: [i, i * 2, i * 3]
            });
        }
        let active = users.filter(u => u.active);
        let total = active.reduce((sum, u) => sum + u.scores[0], 0);
        total;
        """;

    private static final String JS_EVAL_FUNCTIONS = """
        function fibonacci(n) {
            if (n <= 1) return n;
            return fibonacci(n - 1) + fibonacci(n - 2);
        }
        function factorial(n) {
            if (n <= 1) return 1;
            return n * factorial(n - 1);
        }
        let fib = fibonacci(10);
        let fact = factorial(8);
        fib + fact;
        """;

    private static final String JS_EVAL_MIXED = """
        function processData(items) {
            let result = { sum: 0, count: 0, values: [] };
            for (let i = 0; i < items.length; i++) {
                let item = items[i];
                if (item.active) {
                    result.sum += item.value;
                    result.count++;
                    result.values.push(item.value * 2);
                }
            }
            result.average = result.count > 0 ? result.sum / result.count : 0;
            return result;
        }
        let data = [];
        for (let i = 0; i < 100; i++) {
            data.push({ id: i, value: i * 10, active: i % 3 !== 0 });
        }
        let output = processData(data);
        output.sum + output.average;
        """;

    private static final String JS_EVAL_ALL = JS_EVAL_ARITHMETIC + JS_EVAL_STRINGS
            + JS_EVAL_OBJECTS + JS_EVAL_FUNCTIONS + JS_EVAL_MIXED;

    // Combine all samples for a comprehensive test
    private static final String JS_ALL_COMBINED = JS_IDENTIFIERS_KEYWORDS
            + JS_STRINGS_AND_TEMPLATES
            + JS_NUMBERS_AND_OPERATORS
            + JS_OBJECTS_AND_ARRAYS
            + JS_FUNCTIONS_AND_CLASSES
            + JS_COMMENTS_AND_WHITESPACE
            + JS_REGEX
            + JS_EDGE_CASES
            + JS_MIXED_REALISTIC;

    // Create a large source by repeating the combined sample
    private static final String JS_LARGE;
    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append(JS_ALL_COMBINED).append("\n");
        }
        JS_LARGE = sb.toString();
    }

    // Collect benchmark results for CSV output
    private static final List<BenchmarkResult> results = new ArrayList<>();

    public static void main(String[] args) {
        String csvFile = args.length > 0 ? args[0] : null;

        System.out.println("=== JsLexer Benchmark ===\n");
        System.out.println("Source sizes:");
        System.out.println("  Small (combined): " + JS_ALL_COMBINED.length() + " chars");
        System.out.println("  Large (50x):      " + JS_LARGE.length() + " chars");
        System.out.println();

        // Warmup
        System.out.println("Warming up JIT...");
        for (int i = 0; i < 1000; i++) {
            tokenize(JS_ALL_COMBINED);
            tokenize(JS_LARGE);
        }
        System.out.println("Warmup complete.\n");

        // Run benchmarks
        runBenchmark("Small source (mixed JS)", JS_ALL_COMBINED, 10000);
        runBenchmark("Large source (50x mixed)", JS_LARGE, 500);

        // Individual category benchmarks
        System.out.println("\n--- Category Breakdown ---\n");
        runBenchmark("Identifiers & Keywords", JS_IDENTIFIERS_KEYWORDS, 20000);
        runBenchmark("Strings & Templates", JS_STRINGS_AND_TEMPLATES, 20000);
        runBenchmark("Numbers & Operators", JS_NUMBERS_AND_OPERATORS, 20000);
        runBenchmark("Objects & Arrays", JS_OBJECTS_AND_ARRAYS, 20000);
        runBenchmark("Functions", JS_FUNCTIONS_AND_CLASSES, 20000);
        runBenchmark("Comments & Whitespace", JS_COMMENTS_AND_WHITESPACE, 20000);
        runBenchmark("Regex", JS_REGEX, 20000);
        runBenchmark("Edge Cases", JS_EDGE_CASES, 20000);
        runBenchmark("Realistic Mixed", JS_MIXED_REALISTIC, 10000);

        // Full eval benchmarks (lexer + parser + interpreter)
        System.out.println("\n--- Full Eval (Lexer + Parser + Interpreter) ---\n");
        runEvalBenchmark("Eval: Arithmetic", JS_EVAL_ARITHMETIC, 5000);
        runEvalBenchmark("Eval: Strings", JS_EVAL_STRINGS, 2000);
        runEvalBenchmark("Eval: Objects", JS_EVAL_OBJECTS, 1000);
        runEvalBenchmark("Eval: Functions", JS_EVAL_FUNCTIONS, 500);
        runEvalBenchmark("Eval: Mixed", JS_EVAL_MIXED, 500);
        runEvalBenchmark("Eval: All Combined", JS_EVAL_ALL, 200);

        // 1:1 comparison: lexer vs full eval on same source
        System.out.println("\n--- Lexer vs Full Eval Comparison ---\n");
        System.out.printf("%-20s %10s %10s %10s %10s%n", "Source", "Lex (ms)", "Eval (ms)", "Lex %", "Other %");
        System.out.println("-".repeat(70));
        runComparison("Arithmetic", JS_EVAL_ARITHMETIC, 5000);
        runComparison("Strings", JS_EVAL_STRINGS, 5000);
        runComparison("Objects", JS_EVAL_OBJECTS, 2000);
        runComparison("Functions", JS_EVAL_FUNCTIONS, 1000);
        runComparison("Mixed", JS_EVAL_MIXED, 1000);
        runComparison("All Combined", JS_EVAL_ALL, 500);

        // Write CSV output to target directory
        String outputFile = csvFile != null ? csvFile : "target/benchmark.csv";
        writeCsv(outputFile);
    }

    private static void runBenchmark(String name, String source, int iterations) {
        // Get token count first
        List<Token> tokens = tokenize(source);
        int tokenCount = tokens.size();

        // Timed runs
        long[] times = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                tokenize(source);
            }
            times[run] = System.nanoTime() - start;
        }

        // Calculate stats (use median of 5 runs)
        java.util.Arrays.sort(times);
        long medianNs = times[2];
        double avgNsPerIter = (double) medianNs / iterations;
        double avgUsPerIter = avgNsPerIter / 1000.0;
        double charsPerUs = source.length() / avgUsPerIter;
        double tokensPerUs = tokenCount / avgUsPerIter;

        double msPerIter = avgUsPerIter / 1000.0;

        System.out.printf("%-28s %6d chars, %4d tokens | %8.4f ms | %6.1f chars/µs | %5.2f tokens/µs%n",
                name, source.length(), tokenCount, msPerIter, charsPerUs, tokensPerUs);

        // Store result for CSV
        double medianTimeMs = medianNs / 1_000_000.0;
        results.add(new BenchmarkResult(name, source.length(), tokenCount, iterations,
                msPerIter, charsPerUs, tokensPerUs, medianTimeMs));
    }

    private static void runComparison(String name, String source, int iterations) {
        // Warmup
        for (int i = 0; i < 100; i++) {
            tokenize(source);
            new Engine().eval(source);
        }

        // Measure lexer time
        long[] lexTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                tokenize(source);
            }
            lexTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(lexTimes);
        double lexMsPerIter = (lexTimes[2] / (double) iterations) / 1_000_000.0;

        // Measure full eval time
        long[] evalTimes = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine().eval(source);
            }
            evalTimes[run] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(evalTimes);
        double evalMsPerIter = (evalTimes[2] / (double) iterations) / 1_000_000.0;

        // Calculate percentages
        double lexPercent = (lexMsPerIter / evalMsPerIter) * 100;
        double otherPercent = 100 - lexPercent;

        System.out.printf("%-20s %10.4f %10.4f %9.1f%% %9.1f%%%n",
                name, lexMsPerIter, evalMsPerIter, lexPercent, otherPercent);
    }

    private static void runEvalBenchmark(String name, String source, int iterations) {
        // Warmup for this specific source (new Engine each time)
        for (int i = 0; i < 100; i++) {
            new Engine().eval(source);
        }

        // Get token count for reference
        List<Token> tokens = tokenize(source);
        int tokenCount = tokens.size();

        // Timed runs - new Engine for each eval to measure full startup
        long[] times = new long[5];
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                new Engine().eval(source);
            }
            times[run] = System.nanoTime() - start;
        }

        // Calculate stats (use median of 5 runs)
        java.util.Arrays.sort(times);
        long medianNs = times[2];
        double avgNsPerIter = (double) medianNs / iterations;
        double avgUsPerIter = avgNsPerIter / 1000.0;
        double msPerIter = avgUsPerIter / 1000.0;
        double charsPerUs = source.length() / avgUsPerIter;
        double tokensPerUs = tokenCount / avgUsPerIter;

        System.out.printf("%-28s %6d chars, %4d tokens | %8.4f ms | %6.1f chars/µs | %5.2f tokens/µs%n",
                name, source.length(), tokenCount, msPerIter, charsPerUs, tokensPerUs);

        // Store result for CSV
        double medianTimeMs = medianNs / 1_000_000.0;
        results.add(new BenchmarkResult(name, source.length(), tokenCount, iterations,
                msPerIter, charsPerUs, tokensPerUs, medianTimeMs));
    }

    private static void printCsv() {
        System.out.println("\n--- CSV Output ---\n");
        System.out.println(BenchmarkResult.csvHeader());
        for (BenchmarkResult r : results) {
            System.out.println(r.toCsv());
        }
    }

    private static void writeCsv(String filename) {
        try {
            java.io.File file = new java.io.File(filename);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println(BenchmarkResult.csvHeader());
                for (BenchmarkResult r : results) {
                    pw.println(r.toCsv());
                }
            }
            System.out.println("\nCSV written to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            printCsv();
        }
    }

    private static List<Token> tokenize(String source) {
        return JsLexer.getTokens(Resource.text(source));
    }

    // Result holder for CSV output
    private record BenchmarkResult(
            String name,
            int chars,
            int tokens,
            int iterations,
            double msPerIter,
            double charsPerUs,
            double tokensPerUs,
            double medianTimeMs
    ) {
        static String csvHeader() {
            return "timestamp,name,chars,tokens,iterations,ms_per_iter,chars_per_us,tokens_per_us,median_time_ms";
        }

        String toCsv() {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return String.format("%s,%s,%d,%d,%d,%.4f,%.1f,%.2f,%.2f",
                    ts, name, chars, tokens, iterations, msPerIter, charsPerUs, tokensPerUs, medianTimeMs);
        }
    }
}

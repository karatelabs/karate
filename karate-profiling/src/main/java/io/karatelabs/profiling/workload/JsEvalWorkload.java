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
package io.karatelabs.profiling.workload;

import io.karatelabs.js.Engine;
import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.WorkloadContext;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The karate-js family: fresh-eval JS engine workloads, built to A/B <b>two builds of the
 * karate-js jar</b> on elapsed time per iteration ({@code --js-jar} swaps the jar on the child
 * classpath; see the usage text and docs/PROFILING.md).
 *
 * <p>One iteration is exactly one <em>fresh</em> evaluation: a new {@code Engine}, a source
 * string made unique by a trailing comment counter, one {@code eval}. The uniqueness is
 * load-bearing — it is what keeps any present or future source-keyed cache from letting the
 * engine skip the work this family exists to time — and the counter is shared across warmup
 * and the measured window so no source text is ever evaluated twice in one JVM.
 *
 * <p>The scripts are copied <b>verbatim</b> from the public karate-js-benchmark project (MIT,
 * Karate Labs Inc.), so a change measured here is measured on the same work the published
 * scoreboard measures. The five small rows are the family; {@code js-large-1k} is a guard row
 * — report it, never fold it into a cross-row aggregate.
 *
 * <p><b>Every iteration is checked against a Java-recomputed oracle.</b> Cross-arm agreement
 * alone cannot catch an engine that silently does less work — the fastest engine is the one
 * with the bug — and both JS and Java compute these scripts in IEEE doubles, so the expected
 * values are exact, not tolerances. The first mismatch throws with the full story; later
 * iterations throw a pre-allocated, stackless exception so a broken run fails its whole budget
 * quickly instead of spending minutes formatting identical messages.
 *
 * <p>Single-threaded by default: the quantity under test is per-iteration cost, and one worker
 * keeps the measured window free of scheduler interleaving. Process CPU above one core is not
 * by itself contamination (JIT and GC threads are real), which is why the digest reports it as
 * context rather than this class asserting on it.
 */
public abstract class JsEvalWorkload implements io.karatelabs.profiling.Workload {

    /**
     * Shared across warmup and measured phases on purpose — the per-phase iteration number
     * restarts at zero, and reusing a suffix would evaluate one source text twice.
     */
    private final AtomicLong uniqueSuffix = new AtomicLong();

    private volatile boolean oracleFailed;

    /** Stackless, pre-allocated: thrown for every iteration after the first oracle failure. */
    private static final class OracleAlreadyFailed extends RuntimeException {
        OracleAlreadyFailed() {
            super("oracle failed in an earlier iteration — see the first failure above",
                    null, false, false);
        }
    }

    private static final OracleAlreadyFailed ALREADY_FAILED = new OracleAlreadyFailed();

    /** The JS source under test, without the uniqueness suffix. */
    protected abstract String script();

    /** The Java-recomputed expected value. Exact — see the class comment. */
    protected abstract double expected();

    @Override
    public JvmConfig jvm() {
        // Fresh-eval churns hard and retains nothing, so this should be pure young-gen at any
        // sensible size. 768m is the harness default; if the digest's GC pauses say otherwise
        // for a row, raise it per run with --xmx and record that the cell needed it.
        return JvmConfig.defaults();
    }

    @Override
    public RunShape shape() {
        return new RunShape(1, defaultIterations(), null, Duration.ofSeconds(10), null);
    }

    /** Sized so a run's measured window is roughly 30-45 s on the published CI hardware. */
    protected abstract long defaultIterations();

    @Override
    public void setup(WorkloadContext context) {
        // One eval before anything is timed, so a jar whose engine cannot run this script —
        // or computes the wrong thing — fails here with a clear message instead of half-way
        // into a warmup.
        check(new Engine().eval(script()), -1);
    }

    @Override
    public void iterate(int vu, long iteration) {
        if (oracleFailed) {
            throw ALREADY_FAILED;
        }
        String source = script() + "\n//" + uniqueSuffix.getAndIncrement();
        Object result = new Engine().eval(source);
        check(result, iteration);
    }

    private void check(Object result, long iteration) {
        if (result instanceof Number number && number.doubleValue() == expected()) {
            return;
        }
        oracleFailed = true;
        throw new IllegalStateException(name() + " oracle mismatch at iteration " + iteration
                + ": expected " + expected() + ", got "
                + (result == null ? "null" : result.getClass().getSimpleName() + " " + result)
                + " — the engine under test computed the wrong value, so its timing is void");
    }

    public static final class Arithmetic extends JsEvalWorkload {

        @Override
        public String name() {
            return "js-arithmetic";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS: a 100-iteration numeric loop (multiply, divide, modulo, "
                    + "compound float). One new Engine and one unique source per iteration, "
                    + "result checked against a Java-computed oracle. A/B two karate-js builds "
                    + "with --js-jar.";
        }

        @Override
        protected String script() {
            return JS_ARITHMETIC;
        }

        @Override
        protected double expected() {
            // The same computation the script performs, in Java doubles. JS numbers are IEEE
            // doubles and every operator here has identical semantics in both languages, so
            // this is exact, not a tolerance.
            double result = 0;
            for (int i = 0; i < 100; i++) {
                result += i * 2 + i / 2.0 - i % 7;
                result = result * 1.01;
            }
            return result;
        }

        @Override
        protected long defaultIterations() {
            return 400_000;
        }

    }

    public static final class Strings extends JsEvalWorkload {

        @Override
        public String name() {
            return "js-strings";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS: 50 string concatenations then a split-and-count. One new "
                    + "Engine and one unique source per iteration, oracle-checked. A/B two "
                    + "karate-js builds with --js-jar.";
        }

        @Override
        protected String script() {
            return JS_STRINGS;
        }

        @Override
        protected double expected() {
            // 50 'itemN,' segments then split(',') — the trailing comma yields an empty last
            // element, so 51.
            return 51;
        }

        @Override
        protected long defaultIterations() {
            return 800_000;
        }

    }

    public static final class Objects extends JsEvalWorkload {

        @Override
        public String name() {
            return "js-objects";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS: 50 object literals into an array, then filter and reduce "
                    + "with arrow functions. One new Engine and one unique source per "
                    + "iteration, oracle-checked. A/B two karate-js builds with --js-jar.";
        }

        @Override
        protected String script() {
            return JS_OBJECTS;
        }

        @Override
        protected double expected() {
            // scores[0] summed over the even ids 0..48: 2 * (0+1+...+24) = 600.
            int total = 0;
            for (int i = 0; i < 50; i += 2) {
                total += i;
            }
            return total;
        }

        @Override
        protected long defaultIterations() {
            return 300_000;
        }

    }

    public static final class Functions extends JsEvalWorkload {

        @Override
        public String name() {
            return "js-functions";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS: recursive fibonacci(10) plus factorial(8) — call-heavy, "
                    + "the row most sensitive to function-scope cost. One new Engine and one "
                    + "unique source per iteration, oracle-checked. A/B two karate-js builds "
                    + "with --js-jar.";
        }

        @Override
        protected String script() {
            return JS_FUNCTIONS;
        }

        @Override
        protected double expected() {
            return fibonacci(10) + factorial(8);
        }

        private static long fibonacci(int n) {
            return n <= 1 ? n : fibonacci(n - 1) + fibonacci(n - 2);
        }

        private static long factorial(int n) {
            return n <= 1 ? 1 : n * factorial(n - 1);
        }

        @Override
        protected long defaultIterations() {
            return 250_000;
        }

    }

    public static final class Mixed extends JsEvalWorkload {

        @Override
        public String name() {
            return "js-mixed";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS: a processData function over 100 objects — loops, property "
                    + "access, conditionals and array building in one script. One new Engine "
                    + "and one unique source per iteration, oracle-checked. A/B two karate-js "
                    + "builds with --js-jar.";
        }

        @Override
        protected String script() {
            return JS_MIXED;
        }

        @Override
        protected double expected() {
            // The same walk the script performs: items 0..99, active when i % 3 != 0.
            double sum = 0;
            int count = 0;
            for (int i = 0; i < 100; i++) {
                if (i % 3 != 0) {
                    sum += i * 10;
                    count++;
                }
            }
            double average = count > 0 ? sum / count : 0;
            return sum + average;
        }

        @Override
        protected long defaultIterations() {
            return 120_000;
        }

    }

    /**
     * The guard row: a generated ~1 KB script of var-style functions, loops and object
     * literals. Report it beside the five small rows, never inside a cross-row aggregate —
     * its job is to catch a change that helps short scripts by hurting long ones.
     */
    public static final class Large1k extends JsEvalWorkload {

        static final int TARGET_BYTES = 1024;

        private static final String SCRIPT = generate(TARGET_BYTES);

        @Override
        public String name() {
            return "js-large-1k";
        }

        @Override
        public String describe() {
            return "Fresh-eval JS guard row: a generated ~1 KB script (functions, loops, "
                    + "object literals, filter callbacks). One new Engine and one unique "
                    + "source per iteration, oracle-checked. A/B two karate-js builds with "
                    + "--js-jar. Report beside the small rows, never aggregate with them.";
        }

        @Override
        protected String script() {
            return SCRIPT;
        }

        @Override
        protected double expected() {
            // Each processN(): even i in 0..9 pass the filter, contributing value + nested.a
            // = i*id + i, so 20 * (id + 1) per function. Derived from the same counting loop
            // the generator runs, so script and oracle cannot drift apart.
            long total = 0;
            for (int id = 1; id <= functionCount(TARGET_BYTES); id++) {
                total += 20L * (id + 1);
            }
            return total;
        }

        // The generator below is copied verbatim from the public karate-js-benchmark project
        // (MIT, Karate Labs Inc.), so this guard row is the same script the published
        // scoreboard's Large-1KB row runs. JsEvalWorkloadTest pins the generated length —
        // an edit that changes the script would otherwise silently rename what this measures.

        static String generate(int targetBytes) {
            StringBuilder sb = new StringBuilder();
            sb.append("(function() {\n");
            sb.append("  var results = [];\n");
            int functionCount = 0;
            while (sb.length() < targetBytes - 100) {
                functionCount++;
                sb.append(generateFunction(functionCount));
            }
            sb.append("  var total = 0;\n");
            for (int i = 1; i <= functionCount; i++) {
                sb.append("  total += process").append(i).append("();\n");
            }
            sb.append("  return total;\n");
            sb.append("})();\n");
            return sb.toString();
        }

        static int functionCount(int targetBytes) {
            StringBuilder sb = new StringBuilder();
            sb.append("(function() {\n");
            sb.append("  var results = [];\n");
            int functionCount = 0;
            while (sb.length() < targetBytes - 100) {
                functionCount++;
                sb.append(generateFunction(functionCount));
            }
            return functionCount;
        }

        private static String generateFunction(int id) {
            return """
              function process%d() {
                var data = [];
                for (var i = 0; i < 10; i++) {
                  data.push({
                    id: i + %d,
                    name: 'item' + i,
                    value: i * %d,
                    active: i %% 2 === 0,
                    tags: ['tag' + i, 'category%d'],
                    nested: { a: i, b: i * 2, c: i * 3 }
                  });
                }
                var filtered = data.filter(function(x) { return x.active; });
                var sum = 0;
                for (var j = 0; j < filtered.length; j++) {
                  sum += filtered[j].value + filtered[j].nested.a;
                }
                return sum;
              }
            """.formatted(id, id * 100, id, id);
        }

        @Override
        protected long defaultIterations() {
            return 200_000;
        }

    }

    // The five sources, verbatim from karate-js-benchmark's JsEngineBenchmark (MIT, Karate
    // Labs Inc.). Verbatim is the point: a row here must measure the same work as the
    // published row of the same name, and JsEvalWorkloadTest pins each script's length.

    static final String JS_ARITHMETIC = """
        let result = 0;
        for (let i = 0; i < 100; i++) {
            result += i * 2 + i / 2 - i % 7;
            result = result * 1.01;
        }
        result;
        """;

    static final String JS_STRINGS = """
        let s = '';
        for (let i = 0; i < 50; i++) {
            s += 'item' + i + ',';
        }
        s.split(',').length;
        """;

    static final String JS_OBJECTS = """
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

    static final String JS_FUNCTIONS = """
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

    static final String JS_MIXED = """
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

}

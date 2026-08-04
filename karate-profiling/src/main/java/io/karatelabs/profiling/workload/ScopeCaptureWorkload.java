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

import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;

import java.time.Duration;

/**
 * The matched pair that isolates the cost of holding large collections in scope.
 *
 * <p>Both variants make exactly the same number of calls against the same callee over the
 * same base payload, and run the same trailing steps. The <em>only</em> difference is
 * whether each captured result stays bound. So anything that scales with call count, step
 * count or payload size is held constant, and a divergence between them can only be about
 * live scope.
 *
 * <p>Run them together — a number from one alone means nothing:
 * <pre>
 *   etc/run.sh scope-capture-bound
 *   etc/run.sh scope-capture-unbound
 * </pre>
 */
public abstract class ScopeCaptureWorkload extends FeatureWorkload {

    @Override
    public JvmConfig jvm() {
        // 768m is small enough that a geometric copy cost cannot hide, and is the size at
        // which this shape was originally reported. G1 because that is what most users run;
        // pass --gc zgc to reproduce the reported environment.
        return new JvmConfig("768m", JvmConfig.Gc.G1);
    }

    @Override
    public RunShape shape() {
        return new RunShape(16, 5000, null, Duration.ofSeconds(5), null);
    }

    public static final class Bound extends ScopeCaptureWorkload {

        @Override
        public String name() {
            return "scope-capture-bound";
        }

        @Override
        public String describe() {
            return "Thirteen bare karate.call()s over a 100-record payload, each result left bound in "
                    + "scope. Pair with scope-capture-unbound — the difference between them is the "
                    + "cost of holding large collections across steps.";
        }

        @Override
        protected String feature() {
            return "classpath:workload/scope-capture-bound.feature";
        }

    }

    public static final class Unbound extends ScopeCaptureWorkload {

        @Override
        public String name() {
            return "scope-capture-unbound";
        }

        @Override
        public String describe() {
            return "The control for scope-capture-bound: identical calls and payload, but each capture "
                    + "is released immediately after it is made.";
        }

        @Override
        protected String feature() {
            return "classpath:workload/scope-capture-unbound.feature";
        }

    }

}

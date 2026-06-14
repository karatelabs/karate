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
package io.karatelabs.js;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engines on different threads must not interfere with each other.
 * <p>
 * Built-in constructors are per-Engine instances and the shared prototype
 * singletons keep all per-Engine mutable state in Engine-owned overlays, so
 * constructing an Engine on one thread must leave engines mid-evaluation on
 * other threads fully functional. These tests pin that contract — the
 * historical clear-on-construct reset wiped JVM-wide singleton state and
 * produced intermittent "Object.keys is not a function" /
 * "Array.isArray is not a function" failures in parallel suite runs.
 */
class EngineConcurrencyTest {

    // exercises Object.keys, Array.isArray and an Object static via a bound
    // variable — the shapes reported to break under parallel suite runs
    static final String SCRIPT = "var queryObj = { a: 1, b: 2 };"
            + " var keys = Object.keys(queryObj);"
            + " var flag = Array.isArray([1, 2, 3]);"
            + " keys.length + (flag ? 10 : 0)";

    static void assertResult(Object result) {
        if (!(result instanceof Number n) || n.intValue() != 12) {
            throw new AssertionError("expected 12 but got: " + result);
        }
    }

    @Test
    @Timeout(120)
    void parallelEnginesEvalBuiltins() throws Exception {
        int threadCount = 8;
        int iterations = 500;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread(() -> {
                try {
                    barrier.await();
                    // same shape as a parallel suite run: each scenario gets a
                    // fresh Engine, many scenarios in flight at once
                    for (int i = 0; i < iterations; i++) {
                        Engine engine = new Engine();
                        assertResult(engine.eval(SCRIPT));
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            }, "engine-concurrency-" + t);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(60000);
        }
        assertTrue(failures.isEmpty(), () -> failures.size() + " of " + threadCount
                + " threads failed, first failure: " + failures.get(0));
    }

    @Test
    @Timeout(120)
    void engineConstructionDoesNotPoisonRunningEngine() throws Exception {
        int iterations = 2000;
        AtomicBoolean done = new AtomicBoolean();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        // worker holds ONE engine and just evaluates — it never touches
        // another Engine, so any failure here is cross-thread interference
        Thread worker = new Thread(() -> {
            try {
                Engine engine = new Engine();
                for (int i = 0; i < iterations; i++) {
                    assertResult(engine.eval(SCRIPT));
                }
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                done.set(true);
            }
        }, "engine-concurrency-worker");
        worker.setDaemon(true);
        worker.start();
        // churn Engine construction (per-scenario engines, match Operations,
        // TagSelector evals all do this in a parallel suite run)
        while (!done.get()) {
            new Engine();
        }
        worker.join(60000);
        assertTrue(failures.isEmpty(), () -> "running engine broke during concurrent"
                + " Engine construction: " + failures.get(0));
    }

}

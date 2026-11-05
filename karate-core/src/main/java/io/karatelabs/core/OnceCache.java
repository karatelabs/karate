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
package io.karatelabs.core;

import io.karatelabs.gherkin.Scenario;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The run-once cache protocol shared by {@code callonce} (keyword, {@code def x = callonce},
 * {@code karate.callonce()}), {@code karate.setupOnce()} and {@code karate.callSingle()}:
 * a lock-free read, then under the lock a re-check and at most one execution, whose outcome
 * is cached — a value, or a failure. A failure is cached like a value: the call is not
 * attempted again, every later caller fails fast with a {@link CachedFailureException} that
 * names the scenario that ran it (whose report holds the trace). Re-running a failed setup
 * for each scenario re-pays its cost (a retry loop, a timeout) only to fail the same way, and
 * buries the one real trace among copies.
 *
 * <p>A null value is not cached (the maps are concurrent maps), so such a call runs again.
 * Callers copy what they get: the cached value is shared across scenarios.
 */
final class OnceCache {

    private OnceCache() {
    }

    /** The value, and whether it came from the cache (false: this call just ran it). */
    record Result<T>(T value, boolean cached) {
    }

    /**
     * A cached failure. The origin Suite is held weakly: a host-shared cache outlives the
     * Suite, which must not stay reachable through it.
     */
    private record Failure(Throwable cause, Scenario origin, WeakReference<Suite> suite) {
    }

    /** Guarded by a ReentrantLock: the callonce and callSingle locks. */
    static <T> Result<T> get(Map<String, Object> cache, ReentrantLock lock, String key,
                             ScenarioRuntime rt, Supplier<T> run) {
        return get(cache, key, rt, run, body -> {
            lock.lock();
            try {
                return body.get();
            } finally {
                lock.unlock();
            }
        });
    }

    /** Guarded by the cache map's own monitor: the setupOnce cache, as KarateJs.startingFor checks it. */
    static <T> Result<T> getSynchronized(Map<String, Object> cache, String key,
                                         ScenarioRuntime rt, Supplier<T> run) {
        return get(cache, key, rt, run, body -> {
            synchronized (cache) {
                return body.get();
            }
        });
    }

    private static <T> Result<T> get(Map<String, Object> cache, String key, ScenarioRuntime rt, Supplier<T> run,
                                     Function<Supplier<Result<T>>, Result<T>> guarded) {
        Object cached = cache.get(key);
        if (cached != null) {
            return hit(cached, rt);
        }
        return guarded.apply(() -> {
            Object rechecked = cache.get(key);
            if (rechecked != null) {
                return hit(rechecked, rt);
            }
            T value;
            try {
                value = run.get();
            } catch (Exception | AssertionError e) {
                // what a step counts as a failure (StepExecutor), an AssertionError thrown by Java
                // glue included; other Errors (out of memory, stack overflow) are not the call's outcome.
                // Nor is a cancelled run's: it fails every call it reaches, and a host-shared cache
                // would carry that into the next run.
                if (!rt.isSuiteCancelled()) {
                    cache.put(key, new Failure(e, traceHolder(rt), new WeakReference<>(suiteOf(rt))));
                }
                throw e;
            }
            if (value != null) {
                cache.put(key, value);
            }
            return new Result<>(value, false);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Result<T> hit(Object cached, ScenarioRuntime rt) {
        if (cached instanceof Failure failure) {
            Suite suite = suiteOf(rt);
            throw new CachedFailureException(failure.cause(), failure.origin(),
                    suite != null && failure.suite().get() == suite);
        }
        return new Result<>((T) cached, true);
    }

    /**
     * The top-level scenario whose report holds what a call made on this thread attaches:
     * StepExecutor.addCallResult sends a called feature's result to the scenario live on the
     * thread (not the one a shared JS helper was captured in), and each enclosing call carries
     * it up to the top-level scenario's step. Null when the call ran inside an {@code @setup}
     * scenario, whose result karate.setup() discards.
     */
    private static Scenario traceHolder(ScenarioRuntime rt) {
        ScenarioRuntime top = live(rt);
        while (true) {
            if (top.getSetupCaller() != null) {
                return null;
            }
            FeatureRuntime fr = top.getFeatureRuntime();
            if (fr == null || fr.isTopLevel() || fr.getCallerScenario() == null) {
                return top.getScenario();
            }
            top = fr.getCallerScenario();
        }
    }

    // the Suite whose report holds what this thread's calls attach: the live scenario's, as above
    private static Suite suiteOf(ScenarioRuntime rt) {
        FeatureRuntime fr = live(rt).getFeatureRuntime();
        return fr != null ? fr.getSuite() : null;
    }

    // the scenario live on this thread, else the calling runtime (karate-config.js runs before call())
    private static ScenarioRuntime live(ScenarioRuntime rt) {
        ScenarioRuntime current = ScenarioRuntime.currentOrNull();
        return current != null ? current : rt;
    }

}

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

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.profiling.Workload;
import io.karatelabs.profiling.WorkloadContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base for workloads that are "run this feature, over and over, concurrently".
 *
 * <p>One iteration is one feature execution via {@link Runner#runFeature}, which builds a
 * fresh {@code Suite} and re-parses the feature each time. That is deliberate rather than
 * wasteful: it is exactly the per-virtual-user model karate-gatling uses, so what gets
 * measured here is what a load test actually pays.
 *
 * <p>The {@code callSingle} / {@code callOnce} / {@code setupOnce} caches are hoisted into
 * a shared template so they behave across iterations the way they do across virtual users
 * — once globally and once per feature respectively — instead of resetting every
 * iteration and quietly turning a cache workload into a no-cache workload.
 */
public abstract class FeatureWorkload implements Workload {

    private Runner.Builder template;
    private final AtomicBoolean reportedFailure = new AtomicBoolean();

    /** Classpath path of the feature this workload drives. */
    protected abstract String feature();

    @Override
    public void setup(WorkloadContext context) {
        template = Runner.builder()
                .callSingleCache(new ConcurrentHashMap<>(), new ReentrantLock())
                .callOnceCacheStore(new ConcurrentHashMap<>(), new ConcurrentHashMap<>())
                .setupOnceCacheStore(new ConcurrentHashMap<>());
        if (context.mockUrl() != null) {
            template.systemProperty("mock.url", context.mockUrl());
        }
    }

    @Override
    public void iterate(int vu, long iteration) {
        FeatureResult result = Runner.runFeature(feature(), null, null, null, template);
        if (result.isFailed()) {
            // A workload whose feature is quietly failing measures error handling rather
            // than the thing it was written for, so surface it — but only once, or a
            // systematic failure floods stdout and becomes its own bottleneck.
            if (reportedFailure.compareAndSet(false, true)) {
                throw new IllegalStateException("feature failed: " + feature() + " — " + firstError(result));
            }
        }
    }

    private static String firstError(FeatureResult result) {
        for (ScenarioResult scenario : result.getScenarioResults()) {
            if (scenario.isFailed()) {
                return String.valueOf(scenario.getErrorWithLocation());
            }
        }
        return "(no scenario error recorded)";
    }

}

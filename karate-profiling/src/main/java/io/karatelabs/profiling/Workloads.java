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

import io.karatelabs.profiling.workload.CallAccumulationWorkload;
import io.karatelabs.profiling.workload.FeatureSpreadWorkload;
import io.karatelabs.profiling.workload.HarnessSmokeWorkload;
import io.karatelabs.profiling.workload.JsEvalWorkload;
import io.karatelabs.profiling.workload.ScopeCaptureWorkload;
import io.karatelabs.profiling.workload.SuiteSoakWorkload;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The workload registry. Adding a workload means adding one line here — there is no
 * ServiceLoader indirection, because the set is small and a compile error is a better
 * failure mode than an empty catalogue at runtime.
 *
 * <p>The one exception is a family that is not always <em>on</em> the classpath — see
 * {@link #registerOptional} — which cannot be named here without breaking the build that
 * lacks it.
 *
 * <p>Order is preserved so {@code --list} reads sensibly rather than by hash.
 */
public final class Workloads {

    private static final Map<String, Workload> REGISTRY = new LinkedHashMap<>();

    static {
        register(new HarnessSmokeWorkload());
        // The matched pair — neither number means anything without the other.
        register(new ScopeCaptureWorkload.Bound());
        register(new ScopeCaptureWorkload.Unbound());
        // Also a matched pair: same total scenarios, opposite distribution across features.
        register(new CallAccumulationWorkload());
        register(new FeatureSpreadWorkload());
        // The Runner-lane soak: the only workload that puts reporting, TLS and a per-scenario
        // client lifecycle under sustained load at the same time.
        register(new SuiteSoakWorkload());
        // The karate-js family: fresh-eval engine workloads, A/B'd across two karate-js
        // builds with --js-jar. Five rows plus a guard; see JsEvalWorkload.
        register(new JsEvalWorkload.Arithmetic());
        register(new JsEvalWorkload.Strings());
        register(new JsEvalWorkload.Objects());
        register(new JsEvalWorkload.Functions());
        register(new JsEvalWorkload.Mixed());
        register(new JsEvalWorkload.Large1k());
        // Present only in a build that enabled -Pgatling — see registerOptional.
        registerOptional("io.karatelabs.profiling.gatling.GatlingWorkloads");
    }

    private Workloads() {
    }

    private static void register(Workload workload) {
        Workload previous = REGISTRY.put(workload.name(), workload);
        if (previous != null) {
            throw new IllegalStateException("duplicate workload name: " + workload.name());
        }
    }

    /**
     * Register a family that only exists in some builds. The one reflective seam in an
     * otherwise compile-checked registry, and it earns its keep: the Gatling workloads import
     * {@code io.gatling}, which is on the classpath only under {@code -Pgatling}, so a direct
     * reference here would stop the default build compiling.
     *
     * <p>Two ways it can be unavailable, both expected, both silent: the family is not compiled
     * at all, or it is compiled but the dependency it needs is not on the classpath — which
     * happens whenever an earlier profiled build left classes in {@code target/classes} that a
     * later unprofiled build did not clean. The family is expected to surface the second case by
     * resolving one of its dependency's classes up front; see {@code GatlingWorkloads}.
     *
     * <p>Anything else is a build problem and fails loudly, because silently shipping a shorter
     * catalogue is how you spend an afternoon wondering where a workload went.
     */
    private static void registerOptional(String className) {
        Class<?> family;
        try {
            family = Class.forName(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return; // built without the profile that provides it
        }
        try {
            Consumer<Workload> sink = Workloads::register;
            family.getMethod("registerInto", Consumer.class).invoke(null, sink);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof NoClassDefFoundError) {
                return; // compiled, but its dependency is not on this classpath
            }
            throw new IllegalStateException("workload family " + className + " failed to register", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("workload family " + className + " is on the classpath"
                    + " but could not be registered", e);
        }
    }

    public static Workload get(String name) {
        Workload workload = REGISTRY.get(name);
        if (workload == null) {
            throw new IllegalArgumentException("unknown workload: " + name
                    + "\nknown: " + String.join(", ", REGISTRY.keySet()));
        }
        return workload;
    }

    public static Map<String, Workload> all() {
        return REGISTRY;
    }

}

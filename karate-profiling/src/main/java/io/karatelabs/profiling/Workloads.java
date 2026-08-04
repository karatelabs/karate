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

import io.karatelabs.profiling.workload.HarnessSmokeWorkload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The workload registry. Adding a workload means adding one line here — there is no
 * ServiceLoader indirection, because the set is small and a compile error is a better
 * failure mode than an empty catalogue at runtime.
 *
 * <p>Order is preserved so {@code --list} reads sensibly rather than by hash.
 */
public final class Workloads {

    private static final Map<String, Workload> REGISTRY = new LinkedHashMap<>();

    static {
        register(new HarnessSmokeWorkload());
    }

    private Workloads() {
    }

    private static void register(Workload workload) {
        Workload previous = REGISTRY.put(workload.name(), workload);
        if (previous != null) {
            throw new IllegalStateException("duplicate workload name: " + workload.name());
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

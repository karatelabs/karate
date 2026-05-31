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
package io.karatelabs.core;

/**
 * Mints a <em>per-scenario</em> ext-global instance, bound fresh into each
 * scenario's JS scope. The alternative {@link Suite#registerGlobal(String, Object)}
 * registers one shared singleton for the whole Suite — correct for stateless
 * helpers, but unsafe for an ext whose object carries per-scenario mutable state
 * (e.g. {@code image.threshold = 0.02}) under parallel execution, since every
 * scenario would mutate the same instance.
 *
 * <p>Registering a factory instead (see
 * {@link Suite#registerGlobal(String, ExtGlobalFactory)}) gives each scenario its
 * own instance, created at seed time with the scenario's {@link KarateJsContext}
 * in hand — mirroring how {@code karate.channel('kafka')} receives the runtime at
 * creation. Through the context the instance can resolve {@code this:} /
 * {@code classpath:} / {@code file:} paths ({@link KarateJsContext#getWorkingDir()}
 * → {@link io.karatelabs.common.Resource#resolve(String)}), reach the
 * {@link ScenarioRuntime}, and read {@link KarateConfig} — without any thread-local
 * or shared state.</p>
 *
 * <p>Seeded in {@link ScenarioRuntime} init before {@code karate-config.js} runs,
 * exactly like the singleton form, so the per-scenario instance is visible to
 * config and to every step.</p>
 */
@FunctionalInterface
public interface ExtGlobalFactory {

    /**
     * Create the ext-global instance for one scenario.
     *
     * @param context the scenario's runtime context (path resolution, runtime,
     *                config); never null at seed time
     * @return the object bound into JS scope under the registered name (typically a
     *         {@link io.karatelabs.js.SimpleObject})
     */
    Object create(KarateJsContext context);

}

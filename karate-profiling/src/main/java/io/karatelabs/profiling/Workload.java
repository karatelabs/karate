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

import java.util.List;

/**
 * One thing the harness can measure.
 *
 * <p>A workload runs inside the <em>forked child</em> JVM ({@link Child}) — never in the
 * parent. It knows nothing about JFR, forking or reporting; it just does work, repeatedly,
 * on as many threads as it is told. Everything measurable about it is observed from the
 * outside.
 *
 * <p>{@link #iterate} is called concurrently from many virtual threads. Anything shared
 * that it touches must be thread-safe, or created per-iteration.
 *
 * <p><b>{@link #describe} must describe behaviour, not cite issue numbers</b> — it is
 * source, and the house rule (see CLAUDE.md) keeps tracker references out of source.
 * The docs are where an investigation gets named.
 */
public interface Workload {

    /** CLI selector, kebab-case. Must be unique across the registry. */
    String name();

    /** One line for {@code --list}: what this measures and what it exists to catch. */
    String describe();

    /**
     * Default JVM shape for this workload. Heap size and collector are part of the
     * experiment, not an environment detail — a leak that OOMs at 768m may never surface
     * at 4g. CLI flags override individual fields.
     */
    default JvmConfig jvm() {
        return JvmConfig.defaults();
    }

    /** Default run shape (threads, iterations or duration, warmup). CLI flags override. */
    default RunShape shape() {
        return RunShape.defaults();
    }

    /**
     * Extra flags for the child JVM, beyond heap and collector.
     *
     * <p>For a workload that cannot start without them — not for tuning. Heap and collector
     * belong in {@link #jvm()} because they are part of the experiment; this is for a
     * requirement of the code under test, and it lands in {@code run-meta.txt} with the rest
     * of the command line so a run is still reproducible from its own artifacts.
     */
    default List<String> jvmFlags() {
        return List.of();
    }

    /**
     * Whether the parent should fork a sibling mock-server JVM and pass its URL through.
     * Workloads that exercise pure in-JVM behaviour should say no: HTTP is noise, and a
     * mock is a second process to schedule and clean up.
     */
    default boolean needsMock() {
        return false;
    }

    /**
     * When true, {@link #iterate} is called exactly once on one thread, and the requested
     * threads and iterations are handed to the workload through {@link WorkloadContext}
     * instead of being used to drive it.
     *
     * <p>This exists because some things can only be observed across a whole suite. Anything
     * retained for the lifetime of a {@code Suite} is invisible to a workload that builds a
     * fresh {@code Suite} per iteration — each one becomes garbage as soon as the iteration
     * ends, so suite-lifetime accumulation collects harmlessly and the workload reports that
     * all is well. A workload hunting that has to own the suite, and therefore the
     * concurrency inside it.
     */
    default boolean drivesOwnConcurrency() {
        return false;
    }

    /** Classpath-relative feature that {@link MockJvm} serves. Only read when {@link #needsMock()}. */
    default String mockFeature() {
        return "classpath:mock/profiling-mock.feature";
    }

    /** Called once in the child before any iteration, including before warmup. */
    default void setup(WorkloadContext context) {
    }

    /**
     * One unit of work. Called concurrently.
     *
     * @param vu        virtual-user index, stable for the life of one thread
     * @param iteration monotonic iteration number across all threads
     */
    void iterate(int vu, long iteration);

    /** Called once after the measured window closes. Best-effort — skipped if the child dies. */
    default void teardown() {
    }

}

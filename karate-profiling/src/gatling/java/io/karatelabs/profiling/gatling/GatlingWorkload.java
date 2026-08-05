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
package io.karatelabs.profiling.gatling;

import io.gatling.javaapi.core.Simulation;
import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.Workload;
import io.karatelabs.profiling.WorkloadContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Base for workloads whose body is "run one Gatling simulation".
 *
 * <p>These are always {@link #drivesOwnConcurrency() self-driving}: Gatling owns the users
 * and the pacing, so the harness's own virtual-thread driver would be a second, competing
 * scheduler measuring itself. {@code --threads} and {@code --iterations} are therefore
 * translated into an injection profile rather than used to call {@link #iterate}:
 *
 * <pre>
 *   --threads N      → N virtual users, injected at once
 *   --iterations M   → each user repeats ceil(M / N) times
 * </pre>
 *
 * <p>Every workload in this family exists to be <b>compared with its pair</b> — a Karate
 * variant and a plain-Gatling variant doing the same work. A single number here means very
 * little: what is being measured is a ratio, on one machine, back to back.
 */
public abstract class GatlingWorkload implements Workload {

    /** Read by the simulations, which Gatling constructs by name and cannot be handed arguments. */
    static final String USERS_PROPERTY = "karate.profiling.gatling.users";
    static final String REPS_PROPERTY = "karate.profiling.gatling.reps";
    static final String MOCK_URL_PROPERTY = "karate.profiling.mockUrl";

    private WorkloadContext context;

    /** The simulation to run. Loaded by Gatling from the classpath, by name. */
    protected abstract Class<? extends Simulation> simulation();

    @Override
    public boolean drivesOwnConcurrency() {
        return true;
    }

    @Override
    public JvmConfig jvm() {
        // Roomier than the memory workloads: these are throughput comparisons, and a heap
        // small enough to make GC the bottleneck would compare collectors, not clients.
        return new JvmConfig("1g", JvmConfig.Gc.G1);
    }

    @Override
    public RunShape shape() {
        return RunShape.defaults().withThreads(16).withIterations(2000L);
    }

    @Override
    public List<String> jvmFlags() {
        // Not optional and not tuning: Gatling's log writer reaches into java.lang String
        // internals through MethodHandles.privateLookupIn, which the JVM refuses unless the
        // package is opened. Without this the run dies before the first request, in
        // DataWritersStatsEngine. Users hit the same wall — the gatling-maven-plugin config
        // in the docs carries the same flag.
        return List.of("--add-opens=java.base/java.lang=ALL-UNNAMED");
    }

    @Override
    public void setup(WorkloadContext context) {
        this.context = context;
    }

    @Override
    public void iterate(int vu, long iteration) {
        int users = Math.max(1, context.threads());
        long total = context.iterations();
        if (total < 0) {
            // The harness passes a duration-bounded run through as iterations = -1, and a
            // self-driving workload never sees the duration itself. Rather than guess, say so:
            // a soak needs `during()` in the injection profile, which is a real feature and
            // not something to fake by picking a repetition count.
            throw new IllegalStateException(name() + " is iteration-bounded — use --iterations,"
                    + " not --duration (see docs/PROFILING.md)");
        }
        int reps = (int) Math.max(1, Math.ceilDiv(total, users));

        System.setProperty(USERS_PROPERTY, Integer.toString(users));
        System.setProperty(REPS_PROPERTY, Integer.toString(reps));
        System.out.println("[gatling] " + name() + ": " + users + " users x " + reps
                + " reps = " + ((long) users * reps) + " iterations");

        GatlingRunner.run(simulation(), resultsDir());
    }

    /**
     * Gatling's own output goes inside the run directory, so one run is still one
     * self-contained, prunable folder — the property the harness's disk hygiene depends on.
     */
    private static Path resultsDir() {
        String runDir = System.getProperty("karate.profiling.runDir");
        if (runDir == null) {
            throw new IllegalStateException("karate.profiling.runDir not set — not launched by the Profiler?");
        }
        return Path.of(runDir, "gatling");
    }

}

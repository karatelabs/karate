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
 *   --duration D     → each user loops for D instead, via `during()`
 * </pre>
 *
 * <p>The duration form is what makes a <b>Gatling soak</b> possible. Note the harness cannot
 * enforce that window itself here, because a self-driving workload owns its own scheduler — so
 * {@code Child} compares the elapsed time against the one that was asked for and marks the run
 * truncated when they disagree, rather than trusting this to have honoured it.
 *
 * <p>Every workload in this family exists to be <b>compared with its pair</b> — a Karate
 * variant and a plain-Gatling variant doing the same work. A single number here means very
 * little: what is being measured is a ratio, on one machine, back to back.
 */
public abstract class GatlingWorkload implements Workload {

    /** Read by the simulations, which Gatling constructs by name and cannot be handed arguments. */
    static final String USERS_PROPERTY = "karate.profiling.gatling.users";
    static final String REPS_PROPERTY = "karate.profiling.gatling.reps";
    /** Set instead of {@link #REPS_PROPERTY} when the run is duration-bounded; never both. */
    static final String DURATION_SECONDS_PROPERTY = "karate.profiling.gatling.durationSeconds";
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
        System.setProperty(USERS_PROPERTY, Integer.toString(users));

        java.time.Duration window = context.duration();
        if (window != null) {
            // A duration bound becomes `during()` in the injection profile — the loop is Gatling's
            // to close, because Gatling owns the users. This is what makes a Gatling soak possible
            // at all: the alternative, converting a window into a repetition count from an
            // estimated rate, lands on whatever the estimate was wrong by, and a soak's whole
            // point is that its length is the thing being held constant.
            System.clearProperty(REPS_PROPERTY);
            System.setProperty(DURATION_SECONDS_PROPERTY, Long.toString(window.toSeconds()));
            System.out.println("[gatling] " + name() + ": " + users + " users for "
                    + window.toSeconds() + "s");
        } else {
            long total = context.iterations();
            int reps = (int) Math.max(1, Math.ceilDiv(total, users));
            System.clearProperty(DURATION_SECONDS_PROPERTY);
            System.setProperty(REPS_PROPERTY, Integer.toString(reps));
            System.out.println("[gatling] " + name() + ": " + users + " users x " + reps
                    + " reps = " + ((long) users * reps) + " iterations");
        }

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

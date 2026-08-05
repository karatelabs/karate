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
import io.gatling.shared.cli.GatlingCliOptions;

import java.nio.file.Path;

/**
 * The single seam between the profiling harness and Gatling: runs one simulation, in this
 * (already forked, already recording) JVM, and returns.
 *
 * <p>Everything about how Gatling is invoked lives here rather than in the workloads, because
 * the invocation is the part that breaks on a Gatling upgrade. It broke once already: the
 * documented plan was {@code Gatling.fromMap(...)}, which no longer exists in 3.15 — the
 * programmatic entry point is now {@code fromArgs}, reached through the Scala companion
 * object. Keeping that in one place means the next upgrade is one method, not five.
 *
 * <p>{@code fromArgs} is used rather than {@code main} deliberately: {@code main} calls
 * {@code System.exit}, which would kill the child before it wrote its summary line and before
 * JFR flushed the recording — turning every Gatling run into an empty {@code run.jfr}.
 */
final class GatlingRunner {

    private GatlingRunner() {
    }

    /**
     * Run a simulation to completion.
     *
     * @param simulation  the simulation class; Gatling loads it by name off the classpath
     * @param resultsDir  where Gatling writes {@code simulation.log} — inside the run directory,
     *                    so a run's artifacts stay together and are pruned together
     * @throws IllegalStateException if Gatling reports a non-zero status, which for our purposes
     *                               means the measurement is void rather than that a test failed
     */
    static void run(Class<? extends Simulation> simulation, Path resultsDir) {
        String[] args = {
                GatlingCliOptions.Simulation.shortOption(), simulation.getName(),
                GatlingCliOptions.ResultsFolder.shortOption(), resultsDir.toAbsolutePath().toString(),
                // Chart generation is a second pass over the whole simulation log that allocates
                // heavily and measures nothing we are asking about — it would show up in the
                // digest as if it were load-driving cost.
                GatlingCliOptions.NoReports.shortOption()
        };
        int status = io.gatling.app.Gatling$.MODULE$.fromArgs(args);
        if (status != 0) {
            throw new IllegalStateException("gatling exited with status " + status
                    + " for " + simulation.getSimpleName() + " — see stdout.log");
        }
    }

}

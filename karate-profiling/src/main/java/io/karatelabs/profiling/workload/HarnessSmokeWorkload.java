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

import io.karatelabs.profiling.JvmConfig;
import io.karatelabs.profiling.RunShape;
import io.karatelabs.profiling.Workload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cheap, self-contained churn: builds and discards a small nested map per iteration.
 *
 * <p>This exists to prove the <em>harness</em> works, not to measure Karate. It touches no
 * feature files, no HTTP and no mock, so a failure here is unambiguously the plumbing —
 * forking, JFR attachment, digest generation — and not the thing under test.
 *
 * <p>It also gives the allocation panel a known-good answer. Every iteration allocates in
 * exactly one place, so if {@code buildRecord} is not at the top of "Allocation by site",
 * the digest is wrong and nothing else it says can be trusted.
 */
public class HarnessSmokeWorkload implements Workload {

    private static final int RECORDS = 25;

    @Override
    public String name() {
        return "harness-smoke";
    }

    @Override
    public String describe() {
        return "Allocates a small nested map per iteration. Validates forking, JFR capture and "
                + "digest generation — not Karate. Allocation should attribute to buildRecord.";
    }

    @Override
    public JvmConfig jvm() {
        // Small heap and G1: this should never come close to filling it, so an OOM here
        // means the harness is broken rather than the workload being interesting.
        return new JvmConfig("256m", JvmConfig.Gc.G1);
    }

    @Override
    public RunShape shape() {
        // Sized so the measured window is a couple of seconds on a modern laptop — long
        // enough for JFR to take a useful number of samples, short enough to be a smoke test.
        return new RunShape(8, 4_000_000, null, Duration.ofSeconds(1), null);
    }

    @Override
    public void iterate(int vu, long iteration) {
        List<Map<String, Object>> records = new ArrayList<>(RECORDS);
        for (int i = 0; i < RECORDS; i++) {
            records.add(buildRecord(vu, iteration, i));
        }
        // Consume the result so nothing is optimised away.
        if (records.size() != RECORDS) {
            throw new IllegalStateException("unreachable");
        }
    }

    private static Map<String, Object> buildRecord(int vu, long iteration, int index) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("vu", vu);
        nested.put("iteration", iteration);
        nested.put("index", index);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", vu + "-" + iteration + "-" + index);
        record.put("active", index % 3 != 0);
        record.put("score", index * 1.5);
        record.put("nested", nested);
        return record;
    }

}

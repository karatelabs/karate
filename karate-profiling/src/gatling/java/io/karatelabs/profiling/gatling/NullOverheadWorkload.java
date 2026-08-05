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

/**
 * What does one {@code karateFeature()} exec cost when the feature does <em>nothing</em>?
 *
 * <p>The pair isolates the fixed cost of the bridge and of Karate's per-execution machinery —
 * building a {@code Suite}, parsing the feature, evaluating {@code karate-config.js}, running
 * one trivial scenario, threading the session maps back — from any work the user asked for.
 * There is no HTTP on either side, so nothing in the number is the network or the mock.
 *
 * <pre>
 *   gatling-null-plain    Gatling's own floor: an exec that returns the session unchanged
 *   gatling-null-karate   the same injection profile, running an empty feature
 * </pre>
 *
 * <p><b>Read the difference, never either number alone.</b> The plain variant is not a
 * baseline anyone would run in production; it exists to subtract Gatling's scheduling cost
 * from the Karate variant so what is left is attributable to Karate. Neither variant makes a
 * request, so Gatling's own reports are empty by design — the measurement is wall-clock from
 * the harness summary and the allocation panel from the digest.
 */
public final class NullOverheadWorkload {

    private NullOverheadWorkload() {
    }

    public static final class Karate extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-null-karate";
        }

        @Override
        public String describe() {
            return "Gatling driving an empty Karate feature. Paired with gatling-null-plain: the "
                    + "difference is what one karateFeature() exec costs before any user work.";
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return NullKarateSimulation.class;
        }
    }

    public static final class Plain extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-null-plain";
        }

        @Override
        public String describe() {
            return "Gatling driving a no-op exec, same injection profile. The floor to subtract "
                    + "from gatling-null-karate — Gatling's own per-iteration cost.";
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return NullPlainSimulation.class;
        }
    }

}

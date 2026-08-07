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

import io.karatelabs.profiling.Workload;

import java.util.function.Consumer;

/**
 * Entry point the core registry calls reflectively when this family is on the classpath —
 * i.e. when the build enabled {@code -Pgatling}. See {@code Workloads.registerOptional}.
 *
 * <p>The signature is the contract: a public static {@code registerInto(Consumer)}. It is not
 * compile-checked from the other side, so changing it means changing both.
 */
public final class GatlingWorkloads {

    private GatlingWorkloads() {
    }

    public static void registerInto(Consumer<Workload> sink) {
        // Resolve a Gatling class before registering anything. These classes can be present in
        // target/classes while the Gatling jars are NOT on the classpath — an earlier -Pgatling
        // build leaves them behind, and the next build without the profile does not clean them.
        // Java resolves the io.gatling types in our method signatures lazily, so without this
        // the family registers happily and then dies with NoClassDefFoundError in the middle of
        // a run. Failing here instead lets the registry treat it as "not available", which is
        // what a classpath without Gatling means however it came about.
        io.gatling.javaapi.core.Simulation.class.getName();
        // Registered in pairs, adjacent, because that is the only way they may be read.
        sink.accept(new NullOverheadWorkload.Plain());
        sink.accept(new NullOverheadWorkload.Karate());
        sink.accept(new HttpParityWorkload.Plain());
        sink.accept(new HttpParityWorkload.Karate());
        // The equivalence controls pair ACROSS the family above rather than with each other:
        // plain-fat runs against gatling-http-karate, karate-lean against gatling-http-plain.
        // Running these two together would compare two changed arms and answer nothing.
        // `matrix.sh --control fat|lean` is what wires each to the right partner.
        sink.accept(new HttpEquivalenceWorkload.PlainFat());
        sink.accept(new HttpEquivalenceWorkload.KarateLean());
    }

}

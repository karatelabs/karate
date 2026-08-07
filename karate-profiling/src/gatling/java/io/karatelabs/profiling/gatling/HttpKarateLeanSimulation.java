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

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.karatelabs.gatling.KarateProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.karatelabs.gatling.KarateDsl.*;

/**
 * The lower equivalence control: {@link HttpKarateSimulation} with the structural match reduced
 * to the single-field check the plain reference makes. See {@link HttpEquivalenceWorkload}.
 *
 * <p>Identical to the Karate arm in every other respect — same protocol, same URI patterns, same
 * shape — because anything else that differs lands in the measurement as if it were assertion
 * cost. The only difference is the feature file it runs.
 */
public class HttpKarateLeanSimulation extends Simulation {

    KarateProtocolBuilder protocol = karateProtocol(
            uri("/cats/{id}").nil(),
            uri("/cats").nil()
    );

    ScenarioBuilder scn = scenario("karate-http-lean")
            .repeat(SimShape.reps()).on(
                    exec(karateFeature("classpath:workload/gatling-http-lean.feature"))
            );

    {
        protocol.runner.systemProperty("mock.url", SimShape.mockUrl());
        if (SimShape.POOL != null) {
            protocol.runner.httpClientFactory(SimShape.POOL);
        }
        setUp(scn.injectOpen(atOnceUsers(SimShape.users()))).protocols(protocol);
    }

}

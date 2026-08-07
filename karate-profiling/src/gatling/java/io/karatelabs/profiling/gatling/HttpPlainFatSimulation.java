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
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * The upper equivalence control: {@link HttpPlainSimulation} raised to check everything the
 * Karate feature checks. See {@link HttpEquivalenceWorkload} for what the pair is for.
 *
 * <p>Karate's {@code match response == { id: '#(catId)', name: 'Billie', age: 5 }} verifies all
 * three fields and that the id came back as the one that was sent. The plain reference checks
 * one field and never looks at the id, so it does strictly less work — this variant closes that
 * gap on the Gatling side.
 *
 * <p>Note this is NOT a claim about what a Gatling user typically writes. Gatling's documentation
 * describes the available check types and does not recommend a set, so there is no norm to appeal
 * to; the only defensible basis for a control is mirroring the other arm's semantics, which is
 * what this does.
 *
 * <p>The explicit status checks are kept, in both arms, and they are not redundant. Gatling
 * applies an implicit check for 2XX-or-304 when none is written, which would accept any success
 * code where Karate's {@code status 201} pins exactly one. Dropping them would quietly hand the
 * plain arm the weaker assertion.
 */
public class HttpPlainFatSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(SimShape.mockUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("plain-http-fat")
            .repeat(SimShape.reps()).on(
                    exec(http("POST /cats")
                            .post("/cats")
                            .body(StringBody("{ \"name\": \"Billie\", \"age\": 5 }"))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("catId")))
                            .exec(http("GET /cats/{id}")
                                    .get(session -> "/cats/" + session.getString("catId"))
                                    .check(status().is(200))
                                    // The id round-trip Karate asserts and the plain reference
                                    // does not: the response must carry back the id that was
                                    // captured, not merely some id.
                                    .check(jsonPath("$.id").isEL("#{catId}"))
                                    .check(jsonPath("$.name").is("Billie"))
                                    .check(jsonPath("$.age").is("5")))
            );

    {
        setUp(scn.injectOpen(atOnceUsers(SimShape.users()))).protocols(httpProtocol);
    }

}

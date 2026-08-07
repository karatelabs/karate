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
import io.karatelabs.profiling.Payload;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * The body-size tier's equivalence control: the plain arm raised to check everything the Karate
 * arm checks, <b>including the padding</b>.
 *
 * <p>This is the one that makes the tier's result readable. If the Karate arm's cost grows with
 * body size and this one's does not, the growth is Karate handling more of the response — a real
 * difference, and a fair one to publish. If this one grows the same way, the growth is the cost of
 * checking a large field and says nothing about Karate. Without this control a rising deficit
 * cannot be attributed to either.
 */
public class HttpBodyPlainFatSimulation extends Simulation {

    private static final String BODY = Payload.requestBody();
    private static final String PAD = Payload.pad();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(SimShape.mockUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("plain-body-fat")
            .exec(SimShape.loop(
                    exec(http("POST /cats")
                            .post("/cats")
                            .body(StringBody(BODY))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("catId")))
                            .exec(http("GET /cats/{id}")
                                    .get(session -> "/cats/" + session.getString("catId"))
                                    .check(status().is(200))
                                    .check(jsonPath("$.id").isEL("#{catId}"))
                                    .check(jsonPath("$.name").is("Billie"))
                                    .check(jsonPath("$.age").is("5"))
                                    // The whole point of this control. Same residual asymmetry as
                                    // the unpadded fat arm: these are open checks comparing text,
                                    // where Karate's match is closed and typed.
                                    .check(jsonPath("$.pad").is(PAD)))
            ));

    {
        setUp(scn.injectOpen(atOnceUsers(SimShape.users()))).protocols(httpProtocol);
    }

}

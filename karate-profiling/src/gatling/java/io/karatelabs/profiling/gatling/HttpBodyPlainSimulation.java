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
 * The body-size tier's reference arm: the same two calls as {@link HttpBodyKarateSimulation}
 * against the same payload, checked the way a Gatling user would.
 *
 * <p><b>It never reads the padding.</b> The Karate arm's closed match deep-compares the whole
 * document including the pad; this one extracts three small fields. That is the idiomatic form of
 * each and is exactly what the tier measures — whether the gap between them holds as the body
 * grows. {@link HttpBodyPlainFatSimulation} is the control that raises this arm to check the pad
 * too, which separates "Karate handles more of the response" from "Karate is slower per byte".
 */
public class HttpBodyPlainSimulation extends Simulation {

    // Built once, not per iteration: the Karate arm's per-iteration cost includes constructing
    // its request, but neither arm should pay for generating the padding itself.
    private static final String BODY = Payload.requestBody();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(SimShape.mockUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("plain-body")
            .exec(SimShape.loop(
                    exec(http("POST /cats")
                            .post("/cats")
                            .body(StringBody(BODY))
                            .check(status().is(201))
                            .check(jsonPath("$.id").saveAs("catId")))
                            .exec(http("GET /cats/{id}")
                                    .get(session -> "/cats/" + session.getString("catId"))
                                    .check(status().is(200))
                                    .check(jsonPath("$.name").is("Billie")))
            ));

    {
        setUp(scn.injectOpen(atOnceUsers(SimShape.users()))).protocols(httpProtocol);
    }

}

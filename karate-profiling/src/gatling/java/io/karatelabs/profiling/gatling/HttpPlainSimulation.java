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
 * The reference: the same POST + GET as {@link HttpKarateSimulation}, written the way a
 * Gatling user would. See {@link HttpParityWorkload} for what the difference does and does
 * not mean.
 */
public class HttpPlainSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(SimShape.mockUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("plain-http")
            .exec(SimShape.loop(
                    exec(http("POST /cats")
                            .post("/cats")
                            .body(StringBody("{ \"name\": \"Billie\", \"age\": 5 }"))
                            .check(status().is(201))
                            // Karate's feature binds the id and reads it back on the next
                            // request; capturing it here keeps the two doing the same thing.
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

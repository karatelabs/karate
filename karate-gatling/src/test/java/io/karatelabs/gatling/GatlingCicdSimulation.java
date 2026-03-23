/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.gatling;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.karatelabs.gatling.KarateDsl.*;

/**
 * Comprehensive CICD Gatling simulation for karate-gatling integration.
 * <p>
 * Combines load testing and validation scenarios:
 * - Basic CRUD operations (load test)
 * - Feature chaining with feeders (load test)
 * - Java interop with PerfContext (validation)
 * - Error handling - intentional failures (validation)
 * - Silent warm-up
 * <p>
 * Run with: mvn verify -pl karate-gatling -Pcicd
 */
public class GatlingCicdSimulation extends Simulation {

    // Start mock server before simulation runs
    static {
        CatsMockServer.start();
    }

    // Protocol with URI pattern configuration
    KarateProtocolBuilder protocol = karateProtocol(
            uri("/cats/{id}").nil(),
            uri("/cats").pauseFor(method("get", 5), method("post", 10)).build()
    );

    // Feeder for data-driven tests
    Iterator<Map<String, Object>> catFeeder = Stream.iterate(0, i -> i + 1)
            .map(i -> Map.<String, Object>of(
                    "name", "Cat" + i,
                    "age", (i % 10) + 1
            ))
            .iterator();

    // Scenario 1: Silent warm-up (not reported to Gatling stats)
    ScenarioBuilder warmupScenario = scenario("Warm-up")
            .exec(karateFeature("classpath:features/cats-crud.feature").silent());

    // Scenario 2: Basic CRUD operations (load test)
    ScenarioBuilder crudScenario = scenario("CRUD Operations")
            .exec(karateFeature("classpath:features/cats-crud.feature"));

    // Scenario 3: Chained features with feeder data (load test)
    ScenarioBuilder chainedScenario = scenario("Chained Operations")
            .feed(catFeeder)
            .exec(karateSet("name", s -> s.getString("name")))
            .exec(karateSet("age", s -> s.getInt("age")))
            .exec(karateFeature("classpath:features/cats-create.feature"))
            .exec(karateFeature("classpath:features/cats-read.feature"));

    // Scenario 4: Java interop with PerfContext.capturePerfEvent()
    ScenarioBuilder javaInteropScenario = scenario("Java Interop")
            .exec(karateFeature("classpath:features/custom-rpc.feature"));

    // Scenario 5: Error handling - intentional failure to verify Gatling reports errors
    ScenarioBuilder errorHandlingScenario = scenario("Error Handling")
            .feed(catFeeder)
            .exec(karateSet("name", s -> s.getString("name")))
            .exec(karateFeature("classpath:features/cats-create-fail.feature"));

    {
        setUp(
                // Silent warm-up first
                warmupScenario.injectOpen(atOnceUsers(1)),
                // Load test scenarios
                crudScenario.injectOpen(
                        nothingFor(1),  // Wait for warm-up
                        rampUsers(3).during(3)
                ),
                chainedScenario.injectOpen(
                        nothingFor(1),
                        rampUsers(2).during(3)
                ),
                // Validation scenarios
                javaInteropScenario.injectOpen(
                        nothingFor(1),
                        atOnceUsers(2)
                ),
                errorHandlingScenario.injectOpen(
                        nothingFor(1),
                        atOnceUsers(2)
                )
        ).protocols(protocol)
        .assertions(
                // GET requests should succeed
                details("GET /cats/{id}").failedRequests().percent().is(0.0),
                // POST /cats includes Error Handling scenario - expect at least 1 failure
                details("POST /cats").failedRequests().count().gte(1L),
                // Verify we got requests (simulation ran correctly)
                global().allRequests().count().gte(8L)
        );
    }

}

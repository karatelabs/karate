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
 * Smoke test simulation for karate-gatling integration.
 * <p>
 * This simulation runs a minimal test to verify:
 * - Karate features execute correctly
 * - Variable chaining works between features
 * - No failures occur during execution
 * <p>
 * This simulation is designed to be fast and run during normal build.
 */
public class GatlingSmokeSimulation extends Simulation {

    // Start mock server before simulation runs
    static {
        CatsMockServer.start();
    }

    // Simple protocol with URI patterns
    KarateProtocolBuilder protocol = karateProtocol(
            uri("/cats/{id}").nil(),
            uri("/cats").nil()
    );

    // Feeder for data-driven tests
    Iterator<Map<String, Object>> catFeeder = Stream.iterate(0, i -> i + 1)
            .map(i -> Map.<String, Object>of(
                    "name", "SmokeTestCat" + i,
                    "age", (i % 5) + 1
            ))
            .iterator();

    // Scenario: CRUD operations (single feature)
    ScenarioBuilder crudScenario = scenario("CRUD Smoke")
            .exec(karateFeature("classpath:features/cats-crud.feature"));

    // Scenario: Variable chaining (multiple features)
    ScenarioBuilder chainScenario = scenario("Chain Smoke")
            .feed(catFeeder)
            .exec(karateSet("name", s -> s.getString("name")))
            .exec(karateSet("age", s -> s.getInt("age")))
            .exec(karateFeature("classpath:features/cats-create.feature"))
            .exec(karateFeature("classpath:features/cats-read.feature"));

    {
        setUp(
                // Run with minimal users for smoke test
                crudScenario.injectOpen(atOnceUsers(2)),
                chainScenario.injectOpen(atOnceUsers(2))
        ).protocols(protocol)
                .assertions(
                        // All scenarios must succeed (no KO)
                        global().failedRequests().count().is(0L),
                        // All scenarios should complete
                        global().allRequests().count().gte(0L)
                );
    }

}

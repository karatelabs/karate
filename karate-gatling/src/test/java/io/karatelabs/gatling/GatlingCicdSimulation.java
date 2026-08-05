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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
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

    // Captures what the log replayer emitted, asserted in after(). The replay buffer is carried on
    // the Gatling Session between exec() calls, so only a real simulation exercises that round-trip.
    private static final ListAppender<ILoggingEvent> REPLAY_LOG = new ListAppender<>();

    // Start mock server before simulation runs
    static {
        CatsMockServer.start();
        REPLAY_LOG.start();
        ((Logger) LoggerFactory.getLogger(LogReplayer.class)).addAppender(REPLAY_LOG);
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

    // Scenario 7: a feature that passes followed by one that fails, under a protocol that replays
    // the output of everything already run — the "what led up to this?" context a quiet load run
    // otherwise throws away. Its own protocol so the rest of the simulation stays unaffected.
    KarateProtocolBuilder logReplayProtocol = karateProtocol(
            uri("/cats/{id}").nil(),
            uri("/cats").nil()
    ).logReplay(KarateLogReplay.ALL);

    ScenarioBuilder logReplayScenario = scenario("Log Replay")
            .feed(catFeeder)
            .exec(karateSet("name", s -> s.getString("name")))
            .exec(karateFeature("classpath:features/cats-create.feature"))
            .exec(karateFeature("classpath:features/cats-create-fail.feature"));

    // Scenario 6: Group-wrapped CRUD to verify Gatling sub-group aggregation
    ScenarioBuilder groupedScenario = scenario("Grouped CRUD")
            .group("Grouped-CRUD").on(
                    exec(karateFeature("classpath:features/cats-crud.feature"))
            );

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
                ),
                groupedScenario.injectOpen(
                        nothingFor(1),
                        atOnceUsers(2)
                ),
                logReplayScenario.injectOpen(
                        nothingFor(1),
                        atOnceUsers(1)
                ).protocols(logReplayProtocol)
        ).protocols(protocol)
        .assertions(
                // GET requests should succeed
                details("GET /cats/{id}").failedRequests().percent().is(0.0),
                // POST /cats includes Error Handling scenario - expect at least 1 failure
                details("POST /cats").failedRequests().count().gte(1L),
                // Verify we got requests (simulation ran correctly)
                global().allRequests().count().gte(8L),
                // Sub-group aggregation - 2 users x (1 POST + 1 GET) = 2 each
                details("Grouped-CRUD", "POST /cats").successfulRequests().count().is(2L),
                details("Grouped-CRUD", "GET /cats/{id}").successfulRequests().count().is(2L),
                details("Grouped-CRUD", "POST /cats").failedRequests().count().is(0L)
        );
    }

    /**
     * The Log Replay scenario must have emitted the output of BOTH features: the one that already
     * passed — held on the Gatling Session across the exec() boundary — and the one that failed.
     * A Gatling assertion can only see request stats, so this is checked against the log.
     */
    @Override
    public void after() {
        List<String> replayed = REPLAY_LOG.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        boolean passedReplayed = replayed.stream()
                .anyMatch(m -> m.contains("cats-create.feature [passed]"));
        boolean failedReplayed = replayed.stream()
                .anyMatch(m -> m.contains("cats-create-fail.feature [failed]"));
        if (!passedReplayed || !failedReplayed) {
            throw new IllegalStateException("log replay did not survive the session round-trip"
                    + " - passed feature replayed: " + passedReplayed
                    + ", failed feature replayed: " + failedReplayed);
        }
    }

}

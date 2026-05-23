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

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static io.karatelabs.gatling.KarateDsl.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for Gatling DSL components.
 * These tests validate that the DSL works correctly without running
 * a full Gatling simulation (which requires HTTP metrics reporting).
 */
public class GatlingDslTest {

    @BeforeAll
    static void setup() {
        CatsMockServer.start();
    }

    @Test
    void testKarateProtocolBuilder() {
        KarateProtocolBuilder protocol = karateProtocol(
                uri("/cats/{id}").nil(),
                uri("/cats").pauseFor(method("get", 10), method("post", 20)).build()
        );

        assertNotNull(protocol, "Protocol should be created");

        // Verify URI pattern matching
        KarateProtocol p = protocol.build();
        assertTrue(p.pathMatches("/cats/{id}", "/cats/123"));
        assertTrue(p.pathMatches("/cats/{id}", "/cats/abc"));
        assertFalse(p.pathMatches("/cats/{id}", "/cats"));
        assertFalse(p.pathMatches("/cats/{id}", "/dogs/123"));

        // Verify pause configuration
        assertEquals(10, p.pauseFor("/cats", "get"));
        assertEquals(20, p.pauseFor("/cats", "post"));
        assertEquals(0, p.pauseFor("/cats", "delete"));  // Not configured
        assertEquals(0, p.pauseFor("/cats/{id}", "get")); // nil() means 0
    }

    @Test
    void testKarateFeatureBuilder() {
        KarateFeatureBuilder builder = karateFeature("classpath:features/cats-crud.feature");
        assertNotNull(builder, "Feature builder should be created");

        // Test silent mode
        KarateFeatureBuilder silentBuilder = builder.silent();
        assertNotNull(silentBuilder, "Silent feature builder should be created");
    }

    /**
     * Regression: issue #2870 — {@code karateFeature("path", "@tag")} must treat
     * trailing string args as tag selectors, not additional feature paths.
     * Also verifies that the tag filter is honored end-to-end via Runner.runFeature.
     */
    @Test
    void testKarateFeatureTagFilter() {
        // DSL: positional tag arg should be accepted without error
        KarateFeatureBuilder builder = karateFeature(
                "classpath:features/tag-filter.feature", "@perf");
        assertNotNull(builder);

        // End-to-end: tag selector filters scenarios at runtime
        FeatureResult result = Runner.runFeature(
                "classpath:features/tag-filter.feature",
                new HashMap<>(),
                null,
                List.of("@perf"));
        assertFalse(result.isFailed(), "feature should not fail");

        List<ScenarioResult> ran = result.getScenarioResults();
        assertEquals(1, ran.size(), "only the @perf scenario should run");
        assertEquals("tagged scenario", ran.get(0).getScenario().getName());
    }

    @Test
    void testProtocolRunnerKarateEnv() {
        // Mirrors v1: protocol.runner.karateEnv("perf") feeds karate.env
        KarateProtocolBuilder protocolBuilder = karateProtocol();
        protocolBuilder.runner.karateEnv("perf");
        KarateProtocol protocol = protocolBuilder.build();
        assertNotNull(protocol.getRunner(), "protocol.runner should be propagated");

        FeatureResult result = Runner.runFeature(
                "classpath:features/runner-env.feature",
                new HashMap<>(),
                null,
                null,
                protocol.getRunner());
        assertFalse(result.isFailed(),
                "feature should pass when karate.env was set via protocol.runner");
    }

    @Test
    void testKarateFeatureNoTagFilter() {
        // Without a tag filter, every scenario runs
        FeatureResult result = Runner.runFeature(
                "classpath:features/tag-filter.feature",
                new HashMap<>(),
                null,
                null);
        assertFalse(result.isFailed());
        assertEquals(3, result.getScenarioResults().size(),
                "all three scenarios should run when no tags supplied");
    }

    @Test
    void testKarateSetBuilder() {
        KarateSetBuilder builder = karateSet("testVar", s -> "testValue");
        assertNotNull(builder, "Set builder should be created");
    }

    @Test
    void testNameResolver() {
        KarateProtocolBuilder protocol = karateProtocol()
                .nameResolver((req, vars) -> {
                    String method = req.getMethod();
                    String path = req.getPath();
                    return method + " " + path;
                });

        assertNotNull(protocol.build().getNameResolver(), "Name resolver should be set");
    }

    @Test
    void testUriPatternBuilder() {
        // Test nil() pattern
        KarateUriPattern nilPattern = uri("/api/health").nil();
        assertEquals("/api/health", nilPattern.getPattern());
        assertEquals(0, nilPattern.getPauseFor("get"));
        assertEquals(0, nilPattern.getPauseFor("post"));

        // Test pauseFor() pattern
        KarateUriPattern pausePattern = uri("/api/users")
                .pauseFor(method("get", 100), method("post", 200))
                .build();
        assertEquals("/api/users", pausePattern.getPattern());
        assertEquals(100, pausePattern.getPauseFor("get"));
        assertEquals(200, pausePattern.getPauseFor("post"));
        assertEquals(0, pausePattern.getPauseFor("delete"));
    }

    @Test
    void testMethodPause() {
        MethodPause mp = method("get", 50);
        assertEquals("GET", mp.method());  // Method is uppercased
        assertEquals(50, mp.pauseMillis());
    }

}

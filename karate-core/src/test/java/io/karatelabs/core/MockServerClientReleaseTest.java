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
package io.karatelabs.core;

import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mock server's per-feature runtimes must hand back their HTTP clients when it stops.
 *
 * <p>This was the fifth unreleased path, found by review after four others had been fixed, and it
 * is unlike the others: these runtimes are cached on the handler and live as long as the server,
 * so the scenario-teardown release never reaches them at all. The client only builds a real
 * transport if the mock makes outbound requests ({@code karate.proceed}, proxy mocks), which
 * bounds the damage — but it is one per feature per handler, and a dev-mode session mints a whole
 * new handler on every file save.
 *
 * <p>Asserted through the runtime's own released flag rather than a counting factory, because
 * {@code MockHandler} builds its runtimes with the default factory and takes no injection point.
 */
class MockServerClientReleaseTest {

    @SuppressWarnings("unchecked")
    private static Map<Feature, ScenarioRuntime> runtimesOf(MockHandler handler) throws Exception {
        Field field = MockHandler.class.getDeclaredField("runtimes");
        field.setAccessible(true);
        return (Map<Feature, ScenarioRuntime>) field.get(handler);
    }

    private static boolean released(ScenarioRuntime runtime) throws Exception {
        Field field = ScenarioRuntime.class.getDeclaredField("httpClientReleased");
        field.setAccessible(true);
        return field.getBoolean(runtime);
    }

    @Test
    void testStoppingAMockServerReleasesItsFeatureRuntimeClients() throws Exception {
        MockServer server = MockServer.featureString("""
                Feature: mock

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """).port(0).start();

        Map<Feature, ScenarioRuntime> runtimes = runtimesOf(server.getHandler());
        assertFalse(runtimes.isEmpty(), "no cached runtime — this test would prove nothing");
        for (ScenarioRuntime runtime : runtimes.values()) {
            assertFalse(released(runtime), "not released while the server is still up");
        }

        server.stopAndWait();

        for (ScenarioRuntime runtime : runtimes.values()) {
            assertTrue(released(runtime),
                    "stopping the server must hand back the clients its feature runtimes hold");
        }
    }

    /** Stopping twice must not double-release; the flag makes it idempotent. */
    @Test
    void testStoppingTwiceIsHarmless() throws Exception {
        MockServer server = MockServer.featureString("""
                Feature: mock

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """).port(0).start();
        Map<Feature, ScenarioRuntime> runtimes = runtimesOf(server.getHandler());
        server.stopAndWait();
        server.stopAndWait();
        for (ScenarioRuntime runtime : runtimes.values()) {
            assertTrue(released(runtime));
        }
    }
}

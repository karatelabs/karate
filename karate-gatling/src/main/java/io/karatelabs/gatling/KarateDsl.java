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

import io.gatling.javaapi.core.Session;

import java.util.function.Function;

/**
 * Main DSL entry point for Karate-Gatling integration.
 * Import this class statically in your simulation for a fluent API.
 *
 * <p>Example usage:
 * <pre>
 * import static io.karatelabs.gatling.KarateDsl.*;
 *
 * public class MySimulation extends Simulation {
 *
 *     KarateProtocolBuilder protocol = karateProtocol(
 *         uri("/cats/{id}").nil(),
 *         uri("/cats").pauseFor(method("get", 10), method("post", 20))
 *     );
 *
 *     ScenarioBuilder scenario = scenario("CRUD Operations")
 *         .exec(karateFeature("classpath:features/cats.feature"));
 *
 *     {
 *         setUp(scenario.injectOpen(rampUsers(10).during(10)))
 *             .protocols(protocol);
 *     }
 * }
 * </pre>
 */
public final class KarateDsl {

    private KarateDsl() {
        // Utility class
    }

    // ========== URI Pattern Builders ==========

    /**
     * Create a URI pattern builder.
     * URI patterns are used for request naming and pause configuration.
     *
     * @param pattern the URI pattern (e.g., "/users/{id}")
     * @return a URI pattern builder
     */
    public static KarateUriPattern.Builder uri(String pattern) {
        return KarateUriPattern.uri(pattern);
    }

    /**
     * Create a method pause configuration.
     *
     * @param method the HTTP method (GET, POST, etc.)
     * @param pauseMillis the pause duration in milliseconds after requests
     * @return a method pause configuration
     */
    public static MethodPause method(String method, int pauseMillis) {
        return new MethodPause(method, pauseMillis);
    }

    // ========== Protocol Builder ==========

    /**
     * Create a Karate protocol builder with the given URI patterns.
     *
     * @param patterns the URI patterns with pause configurations
     * @return a protocol builder
     */
    public static KarateProtocolBuilder karateProtocol(KarateUriPattern... patterns) {
        return new KarateProtocolBuilder(patterns);
    }

    /**
     * Create a Karate protocol builder with no URI patterns.
     *
     * @return a protocol builder
     */
    public static KarateProtocolBuilder karateProtocol() {
        return new KarateProtocolBuilder();
    }

    // ========== Feature Execution ==========

    /**
     * Create a feature execution builder.
     *
     * @param paths the feature file paths (e.g., "classpath:features/cats.feature")
     * @return a feature builder
     */
    public static KarateFeatureBuilder karateFeature(String... paths) {
        return new KarateFeatureBuilder(paths);
    }

    // ========== Session Variable Injection ==========

    /**
     * Create an action that sets a session variable for subsequent Karate features.
     * The variable will be available in Karate as __gatling.&lt;key&gt;.
     *
     * @param key the variable name
     * @param valueSupplier function to compute the value from the session
     * @return an action builder
     */
    public static KarateSetBuilder karateSet(String key, Function<Session, Object> valueSupplier) {
        return new KarateSetBuilder(key, valueSupplier);
    }

}

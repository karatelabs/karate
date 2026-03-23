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

import io.gatling.javaapi.core.ActionBuilder;
import io.gatling.javaapi.core.Session;

import java.util.function.Function;

/**
 * Builder for setting session variables for Karate features.
 * Variables set with this builder are available in Karate as __gatling.&lt;key&gt;.
 *
 * <p>Example usage:
 * <pre>
 * scenario("Test")
 *     .feed(feeder)
 *     .exec(karateSet("userId", s -&gt; s.getInt("id")))
 *     .exec(karateFeature("classpath:features/user.feature"))
 * </pre>
 */
public final class KarateSetBuilder implements ActionBuilder {

    private final String key;
    private final Function<Session, Object> valueSupplier;

    /**
     * Create a builder that sets a session variable.
     *
     * @param key the variable name
     * @param valueSupplier function to compute the value from the session
     */
    public KarateSetBuilder(String key, Function<Session, Object> valueSupplier) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key cannot be null or blank");
        }
        if (valueSupplier == null) {
            throw new IllegalArgumentException("valueSupplier cannot be null");
        }
        this.key = key;
        this.valueSupplier = valueSupplier;
    }

    /**
     * Convert to a session function for use with Gatling's exec().
     */
    public Function<Session, Session> toSessionFunction() {
        KarateSetAction action = new KarateSetAction(key, valueSupplier);
        return action.toSessionFunction();
    }

    @Override
    public io.gatling.core.action.builder.ActionBuilder asScala() {
        // Use Gatling's session hook approach
        Function<Session, Session> sessionFunc = toSessionFunction();
        // Convert Java function to Scala function that returns Validation[Session]
        scala.Function1<io.gatling.core.session.Session, io.gatling.commons.validation.Validation<io.gatling.core.session.Session>> scalaFunc =
                scalaSession -> {
                    Session javaSession = new Session(scalaSession);
                    Session result = sessionFunc.apply(javaSession);
                    return new io.gatling.commons.validation.Success<>(result.asScala());
                };
        return new io.gatling.core.action.builder.SessionHookBuilder(scalaFunc, true);
    }

}

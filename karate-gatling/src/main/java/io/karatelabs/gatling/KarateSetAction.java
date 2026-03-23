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
 * Encapsulates the logic for setting a session variable.
 * The variable will be available in Karate features as __gatling.&lt;key&gt;.
 */
public class KarateSetAction {

    private final String key;
    private final Function<Session, Object> valueSupplier;

    public KarateSetAction(String key, Function<Session, Object> valueSupplier) {
        this.key = key;
        this.valueSupplier = valueSupplier;
    }

    /**
     * Create a session function that sets the variable.
     */
    public Function<Session, Session> toSessionFunction() {
        return session -> {
            Object value = valueSupplier.apply(session);
            return session.set(key, value);
        };
    }

}

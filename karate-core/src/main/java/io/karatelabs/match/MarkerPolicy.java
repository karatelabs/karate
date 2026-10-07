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
package io.karatelabs.match;

/**
 * Decides, marker by marker, whether an expected-side marker ({@code #(..)}, {@code ##(..)},
 * {@code #[] ..}, {@code #? ..}) may run in the caller's engine. One that may not runs in a fresh
 * engine that sees no variables. The decision is made where the marker is evaluated, so it also
 * covers a marker that only exists because evaluating another marker produced it.
 */
@FunctionalInterface
public interface MarkerPolicy {

    boolean trusted(String marker);

    /** Wraps a failure thrown by a marker the policy sent to the fresh engine. */
    default RuntimeException untrustedFailure(RuntimeException e) {
        return e;
    }

}

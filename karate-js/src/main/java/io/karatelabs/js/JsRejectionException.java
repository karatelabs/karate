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
package io.karatelabs.js;

/**
 * The carrier that takes a JS promise rejection across the Java boundary, and
 * the <b>only</b> exception type the engine ever unwraps back into a JS reason.
 * {@link JsPromise#toFuture()} completes exceptionally with one of these, so a
 * Java consumer sees it per {@code CompletableFuture} norms — usually nested
 * inside a {@code CompletionException} or {@code ExecutionException}.
 * <p>
 * Coming the other way, an arbitrary Java exception never masquerades as a JS
 * rejection: a stage that failed with something else is converted by the same
 * host-error rules a {@code catch} clause applies, and host cancellation
 * ({@link EngineInterruptedException}) is re-thrown rather than turned into a
 * catchable reason.
 */
public class JsRejectionException extends RuntimeException {

    private final transient Object reason;

    public JsRejectionException(Object reason) {
        super(describe(reason), reason instanceof JsError je ? je.getJavaCause() : null);
        this.reason = reason;
    }

    private static String describe(Object reason) {
        if (reason instanceof JsError je) {
            return je.toString();
        }
        return String.valueOf(reason);
    }

    /** The exact JS value the promise was rejected with — an {@code Error}
     *  object in the common case, but JS can reject with anything. */
    public Object getReason() {
        return reason;
    }

}

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

import io.karatelabs.parser.AsiHint;
import io.karatelabs.parser.Node;

/**
 * Java exception carrier for a JS-side {@link JsError} value.
 * <p>
 * Engine code throws {@code JsErrorException.typeError("…")} (or the other
 * factories); the catch boundaries — {@code Interpreter.evalTryStmt} for JS
 * {@code catch}, {@code Interpreter.evalStatement} / {@code Engine.eval} for
 * the host — read the {@link #payload} directly. The payload is a plain
 * {@link JsError} stamped with the right {@link JsErrorPrototype}, so spec
 * identity ({@code .name}, {@code .message}, {@code .constructor}, prototype
 * chain, {@code instanceof TypeError}) all flow through the standard prototype
 * chain — no post-hoc wiring.
 * <p>
 * The Java cause is preserved via {@link Throwable#getCause()} so IDEs (e.g.
 * IntelliJ) can hyperlink the underlying stack trace when an engine bug
 * surfaces a Java throwable that was wrapped via {@link #wrap(Throwable)}.
 */
class JsErrorException extends RuntimeException {

    final JsError payload;

    JsErrorException(JsError payload) {
        super(javaMessage(payload), payload.getJavaCause());
        this.payload = payload;
    }

    private static String javaMessage(JsError payload) {
        return payload.toString();
    }

    private static final byte MESSAGE_ATTRS = JsObject.WRITABLE | JsObject.CONFIGURABLE;

    private static JsError build(JsErrorPrototype proto, String message, Throwable cause) {
        JsError e = new JsError(proto, cause);
        if (message != null) {
            e.defineOwn("message", message, MESSAGE_ATTRS);
        }
        return e;
    }

    static JsErrorException error(String message) {
        return new JsErrorException(build(JsErrorPrototype.ERROR, message, null));
    }

    static JsErrorException typeError(String message) {
        return new JsErrorException(build(JsErrorPrototype.TYPE_ERROR, message, null));
    }

    static JsErrorException rangeError(String message) {
        return new JsErrorException(build(JsErrorPrototype.RANGE_ERROR, message, null));
    }

    /**
     * The RangeError a Java {@link StackOverflowError} becomes. Built on an almost
     * exhausted stack, so {@link Interpreter}'s class init builds one first — see there.
     */
    static JsErrorException stackExceeded() {
        return rangeError(STACK_EXCEEDED);
    }

    static final String STACK_EXCEEDED = "Maximum call stack size exceeded";

    /** The host-facing form, for a boundary that converts without headroom: Engine.eval, a host call. */
    static EngineException stackExceededAtBoundary() {
        JsErrorException jex = stackExceeded();
        return new EngineException(jex.getMessage(), jex, "RangeError", STACK_EXCEEDED);
    }

    /**
     * The RangeError for a {@link StackOverflowError} caught at a JS seam — but only once the
     * stack has unwound far enough for the JS {@code catch} that follows to do first-use work
     * (a class init, an invokedynamic link), which is not retryable once it has overflowed.
     * Until then the raw error is rethrown to the next seam out, cutting the frames between.
     */
    static JsErrorException stackExceeded(StackOverflowError e) {
        if (e == failed && ++seamsSinceFailure < SEAMS_BETWEEN_PROBES) {
            throw e;
        }
        if (!hasHeadroom()) {
            failed = e;
            seamsSinceFailure = 0;
            throw e;
        }
        failed = null;
        return stackExceeded();
    }

    // ~94 KB compiled (144 bytes a level), ~2.7x that interpreted: 2.2x the largest measured
    // first-use need (a class init reaching JsonPath or Logback, 43 KB)
    private static final int HEADROOM_LEVELS = 650;
    // a failed probe costs a StackOverflowError with its trace, so the same error is let
    // through this many seams before the next one; a race between threads only moves a probe
    private static final int SEAMS_BETWEEN_PROBES = 16;
    private static StackOverflowError failed;
    private static int seamsSinceFailure;
    private static final long[] SEED = new long[16];
    private static volatile long sink;

    static {
        for (int i = 0; i < SEED.length; i++) {
            SEED[i] = 0x9E3779B97F4A7C15L * (i + 1);
        }
    }

    private static boolean hasHeadroom() {
        try {
            sink = probe(HEADROOM_LEVELS);
            return true;
        } catch (StackOverflowError e) {
            return false;
        }
    }

    // sixteen longs live across the call, so a compiled frame spills them and stays ~144 bytes
    private static long probe(int n) {
        long[] s = SEED;
        long a0 = s[0] ^ n, a1 = s[1] ^ n, a2 = s[2] ^ n, a3 = s[3] ^ n;
        long a4 = s[4] ^ n, a5 = s[5] ^ n, a6 = s[6] ^ n, a7 = s[7] ^ n;
        long a8 = s[8] ^ n, a9 = s[9] ^ n, a10 = s[10] ^ n, a11 = s[11] ^ n;
        long a12 = s[12] ^ n, a13 = s[13] ^ n, a14 = s[14] ^ n, a15 = s[15] ^ n;
        long r = n == 0 ? 0 : probe(n - 1);
        return r ^ a0 ^ a1 ^ a2 ^ a3 ^ a4 ^ a5 ^ a6 ^ a7 ^ a8 ^ a9 ^ a10 ^ a11 ^ a12 ^ a13 ^ a14 ^ a15;
    }

    static JsErrorException syntaxError(String message) {
        return new JsErrorException(build(JsErrorPrototype.SYNTAX_ERROR, message, null));
    }

    /**
     * The single shape for calling something that is not callable — the JS idiom, naming the
     * callee's source text rather than an engine node type. When the call's {@code (} begins a
     * line, an omitted {@code ;} on the line before is the likely cause, so say so.
     */
    static JsErrorException notAFunction(Node callee) {
        String hint = AsiHint.forOpener(callee.getLastToken().getNextPrimary());
        return typeError(callee.getTextIncludingWhitespace() + " is not a function"
                + (hint == null ? "" : " — " + hint));
    }

    static JsErrorException referenceError(String message) {
        return new JsErrorException(build(JsErrorPrototype.REFERENCE_ERROR, message, null));
    }

    /**
     * The ReferenceError for reading or writing a {@code let} / {@code const} / {@code class}
     * binding in its temporal dead zone — the one factory for it, so every storage path
     * (slot frame, store, closure capture) throws the same shape.
     */
    static JsErrorException uninitialized(String name) {
        return new JsErrorException(build(JsErrorPrototype.REFERENCE_ERROR,
                "cannot access '" + name + "' before initialization", null));
    }

    /**
     * Wrap a non-{@link JsErrorException} Java throwable as a generic JS
     * {@code Error} so JS {@code catch} clauses see a spec-shape value
     * ({@code e instanceof Error}). The cause is preserved on the Java side
     * for IDE-hyperlinkable stack traces; it is NOT installed as the spec
     * {@code .cause} own property (that carries whatever JS code passed).
     * Engine code that intends a specific JS error type should use the typed
     * factories above rather than letting a Java exception leak — this wrap
     * is the safety net for genuinely unexpected throws.
     */
    static JsErrorException wrap(Throwable cause) {
        String message = cause.getMessage() != null
                ? cause.getMessage()
                : cause.getClass().getSimpleName();
        return new JsErrorException(build(JsErrorPrototype.ERROR, message, cause));
    }

}

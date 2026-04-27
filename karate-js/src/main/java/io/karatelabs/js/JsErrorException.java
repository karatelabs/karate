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

    static JsErrorException syntaxError(String message) {
        return new JsErrorException(build(JsErrorPrototype.SYNTAX_ERROR, message, null));
    }

    static JsErrorException referenceError(String message) {
        return new JsErrorException(build(JsErrorPrototype.REFERENCE_ERROR, message, null));
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

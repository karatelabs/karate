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
 * Engine code throws {@code JsErrorException.typeError("...")} instead of a
 * raw {@code RuntimeException} with a parseable {@code "TypeError: ..."}
 * prefix. The catch boundaries ({@code Interpreter.evalTryStmt} for JS
 * {@code catch}, {@code Interpreter.evalStatement} / {@code Engine.eval} for
 * the host) read the payload directly — no message-prefix parsing.
 * <p>
 * The {@link JsError} payload carries the JS-visible identity
 * ({@code .name}, {@code .message}, prototype chain). {@code .constructor}
 * is wired from the registered global at the JS-catch boundary, since the
 * throw site typically does not hold a {@code Context}.
 */
class JsErrorException extends RuntimeException {

    final JsError payload;

    JsErrorException(JsError payload) {
        super(javaMessage(payload));
        this.payload = payload;
    }

    JsErrorException(JsError payload, Throwable cause) {
        super(javaMessage(payload), cause);
        this.payload = payload;
    }

    private static String javaMessage(JsError payload) {
        String name = payload.getName();
        String msg = payload.getMessageString();
        if (msg == null || msg.isEmpty()) return name;
        return name + ": " + msg;
    }

    static JsErrorException typeError(String message) {
        return new JsErrorException(new JsError(message, "TypeError", null));
    }

    static JsErrorException referenceError(String message) {
        return new JsErrorException(new JsError(message, "ReferenceError", null));
    }

    static JsErrorException rangeError(String message) {
        return new JsErrorException(new JsError(message, "RangeError", null));
    }

    static JsErrorException syntaxError(String message) {
        return new JsErrorException(new JsError(message, "SyntaxError", null));
    }

    static JsErrorException error(String message) {
        return new JsErrorException(new JsError(message, "Error", null));
    }

}

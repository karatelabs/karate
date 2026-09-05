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
package io.karatelabs.js;

public class EngineException extends RuntimeException {

    private final String jsErrorName;
    private final String jsMessage;
    private final boolean authoredThrow;
    private final Object thrownValue;
    private final int throwLine;

    public EngineException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * @param jsErrorName canonical JS error constructor name
     *                    ("TypeError" | "ReferenceError" | "RangeError" | "SyntaxError" |
     *                     "URIError" | "EvalError" | "Error" | null if non-JS origin)
     */
    public EngineException(String message, Throwable cause, String jsErrorName) {
        this(message, cause, jsErrorName, null);
    }

    /**
     * @param jsErrorName canonical JS error constructor name (see above)
     * @param jsMessage   the raw JS-side {@code .message} value — what {@code e.message}
     *                    inside a JS {@code catch} would observe. Distinct from
     *                    {@link #getMessage()}, which carries the host-facing
     *                    {@code "js failed: / Code: / Error: ..."} frame for logs.
     *                    Pass {@code null} when no JS-origin message is available.
     */
    public EngineException(String message, Throwable cause, String jsErrorName, String jsMessage) {
        this(message, cause, jsErrorName, jsMessage, false, null, 0);
    }

    /**
     * @param authoredThrow true only when a {@code throw} statement the script author wrote is the
     *                      completion that escaped — never for an engine-raised error
     * @param thrownValue   the JS value that {@code throw} carried ({@code null} when not authored)
     * @param throwLine     the 1-based line of that {@code throw} statement ({@code 0} when not authored)
     */
    public EngineException(String message, Throwable cause, String jsErrorName, String jsMessage,
                           boolean authoredThrow, Object thrownValue, int throwLine) {
        super(message, cause);
        this.jsErrorName = jsErrorName;
        this.jsMessage = jsMessage;
        this.authoredThrow = authoredThrow;
        this.thrownValue = thrownValue;
        this.throwLine = throwLine;
    }

    /**
     * @return the JS error constructor name when this exception originated from
     *         an uncaught JS {@code throw}; {@code null} for Java-origin errors
     *         (the caller can still inspect {@link #getMessage()} or the cause chain).
     */
    public String getJsErrorName() {
        return jsErrorName;
    }

    /**
     * @return the unframed JS-side message (what {@code e.message} would be inside
     *         a JS {@code catch}), or {@code null} when the exception has no
     *         JS-origin message. Callers building a JS-facing surface should
     *         prefer this over {@link #getMessage()}, which intentionally carries
     *         host-side framing (file/line/column) for logging.
     */
    public String getJsMessage() {
        return jsMessage;
    }

    /**
     * @return true when a {@code throw} statement in the script is what escaped — the discriminator
     *         is the statement reached, never the thrown value's type ({@code throw 'x'} and
     *         {@code throw new Error('x')} are both authored, a {@code TypeError} the engine raised
     *         is not)
     */
    public boolean isAuthoredThrow() {
        return authoredThrow;
    }

    /** @return the JS value the authored {@code throw} carried, or {@code null} when not authored. */
    public Object getThrownValue() {
        return thrownValue;
    }

    /** @return the 1-based line of the authored {@code throw} statement, or {@code 0} when not authored. */
    public int getThrowLine() {
        return throwLine;
    }

}

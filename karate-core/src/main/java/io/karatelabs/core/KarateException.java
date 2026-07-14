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
package io.karatelabs.core;

/**
 * The exception surfaced to callers (JUnit / surefire, the fluent {@code Runner} API, any
 * code that catches a Karate failure) when a step fails. It exists to keep the failing
 * <b>feature-file location</b> attached to the {@link Throwable} itself — not just in the
 * console summary — so a raw stack dump still points the reader at the offending
 * {@code .feature:line} instead of Karate/HTTP-client internals.
 *
 * <p>Built by {@link #forStep} at the point a failure escapes to the user (see
 * {@code ScenarioResult.getErrorWithLocation()}), which:</p>
 * <ul>
 *   <li>appends the location to the message on its own line, in the exact
 *       {@code path.feature:line} shape the IDE console filter hyperlinks; and</li>
 *   <li>prepends a synthetic {@code <feature>} stack frame while <b>preserving the
 *       original frames below it</b>, so the real origin in the codebase (e.g. a socket
 *       read timeout deep in the HTTP client) is not lost.</li>
 * </ul>
 *
 * <p>The undecorated message stays available via {@link #getRawMessage()} for consumers
 * (reports, console summary) that render the location separately and don't want it twice.</p>
 */
public class KarateException extends RuntimeException {

    private final String rawMessage;

    private KarateException(String rawMessage, String decoratedMessage) {
        super(decoratedMessage);
        this.rawMessage = rawMessage;
    }

    /** The original error message, without the appended {@code path.feature:line} location. */
    public String getRawMessage() {
        return rawMessage;
    }

    /**
     * Wrap {@code error} so the feature-file location travels with the Throwable.
     * Idempotent — an already-wrapped {@link KarateException} is returned unchanged so a
     * failure that bubbles through nested {@code call}s is not decorated repeatedly.
     *
     * @param error        the raw step failure
     * @param featurePath  the feature file path, without the trailing {@code :line}
     * @param line         the 1-indexed line of the failing step
     * @param stepText     the offending Gherkin source line (e.g. {@code "* match a == 1"}), for the synthetic frame
     */
    public static KarateException forStep(Throwable error, String featurePath, int line, String stepText) {
        if (error instanceof KarateException ke) {
            return ke;
        }
        String message = error.getMessage();
        if (message == null) {
            message = error.toString();
        }
        String location = featurePath + ":" + line;
        // location on its own trailing line — matches the IDE console filter's
        // "^\s*(\S.*\.feature:\d+)$" so it stays click-to-navigate in a raw stack dump
        KarateException wrapper = new KarateException(message, message + "\n" + location);
        // synthetic <feature> frame on top of the real frames, which are preserved so the
        // in-code origin of the failure (HTTP client, match engine, etc.) is still visible
        StackTraceElement featureFrame = new StackTraceElement(
                "<feature>", stepText == null ? "" : stepText, featurePath, line);
        StackTraceElement[] original = error.getStackTrace();
        StackTraceElement[] merged = new StackTraceElement[original.length + 1];
        merged[0] = featureFrame;
        System.arraycopy(original, 0, merged, 1, original.length);
        wrapper.setStackTrace(merged);
        return wrapper;
    }

}

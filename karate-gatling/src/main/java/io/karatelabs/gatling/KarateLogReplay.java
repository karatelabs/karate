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

/**
 * How much Karate output to replay when a feature fails under Gatling.
 * <p>
 * A load run is normally configured at a high Logback level, so the per-step output — HTTP
 * request / response blocks and {@code print} statements — never reaches the log. Karate captures
 * it anyway (the report buffer has its own threshold), so it can be held and replayed at a level
 * that <em>does</em> get through, but only for the runs that actually failed.
 *
 * @see KarateProtocolBuilder#logReplay(KarateLogReplay)
 */
public enum KarateLogReplay {

    /** Do not replay anything (default). Only the failure summary is logged. */
    OFF,

    /** Replay the failing feature's own output. Nothing is retained between features. */
    FAILED,

    /**
     * Replay the output of the features that already passed for this virtual user as well, oldest
     * first, then the failing one. Retention is bounded by
     * {@link KarateProtocolBuilder#logReplayLimit(int)} and is cleared after every replay.
     */
    ALL;

    /**
     * Parse a mode by name, case-insensitively.
     *
     * @throws IllegalArgumentException if the name is not one of {@code off}, {@code failed}, {@code all}
     */
    public static KarateLogReplay fromString(String value) {
        if (value == null) {
            return OFF;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid log replay mode: '" + value
                    + "', expected one of: off, failed, all");
        }
    }
}

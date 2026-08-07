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
package io.karatelabs.profiling.gatling;

import io.gatling.javaapi.core.Simulation;
import io.karatelabs.profiling.Payload;

/**
 * The body-size tier: the same parity pair, run against a response of a chosen size.
 *
 * <p><b>Why it exists.</b> Every figure this project publishes was measured against a response of
 * about 34 bytes. Real APIs return kilobytes. The open question is not the level of the deficit but
 * whether it <b>scales</b>: if Karate's per-iteration cost grows with body size faster than plain
 * Gatling's, "+1.89 ms/iteration" is a property of that tiny payload and every number needs the
 * qualifier. If both scale alike, the figure survives as measured.
 *
 * <p><b>It is a parity matrix like any other</b> — same {@code compare}, same alternating arm
 * order, same tiers. Only the payload varies, set with {@code --body-size}. Run the same size
 * against both arms, then run several sizes and read the trend; a single size answers nothing,
 * because the question is a slope.
 *
 * <ul>
 *   <li>{@code gatling-body-karate} — closed {@code match} over the whole document, padding included
 *   <li>{@code gatling-body-plain} — the reference: two JSONPath checks, which parse the whole
 *       document but never compare the pad
 *   <li>{@code gatling-body-plain-fat} — the control that raises the reference to check the pad too
 * </ul>
 *
 * <p>The control is what makes a rising deficit attributable — but be precise about what it
 * separates. Both arms parse the whole padded document, because Gatling's {@code jsonPath} does
 * too. The control adds one string comparison of the pad, so the fat-minus-plain delta prices
 * comparing a large field, and the karate-minus-plain delta prices Karate's build-and-deep-match
 * against Gatling's parse-and-extract. Neither delta is "reads the bytes" versus "skips them".
 *
 * <p>{@code compare} classifies an arm by whether the directory name contains {@code -karate-} or
 * {@code -plain-}, and buckets on the recorded body size, so these slot in without it knowing they
 * exist — and a 1 KB matrix cannot be averaged together with a 64 KB one.
 */
public final class HttpBodyWorkload {

    private HttpBodyWorkload() {
    }

    private static String sized(String what) {
        int bytes = Payload.bodyBytes();
        return what + (bytes > 0 ? " Body size " + bytes + " bytes (--body-size)." : "");
    }

    /** Karate matching the whole padded document. Pairs against {@code gatling-body-plain}. */
    public static final class Karate extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-body-karate";
        }

        @Override
        public String describe() {
            return sized("Body-size tier: Karate's closed match over a response of a chosen size, "
                    + "padding included. Pair with gatling-body-plain and vary --body-size — the "
                    + "trend across sizes is the measurement, not any one cell.");
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        public boolean requiresBodySize() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpBodyKarateSimulation.class;
        }
    }

    /** The reference arm, reading three small fields. Pairs against {@code gatling-body-karate}. */
    public static final class Plain extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-body-plain";
        }

        @Override
        public String describe() {
            return sized("Body-size tier: the plain-Gatling reference against the same payload. "
                    + "Its jsonPath checks parse the whole document but never compare the pad.");
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        public boolean requiresBodySize() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpBodyPlainSimulation.class;
        }
    }

    /** The reference raised to check the padding as well. Pairs against {@code gatling-body-karate}. */
    public static final class PlainFat extends GatlingWorkload {

        @Override
        public String name() {
            return "gatling-body-plain-fat";
        }

        @Override
        public String describe() {
            return sized("Body-size tier equivalence control: the plain arm checking the padding "
                    + "too, so a rising deficit can be told apart from the cost of checking a "
                    + "large field. Pair with gatling-body-karate.");
        }

        @Override
        public boolean needsMock() {
            return true;
        }

        @Override
        public boolean requiresBodySize() {
            return true;
        }

        @Override
        protected Class<? extends Simulation> simulation() {
            return HttpBodyPlainFatSimulation.class;
        }
    }

}

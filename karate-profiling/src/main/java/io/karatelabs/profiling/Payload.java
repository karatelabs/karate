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
package io.karatelabs.profiling;

/**
 * The request body for the body-size tier, and the one knob that varies it.
 *
 * <p><b>What the tier is for.</b> Every parity figure this project publishes was measured against
 * a response of about 34 bytes — {@code {"name":"Billie","age":5,"id":417}}. Real APIs return
 * kilobytes. The question is not what the deficit <i>is</i> at one payload but whether it
 * <b>scales</b> with payload: if Karate's per-iteration cost grows with body size faster than plain
 * Gatling's, then "+1.89 ms/iteration" is a property of that 34-byte response and every published
 * number needs the qualifier. If both arms scale alike, the figure survives as measured. Either
 * answer is worth having; today neither can be given.
 *
 * <p><b>Why the mock needs no change.</b> {@code LatencyMock} echoes the posted object back with an
 * {@code id} spliced in, so response size is set entirely by request size. Growing the request
 * grows the response, and the mock's request path stays allocation-free.
 *
 * <p><b>What each arm does with it.</b> The Karate arm's closed {@code match response == { ... }}
 * deep-compares the padding; the plain arm's JSONPath checks read three small fields and never
 * touch it. That difference is not a flaw in the comparison — it <i>is</i> the comparison, and it
 * is the idiomatic form of each. {@code gatling-body-plain-fat} raises the plain arm to check the
 * padding too, which brackets how much of any gap is the assertion rather than the transport.
 */
public final class Payload {

    /** Set by {@code --body-size}; absent means the tier is not in use. */
    public static final String BODY_BYTES_PROPERTY = "karate.profiling.bodyBytes";

    /**
     * The body both arms send when no size is requested, and the exact shape the 34-byte tier has
     * always used. Kept as a separate constant rather than {@code pad("")}: the default arms must
     * stay byte-identical to what produced every published number, and a padded body carries a
     * {@code "pad"} key that this one does not.
     */
    private static final String UNPADDED = "{\"name\":\"Billie\",\"age\":5}";

    /** One character of padding, repeated. Ordinary text, so no encoder does anything clever. */
    private static final char FILLER = 'x';

    private static final String PREFIX = "{\"name\":\"Billie\",\"age\":5,\"pad\":\"";
    private static final String SUFFIX = "\"}";

    private Payload() {
    }

    /** The requested body size in bytes, or 0 when the tier is off. */
    public static int bodyBytes() {
        return Math.max(0, Integer.getInteger(BODY_BYTES_PROPERTY, 0));
    }

    /**
     * The padding string alone, for the Karate arm — its feature builds the JSON itself, which is
     * work the plain arm's raw string would hide.
     *
     * <p>Empty when the tier is off, so the feature that interpolates it is not accidentally
     * measuring a one-character body.
     */
    public static String pad() {
        int target = bodyBytes();
        if (target <= 0) {
            return "";
        }
        // The mock appends `,"id":N` to whatever it echoes, so the response runs a little past the
        // request. Not corrected for: the id is ~10 bytes against a tier measured in kilobytes, and
        // a size that shifts with the id counter would be worse than one that is honestly
        // approximate. The digest records the number that was asked for.
        int padding = target - PREFIX.length() - SUFFIX.length();
        return String.valueOf(FILLER).repeat(Math.max(1, padding));
    }

    /**
     * The whole request body, for the plain arm, which posts a literal string.
     *
     * <p>Byte-identical to what the Karate feature builds from the same padding — both arms must
     * put the same number of bytes on the wire or the tier measures the payload difference instead
     * of the handling difference.
     */
    public static String requestBody() {
        return bodyBytes() <= 0 ? UNPADDED : PREFIX + pad() + SUFFIX;
    }

}

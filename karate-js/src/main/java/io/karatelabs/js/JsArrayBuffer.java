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
 * JavaScript ArrayBuffer instance: fixed-length bytes that typed arrays and
 * DataViews view. A {@code byte[]} host value presents as a Uint8Array over its
 * own bytes, so {@link #wrap} gives it a buffer sharing those bytes.
 */
class JsArrayBuffer extends JsObject {

    final byte[] data;

    JsArrayBuffer(byte[] data) {
        super(null, JsArrayBufferPrototype.INSTANCE);
        this.data = data;
    }

    static JsArrayBuffer wrap(byte[] data) {
        return new JsArrayBuffer(data);
    }

    /** Spec ToIndex (§7.1.22): undefined is 0; otherwise an integer in [0, 2^53-1], else {@code error}. */
    static long toIndex(Object value, CoreContext context, String error) {
        if (value == null || value == Terms.UNDEFINED) {
            return 0;
        }
        double d = Terms.toNumberCoerce(value, context).doubleValue();
        double integer = Double.isNaN(d) ? 0 : (d < 0 ? Math.ceil(d) : Math.floor(d));
        if (integer < 0 || integer > 9007199254740991d) {
            throw JsErrorException.rangeError(error);
        }
        return (long) integer;
    }

    /** Spec relative index (begin / end of slice, subarray, fill): negative counts from {@code length}. */
    static int relativeIndex(Object value, int length, int dflt, CoreContext context) {
        if (value == null || value == Terms.UNDEFINED) {
            return dflt;
        }
        double d = Terms.toNumberCoerce(value, context).doubleValue();
        if (Double.isNaN(d)) {
            return 0;
        }
        double integer = d < 0 ? Math.ceil(d) : Math.floor(d);
        if (integer < 0) {
            return (int) Math.max(0, length + integer);
        }
        return (int) Math.min(integer, length);
    }

}

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

import java.util.Arrays;

/**
 * A Uint8Array: the Java-interop form of binary data. A {@code byte[]} value is a
 * Uint8Array over its own bytes and is what crosses back to Java; a view of part of
 * an ArrayBuffer crosses as a copy of its bytes.
 */
non-sealed class JsUint8Array extends JsTypedArray implements JsBinaryValue {

    JsUint8Array(int length) {
        this(new byte[length]);
    }

    /** A view over {@code bytes} itself: the engine wraps a byte[] value for each member
     *  access, so a mutating method must reach the array the value is. */
    JsUint8Array(byte[] bytes) {
        super(Kind.UINT8, bytes, 0, bytes.length, null);
    }

    JsUint8Array(byte[] bytes, int byteOffset, int length, JsArrayBuffer buffer) {
        super(Kind.UINT8, bytes, byteOffset, length, buffer);
    }

    @Override
    public Object getJavaValue() {
        return byteOffset == 0 && length == bytes.length ? bytes : Arrays.copyOfRange(bytes, byteOffset, byteOffset + length);
    }

}

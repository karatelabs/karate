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

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * The Web Crypto {@code crypto} global's two synchronous members, which is what
 * published libraries probe for an entropy source ({@code uuid}, {@code crypto-js}):
 * {@code getRandomValues(typedArray)} fills the array in place and returns it;
 * {@code randomUUID()} is a v4 UUID string.
 */
class JsCrypto extends JsObject {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected Object resolveOwnIntrinsic(String name) {
        return switch (name) {
            case "getRandomValues" -> (JsInvokable) args -> {
                Object array = args.length > 0 ? args[0] : null;
                JsTypedArray view = array instanceof byte[] b ? new JsUint8Array(b)
                        : array instanceof JsTypedArray t && t.kind.isInteger() ? t : null;
                if (view == null) {
                    throw JsErrorException.typeError("The \"typedArray\" argument must be an instance of Int8Array,"
                            + " Int16Array, Int32Array, Uint8Array, Uint16Array, Uint32Array, or Uint8ClampedArray.");
                }
                byte[] random = new byte[view.byteLength()];
                RANDOM.nextBytes(random);
                System.arraycopy(random, 0, view.bytes, view.byteOffset, random.length);
                return array;
            };
            case "randomUUID" -> (JsInvokable) args -> UUID.randomUUID().toString();
            default -> null;
        };
    }

    private static final List<String> INTRINSIC_NAMES = List.of("getRandomValues", "randomUUID");

    @Override
    protected Iterable<String> ownIntrinsicNames() {
        return INTRINSIC_NAMES;
    }

}

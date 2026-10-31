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
                byte[] bytes = array instanceof byte[] b ? b
                        : array instanceof JsUint8Array u ? (byte[]) u.getJavaValue() : null;
                if (bytes == null) {
                    throw JsErrorException.typeError("getRandomValues expects a Uint8Array");
                }
                RANDOM.nextBytes(bytes);
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

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

import java.nio.charset.StandardCharsets;

/**
 * JavaScript TextDecoder for decoding byte arrays to strings.
 */
class JsTextDecoder extends JsObject {

    @Override
    public Object getMember(String name) {
        // Check own properties first
        Object own = super.getMember(name);
        if (own != null) {
            return own;
        }
        // TextDecoder built-in properties and methods
        return switch (name) {
            case "encoding" -> "utf-8";
            case "decode" -> (JsInvokable) args -> {
                if (args.length == 0) {
                    return "";
                }
                if (args[0] instanceof byte[] bytes) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return "";
            };
            default -> null;
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return this;
    }

}

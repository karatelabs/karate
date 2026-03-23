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

/**
 * Sealed interface for JS boxed primitives (Number, String, Boolean objects).
 * <p>
 * These are created via {@code new Number(5)}, {@code new String("x")}, {@code new Boolean(true)}.
 * Unlike primitive values, boxed primitives are objects and always truthy.
 * <p>
 * Permitted implementations:
 * <ul>
 *   <li>{@link JsNumber} - wraps a Number value</li>
 *   <li>{@link JsString} - wraps a String value</li>
 *   <li>{@link JsBoolean} - wraps a boolean value</li>
 * </ul>
 */
sealed interface JsPrimitive extends JsValue permits JsNumber, JsString, JsBoolean {

}

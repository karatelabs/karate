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
 * Sealed interface hierarchy for JS value types that wrap Java values.
 * <p>
 * This hierarchy enables exhaustive pattern matching and ensures type safety
 * when handling JS wrapper types at engine boundaries.
 * <p>
 * Two methods provide access to the underlying value:
 * <ul>
 *   <li>{@link #getJavaValue()} - Returns the idiomatic Java type for external use</li>
 *   <li>{@link #getJsValue()} - Returns the raw internal value for JS operations</li>
 * </ul>
 * <p>
 * Hierarchy:
 * <pre>
 * JsValue (sealed)
 * ├── JsUndefined (singleton)
 * ├── JsPrimitive (sealed) - boxed primitives
 * │   ├── JsNumber
 * │   ├── JsString
 * │   └── JsBoolean
 * ├── JsDateValue (sealed)
 * │   └── JsDate
 * └── JsBinaryValue (sealed)
 *     └── JsUint8Array
 * </pre>
 *
 * @see JsUndefined
 * @see JsPrimitive
 * @see JsDateValue
 * @see JsBinaryValue
 */
public sealed interface JsValue permits JsUndefined, JsPrimitive, JsDateValue, JsBinaryValue {

    /**
     * Returns the idiomatic Java representation of this object.
     * Used when values leave the JS engine and need to be consumed by Java code.
     * <p>
     * Examples: JsNumber → Number, JsDate → Date, JsUint8Array → byte[]
     */
    Object getJavaValue();

    /**
     * Returns the raw internal value for use in JS operations (comparison, arithmetic, etc.).
     * This enables a consistent "unwrap first, then switch on raw types" pattern.
     * <p>
     * Examples: JsNumber → Number, JsDate → Long (millis), JsUint8Array → byte[]
     * <p>
     * Default implementation delegates to {@link #getJavaValue()} which is correct for most types.
     * Override when internal representation differs from external (e.g., JsDate stores long millis).
     */
    default Object getJsValue() {
        return getJavaValue();
    }

}

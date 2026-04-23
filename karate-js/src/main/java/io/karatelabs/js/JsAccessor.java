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
 * Holds getter/setter functions for an accessor property declared in an
 * object literal ({@code {get foo() {...}, set foo(v) {...}}}).
 * <p>
 * Stored as a value in {@link JsObject}'s internal map under the property
 * name. Property-access sites in {@link PropertyAccess} detect this marker:
 * on read, they invoke {@link #getter} with {@code this} bound to the
 * owning object; on write, they invoke {@link #setter}. If only one of the
 * two is defined, the other side of the access is a no-op that returns
 * {@code undefined} (on read) or silently ignores the write.
 * <p>
 * Merging happens in {@link Interpreter#evalLitObject} when a second
 * accessor for the same key is encountered; the existing accessor's other
 * half is preserved (so {@code {get foo(){...}, set foo(v){...}}} keeps
 * both halves on the same {@code JsAccessor}).
 */
class JsAccessor {

    JsCallable getter;
    JsCallable setter;

    JsAccessor(JsCallable getter, JsCallable setter) {
        this.getter = getter;
        this.setter = setter;
    }

}

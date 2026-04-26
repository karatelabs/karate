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
 * Data PropertyDescriptor — ES 6.2.5.2. Holds {@link #value} directly;
 * accessor dispatch is unused. {@link #read} returns the value as-is.
 */
final class DataSlot extends PropertySlot {

    Object value;

    DataSlot(String name) {
        super(name);
    }

    DataSlot(String name, Object value) {
        super(name);
        this.value = value;
    }

    @Override
    boolean isAccessor() {
        return false;
    }

    @Override
    Object read(Object receiver, CoreContext ctx) {
        return value;
    }

    @Override
    void write(Object receiver, Object newValue, CoreContext ctx, boolean strict) {
        if (!isWritable()) {
            if (strict) {
                throw JsErrorException.typeError(
                        "Cannot assign to read only property '" + name + "'");
            }
            return;
        }
        this.value = newValue;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }

}

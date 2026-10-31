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

import java.util.AbstractList;
import java.util.List;

/**
 * Uint32Array over an {@code int[]}: fixed length, every write ToUint32 (§7.1.7), every
 * read the unsigned value. The list view is what the engine's indexed access paths
 * write through, so the coercion lives in {@link Words#set}. Also the constructor global.
 */
class JsUint32Array extends JsArray {

    private static final class Words extends AbstractList<Object> {

        final int[] words;

        Words(int[] words) {
            this.words = words;
        }

        @Override
        public Object get(int index) {
            int w = words[index];
            return w >= 0 ? (Object) w : (Object) (w & 0xFFFFFFFFL);
        }

        @Override
        public Object set(int index, Object value) {
            Object previous = get(index);
            words[index] = (int) Terms.toUint32(value);
            return previous;
        }

        @Override
        public int size() {
            return words.length;
        }

        @Override
        public void add(int index, Object value) {
            throw new UnsupportedOperationException("Uint32Array is fixed-size");
        }

        @Override
        public Object remove(int index) {
            throw new UnsupportedOperationException("Uint32Array is fixed-size");
        }

    }

    JsUint32Array(int[] words) {
        super(new Words(words));
    }

    @Override
    public boolean isConstructable() {
        return true;
    }

    @Override
    public Object call(Context context, Object[] args) {
        Object arg = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (arg instanceof List<?> items) {
            int[] words = new int[items.size()];
            for (int i = 0; i < words.length; i++) {
                words[i] = (int) Terms.toUint32(items.get(i));
            }
            return new JsUint32Array(words);
        }
        double length = arg == Terms.UNDEFINED ? 0 : Terms.toNumberCoerce(arg, null).doubleValue();
        if (length < 0 || length != Math.floor(length) || length > Integer.MAX_VALUE) {
            throw JsErrorException.rangeError("invalid typed array length: " + arg);
        }
        return new JsUint32Array(new int[(int) length]);
    }

}

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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.AbstractList;

/**
 * A typed array: a fixed-length, little-endian view of {@code length} elements of
 * one {@link Kind} over an ArrayBuffer's bytes from {@code byteOffset}. Its
 * elements are the list the inherited {@link JsArray} paths read and write, so
 * every write converts per kind (ToInt8, ToUint8Clamp, ToFloat32, ...) and an
 * index outside the view is ignored. A {@code byte[]} value is a Uint8Array over
 * its own bytes ({@link JsUint8Array}).
 */
class JsTypedArray extends JsArray {

    enum Kind {
        INT8("Int8Array", 1), UINT8("Uint8Array", 1), UINT8_CLAMPED("Uint8ClampedArray", 1),
        INT16("Int16Array", 2), UINT16("Uint16Array", 2), INT32("Int32Array", 4), UINT32("Uint32Array", 4),
        FLOAT32("Float32Array", 4), FLOAT64("Float64Array", 8);

        final String jsName;
        final int size;

        Kind(String jsName, int size) {
            this.jsName = jsName;
            this.size = size;
        }

        boolean isInteger() {
            return this != FLOAT32 && this != FLOAT64;
        }
    }

    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle FLOAT = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle DOUBLE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    final Kind kind;
    final byte[] bytes;
    final int byteOffset;
    final int length;
    private JsArrayBuffer buffer;

    JsTypedArray(Kind kind, byte[] bytes, int byteOffset, int length, JsArrayBuffer buffer) {
        super(new Elements(kind, bytes, byteOffset, length));
        this.kind = kind;
        this.bytes = bytes;
        this.byteOffset = byteOffset;
        this.length = length;
        this.buffer = buffer;
        setPrototype(JsTypedArrayPrototype.of(kind));
    }

    /** A new typed array of {@code kind} owning {@code bytes}: a Uint8Array is the {@code byte[]} itself. */
    static Object fresh(Kind kind, byte[] bytes) {
        return kind == Kind.UINT8 ? bytes : new JsTypedArray(kind, bytes, 0, bytes.length / kind.size, null);
    }

    /** A view of {@code buffer}; a Uint8Array view is a {@link JsUint8Array}. */
    static JsTypedArray view(Kind kind, JsArrayBuffer buffer, int byteOffset, int length) {
        return kind == Kind.UINT8 ? new JsUint8Array(buffer.data, byteOffset, length, buffer)
                : new JsTypedArray(kind, buffer.data, byteOffset, length, buffer);
    }

    JsArrayBuffer buffer() {
        if (buffer == null) {
            buffer = JsArrayBuffer.wrap(bytes);
        }
        return buffer;
    }

    int byteLength() {
        return length * kind.size;
    }

    Object read(int index) {
        return read(kind, bytes, byteOffset + index * kind.size);
    }

    void write(int index, Number value) {
        write(kind, bytes, byteOffset + index * kind.size, value);
    }

    /** Integer-indexed [[Set]]: the value converts first, then an index outside the view is ignored. */
    void setIndexed(int index, Object value, CoreContext context) {
        Number n = Terms.toNumberCoerce(value, context);
        if (index >= 0 && index < length) {
            write(index, n);
        }
    }

    static Object read(Kind kind, byte[] b, int at) {
        return switch (kind) {
            case INT8 -> (int) b[at];
            case UINT8, UINT8_CLAMPED -> b[at] & 0xFF;
            case INT16 -> (int) (short) SHORT.get(b, at);
            case UINT16 -> ((short) SHORT.get(b, at)) & 0xFFFF;
            case INT32 -> (int) INT.get(b, at);
            case UINT32 -> {
                int w = (int) INT.get(b, at);
                yield w >= 0 ? (Object) w : (Object) (w & 0xFFFFFFFFL);
            }
            case FLOAT32 -> (double) (float) FLOAT.get(b, at);
            case FLOAT64 -> (double) DOUBLE.get(b, at);
        };
    }

    static void write(Kind kind, byte[] b, int at, Number n) {
        switch (kind) {
            case INT8, UINT8 -> b[at] = (byte) Terms.toInt32(n);
            case UINT8_CLAMPED -> b[at] = (byte) toUint8Clamp(n.doubleValue());
            case INT16, UINT16 -> SHORT.set(b, at, (short) Terms.toInt32(n));
            case INT32, UINT32 -> INT.set(b, at, Terms.toInt32(n));
            case FLOAT32 -> FLOAT.set(b, at, n.floatValue());
            case FLOAT64 -> DOUBLE.set(b, at, n.doubleValue());
        }
    }

    /** Spec ToUint8Clamp (§7.1.12): clamp to [0, 255], ties to even. */
    private static int toUint8Clamp(double d) {
        if (Double.isNaN(d) || d <= 0) {
            return 0;
        }
        if (d >= 255) {
            return 255;
        }
        return (int) Math.rint(d);
    }

    @Override
    Object getIndexedValue(int index, Object receiver, CoreContext ctx) {
        return index >= 0 && index < length ? read(index) : Terms.UNDEFINED;
    }

    @Override
    public Object getElement(int index) {
        return index >= 0 && index < length ? read(index) : Terms.UNDEFINED;
    }

    @Override
    boolean hasOwnIndexedSlot(int index) {
        return index >= 0 && index < length;
    }

    @Override
    public void setElement(int index, Object value) {
        setIndexed(index, value, null);
    }

    @Override
    boolean handleLengthAssign(Object value, CoreContext context) {
        return true; // length is a prototype getter: the write is dropped
    }

    @Override
    public void putMember(String name, Object value, CoreContext ctx, boolean strict) {
        int i = parseIndex(name);
        if (i >= 0) {
            setIndexed(i, value, ctx);
        } else if (!"length".equals(name)) {
            super.putMember(name, value, ctx, strict);
        } else if (strict) {
            throw JsErrorException.typeError("Cannot set property length of [object " + kind.jsName
                    + "] which has only a getter");
        }
    }

    @Override
    public void removeMember(String name, CoreContext ctx, boolean strict) {
        int i = parseIndex(name);
        if (i < 0) {
            super.removeMember(name, ctx, strict);
        } else if (strict && i < length) {
            throw JsErrorException.typeError("Cannot delete property '" + i + "' of [object " + kind.jsName + "]");
        }
    }

    @Override
    public boolean isOwnProperty(String name) {
        return !"length".equals(name) && super.isOwnProperty(name);
    }

    /** The elements as the list {@link JsArray} works on: fixed length, each write converted per kind. */
    private static final class Elements extends AbstractList<Object> {

        private final Kind kind;
        private final byte[] bytes;
        private final int byteOffset;
        private final int length;

        Elements(Kind kind, byte[] bytes, int byteOffset, int length) {
            this.kind = kind;
            this.bytes = bytes;
            this.byteOffset = byteOffset;
            this.length = length;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= length) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + length);
            }
            return read(kind, bytes, byteOffset + index * kind.size);
        }

        @Override
        public Object set(int index, Object value) {
            Object previous = get(index);
            write(kind, bytes, byteOffset + index * kind.size,
                    value == HOLE ? (Number) 0 : Terms.toNumberCoerce(value, null));
            return previous;
        }

        @Override
        public int size() {
            return length;
        }

        @Override
        public void add(int index, Object value) {
            throw JsErrorException.typeError("Cannot add property " + length + ", object is not extensible");
        }

        @Override
        public Object remove(int index) {
            throw JsErrorException.typeError("Cannot delete property '" + index + "' of [object " + kind.jsName + "]");
        }

    }

}

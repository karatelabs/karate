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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Singleton prototype for {@link JsDataView}: the {@code buffer}, {@code byteLength}
 * and {@code byteOffset} getters and a get / set pair per element type, big-endian
 * unless {@code littleEndian} is true.
 */
class JsDataViewPrototype extends Prototype {

    static final JsDataViewPrototype INSTANCE = new JsDataViewPrototype();

    private static final byte ACCESSOR_ATTRS = (byte) (PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC);

    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    private enum Type {
        Int8(1), Uint8(1), Int16(2), Uint16(2), Int32(4), Uint32(4), Float32(4), Float64(8), BigInt64(8), BigUint64(8);

        final int size;

        Type(int size) {
            this.size = size;
        }
    }

    private JsDataViewPrototype() {
        super(JsObjectPrototype.INSTANCE);
        accessor("buffer", v -> v.buffer);
        accessor("byteLength", v -> v.byteLength);
        accessor("byteOffset", v -> v.byteOffset);
        for (Type type : Type.values()) {
            install("get" + type, 1, (context, args) -> get(type, context, args));
            install("set" + type, 2, (context, args) -> set(type, context, args));
        }
        install("@@toStringTag", "DataView");
        installConstructor("DataView");
    }

    private void accessor(String name, java.util.function.Function<JsDataView, Object> getter) {
        installAccessor(name, new JsBuiltinMethod("get " + name, 0,
                (ctx, args) -> getter.apply(asView(ctx, "get DataView.prototype." + name))), null, ACCESSOR_ATTRS);
    }

    private static JsDataView asView(Context context, String method) {
        if (context.getThisObject() instanceof JsDataView v) {
            return v;
        }
        throw JsErrorException.typeError("Method " + method + " called on incompatible receiver "
                + Terms.typeOf(context.getThisObject()));
    }

    private static ByteBuffer slot(JsDataView view, Type type, Object[] args, int littleEndianArg, CoreContext cc) {
        long index = JsArrayBuffer.toIndex(args.length > 0 ? args[0] : Terms.UNDEFINED, cc,
                "Offset is outside the bounds of the DataView");
        boolean littleEndian = args.length > littleEndianArg && Terms.isTruthy(args[littleEndianArg]);
        if (index + type.size > view.byteLength) {
            throw JsErrorException.rangeError("Offset is outside the bounds of the DataView");
        }
        return ByteBuffer.wrap(view.buffer.data, view.byteOffset + (int) index, type.size)
                .order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }

    private static Object get(Type type, Context context, Object[] args) {
        JsDataView view = asView(context, "DataView.prototype.get" + type);
        CoreContext cc = context instanceof CoreContext c ? c : null;
        ByteBuffer b = slot(view, type, args, 1, cc);
        int at = b.position();
        return switch (type) {
            case Int8 -> (int) b.get(at);
            case Uint8 -> b.get(at) & 0xFF;
            case Int16 -> (int) b.getShort(at);
            case Uint16 -> b.getShort(at) & 0xFFFF;
            case Int32 -> b.getInt(at);
            case Uint32 -> {
                int w = b.getInt(at);
                yield w >= 0 ? (Object) w : (Object) (w & 0xFFFFFFFFL);
            }
            case Float32 -> (double) b.getFloat(at);
            case Float64 -> b.getDouble(at);
            case BigInt64 -> BigInteger.valueOf(b.getLong(at));
            case BigUint64 -> {
                long l = b.getLong(at);
                yield l >= 0 ? BigInteger.valueOf(l) : BigInteger.valueOf(l).add(TWO_64);
            }
        };
    }

    private static Object set(Type type, Context context, Object[] args) {
        JsDataView view = asView(context, "DataView.prototype.set" + type);
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object value = args.length > 1 ? args[1] : Terms.UNDEFINED;
        long bits;
        double d = 0;
        if (type == Type.BigInt64 || type == Type.BigUint64) {
            if (value instanceof Number n && !(value instanceof BigInteger)) {
                throw JsErrorException.typeError("Cannot convert " + Terms.toStringCoerce(n, cc) + " to a BigInt");
            }
            bits = JsBigIntConstructor.toBigInt(value, cc).longValue();
        } else {
            d = Terms.toNumberCoerce(value, cc).doubleValue();
            bits = Terms.toInt32(d);
        }
        ByteBuffer b = slot(view, type, args, 2, cc);
        int at = b.position();
        switch (type) {
            case Int8, Uint8 -> b.put(at, (byte) bits);
            case Int16, Uint16 -> b.putShort(at, (short) bits);
            case Int32, Uint32 -> b.putInt(at, (int) bits);
            case Float32 -> b.putFloat(at, (float) d);
            case Float64 -> b.putDouble(at, d);
            case BigInt64, BigUint64 -> b.putLong(at, bits);
        }
        return Terms.UNDEFINED;
    }

}

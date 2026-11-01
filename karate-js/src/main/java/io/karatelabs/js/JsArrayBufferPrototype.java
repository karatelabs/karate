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

import java.util.Arrays;

/**
 * Singleton prototype for {@link JsArrayBuffer}: {@code byteLength} and {@code slice}.
 * Resizable and transferable buffers are not modelled: their members are absent.
 */
class JsArrayBufferPrototype extends Prototype {

    static final JsArrayBufferPrototype INSTANCE = new JsArrayBufferPrototype();

    private static final byte ACCESSOR_ATTRS = (byte) (PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC);

    private JsArrayBufferPrototype() {
        super(JsObjectPrototype.INSTANCE);
        installAccessor("byteLength", new JsBuiltinMethod("get byteLength", 0,
                (ctx, args) -> asBuffer(ctx, "byteLength").data.length), null, ACCESSOR_ATTRS);
        install("slice", 2, this::slice);
        install("@@toStringTag", "ArrayBuffer");
        installConstructor("ArrayBuffer");
    }

    private static JsArrayBuffer asBuffer(Context context, String method) {
        if (context.getThisObject() instanceof JsArrayBuffer b) {
            return b;
        }
        throw JsErrorException.typeError("Method ArrayBuffer.prototype." + method
                + " called on incompatible receiver " + Terms.typeOf(context.getThisObject()));
    }

    private Object slice(Context context, Object[] args) {
        byte[] data = asBuffer(context, "slice").data;
        CoreContext cc = context instanceof CoreContext c ? c : null;
        int begin = JsArrayBuffer.relativeIndex(args.length > 0 ? args[0] : Terms.UNDEFINED, data.length, 0, cc);
        int end = JsArrayBuffer.relativeIndex(args.length > 1 ? args[1] : Terms.UNDEFINED, data.length, data.length, cc);
        return new JsArrayBuffer(Arrays.copyOfRange(data, begin, Math.max(begin, end)));
    }

}

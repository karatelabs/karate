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

import java.util.List;

/**
 * A typed array constructor ({@code Int8Array}, {@code Uint8Array}, ...): from a
 * length, from an ArrayBuffer (a view: byte offset and length), or from a typed
 * array, iterable or array-like (a copy, each element converted per kind). A new
 * Uint8Array that is not a view of a buffer is a {@code byte[]}.
 */
class JsTypedArrayConstructor extends JsFunction {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private final JsTypedArray.Kind kind;

    private JsTypedArrayConstructor(JsTypedArray.Kind kind) {
        this.kind = kind;
        this.name = kind.jsName;
        this.length = 3;
        defineOwn("prototype", JsTypedArrayPrototype.of(kind), PropertySlot.INTRINSIC);
        defineOwn("BYTES_PER_ELEMENT", kind.size, PropertySlot.INTRINSIC);
        defineOwn("from", new JsBuiltinMethod("from", 1, this::from), METHOD_ATTRS);
        defineOwn("of", new JsBuiltinMethod("of", 0, (context, args) ->
                JsTypedArrayPrototype.fromValues(kind, List.of(args), core(context))), METHOD_ATTRS);
    }

    static JsTypedArrayConstructor of(String name) {
        for (JsTypedArray.Kind kind : JsTypedArray.Kind.values()) {
            if (kind.jsName.equals(name)) {
                return new JsTypedArrayConstructor(kind);
            }
        }
        return null;
    }

    private static CoreContext core(Context context) {
        return context instanceof CoreContext cc ? cc : null;
    }

    private Object from(Context context, Object[] args) {
        Object mapFn = args.length > 1 ? args[1] : Terms.UNDEFINED;
        if (mapFn != Terms.UNDEFINED && !(mapFn instanceof JsCallable)) {
            throw JsErrorException.typeError(Terms.toStringCoerce(mapFn, core(context)) + " is not a function");
        }
        Object values = ((JsCallable) context.getEngine().builtinConstructor("Array").getMember("from")).call(context, args);
        if (!(values instanceof List<?> list)) {
            return values;
        }
        return JsTypedArrayPrototype.fromValues(kind, JsArray.rawElements(list), core(context));
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Constructor " + kind.jsName + " requires 'new'");
        }
        CoreContext cc = core(context);
        Object arg = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (arg instanceof JsArrayBuffer buffer) {
            return view(buffer, args, cc);
        }
        if (arg instanceof byte[] || arg instanceof ObjectLike) {
            return JsTypedArrayPrototype.fromValues(kind, JsTypedArrayPrototype.elementsOf(arg, cc), cc);
        }
        long length = JsArrayBuffer.toIndex(arg, cc, "Invalid typed array length: " + Terms.toStringCoerce(arg, cc));
        return JsTypedArray.fresh(kind, JsTypedArrayPrototype.allocate(kind, length));
    }

    private JsTypedArray view(JsArrayBuffer buffer, Object[] args, CoreContext cc) {
        Object offsetArg = args.length > 1 ? args[1] : Terms.UNDEFINED;
        Object lengthArg = args.length > 2 ? args[2] : Terms.UNDEFINED;
        long offset = JsArrayBuffer.toIndex(offsetArg, cc,
                "Start offset " + Terms.toStringCoerce(offsetArg, cc) + " is outside the bounds of the buffer");
        if (offset % kind.size != 0) {
            throw JsErrorException.rangeError("start offset of " + kind.jsName + " should be a multiple of " + kind.size);
        }
        int byteLength = buffer.data.length;
        long length;
        if (lengthArg == Terms.UNDEFINED) {
            if (byteLength % kind.size != 0) {
                throw JsErrorException.rangeError("byte length of " + kind.jsName + " should be a multiple of " + kind.size);
            }
            if (offset > byteLength) {
                throw JsErrorException.rangeError("Start offset " + offset + " is outside the bounds of the buffer");
            }
            length = (byteLength - offset) / kind.size;
        } else {
            length = JsArrayBuffer.toIndex(lengthArg, cc, "Invalid typed array length: " + Terms.toStringCoerce(lengthArg, cc));
            if (offset + length * kind.size > byteLength) {
                throw JsErrorException.rangeError("Invalid typed array length: " + length);
            }
        }
        return JsTypedArray.view(kind, buffer, (int) offset, (int) length);
    }

}

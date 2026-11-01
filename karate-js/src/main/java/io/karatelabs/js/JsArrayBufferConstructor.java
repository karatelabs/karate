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
 * Global {@code ArrayBuffer} constructor: {@code new ArrayBuffer(length)} is
 * zero-filled; {@code ArrayBuffer.isView(value)} is true for a typed array or DataView.
 */
class JsArrayBufferConstructor extends JsFunction {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    JsArrayBufferConstructor() {
        this.name = "ArrayBuffer";
        this.length = 1;
        defineOwn("prototype", JsArrayBufferPrototype.INSTANCE, PropertySlot.INTRINSIC);
        defineOwn("isView", new JsBuiltinMethod("isView", 1, (JsInvokable) args -> args.length > 0
                && (args[0] instanceof JsTypedArray || args[0] instanceof byte[] || args[0] instanceof JsDataView)), METHOD_ATTRS);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Constructor ArrayBuffer requires 'new'");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        long length = JsArrayBuffer.toIndex(args.length > 0 ? args[0] : Terms.UNDEFINED, cc, "Invalid array buffer length");
        if (length > Integer.MAX_VALUE - 8) {
            throw JsErrorException.rangeError("Array buffer allocation failed");
        }
        return new JsArrayBuffer(new byte[(int) length]);
    }

}

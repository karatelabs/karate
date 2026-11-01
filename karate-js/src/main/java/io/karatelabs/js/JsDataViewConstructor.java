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

/** Global {@code DataView} constructor: {@code new DataView(buffer, byteOffset?, byteLength?)}. */
class JsDataViewConstructor extends JsFunction {

    JsDataViewConstructor() {
        this.name = "DataView";
        this.length = 1;
        defineOwn("prototype", JsDataViewPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Constructor DataView requires 'new'");
        }
        if (!(args.length > 0 && args[0] instanceof JsArrayBuffer buffer)) {
            throw JsErrorException.typeError("First argument to DataView constructor must be an ArrayBuffer");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Object offsetArg = args.length > 1 ? args[1] : Terms.UNDEFINED;
        Object lengthArg = args.length > 2 ? args[2] : Terms.UNDEFINED;
        long offset = JsArrayBuffer.toIndex(offsetArg, cc,
                "Start offset " + Terms.toStringCoerce(offsetArg, cc) + " is outside the bounds of the buffer");
        int byteLength = buffer.data.length;
        if (offset > byteLength) {
            throw JsErrorException.rangeError("Start offset " + offset + " is outside the bounds of the buffer");
        }
        long length = lengthArg == Terms.UNDEFINED ? byteLength - offset
                : JsArrayBuffer.toIndex(lengthArg, cc, "Invalid DataView length " + Terms.toStringCoerce(lengthArg, cc));
        if (offset + length > byteLength) {
            throw JsErrorException.rangeError("Invalid DataView length " + length);
        }
        return new JsDataView(buffer, (int) offset, (int) length);
    }

}

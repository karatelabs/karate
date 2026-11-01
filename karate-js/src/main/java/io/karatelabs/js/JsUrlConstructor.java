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
 * Global {@code URL} constructor: {@code new URL(url, base?)}, {@code URL.canParse} and
 * {@code URL.parse}. An input that is not a URL throws Node's {@code TypeError: Invalid URL}.
 */
class JsUrlConstructor extends JsFunction {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    JsUrlConstructor() {
        this.name = "URL";
        this.length = 1;
        defineOwn("prototype", JsUrlPrototype.INSTANCE, PropertySlot.INTRINSIC);
        defineOwn("canParse", new JsBuiltinMethod("canParse", 1, (ctx, args) -> parse(ctx, args) != null), METHOD_ATTRS);
        defineOwn("parse", new JsBuiltinMethod("parse", 1, JsUrlConstructor::parse), METHOD_ATTRS);
    }

    private static JsUrl parse(Context context, Object[] args) {
        CoreContext cc = context instanceof CoreContext c ? c : null;
        JsUrl base = null;
        if (args.length > 1 && args[1] != Terms.UNDEFINED) {
            base = JsUrl.parse(Terms.toStringCoerce(args[1], cc), null);
            if (base == null) {
                return null;
            }
        }
        return JsUrl.parse(Terms.toStringCoerce(args.length > 0 ? args[0] : Terms.UNDEFINED, cc), base);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Constructor URL requires 'new'");
        }
        JsUrl url = parse(context, args);
        if (url == null) {
            throw JsErrorException.typeError("Invalid URL");
        }
        return url;
    }

}

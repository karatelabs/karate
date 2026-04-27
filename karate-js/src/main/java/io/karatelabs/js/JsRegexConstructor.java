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
 * JavaScript RegExp constructor function. Mirrors the
 * {@link JsNumberConstructor} / {@link JsBooleanConstructor} pattern so
 * {@code RegExp.prototype} resolves the {@link JsRegexPrototype} singleton
 * instead of undefined. {@link JsRegex} stays as the instance type returned
 * by {@code new RegExp(pattern, flags)} and {@code /foo/} literals.
 */
class JsRegexConstructor extends JsFunction {

    static final JsRegexConstructor INSTANCE = new JsRegexConstructor();

    private JsRegexConstructor() {
        this.name = "RegExp";
        this.length = 2;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("prototype", JsRegexPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object... args) {
        if (args.length == 0) {
            return new JsRegex();
        }
        String pattern = args[0].toString();
        String flags = args.length > 1 ? args[1].toString() : "";
        return new JsRegex(pattern, flags);
    }

}

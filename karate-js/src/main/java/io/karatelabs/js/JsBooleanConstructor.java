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
 * JavaScript Boolean constructor function.
 * <p>
 * Mirrors {@link JsNumberConstructor} / {@link JsObjectConstructor}: a singleton
 * {@link JsFunction} that registers the {@code prototype} intrinsic so
 * {@code Boolean.prototype.x = …} resolves the prototype object instead of
 * undefined. {@link JsBoolean} stays as the boxed-primitive instance produced
 * by {@code new Boolean(x)}.
 */
class JsBooleanConstructor extends JsFunction {

    static final JsBooleanConstructor INSTANCE = new JsBooleanConstructor();

    private JsBooleanConstructor() {
        this.name = "Boolean";
        this.length = 1;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("prototype", JsBooleanPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object... args) {
        boolean coerced = args.length > 0 && Terms.isTruthy(args[0]);
        CallInfo callInfo = context.getCallInfo();
        if (callInfo != null && callInfo.constructor) {
            return new JsBoolean(coerced);
        }
        return coerced;
    }

}

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
 * Singleton prototype for {@link JsGenerator} instances. Inherits from
 * {@link JsObjectPrototype}. Every method brand-checks the receiver — a
 * borrowed {@code next.call(notAGenerator)} is a TypeError, matching spec
 * §27.5.3's [[GeneratorState]] slot requirement.
 */
class JsGeneratorPrototype extends Prototype {

    static final JsGeneratorPrototype INSTANCE = new JsGeneratorPrototype();

    private JsGeneratorPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("next", 1, this::next);
        install("return", 1, this::returnMethod);
        install("throw", 1, this::throwMethod);
        // a generator is its own iterator
        install(IterUtils.SYMBOL_ITERATOR, 0, (context, args) -> asGenerator(context));
    }

    private static JsGenerator asGenerator(Context context) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsGenerator g) {
            return g;
        }
        throw JsErrorException.typeError("Method Generator.prototype called on incompatible receiver");
    }

    private static Object arg(Object[] args) {
        return args.length > 0 ? args[0] : Terms.UNDEFINED;
    }

    private Object next(Context context, Object[] args) {
        return asGenerator(context).next((CoreContext) context, arg(args));
    }

    private Object returnMethod(Context context, Object[] args) {
        return asGenerator(context).returnValue((CoreContext) context, arg(args));
    }

    private Object throwMethod(Context context, Object[] args) {
        return asGenerator(context).throwValue((CoreContext) context, arg(args));
    }

}

/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
 * Read / write / brand-check for {@code obj.#x}. The parser has already proved the
 * name is declared by an enclosing class body, so an unresolved name here means the
 * frame is missing its private environment rather than that the program is wrong.
 * The runtime error that IS reachable is the brand miss: the object simply is not an
 * instance of the declaring class.
 */
final class PrivateAccess {

    private PrivateAccess() {
    }

    static PrivateName resolve(String name, CoreContext context) {
        PrivateName pn = context.privateEnv == null ? null : context.privateEnv.resolve(name);
        if (pn == null) {
            throw JsErrorException.syntaxError("private name " + name + " is not declared in an enclosing class");
        }
        return pn;
    }

    static boolean has(Object target, PrivateName pn) {
        return target instanceof JsObject obj && obj.hasPrivate(pn);
    }

    static Object get(Object target, PrivateName pn, CoreContext context) {
        JsObject obj = branded(target, pn, "read");
        return switch (pn.kind) {
            case FIELD -> obj.getPrivate(pn);
            case METHOD -> pn.method;
            case ACCESSOR -> {
                if (pn.getter == null) {
                    throw JsErrorException.typeError("'" + pn.name + "' was defined without a getter");
                }
                yield Interpreter.invokeGetter(pn.getter, target, context);
            }
        };
    }

    static void set(Object target, PrivateName pn, Object value, CoreContext context) {
        JsObject obj = branded(target, pn, "write");
        switch (pn.kind) {
            case FIELD -> obj.putPrivate(pn, value);
            case METHOD -> throw JsErrorException.typeError("Cannot write to private method " + pn.name);
            case ACCESSOR -> {
                if (pn.setter == null) {
                    throw JsErrorException.typeError("'" + pn.name + "' was defined without a setter");
                }
                Interpreter.invokeSetter(pn.setter, target, value, context);
            }
        }
    }

    private static JsObject branded(Object target, PrivateName pn, String verb) {
        if (target instanceof JsObject obj && obj.hasPrivate(pn)) {
            return obj;
        }
        throw JsErrorException.typeError("Cannot " + verb + " private member " + pn.name
                + " from an object whose class did not declare it");
    }

}

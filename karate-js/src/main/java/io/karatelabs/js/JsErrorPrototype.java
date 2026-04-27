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
 * Per-spec prototype for Error and its native subclasses (§20.5).
 * <p>
 * One singleton per error type; each carries its own {@code name} and lazy
 * {@code constructor}. Parent chains:
 * {@code TypeError.prototype} → {@code Error.prototype} → {@code Object.prototype}.
 * Only {@link #ERROR} (the root) installs {@code message} and {@code toString};
 * subclass prototypes inherit both.
 */
class JsErrorPrototype extends Prototype {

    static final JsErrorPrototype ERROR = new JsErrorPrototype("Error", JsObjectPrototype.INSTANCE, true);
    static final JsErrorPrototype TYPE_ERROR = new JsErrorPrototype("TypeError", ERROR, false);
    static final JsErrorPrototype RANGE_ERROR = new JsErrorPrototype("RangeError", ERROR, false);
    static final JsErrorPrototype SYNTAX_ERROR = new JsErrorPrototype("SyntaxError", ERROR, false);
    static final JsErrorPrototype REFERENCE_ERROR = new JsErrorPrototype("ReferenceError", ERROR, false);
    static final JsErrorPrototype URI_ERROR = new JsErrorPrototype("URIError", ERROR, false);
    static final JsErrorPrototype EVAL_ERROR = new JsErrorPrototype("EvalError", ERROR, false);
    static final JsErrorPrototype AGGREGATE_ERROR = new JsErrorPrototype("AggregateError", ERROR, false);

    private final String typeName;

    private JsErrorPrototype(String name, Prototype parent, boolean isRoot) {
        super(parent);
        this.typeName = name;
        install("name", name);
        // Lazy ref — JsErrorConstructor singletons aren't initialized at this point in the
        // static-init cycle. Resolved on first JS-side read (Error.prototype.constructor).
        installLazy("constructor", () -> JsErrorConstructor.forName(name));
        if (isRoot) {
            install("message", "");
            install("toString", 0, JsErrorPrototype::toStringMethod);
        }
    }

    String getTypeName() {
        return typeName;
    }

    /**
     * Spec §20.5.3.4 Error.prototype.toString. Reads {@code name} / {@code message}
     * via the receiver (proto chain), defaulting to "Error" / "" when undefined.
     * Both values run through {@code Terms.toStringCoerce} so a custom
     * {@code valueOf}/{@code toString} that throws (e.g. via {@code @@toPrimitive})
     * propagates the abrupt completion to the caller.
     */
    private static Object toStringMethod(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        if (!(thisObj instanceof ObjectLike obj)) {
            throw JsErrorException.typeError("Error.prototype.toString called on non-object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        String name = readToString(obj, "name", "Error", cc);
        if (name == null) return Terms.UNDEFINED;
        String msg = readToString(obj, "message", "", cc);
        if (msg == null) return Terms.UNDEFINED;
        if (name.isEmpty()) return msg;
        if (msg.isEmpty()) return name;
        return name + ": " + msg;
    }

    /**
     * Spec ToString (§7.1.17) on an own/prototype-chain field, with a default
     * applied when the value is {@code undefined}. ObjectLike values dispatch
     * through {@link Terms#toPrimitive} (hint string) so a custom
     * {@code @@toPrimitive} / {@code toString} / {@code valueOf} that throws
     * propagates via {@code context.isError()}; we surface the abrupt
     * completion to the caller as {@code null}.
     */
    private static String readToString(ObjectLike obj, String key, String defaultIfUndefined, CoreContext cc) {
        // Use the context-aware getMember so an accessor that throws routes
        // the abrupt completion through cc.isError() instead of propagating
        // as a raw Java exception.
        Object val = cc != null ? obj.getMember(key, obj, cc) : obj.getMember(key);
        if (cc != null && cc.isError()) return null;
        if (val == null || val == Terms.UNDEFINED) return defaultIfUndefined;
        if (val instanceof ObjectLike inner && cc != null) {
            val = Terms.toPrimitive(inner, "string", cc);
            if (cc.isError()) return null;
        }
        if (val == null) return "null";
        if (val == Terms.UNDEFINED) return "undefined";
        return val.toString();
    }

}

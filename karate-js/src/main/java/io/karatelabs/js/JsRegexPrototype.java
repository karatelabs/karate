/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
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

import java.util.function.Function;

/**
 * Singleton prototype for RegExp instances. Inherits from JsObjectPrototype.
 * <p>
 * Methods (test / exec / toString) are wrapped via
 * {@link Prototype#install(String, int, JsCallable)} so each carries spec
 * {@code length}+{@code name}. Flag/source/flags getters are wired as accessor
 * descriptors via {@link #installFlagAccessor} — spec §22.2.6 puts these on
 * the prototype as getters, NOT as own properties of instances. Tests read
 * them back via {@code Object.getOwnPropertyDescriptor(RegExp.prototype, "x").get}.
 */
class JsRegexPrototype extends Prototype {

    static final JsRegexPrototype INSTANCE = new JsRegexPrototype();

    private static final byte ACCESSOR_ATTRS = (byte) (PropertySlot.CONFIGURABLE | PropertySlot.INTRINSIC);

    private JsRegexPrototype() {
        super(JsObjectPrototype.INSTANCE);
        install("test", 1, this::test);
        install("exec", 1, this::exec);
        install("toString", 0, this::toStringMethod);
        // Spec §22.2.6.{4,7,8,9,10,11,12,13}: source / flags / global /
        // ignoreCase / multiline / dotAll / sticky / unicode are accessors
        // on the prototype. Each getter:
        //   1. throws TypeError if `this` is not an Object;
        //   2. returns the spec sentinel when `this === RegExp.prototype`
        //      (the prototype itself has no [[OriginalFlags]] internal slot);
        //   3. otherwise reads the field off the JsRegex instance.
        installFlagAccessor("source", "(?:)", r -> r.pattern);
        installFlagAccessor("flags", "", r -> r.flags);
        installFlagAccessor("global", Terms.UNDEFINED, r -> r.flags.contains("g"));
        installFlagAccessor("ignoreCase", Terms.UNDEFINED, r -> r.flags.contains("i"));
        installFlagAccessor("multiline", Terms.UNDEFINED, r -> r.flags.contains("m"));
        installFlagAccessor("dotAll", Terms.UNDEFINED, r -> r.flags.contains("s"));
        installFlagAccessor("sticky", Terms.UNDEFINED, r -> r.flags.contains("y"));
        installFlagAccessor("unicode", Terms.UNDEFINED, r -> r.flags.contains("u"));
        installLazy("constructor", () -> JsRegexConstructor.INSTANCE);
    }

    // Spec §22.2.6.4 etc. shared shape: TypeError on non-object receiver,
    // sentinel on the prototype itself, extractor on a real JsRegex. Routes
    // through {@link Prototype#installAccessor} so the descriptor lives in
    // {@code builtins} and survives per-Engine reset (user-defined
    // accessors via {@code Object.defineProperty} would still shadow these
    // via {@code userProps}).
    private void installFlagAccessor(String name, Object protoSentinel,
                                      Function<JsRegex, Object> extractor) {
        JsCallable lambda = (ctx, args) -> {
            Object thisObj = ctx.getThisObject();
            if (thisObj == this) return protoSentinel;
            if (thisObj instanceof JsRegex r) return extractor.apply(r);
            throw JsErrorException.typeError("get RegExp.prototype." + name + " called on incompatible receiver");
        };
        JsBuiltinMethod getter = new JsBuiltinMethod("get " + name, 0, lambda);
        installAccessor(name, getter, null, ACCESSOR_ATTRS);
    }

    // Spec preamble for test / exec — RegExp objects are required (no ToString
    // coercion of `this`); the search string is ToString-coerced.
    private static JsRegex requireRegex(Context context, String methodName) {
        Object thisObj = context.getThisObject();
        if (thisObj instanceof JsRegex r) return r;
        throw JsErrorException.typeError("RegExp.prototype." + methodName + " called on incompatible receiver");
    }

    private static String argString(Object[] args, Context context) {
        Object arg = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (arg instanceof String s) return s;
        if (arg instanceof JsString js) return js.text;
        return Terms.toStringCoerce(arg, context instanceof CoreContext cc ? cc : null);
    }

    // Instance methods

    private Object test(Context context, Object[] args) {
        return requireRegex(context, "test").test(argString(args, context));
    }

    private Object exec(Context context, Object[] args) {
        return requireRegex(context, "exec").exec(argString(args, context));
    }

    private Object toStringMethod(Context context, Object[] args) {
        Object thisObj = context.getThisObject();
        // Spec §22.2.6.17 RegExp.prototype.toString reads `source` and `flags`
        // via [[Get]] — works on any object that exposes them. JsRegex's
        // toString matches that for the common case; fall back to the spec
        // form when called on something else (e.g., a derived host object).
        if (thisObj instanceof JsRegex r) return r.toString();
        if (thisObj instanceof ObjectLike ol) {
            CoreContext cc = context instanceof CoreContext c ? c : null;
            return "/" + Terms.toStringCoerce(ol.getMember("source"), cc) + "/"
                    + Terms.toStringCoerce(ol.getMember("flags"), cc);
        }
        throw JsErrorException.typeError("RegExp.prototype.toString called on incompatible receiver");
    }

}

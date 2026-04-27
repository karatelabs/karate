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

import java.util.ArrayList;
import java.util.List;

/**
 * Per-spec constructor for Error and its native subclasses (§20.5).
 * <p>
 * One singleton per error type. Each carries its own {@link JsErrorPrototype}
 * as the {@code prototype} own intrinsic; instances created via
 * {@code Error("msg")} or {@code new Error("msg")} carry that prototype as
 * their {@code __proto__}. Per spec the call form and the construct form are
 * identical (§20.5.1.1 step 1).
 */
class JsErrorConstructor extends JsFunction {

    static final JsErrorConstructor ERROR = new JsErrorConstructor(JsErrorPrototype.ERROR, 1);
    static final JsErrorConstructor TYPE_ERROR = new JsErrorConstructor(JsErrorPrototype.TYPE_ERROR, 1);
    static final JsErrorConstructor RANGE_ERROR = new JsErrorConstructor(JsErrorPrototype.RANGE_ERROR, 1);
    static final JsErrorConstructor SYNTAX_ERROR = new JsErrorConstructor(JsErrorPrototype.SYNTAX_ERROR, 1);
    static final JsErrorConstructor REFERENCE_ERROR = new JsErrorConstructor(JsErrorPrototype.REFERENCE_ERROR, 1);
    static final JsErrorConstructor URI_ERROR = new JsErrorConstructor(JsErrorPrototype.URI_ERROR, 1);
    static final JsErrorConstructor EVAL_ERROR = new JsErrorConstructor(JsErrorPrototype.EVAL_ERROR, 1);
    static final JsErrorConstructor AGGREGATE_ERROR = new JsErrorConstructor(JsErrorPrototype.AGGREGATE_ERROR, 2);

    private static final byte MESSAGE_ATTRS = WRITABLE | CONFIGURABLE;

    private final JsErrorPrototype errorPrototype;

    private JsErrorConstructor(JsErrorPrototype prototype, int length) {
        this.name = prototype.getTypeName();
        this.length = length;
        this.errorPrototype = prototype;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("prototype", errorPrototype, PropertySlot.INTRINSIC);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    @Override
    public Object call(Context context, Object[] args) {
        if (errorPrototype == JsErrorPrototype.AGGREGATE_ERROR) {
            return constructAggregate(context, args);
        }
        // Spec §20.5.1.1: O = OrdinaryCreateFromConstructor(...). Set message
        // when args[0] != undefined; install cause when options has own "cause".
        CoreContext cc = context instanceof CoreContext c ? c : null;
        String message = null;
        if (args.length > 0 && args[0] != null && args[0] != Terms.UNDEFINED) {
            message = toMessageString(args[0], cc);
            if (cc != null && cc.isError()) return Terms.UNDEFINED;
        }
        JsError instance = new JsError(errorPrototype);
        if (message != null) {
            instance.defineOwn("message", message, MESSAGE_ATTRS);
        }
        installCauseFromOptions(instance, args, 1, cc);
        return instance;
    }

    /**
     * Spec ToString applied to the constructor's message argument: ObjectLike
     * inputs route through {@link Terms#toPrimitive} (hint string) so a custom
     * {@code valueOf}/{@code toString} that throws propagates via the
     * context's abrupt-completion channel rather than being silently masked.
     */
    private static String toMessageString(Object value, CoreContext cc) {
        if (value instanceof ObjectLike inner && cc != null) {
            value = Terms.toPrimitive(inner, "string", cc);
            if (cc.isError()) return null;
        }
        if (value == null) return "null";
        if (value == Terms.UNDEFINED) return "undefined";
        return value.toString();
    }

    /**
     * AggregateError(errors, message?, options?): iterate errors into an Array
     * own property, set message + cause as for plain Error. Spec §20.5.7.1.1.
     */
    private Object constructAggregate(Context context, Object[] args) {
        Object errorsArg = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (errorsArg == null || errorsArg == Terms.UNDEFINED) {
            throw JsErrorException.typeError("AggregateError requires an iterable of errors");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        List<Object> collected = new ArrayList<>();
        JsIterator iter = IterUtils.getIterator(errorsArg, context);
        while (iter.hasNext()) {
            collected.add(iter.next());
        }
        String message = null;
        if (args.length > 1 && args[1] != Terms.UNDEFINED) {
            message = toMessageString(args[1], cc);
            if (cc != null && cc.isError()) return Terms.UNDEFINED;
        }
        JsError instance = new JsError(errorPrototype);
        if (message != null) {
            instance.defineOwn("message", message, MESSAGE_ATTRS);
        }
        installCauseFromOptions(instance, args, 2, cc);
        instance.defineOwn("errors", new JsArray(collected), MESSAGE_ATTRS);
        return instance;
    }

    /**
     * Spec §20.5.8.1 InstallErrorCause: if {@code options} is an Object and
     * {@code HasProperty(options, "cause")} is true (proto-walking — NOT just
     * own), Get the cause and install it as a non-enumerable own data
     * property on the instance.
     */
    private static void installCauseFromOptions(JsError instance, Object[] args, int optionsIndex, CoreContext cc) {
        if (args.length <= optionsIndex) return;
        Object options = args[optionsIndex];
        if (!(options instanceof ObjectLike opts)) return;
        if (!hasProperty(opts, "cause")) return;
        // Context-aware Get so an accessor descriptor's getter actually fires
        // (and a throw inside the getter routes through cc.isError()).
        Object causeVal = cc != null ? opts.getMember("cause", opts, cc) : opts.getMember("cause");
        if (cc != null && cc.isError()) return;
        instance.defineOwn("cause", causeVal, MESSAGE_ATTRS);
    }

    private static boolean hasProperty(ObjectLike obj, String name) {
        for (ObjectLike o = obj; o != null; o = o.getPrototype()) {
            if (o.isOwnProperty(name)) return true;
        }
        return false;
    }

    static JsErrorConstructor forName(String name) {
        return switch (name) {
            case "TypeError" -> TYPE_ERROR;
            case "RangeError" -> RANGE_ERROR;
            case "SyntaxError" -> SYNTAX_ERROR;
            case "ReferenceError" -> REFERENCE_ERROR;
            case "URIError" -> URI_ERROR;
            case "EvalError" -> EVAL_ERROR;
            case "AggregateError" -> AGGREGATE_ERROR;
            default -> ERROR;
        };
    }

}

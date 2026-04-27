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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Single seam for ECMAScript iteration protocol consumers (for-of, spread,
 * destructuring, Array.from). Built-ins (JsArray/List/JsString/String) use
 * fast paths that bypass the user-facing iterator object; user-defined
 * objects with an {@code @@iterator} method go through the spec dance:
 * call {@code obj[@@iterator]()} → repeatedly invoke {@code .next()} →
 * unpack {@code {value, done}}.
 *
 * <p>The {@code @@iterator} string key stands in for {@code Symbol.iterator}
 * until real Symbol support lands; built-in iterables expose their iterator
 * factory under that key so user code (and Map/Set when they arrive) can
 * use the same protocol.
 */
public class IterUtils {

    /** Stand-in for {@code Symbol.iterator} until real Symbol support lands. */
    public static final String SYMBOL_ITERATOR = "@@iterator";

    /**
     * Shared {@code @@iterator} stand-in installed on built-in iterable
     * prototypes ({@link JsArrayPrototype}, {@link JsStringPrototype}). Per
     * spec, {@code Symbol.iterator} lives on the prototype rather than each
     * instance — installing once means {@code arr.hasOwnProperty('@@iterator')
     * === false} (correct: it's inherited) and identity holds across instances.
     * <p>
     * Reads {@code this} from the call context, resolves it via {@link
     * #getIterator}, and wraps in the spec iterator-result shape. A
     * non-iterable {@code this} surfaces as a TypeError from {@code getIterator}
     * — same behavior as spec's {@code Array.prototype[Symbol.iterator]} when
     * detached from an iterable receiver.
     */
    public static final JsCallable SYMBOL_ITERATOR_METHOD = (ctx, args) -> {
        Object thisObj = ctx.getThisObject();
        JsIterator iter = getIterator(thisObj, ctx);
        return toIteratorObject(iter);
    };

    private IterUtils() {
    }

    /**
     * GetIterator(source) per spec 7.4.1. Throws {@link JsErrorException}
     * (TypeError) if {@code source} is null/undefined or otherwise not iterable.
     */
    public static JsIterator getIterator(Object source, Context context) {
        JsIterator iter = tryGetIterator(source, context);
        if (iter == null) {
            throw JsErrorException.typeError(describe(source) + " is not iterable");
        }
        return iter;
    }

    /**
     * Like {@link #getIterator} but returns null instead of throwing — for callers
     * that need to gate behavior on iterability without forcing a TypeError.
     */
    @SuppressWarnings("unchecked")
    public static JsIterator tryGetIterator(Object source, Context context) {
        if (source == null || source == Terms.UNDEFINED) {
            return null;
        }
        if (source instanceof JsArray jsArray) {
            return listIterator(jsArray.list);
        }
        if (source instanceof JsString jsString) {
            return stringIterator(jsString.text);
        }
        if (source instanceof String s) {
            return stringIterator(s);
        }
        if (source instanceof List<?> list) {
            return listIterator((List<Object>) list);
        }
        // Java native arrays via toJsArray (String[], int[], Object[], byte[] excluded — handled below)
        JsArray asArray = Terms.toJsArray(source);
        if (asArray != null) {
            return listIterator(asArray.list);
        }
        if (source instanceof ObjectLike obj) {
            Object iteratorFn = obj.getMember(SYMBOL_ITERATOR);
            if (iteratorFn instanceof JsCallable callable) {
                return userIterator(callable, obj, context);
            }
        }
        return null;
    }

    /** True iff {@code source} would yield a non-null iterator from {@link #tryGetIterator}. */
    public static boolean isIterable(Object source, Context context) {
        return tryGetIterator(source, context) != null;
    }

    /**
     * Spec {@code GetIteratorFromMethod}: invoke {@code factory} with {@code receiver}
     * as {@code this} (no args) and walk the returned iterator. Used by
     * Set.prototype.{union/difference/...}'s GetKeysIterator step (call the
     * set-like's {@code keys} property).
     * <p>
     * Lenient on the call result: spec demands an iterator Object with
     * {@code .next()}, but built-ins inside karate-js sometimes return raw
     * Lists / JsArrays (e.g. {@code Array.prototype.values()} currently
     * returns the underlying list). When the result lacks a {@code .next}
     * member, fall through to {@link #getIterator(Object, Context)} so those
     * shortcuts still iterate correctly. test262 tests like
     * {@code converts-negative-zero.js} construct set-likes that return
     * {@code [-0].values()} from {@code keys} expecting iteration to work.
     */
    public static JsIterator iteratorFromCallable(JsCallable factory, ObjectLike receiver, Context context) {
        Object result;
        if (context instanceof CoreContext cc) {
            Object savedThis = cc.thisObject;
            cc.thisObject = receiver;
            try {
                result = factory.call(cc, EMPTY_ARGS);
            } finally {
                cc.thisObject = savedThis;
            }
        } else {
            result = factory.call(context, EMPTY_ARGS);
        }
        if (result == null || result == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Result of iterator method is not an object");
        }
        // Spec-shaped iterator object: ObjectLike with a callable .next.
        if (result instanceof ObjectLike obj) {
            Object nextFn = obj.getMember("next");
            if (nextFn instanceof JsCallable) {
                return iteratorObjectWalker(obj, context);
            }
        }
        // Fallback: engine-internal shortcut returned a List / JsArray /
        // String / iterable ObjectLike — getIterator's tryGetIterator covers
        // all of those.
        return getIterator(result, context);
    }

    /**
     * Walk an already-constructed iterator object's {@code .next()} method to
     * produce a {@link JsIterator}. Shared by {@link #userIterator} (which
     * first invokes {@code @@iterator}) and the spec-shaped branch of
     * {@link #iteratorFromCallable}.
     */
    private static JsIterator iteratorObjectWalker(ObjectLike iterObj, Context context) {
        return new JsIterator() {
            Object pending;
            boolean fetched;
            boolean done;

            private void fetch() {
                if (fetched || done) return;
                Object nextFn = iterObj.getMember("next");
                if (!(nextFn instanceof JsCallable nextCallable)) {
                    throw JsErrorException.typeError("iterator.next is not a function");
                }
                Object step;
                if (context instanceof CoreContext cc) {
                    Object savedThis = cc.thisObject;
                    cc.thisObject = iterObj;
                    try {
                        step = nextCallable.call(cc, EMPTY_ARGS);
                    } finally {
                        cc.thisObject = savedThis;
                    }
                } else {
                    step = nextCallable.call(context, EMPTY_ARGS);
                }
                if (isJsErrored(context)) { done = true; return; }
                if (!(step instanceof ObjectLike stepObj)) {
                    throw JsErrorException.typeError("iterator result is not an object");
                }
                if (Terms.isTruthy(readMember(stepObj, "done", context))) {
                    done = true;
                    return;
                }
                if (isJsErrored(context)) { done = true; return; }
                pending = readMember(stepObj, "value", context);
                if (isJsErrored(context)) { done = true; return; }
                fetched = true;
            }

            @Override
            public boolean hasNext() {
                fetch();
                return !done;
            }

            @Override
            public Object next() {
                fetch();
                if (done) {
                    throw new NoSuchElementException();
                }
                fetched = false;
                Object v = pending;
                pending = null;
                return v;
            }
        };
    }

    /**
     * Wraps a {@link JsIterator} as the user-visible iterator object: a
     * {@link JsObject} with a {@code next} method returning {@code {value, done}}.
     * Used by built-in {@code @@iterator} factories ({@link JsArray}, {@link JsString})
     * to expose internal iteration in spec-compliant shape.
     */
    public static JsObject toIteratorObject(JsIterator iter) {
        JsObject result = new JsObject();
        JsCallable nextFn = (ctx, args) -> {
            JsObject step = new JsObject(new LinkedHashMap<>(2));
            if (iter.hasNext()) {
                step.putMember("value", iter.next());
                step.putMember("done", false);
            } else {
                step.putMember("value", Terms.UNDEFINED);
                step.putMember("done", true);
            }
            return step;
        };
        result.putMember("next", nextFn);
        // Iterator objects implement the iterable protocol themselves — `[Symbol.iterator]()`
        // returns the iterator. Lets `for (x of arr[Symbol.iterator]())` work, and matches
        // %IteratorPrototype%'s spec-mandated identity method.
        result.putMember(SYMBOL_ITERATOR, (JsCallable) (ctx, args) -> result);
        return result;
    }

    // ---------------------------------------------------------------------
    // built-in fast paths

    private static JsIterator listIterator(List<Object> list) {
        return new JsIterator() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < list.size();
            }

            @Override
            public Object next() {
                if (i >= list.size()) {
                    throw new NoSuchElementException();
                }
                // for-of / spread / destructuring read holes as undefined per spec
                // (`[...[1,,3]]` → `[1, undefined, 3]`). HOLE is JsArray's
                // sparse-slot sentinel; translate at the iterator boundary.
                return JsArray.unwrapHole(list.get(i++));
            }
        };
    }

    private static JsIterator stringIterator(String text) {
        return new JsIterator() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < text.length();
            }

            @Override
            public Object next() {
                if (i >= text.length()) {
                    throw new NoSuchElementException();
                }
                return String.valueOf(text.charAt(i++));
            }
        };
    }

    // ---------------------------------------------------------------------
    // slow path — user iterator: call obj[@@iterator]() then walk .next()

    private static JsIterator userIterator(JsCallable iteratorFn, ObjectLike receiver, Context context) {
        Object iter;
        if (context instanceof CoreContext cc) {
            Object savedThis = cc.thisObject;
            cc.thisObject = receiver;
            try {
                iter = iteratorFn.call(cc, EMPTY_ARGS);
            } finally {
                cc.thisObject = savedThis;
            }
        } else {
            iter = iteratorFn.call(context, EMPTY_ARGS);
        }
        if (!(iter instanceof ObjectLike iterObj)) {
            throw JsErrorException.typeError("Result of the Symbol.iterator method is not an object");
        }
        return new JsIterator() {
            Object pending;
            boolean fetched;
            boolean done;

            private void fetch() {
                if (fetched || done) return;
                Object nextFn = iterObj.getMember("next");
                if (!(nextFn instanceof JsCallable nextCallable)) {
                    throw JsErrorException.typeError("iterator.next is not a function");
                }
                Object step;
                if (context instanceof CoreContext cc) {
                    Object savedThis = cc.thisObject;
                    cc.thisObject = iterObj;
                    try {
                        step = nextCallable.call(cc, EMPTY_ARGS);
                    } finally {
                        cc.thisObject = savedThis;
                    }
                } else {
                    step = nextCallable.call(context, EMPTY_ARGS);
                }
                // Caller's `throw` inside next() / value-getter sets context.error rather
                // than raising a Java exception (the engine uses cooperative stop-flags for
                // JS-side flow control). Propagate by marking the iterator exhausted —
                // the for-of / spread / Array.from loops then unwind, and the still-set
                // context.error reaches their caller.
                if (isJsErrored(context)) { done = true; return; }
                if (!(step instanceof ObjectLike stepObj)) {
                    throw JsErrorException.typeError("iterator result is not an object");
                }
                if (Terms.isTruthy(readMember(stepObj, "done", context))) {
                    done = true;
                    return;
                }
                if (isJsErrored(context)) { done = true; return; }
                pending = readMember(stepObj, "value", context);
                if (isJsErrored(context)) { done = true; return; }
                fetched = true;
            }

            @Override
            public boolean hasNext() {
                fetch();
                return !done;
            }

            @Override
            public Object next() {
                fetch();
                if (done) {
                    throw new NoSuchElementException();
                }
                fetched = false;
                Object v = pending;
                pending = null;
                return v;
            }
        };
    }

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static boolean isJsErrored(Context context) {
        return context instanceof CoreContext cc && cc.isError();
    }

    /** Reads a slot, invoking an accessor getter if present (so user iterators with
     *  `get value() { ... }` semantics — common in spec tests — surface their getter
     *  errors at iteration time). Routes through the receiver-aware
     *  {@link ObjectLike#getMember(String, Object, CoreContext)}; a non-CoreContext
     *  context yields {@code undefined} for accessor descriptors (no thread for
     *  thisObject swap). */
    private static Object readMember(ObjectLike obj, String name, Context context) {
        CoreContext cc = context instanceof CoreContext c ? c : null;
        return obj.getMember(name, obj, cc);
    }

    private static String describe(Object source) {
        if (source == null) return "null";
        if (source == Terms.UNDEFINED) return "undefined";
        return Terms.toStringCoerce(source, null);
    }
}

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript Object constructor function.
 * <p>
 * Static methods are wrapped in {@link JsBuiltinMethod} (per the JsMath /
 * JsNumberConstructor template) so they expose spec {@code length} and
 * {@code name}; method instances are cached per-Engine in {@code methodCache}
 * for stable identity and tombstone application. {@link #hasOwnIntrinsic} /
 * {@link #getOwnAttrs} declare each method, plus the {@code prototype} slot,
 * with the standard built-in attributes
 * ({@code {writable: true, enumerable: false, configurable: true}} for
 * methods; all-false for {@code prototype}).
 */
class JsObjectConstructor extends JsFunction {

    static final JsObjectConstructor INSTANCE = new JsObjectConstructor();

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    private JsObjectConstructor() {
        this.name = "Object";
        this.length = 1;
        installIntrinsics();
        registerForEngineReset();
    }

    private void installIntrinsics() {
        defineOwn("keys", new JsBuiltinMethod("keys", 1, (JsCallable) this::keys), METHOD_ATTRS);
        defineOwn("values", new JsBuiltinMethod("values", 1, (JsCallable) this::values), METHOD_ATTRS);
        defineOwn("entries", new JsBuiltinMethod("entries", 1, (JsCallable) this::entries), METHOD_ATTRS);
        defineOwn("assign", new JsBuiltinMethod("assign", 2, (JsCallable) this::assign), METHOD_ATTRS);
        defineOwn("fromEntries", new JsBuiltinMethod("fromEntries", 1, (JsInvokable) this::fromEntries), METHOD_ATTRS);
        defineOwn("is", new JsBuiltinMethod("is", 2, (JsInvokable) this::is), METHOD_ATTRS);
        defineOwn("create", new JsBuiltinMethod("create", 2, (JsCallable) this::create), METHOD_ATTRS);
        defineOwn("getPrototypeOf", new JsBuiltinMethod("getPrototypeOf", 1, (JsInvokable) this::getPrototypeOf), METHOD_ATTRS);
        defineOwn("setPrototypeOf", new JsBuiltinMethod("setPrototypeOf", 2, (JsInvokable) this::setPrototypeOf), METHOD_ATTRS);
        defineOwn("hasOwn", new JsBuiltinMethod("hasOwn", 2, (JsInvokable) this::hasOwn), METHOD_ATTRS);
        defineOwn("getOwnPropertyNames", new JsBuiltinMethod("getOwnPropertyNames", 1, (JsInvokable) this::getOwnPropertyNames), METHOD_ATTRS);
        defineOwn("getOwnPropertyDescriptor", new JsBuiltinMethod("getOwnPropertyDescriptor", 2, (JsInvokable) this::getOwnPropertyDescriptor), METHOD_ATTRS);
        defineOwn("getOwnPropertyDescriptors", new JsBuiltinMethod("getOwnPropertyDescriptors", 1, (JsInvokable) this::getOwnPropertyDescriptors), METHOD_ATTRS);
        defineOwn("defineProperty", new JsBuiltinMethod("defineProperty", 3, (JsCallable) this::defineProperty), METHOD_ATTRS);
        defineOwn("defineProperties", new JsBuiltinMethod("defineProperties", 2, (JsCallable) this::defineProperties), METHOD_ATTRS);
        defineOwn("isExtensible", new JsBuiltinMethod("isExtensible", 1, (JsInvokable) this::isExtensible), METHOD_ATTRS);
        defineOwn("preventExtensions", new JsBuiltinMethod("preventExtensions", 1, (JsInvokable) this::preventExtensions), METHOD_ATTRS);
        defineOwn("isSealed", new JsBuiltinMethod("isSealed", 1, (JsInvokable) this::isSealed), METHOD_ATTRS);
        defineOwn("seal", new JsBuiltinMethod("seal", 1, (JsInvokable) this::seal), METHOD_ATTRS);
        defineOwn("isFrozen", new JsBuiltinMethod("isFrozen", 1, (JsInvokable) this::isFrozen), METHOD_ATTRS);
        defineOwn("freeze", new JsBuiltinMethod("freeze", 1, (JsInvokable) this::freeze), METHOD_ATTRS);
        defineOwn("prototype", JsObjectPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    public Object call(Context context, Object... args) {
        // Spec §20.1.1.1 Object(value): if value is null/undefined, return a fresh
        // empty object; otherwise return ToObject(value). Pass through
        // anything that's already an object (ObjectLike); for primitives,
        // route through the matching wrapper so e.g.
        // {@code Object("abc").length === 3}, {@code typeof Object("abc") === "object"},
        // and downstream {@code ToString} dispatch sees the right prototype.
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return new JsObject();
        }
        Object v = args[0];
        if (v instanceof ObjectLike) {
            return v;
        }
        // Primitive boxing — ES §7.1.18 ToObject. The wrappers' .length /
        // .toString / valueOf chain ensures regex / String.prototype.* see
        // the boxed primitive correctly when used as a `this` or `arg`.
        if (v instanceof String s) return new JsString(s);
        if (v instanceof Boolean b) return new JsBoolean(b);
        if (v instanceof Number n) return new JsNumber(n);
        return new JsObject();
    }

    @Override
    public boolean isConstructable() {
        return true;
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        installIntrinsics();
    }

    // -------------------------------------------------------------------------
    // Extensibility (preventExtensions / seal / freeze) and their predicates.
    //
    // Per spec, these methods accept any value and only act on objects.
    // Non-objects pass through unchanged (was a TypeError pre-ES2015 but
    // ES2015+ relaxed it — `Object.freeze(1) === 1`). The predicates return
    // true for non-objects (a primitive cannot be extended/sealed/frozen
    // further than it already is).
    //
    // Dispatch routes through {@link ObjectLike} — both {@link JsObject} and
    // {@link JsArray} override the defaults to enforce the three-bit state.
    // Other ObjectLikes (raw Map host bridges) inherit the no-op defaults.
    // -------------------------------------------------------------------------

    private Object isExtensible(Object[] args) {
        if (args.length == 0) return false;
        Object v = args[0];
        if (v instanceof ObjectLike ol) return ol.isExtensible();
        return false;
    }

    private Object preventExtensions(Object[] args) {
        if (args.length == 0) return Terms.UNDEFINED;
        Object v = args[0];
        if (v instanceof ObjectLike ol) ol.setExtensible(false);
        return v;
    }

    private Object isSealed(Object[] args) {
        if (args.length == 0) return true;
        Object v = args[0];
        if (v instanceof ObjectLike ol) return ol.isSealed();
        // Per spec: non-objects are considered sealed (and frozen) since they
        // have no extensibility/configurability that could change.
        return true;
    }

    private Object seal(Object[] args) {
        if (args.length == 0) return Terms.UNDEFINED;
        Object v = args[0];
        if (v instanceof ObjectLike ol) ol.setSealed(true);
        return v;
    }

    private Object isFrozen(Object[] args) {
        if (args.length == 0) return true;
        Object v = args[0];
        if (v instanceof ObjectLike ol) return ol.isFrozen();
        return true;
    }

    private Object freeze(Object[] args) {
        if (args.length == 0) return Terms.UNDEFINED;
        Object v = args[0];
        if (v instanceof ObjectLike ol) ol.setFrozen(true);
        return v;
    }

    // Static methods

    private Object keys(Context context, Object[] args) {
        CoreContext cc = context instanceof CoreContext c ? c : null;
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0], cc)) {
            result.add(kv.key());
        }
        return result;
    }

    private Object values(Context context, Object[] args) {
        CoreContext cc = context instanceof CoreContext c ? c : null;
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0], cc)) {
            result.add(kv.value());
        }
        return result;
    }

    private Object entries(Context context, Object[] args) {
        CoreContext cc = context instanceof CoreContext c ? c : null;
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0], cc)) {
            List<Object> entry = new ArrayList<>();
            entry.add(kv.key());
            entry.add(kv.value());
            result.add(entry);
        }
        return result;
    }

    private Object assign(Context context, Object[] args) {
        if (args.length == 0) {
            return new LinkedHashMap<>();
        }
        if (args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValue kv : Terms.toIterable(args[0], cc)) {
            result.put(kv.key(), kv.value());
        }
        for (int i = 1; i < args.length; i++) {
            for (KeyValue kv : Terms.toIterable(args[i], cc)) {
                result.put(kv.key(), kv.value());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object fromEntries(Object[] args) {
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValue kv : Terms.toIterable(args[0])) {
            if (kv.value() instanceof List) {
                List<Object> list = (List<Object>) kv.value();
                if (!list.isEmpty()) {
                    Object key = list.getFirst();
                    if (key != null) {
                        Object value = null;
                        if (list.size() > 1) {
                            value = list.get(1);
                        }
                        result.put(key.toString(), value);
                    }
                }
            }
        }
        return result;
    }

    private Object is(Object[] args) {
        if (args.length < 2) {
            return false;
        }
        return Terms.sameValue(args[0], args[1]);
    }

    private Object create(Context context, Object[] args) {
        JsObject newObj = new JsObject();
        if (args.length > 0 && args[0] instanceof ObjectLike proto) {
            newObj.setPrototype(proto);
        }
        // Second argument: property descriptors (same shape as Object.defineProperties).
        if (args.length > 1 && args[1] != null && args[1] != Terms.UNDEFINED) {
            defineProperties(context, new Object[]{newObj, args[1]});
        }
        return newObj;
    }

    private Object getPrototypeOf(Object[] args) {
        if (args.length > 0) {
            if (args[0] instanceof JsObject obj) {
                return obj.getPrototype();
            }
            if (args[0] instanceof JsArray arr) {
                return arr.getPrototype();
            }
        }
        return null;
    }

    private Object setPrototypeOf(Object[] args) {
        if (args.length >= 2) {
            ObjectLike proto = null;
            if (args[1] instanceof ObjectLike p) {
                proto = p;
            }
            if (args[0] instanceof JsObject obj) {
                obj.setPrototype(proto);
                return args[0];
            }
            if (args[0] instanceof JsArray arr) {
                arr.setPrototype(proto);
                return args[0];
            }
        }
        return args.length > 0 ? args[0] : null;
    }

    private Object hasOwn(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        if (args.length < 2) {
            return false;
        }
        String prop = Terms.toPropertyKey(args[1]);
        return isOwnKey(args[0], prop);
    }

    private Object getOwnPropertyNames(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        return new ArrayList<>(ownKeys(args[0]));
    }

    private Object getOwnPropertyDescriptor(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        if (args.length < 2) {
            return Terms.UNDEFINED;
        }
        String prop = Terms.toPropertyKey(args[1]);
        if (!isOwnKey(args[0], prop)) {
            return Terms.UNDEFINED;
        }
        return buildDescriptor(args[0], prop, ownAttrs(args[0], prop));
    }

    private Object getOwnPropertyDescriptors(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        // toMap() entries plus any intrinsic keys that are reachable but not
        // in toMap. Built-in constructors / prototypes report their
        // intrinsic name set via {@link JsObject#ownIntrinsicNames()} —
        // single source of truth, no static probe list to drift.
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(ownKeys(args[0]));
        if (args[0] instanceof JsObject jo) {
            for (String name : jo.ownIntrinsicNames()) {
                if (!keys.contains(name) && jo.hasOwnIntrinsic(name)) {
                    keys.add(name);
                }
            }
        }
        for (String key : keys) {
            result.put(key, buildDescriptor(args[0], key, ownAttrs(args[0], key)));
        }
        return result;
    }

    private static boolean isOwnKey(Object obj, String key) {
        if (obj instanceof ObjectLike ol) return ol.isOwnProperty(key);
        if (ownKeys(obj).contains(key)) return true;
        return false;
    }

    /**
     * Build the descriptor object returned by {@code getOwnPropertyDescriptor}.
     * Routes through {@link #ownAccessorSlot} — if an accessor descriptor
     * lives at {@code key}, return the accessor shape ({@code get / set /
     * enumerable / configurable}); otherwise return the data shape
     * ({@code value / writable / enumerable / configurable}).
     */
    private static Map<String, Object> buildDescriptor(Object obj, String key, byte attrs) {
        Map<String, Object> desc = new LinkedHashMap<>();
        AccessorSlot acc = ownAccessorSlot(obj, key);
        if (acc != null) {
            desc.put("get", acc.getter == null ? Terms.UNDEFINED : acc.getter);
            desc.put("set", acc.setter == null ? Terms.UNDEFINED : acc.setter);
        } else {
            desc.put("value", ownGet(obj, key));
            desc.put("writable", (attrs & JsObject.WRITABLE) != 0);
        }
        desc.put("enumerable", (attrs & JsObject.ENUMERABLE) != 0);
        desc.put("configurable", (attrs & JsObject.CONFIGURABLE) != 0);
        return desc;
    }

    /** Returns the own {@link AccessorSlot} at {@code key} when one exists,
     *  {@code null} otherwise (own data property, missing, or unsupported
     *  type). Scoped to own properties only — for the chain-walking variant
     *  see {@code PropertyAccess#findAccessorInChain}. */
    private static AccessorSlot ownAccessorSlot(Object obj, String key) {
        PropertySlot s = PropertyAccess.ownSlot(obj, key);
        return s instanceof AccessorSlot acc ? acc : null;
    }

    @SuppressWarnings("unchecked")
    private Object defineProperty(Context context, Object[] args) {
        if (args.length < 1 || !(args[0] instanceof ObjectLike || args[0] instanceof Map)) {
            throw JsErrorException.typeError("Object.defineProperty called on non-object");
        }
        if (args.length < 2) {
            throw JsErrorException.typeError("property key is null");
        }
        if (args.length < 3 || args[2] == null || args[2] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Property descriptor must be an object");
        }
        String prop = Terms.toPropertyKey(args[1]);
        Object desc = args[2];
        ObjectLike descObj;
        Map<String, Object> descMap;
        if (desc instanceof ObjectLike ol) {
            descObj = ol;
            descMap = ol.toMap();
        } else if (desc instanceof Map) {
            descObj = null;
            descMap = (Map<String, Object>) desc;
        } else {
            throw JsErrorException.typeError("Property descriptor must be an object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        // Spec ToPropertyDescriptor (§6.2.5.5) checks each field via spec
        // HasProperty, which chain-walks the descriptor's prototype, then
        // reads the value via [[Get]]. For ObjectLike descriptors,
        // {@link #descHas} walks the prototype chain so a descriptor like
        // {@code Object.create({writable: true})} is recognised as having
        // a {@code writable} field. Raw Java Maps have no proto — fall
        // back to {@code containsKey}.
        boolean hasGet = descHas(descObj, descMap, "get");
        boolean hasSet = descHas(descObj, descMap, "set");
        boolean hasValue = descHas(descObj, descMap, "value");
        boolean hasWritable = descHas(descObj, descMap, "writable");
        boolean hasEnumerable = descHas(descObj, descMap, "enumerable");
        boolean hasConfigurable = descHas(descObj, descMap, "configurable");
        boolean isAccessor = hasGet || hasSet;
        boolean isData = hasValue || hasWritable;
        if (isAccessor && isData) {
            throw JsErrorException.typeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
        }

        Object target = args[0];
        boolean keyExists = ownKeys(target).contains(prop);

        // Extensibility check — ObjectLikes that don't model state inherit
        // the perpetually-extensible default and pass through.
        if (!keyExists && target instanceof ObjectLike ol && !ol.isExtensible()) {
            throw JsErrorException.typeError("Cannot define property " + prop + ", object is not extensible");
        }

        // Validate accessor shapes early so we can fall through to the unified write below.
        JsCallable newGetter = null;
        JsCallable newSetter = null;
        if (isAccessor) {
            if (hasGet) {
                Object g = descRead(descObj, descMap, "get", cc);
                if (g != null && g != Terms.UNDEFINED) {
                    if (!(g instanceof JsCallable c)) {
                        throw JsErrorException.typeError("Getter must be a function");
                    }
                    newGetter = c;
                }
            }
            if (hasSet) {
                Object s = descRead(descObj, descMap, "set", cc);
                if (s != null && s != Terms.UNDEFINED) {
                    if (!(s instanceof JsCallable c)) {
                        throw JsErrorException.typeError("Setter must be a function");
                    }
                    newSetter = c;
                }
            }
        }

        // Spec ArraySetLength preface: when target is an Array and prop is
        // "length" with a value present, ToUint32 + ToNumber + RangeError run
        // BEFORE descriptor validation (test262 define-own-prop-length-overflow-
        // order.js asserts RangeError beats TypeError). The double valueOf
        // dispatch is observable (define-own-prop-length-coercion-order.js
        // expects valueOfCalls === 2) and may mutate the array's own length
        // descriptor — re-fetch oldAttrs / existing afterward.
        boolean isArrayLength = target instanceof JsArray && "length".equals(prop) && hasValue;
        Long coercedLength = null;
        if (isArrayLength) {
            coercedLength = JsArray.coerceToUint32(descRead(descObj, descMap, "value", cc), cc);
        }

        Object existing = keyExists ? ownGet(target, prop) : null;
        AccessorSlot existingAcc = keyExists ? ownAccessorSlot(target, prop) : null;
        boolean existingIsAccessor = existingAcc != null;
        byte oldAttrs = ownAttrs(target, prop);

        // Compute new attribute byte. New keys default to all-false per spec
        // ValidateAndApplyPropertyDescriptor (defineProperty's defaults differ
        // from [[Set]]'s all-true defaults). Existing keys preserve missing fields.
        byte newAttrs = keyExists ? oldAttrs : 0;
        if (hasWritable) {
            newAttrs = setBit(newAttrs, JsObject.WRITABLE, Terms.isTruthy(descRead(descObj, descMap, "writable", cc)));
        }
        if (hasEnumerable) {
            newAttrs = setBit(newAttrs, JsObject.ENUMERABLE, Terms.isTruthy(descRead(descObj, descMap, "enumerable", cc)));
        }
        if (hasConfigurable) {
            newAttrs = setBit(newAttrs, JsObject.CONFIGURABLE, Terms.isTruthy(descRead(descObj, descMap, "configurable", cc)));
        }
        // Accessor descriptors carry no writable bit; clear it so the stored byte
        // reflects "not applicable" rather than a stale write/preserved truth.
        if (isAccessor) {
            newAttrs = (byte) (newAttrs & ~JsObject.WRITABLE);
        }

        // Configurability check on existing keys (spec ValidateAndApplyPropertyDescriptor).
        // If the key is non-configurable, almost every change is forbidden — only:
        //   1. Setting the same value (no-op redefine).
        //   2. Toggling writable from true → false on a data property.
        if (keyExists && (oldAttrs & JsObject.CONFIGURABLE) == 0) {
            // configurable cannot flip false → true
            if (hasConfigurable && (newAttrs & JsObject.CONFIGURABLE) != 0) {
                throw JsErrorException.typeError("Cannot redefine property: " + prop);
            }
            // enumerable cannot change
            if (hasEnumerable && ((oldAttrs ^ newAttrs) & JsObject.ENUMERABLE) != 0) {
                throw JsErrorException.typeError("Cannot redefine property: " + prop);
            }
            // Cannot switch between data and accessor shapes
            if (existingIsAccessor != isAccessor && (isAccessor || isData)) {
                throw JsErrorException.typeError("Cannot redefine property: " + prop);
            }
            if (existingIsAccessor && isAccessor) {
                // Accessor→accessor: get / set cannot change unless they match the existing.
                JsCallable mergedGet = hasGet ? newGetter : existingAcc.getter;
                JsCallable mergedSet = hasSet ? newSetter : existingAcc.setter;
                if (mergedGet != existingAcc.getter || mergedSet != existingAcc.setter) {
                    throw JsErrorException.typeError("Cannot redefine property: " + prop);
                }
            } else if (!existingIsAccessor && isData) {
                // Data → data on non-configurable: writable cannot flip false → true.
                boolean oldWritable = (oldAttrs & JsObject.WRITABLE) != 0;
                boolean newWritable = (newAttrs & JsObject.WRITABLE) != 0;
                if (!oldWritable && newWritable) {
                    throw JsErrorException.typeError("Cannot redefine property: " + prop);
                }
                // If !writable, value cannot change. Spec uses SameValue —
                // {@code +0} and {@code -0} are *not* the same value here,
                // even though they are {@code ===}-equal.
                if (!oldWritable && hasValue) {
                    Object newValue = isArrayLength ? coercedLength : descRead(descObj, descMap, "value", cc);
                    if (!Terms.sameValue(existing, newValue)) {
                        throw JsErrorException.typeError("Cannot redefine property: " + prop);
                    }
                }
            }
        }

        // Apply.
        if (isAccessor) {
            // Merge with existing accessor: defining only `get` keeps the existing setter,
            // and vice versa. Matches the literal path's merge behavior in evalLitObject.
            JsCallable getter = newGetter;
            JsCallable setter = newSetter;
            if (existingIsAccessor) {
                if (!hasGet) getter = existingAcc.getter;
                if (!hasSet) setter = existingAcc.setter;
            }
            applyDefineAccessor(target, prop, getter, setter, newAttrs);
        } else if (isArrayLength) {
            // Skip the generic applyDefine path — defineLength bypasses the
            // re-coercion that handleLengthAssign would run, since coercedLength
            // already encodes the spec-validated Uint32.
            boolean ok = ((JsArray) target).defineLength(coercedLength.intValue(), newAttrs);
            if (!ok) {
                // Non-configurable index in truncate range blocked the rest;
                // partial-truncate already applied — report TypeError per spec.
                throw JsErrorException.typeError("Cannot redefine property: length");
            }
        } else if (hasValue) {
            applyDefine(target, prop, descRead(descObj, descMap, "value", cc), newAttrs);
        } else if (!keyExists) {
            // New key created via attribute-only descriptor: spec says value defaults
            // to undefined.
            applyDefine(target, prop, Terms.UNDEFINED, newAttrs);
        } else if (existingIsAccessor && !isAccessor) {
            // Switching accessor → data with no value specified: spec defaults value to undefined.
            applyDefine(target, prop, Terms.UNDEFINED, newAttrs);
        } else {
            // Existing data key, only attribute changes — preserve existing value.
            applyDefine(target, prop, existing, newAttrs);
        }
        return target;
    }

    private static byte setBit(byte attrs, byte mask, boolean on) {
        return on ? (byte) (attrs | mask) : (byte) (attrs & ~mask);
    }

    /** Spec ToPropertyDescriptor reads each field via [[Get]]. For an
     *  ObjectLike descriptor, route through the receiver-aware getMember
     *  so accessor descriptors on the descriptor itself dispatch through
     *  their getter. For plain Java Maps, the snapshot is authoritative. */
    private static Object descRead(ObjectLike descObj, Map<String, Object> descMap, String name, CoreContext ctx) {
        if (descObj != null) {
            return descObj.getMember(name, descObj, ctx);
        }
        return descMap.get(name);
    }

    /** Spec {@code HasProperty} on the descriptor (§6.2.5.5 step 2 etc.).
     *  Walks the prototype chain via {@link ObjectLike#getPrototype()} so
     *  descriptors built via {@code Object.create(parentDesc)} or class
     *  inheritance see fields installed on the parent. Raw Java Maps have
     *  no proto — own-key {@code containsKey} is authoritative. */
    private static boolean descHas(ObjectLike descObj, Map<String, Object> descMap, String name) {
        if (descObj != null) {
            for (ObjectLike o = descObj; o != null; o = o.getPrototype()) {
                if (o.isOwnProperty(name)) return true;
            }
            return false;
        }
        return descMap.containsKey(name);
    }

    private static void applyDefine(Object target, String key, Object value, byte attrs) {
        if (target instanceof JsObject jo) {
            jo.defineOwn(key, value, attrs);
        } else if (target instanceof JsArray ja) {
            ja.defineOwn(key, value, attrs);
        } else {
            ownPut(target, key, value);
        }
    }

    private static void applyDefineAccessor(Object target, String key, JsCallable getter, JsCallable setter, byte attrs) {
        if (target instanceof JsObject jo) {
            jo.defineOwnAccessor(key, getter, setter, attrs);
        } else if (target instanceof JsArray ja) {
            ja.defineOwnAccessor(key, getter, setter, attrs);
        } else if (target instanceof Prototype p) {
            // Prototype surface (e.g. String.prototype, Array.prototype) needs
            // accessor support so {@code Object.defineProperty(Foo.prototype,
            // "x", {get: …})} works — instances inheriting from the prototype
            // resolve the accessor via the chain walk in PropertyAccess.
            p.defineOwnAccessor(key, getter, setter, attrs);
        }
        // Other ObjectLikes (Map, raw List, etc.) don't model accessor
        // descriptors. No reachable test path exercises that today.
    }

    private static byte ownAttrs(Object obj, String key) {
        if (obj instanceof JsObject jo) return jo.getOwnAttrs(key);
        if (obj instanceof JsArray ja) return ja.getOwnAttrs(key);
        if (obj instanceof Prototype p) return p.getOwnAttrs(key);
        return JsObject.ATTRS_DEFAULT;
    }

    private Object defineProperties(Context context, Object[] args) {
        if (args.length < 1 || !(args[0] instanceof ObjectLike || args[0] instanceof Map)) {
            throw JsErrorException.typeError("Object.defineProperties called on non-object");
        }
        Object source = args.length < 2 ? null : args[1];
        if (source == null || source == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        // Spec ToObject(primitive) yields a wrapper. Boolean / number / empty
        // string wrappers have no enumerable own keys, so the loop iterates
        // nothing and returns the target — {@code ownKeys} below returns an
        // empty set for those. A non-empty string wrapper exposes indexed
        // characters as own properties; reading the first character and
        // running ToPropertyDescriptor on it lands on TypeError ("Property
        // descriptor must be an object"). Short-circuit that here rather
        // than wiring full wrapper iteration.
        if (source instanceof String s && !s.isEmpty()) {
            throw JsErrorException.typeError("Property descriptor must be an object");
        }
        // Spec §20.1.2.3 walks enumerable own keys via [[OwnPropertyKeys]] +
        // [[GetOwnProperty]], reading each descriptor via [[Get]] so accessor
        // descriptors on the source dispatch through their getter. ownKeys
        // covers the full key set for arrays / globalThis / boxed primitives
        // (where {@code jsEntries} is array-style index-only); the explicit
        // enumerable filter + receiver-aware getMember below is the single
        // source of truth here.
        CoreContext cc = context instanceof CoreContext c ? c : null;
        for (String key : ownKeys(source)) {
            if (!isEnumerableOwn(source, key)) continue;
            Object value = source instanceof ObjectLike ol
                    ? ol.getMember(key, ol, cc)
                    : ((Map<?, ?>) source).get(key);
            defineProperty(context, new Object[]{args[0], key, value});
        }
        return args[0];
    }

    /** Spec {@code IsEnumerableOwn}: own + enumerable. Dispatches through
     *  the storage-specific {@code getOwnAttrs} so JsObject / JsArray /
     *  Prototype subclass overrides (e.g. {@link JsMath} stripping the
     *  enumerable bit on its built-in methods) apply uniformly. */
    private static boolean isEnumerableOwn(Object obj, String key) {
        byte attrs;
        if (obj instanceof JsObject jo) attrs = jo.getOwnAttrs(key);
        else if (obj instanceof JsArray ja) attrs = ja.getOwnAttrs(key);
        else if (obj instanceof Prototype p) attrs = p.getOwnAttrs(key);
        else return true; // raw Java Map / external — best-effort enumerable
        return (attrs & JsObject.ENUMERABLE) != 0;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> ownKeys(Object obj) {
        if (obj instanceof JsArray ja) {
            // Spec ordering: integer indices ascending, then string keys in
            // insertion order, then "length". Matches what V8/SpiderMonkey
            // expose to Object.keys / getOwnPropertyNames / Reflect.ownKeys.
            // Spec ordering: integer indices ascending (skipping holes so
            // [0,,2] reports ["0","2"] — JsArray.HOLE marks sparse slots),
            // then string keys in toMap insertion order, then "length".
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            for (int i = 0, n = ja.size(); i < n; i++) {
                if (ja.isOwnProperty(Integer.toString(i))) {
                    keys.add(Integer.toString(i));
                }
            }
            for (String k : ja.toMap().keySet()) {
                keys.add(k);
            }
            keys.add("length");
            return keys;
        }
        if (obj instanceof ObjectLike ol) return ol.toMap().keySet();
        if (obj instanceof Map<?, ?> m) return ((Map<String, Object>) m).keySet();
        return java.util.Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private static Object ownGet(Object obj, String key) {
        if (obj instanceof ObjectLike ol) {
            // toMap entries take precedence — user-set values shadow intrinsics.
            // For names absent from toMap but reported as own (intrinsics on
            // JsObject / JsArray indices+length / Prototype built-ins), route
            // through getMember to surface the live value. JsObject's
            // isOwnProperty already covers the resolveOwnIntrinsic-derived
            // surface (collapsed in S5+), so a single isOwnProperty check
            // suffices across all subclasses.
            Map<String, Object> m = ol.toMap();
            if (m.containsKey(key)) return m.get(key);
            if (ol.isOwnProperty(key)) return ol.getMember(key);
            return Terms.UNDEFINED;
        }
        if (obj instanceof Map<?, ?> m) return ((Map<String, Object>) m).get(key);
        return Terms.UNDEFINED;
    }

    @SuppressWarnings("unchecked")
    private static void ownPut(Object obj, String key, Object value) {
        if (obj instanceof ObjectLike ol) {
            ol.putMember(key, value);
        } else if (obj instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).put(key, value);
        }
    }

}

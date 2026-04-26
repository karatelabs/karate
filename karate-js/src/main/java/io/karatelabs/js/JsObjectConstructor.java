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
 * {@code name}; method instances are cached per-Engine in {@code _methodCache}
 * for stable identity and tombstone application. {@link #hasOwnIntrinsic} /
 * {@link #getOwnAttrs} declare each method, plus the {@code prototype} slot,
 * with the standard built-in attributes
 * ({@code {writable: true, enumerable: false, configurable: true}} for
 * methods; all-false for {@code prototype}).
 */
class JsObjectConstructor extends JsFunction {

    static final JsObjectConstructor INSTANCE = new JsObjectConstructor();

    private java.util.Map<String, JsBuiltinMethod> _methodCache;

    private JsObjectConstructor() {
        this.name = "Object";
        this.length = 1;
        registerForEngineReset();
    }

    @Override
    public Object call(Context context, Object... args) {
        // Spec §20.1.1.1 Object(value): if value is null/undefined, return a fresh
        // empty object; otherwise return ToObject(value). We don't model boxed
        // primitives precisely — pass through anything that's already an object
        // (ObjectLike), and return a fresh JsObject for null/undefined.
        if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) {
            return new JsObject();
        }
        Object v = args[0];
        if (v instanceof ObjectLike) {
            return v;
        }
        return new JsObject();
    }

    @Override
    public boolean isConstructable() {
        return true;
    }

    @Override
    public Object getMember(String name) {
        if (isTombstoned(name) || ownContainsKey(name)) {
            return super.getMember(name);
        }
        if (_methodCache != null) {
            JsBuiltinMethod cached = _methodCache.get(name);
            if (cached != null) return cached;
        }
        Object result = resolveMember(name);
        if (result instanceof JsBuiltinMethod jbm) {
            if (_methodCache == null) {
                _methodCache = new java.util.HashMap<>();
            }
            _methodCache.put(name, jbm);
        }
        return result;
    }

    private Object resolveMember(String name) {
        return switch (name) {
            case "keys" -> method(name, 1, (JsInvokable) this::keys);
            case "values" -> method(name, 1, (JsInvokable) this::values);
            case "entries" -> method(name, 1, (JsInvokable) this::entries);
            case "assign" -> method(name, 2, (JsInvokable) this::assign);
            case "fromEntries" -> method(name, 1, (JsInvokable) this::fromEntries);
            case "is" -> method(name, 2, (JsInvokable) this::is);
            case "create" -> method(name, 2, (JsCallable) this::create);
            case "getPrototypeOf" -> method(name, 1, (JsInvokable) this::getPrototypeOf);
            case "setPrototypeOf" -> method(name, 2, (JsInvokable) this::setPrototypeOf);
            case "hasOwn" -> method(name, 2, (JsInvokable) this::hasOwn);
            case "getOwnPropertyNames" -> method(name, 1, (JsInvokable) this::getOwnPropertyNames);
            case "getOwnPropertyDescriptor" -> method(name, 2, (JsInvokable) this::getOwnPropertyDescriptor);
            case "getOwnPropertyDescriptors" -> method(name, 1, (JsInvokable) this::getOwnPropertyDescriptors);
            case "defineProperty" -> method(name, 3, (JsCallable) this::defineProperty);
            case "defineProperties" -> method(name, 2, (JsCallable) this::defineProperties);
            case "isExtensible" -> method(name, 1, (JsInvokable) this::isExtensible);
            case "preventExtensions" -> method(name, 1, (JsInvokable) this::preventExtensions);
            case "isSealed" -> method(name, 1, (JsInvokable) this::isSealed);
            case "seal" -> method(name, 1, (JsInvokable) this::seal);
            case "isFrozen" -> method(name, 1, (JsInvokable) this::isFrozen);
            case "freeze" -> method(name, 1, (JsInvokable) this::freeze);
            case "prototype" -> JsObjectPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public boolean hasOwnIntrinsic(String name) {
        return isObjectMethod(name) || super.hasOwnIntrinsic(name);
    }

    @Override
    public byte getOwnAttrs(String name) {
        if (isObjectMethod(name)) {
            return WRITABLE | CONFIGURABLE;
        }
        if ("prototype".equals(name)) {
            // Built-in constructor prototype: all-false (overrides JsFunction's
            // user-function default of WRITABLE).
            return 0;
        }
        return super.getOwnAttrs(name);
    }

    @Override
    protected void clearEngineState() {
        super.clearEngineState();
        if (_methodCache != null) _methodCache.clear();
    }

    private static boolean isObjectMethod(String n) {
        return switch (n) {
            case "keys", "values", "entries", "assign", "fromEntries",
                 "is", "create", "getPrototypeOf", "setPrototypeOf", "hasOwn",
                 "getOwnPropertyNames", "getOwnPropertyDescriptor",
                 "getOwnPropertyDescriptors", "defineProperty", "defineProperties",
                 "isExtensible", "preventExtensions", "isSealed", "seal",
                 "isFrozen", "freeze" -> true;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Extensibility (preventExtensions / seal / freeze) and their predicates.
    //
    // Per spec, these methods accept any value and only act on objects.
    // Non-objects pass through unchanged (was a TypeError pre-ES2015 but
    // ES2015+ relaxed it — `Object.freeze(1) === 1`). The predicates return
    // true for non-objects (a primitive cannot be extended/sealed/frozen
    // further than it already is).
    // -------------------------------------------------------------------------

    private Object isExtensible(Object[] args) {
        if (args.length == 0) return false;
        return args[0] instanceof JsObject jo && jo.isExtensible();
    }

    private Object preventExtensions(Object[] args) {
        if (args.length > 0 && args[0] instanceof JsObject jo) {
            jo.preventExtensions();
        }
        return args.length > 0 ? args[0] : Terms.UNDEFINED;
    }

    private Object isSealed(Object[] args) {
        if (args.length == 0) return true;
        if (args[0] instanceof JsObject jo) return jo.isSealed();
        // Per spec: non-objects are considered sealed (and frozen) since they
        // have no extensibility/configurability that could change.
        return !(args[0] instanceof ObjectLike);
    }

    private Object seal(Object[] args) {
        if (args.length > 0 && args[0] instanceof JsObject jo) {
            jo.seal();
        }
        return args.length > 0 ? args[0] : Terms.UNDEFINED;
    }

    private Object isFrozen(Object[] args) {
        if (args.length == 0) return true;
        if (args[0] instanceof JsObject jo) return jo.isFrozen();
        return !(args[0] instanceof ObjectLike);
    }

    private Object freeze(Object[] args) {
        if (args.length > 0 && args[0] instanceof JsObject jo) {
            jo.freeze();
        }
        return args.length > 0 ? args[0] : Terms.UNDEFINED;
    }

    // Static methods

    private Object keys(Object[] args) {
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0])) {
            result.add(kv.key());
        }
        return result;
    }

    private Object values(Object[] args) {
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0])) {
            result.add(kv.value());
        }
        return result;
    }

    private Object entries(Object[] args) {
        List<Object> result = new ArrayList<>();
        for (KeyValue kv : Terms.toIterable(args[0])) {
            List<Object> entry = new ArrayList<>();
            entry.add(kv.key());
            entry.add(kv.value());
            result.add(entry);
        }
        return result;
    }

    private Object assign(Object[] args) {
        if (args.length == 0) {
            return new LinkedHashMap<>();
        }
        if (args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValue kv : Terms.toIterable(args[0])) {
            result.put(kv.key(), kv.value());
        }
        for (int i = 1; i < args.length; i++) {
            for (KeyValue kv : Terms.toIterable(args[i])) {
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
        return Terms.eq(args[0], args[1], true);
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
        if (args.length < 2 || args[1] == null) {
            return false;
        }
        String prop = args[1].toString();
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
        if (args.length < 2 || args[1] == null) {
            return Terms.UNDEFINED;
        }
        String prop = args[1].toString();
        if (!isOwnKey(args[0], prop)) {
            return Terms.UNDEFINED;
        }
        return buildDescriptor(ownGet(args[0], prop), ownAttrs(args[0], prop));
    }

    private Object getOwnPropertyDescriptors(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        // toMap() entries plus any intrinsic keys that are reachable but not in toMap.
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(ownKeys(args[0]));
        // Built-in constructors / prototypes report their intrinsics via
        // hasOwnIntrinsic. These don't appear in toMap() — discover them by
        // probing a fixed set of well-known names. The set is the union of
        // intrinsic names known to the JsFunction hierarchy plus what built-in
        // constructors typically declare; subclasses are responsible for
        // returning true from hasOwnIntrinsic for names they expose.
        if (args[0] instanceof JsObject jo) {
            for (String name : INTRINSIC_PROBE_NAMES) {
                if (!keys.contains(name) && jo.hasOwnIntrinsic(name)) {
                    keys.add(name);
                }
            }
        }
        for (String key : keys) {
            result.put(key, buildDescriptor(ownGet(args[0], key), ownAttrs(args[0], key)));
        }
        return result;
    }

    /**
     * Names probed by {@link #getOwnPropertyDescriptors} when discovering
     * intrinsic-only keys (those not in {@code toMap()}). Keep this list short
     * — it's used to enumerate descriptors for built-in constructors /
     * prototypes that don't materialize their intrinsic entries in
     * {@code _map}. The single-property {@code getOwnPropertyDescriptor} path
     * does not use this list (it consults {@code hasOwnIntrinsic} for the
     * given name directly).
     */
    private static final String[] INTRINSIC_PROBE_NAMES = {
            "length", "name", "prototype", "constructor"
    };

    private static boolean isOwnKey(Object obj, String key) {
        if (obj instanceof JsObject jo) return jo.isOwnProperty(key);
        if (obj instanceof JsArray ja) return ja.isOwnProperty(key);
        if (obj instanceof Prototype p) return p.hasOwnMember(key);
        if (ownKeys(obj).contains(key)) return true;
        return false;
    }

    /**
     * Build the descriptor object returned by {@code getOwnPropertyDescriptor}.
     * If the slot holds a {@link JsAccessor}, return the accessor shape
     * ({@code get / set / enumerable / configurable}); otherwise return the
     * data shape ({@code value / writable / enumerable / configurable}).
     */
    private static Map<String, Object> buildDescriptor(Object value, byte attrs) {
        Map<String, Object> desc = new LinkedHashMap<>();
        if (value instanceof JsAccessor acc) {
            desc.put("get", acc.getter == null ? Terms.UNDEFINED : acc.getter);
            desc.put("set", acc.setter == null ? Terms.UNDEFINED : acc.setter);
        } else {
            desc.put("value", value);
            desc.put("writable", (attrs & JsObject.WRITABLE) != 0);
        }
        desc.put("enumerable", (attrs & JsObject.ENUMERABLE) != 0);
        desc.put("configurable", (attrs & JsObject.CONFIGURABLE) != 0);
        return desc;
    }

    @SuppressWarnings("unchecked")
    private Object defineProperty(Context context, Object[] args) {
        if (args.length < 1 || !(args[0] instanceof ObjectLike || args[0] instanceof Map)) {
            throw JsErrorException.typeError("Object.defineProperty called on non-object");
        }
        if (args.length < 2 || args[1] == null) {
            throw JsErrorException.typeError("property key is null");
        }
        if (args.length < 3 || args[2] == null || args[2] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Property descriptor must be an object");
        }
        String prop = args[1].toString();
        Object desc = args[2];
        Map<String, Object> descMap;
        if (desc instanceof ObjectLike ol) {
            descMap = ol.toMap();
        } else if (desc instanceof Map) {
            descMap = (Map<String, Object>) desc;
        } else {
            throw JsErrorException.typeError("Property descriptor must be an object");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        boolean hasGet = descMap.containsKey("get");
        boolean hasSet = descMap.containsKey("set");
        boolean hasValue = descMap.containsKey("value");
        boolean hasWritable = descMap.containsKey("writable");
        boolean hasEnumerable = descMap.containsKey("enumerable");
        boolean hasConfigurable = descMap.containsKey("configurable");
        boolean isAccessor = hasGet || hasSet;
        boolean isData = hasValue || hasWritable;
        if (isAccessor && isData) {
            throw JsErrorException.typeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
        }

        Object target = args[0];
        boolean keyExists = ownKeys(target).contains(prop);

        // Extensibility check — only on JsObject (Maps and other ObjectLikes don't model it).
        if (!keyExists && target instanceof JsObject jo && !jo.isExtensible()) {
            throw JsErrorException.typeError("Cannot define property " + prop + ", object is not extensible");
        }

        // Validate accessor shapes early so we can fall through to the unified write below.
        JsCallable newGetter = null;
        JsCallable newSetter = null;
        if (isAccessor) {
            if (hasGet) {
                Object g = descMap.get("get");
                if (g != null && g != Terms.UNDEFINED) {
                    if (!(g instanceof JsCallable c)) {
                        throw JsErrorException.typeError("Getter must be a function");
                    }
                    newGetter = c;
                }
            }
            if (hasSet) {
                Object s = descMap.get("set");
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
            coercedLength = JsArray.coerceToUint32(descMap.get("value"), cc);
        }

        Object existing = keyExists ? ownGet(target, prop) : null;
        boolean existingIsAccessor = existing instanceof JsAccessor;
        byte oldAttrs = ownAttrs(target, prop);

        // Compute new attribute byte. New keys default to all-false per spec
        // ValidateAndApplyPropertyDescriptor (defineProperty's defaults differ
        // from [[Set]]'s all-true defaults). Existing keys preserve missing fields.
        byte newAttrs = keyExists ? oldAttrs : 0;
        if (hasWritable) {
            newAttrs = setBit(newAttrs, JsObject.WRITABLE, Terms.isTruthy(descMap.get("writable")));
        }
        if (hasEnumerable) {
            newAttrs = setBit(newAttrs, JsObject.ENUMERABLE, Terms.isTruthy(descMap.get("enumerable")));
        }
        if (hasConfigurable) {
            newAttrs = setBit(newAttrs, JsObject.CONFIGURABLE, Terms.isTruthy(descMap.get("configurable")));
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
                JsAccessor exAcc = (JsAccessor) existing;
                JsCallable mergedGet = hasGet ? newGetter : exAcc.getter;
                JsCallable mergedSet = hasSet ? newSetter : exAcc.setter;
                if (mergedGet != exAcc.getter || mergedSet != exAcc.setter) {
                    throw JsErrorException.typeError("Cannot redefine property: " + prop);
                }
            } else if (!existingIsAccessor && isData) {
                // Data → data on non-configurable: writable cannot flip false → true.
                boolean oldWritable = (oldAttrs & JsObject.WRITABLE) != 0;
                boolean newWritable = (newAttrs & JsObject.WRITABLE) != 0;
                if (!oldWritable && newWritable) {
                    throw JsErrorException.typeError("Cannot redefine property: " + prop);
                }
                // If !writable, value cannot change.
                if (!oldWritable && hasValue) {
                    Object newValue = isArrayLength ? coercedLength : descMap.get("value");
                    if (!Terms.eq(existing, newValue, true)) {
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
                JsAccessor exAcc = (JsAccessor) existing;
                if (!hasGet) getter = exAcc.getter;
                if (!hasSet) setter = exAcc.setter;
            }
            applyDefine(target, prop, new JsAccessor(getter, setter), newAttrs);
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
            applyDefine(target, prop, descMap.get("value"), newAttrs);
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

    private static void applyDefine(Object target, String key, Object value, byte attrs) {
        if (target instanceof JsObject jo) {
            jo.defineOwn(key, value, attrs);
        } else if (target instanceof JsArray ja) {
            ja.defineOwn(key, value, attrs);
        } else {
            ownPut(target, key, value);
        }
    }

    private static byte ownAttrs(Object obj, String key) {
        if (obj instanceof JsObject jo) return jo.getOwnAttrs(key);
        if (obj instanceof JsArray ja) return ja.getOwnAttrs(key);
        if (obj instanceof Prototype p) return p.getOwnAttrs(key);
        return JsObject.ATTRS_DEFAULT;
    }

    @SuppressWarnings("unchecked")
    private Object defineProperties(Context context, Object[] args) {
        if (args.length < 1 || !(args[0] instanceof ObjectLike || args[0] instanceof Map)) {
            throw JsErrorException.typeError("Object.defineProperties called on non-object");
        }
        if (args.length < 2 || args[1] == null || args[1] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Object descsObj = args[1];
        Map<String, Object> descs;
        if (descsObj instanceof ObjectLike ol) {
            descs = ol.toMap();
        } else if (descsObj instanceof Map) {
            descs = (Map<String, Object>) descsObj;
        } else {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        for (Map.Entry<String, Object> e : descs.entrySet()) {
            defineProperty(context, new Object[]{args[0], e.getKey(), e.getValue()});
        }
        return args[0];
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
            // Intrinsic own properties (length, name, prototype, etc.) live
            // in getMember switches, not in toMap(). Fall through to getMember
            // when the toMap()'s missing the key — but only after toMap reports
            // absence so user-set values still win over intrinsics.
            Map<String, Object> m = ol.toMap();
            if (m.containsKey(key)) return m.get(key);
            if (obj instanceof JsObject jo && jo.hasOwnIntrinsic(key)) {
                return ol.getMember(key);
            }
            // JsArray's intrinsics (length + canonical numeric indices) likewise
            // resolve via getMember, not toMap. isOwnProperty is the gate so we
            // only fall through for actual array indices in range / "length".
            if (obj instanceof JsArray ja && ja.isOwnProperty(key)) {
                return ol.getMember(key);
            }
            // Prototype's built-in methods (e.g. Array.prototype.push) similarly
            // live in subclass getBuiltinProperty switches — route through
            // getMember so descriptor reads pick them up.
            if (obj instanceof Prototype p && p.hasOwnMember(key)) {
                return ol.getMember(key);
            }
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

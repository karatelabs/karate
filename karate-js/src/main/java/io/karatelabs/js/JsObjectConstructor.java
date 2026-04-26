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
            case "keys" -> new JsBuiltinMethod("keys", 1, (JsInvokable) this::keys);
            case "values" -> new JsBuiltinMethod("values", 1, (JsInvokable) this::values);
            case "entries" -> new JsBuiltinMethod("entries", 1, (JsInvokable) this::entries);
            case "assign" -> new JsBuiltinMethod("assign", 2, (JsInvokable) this::assign);
            case "fromEntries" -> new JsBuiltinMethod("fromEntries", 1, (JsInvokable) this::fromEntries);
            case "is" -> new JsBuiltinMethod("is", 2, (JsInvokable) this::is);
            case "create" -> new JsBuiltinMethod("create", 2, (JsInvokable) this::create);
            case "getPrototypeOf" -> new JsBuiltinMethod("getPrototypeOf", 1, (JsInvokable) this::getPrototypeOf);
            case "setPrototypeOf" -> new JsBuiltinMethod("setPrototypeOf", 2, (JsInvokable) this::setPrototypeOf);
            case "hasOwn" -> new JsBuiltinMethod("hasOwn", 2, (JsInvokable) this::hasOwn);
            case "getOwnPropertyNames" -> new JsBuiltinMethod("getOwnPropertyNames", 1, (JsInvokable) this::getOwnPropertyNames);
            case "getOwnPropertyDescriptor" -> new JsBuiltinMethod("getOwnPropertyDescriptor", 2, (JsInvokable) this::getOwnPropertyDescriptor);
            case "getOwnPropertyDescriptors" -> new JsBuiltinMethod("getOwnPropertyDescriptors", 1, (JsInvokable) this::getOwnPropertyDescriptors);
            case "defineProperty" -> new JsBuiltinMethod("defineProperty", 3, (JsInvokable) this::defineProperty);
            case "defineProperties" -> new JsBuiltinMethod("defineProperties", 2, (JsInvokable) this::defineProperties);
            case "isExtensible" -> new JsBuiltinMethod("isExtensible", 1, (JsInvokable) this::isExtensible);
            case "preventExtensions" -> new JsBuiltinMethod("preventExtensions", 1, (JsInvokable) this::preventExtensions);
            case "isSealed" -> new JsBuiltinMethod("isSealed", 1, (JsInvokable) this::isSealed);
            case "seal" -> new JsBuiltinMethod("seal", 1, (JsInvokable) this::seal);
            case "isFrozen" -> new JsBuiltinMethod("isFrozen", 1, (JsInvokable) this::isFrozen);
            case "freeze" -> new JsBuiltinMethod("freeze", 1, (JsInvokable) this::freeze);
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

    private Object create(Object[] args) {
        JsObject newObj = new JsObject();
        if (args.length > 0 && args[0] instanceof ObjectLike proto) {
            newObj.setPrototype(proto);
        }
        // Second argument: property descriptors (same shape as Object.defineProperties).
        if (args.length > 1 && args[1] != null && args[1] != Terms.UNDEFINED) {
            defineProperties(new Object[]{newObj, args[1]});
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
        return ownKeys(args[0]).contains(prop);
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
    private Object defineProperty(Object[] args) {
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
                    Object newValue = descMap.get("value");
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
        } else {
            ownPut(target, key, value);
        }
    }

    private static byte ownAttrs(Object obj, String key) {
        if (obj instanceof JsObject jo) return jo.getOwnAttrs(key);
        if (obj instanceof Prototype p) return p.getOwnAttrs(key);
        return JsObject.ATTRS_DEFAULT;
    }

    @SuppressWarnings("unchecked")
    private Object defineProperties(Object[] args) {
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
            defineProperty(new Object[]{args[0], e.getKey(), e.getValue()});
        }
        return args[0];
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<String> ownKeys(Object obj) {
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

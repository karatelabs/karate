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
 * Provides static methods like Object.keys, Object.values, Object.assign, etc.
 */
class JsObjectConstructor extends JsFunction {

    static final JsObjectConstructor INSTANCE = new JsObjectConstructor();

    private JsObjectConstructor() {
        this.name = "Object";
        this.length = 1;
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "keys" -> (JsInvokable) this::keys;
            case "values" -> (JsInvokable) this::values;
            case "entries" -> (JsInvokable) this::entries;
            case "assign" -> (JsInvokable) this::assign;
            case "fromEntries" -> (JsInvokable) this::fromEntries;
            case "is" -> (JsInvokable) this::is;
            case "create" -> (JsInvokable) this::create;
            case "getPrototypeOf" -> (JsInvokable) this::getPrototypeOf;
            case "setPrototypeOf" -> (JsInvokable) this::setPrototypeOf;
            case "hasOwn" -> (JsInvokable) this::hasOwn;
            case "getOwnPropertyNames" -> (JsInvokable) this::getOwnPropertyNames;
            case "getOwnPropertyDescriptor" -> (JsInvokable) this::getOwnPropertyDescriptor;
            case "getOwnPropertyDescriptors" -> (JsInvokable) this::getOwnPropertyDescriptors;
            case "defineProperty" -> (JsInvokable) this::defineProperty;
            case "defineProperties" -> (JsInvokable) this::defineProperties;
            case "isExtensible" -> (JsInvokable) this::isExtensible;
            case "preventExtensions" -> (JsInvokable) this::preventExtensions;
            case "isSealed" -> (JsInvokable) this::isSealed;
            case "seal" -> (JsInvokable) this::seal;
            case "isFrozen" -> (JsInvokable) this::isFrozen;
            case "freeze" -> (JsInvokable) this::freeze;
            case "prototype" -> JsObjectPrototype.INSTANCE;
            default -> super.getMember(name);
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
        if (!ownKeys(args[0]).contains(prop)) {
            return Terms.UNDEFINED;
        }
        return buildDescriptor(ownGet(args[0], prop));
    }

    private Object getOwnPropertyDescriptors(Object[] args) {
        if (args.length < 1 || args[0] == null || args[0] == Terms.UNDEFINED) {
            throw JsErrorException.typeError("Cannot convert undefined or null to object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : ownKeys(args[0])) {
            result.put(key, buildDescriptor(ownGet(args[0], key)));
        }
        return result;
    }

    /**
     * Build the descriptor object returned by {@code getOwnPropertyDescriptor}.
     * If the slot holds a {@link JsAccessor}, return the accessor shape
     * ({@code get / set / enumerable / configurable}); otherwise return the
     * data shape ({@code value / writable / enumerable / configurable}).
     * <p>
     * Attribute bits ({@code writable / enumerable / configurable}) aren't
     * tracked per-property today — we report sensible defaults (all true for
     * user-set props). This is the point listed in TEST262.md as deferred:
     * full attribute enforcement comes in a follow-up session.
     */
    private static Map<String, Object> buildDescriptor(Object value) {
        Map<String, Object> desc = new LinkedHashMap<>();
        if (value instanceof JsAccessor acc) {
            desc.put("get", acc.getter == null ? Terms.UNDEFINED : acc.getter);
            desc.put("set", acc.setter == null ? Terms.UNDEFINED : acc.setter);
        } else {
            desc.put("value", value);
            desc.put("writable", true);
        }
        desc.put("enumerable", true);
        desc.put("configurable", true);
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
        boolean isAccessor = hasGet || hasSet;
        boolean isData = hasValue || hasWritable;
        if (isAccessor && isData) {
            throw JsErrorException.typeError(
                    "Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
        }
        if (isAccessor) {
            JsCallable getter = null;
            JsCallable setter = null;
            if (hasGet) {
                Object g = descMap.get("get");
                if (g != null && g != Terms.UNDEFINED) {
                    if (!(g instanceof JsCallable c)) {
                        throw JsErrorException.typeError("Getter must be a function");
                    }
                    getter = c;
                }
            }
            if (hasSet) {
                Object s = descMap.get("set");
                if (s != null && s != Terms.UNDEFINED) {
                    if (!(s instanceof JsCallable c)) {
                        throw JsErrorException.typeError("Setter must be a function");
                    }
                    setter = c;
                }
            }
            // Merge with existing accessor: defining only `get` keeps the existing setter,
            // and vice versa. Matches the literal path's merge behavior in evalLitObject.
            Object existing = ownGet(args[0], prop);
            if (existing instanceof JsAccessor exAcc) {
                if (!hasGet) getter = exAcc.getter;
                if (!hasSet) setter = exAcc.setter;
            }
            ownPut(args[0], prop, new JsAccessor(getter, setter));
        } else if (hasValue) {
            ownPut(args[0], prop, descMap.get("value"));
        }
        // Attribute tracking (writable/enumerable/configurable) is not modeled —
        // the value/accessor slot is the only effect of defineProperty today.
        return args[0];
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
        if (obj instanceof ObjectLike ol) return ol.toMap().get(key);
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

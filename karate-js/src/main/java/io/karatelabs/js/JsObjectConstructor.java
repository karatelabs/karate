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
            case "prototype" -> JsObjectPrototype.INSTANCE;
            default -> super.getMember(name);
        };
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
            throw new RuntimeException("assign() requires valid first argument");
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
            throw new RuntimeException("fromEntries() requires valid argument(s)");
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

}

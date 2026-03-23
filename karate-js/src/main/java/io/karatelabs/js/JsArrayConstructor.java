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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * JavaScript Array constructor function.
 * Provides static methods like Array.from, Array.isArray, Array.of, etc.
 */
class JsArrayConstructor extends JsFunction {

    static final JsArrayConstructor INSTANCE = new JsArrayConstructor();

    private JsArrayConstructor() {
        this.name = "Array";
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "from" -> (JsCallable) this::from;
            case "isArray" -> (JsInvokable) this::isArray;
            case "of" -> (JsInvokable) this::of;
            case "prototype" -> JsArrayPrototype.INSTANCE;
            default -> super.getMember(name);
        };
    }

    @Override
    public Object call(Context context, Object[] args) {
        return JsArray.create(args);
    }

    // Static methods

    @SuppressWarnings("unchecked")
    private Object from(Context context, Object[] args) {
        List<Object> results = new ArrayList<>();
        JsCallable callable = null;
        if (args.length > 1 && args[1] instanceof JsCallable) {
            callable = (JsCallable) args[1];
        }
        JsArray array;
        if (args[0] instanceof Map) {
            array = JsArray.toArray((Map<String, Object>) args[0]);
        } else if (args[0] instanceof JsArray ja) {
            array = ja;
        } else if (args[0] instanceof List) {
            array = new JsArray((List<Object>) args[0]);
        } else {
            array = new JsArray();
        }
        for (KeyValue kv : array.jsEntries()) {
            Object result = callable == null ? kv.value() : callable.call(context, new Object[]{kv.value(), kv.index()});
            results.add(result);
        }
        return results;
    }

    private Object isArray(Object[] args) {
        return args.length > 0 && args[0] instanceof List;
    }

    private Object of(Object[] args) {
        return Arrays.asList(args);
    }

}

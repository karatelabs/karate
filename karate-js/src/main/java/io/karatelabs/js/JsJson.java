/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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

import io.karatelabs.common.StringUtils;
import net.minidev.json.JSONValue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code JSON} global — allocated per-Engine via
 * {@code ContextRoot.initGlobal}. {@code stringify} and {@code parse} are
 * installed at construction time as own properties with the standard
 * built-in attributes ({@code WRITABLE | CONFIGURABLE}, non-enumerable),
 * so user code can read / overwrite them and {@code defineProperties}
 * walks them via the spec [[OwnPropertyKeys]] surface (slice JSON keys
 * via {@code Object.defineProperties(target, JSON)} where the test setup
 * stores arbitrary user keys on the JSON global).
 */
public class JsJson extends JsObject {

    private static final byte METHOD_ATTRS = WRITABLE | CONFIGURABLE | PropertySlot.INTRINSIC;

    public JsJson() {
        defineOwn("stringify", new JsBuiltinMethod("stringify", 3, stringify()), METHOD_ATTRS);
        defineOwn("parse", new JsBuiltinMethod("parse", 2, parse()), METHOD_ATTRS);
    }

    @SuppressWarnings("unchecked")
    private static JsInvokable stringify() {
        return args -> {
            Object value = args[0];
            Object replacer = args.length > 1 ? args[1] : null;
            Object space = args.length > 2 ? args[2] : null;

            // Handle replacer array (array of keys to include) - check BEFORE JsCallable
            // because JsArray implements both List and JsCallable
            if (replacer instanceof List) {
                List<String> list = (List<String>) replacer;
                if (value instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) value;
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (String k : list) {
                        if (map.containsKey(k)) {
                            result.put(k, map.get(k));
                        }
                    }
                    value = result;
                }
            }
            // Handle replacer function - transform the value tree
            else if (replacer instanceof JsCallable replacerFunc) {
                value = applyReplacerFunction(replacerFunc, "", value);
            }

            // Handle space parameter for pretty printing
            boolean pretty = false;
            String indentStr = "  ";

            if (space != null) {
                if (space instanceof Number) {
                    int indent = Math.min(((Number) space).intValue(), 10);
                    if (indent > 0) {
                        pretty = true;
                        indentStr = " ".repeat(indent);
                    }
                } else if (space instanceof String spaceStr) {
                    if (!spaceStr.isEmpty()) {
                        pretty = true;
                        indentStr = spaceStr.substring(0, Math.min(spaceStr.length(), 10));
                    }
                }
            }

            // Spec: JSON.stringify on a value tree containing BigInt throws TypeError.
            // Walk happens only on the rare path where a BigInt is actually present —
            // common case eats one root-level instanceof check.
            if (containsBigInt(value)) {
                throw JsErrorException.typeError("Do not know how to serialize a BigInt");
            }

            // Use centralized StringUtils.formatJson for both compact and pretty output
            // This ensures proper handling of JS types (undefined, JsValue wrappers)
            // lenient=false for strict JSON (double quotes), sort=false to preserve order
            return StringUtils.formatJson(value, pretty, false, false, indentStr);
        };
    }

    private static boolean containsBigInt(Object value) {
        if (value instanceof BigInteger) return true;
        if (value instanceof JsBigInt) return true;
        if (value instanceof Map<?, ?> m) {
            for (Object v : m.values()) {
                if (containsBigInt(v)) return true;
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object v : list) {
                if (containsBigInt(v)) return true;
            }
            return false;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object applyReplacerFunction(JsCallable replacerFunc, String key, Object value) {
        // First, call the replacer function for this key-value pair
        Object transformed;
        try {
            transformed = replacerFunc.call(null, new Object[]{key, value});
        } catch (Exception e) {
            throw new RuntimeException("Error in replacer function: " + e.getMessage(), e);
        }

        // If replacer returns undefined, this key should be filtered out
        if (transformed == Terms.UNDEFINED) {
            return Terms.UNDEFINED;
        }

        // Recursively apply replacer to nested objects and arrays
        if (transformed instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) transformed;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object nestedValue = applyReplacerFunction(replacerFunc, entry.getKey(), entry.getValue());
                // Skip keys where replacer returned undefined
                if (nestedValue != Terms.UNDEFINED) {
                    result.put(entry.getKey(), nestedValue);
                }
            }
            return result;
        } else if (transformed instanceof List) {
            List<Object> list = (List<Object>) transformed;
            List<Object> result = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object nestedValue = applyReplacerFunction(replacerFunc, String.valueOf(i), list.get(i));
                // For arrays, undefined values become null in JSON
                result.add(nestedValue == Terms.UNDEFINED ? null : nestedValue);
            }
            return result;
        }

        return transformed;
    }

    private static JsInvokable parse() {
        return args -> JSONValue.parseKeepingOrder((String) args[0]);
    }

}

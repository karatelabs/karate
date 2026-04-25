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

import io.karatelabs.parser.Node;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ContextRoot extends CoreContext {

    private final Engine engine;

    Consumer<String> onConsoleLog;

    ContextListener listener;
    ExternalBridge bridge;
    RunInterceptor<?> interceptor;
    DebugPointFactory<?> pointFactory;

    // Stores top-level const/let bindings for cross-eval persistence
    private List<BindValue> _topLevelBindings;

    short evalId;

    ContextRoot(Engine engine) {
        super(null, null, -1, null, ContextScope.ROOT, null);
        this.engine = engine;
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public String toString() {
        return "ROOT";
    }

    void setOnConsoleLog(Consumer<String> onConsoleLog) {
        this.onConsoleLog = onConsoleLog;
    }

    @Override
    Object get(String key) {
        if (_bindings != null && _bindings.hasMember(key)) {
            Object result = _bindings.getMember(key);
            if (result instanceof Supplier<?> supplier) {
                return supplier.get();
            }
            return result;
        }
        Object global = initGlobal(key);
        if (global != null) {
            put(key, global);
            return global;
        }
        return Terms.UNDEFINED;
    }

    @Override
    boolean hasKey(String key) {
        if (_bindings != null && _bindings.hasMember(key)) {
            return true;
        }
        return switch (key) {
            case "console", "parseInt", "parseFloat", "encodeURIComponent", "decodeURIComponent",
                 "encodeURI", "decodeURI", "undefined", "Array", "Date", "Error", "Function",
                 "Infinity", "Java", "JSON", "Map", "Math", "NaN", "Number", "BigInt", "Boolean",
                 "Object", "RegExp", "Set", "String", "TypeError", "ReferenceError", "RangeError",
                 "SyntaxError", "URIError", "EvalError", "TextEncoder", "TextDecoder", "Uint8Array",
                 "isNaN", "isFinite", "eval", "Symbol", "Reflect" -> true;
            default -> false;
        };
    }

    void addBinding(String name, BindScope scope) {
        if (_topLevelBindings == null) {
            _topLevelBindings = new ArrayList<>();
        }
        _topLevelBindings.add(new BindValue(name, null, scope, true));
    }

    BindValue getBindValue(String key) {
        if (_topLevelBindings != null) {
            for (BindValue bv : _topLevelBindings) {
                if (bv.name.equals(key)) {
                    return bv;
                }
            }
        }
        return null;
    }

    private Object initGlobal(String key) {
        return switch (key) {
            case "console" -> new JsConsole(this);
            case "parseInt" -> (JsInvokable) args -> {
                int radix = args.length > 1 ? Terms.objectToNumber(args[1]).intValue() : 0;
                return Terms.parseInt(args[0] + "", radix);
            };
            case "parseFloat" -> (JsInvokable) args -> Terms.parseFloat(args[0] + "", false);
            case "isNaN" -> (JsInvokable) args -> {
                if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) return true;
                Number n = Terms.objectToNumber(args[0]);
                return n == null || Double.isNaN(n.doubleValue());
            };
            case "isFinite" -> (JsInvokable) args -> {
                if (args.length == 0 || args[0] == null || args[0] == Terms.UNDEFINED) return false;
                Number n = Terms.objectToNumber(args[0]);
                if (n == null) return false;
                double d = n.doubleValue();
                return !Double.isNaN(d) && !Double.isInfinite(d);
            };
            case "encodeURIComponent" -> (JsInvokable) args -> {
                String encoded = URLEncoder.encode(args[0] + "", StandardCharsets.UTF_8);
                return encoded.replace("+", "%20")
                        .replace("%21", "!").replace("%27", "'").replace("%28", "(")
                        .replace("%29", ")").replace("%7E", "~").replace("%2A", "*");
            };
            case "decodeURIComponent" -> (JsInvokable) args ->
                    URLDecoder.decode(args[0] + "", StandardCharsets.UTF_8);
            case "encodeURI" -> (JsInvokable) args -> {
                String encoded = URLEncoder.encode(args[0] + "", StandardCharsets.UTF_8);
                return encoded.replace("+", "%20")
                        .replace("%21", "!").replace("%23", "#").replace("%24", "$")
                        .replace("%26", "&").replace("%27", "'").replace("%28", "(")
                        .replace("%29", ")").replace("%2A", "*").replace("%2B", "+")
                        .replace("%2C", ",").replace("%2F", "/").replace("%3A", ":")
                        .replace("%3B", ";").replace("%3D", "=").replace("%3F", "?")
                        .replace("%40", "@").replace("%7E", "~");
            };
            case "decodeURI" -> (JsInvokable) args ->
                    URLDecoder.decode(args[0] + "", StandardCharsets.UTF_8);
            case "undefined" -> Terms.UNDEFINED;
            case "eval" -> (JsInvokable) args -> {
                if (args.length == 0) return Terms.UNDEFINED;
                Object src = args[0];
                // per ES spec: non-string arguments are returned unchanged
                if (!(src instanceof String s)) {
                    return src;
                }
                // indirect-eval semantics: evaluate in the global (root) scope
                return engine.evalRaw(s);
            };
            case "Array" -> JsArrayConstructor.INSTANCE;
            case "Date" -> JsDateConstructor.INSTANCE;
            case "Function" -> JsFunctionConstructor.INSTANCE;
            case "Error" -> new JsError(null, "Error", null);
            case "Infinity" -> Double.POSITIVE_INFINITY;
            case "Java" -> new JsJava(bridge);
            case "JSON" -> new JsJson();
            case "Map" -> JsMapConstructor.INSTANCE;
            case "Math" -> new JsMath();
            case "NaN" -> Double.NaN;
            case "Number" -> JsNumberConstructor.INSTANCE;
            case "BigInt" -> JsBigIntConstructor.INSTANCE;
            case "Boolean" -> {
                JsBoolean b = new JsBoolean();
                b.builtinConstructor = true;
                yield b;
            }
            case "Object" -> JsObjectConstructor.INSTANCE;
            case "RegExp" -> {
                JsRegex r = new JsRegex();
                r.builtinConstructor = true;
                yield r;
            }
            case "Set" -> JsSetConstructor.INSTANCE;
            case "String" -> JsStringConstructor.INSTANCE;
            case "TypeError" -> new JsError(null, "TypeError", null);
            case "ReferenceError" -> new JsError(null, "ReferenceError", null);
            case "RangeError" -> new JsError(null, "RangeError", null);
            case "SyntaxError" -> new JsError(null, "SyntaxError", null);
            case "URIError" -> new JsError(null, "URIError", null);
            case "EvalError" -> new JsError(null, "EvalError", null);
            case "TextDecoder" -> new JsTextDecoder();
            case "TextEncoder" -> new JsTextEncoder();
            case "Uint8Array" -> new JsUint8Array(0);
            // Minimal Reflect — only `construct` and `apply` are wired so the
            // test262 isConstructor harness works. Full Reflect stays gated
            // via the `feature: Reflect` skip in expectations.yaml.
            case "Reflect" -> new JsReflect();
            // Minimal Symbol: only the well-known symbols are exposed, as their
            // string-keyed stand-ins ("@@iterator" etc.). No Symbol() constructor,
            // no unique-symbol identity — tests that need real Symbol stay gated by
            // `feature: Symbol`. Lets `arr[Symbol.iterator]` resolve to `arr["@@iterator"]`.
            case "Symbol" -> {
                JsObject sym = new JsObject();
                sym.putMember("iterator", IterUtils.SYMBOL_ITERATOR);
                sym.putMember("asyncIterator", "@@asyncIterator");
                yield sym;
            }
            default -> null;
        };
    }

}

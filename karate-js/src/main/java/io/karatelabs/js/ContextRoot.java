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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Engine-state holder. Sits above the script-level {@link CoreContext} as the
 * terminal link in every name-resolution chain — when a {@link CoreContext}
 * reaches the end of its parent walk without finding a binding, it falls
 * through to {@link #hasKey} / {@link #get} here to resolve built-ins
 * (Math, JSON, …) lazily and to read the unified bindings store.
 * <p>
 * Shares the engine's single {@link Bindings} instance with every CoreContext
 * that runs through this engine — top-level {@code var}, implicit globals,
 * lazy-cached built-ins, and {@link Engine#putRootBinding}-injected resources
 * all live there. The {@code hidden} flag on individual entries keeps the
 * latter two out of {@link Engine#getBindings()} inspection.
 */
class ContextRoot implements Context {

    private final Engine engine;

    /**
     * Single shared bindings store — same instance as {@link Engine#bindings}.
     * Held here so engine internals can write through {@code root._bindings}
     * symmetrically with how script-level CoreContexts use {@code _bindings}.
     */
    final Bindings _bindings;

    /**
     * Top-level {@code this} — globalThis stand-in. Reflects built-in globals
     * + user-visible bindings as own properties (so
     * {@code Object.getOwnPropertyDescriptor(this, "Math")} etc. work). Child
     * contexts inherit this until a function call rebinds {@code thisObject}.
     */
    final Object thisObject;

    Consumer<String> onConsoleLog;

    ContextListener listener;
    ExternalBridge bridge;
    RunInterceptor<?> interceptor;
    DebugPointFactory<?> pointFactory;

    short evalId;

    ContextRoot(Engine engine) {
        this.engine = engine;
        this._bindings = engine.bindings;
        this.thisObject = new JsGlobalThis(this);
    }

    void setOnConsoleLog(Consumer<String> onConsoleLog) {
        this.onConsoleLog = onConsoleLog;
    }

    /**
     * Lookup chain entry point — script-level CoreContext falls through here
     * after exhausting its parent walk. Reads the unified store; if that misses,
     * triggers lazy initGlobal for built-ins.
     */
    Object get(String key) {
        if (_bindings.hasMember(key)) {
            Object result = _bindings.getMember(key);
            if (result instanceof Supplier<?> supplier) {
                return supplier.get();
            }
            return result;
        }
        Object global = initGlobal(key);
        if (global != null) {
            // Cache as hidden so Engine.getBindings() inspection doesn't
            // surface lazy-loaded built-ins.
            _bindings.putHidden(key, global);
            return global;
        }
        return Terms.UNDEFINED;
    }

    /**
     * True if {@code key} resolves at the global level — either it's already
     * in the unified store, or it's a recognized built-in name that
     * {@link #initGlobal} can lazily produce.
     */
    boolean hasKey(String key) {
        if (_bindings.hasMember(key)) {
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

    //=== Context interface =============================================================================================

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public Context getParent() {
        return null;
    }

    @Override
    public ContextScope getScope() {
        return ContextScope.ROOT;
    }

    @Override
    public int getDepth() {
        return -1;
    }

    @Override
    public Node getNode() {
        return null;
    }

    @Override
    public String getPath() {
        return "ROOT";
    }

    @Override
    public int getIteration() {
        return -1;
    }

    @Override
    public Object getThisObject() {
        return thisObject;
    }

    @Override
    public CallInfo getCallInfo() {
        return null;
    }

    @Override
    public Object[] getCallArgs() {
        return null;
    }

    @Override
    public Object getReturnValue() {
        return null;
    }

    @Override
    public Object getErrorThrown() {
        return null;
    }

    @Override
    public ExitType getExitType() {
        return null;
    }

    @Override
    public String toString() {
        return "ROOT";
    }

    //=== Built-in globals ==============================================================================================

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
            // Currently consumed by the engine: @@iterator (IterUtils.getIterator),
            // @@toPrimitive (Terms.toPrimitive), @@toStringTag (JsObjectPrototype).
            // The remainder are exposed so user code that *reads* them (e.g.
            // `obj[Symbol.species]`) sees a stable string key rather than undefined,
            // unblocking tests that just check existence or use the key as a property.
            case "Symbol" -> {
                // `Symbol(...)` is callable — returns a fresh JsObject as a
                // stand-in for a unique symbol value. We don't model real
                // symbols (no unique identity, `typeof === "symbol"`, etc.);
                // tests that need that are gated via `feature: Symbol`. This
                // keeps `Symbol()` from throwing when used as a placeholder
                // "non-function value" by tests of *other* features.
                JsObject sym = new SymbolStandIn();
                sym.putMember("iterator", IterUtils.SYMBOL_ITERATOR);
                sym.putMember("asyncIterator", "@@asyncIterator");
                sym.putMember("toPrimitive", "@@toPrimitive");
                sym.putMember("toStringTag", "@@toStringTag");
                sym.putMember("hasInstance", "@@hasInstance");
                sym.putMember("isConcatSpreadable", "@@isConcatSpreadable");
                sym.putMember("species", "@@species");
                sym.putMember("match", "@@match");
                sym.putMember("matchAll", "@@matchAll");
                sym.putMember("replace", "@@replace");
                sym.putMember("search", "@@search");
                sym.putMember("split", "@@split");
                sym.putMember("unscopables", "@@unscopables");
                yield sym;
            }
            default -> null;
        };
    }

    /**
     * Minimal callable stand-in for {@code Symbol}. Lets tests that exist
     * outside the {@code feature: Symbol} skip list (e.g. ones that pass
     * {@code Symbol()} as a "non-callable value" probe) construct a unique
     * placeholder without {@code Symbol(...)} throwing. Real symbol identity /
     * {@code typeof === "symbol"} stay out of scope.
     */
    private static final class SymbolStandIn extends JsObject implements JsCallable {
        @Override
        public Object call(Context context, Object[] args) {
            return new JsObject();
        }
    }

}

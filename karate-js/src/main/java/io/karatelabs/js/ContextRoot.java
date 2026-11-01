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
import io.karatelabs.parser.ParserException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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

    static final JsBuiltinMethod PARSE_INT = new JsBuiltinMethod("parseInt", 2, (ctx, args) -> {
        if (args.length == 0) return Double.NaN;
        int radix = args.length > 1 && args[1] != Terms.UNDEFINED
                ? Terms.objectToNumber(args[1]).intValue() : 0;
        return Terms.parseInt(argToString(args[0]), radix);
    });

    static final JsBuiltinMethod PARSE_FLOAT = new JsBuiltinMethod("parseFloat", 1, (ctx, args) ->
            args.length == 0 ? Double.NaN : Terms.parseFloat(argToString(args[0]), false));

    // A Number arg is ToString'd per spec before parsing, so it has to take the
    // spec form -- Java's would feed "1.0E21" to a parser that only knows 'e'.
    private static String argToString(Object arg) {
        return arg instanceof Number n ? Terms.numberToString(n) : arg + "";
    }

    private final Engine engine;

    /**
     * Single shared bindings store — same instance as {@link Engine#bindings}.
     * Held here so engine internals can write through {@code root.bindings}
     * symmetrically with how script-level CoreContexts use {@code bindings}.
     */
    final BindingsStore bindings;

    /**
     * Top-level {@code this} — globalThis stand-in. Reflects built-in globals
     * + user-visible bindings as own properties (so
     * {@code Object.getOwnPropertyDescriptor(this, "Math")} etc. work). Child
     * contexts inherit this until a function call rebinds {@code thisObject}.
     */
    final Object thisObject;

    // `performance` is lazily allocated, so its origin has to be stamped here
    // instead — timings must be relative to engine creation, not first read
    private final long nanoOrigin = System.nanoTime();
    private final double timeOrigin = System.currentTimeMillis();

    BiConsumer<String, String> onConsole;

    ContextListener listener;
    ExternalBridge bridge;
    RunInterceptor<?> interceptor;
    DebugPointFactory<?> pointFactory;

    short evalId;

    ContextRoot(Engine engine) {
        this.engine = engine;
        this.bindings = engine.bindings;
        this.thisObject = new JsGlobalThis(this);
    }

    /**
     * Terminal step of {@link CoreContext#resolve} — script-level CoreContext
     * falls through here after exhausting its outer walk. Returns the
     * existing Slot, or lazily initializes a built-in (Math, JSON, etc.)
     * and returns its newly-cached Slot. Returns {@code null} if the name
     * is neither user-bound nor a recognized built-in.
     */
    BindingSlot resolveOrInit(String key) {
        BindingSlot s = bindings.getSlot(key);
        if (s != null) {
            return s;
        }
        Object global = initGlobal(key);
        if (global != null) {
            // Cache as hidden so Engine.getBindings() inspection doesn't
            // surface lazy-loaded built-ins.
            bindings.putHidden(key, global);
            return bindings.getSlot(key);
        }
        return null;
    }

    /**
     * Lookup-chain entry for callers that want a value (not a Slot) — wraps
     * {@link #resolveOrInit} with JsLazy-unwrap and undefined fallback.
     */
    Object get(String key) {
        BindingSlot s = resolveOrInit(key);
        if (s == null) {
            return Terms.UNDEFINED;
        }
        Object v = s.value;
        return v instanceof JsLazy lz ? lz.get() : v;
    }

    /**
     * True if {@code key} resolves at the global level — either it's already
     * in the unified store, or it's a recognized built-in name that
     * {@link #initGlobal} can lazily produce. Side-effect-free: does NOT
     * trigger lazy init.
     */
    boolean hasKey(String key) {
        if (bindings.hasMember(key)) {
            return true;
        }
        return canInitGlobal(key);
    }

    // every name initGlobal produces
    static final List<String> GLOBALS = List.of(
            "console", "parseInt", "parseFloat", "encodeURIComponent", "decodeURIComponent",
            "encodeURI", "decodeURI", "escape", "unescape", "undefined", "Array", "Date", "Error", "Function",
            "Infinity", "Java", "JSON", "Map", "Math", "NaN", "Number", "BigInt", "Boolean",
            "Object", "Promise", "RegExp", "Set", "String", "TypeError", "ReferenceError", "RangeError",
            "SyntaxError", "URIError", "EvalError", "AggregateError", "WeakMap", "WeakSet",
            "TextEncoder", "TextDecoder", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array",
            "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array",
            "Float64Array", "crypto", "globalThis",
            "setTimeout", "clearTimeout", "setInterval", "clearInterval", "setImmediate", "clearImmediate",
            "structuredClone", "performance",
            "isNaN", "isFinite", "eval", "Symbol", "Reflect");

    private static final Set<String> GLOBAL_SET = Set.copyOf(GLOBALS);

    private static boolean canInitGlobal(String key) {
        return GLOBAL_SET.contains(key);
    }

    private static String firstArgToString(Context ctx, Object[] args) {
        return Terms.toStringCoerce(args.length == 0 ? Terms.UNDEFINED : args[0], (CoreContext) ctx);
    }

    private static final String ESCAPE_KEEP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@*_+-./";

    /** Annex B.2.1.1 {@code escape}. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ESCAPE_KEEP.indexOf(c) >= 0) {
                sb.append(c);
            } else if (c < 256) {
                sb.append('%').append(String.format("%02X", (int) c));
            } else {
                sb.append("%u").append(String.format("%04X", (int) c));
            }
        }
        return sb.toString();
    }

    /** Annex B.2.1.2 {@code unescape}: a malformed escape stays as written. */
    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 5 < n && s.charAt(i + 1) == 'u' && isHex(s, i + 2, 4)) {
                    sb.append((char) Integer.parseInt(s, i + 2, i + 6, 16));
                    i += 5;
                    continue;
                }
                if (i + 2 < n && isHex(s, i + 1, 2)) {
                    sb.append((char) Integer.parseInt(s, i + 1, i + 3, 16));
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isHex(String s, int from, int count) {
        for (int i = from; i < from + count; i++) {
            if (hexDigit(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    // ASCII only: Character.digit also accepts non-ASCII digits
    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static int hexOctet(String s, int at) {
        if (at + 2 > s.length() || hexDigit(s.charAt(at)) < 0 || hexDigit(s.charAt(at + 1)) < 0) {
            throw uriMalformed();
        }
        return hexDigit(s.charAt(at)) * 16 + hexDigit(s.charAt(at + 1));
    }

    private static JsErrorException uriMalformed() {
        return JsErrorException.uriError("URI malformed");
    }

    static final String DECODE_URI_RESERVED = ";/?:@&=+$,#";

    /** §19.2.6.5 Decode: an escape decoding to a character in {@code preserve} stays as written. */
    static String decode(String s, String preserve) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        for (int k = 0; k < n; k++) {
            char c = s.charAt(k);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            int start = k;
            int b = hexOctet(s, k + 1);
            k += 2;
            if (b < 0x80) {
                if (preserve.indexOf(b) >= 0) {
                    sb.append(s, start, k + 1);
                } else {
                    sb.append((char) b);
                }
                continue;
            }
            int count;
            int cp;
            if ((b & 0xE0) == 0xC0) {
                count = 2;
                cp = b & 0x1F;
            } else if ((b & 0xF0) == 0xE0) {
                count = 3;
                cp = b & 0x0F;
            } else if ((b & 0xF8) == 0xF0) {
                count = 4;
                cp = b & 0x07;
            } else {
                throw uriMalformed();
            }
            for (int j = 1; j < count; j++) {
                k++;
                if (k >= n || s.charAt(k) != '%') {
                    throw uriMalformed();
                }
                int cont = hexOctet(s, k + 1);
                if ((cont & 0xC0) != 0x80) {
                    throw uriMalformed();
                }
                cp = (cp << 6) | (cont & 0x3F);
                k += 2;
            }
            int min = count == 2 ? 0x80 : count == 3 ? 0x800 : 0x10000;
            if (cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                throw uriMalformed();
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /** Backs {@link Engine#builtins()}: read off a fresh engine so it tracks what the engine installs. */
    static Map<String, List<String>> builtinMembers() {
        Engine engine = new Engine();
        engine.setExternalBridge(new ExternalBridge() {
        });
        ContextRoot root = engine.root();
        Map<String, List<String>> members = new LinkedHashMap<>();
        for (String name : GLOBALS) {
            Object global = root.get(name);
            // its own keys are whichever globals happen to be initialized
            members.put(name, global == root.thisObject ? GLOBALS : ownNames(global));
            if (global instanceof JsObject jo && jo.getMember("prototype") instanceof Prototype proto) {
                members.put(name + ".prototype", ownNames(proto));
            }
        }
        return Collections.unmodifiableMap(members);
    }

    private static List<String> ownNames(Object value) {
        Collection<String> names;
        if (value instanceof Prototype proto) {
            names = proto.builtinNames();
        } else if (value instanceof JsObject jo) {
            names = new LinkedHashSet<>(jo.toMap().keySet());
            jo.ownIntrinsicNames().forEach(names::add);
            names.removeIf(n -> !jo.isOwnProperty(n));
        } else if (value instanceof SimpleObject so) {
            names = so.jsKeys();
        } else {
            names = List.of();
        }
        return names.stream().filter(n -> !n.startsWith("@@")).toList();
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

    // This Engine's built-in constructor instances (Object, Array, …,
    // AggregateError). Per-Engine — NOT JVM singletons — so user mutations,
    // tombstones, and freezes on one engine's constructors can be neither
    // observed nor destroyed by another engine running concurrently. Lazily
    // created per name; identity is stable within the Engine so
    // `Array.prototype.constructor === Array` holds. The prototype objects
    // they expose (`Array.prototype` etc.) remain JVM-wide singletons.
    private Map<String, JsFunction> builtinConstructors;

    JsFunction builtinConstructor(String name) {
        if (builtinConstructors == null) {
            builtinConstructors = new HashMap<>();
        }
        JsFunction c = builtinConstructors.get(name);
        if (c == null) {
            c = switch (name) {
                case "Array" -> new JsArrayConstructor();
                case "BigInt" -> new JsBigIntConstructor();
                case "Boolean" -> new JsBooleanConstructor();
                case "Date" -> new JsDateConstructor();
                case "Function" -> new JsFunctionConstructor();
                case "Map" -> new JsMapConstructor();
                case "Number" -> new JsNumberConstructor();
                case "Object" -> new JsObjectConstructor();
                case "Promise" -> new JsPromiseConstructor();
                case "RegExp" -> new JsRegexConstructor();
                case "Set" -> new JsSetConstructor();
                case "String" -> new JsStringConstructor();
                case "WeakMap" -> new JsWeakMapConstructor();
                case "WeakSet" -> new JsWeakSetConstructor();
                case "ArrayBuffer" -> new JsArrayBufferConstructor();
                case "DataView" -> new JsDataViewConstructor();
                case "Error" -> new JsErrorConstructor(JsErrorPrototype.ERROR, 1);
                case "TypeError" -> new JsErrorConstructor(JsErrorPrototype.TYPE_ERROR, 1);
                case "RangeError" -> new JsErrorConstructor(JsErrorPrototype.RANGE_ERROR, 1);
                case "SyntaxError" -> new JsErrorConstructor(JsErrorPrototype.SYNTAX_ERROR, 1);
                case "ReferenceError" -> new JsErrorConstructor(JsErrorPrototype.REFERENCE_ERROR, 1);
                case "URIError" -> new JsErrorConstructor(JsErrorPrototype.URI_ERROR, 1);
                case "EvalError" -> new JsErrorConstructor(JsErrorPrototype.EVAL_ERROR, 1);
                case "AggregateError" -> new JsErrorConstructor(JsErrorPrototype.AGGREGATE_ERROR, 2);
                default -> JsTypedArrayConstructor.of(name);
            };
            if (c != null) {
                builtinConstructors.put(name, c);
            }
        }
        return c;
    }

    private Object initGlobal(String key) {
        return switch (key) {
            case "console" -> new JsConsole(this);
            // Shared with Number.parseInt / Number.parseFloat so spec identity
            // (Number.parseInt === parseInt) holds. Wrapped via JsBuiltinMethod
            // so .name / .length are observable per spec and isConstructor returns false.
            case "parseInt" -> PARSE_INT;
            case "parseFloat" -> PARSE_FLOAT;
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
                String encoded = URLEncoder.encode(Terms.toStringCoerce(args.length == 0 ? Terms.UNDEFINED : args[0], null), StandardCharsets.UTF_8);
                return encoded.replace("+", "%20")
                        .replace("%21", "!").replace("%27", "'").replace("%28", "(")
                        .replace("%29", ")").replace("%7E", "~").replace("%2A", "*");
            };
            case "decodeURIComponent" -> new JsBuiltinMethod("decodeURIComponent", 1, (ctx, args) ->
                    decode(firstArgToString(ctx, args), ""));
            case "encodeURI" -> (JsInvokable) args -> {
                String encoded = URLEncoder.encode(Terms.toStringCoerce(args.length == 0 ? Terms.UNDEFINED : args[0], null), StandardCharsets.UTF_8);
                return encoded.replace("+", "%20")
                        .replace("%21", "!").replace("%23", "#").replace("%24", "$")
                        .replace("%26", "&").replace("%27", "'").replace("%28", "(")
                        .replace("%29", ")").replace("%2A", "*").replace("%2B", "+")
                        .replace("%2C", ",").replace("%2F", "/").replace("%3A", ":")
                        .replace("%3B", ";").replace("%3D", "=").replace("%3F", "?")
                        .replace("%40", "@").replace("%7E", "~");
            };
            case "decodeURI" -> new JsBuiltinMethod("decodeURI", 1, (ctx, args) ->
                    decode(firstArgToString(ctx, args), DECODE_URI_RESERVED));
            case "escape" -> new JsBuiltinMethod("escape", 1, (ctx, args) -> escape(firstArgToString(ctx, args)));
            case "unescape" -> new JsBuiltinMethod("unescape", 1, (ctx, args) -> unescape(firstArgToString(ctx, args)));
            case "undefined" -> Terms.UNDEFINED;
            case "eval" -> (JsInvokable) args -> {
                if (args.length == 0) return Terms.UNDEFINED;
                Object src = args[0];
                // per ES spec: non-string arguments are returned unchanged
                if (!(src instanceof String s)) {
                    return src;
                }
                // indirect-eval semantics: evaluate in the global (root) scope
                try {
                    return engine.evalRaw(s);
                } catch (ParserException pe) {
                    // per spec, eval-code that fails to parse throws a JS
                    // SyntaxError the caller can catch — a bare ParserException
                    // would escape as a Java exception no JS catch can see
                    throw JsErrorException.syntaxError(pe.getMessage());
                }
            };
            case "Array", "Date", "Function", "Error", "Map", "Number", "BigInt", "Boolean",
                 "Object", "Promise", "RegExp", "Set", "String", "TypeError", "ReferenceError", "RangeError",
                 "SyntaxError", "URIError", "EvalError", "AggregateError",
                 "WeakMap", "WeakSet", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray",
                 "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array",
                 "Float64Array" -> builtinConstructor(key);
            // the top-level `this` object, under its spec name
            case "globalThis" -> thisObject;
            case "setTimeout" -> new JsBuiltinMethod("setTimeout", 2, AsyncSupport::setTimeout);
            case "clearTimeout" -> new JsBuiltinMethod("clearTimeout", 1, AsyncSupport::clearTimeout);
            case "setInterval" -> new JsBuiltinMethod("setInterval", 2, AsyncSupport::setInterval);
            case "clearInterval" -> new JsBuiltinMethod("clearInterval", 1, AsyncSupport::clearTimeout);
            case "setImmediate" -> new JsBuiltinMethod("setImmediate", 1, AsyncSupport::setImmediate);
            case "clearImmediate" -> new JsBuiltinMethod("clearImmediate", 1, AsyncSupport::clearTimeout);
            case "Infinity" -> Double.POSITIVE_INFINITY;
            case "Java" -> new JsJava(bridge);
            case "JSON" -> new JsJson();
            case "Math" -> new JsMath();
            case "NaN" -> Double.NaN;
            case "performance" -> new JsPerformance(nanoOrigin, timeOrigin);
            case "structuredClone" -> new JsBuiltinMethod("structuredClone", 1, JsStructuredClone::call);
            case "TextDecoder" -> new JsTextDecoder();
            case "TextEncoder" -> new JsTextEncoder();
            case "crypto" -> new JsCrypto();
            // Minimal Reflect — only `construct` and `apply` are wired so the
            // test262 isConstructor harness works. Full Reflect stays gated
            // via the `feature: Reflect` skip in expectations.yaml.
            case "Reflect" -> new JsReflect();
            // Minimal Symbol: the well-known symbols are {@link JsSymbol} values over
            // their spec keys ("@@iterator" etc.), so `arr[Symbol.iterator]` resolves
            // to `arr["@@iterator"]`. Consumed by the engine: @@iterator
            // (IterUtils.getIterator), @@toPrimitive (Terms.toPrimitive),
            // @@toStringTag (JsObjectPrototype). The remainder are exposed so user
            // code that *reads* them (e.g. `obj[Symbol.species]`) gets a stable key
            // rather than undefined. No symbol registry (Symbol.for / keyFor) and no
            // Symbol.prototype — tests needing those stay gated by `feature: Symbol`.
            case "Symbol" -> {
                JsObject sym = new SymbolStandIn();
                for (String name : JsSymbol.WELL_KNOWN) {
                    sym.putMember(name, new JsSymbol(JsSymbol.keyOf(name)));
                }
                yield sym;
            }
            default -> null;
        };
    }

    /**
     * The {@code Symbol} global itself — callable, so {@code Symbol(desc)} mints
     * a fresh {@link JsSymbol} with a key of its own.
     */
    private static final class SymbolStandIn extends JsObject implements JsCallable {
        @Override
        boolean isJsFunction() {
            return true;
        }

        @Override
        public Object call(Context context, Object[] args) {
            Object description = args.length == 0 ? Terms.UNDEFINED : args[0];
            return JsSymbol.mint(description == Terms.UNDEFINED ? ""
                    : Terms.toStringCoerce(description, context instanceof CoreContext cc ? cc : null));
        }
    }

}

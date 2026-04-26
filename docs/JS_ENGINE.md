# JavaScript Engine Reference

This document describes the JavaScript engine architecture, type system, and Java interop patterns for karate-js.

> See also: [DESIGN.md](./DESIGN.md) | [TODOS.md](./TODOS.md) | [karate-js README](../karate-js/README.md) | [karate-js-test262 TEST262.md](../karate-js-test262/TEST262.md)

---

## Overview

karate-js is a lightweight JavaScript engine implemented in Java, designed for:
- Thread-safe concurrent execution
- Seamless Java interop
- API testing and data transformation
- Minimal footprint (no GraalVM dependency)

---

## Design Principles

1. **Lazy overhead** - Only create wrapper objects when needed (e.g., `CallInfo` only for `new`)
2. **Internal vs external representation** - Internal state can differ from `getJavaValue()` output
3. **Preserve JS semantics** - `typeof`, `instanceof`, truthiness must match JS spec
4. **Java interop friendly** - `getJavaValue()` returns idiomatic Java types
5. **Performance first** - Primitives stay as Java primitives in the common case
6. **Flexible input, consistent output** - Accept multiple Java types as input, return one preferred type
7. **Unwrap first pattern** - Use `getJsValue()` to unwrap JsValue types before switching on raw types
8. **Consistent "this" resolution** - Use `fromThis(Context)` pattern across all JsObject subclasses

---

## Type System

### Core Interfaces

```java
// Sealed hierarchy for JS wrapper types that need Java interop conversion
public sealed interface JsValue permits JsUndefined, JsPrimitive, JsDateValue, JsBinaryValue {
    Object getJavaValue();              // For external use (e.g., JsDate → Date)

    default Object getJsValue() {       // For internal operations (e.g., JsDate → double timeValue)
        return getJavaValue();
    }
}

// Sub-hierarchies (all sealed)
sealed interface JsPrimitive extends JsValue permits JsNumber, JsString, JsBoolean, JsBigInt {}
sealed interface JsDateValue extends JsValue permits JsDate {}
sealed interface JsBinaryValue extends JsValue permits JsUint8Array {}

// Singleton for undefined
public final class JsUndefined implements JsValue {
    public static final JsUndefined INSTANCE = new JsUndefined();
    public Object getJavaValue() { return null; }
}

// Internal interface - base for all callable objects
interface JsCallable {
    Object call(Context context, Object... args);
    default boolean isExternal() { return false; }  // JS-native by default
}

// Public interface for Java code to implement callables
public interface JavaCallable extends JsCallable {
    @Override
    default boolean isExternal() { return true; }  // External Java code
}

// Convenience interface that ignores context
public interface JavaInvokable extends JavaCallable {
    Object invoke(Object... args);

    default Object call(Context context, Object... args) {
        return invoke(args);
    }
}
```

**The `isExternal()` pattern:** Determines whether arguments should be converted at the JS/Java boundary:
- `true` (default for `JavaCallable`): External Java code - convert `undefined`→`null`, `JsDate`→`Date`
- `false` (default for `JsCallable`): Internal JS functions - preserve JS semantics

`JsFunction` implements `JavaCallable` (for sharing functions with Java code) but overrides `isExternal()` to `false` to preserve `undefined` semantics internally.

**Boundary conversion:** When `callable.isExternal()` is true, arguments are converted:
- `undefined` → `null`
- `JsDate` → `java.util.Date`
- Other `JsValue` types → unwrapped via `getJavaValue()`

### Type Mapping

| JS Type | Java Wrapper | `getJavaValue()` | Implements |
|---------|--------------|------------------|------------|
| undefined | JsUndefined | null | JsValue |
| Number | JsNumber | Number | JsPrimitive → JsValue |
| String | JsString | String | JsPrimitive → JsValue |
| Boolean | JsBoolean | Boolean | JsPrimitive → JsValue |
| BigInt | JsBigInt | BigInteger | JsPrimitive → JsValue |
| Date | JsDate | Date | JsDateValue → JsValue |
| RegExp | JsRegex | Pattern | - |
| Array | JsArray | List | **List\<Object\>** |
| Object | JsObject | Map | **Map\<String, Object\>** |
| Map | JsMap | Map | extends JsObject |
| Set | JsSet | Set | extends JsObject |
| Uint8Array | JsUint8Array | byte[] | JsBinaryValue → JsValue |

### Prototype System Architecture

The engine uses singleton prototype objects for method inheritance, matching JavaScript's prototype chain:

```
Singleton Prototypes (shared JVM-wide; userProps reset per Engine):
    JsObjectPrototype.INSTANCE   ← null (root of chain)
    JsArrayPrototype.INSTANCE    ← JsObjectPrototype.INSTANCE
    JsStringPrototype.INSTANCE   ← JsObjectPrototype.INSTANCE
    JsNumberPrototype.INSTANCE   ← JsObjectPrototype.INSTANCE
    JsBooleanPrototype.INSTANCE  ← JsObjectPrototype.INSTANCE
    JsBigIntPrototype.INSTANCE   ← JsObjectPrototype.INSTANCE
    JsDatePrototype.INSTANCE     ← JsObjectPrototype.INSTANCE
    JsFunctionPrototype.INSTANCE ← JsObjectPrototype.INSTANCE
    JsRegexPrototype.INSTANCE    ← JsObjectPrototype.INSTANCE
    JsMapPrototype.INSTANCE      ← JsObjectPrototype.INSTANCE
    JsSetPrototype.INSTANCE      ← JsObjectPrototype.INSTANCE
    (JsError uses JsObjectPrototype directly)

Constructor Functions (for static methods like Array.isArray, Date.UTC):
    JsObjectConstructor.INSTANCE   → prototype: JsObjectPrototype.INSTANCE
    JsArrayConstructor.INSTANCE    → prototype: JsArrayPrototype.INSTANCE
    JsStringConstructor.INSTANCE   → prototype: JsStringPrototype.INSTANCE
    JsNumberConstructor.INSTANCE   → prototype: JsNumberPrototype.INSTANCE
    JsBigIntConstructor.INSTANCE   → prototype: JsBigIntPrototype.INSTANCE
    JsDateConstructor.INSTANCE     → prototype: JsDatePrototype.INSTANCE
    JsFunctionConstructor.INSTANCE → prototype: JsFunctionPrototype.INSTANCE
    JsMapConstructor.INSTANCE      → prototype: JsMapPrototype.INSTANCE
    JsSetConstructor.INSTANCE      → prototype: JsSetPrototype.INSTANCE
    (JsBoolean has a prototype but no constructor wrapper class — the
     Boolean global is registered directly as a callable in ContextRoot.)
```

**Built-in prototypes accept user-added properties** per spec — `Array.prototype`
methods are configurable + writable, so `Array.prototype.foo = ...` works and
overrides on lookup. The `Prototype` base class carries a `userProps` map for
this; the built-in methods themselves are immutable (cannot be removed via
`removeMember` unless tombstoned-on-delete per the
[Spec Invariants § Property attributes](#property-attributes) rules).

**Per-Engine isolation.** The prototypes are JVM-wide singletons but their
`userProps` reset on every `new Engine()` via `Prototype.clearAllUserProps()`
+ `JsObject.clearAllEngineState()` — otherwise a previous test that did
`Map.prototype.set = function() { throw ... }` would poison the next session.
See [Spec Invariants § Prototype machinery](#prototype-machinery) for the
full mechanism.

User-created objects, arrays, and functions remain fully mutable:
```javascript
var obj = {}; obj.foo = "bar";           // OK
var arr = []; arr.customProp = 123;      // OK
function f() {}; f.meta = "data";        // OK
```

**Property lookup order** (implemented in `Prototype.getMember()`):
1. `userProps` map (user-added properties win per spec)
2. Built-in properties via `lookupBuiltin()` (skipped if tombstoned)
3. Delegate to `__proto__` chain

```java
// Base class for built-in prototype objects
abstract class Prototype implements ObjectLike {
    private final Prototype __proto__;
    private Map<String, Object> userProps;        // user-added properties (lazy)
    private Set<String> tombstones;               // intrinsics user has deleted (lazy)
    final Map<String, JsBuiltinMethod> _methodCache; // wrapped built-in methods (per-Engine)

    public final Object getMember(String name) {
        // 1. User-added properties win over built-ins
        if (userProps != null && userProps.containsKey(name)) {
            return userProps.get(name);
        }
        // 2. Built-in properties (skip if tombstoned)
        if (tombstones == null || !tombstones.contains(name)) {
            Object result = lookupBuiltin(name);   // uses _methodCache
            if (result != null) return result;
        }
        // 3. Delegate to __proto__ chain
        return __proto__ != null ? __proto__.getMember(name) : null;
    }

    public void putMember(String name, Object value) {
        // Built-in methods can't be replaced; user props go in userProps
        if (userProps == null) userProps = new LinkedHashMap<>();
        userProps.put(name, value);
        if (tombstones != null) tombstones.remove(name);   // re-introduces a deleted intrinsic
    }

    protected abstract Object getBuiltinProperty(String name);
}

// Example: JsArrayPrototype provides array methods (package-private singleton).
// Each method is wrapped in JsBuiltinMethod via the `method()` helper so
// arr.push.length / arr.push.name read correctly.
class JsArrayPrototype extends Prototype {
    static final JsArrayPrototype INSTANCE = new JsArrayPrototype();

    private JsArrayPrototype() {
        super(JsObjectPrototype.INSTANCE);  // Arrays inherit from Object
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "push"   -> method(name, 1, this::push);
            case "map"    -> method(name, 1, this::map);
            case "filter" -> method(name, 1, this::filter);
            // ... other array methods
            default -> null;  // Delegate to __proto__ (JsObjectPrototype)
        };
    }
}
```

**Benefits:**
- Single instance per type (memory efficient)
- Spec-conformant: `Array.prototype.foo = ...` polyfill patterns work
- Per-Engine reset prevents cross-session pollution
- Clean separation of constructor vs prototype
- Methods inherited via standard prototype chain
- `ObjectLike.getPrototype()` enables uniform chain walking
- `JsBuiltinMethod` wrap gives every built-in method correct `length` / `name`

### Boxed Primitives

JS constructors behave differently with vs without `new`:

```javascript
Number(5)      // → primitive 5
new Number(5)  // → boxed Number object

String("x")    // → primitive "x"
new String("x") // → boxed String object

Date()         // → string of current time (ES6: ignores arguments)
new Date()     // → Date object
```

The engine uses `CallInfo` to track invocation context:
- `context.getCallInfo().constructor` is true for `new` calls
- Zero overhead for normal calls (returns null)

---

## Java ↔ JS Type Conversion

### Bidirectional Pattern

```
┌─────────────────┐      Java → JS       ┌─────────────────┐
│  java.util.Date │ ──────────────────►  │                 │
│  Instant        │ ──────────────────►  │     JsDate      │
│  LocalDateTime  │ ──────────────────►  │  (internal      │
│  LocalDate      │ ──────────────────►  │   timeValue:    │
│  ZonedDateTime  │ ──────────────────►  │   double, NaN = │
└─────────────────┘                      │   Invalid Date) │
                                         └────────┬────────┘
                                                  │
                        JS → Java                 │
                   ◄──────────────────────────────┘
                   │
                   ▼
           ┌────────────────┐
           │ java.util.Date │
           └────────────────┘
```

### Lazy Input Conversion

Conversion happens at point-of-use in `Terms.toJavaMirror()`:

```java
static JavaMirror toJavaMirror(Object o) {
    return switch (o) {
        case String s -> new JsString(s);
        case Number n -> new JsNumber(n);
        case Boolean b -> new JsBoolean(b);
        case java.util.Date d -> new JsDate(d);
        case Instant i -> new JsDate(i);
        case LocalDateTime ldt -> new JsDate(ldt);
        case LocalDate ld -> new JsDate(ld);
        case ZonedDateTime zdt -> new JsDate(zdt);
        case byte[] bytes -> new JsUint8Array(bytes);
        case null, default -> null;
    };
}
```

**Why lazy?**
- Thread-safety: Engine bindings may be updated by external threads
- Simplicity: Single conversion point handles all entry paths
- Performance: `instanceof` chain is fast; overhead is negligible

---

## JsArray and JsObject as List and Map

### Design Goals

1. **ES6 within JS** - JS code sees native values (`undefined`, prototype methods, etc.)
2. **Seamless Java interop** - `JsArray` implements `List`, `JsObject` implements `Map`
3. **Lazy auto-unwrap** - Java interface methods convert on access, not construction
4. **No eager conversion** - Eliminates `toList()`/`toMap()` overhead

### Dual Access Pattern

Collections have two access modes:

| Access Mode | Method | Returns | Use Case |
|-------------|--------|---------|----------|
| **Java interface** | `List.get(int)` / `Map.get(Object)` | Unwrapped (null, Date) | Java consumers |
| **JS internal** | `getElement(int)` / `getMember(String)` | Raw (undefined, JsDate) | JS engine internals |

```java
// JsArray implements List and uses singleton prototype
class JsArray implements List<Object>, ObjectLike, JsCallable {
    final List<Object> list;                              // Internal storage
    private Map<String, Object> namedProps;               // For named properties (arr.foo = "bar")
    private ObjectLike __proto__ = JsArrayPrototype.INSTANCE;  // Prototype chain

    // JS internal - raw values, ES6 semantics
    public Object getElement(int index) {
        if (index < 0 || index >= list.size()) {
            return Terms.UNDEFINED;  // Out of bounds returns undefined
        }
        return list.get(index);  // Returns Terms.UNDEFINED, JsDate, etc.
    }

    // Java interface - auto-unwrap for Java consumers
    @Override
    public Object get(int index) {
        return Engine.toJava(list.get(index));  // undefined→null, JsDate→Date
    }
}

// JsObject implements Map<String, Object>
class JsObject implements Map<String, Object>, ObjectLike {
    private Map<String, Object> _map;
    private Map<String, Byte> _attrs;        // sparse {writable, enumerable, configurable} byte map
    private Set<String> _tombstones;          // intrinsics user has deleted
    private ObjectLike __proto__ = JsObjectPrototype.INSTANCE;

    // JS internal - raw values, prototype chain.
    // (Simplified — see Spec Invariants § Property attributes for the
    //  intrinsic / tombstone / accessor pipeline.)
    public Object getMember(String name) {
        if (_tombstones != null && _tombstones.contains(name)) {
            return __proto__ != null ? __proto__.getMember(name) : null;
        }
        if (_map != null && _map.containsKey(name)) {
            return _map.get(name);
        }
        // Subclass intrinsic-field fallback (e.g. JsFunction's name/length/prototype/constructor)
        return __proto__ != null ? __proto__.getMember(name) : null;
    }

    // Canonical own-key check — see Spec Invariants
    public boolean isOwnProperty(String name) {
        if (_tombstones != null && _tombstones.contains(name)) return false;
        if (_map != null && _map.containsKey(name)) return true;
        return hasOwnIntrinsic(name);   // subclass-declared own intrinsics
    }

    // Java interface - auto-unwrap, own properties only
    @Override
    public Object get(Object key) {
        Object raw = _map != null ? _map.get(key.toString()) : null;
        return Engine.toJava(raw);  // undefined→null, JsDate→Date
    }
}
```

### ObjectLike Method Naming

To avoid collision with `Map.get(Object)`, ObjectLike uses distinct method names:

| Old Name | New Name | Purpose |
|----------|----------|---------|
| `get(String)` | `getMember(String)` | JS property access with prototype chain |
| `put(String, Object)` | `putMember(String, Object)` | JS property assignment |
| `remove(String)` | `removeMember(String)` | JS property deletion |

### Conversion at Boundaries

Conversion happens at specific boundaries:

1. **`Engine.eval()` return** - Top-level value converted via `toJava()`
2. **`List.get()` / `Map.get()`** - Elements unwrapped lazily on access
3. **JavaCallable args** - Arguments converted before external Java method call
4. **Iteration** - Iterator unwraps values lazily

### Example: Dual Access

```java
Engine engine = new Engine();
Object result = engine.eval("[1, undefined, new Date(0)]");

// As List - Java consumer gets unwrapped values
List<Object> list = (List<Object>) result;
list.get(0);  // 1
list.get(1);  // null (undefined unwrapped)
list.get(2);  // java.util.Date

// As JsArray - JS internal gets raw values
JsArray jsArray = (JsArray) result;
jsArray.getElement(0);  // 1
jsArray.getElement(1);  // Terms.UNDEFINED (raw)
jsArray.getElement(2);  // JsDate (raw)
```

### Why Lazy Unwrap?

1. **Performance** - No upfront traversal of nested structures
2. **Memory** - No duplicate converted collections
3. **Semantics** - JS code sees raw values, Java sees converted values
4. **Simplicity** - Single conversion point in `Engine.toJava()`

---

## The `fromThis()` Pattern

Unified "this" resolution across all JsObject subclasses:

```java
// JsObject - base implementation
JsObject fromThis(Context context) {
    Object thisObject = context.getThisObject();
    if (thisObject instanceof JsObject jo) return jo;
    if (thisObject instanceof Map<?, ?> map) return new JsObject((Map<String, Object>) map);
    return this;
}
```

**Covariant overrides:**

| Class | `fromThis()` returns | Also handles raw type |
|-------|---------------------|----------------------|
| JsObject | JsObject | Map |
| JsArray | JsArray | List |
| JsDate | JsDate | - |
| JsRegex | JsRegex | - |
| JsString | JsString | String |
| JsNumber | JsNumber | Number |
| JsUint8Array | JsUint8Array | byte[] |

This enables proper `.call()` support:
```javascript
Number.prototype.toFixed.call(5, 2)  // Works correctly
```

---

## The `toObjectLike()` Helper

Consolidates object wrapping for property access:

```java
static ObjectLike toObjectLike(Object o) {
    if (o instanceof ObjectLike ol) return ol;
    if (o instanceof List list) return new JsArray(list);
    JavaMirror mirror = toJavaMirror(o);
    return mirror instanceof ObjectLike ol ? ol : null;
}
```

---

## JsDate Implementation

Internal representation is `double timeValue` (NaN sentinel for Invalid Date —
matches the spec's `[[DateValue]]`). Java's `(long) NaN == 0` would silently
collapse Invalid Date to epoch, so `long` storage is unsafe.

```java
class JsDate extends JsObject implements JsDateValue {
    private double timeValue;                         // [[DateValue]]; NaN = Invalid Date

    JsDate(double timeValue) {
        this.timeValue = timeClip(timeValue);         // spec TimeClip
    }
    JsDate(java.util.Date d) {
        this(d == null ? Double.NaN : (double) d.getTime());
    }
    // (Instant / LocalDateTime / LocalDate / ZonedDateTime overloads also)

    boolean isInvalid() { return Double.isNaN(timeValue); }
    double  getTimeValue() { return timeValue; }
    long    getTime() { return (long) timeValue; }    // caller checks isInvalid first

    @Override
    public Object getJavaValue() { return new java.util.Date((long) timeValue); }
    @Override
    public Object getJsValue() { return timeValue; }  // For numeric operations
}
```

Constructor and prototype share spec algorithms via pure helpers on
`JsDate`: `makeDay` / `makeTime` / `makeDate` / `timeClip` / `localToUtc` /
`utcToLocal` / `parseToTimeValue`. `LocalTZA` is truncated to integer minutes
so historical zones with sub-minute offsets round-trip through
`getTimezoneOffset()` (which the spec defines as integer minutes).

See [Spec Invariants § Date](#date) for the load-bearing details: setters
read `[[DateValue]]` *before* coercing args (preserves observable side
effects from `valueOf`); coerce all args even when captured value is NaN
(spec ordering); bail without writing back when captured value was NaN.

**Benefits:**
- Spec-correct Invalid-Date semantics (NaN propagates through arithmetic)
- Thread-safe formatting (DateTimeFormatter)
- Constructor and prototype share helpers — no duplicated date math

---

## Exception Handling

> **Design tenet.** What surfaces when a JS program fails is part of the
> engine's *output contract*, because karate-js is executed by LLMs as often
> as it's written for them. Error messages, constructor identity, and (when
> we add them) stack frames must look JS-native — a raw `IndexOutOfBoundsException`
> or `at io.karatelabs.js.Interpreter.eval(...)` frame leaking out is a
> correctness bug, not cosmetic noise. See
> [karate-js-test262 Working Principle #3](../karate-js-test262/TEST262.md#working-principles)
> for the full statement.

### Java exceptions are JS-catchable

When a `JavaCallable`, `SimpleObject` method, or `Java.type(...)` instance/static method throws a Java `RuntimeException` while its call site is inside a JS `try` body, the engine converts the exception into a JS-level `Error` and binds it to the `catch` variable. Scripts can intercept Java failures with ordinary JS idioms:

```javascript
try {
  utils.decodeLicenseFile(bad);           // throws RuntimeException("signature verification failed")
} catch (e) {
  console.log(e.name);                    // "Error"
  console.log(e.message);                 // "signature verification failed"
  console.log('' + e);                    // "Error: signature verification failed"
}
```

**Implementation.** The conversion happens at a single boundary — `Interpreter.evalTryStmt()`. The try body is evaluated inside a Java `try { ... } catch (RuntimeException e)`; if an exception escapes, the engine calls `context.stopAndThrow(new JsError(e.getMessage(), e))` and lets the existing catch-block machinery bind the `JsError` to the error variable. Any reflection-layer `InvocationTargetException` is unwrapped inside `JavaUtils.invoke`/`invokeStatic` so the original cause reaches the boundary unchanged.

The call-site path (`Interpreter.evalFnCall`) is intentionally left as plain Java throw/propagate. This preserves the existing behavior for **uncaught** exceptions: they continue to bubble up through the expression chain, pick up the helpful `expression: <code> - <message>` framing at `PropertyAccess.getRefDotExpr`, and finally become the usual `js failed:` wrapper at the statement boundary. Only entering a `try` block changes the outcome.

### Exceptions that bypass JS catch

Some exceptions represent control flow rather than errors and must never be caught by scripts. They are marked with the `FlowControlSignal` interface and propagate through both `evalTryStmt` and `Engine.eval` unchanged:

```java
public class TemplateFlowSignal extends RuntimeException implements FlowControlSignal {
    // thrown by context.redirect(...) / context.switch(...)
}
```

Guidance for host code:
- **Plain `RuntimeException`** — Use for genuine error conditions. The JS side can catch and handle.
- **`FlowControlSignal` subclass** — Use for intentional abort signals (redirect, switch, cancel). JS cannot catch; Java callers use `instanceof` to detect.

### JsError shape

`JsError` extends `JsObject` and exposes:
- `name` — defaults to `"Error"`. `new TypeError('x')` sets it to `"TypeError"`.
- `message` — original exception message (or `null` if none).
- `toString()` — matches ES6: `"Error: <message>"`, or just `"Error"` when no message.
- `getCause()` (Java-only) — the original `Throwable` for debugging. Not exposed to JS.

Constructors `Error`, `TypeError` behave ES6-compliant:

```javascript
new Error('boom').message             // 'boom'
new Error('boom').name                // 'Error'
Error('boom').message                 // 'boom' — without `new` also works
new TypeError('bad').name             // 'TypeError' — constructor name is preserved
'' + new Error('x')                   // 'Error: x'
```

### Error-message preservation through reflection

`JavaUtils.invoke` and `JavaUtils.invokeStatic` separate "method not found" (TypeError with `"TypeError: .foo is not a function"`) from "method threw" (unwraps `InvocationTargetException`, rethrows the underlying `RuntimeException` with its original message). Before this change, reflective invocation failures were all collapsed into a generic `TypeError: .<name> is not a function`, masking real exception messages.

---

## Spec Invariants (test262-driven)

Engine rules established by test262 conformance work. Treat as load-bearing —
if a session needs to violate one, the rule goes up for review explicitly.
[`karate-js-test262/TEST262.md`](../karate-js-test262/TEST262.md#engine-guarantees-test262-driven)
keeps a one-liner index pointing back here.

### Error routing & shape

**Engine-emitted errors route through registered constructors.** Engine sites
(`PropertyAccess`, `Interpreter`, `CoreContext` TDZ/const-reassign/redeclare,
`JsJson`, `JsJava`, `JavaUtils`) emit `"<Name>: ..."` prefixes.
`Interpreter.evalTryStmt` parses the prefix into a structured `JsError` with
linked `.constructor`; `Interpreter.evalStatement` stamps
`EngineException.getJsErrorName()`. Result: `e instanceof TypeError`, `e.name`,
`e.constructor.name` all work for engine-originated errors. Low-traffic
internal-invariant sites (`JsArrayPrototype` / `JsRegex` / `JsStringPrototype`,
"finally block threw error") still throw plain `RuntimeException` — convert
the same way as needed.

**`Test262Error` / user-defined error classes** are classified via
`constructor.name` fallback in `Interpreter.evalProgram` when the thrown
`JsObject` has no `.name` on its prototype. Function-name inference in
`CoreContext.declare` fires only when the function's name is empty (so a
named function passed as a parameter doesn't get permanently renamed).

**`ErrorUtils.classify` scans embedded `<Name>:`** as a fallback for wrapper
messages where the type isn't a prefix. Wrappers preserve the cause chain so
the structured `JsErrorException` name propagates first; the embedded-name
scan is the safety net.

**JVM exception → JS error mapping** at `Interpreter.evalStatement` catch via
`classifyJavaException`: `IndexOutOfBoundsException` / `ArithmeticException`
→ `RangeError`; `NullPointerException` / `ClassCastException` /
`NumberFormatException` → `TypeError`. Name is stamped on
`EngineException.jsErrorName` and prefixed to the message.

**`JsError.constructor` populated** at the JS try/catch wrapping site
(`Interpreter.evalTryStmt`) by resolving the registered global for the error's
`.name`, so `assert.throws(Ctor, fn)` reading `thrown.constructor.name` works.

**Error position framing leads with the message.** `Node.toStringError`
appends `    at <path>:<line>:<col>` (JS-stack-frame-style) instead of the
engine-internal `<line>:<col> <NodeType>` prefix.

**`EngineException` exposes a structured `getJsMessage()`.** The unframed
JS-side `.message` value (no `<Name>:` prefix, no host `js failed: /
==========` frame) — what `e.message` inside a JS `catch` would observe.
Distinct from `getMessage()` (kept framed for logs) and complements
`getJsErrorName()`. Set at both wrap sites in `Interpreter`
(`evalProgram` for uncaught throws, `evalStatement` for runtime errors)
and preserved by `Engine.evalInternal` when re-wrapping at the host
boundary. Host callers building a JS-facing surface should prefer this
over parsing the framed message string.

### typeof and callable identity

**`typeof` reports `"function"` on all callable surfaces.** `Terms.typeOf`
returns `"function"` for `JsInvokable`, `JsFunction`, built-in constructor
singletons (via `JsObject.isJsFunction()` — `Boolean` / `RegExp` / error
globals), and `JsCallable` method refs (`[1].map`, `'x'.charAt`). The
`!(value instanceof ObjectLike)` guard keeps `JsObject` / `JsArray` reporting
`"object"`.

### Globals

**`eval` is a global** registered in `ContextRoot.initGlobal` with indirect-
eval semantics (parses/evaluates in engine root scope; non-string args pass
through). Direct-eval scope capture is out of scope.

### Iteration

**Iteration goes through `IterUtils.getIterator`.** Built-ins (JsArray,
JsString, List, native arrays) take fast paths; user-defined `ObjectLike` with
`@@iterator` go through the spec dance. `for-of` on null/undefined TypeErrors
(was silently iterating zero times — non-spec). `for-in` keeps
`Terms.toIterable` (key enumeration over objects, silent zero on
null/undefined per spec). JS-side errors during user iteration propagate via
`context.error` rather than Java exceptions.

**Minimal `Symbol` global.** `ContextRoot.initGlobal` exposes `Symbol.iterator`
/ `Symbol.asyncIterator` as their well-known string keys (`"@@iterator"` /
`"@@asyncIterator"`). No `Symbol(...)` constructor, no unique-symbol identity
— tests needing those still skip via `feature: Symbol`.

### Optional chaining

**Optional chaining sentinel propagation.** `PropertyAccess.SHORT_CIRCUITED`
(distinct identity from `Terms.UNDEFINED`) propagates through chain steps;
`Interpreter.chainStepResult` converts to UNDEFINED only at the chain root.
The "distinct from UNDEFINED" detail is load-bearing — `obj?.a.b` where
`obj.a == null` still throws TypeError per spec. Optional-chain early errors
are validated post-parse in a single walk
(`JsParser.validateOptionalChainEarlyErrors`), not interleaved into the hot
eval loop.

### Object literals & destructuring

**Reserved words as object-literal keys.** `T_OBJECT_ELEM` /
`T_ACCESSOR_KEY_START` are built at class-init from every TokenType with
`keyword == true`, so `{break: x}`, `{default: 1}`, `{class: foo}` parse as
object literals and destructuring LHS patterns.

**Destructuring uses `ObjectLike.getMember`, not `Map.get`.**
`Interpreter.destructurePattern` reads object-source properties via
`ObjectLike.getMember`, falling back to `Map.containsKey` on the
own-properties map to disambiguate absent vs. present-but-undefined. Defaults
fire only on literal `undefined`, not on `null`. Array-source destructuring
routes through `IterUtils.getIterator` and TypeErrors on non-iterable sources
(per spec 13.3.3.5). `evalLitArray` / `evalLitObject` are pure literal
construction — destructuring binds via the unified `destructurePattern` /
`bindTarget` / `bindLeaf` helpers, which recurse on nested patterns and share
between assignment and `var` / `let` / `const` paths.

### Numeric / coercion

**Spec ToString unified** via `Terms.toStringCoerce(Object, CoreContext)`;
`JsObjectPrototype` / `JsArrayPrototype` / `JsBooleanPrototype` /
`JsNumberPrototype` use the spec-correct `toString`. Use
`StringUtils.formatJson` directly for JSON display, not the legacy formatter.

**`Terms.toPrimitive` is the spec ToPrimitive boundary.** Object → primitive
coercion (used by `BigInt()`, `Number()`, radix args of `toString`, `ToIndex`
on `asIntN` / `asUintN`) goes through `Terms.toPrimitive(value, hint,
context)`. Hint `"number"` (default) tries `valueOf` then `toString`; hint
`"string"` reverses. Each callable runs in a sub-context so its errors flow
through `context.updateFrom(...)` rather than wrapping as Java exceptions —
same propagation pattern as `toStringCoerce`. Boxed primitives
(`JsNumber` / `JsString` / `JsBoolean` / `JsBigInt`) unwrap directly to their
`getJavaValue()` rather than dispatching through valueOf; cheaper and
equivalent. Both methods returning objects → TypeError. `Symbol.toPrimitive`
is *not* dispatched (matches our minimal Symbol surface).

**`Terms.narrow()` checks both ends.** Pre-existing bug: `if (d <=
Integer.MAX_VALUE) return (int) d` cast any negative value past
`Integer.MIN_VALUE` to an overflowed int. Fix: both bounds (`d >=
Integer.MIN_VALUE && d <= Integer.MAX_VALUE`) on the int and long collapses.
The collapse rule itself is unchanged for in-range values.

### BigInt

**BigInt rides on `java.math.BigInteger` with type-tested dispatch.**
`BigInteger extends Number`, so it flows through `Terms.objectToNumber`
unchanged. Each arithmetic op in `Terms` (`add`, `mul`, `div`, `mod`, `exp`,
`min`, bit-ops) checks `lhs instanceof BigInteger || rhs instanceof BigInteger`
*before* the existing `doubleValue()` fast path; mixing BigInt with non-BigInt
throws TypeError per spec via `requireBothBigInt`. The branch is paid only by
code that exercises BigInt — plain Number arithmetic stays unchanged. Property
access wraps via `Terms.toJsValue` → `JsBigInt` (sealed primitive, like
`JsNumber` / `JsString` / `JsBoolean`); the `BigInteger` case must be listed
*before* `Number n` because `BigInteger` is a `Number`. Increment/decrement
uses `Terms.incDecStep(operand)` which returns `BigInteger.ONE` for BigInt
operands so `i++` doesn't TypeError on type mixing. `JSON.stringify` pre-walks
for BigInt and throws TypeError; unary `+1n` is a TypeError, unary `-1n`
negates.

**Numeric separators sit on the rare-path lexer rule.** `JsLexer.scanNumber`
uses tight digit loops on the common (separator-free) path; only after the
fast loop terminates does it test `peek() == '_'` and call
`scanDigitsWithSeparators` / `scanHexDigitsWithSeparators` (rare path). The
rare-path scanner enforces "between two digits" by consuming the `_`, then
asserting the next char is a digit; doubled separators error out by the same
check. `Terms.toNumber` strips `_` only when `text.indexOf('_') >= 0` (no
allocation on the common case).

### Property attributes

**Per-property attributes on `JsObject` use a sparse byte map.** `_attrs:
Map<String, Byte>` next to the per-object `nonExtensible` / `sealed` /
`frozen` flags. Bit 0 = writable, bit 1 = enumerable, bit 2 = configurable;
absent key means all-true (the new-property default for plain `obj.x = ...`).
`defineProperty` writes attrs explicitly and uses the spec's "missing fields
default to false on new keys, preserve on existing" rule (different from
`[[Set]]`'s all-true default — this distinction is load-bearing). Per-object
flags `frozen` / `nonExtensible` are kept as fast-path early-exits on
`putMember` / `removeMember` so frozen objects don't have to consult `_attrs`
per write. Read paths: `getOwnPropertyDescriptor` reads `_attrs` (or the
all-true default); `JsObject.jsEntries()` — the back-end for `for...in` /
`Object.keys` / `Object.values` / `Object.entries` / `Object.assign` via
`Terms.toIterable` — filters out non-enumerable keys but is bypassed entirely
when `_attrs == null`. `Object.getOwnPropertyNames` / `hasOwn` go through
`toMap()` directly and are unaffected. `propertyIsEnumerable` consults
`isEnumerable(name)`. Configurability rules enforced on defineProperty:
TypeError on flipping configurable false→true, changing enumerable, switching
data↔accessor shape, or changing a non-writable value — with the spec-allowed
exceptions (writable true→false on data, no-op same-value redefine) passing
through.

**`Object.prototype.hasOwnProperty` is prototype-aware and intrinsic-aware.**
When the receiver is a `Prototype` singleton (`Date.prototype`,
`Array.prototype`, etc.) it consults `Prototype.hasOwnMember` (built-in
methods + userProps); when the receiver is a `JsObject` it consults
`JsObject.hasOwnIntrinsic` alongside the `_map` lookup so built-in
constructors report their intrinsic statics (`Date.prototype`, `Date.now`,
`Date.UTC`, etc.) as own. Subclasses (`JsFunction` exposes `prototype` /
`name` / `length` / `constructor`; `JsDateConstructor` adds `now` / `parse` /
`UTC`) override `hasOwnIntrinsic` to declare the names their `getMember`
resolves directly. Required for the `S15.9.5_A*` and `S15.9.4_A*` test
clusters and analogous tests under other built-ins.

**Intrinsic-attribute pipeline.** Built-in own properties resolved via
subclass `getMember` switches (not via `_map`) declare themselves as own
through `hasOwnIntrinsic(name)` and report attribute bits through
`getOwnAttrs(name)`. `JsFunction` returns spec defaults for its four
intrinsics (`length` / `name`: configurable-only; `prototype`: writable;
`constructor`: writable + configurable); subclasses (`JsMath`, etc.) cover
their own methods/constants. The descriptor read pipeline
(`Object.getOwnPropertyDescriptor`, `propertyIsEnumerable`, `Object.keys` /
`for...in` enumerable filter) consults this rather than the all-true default.
New attribute slots on `_attrs` (set by `Object.defineProperty`) win over the
intrinsic defaults so user override is still possible.

**Tombstone-on-delete for intrinsic properties.** `_tombstones: Set<String>`
(lazy) records intrinsic own properties that the user has deleted (`delete
obj.foo`). `getMember` short-circuits tombstoned names to the prototype chain
(skipping subclass intrinsic field fallback); `isOwnProperty` returns false;
`removeMember` populates the set when the intrinsic is configurable;
`putMember` clears the tombstone on a successful write so reassignment revives
the property. Matters for `propertyHelper.verifyProperty`'s destructive
`isConfigurable()` check, which tries `delete obj[name]` and asserts
`!hasOwnProperty(obj, name)`.

**`JsObject.isOwnProperty(name)` is the canonical own-key check.** Returns
true iff `name` is in `_map` OR `hasOwnIntrinsic(name)` AND not tombstoned.
Replaces the previous mix of `toMap().containsKey + hasOwnIntrinsic` checks at
three call sites (`JsObjectConstructor.isOwnKey`,
`JsObjectPrototype.hasOwnProperty`, `propertyIsEnumerable`). Anything that
wants spec-level "is this an own property" goes through here.

### Prototype machinery

**Built-in prototypes accept user-added properties.** `Prototype` has a
`userProps` map; user-added properties win over built-ins on lookup
(configurable: true / writable: true per spec). Built-in methods themselves
can't be deleted via `removeMember`. Required for `Array.prototype.foo = ...`
polyfill patterns and for spec-conformant test262 behavior.

**Per-Engine prototype isolation.** Built-in prototypes are JVM-wide
singletons (e.g. `JsArrayPrototype.INSTANCE`), but their `userProps` must
reset each time a new `Engine` is constructed — otherwise a previous test
that did `Map.prototype.set = function() { throw ... }` poisons the next
session. `Prototype` constructor registers each singleton in a static `ALL`
list; `Engine()` calls `Prototype.clearAllUserProps()` which walks the list
and clears each `userProps` map. Spec-compliant for sequential single-Engine
usage (the realistic case); concurrent Engines in the same JVM still share
the underlying singleton during overlapping windows. Performance impact:
invisible — the loop is ~10 entries with usually-empty maps, lost in noise at
script-eval scale.

The same pattern holds on the constructor side via `JsObject.ENGINE_RESET_LIST`
+ `clearEngineState()`. All nine built-in constructor singletons (Array /
BigInt / Date / Function / Map / Number / Object / Set / String) register
themselves; `Engine.<init>` invokes `JsObject.clearAllEngineState()` right
after `Prototype.clearAllUserProps()`. Default `clearEngineState()` wipes
`_map` / `_attrs` / `_tombstones` / extensibility flags; subclasses with
caches override and `super.clearEngineState()` first.

**Function declarations hoist** at the start of the enclosing program / block
scope. `Interpreter.hoistFunctionDeclarations` walks immediate `STATEMENT >
FN_EXPR` children, evaluates each — binding the name. The main loop in
`evalProgram` / `evalBlock` then *skips* the FN_EXPR statement (re-evaluating
would replace the hoisted binding with a fresh `JsFunctionNode` and drop any
property assignments made on the hoisted function, e.g. `foo.prototype = X`
before `function foo(){}`). Per spec FunctionDeclaration's completion is
empty (the previous value carries through); we additionally fall back to the
last hoisted function as the completion when a script contains *only*
declarations, so host callers loading a script that's just `function fn()
{...}` still get `fn` back from `eval`.

**`Array.prototype.*` are generic over array-like `this`.**
`JsArrayPrototype.rawList` falls back to a `0..length-1` snapshot via
`getMember(String.valueOf(i))` for any ObjectLike with a numeric `.length` —
so `Array.prototype.every.call(date, fn)` and the
`Array.prototype.X.call(obj, ...)` test262 pattern work. Mutating methods on
a non-array operate on the snapshot list and don't write back (would need
spec ToObject + index-write semantics; not yet modeled).

**`JsArray.getMember` resolves canonical numeric-index keys.**
`Array.prototype` lookup includes `String.valueOf(i)` reads (e.g. inside
`rawList`'s array-like fallback). `JsArray.getMember("3")` returns
`list.get(3)` rather than delegating to the prototype chain. Strict canonical
parse (rejects `"01"`, `"+1"`, `"1.0"`) so non-canonical string keys still go
to namedProps / proto chain.

**`JsArray.HOLE` sentinel marks sparse slots.** Distinct singleton (not
`null`, not `Terms.UNDEFINED`) — `[0,,2]` writes `HOLE` at index 1 so
`arr.hasOwnProperty(1) === false` while `[0,null,2].hasOwnProperty(1) ===
true` (our previous shared-`null` storage couldn't model this). Read seams
translate `HOLE` → `undefined` (`JsArray.getElement`, `List.get`,
`PropertyAccess.getByIndex` raw-List branch, `IterUtils.listIterator`) so
user code never observes the sentinel. `JsArray.jsEntries` *skips* `HOLE`
entries — the spec says `Array.prototype.{forEach, map, filter, every,
some, find, findIndex, reduce, reduceRight}` and `for...in` skip holes,
while `for...of` / spread / destructuring read holes as `undefined` (the
listIterator path).

**`JsArray` length semantics (§10.4.2.4 ArraySetLength).** `arr.length = N`
and `Object.defineProperty(arr, "length", {value: N})` both route through
`JsArray.handleLengthAssign(value, context)` → `applySetLength(int)`.
Three spec checks land in order:

1. **ToUint32 + ToNumber + RangeError on mismatch** (steps 3–5). NaN,
   Infinity, negative, fractional, and `> 2^32-1` values all throw
   `RangeError("Invalid array length")` — *unconditionally*, not gated by
   strictness. The double-coercion is observable: when the value is an
   `ObjectLike`, `Terms.toPrimitive(value, "number", context)` is called
   twice (test262 `define-own-prop-length-coercion-order.js` asserts
   `valueOfCalls === 2`). `new Array(N)` runs the same validation in
   `JsArray.create`. Bounded by `Integer.MAX_VALUE` today — the larger
   Uint32 range (up to `4294967295`) needs a separate `long` length field
   decoupled from `list.size()` (deferred).
2. **Length writable check** (step 12). Returns `false` when length's
   stored writable bit is clear; caller decides whether to throw
   `TypeError`. The four mutating prototype methods
   (`pop`/`shift`/`unshift`/`push`) check upfront via
   `JsArrayPrototype.requireWritableLength` and throw `TypeError` —
   matches the spec's `Set(O, "length", newLen, true)` Throw=true
   semantics. Direct `arr.length = X` silently no-ops on writable=false
   in lenient mode (strict-mode TypeError flip is a separate project).
3. **Partial truncate when an index in `[newLen, oldLen)` is
   non-configurable.** Walks the truncate range high-to-low; on a blocking
   index, truncates above it, returns `false`. `Object.defineProperty`
   surfaces the `false` as `TypeError("Cannot redefine property: length")`.
   `_attrs` and `namedProps` entries for cleared indices are removed.

`length`'s descriptor starts `{writable: true, enumerable: false,
configurable: false}` (`JsArray.getOwnAttrs` consults `_attrs["length"]`
for the writable override; the other two bits are always non-spec-
configurable). The spec-precise interleaving where a prototype getter on
the deleted index mutates `length`'s writable bit *during* pop/shift —
asserted via call-count in `set-length-array-length-is-non-writable.js` —
is not yet modeled (upfront check vs. spec's get → delete → set ordering).

**`JsArray` indexed-accessor enforcement.** Descriptors installed via
`Object.defineProperty(arr, i, {get/set/value: ...})` land in `namedProps`
under the canonical string-form key and take precedence over the dense
list. Reads dispatch via `JsArray.getIndexedSlot(i)` (hot path: single
null-check on `namedProps`); writes route through the named-key path when
`hasIndexedDescriptor(i)` so `JsAccessor` setters fire.
`JsArrayPrototype.rawList` / `jsEntries` take the per-index snapshot path
when `arr.hasAnyDescriptor()` so callbacks see resolved values, not the
accessor wrapper.

**`JsArray._attrs` mirrors `JsObject._attrs`.** Per-property attribute byte
map (sparse `Map<String, Byte>`) keyed by canonical name — numeric indices
use their string form (`"0"`, `"1"`, ...). Bit layout matches `JsObject`
(writable / enumerable / configurable). Three storage layers cooperate:
`list` holds default-attr data values at numeric indices, `namedProps` holds
accessor descriptors and non-index keys, `_attrs` holds the attribute byte
for any key whose triplet deviates from the all-true default. Plain
`arr[i] = x` doesn't allocate `_attrs`; `defineProperty(arr, "0",
{writable: false, value: x})` writes the value to the dense list (so
iteration semantics are preserved) and records the attribute byte in
`_attrs["0"]`. Subsequent `arr[0] = y` is silently ignored: `putMember`
checks `_attrs` for writable=false, and `hasIndexedDescriptor(i)` returns
true when `_attrs` has an entry, routing the indexed-write through
`setByName` so the check fires. `getOwnAttrs` consults `_attrs` for all
keys including `"length"` — defineProperty can flip length's writable bit
to false (the only mutable spec bit; enumerable/configurable are masked
out on read since length is permanently non-enumerable + non-configurable).
`Object.defineProperty` dispatches to
`JsArray.defineOwn(name, value, attrs)` via `applyDefine`; data descriptors
at numeric indices clear any prior `namedProps` entry (the dense slot
becomes the authoritative read) while accessor descriptors land in
`namedProps` as before.

**`JsArray.isOwnProperty` is the canonical own-key check for arrays.**
Returns true iff `name` is `"length"`, in `namedProps` (descriptors / named
properties), or a canonical numeric index in range with `list.get(i) !=
HOLE`. Wired through `Object.hasOwn`, `arr.hasOwnProperty`,
`Object.getOwnPropertyDescriptor`, and the `ownKeys` helper that backs
`Object.keys` / `Object.getOwnPropertyNames` (which emits indices in
ascending order, then named-prop keys, then `"length"`).

**`Function.prototype.bind`** in `JsFunctionPrototype.bindMethod`: returns a
new `JsFunction` whose `call(ctx, args)` sets `ctx.thisObject = boundThis`
and prepends pre-bound args to the caller's args. `length` / `name` of the
bound function are approximate (name is `"bound " + target.name`); call
semantics are what matters.

### Date

**Date stores `[[DateValue]]` as `double` with NaN = Invalid Date.** `JsDate`
no longer uses `long millis`; the spec representation is a Number that may be
NaN, and Java's `(long) NaN == 0` would silently collapse Invalid Date to
epoch. Methods route through pure helpers (`JsDate.makeDay` / `makeTime` /
`makeDate` / `timeClip` / `localToUtc` / `utcToLocal` / `parseToTimeValue`)
so the Constructor and Prototype share spec algorithms. `localTzaMs` is
truncated to integer minutes so historical zones with sub-minute offsets
round-trip through `getTimezoneOffset()` (which spec defines as integer
minutes). `requireDate(context)` TypeErrors on non-Date `this` (Spec
thisTimeValue). Setters read `[[DateValue]]` *before* coercing args, coerce
all args even when the captured value is NaN (preserves observable side
effects from `valueOf`), then bail without writing back when the captured
value was NaN — the date might have been mutated to a valid value during
coercion and must not be clobbered.

### Templates

**Tagged-template AST shape.** `FN_TAGGED_TEMPLATE_EXPR` is `[<callable>,
LIT_TEMPLATE]`. The `LIT_TEMPLATE` child holds paired cooked/raw string
segments and substitution expressions; for N substitution expressions there
are always N+1 string slots (possibly empty). The `strings` JsArray passed to
the tag has its `raw` array attached via `putMember("raw", raw)`. `new
tag\`x\`` evaluates the tagged template first (MemberExpression semantics)
then constructs with no args. `${obj}` interpolations dispatch through the
prototype chain (so user `toString` throws propagate with constructor
identity intact). Template-literal lexing is depth-tracked for nested `{}`
inside `${...}`.

### Object.prototype.toString

**`Object.prototype.toString` dispatches on the host wrapper class.**
`JsObjectPrototype.DEFAULT_TO_STRING` returns `"[object <Tag>]"` where the
tag is derived from the receiver type — `Array` for `JsArray` / `List`,
`Date` for `JsDate` / `java.util.Date`, `RegExp` / `Map` / `Set` / `Error` /
`Boolean` / `Number` / `String` / `Function`, and `Object` as the fallback.
`JsObject` implements `JsCallable` (host-side artifact) so the `Function`
branch must exclude plain `JsObject` instances — only `JsFunction` (and
`JsObject` whose `isJsFunction()` returns true) qualifies. Substitute for the
spec's `@@toStringTag` until Symbol expansion.

---

## Numeric Conversion Pattern

"Unwrap first, then switch on raw types":

```java
static Number objectToNumber(Object o) {
    // Unwrap JsValue first using getJsValue()
    if (o instanceof JsValue jv) {
        o = jv.getJsValue();
    }
    return switch (o) {
        case Number n -> n;
        case Boolean b -> b ? 1 : 0;
        case Date d -> d.getTime();
        case String s -> toNumber(s.trim());
        case null -> 0;
        // includes undefined
        default -> Double.NaN;
    };
}
```

---

## Usage Examples

### Basic Engine Usage

```java
Engine engine = new Engine();
Object result = engine.eval("1 + 2");
// result = 3
```

### Java Interop

```java
Map<String, Object> context = new HashMap<>();
context.put("greeting", "Hello");

Engine engine = new Engine();
engine.putAll(context);
Object result = engine.eval("greeting + ' World'");
// result = "Hello World"
```

### Date Handling

```java
engine.put("javaDate", new java.util.Date(1609459200000L));
assertEquals(1609459200000L, engine.eval("javaDate.getTime()"));
assertEquals(2021, engine.eval("javaDate.getFullYear()"));
```

---

## SimpleObject Pattern

`SimpleObject` is an interface for exposing Java objects to JavaScript with custom property access. It extends `ObjectLike` and provides default implementations.

### Required Methods

| Method | Purpose |
|--------|---------|
| `jsGet(String name)` | Property accessor - implement via switch expression |
| `jsKeys()` | Return property names for serialization (override required) |

### How It Works

```java
public class ProcessHandle implements SimpleObject {

    // List of exposed properties - required for toMap()/toString
    private static final List<String> KEYS = List.of(
        "stdOut", "stdErr", "exitCode", "alive", "pid",
        "waitSync", "close", "signal"
    );

    @Override
    public Collection<String> jsKeys() {
        return KEYS;  // Enables enumeration and JSON serialization
    }

    @Override
    public Object jsGet(String key) {
        return switch (key) {
            case "stdOut" -> getStdOut();
            case "exitCode" -> getExitCode();
            case "waitSync" -> (JavaCallable) (ctx, args) -> waitSync();
            // ... other properties
            default -> null;
        };
    }
}
```

### Key Behaviors

1. **`jsKeys()` enables serialization** - `toMap()` iterates over `jsKeys()` and calls `jsGet()` for each:
   ```java
   default Map<String, Object> toMap() {
       return toMap(jsKeys(), this);  // Uses jsKeys() to enumerate
   }
   ```

2. **Custom `toString` support** - If the object has a `toString` property returning `JsCallable`, it's used:
   ```java
   default JsCallable jsToString() {
       Object temp = jsGet("toString");
       if (temp instanceof JsCallable jsc) {
           return jsc;  // Use custom toString
       }
       return (context, args) -> toString(toMap());  // Fallback to JSON
   }
   ```

3. **`jsGet()` handles property access** - The switch expression is efficient and type-safe. Return `JavaCallable` or `JavaInvokable` for methods.

4. **`jsGet()` is inherently lazy** - Called on every property access, so values are computed fresh each time. No need for `Supplier` pattern.

### Why Both `jsKeys()` and `jsGet()`?

- **`jsGet()`** - Handles individual property access (e.g., `proc.stdOut`)
- **`jsKeys()`** - Enables enumeration for `toMap()`, JSON serialization, and `Object.keys()` in JS

Without `jsKeys()`, the object works for property access but serializes to `{}`.

### Property Presence Detection

For `JsObject`, property presence is detected using `Map.containsKey()` before calling `getMember()`. This allows distinguishing between:
- Property exists with value `null` → returns `null`
- Property doesn't exist → continues up the prototype chain

For `SimpleObject`, there is no `hasMember()` API. When `jsGet()` returns `null`, it's treated as "property not found". This simplifies implementation for Java interop classes that don't need to declare all keys upfront - they only need `jsKeys()` for serialization and `jsGet()` for access. If a property genuinely needs to hold `null`, consider using a sentinel value or implementing the full `JsObject` interface instead.

---

## Lazy Variables with Supplier

The engine supports lazy/computed variables via `java.util.function.Supplier`. When a variable's value is a `Supplier`, it is automatically invoked when accessed:

```java
// In CoreContext.get()
if (result instanceof Supplier<?> supplier) {
    return supplier.get();
}
```

### Usage

```java
Engine engine = new Engine();

// Static value - evaluated once at put time
engine.put("staticValue", someObject.getValue());

// Lazy value - evaluated each time it's accessed
engine.put("lazyValue", (Supplier<String>) () -> someObject.getValue());
```

### Use Cases

1. **Deferred computation** - Value is computed only when accessed
2. **Dynamic values** - Value can change between accesses
3. **Reduced per-call overhead** - Set up once, resolve on demand

### Example: Mock Server Request Variables

The mock server uses this pattern to avoid setting request variables on every HTTP request:

```java
// Set up once during initialization
engine.put("requestPath", (Supplier<String>) () ->
    currentRequest != null ? currentRequest.getPath() : null);
engine.put("requestMethod", (Supplier<String>) () ->
    currentRequest != null ? currentRequest.getMethod() : null);

// Per request, only update the reference
this.currentRequest = incomingRequest;

// When script accesses requestPath, Supplier.get() is called automatically
// * def path = requestPath  →  invokes the Supplier
```

This reduces per-request `engine.put()` calls from many to just one field assignment.

---

## Hidden Root Bindings

`putRootBinding()` creates variables that are accessible in scripts but hidden from `getBindings()`:

```java
Engine engine = new Engine();
engine.putRootBinding("magic", "secret");
engine.put("normal", "visible");

engine.eval("magic");              // "secret" - accessible
engine.eval("normal");             // "visible" - accessible

engine.getBindings().containsKey("magic");   // false - hidden!
engine.getBindings().containsKey("normal");  // true - visible
```

### Use Cases

1. **Internal/system variables** - Variables scripts can use but shouldn't enumerate
2. **Fallback values** - Suite-level resources that feature scripts can access
3. **Magic variables** - Built-in helpers that shouldn't pollute user namespace

### With Lazy Evaluation

Root bindings also support `Supplier` for lazy/dynamic values:

```java
String[] suiteDriver = { null };

engine.putRootBinding("driver", (Supplier<String>) () -> suiteDriver[0]);

engine.eval("driver");  // null initially
suiteDriver[0] = "suite-driver";
engine.eval("driver");  // "suite-driver" - lazily resolved

engine.getBindings().containsKey("driver");  // false - still hidden
```

---

## Variable Scoping and Isolation

The engine provides multiple patterns for controlling variable scope across script executions.

### The Problem: `const`/`let` Redeclaration

When reusing an engine across multiple `eval()` calls, `const` and `let` declarations persist:

```java
Engine engine = new Engine();
engine.eval("const a = 1");
engine.eval("const a = 2");  // ERROR: identifier 'a' has already been declared
```

This matches ES6 behavior where top-level `const`/`let` cannot be redeclared in the same scope.

### Solution 1: `evalWith()` for Complete Isolation

`evalWith()` creates a fully isolated scope. Variables declared inside don't leak out:

```java
Engine engine = new Engine();
engine.put("shared", new HashMap<>());

Map<String, Object> vars1 = new HashMap<>();
engine.evalWith("const a = 1; shared.x = a;", vars1);
// vars1.get("a") = 1

Map<String, Object> vars2 = new HashMap<>();
engine.evalWith("const a = 2; shared.y = a;", vars2);  // No conflict!
// vars2.get("a") = 2

// Engine bindings unaffected
engine.getBindings().containsKey("a");  // false
```

**Key behaviors of `evalWith()`:**
- `const`/`let`/`var` declarations stay in the vars map
- Implicit globals (`foo = 42`) also stay in the vars map (don't leak)
- Can read engine bindings (e.g., `shared` above)
- Can mutate objects in engine bindings

### Solution 2: IIFE Wrapping for Partial Isolation

Wrap scripts in an Immediately Invoked Function Expression (IIFE) to isolate `const`/`let` while allowing implicit globals to persist:

```java
Engine engine = new Engine();
engine.put("shared", new HashMap<>());

// Wrap script in IIFE
engine.eval("(function(){ const json = {a: 1}; shared.first = json.a; })()");
engine.eval("(function(){ const json = {b: 2}; shared.second = json.b; })()");  // No conflict!

// Implicit globals persist to engine scope
engine.eval("(function(){ persistedVar = 42; })()");
engine.get("persistedVar");  // 42
```

This pattern is used by Postman's sandbox for script execution.

### Comparison Table

| Behavior | `eval()` | `evalWith()` | IIFE via `eval()` |
|----------|----------|--------------|-------------------|
| `const`/`let` isolation | No (persists) | Yes (in vars map) | Yes (function-scoped) |
| `var` isolation | No (persists) | Yes (in vars map) | Yes (function-scoped) |
| Implicit globals | Persists to engine | Isolated (in vars map) | **Persists to engine** |
| Access engine bindings | Yes | Yes | Yes |
| Mutate shared objects | Yes | Yes | Yes |

### Implicit Global Assignment (ES6 Non-Strict)

Assigning to an undeclared variable creates a global (ES6 non-strict mode behavior):

```java
Engine engine = new Engine();
engine.eval("function foo() { implicitGlobal = 42; }");
engine.eval("foo()");
engine.get("implicitGlobal");  // 42 - created at global scope
```

This also works inside IIFEs, making them useful for script runners that need `const`/`let` isolation while allowing intentional global state sharing.

### Use Case: Script Runner (e.g., Postman-like)

For running multiple user scripts that may declare same-named variables:

```java
public void runScript(String script) {
    // Wrap in IIFE to isolate const/let but allow global mutations
    engine.eval("(function(){" + script + "})()");
}

// User scripts can use const/let freely
runScript("const json = response.json(); pm.test('ok', () => {});");
runScript("const json = response.json(); pm.test('ok', () => {});");  // No conflict!
```

### Strict Mode Policy

karate-js has **no strict/sloppy distinction**. There is a single execution
mode, and it is closer to sloppy than to strict. A `"use strict"` (or
`'use strict'`) directive is accepted but has no effect — it parses as a
plain string-literal ExpressionStatement and its value is discarded like
any other expression-statement result. This is the spec-intended
backward-compatible behavior for an engine that does not implement strict
mode: ES5 deliberately chose a string-literal directive form so that
pre-ES5 engines would silently ignore it.

Consequences:

- `with`, duplicate parameter names, octal literals like `0755`, and
  assignments to `eval`/`arguments` are not rejected as SyntaxErrors.
- `this` in a plain function call resolves to the global binding object,
  not `undefined`.
- Assigning to an undeclared name creates a global (see above).
- The test262 harness skips tests flagged `onlyStrict` via
  `karate-js-test262/etc/expectations.yaml`; there is no plan to
  implement a parser-side strict flip.

If you need strict semantics, run your code in an engine that supports
them; karate-js is a pragmatic embedded engine tuned for LLM-written and
hand-written idiomatic JS, not for spec-lawyer strict-mode enforcement.

---

## File References

| Purpose | File |
|---------|------|
| Engine | `karate-js/src/main/java/io/karatelabs/js/Engine.java` |
| CoreContext | `karate-js/src/main/java/io/karatelabs/js/CoreContext.java` |
| SimpleObject | `karate-js/src/main/java/io/karatelabs/js/SimpleObject.java` |
| JsValue | `karate-js/src/main/java/io/karatelabs/js/JsValue.java` |
| JsUndefined | `karate-js/src/main/java/io/karatelabs/js/JsUndefined.java` |
| JsPrimitive | `karate-js/src/main/java/io/karatelabs/js/JsPrimitive.java` |
| Bindings | `karate-js/src/main/java/io/karatelabs/js/Bindings.java` |
| BindValue | `karate-js/src/main/java/io/karatelabs/js/BindValue.java` |
| JsCallable | `karate-js/src/main/java/io/karatelabs/js/JsCallable.java` |
| JavaCallable | `karate-js/src/main/java/io/karatelabs/js/JavaCallable.java` |
| JsError | `karate-js/src/main/java/io/karatelabs/js/JsError.java` |
| FlowControlSignal | `karate-js/src/main/java/io/karatelabs/js/FlowControlSignal.java` |
| Terms | `karate-js/src/main/java/io/karatelabs/js/Terms.java` |
| JsDate | `karate-js/src/main/java/io/karatelabs/js/JsDate.java` |
| CallInfo | `karate-js/src/main/java/io/karatelabs/js/CallInfo.java` |
| Prototype base | `karate-js/src/main/java/io/karatelabs/js/Prototype.java` |
| ObjectLike | `karate-js/src/main/java/io/karatelabs/js/ObjectLike.java` |
| Prototype singletons | `Js*Prototype.java` (JsObjectPrototype, JsArrayPrototype, etc.) |
| Constructor singletons | `Js*Constructor.java` (JsObjectConstructor, JsArrayConstructor, etc.) |
| Parser infrastructure | `karate-js/src/main/java/io/karatelabs/parser/` |
| Gherkin parser | `karate-core/src/main/java/io/karatelabs/gherkin/` |
| Tests | `karate-js/src/test/java/io/karatelabs/js/` |

---

## Performance Benchmarks

Results from `karate-js/src/test/java/io/karatelabs/parser/EngineBenchmark.java`. The benchmark runs two 20 KB scripts: an array-method-heavy workload (`filter`/`map`/`reduce`/`find`/`some`/`every`/`slice`/`concat`/`indexOf`) and an object-method-heavy workload (`Object.keys`/`values`/`entries`/`assign`/`hasOwnProperty`/`toString`). Each script allocates a fresh `Engine` per iteration.

Invoke via:

```bash
# Fast mode: median of 10 runs
java -cp "karate-js/target/classes:karate-js/target/test-classes:<deps>" \
  io.karatelabs.parser.EngineBenchmark

# Profile mode: 30 s warm loop, averages over thousands of iterations (JIT-stable, low noise)
java -cp ... io.karatelabs.parser.EngineBenchmark profile
```

### Reference machine

| | |
|---|---|
| Hardware | MacBook Pro (MacBookPro18,1), Apple M1 Pro, 10 cores (8P+2E), 16 GB |
| OS | macOS 26.3.1 |
| JDK | OpenJDK 24.0.2 |

### Results — 2026-04-22 (profile mode, 30 s averages)

| Commit | Array 20 KB eval | Object 20 KB eval | Iterations/30 s |
|---|---|---|---|
| `28d020b87` — benchmark introduced (2026-01-22) | 2.06 ms | 0.84 ms | 10,294 |
| `60b6fde76` — current HEAD (2026-04-22) | **1.32 ms** | **0.50 ms** | **16,397** |
| Speedup | **1.56×** | **1.68×** | **1.59×** |

Engine instantiation is essentially unchanged (~0.4–0.6 µs median in both). The gains come from the cumulative perf work landed between the two commits: tighter `Node` allocation and pre-sized child arrays, static `PropertyAccess`, level-keyed bindings replacing per-scope contexts, EnumSet token lookups in the parser, and lazy ArrayList init on token-only nodes.

### Notes on interpretation

- Fast mode (median of 10) is noisy — the first 1–2 measured iterations consistently show a tail from residual JIT/GC work, despite the 5-iteration warmup. Prefer profile mode for comparing commits.
- Results are sensitive to thermal state and background load on the M1 Pro; expect ±5–10% run-to-run even in profile mode.
- The scripts are deterministic in size (20,722 B array / 20,642 B object) and regenerated per JVM, so cross-commit comparisons are apples-to-apples as long as `EngineBenchmark.java` itself is unchanged.

---

## Future Improvements (Swift Engine Comparison)

This section documents potential improvements identified by comparing the Java engine with a Swift-based JavaScript engine implementation. The Swift engine is smaller (~8 files vs 50+) because it implements fewer features (no prototype chain, no regex, simpler scoping). The Java engine's complexity is justified by its requirements: full ES6 scoping, prototype chain, Java interop, IDE tooling support, and event/debugging system.

**Overall Assessment:** The Java engine is reasonably well-designed given its feature requirements. The areas below represent opportunities for modernization and cleanup rather than fundamental architectural issues.

### 1. ✅ Sealed Interface for Value Types (Java 21+) — COMPLETED

**Status:** Implemented in commit `9103c26`.

**Implementation:** Introduced a sealed `JsValue` hierarchy for JS wrapper types that need Java interop conversion:

```java
public sealed interface JsValue permits JsUndefined, JsPrimitive, JsDateValue, JsBinaryValue {
    Object getJavaValue();              // For external use (e.g., JsDate → Date)
    default Object getJsValue() {       // For internal operations
        return getJavaValue();
    }
}

// Sub-hierarchies (all sealed)
sealed interface JsPrimitive extends JsValue permits JsNumber, JsString, JsBoolean, JsBigInt {}
sealed interface JsDateValue extends JsValue permits JsDate {}
sealed interface JsBinaryValue extends JsValue permits JsUint8Array {}

// Singleton for undefined
public final class JsUndefined implements JsValue {
    public static final JsUndefined INSTANCE = new JsUndefined();
}
```

**Additional changes in this refactor:**
- `Terms.UNDEFINED` now uses `JsUndefined.INSTANCE` (singleton for identity comparison)
- `Bindings` class using `Map<String, BindValue>` for scope storage with auto-unwrapping at Java boundaries
- `BindValue` consolidates binding name, value, type (let/const/var), and initialization state
- `JsFunctionWrapper` for auto-converting function return values
- Removed `JsErrorPrototype` (JsError now uses JsObjectPrototype directly)
- Made all prototype helper methods static (`asString`, `asNumber`, `asDate`, etc.)
- Identity-based `equals/hashCode` on `JsObject`, `JsArray`, `Bindings` to prevent circular reference issues

**Benefits achieved:**
- Cleaner type hierarchy with compiler-enforced exhaustive handling
- Single `instanceof JsValue` check replaces scattered type checks
- `getJsValue()` provides uniform unwrapping for internal operations
- Singleton prototypes shared across Engine instances (`userProps` reset per
  Engine — see [Spec Invariants § Prototype machinery](#prototype-machinery))

---

### 2. Future TODO Items

> The prioritized work list lives in
> [karate-js-test262/TEST262.md § Active priorities](../karate-js-test262/TEST262.md#active-priorities)
> and [§ Deferred TODOs](../karate-js-test262/TEST262.md#deferred-todos). The
> items below are the architectural shape only.

**JavaScript Stack Traces for Errors**
- Single-frame position is done: `Node.toStringError` now appends
  `    at <path>:<line>:<col>` (JS-stack-frame-style) after the user
  message. Enough that LLMs reading `.message` get a source locator.
- Multi-frame call stack still TODO — would track function entry/exit
  in `Interpreter.evalFnCall`, stash name + source on `CoreContext`,
  and capture the chain on throw. Priority: medium.

**Async/Await + Promises**
- Engine is synchronous; no event loop or microtask queue. LLMs write
  `async`/`await` reflexively, so the current full-skip of
  `feature: Promise` / `feature: async-functions` / `flag: async` in
  `etc/expectations.yaml` rejects a large fraction of modern JS. See
  [TEST262.md § Deferred TODOs](../karate-js-test262/TEST262.md#deferred-todos)
  for the deferred-design notes (smooth Java interop, graceful degradation
  when async is decorative, simple multi-threading for real async I/O).
- One viable path: **synchronous subset first** — `Promise` as an eagerly-
  resolving thenable, `async function` runs synchronously and wraps its
  result in `Promise.resolve`, `await expr` synchronously unwraps a
  thenable. Breaks genuinely-concurrent workloads but handles the ~80% of
  LLM glue where `async`/`await` is shape not parallelism. Escalate to a
  full microtask-queue runtime only when a real workload needs timer-
  driven scheduling.
- Priority: deferred until a real workload demands it.

**Class Syntax (ES6)**
- Engine has prototype machinery + `new` but no parser support for
  `class` / `extends` / `super` / method-definition. Currently fails
  loudly at parse time with `SyntaxError` — the right shape (code that
  uses `class` should fail loudly, not silently produce wrong behavior).
- LLMs writing glue / test code default to function + prototype style;
  pick up only if a real workload demands. See
  [TEST262.md § Deferred TODOs](../karate-js-test262/TEST262.md#deferred-todos).

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

### Slot family — property descriptors and bindings

```java
sealed abstract class PropertySlot permits DataSlot, AccessorSlot {
    final String name;
    byte attrs = ATTRS_DEFAULT;        // W|E|C plus an INTRINSIC bit
    boolean tombstoned;                // shadows an intrinsic / proto entry on delete

    abstract Object read(Object receiver, CoreContext ctx);
    abstract void   write(Object receiver, Object newValue, CoreContext ctx, boolean strict);
}

final class DataSlot extends PropertySlot { Object value; }
final class AccessorSlot extends PropertySlot { JsCallable getter, setter; }

final class BindingSlot {                       // separate root, not under PropertySlot
    final String name; Object value;
    BindScope scope; boolean initialized = true;
    int level; BindingSlot previous; short evalId; boolean hidden;
    byte attrs; boolean attrsExplicit;          // for JsGlobalThis surface
    boolean tombstoned;                         // for delete on lazy-realized built-ins
}
```

Two distinct families:

- **`PropertySlot`** is the storage primitive for own properties on
  `JsObject` / `JsArray` / `Prototype`. Sealed with two concrete shapes
  matching ES 6.2.5 PropertyDescriptor (data vs. accessor). The polymorphic
  `read` / `write` seam is what `getMember(receiver, ctx)` and
  `PropertyAccess.setByName` dispatch through — no `instanceof JsAccessor`
  unwrap sites in the hot path.
- **`BindingSlot`** is the storage primitive for variable bindings (lexical-
  scope cells in a `BindingsStore`). Independent from `PropertySlot` because
  bindings carry scope metadata (TDZ, level chain, eval-id, hidden flag)
  that property descriptors don't. Refactor C (post-S4) added the
  `attrs` / `attrsExplicit` / `tombstoned` fields so `JsGlobalThis` can
  surface every observable globalThis state from a single store.

The `INTRINSIC` bit on `attrs` lets per-Engine reset (`clearEngineState()`)
distinguish install-time intrinsics (preserve across engine reuse) from
user-set entries (clear on reset). The `WRITABLE` bit is meaningless for
accessors and not consulted by `AccessorSlot`; the spec's "omit `writable`
from descriptor output for accessors" is handled in
`JsObjectConstructor.buildDescriptor` by branching on the slot family.

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
    JsErrorPrototype.ERROR       ← JsObjectPrototype.INSTANCE
    JsErrorPrototype.{TYPE,RANGE,SYNTAX,REFERENCE,URI,EVAL,AGGREGATE}_ERROR
                                 ← JsErrorPrototype.ERROR

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
    JsErrorConstructor.{ERROR,TYPE_ERROR,RANGE_ERROR,SYNTAX_ERROR,
        REFERENCE_ERROR,URI_ERROR,EVAL_ERROR,AGGREGATE_ERROR}
                                   → prototype: matching JsErrorPrototype.*
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
1. `userProps` slot (user-added properties win per spec; tombstone short-
   circuits to the proto chain)
2. Built-in properties via `resolveBuiltin(name)` (lazy `LazyRef` wrap
   resolved + cached on first access)
3. Delegate to `__proto__` chain

```java
// Base class for built-in prototype objects
abstract class Prototype implements ObjectLike {
    private final Prototype __proto__;
    // Each entry is a PropertySlot — DataSlot for user-added values,
    // AccessorSlot for accessor descriptors installed via
    // Object.defineProperty(Foo.prototype, "x", {get: ...}). The slot's
    // tombstoned flag shadows a built-in deleted via
    // delete Foo.prototype.bar (was a separate Set<String> pre-refactor B).
    private Map<String, PropertySlot> userProps;
    // Install-time built-in members; immutable post-construction. User
    // mutations land in userProps and shadow these.
    private final Map<String, Object> builtins = new LinkedHashMap<>();

    public final Object getMember(String name) {
        // 1. User slot wins (data, accessor, or tombstone)
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) return walkProto(name);
            return s instanceof DataSlot ds ? ds.value : null; // accessor → null at this seam
        }
        // 2. Built-in lookup (resolves + caches LazyRef on first access)
        Object builtin = resolveBuiltin(name);
        if (builtin != null) return builtin;
        // 3. Delegate to __proto__ chain
        return walkProto(name);
    }

    // 3-arg overload invokes accessor getters via slot.read(receiver, ctx)
    public Object getMember(String name, Object receiver, CoreContext ctx) { ... }

    public void putMember(String name, Object value) {
        if (userProps == null) userProps = new LinkedHashMap<>();
        PropertySlot existing = userProps.get(name);
        if (existing instanceof DataSlot ds) {
            ds.value = value;
            ds.tombstoned = false;
        } else {
            userProps.put(name, new DataSlot(name, value)); // also clears any prior accessor / tombstone
        }
    }

    // Mirrors JsObject / JsArray — single-signature own-slot lookup so
    // PropertyAccess.findAccessorInChain can dispatch uniformly.
    final PropertySlot getOwnSlot(String name) { ... }
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
    // Each entry is a sealed PropertySlot — DataSlot (value + attrs +
    // tombstone) or AccessorSlot (getter/setter callables + attrs).
    private Map<String, PropertySlot> props;
    private ObjectLike __proto__ = JsObjectPrototype.INSTANCE;

    // JS internal — raw values, prototype chain. (Simplified — see Spec
    // Invariants § Property attributes for the intrinsic / tombstone /
    // accessor pipeline.)
    public Object getMember(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) {
            if (s.tombstoned) return __proto__ != null ? __proto__.getMember(name) : null;
            return s instanceof DataSlot ds ? ds.value : null; // accessor → null at this seam
        }
        if ("__proto__".equals(name)) return __proto__;
        // Subclass intrinsic hook — e.g. JsFunction's name / length /
        // prototype, JsString.length, JsRegex.source. See § resolveOwnIntrinsic.
        Object intrinsic = resolveOwnIntrinsic(name);
        if (intrinsic != null) return intrinsic;
        return __proto__ != null ? __proto__.getMember(name) : null;
    }

    // 3-arg overload invokes accessor getters via slot.read(receiver, ctx).
    // Single-pass post-refactor A: own slot → intrinsic hook → proto chain.
    public Object getMember(String name, Object receiver, CoreContext ctx) { ... }

    // Canonical own-key check — see Spec Invariants
    public boolean isOwnProperty(String name) {
        PropertySlot s = props == null ? null : props.get(name);
        if (s != null) return !s.tombstoned;
        return hasOwnIntrinsic(name);   // = resolveOwnIntrinsic(name) != null
    }

    // Java interface - auto-unwrap, own properties only
    @Override
    public Object get(Object key) {
        PropertySlot s = props == null || !(key instanceof String n) ? null : props.get(n);
        if (s == null || s.tombstoned) return null;
        return s instanceof DataSlot ds ? Engine.toJava(ds.value) : null; // accessors → null at Java seam
    }
}
```

### ObjectLike Method Naming

To avoid collision with `Map.get(Object)`, ObjectLike uses distinct method names:

| Method | Purpose |
|--------|---------|
| `getMember(String)` | **Raw-value** read with prototype chain. AccessorSlot surfaces as `null` (no extractable raw value). Used by Java-interop, internal fallbacks, and subclass `super.getMember(name)` chains. |
| `getMember(String, Object receiver, CoreContext ctx)` | **JS-semantic resolved** read. AccessorSlot invokes its getter via `slot.read(receiver, ctx)`. `receiver` is the object the property is being read on (may differ from `this` when walking a prototype chain); `ctx` threads through to the getter call. Default delegates to 1-arg; `JsObject` / `JsArray` / `Prototype` / `JsGlobalThis` override. |
| `putMember(String, Object)` | JS property assignment. |
| `removeMember(String)` | JS property deletion. |
| `isOwnProperty(String)` | Canonical own-key check. Default reads `toMap()`; `JsObject` / `JsArray` / `Prototype` override with tighter implementations distinguishing tombstones from absent keys and intrinsic-installed entries. |
| `getPrototype()` | Returns the prototype (`__proto__`) for chain walking. |

### Conversion at Boundaries

Conversion happens at specific boundaries:

1. **`Engine.eval()` return** - Top-level value converted via `toJava()`
2. **`List.get()` / `Map.get()`** - Elements unwrapped lazily on access
3. **JavaCallable args** - Arguments converted before external Java method call
4. **Iteration** - Iterator unwraps values lazily

### `resolveOwnIntrinsic` — subclass intrinsic hook

```java
// JsObject — default implementation
protected Object resolveOwnIntrinsic(String name) {
    return null;
}
```

Subclasses with intrinsic members not stored in `props` — `JsString.length`,
`JsRegex.source` / `flags` / `lastIndex`, `JsFunction.prototype` / `name` /
`length`, `JsArray.length` and numeric-index reads, `JsError.message` / `name`
/ `constructor`, `JsMap.size`, `JsSet.size`, `JsReflect.construct` /
`apply`, `JsTextEncoder.encode`, `JsTextDecoder.decode` / `encoding`,
`JsUint8Array.length` — return the value at *this level only*, no prototype
walk. `JsObject.getMember` (both arities) consults the hook after the own-slot
miss and before the proto walk, so the dispatch is single-pass.

This replaces the historical pattern where each subclass overrode the 1-arg
`getMember` and prefixed its body with
`Object own = super.getMember(name); if (own != null) return own;`.
That pattern caused a *double* prototype walk on accessor descriptors: the
1-arg returned `null` for accessors at every level (raw-value semantic), the
subclass fell through, and the 3-arg ended up walking the chain a second
time. Centralizing intrinsic resolution lets the 3-arg path single-pass
through (own slot → intrinsic hook → proto chain) and lets the 1-arg
overrides shrink or vanish in most subclasses (refactor A, post-S4).

Subclasses chain via `super.resolveOwnIntrinsic(name)` when extending the
parent's intrinsic surface — e.g. `JsUint8Array` overrides to return its
byte-buffer length, then delegates to `super` for the rest of the JsArray
intrinsic surface.

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

Error and its native subclasses follow the standard constructor + prototype
spec shape.

- **`JsErrorConstructor extends JsFunction`** — one parameterized singleton
  per error type (`Error`, `TypeError`, `RangeError`, `SyntaxError`,
  `ReferenceError`, `URIError`, `EvalError`, `AggregateError`). Each carries
  its own `JsErrorPrototype` as the `prototype` own intrinsic
  (non-writable, non-enumerable, non-configurable per spec). `length` is 1
  (Error and friends) or 2 (AggregateError, signature
  `(errors, message?, options?)`). Both `Error("x")` and `new Error("x")`
  route through the same `call` and return a fresh `JsError`.
- **`JsErrorPrototype extends Prototype`** — one singleton per error type,
  chained `TypeError.prototype → Error.prototype → Object.prototype`.
  Carries `name` (own data), `constructor` (lazy ref to the matching
  `JsErrorConstructor`), and (only on `Error.prototype`) `message: ""` and
  the spec `toString` method; subtype prototypes inherit those last two
  through the chain.
- **`JsError extends JsObject`** — slim instance class. `__proto__` is set
  by the constructor; `name` reads through the prototype chain (no own
  field). `message`, `cause` (ES2022), and `errors` (AggregateError) are
  installed as own data properties only when the corresponding argument
  was supplied — per spec, `new Error()` produces an instance with NO
  own `message`. A separate Java-only `javaCause: Throwable` field carries
  the underlying Java exception (when wrapping a Java throwable via
  `JsErrorException.wrap`) so `JsErrorException.getCause()` can chain it
  for IDE-hyperlinkable stack traces — distinct from the JS-visible
  `.cause` own property.

Spec behaviors:

```javascript
new Error('boom').message             // 'boom' — own data property
new Error().hasOwnProperty('message') // false — message lives on the prototype
new TypeError('x').name               // 'TypeError' — read from TypeError.prototype.name
new Error('x', { cause: 42 }).cause   // 42 — own when options.cause is present
new TypeError() instanceof Error      // true — proto-chain walk
Error.prototype.constructor === Error // true — lazy ref resolved on first read
'' + new Error('x')                   // 'Error: x' — Error.prototype.toString
```

The `.constructor` is no longer wired post-hoc by the catch boundary — it
flows naturally through the prototype chain. `Terms.instanceOf` no longer
special-cases the JsError class; the proto-chain walk at the bottom of the
method covers `instanceof TypeError` / `instanceof Error` / etc. uniformly.

### Error-message preservation through reflection

`JavaUtils.invoke` and `JavaUtils.invokeStatic` separate "method not found" (TypeError with `"TypeError: .foo is not a function"`) from "method threw" (unwraps `InvocationTargetException`, rethrows the underlying `RuntimeException` with its original message). Before this change, reflective invocation failures were all collapsed into a generic `TypeError: .<name> is not a function`, masking real exception messages.

---

## Engine-compliance work

The operating-mode maxims for the test262 conformance loop now live in
[`karate-js-test262/TEST262.md` § Working principles](../karate-js-test262/TEST262.md#working-principles)
— treat that section as load-bearing. The engine code map below is the
muscle-memory pointer for "where does this fix go."

### Engine code map

When test262 surfaces a fix, this table is the muscle-memory pointer.

| Concern | Engine source | JUnit test | test262 path |
|---|---|---|---|
| Lexer (tokenization) | `karate-js/.../parser/JsLexer.java`, `BaseLexer.java`, `TokenType.java`, `Token.java` | `JsLexerTest`, `LexerBenchmark` | `test/language/literals/**` (syntax-level) |
| Parser (AST build) | `karate-js/.../parser/JsParser.java`, `BaseParser.java`, `NodeType.java`, `Node.java` | `JsParserTest`, `ParserExceptionTest`, `TermsTest` | `test/language/expressions/**`, `statements/**`, `types/**` (parse-level) |
| Parse errors | `karate-js/.../parser/ParserException.java`, `SyntaxError.java` | `ParserExceptionTest` | parse-phase negative tests |
| Interpreter (eval) | `karate-js/.../js/Interpreter.java`, `CoreContext.java`, `ContextRoot.java` | `EvalTest` (language-semantics catch-all) | `test/language/expressions/**`, `statements/**`, `types/**` (runtime) |
| Built-ins / types | `karate-js/.../js/JsObject.java`, `JsArray.java`, `JsString.java`, `JsError.java`, `JsFunction.java`, prototype classes (`JsArrayPrototype` etc.), `Terms.java` (operators/coercion) | `JsArrayTest`, `JsStringTest`, `JsObjectTest`, `JsMathTest`, `JsNumberTest`, `JsJsonTest`, `JsDateTest`, `JsRegexTest`, `JsFunctionTest`, `JsBooleanTest` | `test/built-ins/Array/**`, `String/**`, `Object/**`, `Math/**`, `Number/**`, `JSON/**`, `Date/**`, `RegExp/**`, `Function/**`, `Boolean/**` |
| Runtime exceptions | `karate-js/.../js/EngineException.java` | `EngineExceptionTest` | error-propagation regressions |
| Performance regression | — | `EngineBenchmark` | (gut-check after engine change) |

Guidance:
- **Pure tokenization change** → `JsLexer` + `JsLexerTest`.
- **Grammar change** → `JsParser` + `JsParserTest` (AST shape) + `EvalTest`
  (runtime semantics).
- **Semantics-only change** → `Interpreter.java` or the relevant
  `Js*Prototype`; test in `EvalTest` or the matching `Js*Test`.
- `NodeType` and `TokenType` are small enums — consult them before inventing
  new node/token kinds; many "feels like I need a new node" fixes turn out
  to be wiring an existing one to a new call site.
- **`EngineTest` is *not* a test262 sink.** It covers the engine's
  integration surface: `ContextListener` events, `BindEvent`, `Engine.put`
  lifecycle, Java↔JS exception boundary, `$BUILTIN`/prototype immutability.
- **When to split a `Js*Test`:** don't pre-emptively. If a cluster inside
  `EvalTest` grows to ~10+ tests on one feature (destructuring, TDZ,
  template literals), spin it out — let the split follow the evidence.

---

## Spec Invariants (test262-driven)

Engine rules established by test262 conformance work. Treat as load-bearing —
if a session needs to violate one, the rule goes up for review explicitly.

### Error routing & shape

**Engine-emitted errors route through `JsErrorException` factories.** Engine
sites throw via `JsErrorException.typeError("...")` (and `rangeError` /
`syntaxError` / `referenceError` / `error`); each factory stamps the right
`JsErrorPrototype` on the payload. The catch boundaries
(`Interpreter.evalTryStmt` for JS `catch`, `Interpreter.evalStatement` /
`Engine.eval` for the host) read the `JsError` payload directly — name and
constructor flow through the prototype chain, no post-hoc wiring. The
previous `wireErrorConstructor` and embedded-name prefix-parsing rituals
are gone.

**Java-throwable wrap path.** A non-`JsErrorException` Java throwable
escaping into a JS `catch` is funnelled through
`JsErrorException.wrap(throwable)` — the payload becomes a generic `Error`
(spec `Error.prototype` chain, so `e instanceof Error` holds) and the
underlying `Throwable` is preserved as the Java cause for IDE
stack-trace hyperlinks. There is **no** Java-class → JS-name classifier:
`NullPointerException` no longer pretends to be a `TypeError`. Engine code
that wants a typed JS error must say so explicitly via the factories;
unexpected Java leaks surface as generic `Error` + an IDE-clickable cause
chain in the host log, treating principle 2 ("errors must look like JS,
not Java") as a bug-finding signal rather than papering over it.

**`Test262Error` / user-defined error classes** are classified via
`constructor.name` fallback in `Interpreter.evalProgram` when the thrown
`JsObject` has no `.name` on its prototype. Function-name inference in
`CoreContext.declare` fires only when the function's name is empty (so a
named function passed as a parameter doesn't get permanently renamed).

**Host-boundary identity.** `Interpreter.evalStatement` catches at the
script-level boundary; `JsErrorException` payloads surface `name` /
`message` (read via the prototype chain / own-property) into
`EngineException.getJsErrorName()` / `getJsMessage()` so the host gets
structured info without re-parsing prefix strings. Non-`JsErrorException`
Java throwables flow through with `jsErrorName=null` and the unwrapped
message — bugs, not pseudo-JS.

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
globals), and `JsCallable` method refs (`[1].map`, `'x'.charAt`). Plain
`JsObject` is **not** `JsCallable`; only subclasses that explicitly opt in
(`JsString` / `JsNumber` / `JsBoolean` / `JsRegex` / `JsError` /
`JsTextEncoder` / `JsTextDecoder` and `JsFunction` via `JavaCallable`) are.
This is the structural reason `JSON()` / `Math()` / `Reflect()` throw
`TypeError` — they fail the `instanceof JsCallable` check at the call site,
not via per-class `call` overrides.

### Globals

**`eval` is a global** registered in `ContextRoot.initGlobal` with indirect-
eval semantics (parses/evaluates in engine root scope; non-string args pass
through). Direct-eval scope capture is out of scope.

**Single bindings store.** `Engine.bindings` (a `BindingsStore`) holds every
binding at every scope: top-level `var` / `let` / `const`, implicit globals,
`Engine.put`-injected host state, `Engine.putRootBinding`-injected resources,
and the lazy-cached built-ins from `ContextRoot.initGlobal`. Per-entry
`hidden` flag on `BindingSlot` distinguishes the last two so
`Engine.getBindings()` (a thin auto-unwrapping `Bindings` wrapper) filters
them out of host inspection while the engine's lookup chain sees one
unified set. `Engine.getRootBindings()` exposes the hidden subset to hosts
that need to inherit it across scenarios.

**Name resolution is a single chain walk.** `CoreContext.resolve(name)` walks
own bindings → captured (closure snapshot) → `outer` (lexical parent for
function contexts; dynamic `parent` otherwise — see issue #2802) → root
(with lazy built-in init) and returns the matching `BindingSlot` or null.
`get`, `hasKey`, `update` all compose over a single `resolve` call (was: five
separate chain walks with subtly different shapes). Spec mapping:
ResolveBinding (ES 8.1.2.1).

**Top-level `this` is a `JsGlobalThis` stand-in for `globalThis`.**
`ContextRoot` constructs one and assigns it to `thisObject`; child contexts
inherit it until a function call rebinds. Refactor C (post-S4) collapsed
the prior split storage (values in `BindingsStore`, attrs in
`JsObject.props`) into a single store: `BindingSlot` carries `attrs` /
`attrsExplicit` / `tombstoned` fields directly. `JsGlobalThis` no longer
uses the inherited `JsObject.props` map at all — every observable property,
attribute, and tombstone lives on the `BindingSlot`.

So `this.foo = 1; foo` and `foo = 1; this.foo` see the same value (no
divergence — same store). Lazy built-ins land hidden via
`bindings.putHidden`, so `Object.keys(globalThis)` only sees user-visible
state. `getOwnAttrs` reports `{ writable: true, enumerable: false,
configurable: true }` per spec default for built-ins;
`defineProperty(globalThis, …)` flips `attrsExplicit` to honor the stored
byte verbatim (the global default `W|C` differs from `ATTRS_DEFAULT`'s
`W|E|C`, so explicit-equals-`ATTRS_DEFAULT` writes still need the explicit
marker). `delete globalThis.X` tombstones the slot so a lazy-realized
built-in can't re-resurrect via `initGlobal`.

**`this` binding follows spec OrdinaryCallBindThis.** Every regular call
site routes through `Interpreter.bindThisForCall(receiver, context)`,
which substitutes `globalThis` for null/undefined receivers (sloppy-mode
non-strict). `f()` (no receiver) gets `this = globalThis`, not
`this = f`. `Function.prototype.call` / `.apply` use the same helper. The
`new`-keyword paths bind `this` separately (newInstance for user fns,
constructor singleton for built-ins) and don't go through the helper.

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
check.

**`Terms` splits literal-path and runtime-path String → Number.** Spec
StringNumericLiteral §7.1.4.1.1 rejects `_` separators (those are valid only
inside source-text NumericLiterals, lexer-territory). Two methods carry the
two contracts: `literalToNumber(text)` is called from `Terms.literalValue`
for NUMBER tokens — strips `_` first since the lexer already validated
placement. `stringToNumber(text)` is the runtime String → Number coercion
called from `Terms.objectToNumber(String)` — strips spec WhiteSpace +
LineTerminator (`Character.isWhitespace` + NBSP ` ` + ZWNBSP `﻿`),
returns NaN on `_` (separators are literal-only), and accepts `0b`/`0o`/`0x`
radix prefixes via `fromRadixPrefix`. `fromRadixPrefix` catches
`NumberFormatException` (e.g. `Number("0o8")`) and returns NaN rather than
leaking a Java exception.

**`Number.prototype.*` use spec `thisNumberValue` (§21.1.3).** Unwrap
`JsNumber`, accept primitive `Number`, route `JsNumberPrototype.INSTANCE`
itself to `+0` (the prototype object is a Number exotic with internal
`[[NumberData]]` of zero per spec). Anything else throws TypeError —
`Number.prototype.toString.call(true)` no longer silently coerces to 0.
`numberToString(d)` canonicalizes special values (`NaN`, `Infinity`,
`-Infinity`) before falling back to `Number.toString`.

**Number digits args dispatch through ToPrimitive.** `toFixed` /
`toPrecision` / `toExponential` route the digits/precision argument through
`Terms.toNumberCoerce(arg, ctx)` (via `JsNumberPrototype.toIntegerArg`) so
ObjectLike inputs invoke `valueOf` / `toString`. `[2].toExponential(...)`
becomes `(123.456).toExponential(2)` per spec. NaN-on-coerce → 0 (spec
ToInteger of NaN). BigInt args throw TypeError before any coercion (spec
§21.1.3.3). `toFixed` falls back to `numberToString` for `|x| ≥ 1e21` —
`BigDecimal` of such doubles produces a noisy decimal expansion that doesn't
match the spec's `1e+21` ToString form. Range checks are `[0, 100]` per spec
(was unchecked); non-finite receivers short-circuit before the range check
(§21.1.3.4 step 6 — NaN/Infinity precede the precision-range error).
`toPrecision(undefined)` / no-arg returns `numberToString(d)` (spec §21.1.3.4
step 1). `(0).toPrecision(p)` and `(-0).toPrecision(p)` both produce a
sign-elided `"0[.0...]"` mantissa (Number::toString strips the negative-zero
sign per §6.1.6.1.13). `toExponential` with no/undefined fractionDigits emits
the minimum digits that round-trip to the receiver — Java's `%.15e` then
trim trailing fractional zeros. Both `toExponential` and `toPrecision`
canonicalize Java's `1.0e+01` exponent shape to the spec's `1.0e+1` form.

**`Number.parseInt === parseInt` and `Number.parseFloat === parseFloat`.**
Per spec the constructor static and the global function are the same object.
`ContextRoot.PARSE_INT` / `ContextRoot.PARSE_FLOAT` are static
`JsBuiltinMethod` singletons; both `initGlobal("parseInt")` and
`JsNumberConstructor.installIntrinsics` reference the same instances so
identity holds. `JsBuiltinMethod` reports `isConstructable() === false`
which clears the test262 `not-a-constructor.js` cluster.

### Property attributes

**Per-property attributes live on each `PropertySlot`.** Own properties on
`JsObject` are stored as `props: Map<String, PropertySlot>`; each slot is a
sealed `DataSlot` (carries `value` + attrs byte + tombstone) or
`AccessorSlot` (carries `getter` / `setter` callables + attrs byte +
tombstone). The attrs byte encodes bit 0 = writable, bit 1 = enumerable,
bit 2 = configurable, bit 3 = INTRINSIC. New slots default to
`ATTRS_DEFAULT` (W|E|C) — the new-property default for plain
`obj.x = ...`. `defineProperty` writes attrs explicitly and uses the spec's
"missing fields default to false on new keys, preserve on existing" rule
(different from `[[Set]]`'s all-true default — this distinction is
load-bearing). Per-object flags `frozen` / `sealed` / `nonExtensible` are
kept as fast-path early-exits on `putMember` / `removeMember` so frozen
objects don't have to consult per-slot bits per write.

**Extensibility / integrity-level API is `ObjectLike` bean-style.**
`isExtensible() / isSealed() / isFrozen()` predicates pair with mutators
`setExtensible(boolean) / setSealed(boolean) / setFrozen(boolean)`. The
mutators are *monotonic*: only the spec-allowed direction does anything
(`setExtensible(false)`, `setSealed(true)`, `setFrozen(true)`); the other
direction is a silent no-op (lenient mode — strict-mode TypeError flip
lives elsewhere). `JsObject` and `JsArray` carry the three-bit state and
override; other `ObjectLike` implementors (raw `Map` host bridges) inherit
the perpetually-extensible defaults. `JsObjectConstructor.{
preventExtensions, seal, freeze, isExtensible, isSealed, isFrozen}`
dispatch through the unified API — no per-type `instanceof` fork —
so any future `ObjectLike` (e.g. a spec-shaped `JsArguments`) participates
automatically.

**`Object.freeze(arr)` enforcement on JsArray.** Three layers cooperate
so the dense `list` backing store honors integrity bits:

1. **`JsArray.putMember`** silently drops all writes when `frozen`; for
   non-extensible / sealed it blocks creation of new own keys (out-of-
   bounds index, named key, or HOLE fill — `HOLE` positions count as
   "key absent") while letting existing-index modification proceed on
   sealed arrays.
2. **`JsArray.ArrayLength.applySet`** blocks length-extension on
   non-extensible arrays (extending populates new HOLE indices, which
   would create new own properties). Length truncation still works.
3. **`JsArray.getOwnAttrs`** derives the spec-correct attribute byte for
   dense-list indices from the `frozen` / `sealed` flags so
   `Object.getOwnPropertyDescriptor(frozenArr, 0)` reports
   `{writable: false, configurable: false}` without having to
   materialize a `namedProps` slot per index. `defineProperty`'s
   configurable check then fires correctly on indexed redefines.

The hot-path indexed-write fast path in `PropertyAccess.setByIndex`
routes through `setByName` (and thus through `JsArray.putMember`)
whenever `!array.isExtensible()` — single source of truth, single
boolean read for the common-case branch. `SpecPinTest.{lenient_writeToFrozenArrayIndexIsSilent,
lenient_extendFrozenArrayIsSilent, frozenArrayDescriptorReportsNonWritableNonConfigurable,
sealedArrayAllowsExistingIndexWriteButBlocksNewIndex,
sealedArrayDescriptorReportsNonConfigurableButWritable,
nonExtensibleArrayBlocksNewIndexButAllowsExisting,
nonExtensibleArrayBlocksLengthExtension, frozenArrayBlocksHoleFill}`
pin these.

**Polymorphic read / write seam.** `PropertySlot.read(receiver, ctx)` and
`write(receiver, value, ctx, strict)` are the dispatch point. `DataSlot.read`
returns `value` directly; `AccessorSlot.read` invokes the getter via
`Interpreter.invokeGetter(getter, receiver, ctx)`. `DataSlot.write` honors
the writable bit (silent ignore in lenient mode, TypeError in strict);
`AccessorSlot.write` invokes the setter (silent / TypeError on get-only).
`PropertyAccess.findAccessorInChain(obj, name)` walks the prototype chain
via the unified `getOwnSlot` (defined on `JsObject`, `JsArray`, `Prototype`)
and returns the first AccessorSlot. `setByName` invokes
`acc.write(receiver, value, ctx, false)` rather than `objectLike.putMember(...)`
when an accessor is in chain — preserves the descriptor and threads the
live ctx so setters that read other properties see the correct call frame.

**Read paths.** `getOwnPropertyDescriptor` reads the slot's attrs byte (or
all-true default for a missing slot); `JsObject.jsEntries(ctx)` — the
back-end for `for...in` / `Object.keys` / `Object.values` / `Object.entries`
/ `Object.assign` via `Terms.toIterable(o, ctx)` — iterates `props`
directly (so subclass overrides like `JsGlobalThis` participate via
`@Override`), filters by `isEnumerable(name)` so subclass `getOwnAttrs`
overrides win, and resolves accessor descriptors via `slot.read(this, ctx)`
when `ctx != null`. The no-arg `jsEntries()` keeps the Java-interop
semantic (accessors → null at the host boundary). `Object.getOwnPropertyNames` /
`hasOwn` go through `toMap()` directly. `propertyIsEnumerable` consults
`isEnumerable(name)`. Configurability rules enforced on defineProperty:
TypeError on flipping configurable false→true, changing enumerable,
switching data↔accessor shape, or changing a non-writable value — with the
spec-allowed exceptions (writable true→false on data, no-op same-value
redefine) passing through.

**`Object.prototype.hasOwnProperty` is prototype-aware and intrinsic-aware.**
Single dispatch through `ObjectLike.isOwnProperty` covers all storage
shapes: `Prototype.isOwnProperty` (built-in methods + userProps),
`JsObject.isOwnProperty` (props + `hasOwnIntrinsic`), `JsArray.isOwnProperty`
(length / namedProps / non-HOLE indices), `JsGlobalThis.isOwnProperty`
(bindings + lazy globals). Required for the `S15.9.5_A*` / `S15.9.4_A*`
test clusters and analogous tests under other built-ins.

**`hasOwnIntrinsic` is derived from `resolveOwnIntrinsic`.** The base
`JsObject.hasOwnIntrinsic(name)` returns `resolveOwnIntrinsic(name) != null`
— a single source of truth for the subclass-declared own-intrinsic surface.
Subclasses override `resolveOwnIntrinsic` to return the value (or `null`);
the existence check derives. Eliminates the previous drift risk where
`JsFunction` declared `constructor` in its boolean `hasOwnIntrinsic`
override but not in `resolveOwnIntrinsic` (causing
`f.hasOwnProperty('constructor') === true`, which is wrong per spec —
`constructor` lives on `Function.prototype`). The collapse also fixed
anonymous-function `name` reporting: `(function(){}).name === ""` and
`hasOwnProperty('name') === true` per spec, since
`resolveOwnIntrinsic("name")` defaults `null`-named functions to `""`.

**`ownIntrinsicNames` is the discovery seam for descriptor enumeration.**
`Object.getOwnPropertyDescriptors` needs to enumerate keys that don't
materialize in `toMap()` — built-in constructors / wrappers expose
intrinsics via `resolveOwnIntrinsic` rather than as own slots. Each
subclass that overrides `resolveOwnIntrinsic` returns its closed name
set from `ownIntrinsicNames()` (default empty); the constructor unions
those names with `toMap()` keys. Replaces a previous static
`INTRINSIC_PROBE_NAMES = {length, name, prototype, constructor}` list
that was hand-maintained on `JsObjectConstructor` and easy to drift.
Current implementors: `JsFunction` (`prototype`/`name`/`length`),
`JsString` (`length`), `JsRegex` (`source`/`flags`/`lastIndex`/`global`/
`ignoreCase`/`multiline`/`dotAll`), `JsError` (`message`/`name`/
`constructor`), `JsMap` / `JsSet` (`size`), `JsTextEncoder` (`encode`),
`JsTextDecoder` (`encoding`/`decode`), `JsReflect` (`construct`/
`apply`). Built-in constructors (`JsObjectConstructor`, etc.) install
their methods via `defineOwn` so they surface through `toMap()` directly
and inherit the `JsFunction` list for the function-shape intrinsics.

**Intrinsic-attribute pipeline.** Built-in own properties resolved via
`resolveOwnIntrinsic` (not via `props`) declare themselves as own through
the derived `hasOwnIntrinsic(name)` and report attribute bits through
`getOwnAttrs(name)`. `JsFunction` returns spec defaults for its three
intrinsics (`length` / `name`: configurable-only; `prototype`: writable);
`constructor` is inherited from `Function.prototype` and intentionally not
own. Subclasses (`JsMath`, etc.) cover their own methods / constants via
`defineOwn` with explicit attrs. The descriptor read pipeline
(`Object.getOwnPropertyDescriptor`, `propertyIsEnumerable`, `Object.keys`
/ `for...in` enumerable filter) consults this rather than the all-true
default. A user-set slot's attrs (set by `Object.defineProperty`) win over
the intrinsic defaults so user override is still possible.

**`@@iterator` lives on the prototype, not the instance.** The
`Symbol.iterator` stand-in (`IterUtils.SYMBOL_ITERATOR_METHOD`) is installed
once on `JsArrayPrototype` and `JsStringPrototype` rather than allocated
per-instance via `resolveOwnIntrinsic`. Spec-correct
(`arr.hasOwnProperty('@@iterator') === false` — it's inherited), identity
holds across instances, and `hasOwnIntrinsic` doesn't pay a per-call lambda
allocation. Future Symbol primitive work replaces the string key with the
real `Symbol.iterator` value.

**Tombstone-on-delete for intrinsic properties.** Each `PropertySlot`
carries a `tombstoned` flag; set true by `removeMember` when the deleted
name had a backing intrinsic, cleared by `putMember` on a successful
re-write. `getMember` short-circuits tombstoned slots to the prototype
chain (skipping the `resolveOwnIntrinsic` hook); `isOwnProperty` returns
false. Matters for `propertyHelper.verifyProperty`'s destructive
`isConfigurable()` check, which tries `delete obj[name]` and asserts
`!hasOwnProperty(obj, name)`. `Prototype` shares the same flag for
`delete Foo.prototype.bar` (the prior separate `Set<String> tombstones`
was migrated into `PropertySlot.tombstoned` in refactor B, post-S4).

**Tombstone-on-shadow rule for `Prototype.removeMember`.** When a user
slot exists in `userProps` AND the same name lives in `builtins` under
it, `delete` must tombstone the user slot rather than drop it — else
the underlying built-in re-emerges through `getMember` /
`isOwnProperty`. This was the silent failure mode behind ~155 test262
"should be configurable" prop-desc fails: `verifyProperty`'s pipeline
runs `isWritable` (which writes a fresh `DataSlot` into `userProps`)
before `isConfigurable` (which deletes). Pre-fix, the delete dropped
the user slot, the built-in re-emerged, and `!hasOwnProperty` returned
false. The shadowsBuiltin check is independent of `inUser` so the
tombstone fires whether the user slot pre-existed (reuse it) or not
(install a fresh tombstone).

**`JsFunction.getOwnAttrs` honors explicit slot attrs before the
function-default switch.** Built-in constructors install their
`prototype` slot via `defineOwn(..., INTRINSIC)` for spec all-false
(non-writable / non-enumerable / non-configurable). The user-function
default for `prototype` is `WRITABLE`, which would mask the explicit
INTRINSIC and report writable=true on `Object.getOwnPropertyDescriptor(
Number, "prototype")` etc. Gating the switch on `!hasExplicitAttrs(name)`
keeps the user-function default for plain `function f(){}` while letting
built-in constructors override per spec. Same precedence applies to
`length` / `name` / `constructor` overrides if a built-in ever needs to
deviate.

**`JsObject.isOwnProperty(name)` is the canonical own-key check.** Returns
true iff there's a non-tombstoned slot for `name` OR `hasOwnIntrinsic(name)`.
Replaces the previous mix of `toMap().containsKey + hasOwnIntrinsic` checks
at three call sites (`JsObjectConstructor.isOwnKey`,
`JsObjectPrototype.hasOwnProperty`, `propertyIsEnumerable`). Anything that
wants spec-level "is this an own property" goes through here.

### Prototype machinery

**Built-in prototypes accept user-added properties.** `Prototype` has a
`userProps: Map<String, PropertySlot>` map; user-added properties win over
built-ins on lookup (configurable: true / writable: true per spec). Built-in
methods themselves can't be deleted via `removeMember` — instead, the
delete tombstones the slot in place so future reads skip the install map and
fall through to the proto chain. Required for `Array.prototype.foo = ...`
polyfill patterns and for spec-conformant test262 behavior. Storage is
unified post-refactor B: data writes install `DataSlot`, accessor descriptors
install `AccessorSlot`, both surfaces through the shared `getOwnSlot`
signature that mirrors `JsObject` / `JsArray`.

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
`props` / extensibility flags; subclasses with intrinsic install routines
override, call `super.clearEngineState()` first, then re-run
`installIntrinsics()` (the install code is the single source of truth for
what gets restored). The `INTRINSIC` bit on the slot's attrs byte is
informational — it doesn't gate reset behavior.

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

**`Array.prototype.*` are generic over array-like `this`.** Built-in
methods treat `this` as an ObjectLike with a numeric `.length` and indexed
properties; `Array.prototype.shift.call(obj)` works on a plain `JsObject`
the same way it works on a `JsArray`. The split inside `JsArrayPrototype`:

- *Read-only / new-array-returning methods* (`slice` / `concat` / `flat` /
  `join` / `at` / `keys` / `values` / `entries` / `with` / `group`) build a
  `0..length-1` snapshot via `rawList` + `getMember(String.valueOf(i))`.
- *Iterating methods* (`every` / `some` / `forEach` / `map` / `filter` /
  `reduce` / `reduceRight` / `find` / `findIndex` / `findLast` /
  `findLastIndex` / `includes` / `indexOf` / `lastIndexOf` / `flat` /
  `flatMap`) dispatch through `specIterate` — length-bounded `HasProperty`
  + `Get`, proto-chain aware, with a clean-JsArray fast path that reads
  the dense `list` directly.
- *Mutating methods* (`push` / `pop` / `shift` / `unshift` / `sort` /
  `splice` / `reverse` / `fill` / `copyWithin`) dispatch per-index through
  one set of spec primitives on the `ObjectLike` receiver, so writes
  propagate back to a non-array `this` (test262 `S15.4.4.{8,9,11,12,13}_A2_*`
  clusters).
- *ES2023 immutables* (`toReversed` / `toSorted` / `toSpliced` — `with` was
  already there) read source via `specGet` (NOT `HasProperty` + `Get`) so
  holes surface as `undefined` / proto-chain values, build a fresh `JsArray`
  result, and never mutate the receiver. Standalone implementations rather
  than wrappers around `*InPlace` helpers extracted from `sort` / `splice` /
  `reverse` — the per-index spec primitives are cheap and the duplication
  is small enough that a mutate-then-clone round-trip would lose more than
  it saved.

The shared spec-primitive contract for the mutating path:

| Primitive | Spec name | Maps to |
|---|---|---|
| `specGet(O, k)` | `Get(O, k)` | `O.getMember(k, O, ctx)` (proto-walking, accessor-aware) |
| `specSet(O, k, v)` | `Set(O, k, v, true)` | `PropertyAccess.setByName` (proto-walks setters; routes JsArray length through `handleLengthAssign`) |
| `specDelete(O, k)` | `DeletePropertyOrThrow` | `O.removeMember(k)` (lenient on configurable today; strict-mode flip deferred) |
| `hasPropertyChain(O, k)` | `HasProperty(O, k)` | own + `__proto__` walk |
| `lengthOf(O)` | `ToLength(? Get(O, "length"))` | `arr.size()` for `JsArray`; otherwise `Terms.toNumberCoerce` (so `length: {valueOf(){…}}` resolves and `valueOf` abrupt-completion propagates via `ctx.isError()`) |
| `setLength(O, n)` | `Set(O, "length", n, true)` | `JsArray.handleLengthAssign` for arrays (TypeError on writable=false), `setByName` otherwise |

Length is clamped to `Integer.MAX_VALUE` — the spec's full Uint53 range
needs a long-typed length field, deferred.

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
listIterator path). `Array.prototype.{join, toString}` are NOT
hole-skipping per spec: they walk `0..length-1` and emit `""` for
holes (and for `undefined` / `null` elements). They use `rawList`
+ a length-walk filter rather than `jsEntries` to honor that contract;
pinned by `SpecPinTest.{joinEmitsEmptyForHoles, toStringEmitsEmptyForHoles}`.

**Past-end indexed write pads with `HOLE`.** `arr[5] = 'x'` on an empty
array extends `arr.list` with `HOLE` (not `Terms.UNDEFINED`) at
positions 0..4 so `arr.hasOwnProperty(0) === false` (spec). The pad
sentinel is gated on `instanceof JsArray` in
`PropertyAccess.setByIndex` — raw `List` host bridges keep
`UNDEFINED` since they don't model holes. `JsArray.putMember`,
`JsArray.create(n)`, and `JsArray.ArrayLength.applySet` use the same
sentinel for symmetry.

**`JsArray.resolveOwnIntrinsic` returns `null` for hole positions.**
Spec semantic: a hole at index `i` means the own property at `"i"` is
absent, so `[[Get]]` walks the proto chain. With `null` (not the
`HOLE` sentinel) returned from `resolveOwnIntrinsic`, `getMember`
falls through to `__proto__.getMember(name, ...)` and a getter
installed on `Array.prototype["i"]` fires — required by the spec-shape
`Array.prototype.{pop, shift}` machinery and the
`set-length-array-length-is-non-writable.js` cluster's call-count
assertions. The plain `arr[i]` user-facing read goes through
`getIndexedValue` which mirrors the same chain walk: out-of-bounds and
HOLE both fall through to `__proto__.getMember(idx, ...)` so an
inherited indexed property surfaces (test262 `S15.4.4.9_A4_T1` /
`S15.4.4.13_A4_T2` read inherited indices via plain `arr[i]` after a
mutating call). Hot path stays branch-light: in-bounds non-HOLE returns
the dense value with two range checks and one HOLE compare.

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
   (`pop`/`shift`/`unshift`/`push`) call `setLengthOrThrow` which
   wraps `handleLengthAssign` and throws `TypeError` on `false` —
   matches the spec's `Set(O, "length", newLen, true)` Throw=true
   semantics. Direct `arr.length = X` silently no-ops on writable=false
   in lenient mode (strict-mode TypeError flip is a separate project).
3. **Partial truncate when an index in `[newLen, oldLen)` is
   non-configurable.** Walks the truncate range high-to-low; on a blocking
   index, truncates above it, returns `false`. `Object.defineProperty`
   surfaces the `false` as `TypeError("Cannot redefine property: length")`.
   `namedProps` entries for cleared indices are removed.

`length`'s descriptor starts `{writable: true, enumerable: false,
configurable: false}`. Length's writable bit is stored in a dedicated
`lengthWritable` boolean rather than a Slot (length's value lives in
`list.size()`, so a Slot would either need an attrs-only marker or
shadow the dense length with a null value). `defineProperty` can flip
that bit; the other two are spec-fixed.

**Spec-shape `Array.prototype.{pop, shift, push, unshift}`.** Each
follows the spec's Get → (Delete) → Set length sequence so prototype
getter/setter side-effects observable via call-count assertions match:

- `pop` reads the last element via `arr.getMember(idx, arr, ctx)`
  before calling `setLengthOrThrow(arr, len-1)`. Because
  `JsArray.resolveOwnIntrinsic` returns `null` (not the `HOLE`
  sentinel) for hole positions, `getMember` falls through to the
  proto chain and a getter installed on
  `Array.prototype[ToString(len-1)]` fires exactly once — pinned by
  `set-length-array-length-is-non-writable.js`.
- `shift` reads index 0 the same way, then runs the spec move loop
  for k = 1..len-1: HasProperty walks the proto chain (own non-HOLE +
  proto's `isOwnProperty`); if true, `Get` + `setByName` so a proto
  getter at `fromKey` and a proto setter at `toKey` both fire; if
  false, `removeMember(toKey)` tombstones the dense slot. Final
  `setLengthOrThrow(arr, len-1)` truncates (the spec's terminal
  `DeletePropertyOrThrow(O, ToString(len-1))` is implicit in the
  truncate, same simplification as `pop`).
- `push` calls `PropertyAccess.setByName(arr, ToString(len+i), item, ctx, null)`
  per item so a setter installed on `Array.prototype[ToString(len)]`
  fires; the proto setter accepting the value means no own property
  is created (matches the test's `!arr.hasOwnProperty(0)` assertion).
  Final `setLengthOrThrow(arr, len + items.length)`.
- `unshift` runs the same spec move loop in reverse (k = len-1..0,
  toKey = k + argCount), then per-arg `setByName` for the leading
  inserts, then final `setLengthOrThrow`.

The shared `hasPropertyChain(ObjectLike, name)` helper in
`JsArrayPrototype` walks `getPrototype()` so an inherited
`Array.prototype[i] = …` (or an accessor higher up the chain) drives
the move loop's "Set inherited value at toKey" branch. Pinned by
test262 `S15.4.4.9_A4_T*` (shift) and `S15.4.4.13_A4_T*` (unshift) —
the JsArray paths now PASS; the generic ObjectLike receiver path
(`obj.shift = Array.prototype.shift; obj.shift()`) still fails on
writeback semantics tracked in TEST262.md.

**Spec-correct length-bounded iteration helper
(`JsArrayPrototype.specIterate`).** `every` / `forEach` / `map` /
`filter` / `some` / `reduce` / `reduceRight` / `find` / `findIndex` /
`findLast` / `findLastIndex` / `includes` / `indexOf` / `lastIndexOf` /
`flatMap` all route through `specIterate(ctx, ascending, skipAbsent,
visitor)`. The helper walks `0..len-1` (or in reverse) once at start,
then per-index does HasProperty + Get with the visitor short-circuiting
on `false` return. Two iteration shapes per spec:

- **HasProperty-skipping** (`skipAbsent=true`): every / forEach / map /
  filter / some / reduce / reduceRight / indexOf / lastIndexOf / flatMap.
  Skips holes — `[1,2,,4].forEach(cb)` calls `cb` 3 times.
- **No-skip** (`skipAbsent=false`): find / findIndex / findLast /
  findLastIndex / includes. Treats holes as `undefined` via Get's
  proto walk — `[1,2,,4].includes(undefined) === true`.

Hot path: when the receiver is a plain `JsArray` (exact class — buffer-
backed `JsUint8Array` routes through the slow path so its
`hasOwnIndexedSlot` override fires), no descriptors are installed,
`__proto__ === JsArrayPrototype.INSTANCE`, and no canonical-numeric key
was ever installed on a prototype's userProps in this Engine
(`Prototype.isNumericPropPolluted == false`), HasProperty reduces to an
in-bounds non-HOLE check on the dense list — no per-element
`String.valueOf` or chain walk. The `numericPropPolluted` bit flips on
the first `Array.prototype[i] = …` / `Object.prototype[i] = …` write in
the session and resets per-Engine in `Prototype.clearAllUserProps`. Slow
path (proto pollution, custom proto, descriptors, generic ObjectLike
receiver) walks `hasPropertyChain` and `getMember` per index.

`len` is captured once at the start of the helper, then `list.size()` is
re-checked per step — callbacks can shrink (`arr.length = N`) or extend
(`arr.push(…)`) the array mid-iteration, and the per-step OOR check
treats moved-out indices as absent (HasProperty false) per spec.

`JsArray.hasOwnIndexedSlot(int)` is the unified
"is this index an own data slot" check used by `isOwnProperty`,
`getIndexedValue`, and the spec-iteration slow path. Plain `JsArray`:
in-bounds and non-HOLE. Buffer-backed `JsUint8Array` overrides for
`buffer.length` bounds — every in-buffer index is present (no hole
concept on byte storage). `JsString` exposes indexed character access
via `resolveOwnIntrinsic` so
`Array.prototype.forEach.call(new String("abc"), cb)` iterates the chars
per the spec exotic-string-object semantics (test262 `15.4.4.18-1-8.js`
cluster).

`JsArray.defineOwnAccessor` extends `length` to `idx + 1` when the key
is an array index >= length, mirroring the data-slot path in
`defineOwn` — required so
`Object.defineProperty(arr, "20", {get: …})` extends a length-3 array
to length 21 and the accessor's side effects fire during iteration
(test262 `lastIndexOf/15.4.4.15-8-a-14.js`).

**`JsArray` indexed-accessor enforcement.** Descriptors installed via
`Object.defineProperty(arr, i, {get/set/value: ...})` land in `namedProps`
under the canonical string-form key and take precedence over the dense
list. Reads dispatch via `JsArray.getIndexedValue(i)` (hot path: single
null-check on `namedProps`); writes route through the named-key path when
`hasIndexedDescriptor(i)` so `JsAccessor` setters fire.
`JsArrayPrototype.rawList` / `jsEntries` take the per-index snapshot path
when `arr.hasAnyDescriptor()` so callbacks see resolved values, not the
accessor wrapper.

**`JsArray.namedProps` is a `Map<String, Slot>`.** Mirrors `JsObject.props`
in shape — each Slot carries `value` and `attrs` byte. Two storage layers
cooperate: `list` holds default-attr data values at numeric indices,
`namedProps` holds the rare-path overrides — accessor descriptors,
non-default attrs at numeric indices (`Object.defineProperty(arr, "0",
{writable: false, ...})`), and named (non-index) keys. Plain `arr[i] = x`
doesn't allocate a Slot. After `defineProperty(arr, "0", {writable: false,
value: x})`, `namedProps["0"]` carries both the value and the W-cleared
attrs; `putMember` checks the Slot's writable bit and silently no-ops on
subsequent `arr[0] = y`. `hasIndexedDescriptor(i)` is the routing hint
that pushes indexed writes through `setByName` so the check fires.
`Object.defineProperty` dispatches to `JsArray.defineOwn(name, value,
attrs)` via `applyDefine`; data descriptors at numeric indices write to
the dense list and additionally record a Slot in `namedProps` only when
attrs deviate from the all-true default.

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
`Boolean` / `Number` / `String` / `Function`, `Null` / `Undefined` for the
unguarded receivers, and `Object` as the fallback. `JsObject` implements
`JsCallable` (host-side artifact) so the `Function` branch must exclude plain
`JsObject` instances — only `JsFunction` (and `JsObject` whose
`isJsFunction()` returns true) qualifies. Substitute for the spec's
`@@toStringTag` until Symbol expansion.

### Spec preamble at built-in entry points

**Every `String.prototype.*` and most `Object.prototype.*` methods open
with a fixed two-step preamble** — spec `RequireObjectCoercible(this)`
(§7.2.1) followed by `ToString(this)` / `ToObject(this)` as appropriate.
The shared helpers live on `Terms`: `requireObjectCoercible(value, name)`
throws `TypeError` on null / undefined with the method name woven into the
message; `toStringCoerce(value, ctx)` runs the full ToPrimitive →
ToString pipeline so a host with a JS `toString` returns the user's value
instead of `"[object Object]"`. `JsStringPrototype.thisString(ctx, name)`
+ `argString(args, idx, ctx)` + `argInt(args, idx, default)` thread the
preamble uniformly across all 30 String methods — no ad-hoc casts, no
silent `ClassCastException` on a Boolean / Object / String argument.

**Built-in functions receive raw `thisArg` from `Function.prototype.call`
/ `apply`.** Spec `OrdinaryCallBindThis` (§9.2.1.2) substitutes
null / undefined → globalThis only for sloppy-mode user-defined
functions. `JsBuiltinMethod` instances skip the substitution so e.g.
`Object.prototype.toString.call(null) === "[object Null]"` and the
`RequireObjectCoercible` gate on `Object.prototype.{valueOf,
hasOwnProperty, propertyIsEnumerable, toLocaleString}` actually fires
on null / undefined receivers. The branch lives in
`JsFunctionPrototype.bindForCall`; user `JsFunction` instances still go
through `Interpreter.bindThisForCall` (lenient sloppy substitution).

### Built-in accessor descriptors on prototypes

**Spec accessor getters live in `Prototype.builtins` so they survive
per-Engine reset.** ECMA-262 declares
`RegExp.prototype.{source, flags, global, ignoreCase, multiline, dotAll,
sticky, unicode}` as accessor descriptors on the prototype, NOT as own
properties of instances — `Object.getOwnPropertyDescriptor(RegExp.prototype,
'source').get` must be a function with `.length === 0` and the proto-self
sentinel branch (`get.call(RegExp.prototype) === "(?:)"`). User-installed
accessors via `Object.defineProperty` go through `defineOwnAccessor`
into `userProps` and shadow these (consistent with the data-slot
shadowing rule); built-in accessors go through `installAccessor` into
`builtins`, which is NOT cleared by `Prototype.clearAllUserProps`. The
read seam `Prototype.getMember(name, receiver, ctx)` walks userProps →
builtins (with `AccessorSlot.read` dispatch) → proto chain;
`getOwnSlot` and `getOwnAttrs` mirror the precedence so descriptor
inspection (`hasOwnProperty`, `getOwnPropertyDescriptor`,
`propertyIsEnumerable`) sees the same view.

Each getter follows the spec receiver triage: `this === RegExp.prototype`
→ sentinel (`"(?:)"` for `source`, `""` for `flags`, `undefined` for the
flag bits); `this instanceof JsRegex` → field; otherwise TypeError. The
shared helper is `JsRegexPrototype.installFlagAccessor(name,
protoSentinel, extractor)`.

### Annex B legacy accessor methods on `Object.prototype`

**`__defineGetter__` / `__defineSetter__` / `__lookupGetter__` /
`__lookupSetter__`** are web-compat-mandated even though formally
Annex B. Live on `JsObjectPrototype` as thin wrappers over the existing
descriptor plumbing.

The two `define*` methods build a fixed-shape descriptor
(`{get|set: fn, enumerable: true, configurable: true}`) and dispatch
through `JsObjectConstructor.defineProperty` — reusing the spec
ToPropertyDescriptor + ValidateAndApplyPropertyDescriptor pipeline
(notably the merge rule: defining only `get` preserves the existing
setter). Spec ordering is load-bearing for `getter-non-callable` and
`this-non-obj` tests: ToObject(this) gates first, then IsCallable on the
function arg, then ToPropertyKey on the name — so the test's
toString-side-effect counter stays at zero on a rejected receiver or
non-callable function.

The two `lookup*` methods walk the prototype chain via
`PropertyAccess.ownSlot` at each level, returning the accessor's
getter/setter (or `undefined` when the slot lacks that half) and
terminating with `undefined` on the first own data slot — matches spec
`OrdinaryGetOwnProperty` semantics, same shape as
`PropertyAccess.findAccessorInChain`.

### `JsRegex.replace` — JS substitution template

**Java's `Matcher.appendReplacement` interprets `$<n>` differently from
JS and throws `IllegalArgumentException` ("Illegal group reference") on
unrecognized patterns.** `JsRegex.replace` does its own walk per spec
§22.1.3.18 GetSubstitution: `$$` → `$`, `$&` → match, `` $` `` →
prefix, `$'` → suffix, `$<name>` → named group, `$1`–`$99` →
positional groups (two-digit form only when the resulting index is in
range; falls back to single-digit otherwise). Unrecognized `$X` lands as
the literal two characters — the test262 conformance contract differs
from Java's regex error mode. Callback replacements (when `args[1]` is a
`JsCallable`) live in `JsStringPrototype.regexReplace` so `JsRegex`
doesn't need to depend on `JsCallable` / `Context`; the callback
receives `(match, ...captures, offset, string)` per spec.

### `JsRegex.lastIndex` is a writable field

**Spec §22.2.7.1 makes `lastIndex` a writable own data property of the
instance.** Reads route through `resolveOwnIntrinsic` to the `int`
field; writes route through an overridden `JsRegex.putMember` that
coerces via `Terms.objectToNumber` and updates the field. Without the
override, `re.lastIndex = 12` would land in the side `props` map and
the `int` field that `exec` consults would stay stale — global-flag
exec ignores user-set positions.

### `String.prototype.{trim, trimStart, trimEnd}` whitespace

**JS WhiteSpace per §11.2 includes a wider set than Java's `\s`.**
`JsStringPrototype.isJsWhitespace` covers explicit code points (TAB, VT,
FF, SP, NBSP, ZWNBSP, LF, CR, LS, PS) plus the Unicode
`Space_Separator` block (U+1680, U+2000–U+200A, U+202F, U+205F,
U+3000). U+180E is **not** included — reclassified out of `Zs` in
Unicode 6.3 (test262 `u180e.js` pins this). The trimmer walks
codepoints rather than `String.trim()` / `replaceAll("\\s+$", "")`
which would miss the wider set.

### `Object("primitive")` boxing

**`Object(value)` boxes primitives per spec ToObject (§7.1.18) — for
String / Boolean / Number it returns the matching wrapper instance
(`JsString` / `JsBoolean` / `JsNumber`).** Without this,
`new Object("abc")` would return an empty `JsObject` with no
`toString` short-circuit, and downstream `ToString` dispatch (e.g.
`/regex/.exec(new Object("abc"))`) would land on the `[object Object]`
fallback instead of the boxed string's "abc".

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

> **Aspirational TODO.** Adding strict-mode plumbing — parse the directive
> prologue, thread `strictMode` via `CoreContext`, flip ~7 lenient sites
> through one `failSilentOrThrow` helper — is tracked in
> [TEST262.md § Engine — feature gaps](../karate-js-test262/TEST262.md#engine--feature-gaps).
> The infrastructure is partly in place: `AccessorSlot.write` already accepts
> a `strict` flag from S2 wiring. The risk is parser regressions on
> `flags: [noStrict]` test262 paths if the directive parsing is over-eager.

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
| PropertySlot (sealed) | `karate-js/src/main/java/io/karatelabs/js/PropertySlot.java` |
| DataSlot | `karate-js/src/main/java/io/karatelabs/js/DataSlot.java` |
| AccessorSlot | `karate-js/src/main/java/io/karatelabs/js/AccessorSlot.java` |
| BindingSlot | `karate-js/src/main/java/io/karatelabs/js/BindingSlot.java` |
| BindingsStore | `karate-js/src/main/java/io/karatelabs/js/BindingsStore.java` |
| PropertyAccess (read/write dispatch) | `karate-js/src/main/java/io/karatelabs/js/PropertyAccess.java` |
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

### Results — 2026-04-26 (profile mode, 30 s averages)

| Commit | Array 20 KB eval | Object 20 KB eval | Iterations/30 s |
|---|---|---|---|
| `28d020b87` — benchmark introduced (2026-01-22) | 2.06 ms | 0.84 ms | 10,294 |
| `60b6fde76` — pre-Slot HEAD (2026-04-22) | 1.32 ms | 0.50 ms | 16,397 |
| post-Slot unification (2026-04-26) | 1.36 ms | 0.53 ms | 15,824 |
| post-S4 + refactors A/B/C/E (2026-04-26) | **1.36 ms** | **0.53 ms** | **15,824** |
| Speedup vs. 2026-01-22 baseline | **1.51×** | **1.58×** | **1.54×** |

Engine instantiation is essentially unchanged (~0.4–0.6 µs median in both).
Cumulative gains come from earlier perf work: tighter `Node` allocation and
pre-sized child arrays, static `PropertyAccess`, level-keyed bindings
replacing per-scope contexts, EnumSet token lookups in the parser, and lazy
ArrayList init on token-only nodes. Post-S4 refactors (subclass intrinsic
hook, Prototype storage uniformity, JsGlobalThis split-storage cleanup,
ctx-aware accessor iteration) net out within ±5% — the new virtual
`resolveOwnIntrinsic` call is offset by removing the 3-arg double-walk on
accessor descriptors and by collapsing the prior split storage in
`JsGlobalThis`.

> **Note on absolute numbers.** The reference machine values (1.32 / 0.50 ms)
> are the M1 Pro baseline. Other hardware will see different absolute
> numbers — the 2x ratios you may see locally are normal. What matters for
> session-to-session comparison is the **relative delta** on the same
> machine across pre/post commits. Re-baseline locally when starting a
> session before judging a refactor's perf impact.

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
- `Bindings` class using `Map<String, BindingSlot>` for scope storage with auto-unwrapping at Java boundaries
- Sealed `PropertySlot` family (`DataSlot` / `AccessorSlot`) for property descriptors and a separate `BindingSlot` root for variable bindings — see [§ Slot family](#slot-family--property-descriptors-and-bindings) and [§ Property attributes](#property-attributes)
- `JsFunctionWrapper` for auto-converting function return values
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
> and [§ Deferred TODOs](../karate-js-test262/TEST262.md#deferred-todos). One
> architectural-shape item lives here because it's not yet covered there:

**JavaScript Stack Traces for Errors**
- Single-frame position is done: `Node.toStringError` now appends
  `    at <path>:<line>:<col>` (JS-stack-frame-style) after the user
  message. Enough that LLMs reading `.message` get a source locator.
- Multi-frame call stack still TODO — would track function entry/exit
  in `Interpreter.evalFnCall`, stash name + source on `CoreContext`,
  and capture the chain on throw. Priority: medium.

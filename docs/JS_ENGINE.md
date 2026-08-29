# JavaScript Engine Reference

This document describes the JavaScript engine architecture, type system, and Java interop patterns for karate-js.

> See also: [DESIGN.md](./DESIGN.md) | [karate-js README](../karate-js/README.md) | [karate-js-test262 TEST262.md](../karate-js-test262/TEST262.md)

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

> **Java → JS, the `byte[]` carve-out.** A Java array crossing *into* JS becomes a `JsArray` (→ `List` when unwrapped) — **except `byte[]`, which is wrapped as `JsUint8Array`** (binary). So a Java method returning `byte[]` round-trips back to `byte[]` at the external-call boundary instead of degrading to a `List<Byte>`. This carve-out holds on every inbound path: literal/value conversion (`Terms.toJsValue` / `toJsArray`) **and** reflective Java method returns + field reads (`JavaUtils.convertIfArray`). Locked by `ExternalBridgeTest` (`Base64.decode(...)` → `byte[]`; `DemoPojo.bytes()` → `byte[]` with `Uint8Array` `.length`/indexing in JS).

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

The `INTRINSIC` bit on `attrs` marks install-time intrinsics (vs user-set
entries) — informational for strict-mode checks and introspection; nothing
resets on it. The `WRITABLE` bit is meaningless for
accessors and not consulted by `AccessorSlot`; the spec's "omit `writable`
from descriptor output for accessors" is handled in
`JsObjectConstructor.buildDescriptor` by branching on the slot family.

### Prototype System Architecture

The engine uses singleton prototype objects for method inheritance, matching JavaScript's prototype chain:

```
Singleton Prototypes (shared JVM-wide and immutable; user props are
per-Engine overlays resolved via Engine.current()):
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
    PER-ENGINE instances (not JVM singletons), created lazily by
    ContextRoot.builtinConstructor(name) and cached in the engine's
    bindings — like JsMath always was:
    JsObjectConstructor   → prototype: JsObjectPrototype.INSTANCE
    JsArrayConstructor    → prototype: JsArrayPrototype.INSTANCE
    JsStringConstructor   → prototype: JsStringPrototype.INSTANCE
    JsNumberConstructor   → prototype: JsNumberPrototype.INSTANCE
    JsBooleanConstructor  → prototype: JsBooleanPrototype.INSTANCE
    JsBigIntConstructor   → prototype: JsBigIntPrototype.INSTANCE
    JsDateConstructor     → prototype: JsDatePrototype.INSTANCE
    JsFunctionConstructor → prototype: JsFunctionPrototype.INSTANCE
    JsMapConstructor      → prototype: JsMapPrototype.INSTANCE
    JsSetConstructor      → prototype: JsSetPrototype.INSTANCE
    JsRegexConstructor    → prototype: JsRegexPrototype.INSTANCE
    JsErrorConstructor ×8 (Error, TypeError, RangeError, SyntaxError,
        ReferenceError, URIError, EvalError, AggregateError)
                          → prototype: matching JsErrorPrototype.*
```

**Built-in prototypes accept user-added properties** per spec — `Array.prototype`
methods are configurable + writable, so `Array.prototype.foo = ...` works and
overrides on lookup. User props live in a per-Engine overlay
(`Engine.protoUserProps`, resolved through `Prototype.userProps()`); the
built-in methods themselves are immutable (cannot be removed via
`removeMember` unless tombstoned-on-delete per the
[Spec Invariants § Property attributes](#property-attributes) rules).

**Per-Engine isolation.** The prototypes are JVM-wide singletons, but all
their mutable state is per-Engine: user props resolve through the thread's
current engine (`Engine.current()`, an eval-scoped ThreadLocal) and die with
it, so `Map.prototype.set = function() { throw ... }` in one Engine can
neither poison another Engine nor be wiped by one — concurrent engines are
fully isolated with no reset step. The constructors are per-Engine instances
outright, so their mutable state needs no special handling. See
[Spec Invariants § Prototype machinery](#prototype-machinery) for the
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
    // Install-time built-in members; immutable post-construction (lazy
    // entries cache inside their LazyRef holder, never written back), so
    // the map is safe under concurrent engines. User mutations land in the
    // per-Engine overlay and shadow these.
    private final Map<String, Object> builtins = new LinkedHashMap<>();

    // The current Engine's user-prop overlay for this prototype (null when
    // none). Each entry is a PropertySlot — DataSlot for user-added values,
    // AccessorSlot for accessor descriptors installed via
    // Object.defineProperty(Foo.prototype, "x", {get: ...}). The slot's
    // tombstoned flag shadows a built-in deleted via
    // delete Foo.prototype.bar. Resolved via Engine.current() (eval-scoped
    // ThreadLocal); a JVM-wide monotonic anyUserProps flag short-circuits
    // the lookup until any engine actually polyfills a prototype.
    private Map<String, PropertySlot> userProps() { ... }
    private Map<String, PropertySlot> userPropsForWrite() { ... }

    public final Object getMember(String name) {
        // 1. Per-Engine user slot wins (data, accessor, or tombstone)
        Map<String, PropertySlot> userProps = userProps();
        PropertySlot s = userProps == null ? null : userProps.get(name);
        if (s != null) {
            if (s.tombstoned) return walkProto(name);
            return s instanceof DataSlot ds ? ds.value : null; // accessor → null at this seam
        }
        // 2. Built-in lookup (LazyRef self-caches; ConstructorRef resolves
        //    per access against the reading Engine's constructor instance)
        Object builtin = resolveBuiltin(name, null);
        if (builtin != null) return builtin;
        // 3. Delegate to __proto__ chain
        return walkProto(name);
    }

    // 3-arg overload invokes accessor getters via slot.read(receiver, ctx)
    public Object getMember(String name, Object receiver, CoreContext ctx) { ... }

    public void putMember(String name, Object value) {
        Map<String, PropertySlot> userProps = userPropsForWrite();
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
- Per-Engine state isolation prevents cross-session (and cross-thread) pollution
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

**The boundary catches `RuntimeException`, not `Throwable`** — so an
`Error`-class throwable escapes JS entirely: it is not convertible to a
`JsError`, no `catch` block sees it, and it reaches the host raw. The known
raw-leak shapes today (all bugs by the tenet above, none of them papered
over):

- `RegExp` `exec` / `test` with a null argument → `NullPointerException`;
  catastrophic backtracking → the engine `Timeout`.
- `Array` operations near a 2^32 length → `IndexOutOfBoundsException` /
  VM-limit / heap errors (the `Integer.MAX_VALUE` length clamp noted under
  §JsArray length semantics).

(Fixed and no longer on this list: `JSON.stringify` circular / BigInt
pre-walk — one `JsJson.checkSerializable` identity-set walk throwing
`TypeError`; the replacer-array `ClassCastException` — non-String/Number
entries are ignored per §25.5.2; `replaceAll` / `endsWith` range clamps.)

(known deviations — see TEST262.md Active priorities.)

The call-site path (`Interpreter.evalFnCall`) is intentionally left as plain Java throw/propagate. This preserves the existing behavior for **uncaught** exceptions: they continue to bubble up through the expression chain, pick up the helpful `expression: <code> - <message>` framing at `PropertyAccess.getRefDotExpr`, and finally become the usual `js failed:` wrapper at the statement boundary. Only entering a `try` block changes the outcome.

### Host invocations surface uncaught throws

When a Java host invokes a JS function **directly** — `someFunction.call(hostCtx, args)` on a `JavaCallable`, with no JS caller context (a `null` or otherwise non-`CoreContext` caller) — an uncaught JS `throw` inside the body surfaces to the host as a Java `EngineException`, exactly as `engine.eval` surfaces one at the statement boundary. The structured `jsErrorName` / `jsMessage` are preserved, so `throw new TypeError('x')` and `throw 'x'` both reach the host with a JS-native shape.

**Implementation.** `JsFunctionNode.call` detects the host-invocation case (the caller is not a `CoreContext`) and, after running the body, converts a function context left in JS-error state into an `EngineException` via the shared `Interpreter.errorAsException(context, node)` helper (the same conversion `evalProgram` applies at the top-level statement boundary). The error is then cleared from the declaring context (`CoreContext.reset()`) so a throwing host call does not leave the shared engine context dirty for the next call. The **JS-to-JS** path is unchanged: when the caller *is* a `CoreContext`, the error still propagates via `parentContext.updateFrom(...)` so a surrounding JS `try/catch` intercepts it. Pinned in `HostCallThrowTest`.

Before this boundary existed, a direct `call(...)` silently swallowed the throw and returned normally — a latent bug for any host that drives a user-supplied JS function (e.g. a rule oracle that rejects an impossible input with `throw`).

### Abrupt completions in control-flow tests

**Errors propagate via `context.stopAndThrow`, not via Java
exceptions** — every control-flow eval site that consumes a
sub-expression result must check `context.isStopped()` before acting
on it. `Interpreter.evalIfStmt` does so post-condition: if the test
expression sets the stop signal, both branches are skipped so the
surrounding `evalBlock` (which already checks `isStopped`) propagates
up to the nearest `try` boundary. Without this guard the throw
returns `undefined`, the truthy check reads it as falsy, and the
else-branch runs silently before the throw is observed — eating the
exception in inner contexts. Pinned in
`SpecPinTest.ifConditionThrowPropagatesToCatch` /
`…ThrowInOrChain_propagates`. **Same gap is open at `evalWhileStmt` /
`evalDoWhileStmt` / `evalForStmt` / `evalSwitchStmt` / `evalTernary` /
`evalLogicalExpr` — see TEST262.md "Abrupt-completion gap" TODO.**

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

**Cooperative interrupt.** `EngineInterruptedException` (subclass of
`EngineException`) is thrown when `Thread.currentThread().isInterrupted()`
is observed at a loop back-edge inside `Interpreter` (while / do-while /
for / for-in / for-of). Lets hosts terminate a long-running script via
`Thread.interrupt()` or `Future.cancel(true)` without leaking the worker
thread. The interrupt flag is left set (we poll via `isInterrupted()`,
which doesn't clear it), so callers higher in the stack still observe the
cancellation. JS `try/catch` cannot swallow it — `evalTryStmt`
special-cases it and re-throws. `Engine.eval` likewise re-throws it
unwrapped so the host can distinguish a host-initiated cancel from a
JS-origin error. **Intentionally not a `FlowControlSignal`** — karate-core
callers (`Markup`, `ServerRequestCycle`) treat that marker as "intentional
redirect/switch, response state already set, return normally," which would
mask an interrupted handler as a successful run.

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

## Async / await / Promise

`async` / `await` / `Promise` run as **virtual-thread activations under a
per-Engine lock** — the spec's synchronous-start semantics without CPS-rewriting
the recursive tree-walk (a mutable per-frame completion record can't migrate
between stacks, so a suspended body needs its own thread). `AsyncSupport` is the
runtime, `AsyncScope` the linearizable facade, `AsyncActivation` one invocation.

### Iron rules

1. **JS executes only under `Engine.jsLock`** — a *fair* `ReentrantLock`, so a
   resuming activation can't be starved by an eval thread that keeps re-locking.
   Exactly one thread runs JS at any moment; there is no other synchronization
   in the interpreter, and every locked span establishes and restores
   `Engine.current()` around itself (`Engine.enter` / `exit`).
2. **Foreign threads (timers, interop executors) never run JS and never touch
   `JsObject` state.** They may only complete a `CompletableFuture`, enqueue a
   job, and unpark a thread — see `AsyncSupport.settleFromCallback`, which routes
   an off-engine `resolve(...)` back through the queue.
3. **Quiescence accounting is linearizable.** Every unit of outstanding work
   holds exactly one `AsyncToken` (live timer, live activation, queued job,
   armed-but-unsettled `CompletionStage` subscription), and successor work is
   always enqueued **before** the token that permitted it is released —
   `AsyncScope.publishSuccessor(oldToken, work)` does both under one monitor. So
   a foreign thread can never publish into a closed scope, the count can never go
   negative, and quiescence (`liveTokens == 0` **and** both queues empty, read as
   one operation) can never be observed with work in flight. Tokens are idempotent
   handles with their own `LIVE → RELEASED` CAS, never bulk decrements.

`AsyncScope`'s monitor is **not** `jsLock`: holding it never confers the right to
run JS and it is never held across JS execution. It doubles as the pump owner's
wait set, so any enqueue wakes an owner blocked on an empty queue.

### Activations

`JsFunctionNode.bindArgsAndExecute` forks on the `async` flag: an async
invocation never runs its body on the calling thread — `AsyncSupport.callAsync`
→ `AsyncActivation.spawn` returns a promise, always. The caller (holding
`jsLock`) creates the result `JsPromise`, spawns a `js-async-N` virtual thread,
**releases `jsLock`**, and parks on a **startup-outcome cell**: a
single-assignment `SUSPENDED | COMPLETED | FAILED` slot (one CAS — a second
publication is a harmless failed CAS), not a bare latch, so the caller can tell
"ran to completion" from "parked at its first unresolved `await`" from "never got
off the ground". That handshake is the design: it makes `const p = f();
release();` work instead of deadlocking, and lets `Promise.all([a(), b()])`
overlap rather than serialize. Argument binding is part of startup and runs on
the activation thread under the same protocol — hence
`JsFunctionNode.executeBody` as the split-out synchronous body run.

Unwind ownership is explicit: on vthread-start failure the **caller** releases
the token and settles the promise rejected (ownership transfers only once the
thread has started); an interrupted outcome-wait cancels the scope and propagates
`EngineInterruptedException` **without touching `jsLock`** — ownership there
belongs to the activation or to nobody, so every enclosing frame unlocks
conditionally (`if (jsLock.isHeldByCurrentThread())`).

`AsyncActivation.awaitOn` suspends at the first **unresolved** await: arm the
resumption first (`cf.whenComplete(… unpark)` — an unpark permit survives an
unpark-before-park race, a condition flag would not), publish `SUSPENDED`,
release `jsLock` in a `finally`, then park. On wake it validates the scope gate,
reacquires `jsLock`, and validates **again**. `AsyncActivation.anyAsync` is a
JVM-wide monotonic flag (same shape as `Prototype.anyUserProps`): while false the
interpreter's polls skip the thread-local lookup, so synchronous scripts pay
nothing for any of this.

**Top-level `await`** has no activation — the eval thread becomes the pump
(`AsyncSupport.pumpUntilSettled`): release `jsLock`, `takeJob`, reacquire, run,
re-check the target. That is what lets activations and callbacks interleave with
top-level code; a quiescent scope with an unsettled target raises "await on a
promise that can never settle" rather than hanging.

### Eval scopes

Each **outermost** `Engine.eval` opens one `AsyncScope`, and **scope object
identity is the generation stamp** — deliberately not `ContextRoot.evalId`, a
wrapping `short` that nested evals increment. `Engine.enterEvalScope` serializes
outer evals: a second host thread blocks until the current scope has fully
closed, drain and teardown included. A **nested** eval on the same thread (a job
or host callback calling `Engine.eval`) opens no scope — it shares the outer one
and its pump owner via an eval-depth counter, and only depth 0 runs the
end-of-eval drain and teardown. `Engine.eval` keeps its synchronous
`String → value` contract: `AsyncSupport.finishScope` drains to quiescence, then
unwraps a promise result (a rejection is thrown, and marked *observed* so it is
not double-reported as unhandled).

### Cancellation and teardown

`AsyncScope.close` is a **fenced, awaited** teardown, run by the pump owner
holding no locks: (1) the facade goes `CLOSED` — every late `publishSuccessor` is
rejected and the activation reacquisition gate is shut; (2) live timers are
CAS-cancelled; (3) queued jobs are discarded and their tokens released; (4) every
live activation is interrupted **and waited for**, bounded by
`Engine.asyncTeardownMillis`. No subsequent outer eval may start until that
returns, so a cancelled activation's preserved stack can never mutate a later
eval's state.

Thread interruption alone is not a sufficient fence, because host code may
swallow or clear it. Two additions close that: `Interpreter.checkInterrupted` now
tests **scope closure as well as thread interruption** (gated on `anyAsync`), and
a **post-host-return fence** (`AsyncSupport.hostReturnFence`) runs at every
host-call return in `Interpreter` before any further JS executes or anything is
settled. If the teardown deadline still expires with an activation stuck in
non-interruptible host code, the engine is **poisoned** — a permanent atomic
transition made *before* the timed-out teardown returns. `Engine.checkPoisoned`
runs before waiting for scope ownership or touching `jsLock`, so later evals fail
fast instead of blocking on a lock the stuck activation may still hold;
`Engine.isPoisoned()` is public, and embedders must not return a poisoned engine
to a reuse pool.

An activation that hits a fatal internal error must **not** tear its own scope
down — it would join its own thread. It calls `AsyncScope.requestCancel`, which
marks the request and enqueues an `AsyncJob.Control` as **one atomic operation**,
race-safe whether the pump owner is already blocked on the empty queue or has not
begun waiting; teardown is then the pump owner's work.

Host cancellation never becomes a catchable rejection: at every async boundary
`FlowControlSignal` and `EngineInterruptedException` are rethrown *before* any
catch-and-reject conversion (`AsyncSupport.isHostCancellation`). The optional
drain cap (`Engine.setAsyncDrainTimeout`, off by default — cooperative interrupt
stays the outer bound) throws `EngineTimeoutException`, deliberately a **subclass
of `EngineInterruptedException`** so it inherits the existing "JS can't catch it,
hosts pass it through unwrapped" routing with no second special case.

### Promise semantics

`JsPromise extends JsObject` for the prototype surface, but **its settlement
state lives exclusively in its `CompletableFuture`** — the one field foreign
threads may touch; reactions and JS-visible effects are mutated only under
`jsLock`, by queued jobs. It is deliberately **not** a `JsValue`, so
`Engine.toJava` passes it through unchanged and a host calling an async JS
function gets the promise, not an auto-awaited value (blocking unwrap is opt-in:
`await()` / `join(Duration)`).

- **One adoption operation**, `AsyncSupport.resolveValue`, shared by the
  executor's `resolve`, `Promise.resolve`, async-function return, `.then` results
  and `CompletionStage` wrapping. JS-level rules run before any CF composition:
  self-resolution → `TypeError`; a `JsPromise` is adopted, never nested; `then`
  is read **once**, through a property access that may itself throw; thenable
  resolve/reject are first-call-wins, and a `then` that throws after resolving is
  swallowed per spec. Callbacks always route through the queue, even for an
  already-settled promise ("sync code first, callbacks after").
- **Unhandled rejections are tracked at reaction level, not lineage-wide.**
  Attaching any reaction marks the source handled — a fulfillment-only `.then(f)`
  transfers rejection responsibility to the derived promise, tracked in its own
  right, so `Promise.reject(x).then(v => v)` reports the *derived* promise,
  exactly once. Decided only at scope quiescence (so a later-queued `.catch`
  counts); the default is **fail the eval** with the first terminal rejection,
  the rest logged. `Engine.setAsyncRejectionWarnOnly(true)` downgrades to WARN on
  SLF4J and the `console` consumer (`ContextRoot.onConsoleLog`).
- **`JsRejectionException`** carries a rejection across the Java boundary and is
  the **only** exception the engine ever unwraps back into a JS reason. Java → JS
  rules, in order (`AsyncSupport.reasonFromStage`): unwrap `CompletionException` /
  `ExecutionException` **one layer**; our wrapper → its `getReason()`;
  `EngineInterruptedException` → rethrown as host cancellation, never a reason;
  `CancellationException` → a JS `Error`; anything else → the ordinary host-error
  conversion. Arbitrary Java exceptions never masquerade as JS rejections.
- **`toFuture()` returns the same instance every time** — stable,
  scheduler-neutral (no thread, ordering or drain guarantee), and cancelling it
  does **not** cancel the JS activation; it is a view. `JsPromise.PromiseView`
  carries the reverse association, so a promise's own future handed back into JS
  recovers the original `JsPromise`.
- Java `CompletionStage` → JS is wrapped on first crossing with **scope-scoped**
  identity (an `IdentityHashMap` dropped wholesale at close — weak keys were
  rejected because the wrapper's CF graph strongly reaches the stage); identity
  across scopes is explicitly not promised. An armed subscription holds a token,
  so a Java future in flight keeps the eval from going quiescent.
- `JsPromiseConstructor` statics: `resolve`, `reject`, `all`, `allSettled`,
  `race`, `any`. `new` on an async function is a `TypeError` —
  `JsFunctionNode.isConstructable` returns false for `async` as for arrows.

### setTimeout / clearTimeout

Registered in `ContextRoot.initGlobal` alongside `Promise` and `globalThis`. One
shared lazy single-thread scheduler (`ThreadUtils.daemonFactory("js-timer-")`),
registered with `KarateLifecycle` on first use — see
[DESIGN.md § Threading & Lifecycle](./DESIGN.md#threading--lifecycle) for the
`Stoppable` registry. Owner resolution is from the call's own
`CoreContext.getEngine()`, validated against `Engine.current()`; scheduling
without a definite engine is a `TypeError`, not a guess. Ids are engine-local
(`AsyncScope.nextTimerId`), and each record is a `LIVE → FIRED | CANCELLED` CAS,
so a callback can never run after `clearTimeout` returned and the timer thread
may only win that CAS and `publishSuccessor` — never run JS. Delay coercion:
`NaN` / negative / non-numeric → 0, fractional → floored, infinite →
`Integer.MAX_VALUE`; a missing or non-callable callback is a `TypeError`; extra
args pass through. **No `setInterval` by design** — it never quiesces.

### Job ordering — the microtask checkpoint

`AsyncScope` keeps **two FIFO queues**, and `AsyncJob.macrotask()` is the single
place the split is decided: a fired `AsyncJob.Timer` is the only macrotask;
reactions, resolutions, thenable adoptions and the cancellation control job all
go to the microtask queue. `takeJob` gives the microtask queue **strict
priority**, and because the pump asks for one job at a time that single rule is
the HTML checkpoint — microtasks drain to exhaustion (including microtasks
enqueued by microtasks) after the synchronous script and again after every timer
callback returns, before the next timer runs. Timers reach the macrotask queue in
the order the shared scheduler fires them, so they order by delay with ties FIFO.

Quiescence is unaffected: both queues are counted, every queued job still holds
its own token, and a microtask loop that never ends starves timers exactly as a
browser would.

### Accepted deviations

1. The **settled-`await` fast path does not yield** to the microtask queue.
2. **A suspended activation's resumption is not a queued job.** Its ordering
   against an already-pending timer is decided by the fair lock, not by the job
   queues (deviation 3), so `setTimeout(() => resolve(), 0)` with a second timer
   already pending runs that second timer before the resumed `await` body —
   HTML would run the continuation first. Ordering *through* a promise reaction
   (`.then`, an async function's own result promise being consumed) is exact.
3. `Engine.eval` blocks to quiescence by design; a far-future timer blocks it
   (interrupt and the drain cap are the escape hatches), and fair-lock order
   among runnable activations is approximate, not the spec's job ordering.
4. No `queueMicrotask`, no `setInterval`, no async iterators — `for await` is
   rejected at parse time.

> **Spec invariant.** Pinned by `JsAsyncAwaitTest` (19 cases — parse and call
> shape, contextual-keyword back-compat), `JsPromiseTest` (60 — promise surface,
> adoption, job ordering, unhandled-rejection policy, the Java bridge) and
> `JsAsyncConcurrencyTest` (18 barrier cases — token idempotence,
> close-vs-publish races, unpark-before-park, interrupt-before-start, stale
> reacquisition, nested eval, cross-thread eval serialization, stage-cache
> reclamation, the poison transition, the drain cap, engine reuse after a
> cancelled eval).

---

## Generators (`function*` / `yield` / `yield*`)

Generators reuse the async foundation — a suspended body needs its own stack,
so each *started* generator owns one virtual thread, strictly alternating
with its **driver** (the JS thread calling `next`/`return`/`throw`) under the
engine's fair `jsLock`. `GeneratorActivation` is the coroutine engine;
`JsGenerator extends JsObject` is the object, with `next`/`return`/`throw` +
`@@iterator → this` on the shared `JsGeneratorPrototype` (brand-checked — a
borrowed `next.call(notAGenerator)` is a TypeError). Because well-known
symbols are string stand-ins, a generator plugs into for-of / spread /
destructuring / `Array.from` through the existing `@@iterator` protocol with
zero consumer changes. The design went through a 3-round external review;
the invariants that came out of it:

1. **A RUNNING step is scope-owned; a SUSPENDED generator is not.** The
   driver registers the step with its current `AsyncScope` exactly once
   (`addGenerator` is atomic register-only-if-open); the gen side only ever
   *de*registers — at yield publication and in its outer finally. Scope
   teardown cancels and joins registered generators with the same bounded
   deadline + engine-poisoning story as async activations. A suspended
   generator holds no `AsyncToken`, survives eval-scope close, and is
   resumable from a later eval on the same engine (`jsLock` is per-Engine,
   not per-scope). Quiescence needs no generator token: the driver is parked
   inside its own eval until the step completes, and async work created
   mid-step holds its own tokens.
2. **`gen.return(v)` is a return completion injected at the yield site**
   (`context.stopAndReturn`) — never a `FlowControlSignal`, which
   `evalTryStmt` rethrows *before* running `finally`. Riding the completion
   machinery is what makes `finally` run, lets a `yield` inside `finally`
   suspend normally (the pending RETURN completion is parked in
   `evalTryStmt`'s saved locals on the generator's own vthread stack), and
   gives finally-overrides-return the same answers regular code gets.
   `gen.throw(e)` likewise injects through the cooperative
   `context.stopAndThrow` seam — catchable by the body, never a Java throw
   of the raw value.
3. **`yield*` is an explicit iterator-record state machine** (spec
   §27.5.3.7) over the delegate's `next`/`throw`/`return` invoked *with
   arguments* and validated per result — not the value-only `JsIterator`
   walk, which cannot carry sent values, throw forwarding, or the
   delegate's final completion value. A delegate `return()` answering
   `done:false` keeps delegation alive.
4. **Handoff ownership**: the state CAS (`NOT_STARTED|SUSPENDED → RUNNING`)
   owns the step; resume input is written under `jsLock` before the unpark
   and read after reacquisition; each step has a fresh single-assignment
   outcome cell with a last-resort HOST_CANCELLED publication in the gen
   thread's outer finally (a driver can never park forever); vthread start
   failure retires the generator while the driver still holds the lock; a
   driver interrupted mid-step cancels the gen thread and rethrows
   `EngineInterruptedException` without reacquiring `jsLock`.
5. **A nested `eval()` on an activation thread shares the open scope.**
   `Engine.enterEvalScope`'s nested-eval detection is scope-aware, not
   thread-identity-only: an async-activation or generator vthread whose work
   belongs to the currently open `AsyncScope` increments `evalDepth` instead
   of waiting for the scope to close — the wait deadlocked (the scope owner
   is parked waiting for that very thread). This fixed generators and a
   pre-existing `eval()`-inside-`async`-function deadlock in one seam.

Accepted deviations (shared with async where noted): parameter binding runs
on the gen thread at first `next()`, not at generator-function call (same as
async activations); async generators / `for await` are unimplemented
(`async-iteration` stays feature-skipped); the `GeneratorFunction` intrinsic
realm surface is absent; GC reclaim of a parked vthread whose abandoned
generator became unreachable is a best-effort backstop, not a contract — the
explicit cleanup path is `return()` / for-of close.

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

### Class semantics (`class` / `extends` / `super`)

**`class` is desugared at eval time, not rewritten in the AST.**
`Interpreter.evalClassExpr` builds a constructor `JsFunctionNode` whose
auto-allocated `.prototype` holds the instance methods (installed
non-enumerable via `defineOwn(..., WRITABLE|CONFIGURABLE)`); `static` members
go on the constructor; `get`/`set` become `AccessorSlot`s. Class bodies are
always strict (the `JsFunctionNode` `forceStrict` ctor overload). The whole
thing rides the existing `new` / prototype-chain / `this`-binding path —
`new Foo()` over a class constructor is an ordinary construction.

**`extends` links two chains.** `Child.[[Prototype]] = Parent` (static method
inheritance *and* the object `super(...)` reads to find the parent
constructor) and `Child.prototype.[[Prototype]] = Parent.prototype` (instance
method inheritance + `instanceof`). Set via `setPrototype` at class-eval time.

**`super` dispatch rests on two seams that future code must keep wired:**
- **`JsFunctionNode.homeObject`** — the [[HomeObject]]: the class prototype for
  an instance method/constructor, the constructor for a static method. A
  `super.m()` resolves `homeObject.getPrototype()` and reads `m` off it with
  `this` = the current receiver (not the prototype).
- **`CoreContext.activeFunction`** — the `JsFunctionNode` whose body is running
  in this frame. It **must be set on every non-arrow user-function call frame**
  (`invokeCallable`, `JsFunctionNode.call`, `runSuperConstructor`); an arrow
  inherits its defining method's value (from `declaredContext.activeFunction`)
  so `super` inside an arrow-in-a-method still resolves. Nested block scopes
  inherit it through the `CoreContext` constructors. Miss a call site and
  `super` in that frame resolves to the wrong (or no) home object.

**`super(...)` does not refactor construction.** A derived `new Child()`
creates the instance normally (proto already chains to `Parent.prototype`);
`super(...)` then runs the parent constructor *against that same instance* via
`Interpreter.runSuperConstructor` rather than allocating a new one. A class
with no constructor that `extends` carries `isDefaultDerivedConstructor` and
forwards all args to `super(...)` at construction time.

**Public fields are enumerable own properties, run per-instance.** The parser
emits a field as a `CLASS_METHOD` node with no trailing `FN_EXPR` (just an
optional `= EXPR`); `evalClassExpr` resolves the field name once (computed
names too) and stashes `JsFunctionNode.instanceFields`. Each `new` runs
`Interpreter.runInstanceFieldInitializers` with `this` = the instance, in
declaration order — before the constructor body for a base class, right after
`super()` returns for a derived one. `static` fields are evaluated at
class-definition time with `this` = the constructor. Unlike methods, fields
use `putMember` (enumerable).

**Known deviations (deferred):** no `this`-TDZ before `super()`, no `super()`
return-override, `extends` of a built-in exotic (Error/Array) uses a
copy-own-props shim rather than true exotic subclassing; decorators, class
early-errors and the public-field conformance edge tail stay test262-skipped
(see the un-skip gate in `karate-js-test262/etc/expectations.yaml`).

**`async` methods are honored** — instance, `static`, and computed-name alike.
The parse-time flag reaches `JsFunctionNode.async`, so `new C().m()` returns a
promise rather than the body's value (see
[§ Async / await / Promise](#async--await--promise)).

**Private class elements (`#x`) are fully modelled** — instance/static
private fields, private methods and static methods, private accessors,
read/write/compound/logical-assign/inc-dec via `this.#x` / `obj.#x`,
`obj?.#x`, and the `#x in obj` brand check. The lexer emits a
`PRIVATE_NAME` token (`#` + IdentifierName; a stray `#` is a clean
`ParserException`, not the lenient IDENT fallback). Each class evaluation
allocates a `PrivateEnv` binding `#x` to a fresh `PrivateName` identity,
chained to the enclosing class's env; functions capture it eagerly at
construction (`JsFunctionNode.privateEnv`) and re-install it on their call
frame — resolution is lexical, so callbacks never inherit it. Per-object
state lives in `JsObject.privates`, a lazily-allocated identity-keyed side
map, fully out-of-band from `props`: `Object.keys` /
`getOwnPropertyNames` / `for...in` / spread / `JSON.stringify` /
`obj['#n']` can neither observe nor collide with it. Private
methods/accessors hang off the `PrivateName` itself; the instance map
holds a brand sentinel backing `#x in obj` and the foreign-receiver
TypeError. Parse-time SyntaxErrors (one helper in the fused
`JsParser.earlyErrors` walk, gated on a `sawPrivateName` flag so `#`-free
files pay nothing): undeclared `#x`, `#x` outside a class,
`delete obj.#x`, bare `#x` outside `in`, duplicate private names,
`#constructor`.

**Field initializers run for every class on the prototype chain.**
`runSuperConstructor` runs the parent's instance-field initializers when
construction reaches it through `super()` — explicit or from a default
derived constructor — so `class A { x = 1 }; class B extends A {}` gives
`new B().x === 1`. (Private brands install during field initialization,
which is what made this bug load-bearing.)

**Arrow `this` is lexical (spec §10.2.1.3), enforced at call time.** Every
call frame is seeded from the caller's dynamic chain, so the three
arrow-call sites (`Interpreter.evalFnCall`'s two paths and
`JsFunctionNode.call`) rebind via `Interpreter.bindArrowThis`, which reads
`thisObject` off the arrow's `declaredContext`. Field initializers each run
in their own child `CoreContext` carrying `thisObject` = the instance
(`Interpreter.evalFieldInitializer`) — an arrow field keeps that frame as
its `declaredContext`, so `class C { f = () => this.x }` works after
construction; save/restore mutation of the shared context would not.

`JsClassTest` is the canonical behavior record (100+ cases, including the
ES2022 private-element and arrow-field families).

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

**`globalThis` names that same object.** `ContextRoot.initGlobal` registers
`globalThis` as a lazy global resolving to the `JsGlobalThis` that backs
top-level `this`, so `globalThis === this` at top level and the two see one
store. Absent from the
global surface: `WeakMap` / `WeakSet` / `Proxy` / `ArrayBuffer` / `DataView`
(and the Iterator helpers); `Uint8Array` is the only binary type, per the
§Type Mapping table (known deviations — see TEST262.md Active priorities).

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

**Minimal `Symbol` global — two kinds, stored differently.** `typeof` reports
`"symbol"` and `Object.prototype.toString` reports `[object Symbol]` for both.

A **minted** symbol (`Symbol(desc)`) has *no string key at all*. It addresses a
separate symbol-keyed store on `JsObject` (`JsObject.symbols`) holding
`PropertySlot`s. Spec-exact there: writable/enumerable/configurable attributes,
accessors (`get [s]() {}`), `getOwnPropertyDescriptor` and `defineProperty`
(sharing the string path's ValidateAndApplyPropertyDescriptor rejection checks),
`freeze`/`seal`, strict-mode rejection on write and delete, prototype-aware
`[[Set]]` (`JsObject.setSymbol` — an inherited setter runs with the receiver, an
inherited non-writable data property rejects), and copying through
`Object.assign` ([[Set]]) and spread (CreateDataProperty) for own enumerable
symbols only. It is keyed by `JsSymbol` identity — the same shape the ES2022 private names beside it already
use, and lazily allocated so an object with no symbol keys pays one null check.
`JsSymbol.keyedBy(key)` is the single seam every property site consults to pick
the store: computed get / put / delete in `PropertyAccess`, `in`,
`hasOwnProperty`, computed keys in object literals, and the `Object.assign` /
spread copies (the spec copies both key partitions). Because a minted symbol
never enters the string key space, `Object.keys` / `values` / `entries`,
`getOwnPropertyNames`, `for…in` and `JSON.stringify` skip it **by
construction** — there is no predicate that could misclassify a key, which is
what makes a customer payload key like `"@@type"` or `"@@sym:1:x"` safe. It is
reachable only through the symbol: `Object.getOwnPropertySymbols` and
`Reflect.ownKeys` return the symbol *values*.

A **well-known** symbol (`Symbol.iterator` and friends) is an engine-internal
*string* key — `"@@iterator"` — because the iteration, `ToPrimitive` and
`toStringTag` dispatch is built on those strings (`IterUtils.SYMBOL_ITERATOR`).
`ContextRoot` installs them from `JsSymbol.WELL_KNOWN`. `Terms.toPropertyKey`
is the one place such a symbol coerces to its string, which is what keeps
`obj[Symbol.iterator]` resolving to `obj["@@iterator"]`. **Consequence:** a
well-known symbol used as a key stays visible to the string-key surfaces —
`Object.keys({[Symbol.iterator]: f})` shows `"@@iterator"` — and, symmetrically,
a user string key `"@@iterator"` is ordinary data that round-trips. That is a
pre-existing deviation, not a property of the symbol store.

**Coercion.** Implicit `ToString` / `ToPrimitive` on a symbol throws a
`TypeError` per spec — `` `${sym}` ``, `sym + ''`, `'' + sym`, `[sym].join()`.
`String(sym)` is the one operation that does not: it yields
`SymbolDescriptiveString` (`"Symbol(x)"`), special-cased in `JsString.getObject`,
and `console.log` follows it so logging never throws. `Terms.toPropertyKey` has
its own branch, which is what keeps a well-known symbol resolving to its string
key. Loose equality does not coerce: a symbol equals only itself.

**Well-known identity is per Engine — a deliberate deviation.** `ContextRoot`
builds a fresh well-known set during each global initialization, so
`Symbol.iterator === Symbol.iterator` holds *within* one Engine but two Engines
get distinct objects. The spec shares the well-known symbols across every realm
in an agent, so this is a deviation, not realm-correct behaviour; it is
invisible to code that stays inside one Engine.

Residuals, exactly as they stand: no `Symbol.prototype`, no registry
(`Symbol.for` / `keyFor`), no `description` accessor; a `JsSymbol` is a
`JsObject` underneath, so `instanceof Object` is `true` and `Object.keys(sym)`
works; well-known symbols remain visible to `Object.keys` /
`getOwnPropertyNames` as their `"@@…"` string keys; a symbol key on a
non-`JsObject` target (a `JsArray`, a raw Java `Map`) has nowhere to go; and
`Reflect.get` / `set` / `has` / `deleteProperty` are absent (only `construct`,
`apply` and `ownKeys` exist). Tests needing those still skip via
`feature: Symbol`.

**C-style `for` per-iteration `let`/`const` environment (§14.7.4.3).**
`Interpreter.evalForStmt` keeps three environments distinct, and a refactor must
preserve all three: (1) the **LOOP_INIT** scope where the init declaration lives
— a closure created in the initializer (`for (let i = 0, f = () => i; …)`)
captures these slots, which must NEVER be mutated by the loop, so it keeps seeing
the initial value; (2) the **per-iteration body scope** — test + body run here so
a closure created in the body captures that iteration's distinct binding (the
`[0,1,2]` loop-closure invariant); (3) a **fresh increment scope** seeded by
copying the body's end-of-iteration values, in which the increment clause runs —
this is what lets an in-body update with no increment clause (`for (let x = 0; x
< 10;) { x++; }`) carry forward without the increment leaking into a body closure.
Values thread across iterations through an explicit `carry` list, NOT by writing
back to the LOOP_INIT slots (doing so corrupts initializer closures and can hit
an outer `const` referenced by the initializer expression). `var` loop variables
take a single shared scope (no per-iteration binding). Pinned by
`SpecPinTest.forLet_*`.

### Own-key ordering

**Spec §9.1.11.1 OrdinaryOwnPropertyKeys ordering applied at the
`JsObject` seam.** Integer-index string keys come first in ascending
numeric order (per `JsArray.parseIndex` — the canonical
CanonicalNumericIndexString check); remaining string keys keep
insertion order. Single helper:
`JsObject.orderedOwnKeys(Set<String> insertionOrder)` — a no-op when
no integer-index keys are present (the common case for prototype /
global surfaces, so prototype/Prototype-subclass iteration pays
nothing). Two consumers route through it:

- `JsObject.jsEntries(ctx)` — back-end of `Object.keys / values /
  entries / assign` via `Terms.toIterable`. Pre-materializes a
  spec-ordered slot list once (tombstone-skipped, enumerable filter
  re-applied at yield time so a getter that mid-iteration flips a
  not-yet-yielded slot's enumerable bit is observed — test262
  `{entries,values}/getter-making-future-key-nonenumerable.js`).
- `JsObjectConstructor.ownKeys` — back-end of
  `Object.getOwnPropertyNames / getOwnPropertyDescriptors /
  defineProperties`. Falls through to `orderedOwnKeys(toMap().keySet())`
  for the generic `ObjectLike` branch (Prototype, JsGlobalThis,
  raw Map).

`JsArray` is exotic: it has its own integer-first iteration via the
dense list (Phase 1) plus a Phase 2 walk over `namedProps` for
non-index enumerable entries (test262 `Object/keys/15.2.3.14-5-12.js`
installs an accessor named `"prop"` on an array). Mirrors §9.4.2
[[OwnPropertyKeys]] for Array exotics. **Hole**: integer-index
accessors beyond `list.size()` — currently worked around by
`defineOwnAccessor`'s HOLE-pad loop extending `list` to `idx + 1` so
Phase 1 reaches the slot (allocates `idx` HOLE entries; correctness-
brittle if a later spec-shape change drops the pad). Real fix tracks
them in `namedProps` and merges into Phase 1 ordering — see the
`JsArray.jsEntries` TODO in TEST262.md.

**Future contract.** Code that surfaces own keys for a JsObject must
go through `jsEntries(ctx)` or `JsObject.orderedOwnKeys(...)` — never
read `props.keySet()` / `toMap().keySet()` raw.

**CopyDataProperties (§7.3.26) has one seam:
`Terms.copyDataProperties(target, source, excluded, ctx)`.** Three
call sites share it — `Object.assign`, object spread `{...src}`
(`Interpreter.evalLitObject`) and destructuring rest `{a, ...rest}`
(`Interpreter.bindPattern`). Spread and rest previously each carried
their own slot-copy loop, which read raw `DataSlot` values (an
accessor copied as `null`) and did not consult the enumerable bit. The
seam iterates `toIterable(source, ctx)`, so the getter fires at copy
time and its result lands as a *data* property; non-enumerable own
properties never copy; a null/undefined source is a no-op; primitives
contribute whatever their ToObject wrapper exposes. `excluded` is the
rest pattern's already-bound key set. A getter that throws stops the
copy (`ctx.isError()` checked per entry). Pinned in
`SpecPinTest.copyDataProperties_*` / `objectSpread_sourceShapes`.

**B.3.1 `__proto__:` in an object literal has one shape predicate:
`JsParser.isProtoSetter(objectElem)`.** The proto-setter form is a
plain `__proto__` key — identifier or quoted string — followed by
`:`. Nothing else qualifies: shorthand `{__proto__}`, computed
`{['__proto__']: v}`, methods, accessors and spread all create
ordinary own properties. `Interpreter.evalLitObject` calls it to route
the value into `setPrototype` (object or null only; any other value is
silently ignored and still creates no own property), and the fused
early-error walk calls it to enforce §13.2.5.1's at-most-one rule —
gated on `!inPattern`, since the cover grammar drops the rule for
destructuring. Both must keep using the one predicate or the early
error and the evaluation will disagree. Pinned in
`SpecPinTest.protoLiteral_*` /
`JsParserTest.testDuplicateProtoSetter`.

**`for-in` walks the prototype chain.** `Terms.forInIterable(o, ctx)`
is the back-end (distinct from `toIterable`, which yields own
properties only — used by `Object.keys / values / entries / assign`).
The walker collects enumerable own string keys at every level via
`getPrototype`, dedup'd by name (closer-receiver wins). `Prototype`
singletons participate via `userProps` + `getOwnAttrs` enumerable
filter. Spec mapping: §14.7.5.6 EnumerateObjectProperties. Limitation:
non-enumerable own keys at a closer level don't currently shadow
inherited same-named enumerable keys — none of the test262 paths
exercising for-in over inherited properties surface this edge case
today; revisit when one does. Pinned in
`SpecPinTest.forIn_walksPrototypeChain_yieldsInheritedEnumerable` /
`forIn_skipsInheritedNonEnumerable`.

**`Prototype.defineOwn(name, value, attrs)` carries the descriptor's
attribute byte.** `JsObjectConstructor.applyDefine` previously fell
through to `putMember` for `Prototype` targets, dropping the
descriptor's attrs (defaulted to W|E|C). The new seam stores them so
`getOwnPropertyDescriptor` and the for-in enumerable filter both see
spec-correct attrs after
`Object.defineProperty(Function.prototype, "p", { value:1,
enumerable:false, … })`. Pinned in
`SpecPinTest.defineProperty_onPrototype_dataDescriptor_storesAttrs`.

**`Object.keys / values / entries / getOwnPropertyNames` return Array
exotics, not raw `ArrayList`.** Test262 (and idiomatic JS) calls
`Object.keys(o).hasOwnProperty(0)`, `arr instanceof Array`,
`Object.getOwnPropertyDescriptor(arr, "0")` on the result — all
require a `JsArray`. `Object.entries` wraps both the outer list and
each `[k, v]` pair. The Java-interop seam preserves: `JsArray.get(int)`
unwraps `Terms.UNDEFINED` to `null`, `JsArray.iterator()` walks raw
slots; raw element access via `.getElement(int)`.

### Optional chaining

**Optional chaining sentinel propagation.** `PropertyAccess.SHORT_CIRCUITED`
(distinct identity from `Terms.UNDEFINED`) propagates through chain steps;
`Interpreter.chainStepResult` converts to UNDEFINED only at the chain root.
The "distinct from UNDEFINED" detail is load-bearing — `obj?.a.b` where
`obj.a == null` still throws TypeError per spec. Optional-chain early errors
are validated post-parse in a single walk
(`JsParser.validateOptionalChainEarlyErrors`), not interleaved into the hot
eval loop.

**Write-site short circuits are separable from abrupt completions.**
`PropertyAccess.resolveWriteSite` returns a distinct `SHORT_CIRCUIT_SITE`
when the target chain short-circuited and `null` on abrupt completion, so
the two outcomes never fold together. Assignment / compound / inc-dec on an
optional chain is a parse-time early error, so the short circuit reaches
only `delete` (which yields `true` per spec — no reference, nothing to
delete) and destructuring-pattern leaves. Read paths were always complete:
`typeof (o.x?.y)` is `'undefined'` and the `obj?.a.b`-throws invariant
holds; note the host seam (`Engine.eval`) still unwraps `UNDEFINED` to Java
`null`, which is easy to misread as an engine bug when probing from Java.

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

**Object ToString is unified** via `Terms.toStringCoerce(Object, CoreContext)`
— the ToPrimitive → `toString` dispatch for `ObjectLike` receivers. Use
`StringUtils.formatJson` directly for JSON display, not the legacy formatter.

**Number → String is unified on `Terms.numberToString`** — the single
implementation of spec Number::toString (§6.1.6.1.13 — plain-decimal band
`1e-6 ≤ |d| < 1e21`, `e+`/`e-` form outside it, `-0` → `"0"`). Every
user-visible number → string site routes through it: `Terms.toStringCoerce`,
the `+` string-concat branch (`Terms.concatOperand`),
`JsNumberPrototype.numberToString` (radix-10), `StringUtils.formatRecurse`
(JSON output), and ToPropertyKey — so `String(1e21)` is `"1e+21"` and
`JSON.stringify({a:1e21})` is valid JSON. It sits on the interpreter hot
path (property keys, concat), so `Integer`/`Long` short-circuit by type
test before any double inspection. `Terms.parseFloat` scans the numeric
prefix (including a well-formed ExponentPart — a dangling `e` is not
consumed, so `parseFloat('1e')` is `1`) and delegates digits → double
rounding to the JDK; `ContextRoot.PARSE_FLOAT` / `PARSE_INT` spec-stringify
a `Number` arg instead of Java-formatting it.

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

**Comparison operators are three seams, all ToPrimitive-aware.**
`Interpreter.evalLogicExpr` dispatches `==`/`!=` to `Terms.looseEq(lhs, rhs,
ctx)` (spec IsLooselyEqual §7.2.14), `===`/`!==` to `Terms.strictEq`
(IsStrictlyEqual §7.2.15), and all four relational operators to
`Terms.isLessThan(lhs, rhs, leftFirst, ctx)` (IsLessThan §7.2.13, returning
`1` / `0` / `LESS_UNDEFINED`). `x > y` is `isLessThan(y, x, false)` and
`x <= y` is its negation — the operand swap pairs with clearing LeftFirst,
which is what keeps the two ToPrimitive calls in source order. An ObjectLike
operand routes through `Terms.toPrimitive` with the live ctx exactly as `+`
does, so a user `valueOf`/`toString` runs and its abrupt completion
propagates via `context.isError()`. Two string primitives compare as strings
(`String.compareTo` is the spec's UTF-16 code-unit order); everything else
numerically. `Terms.eq(lhs, rhs, true)` stays SameValueZero for Map / Set key
lookup — `strictEq` is the operator wrapper that adds the NaN exclusion Java's
`Double.equals` does not have.

**`Terms.narrow()` checks both ends.** Pre-existing bug: `if (d <=
Integer.MAX_VALUE) return (int) d` cast any negative value past
`Integer.MIN_VALUE` to an overflowed int. Fix: both bounds (`d >=
Integer.MIN_VALUE && d <= Integer.MAX_VALUE`) on the int and long collapses.
The collapse rule itself is unchanged for in-range values.

**`Terms.toPropertyKey(o, ctx)` is spec ToPropertyKey (§7.1.18).** With
ctx, ObjectLike receivers route through `toPrimitive(o, "string", ctx)`
(toString first, then valueOf, TypeError when neither yields a
primitive — matches `Object.defineProperty(obj, {toString:()=>{},
valueOf:()=>{}}, ...)` per test262 `15.2.3.6-2-47`). Without ctx,
falls back to Java `o.toString()` (legacy lenient path). The
context-flowing call sites pass ctx — `defineProperty` is migrated;
`hasOwn` / `getOwnPropertyDescriptor` are still on the no-ctx path
(both wired as `JsInvokable`; switch the wiring when a real workload
passes non-string keys). The numeric branch (`Terms.numberToString`)
is spec Number::toString: `0.000001` → `"0.000001"` (BigDecimal
plain-string with `stripTrailingZeros()` so `Double.toString`'s
`"1.0E-6"` round-trip doesn't leak a trailing zero through scale).

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

**Generic descriptors preserve the existing slot's type.** Spec
ValidateAndApplyPropertyDescriptor §10.1.6.3: a descriptor that
specifies neither value/writable nor get/set is *generic* — it only
flips enumerable / configurable bits, never the descriptor shape.
`JsObjectConstructor.defineProperty` routes the generic-on-accessor
case through `applyAttrsOnly` (mutate `slot.attrs` in place) instead
of falling through to `applyDefine` (which would clobber the
AccessorSlot with a fresh DataSlot carrying undefined). The existing-
accessor → data-descriptor path (writable-only on an accessor) still
switches shape with undefined value per spec. Pinned in
`SpecPinTest.genericDescriptor_onAccessor_preservesGetSet` /
`genericDescriptor_onAccessor_preservesShape`.

**Accessor descriptor's `get` / `set` field rejects non-callable
non-undefined.** Spec ToPropertyDescriptor §6.2.5.5 step 7.b/8.b:
TypeError when get/set is present and not undefined and not callable.
`null` is non-callable non-undefined → TypeError. Pre-fix our engine
silently accepted `null`. Pinned in
`SpecPinTest.definePropertyNullGetter_throwsTypeError` /
`definePropertyNullSetter_throwsTypeError`.

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

**`Object.getPrototypeOf` dispatches via `ObjectLike.getPrototype()`.** All
three storage shapes (`JsObject`, `JsArray`, `Prototype` singleton) implement
`ObjectLike#getPrototype`; the constructor's introspector branches on
`instanceof ObjectLike` rather than the historical
`instanceof JsObject || JsArray`, so
`Object.getPrototypeOf(Set.prototype) === Object.prototype` (and same for
`Map.prototype`, `Array.prototype`, etc. — all `Prototype` singletons). The
old narrower dispatch returned `null` for any `Prototype` receiver and broke
properties-of-the-X-prototype-object.js tests across Map / Set / Array /
RegExp / Error / Date / Function / Number / Boolean / String / BigInt.

**Per-Engine prototype isolation.** Built-in prototypes are JVM-wide
singletons (e.g. `JsArrayPrototype.INSTANCE`), but user mutations
(`Map.prototype.set = function() { throw ... }`) are per-Engine state: they
live in an overlay on the `Engine` (`Engine.protoUserProps`, keyed by
prototype identity) and are resolved through the thread's *current engine*
(`Engine.current()`, an eval-scoped ThreadLocal established with
save/restore nesting by `Engine.evalInternal` / `evalRaw` and by
`JsFunctionNode.call` for host-initiated invocations). One engine's
prototype pollution is therefore invisible to — and indestructible by —
every other engine, with no reset step at all; the overlay dies with its
Engine. This replaced the historical clear-on-construct model
(`Prototype.clearAllUserProps()` walking a static registry from
`Engine.<init>`), which was correct only for sequential single-Engine usage:
under concurrent engines, one thread's `new Engine()` wiped singleton state
out from under another thread mid-evaluation (manifesting as intermittent
`TypeError: Object.keys is not a function` in parallel suite runs — pinned
by `EngineConcurrencyTest`). Hot-path cost: zero until any engine polyfills
a prototype (a JVM-wide monotonic `anyUserProps` flag short-circuits the
overlay lookup), one ThreadLocal read per prototype-level lookup after.
The `builtins` install map on each prototype singleton is immutable
post-construction (lazy entries cache inside their `LazyRef` holder rather
than writing back), so concurrent readers never observe structural mutation.

On the constructor side the problem dissolved rather than moved: the
built-in constructors (Array / BigInt / Boolean / Date / Function / Map /
Number / Object / RegExp / Set / String and the eight Error types) are
**per-Engine instances**, not JVM singletons — created lazily by
`ContextRoot.builtinConstructor(name)` and cached in the engine's bindings
like `JsMath` always was. User mutations, tombstones, and freezes on
`Object` / `Array` / etc. ride the ordinary `JsObject` machinery and are
naturally engine-isolated; `JsObject.ENGINE_RESET_LIST` /
`clearEngineState()` are gone. The `constructor` back-references on the
shared prototype singletons (`Array.prototype.constructor === Array`)
resolve per access through a `ConstructorRef` marker against the reading
engine's instance — never cached in the shared `builtins` map. The
`INTRINSIC` bit on the slot's attrs byte is informational.

*Residual shared state (known corners, all narrow).* "Per-Engine isolation"
covers prototype **user props** (overlay), **constructors** (per-Engine
instances), and the **numeric-pollution** flag — the state real scripts
mutate. Three things stay JVM-wide by design: (1) the built-in **method
function objects** themselves (`Array.prototype.push` and friends) are cached
inside their `LazyRef` and shared across engines, so own-property writes onto
*them* (`[].push.foo = 1`) leak/persist cross-engine — rare, and unchanged
from before this work. (2) The **engineless fallback** (`orphanUserProps` /
`orphanNumericPropPolluted`) backs prototype mutations issued with no
`Engine.current()` (i.e. outside any JS execution); it is shared and
unsynchronized, so concurrent host-side prototype writes off the eval path
would race it — hosts virtually never do this. (3) `Engine.current()` is
eval-scoped, so a host reading a polyfilled prototype member off a *returned*
`JsObject`/`JsArray` **after** `eval` returns sees no overlay (resolves
against `orphanUserProps`, not that engine's) — own-property and `List`/`Map`
interface reads are unaffected, only prototype-polyfill reads post-eval.

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
| `specDelete(O, k)` | `DeletePropertyOrThrow` | `O.removeMember(k, ctx, strict)` (sloppy: silent no-op on non-configurable; strict: TypeError) |
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
   in sloppy mode; the strict-mode TypeError flip is the one remaining
   gap (the `"length"` write is special-cased in `setByName` ahead of the
   strict-aware `putMember`, so `handleLengthAssign` doesn't yet receive
   the `strict` flag — see TEST262.md § Engine — spec alignment).
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
`String.valueOf` or chain walk. The `numericPropPolluted` bit lives on
the Engine, flips on the first `Array.prototype[i] = …` /
`Object.prototype[i] = …` write in the session, and dies with it. Slow
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

**Spec accessor getters live in `Prototype.builtins` — the shared,
immutable, install-time tier.** ECMA-262 declares
`RegExp.prototype.{source, flags, global, ignoreCase, multiline, dotAll,
sticky, unicode}` as accessor descriptors on the prototype, NOT as own
properties of instances — `Object.getOwnPropertyDescriptor(RegExp.prototype,
'source').get` must be a function with `.length === 0` and the proto-self
sentinel branch (`get.call(RegExp.prototype) === "(?:)"`). User-installed
accessors via `Object.defineProperty` go through `defineOwnAccessor`
into the per-Engine user props and shadow these (consistent with the
data-slot shadowing rule); built-in accessors go through `installAccessor`
into `builtins`, the immutable shared tier. The
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
receives `(match, ...captures, offset, string)` per spec, plus a trailing
`groups` object when the pattern has named captures.

**Function replacers honor the `g` flag.** `JsStringPrototype.replace`
forwards `regex.global` into `regexReplace(s, regex, fn, context, global)`,
so `'a1b2'.replace(/\d/g, fn)` invokes `fn` per match; a non-global regex
fires it once. `replaceAll` passes `true` unconditionally.

### `JsRegex.lastIndex` is a writable field

**Spec §22.2.7.1 makes `lastIndex` a writable own data property of the
instance.** Reads route through `resolveOwnIntrinsic` to the `int`
field; writes route through overridden `JsRegex.putMember` — **both
overloads**: JS assignment enters through the 4-arg
`putMember(name, value, ctx, strict)` (the `PropertyAccess.setByName`
seam), host writes through the 2-arg form; both coerce via
`Terms.objectToNumber` and update the field. Overriding only the 2-arg
form is the historical bug shape: `re.lastIndex = 12` landed in the side
`props` map, shadowed the field `exec` consults, and global-flag exec
ignored user-set positions. A global function-replace
(`JsStringPrototype.regexReplace`) resets `lastIndex` to 0 per @@replace.

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

4. **`jsGet()` is inherently lazy** - Called on every property access, so values are computed fresh each time. No need for `JsLazy` pattern.

### Why Both `jsKeys()` and `jsGet()`?

- **`jsGet()`** - Handles individual property access (e.g., `proc.stdOut`)
- **`jsKeys()`** - Enables enumeration for `toMap()`, JSON serialization, and `Object.keys()` in JS

Without `jsKeys()`, the object works for property access but serializes to `{}`.

### Property Presence Detection

For `JsObject`, property presence is detected using `Map.containsKey()` before calling `getMember()`. This allows distinguishing between:
- Property exists with value `null` → returns `null`
- Property doesn't exist → continues up the prototype chain

For `SimpleObject`, there is no `hasMember()` API. When `jsGet()` returns `null`, it's treated as "property not found". This simplifies implementation for Java interop classes that don't need to declare all keys upfront - they only need `jsKeys()` for serialization and `jsGet()` for access. If a property genuinely needs to hold `null`, consider using a sentinel value or implementing the full `JsObject` interface instead.

### Dual: a host object that is both a namespace and directly callable

A `SimpleObject` may **also** implement `JavaCallable` (the native call interface, `call(Context, Object...)`). The single object is then *bimodal* — both directly callable **and** a namespace with members:

```java
static class Match implements SimpleObject, JavaCallable {
    @Override
    public Object call(Context context, Object... args) {   // match(actual, expected) — the default
        return equals(args);
    }
    @Override
    public Object jsGet(String name) {                      // match.contains(...), match.each(...), ...
        return switch (name) {
            case "contains" -> (JavaInvokable) a -> contains(a);
            case "each"     -> (JavaInvokable) a -> each(a);
            default -> null;
        };
    }
    @Override
    public Collection<String> jsKeys() { return List.of("contains", "each"); }
}
```

```javascript
match({ a: 1 }, { a: 1 })            // direct call — the object is JsCallable
match.contains({ a: 1, b: 2 }, { a: 1 })  // member call — the object is ObjectLike
```

No special wiring is needed: the engine resolves a **call** by `instanceof JsCallable` (`Interpreter` call sites) and a **member read** by `instanceof ObjectLike` — two independent checks, so an object satisfying both interfaces works on both paths. This is the right shape for an API with a terse common case plus named variants (e.g. a `match` assertion: `match(a, b)` for equals, `match.contains(a, b)` / `match.within(a, b)` for the rest). Pinned by `EngineTest.testBimodalCallableNamespace`.

---

## Lazy Variables with JsLazy

The engine supports lazy/computed variables via the `JsLazy` marker interface. When a variable's value implements `JsLazy`, it is automatically invoked when accessed:

```java
// In CoreContext.readSlot()
if (v instanceof JsLazy lz) {
    return lz.get();
}
```

`JsLazy` is deliberately distinct from `java.util.function.Supplier`: the latter is a generic functional interface that any callable host object (including JS functions, since `JavaCallable` extends `Supplier<Object>`) can satisfy, which would conflate "compute lazily on read" with "this happens to be callable." `JsLazy` is reserved for the lazy-binding sentinel role.

### Usage

```java
Engine engine = new Engine();

// Static value - evaluated once at put time
engine.put("staticValue", someObject.getValue());

// Lazy value - evaluated each time it's accessed
engine.put("lazyValue", (JsLazy) () -> someObject.getValue());
```

### Use Cases

1. **Deferred computation** - Value is computed only when accessed
2. **Dynamic values** - Value can change between accesses
3. **Reduced per-call overhead** - Set up once, resolve on demand

### Example: Mock Server Request Variables

The mock server uses this pattern to avoid setting request variables on every HTTP request:

```java
// Set up once during initialization
engine.put("requestPath", (JsLazy) () ->
    currentRequest != null ? currentRequest.getPath() : null);
engine.put("requestMethod", (JsLazy) () ->
    currentRequest != null ? currentRequest.getMethod() : null);

// Per request, only update the reference
this.currentRequest = incomingRequest;

// When script accesses requestPath, JsLazy.get() is called automatically
// * def path = requestPath  →  invokes the lazy resolver
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

Root bindings also support `JsLazy` for lazy/dynamic values:

```java
String[] suiteDriver = { null };

engine.putRootBinding("driver", (JsLazy) () -> suiteDriver[0]);

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

karate-js implements **runtime** strict mode. The default (no directive) is
sloppy and stays lenient — that is the documented engine policy for
LLM/hand-written glue. A `"use strict"` / `'use strict'` directive activates
the spec's strict *runtime* semantics for the program or function it heads.

**Strictness is lexical and resolved at function-object creation.** A
function is strict iff it carries its own `"use strict"` Directive Prologue
**or** it was defined inside already-strict code. `Interpreter.hasUseStrictDirective`
scans the prologue (a leading run of single-string-literal ExpressionStatements;
exact source `"use strict"`, no escapes per ES 11.2.1). The result is cached
once on `JsFunctionNode.strict` (and on the script context in `evalProgram`),
then copied onto `CoreContext.strict` for each call frame. Block / loop /
catch sub-scopes share the same `CoreContext` (via `enterScope`, not a fresh
context), so they inherit strictness for free. **Built-in call frames are left
non-strict**, so the engine's internal `[[Set]]`s stay lenient regardless of
the caller.

The flips that `CoreContext.strict` drives (all emit JS-shaped
`ReferenceError` / `TypeError`, never Java leaks):

| Sloppy behavior | Strict behavior | Site |
|---|---|---|
| Assign to undeclared name → implicit global | `ReferenceError` | `CoreContext.update` |
| `this` in a plain `f()` call → globalThis | `undefined` | `Interpreter.bindThisForCall` (+ `Function.prototype.{call,apply}`) |
| Write to frozen / non-writable data prop → silent | `TypeError` | `JsObject` / `JsArray` / `JsGlobalThis` `putMember(name,value,ctx,strict)` |
| Write to a get-only accessor → silent | `TypeError` | `AccessorSlot.write(...,strict)` |
| Add prop to non-extensible object → silent | `TypeError` | `putMember(...,strict)` |
| `delete` a non-configurable prop → `false` | `TypeError` | `removeMember(name,ctx,strict)` |

The strict flag is threaded by `PropertyAccess.{setByName,deleteByKey}`
reading `context.strict`. The lenient and strict paths share one
implementation per object kind — the `if (strict) failX(name)` guard sits
inline where the sloppy path returns silently, so there is no duplicated
rejection logic (`JsObject.fail{ReadOnly,NotExtensible,NotConfigurable}`).

**Parser-side early errors.** The parser tracks lexical strictness
(`JsParser.checkStrictEarlyErrors`) and rejects, as parse-phase SyntaxErrors:
legacy/non-octal-decimal literals (`0755`/`08`), `eval`/`arguments` as an
assign/update target or a bound name (function name / param / var-binding /
pattern), duplicate simple params, and the full BoundNames walk over binding
patterns (`flags: [onlyStrict]` tests run with a prepended strict directive and
are no longer skipped). A separate **mode-independent** walk
(`validateEarlyErrors`) rejects assignment-target / optional-chain misuse and
**function declarations in single-statement position**: a `FunctionDeclaration`
is a StatementListItem, never a Statement, so it may not be the sole body of a
loop (`for`/`while`/`do-while`, always — no Annex B carve-out) or — only in
strict mode (Annex B.3.4 allows it sloppily) — an `if`/`else` clause. Detection
keys on the body `STATEMENT` directly wrapping an `FN_EXPR`; a braced body
(`BLOCK`) is legal. Labelled-function declarations are not yet covered (no
LABELLED node type). Still deferred: the `with` statement early error (lexes as a
call; path-skipped). Pinned by `SpecPinTest.functionDeclAs*` and the
`strict_*`/`*EvalOrArguments*` family. See
[TEST262.md § Engine — feature gaps](../karate-js-test262/TEST262.md#engine--feature-gaps).

> **Spec invariant.** The sloppy default is intentional and load-bearing —
> `SpecPinTest.lenient_*` pins it. Strict flips are pinned by
> `SpecPinTest.strict_*`, which assert via in-engine `try/catch` so the error
> name *and* the routing are part of the invariant. Don't make a lenient site
> throw unconditionally; gate it on `CoreContext.strict`.

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
| Async runtime (await, pump, adoption, timers) | `karate-js/src/main/java/io/karatelabs/js/AsyncSupport.java` |
| Async scope facade / tokens / jobs | `karate-js/src/main/java/io/karatelabs/js/AsyncScope.java`, `AsyncToken.java`, `AsyncJob.java` |
| Async activation (one `async` call) | `karate-js/src/main/java/io/karatelabs/js/AsyncActivation.java` |
| Promise | `karate-js/src/main/java/io/karatelabs/js/JsPromise.java`, `JsPromisePrototype.java`, `JsPromiseConstructor.java` |
| Async host exceptions | `karate-js/src/main/java/io/karatelabs/js/JsRejectionException.java`, `EngineTimeoutException.java` |
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

> For a **cross-engine** comparison — `karate-js` vs. Mozilla Rhino and GraalJS,
> each measured at its defaults and tuned — see
> [ptrthomas/karate-js-benchmark](https://github.com/ptrthomas/karate-js-benchmark).
> The section below is version-over-version tracking of this engine only.
>
> **The active performance workstream lives in
> [JS_PERF_PLAN.md](./JS_PERF_PLAN.md)** — profiling-derived mechanism
> attribution, what has shipped with measured deltas, the ranked remaining
> items, and the per-change measurement protocol. The quantitative
> karate-vs-rhino-best tracking (the R lane) is in
> [PROFILING.md §9](./PROFILING.md#9-the-steering-surface).

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

### Current baseline (profile mode, 30 s averages)

| | Array 20 KB eval | Object 20 KB eval | Iterations/30 s |
|---|---|---|---|
| pre token memoization | 1.34 ms | 0.58 ms | 15,531 |
| **current** | **1.20 ms** | **0.59 ms** | **16,733** |

Token memoization — caching a token's extracted text and, for literals, its
parsed value, in `TokenBuffer` — moved the array workload about 10%. The
object workload is flat, which is expected: it spends its time in
`Object.keys`/`values`/`entries`, not in loops over literals.

Treat that 10% as **indicative, not established**: it is one pre/post pair, and
it sits right at the edge of the ±5–10% band this same section warns about.
Taking it as settled would be the exact error the note below describes. The
change was measured properly elsewhere, on a harness that reports allocation
per operation and confidence intervals; this table only has to agree with that
conclusion, not carry it.

Engine instantiation is essentially unchanged (~0.4–0.6 µs median).

**This table is a baseline, not a changelog.** Earlier revisions accumulated a
row per commit and derived cumulative speedup multipliers from them. That was
never sound: the same section documents ±5–10% run-to-run variance, so a table
of ±5% steps cannot support a claim about any individual one. Replace the
baseline when it moves materially; do not append.

> **Note on absolute numbers.** The values above are the M1 Pro baseline.
> Other hardware will see different absolute numbers — the 2x ratios you may
> see locally are normal. What matters for session-to-session comparison is
> the **relative delta** on the same machine across pre/post commits.
> Re-baseline locally when starting a session before judging a refactor's
> perf impact.

### What this benchmark is for

A **gut-check**, and it is good at that: it lives in this repo, runs against
the working tree with no install step, and answers "did I just wreck
something" in seconds — which is exactly what you want after a test262 or
refactor session.

It is not a measurement instrument. It reports wall-clock medians only, with
no allocation figures, no confidence intervals and no cross-engine reference,
and its own ±5–10% run-to-run band swallows any change smaller than that. A
change too small to show up here has not been shown to be free; it has only
failed to be measured. Take anything finer-grained to a harness built for it.

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
- Singleton prototypes shared across Engine instances (user props are
  per-Engine overlays — see [Spec Invariants § Prototype machinery](#prototype-machinery))

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

**JsonParser confidence — run JSONTestSuite**
- `JsonParser` (replaced json-smart) is currently validated by
  `JsonParserTest` (~50 inputs) and the test262 `JSON/parse` slice
  (51 pass / 16 fail; the 16 are reviver/engine-level, not parser-shape).
- test262 is an ECMAScript-interface oracle, not a JSON-format oracle.
  The canonical JSON-format conformance corpus is Nicolas Seriot's
  [JSONTestSuite](https://github.com/nst/JSONTestSuite) (~318 hand-curated
  inputs: `y_*` definitely-valid, `n_*` definitely-invalid, `i_*`
  implementation-defined). It surfaces parser disagreements test262
  doesn't reach: BOM handling, numbers at precision boundaries,
  pathological whitespace, ambiguous Unicode.
- Action: add a `JsonTestSuiteRunner` test that walks the three
  directories and asserts per-file (y_=accept, n_=reject, i_=record-
  disposition). Pin scores against the previous json-smart behavior to
  flag any drift. ~30 min of work; closes the "handles JSON in the wild"
  confidence gap. Priority: low (parser is already production-shape; this
  is calibration, not bug-hunting).

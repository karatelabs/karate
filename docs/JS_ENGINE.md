# JavaScript Engine Reference

This document describes the JavaScript engine architecture, type system, and Java interop patterns for karate-js.

> See also: [ROADMAP.md](./ROADMAP.md#javascript-engine-karate-js) for pending work | [karate-js README](../karate-js/README.md)

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

    default Object getJsValue() {       // For internal operations (e.g., JsDate → Long millis)
        return getJavaValue();
    }
}

// Sub-hierarchies (all sealed)
sealed interface JsPrimitive extends JsValue permits JsNumber, JsString, JsBoolean {}
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
| Date | JsDate | Date | JsDateValue → JsValue |
| RegExp | JsRegex | Pattern | - |
| Array | JsArray | List | **List\<Object\>** |
| Object | JsObject | Map | **Map\<String, Object\>** |
| Uint8Array | JsUint8Array | byte[] | JsBinaryValue → JsValue |

### Prototype System Architecture

The engine uses singleton prototype objects for method inheritance, matching JavaScript's prototype chain:

```
Singleton Prototypes (shared across all instances):
    JsObjectPrototype.INSTANCE  ← null (root of chain)
    JsArrayPrototype.INSTANCE   ← JsObjectPrototype.INSTANCE
    JsStringPrototype.INSTANCE  ← JsObjectPrototype.INSTANCE
    JsNumberPrototype.INSTANCE  ← JsObjectPrototype.INSTANCE
    JsDatePrototype.INSTANCE    ← JsObjectPrototype.INSTANCE
    JsFunctionPrototype.INSTANCE← JsObjectPrototype.INSTANCE
    JsRegexPrototype.INSTANCE   ← JsObjectPrototype.INSTANCE
    (JsError uses JsObjectPrototype directly)

Constructor Functions (for static methods like Array.isArray):
    JsObjectConstructor.INSTANCE  → prototype: JsObjectPrototype.INSTANCE
    JsArrayConstructor.INSTANCE   → prototype: JsArrayPrototype.INSTANCE
    JsStringConstructor.INSTANCE  → prototype: JsStringPrototype.INSTANCE
    JsNumberConstructor.INSTANCE  → prototype: JsNumberPrototype.INSTANCE
    JsDateConstructor.INSTANCE    → prototype: JsDatePrototype.INSTANCE
```

**Built-in prototypes are immutable.** Unlike standard JavaScript, the engine does not allow modifying built-in prototypes:

```javascript
Array.prototype.customMethod = function() {};  // TypeError: Cannot add property to immutable built-in prototype
String.prototype.foo = "bar";                  // TypeError
```

This design decision provides:
- **Thread safety** - Singleton prototypes can be safely shared across Engine instances
- **Security** - Prevents prototype pollution attacks
- **Simplicity** - No need for per-engine prototype copies

User-created objects, arrays, and functions remain fully mutable:
```javascript
var obj = {}; obj.foo = "bar";           // OK
var arr = []; arr.customProp = 123;      // OK
function f() {}; f.meta = "data";        // OK
```

**Property lookup order** (implemented in `Prototype.getMember()`):
1. Built-in properties (`getBuiltinProperty()`)
2. Delegate to `__proto__` chain

```java
// Base class for built-in prototype objects (immutable singletons)
abstract class Prototype implements ObjectLike {
    private final Prototype __proto__;

    public Object getMember(String name) {
        Object builtin = getBuiltinProperty(name);
        if (builtin != null) return builtin;
        return __proto__ != null ? __proto__.getMember(name) : null;
    }

    public void putMember(String name, Object value) {
        throw new RuntimeException("TypeError: Cannot add property to immutable built-in prototype");
    }

    protected abstract Object getBuiltinProperty(String name);
}

// Example: JsArrayPrototype provides array methods (package-private singleton)
class JsArrayPrototype extends Prototype {
    static final JsArrayPrototype INSTANCE = new JsArrayPrototype();

    private JsArrayPrototype() {
        super(JsObjectPrototype.INSTANCE);  // Arrays inherit from Object
    }

    @Override
    protected Object getBuiltinProperty(String name) {
        return switch (name) {
            case "map" -> (JsCallable) this::map;
            case "filter" -> (JsCallable) this::filter;
            case "push" -> (JsCallable) this::push;
            // ... other array methods
            default -> null;  // Delegate to __proto__ (JsObjectPrototype)
        };
    }
}
```

**Benefits:**
- Single instance per type (memory efficient)
- Thread-safe sharing across Engine instances
- Clean separation of constructor vs prototype
- Methods inherited via standard prototype chain
- `ObjectLike.getPrototype()` enables uniform chain walking

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
┌─────────────────┐      Java → JS       ┌─────────────┐
│  java.util.Date │ ──────────────────►  │             │
│  Instant        │ ──────────────────►  │   JsDate    │
│  LocalDateTime  │ ──────────────────►  │  (internal  │
│  LocalDate      │ ──────────────────►  │   millis)   │
│  ZonedDateTime  │ ──────────────────►  │             │
└─────────────────┘                      └──────┬──────┘
                                               │
                        JS → Java              │
                   ◄───────────────────────────┘
                   │
                   ▼
           ┌───────────────┐
           │ java.util.Date │
           └───────────────┘
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
    private ObjectLike __proto__ = JsObjectPrototype.INSTANCE;  // Prototype chain

    // JS internal - raw values, prototype chain
    public Object getMember(String name) {
        // Own property first, then prototype chain
        if (_map != null && _map.containsKey(name)) {
            return _map.get(name);
        }
        return __proto__ != null ? __proto__.getMember(name) : null;
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

Internal representation uses `long millis` with `java.time` for operations:

```java
class JsDate extends JsObject implements JavaMirror {
    private long millis;

    // Getters use ZonedDateTime
    public int getFullYear() {
        return toZonedDateTime().getYear();
    }

    // Setters use java.time for overflow handling
    public void setDate(int day) {
        ZonedDateTime zdt = toZonedDateTime().withDayOfMonth(day);
        this.millis = zdt.toInstant().toEpochMilli();
    }

    @Override
    public Object getJavaValue() { return new Date(millis); }

    @Override
    public Object getInternalValue() { return millis; }  // For numeric operations
}
```

**Benefits:**
- Thread-safe formatting (DateTimeFormatter)
- Modern API (java.time vs Calendar)
- Consistent internal representation

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
sealed interface JsPrimitive extends JsValue permits JsNumber, JsString, JsBoolean {}
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
- Thread-safe immutable prototypes shared across Engine instances

---

### 2. Future TODO Items

**JavaScript Stack Traces for Errors**
- Current error messages are basic with no JS call stack
- Would require tracking function entry/exit in `Interpreter.evalFnCall()`
- Store function name + source location in `CoreContext`, capture on throw
- Priority: High (improves debugging experience)

**Async/Await Support**
- Engine is currently synchronous (async handled at Karate DSL level)
- Current design doesn't preclude async - context-based execution can track promise state
- Priority: Future (when needed)

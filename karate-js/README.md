# karate-js

A lightweight JavaScript engine implemented in Java from scratch, designed to run on the JVM with full support for concurrent thread execution.

> See also: [docs.karatelabs.io](https://docs.karatelabs.io) | [docs/DESIGN.md](../docs/DESIGN.md) | [karate-core](../karate-core)

## Features

- **Thread-safe** - Full support for concurrent execution across threads
- **Minimal** - small JAR footprint; SLF4J and JSON-smart are the only dependencies
- **Java Interop** - bridge Java and JS code; full control, behavior customization, introspection from Java
- **Simple** - concise codebase, plenty of unit tests
- **Fast** - see [benchmarks](https://github.com/ptrthomas/karate-js-benchmark) for numbers

## Architecture

| Package | Description |
|---------|-------------|
| `io.karatelabs.js` | Core JavaScript engine (interpreter, runtime, built-in types) |
| `io.karatelabs.parser` | Parsing infrastructure (lexer, parser, AST nodes) shared with karate-core |
| `io.karatelabs.common` | Shared utilities (file, OS, string operations) |

The engine targets idiomatic ES6 used in real-world JavaScript; some advanced features are intentionally not supported. See [docs/DESIGN.md](../docs/DESIGN.md) for the JS engine reference.

## Usage

```java
import io.karatelabs.js.Engine;

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

## Third-Party Usage

This module can be used independently of karate-core. If you're integrating karate-js into your own project:

```xml
<dependency>
    <groupId>io.karatelabs</groupId>
    <artifactId>karate-js</artifactId>
    <version>2.0.0.RC1</version>
</dependency>
```

Contributions that improve decoupling for third-party usage are welcome.

## License

[MIT License](../LICENSE)

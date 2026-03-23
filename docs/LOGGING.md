# Karate v2 Logging

This document describes the logging architecture in Karate v2.

> See also: [RUNTIME.md](./RUNTIME.md) | [CLI.md](./CLI.md)

---

## Overview

Karate v2 uses a unified logging architecture based on SLF4J with category-based filtering:

| Category | Purpose | Usage |
|----------|---------|-------|
| `karate.runtime` | Core framework logs | Suite, Runner, StepExecutor, reports, config |
| `karate.http` | HTTP request/response logs | Request/response bodies, headers |
| `karate.mock` | Mock server logs | MockServer, MockHandler |
| `karate.scenario` | Test logs | `print`, `karate.log()`, `karate.logger.*` |
| `karate.console` | Console output | Test summary (TRACE level to avoid duplication) |

**Hierarchy benefit:** `<logger name="karate" level="DEBUG"/>` controls all subcategories.

```
┌─────────────────────────────────────────────────────────────┐
│  Test Execution                                              │
│                                                              │
│  print "hello"  ──┐                                          │
│  karate.log()   ──┼──► LogContext.with(SCENARIO_LOGGER)      │
│                   │           │                              │
│                   │           ├──► Report Buffer (HTML)      │
│                   │           └──► SLF4J (karate.scenario)   │
│                   │                                          │
│  HTTP logs      ──┴──► LogContext.with(HTTP_LOGGER)          │
│                               │                              │
│                               ├──► Report Buffer (HTML)      │
│                               └──► SLF4J (karate.http)       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Framework Infrastructure                                    │
│                                                              │
│  logger.info() ──► SLF4J (karate.runtime)                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Console Output                                              │
│                                                              │
│  Console.println() ──► System.out (colored)                 │
│                    └──► SLF4J TRACE (karate.console)        │
└─────────────────────────────────────────────────────────────┘
```

---

## Test Logs (LogContext)

`LogContext` is a thread-local log collector that captures all test output:

- `print` statements
- `karate.log()` calls
- HTTP request/response logs
- Match failures and assertions

### Basic Usage

```gherkin
Scenario: Logging example
  * print 'Starting test'
  * karate.log('Debug info:', someVar)
  * url 'https://api.example.com'
  * method get
  # All output captured in LogContext
```

### karate.logger API

Use `karate.logger` for level-aware logging in your tests:

```gherkin
Scenario: Level-aware logging
  * karate.logger.debug('Detailed debug info:', data)
  * karate.logger.info('Processing item:', itemId)
  * karate.logger.warn('Rate limit approaching')
  * karate.logger.error('Failed to connect:', error)
```

The `karate.log()` method is equivalent to `karate.logger.info()`.

### Log Level Filtering

Control which log levels appear in reports:

**In karate-config.js:**
```javascript
function fn() {
  karate.configure('report', { logLevel: 'warn' });  // Only WARN and ERROR
  return { };
}
```

**CLI:**
```bash
karate run --report-log-level debug features/   # Include DEBUG and above in reports
karate run --report-log-level warn features/    # Only WARN and ERROR in reports
```

**Runner API:**
```java
Runner.path("features/")
    .logLevel("info")     // INFO, WARN, ERROR
    .parallel(5);
```

**karate-pom.json:**
```json
{
  "paths": ["features/"],
  "output": {
    "logLevel": "debug"
  }
}
```

| Level | Shows |
|-------|-------|
| `trace` | All logs |
| `debug` | DEBUG, INFO, WARN, ERROR |
| `info` | INFO, WARN, ERROR (default) |
| `warn` | WARN, ERROR only |
| `error` | ERROR only |

### Accessing Logs Programmatically

```java
LogContext ctx = LogContext.get();
ctx.log("Custom message");
ctx.log("Value: {}", someValue);  // SLF4J-style formatting

String allLogs = ctx.collect();  // Get captured logs
```

### LogContext.with(Logger) API

For category-aware logging that both captures to reports AND cascades to SLF4J:

```java
import io.karatelabs.output.LogContext;

// Use predefined category loggers
private static final LogContext.LogWriter log = LogContext.with(LogContext.HTTP_LOGGER);

// Log at various levels - writes to both report buffer and SLF4J
log.info("Request: {}", request);
log.debug("Headers: {}", headers);
log.warn("Rate limit exceeded");
```

Built-in category loggers:
- `LogContext.RUNTIME_LOGGER` - `karate.runtime`
- `LogContext.HTTP_LOGGER` - `karate.http`
- `LogContext.MOCK_LOGGER` - `karate.mock`
- `LogContext.SCENARIO_LOGGER` - `karate.scenario`
- `LogContext.CONSOLE_LOGGER` - `karate.console`

### Embedding Content

`LogContext` also collects embedded content (HTML, images, etc.) for inclusion in reports:

```java
LogContext ctx = LogContext.get();

// Embed HTML content
ctx.embed(htmlBytes, "text/html");

// Embed with optional name
ctx.embed(imageBytes, "image/png", "screenshot.png");

// Collect embeds (typically done by StepExecutor)
List<StepResult.Embed> embeds = ctx.collectEmbeds();
```

The `doc` keyword uses this system to embed rendered templates:

```gherkin
* doc 'report.html'  # Rendered HTML is embedded in step result
```

Embeds appear in HTML reports and are included in the JSON output as Base64-encoded data.

---

## Framework Logs

Framework code uses standard SLF4J logging with the `karate.runtime` category:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

logger.info("Starting suite");
logger.debug("Loading config from {}", path);
logger.warn("Config not found: {}", path);
logger.error("Execution failed", exception);
```

Or use the constant from LogContext:

```java
private static final Logger logger = LogContext.RUNTIME_LOGGER;
```

### Configuring Framework Log Levels

Use standard SLF4J/logback configuration:

```xml
<!-- logback.xml -->
<configuration>
    <!-- Control all karate logs -->
    <logger name="karate" level="INFO"/>

    <!-- Or fine-grained control -->
    <logger name="karate.runtime" level="INFO"/>
    <logger name="karate.http" level="WARN"/>
    <logger name="karate.mock" level="DEBUG"/>
    <logger name="karate.scenario" level="DEBUG"/>
</configuration>
```

---

## CLI Flags

| Flag | Purpose | Controls |
|------|---------|----------|
| `--report-log-level <level>` | Report filtering | What goes in HTML reports |
| `--runtime-log-level <level>` | Console/JVM verbosity | SLF4J logger level (karate.*) |

**Values:** `trace`, `debug`, `info` (default), `warn`, `error`

**Examples:**
```bash
karate run --report-log-level debug features/     # Capture DEBUG+ in reports
karate run --report-log-level warn features/      # Only WARN+ in reports
karate run --runtime-log-level debug features/    # Show DEBUG+ in console
```

---

## Log Masking

Mask sensitive data in logs to prevent credential leaks.

### CLI

```bash
# Single preset
karate run --log-mask PASSWORDS features/

# Multiple presets
karate run --log-mask PASSWORDS,CREDIT_CARDS features/

# All sensitive data
karate run --log-mask ALL_SENSITIVE features/
```

### karate-pom.json

```json
{
  "paths": ["features/"],
  "logMask": {
    "presets": ["PASSWORDS", "CREDIT_CARDS"],
    "patterns": [
      {"regex": "secret[=:]\\s*([^\\s]+)", "replacement": "***"}
    ],
    "headers": ["Authorization", "X-Api-Key"]
  }
}
```

### Runner.Builder API

```java
import io.karatelabs.output.LogMask;
import io.karatelabs.output.LogMaskPreset;

Runner.path("features/")
    .logMask(LogMask.builder()
        .preset(LogMaskPreset.PASSWORDS)
        .preset(LogMaskPreset.CREDIT_CARDS)
        .pattern("secret[=:]\\s*([^\\s]+)", "***")
        .headerMask("Authorization", "***")
        .headerMask("X-Api-Key", "***")
        .build())
    .parallel(5);
```

### Built-in Presets

| Preset | Description |
|--------|-------------|
| `PASSWORDS` | password, passwd, pwd, secret fields |
| `CREDIT_CARDS` | 16-digit numbers (keeps last 4) |
| `SSN` | Social security numbers (xxx-xx-xxxx) |
| `EMAILS` | Masks local part of email addresses |
| `API_KEYS` | Authorization, X-Api-Key headers |
| `BEARER_TOKENS` | Bearer token values |
| `BASIC_AUTH` | Basic auth credentials |
| `ALL_SENSITIVE` | Combines all above presets |

---

## Console Output

### Summary Output

Control console summary after test execution:

```java
Runner.path("features/")
    .outputConsoleSummary(true)   // default: print summary
    .parallel(5);
```

### ANSI Colors

Console output uses ANSI colors for readability:

| Element | Color |
|---------|-------|
| Pass | Green |
| Fail | Red |
| Skip | Yellow |
| Info | Cyan |
| Headers | Bold |

Disable colors:
```bash
karate run --no-color features/
```

Or programmatically:
```java
Console.setColorsEnabled(false);
```

### Console to Logger

Console output is also sent to `karate.console` at TRACE level (with ANSI codes stripped). This allows capturing console output in logs without duplication:

```xml
<!-- To capture console output in logs -->
<logger name="karate.console" level="TRACE"/>
```

---

## Configuration Priority

For log masking and other settings:

**CLI Entry Point:**
```
CLI flags > KARATE_OPTIONS > karate-pom.json > system props > defaults
```

**Java API Entry Point:**
```
Runner.Builder API > system props > defaults
```

---

## Migration from V1

### HttpLogModifier

V1 used a Java interface for HTTP log modification:

```java
// V1 (deprecated)
public interface HttpLogModifier {
    boolean enableForUri(String uri);
    String header(String header, String value);
    String request(String uri, String request);
    String response(String uri, String response);
}
```

V2 replaces this with declarative `LogMask`:

```java
// V2
Runner.path("features/")
    .logMask(LogMask.builder()
        .pattern("token=([^&]+)", "token=***")
        .headerMask("Authorization")
        .build())
    .parallel(5);
```

---

## SLF4J Binding

Karate uses SLF4J for logging. The SLF4J binding (Logback, Log4j2, etc.) you need depends on how you use Karate:

| Usage | SLF4J Binding |
|-------|---------------|
| **CLI / Fatjar** | Logback bundled automatically |
| **Maven/Gradle dependency** | You must provide your own binding |

### Library Usage (Maven/Gradle)

When using `karate-core` as a dependency, Logback is declared with `provided` scope and is **not** included transitively. You must add an SLF4J binding to your project:

**Spring Boot:** Already includes Logback - works automatically.

**Quarkus:** Uses JBoss Logging - works automatically.

**Plain Java project:** Add a binding explicitly:
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.22</version>
    <scope>runtime</scope>
</dependency>
```

Or use Log4j2:
```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
    <version>2.24.3</version>
    <scope>runtime</scope>
</dependency>
```

Without an SLF4J binding, you'll see: `SLF4J: No SLF4J providers were found.`

---

## Implementation Classes

| Class | Purpose |
|-------|---------|
| `io.karatelabs.output.LogContext` | Thread-local test log collector with category support |
| `io.karatelabs.output.LogContext.LogWriter` | Category-aware logger that writes to buffer + SLF4J |
| `io.karatelabs.output.LogLevel` | Log level enum |
| `io.karatelabs.output.LogMask` | Log masking (future) |
| `io.karatelabs.output.LogMaskPreset` | Built-in masking patterns (future) |

---

## TODO: JS Line-Level Logging

> **Status:** Not yet implemented

Capture JS line-of-code execution for reports, similar to how Gherkin steps are displayed.

**Goal:** When executing `.karate.js` scripts or embedded JS, show each line execution in the report:
```
1: var proc = karate.fork({ args: ['node', 'server.js'] })
2: proc.waitForPort('localhost', 8080, 30, 250)
3: var response = http.get()
```

**Implementation approach:**
- JS engine instrumentation to emit line execution events
- Opt-in via `configure report = { showJsLineNumbers: true }`
- Integrate with `LogContext` for report capture
- Performance overhead acceptable since opt-in

**Related:**
- [REPORTS.md](./REPORTS.md) - Configure report
- [RUNTIME.md](./RUNTIME.md) - Priority 7 (JS Script Execution)

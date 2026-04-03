# Karate v1 to v2 Migration Guide

## What's New in Karate v2

Karate v2 is a **complete ground-up rewrite** with significant improvements across the board. Here are the highlights:

### Performance & Scalability

| Improvement | Description                                                                                              | Commit |
|-------------|----------------------------------------------------------------------------------------------------------|--------|
| **Embedded JS Engine** | Fast hand-rolled lexer/parser with ES6+ support, focused on the Java interop use-case, [see benchmark](https://github.com/ptrthomas/karate-js-benchmark) | [90d6e07](https://github.com/karatelabs/karate/commit/90d6e07) |
| **Virtual Threads** | Java 21+ unlocks massive parallelism with minimal overhead                                               | - |
| **@lock Tag** | Scenario-level mutual exclusion for parallel safety (`@lock=name`)                                       | [a08337b](https://github.com/karatelabs/karate/commit/a08337b) |
| **@lock=\*** | Exclusive execution - scenario runs alone                                                                | [cd94b11](https://github.com/karatelabs/karate/commit/cd94b11) |

### Assertions

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **match within** | Frequently requested - assert that a value falls within a range | [8535be0](https://github.com/karatelabs/karate/commit/8535be0) |
| **karate.faker.\*** | Built-in test data generation: `firstName()`, `email()`, `randomInt()`, etc. | [245c540](https://github.com/karatelabs/karate/commit/245c540) |
| **karate.expect()** | Chai-style BDD assertions - easier migration from Postman! | [ad2f475](https://github.com/karatelabs/karate/commit/ad2f475) |
| **karate.uuid()** | Generate random UUIDs | [cb516d4](https://github.com/karatelabs/karate/commit/cb516d4) |

### Modern HTTP Client

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **Apache HttpClient 5.6** | Modern HTTP client with Brotli compression support | [1a35bcd](https://github.com/karatelabs/karate/commit/1a35bcd) |
| **Declarative Auth** | `configure auth` for Basic, Bearer, and OAuth2 with automatic token refresh | [1a06c64](https://github.com/karatelabs/karate/commit/1a06c64) |

### HTTP Mocks

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **Mock Server Rewrite** | New JS engine and rewritten from scratch for performance - see [MOCKS.md](./MOCKS.md) | [d84c0e4](https://github.com/karatelabs/karate/commit/d84c0e4) |

### Performance Testing

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **Gatling 3.14** | Re-implemented load testing with pure Java architecture | [32f8b00](https://github.com/karatelabs/karate/commit/32f8b00) |

### Browser Automation

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **CDP Driver Rewrite** | Complete reimplementation using Chrome DevTools Protocol directly | [68111e5](https://github.com/karatelabs/karate/commit/68111e5) |
| **PooledDriverProvider** | Automatic browser pooling for parallel UI automation | [b140436](https://github.com/karatelabs/karate/commit/b140436) |
| **Auto-wait** | Automatic waiting before element operations reduces flaky tests | [67e4c2d](https://github.com/karatelabs/karate/commit/67e4c2d) |

### Developer Experience

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **Unified Event System** | Single `RunListener` API for observing and controlling test execution - see [EVENTS.md](./EVENTS.md) | [f4240a2](https://github.com/karatelabs/karate/commit/f4240a2) |
| **JSONL Streaming** | Memory-efficient `karate-results.jsonl` format with real-time progress | [f4240a2](https://github.com/karatelabs/karate/commit/f4240a2) |
| **Modern HTML Reports** | Bootstrap 5.3 with dark mode, interactive tag filtering | [3b965b6](https://github.com/karatelabs/karate/commit/3b965b6) |
| **JUnit 6 Integration** | Streaming dynamic test generation via `@TestFactory` | [a794b02](https://github.com/karatelabs/karate/commit/a794b02) |

### V1 Compatibility

| Improvement | Description | Commit |
|-------------|-------------|--------|
| **Compatibility Shims** | `com.intuit.karate` package delegates to v2 | [fefb91f](https://github.com/karatelabs/karate/commit/fefb91f) |
| **Drop-in Migration** | Most v1 code works with just dependency update | - |

### More
* ANSI colors in console, works even outside IDE
* HTML report with tags filtering
* Soft assertions
* JSON validation works in "soft assertion mode by default"
* Debugging of JavaScript is possible now
* Large JSON operations such as "contains" will use disk when needed to avoid out-of-memory issues
---

#### Configure Auth Details

Centralized authentication configuration supporting multiple auth types:

```javascript
// Basic Auth
configure auth = { type: 'basic', username: 'user', password: 'pass' }

// Bearer Token
configure auth = { type: 'bearer', token: 'your-token' }

// OAuth2 Client Credentials
configure auth = { type: 'oauth2', tokenUrl: 'https://auth.example.com/token', clientId: '...', clientSecret: '...' }

// OAuth2 Authorization Code (PKCE)
configure auth = { type: 'oauth2', flow: 'authorization_code', authUrl: '...', tokenUrl: '...', clientId: '...' }
```

- Automatic token refresh for OAuth2
- Auth configuration inherited by called features

#### karate.expect() - Chai-style Assertions

If you're migrating from Postman or other frameworks using Chai-style JavaScript syntax, `karate.expect()` provides a familiar API:

```javascript
karate.expect(response.name).to.equal('John')
karate.expect(response.age).to.be.above(18)
karate.expect(response).to.have.property('email')
karate.expect(response.tags).to.include('active')
karate.expect(response.status).to.not.equal('deleted')
```

---

## Overview

Karate v2 includes **backward compatibility shims** that allow most v1 code to work with minimal changes. For most users, the only change required is updating the Maven dependency.

## Quick Start

### Step 1: Update Maven Dependencies

```xml
<!-- v1 -->
<dependency>
    <groupId>io.karatelabs</groupId>
    <artifactId>karate-junit5</artifactId>
    <version>1.5.2</version>
    <scope>test</scope>
</dependency>

<!-- v2 -->
<dependency>
    <groupId>io.karatelabs</groupId>
    <artifactId>karate-junit6</artifactId>
    <version>2.0.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>
```

> **Note:** Unlike `karate-junit5` which bundled JUnit, `karate-junit6` declares JUnit as a `provided` dependency, giving you control over the JUnit version. You must add `junit-jupiter` explicitly.

> **Reference:** See the [karate-demo migration commit](https://github.com/karatelabs/karate/commit/c8fca97ce) for a complete example of dependency changes.

### Step 2: Update Java Version

Karate v2 requires **Java 21+** for virtual threads support.

```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

That's it for most projects. Run your tests and they should work.

---

## V1 Compatibility Shims

The following v1 APIs work without code changes via deprecated shims:

| v1 Class | Status |
|----------|--------|
| `com.intuit.karate.Runner` | Works - delegates to v2 |
| `com.intuit.karate.Results` | Works - wraps v2 SuiteResult |
| `com.intuit.karate.core.MockServer` | Works - delegates to v2 |
| `com.intuit.karate.junit5.Karate` | Works - delegates to v2 |

### Example: Runner API (no changes needed)

```java
// This v1 code works in v2 without modification
import com.intuit.karate.Results;
import com.intuit.karate.Runner;

Results results = Runner.path("classpath:features")
    .tags("~@ignore")
    .parallel(5);
assertTrue(results.getFailCount() == 0, results.getErrorMessages());
```

### Example: MockServer API (no changes needed)

```java
// This v1 code works in v2 without modification
import com.intuit.karate.core.MockServer;

MockServer server = MockServer
    .feature("classpath:mock.feature")
    .arg("key", "value")
    .http(0).build();
```

## Deprecated Configure Options

These configure options now produce a warning and have no effect:

- `logPrettyRequest` / `logPrettyResponse`
- `printEnabled`
- `lowerCaseResponseHeaders`
- `logModifier`

Your tests will still pass - these are just no-ops now.

---

## Feature File Compatibility

Most feature files work unchanged. The only known difference:

- **Cookie domain assertions**: If testing cookie domains, note that RFC 6265 compliance means leading dots are stripped (`.example.com` → `example.com`)

---

## Browser Automation (UI Tests)

V2 uses a rewritten driver with CDP (Chrome DevTools Protocol) as the primary backend and full W3C WebDriver support for cross-browser testing.

### Driver Configuration

```javascript
// karate-config.js - minimal config
function fn() {
  karate.configure('driver', { type: 'chrome', headless: false });
  return { serverUrl: 'http://localhost:8080' };
}
```

**Key differences from v1:**
- `showDriverLog` has no effect (TODO)
- W3C WebDriver types are fully supported: `chromedriver`, `geckodriver`, `safaridriver`, `msedgedriver`
- `submit()` has been ported from v1 and works on all backends

### Driver Types

| Type | Backend | Description |
|------|---------|-------------|
| `chrome` | CDP | Chrome/Chromium/Edge via DevTools Protocol (recommended for development) |
| `chromedriver` | W3C | Chrome via chromedriver |
| `geckodriver` | W3C | Firefox via geckodriver |
| `safaridriver` | W3C | Safari (macOS only) |
| `msedgedriver` | W3C | Microsoft Edge |

### Gherkin Syntax (unchanged)

All v1 driver keywords work the same way:

```gherkin
* driver serverUrl + '/login'
* input('#username', 'admin')
* click('button[type=submit]')
* waitFor('#dashboard')
* match driver.title == 'Welcome'
```

### Driver in Called Features (V1-Compatible)

V2 preserves V1 behavior for shared-scope calls (`* call read('feature')`): if a called feature creates a driver, it automatically propagates back to the caller. No special configuration is needed.

```gherkin
# login.feature — driver propagates to caller automatically
@ignore
Feature: Login

Background:
  * configure driver = { type: 'chrome' }

Scenario: Login
  * driver serverUrl + '/login'
  * input('#username', 'admin')
  * input('#password', 'secret')
  * click('#submit')
  * waitFor('#dashboard')
```

```gherkin
# main.feature — driver is available after call returns
Scenario: Full regression
  * call read('classpath:pages/login.feature')
  * delay(5000)                                    # ✅ works — driver propagated from login
  * call read('classpath:pages/dashboard.feature') # ✅ works — driver is shared
```

> **Note:** Early v2 releases required `scope: 'caller'` in the driver config. This is no longer needed and can be safely removed.

### Driver-Bound Functions

These functions are only available **after** `driver 'url'` has been called (i.e., a browser session is active):

`click()`, `input()`, `clear()`, `focus()`, `scroll()`, `select()`, `submit()`, `text()`, `html()`, `value()`, `attribute()`, `exists()`, `enabled()`, `position()`, `locate()`, `locateAll()`, `waitFor()`, `waitForText()`, `waitForUrl()`, `waitUntil()`, `screenshot()`, `highlight()`, `delay()`, `script()`, `scriptAll()`, `mouse()`, `keys()`, `switchFrame()`, `switchPage()`, `refresh()`, `back()`, `forward()`

If you see `<function> is not defined`, check that the driver was initialized before that line.

**For a delay without a driver**, use `karate.pause(millis)` instead of `delay(millis)`.

### Browser Pooling (Default Behavior)

V2 automatically pools browser instances using `PooledDriverProvider`. This is the default — no configuration needed:

```java
Runner.path("features/")
    .parallel(4);  // Pool of 4 drivers auto-created
```

Benefits:
- Browser instances are reused across scenarios
- Pool size auto-scales to match parallelism
- Clean state reset between scenarios

See [DRIVER.md](./DRIVER.md) for detailed DriverProvider documentation.

### Migration Checklist for Driver Tests

- [ ] Remove `scope: 'caller'` from driver config if present (no longer needed)
- [ ] Replace `delay(millis)` with `karate.pause(millis)` if used before the driver starts
- [ ] `showDriverLog` has no effect (TODO)
- [ ] W3C WebDriver types (`chromedriver`, `geckodriver`, `safaridriver`) are now fully supported

---

## Migration Checklist

- [ ] Update `karate-junit5` → `karate-junit6` dependency
- [ ] Update Java version to 21+
- [ ] Replace `JsonUtils` with `Json` class (if used)
- [ ] Remove code using `HttpLogModifier`, `WebSocketClient`, or Driver Java APIs (if used)
- [ ] Update cookie domain assertions if needed

---

## Gradual Migration to v2 APIs

Each shim provides a `toV2*()` method if you want to migrate incrementally:

```java
// Get underlying v2 Builder
io.karatelabs.core.Runner.Builder v2Builder = v1Builder.toV2Builder();

// Get underlying v2 MockServer
io.karatelabs.core.MockServer v2Server = v1Server.toV2MockServer();

// Get underlying v2 SuiteResult
io.karatelabs.core.SuiteResult v2Results = v1Results.toSuiteResult();
```

---

## Getting Help

- GitHub Issues: https://github.com/karatelabs/karate/issues
- Documentation: https://karatelabs.io/docs

---

## Appendix: Migration Reference Commits

We've migrated two projects as reference implementations. These commits show all the changes needed:

### karate-demo Migration

**Commit:** [c8fca97ce](https://github.com/karatelabs/karate/commit/c8fca97ce)

This involved additional infrastructure changes beyond what typical end-users need:

- Spring Boot 2.x → 3.x upgrade (required for Java 21)
- `javax.*` → `jakarta.*` servlet imports
- Spring Security 5 → 6 configuration style
- Cookie domain normalization for RFC 6265 compliance

### karate-e2e-tests Migration

**Commit:** [7ffe47509](https://github.com/karatelabs/karate/commit/7ffe47509)

This shows a simpler migration focused on test dependencies and runner changes.

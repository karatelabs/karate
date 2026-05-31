# Karate v1 to v2 Migration Guide

Karate v2 is a complete ground-up rewrite. For the feature overview see [What's New in v2](https://docs.karatelabs.io/getting-started/whats-new-v2/). This guide focuses on the migration mechanics for v1 users.

V2 includes backward compatibility shims that allow most v1 code to work with minimal changes — for most users the only change required is updating the Maven dependency.

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
    <version>2.0.4</version>
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

---

## Native v2 APIs (Recommended)

While the v1 shims work, we recommend migrating to the native v2 APIs to avoid deprecation warnings and take advantage of new features.

### Runner API

```java
// v1 (deprecated shim)
import com.intuit.karate.Results;
import com.intuit.karate.Runner;

Results results = Runner.path("classpath:features")
    .tags("~@ignore")
    .parallel(5);
assertTrue(results.getFailCount() == 0, results.getErrorMessages());

// v2 (native)
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;

SuiteResult result = Runner.path("classpath:features")
    .tags("~@ignore")
    .parallel(5);
assertEquals(0, result.getScenarioFailedCount(), String.join("\n", result.getErrors()));
```

**Key differences:**
- `Results` → `SuiteResult`
- `getFailCount()` → `getScenarioFailedCount()`
- `getErrorMessages()` → `getErrors()` (returns `List<String>`)

### MockServer API

```java
// v1 (deprecated shim)
import com.intuit.karate.core.MockServer;

MockServer server = MockServer
    .feature("classpath:mock.feature")
    .arg("key", "value")
    .http(0).build();
int port = server.getPort();

// v2 (native)
import io.karatelabs.core.MockServer;

MockServer server = MockServer
    .feature("classpath:mock.feature")
    .arg(Map.of("key", "value"))
    .pathPrefix("/api")
    .start();
int port = server.getPort();
server.stopAndWait();  // clean shutdown
```

**Key differences:**
- `.http(0).build()` → `.start()` (port 0 is the default for dynamic port)
- `.arg("key", "value")` → `.arg(Map.of("key", "value"))`
- `.pathPrefix("/api")` strips path prefix from incoming requests before matching
- Use `.stopAndWait()` or `.stopAsync()` for cleanup

### JUnit Runner (Karate)

```java
// v1 (deprecated shim)
import com.intuit.karate.junit5.Karate;

class SampleTest {
    @Karate.Test
    Karate testAll() {
        return Karate.run("sample").relativeTo(getClass());
    }
}

// v2 (native)
import io.karatelabs.junit6.Karate;
import org.junit.jupiter.api.DynamicNode;

class SampleTest {
    @Karate.Test
    Iterable<DynamicNode> testAll() {
        return Karate.run("sample").relativeTo(getClass());
    }
}
```

**Key difference:** Return type changes from `Karate` to `Iterable<DynamicNode>` for JUnit 6 dynamic test support.

### Gatling Performance Testing

```java
// v1
import static com.intuit.karate.gatling.javaapi.KarateDsl.*;

// v2
import static io.karatelabs.gatling.KarateDsl.*;
```

The DSL methods (`karateProtocol`, `karateFeature`, `karateSet`, `uri`) are the same — only the package changes.

**Java 17+ `--add-opens` requirement.** Gatling 4.7+ calls `MethodHandles.privateLookupIn` on `java.lang` internals, which the JVM blocks unless `java.base/java.lang` is opened to unnamed modules. Add this to the `gatling-maven-plugin` configuration:

```xml
<plugin>
  <groupId>io.gatling</groupId>
  <artifactId>gatling-maven-plugin</artifactId>
  <version>4.7.0</version>
  <configuration>
    ...
    <jvmArgs>
      <jvmArg>--add-opens=java.base/java.lang=ALL-UNNAMED</jvmArg>
    </jvmArgs>
  </configuration>
</plugin>
```

Without it the simulation crashes on startup with `IllegalAccessException: module java.base does not open java.lang to unnamed module`.

## Logging

V2 unifies the v1 logging knobs (`logPrettyRequest`, `logPrettyResponse`, `printEnabled`, `lowerCaseResponseHeaders`, `logModifier`) under a single `configure logging` bucket. The shape:

```javascript
configure logging = {
  report:  'debug',         // threshold for report-buffer capture (default DEBUG)
  console: 'info',          // threshold for SLF4J/console output (default INFO)
  pretty:  true,            // pretty-print HTTP JSON bodies (default true)
  mask: {                   // HTTP-only redaction (replaces v1 HttpLogModifier)
    headers:    ['Authorization', 'Cookie'],
    jsonPaths:  ['$.password', '$..token'],
    patterns:   [{ regex: '\\bBearer [A-Za-z0-9._-]+\\b', replacement: 'Bearer ***' }],
    replacement: '***',
    enableForUri: function(uri) { return uri.indexOf('/health') < 0 }
  }
}
```

See [DESIGN.md § Logging](./DESIGN.md#logging) for the full shape and semantics.

### Where do HTTP bodies show up?

Two channels, two thresholds. **The HTML report has full bodies by default — you do not need to set anything to see them.**

| What you want | `report` | `console` | Notes |
|---|---|---|---|
| Bodies in HTML report, quiet console (**default**) | `'debug'` | `'info'` | One-liner per request on stdout, full headers + body in HTML / JSONL / JUnit / Cucumber. |
| Bodies on console too | `'debug'` | `'trace'` | Streams full request + body to stdout. Noisy — use locally for debugging, not CI. |
| Headers on console (no body), bodies in report | `'debug'` | `'debug'` | Useful when you want to see what URL/headers fired without dumping bodies on screen. |
| Quiet report and console | `'warn'` | `'warn'` | HTTP request lines drop out of HTML too. Last resort for sensitive runs — see also `@report=false` below. |

**Why this differs from v1.** v1 wired a single Logback level for everything, and emitted full bodies at DEBUG. v2 splits report-buffer threshold from console threshold, and the console is auto-tiered: `INFO` = one-liner, `DEBUG` = headers, `TRACE` = body. If your v1 muscle memory was "set DEBUG to see bodies in the terminal," in v2 just open `target/karate-reports/karate-summary.html` — the bodies are already there.

### V1 → V2 mapping

| V1                                                      | V2 equivalent                                                                                       |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `configure logPrettyRequest = true/false`               | `configure logging = { pretty: true/false }` — single boolean for both directions                   |
| `configure logPrettyResponse = true/false`              | `configure logging = { pretty: true/false }` — same single boolean                                  |
| `configure printEnabled = false`                        | `configure logging = { report: 'warn' }` — raise threshold to drop INFO `print`/`karate.log` lines  |
| `configure lowerCaseResponseHeaders = true`             | **Dropped.** `match header X-Foo` is already case-insensitive; use `karate.lowerCase(responseHeaders)` for direct map access |
| `configure logModifier = MyImpl`                        | `configure logging = { mask: {...} }` — declarative map, no Java class needed                        |
| Manual `LoggerFactory.getLogger('com.intuit.karate').setLevel(...)` for mid-test silencing | `* configure logging = { console: 'error' }` — auto-restored at scenario end |

The four v1 keys above (`logPrettyRequest`, `logPrettyResponse`, `printEnabled`, `lowerCaseResponseHeaders`) are silent no-ops with deprecation warnings — your tests still run, you just see a one-line WARN per process pointing at the new shape. `logModifier` likewise warns; rewrite the masking declaratively as shown above.

---

## Reports

V2 redesigned the report architecture around `FeatureResult.toJson()` as the single source of truth. The text-based v1 dumps (`karate-summary.json`, per-feature `*.json.txt`) are gone; their replacement is the **JSON Lines event stream** (`karate-json/karate-events.jsonl`), which is the supported structured feed for CI/CD scrapers and IDE integrations.

### V1 → V2 mapping

| V1 artifact | V2 equivalent |
|---|---|
| `target/karate-reports/karate-summary.json` | `SUITE_EXIT.data.summary` line in `karate-events.jsonl` (carries pass/fail counters and total duration) |
| `target/karate-reports/<feature>.karate-json.txt` (per feature) | `FEATURE_EXIT.data` line in `karate-events.jsonl` (the full `FeatureResult.toJson()`) |
| `target/karate-reports/karate-summary.html` | `target/karate-reports/karate-summary.html` (unchanged location, redesigned UI) |
| `cucumber-json/*.json` | `target/karate-reports/cucumber-json/*.json` — **now opt-in** |
| JUnit XML | `target/karate-reports/junit-xml/*.xml` — **now opt-in** |

### What's on by default

Only HTML — `karate-summary.html`, `karate-timeline.html`, and `feature-html/`. Everything else is opt-in:

```java
Runner.path("features/")
    .outputJsonLines(true)      // karate-json/karate-events.jsonl
    .outputCucumberJson(true)   // cucumber-json/*.json
    .outputJunitXml(true)       // junit-xml/*.xml
    .parallel(5);
```

CLI: `-f html,karate:jsonl,cucumber:json,junit:xml` (prefix with `~` to disable, e.g. `-f ~html`).

### `karate-events.jsonl` envelope

One record per line, lifecycle events flushed per-write so the file can be tailed live:

```json
{"type":"SUITE_ENTER","timeStamp":1747555200000,"threadId":null,"data":{"version":"2.0.8","env":"dev","threads":4}}
{"type":"FEATURE_ENTER","timeStamp":1747555200010,"threadId":"worker-1","data":{...}}
{"type":"SCENARIO_ENTER","timeStamp":1747555200020,"threadId":"worker-1","data":{...}}
{"type":"SCENARIO_EXIT","timeStamp":1747555200100,"threadId":"worker-1","data":{...}}
{"type":"FEATURE_EXIT","timeStamp":1747555200200,"threadId":"worker-1","data":{...FeatureResult.toJson()}}
{"type":"SUITE_EXIT","timeStamp":1747555210000,"threadId":null,"data":{"summary":{...}}}
```

### Migrating CI/CD scrapers

If you parsed `karate-summary.json` or per-feature `*.json.txt` from v1, switch to `karate-events.jsonl`. The single line for a per-suite IM/Slack summary:

```bash
jq 'select(.type == "SUITE_EXIT") | .data.summary' \
   target/karate-reports/karate-json/karate-events.jsonl
```

For per-feature detail (drop-in for the old `<feature>.json.txt`):

```bash
jq 'select(.type == "FEATURE_EXIT") | .data' \
   target/karate-reports/karate-json/karate-events.jsonl
```

This is a deliberate, breaking change — budget time for the scraper rewrite. The JSON Lines format is the long-term support contract; per-feature `.txt` dumps will not return.

> **Note:** opt-in Cucumber JSON output is still produced when `cucumber:json` is set, with the same per-feature shape v1 emitted. If you have existing Cucumber JSON consumers (e.g., third-party dashboards) you can keep using them — just enable the flag.

See [DESIGN.md § Reports](./DESIGN.md#reports) for the full report architecture.

---

## Feature File Compatibility

Most feature files work unchanged. Known differences:

- **`@parallel=false` is gone — use `@lock` instead.** See [Parallel Execution Control](#parallel-execution-control) below.
- **Cookie domain assertions**: If testing cookie domains, note that RFC 6265 compliance means leading dots are stripped (`.example.com` → `example.com`).
- **Karate-JSON vs JavaScript on the RHS**: as in v1, any `def` / `set` / `configure` / `match` RHS that starts with `{` or `[` is parsed as Karate's relaxed JSON. To force JavaScript / ES6 semantics on the value side, wrap the literal in parens. See below.

### Karate-JSON vs JavaScript on the right-hand side

Anything on the right-hand side of `def` (or `set`, `configure`, `match`, …) that starts with `{` or `[` goes through Karate's relaxed JSON parser — hyphenated keys work, `#(expr)` is substituted, and a bare identifier on the value side is read as the **string** with that name. To get JavaScript / ES6 evaluation instead, wrap the literal in parens.

```gherkin
* def id = 123
* def name = 'sample'

# Karate JSON — #(...) substitution preserves types
* def a = { "id": #(id), "name": "sample" }
* def b = { id: '#(id)', name: 'sample' }       # equivalent

# Hyphenated keys are fine
* def headers = { Accept: 'application/json', Content-Type: 'application/json', Idempotency-Key: 'abc-123' }

# Paren-wrap forces JavaScript evaluation
* def c = ({ id, name })                        # ES6 shorthand
* def d = ({ id: id, name: name })              # explicit reference
```

This matches v1 behavior — most v1 feature files work unchanged. The only thing to watch for is feature/test code that intentionally relied on JS semantics for an unwrapped literal (e.g., `* def response = { id: pathParams.id }`) — those need paren-wrapping (`* def response = ({ id: pathParams.id })`) or rewriting with `#(...)`.

---

## Parallel Execution Control

V2 **drops the `@parallel=false` tag**. It is silently ignored — no deprecation warning, no error — and any feature still relying on it will run in parallel as if untagged. The replacement is the more expressive `@lock` tag.

| v1 intent | v2 equivalent |
|---|---|
| `@parallel=false` on a `Feature` — run that feature's scenarios sequentially, but let other features still run in parallel | `@lock=<unique-name>` on the `Feature` (any name; pick one not used elsewhere) |
| Single-thread the entire run — keep one feature isolated from every other scenario in the suite | `@lock=*` on the `Feature` or `Scenario` |
| Coordinate two unrelated features that mutate the same shared resource | `@lock=<shared-name>` on both — any scenarios tagged with the same name serialize against each other |

Feature-level tags propagate to every scenario in the feature (`Scenario.getTagsEffective()` merges `Feature` tags in), so `@lock` on the `Feature:` line is the drop-in for v1's feature-level `@parallel=false`.

```gherkin
# v1 — no longer works in v2 (silently ignored)
@parallel=false
Feature: order workflow

# v2 — same effect: every scenario in this feature serializes against every other
# scenario tagged @lock=order-workflow
@lock=order-workflow
Feature: order workflow
```

```gherkin
# v2 — run this feature exclusively (no other scenarios anywhere run concurrently)
@lock=*
Feature: db migration smoke
```

Unlike `@parallel=false`, `@lock=name` lets unrelated features still run concurrently with each other — only scenarios sharing the same lock name serialize. Use `@lock=*` only when full exclusion is genuinely required, since it stalls the whole worker pool.

See [DESIGN.md § Built-in Tags](./DESIGN.md#built-in-tags) and `ScenarioLockManager.java` for implementation detail.

---

## Java Interop

`Java.type()` and existing v1 patterns work unchanged. Two notes:

- **`karate.toJava()` is a deprecated no-op** in v2 (logs a one-line warning per process). It's no longer needed: JS arrays/objects work directly as Java `List`/`Map`, and JS functions auto-coerce to Java `@FunctionalInterface` parameters (`Function`, `Predicate`, `Consumer`, `Supplier`, `Runnable`).
- **JS function → Java functional interface coercion** works natively. v1 got this from Graal's interop layer; v2 routes through default methods on `JavaCallable`. Pass an inline `function` / arrow directly to a Java method that declares e.g. `Predicate<Map<String, Object>>`. `Predicate.test()` uses JS-truthy semantics on the return value; `Function.apply()` and `Supplier.get()` auto-unwrap (`undefined → null`, `JsDate → java.util.Date`). For multi-arg interfaces (`BiFunction`, `BiConsumer`), receive the JS function as `JavaCallable` and call `.call(null, arg1, arg2)` explicitly.
- **Lazy bindings**: register through `JsLazy` (in `io.karatelabs.js`), not `Supplier<T>`. The previous `instanceof Supplier` sentinel collided with the new functional-interface coercion (every JS function would have been treated as a lazy binding). External lazy bindings via `engine.put("key", (Supplier<X>) () -> ...)` need to be migrated to `(JsLazy) () -> ...`. See [JS_ENGINE.md § Lazy Variables with JsLazy](./JS_ENGINE.md#lazy-variables-with-jslazy).

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

### Element DOM Navigation

V2 drops v1's tree-walking element accessors — `parent`, `children`, `firstChild`, `lastChild`, `previousSibling`, `nextSibling` — by design. Hop-counting patterns like `e.parent.parent` are fragile: any intervening `<div>` added by a designer silently breaks the test. The v2 surface is selector-based and mirrors the native W3C DOM `Element` API:

| API | Purpose |
|-----|---------|
| `element.closest(selector)` | Nearest ancestor (or self) matching a CSS selector |
| `element.matches(selector)` | Boolean: does this element match the selector |
| `element.locate(childSelector)` / `element.locateAll(childSelector)` | Scoped descendant lookups |
| `element.script(jsExpression)` | Escape hatch for arbitrary DOM walks (`_.nextElementSibling.id`, etc.) |

V1 → V2 translation recipes:

```gherkin
# v1: row.parent.click() — v2: closest + a selector
* locate('//td[text()="John"]').closest('tr').click()

# v1: el.parent.children → v2: closest + scoped locateAll
* def cells = locate('//td[text()="John"]').closest('tr').locateAll('td')

# v1: el.nextSibling — v2: script() drops into the browser
* def nextId = locate('#anchor').script('_.nextElementSibling.id')
```

### Migration Checklist for Driver Tests

- [ ] Replace `delay(millis)` with `karate.pause(millis)` if used before the driver starts
- [ ] `showDriverLog` has no effect (TODO)
- [ ] W3C WebDriver types (`chromedriver`, `geckodriver`, `safaridriver`) are now fully supported
- [ ] Rewrite v1 tree-walking (`element.parent`, `.children`, `.nextSibling`, etc.) in terms of `closest(selector)` + scoped `locateAll`, or `element.script()` for arbitrary DOM walks

---

## Image Comparison

Visual regression moved out of core into the **`karate-image` extension** with a redesigned API — the v1 `compareImage` keyword and `karate.compareImage(...)` method are replaced by an `image` object (`image.compare(...)` / `image.rebase(...)`) activated from `karate-boot.js`. Add `io.karatelabs:karate-image` (or drop its fat JAR into `~/.karate/ext/`). Full guide: [Image Comparison](https://docs.karatelabs.io/extensions/image-comparison/) (once published).

---

## Desktop Automation (karate-robot)

`karate-robot` is **not part of Karate v2**. Teams that depend on native desktop automation can continue to use — or maintain a fork of — the v1 code, which is preserved on the [`v1` branch](https://github.com/karatelabs/karate/tree/v1/karate-robot). For commercial support or to discuss future plans, reach out via [karatelabs.io/contact-us](https://karatelabs.io/contact-us).

---

## Embedded HTTP Server

The v1 embedded HTTP server (`com.intuit.karate.http.HttpServer` with `ServerConfig` and `contextFactory`) has been replaced with a new architecture in v2:

- v2 uses `io.karatelabs.http.ServerRequestHandler` with file-based API routing (`/api/todos` → `api/todos.js`)
- Sub-path routing is supported: `/api/todos/{id}` resolves to `api/todos.js` with `request.pathMatches()` for path parameters
- v1's `contextFactory` pattern for manual path routing does not exist in v2
- v1's `useGlobalSession(true)` is replaced by explicit session init in JS handlers: `session || context.init()`

### Startup pattern: `HttpServer.config(...).build()` → `HttpServer.start(port, handler)`

```java
// v1
ServerConfig config = new ServerConfig("src/main/java/app").useGlobalSession(true);
config.contextFactory(request -> {
    ServerContext context = new ServerContext(config, request);
    if (context.setApiIfPathStartsWith("/api/")) {
        context.setLockNeeded(true);
    }
    return context;
});
HttpServer server = HttpServer.config(config).http(8080).build();

// v2
ServerConfig config = new ServerConfig("src/main/java/app")
        .sessionStore(new InMemorySessionStore())  // required for context.init() to do anything
        .csrfEnabled(false);                       // on by default; turn off for API-only test backends
// /api/* and /pub/* routing is built in — no contextFactory
ServerRequestHandler handler = new ServerRequestHandler(config, new RootResourceResolver(config.getResourceRoot()));
HttpServer server = HttpServer.start(8080, handler);
```

Two easy-to-miss side effects of the change:

- **Sessions are disabled unless a `SessionStore` is configured.** `config.isSessionEnabled()` returns true only when `sessionStore(...)` has been called. Without it, `context.init()` is a silent no-op and every JS handler dereferencing `session.foo` throws `cannot read properties of null`. `InMemorySessionStore` is the drop-in for dev and single-instance apps.
- **CSRF is enabled by default in v2.** A POST from a test client that hasn't first fetched a page and picked up a CSRF token is rejected. For pure API demos, set `csrfEnabled(false)`; for web apps, use `csrfExemptPaths(...)` for webhook/API routes that authenticate differently.

**If your tests used the embedded HTTP server as a test backend**, consider switching to `MockServer` with a feature file — this is the idiomatic v2 approach for test API backends. See [karate-todo](https://github.com/karatelabs/karate-todo) for a complete example.

For serving full web applications with templates, see [TEMPLATING.md](./TEMPLATING.md).

---

## Migration Checklist

- [ ] Update `karate-junit5` → `karate-junit6` dependency
- [ ] Add `junit-jupiter` dependency explicitly (v2 doesn't bundle it)
- [ ] Update Java version to 21+
- [ ] Update `maven-surefire-plugin` to 3.2.5+ (for JUnit 6 support)
- [ ] If using the Gatling plugin, add `--add-opens=java.base/java.lang=ALL-UNNAMED` to its `<jvmArgs>`
- [ ] Replace `JsonUtils` with `Json` class (if used)
- [ ] Remove code using `HttpLogModifier`, `WebSocketClient`, or Driver Java APIs (if used)
- [ ] Replace any `@parallel=false` tags with `@lock=<name>` (or `@lock=*` for full exclusion) — `@parallel=false` is silently ignored in v2
- [ ] Update cookie domain assertions if needed
- [ ] Remove `karate.toJava(...)` calls (deprecated no-op — JS functions auto-coerce to Java functional interfaces; arrays/objects work as `List`/`Map` directly)
- [ ] If you registered lazy bindings via `engine.put("key", (Supplier<X>) () -> ...)`, migrate to `(JsLazy) () -> ...`
- [ ] If using embedded HTTP server: switch to `HttpServer.start(port, ServerRequestHandler)`, add a `SessionStore`, and decide whether to keep CSRF on
- [ ] If migrating to native v2 APIs: update imports, return types, and method names (see above)

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

## CI/CD with Testcontainers

For a complete reference CI/CD pipeline that runs API tests, UI tests (via a containerized Chrome),
Gatling smoke, secret scanning, and publishes HTML reports to GitHub Pages, see
[karatelabs/karate-todo](https://github.com/karatelabs/karate-todo).

The UI side uses `chromedp/headless-shell` + `Testcontainers.exposeHostPorts(...)` to reach an
in-process Karate-hosted app from inside the browser container. The critical pattern:

```java
// src/test/java/app/ui/UiTest.java
private static final int PORT = 18080;

static {
    // Docker 29.x API negotiation workaround
    System.setProperty("api.version", "1.44");
}

@BeforeAll
static void beforeAll() {
    server = App.start(App.serverConfig("src/main/java/app"), PORT);
    chrome = new ChromeContainer();  // extends GenericContainer
    chrome.start();
}

@Test
void testAll() {
    ContainerDriverProvider provider = new ContainerDriverProvider(chrome);
    SuiteResult result = Runner.path("classpath:app/ui")
            .tags("~@external", "~@todo")
            .systemProperty("serverUrl", chrome.getHostAccessUrl(PORT))
            .systemProperty("apiUrl", "http://localhost:" + PORT)
            .driverProvider(provider)
            .parallel(1);
    assertEquals(0, result.getScenarioFailedCount(), String.join("\n", result.getErrors()));
}
```

`ContainerDriverProvider` is a thin extension of `PooledDriverProvider` that overrides `createDriver(config)`
to call `CdpDriver.connect(container.getCdpUrl(), CdpDriverOptions.fromMap(config))` — each pooled slot gets a
fresh tab in the shared container. Full source:
[`app/ui/support/`](https://github.com/karatelabs/karate-todo/tree/main/src/test/java/app/ui/support).

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

### karate-todo Migration

**Repository:** [karatelabs/karate-todo](https://github.com/karatelabs/karate-todo)

A complete v1 → v2 migration using native v2 APIs (no shims):

- `karate-junit5` → `karate-junit6` with `Iterable<DynamicNode>` return type
- Embedded HTTP server → `MockServer` for test backend
- `Results` → `SuiteResult` with updated method names
- `com.intuit.karate.gatling` → `io.karatelabs.gatling` imports

**Reference diffs:**

- Bulk v1 → v2 migration: [commit d901b3e](https://github.com/karatelabs/karate-todo/commit/d901b3e2b12a0a7f5dccd6b403117d0d3778eb59)
  — pom (Java 21, karate 2.0.x, karate-junit6, surefire 3.5.2, Gatling `--add-opens`), `App.java`
  (v2 `HttpServer.start` + `ServerRequestHandler` + `SessionStore`), JS handlers (`session || context.init()`),
  templates (absolute `/pub` paths, CDN trinity), JUnit 6 runners, Gatling package rename.
- CI/CD + Testcontainers UI harness on top of v2.0.4: see the `Add Testcontainers UI runner...` and
  `Add GitHub Actions CI...` commits on `main`.

# Karate v2 Design

> **Start here.** This is the primary reference for LLMs and maintainers working on the Karate codebase.
>
> See also: [PRINCIPLES.md](./PRINCIPLES.md) | [CLI.md](./CLI.md) | [JS_ENGINE.md](./JS_ENGINE.md) | [TODOS.md](./TODOS.md)

---

## Architecture

```
Suite → FeatureRuntime → ScenarioRuntime → StepExecutor
                                               ↓
                              ┌────────────────┼────────────────┐
                              ▼                ▼                ▼
                         Match Engine    Http Client    Other Actions
```

### Module Map

```
karate/
├── karate-js/          # JavaScript engine, Gherkin parser, lexer
│   └── io.karatelabs.js        # JsEngine, JsValue, GherkinParser, JsParser
├── karate-core/        # Runtime, HTTP, matching, mocks, reports, templating
│   └── io.karatelabs.*         # See packages below
├── karate-junit6/      # JUnit 6 integration
├── karate-gatling/     # Performance testing (Gatling integration)
└── docs/               # Design docs (this file)
```

### Key Packages (karate-core)

| Package | Purpose |
|---------|---------|
| `io.karatelabs` | Suite, Runner, FeatureRuntime, ScenarioRuntime, StepExecutor |
| `io.karatelabs.http` | ApacheHttpClient, HttpClientFactory, MockServer, WebSocket |
| `io.karatelabs.match` | Match engine (EQUALS, CONTAINS, WITHIN, etc.) |
| `io.karatelabs.output` | Reports (HTML, Cucumber JSON, JUnit XML, JSONL), LogContext |
| `io.karatelabs.template` | Thymeleaf-based HTML templating |
| `io.karatelabs.driver` | Browser automation (CDP, W3C WebDriver) |

---

## Core Classes

| Class | Role |
|-------|------|
| `Suite` | Top-level orchestrator, config, parallel execution |
| `FeatureRuntime` | Feature execution, scenario iteration, callOnce caching |
| `ScenarioRuntime` | Scenario execution, variable scope, implements `KarateJsContext` |
| `StepExecutor` | Keyword dispatch (def, match, url, method, etc.) |
| `KarateJs` | JS engine bridge, `karate.*` API methods |
| `KarateJsBase` | Shared state and infrastructure for KarateJs |
| `KarateJsUtils` | Stateless utility methods (`karate.filter`, `karate.map`, etc.) |
| `HttpClientFactory` | Factory for HTTP clients (extensible for Gatling pooling) |
| `Runner` | Fluent API entry point for test execution |
| `CommandProvider` | SPI for CLI subcommand registration |

---

## Step Keywords

- **Variables:** `def`, `set`, `remove`, `text`, `json`, `xml`, `csv`, `yaml`, `string`, `xmlstring`, `copy`, `table`, `replace`
- **Assertions:** `match` (all operators), `assert`, `print`
- **HTTP:** `url`, `path`, `param`, `params`, `header`, `headers`, `cookie`, `cookies`, `form field`, `form fields`, `request`, `retry until`, `method`, `status`, `multipart file/field/fields/files/entity`
- **Control:** `call`, `callonce`, `eval`, `doc`
- **Config:** `configure` (ssl, proxy, timeouts, headers, cookies, auth, retry, report, etc.)

## Built-in Tags

| Tag | Description |
|-----|-------------|
| `@ignore` | Skip execution (but still callable via `call read(...)`) |
| `@env=<name>` | Run only when `karate.env` matches |
| `@envnot=<name>` | Skip when `karate.env` matches |
| `@setup` | Data provider for dynamic outlines |
| `@fail` | Expect failure (invert result) |
| `@lock=name` | Named mutual exclusion (same name = sequential) |
| `@lock=*` | Exclusive execution (no other scenarios run concurrently) |
| `@skipped` | Synthetic — engine auto-adds in the result's tags when a scenario was aborted (via `karate.abort()` or suite abort). Informational only; surfaces in the HTML report's tag chips. `skippedCount` is additive — skipped scenarios still count as passed (no breaking change to existing counts). |

## Caching

| Method | Scope | Use Case |
|--------|-------|----------|
| `callonce` | Feature-scoped | Shared setup within a feature |
| `karate.callSingle()` | Suite-scoped | Global setup (e.g., auth token). Supports disk caching via `configure callSingleCache` |

## Line Number Filtering

`Runner.path("features/users.feature:10:25")` — selects scenarios by line. **Bypasses all tag filters** including `@ignore`. Essential for IDE integrations.

## Scenario Name Filtering

`Runner.Builder.scenarioName("Login happy path")` (CLI: `-n/--name`) — selects scenarios by exact name, trimmed on both sides. Same tag-bypass semantics as the line filter; intersects with `:LINE` when both are set (for Scenario Outline row targeting). Stable under edits — IDE plugins use this as a line-independent key. Source: `FeatureRuntime.matchesScenarioName`.

## System-Property Overrides

`Runner.Builder.parallel()` applies CI overrides before execution (v1 parity). Reads `karate.options` (with `KARATE_OPTIONS` env fallback), plus `karate.env` and `karate.config.dir`, and overrides Builder values in place. The option string uses the `karate run` CLI grammar. Applied before `startDebugServerIfRequired`, so IDE debug launches inherit the merged state via `buildDebugArgs`. See [CLI.md](./CLI.md#system-properties--environment-variables). Source: `KarateOptionsHandler.java`.

---

## karate.* API

150+ methods on the `karate` object. Key categories:

| Category | Examples |
|----------|---------|
| **Flow** | `abort()`, `call()`, `callonce()`, `callSingle()`, `eval()`, `fail()` |
| **HTTP** | `http(url)`, `prevRequest`, `request`, `response` |
| **Data** | `read()`, `readAsBytes()`, `readAsString()`, `write()`, `fromJson()`, `toJson()`, `toCsv()` |
| **Collections** | `append()`, `distinct()`, `filter()`, `map()`, `sort()`, `merge()`, `keysOf()`, `valuesOf()`, `range()`, `repeat()` |
| **Assertions** | `match()`, `expect()` (Chai-style BDD API) |
| **Process** | `exec()`, `fork()`, `signal()`, `waitForHttp()`, `waitForPort()` |
| **Mock** | `start()`, `proceed()`, `stop()` |
| **Test data** | `faker.*` (names, emails, addresses, numbers, timestamps, etc.), `uuid()` |
| **Logging** | `log()`, `logger.debug/info/warn/error()`, `embed()` |
| **Info** | `env`, `os`, `properties`, `config`, `feature`, `scenario`, `tags`, `tagValues` |
| **Templating** | `doc()`, `render()` |

Full listing: see `KarateJs.java`, `KarateJsUtils.java` in karate-core.

### karate.expect() — Chai-Style Assertions

```gherkin
* karate.expect(response.status).to.equal(200)
* karate.expect(response.items).to.have.length(3)
* karate.expect(response.count).to.be.within(1, 10)
* karate.expect(response).to.have.nested.property('user.address.city', 'NYC')
```

Supports: `equal`, `a`/`an`, `property`, `keys`, `include`/`contain`, `above`/`below`/`within`/`closeTo`, `match` (regex), `oneOf`, `ok`/`empty`/`true`/`false`/`null`/`exist`, negation via `.not`.

### karate.faker.*

```gherkin
* def name = karate.faker.fullName()
* def email = karate.faker.email()
* def num = karate.faker.randomInt(18, 65)
* def ts = karate.faker.isoTimestamp()
```

Categories: names, contact, location, numbers, text, business, timestamps. See `KarateJsUtils.java`.

### configure auth

```gherkin
* configure auth = { type: 'basic', username: 'user', password: 'pass' }
* configure auth = { type: 'bearer', token: '#(accessToken)' }
* configure auth = { type: 'oauth2', grantType: 'client_credentials', tokenUrl: '...', clientId: '...', clientSecret: '...' }
```

---

## Process Execution

### karate.exec(command)

Synchronous command execution. Accepts string, array, or map with options (`line`, `args`, `workingDir`, `env`, `timeout`).

### karate.fork(options)

Async background process. Returns `ProcessHandle` with:
- **Properties:** `stdOut`, `stdErr`, `exitCode`, `alive`, `pid`
- **Methods:** `waitSync()`, `waitForOutput(predicate)`, `waitForPort()`, `waitForHttp()`, `onStdOut()`, `onStdErr()`, `start()`, `close()`, `signal()`

**Options:** `line`/`args`, `workingDir`, `env`, `useShell`, `redirectErrorStream`, `timeout`, `listener`, `errorListener`, `start`

### karate.signal() + listen

Communicate from forked process listener back to test flow:

```gherkin
* def proc = karate.fork({ args: ['node', 'server.js'], listener: function(line) { if (line.contains('listening')) karate.signal({ ready: true }) } })
* def result = listen 5000
* match result.ready == true
```

See [MOCKS.md](./MOCKS.md) for mock server, [CLI.md](./CLI.md) for CLI architecture, [GATLING.md](./GATLING.md) for performance testing.

---

## Event System

Unified observation and control of test execution via `RunListener`.

```
Suite.fireEvent(RunEvent)  →  RunListener.onEvent(RunEvent)  →  return boolean
```

### Event Lifecycle

```
SUITE_ENTER
├── FEATURE_ENTER
│   ├── SCENARIO_ENTER
│   │   ├── STEP_ENTER
│   │   │   ├── HTTP_ENTER → HTTP_EXIT
│   │   └── STEP_EXIT
│   └── SCENARIO_EXIT
└── FEATURE_EXIT
SUITE_EXIT
```

Return `false` from `*_ENTER` events to skip execution. Events fire for **all** features including `call`ed ones — use `event.isTopLevel()` to filter.

### Core Interfaces

```java
// Single listener method — pattern matching for dispatch
public interface RunListener {
    default boolean onEvent(RunEvent event) { return true; }
}

// Per-thread listeners (for debuggers)
public interface RunListenerFactory {
    RunListener create();
}
```

### HTTP Events

`HttpRunEvent` gives access to `request`, `response`, `scenarioRuntime`, and `getCurrentStep()`. Return `false` from `HTTP_ENTER` to skip/mock the request.

**Source files:** `RunEventType.java`, `RunEvent.java`, `HttpRunEvent.java`, `StepRunEvent.java`, `RunListener.java`, `RunListenerFactory.java`

---

## Logging

SLF4J-based with category hierarchy — `karate.runtime`, `karate.http`, `karate.mock`, `karate.scenario`, `karate.console`.

### LogContext

Thread-local collector that captures all test output (print, karate.log, HTTP logs) for reports. Also collects embeds (HTML, images) via `LogContext.get().embed()`.

### Log Levels

Controlled separately for reports vs runtime:
- **Report:** `configure report = { logLevel: 'warn' }` or `--report-log-level warn`
- **Runtime:** `--runtime-log-level debug` or logback.xml config

### Log Masking

Built-in presets: `PASSWORDS`, `CREDIT_CARDS`, `SSN`, `EMAILS`, `API_KEYS`, `BEARER_TOKENS`, `BASIC_AUTH`, `ALL_SENSITIVE`. Custom patterns via `LogMask.builder().pattern(regex, replacement)`.

**Source files:** `LogContext.java`, `LogLevel.java`, `LogMask.java`

---

## Reports

### Architecture

```
FeatureResult.toJson()  ← Single source of truth
      ↓
  ┌───┼──────┬──────────┐
  ↓   ↓      ↓          ↓
JSONL HTML Cucumber   JUnit
          JSON        XML
```

All report formats derive from `FeatureResult.toJson()`. Generation is async via `ResultListener` implementations. HTML uses Alpine.js + Bootstrap 5 with inlined JSON.

### Output Structure

```
target/karate-reports/
├── karate-summary.html        # Summary dashboard
├── karate-timeline.html       # Gantt-style parallel execution view
├── feature-html/              # Per-feature interactive reports
├── cucumber-json/             # Per-feature Cucumber JSON (opt-in)
├── junit-xml/                 # Per-feature JUnit XML (opt-in)
└── karate-json/karate-events.jsonl  # Event stream (opt-in)
```

### JSONL Event Stream

Standard envelope: `{"type":"EVENT_TYPE","ts":epoch_ms,"threadId":"...","data":{...}}`

`FEATURE_EXIT` contains full `toJson()` — the source of truth for offline report generation. Enable with `Runner.outputJsonLines(true)` or `--jsonl`.

### Runner API

```java
Runner.path("features/")
    .outputJsonLines(true)      // JSONL event stream
    .outputCucumberJson(true)   // Cucumber JSON
    .outputJunitXml(true)       // JUnit XML
    .outputHtmlReport(false)    // disable HTML (on by default)
    .parallel(5);
```

### Report Aggregation

```java
HtmlReport.aggregate()
    .json("target/run1/karate-json/karate-events.jsonl")
    .json("target/run2/karate-json/karate-events.jsonl")
    .outputDir("target/combined-report")
    .generate();
```

**Source files:** `HtmlReportListener.java`, `HtmlReportWriter.java`, `CucumberJsonWriter.java`, `JunitXmlWriter.java`, `JsonLinesEventWriter.java`

---

## Plugin Architecture

| Interface | Purpose | Discovery |
|-----------|---------|-----------|
| `CommandProvider` | CLI subcommands | ServiceLoader (`~/.karate/ext/` JARs) |
| `HttpClientFactory` | Custom HTTP clients | Constructor injection |
| `RunListener` | Event listeners | `Runner.listener()` or `--listener` CLI |
| `RunListenerFactory` | Per-thread listeners | `Runner.listenerFactory()` |
| `ReportWriterFactory` | Custom report formats | ServiceLoader (planned) |

---

## Deep-Dive Docs

| Doc | Covers |
|-----|--------|
| [PRINCIPLES.md](./PRINCIPLES.md) | Design philosophy and priorities |
| [CLI.md](./CLI.md) | Two-tier CLI (Rust launcher + Java), subcommands, karate-pom.json |
| [JS_ENGINE.md](./JS_ENGINE.md) | Type system (JsValue hierarchy), Java interop, prototypes |
| [DRIVER.md](./DRIVER.md) | Browser automation — CDP, W3C WebDriver, frame/window management |
| [MOCKS.md](./MOCKS.md) | Mock server — feature-based definitions, proxy mode, stateful mocks |
| [GATLING.md](./GATLING.md) | Performance testing — Java DSL, session chaining, HTTP pooling |
| [TEMPLATING.md](./TEMPLATING.md) | HTML templating — Thymeleaf + JS expressions, HTMX, server/static modes |
| [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) | V1 -> V2 migration guide |
| [CAPABILITIES.yaml](./CAPABILITIES.yaml) | Complete feature inventory (366 capabilities) |
| [TODOS.md](./TODOS.md) | Actionable work items |
| [RELEASING.md](./RELEASING.md) | Release checklist |

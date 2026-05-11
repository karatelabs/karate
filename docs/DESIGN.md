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
├── karate-js/          # JS engine + reusable parser framework + Resource abstraction
│   ├── io.karatelabs.js        # Engine, Context, Bindings, JsValue hierarchy
│   ├── io.karatelabs.parser    # BaseParser, BaseLexer — extended by GherkinParser
│   └── io.karatelabs.common    # Resource, Pair, StringUtils (no karate-core deps)
├── karate-core/        # Runtime, HTTP, matching, mocks, reports, templating, gherkin model
│   └── io.karatelabs.*         # See packages below
├── karate-junit6/      # JUnit 6 integration
├── karate-gatling/     # Performance testing (Gatling integration)
└── docs/               # Design docs (this file)
```

### Key Packages (karate-core)

| Package | Purpose |
|---------|---------|
| `io.karatelabs.core` | Suite, Runner, FeatureRuntime, ScenarioRuntime, StepExecutor, KarateConfig, ScenarioLockManager |
| `io.karatelabs.gherkin` | Gherkin model + parser: Feature, Scenario, Tag, GherkinParser (extends `io.karatelabs.parser.BaseParser`) |
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
| `KarateConfig` | Mutable per-scenario configuration; source of truth for `configure ...` keys (snapshotted/restored across scenarios) |
| `ScenarioLockManager` | `@lock` enforcement — named locks + global read/write lock for `@lock=*` |
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
| `@report=false` | Scenario runs and counts toward suite totals, but its step detail is suppressed from HTML / Cucumber JSON / JUnit XML / JSONL outputs. Failures surface only a redacted message; full detail still hits runtime logs. Inherits into any features called from this scenario. Use for runs where step content (HTTP bodies, error messages) may include secrets that mustn't reach CI artifacts. |
| `@skipped` | **Synthetic** — engine adds this tag to a scenario result when it didn't run to completion. Three triggers: (1) `karate.abort()` called from a step, (2) suite-abort via `abortSuiteOnFailure` (top-level scenarios only), (3) no step passed or failed (empty / fully-skipped body). Surfaces: HTML summary `@skipped` chip + dedicated Skipped column with pass-%, per-feature stdout, `ScenarioResult.skipped`, `FeatureResult.skippedCount`, `SuiteResult.getScenarioSkippedCount()` / `summary.scenariosSkipped`. **Additive** — a skipped scenario is also counted as passed, so existing pass/fail totals are unchanged. |

**Source files.** `Tag.java` (recognized constants: `IGNORE`, `ENV`, `ENVNOT`, `SETUP`, `FAIL`, `LOCK`), `GherkinParser.transformTags` (parse-time tag construction), `Scenario.getTagsEffective()` (feature + scenario tag merge), `ScenarioLockManager.java` (`@lock` enforcement), `ScenarioResult.isSkipped()` (`@skipped` semantics), `Scenario.isIgnore()` / `Feature.getSetup()` (`@ignore` / `@setup` enforcement).

**v1 leftover — `@parallel=false`.** Not recognized in v2; runs in parallel as if untagged. `GherkinParser.transformTags` emits a one-shot WARN at parse time pointing users at `@lock`. See [MIGRATION_GUIDE.md § Parallel Execution Control](./MIGRATION_GUIDE.md#parallel-execution-control).

## Caching

| Method | Scope | Use Case |
|--------|-------|----------|
| `callonce` | Feature-scoped | Shared setup within a feature |
| `karate.callSingle()` | Suite-scoped | Global setup (e.g., auth token). Supports disk caching via `configure callSingleCache` |

## Line Number Filtering

`Runner.path("features/users.feature:10:25")` — selects scenarios by line. **Bypasses all tag filters** including `@ignore`. Essential for IDE integrations.

## Scenario Name Filtering

`Runner.Builder.scenarioName("Login happy path")` (CLI: `-n/--name`) — selects scenarios by exact name, trimmed on both sides. Same tag-bypass semantics as the line filter; intersects with `:LINE` when both are set (for Scenario Outline row targeting). Stable under edits — IDE plugins use this as a line-independent key. Source: `FeatureRuntime.matchesScenarioName`.

## Dry Run

`Runner.Builder.dryRun(true)` or CLI `-D/--dryrun` skips step execution and still produces a full report. Intended for fast feature-file validation, outline-expansion sanity checks, and CI smoke passes that don't need real I/O.

Under dry-run:

- Every step on a non-`@setup` scenario is recorded as passed with 0ms duration — no HTTP, no `match`, no `def`, no side effects.
- `karate-base.js`, `karate-config.js`, and env-specific config JS are **not** evaluated for non-`@setup` scenarios.
- `beforeScenario` / `afterScenario` hooks are skipped for non-`@setup` scenarios.
- `@setup` scenarios execute fully, so dynamic outlines (`Examples: | karate.setup().data |`) still resolve their rows.
- All configured report formats (HTML, JUnit XML, Cucumber JSON, JSONL) are generated normally.

**Escape hatch — `karate.dryRun`.** A boolean readable from any step, useful inside `@setup` to short-circuit expensive fixture creation:

```gherkin
@setup
Scenario:
  * def rows = karate.dryRun ? [{ name: 'placeholder' }] : fetchFromDb()
```

Source: `ScenarioRuntime.isDryRunSkip()`, `KarateJs.isDryRun()`.

---

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

### `configure logging`

Single bucket for all logging behavior. Deep-merges with parent values so a partial update (e.g., flipping just the level) preserves mask + pretty.

```javascript
configure logging = {
  report:  'debug',         // threshold for report-buffer capture (default DEBUG)
  console: 'info',          // threshold for SLF4J/console (default INFO; null = inherit logback.xml)
  pretty:  true,            // pretty-print HTTP req/res JSON bodies (default true)
  mask: {                   // HTTP-only redaction
    headers:    ['Authorization', 'Cookie', 'X-Api-Key'],
    jsonPaths:  ['$.password', '$..token'],
    patterns:   [{ regex: '\\bBearer [A-Za-z0-9._-]+\\b', replacement: 'Bearer ***' }],
    replacement: '***',
    enableForUri: function(uri) { return uri.indexOf('/health') < 0 }
  }
}
```

### Two Thresholds: report vs console

| Threshold | Knob | What it controls | CLI |
|-----------|------|------------------|-----|
| Report buffer | `logging.report` | What gets captured into HTML / JSONL / Cucumber JSON / JUnit XML | `--log-report <level>` |
| SLF4J / console | `logging.console` | What hits stdout via Logback (also affects file appenders) | `--log-console <level>` |

The `HttpLogger` always writes the **full** request/response (with bodies, headers) to the report buffer at INFO. The console emission is auto-tiered by SLF4J level: INFO = one-liner, DEBUG = +headers, TRACE = +body. The two knobs let you, e.g., capture full traces in reports for post-hoc debugging while keeping a parallel run's console quiet.

> **HTTP bodies show up in the HTML report by default — you do not need to crank console to TRACE.** Defaults are `report: 'debug'` (≥ INFO captured) and `console: 'info'` (one-liner on stdout). Bodies always land in the report buffer at INFO, so they appear in HTML / JSONL / Cucumber / JUnit regardless of the console level. Only set `console: 'trace'` if you specifically want bodies streaming to stdout — which is rarely what you want for a real test run. **v1 difference:** v1 emitted full bodies to console at DEBUG; v2 reserves DEBUG for headers and TRACE for body. If you used to set `karate.console.log.level=debug` to see bodies in your terminal, switch to looking at the HTML report (or set `console: 'trace'` if you really want it on stdout).

### Where to put `configure logging`

Both forms are supported and both stick across the scenario:

```javascript
// karate-config.js — applies to every scenario in the suite
karate.configure('logging', { mask: { headers: ['Authorization'] }, pretty: true });
```

```gherkin
# Background — applies to every scenario in this feature
Background:
* configure logging = { mask: { jsonPaths: ['$..token'] } }
```

`KarateConfig` is the source of truth — `LogContext` is a per-thread cache that `ScenarioRuntime.call()` repopulates from config at scenario entry. Mid-test `* configure logging` mutations are auto-snapshot/restored so they don't leak into the next scenario. Source: `KarateConfig.applyLoggingToContext`, `ScenarioRuntime.call()`.

### Mid-test level flips with auto-restore

`* configure logging = { report: 'error' }` mid-flow takes effect immediately. At scenario end, the level is automatically snapshotted and restored, so the next scenario starts at whatever `karate-config.js` set. This automates the v1 pattern of manually reading/saving/resetting Logback's level via reflection.

```gherkin
Scenario: silence a noisy reusable
  * configure logging = { report: 'error' }
  * call read('classpath:noisy-warmup.feature')
  # report level is restored to default at scenario end — no manual cleanup
```

### Pretty body formatting

`logging.pretty` applies to both console and report bodies. With `pretty: true` (default), JSON bodies are re-parsed and pretty-printed (multi-line, 2-space indent); `pretty: false` collapses to single-line. Non-JSON bodies pass through unchanged. The pretty pass also runs after `mask` so masked values stay masked.

### Mask scope

`mask` applies **only** to HTTP request/response logging. It does NOT scan `* print` or `karate.log` output — those are user-controlled channels. If a scenario's body could leak via prints, raise `logging.report: 'warn'` to drop INFO captures, or tag it `@report=false`.

### Log Masking — declarative

The `mask` object replaces v1's `HttpLogModifier` Java interface. Compiled once per `configure logging` call into a `LogMask` instance stored on the thread-local `LogContext`. Each `HttpLogger.logRequest/logResponse` reads the current mask and applies:

1. `headers` — case-insensitive header-name set; matching headers' values become `replacement`.
2. `jsonPaths` — `$.x.y` (descend) and `$..x` (recursive) keys; matched values become `replacement`.
3. `patterns` — regex/replacement pairs applied last, so they catch anything header / JSON-path didn't.
4. `enableForUri(uri)` — optional JS predicate; when it returns falsy, no masking applies for that URL (useful for excluding `/health` so debugging stays easy).

```javascript
configure logging = {
  mask: {
    headers:    ['Authorization', 'Cookie'],
    jsonPaths:  ['$.password', '$..token'],
    patterns:   [{ regex: '\\b\\d{16}\\b', replacement: '****-****-****-****' }],
    replacement: '***'
  }
}
```

### Migration from v1 logging keys

The v1 keys (`logPrettyRequest`, `logPrettyResponse`, `printEnabled`, `lowerCaseResponseHeaders`, `logModifier`) are silent no-ops in v2 with deprecation warnings pointing at the new shape. `configure report = { logLevel }` is **hard-removed** in 2.0.6 — it now throws with a migration error. See [MIGRATION_GUIDE.md § Logging](./MIGRATION_GUIDE.md#logging).

**Source files:** `LogContext.java`, `LogLevel.java`, `LogMask.java`, `HttpLogger.java`, `KarateConfig.configureLogging`

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
├── karate-summary.html               # Summary dashboard (default)
├── karate-timeline.html              # Gantt-style parallel execution view (default)
├── feature-html/                     # Per-feature interactive reports (default)
├── karate-json/karate-events.jsonl   # JSON Lines event stream (opt-in)
├── cucumber-json/                    # Per-feature Cucumber JSON (opt-in)
└── junit-xml/                        # Per-feature JUnit XML (opt-in)
```

### Defaults

Only HTML is on by default. Cucumber JSON, JUnit XML, and JSONL are opt-in via `Runner.Builder` flags or the CLI `-f/--format` switch:

```java
Runner.path("features/")
    .outputJsonLines(true)      // karate-json/karate-events.jsonl
    .outputCucumberJson(true)   // cucumber-json/*.json
    .outputJunitXml(true)       // junit-xml/*.xml
    .outputHtmlReport(false)    // disable HTML (on by default)
    .parallel(5);
```

CLI: `-f html,karate:jsonl,cucumber:json,junit:xml`. Prefix with `~` to disable (`-f ~html`). Default is `html`. See `RunCommand.java`.

### JSON Lines event stream

Written by `JsonLinesEventWriter` (a `RunListener`) to `karate-json/karate-events.jsonl`. One record per line, flushed per write so external tools — IDE test runners, dashboards — can tail the file in real time during the run.

Standard envelope:

```json
{"type":"SUITE_ENTER","timeStamp":1747555200000,"threadId":null,"data":{"schemaVersion":"1","version":"2.0.8","env":"dev","threads":4}}
{"type":"FEATURE_ENTER","timeStamp":1747555200010,"threadId":"worker-1","data":{...}}
{"type":"SCENARIO_ENTER","timeStamp":1747555200020,"threadId":"worker-1","data":{...}}
{"type":"SCENARIO_EXIT","timeStamp":1747555200100,"threadId":"worker-1","data":{...}}
{"type":"FEATURE_EXIT","timeStamp":1747555200200,"threadId":"worker-1","data":{...FeatureResult.toJson()}}
{"type":"SUITE_EXIT","timeStamp":1747555210000,"threadId":null,"data":{"summary":{...}}}
```

`FEATURE_EXIT.data` is the full `FeatureResult.toJson()` — the canonical structured payload for offline analysis, CI/CD scraping, and downstream tooling. `SUITE_EXIT.data.summary` carries pass/fail counters and total duration.

`STEP_ENTER` / `STEP_EXIT` / `HTTP_ENTER` / `HTTP_EXIT` events fire on the `RunListener` bus but are deliberately not emitted into JSONL (too granular for a streaming feed). HTTP request/response detail still reaches consumers via `step.embeds[]` inside `FEATURE_EXIT`.

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
| [CAPABILITIES.yaml](./CAPABILITIES.yaml) | Complete feature inventory (source of truth — regen `CAPABILITIES.md` via `./etc/generate-capabilities.sh`) |
| [TODOS.md](./TODOS.md) | Actionable work items |
| [RELEASING.md](./RELEASING.md) | Release checklist |

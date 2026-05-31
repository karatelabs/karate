# Karate v2 Design

> **Start here.** This is the primary reference for LLMs and maintainers working on the Karate codebase.
>
> See also: [CLI.md](./CLI.md) | [JS_ENGINE.md](./JS_ENGINE.md) | [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md)

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

## Karate-Expression Evaluation

`StepExecutor.evalKarateExpression(String)` is the **single entry point** for any RHS that may be a Karate-specific (non-JS) expression. It dispatches on the leading token:

| Prefix | Handler |
|--------|---------|
| `call ` / `callonce ` | Nested call (returns the result variable) |
| `$` | `$.path` / `$varname[*].path` JSONPath |
| `get ` / `get[N]` | get-expression on a named variable |
| `<` | XML literal (+ embedded-expression walk) |
| `/` or `//` | XPath on `response` |
| `varname/xpath` | XPath on a named variable |
| `{` or `[` | Relaxed JSON via `Json.parseLenient` (+ embedded-expression walk on the result) |
| _other_ | JS eval (+ embedded-expression walk if result is Map or List) |

The `{...}` / `[...]` branch is what lets `{ userId: #(userId) }` resolve embedded expressions without first surviving JS parsing — `Json.parseLenient` accepts `#(...)` as a string token, then `processEmbeddedExpressions` walks the result tree. To force ES6 / JS evaluation of a `{`-leading expression (e.g. shorthand `{ id }`, or values that aren't lenient-JSON-tokenisable), wrap in parens: `({ id })`.

**Call-arg sites must use this entry point.** All four call-arg evaluation sites — `parseCallExpression` (read-based feature call), the JS-function branches in `executeCall` and `executeCallWithResult`, and `executeFeatureCall` — route their arg through `evalKarateExpression` so inline JSON with embedded `#(...)` resolves uniformly. Reaching for `runtime.eval(wrapJsonLikeExpression(...))` directly in a new call-related path is a regression bait — it bypasses the JSON+embedded branch and produces `ReferenceError: # is not defined` on unquoted placeholders (issue #2849).

Splitting `read(path) arg` is **quote- and nested-paren-aware** via `StepUtils.findReadCloseParen` — paths containing `)` (inside quotes) and args containing parens (`{ val: foo() }`) both split correctly.

**Source files:** `StepExecutor.evalKarateExpression`, `StepExecutor.processEmbeddedExpressions`, `StepUtils.findReadCloseParen` / `findCallArgSeparator`, `Json.parseLenient`.

---

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

**Escape hatch — `karate.suite.dryRun`.** A boolean readable from any step, useful inside `@setup` to short-circuit expensive fixture creation:

```gherkin
@setup
Scenario:
  * def rows = karate.suite.dryRun ? [{ name: 'placeholder' }] : fetchFromDb()
```

Source: `ScenarioRuntime.isDryRunSkip()`, `KarateJsBase.getSuiteData()`.

---

## Match Engine

`io.karatelabs.match` — operator set in `Operation` (EQUALS, CONTAINS, CONTAINS_DEEP, CONTAINS_ONLY, CONTAINS_ANY, WITHIN, EACH, etc.), driven from `Match.java`. Two notable behaviors:

- **Full-tree failure collection.** A single `match` walks the entire actual/expected pair and **collects every mismatched path**, not just the first one. Each failure is a `Result.Failure` record (`path`, `reason`, `actualType`, `expectedType`, `actualValue`, `expectedValue`, `depth`). The structured list is rendered into the hierarchical message by `Operation.collectFailureReasons` and surfaced to reports via `Result.toMap()`. This is what makes "fix all mismatches in one iteration" possible — pairs naturally with `continueOnStepFailure` for the cross-step equivalent.
- **Fuzzy markers in `Validators.java`.** `#string`, `#number`, `#regex(...)`, `#?<expr>`, `##` (optional), `#null` / `#notpresent`, cross-field `$` references, embedded JS predicates. The engine evaluates markers in place during the walk; no separate schema phase.

**Source files:** `Match.java`, `Operation.java`, `Result.java`, `Value.java`, `Validators.java`, `MatchContext.java`.

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
| **Info** | `env`, `os`, `properties`, `config`, `feature`, `scenario`, `suite`, `tags`, `tagValues` |
| **Driver** | `driver` (lazy getter — JS-side equivalent of the `* driver ...` step; re-inits cleanly after `driver.quit()`) |
| **System** | `sysenv(name [, default])`, `sysprop(name [, default])` |
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

### ProcessHandle infrastructure guarantees

`io.karatelabs.process.ProcessHandle` is the single child-process abstraction
used by `karate.exec`, `karate.fork`, `CdpLauncher` (Chrome), and `W3cDriver`
(chromedriver / geckodriver / safaridriver / msedgedriver). Three guarantees
hold for every consumer — no per-call-site code required:

- **Stream draining.** Stdout / stderr are pulled by virtual-thread readers as
  soon as the process starts. Virtual threads are daemons by JEP 444 guarantee,
  so they never keep the JVM alive. Chatty children (chromedriver under
  `--verbose`, geckodriver, any noisy fork) cannot block by filling the OS
  pipe buffer.
- **JVM-exit cleanup.** Every started handle registers in a static
  `LIVE_HANDLES` set; a `Runtime.addShutdownHook` callback iterates the set
  and `destroyForcibly()`s any survivor. Covers clean exit, Ctrl-C, OOM, and
  `kill` — a forked process can't be orphaned even on abnormal JVM
  termination. Removal is idempotent (on `close()` and on natural process
  exit), so the set doesn't bloat over a long-lived JVM.
- **Argv-safe construction.** `ProcessBuilder.command().add(String)` does not
  split on whitespace, so every consumer must pass each argv token as a
  separate string. Format strings that bake the value into the same token as
  the flag must use `--flag=value` form, never `--flag value` (the latter
  becomes a single unrecognised token). The W3C driver's port arg format is
  the canonical example — see `W3cBrowserTypeTest` for the enforced
  invariant.

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
│   ├── OUTLINE_ENTER            (once per Scenario Outline section, before its examples)
│   ├── SCENARIO_ENTER
│   │   ├── STEP_ENTER
│   │   │   ├── HTTP_ENTER → HTTP_EXIT
│   │   └── STEP_EXIT
│   └── SCENARIO_EXIT
└── FEATURE_EXIT
SUITE_EXIT
```

`OUTLINE_ENTER` fires once per outline section, the first time one of its generated examples is about to run. There's no `OUTLINE_EXIT` — outline completion is implied by the last outline-example's `SCENARIO_EXIT`. Outline-example scenarios reference their parent outline via `outlineSlug` on the SCENARIO_ENTER/EXIT data.

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

### Failure hooks

When a Gherkin step fails, `ScenarioRuntime.runStepFailurePipeline` fans out to three sinks in order:

1. **Built-in defaults.** Driver `screenshotOnFailure` (default `true`) — captures a PNG and attaches it directly to the failed `StepResult`. The enabled flag is resolved from the live `configure driver` map first, falling back to the driver instance's frozen options, so per-scenario overrides win under pooled-driver reuse. Capture errors are swallowed with a warn — a dead browser must never escalate into a second scenario failure.
2. **User DSL hook: `configure onStepFailure`.** A JS function called with one info-map argument:
   ```js
   karate.configure('onStepFailure', function(info) {
     // info.error           — failure message
     // info.step            — { line, text, prefix }
     // info.scenarioName    — current scenario name
     // info.featureName     — current feature name
     // info.embed(bytes, mime, name?)  — attach to the failed step
     // info.proceed()       — per-step override: soft-assert this failure
     // info.stop()          — per-step override: hard-stop this failure
   })
   ```
   `info.proceed()` / `info.stop()` give the hook the per-step decision power that the static `configure continueOnStepFailure` flag cannot — last call wins; if neither is called, the runtime falls back to the static config. Hook exceptions are caught and warn-logged.
3. **Bus event `ErrorRunEvent`.** Fired on the `RunListener` bus for programmatic observers (debuggers, IDE plugins, JSONL streams). Skipped for `@report=false` scenarios so sensitive content stays out of report artefacts.

The pipeline fires only at the **innermost** failure: a `call` step whose callee already ran the pipeline (signalled by `StepResult.hasCallResults()`) skips built-in screenshot and user hook, mirroring v1's `isWithCallResults` guard. `ErrorRunEvent` still fires at every level so observers always see the failure surface.

**Source files:** `ScenarioRuntime.runStepFailurePipeline`, `KarateConfig.onStepFailure`, `ErrorRunEvent.java`.

---

## Configuration

`KarateConfig` is the single source of truth for every `configure ...` key — `proxy`, `ssl`, `readTimeout`, `connectTimeout`, `followRedirects`, `auth`, `retry`, `httpRetryEnabled`, `localAddress`, `charset`, `headers`, `cookies`, `logging`, `report`, `callSingleCache`, `driver`, `continueOnStepFailure`, lifecycle hooks (`beforeScenario`, `afterScenario`, `afterScenarioOutline`, `afterFeature`, `onStepFailure`), channel options (kafka/grpc/websocket), and execution flags. `KarateConfig.configure(key, value)` is the *only* place that parses key names; the HTTP client and `LogContext` are projections that read typed getters.

### Projection points

| Sink | Method | When |
|------|--------|------|
| `HttpClient` | `HttpClient.apply(KarateConfig)` | `KarateConfig.configure` returns `true` (client-affecting key), and at every inheritance / restore site below. |
| `LogContext` | `KarateConfig.applyLoggingToContext(LogContext)` | At scenario entry (`ScenarioRuntime.call()`), so mask + pretty set in `karate-config.js` survive the thread-local reset. |

`HttpClient.apply` is the entire interface contract for client setup — no per-key dispatch. `ApacheHttpClient.apply` reads `config.getProxyUri()`, `config.isSslEnabled()`, etc., into local fields and nulls its cached `CloseableHttpClient` to trigger a lazy rebuild on the next `invoke()`. Each `ScenarioRuntime` constructs a fresh `HttpClient` via `Suite.httpClientFactory` (default: `DefaultHttpClientFactory` → one `ApacheHttpClient` per scenario), so the projection has to fire for *every* scenario, including called features.

### Inheritance and propagation

Variables and configuration have different scope semantics for `call read(...)`:

| Direction | Variables | Configuration (proxy, ssl, timeouts, …) |
|-----------|-----------|-----------------------------------------|
| Down (caller → callee) | Copied (isolated) or shared (shared scope) | **Always copied**, regardless of scope |
| Up (callee → caller) | Returned as result map (isolated) or shared (shared) | **Shared scope only** |

This is intentional: a `def foo = call bar` (isolated) explicitly opts out of variable mutation but still needs the caller's proxy/SSL/auth to reach the callee's HTTP client (issue #2839).

Three sites push the typed `KarateConfig` to the relevant `HttpClient` after `copyFrom`:

- `ScenarioRuntime.inheritConfigFromCaller` — caller → callee on `call read(...)`. Both scopes.
- `StepExecutor.propagateFromCallee` — callee → caller on shared scope. Isolated scope skips this by design.
- `StepExecutor.applyCachedCallOnceResult` — restores `KarateConfig` (and re-projects to the client) when replaying a cached `callonce`.

Mid-test `* configure ...` mutations are auto-snapshotted at scenario entry and restored in the `finally` of `ScenarioRuntime.call()` so they don't leak into the next scenario.

**Adding a new `configure` key:** add the field + typed getter to `KarateConfig`, add a `case` in `KarateConfig.configure(...)`, and if it affects HTTP client state, return `true` (rebuild required) and read it in `ApacheHttpClient.apply`. Nothing else dispatches on key name.

**Source files:** `KarateConfig.java`, `HttpClient.java`, `ApacheHttpClient.apply`, `ScenarioRuntime.inheritConfigFromCaller` / `configure`, `StepExecutor.propagateFromCallee` / `applyCachedCallOnceResult`.

### `configure continueOnStepFailure`

Boolean. When `true`, a failing step (`match`, `assert`, also a `beforeScenario` hook throw) is *deferred* — the runtime records it but execution continues into the next step. When `false` (default), the first failure stops the scenario as usual.

Semantics:

- Only the **first** deferred failure's error is retained; later failures while the flag is `true` are continued past but do not overwrite the captured error.
- Flipping the flag **back to `false`** mid-scenario with `* configure continueOnStepFailure = false` surfaces accumulated failures immediately at that step — subsequent steps do not run.
- If the flag is still `true` at scenario end and any failure accumulated, the scenario is marked failed with the first captured error.
- Honoured by the `beforeScenario` hook path (`ScenarioRuntime` line ~934): a hook throw does not stop the scenario when the flag is `true`.
- Like every other `configure` key, snapshotted at scenario entry and restored on exit — does not leak across scenarios.

**v2 simplification.** v1 had a per-keyword list (`continueAfter`); v2 is a plain boolean. For dynamic per-step decisions (the use case `continueAfter` originally covered), install an [`onStepFailure`](#failure-hooks) hook and call `info.proceed()` / `info.stop()` — the hook overrides the static flag on a per-failure basis.

**Source files:** `KarateConfig.continueOnStepFailure`, `ScenarioRuntime.call` (step loop + `configure` override), `ScenarioRuntime.runStepFailurePipeline` (per-step override resolution).

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
{"type":"FEATURE_ENTER","timeStamp":1747555200010,"threadId":"worker-1","data":{path,slug,name,description,tags,line,callDepth}}
{"type":"OUTLINE_ENTER","timeStamp":1747555200015,"threadId":null,"data":{feature,slug,name,description,line,numExamples,tags,callDepth}}
{"type":"SCENARIO_ENTER","timeStamp":1747555200020,"threadId":"worker-1","data":{feature,slug,name,description,line,refId,callDepth,tags,isOutlineExample,exampleIndex,outlineSlug}}
{"type":"SCENARIO_EXIT","timeStamp":1747555200100,"threadId":"worker-1","data":{...same+passed,skipped,durationMillis,error}}
{"type":"FEATURE_EXIT","timeStamp":1747555200200,"threadId":"worker-1","data":{...FeatureResult.toJson()}}
{"type":"SUITE_EXIT","timeStamp":1747555210000,"threadId":null,"data":{"summary":{...}}}
```

**ENTER and other non-EXIT events are intentionally thin — identity + metadata only.** The heavy payload (every scenario, every step, every named embed) lands exactly once, on `FEATURE_EXIT.data` via `FeatureResult.toJson()`. Streaming consumers tail the lightweight envelope events for live progress; offline / aggregator consumers read `FEATURE_EXIT` for full results. This is a load-bearing rule, not an accident — duplicating step / embed data onto SCENARIO_EXIT or OUTLINE_ENTER would either double on-wire bytes for HTTP-heavy runs or force receivers to de-duplicate. New event types added later must honour the same split.

Identity (`slug`) is computed once per node and is intended to be cross-run stable: feature path for features; `<feature-path>:<name>` (or `::L<line>` if unnamed) for scenarios and outlines; `<outline-slug>:<exampleIndex>` for outline-examples. `outlineSlug` on a SCENARIO_ENTER/EXIT lets receivers stitch outline-examples back to their `OUTLINE_ENTER` event without denormalising outline metadata into every example. `tags` on scenarios are the *effective* list (feature + scenario tags merged); on outlines they merge feature + outline tags. See `RunUtils` for the slug formulas.

`FEATURE_EXIT.data` is the full `FeatureResult.toJson()` — the canonical structured payload for offline analysis, CI/CD scraping, and downstream tooling. `SUITE_EXIT.data.summary` carries pass/fail counters, suite-level `startTime`/`endTime`/`durationMillis`, and a `passedRate` (integer percentage 0–100, or null when no scenarios executed). The same `passedRate` is exposed per feature on `FEATURE_EXIT.data` so dashboards don't have to recompute it. Denominator is `passedCount + failedCount` (matching the HTML report's totals row); since `@skipped` is additive to `passedCount`, it's also counted in the denominator. The suite-level epoch markers are co-located with `durationMillis` so a single read of `summary` gives consumers both absolute wall-clock anchors and the relative duration — without falling back to per-step `result.startTime`/`endTime` pairs, which only ever span a single step.

`STEP_ENTER` / `STEP_EXIT` / `HTTP_ENTER` / `HTTP_EXIT` events fire on the `RunListener` bus but are deliberately not emitted into JSONL (too granular for a streaming feed). HTTP request/response detail still reaches consumers via `step.embeds[]` inside `FEATURE_EXIT`.

**Where named embeds live on the wire (and why).** Step embeds — including ext-emitted named entries like `openapi-match`, `grpc-match`, `http-exchange` — appear **only** at `FEATURE_EXIT.data.scenarioResults[i].stepResults[j].embeds[]`. They are deliberately **not** duplicated onto `SCENARIO_EXIT.data`. The rationale is bandwidth: `FEATURE_EXIT` already serializes the full `FeatureResult.toJson()` (which transitively walks every scenario's step results with their embeds), so a parallel `SCENARIO_EXIT.embeds[]` would either ship every embed twice for typical runs or force receivers to de-duplicate. Receivers wanting per-scenario embeds traverse `FEATURE_EXIT.data.scenarioResults[]` and key by `scenarioResults[i].refId` or `name`. Embeds use the canonical wire shape `{name, parts: [{role, mime, data (base64) | url | file}], meta}` (see `StepResult.Embed.toMap`). A single-asset embed (screenshot, `doc`, `karate.embed`) is one `"primary"` part; a multi-asset embed (e.g. image-comparison: baseline/current/diff) carries several. `data` is inline base64, `url` points at an ext-written asset, `file` is set once core writes inline bytes to `embeds/`. An ext that wants a JSON payload base64-encodes the JSON bytes into a part with `mime: "application/json"`.

### Outbound delivery

The on-disk JSONL stream is the canonical outbound surface. There is no built-in
push-to-HTTP transport in karate-core — an earlier `boot.ext('agent')` client was
removed pending a redesign focused on report-aggregator-style consumption rather
than per-event POSTs. Teams that need a live feed can drop a `RunListener` via
`Runner.listener(...)` (or a future ext) to forward events wherever they want;
the JSONL file remains the source of truth for offline / async ingestion.

**Source files:** `HtmlReportListener.java`, `HtmlReportWriter.java`, `CucumberJsonWriter.java`, `JunitXmlWriter.java`, `JsonLinesEventWriter.java`

---

## Ext Architecture

> **Authoring an ext?** See [EXT.md](./EXT.md) — the SPI reference for the types an ext
> implements/calls (globals, `ReportAssets`, embeds, the `KarateReport.registerEmbed` UI
> hook). This section covers the `karate-boot.js` activation surface + lifecycle.

| Interface | Purpose | Discovery |
|-----------|---------|-----------|
| `CommandProvider` | CLI subcommands | ServiceLoader (`~/.karate/ext/` JARs) |
| `HttpClientFactory` | Custom HTTP clients | Constructor injection |
| `RunListener` | Event listeners | `Runner.listener()` or `--listener` CLI |
| `RunListenerFactory` | Per-thread listeners | `Runner.listenerFactory()` |
| `Ext` | **Suite-lifetime singletons configured from `karate-boot.js`** | Name convention via `boot.ext('name')` |
| `ReportWriterFactory` | Custom report formats | ServiceLoader (planned) |

### `Ext` + `karate-boot.js`

A second activation surface (coexisting with `karate.channel(...)`). Exts are
singletons-per-Suite that observe the run via `RunListener`. They are configured
declaratively from a `karate-boot.js` file at the workdir root, evaluated **once
per Suite** before `SUITE_ENTER` fires.

```js
// karate-boot.js — runs once per Suite; cannot contribute variables to test scope
const openapi = boot.ext('openapi');
openapi.path = 'api/openapi.yaml';
openapi.excludes = ['/health/**'];
```

**Resolution.** `boot.ext('foo')` looks up
`io.karatelabs.ext.foo.FooExt` on the classpath (name convention). Missing
class → boot-time failure that fails the Suite loud.

**`boot.*` namespace** — the only API surface inside `karate-boot.js`:

| Member | Purpose |
|---|---|
| `boot.env` | Value of `karate.env` (CLI `-e` flag). |
| `boot.sysenv(name [, default])` | Read an OS environment variable; falls back to `default` when unset or empty. |
| `boot.sysprop(name [, default])` | Read a JVM system property; reads from the Suite's merged property map (CLI `-D` plus `Runner.Builder.systemProperties`) when available. |
| `boot.read(path)` | Read a text file relative to workdir (e.g. an OpenAPI spec). |
| `boot.log(msg)` | INFO log with `[boot]` prefix. |
| `boot.ext(name)` | Construct + register an ext; returns the instance for configuration. |

**Lifecycle.**

1. `karate-boot.js` evaluates top-to-bottom. Each `boot.ext('name')` call
   constructs the ext, fires its `onBoot(Suite)`, and registers it as a
   `RunListener` on the Suite.
2. Property setters validate eagerly — e.g. `openapi.path = '/no/such/file'`
   throws on the line itself, before any tests run.
3. `SUITE_ENTER.data.exts[]` carries each ext's `getManifest()` so receivers
   know which exts were active and with what config.
4. Exts see every event from `SUITE_ENTER` through `SUITE_EXIT` via
   `onEvent(RunEvent)`.
5. After `SUITE_EXIT`, each ext's `onShutdown()` fires.

**Failure mode.** Exceptions during `onBoot` fail the Suite. Exceptions inside
`onEvent` are logged WARN and dropped — the run continues, that signal is lost.

**Cross-ext coordination.** Exts do not call each other directly. They
contribute via the existing `step.embed(name, payload)` mechanism (the same
channel HTTP-exchange data already uses). Embeds ride on
`FEATURE_EXIT.data.scenarioResults[].stepResults[].embeds[]`; multiple exts
each write their own named embed (e.g. `step.embed('openapi-match', {...})`,
`step.embed('image-comparison', {...})`) and receivers decode by name.

**Mock-server mode** (`karate.start({mock: ...})`) suppresses `karate-boot.js`
loading entirely — mock servers aren't tests, so exts don't activate.

**Source files:** `Ext.java`, `BootBinding.java`, `BootLoader.java`,
`Suite.java` (loader hook + ext registration / shutdown).

---

## Deep-Dive Docs

| Doc | Covers |
|-----|--------|
| [CLI.md](./CLI.md) | Two-tier CLI (Rust launcher + Java), subcommands, karate-pom.json |
| [EXT.md](./EXT.md) | Ext SPI — authoring an extension: globals, report assets, embeds, the `registerEmbed` UI hook |
| [JS_ENGINE.md](./JS_ENGINE.md) | Type system (JsValue hierarchy), Java interop, prototypes |
| [DRIVER.md](./DRIVER.md) | Browser automation — CDP, W3C WebDriver, frame/window management |
| [MOCKS.md](./MOCKS.md) | Mock server — feature-based definitions, proxy mode, stateful mocks |
| [GATLING.md](./GATLING.md) | Performance testing — Java DSL, session chaining, HTTP pooling |
| [TEMPLATING.md](./TEMPLATING.md) | HTML templating — Thymeleaf + JS expressions, HTMX, server/static modes |
| [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) | V1 → V2 migration guide |
| [RELEASING.md](./RELEASING.md) | Release checklist |

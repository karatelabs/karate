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

### Suite isolation

A Suite's inputs — `karate.env`, the merged system-property map, and the env overlay (`Runner.Builder.systemEnv`) — are fixed when it is built and read from the Suite, never back from JVM globals, so Suites run side by side in one JVM without sharing state. A host hook (listener, client factory, ext) is Suite-scoped, never static. Known exception: `Runner.java` (~1040) still runs `systemProperties.forEach(System::setProperty)` — tracked in karate-ext `docs/BACKLOG.md`, "`Runner` writes the builder's system properties into JVM globals, breaking Suite isolation".

A file's bytes can be a Suite input too: `Runner.Builder.overlay(path, bytes)` runs the feature at `path` from `bytes` (relative refs, config discovery, reports and the feature id resolve from `path`, which need not exist), and a `path` the Suite loads as a config file (`karate-config.js`, `karate-config-<env>.js`, `karate-base.js`) is read from its bytes the same way; neither file is read or written, so a concurrent run of the same project sees the disk.

A mock server `karate.start()` opens is owned by the top-level scenario that started it (from its Background, steps, config, a feature it called, or an `@setup` scenario `karate.setup()` ran for it) and stopped (`ScenarioRuntime.stopServers`) when that scenario ends, after `afterScenario` — so a Background start on a fixed port binds again for the next scenario. A start inside `callonce`, `setupOnce` or `callSingle` (detected by the lock that call holds on the thread) is reused by later scenarios, so it belongs to the Suite alone (`KarateJs.ServerOwner`). Every one is also held by the Suite running on that thread (a nested Suite's config counts for the nested Suite, and a mock's Background starting another mock counts for the owner of the first) and stopped (`stopAndWait`) at the end of `Suite.run()` if nothing stopped it sooner, after the features, ext `onShutdown` and the result listeners, even when one of those throws — whether started by a scenario or by `callSingle` from config, since that cache is the Suite's too. A host that needs them longer takes them with `Suite.detachServers()` (e.g. from a `SUITE_EXIT` listener) and stops them itself. Under a host-injected `callSingleCache` (karate-gatling) a server started inside `callSingle` is left alone: it serves every Suite sharing that cache, so it lives as long as the cache.

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

**Schema escape hatches.** `##(expr)` means "inline, drop the key if null" when building data but *optional* when asserting, so a nullable nested schema needs a path that reaches the match engine un-substituted. Two exist: `def schema = ({ ... })`, which walks with `forMatch = true` (see `isParenWrappedJson`), and `json schema = karate.readAsString(...)`, where `executeJson` deliberately does not walk a string RHS at all (v1-exact — the JSON twin of `text` then `xml`). Adding a walk back to `executeJson` re-breaks the second one; a literal or doc-string RHS already gets one inside `evalKarateExpression`.

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
| **Assertions** | the `match` keyword, `karate.match()`, `expect()` (Chai-style BDD API) |
| **Process** | `exec()`, `fork()`, `signal()`, `waitForHttp()`, `waitForPort()` |
| **Mock** | `start()`, `proceed()`, `stop()` |
| **Test data** | `faker.*` (names, emails, addresses, numbers, timestamps, etc.), `uuid()` |
| **Logging** | `log()`, `logger.debug/info/warn/error()`, `embed()` |
| **Info** | `env`, `os`, `properties`, `config`, `feature`, `scenario`, `suite`, `tags`, `tagValues` |
| **Driver** | `driver` (lazy getter — JS-side equivalent of the `* driver ...` step; re-inits cleanly after `driver.quit()`) |
| **System** | `sysenv(name [, default])` (the Suite's env overlay, then the OS environment), `sysprop(name [, default])` |
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
* configure auth = { type: 'oauth2', grantType: 'client_credentials', accessTokenUrl: '...', clientId: '...', clientSecret: '...' }
* configure auth = { type: 'oauth2', grantType: 'password', accessTokenUrl: '...', username: '...', password: '...', clientId: '...', clientSecret: '...' }
* configure auth = { type: 'digest', username: 'user', password: 'pass' }
* configure auth = { type: 'aws', accessKey: '...', secretKey: '...', sessionToken: '...', region: 'us-east-1', service: 's3' }
```

`oauth2` (`ClientCredentialsAuthHandler`, or `PasswordAuthHandler` for `grantType: 'password'`) fetches its token on the first request and sends `Authorization: Bearer <token>`; `scope` is optional, `clientId`/`clientSecret` too for the password grant, and a relative `accessTokenUrl` resolves against the request's host. `clientAuthentication: 'header'` sends the client's credentials to the token endpoint as HTTP Basic (RFC 6749 §2.3.1) instead of in the form (`'body'`, the default). The handlers' own config names these `url`, `client_id`, `client_secret` and `client_authentication`. `AuthorizationCodeAuthHandler`'s code exchange and `OAuth2TokenManager`'s refresh read `client_authentication` the same way. The password grant refreshes an expired token with its refresh token, or signs in again. A cURL preview shows `<your-oauth2-access-token>` and fetches nothing.

`digest` answers the server's `401` Digest challenge (qop `auth`, MD5 or SHA-256) in `ApacheHttpClient` and never answers a Basic challenge with the password; a wrong password surfaces as the server's `401`. The credentials (NTLM's too) are scoped to the request's own scheme, host and port and to that challenge scheme, so a proxy's `407` or a host reached by redirect never receives them; proxy credentials are scoped to the proxy.

`aws` is Signature Version 4 (`AwsSigV4AuthHandler`): signed at send time over the final method, path, query, headers and body; `sessionToken` is optional; `service: 's3'` also sends `x-amz-content-sha256` and signs the path as sent (no normalization, no second encoding). Hop-by-hop and proxy headers (`transfer-encoding`, `connection`, `expect`, ...) are left out of the signature, a non-ASCII header value is refused before signing, and a redirect is returned to the caller, never followed, because the signature covers the URI it was made for.

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

## Threading & Lifecycle

The conventions every thread-owning component follows today, and where the
seams are. (Surveyed 2026-08-12 across karate, karate-ext, and veriquant.)

**The daemon + named contract — `ThreadUtils` (in karate-js,
`io.karatelabs.common`).** Every long-lived thread comes from
`ThreadUtils.daemonFactory(prefix)` (numbered, for pools) or
`daemonFactory(name, false)` (stable name, single-thread executors). The
javadoc there is the contract: owners call `close()`/`shutdownNow()`
explicitly, and if they don't, the daemon flag guarantees the JVM still
terminates. Consequence: **a leaked executor never hangs an app at exit,
but in-flight work is silently abandoned** — graceful drain is the owner's
job, not the platform's.

**Per-class active-set + `closeAll()` idiom.** Components that can
accumulate live instances self-register in a private static set and expose
a bulk stop: `HttpServer.ACTIVE_SERVERS` + `shutdownAll()` (Netty event
loop groups, `stopAsync()` / `stopAndWait()` — shut down with **no quiet
period**, the 15 s timeout bounding in-flight work, so a blocking stop is
milliseconds and the port is free when it returns), `WsClient.ACTIVE_CLIENTS` +
`closeAll()` (+ a shared `ws-callback-` pool, per-connection executor
injectable via `WsClientOptions`), `CdpDriver`/`CdpLauncher.closeAll()`,
`ProcessHandle.LIVE_HANDLES` (the one with its own JVM shutdown hook — see
[ProcessHandle infrastructure guarantees](#processhandle-infrastructure-guarantees)).
Nothing inside karate calls these bulk stops — **embedders aggregate them**
(e.g. veriquant's `KarateAppService.shutdown()` runs
`HttpServer.shutdownAll()` then drains its own executor).

**Scenario parallelism is scoped, not global.** `Suite.runParallel()` opens
a `Executors.newVirtualThreadPerTaskExecutor()` in try-with-resources,
publishes it as `getScenarioExecutor()`, and `FeatureRuntime` gates it with
a `Semaphore` + lane queue. Java 21 is the floor across all three repos and
virtual threads are used liberally; new async work should assume them.

**Cancellation of running JS is cooperative.** The engine polls
`Thread.interrupt()` at loop tops (`Interpreter.checkInterrupted` →
`EngineInterruptedException`); bounded evaluation is built on it
(karate-ext `Sandbox.TimeBox`: single-thread daemon executor,
`future.get(timeout)` → `cancel(true)`).

**The unified seam — `Stoppable` + `KarateLifecycle`
(`io.karatelabs.common`, karate-js).** One documented way to enumerate
everything running and drain it. `Stoppable extends AutoCloseable` —
`lifecycleName()` / `lifecycleKind()` / `stop()` (graceful, blocking,
idempotent) / optional `lifecycleExecutor()` for pool inspection.
`KarateLifecycle` — identity-based, insertion-ordered registry with phases
`RUNNING → SHUTTING_DOWN → STOPPED` (+ `reset()`); `running()` snapshot;
`shutdownAll(Duration)` treats the duration as an **enforceable total
deadline** (each `stop()` runs on an interruptible worker bounded by the
remaining budget — a hung component forfeits only its slice and is marked
`TIMED_OUT`), stops in reverse registration order, and returns a
per-component `StopResult` summary. External concurrent callers join the
shared result; a call originating inside the shutdown worker returns a
non-blocking in-progress summary (no self-join). `register()` during/after
shutdown stops the arrival via the bounded path. `installShutdownHook()`
is opt-in for CLI entry points; `wrap(name, kind, executor)` adapts
pool-only owners. `HttpServer` / `WsClient` register themselves (their
static `shutdownAll()`/`closeAll()` are now registry-backed views with
their original async/immediate semantics); the async `js-timer` scheduler
registers on first use. karate-ext and veriquant components can adopt the
same interface at their own pace — until then their stop stories remain
per-component (`stop`/`close`/`shutdown` naming varies). Pinned by
`KarateLifecycleTest` and `HttpServerLifecycleTest`.

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
│   │   │   ├── STEP_LOG         (IDE profile only: a chunk of the step's output, as written)
│   │   │   ├── HTTP_ENTER → HTTP_EXIT
│   │   └── STEP_EXIT
│   └── SCENARIO_EXIT
│   PROGRESS                     (after each top-level scenario)
└── FEATURE_EXIT
SUITE_EXIT
RUN_COMPLETE                     (after every cleanup and report write — the last event)
```

`OUTLINE_ENTER` fires once per outline section, the first time one of its generated examples is about to run. There's no `OUTLINE_EXIT` — outline completion is implied by the last outline-example's `SCENARIO_EXIT`. Outline-example scenarios reference their parent outline via `outlineSlug` on the SCENARIO_ENTER/EXIT data, and carry `exampleTableIndex` beside `exampleIndex` (the row index restarts per `Examples:` table).

`SUITE_EXIT` closes execution (the result is final); `RUN_COMPLETE` follows the driver and mock teardown, ext shutdown and every `ResultListener.onSuiteEnd` (the report writers), so a consumer that opens the report waits for it. `RUN_CANCEL` fires once, on the thread that called `Suite.cancel(reason)`, and never after `RUN_COMPLETE`: completion waits for a cancel that is publishing, and a cancel after completion returns false.

### IDE event profile

`Runner.Builder.ideEvents(true)` is the profile an IDE streaming a run turns on: every event is dispatched under one lock and stamped with a run-wide `sequence` (`RunEvent.getSequence()` — 1-based, gap-free across threads, in delivery order) and `STEP_LOG` chunks fire as each step's output is written, before that step's `STEP_EXIT`. Listeners run under that lock: one that blocks stalls every producer, and one that waits for another producer's event deadlocks. An event a listener fires from inside `onEvent` (a `Suite.cancel` from a listener) is queued and delivered — with its sequence — once the current event has reached every listener, so no listener sees N+1 before N; a listener that throws does not strand what it queued (the queue is drained, the first failure rethrown after). A step's output outside any step (karate-config.js, a hook) is a `STEP_LOG` with no step; the execution index is allocated before config evaluates, so config-time output and calls resolve to their scenario. `JsonLinesEventWriter.envelope(event)` is the wire form every consumer shares: `runId` (`Runner.Builder.runId`, else a UUID), `sequence`, `executionIndex` (the scenario an event belongs to) and `parentExecutionIndex` + `parentStepLine` (the scenario and step that called a nested feature, outline or scenario).

### Cancel

`Suite.cancel(reason)` — idempotent, distinct from `Suite.abort()` (the `abortSuiteOnFailure` stop, which lets the running scenario finish) — stops work in flight: every scenario, nested call and `call` loop at its next step boundary, an HTTP call in flight by aborting it (`HttpClient.abort()` on every live scenario's client), a `retry until` poll by waking its sleep, config and `callonce` setup through the nested call they make. An unfinished scenario is recorded **cancelled** (`ScenarioResult.isCancelled()`, the step in flight `StepResult.Status.CANCELLED` — whether it threw or returned after the cancel landed — the rest skipped), never passed; a scenario that had already failed stays failed, otherwise cancelled is the whole verdict (`isFailed()` false, no `@fail` inversion), so `FeatureResult`/`SuiteResult.isPassed()` are false and each scenario counts once. The scenario's HTTP client refuses every call after the cancel (`HttpClient.cancel()`, sticky where `abort()` is one-shot; a client that wraps another must delegate it), so a script that catches the aborted request cannot send another. Cancel outranks `abortSuiteOnFailure`: an abort already in force does not stop the enumeration. A scenario or feature not yet started when cancel lands still gets its cancelled result, with `SCENARIO_ENTER`/`SCENARIO_EXIT` fired back to back (config is not evaluated, no step runs): the run enumerates exactly what it would have selected — the same count `Suite.getScenarioTotal()` pre-computes from `FeatureRuntime.countSelected()` — so for a cancelled run `getScenarioTotal() == passed + failed + cancelled` (a skipped scenario counts as passed). The one exception is a dynamic `Examples:` table, whose rows exist only once its expression runs: it counts as one and records one cancelled result, the outline's own (`exampleIndex` -1, the outline's name). The suite result, the JSONL summary and the HTML report all say cancelled, with the reason.

Return `false` from `SCENARIO_ENTER` / `STEP_ENTER` / `HTTP_ENTER` to skip execution — `SUITE_ENTER` and `FEATURE_ENTER` fire for observation only and their return value is ignored. Events fire for **all** features including `call`ed ones — use `event.source().isTopLevel()` to filter.

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

`KarateConfig` is the single source of truth for every `configure ...` key — `proxy`, `ssl`, `readTimeout`, `connectTimeout`, `followRedirects`, `strictResponseParsing` (the response body converts per its DECLARED Content-Type only — no first-character sniffing; default false keeps the V1-compatible sniff), `auth`, `retry`, `httpRetryEnabled`, `localAddress`, `charset`, `headers`, `cookies`, `logging`, `report`, `callSingleCache`, `driver`, `continueOnStepFailure`, lifecycle hooks (`beforeScenario`, `afterScenario`, `afterScenarioOutline`, `afterFeature`, `onStepFailure`), channel options (kafka/grpc/websocket), and execution flags. `KarateConfig.configure(key, value)` is the *only* place that parses key names; the HTTP client and `LogContext` are projections that read typed getters.

### Projection points

| Sink | Method | When |
|------|--------|------|
| `HttpClient` | `HttpClient.apply(KarateConfig)` | `KarateConfig.configure` returns `true` (client-affecting key), and at every inheritance / restore site below. |
| `LogContext` | `KarateConfig.applyLoggingToContext(LogContext)` | At scenario entry (`ScenarioRuntime.call()`), so mask + pretty set in `karate-config.js` survive the thread-local reset. |

`HttpClient.apply` is the entire interface contract for client setup — no per-key dispatch. `ApacheHttpClient.apply` reads `config.getProxyUri()`, `config.isSslEnabled()`, etc., into local fields and nulls its cached `CloseableHttpClient` to trigger a lazy rebuild on the next `invoke()`. Each `ScenarioRuntime` constructs a fresh `HttpClient` via `Suite.httpClientFactory` (default: `DefaultHttpClientFactory` → one `ApacheHttpClient` per scenario), so the projection has to fire for *every* scenario, including called features.

Cookies are Karate's to manage (`cookie`, `cookies`, `responseCookies`): the client's own Apache cookie store is cleared after every request, failed ones included, and only the cookies this exchange's redirect hops set are merged into the final response's `Set-Cookie`. `new ApacheHttpClient(cookieStore)` takes a caller's store instead, which `getCookieStore()` returns: it sends its matching cookies, takes every `Set-Cookie`, and is never cleared or replaced by a rebuild. A response's time runs from the request's send to its body fully read.

### Inheritance and propagation

Variables and configuration have different scope semantics for `call read(...)`:

| Direction | Variables | Configuration (proxy, ssl, timeouts, …) |
|-----------|-----------|-----------------------------------------|
| Down (caller → callee) | Copied (isolated) or shared (shared scope) | **Always copied**, regardless of scope |
| Up (callee → caller) | Returned as result map (isolated) or shared (shared) | **Shared scope only** |

This is intentional: a `def foo = call bar` (isolated) explicitly opts out of variable mutation but still needs the caller's proxy/SSL/auth to reach the callee's HTTP client (issue #2839).

**The isolated-call result is a delta — `StepUtils.calleeDelta`, via the one funnel `StepExecutor.calleeResult`.** A called feature runs with the caller's visible variables in scope so it can read them, so its full binding set echoes caller state back; returning that leaks caller locals and config-level refs into the result, into the `callonce` cache, and into the `callSingle` disk cache. The delta is computed against `ScenarioRuntime.getInheritedVariables()` — what the callee was actually *seeded* with, not the caller's live vars, since isolated scope shallow-copies maps and lists and a live-vars comparison reads every inherited map as "replaced". Two rules, unioned, both additive: **identity** (an untouched inherited binding keeps the same object reference) and **`ScenarioRuntime.getAssignedVariables()`**, the names the callee bound for itself through `setVariable` plus the spread call-arg keys. The second rule exists because identity alone cannot see `* def x = seed` when the caller passed its own `x` in as `seed` — the callee's own `def` is identity-equal to the inherited binding and used to vanish from the result. Writes that bypass `setVariable` (raw JS assignment, `karate.set`) are covered only by the identity rule.

Three sites push the typed `KarateConfig` to the relevant `HttpClient` after `copyFrom`:

- `ScenarioRuntime.inheritConfigFromCaller` — caller → callee on `call read(...)`. Both scopes.
- `StepExecutor.propagateFromCallee` — callee → caller on shared scope. Isolated scope skips this by design.
- `StepExecutor.applyCachedCallOnceResult` — restores `KarateConfig` (and re-projects to the client) when replaying a cached `callonce`. This one merges rather than copies: the cache holds the before/after snapshot pair taken around the single real execution, and `KarateConfig.copyChangedFrom` applies only the keys the callee itself changed — the config analogue of the `StepUtils.calleeDelta` rule for variables. A wholesale copy would also stamp the *first* scenario's own config over every later scenario, which is invisible for plain data but wrong for JS-function values: a lifecycle hook or `configure headers` function is re-created per scenario by karate-config.js and is bound to that scenario's JS context, so replaying the first instance froze every later `afterScenario` hook on the first scenario's variables (`karate.get('response')`, `karate.prevRequest`).

Mid-test `* configure ...` mutations are auto-snapshotted at scenario entry and restored in the `finally` of `ScenarioRuntime.call()` so they don't leak into the next scenario.

**Adding a new `configure` key:** add the field + typed getter to `KarateConfig`, add a `case` in `KarateConfig.configure(...)`, mirror it in `copyFrom` *and* `copyChangedFrom`, and if it affects HTTP client state, return `true` (rebuild required) and read it in `ApacheHttpClient.apply`. Nothing else dispatches on key name.

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

**Synthetic steps — `LogContext.get().step(text, passed[, embeds])`.** Symmetric with `embed()`: an out-of-band producer running inside a step (e.g. an ext binding mid-`* eval`) can contribute a **real, named step row** to the current scenario without parsing a `.feature`. Buffered steps are flushed into the scenario's result immediately after the in-flight step completes (`ScenarioRuntime` drains `collectPendingSteps()`), so they render as sibling step rows right where they were produced — in the HTML report and the `FEATURE_EXIT` JSONL, like any step. The status is reported and, because `ScenarioResult.isFailed()` scans the step list, a **failed** synthetic step fails its scenario with no exception — so a producer can signal a pass/fail purely by appending a step. Embeds passed to `step(...)` attach to that synthetic step (not the producing step); `StepResult.synthetic(text, status, startTime, error)` is the escape hatch for a failure message / custom log via `LogContext.get().step(StepResult)`. Built on the same fake-step machinery as `@fail` and lifecycle-hook steps (`StepResult` with a null gherkin `step`).

**Report collection follows the live scenario, not the bridge.** Logs and embeds already go to the thread-local `LogContext`, so they land on whichever scenario is executing — never the runtime captured by a `karate` bridge. Feature-call results (`StepExecutor.addCallResult`, drained into the invoking step) follow the same rule via `ScenarioRuntime.currentOrNull()`, the scenario set on the thread for the duration of `call()`. This matters when a JS helper is defined in one scenario and reused in another — e.g. loaded once with `callSingle` and shared through the config object: its `karate.call()` bridge stays bound to the scenario that won the `callSingle` race, so naive routing attached the called feature to that originating (now finished) scenario and it vanished from every *other* scenario's report (issue #2928). `currentOrNull()` is deliberately **not** consulted by `getRuntime()` — execution and variable scope keep honoring the bridge's captured context, leaving room to honor the closure for future async JS calls.

### `configure logging`

Single bucket for all logging behavior. Deep-merges with parent values so a partial update (e.g., flipping just the level) preserves mask + pretty.

```javascript
configure logging = {
  report:  'debug',         // threshold for report-buffer capture (default DEBUG)
  console: 'info',          // Logback level override for the built-in karate.* categories; unset = logback.xml governs
  pretty:  true,            // pretty-print HTTP req/res JSON bodies (default true)
  mask: {                   // HTTP-only redaction; unset = the report-only defaults, false = off
    defaults:   true,       // also mask Authorization, Cookie, Set-Cookie, Proxy-Authorization in reports
    headers:    ['X-Api-Key'],
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

**`logging.console` levels: `trace` / `debug` / `info` / `warn` / `error` / `off` (alias `none`).** To silence the console completely — including the HTTP request/response one-liners — use `console: 'off'` (or `'none'`). The two are equivalent; `none` is mapped to Logback's `off`. The report buffer is **unaffected** by the console level (it is governed solely by `logging.report`), so silencing the console never removes HTTP traffic from the HTML report.

**Why the console knob fans out to every category.** `LogContext.setRuntimeLogLevel` sets the Logback level on the `"karate"` parent *and* every child category (`karate.runtime`, `karate.http`, `karate.mock`, `karate.server`, `karate.scenario`, `karate.console`) plus `io.karatelabs`. Setting only the parent is not enough: in Logback a child with an explicit level **ignores its ancestor's level**, and users pin children — the per-category recipes in the migration guide and the user docs tell them to (`karate.http` TRACE, `karate.scenario` INFO). The bundled `logback.xml` used to pin them too (`karate.http=DEBUG`, a leftover from before the INFO/DEBUG/TRACE tiering, which made the out-of-the-box console show headers while the docs promised one-liners); it now sets only the `karate` and `io.karatelabs` parents at INFO, so every category inherits and the default console matches the documented one. Before the fan-out, `console: 'warn'`/`'error'`/`'off'` left HTTP lines on the console at every setting (issue #2917). The per-category levels are snapshotted at scenario entry and restored on exit, so a mid-test `configure logging` change does not leak across scenarios. Source: `LogContext.RUNTIME_LOGGERS`, `LogContext.setRuntimeLogLevel`, `LogContext.Snapshot`.

**The fan-out is why `console` and a hand-written `logback.xml` do not compose.** Any `console` value — from `karate-config.js`, `--log-console` or `karate-pom.json` — replaces the user's own per-category pins, so a recipe like "`karate.scenario` INFO, everything else ERROR" only works with `console` unset. `setRuntimeLogLevel` is called only when the key is present (`Runner`, `RunCommand`, `KarateOptionsHandler` all guard on null), so *unset* really does mean "Logback untouched"; the `info` the config map reports as the default is never applied. The precedence rule is: `configure` wins over `logback.xml` when set, and the escape hatch is to not set it — documented in the migration guide next to the Logback recipe.

**Restore covers mid-scenario changes only.** karate-config.js is evaluated in the `ScenarioRuntime` constructor (`initEngine`), and `LogContext.snapshot()` is taken later, in `call()`. A `console` set at config time therefore runs `setRuntimeLogLevel` with no live snapshot to capture the "before", and `Snapshot.restore()` puts back the report threshold *as it was after config* — so config-time `console` / `report` values persist process-wide past the scenario (and past the run, in a JUnit-embedded JVM; `Runner.buildSuite` re-applies the report threshold on the next run, nothing re-applies Logback levels). This is the intended steady state — every scenario evaluates the same config — but it is why tests that drive a karate-config.js with `logging` in it must save and restore the eight `RUNTIME_LOGGERS` levels and `LogContext.getLogLevel()` themselves (`LogReplayerOutputTest` does).

> **HTTP bodies show up in the HTML report by default — you do not need to crank console to TRACE.** Defaults are `report: 'debug'` (≥ INFO captured) and `console: 'info'` (one-liner on stdout). Bodies always land in the report buffer at INFO, so they appear in HTML / JSONL / Cucumber / JUnit regardless of the console level. Only set `console: 'trace'` if you specifically want bodies streaming to stdout — which is rarely what you want for a real test run. **v1 difference:** v1 emitted full bodies to console at DEBUG; v2 reserves DEBUG for headers and TRACE for body. If you used to set `karate.console.log.level=debug` to see bodies in your terminal, switch to looking at the HTML report (or set `console: 'trace'` if you really want it on stdout).

### Where to put `configure logging`

Both forms are supported and both stick across the scenario:

```javascript
// karate-config.js — applies to every scenario in the suite
karate.configure('logging', { mask: { headers: ['X-Api-Key'] }, pretty: true });
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

### Syntax highlighting in HTML reports

The HTML report applies client-side **Prism.js** syntax highlighting to HTTP request/response JSON bodies — independent of pretty-printing, which controls whitespace; highlighting only colors tokens, never reflows. The mechanism is a pair of invisible **in-band sentinels** rather than a structured-log model, so the flat `StepResult.log` contract that JUnit / Cucumber / JSONL depend on is untouched:

- `HttpLogger.logBody` wraps a JSON body in `Console.BODY_OPEN + "json" + BODY_LANG_END + <body> + BODY_CLOSE` (C0 control chars `U+0000`/`U+0001`/`U+0002` — never present literally in JSON/XML/text, which escape them). The lang token (`json` today, `javascript` etc. later) selects the Prism grammar.
- The report buffer keeps the sentinels; **every other consumer strips them.** `Console.stripAnsi` now removes ANSI *and* sentinels (so JUnit / Cucumber / JSONL / `StepResult.toMap` stay clean); `Console.stripSentinels` removes only the markers, keeping ANSI, for the console/SLF4J `TRACE` mirror.
- `Console.splitLog` parses the buffer into ordered segments — `{text}` plain runs (request line, headers) and `{lang, code}` body blocks — that `HtmlReportWriter.buildStepData` emits as `logSegments`. `karate-report.js` renders them into one contiguous `<pre>`, wrapping code blocks in `<code class="language-…">`, then `_highlightCode` runs Prism as Alpine inserts each scenario (piggybacking the deferred-embed MutationObserver; Prism highlights hidden/collapsed detail fine).
- Token colors live in `res/prism-karate.css` keyed on the report's `[data-theme="dark"]` attribute (the same switch Tailwind dark mode uses) and mirror the `AnsiJson` console palette. Because Prism emits class-only spans, the theme toggle re-colors instantly with no JS re-highlight. Prism is vendored at `res/prism.min.js` (core + clike + javascript + json) and loaded `data-manual` since content is injected after page load.

**Source files:** `HttpLogger.logBody`, `Console` (`BODY_OPEN`/`BODY_LANG_END`/`BODY_CLOSE`, `stripAnsi` / `stripSentinels` / `splitLog`), `AnsiJson.java`, `HtmlReportWriter.buildStepData`, `res/karate-report.js` (`_highlightCode`), `res/prism-karate.css`, `res/prism.min.js`.

### Mask scope

`mask` applies to HTTP request/response logging — the `HttpLogger` text, which is the only HTTP-derived content core writes to a report. Masked at the capture, a header or JSON path value reaches no artifact that carries the log: HTML page data, the JSONL stream, Cucumber JSON (where the log travels base64), JUnit XML. It does NOT scan `* print`, `karate.log` or `karate.embed` — user-controlled channels; raise `logging.report: 'warn'` to drop INFO captures, or tag the scenario `@report=false`. `HTTP_ENTER`/`HTTP_EXIT` carry the real request and response (a listener may mock or inspect them) and are not written to JSONL; an ext that builds an embed from them applies the mask itself before serializing — `LogContext.get().getMask()` (null only under `mask: false`), then `enabledForUri(uri)`, `maskHeader(name, value)`, `maskBody(text)`. A value that reaches a file by another road is the [output mask](#output-mask)'s to catch.

### Log Masking — declarative

The `mask` object replaces v1's `HttpLogModifier` Java interface. Compiled once per `configure logging` call into a `LogMask` instance stored on the thread-local `LogContext`. Each `HttpLogger.logRequest/logResponse` reads the current mask and applies:

0. **Defaults** — `Authorization`, `Cookie`, `Set-Cookie` and `Proxy-Authorization` (`LogMask.DEFAULT_HEADERS`) are masked in the **report buffer only**; the console (DEBUG headers, TRACE body) shows them as sent, since it is the developer's own terminal while reports are shared artifacts. With `mask` unset the mask is `LogMask.DEFAULT` (these four, nothing else).
1. `headers` — case-insensitive header-name set; matching headers' values become `replacement` in the report **and** on the console. The list **adds to** the defaults — naming a credential header should never unmask the other three — and a default header named here is masked on the console too.
2. `jsonPaths` — `$.x.y` (descend) and `$..x` (recursive) keys; matched values become `replacement`.
3. `patterns` — regex/replacement pairs applied last, so they catch anything header / JSON-path didn't.
4. `enableForUri(uri)` — optional JS predicate; when it returns falsy, no masking applies for that URL (useful for excluding `/health` so debugging stays easy) — the defaults included.

Opting out: `mask: { defaults: false, ... }` keeps only the user's rules (a map with `defaults: false` and no rule is OFF, with a warn); `mask: false` turns masking off entirely, reports carry the raw values. `mask: null` (or `true`) returns to the defaults. Each `configure logging = { mask }` replaces the previous mask whole — it is the one key the deep-merge does not reach into. For an ext, `LogMask.maskHeader`/`isHeaderMasked` answer for the report (defaults included) and `maskConsoleHeader` for the console; `LogContext.get().getMask()` is null only under `mask: false`. Source: `DefaultReportMaskTest`.

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

All report formats derive from `FeatureResult.toJson()`. HTML uses Alpine.js + Tailwind CSS with inlined JSON, and Prism.js for client-side JSON body syntax highlighting (see [Logging § Syntax highlighting](#syntax-highlighting-in-html-reports)).

**Generation is synchronous, on the feature's own thread.** It used to be async — each listener owned a single-thread executor with an unbounded queue — and that queue was the largest memory problem the engine has had: rendering one feature's HTML cost several times the suite's wall-clock summed over features, so the queue grew for the whole run holding a full page model per entry. Writing inline was measured *faster* (N-way parallelism beats one background thread) as well as bounded. Nothing reads a `FeatureResult` after `onFeatureEnd` returns, and the release below depends on that.

### Two listener interfaces, and why

| | `RunListener` | `ResultListener` |
|---|---|---|
| unit | a `RunEvent`, as it happens | a completed `FeatureResult` / `SuiteResult` |
| timing | live, during execution | at feature end / suite end |
| can control execution? | **yes** — return `false` from `SCENARIO_ENTER` / `STEP_ENTER` to skip, or from `HTTP_ENTER` to mock | no, observation only |
| scope | fires for called features too (`event.source().isTopLevel()` to filter) | top-level only |
| implementations | JSONL stream, IDE runners, debuggers, exts | HTML, Cucumber JSON, JUnit XML |

Control-plane versus artifact-plane. The split is why JSONL is a `RunListener` — it streams events to disk as they happen and never walks the retained result model — while the three file-per-feature formats are `ResultListener`s handed one finished, canonical result.

### What a result stops holding, and when

A `SuiteResult` holds every `FeatureResult` until the run ends, so anything hanging off a step stays reachable for the whole suite. Two releases run in `FeatureRuntime.call()`'s `finally`, **after** `FEATURE_EXIT` has been fired and every `ResultListener` has consumed the feature — so every report is generated from a whole result, and no artifact loses content:

| released | holds | opt out with |
|---|---|---|
| nested `karate.call()` result trees | memory ∝ scenarios × calls per scenario | `Runner.Builder.retainCallResults(true)` |
| each step's captured `log` and `embeds` | the rendered HTTP request/response text, and raw `byte[]` assets such as screenshots — memory ∝ steps × payload size | `Runner.Builder.retainStepLogs(true)` |

The second is the larger by far: measured on a 350,000-scenario soak with all four formats on, roughly 70% of per-scenario retention was captured text, and that share grows with payload size. Both opt-outs exist for the same narrow reason — reading a `SuiteResult` programmatically *after* the run — and neither is exposed on the CLI or in `karate-pom.json`, deliberately: they are not tuning knobs.

**Neither release runs in perf mode.** The Gatling lane builds a throwaway `Suite` per execution, so there is nothing to free — and `logReplay` renders `StepResult.log` *after* `Runner.runFeature` returns (`LogReplayer.render`, from `KarateExecutor.execute`), so releasing would silently empty the replay buffer that is a load tester's only view of a failed scenario.

**Source files:** `FeatureResult.releaseCallResults` / `releaseStepLogs`, `FeatureRuntime.run` (the `finally`), `CallResultReleaseTest`, `StepLogReleaseTest`.

### Output Structure

```
target/karate-reports/
├── karate-summary.html               # Summary dashboard (default)
├── karate-timeline.html              # Gantt-style parallel execution view (default)
├── feature-html/                     # Per-feature interactive reports (default)
├── embeds/                           # Embed parts written out of line (screenshots, text attachments)
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

### Output mask

`Runner.Builder.outputMask(UnaryOperator<String>)` is applied to every report file the Suite writes, at the write: each JSONL line (`JsonLinesEventWriter`), each HTML page's data (`HtmlReportWriter.writeInlined`), each Cucumber JSON and JUnit XML document, and each text embed file under `embeds/` (`HtmlReportWriter.writeEmbedFile`: a part whose mime is not `ResourceType.isBinary` is decoded as UTF-8, masked and re-encoded; a binary part — image, PDF, video, unknown type, or bytes that are not UTF-8 — is written as it was). `configure logging.mask` masks at the capture and is the API for HTTP traffic; `outputMask` is an embedder's backstop for a value that reached the result by another road (a scenario name, a print, an ext's embed). The wire and the in-memory `SuiteResult` are untouched. The contract is on the Builder method: the mask sees serialized text — a value as the format escapes it, and base64 where the format carries bytes (a JSON embed part inline in page data and JSONL; a step log and inline embeds in Cucumber JSON) — and returns text valid in the same format. There is no byte mask: a credential is not a contiguous byte run in a compressed or pixel payload, and splicing bytes into an offset-bearing container (PNG chunks, PDF xref, MP4 atoms) corrupts it. An asset an ext writes itself (`Part.url`, `ext/`) is the ext's to mask. Source: `OutputMaskTest`, `LogMaskReportArtifactsTest`.

### JSON Lines event stream

Written by `JsonLinesEventWriter` (a `RunListener`) to `karate-json/karate-events.jsonl`. One record per line, flushed per write so external tools — IDE test runners, dashboards — can tail the file in real time during the run.

Standard envelope:

```json
{"type":"SUITE_ENTER","timeStamp":1747555200000,"threadId":null,"data":{"schemaVersion":"1","version":"2.0.8","env":"dev","threads":4}}
{"type":"FEATURE_ENTER","timeStamp":1747555200010,"threadId":"worker-1","data":{path,slug,name,description,tags,line,callDepth}}
{"type":"OUTLINE_ENTER","timeStamp":1747555200015,"threadId":null,"data":{feature,slug,name,description,line,numExamples,tags,callDepth}}
{"type":"SCENARIO_ENTER","timeStamp":1747555200020,"threadId":"worker-1","data":{feature,slug,name,description,line,refId,callDepth,tags,isOutlineExample,exampleIndex,outlineSlug}}
{"type":"SCENARIO_EXIT","timeStamp":1747555200100,"threadId":"worker-1","data":{...same+passed,skipped,durationMillis,error (led by path.feature:line + step text),errorReason (error's suffix: the failure without location, step or comment label)}}
{"type":"FEATURE_EXIT","timeStamp":1747555200200,"threadId":"worker-1","data":{...FeatureResult.toJson()}}
{"type":"SUITE_EXIT","timeStamp":1747555210000,"threadId":null,"data":{"summary":{...}}}
```

**ENTER and other non-EXIT events are intentionally thin — identity + metadata only.** The heavy payload (every scenario, every step, every named embed) lands exactly once, on `FEATURE_EXIT.data` via `FeatureResult.toJson()`. Streaming consumers tail the lightweight envelope events for live progress; offline / aggregator consumers read `FEATURE_EXIT` for full results. This is a load-bearing rule, not an accident — duplicating step / embed data onto SCENARIO_EXIT or OUTLINE_ENTER would either double on-wire bytes for HTTP-heavy runs or force receivers to de-duplicate. New event types added later must honour the same split.

Identity (`slug`) is computed once per node and is intended to be cross-run stable: feature path for features; `<feature-path>:<name>` (or `::L<line>` if unnamed) for scenarios and outlines; `<outline-slug>:<exampleIndex>` for outline-examples. A scenario may override its derived slug with a **rename-proof, author-set identity** by binding an `__id` variable — a sibling of `__row`/`__num`, set either by a `* def __id = '<value>'` step or an `Examples:` column literally named `__id` (so each row pins its own id). When `__id` resolves to a non-blank value it is emitted as the `slug` verbatim; otherwise the derived slug stands. `__id` is read at SCENARIO_EXIT construction (while the engine is live) and may contain any non-whitespace characters — `/`, `:`, `.` etc. survive untouched, matching the alphabet allowed in tag values. Unlike a tag-based override, it never co-opts a team's existing tag conventions, because the author binds it on purpose. `outlineSlug` on a SCENARIO_ENTER/EXIT lets receivers stitch outline-examples back to their `OUTLINE_ENTER` event without denormalising outline metadata into every example. `tags` on scenarios are the *effective* list (feature + scenario tags merged); on outlines they merge feature + outline tags. See `RunUtils` for the slug formulas and `ScenarioRunEvent` for the `__id` override.

`FEATURE_EXIT.data` is the full `FeatureResult.toJson()` — the canonical structured payload for offline analysis, CI/CD scraping, and downstream tooling. **`SUITE_EXIT.data` is the summary and nothing else.** It used to serialize `SuiteResult.toJson()`, whose `features` array repeated every scenario and every step of the whole run a second time — breaking the exactly-once rule above, and doing it at the one moment when each feature has already released its step logs, so the duplicate was quietly *thinner* than the `FEATURE_EXIT` records it duplicated. On a large run the saving is the size of the run. `SUITE_EXIT.data.summary` carries pass/fail counters, suite-level `startTime`/`endTime`/`durationMillis`, and a `passedRate` (integer percentage 0–100, or null when no scenarios executed; rounded, but 100 only when nothing failed and 0 only when nothing passed — 3120 of 3121 reads 99). The same `passedRate` is exposed per feature on `FEATURE_EXIT.data` so dashboards don't have to recompute it. Denominator is `passedCount + failedCount` (matching the HTML report's totals row); since `@skipped` is additive to `passedCount`, it's also counted in the denominator. The suite-level epoch markers are co-located with `durationMillis` so a single read of `summary` gives consumers both absolute wall-clock anchors and the relative duration — without falling back to per-step `result.startTime`/`endTime` pairs, which only ever span a single step.

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
| `boot.sysenv(name [, default])` | Read the Suite's env overlay, then the OS environment; falls back to `default` when unset or empty. |
| `boot.sysprop(name [, default])` | Read a JVM system property; reads from the Suite's merged property map (CLI `-D` plus `Runner.Builder.systemProperties`) when available. |
| `boot.read(path)` | Read a text file relative to workdir (e.g. an OpenAPI spec). |
| `boot.log(msg)` | INFO log with `[boot]` prefix. |
| `boot.ext(name)` | Construct + register an ext; returns the instance for configuration. Missing ext ⇒ the Suite fails loudly. |
| `boot.has(name)` | Is this ext on the classpath? A pure probe (constructs nothing, registers nothing) for an ext whose *absence is a valid configuration* — `if (boot.has('grpc')) { … }`. Not a way to soften a dependency the project genuinely needs. |

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
| [DRIVER_PLAN.md](./DRIVER_PLAN.md) | Open CDP driver defects + the CDP extension surface treated as public API |
| [MOCKS.md](./MOCKS.md) | Mock server — feature-based definitions, proxy mode, stateful mocks |
| [GATLING.md](./GATLING.md) | Performance testing — Java DSL, session chaining, HTTP pooling |
| [PROFILING.md](./PROFILING.md) | Profiling runbook — JFR workloads, reading a digest, leak vs churn |
| [TEMPLATING.md](./TEMPLATING.md) | HTML templating — Thymeleaf + JS expressions, HTMX, server/static modes |
| [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) | V1 → V2 migration guide |
| [RELEASING.md](./RELEASING.md) | Release checklist |

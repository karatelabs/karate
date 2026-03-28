# Karate v2 Roadmap

This document tracks the development roadmap for Karate v2. It serves as a persistent reference for contributors and LLM assistants working on the project.

> See also: [Design Principles](./PRINCIPLES.md) | [Capabilities](./CAPABILITIES.md) | [karate-js](../karate-js) | [karate-core](../karate-core)

> **Status Key:**
> - `[ ]` Not started
> - `[~]` In progress
> - `[x]` Complete

---

## Milestone 1: API Testing Release ✅

> **Goal:** Drop-in replacement for Karate 1.x API testing. Existing tests should just work.
> **Status:** Complete. All core API testing capabilities are implemented and tested.

### Gherkin Parser & Scenario Engine ✅

The Gherkin parser lives in `karate-js` (reuses the JS lexer). The ScenarioEngine lives in `karate-core`.

- [x] Gherkin parser (Feature, Scenario, ScenarioOutline, Background, Examples, tags, steps)
- [x] ScenarioEngine (Suite, FeatureRuntime, ScenarioRuntime, StepExecutor)
- [x] Variable scoping (local, global, feature-level)
- [x] `call` and `callonce` keywords for feature composition
- [x] Doc strings for multi-line values
- [x] Data tables for parameterized steps
- [x] Tags parsing
- [x] Parallel scenario execution (virtual threads)
- [x] RunListener interface (unified event system for test execution)
- [x] RunListenerFactory for per-thread listeners (debugger support)
- [x] ResultListener interface for result streaming
- [x] JavaScript expression evaluation in steps
- [x] String interpolation with variable substitution
- [x] `retry until` keyword for polling
- [x] Tag expressions filtering (TagSelector with `anyOf()`, `allOf()`, `not()`, `valuesFor()`)
- [x] `@lock` tag for mutual exclusion in parallel execution (`@lock=name`, `@lock=*`)

### HTTP Client ✅

- [x] All HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
- [x] Request/response cycle management
- [x] Cookie and session management
- [x] Custom headers and header tracking
- [x] Query parameters and path parameters
- [x] SSL/TLS (custom certs, keystore/truststore, trust-all option)
- [x] Proxy support with authentication
- [x] Timeout configuration (connect, read)
- [x] Follow redirects option
- [x] Multipart and file uploads
- [x] Form-encoded data
- [x] Declarative auth (`configure auth` for Basic, Bearer, OAuth2 with automatic token refresh)
- [x] SOAP/soapAction support
- [x] NTLM authentication (`configure auth = { type: 'ntlm' }`, Apache HttpClient 5 NTCredentials)
- [x] HttpLogModifier for sensitive data masking

### Match & Assertions ✅

- [x] Core operators: EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS
- [x] CONTAINS_ONLY, CONTAINS_ANY, CONTAINS_DEEP variants
- [x] EACH_* variants for array iteration
- [x] WITHIN and NOT_WITHIN operators (range assertions)
- [x] Fuzzy markers (`#string`, `#number`, `#array`, `#boolean`, `#null`, `#present`, `#notpresent`, `#uuid`, `#regex`, `#ignore`)
- [x] Schema validation (via fuzzy markers in match expressions)
- [x] Regular expression matching (via `#regex` validator)
- [x] Response status code validation (`responseStatus`)
- [x] Response header validation (`responseHeaders`)
- [x] `assert` keyword for JS expression assertions
- [x] `karate.expect()` Chai-style BDD assertions
- [x] Soft assertions (`continueOnStepFailure` configuration)
- [x] JSONPath expressions (Jayway JsonPath 2.10.0, `karate.jsonPath()`, `get` keyword, `$` prefix, deep scan `..`)
- [x] XPath expressions (namespace-aware, `karate.xmlPath()`, `get`/`set`/`remove` keywords, `count()` etc.)

### Data Formats ✅

- [x] JSON (parsing, serialization, pretty print)
- [x] XML (parsing, serialization)
- [x] CSV (FastCSV integration)
- [x] YAML (SnakeYAML)
- [x] Binary/bytes data handling
- [x] Template substitution (Karate expression embedding)

### Configuration ✅

- [x] `karate-config.js` support for global configuration
- [x] Environment-based config (`karate.env`)
- [x] RunListener/RunListenerFactory for event-driven extensibility
- [x] `karate.faker.*` built-in test data generation
- [x] `karate.uuid()` UUID generation
- [x] `karate-base.js` (shared config from classpath JAR)

### Reporting ✅

- [x] Karate JSONL event stream (`karate-events.jsonl`) - opt-in via `.outputJsonLines(true)`
- [x] JUnit XML report format (`karate-junit.xml`)
- [x] Summary statistics (pass/fail counts, durations)
- [x] Console output with ANSI colors
- [x] HTML report (interactive dashboard with Alpine.js)
- [x] Timeline view (Gantt-style parallel execution)
- [x] Result embedding in reports (images, HTML, etc.)
- [x] Nested feature call display in HTML reports
- [x] Cucumber JSON report format (`cucumber-json/` subfolder, async per-feature)

### CLI Compatibility ✅

Integration with [Karate CLI](https://github.com/karatelabs/karate-cli):

- [x] Feature file/directory paths
- [x] `-t, --tags` - Tag filtering
- [x] `-T, --threads` - Parallel thread count
- [x] `-n, --name` - Run single scenario by name
- [x] `-D, --dryrun` - Dry run mode
- [x] `-o, --output` - Output directory
- [x] `-f, --format` - Report formats (html, cucumber:json, junit:xml, karate:jsonl)
- [x] `-e, --env` - Environment variable
- [x] `-g, --configdir` - Config directory for karate-config.js
- [x] `-C, --clean` - Clean output before run
- [x] Debug support API (`Runner.debugSupport()` hook for commercial Xplorer)
- [x] ANSI colored console output

### Console & Logging ✅

- [x] ANSI coloring for all console logs
- [x] Structured logging (SLF4J/Logback)
- [x] Request/response log prettification
- [x] Print statements with expression evaluation

### Event System ✅

- [x] `RunEventType` enum (SUITE_ENTER, SCENARIO_EXIT, etc.)
- [x] `RunEvent` interface with full runtime object access and `isTopLevel()`
- [x] `RunListener` interface with single `onEvent(RunEvent)` method
- [x] `RunListenerFactory` for per-thread listeners (debugger support)
- [x] Suite/FeatureRuntime/ScenarioRuntime/StepExecutor use `fireEvent()`
- [x] Events fire for all calls (use `isTopLevel()` to filter if needed)
- [x] JSONL event stream for decoupled consumers (replayable, aggregatable)
- [x] Cucumber JSON from FeatureResult (async per-feature, same data as JSONL)

---

## Milestone 2: API Mocks ✅

> **Goal:** Full mock server capabilities.
> **Status:** Core complete. Mock server is fully functional and used in API client testing.

### Mock Server Core ✅

- [x] HTTP mock server (Netty-based)
- [x] Feature-based mock definitions (`MockServer.feature(path)`)
- [x] Dynamic request/response handling
- [x] Request matching and routing
- [x] Header and body customization
- [x] Status code control
- [x] Delay/latency simulation (`responseDelay` + Netty non-blocking scheduler)
- [x] Stateful mocks (session support)
- [x] CORS support
- [x] `-p, --port` - Server port

---

## Milestone 3: API Performance Testing ✅

> **Goal:** Scale from functional tests to load tests.
> **Status:** Core complete. See [GATLING.md](./GATLING.md) for details.

- [x] Gatling integration (`karate-gatling` module with PerfHook, StatsEngine reporting)
- [x] Java-only DSL (`karateProtocol()`, `karateFeature()`, `karateSet()`)
- [x] URI pattern matching for request grouping
- [x] Session variable chaining (`__gatling` / `__karate` maps)
- [x] Silent mode for warm-up iterations
- [x] Custom perf event capture (PerfContext for DB, gRPC, etc.)
- [x] Gatling HTML reports (Highcharts) and JSON format

---

## Milestone 4: Browser Automation ✅

> **Goal:** CDP and W3C WebDriver support for cross-browser testing.
> **Status:** Complete. See [DRIVER.md](./DRIVER.md) for details.

- [x] Chrome/Chromium/Edge via CDP (complete)
- [x] Firefox via W3C WebDriver + geckodriver (110/110 tests pass)
- [x] Safari via W3C WebDriver + safaridriver
- [x] W3C WebDriver backend (`W3cDriver` in `io.karatelabs.driver.w3c`)
- [x] W3C Actions API for keyboard/mouse interaction
- [x] Frame switching, cookies, window management
- [x] Separate `w3c` Maven profile with CI integration

---

## Release Preparation

### Documentation ✅

- [x] Create PRINCIPLES.md
- [x] Create ROADMAP.md (this file)
- [x] Create root README.md
- [x] Create karate-js/README.md
- [x] Create karate-core/README.md
- [x] Create CONTRIBUTING.md
- [x] Create SECURITY.md
- [x] Create MIGRATION_GUIDE.md

### CI/CD Pipeline ✅

- [x] GitHub Actions workflow for CI (`cicd.yml` — `mvn verify -Pcicd`)
- [x] W3C WebDriver tests in CI (`cicd.yml` — `mvn verify -Pw3c`)
- [x] Configure automated testing on PR (triggers on push to main and PRs)
- [x] CodeQL security scanning (`codeql.yml`)
- [x] JDK compatibility testing (`jdk-compat.yml` — JDK 23/24 matrix)
- [x] OWASP dependency vulnerability scanning (integrated into `maven-release.yml`)
- [x] OpenSSF Scorecard workflow and badge (`openssf-scorecard.yml`)

### One-Click Release Workflow ✅

- [x] GitHub Actions workflow for unified release process (`maven-release.yml`)
- [x] Maven Central publish step (with GPG signing, Sonatype Central)
- [x] Fatjar build and artifact upload (ZIP archive)
- [x] OWASP CVE report generation (per-module HTML)
- [x] Configurable inputs (version, publish toggle, test toggle, CVE toggle)

### Maven Artifact Publishing ✅

- [x] Maven Central publishing via `central-publishing-maven-plugin` v0.10.0
- [x] POM metadata (SCM URLs, developer info, MIT license)
- [x] GPG signing with loopback pinentry mode

### Repository Hygiene

- [x] Merge karate-v2 into karatelabs/karate (preserve stars and history)
- [x] Tag v1 state as `v1-final`, create `v1` branch
- [x] Remove v1-only modules (preserved on `v1` branch)
- [x] Remove v1-only GitHub Actions workflows
- [x] Update repository references from karate-v2 to karate
- [x] Bump dependencies (logback, maven plugins, Gatling)
- [x] Harden workflows (pinned action SHAs, `permissions: read-all`)
- [x] Archive [karatelabs/karate-v2](https://github.com/karatelabs/karate-v2) with redirect notice
- [x] Archive [karatelabs/karate-js](https://github.com/karatelabs/karate-js) with redirect notice
- [x] Configure GitHub repository settings (branch protection via OpenSSF)

### Release TODOs

Items remaining before or shortly after v2.0.0 release:

- [ ] Create release notes and blog post for karate 2.0.0
- [ ] Sync [karate-cli](https://github.com/karatelabs/karate-cli) release (update to use karate v2 as backend)
- [ ] Update [karate-docs](https://github.com/karatelabs/karate-docs) (Docusaurus site) with v2 documentation
- [ ] Update MIGRATION_GUIDE.md with WebDriver section (type mapping, capabilities config, CDP-only operations)
- [ ] Merge karate-demo + karate-e2e-tests into [karate-examples](https://github.com/karatelabs/karate-examples)
- [ ] Clean up GitHub issues (close stale, redirect to docs.karatelabs.io)
- [ ] Redirect GitHub wiki pages to docs.karatelabs.io
- [ ] GitHub Release creation with assets
- [ ] Release compatible IDE plugins (VS Code extension, IntelliJ plugin)
- [ ] Automated version bumping and changelog generation

---

## Post-Release / Future

> For the complete capability inventory (planned, future, commercial), see [CAPABILITIES.yaml](./CAPABILITIES.yaml).

### Near-Term Enhancements

- [ ] HTTP/2 support
- [ ] Response time validation
- [ ] HTML report cosmetic improvements
- [ ] Step definitions with regex pattern matching
- [ ] `--listener` / `--listener-factory` CLI flags
- [ ] PROGRESS events for real-time progress display
- [ ] `FeatureResult.fromJson()` for offline report generation from JSONL
- [ ] `SuiteResult.merge()` API to consolidate multiple runner results into a single report ([#2337](https://github.com/karatelabs/karate/issues/2337))
- [ ] Retry failed scenarios (`@retry` tag or `Runner` API for re-running failures) ([#2578](https://github.com/karatelabs/karate/issues/2578))
- [ ] `Runner.Builder` exposure via `protocol.runner()` for Gatling
- [ ] `@report=false` tag (exclude scenario from reports but still execute)
- [ ] Playwright emulation (Firefox/WebKit via Playwright CDP)
- [ ] Mock CLI options (`-m`, `-s`, `-W`, etc.)
- [ ] HTMX integration for mocks
- [ ] `configure beforeScenario` hook for mocks ([#2239](https://github.com/karatelabs/karate/issues/2239))

### JavaScript Engine (karate-js)

> See [JS_ENGINE.md](./JS_ENGINE.md) for detailed type system and design patterns.

**High Priority - Java Interop:**
- [ ] BigInt → `BigInteger` (large IDs, timestamps, financial identifiers)
- [ ] BigDecimal → `BigDecimal` (money/finance - floating point is dangerous)
- [ ] ArrayBuffer → `byte[]` (raw binary data container)
- [ ] JsRegex + JavaMirror (return `Pattern` from `getJavaValue()`)

**Medium Priority:**
- [ ] Set → `java.util.Set` (deduplication, membership checks)
- [ ] Map (proper JS Map) → `java.util.Map` (ordered keys, non-string keys)
- [ ] Iterator/for-of → `java.util.Iterator` (clean iteration over Java collections)

**ES Compatibility (Lower Priority):**
- [ ] `async`/`await` → `CompletableFuture` / virtual threads
- [ ] `setTimeout()` and timer functions
- [ ] ES Modules (`import`/`export`) for JS reuse across tests

### Parser & IDE Support

> See [PARSER.md](./PARSER.md) for detailed parser architecture and APIs.

- [ ] Code Formatting (JSON-based options, token-based and AST-based strategies)
- [ ] Source Reconstitution (regenerate source from AST)
- [ ] Embedded Language Support (JS highlighting inside Gherkin steps)

### Cross-Language Support

- [ ] Continue [Karate CLI](https://github.com/karatelabs/karate-cli) development
- [ ] Platform binaries (macOS, Windows, Linux)
- [ ] .NET, Python, Go client libraries

### Platform Automation

- [ ] Desktop automation (macOS, Windows, Linux)
- [ ] Mobile automation (iOS, Android)

### Enterprise Features (Commercial)

> These features fund continued open-source innovation.

- [ ] WebSocket testing
- [ ] Requirements management integration
- [ ] Advanced distributed testing
- [ ] Enhanced IDE support
- [ ] API governance tools
- [ ] [Karate Xplorer](https://xplorer.karatelabs.io/) desktop platform

---

## Notes for Contributors

This roadmap is a living document. When working on tasks:

1. Update the status checkbox when starting or completing work
2. Add new tasks as they're identified
3. Reference this document in PRs to maintain context

**For LLM assistants:**

Start each session by reading:
1. **PRINCIPLES.md** - Understand the "why" behind decisions
2. **ROADMAP.md** (this file) - Development status and pending work
3. **CAPABILITIES.yaml** - Source of truth for capabilities (edit this, not the .md)
4. **Module READMEs** - Understand architecture (`karate-js/README.md`, `karate-core/README.md`)

**Important:** `CAPABILITIES.md` is auto-generated from `CAPABILITIES.yaml`. Never edit the .md file directly. After updating the YAML, run `./etc/generate-capabilities.sh` to regenerate.

Key decisions made:
- WebSocket support deprecated from open-source, moved to commercial
- Gherkin parser in `karate-js` (reuses JS lexer), ScenarioEngine in `karate-core`
- Milestone 1 focus: drop-in replacement for Karate 1.x API testing
- Desktop/mobile automation will use polyglot approach (Swift on macOS, .NET on Windows, etc.)
- Karate 1.x source is available for porting reference

Update this file as work progresses to maintain context across sessions.

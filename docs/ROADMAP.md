# Karate v2 Roadmap

This document tracks the development roadmap for Karate v2. It serves as a persistent reference for contributors and LLM assistants working on the project.

> See also: [Design Principles](./PRINCIPLES.md) | [Capabilities](./CAPABILITIES.md) | [karate-js](../karate-js) | [karate-core](../karate-core)

> **Status Key:**
> - `[ ]` Not started
> - `[~]` In progress
> - `[x]` Complete

---

## Milestone 1: API Testing Release

> **Goal:** Drop-in replacement for Karate 1.x API testing. Existing tests should just work.

### Gherkin Parser & Scenario Engine

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
- [ ] Step definitions with regex pattern matching

### HTTP Client

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
- [ ] HTTP/2 support
- [x] SOAP/soapAction support
- [x] NTLM authentication (`configure auth = { type: 'ntlm' }`, Apache HttpClient 5 NTCredentials)
- [x] HttpLogModifier for sensitive data masking
- [ ] gRPC support (if external module exists)

### Match & Assertions

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
- [ ] Response time validation

### Data Formats

- [x] JSON (parsing, serialization, pretty print)
- [x] XML (parsing, serialization)
- [x] CSV (FastCSV integration)
- [x] YAML (SnakeYAML)
- [x] Binary/bytes data handling
- [x] Template substitution (Karate expression embedding)

### Configuration

- [x] `karate-config.js` support for global configuration
- [x] Environment-based config (`karate.env`)
- [x] RunListener/RunListenerFactory for event-driven extensibility
- [x] `karate.faker.*` built-in test data generation
- [x] `karate.uuid()` UUID generation
- [ ] Plugin system support
- [x] `karate-base.js` (shared config from classpath JAR)

### Reporting

- [x] Karate JSONL event stream (`karate-events.jsonl`) - opt-in via `.outputJsonLines(true)`
- [x] JUnit XML report format (`karate-junit.xml`)
- [x] Summary statistics (pass/fail counts, durations)
- [x] Console output with ANSI colors
- [x] HTML report (interactive dashboard with Alpine.js)
- [x] Timeline view (Gantt-style parallel execution)
- [x] Result embedding in reports (images, HTML, etc.)
- [x] Nested feature call display in HTML reports
- [x] Cucumber JSON report format (`cucumber-json/` subfolder, async per-feature)
- [ ] HTML report cosmetic improvements (polish styling, spacing, colors, typography)
- [ ] Tag-based analytics page

### CLI Compatibility

Integration with [Karate CLI](https://github.com/karatelabs/karate-cli):

- [ ] Update Karate CLI to support karate as backend
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
- [ ] `--listener` - RunListener class by name
- [ ] `--listener-factory` - RunListenerFactory class by name
- [ ] Progress indicators (deferred - PROGRESS events)
- [ ] Interactive mode for LLM sessions
- [ ] Two-way `karate-pom.json` (deferred - serialize CLI to JSON and back)

### Console & Logging

- [x] ANSI coloring for all console logs
- [x] Structured logging (SLF4J/Logback)
- [x] Request/response log prettification
- [x] Print statements with expression evaluation
- [ ] File logging support
- [ ] Configurable verbosity levels
- [ ] LLM-friendly output mode (token-efficient)
- [ ] Stack traces with context

### Backwards Compatibility Testing

- [ ] Create compatibility test suite from karate-demo
- [ ] Test against real-world Karate 1.x projects
- [ ] Document any intentional breaking changes
- [ ] Migration guide for edge cases

---

## Milestone 2: API Mocks

> **Goal:** Full mock server capabilities with HTMX/AlpineJS support.
> **Status:** Partially implemented (helps test API client)

### Mock Server Core
- [x] HTTP mock server (Netty-based)
- [x] Feature-based mock definitions (`MockServer.feature(path)`)
- [x] Dynamic request/response handling
- [x] Request matching and routing
- [x] Header and body customization
- [x] Status code control
- [x] Delay/latency simulation (`responseDelay` + Netty non-blocking scheduler)
- [x] Stateful mocks (session support)

### CLI Mock Options
- [ ] `-m, --mock` - Mock server feature files
- [ ] `-P, --prefix` - Path prefix/context-path
- [x] `-p, --port` - Server port
- [ ] `-s, --ssl` - Enable HTTPS
- [ ] `-c, --cert` and `-k, --key` - SSL cert/key files
- [ ] `-W, --watch` - Hot-reload mock files
- [ ] `-S, --serve` - App server mode

### Advanced Features
- [ ] Mock recording and playback
- [x] CORS support
- [~] HTMX integration
- [ ] AlpineJS integration

---

## Milestone 3: API Performance Testing

> **Goal:** Scale from functional tests to load tests.
> **Status:** Core complete (Phases 0-3). See [GATLING.md](./GATLING.md) for details.

- [x] Gatling integration (`karate-gatling` module with PerfHook, StatsEngine reporting)
- [x] Java-only DSL (`karateProtocol()`, `karateFeature()`, `karateSet()`)
- [x] URI pattern matching for request grouping
- [x] Session variable chaining (`__gatling` / `__karate` maps)
- [x] Silent mode for warm-up iterations
- [x] Custom perf event capture (PerfContext for DB, gRPC, etc.)
- [x] Gatling HTML reports (Highcharts) and JSON format
- [ ] `Runner.Builder` exposure via `protocol.runner()` (karateEnv, configDir, systemProperty)
- [ ] Standalone CLI support (`karate perf` command, Phase 5)
- [ ] Profiling validation (overhead comparison, memory leak testing, Phase 6)
- [ ] Distributed test execution
- [ ] Throttling and rate limiting

---

## Release Preparation

### Documentation
- [x] Create PRINCIPLES.md
- [x] Create ROADMAP.md (this file)
- [x] Create root README.md
- [x] Create karate-js/README.md
- [x] Create karate-core/README.md
- [x] Create CONTRIBUTING.md
- [x] Create SECURITY.md
- [ ] Document SLF4J binding requirement for library users (logback is `provided` scope, users must supply their own binding for Spring Boot, Quarkus, etc.)

### CI/CD Pipeline
- [x] Set up GitHub Actions workflow for CI (`cicd.yml` - `mvn verify -Pcicd`)
- [x] Configure automated testing on PR (triggers on push to main and PRs)
- [x] CodeQL security scanning (`codeql.yml`)
- [x] JDK compatibility testing (`jdk-compat.yml` - latest JDK EA on main)
- [ ] Add code coverage reporting
- [ ] Add dependency vulnerability scanning

### One-Click Release Workflow
- [x] GitHub Actions workflow for unified release process (`maven-release.yml`)
- [x] Maven Central publish step (with GPG signing, Sonatype Central)
- [x] Fatjar build and artifact upload
- [ ] Automated version bumping and changelog generation
- [ ] GitHub Release creation with assets
- [ ] Release validation tests (smoke tests against published artifacts)

> **Note:** Docker image build has been removed from the release workflow.
> Karate v2 no longer ships an official `karate-chrome` Docker image. Users
> should use off-the-shelf Chromium images (e.g. `chromedp/headless-shell`)
> with Testcontainers and the `PooledDriverProvider` pattern.
> See [DRIVER.md](./DRIVER.md) for details.
>
> For AI-powered browser testing, the commercial `karatelabs/karate-agent`
> Docker container provides Chrome, VNC, and the Karate Agent server for
> autonomous testing via LLM agents. See https://karatelabs.io.

### Maven Artifact Publishing
- [ ] Configure Maven Central publishing (Sonatype OSSRH)
- [ ] Configure POM metadata (SCM, developers, licenses)
- [ ] Document release process

### Repository Hygiene
- [x] Merge karate-v2 into karatelabs/karate (preserve stars and history)
- [x] Tag v1 state as `v1-final`, create `v1` branch
- [ ] Archive [karatelabs/karate-v2](https://github.com/karatelabs/karate-v2) with redirect notice
- [ ] Archive [karatelabs/karate-js](https://github.com/karatelabs/karate-js) with redirect notice
- [ ] Configure GitHub repository settings (branch protection, etc.)
- [ ] Set up issue templates
- [ ] Set up PR templates

### Post-Migration
- [ ] Update [karatelabs/karate-docs](https://github.com/karatelabs/karate-docs) (Docusaurus site) with v2 documentation
- [ ] Update karate-docs links from master to main/v1 as appropriate
- [ ] Update [karatelabs/karate-cli](https://github.com/karatelabs/karate-cli) references to point to karatelabs/karate
- [ ] Merge karate-demo + karate-e2e-tests into [karatelabs/karate-examples](https://github.com/karatelabs/karate-examples)
- [ ] Set up CI/CD in karate-examples against karate v2

---

## Future Milestones

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
- [ ] Generator/yield → Iterator with state
- [ ] Symbol → `JsSymbol` (unique identifiers)
- [ ] Proxy → Dynamic proxy (metaprogramming)
- [ ] WeakMap/WeakSet → `WeakHashMap`

**Utilities:**
- [ ] Engine State JSON Serialization (persist/restore engine bindings)

> Full ES compatibility is a long-term goal if there is community interest.

### Parser & IDE Support

> See [PARSER.md](./PARSER.md) for detailed parser architecture and APIs.

Error-tolerant parsing for IDE features (syntax coloring, code completion, formatting).

- [ ] Code Formatting (JSON-based options, token-based and AST-based strategies)
- [ ] Source Reconstitution (regenerate source from AST)
- [ ] Embedded Language Support (JS highlighting inside Gherkin steps)

### Runtime Advanced Features

- [x] Lock System (`@lock=<name>`) for mutual exclusion in parallel execution
- [ ] Retry System (`@retry`) for flaky test handling with `rerun.txt`
- [ ] Multiple Suite Execution with environment isolation
- [ ] Telemetry (anonymous usage stats, once per day, opt-out via `KARATE_TELEMETRY=false`)
- [ ] Classpath scanning for feature files (`classpath:features/`)
- [ ] CLI JSON Configuration (`karate --config karate.json`)

### Event System (see [EVENTS.md](./EVENTS.md))

Unified event system for test execution lifecycle:

- [x] `RunEventType` enum (SUITE_ENTER, SCENARIO_EXIT, etc.)
- [x] `RunEvent` interface with full runtime object access and `isTopLevel()`
- [x] `RunListener` interface with single `onEvent(RunEvent)` method
- [x] `RunListenerFactory` for per-thread listeners (debugger support)
- [x] Refactor Suite/FeatureRuntime/ScenarioRuntime/StepExecutor to use `fireEvent()`
- [x] Events fire for all calls (use `isTopLevel()` to filter if needed)
- [x] JSONL event stream for decoupled consumers (replayable, aggregatable)
- [x] Review event data for reporting platforms (Allure, ReportPortal, JIRA/X-Ray)
- [x] Cucumber JSON from FeatureResult (async per-feature, same data as JSONL)
- [ ] PROGRESS events for real-time progress display (deferred)
- [ ] `FeatureResult.fromJson()` for offline report generation from JSONL (deferred)
- [ ] JS event handling via optional library (karate-boot.js, karate.on()) - future

### Templating & Markup
- [ ] Document Thymeleaf-based templating
- [ ] Document custom Karate dialect processors
- [ ] Native markdown parsing and rendering

### Cross-Language Support
- [ ] Continue [Karate CLI](https://github.com/karatelabs/karate-cli) development
- [ ] Platform binaries (macOS, Windows, Linux)
- [ ] .NET integration
- [ ] Python client library
- [ ] Go client library

### Browser Automation (Post-Milestone 3)
- [x] Chrome/Chromium/Edge via CDP (complete)
- [~] WebDriver protocol support (experimental — W3cDriver in `io.karatelabs.driver.w3c`)
- [ ] Playwright emulation (replaces v1 experimental Playwright; enables Firefox/WebKit via Playwright CDP)
- [ ] PDF testing
- [ ] Video recording
- [ ] Cross-browser matrix testing and device emulation

### Platform Automation (Post-Milestone 3)
- [ ] Desktop automation (macOS, Windows, Linux)
- [ ] Mobile automation (iOS, Android)

### Extension Points
- [ ] Plugin system documentation
- [ ] Protocol handler extension
- [ ] Data format converters (Protobuf, Avro)

### Enterprise Features (Commercial)

> These features fund continued open-source innovation.

- [ ] WebSocket testing (deprecated from open-source, moved to commercial)
- [ ] Requirements management integration
- [ ] Advanced distributed testing
- [ ] Enhanced IDE support
- [ ] API governance tools
- [ ] [Karate Xplorer](https://xplorer.karatelabs.io/) desktop platform

---

## Principle Extensions

These themes emerged from brainstorming but aren't explicitly covered by the current principles in `PRINCIPLES.md`. They may warrant future principle additions or can inform implementation decisions.

### Soft Assertions & Error Handling Philosophy

Current Karate behavior is fail-fast on assertion failures. Consider:
- **Soft assertions mode:** Continue execution after match failures, aggregate all failures in report
- **User-defined assertion messages:** Allow custom failure messages for better diagnostics
- **onError hooks:** JavaScript hooks that fire on any error, enabling custom recovery or logging

### Telemetry & Version Awareness

Help users stay current and provide usage insights:
- **Version out-of-date banner:** Alert users when a newer Karate version is available
- **Usage telemetry:** Anonymous usage patterns to guide development priorities
- **Upsell touchpoints:** Tasteful awareness of commercial offerings (e.g., report footers)

### Testing AI & LLM Systems

Beyond being LLM-friendly, Karate can be a tool for testing AI systems:
- **Testing MCP servers:** Validate MCP server implementations
- **Mocking LLM APIs:** Deterministic responses for testing LLM-dependent code
- **Prompt injection testing:** Security testing for AI systems
- **LLM comparison:** A/B testing different models for given tasks
- **Similarity assertions:** Fuzzy matching using embeddings for AI-generated content

### Robustness & Execution Control

- **Re-run only failed tests:** Resume from failure point using report data
- **Random seed for test order:** Detect order-dependent tests
- **Tag execution ordering:** Some tags run on same thread, some after others
- **Multiple environments in parallel:** Run same tests against staging and prod simultaneously

### Large Data Handling

- **Streaming responses:** Memory-efficient handling of large file downloads
- **Operations on huge data:** Match/sort operations without loading everything into memory

---

## Reporting & Logging TODOs

> Items identified during Dec 2023 logging/reporting session.

### Code Organization
- [x] Move all reporting and logging related code to an "output" folder (not "reports")
- [x] Consolidate `HtmlReportListener`, `JsonLinesEventWriter`, `HtmlReportWriter` into unified output package

### Report Format & Compatibility
- [x] Verify v2 karate-json format matches v1 format for external tool compatibility
- [x] Verify report events (JSON Lines) are sufficient for external aggregation services
- [x] Implement report merging from multiple runs (`HtmlReport.aggregate()`)
- [x] Cucumber JSON format support (async per-feature, `cucumber-json/` subfolder)
- [x] JUnit XML format support (async per-feature, `junit-xml/` subfolder)

### Logging Configuration
- [x] Clarify LogContext vs SLF4J relationship for log level settings
  - `--report-log-level` affects LogContext buffer (what goes in HTML reports)
  - `--runtime-log-level` affects SLF4J logger level (console/JVM output)
  - `karate.configure('report', { logLevel: '...' })` affects report filtering only
- [ ] Document log level precedence: CLI > config > default

### Tag Features
- [ ] Implement `@report=false` tag (exclude scenario from reports but still execute)
- [ ] Tag expression evaluation (v1-style: `anyOf()`, `allOf()`, `not()`, `valuesFor()`)

### Testing Gaps
- [ ] Add tests for log masking combined with reports
- [ ] Add tests for tag inheritance edge cases

### Performance & Consistency
- [ ] Cache `Scenario.getTagsEffective()` result (currently creates new merged list each call)
- [ ] Consider porting v1 `Tags` wrapper class for rich tag evaluation (v2 uses raw `List<Tag>`)

---

## Notes for Contributors

This roadmap is a living document. When working on tasks:

1. Update the status checkbox when starting or completing work
2. Add new tasks as they're identified
3. Move completed sections to an archive if the list grows too long
4. Reference this document in PRs to maintain context

**For LLM assistants:**

Start each session by reading:
1. **PRINCIPLES.md** - Understand the "why" behind decisions
2. **ROADMAP.md** (this file) - Single source of truth for all pending work
3. **CAPABILITIES.yaml** - Source of truth for capabilities (edit this, not the .md)
4. **Module READMEs** - Understand architecture (`karate-js/README.md`, `karate-core/README.md`)
5. **RUNTIME.md** - Runtime architecture and implementation details
6. **JS_ENGINE.md** - JavaScript engine type system and Java interop patterns
7. **PARSER.md** - Parser infrastructure for IDE support and tooling

**Important:** `CAPABILITIES.md` is auto-generated from `CAPABILITIES.yaml`. Never edit the .md file directly. After updating the YAML, run `./etc/generate-capabilities.sh` to regenerate.

Key decisions made:
- WebSocket support deprecated from open-source, moved to commercial
- Gherkin parser in `karate-js` (reuses JS lexer), ScenarioEngine in `karate-core`
- Milestone 1 focus: drop-in replacement for Karate 1.x API testing
- Desktop/mobile automation will use polyglot approach (Swift on macOS, .NET on Windows, etc.)
- Karate 1.x source is available for porting reference

Update this file as work progresses to maintain context across sessions.

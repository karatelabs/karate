# Karate v2 Capabilities

Complete taxonomy of Karate capabilities - current, in-progress, and planned.

> **Generated:** 2026-04-22 from `CAPABILITIES.yaml`
>
> See also: [Design Principles](./PRINCIPLES.md) | [Roadmap](./ROADMAP.md)

## Legend

| Symbol | Meaning |
|--------|---------|
| `[1.x ✓]` | Was in v1, now implemented in v2 |
| `[1.x ~]` | Was in v1, in progress for v2 |
| `[1.x  ]` | Was in v1, not yet ported to v2 |
| `[v2]` | New in v2 (not in v1), implemented |
| `[~]` | New, in progress |
| `[ ]` | Planned |
| `[-]` | Future / Wish list |
| `$` | Commercial / Enterprise |

## Summary

**Total capabilities: 373**

| Category | Count |
|----------|-------|
| v1 features ported to v2 | 131 |
| v1 features pending | 8 |
| New in v2 (implemented) | 50 |
| New in v2 (in progress) | 1 |
| Planned | 19 |
| Future / Wish list | 164 |

## Categories

- [Core Test Automation](#core-test-automation)
- [API Testing](#api-testing)
- [UI Testing](#ui-testing)
- [UI & UX Testing](#ui--ux-testing)
- [Scale & Performance](#scale--performance)
- [Developer Experience](#developer-experience)
- [Reports & Integration](#reports--integration)
- [Test Generation](#test-generation)
- [AI & LLM Integration](#ai--llm-integration)
- [Cross-Language Support](#cross-language-support)
- [Commercial Tools](#commercial-tools)

---

### Core Test Automation

Foundation capabilities that power all test types

- `[v2]` Embedded JavaScript Engine
  - `[v2]` ES6+ syntax support
  - `[v2]` Java interop from JS
  - `[ ]` Pluggable engine architecture
  - `[-]` async/await support
  - `[-]` ES Modules (import/export)
  - `[-]` setTimeout and timers

- `[1.x ✓]` Assertions (Match)
  - `[1.x ✓]` Core operators (equals, not equals, contains)
  - `[1.x ✓]` contains only, contains any, contains deep
  - `[1.x ✓]` each variants for arrays
  - `[1.x ✓]` Schema validation
  - `[1.x ✓]` Regex matching
  - `[1.x ✓]` JSONPath expressions
  - `[1.x ✓]` XPath expressions
  - `[1.x ✓]` Fuzzy markers (#string,
  - `[v2]` match within (range assertions)
  - `[-]` Numeric tolerance assertions
  - `[-]` Date and time assertions
  - `[v2]` Soft assertions (continueOnStepFailure)
  - `[v2]` User-defined assertion failure messages
  - `[-]` Similarity assertions (for AI) `$`

- `[1.x ✓]` Data Formats
  - `[1.x ✓]` JSON (parsing, serialization, pretty print)
  - `[1.x ✓]` XML (parsing, XPath, namespaces)
  - `[1.x ✓]` YAML
  - `[1.x ✓]` CSV
  - `[1.x ✓]` Binary/bytes handling
  - `[-]` Protobuf support `$`
  - `[-]` Avro support `$`

- `[1.x ✓]` Gherkin Parser
  - `[1.x ✓]` Feature and Scenario parsing
  - `[1.x ✓]` Background
  - `[1.x ✓]` Scenario Outline with Examples
  - `[1.x ✓]` Doc strings
  - `[1.x ✓]` Data tables
  - `[1.x ✓]` Tags parsing
  - `[1.x ✓]` Tag expressions filtering
  - `[1.x ✓]` call and callonce keywords
  - `[1.x ✓]` callSingle for shared setup
  - `[1.x ✓]` retry until keyword

- `[v2]` Built-in Test Utilities
  - `[v2]` karate.faker.* (test data generation)
  - `[v2]` karate.expect() (Chai-style BDD assertions)
  - `[v2]` karate.uuid() (UUID generation)

- `[v2]` Parallel Safety
  - `[v2]` @lock=name (named mutual exclusion)
  - `[v2]` @lock=* (exclusive execution)

- `[1.x ✓]` Environment & Configuration
  - `[1.x ✓]` karate-config.js
  - `[1.x ✓]` Environment-based config (karate.env)
  - `[v2]` karate-base.js (shared config loaded before karate-config.js)
  - `[v2]` Config fallback to working directory
  - `[v2]` API to read current config settings (karate.config)
  - `[-]` Multiple environments in parallel

- `[1.x ✓]` Grouping & Tags
  - `[1.x ✓]` Tag-based filtering
  - `[1.x ✓]` Tag expressions (and, or, not)
  - `[-]` Tags run on same thread
  - `[-]` Tag ordering (run after other tags)

- `[1.x ✓]` Data Driven Testing
  - `[1.x ✓]` Scenario Outline with Examples
  - `[1.x ✓]` Dynamic scenario generation
  - `[1.x ✓]` External data sources (CSV, JSON)

- `[1.x ✓]` Extensibility & Hooks
  - `[v2]` RunListener interface (unified event system)
  - `[v2]` RunListenerFactory (per-thread listeners)
  - `[1.x ✓]` Before/After scenario hooks
  - `[1.x ✓]` Before/After feature hooks
  - `[-]` JavaScript hooks
  - `[-]` onError hook
  - `[ ]` Plugin system

- `[1.x ✓]` Artifact & Code Re-use
  - `[1.x ✓]` call keyword for feature composition
  - `[1.x ✓]` callonce for shared setup
  - `[1.x ✓]` callSingle for one-time global setup
  - `[1.x ✓]` Shared feature libraries
  - `[1.x ✓]` Java interop
  - `[1.x ✓]` Template substitution

---

### API Testing

HTTP and protocol-level API testing

- `[1.x ✓]` HTTP API Testing
  - `[1.x ✓]` All HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
  - `[1.x ✓]` Request/response cycle management
  - `[1.x ✓]` Headers and cookies
  - `[1.x ✓]` Query and path parameters
  - `[1.x ✓]` Form-encoded data
  - `[1.x ✓]` Multipart and file uploads
  - `[1.x ✓]` SSL/TLS (custom certs, trust-all)
  - `[1.x ✓]` Proxy support with authentication
  - `[v2]` Wildcard non-proxy hosts (e.g. *.example.com)
  - `[1.x ✓]` Timeout configuration
  - `[1.x ✓]` Follow redirects option
  - `[ ]` HTTP/2 support
  - `[v2]` Declarative auth (Basic, Bearer, OAuth2)
  - `[1.x ✓]` NTLM authentication
  - `[1.x ✓]` HttpLogModifier for sensitive data masking
  - `[v2]` cURL export
  - `[-]` Large file streaming (memory-efficient)
  - `[-]` Operations on huge data (match, sort)

- `[1.x ✓]` SOAP
  - `[1.x ✓]` SOAP action support
  - `[1.x ✓]` XML namespace handling

- `[v2]` Async Protocols `$`
  - `[v2]` WebSocket `$`
  - `[-]` Server-Sent Events (SSE) `$`
  - `[-]` gRPC `$`
  - `[-]` Kafka `$`
  - `[-]` STOMP `$`
  - `[-]` Webhook testing `$`
  - `[-]` STDIO (for MCP) `$`
  - `[-]` JSON streaming `$`

- `[1.x ✓]` API Test Doubles (Mocks)
  - `[v2]` HTTP mock server (Netty-based)
  - `[1.x ✓]` Feature-based mock definitions
  - `[1.x ✓]` Dynamic request/response handling
  - `[1.x ✓]` Request matching and routing
  - `[1.x ✓]` Delay/latency simulation
  - `[1.x ✓]` Stateful mocks (session support)
  - `[-]` Mock recording and playback
  - `[1.x ✓]` CORS support
  - `[~]` HTMX integration
  - `[-]` AlpineJS integration
  - `[-]` Async protocol mocks `$`

- `[-]` Contract Testing
  - `[-]` Consumer-driven contracts
  - `[-]` OpenAPI/Swagger validation `$`
  - `[-]` JSON Schema validation `$`

- `[-]` API Documentation `$`
  - `[-]` API docs as side-effect of test execution `$`
  - `[-]` OpenAPI/Swagger import `$`

---

### UI Testing

User interface automation across platforms

- `[1.x ✓]` Browser Automation
  - `[1.x ✓]` Chrome/Chromium support (CDP)
  - `[1.x ✓]` Firefox support
  - `[1.x ✓]` Safari/WebKit support
  - `[1.x ✓]` Edge support
  - `[1.x ✓]` Headless mode
  - `[1.x ✓]` Element locators (CSS, XPath, wildcard)
  - `[1.x ✓]` Keyboard and mouse actions
  - `[1.x ✓]` File uploads/downloads
  - `[1.x ✓]` Multiple windows/tabs
  - `[1.x ✓]` iframes support
  - `[1.x ✓]` Shadow DOM support
  - `[v2]` Auto-wait before element operations
  - `[v2]` Browser pooling (PooledDriverProvider)
  - `[1.x ✓]` Dialog handling
  - `[1.x  ]` PDF testing
  - `[1.x ✓]` WebDriver protocol
  - `[ ]` Playwright emulation
  - `[-]` WebDriver BiDi

- `[-]` Mobile Automation
  - `[-]` Android native apps
  - `[-]` iOS native apps
  - `[-]` Flutter apps
  - `[-]` React Native apps
  - `[1.x -]` Mobile web testing

- `[1.x -]` Desktop Automation
  - `[1.x -]` Windows desktop apps
  - `[-]` macOS desktop apps
  - `[-]` Linux desktop apps

---

### UI & UX Testing

Visual and user experience validation

- `[1.x ✓]` Video Recording & Screenshots
  - `[1.x ✓]` Screenshot capture
  - `[1.x  ]` Video recording
  - `[1.x ✓]` Screenshot on failure

- `[v2]` Visual Validation `$`
  - `[v2]` Image comparison `$`
  - `[v2]` Visual diff reporting `$`
  - `[-]` Visual validation UI `$`

- `[-]` Accessibility Testing `$`
  - `[-]` WCAG compliance checks
  - `[-]` Accessibility audit reports

- `[-]` Localization Testing `$`
  - `[-]` Multi-locale test execution
  - `[-]` Translation validation

- `[1.x  ]` Cross Browser & Device
  - `[1.x  ]` Browser matrix testing
  - `[1.x  ]` Device emulation
  - `[-]` Real device testing `$`

---

### Scale & Performance

Load testing and distributed execution

- `[1.x ✓]` API Performance / Load Testing
  - `[1.x ✓]` Gatling integration (karate-gatling module)
  - `[1.x ✓]` Performance metrics and reporting
  - `[ ]` Throttling and rate limiting
  - `[-]` Resource monitoring

- `[-]` UI/UX Performance `$`
  - `[-]` Page load metrics
  - `[-]` Core Web Vitals
  - `[-]` Performance budgets

- `[-]` Distributed / Cloud Testing `$`
  - `[-]` Distributed test orchestration
  - `[-]` Cloud execution environments
  - `[-]` Parallel across machines

- `[-]` Chaos Testing `$`
  - `[-]` Fault injection
  - `[-]` Network chaos simulation

---

### Developer Experience

Tools and workflows for test development

- `[v2]` IDE Support `$`
  - `[v2]` IntelliJ plugin `$`
  - `[v2]` VS Code extension `$`
  - `[-]` Visual Studio extension `$`
  - `[-]` Language Server Protocol (LSP) `$`

- `[v2]` Live Debugging `$`
  - `[v2]` Gherkin step-through debugging `$`
  - `[-]` JavaScript debugging `$`
  - `[v2]` Java debugging `$`
  - `[v2]` Breakpoints and watch expressions `$`

- `[1.x ✓]` Parallel Execution
  - `[1.x ✓]` Parallel scenarios
  - `[1.x ✓]` Parallel features
  - `[1.x ✓]` Thread pool configuration
  - `[v2]` Virtual threads support
  - `[1.x ✓]` Fail-fast (abortSuiteOnFailure)

- `[1.x ✓]` Robustness & Predictability
  - `[1.x ✓]` Retry on failure
  - `[1.x ✓]` retry until keyword
  - `[-]` Random seed for test order

- `[1.x ✓]` Docker & Cloud Readiness
  - `[1.x ✓]` Docker image
  - `[-]` Testcontainers integration
  - `[-]` Signed Docker container `$`

- `[-]` Git & Version Diffs `$`
  - `[-]` Test diff visualization
  - `[-]` Impact analysis `$`

- `[1.x ✓]` Logging & Traceability
  - `[1.x ✓]` Structured logging (SLF4J)
  - `[1.x ✓]` Request/response logging
  - `[1.x ✓]` Log masking for sensitive data
  - `[-]` Selective logging control
  - `[-]` HTML report without console logs
  - `[v2]` ANSI colored console output
  - `[ ]` LLM-friendly output mode

- `[1.x ✓]` Test Runner SDK / CLI
  - `[1.x ✓]` Feature file execution
  - `[1.x ✓]` Tag filtering
  - `[1.x ✓]` Parallel thread configuration
  - `[1.x ✓]` Environment selection
  - `[1.x ✓]` Output directory configuration
  - `[1.x ✓]` Dry run mode
  - `[v2]` Debug mode `$`
  - `[1.x ✓]` JUnit integration
  - `[1.x ✓]` Maven/Gradle plugins
  - `[-]` Version out-of-date banner
  - `[ ]` Interactive mode for LLMs

- `[1.x ✓]` Low Code / No Code
  - `[1.x ✓]` Gherkin syntax (no Java required)
  - `[ ]` JavaScript test authoring
  - `[1.x ✓]` Java test authoring

- `[1.x ✓]` BDD / Natural Language
  - `[1.x ✓]` Gherkin Given/When/Then
  - `[1.x ✓]` Business-readable reports
  - `[-]` Specification mapping layer `$`

- `[-]` Remote Dev Environments `$`
  - `[-]` GitHub Codespaces support
  - `[-]` Gitpod support

---

### Reports & Integration

Test results and third-party integrations

- `[1.x ✓]` Results Output / Export
  - `[1.x ✓]` Karate JSON report
  - `[1.x ✓]` JUnit XML report
  - `[1.x ✓]` Cucumber JSON report
  - `[-]` PDF reports `$`

- `[1.x ✓]` HTML Reports
  - `[1.x ✓]` Interactive dashboard
  - `[1.x ✓]` Timeline view
  - `[1.x ✓]` Step-by-step logs
  - `[1.x ✓]` Embedded screenshots/videos
  - `[v2]` Dark mode support
  - `[v2]` Modern Bootstrap 5 styling
  - `[ ]` Cosmetic improvements (spacing, colors, typography)
  - `[-]` No-JS simple mode

- `[-]` Run History & Insights `$`
  - `[-]` Test run history
  - `[-]` Trend analysis
  - `[-]` Flaky test detection

- `[-]` Coverage Reports `$`
  - `[-]` API coverage against OpenAPI
  - `[-]` Requirements coverage
  - `[-]` Code coverage integration

- `[1.x ✓]` 3rd Party Tool Integrations
  - `[1.x ✓]` CI/CD integration (Jenkins, GitHub Actions, etc.)
  - `[-]` Zephyr integration `$`
  - `[-]` Jira integration `$`
  - `[-]` Report server and aggregation `$`
  - `[-]` GitHub Action (official) `$`

- `[-]` Observability
  - `[-]` OpenTelemetry hook support
  - `[-]` Metrics export
  - `[-]` API monitoring `$`

---

### Test Generation

Automated test creation and data generation

- `[-]` Artifact Import / Conversion `$`
  - `[-]` Postman collection import
  - `[-]` OpenAPI/Swagger import
  - `[-]` cURL import
  - `[-]` HAR file import
  - `[-]` Migration from other tools (ReadyAPI, Parasoft, Tricentis)

- `[-]` Record & Replay `$`
  - `[-]` Browser API recording
  - `[-]` Browser UI recording
  - `[-]` Proxy-based recording
  - `[-]` AI-assisted recording cleanup

- `[-]` AI / ML `$`
  - `[-]` AI test generation
  - `[-]` AI test data generation
  - `[-]` LLM-guided test creation
  - `[-]` Claude skill for Karate
  - `[-]` Sample prompts library

- `[-]` Property Based Testing `$`
  - `[-]` Property-based assertions
  - `[-]` Hypothesis testing

- `[-]` Mutation / Fuzzing `$`
  - `[-]` API fuzzing
  - `[-]` Mutation testing

- `[-]` Fake Data Generation `$`
  - `[-]` Faker integration
  - `[-]` AI-based synthetic data

- `[-]` Security Testing `$`
  - `[-]` OWASP vulnerability scanning
  - `[-]` Security fuzzing
  - `[-]` Prompt injection testing

---

### AI & LLM Integration

AI-powered capabilities and LLM testing

- `[ ]` LLM-Friendly Interface
  - `[ ]` Discoverable CLI commands
  - `[ ]` Token-efficient output
  - `[ ]` Interactive REPL for LLMs
  - `[ ]` JavaScript API for LLM control
  - `[ ]` Stack traces with context

- `[-]` Testing AI Systems `$`
  - `[-]` Testing MCP servers
  - `[-]` Mocking MCP servers
  - `[-]` Mocking LLM APIs
  - `[-]` LLM effectiveness testing
  - `[-]` LLM comparison testing
  - `[-]` Prompt injection security testing

- `[-]` Built-in LLM Integration `$`
  - `[-]` CLI-based LLM integration
  - `[-]` MCP server (Xplorer)
  - `[-]` LLM-friendly specifications files

---

### Cross-Language Support

Polyglot and non-Java platform support

- `[v2]` Karate CLI
  - `[v2]` npm install support
  - `[v2]` macOS installer
  - `[v2]` Windows installer
  - `[v2]` Linux installer
  - `[-]` Signed installers `$`

- `[-]` Client Libraries
  - `[-]` Python client
  - `[-]` .NET client
  - `[-]` Go client
  - `[-]` Rust client

- `[1.x ✓]` Custom JAR Libraries
  - `[1.x ✓]` Custom JAR loading
  - `[-]` Extension to non-Java clients

---

### Commercial Tools

Enterprise and commercial offerings

- `[ ]` Retry & Report Aggregation Connector `$`
  - `[ ]` Rerun failed scenarios (rerun.txt) `$`
  - `[ ]` Multi-runner result consolidation (SuiteResult.merge) `$`
  - `[ ]` Retry-correct Scenario Outline reporting `$`

- `[v2]` Karate Xplorer `$`
  - `[v2]` Postman emulation `$`
  - `[v2]` Step-through debugging `$`
  - `[v2]` Visual API explorer `$`
  - `[-]` Hybrid CI/CD suites `$`
  - `[-]` Performance test reuse `$`

- `[-]` Requirements Management `$`
  - `[-]` Local-first requirements management
  - `[-]` Test coverage against requirements
  - `[-]` Impact analysis

- `[-]` API Governance `$`
  - `[-]` API linting (Spectral-like)
  - `[-]` API rating
  - `[-]` Governance checks

- `[-]` Code Quality Tools `$`
  - `[-]` Karate test linter
  - `[-]` Karate test formatter
  - `[-]` Static code analysis
  - `[-]` Code review tool

- `[-]` IoT & Embedded Testing `$`
  - `[-]` Client for embedded platforms
  - `[-]` IoT protocol testing
  - `[-]` Device-specific UI testing

- `[-]` Big Data Testing `$`
  - `[-]` Large dataset handling
  - `[-]` Data pipeline validation

---

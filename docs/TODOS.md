# TODOs

Scratch pad for tracking work across the project. See also [CAPABILITIES.yaml](./CAPABILITIES.yaml) for the full feature inventory.

---

## Runtime / Core

- [ ] Wire `configure afterScenario` hook in regular scenario execution (currently only works for mocks); also support strict mode where hook failures fail the scenario ([#2540](https://github.com/karatelabs/karate/issues/2540), [#2699](https://github.com/karatelabs/karate/issues/2699))
- [ ] `configure beforeScenario` hook for mocks ([#2239](https://github.com/karatelabs/karate/issues/2239))
- [ ] Expose caller's `karate.scenario` in mock feature files so mocks can vary behavior by calling scenario ([#2618](https://github.com/karatelabs/karate/issues/2618))
- [ ] HTTP/2 support
- [ ] Response time validation
- [ ] `@retry` tag — re-run failed scenarios, CLI: `karate --rerun target/karate-reports/rerun.txt` ([#2578](https://github.com/karatelabs/karate/issues/2578))
- [ ] `@report=false` tag (exclude scenario from reports but still execute)
- [ ] `SuiteResult.merge()` API to consolidate multiple runner results into a single report ([#2337](https://github.com/karatelabs/karate/issues/2337))
- [ ] Priority 7: JavaScript script execution (`*.karate.js` files)
- [ ] Priority 9: `configure report = { showJsLineNumbers: true }`
- [ ] Priority 9: `karate-base.js` (shared config from classpath JAR)
- [ ] Step definitions with regex pattern matching
- [ ] Multiple Suite Execution: `Runner.suites().add(...).parallel(n).run()`

## JavaScript Engine (karate-js)

- [ ] BigInt -> `BigInteger` (large IDs, timestamps, financial identifiers)
- [ ] BigDecimal -> `BigDecimal` (money/finance)
- [ ] ArrayBuffer -> `byte[]` (raw binary data)
- [ ] JsRegex + JavaMirror (return `Pattern` from `getJavaValue()`)
- [ ] Set -> `java.util.Set` (deduplication, membership)
- [ ] Map (proper JS Map) -> `java.util.Map` (ordered keys, non-string keys)
- [ ] Iterator/for-of -> `java.util.Iterator`
- [ ] Console log levels — `console.warn()`, `console.error()`, `console.trace()` etc. should map to appropriate log levels (WARN, ERROR, TRACE) when cascading onto core/karate logging
- [ ] `async`/`await` -> `CompletableFuture` / virtual threads
- [ ] `setTimeout()` and timer functions
- [ ] ES Modules (`import`/`export`) for JS reuse across tests

## Parser / IDE

- [ ] Code formatting (JSON-based options, token-based and AST-based strategies)
- [ ] Source reconstitution (regenerate source from AST)
- [ ] Embedded language support (JS highlighting inside Gherkin steps)

## CLI

- [ ] `--listener` / `--listener-factory` CLI flags
- [ ] Mock CLI options (`-m`, `-s`, `-W`, etc.)

## Reports

- [ ] HTML report cosmetic improvements
- [ ] PROGRESS events for real-time progress display
- [ ] `FeatureResult.fromJson()` for offline report generation from JSONL
- [ ] JS line-level logging in reports (opt-in)
- [ ] Report rendering test cases for nested calls (`karate.call()` from JS, Background calls, multi-level chains)

## WebSocket

- [ ] WsServerOptions, WsServer, WsServerHandler (not started)
- [ ] Unit tests for echo server (in progress)

## Browser / Driver

- [ ] Playwright emulation (Firefox/WebKit via Playwright CDP)
- [ ] `Runner.Builder` exposure via `protocol.runner()` for Gatling

## Cross-Language / Platform

- [ ] Continue Karate CLI development (platform binaries)
- [ ] .NET, Python, Go client libraries
- [ ] Desktop automation (macOS, Windows, Linux)
- [ ] Mobile automation (iOS, Android)

## Mocks

- [ ] Expand JS mock documentation in MOCKS.md — more examples of `pathMatches`, session patterns, and comparison with feature-file mocks

## Docs / Quality

- [ ] Create release notes and blog post for karate 2.0.0
- [ ] Update [karate-docs](https://github.com/karatelabs/karate-docs) (Docusaurus site) with v2 documentation
- [ ] Update MIGRATION_GUIDE.md with WebDriver section (type mapping, capabilities config, CDP-only operations)
- [ ] Merge karate-demo + karate-e2e-tests into [karate-examples](https://github.com/karatelabs/karate-examples)
- [ ] Clean up GitHub issues (close stale, redirect to docs.karatelabs.io)
- [ ] Redirect GitHub wiki pages to docs.karatelabs.io
- [ ] Release compatible IDE plugins (VS Code extension, IntelliJ plugin)
- [ ] Automated version bumping and changelog generation
- [ ] Automate karate-examples version bump with CI/CD (see [RELEASING.md](./RELEASING.md) step 7)

## CI/CD

- [ ] CI/CD workflow for karate-examples to bump version and run tests on release
- [ ] Telemetry — anonymous usage ping, opt-out: `KARATE_TELEMETRY=false`

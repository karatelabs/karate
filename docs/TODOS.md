# TODOs

Scratch pad for tracking work across the project. See also [CAPABILITIES.yaml](./CAPABILITIES.yaml) for the full feature inventory.

---

## Runtime / Core

- [ ] HTTP/2 support
- [ ] Response time validation
- [ ] Priority 7: JavaScript script execution (`*.karate.js` files)
- [ ] Priority 9: `configure report = { showJsLineNumbers: true }`
- [ ] Priority 9: `karate-base.js` (shared config from classpath JAR)
- [ ] Step definitions with regex pattern matching
- [ ] Multiple Suite Execution: `Runner.suites().add(...).parallel(n).run()`
- [ ] Shared "misc" JS engine for tag-selector evaluation, `@setup` dynamic expressions, and examples-table cell interpolation. Currently `TagSelector.evaluate` creates a fresh `Engine` per call — low individual cost but adds up across per-section pre-filter + per-scenario runtime evaluation. Would need per-thread or pooled engines for parallel execution.

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
- [ ] Add SSE browser-side test to `DriverFeatureTest` — verify `EventSource` connects to `SseHandler` and receives events in a real browser. Current SSE tests only validate server-side wire format. This would cover the HTMX `sse-swap` and Alpine `EventSource` patterns end-to-end.
- [ ] Consider `find` / `findAll` as aliases for `locate` / `locateAll` — jQuery, Cypress, Selenium (`findElement`) all use `find` for scoped descendant lookups, and `$()` / `$$()` shorthands are near-universal. `locate` is internally consistent with Karate's "locator" noun but non-standard elsewhere. Cost is ~5 lines (bind as aliases in `Driver.jsGet` and `BaseElement.jsGet`); benefit is one less thing for users arriving from other frameworks to learn. Skip until someone actually asks — existing `locate` is established, documented, and v1-compatible.

## Cross-Language / Platform

- [ ] Continue Karate CLI development (platform binaries)
- [ ] .NET, Python, Go client libraries
- [ ] Desktop automation (macOS, Windows, Linux)
- [ ] Mobile automation (iOS, Android)

## Mocks

- [ ] Expand JS mock documentation in MOCKS.md — more examples of `pathMatches`, session patterns, and comparison with feature-file mocks
- [ ] **`context.synchronized(name, fn)` for JS-file mocks.** `MockHandler.apply()` wraps every Karate-feature mock request in a `requestLock`, so feature-file mocks serialize naturally and shared mutable state (singleton session, caches) "just works". `ServerRequestHandler` (the JS-file mock path) has no equivalent — concurrent requests race on shared state, and the races aren't fixable in user data structures alone: JS array ops (`push`, `splice`, `sort`, …) are non-atomic read-modify-write sequences in `JsArrayPrototype` itself. Two manifestations seen in repro: silent item loss (T2 reads stale `len`, `set(len, x)` overwrites T1's append) and `IndexOutOfBoundsException` / `ConcurrentModificationException` from `JsArray$ArrayLength.applySet` taking the truncate path on a list that grew under it. Auditing every Array.prototype/Object operation for atomicity isn't feasible (single-threaded execution is a JS-spec invariant — no engine promises this), so the right fix is to expose locking to user code.

  **Decision**: ship `context.synchronized(name, fn)` on the JS-file-mock `context` namespace (matches the existing JS-mock idiom of `context.uuid()` etc.; avoids introducing a new `karate` binding for one method). Reentrant, named, callback-only (forces try/finally; no leaks). Lock registry is a `ConcurrentHashMap<String, ReentrantLock>` on `ServerConfig` so each server is isolated.

  **Why not a global `serverConfig.singleThreadedJs(true)` knob**: punishes read-only / non-shared paths in the same app and turns parallel JS mocks back into the same single-threaded performance profile that feature-file mocks already have — power users want to be selective.

  **Why not also bind on the test-scenario `karate` namespace today**: footgun risk. Easy to silently kill `parallel(N)` suite throughput, hides scenario-isolation problems, lock-name typos are different locks (silent), unbounded lock-map growth from per-id keys. Defer until real demand surfaces.

  **Workaround in the meantime**: wrap the entire request handler in a `ReentrantLock` (mirrors what `MockHandler` does internally). One-liner in user code:

  ```java
  ReentrantLock lock = new ReentrantLock();
  Function<HttpRequest, HttpResponse> inner = new ServerRequestHandler(config, resolver);
  Function<HttpRequest, HttpResponse> serialized = req -> {
      lock.lock();
      try { return inner.apply(req); } finally { lock.unlock(); }
  };
  ```

  See `karate-todo`'s `App.handler()` for a worked example.

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

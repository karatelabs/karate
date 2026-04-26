# karate-js-test262

ECMAScript [test262](https://github.com/tc39/test262) conformance harness for
karate-js — the **living document** for evolving karate-js toward spec
compliance: a reproducible pass/fail matrix across the whole ES surface area,
plus the roadmap for which work to tackle in which order. **Not** published to
Maven Central — internal testing/reporting harness.

> **See also (start a fresh session here):**
> [../karate-js/README.md](../karate-js/README.md) for what karate-js is ·
> [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) for engine architecture
> (types, prototype system, exception model, **spec invariants**, benchmarks) ·
> [../docs/DESIGN.md](../docs/DESIGN.md) for the wider project design ·
> [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
> for the authoritative test-runner spec.

---

## Why this exists

karate-js is a lightweight JavaScript engine. Hand-written JUnit tests in
`karate-js/src/test/java/` cover behaviors we care about but tell us nothing
about **spec compliance**. Running tc39/test262 gives us ground truth — and
the module's committed skip list
([`etc/expectations.yaml`](etc/expectations.yaml)) becomes the declarative
statement of "what karate-js deliberately does not support."

The **real bar** is not spec-lawyer compliance — it is *can karate-js run
real-world JavaScript written in the wild, especially by LLMs?* test262 is the
scorecard; pragmatic ES6 coverage of idiomatic code is the goal.

---

## Working principles

Operating-mode maxims for engine-compliance work. Treat as load-bearing.

1. **Real-world JS first; test262 is the scorecard, spec is ground truth.** A
   fix that unblocks 500 idiomatic tests beats one that tightens a rare
   spec corner. `test/language/**` failures tell you the parser can't read
   common code; `test/built-ins/AggregateError` failures tell you a rarely-used
   constructor is missing — same FAIL count, very different signal. Existing
   JUnit tests can be wrong: when the spec disagrees, the spec wins — fix the
   test along with the engine.

2. **Fix friction before moving on.** Bad error messages in `results.jsonl`,
   parse-vs-runtime classification gaps, missing report fields, `--single -vv`
   not showing what you need — stop and fix the tooling rather than working
   around it. The `ContextListener` / `Event` / `BindEvent` surface is fair
   game for testability; not a load-bearing API guarantee.

3. **Errors must look like JavaScript, not Java.** Stronger form of #2: the
   engine's user-visible error surface is its own contract. Any raw Java
   exception escaping `Engine.eval(...)` is a correctness bug. See
   [JS_ENGINE.md § Exception Handling](../docs/JS_ENGINE.md#exception-handling).

4. **Protect the hot path — pay edge-case cost on the edge case.** New
   features dispatch via fast-path-then-fallback: sentinels over thrown
   signals, type-check rare cases after the common-case miss, parse-time
   analysis over inner-loop checks. After any non-trivial engine change, run
   `EngineBenchmark profile` and compare against
   [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).

5. **Refactor toward elegance — fix inline or write it down, never carry the
   smell forward.** Spot near-duplicate dispatch or wrong-layer workarounds:
   either fix it now (cheapest moment is the session that just touched the
   area) or file a Deferred TODO with concrete pointers (file, method, what
   the unification looks like). Vague "this could be cleaner" notes are
   worthless. A workaround is a clue that the layer below is wrong.

6. **Batched commits are fine if the message enumerates the changes.** A
   single commit covering several related fixes from one session is preferred
   over the ceremony of splitting hunks across files. What matters is that
   the commit message lets a future bisect attribute regressions.

---

## Running

**All commands run from `karate-js-test262/`** (the runner resolves
`etc/expectations.yaml` and `test262/` relative to cwd). Use `-f ../pom.xml`
so Maven finds the parent reactor. After any change under `karate-js/`,
**re-install it first** — the runner uses the karate-js jar from your local
Maven repo, not from the reactor.

### Quick start

```sh
cd karate-js-test262
etc/fetch-test262.sh                                       # first time only — shallow clone
etc/run.sh --only 'test/language/expressions/addition/**'  # install + run + HTML
open target/test262/html/index.html
```

**Every run writes a fresh, self-contained directory:**
`target/test262/run-<timestamp>/` containing `results.jsonl`,
`results.jsonl.partial`, `run-meta.json`, `progress.log`, and `html/`. Old
runs are never touched (clean up with `mvn clean`). The runner prints
`Run dir: <path>` on completion; pass that to `Test262Report --run-dir
<path>` to render the HTML. `etc/run.sh` plumbs the path through
automatically.

[`etc/run.sh`](etc/run.sh) does install + run + HTML in a single command:

```sh
etc/run.sh                                                 # full suite + HTML
etc/run.sh --only 'test/language/**' --max-duration 300000 # 5-min cap
# Live tap (path is printed by the runner; substitute the actual <ts>):
tail -f target/test262/run-<ts>/progress.log               # live progress
tail -f target/test262/run-<ts>/results.jsonl.partial      # live per-test JSONL
```

### Driving the pieces by hand

```sh
# 1. After editing karate-js/, refresh the local Maven repo
mvn -f ../pom.xml -pl karate-js -o install -DskipTests

# 2. Run the suite (sequential; minutes to tens of minutes)
#    Run-dir defaults to target/test262/run-<timestamp>/; the runner prints it.
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/**"

# 3. HTML report — required: --run-dir pointing at step 2's output
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.mainClass=io.karatelabs.js.test262.Test262Report \
    -Dexec.args="--run-dir target/test262/run-<ts>"
```

**Why install instead of `-am`:** `exec:java` is a direct goal, not a phase,
so Maven invokes it on every selected reactor project. With `-am`, the reactor
also includes `karate-parent` (no `mainClass`), and the goal aborts the run
before our module is reached. Installing karate-js to the local repo first,
then running without `-am`, sidesteps the reactor entirely.

### Flags

| Flag | Default | Purpose |
|---|---|---|
| `--expectations <path>` | `etc/expectations.yaml` | skip list manifest |
| `--test262 <path>` | `test262` | suite clone dir |
| `--run-dir <path>` | `target/test262/run-<timestamp>/` | output dir for this run; everything (results.jsonl, run-meta.json, progress.log, html/) goes inside |
| `--timeout-ms <n>` | `10000` | per-test watchdog (infinite-loop guard) |
| `--max-duration <ms>` | `0` (unlimited) | overall wall-clock cap; writes partial results and prints `Aborted:` on hit |
| `--only <glob>` | — | restrict to matching paths |
| `--single <path>` | — | run one test, no file writes |
| `-v` / `-vv` | off | (with `--single`) `-v` prints parsed metadata + source location; `-vv` adds full source |

Runs are **silent except failures + periodic progress**. A `FAIL <path> —
<type>: <msg>` line prints to stdout per failure; a `[progress]` line every
500 tests or every 5 seconds. Banner / progress / summary lines are mirrored
to `<run-dir>/progress.log` via Logback so `tail -f` is a lightweight live
view. Per-FAIL detail lives only in JSONL (mirroring would duplicate without
new signal).

### Hang handling

The runner uses a single-thread `ExecutorService` to enforce `--timeout-ms`
per test. The karate-js engine doesn't poll `Thread.interrupt()`, so
`cancel(true)` can't stop the underlying thread. When a timeout fires, the
runner **retires the executor** (shuts it down, creates a fresh one) so
subsequent tests don't queue behind the stuck thread. Net cost of a genuine
hang: one abandoned daemon thread, one Timeout row in `results.jsonl`, a few
ms of recreate overhead.

### Driving from scripts / agents

Pass `--max-duration <ms>` so a catastrophic engine bug can't wedge the
caller. The periodic `[progress]` line gives a machine-parseable heartbeat —
stream stdout and tail the most recent line without waiting for the run.

---

## Active priorities

Action list — start at the top. Ordered by core-engine confidence
cross-multiplied by score impact. Re-probe with the relevant `--only` glob
before scoping a session.

**Current state baseline** (last sampled 2026-04-26):

| Slice | Pass | Fail | Skip | Total |
|---|---|---|---|---|
| `test/language/**` | 4327 | 3776 | 15542 | 23645 |
| `test/built-ins/Math/**` | 293 | 33 | 1 | 327 |
| `test/built-ins/Number/**` | 232 | 96 | 12 | 340 |
| `test/built-ins/String/**` | 456 | 717 | 50 | 1223 |
| `test/built-ins/Array/**` | 1256 | 1645 | 180 | 3081 |
| `test/built-ins/Object/**` | 2041 | 1245 | 125 | 3411 |
| `test/built-ins/Date/**` | 500 | 52 | 42 | 594 |
| `test/built-ins/Function/**` | 229 | 152 | 128 | 509 |
| `test/built-ins/Symbol/**` | 2 | 47 | 49 | 98 |

### Top items

1. **Full Symbol primitive (layer 2).** Well-known symbols already exposed
   as string-keyed stand-ins (see [JS_ENGINE.md § Iteration](../docs/JS_ENGINE.md#iteration)).
   Residual is *Symbol-as-primitive*: `typeof Symbol.X === "symbol"`,
   `Symbol()` constructor with unique identity, `Symbol.for` / `keyFor`
   global registry, `symbol.description`, `Object.getOwnPropertySymbols`,
   `Reflect.ownKeys` returning symbol keys. Touches: new primitive type
   in `Terms.typeOf` / `eq` / coercion sites; property-key abstraction so
   `JsObject._map` (currently `Map<String,Object>`) can carry symbol
   keys distinct from string keys; `_attrs` / `_tombstones` /
   `isOwnProperty` / intrinsic-attribute pipeline all key by `String` and
   need parallel symbol storage. ~580 SKIPs today; 2-4 sessions of
   focused work. Re-probe before scoping with `test/built-ins/Symbol/**`
   to size per-feature cluster.

2. **Array methods using `CreateDataPropertyOrThrow` + `Symbol.species`.**
   `JsArray._attrs` per-index tracking is in place (see [JS_ENGINE.md
   § Property attributes](../docs/JS_ENGINE.md#property-attributes)), but
   the `Array/prototype/*-target-array-with-non-writable-property` cluster
   stays red because the prototype methods (splice / concat / filter /
   slice / flat / from) don't honor `arr.constructor[Symbol.species]` and
   write results via `[[Set]]` instead of `CreateDataPropertyOrThrow`
   (defineProperty-style, all-true attrs that overwrite a non-writable
   slot). Two changes that wedge together: species lookup on the result-
   array allocation site, and a `defineOwn` write path in the bulk
   methods. Most affected tests also have `features: [Symbol.species]`
   or `[generators]`, so re-probe with `--only test/built-ins/Array/
   prototype/**` to size the actually-runnable subset.

3. **Array length truncation across non-configurable indices
   (§10.4.2.4).** `defineProperty(arr, "1", {configurable: false}); arr.
   length = 1` should TypeError; today `setLength` truncates silently.
   Same for `arr.pop()` / `shift()` when an index in the truncated range
   is non-configurable. The `Array/length/define-own-prop-length-*` and
   `Array/prototype/{pop,shift,unshift,push}/set-length-*-non-writable`
   clusters depend on this. Discoverable now that `_attrs` carries the
   configurable bit per index.

### Background

Picked off opportunistically when nearby — not session-sized on their own.

- **Map / Set residuals.** `Map.prototype.{groupBy, getOrInsert,
  getOrInsertComputed}` (ES2024+). `Set.prototype.{difference, intersection,
  union, isDisjointFrom, isSubsetOf, isSupersetOf, symmetricDifference}`
  (ES2025; some test cases use `with` blocks). `Object.getPrototypeOf(Map.
  prototype)` returns `JsObjectPrototype.INSTANCE` (singleton) where tests
  compare against `Object.prototype` (the global) — needs identity sharing.
  Same for `Set.prototype`.

- **`.length` / `.name` rollout to remaining prototypes.** `JsBuiltinMethod`
  infra is in place. Incremental wins are small — most residual `length.js`
  fails are array-instance length semantics, most `name.js` fails are
  Symbol-gated. Treat as background cleanup.

- **Utility-method residual sweep.** Pick off remaining built-in gaps as
  triage surfaces them.

- **Destructuring residuals.** Bulk works; the long tail is low-leverage —
  lexer identifier-escape support, TDZ / init-order corners, negative parse
  tests needing pattern-vs-literal two-mode parsing.

- **Cleanup residuals.** Occasional `"null"` NPE paths, `IllegalName` JDK
  lambda leak, `Java heap space` OOM in array-slice paths. Grab while nearby.

- **Directive prologue (`"use strict"`) flip.** Parser tolerates the string
  without activating strict-mode assertions. Skip triage is done (`flags:
  [onlyStrict]` entry in `expectations.yaml`). Real strict-mode implementation
  (with / duplicate-params / eval-assign / octal-literal negative checks) is
  a separate, larger project; revisit only if a meaningful test cluster
  outside `onlyStrict` depends on it.

---

## Deferred TODOs

Items left for later; un-scheduled but tracked.

- **Promises + async/await + setTimeout.** All skipped today (`feature:
  Promise`, `feature: async-functions`, `flag: async`, `feature:
  Symbol.asyncIterator`, `include: promiseHelper.js`). karate-js is
  synchronous — no event loop, no microtask queue, no timers. When eventually
  scheduled: smooth Java interop with `CompletableFuture` / callback APIs;
  graceful degradation when async/await is decorative; simple multi-threading
  for real async I/O (websockets, Kafka, gRPC). Microtask model / thread
  strategy / `setTimeout` backing belong in the implementation session.

- **Class syntax (ES6).** All skipped (`feature: class`, `class-fields-public`,
  `class-fields-private`, `class-methods-private`, `class-static-fields-public`,
  `class-static-methods-private`). Engine has prototype machinery + `new`
  but no parser support for `class` / `extends` / `super` /
  method-definition. LLMs writing glue/test code default to function +
  prototype style; pick up only if real workload demands. Currently fails
  loudly at parse time with `SyntaxError` — the right shape.

- **Unify the read-side dot dispatch in `PropertyAccess`.** Write-side is
  unified through `resolveWriteSite` + `AccessSite` (10 near-duplicate
  dispatch wrappers collapsed). Read-side (`getRefDotExpr`,
  `getCallableRefDotExpr`) still has mirrored bridge-fallback + `?.` logic
  with slightly different return shapes. Defer until there's a third caller
  that wants the same resolution shape.

- **Replace hand-rolled YAML parser with SnakeYAML.** `Expectations.java` and
  `Test262Metadata.java` are hand-rolled to avoid the dep. They break on `#`
  in quoted reasons, block-scalar (`|`, `>`) `description:` fields, and
  block-form list values. Add SnakeYAML when we next touch either file.

- **Make `--resume` refresh stale records.** Currently echoes back records
  for tests that no longer exist or are now SKIP'd. Either gate on
  test-still-exists + not-in-skip-list, or rename to `--resume-crash-only`.

- **Cache parsed harness ASTs, not source text.** `HarnessLoader` re-parses
  `assert.js`, `sta.js`, and each test's `includes:` on every test;
  ~50k re-parses per full-suite run is a measurable chunk of wall time.

- **`phase: resolution` (module-resolution) negative tests.** Currently
  conflated with `runtime`. Modules are globally skipped via `flags:
  [module]`, so this is latent.

- **Structured `$262` surface.** `AbstractModuleSource`, `IsHTMLDDA`,
  `agent.broadcast/getReport/sleep/monotonicNow` are absent — all
  feature-gated, unreachable today. Add stubs when a feature comes off the
  skip list.

- **`readHeadSha` path fragility.** `Test262Runner.readHeadSha` walks up
  `cli.test262.getParent().getParent()` to find the karate repo. Prefer a
  `git rev-parse HEAD` subprocess, or a `--karate-sha` flag.

- **Surface per-test console capture in `ResultRecord`.** `evaluate(...)`
  already wires `Engine.setOnConsoleLog(...)` to a per-test sink (discarded
  today). Plumb it into `ResultRecord` for FAIL rows so `--single -vv` and
  the HTML drill-down can show what the test printed.

- **Parallel execution.** Prior attempts showed no speedup on moderate slices
  (thread context-switch overhead beat the 1ms per-test eval cost) and the
  engine doesn't poll `Thread.interrupt()`. Revisit when per-test cost grows
  enough that 8× parallelism clearly wins, or when the engine learns to
  cooperatively abort.

- **Commit `target/test262/results.jsonl` once stable.** Currently gitignored
  (too noisy in git while engine iterates). Re-evaluate when the engine is
  stable enough that diffs are meaningful.

- **Thin `EngineException` once `JsError` goes public.** `EngineException`
  carries a `jsErrorName` string that duplicates what's in the cause-chain's
  `JsErrorException` payload. `JsError` is package-private, so we can't just
  expose the payload. Once a host use case forces `JsError` public, drop the
  name field.

---

## Engine guarantees

Spec invariants and engine architecture decisions live in
[**JS_ENGINE.md § Spec Invariants**](../docs/JS_ENGINE.md#spec-invariants-test262-driven)
— treat as load-bearing; sessions that need to violate one bring the rule
up for review explicitly. The runner depends on `ParserException`
propagation (parse-phase classification), `EngineException` wrapping
(runtime), and the structured `getJsErrorName()` / `getJsMessage()`
surface — `ErrorUtils` reads those directly rather than parsing message
strings.

---

## Engine code map

When test262 surfaces a fix, this table is the muscle-memory pointer.

| Concern | Engine source | JUnit test | test262 path |
|---|---|---|---|
| Lexer (tokenization) | `karate-js/.../parser/JsLexer.java`, `BaseLexer.java`, `TokenType.java`, `Token.java` | `JsLexerTest`, `LexerBenchmark` | `test/language/literals/**` (syntax-level) |
| Parser (AST build) | `karate-js/.../parser/JsParser.java`, `BaseParser.java`, `NodeType.java`, `Node.java` | `JsParserTest`, `ParserExceptionTest`, `TermsTest` | `test/language/expressions/**`, `statements/**`, `types/**` (parse-level) |
| Parse errors | `karate-js/.../parser/ParserException.java`, `SyntaxError.java` | `ParserExceptionTest` | parse-phase negative tests |
| Interpreter (eval) | `karate-js/.../js/Interpreter.java`, `CoreContext.java`, `ContextRoot.java` | `EvalTest` (language-semantics catch-all) | `test/language/expressions/**`, `statements/**`, `types/**` (runtime) |
| Built-ins / types | `karate-js/.../js/JsObject.java`, `JsArray.java`, `JsString.java`, `JsError.java`, `JsFunction.java`, prototype classes (`JsArrayPrototype` etc.), `Terms.java` (operators/coercion) | `JsArrayTest`, `JsStringTest`, `JsObjectTest`, `JsMathTest`, `JsNumberTest`, `JsJsonTest`, `JsDateTest`, `JsRegexTest`, `JsFunctionTest`, `JsBooleanTest` | `test/built-ins/Array/**`, `String/**`, `Object/**`, `Math/**`, `Number/**`, `JSON/**`, `Date/**`, `RegExp/**`, `Function/**`, `Boolean/**` |
| Runtime exceptions | `karate-js/.../js/EngineException.java` | `EngineExceptionTest` | error-propagation regressions |
| Performance regression | — | `EngineBenchmark` | (gut-check after engine change) |

Guidance:
- **Pure tokenization change** → `JsLexer` + `JsLexerTest`.
- **Grammar change** → `JsParser` + `JsParserTest` (AST shape) + `EvalTest`
  (runtime semantics).
- **Semantics-only change** → `Interpreter.java` or the relevant
  `Js*Prototype`; test in `EvalTest` or the matching `Js*Test`.
- `NodeType` and `TokenType` are small enums — consult them before inventing
  new node/token kinds; many "feels like I need a new node" fixes turn out
  to be wiring an existing one to a new call site.
- **`EngineTest` is *not* a test262 sink.** It covers the engine's
  integration surface: `ContextListener` events, `BindEvent`, `Engine.put`
  lifecycle, Java↔JS exception boundary, `$BUILTIN`/prototype immutability.
- **When to split a `Js*Test`:** don't pre-emptively. If a cluster inside
  `EvalTest` grows to ~10+ tests on one feature (destructuring, TDZ,
  template literals), spin it out — let the split follow the evidence.

---

## Skip list

There is only one concept: **SKIP**. A test matching any rule in
[`etc/expectations.yaml`](etc/expectations.yaml) is not run and appears as
`{"status":"SKIP",...}` in results. Everything else is attempted; failures
are failures.

Match order: `paths → flags → features → includes`. **First match wins.**
Every entry requires a `reason`.

**Precedence example.** A test at `test/language/statements/class/foo.js`
with `flags: [module]` and `features: [Symbol]` is skipped with the *module*
reason (the `flags` match fires before `features` is consulted). If you want
`features: [Symbol]` to win, don't have a matching flag rule.

Starter set covers Symbol, BigInt, generators, class syntax, Proxy, Reflect,
Promises, async/await, Temporal, TypedArray beyond Uint8Array, WeakRef,
ArrayBuffer, and the suite directories `test/intl402/`, `test/staging/`,
`test/annexB/`. To add a skip: edit the YAML under the right section with a
`reason`. To remove a skip: delete the entry, re-run the relevant `--only`
glob, debug failures with `--single -vv`.

---

## Results schema

Two JSONL files during a run:

- **`target/test262/results.jsonl.partial`** — appended per test as results
  arrive, flushed per write. **Run order, not sorted.** Deleted on clean
  exit; preserved on abort (`--max-duration` hit, Ctrl-C, JVM kill).
- **`target/test262/results.jsonl`** — canonical output, **sorted
  alphabetically by path**, atomically written at end-of-run (tmp + rename).
  This is what tooling reads and what `--resume` skips-seen against.

Example line shape (same in both):

```jsonl
{"path":"test/language/expressions/addition/S11.6.1_A1.js","status":"PASS"}
{"path":"test/.../something.js","status":"FAIL","error_type":"TypeError","message":"foo is not a function"}
{"path":"test/.../bigint-test.js","status":"SKIP","reason":"BigInt not supported"}
```

Error types are classified into:
`SyntaxError | TypeError | ReferenceError | RangeError | Error | Timeout |
Harness | Unknown` by inspecting message prefixes (the engine emits
`"TypeError: ..."` style messages at most failure sites). The classifier
itself is in `ErrorUtils`.

---

## Recipes

### Debug one failing test

```sh
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -vv"
```

`-v` prints parsed YAML metadata (description / flags / features / includes
/ negative), the classification, and — if the engine attached a position
— a `location: <path>:<line>:<col>` line. `-vv` additionally prints the
full test source. `--single` does no file writes; pass any test path
(relative to the test262 clone root) directly. **No HTML drill-down page is
generated** — the details.html report shows path + error_type + message
inline; for full source / repro context, run `--single -vv` directly.

### Check performance after an engine change

The conformance suite allocates a fresh `Engine` per test (~50k tests); small
regressions compound into minutes of wall time. **Prefer profile mode** — the
30 s warm loop is JIT-stable and directly comparable to the reference table.

```sh
mvn -pl karate-js -q test-compile

# Profile mode (30 s warm loop; JIT-stable, ~16k iterations averaged).
# This is the mode the reference table in JS_ENGINE.md was recorded in.
java -cp "karate-js/target/classes:karate-js/target/test-classes:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1):$(find ~/.m2/repository -name 'json-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'accessors-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'asm-9*.jar' | grep -v asm-tree | grep -v asm-commons | head -1)" \
    io.karatelabs.parser.EngineBenchmark profile

# Fast mode (median of 10 cold runs) — noisy, gut-check only
java -cp "…same classpath…" io.karatelabs.parser.EngineBenchmark
```

Compare against [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).
If averages moved >±10%, understand why before merging. If unavoidable
(correctness > speed), update the reference table in `JS_ENGINE.md` in the
same commit.

### Bump the pinned test262 SHA

Edit `TEST262_SHA=...` at the top of `etc/fetch-test262.sh`, delete the local
`test262/` directory, re-run the script. All subsequent runs use the new
commit. Coordinate bumps with whoever else is iterating — the suite itself
evolves.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `expectations file not found: etc/expectations.yaml` | Wrong directory. `cd karate-js-test262` first. |
| `test262 directory not found: test262` | Haven't run `etc/fetch-test262.sh` yet. |
| `Failed to execute goal ... exec-maven-plugin ... on project karate-parent: 'mainClass' ... missing` | Used `-am` with `exec:java`. Don't — install `karate-js` separately and run without `-am`. |
| Engine change has no effect on test262 output | Forgot `mvn ... -pl karate-js -o install -DskipTests`. The runner uses the local Maven repo jar, not the reactor classpath. |
| `Test262Report` says `--run-dir <path> is required` | Pass the path the runner printed on completion: `--run-dir target/test262/run-<ts>`. `etc/run.sh` does this for you. |
| Where's my report? | The runner prints `Run dir: <path>` on completion. Look in `<path>/html/index.html`. Each invocation creates a fresh `run-<timestamp>/` dir; nothing is overwritten. |
| `ReferenceError: <name> is not defined` on common classes (`ReferenceError`/`RangeError`) | Known first-order gap — those constructors are not registered globals yet. |
| Suite hangs on one test | Infinite loop; watchdog kicks in at `--timeout-ms`. The inner executor is retired and replaced; a genuine hang leaks one daemon thread and keeps going. Bisect with `--only`, or add `--max-duration` as a safety net. |
| Driving from a script that must not block | Pass `--max-duration <ms>`. On hit, partial results written and `Aborted:` replaces `Summary:`. |
| Tests that used to pass now fail | Run `EngineBenchmark` too — perf regression sometimes manifests as timeouts before correctness. |
| `target/test262/` growing unbounded across iteration sessions | No auto-pruning; each run writes its own `run-<ts>/`. `mvn clean` wipes the lot. |

---

## Directory layout

```
karate-js-test262/
├── TEST262.md                         # this file
├── pom.xml                            # Maven module (deploy explicitly disabled)
├── etc/
│   ├── expectations.yaml              # declarative SKIP list (committed)
│   ├── fetch-test262.sh               # shallow clone of tc39/test262 at pinned SHA
│   └── run.sh                         # one-shot: install + run + HTML
├── src/main/java/…/test262/           # runner + report + helpers
├── src/test/java/…/test262/           # unit tests for the harness itself
├── src/main/resources/report/         # HTML/CSS/JS templates for the report
├── src/main/resources/logback.xml     # logger config (file appender → target/test262/)
├── test262/                           # [gitignored] the cloned suite
└── target/test262/                    # [gitignored] one subdir per run
    └── run-<timestamp>/               # self-contained per-run dir
        ├── results.jsonl              # per-test pass/fail/skip, sorted by path (end of run)
        ├── results.jsonl.partial      # live feed — appended per test, flushed; deleted on clean exit, kept on abort
        ├── run-meta.json              # per-run context (test262 SHA, karate-js ver+SHA, JDK, OS, started/ended, counts)
        ├── progress.log               # banner + [progress] lines + final summary
        └── html/                      # two-file static HTML report
            ├── index.html             # tree + per-slice summary tiles
            └── details.html           # full per-test list with search + status filter
```

Each run is self-contained and immutable; old runs persist until `mvn clean`.
The CI workflow uploads `target/test262/` (parent) as a single artifact.

---

## CI

A `workflow_dispatch`-only workflow at
[`.github/workflows/test262.yml`](../.github/workflows/test262.yml) runs
`etc/fetch-test262.sh` + the runner + the report, and uploads the whole
`target/test262/` directory as a single artifact. Never triggered
automatically — kick off from the Actions tab when you want a fresh run. Two
inputs (`only` and `timeout_ms`) default to full-suite / 10 s per test.

The module's `pom.xml` sets `maven.deploy.skip=true` / `gpg.skip=true` /
`skipPublishing=true` so the release workflow does not publish this module to
Maven Central.

---

## References

- [tc39/test262](https://github.com/tc39/test262) — the suite
- [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md) — authoritative runner spec
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, type system, exception model, **spec invariants**, benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

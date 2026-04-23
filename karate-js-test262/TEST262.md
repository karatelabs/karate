# karate-js-test262

ECMAScript [test262](https://github.com/tc39/test262) conformance harness for
karate-js.

This module is the **living document** for evolving karate-js toward spec
compliance: a reproducible pass/fail matrix across the whole ES surface area,
plus the roadmap for which tiers to tackle in which order. It is **not**
published to Maven Central — it is an internal testing/reporting harness.

> **See also (start a fresh session here):**
> [../karate-js/README.md](../karate-js/README.md) for what karate-js is ·
> [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) for engine architecture
> (types, prototype system, exception model, benchmarks) ·
> [../docs/DESIGN.md](../docs/DESIGN.md) for the wider project design ·
> [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
> for the authoritative test-runner spec.

---

## Quick start

**All commands run from the `karate-js-test262/` directory** (the runner
resolves `etc/expectations.yaml` and `test262/` relative to the current
working directory). If you run from the repo root, pass `--expectations`
and `--test262` as absolute paths.

```sh
cd karate-js-test262
etc/fetch-test262.sh                              # first time only — shallow clone
etc/run.sh --only 'test/language/expressions/addition/**'   # install + run + HTML
open target/test262/html/index.html
```

Or drive the three Maven invocations yourself:

```sh
cd karate-js-test262

# 1. Install current karate-js to local Maven repo so the runner picks it up
mvn -f ../pom.xml -pl karate-js -o install -DskipTests

# 2. Run the conformance runner (uses the just-installed karate-js)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/addition/**"

# 3. Generate the HTML report
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.mainClass=io.karatelabs.js.test262.Test262Report
open target/test262/html/index.html
```

**Why install instead of `-am`:** `exec:java` is a direct goal, not a
phase, so Maven invokes it on *every* selected reactor project. With
`-pl karate-js-test262 -am`, the reactor also includes `karate-parent`,
which has no `mainClass` configured — the goal fails there and aborts
the run before the module is reached. Installing karate-js to the local
repo first, then running without `-am`, sidesteps the reactor entirely.

The `-f ../pom.xml` makes Maven find the parent reactor for `-pl`
resolution while the working directory stays inside the module so the
runner resolves `etc/expectations.yaml` and `test262/` correctly.

Typical inner loop: change something in `karate-js/`, re-install (step 1),
re-run (step 2) with the same `--only`, refresh the HTML report, drill
into a failing test via its `Reproducer` button.

---

## Why this exists

karate-js is a lightweight JavaScript engine. Hand-written JUnit tests in
`karate-js/src/test/java/` cover behaviors we care about but tell us nothing
about **spec compliance**. Running tc39/test262 gives us ground truth — and
the module's committed skip list
([`etc/expectations.yaml`](etc/expectations.yaml)) becomes the
declarative statement of "what karate-js deliberately does not support."

The **real bar** is not spec-lawyer compliance — it is *can karate-js run
real-world JavaScript written in the wild, especially by LLMs?* test262 is
the scorecard; pragmatic ES6 coverage of idiomatic code is the goal.

Design goals:

- **Observable state** — every test is PASS / FAIL / SKIP with a reason.
- **Tight iteration** — sequential runs, silent except failures, HTML
  drill-down with a one-liner reproducer per failing test.
- **Declarative non-goals** — features/flags/paths we skip are listed with
  one-line reasons, in YAML, in git.
- **No noise** — `target/test262/results.jsonl` is currently gitignored; the committed
  expectations.yaml tells the story of intent. Revisit committing results
  once the engine is stable enough that diffs are meaningful.

---

## Working principles

These are operating-mode guidelines for anyone picking up engine-compliance
work. They apply alongside the Roadmap below.

1. **Real-world JS is the target, test262 is the scorecard.** When you're
   triaging failures, weight "does this break common LLM-written code?"
   higher than "does this break an obscure spec corner?" A fix that
   unblocks 500 idiomatic tests is worth more than one that tightens a
   rarely-exercised edge case. The tier list already reflects this
   ordering (fundamentals → common built-ins → long tail).

2. **Fix friction before moving on.** If the harness makes something hard
   to see, or the engine makes something hard to debug, **stop and fix it
   first**. Concretely:
   - Bad error messages in `target/test262/results.jsonl` → improve `ErrorUtils` or the
     engine's error-framing, don't work around it.
   - Can't tell parse-phase from runtime-phase → improve the classifier
     or the engine's exception typing.
   - HTML report missing something you keep wanting → add it to
     `Test262Report`.
   - `--single -vv` doesn't show what you need → extend it; use the
     event listener hook.
   Working around tooling pain compounds over 50k tests; fixing it once
   pays back the same day.

3. **Errors must look like JavaScript, not Java.** karate-js is executed by
   LLMs as often as it's written for them — the `.message`, error-constructor
   identity, and (when we add them) stack frames they read back are part of
   their feedback loop. A leaking `IndexOutOfBoundsException` or `at
   io.karatelabs.js.Interpreter.eval(...)` frame is a correctness bug, not a
   cosmetic one: it derails the model's next step. Concretely:
   - Any raw Java exception escaping `Engine.eval(...)` (ArithmeticException,
     IndexOutOfBoundsException, NullPointerException, ClassCastException…) →
     catch at the boundary, re-throw as the right JS error type with a
     JS-native message.
   - Reserve the `js failed: / ========== / Code: / Error: …` framing for
     host-side logging; expose raw JS-style `.message` on `EngineException`
     directly.
   - Error constructor identity (`.constructor`, `instanceof`) is part of the
     error surface — see Known first-order gaps.
   - When stack traces arrive, they should enumerate JS frames (script
     file:line, JS function names), not Java frames — Java frames are
     implementation detail.

   This is a stronger form of #2: even if the test262 classifier strips
   noise, the *engine's user-visible error surface* is its own output
   contract. Treat every raw Java exception name or `io.karatelabs.js.*`
   frame an LLM could read as a bug.

4. **The engine event system is fair game for testability.** karate-js's
   `ContextListener` / `Event` / `BindEvent` surface (see
   [JS_ENGINE.md](../docs/JS_ENGINE.md)) exists partly for debugging and
   introspection. If exposing a new event or adding a field to an existing
   one makes test262 failures dramatically clearer, do it — this is not
   a load-bearing API guarantee.

5. **Performance is a feature.** The suite runs a fresh `new Engine()` per
   test (~50k tests); regressions of a few µs in engine startup or
   per-statement eval cost compound into minutes of wall time. After any
   non-trivial engine change, run
   [`EngineBenchmark`](../karate-js/src/test/java/io/karatelabs/parser/EngineBenchmark.java)
   **in profile mode** (`EngineBenchmark profile` — 30 s warm loop, JIT-stable)
   and compare against the reference results in
   [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).
   Default (no arg) is fast mode — median-of-10 cold runs, noisy, gut-check
   only. See the [check performance](#check-performance-after-an-engine-change)
   recipe below.

6. **Focused engine changes, but batched commits are fine.** A single
   commit covering several related fixes from one session (e.g. IIFE +
   destructuring overhaul landed together) is preferred over the ceremony
   of splitting hunks across files. What matters is that each commit
   message clearly enumerates the logical changes and quotes concrete
   pass-rate deltas per slice so that a future bisect can still attribute
   regressions. Only split commits when the work is genuinely independent
   (e.g. a purely cosmetic change alongside a behavioral fix) — not for
   its own sake.

---

## Directory layout

```
karate-js-test262/
├── TEST262.md                         # this file (the living document)
├── pom.xml                            # Maven module (deploy explicitly disabled)
├── etc/
│   ├── expectations.yaml              # declarative SKIP list (committed)
│   ├── fetch-test262.sh               # shallow clone of tc39/test262 at pinned SHA
│   └── run.sh                         # one-shot: install + run suite + generate HTML
├── src/main/java/…/test262/           # runner + report + helpers
├── src/test/java/…/test262/           # unit tests for the harness itself
├── src/main/resources/report/         # HTML/CSS/JS templates for the report
├── src/main/resources/logback.xml     # logger config (file appender → target/test262/)
├── test262/                           # [gitignored] the cloned suite
└── target/test262/                    # [gitignored] all per-run outputs
    ├── results.jsonl                  # per-test pass/fail/skip, sorted by path (end of run)
    ├── results.jsonl.partial          # live feed — appended per test, flushed; deleted on clean exit, kept on abort
    ├── run-meta.json                  # per-run context
    ├── progress.log                   # overwritten per run — banner + [progress] lines + final summary
    └── html/                          # generated HTML report
```

Everything a run produces lives under one directory (`target/test262/`), so
`mvn clean` wipes it in full and the CI workflow uploads it as a single
artifact.

---

## Running the suite

All commands assume `cwd = karate-js-test262/` (see Quick Start). Use
`-f ../pom.xml` so Maven finds the parent reactor.

**After any change under `karate-js/`, re-install it first** — the runner
uses the karate-js jar from your local Maven repo, not from the reactor
(see Quick Start's "Why install instead of `-am`").

The one-shot wrapper at [`etc/run.sh`](etc/run.sh) does install + run + HTML
in a single command and writes a timestamped log under `target/test262/` so
you (or an external caller) can `tail -f` while the suite is running:

```sh
etc/run.sh                                              # full suite + HTML report
etc/run.sh --only 'test/language/**'                    # slice
etc/run.sh --only 'test/language/**' --max-duration 300000   # 5-min cap
tail -f target/test262/progress.log                     # live [progress] lines + summary
tail -f target/test262/results.jsonl.partial            # live per-test JSONL feed
```

Or invoke the pieces by hand:

```sh
# After editing karate-js/: refresh the local repo
mvn -f ../pom.xml -pl karate-js -o install -DskipTests

# Full suite (sequential; minutes to tens of minutes). A progress line is
# emitted every 500 tests OR every 5s of wall time, whichever fires first.
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java

# Narrow to a tier (see Roadmap below)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/**"

# Debug one test end-to-end (prints metadata, source, classification)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single test/language/expressions/addition/S11.6.1_A1.js -vv"

# Resume after a crash — re-uses the existing target/test262/results.jsonl
#   (caveat: does NOT refresh records for tests that were since removed or
#    re-classified as SKIP. Delete target/test262/results.jsonl for a clean run.)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -Dexec.args="--resume"

# Run a "set" with a wall-clock safety cap — useful when driving the runner
# from scripts / a sub-shell that must not block indefinitely. Partial
# results are written and an `Aborted:` summary replaces the usual one
# once the cap trips.
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/** --max-duration 300000"   # 5 min hard cap
```

### All flags

| Flag | Default | Purpose |
|---|---|---|
| `--expectations <path>` | `etc/expectations.yaml` | skip list manifest |
| `--test262 <path>` | `test262` | suite clone dir |
| `--results <path>` | `target/test262/results.jsonl` | output JSONL |
| `--run-meta <path>` | `target/test262/run-meta.json` | output run metadata |
| `--timeout-ms <n>` | `10000` | per-test watchdog (infinite-loop guard) |
| `--max-duration <ms>` | `0` (unlimited) | overall wall-clock cap; writes partial results and prints `Aborted:` on hit |
| `--only <glob>` | — | restrict to matching paths |
| `--single <path>` | — | run one test, no file writes |
| `-v` / `-vv` | off | (with `--single`) `-v` prints parsed metadata; `-vv` adds full source |
| `--resume` | off | skip tests already in existing `target/test262/results.jsonl` |

Runs are **silent except failures + periodic progress**. A
`FAIL <path> — <type>: <msg>` line is printed to stdout as each failure
occurs; a `[progress] <N> processed …` line prints every 500 tests or every
5 seconds (whichever fires first) so long runs have an observable heartbeat.
A one-line `Summary:` (or `Aborted:`) ends the run.

The progress / banner / summary lines (NOT the per-FAIL ones) are also
mirrored to `target/test262/progress.log` via Logback
(config: `src/main/resources/logback.xml`) so `tail -f progress.log` is a
lightweight live view. Per-FAIL detail deliberately lives only in the JSONL
files — mirroring it into the log would duplicate data without a new signal.

Generate the HTML report with
`mvn exec:java -Dexec.mainClass=io.karatelabs.js.test262.Test262Report`;
`etc/run.sh` runs that step automatically. The POM uses
`<mainClass>${exec.mainClass}</mainClass>` with a default, which is what
makes the `-Dexec.mainClass` override actually take effect (a bare POM
literal would not — the command-line property would be silently ignored
and `Test262Runner` would re-run instead of the report generator).

### Hang handling

The runner uses a single-thread `ExecutorService` to enforce `--timeout-ms`
per test. The karate-js engine does not currently poll `Thread.interrupt()`,
so a `cancel(true)` on a runaway test cannot stop the underlying thread.
When a timeout fires, the runner **retires the executor** (shuts it down,
creates a fresh one) so subsequent tests don't queue behind the stuck
thread. Without this step, a single `while (true) {}` in a test file would
cause every test after it to report Timeout and take the full
`--timeout-ms` each, because its submission would wait on the stuck
thread to drain. Net cost of a genuine hang today: one abandoned daemon
thread, one Timeout row in `target/test262/results.jsonl`, and a few ms of
recreate overhead — the suite continues at full speed.

### Running a "set" safely (for scripts and agents)

When driving the runner from another process (a slash command, a CI
scaffold, an agent), use `--max-duration` as a safety net so a
catastrophic engine bug cannot wedge the caller. Typical shape:

```sh
# 5-min hard cap; partial results written if we hit it.
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/assignment/** --max-duration 300000" \
    -q 2>&1 | tail -20
```

The periodic `[progress]` line gives you a machine-parseable heartbeat:
the caller can stream stdout and tail the most recent progress line
without waiting for the whole run to finish.

---

## Results schema

Two JSONL files during a run:

- `target/test262/results.jsonl.partial` — appended per test as results
  arrive, flushed per write. **Run order, not sorted.** This is what
  `tail -f` or an external watcher sees in real time. Deleted on clean
  exit; preserved on abort (`--max-duration` hit, Ctrl-C, JVM kill) so
  you can see exactly how far the run got.
- `target/test262/results.jsonl` — the canonical output, **sorted
  alphabetically by path**, atomically written at end-of-run (tmp +
  rename). This is what tooling reads and what `--resume` skips-seen
  against.

Example line shape (same in both):

```jsonl
{"path":"test/language/expressions/addition/S11.6.1_A1.js","status":"PASS"}
{"path":"test/.../something.js","status":"FAIL","error_type":"TypeError","message":"foo is not a function"}
{"path":"test/.../bigint-test.js","status":"SKIP","reason":"BigInt not supported"}
```

Error types are classified into:
`SyntaxError | TypeError | ReferenceError | RangeError | Error | Timeout |
Harness | Unknown`

by inspecting message prefixes (the engine emits `"TypeError: ..."` style
messages at most failure sites; see
[JS_ENGINE.md#exception-handling](../docs/JS_ENGINE.md#exception-handling)).
The classifier itself is in `ErrorUtils`.

---

## The skip list

There is only one concept: **SKIP**. A test matching any rule in
`etc/expectations.yaml` is not run and appears as `{"status":"SKIP",...}`
in results. Everything else is attempted; failures are failures.

Match order: `paths → flags → features → includes`. First match wins. Every
entry requires a `reason`.

**Precedence example.** A test at `test/language/statements/class/foo.js`
with `flags: [module]` and `features: [Symbol]` is skipped with the
*module* reason (the `flags` match fires before `features` is consulted).
If you want `features: [Symbol]` to win, don't have a matching flag rule.

See [`etc/expectations.yaml`](etc/expectations.yaml) for the starter
set and rationale (Symbol, BigInt, generators, class syntax, Proxy, Reflect,
Promises, async/await, Temporal, TypedArray beyond Uint8Array, WeakRef,
ArrayBuffer, and the suite directories `test/intl402/`, `test/staging/`,
`test/annexB/`).

---

## How this interacts with karate-js

The runner relies on three small engine behaviors (see
[JS_ENGINE.md](../docs/JS_ENGINE.md#exception-handling) for the full model):

1. `io.karatelabs.parser.ParserException` propagates unchanged out of
   `Engine.eval(...)` — used to match `phase: parse` negative tests by type.
2. `io.karatelabs.js.EngineException` wraps everything else with the cause
   chain preserved — the runner classifies the error by message prefix.
3. For `throw new TypeError('x')` style tests, `Interpreter.evalProgram`
   prefixes the message with the JS error-object name so that prefix-based
   classification works uniformly.

If you find yourself wanting a more structured error surface (e.g. typed
`EngineException.getJsError()`), that is a valid engine improvement — the
current prefix-based classifier is a pragmatic starting point, not a
commitment.

---

## Where engine code lives

Most test262-driven fixes touch one of these layers. Pointers so you
don't have to `grep` from scratch each session:

| Concern | Source package | Key files |
|---|---|---|
| Lexer (tokenization) | `karate-js/src/main/java/io/karatelabs/parser/` | `JsLexer.java`, `BaseLexer.java`, `TokenType.java`, `Token.java` |
| Parser (AST build) | `karate-js/src/main/java/io/karatelabs/parser/` | `JsParser.java`, `BaseParser.java`, `NodeType.java`, `Node.java` |
| Parse errors | `karate-js/src/main/java/io/karatelabs/parser/` | `ParserException.java`, `SyntaxError.java` |
| Interpreter (AST eval) | `karate-js/src/main/java/io/karatelabs/js/` | `Interpreter.java`, `CoreContext.java`, `ContextRoot.java` |
| Types / built-ins | `karate-js/src/main/java/io/karatelabs/js/` | `JsObject.java`, `JsArray.java`, `JsString.java`, `JsError.java`, `JsFunction.java`, prototype classes (`JsArrayPrototype` etc.), `Terms.java` (operators/coercion) |
| Runtime exceptions | `karate-js/src/main/java/io/karatelabs/js/` | `EngineException.java` |

| Test concern | Test package | Key files |
|---|---|---|
| Lexer token-level | `karate-js/src/test/java/io/karatelabs/parser/` | `JsLexerTest.java` |
| Parser AST shape | `karate-js/src/test/java/io/karatelabs/parser/` | `JsParserTest.java` |
| Parse-error surface | `karate-js/src/test/java/io/karatelabs/js/` | `ParserExceptionTest.java` |
| Performance regression | `karate-js/src/test/java/io/karatelabs/parser/` | `EngineBenchmark.java`, `LexerBenchmark.java` |

Guidance:
- **Pure tokenization change** (new literal form, escape sequence, line
  terminator) → `JsLexer` + `JsLexerTest`.
- **Grammar change** (new syntactic form, operator slot widening, call-site
  wiring like the comma operator above) → `JsParser` + `JsParserTest`
  for AST shape, plus `EvalTest` for runtime semantics.
- **Semantics-only change** (prototype behavior, coercion, operator
  evaluation) → `Interpreter.java` or the relevant `Js*Prototype`;
  test in `EvalTest` or the matching `Js*Test` per the mapping below.
- `NodeType` and `TokenType` are small enums — consult them before
  inventing new node/token kinds; many "feels like I need a new node"
  fixes turn out to be wiring an existing one to a new call site.

---

## Landing regression tests in karate-js

When a test262 failure drives an engine fix, also add a small hand-written
JUnit test alongside the fix, in
[`karate-js/src/test/java/io/karatelabs/js/`](../karate-js/src/test/java/io/karatelabs/js/).
test262 is the breadth scorecard; the JUnit tests are the targeted
regression net that runs on every build.

**Rough mapping — use the existing `Js*Test` file whose name matches the
area. No formal reorganization, no new classes, no JUnit `@Tag`s.**

| test262 path | JUnit file |
|---|---|
| `test/built-ins/Array/**` | `JsArrayTest` |
| `test/built-ins/String/**` | `JsStringTest` |
| `test/built-ins/Object/**` | `JsObjectTest` |
| `test/built-ins/Math/**` | `JsMathTest` |
| `test/built-ins/Number/**` | `JsNumberTest` |
| `test/built-ins/JSON/**` | `JsJsonTest` |
| `test/built-ins/Date/**` | `JsDateTest` |
| `test/built-ins/RegExp/**` | `JsRegexTest` |
| `test/built-ins/Function/**` | `JsFunctionTest` |
| `test/built-ins/Boolean/**` | `JsBooleanTest` |
| `test/language/expressions/**`, `test/language/statements/**`, `test/language/types/**` | `EvalTest` (language-semantics catch-all) |
| Parser / lexer error regressions, `test/language/literals/**` syntax-level | `JsParserTest`, `JsLexerTest`, `ParserExceptionTest`, `TermsTest` |
| `EngineException` / error-propagation shape | `EngineExceptionTest` |

**`EngineTest` is *not* a test262 sink.** It covers the engine's
integration surface: `ContextListener` events, `BindEvent`, `Engine.put`
lifecycle, Java↔JS exception boundary, `$BUILTIN`/prototype immutability.
Only drop test262-driven tests here if they genuinely exercise that
embedding surface (e.g. observing a `BindEvent` for a spec-defined
binding). Language-semantics drops belong in `EvalTest` or a `Js*Test`.

**When to split a file.** Don't split pre-emptively. If a cluster inside
`EvalTest` grows to ~10+ tests on one feature (e.g. destructuring, TDZ,
template literals), spin it out (e.g. `JsDestructuringTest`). The test262
tier work will surface these naturally — let the split follow the
evidence, not a plan.

**Filtering by spec surface is test262's job.** If you want "all Symbol
tests" or "all Array.prototype.map tests," run the conformance suite with
the right `--only` glob. Don't duplicate that slicing in JUnit with tags.

---

## Roadmap — what to work on next

**This is the living section.** Tiers are ordered for the stated goal:
*handle real-world JS written in the wild, especially by LLMs.* That means
**core built-ins (Object/Array/String) move up** — they're in every
paragraph of idiomatic JS — interleaved with the grammar/scoping work that
they depend on. Tier numbers are priority order, not a strict dependency
DAG.

For each tier, run with the given `--only` glob, triage the HTML report,
fix in the engine, re-run. A tier is "done enough" when its non-skipped
tests are ≥95% PASS; then graduate to the next.

### Tier 0 — lexer, parser, source-text fundamentals

Nothing above this is meaningful if these fail. Most are ES5 bedrock.

| Area | `--only` glob |
|---|---|
| Whitespace & line terminators | `test/language/white-space/** test/language/line-terminators/**` |
| Comments | `test/language/comments/**` |
| Punctuators | `test/language/punctuators/**` |
| Reserved words / keywords | `test/language/reserved-words/** test/language/keywords/**` |
| Identifiers (names, Unicode escapes) | `test/language/identifiers/**` |
| Literals (number, string, boolean, null, regexp) | `test/language/literals/**` |

### Tier 1 — operators and primitive type semantics

The "engine math is wrong" bucket: `typeof`, `instanceof`, `in`, `delete`,
`new`, `void`, `comma`, plus ToNumber/ToString/ToPrimitive coercions.
Fixes here cascade into most other tiers.

| Area | `--only` glob |
|---|---|
| All expressions | `test/language/expressions/**` |
| Primitive conversions | `test/language/types/**` |

### Tier 2 — core built-ins that LLM-written JS leans on

**Promoted ahead of statements/functions** because idiomatic modern JS is
`arr.map(...).filter(...)`, `Object.keys(x).forEach(...)`, template
literals, destructuring. If these don't work, neither does anything LLMs
produce.

| Order | Built-in | `--only` glob |
|---|---|---|
| 1 | `Object` | `test/built-ins/Object/**` |
| 2 | `Array` | `test/built-ins/Array/**` |
| 3 | `String` | `test/built-ins/String/**` |
| 4 | Destructuring | `test/language/expressions/**/dstr-** test/language/statements/**/dstr-** test/language/destructuring/**` |
| 5 | Template literals | `test/language/expressions/template-literal/**` |
| 6 | Object literals (shorthand, computed) | `test/language/expressions/object/**` |
| 7 | Array literals (holes, spread) | `test/language/expressions/array/**` |

Destructuring is *not* blanket-skipped in `expectations.yaml` — we want
real engine breakage surfaced here, since LLMs write `const {a, b} = obj`
constantly.

### Tier 3 — statements and control flow

`if`, `for`, `while`, `do-while`, `switch`, `break`, `continue`, `return`,
`throw`, `try/catch/finally`, `var`, `let`, `const`, labeled statements.

| Area | `--only` glob |
|---|---|
| All statements | `test/language/statements/**` |

`with` is not supported by karate-js — add to `expectations.yaml` under
paths if needed (`test/language/statements/with/**`).

### Tier 4 — functions, scoping, `this` binding

| Area | `--only` glob |
|---|---|
| Function bodies & hoisting | `test/language/function-code/**` |
| arguments object | `test/language/arguments-object/**` |
| Block scope (let/const TDZ) | `test/language/block-scope/**` |
| Function expressions | `test/language/expressions/function/**` |
| Arrow functions | `test/language/expressions/arrow-function/**` |
| Call / new semantics | `test/language/expressions/call/** test/language/expressions/new/**` |
| Property accessors | `test/language/expressions/property-accessors/**` |

### Tier 5 — remaining built-ins

Less-frequently-used but still needed for full ES6 coverage.

| Order | Built-in | `--only` glob |
|---|---|---|
| 1 | `Number` | `test/built-ins/Number/**` |
| 2 | `Math` | `test/built-ins/Math/**` |
| 3 | `JSON` | `test/built-ins/JSON/**` |
| 4 | `Error` (and subtypes) | `test/built-ins/Error/** test/built-ins/TypeError/** test/built-ins/RangeError/** test/built-ins/ReferenceError/**` |
| 5 | `Function` | `test/built-ins/Function/**` |
| 6 | `RegExp` | `test/built-ins/RegExp/**` |
| 7 | `Date` | `test/built-ins/Date/**` |
| 8 | `Boolean` | `test/built-ins/Boolean/**` |

### Tier 6 — long tail

`directive-prologue/`, `eval-code/`, `global-code/`, iterators (mostly
already skipped via `Symbol.iterator`), regex edge features on the skip
list. Revisit when earlier tiers are green.

---

## Known first-order gaps

High-leverage issues that each break many tests at once.

### Open

- **Directive prologue (`"use strict"`) flip.** Parser tolerates the
  string without activating strict-mode assertions. Skip triage is done
  (`flags: [onlyStrict]` entry in `expectations.yaml` — keeps
  unexpected-passes from hiding real signal). Real strict-mode
  implementation (with/duplicate-params/eval-assign/octal-literal
  negative checks) is a separate, larger project; revisit only if a
  meaningful test cluster outside `onlyStrict` depends on it.
- **Harness-helper dependencies.** `propertyHelper.js`,
  `compareArray.js`, `testTypedArray.js` need descriptor introspection
  (`Object.getOwnPropertyDescriptor`) and full iterator protocol —
  karate-js exposes neither. Gate **thousands** of `test/built-ins/**`
  tests currently skipped via `expectations.yaml`. The next big lever
  once Tier 2 built-ins are solid.
- **`EngineException` framing noise** (per Working Principle #3). Wraps
  runtime errors in a multi-line `js failed: / ========== / Code: /
  Error: ...` frame. The runner strips it in `ErrorUtils.unwrapFraming`,
  but JS-side fixtures that inspect `.message` via `assert.throws` see
  the framed text — classifier workaround doesn't fix the real problem.
  Reserve the frame for host-side logging; expose the raw JS message on
  `EngineException` directly.

### Addressed — design decisions preserved

- **Engine-emitted errors route through the registered error
  constructors.** Engine sites (`PropertyAccess`, `Interpreter`,
  `CoreContext` TDZ/const-reassign/redeclare, `JsJson`, `JsJava`,
  `JavaUtils`) emit `"<Name>: ..."` prefixes.
  `Interpreter.evalTryStmt` parses the prefix into a structured
  `JsError` with linked `.constructor`; `Interpreter.evalStatement`
  stamps `EngineException.getJsErrorName()`. Result: `e instanceof
  TypeError`, `e.name`, `e.constructor.name` all work for
  engine-originated errors. Low-traffic internal-invariant sites
  (`JsArrayPrototype`/`JsRegex`/`JsStringPrototype`, "finally block
  threw error") still throw plain `RuntimeException` — convert the
  same way as needed.
- **`eval` is a global** registered in `ContextRoot.initGlobal` with
  indirect-eval semantics (parses/evaluates in engine root scope;
  non-string args pass through). Direct-eval scope capture is out of
  scope.
- **`typeof` on all callable surfaces.** `Terms.typeOf` returns
  `"function"` for `JsInvokable`, `JsFunction`, built-in constructor
  singletons (via `JsObject.isJsFunction()` — `Boolean`/`RegExp`/error
  globals), and `JsCallable` method refs (`[1].map`, `'x'.charAt`).
  The `!(value instanceof ObjectLike)` guard keeps `JsObject`/`JsArray`
  reporting `"object"`.
- **Error-position framing.** `Node.toStringError` leads with the user
  message and appends `    at <path>:<line>:<col>` (JS-stack-frame-style)
  instead of the engine-internal `<line>:<col> <NodeType>` prefix.
- **`Test262Error` / user-defined error classes** classified via
  `constructor.name` fallback in `Interpreter.evalProgram` when the
  thrown `JsObject` has no `.name` on its prototype. Related: fixed a
  function-name-inference bug in `CoreContext.declare` where a named
  function passed as a parameter was permanently renaming globally —
  inference now fires only when the function's name is empty.
- **`ErrorUtils.classify` scans embedded `<Name>:`** as a fallback for
  wrapper messages where the type isn't a prefix (e.g. `"expression:
  ... - TypeError: ..."`). Wrappers now preserve the cause chain so the
  structured `JsErrorException` name propagates first; embedded-name
  scan is the safety net.
- **JVM exception → JS error mapping** at `Interpreter.evalStatement`
  catch via `classifyJavaException`: `IndexOutOfBoundsException` /
  `ArithmeticException` → `RangeError`; `NullPointerException` /
  `ClassCastException` / `NumberFormatException` → `TypeError`. Name is
  stamped on `EngineException.jsErrorName` and prefixed to the message.
- **`JsError.constructor` populated** at the JS try/catch wrapping site
  (`Interpreter.evalTryStmt`) by resolving the registered global for the
  error's `.name`, so `assert.throws(Ctor, fn)` reading
  `thrown.constructor.name` works.

### Recommended next-session ordering

**Recently landed** (decisions preserved; see git log for per-commit
detail):

- **Destructuring — default-undefined + iterable-check.**
  `destructurePattern` now reads object-source properties via
  `ObjectLike.getMember` instead of the Java `Map.get`. Reason: `JsObject`
  implements `Map<String, Object>` and its `get(Object)` auto-unwraps
  `Terms.UNDEFINED → null` via `Engine.toJava` — so
  `{x = 1} = {x: undefined}` saw `x=null` (present-but-undefined looked
  absent, default didn't fire). Null / UNDEFINED / absent get
  disambiguated by falling back to `Map.containsKey` on the own
  properties map (so `{x = 5} = {x: null}` still preserves `x=null`
  without the default firing). Also added spec-correct iterable check
  for array destructuring (13.3.3.5): non-List / non-String sources
  throw `TypeError`. Slice deltas:
  `assignment/dstr/**` 147 → **157** (+10),
  `variable/dstr/**` 49 → **52**, `let/dstr/**` 47 → **50**,
  `const/dstr/**` 47 → **50**, `expressions/function/**` 56 → **66**
  (+10 — bonus cascade from the same fix via default params),
  `expressions/template-literal/**` 42 → **51**.
- **Reserved words as object-literal keys.** Parser's `T_OBJECT_ELEM` /
  `T_ACCESSOR_KEY_START` sets are now built at class-init from every
  TokenType with `keyword == true`, so `{break: x}`, `{default: 1}`,
  `{class: foo}` parse as object literals and destructuring LHS
  patterns. (Escaped-identifier forms like `break` still fail —
  they need lexer support for `\uXXXX` in identifier scanning, a
  separate lexer project.)
- **Tagged templates.** New `FN_TAGGED_TEMPLATE_EXPR` node with shape
  `[<callable>, LIT_TEMPLATE]`. Parser hook in `expr_rhs` after
  `FN_CALL_EXPR` fires on a trailing BACKTICK (same postfix-chain
  precedence as `()` / `.x`). `evalFnTaggedTemplate` walks the
  LIT_TEMPLATE children to recover paired cooked/raw segments and
  substitution expressions — for N expressions there are always N+1
  string slots (possibly empty). `strings` is a `JsArray` with the
  raw array attached via `putMember("raw", raw)`. `new tag`x`` routes
  through a new `evalNewExpr` helper that evaluates the tagged
  template first (MemberExpression semantics) then constructs the
  result with no args. `test/language/expressions/tagged-template/**`
  0 → **11 PASS** of 22 non-skip. Known limits: no template-site
  caching (same lexical site returns different arrays — breaks 6
  `cache-*` tests), no `Object.freeze` on the strings array (descriptor
  infra), no lexer relaxation for malformed escapes in tag context
  (cooked should be `undefined`, raw preserves source — lexer still
  rejects at parse time).
- **Template-literal overhaul.** `${obj}` dispatches through the
  prototype chain (spec `Object.prototype.toString` = `"[object
  Object]"`; `Array.prototype.toString` calls `this.join(",")`). User
  `toString` throws propagate with JS-side constructor identity intact.
  `JsLexer.scanTemplateContent` rejects malformed escapes (legacy octal,
  `\xGG`, malformed `\u`, overflow `\u{…}`) as parse-phase `SyntaxError`
  — safe until tagged templates land. Nested `{}` inside `${...}` lex
  correctly (depth-tracked). 18 → **42 PASS** in
  `expressions/template-literal/**`.
- **IIFE in expression position.** `PropertyAccess.getCallable` now
  evaluates `FN_EXPR` / `FN_ARROW_EXPR` in the callable slot — parser
  was already emitting `FN_CALL_EXPR -> [FN_EXPR, FN_CALL_ARGS]`; the
  dispatch switch was missing those cases. `expressions/call/**`
  16 → **30 PASS**. Paren-wrapped IIFE and arrow IIFE were already
  working via `PAREN_EXPR`.
- **Destructuring overhaul (assignment + declaration).** Unified
  `Interpreter.destructurePattern` / `bindTarget` / `bindLeaf` helpers
  recurse on nested patterns, handle rename+default (`{a: b = 5}`),
  fire defaults only on literal `undefined` (not null). `evalAssignExpr`
  and the `var/let/const` paths share the helper, so property-reference
  targets (`[o.x] = [99]`) and full nesting work. Per 13.3.3.5,
  destructuring null/undefined throws `TypeError`. `evalLitArray` /
  `evalLitObject` are pure literal-construction (their `bindScope`/
  `bindSource` params are gone). Slice deltas:
  `assignment/dstr/**` 82 → **147**, `variable/dstr/**` 23 → **49**,
  `let/dstr/**` 21 → **47**, `const/dstr/**` 21 → **47**,
  `expressions/function/**` 37 → **56** (destructured params).
- **`Object` built-ins: 298 → 552 PASS** after the ToString overhaul.
  Remaining ~1850 fails are property-descriptor semantics — see item 4.
- **Spec `ToString` unified** via `Terms.toStringCoerce(Object,
  CoreContext)`; `JsObjectPrototype` / `JsArrayPrototype` /
  `JsBooleanPrototype` / `JsNumberPrototype` have spec-correct
  `toString`. Old JSON-formatter gone — use `StringUtils.formatJson`
  directly for JSON display.
- **Object-literal parser gaps closed:** shorthand methods, computed
  keys, accessors (getters/setters).
- **Error classification & framing** — see "Known first-order gaps →
  Addressed".

**What remains — work items, highest LLM-value first.** Ordering is
by "how often does idiomatic LLM-written JS hit this?" not by
test-count. Each entry names (a) what LLMs actually do that depends on
it, (b) the shape of the work, and (c) the skip-list entry to retire.

---

**1. Optional chaining (`?.`) + nullish coalescing (`??`).**

Both currently skipped via `expectations.yaml`
(`feature: optional-chaining`, `feature: nullish-coalescing`). LLMs
write `config?.server?.port ?? 8080` reflexively — this is defensive
property access plus default fallback, the single most common modern
JS pattern after destructuring. Neither feature exists in karate-js
today.

Shape: new tokens (`?.`, `??`), parser slots in the postfix chain
(optional chaining) and logical-operator tier (nullish coalescing),
interpreter short-circuit evaluation. Small to mid session — both
features share the same "short-circuit on nullish" machinery, land
them together. Remove both skip entries when done.

**Why first:** cheapest *and* highest-frequency. Together they touch
thousands of real LLM-generated snippets; ignoring them means any
paste from a modern codebase breaks at the first `?.`.

---

**2. Iteration-layer unification + un-skip `Symbol.iterator`.**

Right now karate-js has **ad-hoc iteration** scattered across sites
that each special-case `List` / `String` / `JsArray`:

- `destructurePattern` in `Interpreter` (this session added a
  bespoke String → List<Object> expansion).
- `evalForStmt` for `for-of` (walks List elements).
- `...spread` in call args (`evalFnCall` iterates List).
- `...spread` in array literals.
- `Array.from`, `Array.of`, and similar built-ins.

No single "give me an iterator over X" seam exists. Adding one — a
`getIterator(Object)` helper returning a `JsIterator` interface with
`next(): IterResult`, backed by:
- JsArray / List → index walker
- String → code-unit walker
- JsObject with `@@iterator` key → user-defined iterator via its
  `next()` method
- a minimal `Symbol.iterator` registered as a *well-known-symbol*
  string key (`"@@iterator"` until real `Symbol` lands)

— collapses five ad-hoc call sites into one, and unblocks
`compareArray.js` (gates thousands of `test/built-ins/**` tests
currently SKIP'd because `Symbol.iterator` is feature-skipped).

Shape: focused engine-plumbing session. (1) Introduce `JsIterator`
interface + `IterUtils.getIterator(Object)`. (2) Convert the five
call sites above. (3) Remove `feature: Symbol.iterator` from
`expectations.yaml`. (4) Expose `@@iterator` read on `JsArray` /
`JsString` as a well-known-symbol alias. Real `Symbol()` construction
can stay skipped.

**Why second:** architectural, but this session's work exposed how
fragile the ad-hoc pattern is (the String-case in `destructurePattern`
is smell). One refactor in exchange for: simpler engine, consistent
semantics across all iteration sites, a huge cascade of newly-runnable
Array tests, and the door open to for-of over user-defined iterables.
Per Working Principle #2 — the friction is real; fix it now.

---

**3. Class syntax (ES6).**

Currently skipped (`feature: class` plus five `class-*` variants).
LLMs default to `class Foo extends Bar {}` when they want OO.
Karate-js already has the prototype and `new` machinery — this is
primarily a parser + desugarer.

Shape: (1) parser recognizes `class`/`extends`/method-definitions;
(2) desugar to an equivalent constructor function + prototype
assignments at parse time (or during interpretation); (3) handle
`super` calls inside constructors and methods; (4) `static` members
become direct function properties. Mid-to-large session. Remove the
six `class-*` skips when done.

**Why third:** qualitatively important but larger scope than #1/#2.
Also, much working karate-js code uses prototype syntax directly, so
the impact on existing LLM-written code is high but not
feature-blocking the way #1 is.

---

**4. Destructuring-assignment residuals (75).**

After the reserved-words widening + default-undefined + iterable-check
fixes, the assignment slice has 75 fails, all low-leverage:

- **47 SyntaxErrors, all `-escaped` forms** — need lexer support for
  `\uXXXX` in identifier scans. Isolated lexer project; LLMs don't
  write escape-form keywords.
- **14 Test262Errors** — TDZ-on-assignment-to-later-`let`,
  init-evaluation order, function-name inference in defaults. Small
  independent fixes; low frequency in real code.
- **14 Unknowns — negative parse tests** (`[...rest, elem]`,
  `{...rest, key: v}`) that need pattern-vs-literal two-mode parsing.
  Significant refactor; real-world code doesn't write these.

**Why fourth:** each sub-bucket is architectural or
low-LLM-frequency. Skip unless one of them blocks something.

---

**5. Deferred — descriptor infra, low LLM-leverage.**

- **Array cliff (~1896 FAILs)**, **Object-attribute polish
  (~1855 FAILs)**, **harness helpers (`propertyHelper.js`,
  `testTypedArray.js`)** — all blocked on the same descriptor layer:
  per-property `writable`/`enumerable`/`configurable` attribute
  tracking, accessor getter/setter enforcement, `Object.freeze` /
  `seal` / `preventExtensions`.

  **Low LLM-value** per our analysis: LLMs don't write
  `defineProperty`, don't rely on `writable: false` enforcement, and
  don't produce code that breaks if attributes are missing. The
  thousands of failing tests assert spec invariants, not things
  LLM-generated code hits. Treat the descriptor layer as one project
  (not three) — but only worth starting when the skip list's SKIP
  fraction is hiding too much other signal. Currently item #2
  (iteration unification) is a cheaper way to retire a big cluster of
  SKIPs.

---

**Ancient residuals to clean up opportunistically.** Remaining
Array-slice Unknowns: 6 × `"null"` NPE path, 2 × `IllegalName` JDK
lambda leak, 1 × `Java heap space` OOM. These are individual bugs,
not a bucket — pick off while nearby.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `expectations file not found: etc/expectations.yaml` | You ran from the wrong directory. `cd karate-js-test262` first (see Quick Start). |
| `test262 directory not found: test262` | You haven't run `etc/fetch-test262.sh` yet (or you're in the wrong dir). |
| `Failed to execute goal ... exec-maven-plugin ... on project karate-parent: The parameters 'mainClass' ... are missing or invalid` | You used `-am` with `exec:java`. Don't — install `karate-js` separately (see Quick Start) and run without `-am`. |
| Engine change seems to have no effect on test262 output | You forgot `mvn ... -pl karate-js -o install -DskipTests`. The runner uses the karate-js jar from your local Maven repo, not the reactor classpath. |
| HTML dashboard shows empty header | `target/test262/run-meta.json` missing — run `Test262Report` *after* `Test262Runner` in the same directory. |
| `ReferenceError: <name> is not defined` on common classes like `ReferenceError`/`RangeError` | Known first-order gap — those constructors are not registered globals yet. |
| Suite seems to hang on one test | Infinite loop; watchdog kicks in at `--timeout-ms`. The inner executor is retired and replaced on every timeout so subsequent tests start on a fresh thread; a genuine hang leaks one daemon thread and keeps going. If progress truly stops, bisect with `--only`, or add `--max-duration` as a safety net. |
| Driving the runner from a script / agent that must not block | Pass `--max-duration <ms>`. On hit, partial results are written and `Aborted:` replaces the usual `Summary:` line. The periodic `[progress]` output on stdout lets the caller tail heartbeat without waiting for the full run. |
| Tests that used to pass now fail after an engine change | Run `EngineBenchmark` too — perf regression sometimes manifests as timeouts before correctness. |
| `--resume` gives stale results | Known limitation — it doesn't refresh records for tests that were removed or re-SKIP'd. Delete `target/test262/results.jsonl` for a clean baseline. |

---

## Deferred TODOs

Items left for later; un-scheduled but tracked.

- **Replace hand-rolled YAML parser with SnakeYAML.** `Expectations.java`
  and `Test262Metadata.java` are hand-rolled to avoid the dep. They break
  on `#` in quoted reasons, block-scalar (`|`, `>`) `description:` fields,
  and block-form list values. Add SnakeYAML when we next touch either
  file; one dep is fine.
- **Make `--resume` refresh stale records.** Currently echoes back records
  for tests that no longer exist or are now SKIP'd. Either gate on
  test-still-exists + not-in-skip-list, or rename to `--resume-crash-only`.
- **Cache parsed harness ASTs, not source text.** `HarnessLoader` currently
  re-parses `assert.js`, `sta.js`, and each test's `includes:` on every
  test; ~50k re-parses per full-suite run is a measurable chunk of wall
  time. Cache `Node` trees and re-eval from AST.
- **`phase: resolution` (module-resolution) negative tests.** Currently
  conflated with `runtime`. Modules are globally skipped via
  `flags: [module]`, so this is latent — document if we ever un-skip
  modules.
- **Structured `$262` surface.** `AbstractModuleSource`, `IsHTMLDDA`,
  `agent.broadcast/getReport/sleep/monotonicNow` are absent — all are
  feature-gated in tests, so they're unreachable today. Add stubs when a
  feature is moved off the skip list.
- **`readHeadSha` path fragility.** `Test262Runner.readHeadSha` walks up
  `cli.test262.getParent().getParent()` to find the karate repo. Prefer a
  `git rev-parse HEAD` subprocess, or a `--karate-sha` flag.
- **Surface per-test console capture in `ResultRecord`.** `evaluate(...)`
  already wires `Engine.setOnConsoleLog(...)` to a per-test sink
  (discarded today). Plumb it into `ResultRecord` for FAIL rows so
  `--single -vv` and the HTML drill-down can show what the test
  printed. Adds debug value; the capture itself is already what
  would prevent `print` output from tests polluting our stdout.
- **Parallel execution.** Prior attempts showed no speedup on moderate
  slices (thread context-switch overhead beat the 1ms per-test eval cost
  on the hot path) and the engine does not poll `Thread.interrupt()`,
  making safe cancellation hard. `HarnessLoader.cache` is already
  `ConcurrentHashMap` so the shared-state work is done; revisit when
  either (a) per-test cost grows enough that 8× parallelism clearly
  wins, or (b) the engine learns to cooperatively abort.
- **Commit `target/test262/results.jsonl` once stable.** Currently gitignored (too noisy
  in git while engine iterates). Re-evaluate when Tier 0–2 are ≥95%
  green — at that point, diff-based regression detection becomes the
  cheapest signal.
- **Thin `EngineException` once `JsError` goes public.** Today
  `EngineException` (host boundary) carries a `jsErrorName` string that
  duplicates what's in the cause-chain's `JsErrorException` payload.
  `JsError` is package-private, so we can't just expose the payload. Once
  a host use case forces `JsError` public, drop the name field and have
  `EngineException.getJsErrorName()` / `getJsErrorMessage()` delegate to
  the payload. Keep `EngineException` for the host-side framing
  ("js failed: / ========== / ..."), and keep `ParserException` separate
  — it lives in `io.karatelabs.parser` (no runtime deps) and the test262
  runner uses `instanceof ParserException` to distinguish
  `phase: parse` from `phase: runtime` SyntaxErrors.

---

## Recipes

### Add a skip rule

Edit `etc/expectations.yaml`, add under the right section, always with a
`reason`:

```yaml
skip:
  features:
    - feature: Temporal
      reason: Temporal proposal not implemented
```

Match order is `paths → flags → features → includes`; first match wins.

### Remove a skip rule (feature is now supported)

Delete the entry. Re-run the relevant tier's `--only` glob. Tests that were
skipped will now run; if they pass, you're done. If they fail, use
`--single <path> -vv` to debug and fix before removing the skip.

### Debug one failing test

Find its `--only` link in the HTML report's FAIL list, then:

```sh
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -vv"
```

The `-vv` output includes the parsed YAML metadata, the resolved harness
helpers, the classification, and the full test source. The HTML
drill-down page has the same reproducer command pre-filled.

### Promote from one tier to the next

When a tier's `--only` run is ≥95% PASS of its non-skipped tests, commit
whatever engine fixes you made, update the Roadmap's "Known first-order
gaps" if anything new emerged, and move on to the next tier. No ceremony.

### Check performance after an engine change

The conformance suite allocates a fresh `Engine` per test (~50k tests in
the default pinned SHA); small regressions compound into minutes of wall
time. After any non-trivial engine change, **prefer profile mode** — the
30 s warm loop is JIT-stable and directly comparable to the reference
table. Fast mode's median-of-10 is dominated by cold-start noise and
should only be used for a quick gut-check:

```sh
mvn -pl karate-js -q test-compile

# Profile mode (30 s warm loop; JIT-stable, ~16k iterations averaged)
# This is the mode the reference table in JS_ENGINE.md was recorded in.
java -cp "karate-js/target/classes:karate-js/target/test-classes:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1):$(find ~/.m2/repository -name 'json-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'accessors-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'asm-9*.jar' | grep -v asm-tree | grep -v asm-commons | head -1)" \
    io.karatelabs.parser.EngineBenchmark profile

# Fast mode (median of 10 cold runs) — noisy, do not compare against the reference table
java -cp "…same classpath…" io.karatelabs.parser.EngineBenchmark
```

Compare the profile-mode output against the reference numbers in
[JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).
If the averages moved materially (>±10%), understand why before merging.
If the change is unavoidable (correctness > speed), update the reference
table in `JS_ENGINE.md` in the same commit.

### Fix harness friction

If you hit friction while iterating — unreadable error messages in results,
missing info in `--single -vv`, a gap in HTML drill-down — **fix that first**,
don't work around it. The engine's `ContextListener` event surface can be
extended for testability; bad error framing in `EngineException` can be
improved. See Working Principle #2 above.

### Bump the pinned test262 SHA

Edit the `TEST262_SHA=...` line at the top of `etc/fetch-test262.sh`, delete
the local `test262/` directory, re-run the script. All subsequent runs use
the new commit. Coordinate bumps with whoever else is iterating — the test
suite itself evolves and can add/remove tests.

---

## CI

A `workflow_dispatch`-only workflow at
[`.github/workflows/test262.yml`](../.github/workflows/test262.yml) runs
`etc/fetch-test262.sh` + the runner + the report, and uploads the whole
`target/test262/` directory as a single artifact (results.jsonl +
run-meta.json + session log + generated HTML tree). It is never
triggered automatically — you kick it off from the Actions tab when you
want a fresh run. The two workflow inputs (`only` and `timeout_ms`)
default to full-suite / 10 s per test.

The module's `pom.xml` sets `maven.deploy.skip=true` / `gpg.skip=true` /
`skipPublishing=true` so the release workflow
([`.github/workflows/maven-release.yml`](../.github/workflows/maven-release.yml))
does not publish this module to Maven Central.

---

## References

- [tc39/test262](https://github.com/tc39/test262) — the suite
- [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
  — authoritative runner spec
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — karate-js engine architecture,
  type system, exception model, and benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

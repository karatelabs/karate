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

Operating-mode guidelines for anyone picking up engine-compliance work.
They apply alongside the Roadmap below.

- **Real-world JS is the target, test262 is the scorecard, the spec is
  ground truth.** When triaging, weight "does this break common LLM-written
  code?" higher than "does this break an obscure spec corner?" A fix that
  unblocks 500 idiomatic tests is worth more than one that tightens a
  rarely-exercised edge case. The tier list reflects this ordering
  (fundamentals → common built-ins → long tail).

  But the scorecard exists for a reason: **the engine should give
  confidence that it handles general JS syntax, expressions, math,
  functions, scoping, and control flow correctly.** Two corollaries:
  - **`test/language/**` is the core-engine surface;
    `test/built-ins/**` is the standard library.** Failures in
    `test/language/expressions/compound-assignment` mean the parser
    can't read code an LLM might write tomorrow; failures in
    `test/built-ins/AggregateError` mean we lack a constructor that
    almost no LLM uses. Same FAIL count, very different signal.
  - **Harness-include skips are leverage multipliers, not corners.**
    `propertyHelper.js` and `compareArray.js` together gate ~2200
    `test/built-ins/**` tests today. Those tests overwhelmingly
    exercise *core* JS (iteration, comparison, object identity)
    through a feature we don't expose (accessor descriptors). One
    infra fix moves the engine's *known-good surface* by thousands
    of tests — they aren't credit for new behavior, they're proof
    the existing behavior was already correct. Prioritize harness
    unblocks accordingly.

  **Existing JUnit tests can be wrong.** When a hand-written test in
  `karate-js/src/test/` disagrees with the ECMAScript spec, the spec wins —
  read [tc39/ecma262](https://tc39.es/ecma262/) §13.x before assuming the
  JUnit assertion is correct, and fix the test along with the engine. The
  session that just touched a feature is the cheapest moment to clean up its
  hand-written tests; don't preserve a wrong assertion just because it has a
  `@Test` annotation. (If you're unsure whether the existing assertion is
  wrong, write a test262 reproducer first — that's the impartial referee.)

- **Fix friction before moving on.** If the harness makes something hard to
  see, or the engine makes something hard to debug, **stop and fix it
  first**. Concretely:
  - Bad error messages in `target/test262/results.jsonl` → improve
    `ErrorUtils` or the engine's error-framing, don't work around it.
  - Can't tell parse-phase from runtime-phase → improve the classifier or
    the engine's exception typing.
  - HTML report missing something you keep wanting → add it to
    `Test262Report`.
  - `--single -vv` doesn't show what you need → extend it; use the event
    listener hook.

  Working around tooling pain compounds over 50k tests; fixing it once pays
  back the same day.

- **Errors must look like JavaScript, not Java.** karate-js is executed by
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
    error surface — see Design invariants.
  - When stack traces arrive, they should enumerate JS frames (script
    file:line, JS function names), not Java frames — Java frames are
    implementation detail.

  This is a stronger form of the *fix friction* principle: even if the
  test262 classifier strips noise, the *engine's user-visible error surface*
  is its own output contract. Treat every raw Java exception name or
  `io.karatelabs.js.*` frame an LLM could read as a bug.

- **The engine event system is fair game for testability.** karate-js's
  `ContextListener` / `Event` / `BindEvent` surface (see
  [JS_ENGINE.md](../docs/JS_ENGINE.md)) exists partly for debugging and
  introspection. If exposing a new event or adding a field to an existing
  one makes test262 failures dramatically clearer, do it — this is not a
  load-bearing API guarantee.

- **Performance is a feature — protect the hot path.** The suite runs a
  fresh `new Engine()` per test (~50k tests); regressions of a few µs in
  engine startup or per-statement eval cost compound into minutes of wall
  time. After any non-trivial engine change, run
  [`EngineBenchmark`](../karate-js/src/test/java/io/karatelabs/parser/EngineBenchmark.java)
  **in profile mode** (`EngineBenchmark profile` — 30 s warm loop,
  JIT-stable) and compare against the reference results in
  [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).
  Default (no arg) is fast mode — median-of-10 cold runs, noisy, gut-check
  only. See the [check performance](#check-performance-after-an-engine-change)
  recipe below.

  When wiring a new feature, **structure dispatch so the hot path stays
  free of new branches**. A new flag/syntax can't add a runtime check to
  every existing property read — the cost has to be paid by the code that
  exercises the new feature, not by the rest of the engine. Concrete
  techniques:
  - **Sentinel propagation > exception throwing** for non-exceptional
    control flow. The `?.` short-circuit uses
    `PropertyAccess.SHORT_CIRCUITED` (a singleton object identity) rather
    than a thrown signal — every chain step pays a single reference
    comparison; only the chain root branches. Java exceptions are slow
    enough that doing this on a hot path costs measurably.
  - **Pay edge-case cost on the edge case.** Validation walks (early
    errors), AST repair, optional-feature wiring belong post-parse or in
    error paths — not interleaved into the hot eval loop.
  - **Static analysis once, propagate the boolean.** "Does this subtree
    have an `?.`?" is computed at parse time on a shape that exists,
    never recomputed in the inner loop.
  - **Default to the no-feature path.** A new `if (cur.type == X) ...`
    in `Interpreter.eval` should fail fast (cheap type check), not
    allocate or recurse.
  - **Tight happy path, rare branches off to the side — both in
    lexer/parser AND interpreter.** Most source characters are plain
    ASCII; most numbers are plain decimals; most property reads hit
    own-properties on plain objects; most arithmetic operands are
    plain `Number`. Exotic shapes (numeric separators, BigInt `n`
    suffix, Unicode escapes, BigInt arithmetic dispatch, `?.`
    short-circuit, getter/setter accessors) should be entered *after*
    a fast-path test fails, not woven into the inner loop.
    - **Lexer:** scan consecutive plain digits in one tight
      `while (isDigit(peek())) advance();` loop, then *outside*
      that loop check for `_` / `n` / `.` / `e` and only re-enter a
      slower branch if one matches.
    - **Parser:** keep the descent on common productions free of
      lookahead for rare ones; resolve rare-form ambiguities
      post-parse where possible.
    - **Interpreter:** in `Terms` arithmetic and property
      resolution, type-check for the rare case (`BigInteger`
      operand, accessor descriptor) only after the common case
      misses. The cost of supporting a rare form is paid by code
      that uses it, not by every numeric op or property read in
      every test.

- **Focused engine changes, but batched commits are fine.** A single commit
  covering several related fixes from one session (e.g. IIFE + destructuring
  overhaul landed together) is preferred over the ceremony of splitting
  hunks across files. What matters is that each commit message clearly
  enumerates the logical changes so a future bisect can attribute
  regressions. Only split commits when the work is genuinely independent
  (e.g. a purely cosmetic change alongside a behavioral fix) — not for its
  own sake.

- **Refactor toward elegance — fix inline or write it down, never carry the
  smell forward implicitly.** When you spot near-duplicate dispatch, an
  awkward AST shape, or a workaround that's clearly the wrong layer to fix,
  decide between "fix it now" and "write it down." Two adjacent sessions
  both rediscovering the same duplication is a tax on every future
  contributor.
  - **Fix inline** when the unification is small and keeps the diff
    focused. The session that just touched the area is the cheapest moment
    for a small DRY pass — variables are loaded, spec is fresh.
  - **File a Deferred TODO** when the cleanup is bigger than the current
    task warrants. The TODO must include **concrete pointers** (file,
    method, what's duplicated, what the unification looks like) so a future
    session can land it cold without re-deriving the diagnosis. Vague "this
    could be cleaner" notes are worthless.
  - **Recommend cleanup in the commit message** when you noticed something
    during the work but didn't act. Future bisects should surface the
    observation.

  A workaround is a clue that the layer below is wrong. When evaluating
  "where to fix this," ask which layer's contract is being broken — a
  parser-shape problem should not be patched in the interpreter.

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
- **Grammar change** (new syntactic form, operator slot widening, new
  call-site wiring) → `JsParser` + `JsParserTest` for AST shape, plus
  `EvalTest` for runtime semantics.
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

## Roadmap — tiers

Tiers are ordered for the stated goal: *handle real-world JS written in the
wild, especially by LLMs.* Core built-ins (Object/Array/String) are
promoted ahead of statements/functions because that is the shape of
idiomatic modern JS. Tier numbers are priority order, not a strict
dependency DAG.

For each tier, run with the given `--only` glob, triage the HTML report,
fix in the engine, re-run. A tier is "done enough" when its non-skipped
tests are ≥95% PASS; then graduate to the next.

**Probe before scoping.** Run the relevant `--only` glob to get current
pass/fail counts before estimating session size. Numbers drift with every
engine change; this doc deliberately does not carry them inline.

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

Promoted ahead of statements/functions because idiomatic modern JS is
`arr.map(...).filter(...)`, `Object.keys(x).forEach(...)`, template
literals, destructuring. If these don't work, neither does anything LLMs
produce.

| Area | `--only` glob |
|---|---|
| `Object` | `test/built-ins/Object/**` |
| `Array` | `test/built-ins/Array/**` |
| `String` | `test/built-ins/String/**` |
| Destructuring | `test/language/expressions/**/dstr-** test/language/statements/**/dstr-** test/language/destructuring/**` |
| Template literals | `test/language/expressions/template-literal/**` |
| Object literals (shorthand, computed) | `test/language/expressions/object/**` |
| Array literals (holes, spread) | `test/language/expressions/array/**` |

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

| Area | `--only` glob |
|---|---|
| `Number` | `test/built-ins/Number/**` |
| `Math` | `test/built-ins/Math/**` |
| `JSON` | `test/built-ins/JSON/**` |
| `Error` (and subtypes) | `test/built-ins/Error/** test/built-ins/TypeError/** test/built-ins/RangeError/** test/built-ins/ReferenceError/**` |
| `Function` | `test/built-ins/Function/**` |
| `RegExp` | `test/built-ins/RegExp/**` |
| `Date` | `test/built-ins/Date/**` |
| `Boolean` | `test/built-ins/Boolean/**` |

### Tier 6 — long tail

`directive-prologue/`, `eval-code/`, `global-code/`, iterators (mostly
already covered via `@@iterator`), regex edge features on the skip list.
Revisit when earlier tiers are green.

---

## Open gaps

High-leverage issues that each break many tests at once. Probe with the
relevant `--only` glob before scoping.

- **Directive prologue (`"use strict"`) flip.** Parser tolerates the
  string without activating strict-mode assertions. Skip triage is done
  (`flags: [onlyStrict]` entry in `expectations.yaml` — keeps
  unexpected-passes from hiding real signal). Real strict-mode
  implementation (with/duplicate-params/eval-assign/octal-literal
  negative checks) is a separate, larger project; revisit only if a
  meaningful test cluster outside `onlyStrict` depends on it.
- **Harness-helper dependencies.** `propertyHelper.js`,
  `testTypedArray.js` need descriptor introspection
  (`Object.getOwnPropertyDescriptor` returning attribute slots,
  `defineProperty` enforcing them). `compareArray.js` itself is now
  an empty stub but its users frequently lean on accessor descriptors
  (`Object.defineProperty` with `get` / `set`) — without enforcement,
  iteration-protocol tests that throw via accessor getters loop
  forever, so the include is still skipped. Gates a large fraction of
  `test/built-ins/**` tests; the next big lever once Tier 2 built-ins
  are solid. (Iterator protocol itself is in place — `IterUtils.getIterator`
  + `@@iterator` stand-in.)
- **`EngineException` framing noise** (per *errors must look like
  JavaScript*). Wraps runtime errors in a multi-line `js failed: /
  ========== / Code: / Error: ...` frame. The runner strips it in
  `ErrorUtils.unwrapFraming`, but JS-side fixtures that inspect `.message`
  via `assert.throws` see the framed text — classifier workaround doesn't
  fix the real problem. Reserve the frame for host-side logging; expose the
  raw JS message on `EngineException` directly.

---

## Design invariants

Engine rules established by prior work. Treat as load-bearing — if a
session needs to violate one, the principle goes up for review explicitly.

- **Engine-emitted errors route through the registered error
  constructors.** Engine sites (`PropertyAccess`, `Interpreter`,
  `CoreContext` TDZ/const-reassign/redeclare, `JsJson`, `JsJava`,
  `JavaUtils`) emit `"<Name>: ..."` prefixes.
  `Interpreter.evalTryStmt` parses the prefix into a structured `JsError`
  with linked `.constructor`; `Interpreter.evalStatement` stamps
  `EngineException.getJsErrorName()`. Result: `e instanceof TypeError`,
  `e.name`, `e.constructor.name` all work for engine-originated errors.
  Low-traffic internal-invariant sites (`JsArrayPrototype`/`JsRegex`/
  `JsStringPrototype`, "finally block threw error") still throw plain
  `RuntimeException` — convert the same way as needed.
- **`eval` is a global** registered in `ContextRoot.initGlobal` with
  indirect-eval semantics (parses/evaluates in engine root scope; non-string
  args pass through). Direct-eval scope capture is out of scope.
- **`typeof` reports `"function"` on all callable surfaces.** `Terms.typeOf`
  returns `"function"` for `JsInvokable`, `JsFunction`, built-in constructor
  singletons (via `JsObject.isJsFunction()` — `Boolean`/`RegExp`/error
  globals), and `JsCallable` method refs (`[1].map`, `'x'.charAt`).
  The `!(value instanceof ObjectLike)` guard keeps `JsObject`/`JsArray`
  reporting `"object"`.
- **Error position framing leads with the message.** `Node.toStringError`
  appends `    at <path>:<line>:<col>` (JS-stack-frame-style) instead of
  the engine-internal `<line>:<col> <NodeType>` prefix.
- **`Test262Error` / user-defined error classes** are classified via
  `constructor.name` fallback in `Interpreter.evalProgram` when the thrown
  `JsObject` has no `.name` on its prototype. Function-name inference in
  `CoreContext.declare` fires only when the function's name is empty (so a
  named function passed as a parameter doesn't get permanently renamed).
- **`ErrorUtils.classify` scans embedded `<Name>:`** as a fallback for
  wrapper messages where the type isn't a prefix. Wrappers preserve the
  cause chain so the structured `JsErrorException` name propagates first;
  embedded-name scan is the safety net.
- **JVM exception → JS error mapping** at `Interpreter.evalStatement`
  catch via `classifyJavaException`: `IndexOutOfBoundsException` /
  `ArithmeticException` → `RangeError`; `NullPointerException` /
  `ClassCastException` / `NumberFormatException` → `TypeError`. Name is
  stamped on `EngineException.jsErrorName` and prefixed to the message.
- **`JsError.constructor` populated** at the JS try/catch wrapping site
  (`Interpreter.evalTryStmt`) by resolving the registered global for the
  error's `.name`, so `assert.throws(Ctor, fn)` reading
  `thrown.constructor.name` works.
- **Iteration goes through `IterUtils.getIterator`.** Built-ins (JsArray,
  JsString, List, native arrays) take fast paths; user-defined `ObjectLike`
  with `@@iterator` go through the spec dance. `for-of` on null/undefined
  TypeErrors (was silently iterating zero times — non-spec). `for-in` keeps
  `Terms.toIterable` (key enumeration over objects, silent zero on
  null/undefined per spec). JS-side errors during user iteration propagate
  via `context.error` rather than Java exceptions.
- **Minimal `Symbol` global.** `ContextRoot.initGlobal` exposes
  `Symbol.iterator` / `Symbol.asyncIterator` as their well-known string
  keys (`"@@iterator"` / `"@@asyncIterator"`). No `Symbol(...)` constructor,
  no unique-symbol identity — tests needing those still skip via
  `feature: Symbol`.
- **Optional chaining sentinel propagation.** `PropertyAccess.SHORT_CIRCUITED`
  (distinct identity from `Terms.UNDEFINED`) propagates through chain
  steps; `Interpreter.chainStepResult` converts to UNDEFINED only at the
  chain root. The "distinct from UNDEFINED" detail is load-bearing —
  `obj?.a.b` where `obj.a == null` still throws TypeError per spec.
  Optional-chain early errors are validated post-parse in a single walk
  (`JsParser.validateOptionalChainEarlyErrors`), not interleaved into the
  hot eval loop.
- **Reserved words as object-literal keys.** `T_OBJECT_ELEM` /
  `T_ACCESSOR_KEY_START` are built at class-init from every TokenType with
  `keyword == true`, so `{break: x}`, `{default: 1}`, `{class: foo}` parse
  as object literals and destructuring LHS patterns.
- **Spec ToString unified** via `Terms.toStringCoerce(Object, CoreContext)`;
  `JsObjectPrototype` / `JsArrayPrototype` / `JsBooleanPrototype` /
  `JsNumberPrototype` use the spec-correct `toString`. Use
  `StringUtils.formatJson` directly for JSON display, not the legacy
  formatter.
- **Tagged-template AST shape.** `FN_TAGGED_TEMPLATE_EXPR` is
  `[<callable>, LIT_TEMPLATE]`. The `LIT_TEMPLATE` child holds paired
  cooked/raw string segments and substitution expressions; for N
  substitution expressions there are always N+1 string slots (possibly
  empty). The `strings` JsArray passed to the tag has its `raw` array
  attached via `putMember("raw", raw)`. `new tag\`x\`` evaluates the
  tagged template first (MemberExpression semantics) then constructs
  with no args. `${obj}` interpolations dispatch through the prototype
  chain (so user `toString` throws propagate with constructor identity
  intact). Template-literal lexing is depth-tracked for nested `{}`
  inside `${...}`.
- **`Terms.toPrimitive` is the spec ToPrimitive boundary.** Object →
  primitive coercion (used by `BigInt()`, `Number()`, radix args of
  `toString`, `ToIndex` on `asIntN`/`asUintN`) goes through
  `Terms.toPrimitive(value, hint, context)`. Hint `"number"` (default)
  tries `valueOf` then `toString`; hint `"string"` reverses. Each
  callable runs in a sub-context so its errors flow through
  `context.updateFrom(...)` rather than wrapping as Java exceptions —
  same propagation pattern as `toStringCoerce`. Boxed primitives
  (`JsNumber`/`JsString`/`JsBoolean`/`JsBigInt`) unwrap directly to
  their `getJavaValue()` rather than dispatching through valueOf;
  cheaper and equivalent. Both methods returning objects → TypeError.
  `Symbol.toPrimitive` is *not* dispatched (matches our minimal
  Symbol surface).
- **`Terms.narrow()` checks both ends.** Pre-existing bug:
  `if (d <= Integer.MAX_VALUE) return (int) d` cast any negative
  value past `Integer.MIN_VALUE` to an overflowed int. Fix: both
  bounds (`d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE`) on
  the int and long collapses. The collapse rule itself is unchanged
  for in-range values.
- **BigInt rides on `java.math.BigInteger` with type-tested dispatch.**
  `BigInteger extends Number`, so it flows through `Terms.objectToNumber`
  unchanged. Each arithmetic op in `Terms` (`add`, `mul`, `div`, `mod`,
  `exp`, `min`, bit-ops) checks `lhs instanceof BigInteger ||
  rhs instanceof BigInteger` *before* the existing `doubleValue()`
  fast path; mixing BigInt with non-BigInt throws TypeError per spec
  via `requireBothBigInt`. The branch is paid only by code that
  exercises BigInt — plain Number arithmetic stays unchanged.
  Property access wraps via `Terms.toJsValue` → `JsBigInt` (sealed
  primitive, like `JsNumber`/`JsString`/`JsBoolean`); the
  `BigInteger` case must be listed *before* `Number n` because
  `BigInteger` is a `Number`. Increment/decrement uses
  `Terms.incDecStep(operand)` which returns `BigInteger.ONE` for
  BigInt operands so `i++` doesn't TypeError on type mixing.
  `JSON.stringify` pre-walks for BigInt and throws TypeError;
  unary `+1n` is a TypeError, unary `-1n` negates.
- **Numeric separators sit on the rare-path lexer rule.** `JsLexer.scanNumber`
  uses tight digit loops on the common (separator-free) path; only after
  the fast loop terminates does it test `peek() == '_'` and call
  `scanDigitsWithSeparators` / `scanHexDigitsWithSeparators` (rare
  path). The rare-path scanner enforces "between two digits" by
  consuming the `_`, then asserting the next char is a digit; doubled
  separators error out by the same check. `Terms.toNumber` strips `_`
  only when `text.indexOf('_') >= 0` (no allocation on the common case).
- **Destructuring uses `ObjectLike.getMember`, not `Map.get`.**
  `Interpreter.destructurePattern` reads object-source properties via
  `ObjectLike.getMember`, falling back to `Map.containsKey` on the
  own-properties map to disambiguate absent vs. present-but-undefined.
  Defaults fire only on literal `undefined`, not on `null`.
  Array-source destructuring routes through `IterUtils.getIterator` and
  TypeErrors on non-iterable sources (per spec 13.3.3.5).
  `evalLitArray` / `evalLitObject` are pure literal construction —
  destructuring binds via the unified `destructurePattern` /
  `bindTarget` / `bindLeaf` helpers, which recurse on nested patterns
  and share between assignment and `var`/`let`/`const` paths.
- **Built-in prototypes accept user-added properties.** `Prototype` has
  a `userProps` map; user-added properties win over built-ins on
  lookup (configurable: true / writable: true per spec). Built-in
  methods themselves can't be deleted via `removeMember`. Required for
  `Array.prototype.foo = ...` polyfill patterns and for spec-conformant
  test262 behavior.
- **Per-Engine prototype isolation.** Built-in prototypes are JVM-wide
  singletons (e.g. `JsArrayPrototype.INSTANCE`), but their `userProps`
  must reset each time a new `Engine` is constructed — otherwise a
  previous test that did `Map.prototype.set = function() { throw ... }`
  poisons the next session. `Prototype` constructor registers each
  singleton in a static `ALL` list; `Engine()` calls
  `Prototype.clearAllUserProps()` which walks the list and clears each
  `userProps` map. Spec-compliant for sequential single-Engine usage
  (the realistic case); concurrent Engines in the same JVM still share
  the underlying singleton during overlapping windows. Performance
  impact: invisible — the loop is ~10 entries with usually-empty maps,
  lost in noise at script-eval scale.
- **Function declarations hoist** at the start of the enclosing program
  / block scope. `Interpreter.hoistFunctionDeclarations` walks
  immediate `STATEMENT > FN_EXPR` children, evaluates each — binding
  the name. The main loop in `evalProgram` / `evalBlock` then *skips*
  the FN_EXPR statement (re-evaluating would replace the hoisted
  binding with a fresh `JsFunctionNode` and drop any property
  assignments made on the hoisted function, e.g. `foo.prototype = X`
  before `function foo(){}`). Per spec FunctionDeclaration's
  completion is empty (the previous value carries through); we
  additionally fall back to the last hoisted function as the
  completion when a script contains *only* declarations, so host
  callers loading a script that's just `function fn() {...}` still
  get `fn` back from `eval`.
- **`Array.prototype.*` are generic over array-like `this`.**
  `JsArrayPrototype.rawList` falls back to a `0..length-1` snapshot
  via `getMember(String.valueOf(i))` for any ObjectLike with a
  numeric `.length` — so `Array.prototype.every.call(date, fn)` and
  the `Array.prototype.X.call(obj, ...)` test262 pattern work.
  Mutating methods on a non-array operate on the snapshot list and
  don't write back (would need spec ToObject + index-write
  semantics; not yet modeled).
- **`JsArray.getMember` resolves canonical numeric-index keys.**
  `Array.prototype` lookup includes `String.valueOf(i)` reads (e.g.
  inside `rawList`'s array-like fallback). `JsArray.getMember("3")`
  returns `list.get(3)` rather than delegating to the prototype
  chain. Strict canonical parse (rejects `"01"`, `"+1"`, `"1.0"`)
  so non-canonical string keys still go to namedProps / proto chain.
- **`Function.prototype.bind`** in `JsFunctionPrototype.bindMethod`:
  returns a new `JsFunction` whose `call(ctx, args)` sets
  `ctx.thisObject = boundThis` and prepends pre-bound args to the
  caller's args. `length` / `name` of the bound function are
  approximate (name is `"bound " + target.name`); call semantics are
  what matters.
- **Date stores `[[DateValue]]` as `double` with NaN = Invalid Date.**
  `JsDate` no longer uses `long millis`; the spec representation is a
  Number that may be NaN, and Java's `(long) NaN == 0` would silently
  collapse Invalid Date to epoch. Methods route through pure helpers
  (`JsDate.makeDay` / `makeTime` / `makeDate` / `timeClip` /
  `localToUtc` / `utcToLocal` / `parseToTimeValue`) so the
  Constructor and Prototype share spec algorithms. `localTzaMs` is
  truncated to integer minutes so historical zones with sub-minute
  offsets round-trip through `getTimezoneOffset()` (which spec defines
  as integer minutes). `requireDate(context)` TypeErrors on non-Date
  `this` (Spec thisTimeValue). Setters read `[[DateValue]]` *before*
  coercing args, coerce all args even when the captured value is NaN
  (preserves observable side effects from `valueOf`), then bail
  without writing back when the captured value was NaN — the
  date might have been mutated to a valid value during coercion and
  must not be clobbered.
- **`Object.prototype.hasOwnProperty` is prototype-aware and
  intrinsic-aware.** When the receiver is a `Prototype` singleton
  (`Date.prototype`, `Array.prototype`, etc.) it consults
  `Prototype.hasOwnMember` (built-in methods + userProps); when the
  receiver is a `JsObject` it consults `JsObject.hasOwnIntrinsic`
  alongside the `_map` lookup so built-in constructors report their
  intrinsic statics (`Date.prototype`, `Date.now`, `Date.UTC`, etc.)
  as own. Subclasses (`JsFunction` exposes `prototype`/`name`/`length`/
  `constructor`; `JsDateConstructor` adds `now`/`parse`/`UTC`) override
  `hasOwnIntrinsic` to declare the names their `getMember` resolves
  directly. Required for the `S15.9.5_A*` and `S15.9.4_A*` test
  clusters and analogous tests under other built-ins.
- **Intrinsic-attribute pipeline.** Built-in own properties resolved
  via subclass `getMember` switches (not via `_map`) declare themselves
  as own through `hasOwnIntrinsic(name)` and report attribute bits
  through `getOwnAttrs(name)`. `JsFunction` returns spec defaults for
  its four intrinsics (`length`/`name`: configurable-only; `prototype`:
  writable; `constructor`: writable + configurable); subclasses
  (`JsMath`, etc.) cover their own methods/constants. The descriptor
  read pipeline (`Object.getOwnPropertyDescriptor`,
  `propertyIsEnumerable`, `Object.keys` / `for...in` enumerable filter)
  consults this rather than the all-true default. New attribute slots
  on `_attrs` (set by `Object.defineProperty`) win over the intrinsic
  defaults so user override is still possible.
- **Tombstone-on-delete for intrinsic properties.** `_tombstones:
  Set<String>` (lazy) records intrinsic own properties that the user
  has deleted (`delete obj.foo`). `getMember` short-circuits tombstoned
  names to the prototype chain (skipping subclass intrinsic field
  fallback); `isOwnProperty` returns false; `removeMember` populates
  the set when the intrinsic is configurable; `putMember` clears the
  tombstone on a successful write so reassignment revives the
  property. Matters for `propertyHelper.verifyProperty`'s destructive
  `isConfigurable()` check, which tries `delete obj[name]` and asserts
  `!hasOwnProperty(obj, name)`.
- **`JsObject.isOwnProperty(name)` is the canonical own-key check.**
  Returns true iff `name` is in `_map` OR `hasOwnIntrinsic(name)` AND
  not tombstoned. Replaces the previous mix of
  `toMap().containsKey + hasOwnIntrinsic` checks at three call sites
  (`JsObjectConstructor.isOwnKey`, `JsObjectPrototype.hasOwnProperty`,
  `propertyIsEnumerable`). Anything that wants spec-level "is this an
  own property" goes through here.
- **Per-property attributes on `JsObject` use a sparse byte map.**
  `_attrs: Map<String, Byte>` next to the per-object `nonExtensible` /
  `sealed` / `frozen` flags. Bit 0 = writable, bit 1 = enumerable,
  bit 2 = configurable; absent key means all-true (the new-property
  default for plain `obj.x = ...`). `defineProperty` writes attrs
  explicitly and uses the spec's "missing fields default to false on
  new keys, preserve on existing" rule (different from `[[Set]]`'s
  all-true default — this distinction is load-bearing). Per-object
  flags `frozen` / `nonExtensible` are kept as fast-path early-exits
  on `putMember` / `removeMember` so frozen objects don't have to
  consult `_attrs` per write. Read paths: `getOwnPropertyDescriptor`
  reads `_attrs` (or the all-true default); `JsObject.jsEntries()` —
  the back-end for `for...in` / `Object.keys` / `Object.values` /
  `Object.entries` / `Object.assign` via `Terms.toIterable` — filters
  out non-enumerable keys but is bypassed entirely when `_attrs ==
  null`. `Object.getOwnPropertyNames` / `hasOwn` go through `toMap()`
  directly and are unaffected. `propertyIsEnumerable` consults
  `isEnumerable(name)`. Configurability rules enforced on
  defineProperty: TypeError on flipping configurable false→true,
  changing enumerable, switching data↔accessor shape, or changing
  a non-writable value — with the spec-allowed exceptions (writable
  true→false on data, no-op same-value redefine) passing through.
- **`Object.prototype.toString` dispatches on the host wrapper class.**
  `JsObjectPrototype.DEFAULT_TO_STRING` returns `"[object <Tag>]"`
  where the tag is derived from the receiver type — `Array` for
  `JsArray` / `List`, `Date` for `JsDate` / `java.util.Date`, `RegExp`
  / `Map` / `Set` / `Error` / `Boolean` / `Number` / `String` /
  `Function`, and `Object` as the fallback. `JsObject` implements
  `JsCallable` (host-side artifact) so the `Function` branch must
  exclude plain `JsObject` instances — only `JsFunction` (and
  `JsObject` whose `isJsFunction()` returns true) qualifies.
  Substitute for the spec's `@@toStringTag` until Symbol expansion.

---

## Active priorities

Action list — start at the top. Ordered by core-engine confidence
(does the engine read/run plain JS correctly?) cross-multiplied by
score impact (a one-fix harness unblock beats ten one-test patches).
Re-probe with the relevant `--only` glob before scoping a session.

Current state baseline (2026-04, post full prototype wrap sweep —
Array + Number + String + Date + Object + Boolean + Function +
RegExp + BigInt + Map + Set — on top of the constructor-singleton
sweep, Prototype tombstones/getOwnAttrs, singleton-reset infra,
assignment-target negative-test pass):

| Slice | Pass | Fail | Skip | Total |
|---|---|---|---|---|
| `test/language/**` | 4265 | 3798 | 15582 | 23645 |
| `test/built-ins/Math/**` | 293 | 33 | 1 | 327 |
| `test/built-ins/Number/**` | 232 | 96 | 12 | 340 |
| `test/built-ins/String/**` | 456 | 683 | 84 | 1223 |
| `test/built-ins/Array/**` | 1203 | 1697 | 181 | 3081 |
| `test/built-ins/Object/**` | 2004 | 1276 | 131 | 3411 |
| `test/built-ins/Date/**` | 477 | 47 | 70 | 594 |
| `test/built-ins/Function/**` | 228 | 153 | 128 | 509 |

### Tier 1 — core-engine parser/expression gaps

These are the items that most directly answer "does the engine read
plain JS correctly?" — and they live in `test/language/expressions/**`
where every fix shows up on the scorecard for the right reason.

*(The negative-test strictness pass for assignment targets shipped —
see Recently completed. Open Tier 1 residuals: `this = 1`,
`import.meta = 1`, and a stray NPE on `eval = 1` — small wins, pick
off opportunistically.)*

### Tier 2 — built-in attribute attribution rollout (residual)

The constructor side is fully landed: `getOwnAttrs` infrastructure on
JsObject, JsFunction defaults for length/name/prototype/constructor,
Math + all nine constructor singletons (Array / BigInt / Date /
Function / Map / Number / Object / Set / String) wrapped with
`JsBuiltinMethod` + `_methodCache` + `hasOwnIntrinsic` +
`getOwnAttrs`. Singleton-reset infra (`JsObject.ENGINE_RESET_LIST` +
`clearEngineState()`) plumbs per-Engine reset for all of them.
Remaining work is the **prototype** side and `JsArray.length`.

*(Constructor + prototype wrap rollout fully shipped — see Recently
completed. The Prototype.method() helper + base-class
`_methodCache` + tombstones cover every built-in prototype.)*

- **`JsArray.length` descriptor.** Spec: `{writable: true,
  enumerable: false, configurable: false}`. Currently
  `getMember("length")` short-circuits to `list.size()`; no slot.
  Override `getOwnAttrs("length")` on JsArray to return
  `WRITABLE` (no enumerable, no configurable). The trickier piece is
  that `length` is also writable in a special way (truncates the
  list); the descriptor shape change is independent of that, so
  ship it as its own commit.

### Tier 3 — cross-cutting feature unblock

- **Partial Symbol expansion** — well-known symbols only, not the full
  primitive type. The string-keyed scaffold already in place for
  `@@iterator` widens to `@@toPrimitive`, `@@toStringTag`, `@@species`,
  `@@hasInstance`, `@@isConcatSpreadable`, `@@matchAll`, `@@replace`,
  `@@search`, `@@split`. Symbol-gated tests cluster across *every*
  core type (Object 58, String 40, Array 35, Map 34, Date 30,
  RegExp 29, WeakMap 27, the Symbol object itself 47, etc.) — total
  ~580 SKIP today. Even a partial expansion that:
  - Wires `Terms.toPrimitive` to dispatch through
    `obj["@@toPrimitive"]` if present (already named in three other
    residuals: BigInt `constructor-coercion.js`, Date residuals,
    Map/Set Symbol.species).
  - Exposes `Symbol.species` / `Symbol.toStringTag` /
    `Symbol.hasInstance` as string-keyed stand-ins on the existing
    Symbol global.
  - Routes `Object.prototype.toString.call(x)` through
    `obj["@@toStringTag"]`.
  
  …unblocks a measurable fraction of the 580 without introducing a
  new primitive type. Full `Symbol()` constructor with unique
  identity, `Symbol.for` / `keyFor` / global registry, and proper
  typeof === "symbol" stays gated as before.

  Probe before scoping: count SKIP-by-feature in the latest
  `test/built-ins/**` results.jsonl, group by which Symbol features
  cluster where, decide which carve-outs land first.

### Tier 4 — opportunistic residuals

Smaller items, picked off when nearby. Not session-sized on their own.

- **Map / Set method residuals.**
  - **`Map.prototype.groupBy` / `getOrInsert` / `getOrInsertComputed`**
    (ES2024+).
  - **`Set.prototype.difference` / `intersection` / `union` /
    `isDisjointFrom` / `isSubsetOf` / `isSupersetOf` /
    `symmetricDifference`** (ES2025). Some test cases use `with`
    blocks which hit a separate parser gap.
  - **`Object.getPrototypeOf(Map.prototype)` identity** — returns
    `JsObjectPrototype.INSTANCE` (singleton) where tests compare
    against `Object.prototype` (the global). Need them to share
    identity. Same for `Set.prototype`.

- **`.length` / `.name` rollout to remaining prototypes.**
  `JsBuiltinMethod` infra is in place; `JsDateConstructor` +
  `JsDatePrototype` are wired. Probe (2026-04) shows the
  *incremental* test count from sweeping it through other prototypes
  is small (most `length.js` fails are about array-instance length
  semantics, not method arity; most `name.js` fails are
  Symbol-gated). Treat as background cleanup — wrap any prototype
  in `JsBuiltinMethod` while you're already touching it for another
  reason. Don't schedule a session for the rollout alone.

- **Utility-method residual sweep.** `String.prototype` is nearly
  complete (`padStart` / `padEnd` / `replaceAll` / `matchAll` / `at`
  all landed). Remaining method gaps across other built-ins — pick
  off opportunistically as triage surfaces them.
  *(Object.entries/fromEntries/assign and Array.prototype.includes/
  flat/flatMap/at were already implemented; Array.prototype.includes
  was un-skipped previously.)*

- **Destructuring residuals.** Bulk works; the long tail is
  low-leverage — lexer identifier-escape support (LLMs don't write
  `break`), TDZ / init-order corners, negative parse tests needing
  pattern-vs-literal two-mode parsing. Tackle when nearby.

- **Cleanup residuals.** Individual bugs to pick off opportunistically:
  occasional `"null"` NPE paths, `IllegalName` JDK lambda leak, `Java
  heap space` OOM in array-slice paths. Not a bucket — grab while
  nearby.

### Recently completed (residuals tracked)

Not action items — retrospective notes on areas that just shipped, with
their residual fails enumerated for opportunistic pickup. Read for
context; the action list is above.

- **Full prototype wrap sweep + DRY refactor (Number / String / Date /
  Map / Set / Object / Boolean / Function / RegExp / BigInt).**
  Three commits land the remaining 10 prototypes after the
  Array.prototype foundation, plus a refactor that lifts the
  cache-and-wrap template from each prototype into the `Prototype`
  base class. Each subclass's switch now reads
  `case "push" -> method(name, 1, this::push)` instead of a six-line
  cache check + `new JsBuiltinMethod("push", 1, …)` ceremony — the
  base class's `_methodCache` field, `lookupBuiltin` template,
  `clearAllUserProps` integration, and `method()` helper handle the
  rest. Constructor wraps got the same `name` + `method()` treatment
  via a parallel static helper on `JsObject`.

  | `--only` glob | Before sweep | After sweep | Δ pass |
  |---|---|---|---|
  | `test/built-ins/String/**` | 363 / 776 / 84 | **456** / 683 / 84 | **+93** |
  | `test/built-ins/Object/**` | 1918 / 1362 / 131 | **2004** / 1276 / 131 | **+86** |
  | `test/built-ins/Number/**` | 216 / 112 / 12 | **232** / 96 / 12 | **+16** |
  | `test/built-ins/Map/**` | 80 / 74 / 50 | **98** / 56 / 50 | **+18** |
  | `test/built-ins/Set/**` | 154 / 206 / 23 | **168** / 192 / 23 | **+14** |
  | `test/built-ins/Function/**` | 215 / 166 / 128 | **228** / 153 / 128 | **+13** |
  | `test/built-ins/BigInt/**` | 48 / 13 / 16 | **52** / 9 / 16 | **+4** |
  | `test/language/**` | 4265 / 3798 / 15582 | **4266** / 3797 / 15582 | **+1** |

  Net **+245 PASS**. String and Object lead because both sit at the
  intersection of two effects: their own `name.js` / `length.js` /
  `prop-desc.js` tests across every prototype method, plus the
  cross-cutting `Object.getOwnPropertyDescriptor(SomeProto, 'X')`
  paths that previously returned `undefined` for every built-in
  prototype key. Date / Boolean / RegExp / Math / Array unchanged in
  their own slices on this round (Array was shipped earlier; Date's
  prototype was already wrapped inline; the rest have small or no
  prototype-test footprints).

  Refactor mechanics:
  - `Prototype._methodCache` (LinkedHashMap) lives on the base.
    `lookupBuiltin` is the cache-aware wrapper called from
    `getMember`; subclasses just declare `getBuiltinProperty` (the
    switch). `clearAllUserProps` walks the cache too.
  - `Prototype.method(name, length, delegate)` and
    `JsObject.method(name, length, delegate)` (static) are the
    canonical sugar — using the case-label value `name` as the first
    arg removes a class of "wrong-name" typos
    (e.g. `trimLeft.name === "trimLeft"`, not "trimStart").
  - The `clearSubclassState()` hook introduced for Array.prototype
    is gone — base-class cache management makes it unnecessary.

- **Array.prototype wrap + Prototype tombstones / `getOwnAttrs`.**
  First step on the prototype side, after the constructor sweep.
  Prototype.java grew the spec-correct delete + descriptor machinery
  it was missing: a tombstone Set so `delete Array.prototype.push`
  actually removes the built-in (was a silent no-op),
  `getOwnAttrs(name)` returning `{writable: true, enumerable: false,
  configurable: true}` by default for built-in methods, a
  `clearSubclassState()` hook called per-Engine for
  `_methodCache` reset. `JsObjectConstructor.isOwnKey` /
  `ownAttrs` / `ownGet` now route through `Prototype.hasOwnMember` /
  `getOwnAttrs` / `getMember` so `getOwnPropertyDescriptor(Array.
  prototype, 'push')` reports the right shape (was returning
  `undefined`). JsArrayPrototype wraps each of its 35 built-in
  methods in `JsBuiltinMethod`; toString stays as the
  `DEFAULT_TO_STRING` lambda so JsConsole's identity-by-reference
  override detection keeps working.

  | `--only` glob | Before | After | Δ pass |
  |---|---|---|---|
  | `test/built-ins/Array/**` | 1137 / 1763 / 181 | **1203** / 1697 / 181 | **+66** |
  | `test/built-ins/Object/**` | 1893 / 1387 / 131 | **1918** / 1362 / 131 | **+25** |
  | `test/language/**` | 4265 / 3798 / 15582 | **4266** / 3797 / 15582 | **+1** |

  Net **+92 PASS**. Array's wins concentrate on the 38 `name.js` +
  38 `length.js` + 35 `prop-desc.js` tests across its 35 methods —
  one wrap pattern lights up most of them. Object incidentals come
  from `Object.getOwnPropertyDescriptor(Array.prototype, ...)` paths
  that previously returned `undefined` for every key. Same template
  still to roll out across the other 11 prototypes — see the
  Tier 2 residual above.

- **Constructor-singleton wrap sweep (Number → Object → Array →
  String → Function/BigInt/Map/Set/Date).** Five commits, one per
  cluster, applying the JsMath template across every built-in
  constructor singleton. Each constructor's static methods are now
  wrapped in `JsBuiltinMethod` with spec `length` and `name`, cached
  per-Engine in `_methodCache` (cleared via `clearEngineState`), and
  declared via `hasOwnIntrinsic` + `getOwnAttrs` so descriptor probes
  report the correct attribute bits. The `prototype` slot on every
  built-in constructor reports all-false attrs (overriding
  JsFunction's user-function default of `WRITABLE`).

  | `--only` glob | Before sweep | After sweep | Δ pass |
  |---|---|---|---|
  | `test/built-ins/Object/**` | 1798 / 1482 / 131 | **1893** / 1387 / 131 | **+95** |
  | `test/built-ins/Number/**` | 187 / 141 / 12 | **215** / 113 / 12 | **+28** |
  | `test/built-ins/Array/**` | 1127 / 1773 / 181 | **1137** / 1763 / 181 | **+10** |
  | `test/built-ins/String/**` | 357 / 782 / 84 | **363** / 776 / 84 | **+6** |
  | `test/built-ins/Date/**` | 474 / 50 / 70 | **477** / 47 / 70 | **+3** |

  Net **+142 PASS** across the sweep. Object dominates because the
  slice carries a propertyHelper-style probe (`length.js`,
  `name.js`, `prop-desc.js`) for *each* of its 22 static methods —
  one wrap pattern lights up dozens of tests. Function / Map / Set /
  BigInt unchanged in their own slices: their static-method
  population is small (or zero) and the `prototype`-attr fix for
  built-in constructors isn't probed there directly. The cumulative
  Object win includes incidental gains from the cross-cutting
  `getOwnPropertyDescriptor(Date|Number|…, 'X')` paths now reading
  the right shape. Math / Function / language slices unchanged.

  Per-commit breakdown:
  - `5b7f9efa0` Number — +28 Number, +6 Object
  - `a0b4cf47f` Object — +81 Object
  - `5d8d4e25a` Array + String — +10 Array, +6 String
  - `589a6f03b` Function / BigInt / Map / Set / Date — +3 Date,
    +8 Object (incidental from Date's descriptor fix)

  Residual: the **prototype** side is still raw `JsCallable`
  lambdas — `Array.prototype.push.length` etc. won't read correctly
  yet, and `delete Array.prototype.push` is a silent no-op. See the
  Tier 2 "Prototype per-method attributes" item above for the next
  step (needs Prototype tombstones plus per-method wraps).

- **Per-Engine reset for built-in constructor singletons.** Mirror of
  `Prototype.ALL` / `clearAllUserProps` on the JsObject side. New
  `JsObject.ENGINE_RESET_LIST` (a `CopyOnWriteArrayList`) collects
  singleton instances that call `registerForEngineReset()` from their
  constructor; `Engine.<init>` invokes
  `JsObject.clearAllEngineState()` right after
  `Prototype.clearAllUserProps()`. Default `clearEngineState()` wipes
  `_map` / `_attrs` / `_tombstones` / extensibility flags; subclasses
  with caches override and `super.clearEngineState()` first.
  All nine constructor singletons (Array / BigInt / Date / Function /
  Map / Number / Object / Set / String) registered themselves in the
  same commit. `EngineTest.testBuiltinConstructorStateResetBetween
  Engines` exercises the user-set / delete / re-resolve paths so a
  regression can't silently re-introduce the leak. Pure infra — no
  behavior change for non-leak cases. Incidentally fixed +1 PASS in
  `test/language/**` from a previously polluted test (4264 → 4265).

- **Assignment-target negative-test pass (IsValidSimpleAssignmentTarget).**
  Folded into the existing post-parse early-error walk in
  `JsParser.validateEarlyErrors`. At every `ASSIGN_EXPR` /
  `MATH_PRE_EXPR(++/--)` / `MATH_POST_EXPR` node the LHS / operand is
  classified per spec: bare identifier and `x.y` / `x[k]` (including
  over a CallExpression head) are valid simple targets;
  `[a,b]`/`{a,b}` *at the top of the LHS* refines to a destructuring
  AssignmentPattern; everything else (binary expressions, unary
  operators, literals, function/arrow expressions, comma expressions,
  parenthesized destructuring patterns, nested assignments,
  tagged-template heads, `new`-expressions) throws
  `ParserException` so the test262 runner classifies it as
  `phase: parse`. The optional-chain checks already in the walk were
  re-folded into the same visit; the walk now runs unconditionally
  (it's O(N) and assignments are common enough that gating doesn't
  pay).

  | `--only` glob | Before | After | Δ pass |
  |---|---|---|---|
  | `test/language/expressions/assignmenttargettype/**` | 63 / 243 / 18 | **301** / 5 / 18 | **+238** |
  | `test/language/**` | 4003 / 4060 / 15582 | **4264** / 3799 / 15582 | **+261** |

  Net **+261 PASS** in `test/language/**` (no SKIP delta — every
  conversion was FAIL → PASS). Built-in slices unchanged
  (`test/built-ins/Object/**` still 1798/1482/131, `Array/**` still
  1127/1773/181). Profile-mode `EngineBenchmark`: 1.38 ms array /
  0.52 ms object — within the ±5 % thermal-noise band of the prior
  reference (1.32 / 0.50).

  Spec carve-outs deliberately not enforced (these tests stay FAIL
  or stay SKIP, by design):
  - **Web-compat `f() = 1` / `f() += 1` / `++f()`.** The spec
    permits `CallExpression = ...` in non-strict mode (returns
    `~web-compat~`); the corresponding test262 cases are flagged
    `onlyStrict` and skipped by the harness. The engine has no
    separate strict mode, so non-strict is permanent — no rejection
    needed.
  - **`this = 1` / `parenthesized this`.** `this` lexes as IDENT
    (per JsLexer's keyword-set policy); rejecting requires a text
    check on the identifier. 2 fails in this slice.
  - **`import.meta = 1`** parses as a normal `REF_DOT_EXPR` over an
    `import` identifier; we'd have to special-case the literal
    `import.meta` form. 2 fails.
  - **`eval = 1`** (`simple-basic-identifierreference-eval.js`) —
    pre-existing NPE in the engine's host-bindings setup
    (`Cannot read field "listener" because "this.root" is null`),
    unrelated to assignment-target validation.

- **Built-in intrinsic attribute attribution + tombstone-on-delete +
  Math wrap.** Follow-up to the per-property attribute enforcement
  work. With `propertyHelper.js` un-skipped, ~hundreds of tests now
  call `verifyProperty(BuiltIn, 'method', {writable, enumerable,
  configurable, value})` and `verifyCallableProperty` on built-in
  methods. The descriptor reader was hitting all-true defaults
  because `Prototype.getMember` / built-in constructor `getMember`
  switches don't carry attribute info. This commit threads spec
  defaults through the descriptor pipeline for the foundation
  (JsFunction's four intrinsics) and the Math object specifically.

  | `--only` glob | Before | After | Δ pass |
  |---|---|---|---|
  | `test/built-ins/Math/**` | 177 / 149 / 1 | **293** / 33 / 1 | **+116** |
  | `test/built-ins/Date/**` | 382 / 142 / 70 | **474** / 50 / 70 | **+92** |
  | `test/built-ins/Object/**` | 1763 / 1517 / 131 | **1798** / 1482 / 131 | **+35** |
  | `test/built-ins/Function/**` | 204 / 177 / 128 | **215** / 166 / 128 | **+11** |
  | `test/language/**` | 3992 / 4071 / 15582 | **4003** / 4060 / 15582 | **+11** |
  | `test/built-ins/Array/**` | 1123 / 1777 / 181 | **1127** / 1773 / 181 | **+4** |
  | `test/built-ins/String/**` | 356 / 783 / 84 | **357** / 782 / 84 | **+1** |

  Net **~+270 PASS**. Math is the headline (+116 from the wrap +
  attrs); Date (+92) benefits from the JsFunction defaults applying
  to `Date`/`Date.now`/`Date.UTC`/`Date.parse`. Number's potential
  ~+18 deferred — see the Tier 2 residual note above on singleton
  state leak.

  Concretely:
  - **`getOwnAttrs(name)` mechanism on JsObject.** Default reads
    from `_attrs` (the user-defined attribute map from the prior
    session); subclasses override to return spec-default attributes
    for intrinsic-declared own properties. Routed into both
    `JsObject.isEnumerable` (so `for...in` / `Object.keys` filter
    is spec-correct for built-ins, not just user-defined) and
    `JsObjectConstructor.ownAttrs` (so descriptor reads see the
    right bits).
  - **JsFunction defaults.** `length` / `name` →
    `{writable: false, enumerable: false, configurable: true}`.
    `prototype` → `{writable: true, enumerable: false,
    configurable: false}` (user-function shape; built-in
    constructors override to all-false but the user-function shape
    is the most common case). `constructor` →
    `{writable: true, enumerable: false, configurable: true}`.
  - **Math wrap.** Each method wrapped in `JsBuiltinMethod(name,
    length, delegate)` so `Math.cos.length === 1` /
    `Math.cos.name === 'cos'` work as own properties. Methods
    cached per-Engine in a `_methodCache` Map so `Math.cos ===
    Math.cos` holds (and tombstones can be applied to a stable
    instance). `JsMath.hasOwnIntrinsic` declares each method +
    constant; `JsMath.getOwnAttrs` returns
    `WRITABLE | CONFIGURABLE` for methods (per spec) and `0`
    (all-false) for the seven constants. Plus added `Math.LOG10E`
    which was missing from the original switch.
  - **Tombstone semantics for `delete` on intrinsics.**
    `propertyHelper.verifyProperty` actually deletes the property
    being verified to prove it's configurable, then asserts
    `!hasOwnProperty(obj, name)`. Intrinsic-backed properties
    (resolved via getMember switches, not stored in `_map`) had no
    way to "go away" — `delete Math.cos` was silently a no-op,
    breaking `isConfigurable()`. Now `JsObject` carries a sparse
    `_tombstones: Set<String>`. `removeMember` adds to it when the
    intrinsic is configurable; `getMember` short-circuits to the
    prototype chain when tombstoned (skipping subclass intrinsic
    field fallback); `isOwnProperty` returns false; `putMember`
    clears the tombstone on a successful write so re-assignment
    revives the property.
  - **`isOwnProperty(name)` on JsObject** — the canonical own-key
    check. Replaces the previous mix of
    `_map.containsKey + hasOwnIntrinsic` checks at three sites
    (`JsObjectConstructor.isOwnKey`, `hasOwn`,
    `propertyIsEnumerable`). Returns false for tombstoned keys.
  - **Descriptor read pipeline updates.**
    `JsObjectConstructor.getOwnPropertyDescriptor` consults
    `isOwnProperty` (which sees through `hasOwnIntrinsic`) instead
    of just the `_map.keySet` containment check; `ownGet` falls
    through to `getMember` for intrinsic-only keys so e.g.
    `getOwnPropertyDescriptor(Math, 'cos')` resolves the
    JsBuiltinMethod via `Math.getMember("cos")` not
    `Math._map.get("cos")` (which is empty).
    `getOwnPropertyDescriptors` walks toMap() + a fixed probe set
    (`length / name / prototype / constructor`) for intrinsic-only
    keys.
  - **`putMember` honors intrinsic writable.** Before, writing to
    `Math.E` succeeded because `_map` was empty and there was no
    writable check on the intrinsic side. Now `putMember` consults
    `getOwnAttrs(name)` for intrinsic-only keys and refuses the
    write when `WRITABLE` is clear. Combined with the existing
    user-side `_attrs` writable check, both paths enforce now.

  Residual fails (each blocked on infra not in this session):
  - **Number / Function / Array / String / Date prototype methods**
    — same wrap pattern needs to roll out to the rest. Singleton
    state leak (`JsNumberConstructor` etc. are JVM-wide singletons,
    unlike `JsMath`) needs a parallel reset hook to
    `Prototype.clearAllUserProps`. Predicted ~+50–100 PASS once
    that's threaded.
  - **`Math.f16round` / `Math.sumPrecise`** — ES2024 / ES2025
    additions not implemented. ~8 fails.
  - **`Math.max`/`Math.min` argument coercion order** — 3 tests
    expect side effects from `valueOf` to happen in a specific
    order; we currently coerce all args before reducing. Small fix.
  - **`Math.asinh(-Infinity)` / `Math.atanh(±1)`** — return NaN
    where spec wants ±Infinity. Edge cases in our manual log+sqrt
    impl.
  - **`Symbol.toStringTag` on Math** — gated on Symbol expansion.

- **Per-property attribute enforcement (`writable` / `enumerable` /
  `configurable`) + harness un-skip.** The big Tier 2 lever from the
  prior plan. JsObject grew a sparse `_attrs` byte map; defineProperty
  threads the triplet (defaults to all-false for new keys per spec,
  preserves missing fields on existing keys); `[[Set]]` checks
  writable; `delete` / `removeMember` checks configurable; `for...in`
  / `Object.keys` / `Object.values` / `Object.entries` /
  `Object.assign` filter by enumerable; `seal` / `freeze` populate
  the attribute map for existing keys; non-configurable redefinition
  rules enforced (TypeError on attempts to flip configurable false→true,
  change enumerable, switch shape, or change a non-writable value).
  `propertyHelper.js` and `compareArray.js` un-skipped from
  `expectations.yaml`.

  | `--only` glob | Before | After | Δ pass |
  |---|---|---|---|
  | `test/built-ins/Object/**` | 975 / 1447 / 989 | **1763** / 1517 / 131 | **+788** |
  | `test/language/**` | 3121 / 3012 / 15150 | **3992** / 4071 / 15582 | **+871** |
  | `test/built-ins/Array/**` | 1085 / 1495 / 501 | **1123** / 1777 / 181 | **+38** |
  | `test/built-ins/Date/**` | 374 / 4 / 216 | **382** / 142 / 70 | **+8** |
  | `test/built-ins/String/**` | 349 / 672 / 202 | **356** / 783 / 84 | **+7** |
  | `test/built-ins/Function/**` | 204 / 149 / 156 | **204** / 177 / 128 | 0 |
  | `test/built-ins/Math/**` | 177 / 28 / 122 | **177** / 149 / 1 | 0 |
  | `test/built-ins/Number/**` | 187 / 94 / 59 | **187** / 141 / 12 | 0 |

  Net **~+1712 PASS** across the probed slices, in the predicted
  +1500–2200 band. Slices where pass moved 0 (Math/Number/Function)
  saw their SKIPs convert to FAILs — those tests now run but require
  the *built-in* method attribute attribution that's the new Tier 2
  follow-up. Hidden coverage flipped to visible-fail signal, which is
  the right direction.

  Concretely:
  - **Storage.** `JsObject` carries a sparse `Map<String, Byte>
    _attrs` next to the existing `nonExtensible` / `sealed` / `frozen`
    flags. Default (all three bits set) is encoded by absence — no
    map entry is allocated when a property is created via plain `obj.x
    = ...`, so the common path stays zero-allocation. Bit layout:
    `WRITABLE = 0b001`, `ENUMERABLE = 0b010`, `CONFIGURABLE = 0b100`,
    `ATTRS_DEFAULT = 0b111`.
  - **defineProperty new vs. existing semantics.** New keys default
    missing attribute fields to false (per spec
    ValidateAndApplyPropertyDescriptor); existing keys preserve
    missing fields. The `[[Set]]` path (plain assignment, `putMember`)
    keeps creating new keys with all-true defaults — the two paths
    diverge correctly.
  - **Configurability rules enforced.** Redefining a non-configurable
    property TypeErrors on most changes; the spec-allowed exceptions
    (writable true→false, no-op same-value redefine) pass through.
    Includes the data↔accessor shape switch and accessor get/set
    identity checks.
  - **`buildDescriptor` reads from `_attrs`.** Both
    `getOwnPropertyDescriptor` and `getOwnPropertyDescriptors` now
    return the actual attribute bits (writable on data, enumerable,
    configurable on both). Accessor shape suppresses writable per
    spec.
  - **Iteration filters by enumerable.** `JsObject.jsEntries()`
    (the back-end for `Terms.toIterable`, `for...in`, `Object.keys`,
    `Object.values`, `Object.entries`, `Object.assign`) skips entries
    whose attribute byte has `ENUMERABLE` cleared. Fast path: when
    `_attrs == null` (no defineProperty has touched the object), the
    filter is bypassed entirely. `Object.getOwnPropertyNames` /
    `hasOwn` go through `toMap()` directly and are unaffected — they
    must include non-enumerable own keys.
  - **`seal` / `freeze` populate the map.** `seal` clears
    `CONFIGURABLE` on every existing key (so descriptor reads report
    `configurable: false`); `freeze` additionally clears `WRITABLE` on
    data slots (skipping accessor slots since `writable` is N/A there).
    The per-object flags are kept as the fast-path early-exit on
    `putMember` / `removeMember`, so frozen objects don't have to
    consult `_attrs` per key.
  - **`propertyIsEnumerable`** consults `JsObject.isEnumerable(name)`
    after the own-key check; previously assumed all own keys were
    enumerable.
  - **Harness friction fix:** `JsonLite.writeString` now escapes
    surrogate halves as `\uXXXX` regardless of pairing. Without this,
    a test message containing a lone surrogate (e.g. emoji-related
    `Array.prototype.concat` diagnostics that quote
    `"yuck💩"`) tripped `MalformedInputException` in the
    UTF-8 BufferedWriter and aborted the whole run on the FAIL row's
    JSONL append. The escape is JSON-legal and round-trips correctly.

  Residual fails (each blocked on infra not in this session):
  - **Built-in method attributes.** ~hundreds of `length.js` /
    `name.js` / `prop-desc.js` tests across Math/Number/Function/
    Array/String/Date verify built-in methods report `enumerable:
    false`, but our descriptor reader hits the all-true default
    because `Prototype.getMember` / `JsBuiltinMethod` don't carry
    attribute info. This is the new Tier 2 follow-up.
  - **`JsArray.length` descriptor.** Spec: `writable: true,
    enumerable: false, configurable: false`. Our read returns
    all-true (length is short-circuited in `JsArray.getMember`,
    bypassing `_attrs`). Cluster of ~10 tests across
    `Array.prototype.*` and `Array/length/**`.
  - **Array element index descriptors after `defineProperty`.**
    `Object.defineProperty(arr, "0", {value: x, writable: false})`
    — the attribute map lives on `JsObject` but `JsArray` overrides
    `getMember`/`putMember` and stores indices in `list`, not in
    `_map`. Cross-cutting array-vs-defineProperty work, follow-up.
  - **`Object.create(proto, descriptors)` non-configurable defaults.**
    Smoke-tested via `JsObjectTest.testCreate*`; the spec edge cases
    around inheriting non-configurable descriptors from the prototype
    chain weren't all covered. Tackle when nearby.

- **Descriptor infra — `Object.defineProperty` accessor descriptors,
  `getOwnPropertyDescriptor` accessor shape, `isExtensible` /
  `preventExtensions` / `seal` / `freeze`.** Tier 2 step 1 (parser)
  was already shipped; this lands steps 2–5 except per-property
  attribute enforcement (the deferred step listed in the new Tier 2).

  | `--only` glob | Before | After | Δ |
  |---|---|---|---|
  | `test/built-ins/Object/**` | 529 / 1893 / 989 | **975** / 1447 / 989 | **+446** |
  | `test/built-ins/Array/**` | 1006 / 1574 / 501 | **1085** / 1495 / 501 | **+79** |
  | `test/built-ins/Function/**` | 194 / 159 / 156 | **204** / 149 / 156 | **+10** |
  | `test/language/**` | 3093 / 3040 / 15150 | **3121** / 3012 / 15150 | **+28** |

  Concretely:
  - **`Object.defineProperty` accepts accessor descriptors.**
    `JsObjectConstructor.defineProperty` previously silently dropped
    `get`/`set` keys (only `value` did anything). Now it detects the
    descriptor type (data: `value`/`writable`; accessor: `get`/`set`),
    rejects the data+accessor conflict per spec, validates that
    `get`/`set` are callables, and constructs a `JsAccessor` installed
    via `putMember`. Merge with existing accessor: defining only one
    half preserves the other (matches the literal path's behavior in
    `evalLitObject`).
  - **`getOwnPropertyDescriptor` returns the right shape.** New
    `buildDescriptor` helper detects `JsAccessor` slots and returns
    `{get, set, enumerable, configurable}`; data slots return
    `{value, writable, enumerable, configurable}`. The triplet
    `enumerable / writable / configurable` is hardcoded `true` —
    that's the deferred Tier 2 work.
  - **`Object.isExtensible / preventExtensions / isSealed / seal /
    isFrozen / freeze`.** Per-`JsObject` boolean flags (`nonExtensible`
    / `sealed` / `frozen`); `putMember` consults them at the write
    boundary (frozen → ignore; non-extensible → ignore writes that
    would create a new key, allow updates to existing). Per-property
    attribute slots aren't tracked, so the per-key sealing dimension
    of `seal` (configurable: false on existing keys) is approximate —
    enough for most tests but not for ones verifying individual key
    descriptors after seal.
  - **`Array.prototype.*` methods see accessor values, not wrappers.**
    `JsArrayPrototype.rawList` builds an array-like snapshot via
    `getMember(String.valueOf(i))`. Without my change, an accessor
    installed via `defineProperty(obj, "1", {set: ...})` would surface
    in the snapshot as a raw `JsAccessor` object (callbacks would see
    `typeof JsAccessor === "object"`, not `undefined`). Fixed by
    detecting `JsAccessor` in the snapshot loop and resolving through
    the getter (or `UNDEFINED` for setter-only). This unlocked ~20
    `Array.prototype.*` regression tests.

  Residual fails (each blocked on infra not in this session):
  - **`propertyHelper.js` users with non-writable / non-enumerable /
    non-configurable expectations.** ~2200 tests gated; un-skipping
    today net-regresses Array/String slices because we report all
    three attributes as `true`. The next Tier-2 item handles this.
  - **`compareArray.js` un-skipped users with accessor-throws-to-
    terminate iteration patterns.** Same blocker — without
    configurable enforcement on `length`, those tests can hang.
  - **Setter side effects on existing JsArray indices** —
    `defineProperty(arr, "0", {set: fn})` doesn't dispatch through
    `JsArray.getMember(numeric)` because that path uses canonical
    numeric-index resolution (`list.get(i)`) and bypasses the
    JsAccessor map. Cluster of ~5 fails. Probably small but
    requires a numeric-index-vs-accessor coexistence story.

- **`void` unary operator + binary/octal numeric literals.**
  Two adjacent parser/lexer gaps that surfaced while triaging the
  (already-shipped) accessor-literal item:

  | Slice | Before | After | Δ |
  |---|---|---|---|
  | `test/language/**` | 3045 / 3088 / 15150 | **3093** / 3040 / 15150 | **+48 PASS, −48 FAIL** |
  | `test/language/expressions/object/**` | 165 / 315 / 690 | **171** / 309 / 690 | **+6** |
  | `test/language/expressions/void/**` | 0 (parse error) | **8** / 1 / 0 | **+8** |

  Concretely:
  - **`void` was not lexed as a keyword.** The lexer comment said
    "this and void are NOT keywords in the lexer", and `keywordOrIdent`
    indeed returned IDENT for `void`. No parser path consumed it.
    Fix: add `c0 == 'v' && matchKeyword(start, "void") -> VOID` in
    the len==4 branch; thread VOID through `T_UNARY_EXPR` (so it
    flows through the existing `unary_expr()` shape, parallel to
    NOT/TILDE), `T_EXPR_START` (so statement-level `void { ... }`
    isn't pre-empted by the block path), and the `regexAllowed` set
    (so `void /regex/` works). Interpreter: one `case VOID ->
    Terms.UNDEFINED` arm in `evalUnaryExpr` — operand evaluated for
    side effects, result discarded per spec. Unblocks `void { ... };`
    statement form (the `prop-dup-*` cluster — 8 tests — depended on
    this) and idiomatic `void 0` patterns.
  - **Binary / octal numeric literals.** Lexer recognized `0x`/`0X`
    (hex) but not `0b`/`0B` (binary) or `0o`/`0O` (octal) — both
    valid since ES2015. Added `scanNumber` branches that mirror the
    hex shape (fast-path digit loop, separator support via
    `scanBinaryDigitsWithSeparators`/`scanOctalDigitsWithSeparators`,
    BigInt suffix). `Terms.fromHex` renamed to `fromRadixPrefix`,
    extended to dispatch on prefix (`x`/`b`/`o` → radix 16/2/8) and
    return the parsed value; `toBigInt` mirror-extended the same way.
    Existing hex-only behavior preserved on the common path.

  Residual fails enumerated:
  - **`accessor-name-computed-in.js`** — `in` operator inside computed
    accessor key (`[a in b]`) isn't parsed; tests using `in` in expressions
    seem to confuse the parser. Single-test cluster. Investigate when
    nearby.
  - **`accessor-name-literal-string-{line-continuation,hex-escape,
    unicode-escape,default-escaped-ext}.js`** (~5 tests) — string-literal
    escape sequences in accessor key positions don't normalize to the
    canonical name string at access time. Same shape across the cluster:
    the literal text is stored as the key (`'\x66\x6f\x6f'`) but access
    via the unescaped form (`obj.foo`) misses. Likely fix in
    `evalAccessorElem` to call `unescapeString` (already exists for
    template literals) on string-literal accessor keys.
  - **`accessor-name-literal-numeric-non-canonical.js`** — `{get 1.0() {}}`
    should canonicalize `1.0` to `"1"` per `ToString(1)`. Numeric-key
    canonicalization in `evalAccessorElem` uses `keyChild.getText()` (the
    raw token text) instead of `Terms.toStringCoerce(Number(...))`.
  - **`accessor-name-computed-err-to-prop-key.js`** — `ToPropertyKey`
    on a thrown-via-Symbol object should TypeError. Symbol-gated.
  - **`S11.4.2_A1.js`** — `void` followed by exotic whitespace
    (``/``) between operator and operand. Lexer
    whitespace recognition; not specific to `void`.

- **Class/super skip-list expansion + bitwise compound assignments.**
  Two combined changes that compound:

  | Slice | Before | After | Δ |
  |---|---|---|---|
  | `test/language/**` (FAIL) | 4194 | 3088 | **−1106** |
  | `test/language/expressions/compound-assignment/**` (PASS) | 149 | 203 | **+54** |

  Concretely:
  - **`etc/expectations.yaml` path skips for class / super.**
    ~1000 tests under `test/language/**/class/**` and
    `test/language/expressions/super/**` lacked the `feature: class`
    YAML tag and surfaced as parser-error FAILs, polluting every
    language probe. Two `paths:` entries (one for `class/**`, one
    for `expressions/super/**`) move 1100+ noise rows from FAIL to
    SKIP.
  - **Pre-existing parser bug in `Expectations.java`.** The hand-rolled
    YAML parser only flushed pending key/reason on a new `- ` item,
    not on section transition (`paths:` → `flags:`). The last entry
    of every section was being silently misrouted to the *next*
    section's map. Surfaced when the new last `paths:` entry
    (`expressions/super/**`) didn't match. Fix: flush at section
    transition. Regression test added in `ExpectationsTest`.
  - **Bitwise compound assignments (`&=`, `|=`, `^=`).** Tokens were
    already lexed (`AMP_EQ`, `PIPE_EQ`, `CARET_EQ`) and `Terms` had
    the underlying ops (`bitAnd` / `bitOr` / `bitXor`). Wiring was
    missing in `JsParser.T_ASSIGN_EXPR` (the assignment-operator set
    consulted by `expr_rhs`) and in `PropertyAccess.applyOperator`
    (the compound-op dispatcher). One token added to each. The shift
    and exponent compounds (`<<=`, `>>=`, `>>>=`, `**=`) were
    *already* wired and pass — the action item's framing ("currently
    fail to parse") was outdated for those four; the actual gap was
    just the three bitwise compounds.

  Residual fails in `compound-assignment/**` (147): all blocked on
  infra outside this session.
  - **Strict-mode ReferenceError tests** (~9): `eval("undeclared *= 1")`
    must throw ReferenceError under strict. Engine has no strict-mode
    flip — already documented in TEST262.md *Open gaps*.
  - **`with` block + accessor getter/setter object literals** (~50):
    `with (scope) { x ^= 3 }` plus `var scope = { get x() {...}, set x(v) {...} }`.
    Blocked on the Tier-1 *Object-literal getter/setter parsing* and
    the engine has no `with` (out of scope).
  - **Negative parse tests** (`*-non-simple.js`, ~11): expect
    SyntaxError for `({a:0} += b)` etc. — covered by Tier-1
    *Negative-test strictness pass*.

- **Function-object identity trio + `JsFunction.length` unification.**
  Score impact across the priority probes:

  | `--only` glob | Before | After | Δ |
  |---|---|---|---|
  | `test/built-ins/**/not-a-constructor.js` | 62 / 213 / 240 / 515 | **215** / 60 / 240 / 515 | **+153** |
  | `test/built-ins/**/is-a-constructor.js` | 0 / 28 / 17 / 45 | **18** / 10 / 17 / 45 | **+18** |
  | `test/built-ins/Function/**` | 89 / 264 / 156 / 509 | **194** / 159 / 156 / 509 | **+105** |
  | `test/built-ins/Date/**` | 367 / 11 / 216 / 594 | **374** / 4 / 216 / 594 | **+7** |

  Concretely:
  - **`isConstructable` marker on `JsCallable`** (the spelling tracks
    Java's `*-able` interface convention). Default `false`; overridden
    `true` on `JsFunction`, `JsFunctionNode` (gated by `!arrow`),
    `JsBoolean` / `JsRegex` / `JsError` (when `builtinConstructor` /
    registered-global), `JsTextEncoder` / `JsTextDecoder` /
    `JsUint8Array`. The interpreter's two construct paths
    (`invokeCallable` when `newKeyword=true`, and `evalNewExpr`'s
    tagged-template fallback) check the flag and TypeError otherwise.
    A new `JsConstructor` functional interface lets the Java-bridge
    construct lambdas in `PropertyAccess` (the `(JsConstructor) (c, a) ->
    ea.construct(args)` shape) opt back in.
  - **`Function` global + `Function.prototype` intrinsic.**
    `JsFunctionConstructor extends JsFunction` registered in
    `ContextRoot.initGlobal`. `new Function('a','b','return a+b')`
    builds a source string `(function anonymous(a,b) { return a+b })`,
    feeds it through `Engine.evalRaw`, returns the resulting
    `JsFunctionNode`. `Function.prototype` returns the
    `JsFunctionPrototype.INSTANCE` singleton — so
    `Object.getPrototypeOf(someFn) === Function.prototype` holds and
    `(function(){}).constructor === Function` resolves correctly. The
    fix dropped `JsFunction.getMember`'s `case "constructor" -> this`
    short-circuit so `f.constructor` walks the prototype chain to
    `JsFunctionPrototype.constructor → JsFunctionConstructor.INSTANCE`.
    One existing JUnit assertion was wrong (`a.constructor.name === 'a'`)
    and was corrected to match the spec.
  - **Minimal `Reflect.construct` / `Reflect.apply`.** Just enough
    surface for the test262 `isConstructor.js` harness to work. Full
    `Reflect` stays gated via `feature: Reflect` — tests with the
    narrower `feature: Reflect.construct` key now run.
    `Interpreter.constructFromHost` is the package-visible entry point
    that bypasses the syntactic `NEW_EXPR` Node and dispatches to
    `invokeAsConstructor` after validating `isConstructable()`.
  - **`JsBuiltinMethod` infra (partial — Date proof-of-concept).**
    `final class JsBuiltinMethod extends JsFunction` carrying explicit
    `name` + `length` and a delegate `JsCallable`. Wired through
    `JsDateConstructor` (now/parse/UTC) and `JsDatePrototype` (every
    method, with spec arities). Other prototypes still return raw
    `(JsCallable) this::foo` — see the Tier-4 opportunistic entry.
  - **`int length` lifted onto `JsFunction`** next to the existing
    `String name` field; resolved centrally in `JsFunction.getMember`.
    Every `Js*Constructor` now sets its spec arity in the ctor
    (Date=7, Array=1, Function=1, Map=0, Set=0, etc.); the redundant
    `case "length" -> N` arms fell out. `JsFunctionNode` sets
    `length = argCount` so user functions report `f.length` for free
    (this is where the +3 over the Tier-3 baseline came from).
    `JsBuiltinMethod` shrank to ~5 lines of body — name and length
    moved up to the parent.

  Residual fails: 4 in Date — `toJSON/builtin.js` (needs
  `Object.isExtensible`), `toJSON/{invoke-arguments,invoke-abrupt,
  to-primitive-abrupt}.js` (need accessor-descriptor object literals).
  All blocked on the descriptor-infra item (Tier 2).

- **Date polish.** `built-ins/Date/**` went from **33 pass / 370 fail / 191 skip
  / 594 total** to **367 pass / 11 fail / 216 skip / 594 total** (+334 passing,
  +25 newly skipped via `feature: Symbol.toPrimitive`). The rewrite swapped
  `JsDate`'s `long millis` for a `double timeValue` (NaN sentinel = Invalid
  Date), implemented spec `MakeDay` / `MakeTime` / `MakeDate` / `TimeClip`
  helpers as pure functions on `JsDate`, and rebuilt the prototype around
  spec algorithm ordering. Concretely:
  - `JsDateConstructor` and the prototype setters call `Terms.toPrimitive`
    on object args (valueOf / toString dispatch) and propagate
    `cc.isError()` so a throwing `valueOf` becomes a JS exception.
  - All getters return NaN for Invalid Date; all setters read
    `[[DateValue]]` *before* arg coercion (so a valueOf that mutates the
    date doesn't poison the captured `t`), coerce all args even when `t`
    is NaN (spec ordering / observable side effects), then bail without
    overwriting `[[DateValue]]` when `t` was NaN.
  - Two-arg constructor (`new Date(year, month)`) now actually builds the
    date — was previously falling through to `new Date()` (current time).
    Years 0..99 map to 1900..1999 per spec.
  - `setUTC*` family added (`setUTCDate` / `setUTCMonth` / `setUTCFullYear` /
    `setUTCHours` / `setUTCMinutes` / `setUTCSeconds` / `setUTCMilliseconds`).
    Annex B `getYear` / `setYear` also wired.
  - `Date.prototype.toJSON` now follows the spec's "generic-this" path
    (ToPrimitive(O) → if Number+non-finite return null, else
    Invoke(O, "toISOString")) — works on non-Date receivers too.
  - `requireDate(context)` TypeErrors when `this` is not a JsDate (was
    silently fabricating a fresh `new JsDate()`).
  - String parser handles ES Date Time String Format more completely:
    bare `YYYY` / `YYYY-MM`, extended-year `±YYYYYY-MM-DDTHH:mm:ss.sssZ`,
    and round-trips for our own `toString` / `toUTCString` output.
  - `toString` / `toDateString` / `toUTCString` / `toISOString` /
    `toTimeString` re-implemented manually (not via Java pattern letters)
    so negative years pad as `-0001` (not `0002`) and the timezone slot
    matches the test262 regex `GMT[+-][0-9]{4}`.
  - LocalTZA truncated to integer minutes so historical zones with
    sub-minute offsets (Madras Mean Time +05:21:10 in 1899) round-trip
    correctly through `assertRelativeDateMs(d, expectedMs)` —
    `getTimezoneOffset()` reports an integer-minute value, and the
    local↔UTC conversion now uses the same granularity.
  - Adjuncts (general, not Date-only):
    `Object.prototype.hasOwnProperty` checks `Prototype.hasOwnMember`
    when the receiver is a built-in prototype singleton, and
    `JsObject.hasOwnIntrinsic` when the receiver is a built-in
    constructor — so `Date.prototype.hasOwnProperty('toString')` and
    `Date.hasOwnProperty('UTC')` return true per spec. Affects every
    built-in, not just Date.
    `Object.prototype.toString.call(value)` now dispatches on the
    receiver's host class to produce `[object Date]` /
    `[object Array]` / `[object Map]` / `[object RegExp]` etc.
    `Object.prototype.isPrototypeOf` and `propertyIsEnumerable`
    landed (the latter as an own-property alias since we don't
    track attribute slots).

  Residual fails (each blocked on infra outside Date scope — see
  *Date residuals* in the action list above for the enumerated list).

- **`Map` / `Set` / `String.prototype.matchAll` + per-Engine prototype
  isolation.** Map and Set landed end-to-end:
  - `JsMap` / `JsMapPrototype` / `JsMapConstructor` —
    `set` / `get` / `has` / `delete` / `clear` / `size` / `forEach` /
    `keys` / `values` / `entries`, plus `@@iterator` defaulting to
    `entries`. Construction with an iterable invokes the prototype's
    `set` (so user-overridden `Map.prototype.set` is honored per spec).
  - `JsSet` / `JsSetPrototype` / `JsSetConstructor` — `add` / `has` /
    `delete` / `clear` / `size` / `forEach` / `keys` / `values` /
    `entries` / `@@iterator`; iterable construction invokes the
    prototype's `add`.
  - SameValueZero key semantics: `-0 → +0` normalization, NaN-equal-NaN
    via Java's Double.equals quirk, `1 === 1.0` cross-Java-numeric-type
    matching via linear-scan fallback in `findStoredKey` (storage stays
    `LinkedHashMap` for the common case; the scan only fires on numeric
    keys).
  - `String.prototype.matchAll` returns a spec-shaped iterator object
    over `JsArray` match results (with `index` / `input` / capture
    groups). TypeError on non-global RegExp argument per spec.
  - `forEach` on Map and Set walks the live entry list with a cursor,
    so entries appended during iteration are visited (per spec).
    `Set.prototype.forEach` honors the optional `thisArg` second arg.

  Map slice: 0 → 74 pass / 29 fail / 101 skip / 204 total.
  Set slice: 0 → 150 pass / 85 fail / 148 skip / 383 total.
  String matchAll slice: 1 → 2 pass / 13 fail / 10 skip / 25 total
  (most fails depend on infra outside this session — see Map/Set residuals
  for the same blockers).

  Adjunct: **per-Engine prototype isolation** (see Design invariants).
  Built-in `Prototype` singletons accumulated user-added properties across
  the JVM lifetime, so a test that did `Map.prototype.set = function() {
  throw ... }` poisoned every subsequent test. `Engine` constructor now
  calls `Prototype.clearAllUserProps()` which walks a registered list of
  prototype singletons and clears each one's `userProps`. Profile-mode
  benchmark (Array 20KB / Object 20KB) shows no regression — the clear
  loop is ~10 entries × cleared-`LinkedHashMap`.

  Residual fails (each small or blocked on bigger infra — see Map / Set
  residuals action item above for the enumerated list).

- **Prototype extension + function-decl hoisting + array-like generic
  methods.** Three changes that compound: built-in prototypes accept
  user-added properties (`Array.prototype.foo = ...` per spec —
  configurable: true / writable: true), function declarations hoist to
  the enclosing program/block scope (so `foo.prototype = X` before
  `function foo(){}` survives), and `Array.prototype.*` methods read
  from any array-like `this` via `.length` + indexed snapshot (so
  `Array.prototype.every.call(date, fn)` etc. behave). Plus
  `Function.prototype.bind` and `JsArray.getMember` numeric-index
  routing (needed when a JsArray sits in a prototype chain).
  `Array.prototype.includes` skip retired.
  `built-ins/Array/**`: 648 → 912 pass (+264).
  Full diff: `Prototype.java`, `Interpreter.java`, `JsArray.java`,
  `JsArrayPrototype.java`, `JsFunctionPrototype.java`.

  Residual fails (each small or blocked on bigger infra):
  - **Mutating Array methods on non-array `this`** — `[].push.call(obj, ...)`
    on an arbitrary object operates on the snapshot list and the
    mutation doesn't write back to the source. Spec ToObject + index
    write-back would need property-write semantics on ObjectLike.
  - **`obj` arg passed to `every`/`some`/`reduce` callbacks** — currently
    passes the snapshot list, not the original `this`. Per spec the
    third callback arg should be `O` (the original). Cluster of ~10
    test262 fails. Single-line fix per method.
  - **`for (let x = 0; x < N;) { x++; }`** — pre-existing hang
    independent of this work. Per-iteration `let`-snapshot in
    `evalForStmt` doesn't write the body's mutation of the loop
    variable back to the LOOP_INIT scope, so `x` stays 0 forever.
    Affects ~10 `language/statements/**` tests; revisit when scoping
    a `let`-binding fix.
  - **`compareArray.js` skip kept** — many of its users depend on
    accessor descriptors (`Object.defineProperty` get/set), which
    aren't enforced. Without enforcement, iteration-protocol tests
    that throw via accessor getters loop forever. Un-skip when
    descriptor infra lands.

- **BigInt + numeric separators.** BigInt landed end-to-end: `123n`
  literals (with `_` separators), `BigInteger`-backed runtime, full
  `Terms` arithmetic dispatch (mixing with Number is a TypeError per
  spec), `typeof "bigint"`, `BigInt()` global with `asIntN`/`asUintN`
  (with proper `ToPrimitive` + `ToIndex` coercion),
  `BigInt.prototype.toString(radix)` / `valueOf`, `JSON.stringify`
  TypeError, `Number(1n)` collapse, `Number({valueOf})` ToPrimitive,
  `i++`/`i--` step-1n. BigInt skip and numeric-separator-literal skip
  retired. `BigInt/**` cluster: 35 pass / 9 fail / 33 skip (was 0 pass
  before this session); Number cluster +7, `language/expressions/**`
  +134 (boundary fixes from `narrow()` and ToPrimitive cascade beyond
  just BigInt).

  Residual fails (all individually small or blocked on bigger infra):
  - **`new BigInt.asIntN(...)` should TypeError** (×2) — needs a
    "non-constructible function" mark on `JsCallable`/`JsInvokable` so
    `new` checks before invoking. Useful fix but ripples through the
    call/construct dispatch in `Interpreter`.
  - **`Symbol.toPrimitive` dispatch in `Terms.toPrimitive`** — one test
    (`constructor-coercion.js`) uses `[Symbol.toPrimitive]`. Needs
    minimal Symbol expansion (well-known string-keyed stand-ins exist;
    thread `"@@toPrimitive"` through `toPrimitive`). Small.
  - **`isConstructor(BigInt)` introspection** — `Reflect.construct`
    surface.
  - **`Function` global / `BigInt.__proto__`** — exposing
    `Function.prototype` as a real intrinsic.
  - **`Object(1n)` wrapper-object identity** — `Object(primitive)`
    should return a wrapper that auto-unboxes. Same shape as
    `Object(1)` / `Object(true)` wrapper-object gaps.
  - **`cross-realm`** — multi-realm not modeled. Skip-worthy.

- **`Number` / `Math` adjacency.** Constants, predicates, and ES2015
  Math additions are all in place. Latent `Terms.narrow()` overflow on
  values `< Integer.MIN_VALUE` was fixed (was silently casting
  `(int) d` for any `d <= Integer.MAX_VALUE`, including large
  negatives). No further work expected here unless something concrete
  fails.

---

## Deferred TODOs

Items left for later; un-scheduled but tracked.

- **Unify the read-side dot dispatch in `PropertyAccess`.** The write-side
  (set / compound / postIncDec / preIncDec / delete) is unified through
  `resolveWriteSite` + `AccessSite` (10 near-duplicate dispatch wrappers
  collapsed). The read-side (`getRefDotExpr`, `getCallableRefDotExpr`)
  still has mirrored bridge-fallback + `?.` logic with slightly different
  return shapes (single `Object` vs `Object[]{value, receiver}`). Unifying
  these would need either a shared helper that returns both shapes
  (allocates a wrapper — bad for the hot path) or a callback-style "what to
  do when bridge resolves the full path" parameter. Defer until there's a
  third caller that wants the same resolution shape, or until the read
  paths grow another concern.
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
  printed. Adds debug value; the capture itself is already what would
  prevent `print` output from tests polluting our stdout.
- **Parallel execution.** Prior attempts showed no speedup on moderate
  slices (thread context-switch overhead beat the 1ms per-test eval cost
  on the hot path) and the engine does not poll `Thread.interrupt()`,
  making safe cancellation hard. `HarnessLoader.cache` is already
  `ConcurrentHashMap` so the shared-state work is done; revisit when
  either (a) per-test cost grows enough that 8× parallelism clearly
  wins, or (b) the engine learns to cooperatively abort.
- **Commit `target/test262/results.jsonl` once stable.** Currently
  gitignored (too noisy in git while engine iterates). Re-evaluate when
  Tier 0–2 are ≥95% green — at that point, diff-based regression detection
  becomes the cheapest signal.
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
whatever engine fixes you made, update Open gaps if anything new emerged,
and move on to the next tier. No ceremony.

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
improved. See *fix friction before moving on* in Working principles.

### Bump the pinned test262 SHA

Edit the `TEST262_SHA=...` line at the top of `etc/fetch-test262.sh`, delete
the local `test262/` directory, re-run the script. All subsequent runs use
the new commit. Coordinate bumps with whoever else is iterating — the test
suite itself evolves and can add/remove tests.

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

## Future / explored, deferred

Items that have been studied but deliberately not scheduled. Pick up only
when a real workload demands them, not for spec coverage alone.

### Promises + async/await + setTimeout

Currently all skipped (`feature: Promise`, `feature: async-functions`,
`flag: async`, `feature: Symbol.asyncIterator`, `include: promiseHelper.js`).
karate-js is synchronous today — no event loop, no microtask queue, no
timers.

Goals when this is eventually scheduled — design decisions deferred to
that session:

- **Smooth Java interop.** `CompletableFuture`, callback-style Java APIs,
  and reactive sources should compose naturally with JS `Promise` /
  `async` / `await` at the embedding boundary, in both directions.
- **Graceful degradation when `async`/`await` is decorative.** LLMs reach
  for `async function` and `await expr` even when the underlying
  operation is synchronous. Code shaped that way should just work, not
  reject at parse time or stall.
- **Simple multi-threading for real async I/O.** Make it easier for JS
  code running in karate to work with websockets, Kafka, gRPC, and
  similar streaming/event-driven sources — and to write mocks for async
  servers without each user inventing their own thread plumbing.

Detailed architectural design (microtask model, thread strategy,
`setTimeout` backing, `engine.eval(...)` return semantics, which test262
clusters to retire) belongs in the implementation session, not here.

### Class syntax (ES6)

Currently all skipped (`feature: class`, `feature: class-fields-public`,
`feature: class-fields-private`, `feature: class-methods-private`,
`feature: class-static-fields-public`,
`feature: class-static-methods-private`). karate-js has the prototype
machinery and `new`, but no parser support for
`class`/`extends`/`super`/method-definition syntax — implementing it
would need parser productions for class declarations and expressions
plus an interpreter desugarer to constructor-function +
prototype-assignment form, with `super` resolved through the captured
class context.

Deferred because LLMs writing glue / test code for karate default to
function and prototype style and can be prompted to stay there; class
syntax is a "good to have" rather than load-bearing for the
embed-karate-with-LLM use case. The cluster currently fails primarily
at parse-time with `SyntaxError` ("cannot parse statement"), which is
the right shape — code that uses `class` should fail loudly, not
silently produce wrong behavior. Pick up if a real workload (a karate
user pasting `class`-shaped code, a meaningful test cluster outside the
`feature: class` skips that depends on it) creates demand.

---

## References

- [tc39/test262](https://github.com/tc39/test262) — the suite
- [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
  — authoritative runner spec
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — karate-js engine architecture,
  type system, exception model, and benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

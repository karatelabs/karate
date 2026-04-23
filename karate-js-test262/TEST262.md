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
resolves `config/expectations.yaml` and `test262/` relative to the current
working directory). If you run from the repo root, pass `--expectations`
and `--test262` as absolute paths.

```sh
cd karate-js-test262
./fetch-test262.sh                                # first time only — shallow clone

# 1. Install current karate-js to local Maven repo so the runner picks it up
mvn -f ../pom.xml -pl karate-js -o install -DskipTests

# 2. Run the conformance runner (uses the just-installed karate-js)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/addition/**"

# 3. Generate the HTML report
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.mainClass=io.karatelabs.js.test262.Test262Report
open html/index.html
```

**Why install instead of `-am`:** `exec:java` is a direct goal, not a
phase, so Maven invokes it on *every* selected reactor project. With
`-pl karate-js-test262 -am`, the reactor also includes `karate-parent`,
which has no `mainClass` configured — the goal fails there and aborts
the run before the module is reached. Installing karate-js to the local
repo first, then running without `-am`, sidesteps the reactor entirely.

The `-f ../pom.xml` makes Maven find the parent reactor for `-pl`
resolution while the working directory stays inside the module so the
runner resolves `config/expectations.yaml` and `test262/` correctly.

Typical inner loop: change something in `karate-js/`, re-install (step 1),
re-run (step 2) with the same `--only`, refresh the HTML report, drill
into a failing test via its `Reproducer` button.

---

## Why this exists

karate-js is a lightweight JavaScript engine. Hand-written JUnit tests in
`karate-js/src/test/java/` cover behaviors we care about but tell us nothing
about **spec compliance**. Running tc39/test262 gives us ground truth — and
the module's committed skip list
([`config/expectations.yaml`](config/expectations.yaml)) becomes the
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
- **No noise** — `results.jsonl` is currently gitignored; the committed
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
   - Bad error messages in `results.jsonl` → improve `ErrorUtils` or the
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

6. **Small, focused engine changes.** Prefer several small PRs over one
   sweeping one. The test262 scorecard makes it easy to attribute
   regressions when changes are tight.

---

## Directory layout

```
karate-js-test262/
├── TEST262.md                        # this file (the living document)
├── pom.xml                           # Maven module (deploy explicitly disabled)
├── fetch-test262.sh                  # shallow clone of tc39/test262 at pinned SHA
├── config/
│   └── expectations.yaml             # declarative SKIP list (committed)
├── src/main/java/…/test262/          # runner + report + helpers
├── src/test/java/…/test262/          # unit tests for the harness itself
├── src/main/resources/report/        # HTML/CSS/JS templates for the report
├── test262/                          # [gitignored] the cloned suite
├── results.jsonl                     # [gitignored] per-test pass/fail/skip
├── run-meta.json                     # [gitignored] per-run context
├── html/                             # [gitignored] generated report
└── logs/                             # [gitignored] --single verbose output
```

---

## Running the suite

All commands assume `cwd = karate-js-test262/` (see Quick Start). Use
`-f ../pom.xml` so Maven finds the parent reactor.

**After any change under `karate-js/`, re-install it first** — the runner
uses the karate-js jar from your local Maven repo, not from the reactor
(see Quick Start's "Why install instead of `-am`").

```sh
# After editing karate-js/: refresh the local repo
mvn -f ../pom.xml -pl karate-js -o install -DskipTests

# Full suite (sequential; minutes to tens of minutes)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java

# Narrow to a tier (see Roadmap below)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--only test/language/expressions/**"

# Debug one test end-to-end (prints metadata, source, classification)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single test/language/expressions/addition/S11.6.1_A1.js -vv"

# Resume after a crash — re-uses the existing results.jsonl
#   (caveat: does NOT refresh records for tests that were since removed or
#    re-classified as SKIP. Delete results.jsonl for a clean run.)
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -Dexec.args="--resume"
```

### All flags

| Flag | Default | Purpose |
|---|---|---|
| `--expectations <path>` | `config/expectations.yaml` | skip list manifest |
| `--test262 <path>` | `test262` | suite clone dir |
| `--results <path>` | `results.jsonl` | output JSONL |
| `--run-meta <path>` | `run-meta.json` | output run metadata |
| `--timeout-ms <n>` | `10000` | per-test watchdog (infinite-loop guard) |
| `--only <glob>` | — | restrict to matching paths |
| `--single <path>` | — | run one test, no file writes |
| `-v` / `-vv` | off | (with `--single`) `-v` prints parsed metadata; `-vv` adds full source |
| `--resume` | off | skip tests already in existing `results.jsonl` |

Runs are **silent except failures**. A `FAIL <path> — <type>: <msg>` line is
printed as each failure occurs; a one-line summary ends the run. Generate the
HTML report separately with `-Dexec.mainClass=io.karatelabs.js.test262.Test262Report`.

---

## Results schema

`results.jsonl` — one line per test, sorted alphabetically by path:

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
`config/expectations.yaml` is not run and appears as `{"status":"SKIP",...}`
in results. Everything else is attempted; failures are failures.

Match order: `paths → flags → features → includes`. First match wins. Every
entry requires a `reason`.

**Precedence example.** A test at `test/language/statements/class/foo.js`
with `flags: [module]` and `features: [Symbol]` is skipped with the
*module* reason (the `flags` match fires before `features` is consulted).
If you want `features: [Symbol]` to win, don't have a matching flag rule.

See [`config/expectations.yaml`](config/expectations.yaml) for the starter
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

High-leverage issues that each break many tests at once. Fixing one of these
is worth more than fixing twenty one-off bugs. Grow this list as tiers run;
remove items once fixed.

- **Engine-emitted errors must route through the registered error
  constructors.** ✅ *Addressed.* Engine sites that previously threw plain
  `RuntimeException` (`PropertyAccess` property access on undefined,
  `Interpreter` "is not a function" / "is not defined", `CoreContext`
  TDZ / const-reassign / redeclare) now emit a recognized
  `"<Name>: ..."` prefix. `Interpreter.evalTryStmt` parses the prefix
  into a structured `JsError` (name + linked `.constructor` pointing at
  the registered global), and `Interpreter.evalStatement` sets
  `EngineException.getJsErrorName()` so host callers see the JS error
  name without re-parsing. Consequence: `e instanceof TypeError`,
  `e.name`, and `e.constructor.name` all work for engine-originated
  errors caught in JS. The user-visible throw sites in `PropertyAccess`
  ("cannot get from", "cannot call", "cannot set on", "cannot set 'X'",
  "cannot apply compound/inc/dec", "get by index for non-array",
  "expression: X - Y"), `JsJson` ("no such api on JSON"), `JsJava`
  ("no such api on Java"), and `JavaUtils` ("no instance property")
  now throw `JsErrorException.typeError(...)` directly; the
  `expression: ...` wrappers preserve the cause chain so a nested
  `JsErrorException` classifies via its structured name. Remaining plain
  `RuntimeException` sites (internal-invariant messages like "unexpected
  operator", `JsArrayPrototype`/`JsRegex`/`JsStringPrototype`, and
  `Interpreter.java:1107` "finally block threw error") are low-traffic
  and convert the same way as needed.
- **`eval` as a global.** ✅ *Addressed.* `eval` is now registered in
  `ContextRoot.initGlobal` with indirect-eval semantics (parses and
  evaluates in the engine root scope; non-string arguments pass through
  unchanged per spec). Direct-eval scope capture is still out of scope.
- **`typeof` on all callable surfaces.** ✅ *Addressed.* `Terms.typeOf`
  now returns `"function"` for any `JsInvokable`, for `JsFunction`, for
  built-in constructor singletons that extend `JsObject` rather than
  `JsFunction` (the `Boolean`, `RegExp`, `Error`/`TypeError`/etc. globals)
  via a `JsObject.isJsFunction()` override, **and** for prototype method
  refs cast as `(JsCallable) this::method` (e.g. `[1].map`, `'x'.charAt`,
  `({}).hasOwnProperty`) via a `JsCallable && !(value instanceof
  ObjectLike)` branch. The `ObjectLike` guard keeps `JsObject` / `JsArray`
  (which both implement `JsCallable` directly) reporting `"object"`.
- **Engine-position prefix hides assertion messages (framing gap).** ✅
  *Addressed.* `Node.toStringError` now emits the user message first and
  a JS-stack-frame-style `    at <path>:<line>:<col>` suffix instead of
  leading with the engine-internal `<line>:<col> <NodeType>` token.
  ~1500 Array-slice failures that previously showed `"13:1 STATEMENT"`
  now surface the actual assertion text.
- **`Test262Error` / user-defined error classes classified as Unknown.**
  ✅ *Addressed.* `Interpreter.evalProgram` falls back to `constructor.name`
  when the thrown JsObject has no `.name` property set on its prototype,
  populating `EngineException.jsErrorName` so the runner's `ErrorUtils`
  surfaces a meaningful type. Also fixed a related function-name-inference
  bug in `CoreContext.declare` where passing a named function as a
  parameter was permanently renaming it globally (`x.name` returning the
  parameter identifier); guarded the inference to only fire when the
  function's name is currently empty. Net effect on the Array slice:
  `Unknown` fail bucket went from ~1480 → 136; 1325 now classify as
  `Test262Error` (assertion failures).
- **`ErrorUtils.classify` missed embedded error names in wrapper messages.**
  ✅ *Addressed.* Messages like
  `"expression: $262.createRealm().global - TypeError: cannot read ..."`
  where the error type is a substring (not a prefix) now classify
  correctly. `ErrorUtils.classifyByMessagePrefix` falls back to scanning
  for `<Name>:` preceded by a non-word character after the prefix check
  fails. Paired with `PropertyAccess`'s `expression: ... - ...` wrappers
  now preserving the cause chain so the structured `JsErrorException`
  name propagates through `findJsErrorException`, these are classified
  by their underlying structured name first; the embedded-name scan is
  a safety net for any wrapper that loses the cause.
- **Raw Java exception names leak through `EngineException.getMessage()`.**
  ✅ *Addressed.* `Interpreter.evalStatement`'s catch block now maps
  common JVM exceptions to JS error constructor names via
  `classifyJavaException`: `IndexOutOfBoundsException` /
  `ArithmeticException` → `RangeError`; `NullPointerException` /
  `ClassCastException` / `NumberFormatException` → `TypeError`. The
  mapped name is both stamped on `EngineException.jsErrorName` and
  prefixed to the message body so runner-side and JS-side consumers
  agree. 13 `"Index -N out of bounds"` failures in the Array slice are
  now classified as `RangeError` instead of `Unknown`.
- **`cannot read properties of undefined (reading 'name')`.** ✅ *Addressed.*
  Root cause turned out not to be a `.name` read in engine code but the
  test262 harness's `assert.throws(Ctor, fn)` reading `thrown.constructor.name`
  on engine-generated `JsError` instances whose `.constructor` field was
  never populated. Fixed at the JS try/catch wrapping site
  (`Interpreter.evalTryStmt`) by resolving the registered global for the
  error's `.name` and wiring it into the new `JsError.constructor` (new
  package-private setter). Const-slice result: 34 → 45 PASS.
- **Directive prologue (`"use strict"`) is a statement-level string
  literal that turns on strict mode.** test262 wraps tons of tests in
  `"use strict"; ...` — in lenient parsers the string is silently
  tolerated but strict-only assertions (`with` statement = SyntaxError,
  duplicate params = SyntaxError, assignment to `eval`/`arguments` =
  SyntaxError, octal literals = SyntaxError) all fail their
  negative-test expectation. Either implement the parser-side strict
  mode flip or explicitly skip `flags: [onlyStrict]` tests via
  `expectations.yaml` with a reason (currently they fail as "unexpected
  pass"). Cheapest win: skip, document, revisit.
- **Harness-helper dependencies we currently skip.** `propertyHelper.js`,
  `compareArray.js`, `testTypedArray.js`, etc. depend on engine internals
  karate-js does not expose (descriptor introspection via
  `Object.getOwnPropertyDescriptor`, proper iterator protocol, etc.).
  These gate **thousands** of `test/built-ins/**` tests currently SKIP'd
  via `expectations.yaml`. Once Tier 2's built-ins work, un-skipping these
  helpers one at a time is the next lever.
- **Engine framing noise in error messages** (per Working Principle #3).
  `EngineException` wraps runtime errors in a multi-line `js failed: /
  ========== / Code: / Error: ...` frame. The test262 runner strips this
  in `ErrorUtils.unwrapFraming`, but JS-side fixtures that inspect
  `.message` via `assert.throws` see the framed text — so the classifier
  workaround doesn't fix the real problem. Reserve the frame for
  host-side logging; expose the raw JS message on the exception directly.

### Recommended next-session ordering

Classification and framing work is done. `Object` built-ins had a big
round — `Object.hasOwn`, `getOwnPropertyNames`,
`getOwnPropertyDescriptor(s)`, `defineProperty`, `defineProperties`,
and `Object.create(proto, descriptors)` now work (298 → 550 PASS in
`test/built-ins/Object/**`). Object-literal parser gaps also closed:
shorthand methods (`{foo() {}}`), computed keys (`{[k]: v}`), and
getters/setters (`{get x() {}, set x(v) {}}`).

**Pick up here** — concrete levers with measured impact, ordered by
leverage-per-hour. Numbers are from probe runs in the current HEAD.

1. **Template literals — highest concentration of contained fails.**
   `test/language/expressions/template-literal/**` → 18 PASS /
   **38 FAIL** / 1 SKIP. Three sub-buckets:
   - **14 × `cannot parse statement`** — tagged templates
     (`` tag`hello ${x}` ``) and complex interpolation patterns are
     not parsed. Common in React/styled-components/SQL libraries.
   - **15 × `expected negative SyntaxError but got: This statement
     should not be evaluated`** — the parser accepts escape sequences
     that the spec rejects (malformed hex / unicode escapes inside
     templates). Reject these at lex-time in `JsLexer`'s template
     scanner.
   - **Object-to-string coercion in templates**: `${obj}` emits
     `io.karatelabs.js.JsObject@78292ffe` instead of `[object Object]`.
     Route template interpolation through `Terms.TO_STRING` (which
     already handles ObjectLike correctly) — likely a one-line fix in
     `evalLitTemplate`.

2. **Destructuring assignment as an expression** —
   `test/language/expressions/assignment/dstr/**` → 82 PASS /
   **148 FAIL** / 138 SKIP. Dominant pattern: **45 × `expected:
   [EXPR]`** on `[a, b] = arr` or `({a, b} = obj)`. The parser sees
   `[a, b]` as an array literal, not as a destructuring target, so
   `= arr` on the RHS fails. Declarations (`var [a, b] = arr`) already
   work — the gap is pure assignment expressions. Touch the assignment
   parser to accept LIT_ARRAY / LIT_OBJECT on the LHS, then let the
   interpreter reuse the existing bindScope-free destructuring path.

3. **`var`/`const` destructuring edge cases** —
   `test/language/statements/variable/dstr/**` → 23 PASS / 48 FAIL /
   26 SKIP (and same shape in `const/dstr/`). Mostly **`ReferenceError:
   x is not defined`** (11×) after a successful destructuring — so the
   parser accepts the pattern but bindings never make it into scope
   for some shapes. Likely nested-pattern or default-value corners.
   Worth fixing once (2) is done.

4. **Array built-ins, deferred.** The big Array cliff remains mostly
   architectural — property-descriptor attribute tracking, proper
   iterator protocol (`Symbol.iterator` is skip-listed), sparse-array
   dictionary mode, TypedArray/species. Core `Array.prototype.*`
   already works for idiomatic code. Skip this in favor of higher
   leverage items above.

5. **Object-slice polish, also deferred.** The remaining ~1855 Object
   FAILs concentrate in tests that need writable/enumerable/configurable
   attribute semantics (~217 `Expected a TypeError to be thrown`,
   ~100 `accessed !== true` enumerable checks). Low LLM-code leverage
   vs the engine-churn cost of descriptor tracking.

Remaining Array-slice Unknowns from earlier (~9 tests, all
low-priority): 6 × `"null"` NPE path, 2 × `IllegalName` JDK lambda
leak, 1 × `Java heap space` OOM.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `expectations file not found: config/expectations.yaml` | You ran from the wrong directory. `cd karate-js-test262` first (see Quick Start). |
| `test262 directory not found: test262` | You haven't run `./fetch-test262.sh` yet (or you're in the wrong dir). |
| `Failed to execute goal ... exec-maven-plugin ... on project karate-parent: The parameters 'mainClass' ... are missing or invalid` | You used `-am` with `exec:java`. Don't — install `karate-js` separately (see Quick Start) and run without `-am`. |
| Engine change seems to have no effect on test262 output | You forgot `mvn ... -pl karate-js -o install -DskipTests`. The runner uses the karate-js jar from your local Maven repo, not the reactor classpath. |
| HTML dashboard shows empty header | `run-meta.json` missing — run `Test262Report` *after* `Test262Runner` in the same directory. |
| `ReferenceError: <name> is not defined` on common classes like `ReferenceError`/`RangeError` | Known first-order gap — those constructors are not registered globals yet. |
| Suite seems to hang on one test | Infinite loop; watchdog should kick in at `--timeout-ms`. If it doesn't, bisect with `--only`. |
| Tests that used to pass now fail after an engine change | Run `EngineBenchmark` too — perf regression sometimes manifests as timeouts before correctness. |
| `--resume` gives stale results | Known limitation — it doesn't refresh records for tests that were removed or re-SKIP'd. Delete `results.jsonl` for a clean baseline. |

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
- **Parallel test execution.** Runner is sequential. Each test gets a
  fresh `Engine`, so per-test isolation holds; switch
  `ExecutorService` to a thread pool once the classifier is stable enough
  that non-deterministic ordering in logs is acceptable.
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
- **Thread-safe `HarnessLoader` cache.** `HashMap` is fine for
  sequential runs; switch to `ConcurrentHashMap` before enabling parallel
  execution.
- **Commit `results.jsonl` once stable.** Currently gitignored (too noisy
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

Edit `config/expectations.yaml`, add under the right section, always with a
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

Edit the `TEST262_SHA=...` line at the top of `fetch-test262.sh`, delete
the local `test262/` directory, re-run the script. All subsequent runs use
the new commit. Coordinate bumps with whoever else is iterating — the test
suite itself evolves and can add/remove tests.

---

## CI

A `workflow_dispatch`-only workflow at
[`.github/workflows/test262.yml`](../.github/workflows/test262.yml) runs
`fetch-test262.sh` + the runner + the report, and uploads both the HTML
tree and `results.jsonl` as artifacts. It is never triggered automatically
— you kick it off from the Actions tab when you want a fresh run.

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

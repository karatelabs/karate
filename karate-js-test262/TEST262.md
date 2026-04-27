# karate-js-test262

ECMAScript [test262](https://github.com/tc39/test262) conformance harness for
karate-js. Reproducible pass/fail matrix across the ES surface area, declarative
skip list ([`etc/expectations.yaml`](etc/expectations.yaml)), and the roadmap
for what to tackle next. **Not** published to Maven Central.

The bar is *can karate-js run real-world JavaScript written in the wild,
especially by LLMs?* test262 is the scorecard; pragmatic ES6 coverage of
idiomatic code is the goal — not spec-lawyer compliance for its own sake.

> **See also:**
> [../karate-js/README.md](../karate-js/README.md) — what karate-js is ·
> [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, slot
> family, prototype machinery, **spec invariants**, benchmarks. **Design
> reference for every TODO below.** ·
> [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design ·
> [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
> — authoritative test-runner spec.

This file is the **roadmap**. For *why* a TODO exists or *how* a subsystem is
shaped, follow the JS_ENGINE.md anchors below.

---

## Working principles

Operating-mode maxims for the test262 conformance loop. Treat as load-bearing.

1. **Real-world JS first; test262 is the scorecard, spec is ground truth.** A
   fix that unblocks 500 idiomatic tests beats one that tightens a rare spec
   corner. Existing JUnit tests can be wrong: when the spec disagrees, the
   spec wins — fix the test along with the engine.

2. **Errors must look like JavaScript, not Java.** A raw
   `IndexOutOfBoundsException` or `at io.karatelabs.js.Interpreter.eval(...)`
   frame escaping `Engine.eval(...)` is a correctness bug, not cosmetic
   noise. See [JS_ENGINE.md § Exception Handling](../docs/JS_ENGINE.md#exception-handling)
   and [§ Error routing & shape](../docs/JS_ENGINE.md#error-routing--shape).

3. **Fix friction before moving on.** Bad error messages in `results.jsonl`,
   parse-vs-runtime classification gaps, missing report fields, `--single -vv`
   not showing what you need — stop and fix the tooling rather than working
   around it.

4. **Protect the hot path — pay edge-case cost on the edge case.** Sentinels
   over thrown signals, type-check rare cases after the common-case miss,
   parse-time analysis over inner-loop checks. After any non-trivial engine
   change, run `EngineBenchmark profile` and compare against
   [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).

5. **Code should be DRY and aligned with the JS spec.** Near-duplicate
   dispatch and wrong-layer workarounds are clues that the layer below is
   wrong; collapse to a single spec-shaped seam. Fix it inline or file a
   [Deferred TODO](#deferred-todos) with concrete pointers (file, method,
   what the unification looks like) — vague "this could be cleaner" notes
   are worthless.

6. **Batched commits are fine if the message enumerates the changes.** What
   matters is that the commit message lets a future bisect attribute
   regressions.

---

## Per-session ritual

Each session that touches the engine should:

1. **Re-probe the slice baseline** with `--only` before scoping. Old slice
   numbers go stale fast — record fresh before/after pass counts in the
   commit message and pin the run-dir.
2. **Unit tests:** `mvn -f pom.xml -pl karate-js -o test` →
   `Tests run: 981+, Failures: 0, Errors: 0, Skipped: 2` (count grows as
   `SpecPinTest` accretes invariants).
3. **test262 built-ins probe:** diff `results.jsonl` against the previous
   run. **Zero regressions (PASS → FAIL).** Document any flip in the commit
   message.
4. **EngineBenchmark profile:** within ±10% of the
   [JS_ENGINE.md reference](../docs/JS_ENGINE.md#performance-benchmarks);
   ±5% on hot-path refactors. If unavoidable (correctness > speed), update
   the reference table in the same commit.
5. **karate-core consumer check:**
   ```sh
   mvn -f pom.xml -pl karate-js -o install -DskipTests
   mvn -f pom.xml -o test -pl karate-core
   ```
   Expect `Tests run: 1969, Failures: 0, Errors: 0, Skipped: 1`.
6. **Update this file's TODOs in the same commit.** This is a roadmap,
   not a changelog. For each item the commit addressed (active priority
   bullet, background sweep, deferred TODO, or implicit assumption a bug
   broke), strike or rewrite it here. If the work surfaced a new
   architectural invariant — a contract that future code must respect —
   push the *why* into [JS_ENGINE.md](../docs/JS_ENGINE.md) under the
   relevant spec-invariant anchor, then leave a one-line pointer here
   from any TODO that still depends on it. Yesterday's done work doesn't
   belong in this file; the commit log is the audit trail.

---

## Active priorities

Slice order, picked for **feedback-loop speed** + **foundations first**.
Re-probe each session before scoping. Symbol is **last**, not first —
Math/Number/Date/String are independent of Symbol.

| # | Slice | What's left |
|---|---|---|
| 1 | `test/built-ins/Number/**` | `Object.prototype.toString.call(num)` `[object Number]` (Symbol-gated, slice #7), `MAX_VALUE` / `MIN_VALUE` literal-form parser edge. See [JS_ENGINE.md § Numeric / coercion](../docs/JS_ENGINE.md#numeric--coercion). |
| 2 | `test/built-ins/Date/**` | `Date.parse` ISO format edges, UTC vs local hour math, invalid-date propagation. See [JS_ENGINE.md § Date](../docs/JS_ENGINE.md#date). |
| 3 | `test/built-ins/String/**` | `substring` / `lastIndexOf` / `charAt` edge cases tied to ToInteger spec corners, parser-blocked tests, Symbol-gated tail. Spec preamble pattern + JS-spec whitespace + `replace` substitution doc'd in [JS_ENGINE.md § Spec preamble at built-in entry points](../docs/JS_ENGINE.md#spec-preamble-at-built-in-entry-points). |
| 4 | `test/built-ins/RegExp/**` | `Symbol.{match,replace,search,split,matchAll}` (slice #7), `RegExp.escape` (ES2025), parser `R_PAREN` / Unicode-escape edges, `cross-realm` tests (multi-realm not modeled). See [JS_ENGINE.md § Built-in accessor descriptors on prototypes](../docs/JS_ENGINE.md#built-in-accessor-descriptors-on-prototypes) and [§ JsRegex.replace](../docs/JS_ENGINE.md#jsregexreplace--js-substitution-template). |
| 5 | `test/built-ins/Object/**` | `defineProperty` length-descriptor / array-length cluster (~30 fails), `seal` (TypedArray-feature-gated), Symbol-gated tail. See [JS_ENGINE.md § Property attributes](../docs/JS_ENGINE.md#property-attributes) and [§ Own-key ordering](../docs/JS_ENGINE.md#own-key-ordering). |
| 6 | `test/built-ins/Array/**` | `splice` / `concat` `Symbol.species` residuals (slice #7), parser-blocked async / generator paths, harness-feature-gated (Int8Array). See [JS_ENGINE.md § Prototype machinery](../docs/JS_ENGINE.md#prototype-machinery). |
| 7 | `test/built-ins/Symbol/**` + cascades | Full Symbol primitive: `typeof === "symbol"`, unique identity, `Symbol.for` / `keyFor` / `description`, `Object.getOwnPropertySymbols`, `Reflect.ownKeys`. Touches `Terms.typeOf` / `eq` / coercion; `PropertyKey` abstraction across `JsObject.props` / `isOwnProperty`. 2–4 sessions. Unblocks the Array / String / RegExp Symbol-gated tail. |

### Background sweeps

Picked off opportunistically when nearby — not session-sized on their own.

- **String iterator splits surrogate pairs.** `IterUtils.stringIterator`
  walks `charAt(i)` so `'🥰💩'` yields four iterations instead of two
  code-points. Spec §22.1.5.1 calls for code-point iteration. Affects
  test262 `Object/groupBy/string.js`, `Map/groupBy/string.js`, and any
  spec that consumes a string via `for-of`. Fix: walk `codePointAt` /
  `charCount` in the iterator. ~1 h.

- **`Array.prototype.values()` returns raw `List` instead of a spec
  iterator object.** `IterUtils.iteratorFromCallable` was made lenient
  (falls back to `getIterator`) to absorb the mismatch for set-algebra
  iteration of `[v].values()`-style set-likes; the underlying gap is
  that `Array.prototype.values` should return a wrapped iterator with
  `next` / `@@iterator` so direct `arr.values().next()` works. Use
  `IterUtils.toIteratorObject(listIterator(...))`. ~30 min.

- **Block-scope `const` redeclaration across sibling `{ ... }` blocks.**
  test262 `Set/prototype/{union,intersection,difference,
  symmetricDifference}/result-order.js` declares `const s1` inside each
  of four sibling blocks; the second block reports
  `s1.union(s2) is not defined` (the AST node text leaks into the
  reference-error message), suggesting block scoping for `const` does
  not isolate the declarations cleanly. Investigate parser/scope
  handling for `LET` / `CONST` inside `BLOCK` nodes.

- **`.length` / `.name` rollout to remaining prototypes.** `JsBuiltinMethod`
  infra in place; most residual `name.js` fails are Symbol-gated.

- **Destructuring residuals.** Lexer identifier-escape support, TDZ /
  init-order corners, negative parse tests needing pattern-vs-literal
  two-mode parsing.

- **Cleanup residuals.** Occasional `"null"` NPE paths, `IllegalName` JDK
  lambda leak, `Java heap space` OOM in array-slice paths.

---

## Deferred TODOs

Tracked but un-scheduled. Three flavors: feature gaps that are intentional
non-goals today, engine cleanup needing a dedicated session, and
harness-quality fixes. **For design context behind each, see
[JS_ENGINE.md](../docs/JS_ENGINE.md)** — anchors inline below.

### Engine — feature gaps

- **Strict mode plumbing.** Parse `"use strict"` directive prologue,
  thread `strictMode` flag via `CoreContext`, flip ~7 lenient sites
  (frozen-write / writable=false / read-only / set-only-accessor /
  non-extensible-add / non-configurable-delete) through one
  `failSilentOrThrow` helper. `AccessorSlot.write` already accepts a
  `strict` arg. ~3–4 h. **Risk:** parser changes may regress
  `flags: [noStrict]` test262 paths if directive parsing is over-eager.

- **Promises + async / await + setTimeout.** Skipped (`feature: Promise`,
  `async-functions`, `Symbol.asyncIterator`, `include: promiseHelper.js`).
  karate-js is synchronous. Viable path: synchronous subset first —
  `Promise` as eagerly-resolving thenable, `async function` runs
  synchronously, `await` synchronously unwraps. Escalate to a microtask
  runtime only when a real workload needs timer-driven scheduling.

- **Class syntax (ES6).** Skipped (`feature: class` and friends). Engine
  has prototype machinery + `new` but no parser support for `class` /
  `extends` / `super` / method-definition. Parse fails loudly with
  `SyntaxError` — the right shape until real workload demands it.

- **Symbol primitive.** Slice #7 above. Tracked here as a feature gap
  because it gates a long tail of fails across String / Array / RegExp /
  Object that depend on `@@iterator` / `Symbol.species` / `Symbol.toPrimitive`.

### Engine — cleanup

Items needing a dedicated session — benchmark-gated or coordinated with
other work. Design context in
[JS_ENGINE.md § Spec Invariants](../docs/JS_ENGINE.md#spec-invariants-test262-driven)
and the per-section anchors.

- **`Prototype.toMap()` rebuilds on every call.** Allocates fresh
  `LinkedHashMap` per invocation; iteration paths re-pay the cost.
  Memoize on slot-map modification stamp or expose non-materializing
  iterator. ~1 h. Defer until benchmark shows it matters.

- **`HOLE` → tombstone full elimination.** Centralization fallback
  landed; full elimination needs sparse-array storage rework (dense
  `list` only set values; sparse positions consult `props` with
  tombstoned `DataSlot` entries). Pair with parser `in` support.
  Pinned in `SpecPinTest`. ~6–8 h.

- **`PropertyKey` abstraction.** Symbol prep. Defer to slice #7 itself —
  introducing `PropertyKey` ahead of a concrete consumer is YAGNI.

- **Arguments → spec exotic Arguments object.** Now a `JsArray` (cached
  per-call frame) — supports `arguments[i]` / `.length` / iteration /
  property writes. Not yet: `arguments.callee` (deprecated, strict-mode
  TypeError), dynamic alias to formal parameters in non-strict mode
  (`function f(x){ arguments[0]=2; return x }` → `2`),
  `Object.prototype.toString.call(arguments) === "[object Arguments]"`.
  Subclass `JsArguments extends JsArray` with the alias map +
  `Symbol.toStringTag` override when a real workload demands.

- **`CreateDataPropertyOrThrow` + `ArraySpeciesCreate` proper.** Array
  result-allocation methods (`slice` / `concat` / `splice` / `map` /
  `filter` / `flat` / `flatMap`) currently `new JsArray(new ArrayList<>())`
  then populate via `.add()`. Spec shape uses `ArraySpeciesCreate(O, len)`
  (depends on `Symbol.species` — slice #7) and
  `CreateDataPropertyOrThrow(A, k, v)` per element. Defer until Symbol
  lands; current shape passes ~all non-Symbol-gated tests.

### Engine — spec alignment

Cases where current behavior is observably non-spec but not yet a slice
priority. Pick up when the relevant slice surfaces them.

- **`JsArray.handleLengthAssign` return value dropped on direct
  assignment.** Returns `boolean` for "throw TypeError on writable=false /
  partial-truncate"; only `defineLength` consumes it. Direct
  `arr.length = X` silently no-ops on writable=false. Spec wants
  TypeError under strict. Pair with the strict-mode plumbing TODO above.

- **`ToObject` coercion for non-empty string descriptor sources.**
  Non-empty strings short-circuit to TypeError matching the spec
  end-state but skip the wrapper-iterate-each-char pipeline. Become
  spec-shaped when adjacent.

- **`JsArray.jsEntries` vs `[[OwnPropertyKeys]]` semantics asymmetry.**
  `jsEntries` yields indices only (correct for `Array.prototype.*`).
  For-in / `Object.keys` / `defineProperties` want indices PLUS named
  non-index props. `JsObjectConstructor` works around it via its own
  `ownKeys` helper. Cleaner long-term: split into `arrayEntries(ctx)`
  vs `ownEntries(ctx)`. ~1 h once a fourth caller surfaces the bug.

- **`ToPropertyKey` for ObjectLike — remaining no-ctx callers.**
  `defineProperty` now passes ctx through and `toPropertyKey(o, ctx)`
  routes ObjectLike receivers through spec ToPrimitive(string) →
  ToString (throws TypeError when neither toString nor valueOf yields
  a primitive). Still on the no-ctx `toPropertyKey(o)` path:
  `JsObjectConstructor.hasOwn` and `getOwnPropertyDescriptor` (both
  wired as `JsInvokable`, no Context arg). Migrate when a real workload
  passes non-string keys to either; switching the wiring to
  `JsCallable` is the mechanical part. See
  [JS_ENGINE.md § Property attributes](../docs/JS_ENGINE.md#property-attributes).

- **Integer-index accessors beyond `JsArray.list.size()`.**
  `JsArray.jsEntries` Phase 1 walks `0..list.size()`, Phase 2 yields
  non-index named props in insertion order. An accessor installed at
  e.g. index 100 on an empty array (via `defineProperty(arr, "100",
  {get, enumerable:true})`) is missed by both — Phase 1's bound, Phase 2's
  `parseIndex >= 0` skip. Spec §9.4.2 [[OwnPropertyKeys]] says
  ALL integer-index keys ascend first. Fix: track integer-index entries
  in `namedProps` and merge into Phase 1's ascending walk (or extend
  Phase 1's bound to include the max integer-index key in `namedProps`).
  Surfaces in residual `Object/defineProperties` and `Object/defineProperty`
  array-index fails.

- **`JsGlobalThis` two-store reads.** Data descriptors live on
  `BindingsStore`, accessor descriptors on `JsObject.props` (because
  `BindingSlot` is data-only). `getMember` / `isOwnProperty` / `toMap`
  consult both, but the split is a smell. Either extend `BindingSlot`
  to carry an `AccessorSlot` side-table, or commit to a unified
  two-store contract with a single authoritative read seam. ~2 h. See
  [JS_ENGINE.md § Globals](../docs/JS_ENGINE.md#globals).

- **`Symbol.toPrimitive` is not dispatched.** Matches our minimal
  Symbol surface. Fix as part of slice #7.

- **`(0, fn)()` indirect call this-binding.**
  `(1, Object.prototype.valueOf)()` should pass undefined as `this`
  (per spec the comma operator drops the reference base), invoking the
  spec preamble's TypeError. Today the receiver still falls through to
  globalThis. Audit `evalCallExpr` for the parenthesized comma case.

### Harness quality

- **Replace hand-rolled YAML parser with SnakeYAML.** `Expectations.java` /
  `Test262Metadata.java` break on `#` in quoted reasons, block-scalar
  `description:` fields, and block-form list values. Swap when next
  touched.

- **`--resume` refreshes stale records.** Currently echoes records for
  tests that no longer exist or are now SKIP'd. Gate on test-still-exists
  + not-in-skip-list, or rename to `--resume-crash-only`.

- **Cache parsed harness ASTs, not source text.** `HarnessLoader`
  re-parses `assert.js`, `sta.js`, and per-test `includes:` on every
  test; ~50k re-parses per full-suite run.

- **Surface per-test console capture in `ResultRecord`.** `evaluate(...)`
  already wires `Engine.setOnConsoleLog(...)` to a per-test sink
  (discarded); plumb into `ResultRecord` for `--single -vv` and the
  HTML drill-down.

- **`phase: resolution` (module-resolution) negative tests.** Conflated
  with `runtime` today. Modules globally skipped, so latent.

- **Structured `$262` surface.** `AbstractModuleSource`, `IsHTMLDDA`,
  `agent.broadcast/getReport/sleep/monotonicNow` absent; all
  feature-gated, unreachable today. Add stubs when a feature comes off
  the skip list.

- **Parallel execution.** Prior attempts showed no speedup (thread
  context-switch beat ~1 ms per-test cost) and the engine doesn't poll
  `Thread.interrupt()`. Revisit when per-test cost grows or the engine
  learns cooperative abort.

- **`readHeadSha` path fragility.** `Test262Runner.readHeadSha` walks up
  `cli.test262.getParent().getParent()`. Prefer `git rev-parse HEAD`
  or `--karate-sha`.

- **Commit `target/test262/results.jsonl` once stable.** Gitignored
  today; re-evaluate when engine churn slows.

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

- **`<run-dir>/results.jsonl.partial`** — appended per test as results
  arrive, flushed per write. **Run order, not sorted.** Deleted on clean
  exit; preserved on abort (`--max-duration` hit, Ctrl-C, JVM kill).
- **`<run-dir>/results.jsonl`** — canonical output, **sorted alphabetically
  by path**, atomically written at end-of-run (tmp + rename). This is what
  tooling reads and what `--resume` skips-seen against.

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

### Diff two run-dirs (regression check)

```sh
python3 -c "
import json, sys
def state(p): return {json.loads(l)['path']: json.loads(l)['status'] for l in open(p)}
prev = state(sys.argv[1])
curr = state(sys.argv[2])
regressed = sorted(p for p in prev if prev[p]=='PASS' and curr.get(p)=='FAIL')
new_pass  = sorted(p for p in prev if prev[p]=='FAIL' and curr.get(p)=='PASS')
print(f'Regressed (PASS->FAIL): {len(regressed)}')
for p in regressed: print(f'  {p}')
print(f'New PASS (FAIL->PASS): {len(new_pass)}')
for p in new_pass: print(f'  {p}')
" target/test262/run-<prev>/results.jsonl target/test262/run-<curr>/results.jsonl
```

This is the per-session safety check against the slice baseline.

### Check performance after an engine change

The conformance suite allocates a fresh `Engine` per test (~50k tests); small
regressions compound into minutes of wall time. **Prefer profile mode** — the
30 s warm loop is JIT-stable and directly comparable to the
[reference table in JS_ENGINE.md](../docs/JS_ENGINE.md#performance-benchmarks).

```sh
mvn -pl karate-js -q test-compile

# Profile mode (30 s warm loop; JIT-stable, ~16k iterations averaged).
java -cp "karate-js/target/classes:karate-js/target/test-classes:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1):$(find ~/.m2/repository -name 'json-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'accessors-smart-*.jar' | head -1):$(find ~/.m2/repository -name 'asm-9*.jar' | grep -v asm-tree | grep -v asm-commons | head -1)" \
    io.karatelabs.parser.EngineBenchmark profile

# Fast mode (median of 10 cold runs) — noisy, gut-check only
java -cp "…same classpath…" io.karatelabs.parser.EngineBenchmark
```

If averages move >±10%, understand why before merging. If unavoidable
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
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, slot family, prototype machinery, **spec invariants**, benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

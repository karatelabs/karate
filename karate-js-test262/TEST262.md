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

7. **Aggregate, don't dump — context is precious.** A full run is ~53k
   JSONL rows. Treat run output as files to query, not streams to tail.
   Full rules in [Context discipline](#context-discipline).

8. **Playbook hygiene is the work, not a chore.** Stale counts, "past
   wins" narration, log patterns that flood context, JSONL the queries
   can't parse — fix the rot inline in the session that surfaced it.
   Fix it at the writer, not in a workaround. A playbook future
   sessions can trust is worth more than a museum piece.

---

## Per-session ritual

Each session that touches the engine should:

1. **Re-probe the slice baseline** with `--only` before scoping. Old slice
   numbers go stale fast — record fresh before/after pass counts in the
   commit message and pin the run-dir. If `target/test262/` has no
   `run-*` dirs yet (clean clone, or after `mvn clean`), your first
   `--only` invocation *is* the baseline; pin its run-dir in the commit
   so the next session has a diff target.
2. **Unit tests:** `mvn -f pom.xml -pl karate-js -o test` →
   `Tests run: 1086+, Failures: 0, Errors: 0, Skipped: 2` (count grows as
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
   Expect `Tests run: 2224+, Failures: 0, Errors: 0, Skipped: 3`.
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

## Context discipline

A full conformance run is ~53k JSONL rows. A slice (`test/language/**`)
emits one `FAIL <path> — <type>: <msg>` line per failure on stdout plus a
growing `results.jsonl.partial`. Per-test `-vv` dumps full source. Pulling
any of this raw into your context burns the budget you need for the
actual engineering work. **Treat run output as files to be queried, not
streams to be tailed.**

**Rules:**

1. **Never `tail -f` or `cat` a full `progress.log` / `results.jsonl`.**
   For liveness, `tail -n 1 <progress.log>` returns the last heartbeat
   (`processed N pass M fail K skip L @ rate`) — that's tests-done
   authoritatively in either mode. (`wc -l <partial>` counts only
   FAIL+SKIP in dev mode, so don't use it for total-processed.) For
   slicing use the [Failure triage](#failure-triage) `jq` one-liners.

2. **Default `--single` to `-v`, not `-vv`.** `-v` prints metadata +
   classification + the engine's `location: <path>:<line>:<col>` —
   usually enough to find the call site. Escalate to `-vv` (full test
   source) only after `-v` fails to localize the cause.

3. **Cap diff output.** When comparing two run-dirs, emit counts +
   top-N representative paths + per-slice cluster breakdown. Never the
   full regressed / new-pass lists. The
   [Diff two run-dirs](#diff-two-run-dirs) recipe is already capped —
   use it as written.

4. **Delegate slice runs to a sub-agent with a strict return contract.**
   Spawn a `general-purpose` agent (it has Bash) and require a ≤200-word
   digest: pass/fail/skip counts, top 3 failure clusters with one
   example each, anything surprising. The agent reads the full output;
   you receive the digest. See [Delegate a slice run](#delegate-a-slice-run)
   for the exact prompt template.

5. **Prefer reading engine source over reading log streams.** A FAIL
   line tells you *what* threw; the engine source tells you *why*.
   Once you have one representative failing path and the call site
   from `--single -v`, close the JSONL and work from the code — the
   slice re-run to confirm the fix is a single `etc/run.sh --only`
   away (delegate it).

6. **Mvn output is verbose — pipe to `tail -n 30`.** Unit tests,
   benchmark, and karate-core consumer check from the
   [per-session ritual](#per-session-ritual) all dump compile noise
   before the summary. `mvn ... -o test 2>&1 | tail -n 30` is enough
   to see `Tests run: ...` and any failures. Use `-q` to suppress
   compile chatter when you don't need it.

7. **`etc/expectations.yaml` is 175 lines** — fine to `Read` whole when
   editing the skip list. Long-form files in `target/test262/run-*/`
   are not — query them.

---

## Active priorities

Remaining work is concentrated in `test/language/**`, dominated by
destructuring-assignment pattern parsing (see Background sweeps).
Symbol stays parked — real-world JS doesn't use `Symbol(...)`, and the
well-known symbols (`@@iterator` / `@@toPrimitive` / `@@toStringTag`)
already work as string stand-ins. For current pass/fail/skip counts,
query the latest run-dir (Recipes → [Failure triage](#failure-triage)) —
counts go stale fast and don't belong in this file.

| Slice | What's blocking it |
|---|---|
| `test/language/statements/for-of` | IteratorClose-on-throw; fn-name inference for `[x = (function(){})] of …`; a few negative-parse tightenings. |
| `test/language/expressions/object` | Escaped-keyword cover-name dominates; `__proto__`-duplicate edges; computed-key / spread / method-def tail. |
| `test/language/expressions/assignment` | Iterator-return semantics on default-expr throw. |
| `test/language/{statements,expressions}/function` + `arrow-function` | fn-name inference for `[x = (function(){})]`-style defaults; IteratorClose-on-throw; rest-element edges. |
| `test/language/expressions/compound-assignment` | Strict-mode ReferenceError on undeclared LHS (gated on strict-mode plumbing); `valueOf` / ToNumeric ordering for `+=` / `*=`; `A5.*_T2/T3` family (non-identifier LHS — Annex-B carve-out). |
| `test/language/statements/{try,for,switch}` | Control-flow tail; abrupt-completion handles headline cases. |
| `test/built-ins/Array/**` | `splice` / `concat` `Symbol.species` (Symbol-gated). |
| `test/built-ins/RegExp/**` | `Symbol.{match,replace,search,split,matchAll}` (Symbol-gated), parser edges, named-groups feature-gated. |
| `test/built-ins/String/**` | `substring` / `lastIndexOf` / `charAt` ToInteger corners; parser-blocked; Symbol-gated tail. See [JS_ENGINE.md § Spec preamble at built-in entry points](../docs/JS_ENGINE.md#spec-preamble-at-built-in-entry-points). |
| `test/built-ins/Object/**` | Descriptor edges; `seal` (TypedArray-gated); Annex-B `arguments` aliasing. See [JS_ENGINE.md § Property attributes](../docs/JS_ENGINE.md#property-attributes). |
| `test/built-ins/JSON/**` | `JSON.stringify` reviver/replacer 2-arg semantics; `-0`/`__proto__` parser tail. Calibration: run JSONTestSuite — see [JS_ENGINE.md § Future TODO Items](../docs/JS_ENGINE.md#2-future-todo-items). |
| `test/built-ins/Number/**` | `[object Number]` (Symbol-gated) + a literal-form parser edge. |
| `test/built-ins/Date/**` | ISO format edges + invalid-date propagation. See [JS_ENGINE.md § Date](../docs/JS_ENGINE.md#date). |
| `test/built-ins/Symbol/**` (parked) | Symbol primitive. Deprioritized — no real-world code uses it. Pick up after the language work. |

### Background sweeps

Picked off opportunistically when nearby — not session-sized on their own.

- **String iterator splits surrogate pairs.** `IterUtils.stringIterator`
  walks `charAt(i)` (UTF-16) instead of code-points (spec §22.1.5.1).
  Affects any `for-of` over a string. Fix: walk `codePointAt` /
  `charCount`. ~1 h.

- **`Array.prototype.values()` returns raw `List`, not a spec iterator
  object.** `IterUtils.iteratorFromCallable` absorbs the mismatch via a
  lenient fallback; direct `arr.values().next()` still fails. Use
  `IterUtils.toIteratorObject(listIterator(...))`. ~30 min.

- **Object-literal spread restricted to bare ident — parser-level.**
  `{...fn()}` / `{...obj.method()}` / `{...{x:1}}` fail at parse
  (parser accepts only `IDENT` after `...` in `LIT_OBJECT`). Parser
  grammar change; bigger scope than the array-spread fix. Array side
  covered by `EvalTest.testArrayLiteralSpread`.

- **`.length` / `.name` rollout to remaining prototypes** —
  `JsBuiltinMethod` infra in place; most residual `name.js` fails are
  Symbol-gated.

- **Destructuring cover-grammar tail.** Arrow-fn early errors:
  duplicate-binding-name walk over `FN_DECL_ARGS`
  (`(x, {x}) => 1` / `({a, a}) => …` should SyntaxError);
  `dstr/syntax-error-ident-ref-default.js` and siblings under
  `arrow-function/syntax/early-errors`. **Same family: duplicate
  BoundNames in a catch parameter** (`catch ([x,x])` /
  `catch ({a,a})` — `try/early-catch-duplicates.js`, currently
  SKIP'd). The shared fix is one spec-shaped BoundNames walk over a
  binding pattern (mirror the binding structure — bound names of
  `{a: x = y}` are `{x}` only, so a flat `findChildren(IDENT)` would
  false-positive on valid renamed/defaulted patterns). Catch-clause
  *runtime/parse* destructuring itself now works
  (`EvalTest.testCatchDestructuring`).

- **Cleanup residuals** — occasional `"null"` NPE paths, `IllegalName`
  JDK lambda leak, `Java heap space` OOM in array-slice paths.

---

## Deferred TODOs

Tracked but un-scheduled. Each item: a one-line *what* + *why parked* +
file pointer. For *how the subsystem is shaped*, read the file. For
*spec invariants worth honoring*, see
[JS_ENGINE.md § Spec Invariants](../docs/JS_ENGINE.md#spec-invariants-test262-driven).

### Engine — feature gaps

- **Strict mode plumbing.** Parse `"use strict"` directive, thread
  `strictMode` via `CoreContext`, flip lenient sites through one
  `failSilentOrThrow` helper. `AccessorSlot.write` already takes a
  `strict` arg. ~3–4 h. Risk: directive parser may regress
  `flags: [noStrict]` paths.
- **Promises / async / await / setTimeout.** Skipped (`feature: Promise`,
  `async-functions`, `Symbol.asyncIterator`). karate-js is synchronous.
  Viable path: sync subset first — `Promise` as eager thenable,
  `async function` runs sync, `await` sync-unwraps.
- **Class syntax (ES6).** No parser support for `class` / `extends` /
  `super`. Parked because target workload is glue/scripting, not OO;
  prototype + `new` covers the cases that appear.
- **Symbol primitive.** Gates a long tail across String / Array / RegExp /
  Object. Deprioritized — real-world code doesn't use it.

### Engine — cleanup

Benchmark-gated or coordinated with other work.

- **`Prototype.toMap()` rebuilds per call** — memoize on slot-map mod
  stamp or expose a non-materializing iterator. Defer until benchmark
  shows it matters.
- **`HOLE` → tombstone full elimination.** Sparse-array storage rework;
  pair with parser `in` support. Pinned in `SpecPinTest`. ~6–8 h.
- **HOLE leak audit at `JsArray` Java-interop seams** —
  `iterator()` / `toArray()` / `subList()` / `contains()` / `indexOf()` /
  `lastIndexOf()` route raw; only `get(int)` translates HOLE→null.
  Centralize on one unwrap helper. ~30 min. Pairs with above.
- **`PropertyKey` abstraction.** Symbol prep — YAGNI until Symbol lands.
- **`Arguments` → spec exotic Arguments object.** Cached `JsArray`
  today; missing `arguments.callee` (strict TypeError), non-strict
  alias-to-formal-parameters, and `[object Arguments]` toStringTag.
  Subclass when a workload demands.
- **`CreateDataPropertyOrThrow` + `ArraySpeciesCreate`.** Array
  result-allocation (`slice` / `concat` / `splice` / `map` / `filter` /
  `flat` / `flatMap`) bypasses spec sequence; depends on `Symbol.species`.
  Defer until Symbol.

### Engine — spec alignment

Observably non-spec; pick up when the owning slice surfaces them.

- **`JsArray.handleLengthAssign` return dropped on direct assign** —
  silent no-op on writable=false; spec wants TypeError under strict.
  Pair with strict-mode plumbing.
- **`ToObject` for non-empty string descriptor sources** — short-circuits
  to TypeError (correct end-state), skips wrapper pipeline.
- **`JsArray.jsEntries` vs `[[OwnPropertyKeys]]` asymmetry** —
  `jsEntries` is indices only; for-in / `Object.keys` /
  `defineProperties` want indices + named. `JsObjectConstructor.ownKeys`
  works around it. Split into `arrayEntries(ctx)` / `ownEntries(ctx)`
  when a 4th caller surfaces.
- **`ToPropertyKey` no-ctx callers** — `JsObjectConstructor.hasOwn` and
  `getOwnPropertyDescriptor` still on the no-ctx path. Migrate when a
  workload passes non-string keys.
- **Integer-index accessors beyond `JsArray.list.size()`** — high-index
  accessor via `defineProperty` is missed by `jsEntries`. Current
  workaround: `defineOwnAccessor` HOLE-pads. Real fix: merge integer-index
  `namedProps` into Phase 1. Pairs with HOLE elimination.
- **`JsGlobalThis` two-store reads** — data in `BindingsStore`,
  accessors in `JsObject.props`. Extend `BindingSlot` with accessor
  side-table OR commit to a unified two-store contract. ~2 h.
- **`Symbol.toPrimitive` not dispatched** — matches minimal Symbol
  surface; fix with Symbol.
- **`(0, fn)()` indirect-call `this`-binding** — comma should drop
  reference base (→ `this = undefined`); today falls through to
  globalThis. Audit `evalCallExpr` for the parenthesized-comma case.

### Harness quality

- Replace hand-rolled YAML parser with SnakeYAML (`Expectations.java` /
  `Test262Metadata.java` — breaks on `#` in quoted reasons, block scalars).
- `--resume` echoes records for deleted / now-SKIP'd tests — gate or
  rename to `--resume-crash-only`.
- Cache parsed harness ASTs in `HarnessLoader` (~50k re-parses per run).
- Plumb per-test console capture into `ResultRecord` (currently wired
  and discarded by `evaluate(...)`).
- `phase: resolution` (module-resolution) negatives conflated with
  `runtime` — latent (modules skipped).
- `$262` surface stubs (`AbstractModuleSource`, `IsHTMLDDA`,
  `agent.*`) — add when a feature unblocks.
- Parallel execution — prior attempts showed no speedup; engine
  doesn't poll `Thread.interrupt()`. Revisit when per-test cost grows.
- `Test262Runner.readHeadSha` walks parent chain — prefer
  `git rev-parse HEAD` or `--karate-sha`.
- Commit `target/test262/results.jsonl` once engine churn slows.

---

## Running

**All commands run from `karate-js-test262/`** (the runner resolves
`etc/expectations.yaml` and `test262/` relative to cwd). Use `-f ../pom.xml`
so Maven finds the parent reactor. After any change under `karate-js/`,
**re-install it first** — the runner uses the karate-js jar from your local
Maven repo, not from the reactor.

### Quick start

[`etc/run.sh`](etc/run.sh) does install + run (+ HTML on `--full`):

```sh
cd karate-js-test262
etc/fetch-test262.sh                                       # first time only — shallow clone
etc/run.sh                                                 # dev mode, full suite
etc/run.sh --only 'test/language/**' --max-duration 300000 # scoped, 5-min cap
etc/run.sh --full                                          # PASS rows + HTML
```

Each run writes a fresh `target/test262/run-<timestamp>/` (the runner
prints the path) containing `results.jsonl`, `results.jsonl.partial`,
`run-meta.json`, `progress.log`; `html/` only with `--full`. Old runs
are immutable; `mvn clean` wipes them.

**Dev mode (default)** keeps `results.jsonl` to FAIL+SKIP only; the
pass count is in `run-meta.json` (`counts.pass`). `--full` adds PASS
rows (for CI artifacts / audits / HTML).

**Liveness sampling** (never `tail -f` — see
[Context discipline](#context-discipline)):

```sh
tail -n 1 <run-dir>/progress.log    # last heartbeat: processed N pass M fail K skip L
tail -n 5 <run-dir>/progress.log
```

### Driving by hand

If you need to invoke the runner or HTML report without `etc/run.sh`,
read [`etc/run.sh`](etc/run.sh) — it documents the install step and the
`-am` gotcha (`exec:java` is a direct goal; with `-am` the reactor
includes `karate-parent`, which has no `mainClass`, and aborts before
this module). Install karate-js separately, then run without `-am`.

### Flags

Most-used flags below. Full set + defaults: read `main(...)` in
[`Test262Runner`](src/main/java/io/karatelabs/js/test262/Test262Runner.java).

| Flag | Purpose |
|---|---|
| `--only <glob>` | restrict to matching paths |
| `--single <path> [-v] [-vv]` | run one test, no file writes. `-v` prints metadata + classification + engine location; `-vv` adds full source |
| `--full` | write PASS rows (default is FAIL+SKIP only); also gates HTML render in `etc/run.sh` |
| `--max-duration <ms>` | overall wall-clock cap (default unlimited); writes partial results + prints `Aborted:` on hit |
| `--timeout-ms <n>` | per-test watchdog (default 10s) |
| `--run-dir <path>` | output dir (default `target/test262/run-<ts>/`) |

Runs are silent except FAIL lines + periodic `[progress]`. FAIL lines
on stdout are capped at 20 (footer `(… N more FAILs, see results.jsonl)`
fires after). `[progress]` lines emit every 5000 tests or 60 s and are
mirrored to `<run-dir>/progress.log`. Per-FAIL detail lives only in
JSONL — sample `progress.log` for liveness, never `tail -f`
`results.jsonl.partial`.

### Hang handling

The runner uses a single-thread `ExecutorService` to enforce `--timeout-ms`
per test. The karate-js engine doesn't poll `Thread.interrupt()`, so
`cancel(true)` can't stop the underlying thread. When a timeout fires, the
runner **retires the executor** (shuts it down, creates a fresh one) so
subsequent tests don't queue behind the stuck thread. Net cost of a genuine
hang: one abandoned daemon thread, one Timeout row in `results.jsonl`, a few
ms of recreate overhead.

For scripts / agents driving the runner: pass `--max-duration <ms>` as a
safety net and follow [Context discipline](#context-discipline).

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
glob, debug failures with `--single -v`.

**Adding a new unimplemented feature.** If you hit FAILs for an ES surface
the engine genuinely doesn't implement (e.g. `JSON.rawJSON` / `isRawJSON`
from ES2024), add a `features:` rule with the test262 feature flag name —
not a `paths:` rule. The feature names match what the tests declare in
their YAML frontmatter (`features: [json-parse-with-source]`), which is
also what `--single -v` prints under `features:`. See existing entries
for the exact shape; precedence rules above still apply.

---

## Results schema

Two JSONL files during a run:

- **`<run-dir>/results.jsonl.partial`** — appended per test as results
  arrive, flushed per write. **Run order, not sorted.** Deleted on clean
  exit; preserved on abort (`--max-duration` hit, Ctrl-C, JVM kill).
- **`<run-dir>/results.jsonl`** — canonical output, **sorted alphabetically
  by path**, atomically written at end-of-run (tmp + rename). This is what
  tooling reads.

**Dev mode (default):** only FAIL and SKIP rows are written. The pass
count comes from `run-meta.json` (`counts.pass`). The `Failure triage`
and `Diff two run-dirs` recipes are designed to work without PASS rows.

**`--full` mode:** PASS rows are also written, one per attempted test
that didn't fail or get skipped. Use when you need the canonical full
record (CI artifact, deep audit) or want the HTML report (which
`etc/run.sh` gates on `--full`).

Example line shape (same in both):

```jsonl
{"path":"test/language/expressions/addition/S11.6.1_A1.js","status":"PASS"}
{"path":"test/.../something.js","status":"FAIL","error_type":"TypeError","message":"foo is not a function"}
{"path":"test/.../bigint-test.js","status":"SKIP","reason":"BigInt not supported"}
```

(The PASS row only appears in `--full` mode.)

Error types are classified into:
`SyntaxError | TypeError | ReferenceError | RangeError | Error | Timeout |
Harness | Unknown` by inspecting message prefixes (the engine emits
`"TypeError: ..."` style messages at most failure sites). The classifier
itself is in `ErrorUtils`.

---

## Recipes

### Debug one failing test

```sh
# Default: -v gives metadata + classification + location — usually enough
# to find the engine call site without dumping test source into context.
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -v" 2>&1 | tail -n 40

# Escalate to -vv (full source) only if -v didn't pinpoint the cause:
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -vv" 2>&1 | tail -n 200
```

`-v` prints parsed YAML metadata (description / flags / features / includes
/ negative), the classification, and — if the engine attached a position
— a `location: <path>:<line>:<col>` line. `-vv` additionally prints the
full test source. `--single` does no file writes. **No HTML drill-down
page is generated** — the details.html report shows path + error_type +
message inline.

**Location-line caveat.** `location:` only appears when the engine
itself threw and attached a position. Two common FAIL shapes carry no
location and you should skip straight to reading the relevant built-in
source:
- `Test262Error: <expectation>` — the harness assertion fired
  inside the test's own JS, not the engine. Find the failure inside
  the test source (or look at what the test is asserting) and trace
  back to the engine method that built the wrong value.
- `Unknown: java.lang.StackOverflowError` / `NullPointerException` /
  other Java exceptions — uncaught Java throwables surface without a
  JS-level position. Grep the stack for the engine class.

### Failure triage

Compact rollups over `results.jsonl`. All return tens of lines, not
thousands. Use these instead of reading the raw JSONL when scoping a
slice or hunting for clusters.

```sh
RD=target/test262/run-<ts>            # the run-dir to analyze
JSONL=$RD/results.jsonl               # use .partial during an in-progress run

# PASS / FAIL / SKIP counts.
jq -r .status "$JSONL" | sort | uniq -c

# FAIL histogram by error_type — which classifier buckets dominate.
jq -r 'select(.status=="FAIL").error_type' "$JSONL" \
  | sort | uniq -c | sort -rn

# Top 20 FAIL message clusters (numbers normalized so near-duplicates merge).
jq -r 'select(.status=="FAIL").message' "$JSONL" \
  | sed 's/[0-9][0-9]*/N/g' \
  | sort | uniq -c | sort -rn | head -20

# FAIL counts per slice (two path components deep).
jq -r 'select(.status=="FAIL") | .path | split("/")[1:3] | join("/")' "$JSONL" \
  | sort | uniq -c | sort -rn | head -30

# One example failing path per error_type — for `--single -v` follow-up.
jq -r 'select(.status=="FAIL") | "\(.error_type)\t\(.path)"' "$JSONL" \
  | sort -u -k1,1 | head -20

# All FAILs under a specific slice — bounded with head, never raw.
jq -r 'select(.status=="FAIL" and (.path|startswith("test/language/statements/for-of"))) | .path' \
  "$JSONL" | head -30
```

### Diff two run-dirs (regression check)

FAIL-set difference — works in dev mode (no PASS rows needed). Capped
output: counts + first 10 of each list + per-slice cluster breakdown.
Assumes both runs covered the same `--only` scope (recorded in each
`run-meta.json` if you want to verify).

```sh
PREV=target/test262/run-<prev>/results.jsonl
CURR=target/test262/run-<curr>/results.jsonl

python3 - "$PREV" "$CURR" <<'PY'
import json, sys, collections
def fails(p):
    return {json.loads(l)['path'] for l in open(p) if json.loads(l)['status']=='FAIL'}
prev, curr = fails(sys.argv[1]), fails(sys.argv[2])
regr = sorted(curr - prev)   # newly failing — likely regressions
fixed = sorted(prev - curr)  # newly passing (or removed/skipped)
def by_slice(paths):
    c = collections.Counter('/'.join(p.split('/')[1:3]) for p in paths)
    return c.most_common(10)
def show(label, paths):
    print(f'{label}: {len(paths)}')
    for p in paths[:10]: print(f'  {p}')
    if len(paths) > 10: print(f'  ... {len(paths)-10} more')
    if paths: print(f'  by slice: {by_slice(paths)}')
show('Regressed (newly FAIL)', regr)
show('Fixed   (no longer FAIL)', fixed)
PY
```

Per-session safety check against the slice baseline. If `Regressed` is
non-zero, drill into a couple of representative paths with
`--single -v` — do **not** paste the full list into context. Note: a
path appearing under "Fixed" could mean it now PASSes *or* that it was
moved to SKIP / removed from scope; cross-check with the SKIP set if
ambiguous.

### Delegate a slice run

For re-probing a slice or triaging a cluster, spawn a `general-purpose`
sub-agent and require a digest. The sub-agent reads the full output;
you receive only the summary.

Prompt template (copy, fill in `<glob>`, paste into `Agent`):

> Run `etc/run.sh --only '<glob>' --max-duration 600000` from
> `karate-js-test262/`. After it completes, query the run-dir's
> `results.jsonl` (the runner prints `Run dir: <path>` on completion)
> and return **≤200 words**:
>
> - PASS / FAIL / SKIP counts for the slice.
> - Top 3 FAIL clusters (group by error_type + normalized message
>   prefix). For each: count, one example path, one example message.
> - Anything surprising: Timeouts, NPE-shaped errors, `Java heap space`,
>   `IllegalName` lambda leaks, parse-vs-runtime classification gaps.
>
> Do **not** paste raw FAIL lines, full test source, or JSONL
> contents. If you need to inspect a specific test, use
> `--single -v` and quote ≤3 relevant lines.

Use it for: slice probes, cluster triage, "did my engine change regress
anything" checks, post-edit slice re-runs. Skip for small targeted
lookups (one test, one symbol) — run those inline.

### Check performance after an engine change

The conformance suite allocates a fresh `Engine` per test (~50k tests); small
regressions compound into minutes of wall time. **Prefer profile mode** — the
30 s warm loop is JIT-stable and directly comparable to the
[reference table in JS_ENGINE.md](../docs/JS_ENGINE.md#performance-benchmarks).

```sh
mvn -pl karate-js -q test-compile

# Profile mode (30 s warm loop; JIT-stable, ~16k iterations averaged).
java -cp "karate-js/target/classes:karate-js/target/test-classes:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1)" \
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

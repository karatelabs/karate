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
> [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, working
> principles, code map, **spec invariants**, benchmarks ·
> [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design ·
> [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
> — authoritative test-runner spec.

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

## Per-session ritual

Each session that touches the engine should:

1. Re-probe the slice with `--only` before scoping; record before/after
   pass counts in the commit message.
2. **Unit tests:** all green. `mvn -f pom.xml -pl karate-js -o test` →
   `Tests run: 924, Failures: 0, Errors: 0`. Update tests where the spec
   disagrees with them; never delete coverage.
3. **test262 built-ins probe:** `etc/run.sh --only 'test/built-ins/**'`.
   Diff against the previous run's `results.jsonl`. **Zero regressions
   (PASS → FAIL).** Net-positive is preferred but not required for a pure
   refactor — a spec-aligned correction may flip a few tests in either
   direction; document any flip in the commit message.
4. **EngineBenchmark profile:** within ±10% of the
   [JS_ENGINE.md reference](../docs/JS_ENGINE.md#performance-benchmarks)
   (1.32 ms array / 0.50 ms object on the M1 Pro). If unavoidable
   (correctness > speed), update the reference table in the same commit.

---

## Slice baseline

Numbers as of 2026-04-26 (`run-2026-04-26-174407`):

| Slice | Pass | Fail | Skip | Total |
|---|---|---|---|---|
| `test/built-ins/**` (full) | 6303 | 6607 | 10604 | 23514 |
| `test/built-ins/Math/**` | 311 | 0 | 16 | 327 |
| `test/built-ins/Number/**` | 233 | 95 | 12 | 340 |
| `test/built-ins/JSON/**` | 29 | 112 | 24 | 165 |
| `test/built-ins/Date/**` | 500 | 52 | 42 | 594 |
| `test/built-ins/RegExp/**` | 471 | 521 | 887 | 1879 |
| `test/built-ins/String/**` | 457 | 716 | 50 | 1223 |
| `test/built-ins/Object/**` | 2191 | 1083 | 137 | 3411 |
| `test/built-ins/Array/**` | 1280 | 1621 | 180 | 3081 |
| `test/built-ins/Function/**` | 261 | 120 | 128 | 509 |
| `test/built-ins/Symbol/**` | 2 | 44 | 52 | 98 |

Re-probe at the start of each session and pin the baseline run-dir in the
commit so the next session can diff cleanly.

---

## Slice roadmap

Committed slice order. Picked for feedback-loop speed: small, contained slices
first surface ergonomic gaps in the harness and harden type-coercion +
error-propagation foundations that the bigger sweeps (Object/Array/Symbol)
lean on. Symbol is **last**, not first — Math/Number/Date are independent of
Symbol and only ~13% of String fails touch it (regex protocol + iterator).

| # | Slice | Fails | What's likely in scope |
|---|---|---|---|
| 1 | `test/built-ins/Number/**` | ~95 | `parseInt`/`parseFloat` edge cases; `toFixed`/`toPrecision`/`toExponential` rounding; `Number.isInteger` / `isSafeInteger` / `isFinite` / `isNaN` distinctions |
| 2 | `test/built-ins/JSON/**` | ~112 | `stringify` replacer-fn / array-filter / nested `toJSON` dispatch; `parse` reviver hooks; circular detection |
| 3 | `test/built-ins/Date/**` | ~52 | `Date.parse` ISO format edges; UTC vs local hour math; invalid-date propagation |
| 4 | `test/built-ins/RegExp/**` | ~521 | Constructor + `.source` / `.flags` / `.lastIndex`; `exec` / `test` semantics; flag-set validation; non-Symbol `String.prototype.{match,replace,search,split}` integration |
| 5 | `test/built-ins/String/**` | ~716 | `padStart`/`padEnd`, `trimStart`/`trimEnd`, `normalize`, `repeat`, `raw`, `fromCodePoint`; non-`@@`-protocol regex methods |
| 6 | `test/built-ins/Object/**` | ~1083 | `assign` / `keys` / `values` / `entries` corners; `Object.fromEntries`; descriptor-handling residuals |
| 7 | `test/built-ins/Array/**` | ~1621 | Bulk methods using `CreateDataPropertyOrThrow` + `Symbol.species`; iterator-result objects; remaining length-cluster residuals (Uint32 representation, spec-precise pop/shift interleaving — see [JS_ENGINE.md § JsArray length semantics](../docs/JS_ENGINE.md#prototype-machinery)); `Object.freeze(arr)` is a no-op today |
| 8 | `test/built-ins/Symbol/**` + cascades | ~580 SKIPs | Full Symbol primitive: `typeof === "symbol"`, unique identity, `Symbol.for` / `keyFor` registry, `description`, `Object.getOwnPropertySymbols`, `Reflect.ownKeys`. Touches `Terms.typeOf` / `eq` / coercion; property-key abstraction across `JsObject._map` / `_attrs` / `_tombstones` / `isOwnProperty`. 2–4 sessions. Unblocks the Array/Symbol.species cluster from #7 |

### Background sweeps

Picked off opportunistically when nearby — not session-sized on their own.

- **Map / Set residuals.** `Map.prototype.{groupBy, getOrInsert,
  getOrInsertComputed}` (ES2024+). `Set.prototype.{difference, intersection,
  union, isDisjointFrom, isSubsetOf, isSupersetOf, symmetricDifference}`
  (ES2025). `Object.getPrototypeOf(Map.prototype)` returns
  `JsObjectPrototype.INSTANCE` (singleton) where tests compare against
  `Object.prototype` (the global) — needs identity sharing. Same for
  `Set.prototype`.

- **`.length` / `.name` rollout to remaining prototypes.** `JsBuiltinMethod`
  infra is in place; incremental wins are small. Most residual `name.js`
  fails are Symbol-gated. Treat as background cleanup.

- **Destructuring residuals.** Bulk works; long tail is lexer identifier-
  escape support, TDZ / init-order corners, negative parse tests needing
  pattern-vs-literal two-mode parsing.

- **Cleanup residuals.** Occasional `"null"` NPE paths, `IllegalName` JDK
  lambda leak, `Java heap space` OOM in array-slice paths. Grab while nearby.

---

## Deferred engine work

Un-scheduled but tracked. Two flavors: large feature gaps that are intentional
non-goals today, and small harness-quality fixes that don't gate work but
should be done when convenient.

### Engine refactors (deferred)

- **EnvironmentRecord-shaped name resolution.** `CoreContext.get` /
  `update` / `declare` / `hasKey` / `assignImplicitGlobal` each walk the
  same chain (`_bindings` → `closureContext` → `parent` → `root`) with
  subtly different shapes. The spec's `ResolveBinding` /
  `GetIdentifierReference` is the underlying algorithm — a single
  `resolveBinding(context, name)` returning `{ store, BindValue }` would
  let `get` be "find slot, return value", `update` be "find slot, mutate
  or fall through to assignImplicitGlobal", `declare` be "find slot in
  current scope, push if absent." Touches the **hottest path** in the
  engine (every variable lookup); EngineBenchmark must stay within ±5%,
  not the usual ±10%, or walk it back. Save for a session that's already
  in the lookup machinery (strict-mode bindings, modules).

- **Fast-path bypass sweep.** `JsObject.jsEntries` had `if (_attrs == null
  || isEnumerable(...))` — the `_attrs == null` short-circuit skipped
  subclass `getOwnAttrs` overrides entirely. Patched in commit
  `1a67fb110`. Pattern likely exists elsewhere as `_attrs == null` /
  `_map == null` / `_tombstones == null` / `__proto__ == null` (and
  `!=` variants) across `JsObject.java`, `JsArray.java`, `Prototype.java`,
  `JsFunction.java`. For each, ask: **does this fast-path bypass a
  virtual method a subclass might override?** If yes, route through the
  virtual method. ~30 minutes plus EngineBenchmark verification.

### Large feature gaps (intentional non-goals)

- **Promises + async/await + setTimeout.** Skipped (`feature: Promise`,
  `async-functions`, `Symbol.asyncIterator`, `include: promiseHelper.js`).
  karate-js is synchronous. When eventually scheduled: Java interop via
  `CompletableFuture`, microtask model, `setTimeout` backing — all design
  decisions belong in the implementation session.

- **Class syntax (ES6).** Skipped (`feature: class` and friends). Engine has
  prototype machinery + `new` but no parser support for `class` / `extends` /
  `super` / method-definition. LLMs default to function+prototype style;
  parse currently fails loudly with `SyntaxError` — the right shape until
  real workload demands it.

- **Directive prologue / strict-mode.** Parser tolerates `"use strict"`
  without activating strict-mode assertions. Skip triage is done; full
  implementation is a separate project.

### Harness quality

- **Replace hand-rolled YAML parser with SnakeYAML.** `Expectations.java` /
  `Test262Metadata.java` break on `#` in quoted reasons, block-scalar
  `description:` fields, and block-form list values. Swap when next touched.

- **`--resume` refreshes stale records.** Currently echoes records for tests
  that no longer exist or are now SKIP'd. Gate on test-still-exists +
  not-in-skip-list, or rename to `--resume-crash-only`.

- **Cache parsed harness ASTs, not source text.** `HarnessLoader` re-parses
  `assert.js`, `sta.js`, and per-test `includes:` on every test; ~50k
  re-parses per full-suite run.

- **Surface per-test console capture in `ResultRecord`.** `evaluate(...)`
  already wires `Engine.setOnConsoleLog(...)` to a per-test sink (discarded);
  plumb into `ResultRecord` for `--single -vv` and the HTML drill-down.

- **`phase: resolution` (module-resolution) negative tests.** Conflated with
  `runtime` today. Modules are globally skipped, so latent.

- **Structured `$262` surface.** `AbstractModuleSource`, `IsHTMLDDA`,
  `agent.broadcast/getReport/sleep/monotonicNow` absent; all feature-gated,
  unreachable today. Add stubs when a feature comes off the skip list.

- **Parallel execution.** Prior attempts showed no speedup (thread
  context-switch beat the ~1 ms per-test cost) and the engine doesn't
  poll `Thread.interrupt()`. Revisit when per-test cost grows or the
  engine learns cooperative abort.

- **`readHeadSha` path fragility.** `Test262Runner.readHeadSha` walks up
  `cli.test262.getParent().getParent()`. Prefer `git rev-parse HEAD` or
  `--karate-sha`.

- **Commit `target/test262/results.jsonl` once stable.** Gitignored today;
  re-evaluate when engine churn slows.

### Engine cleanup

- **Unify the read-side dot dispatch in `PropertyAccess`.** Write-side is
  unified via `resolveWriteSite` + `AccessSite`; read-side
  (`getRefDotExpr` / `getCallableRefDotExpr`) still has mirrored
  bridge-fallback + `?.` logic. Defer until there's a third caller wanting
  the same resolution shape.

- **Thin `EngineException` once `JsError` goes public.** Carries a
  `jsErrorName` string that duplicates the cause-chain's `JsErrorException`
  payload. `JsError` is package-private. Drop the name field when a host
  use case forces `JsError` public.

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

This is the per-session safety check against the slice baseline above.

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
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, working principles, code map, **spec invariants**, benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

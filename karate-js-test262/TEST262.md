# karate-js-test262

ECMAScript [test262](https://github.com/tc39/test262) conformance harness for
karate-js. Reproducible pass/fail matrix across the ES surface area, declarative
skip list ([`etc/expectations.yaml`](etc/expectations.yaml)), and the roadmap
for what to tackle next. **Not** published to Maven Central.

The bar is *can karate-js run real-world JavaScript written in the wild,
especially by LLMs?* test262 is the scorecard; pragmatic ES6 coverage of
idiomatic code is the goal — not spec-lawyer compliance for its own sake.

> ⚠️ **Engine is currently in refactor mode (sessions R1–R4 below).** The
> test262 slice roadmap is **paused** until the refactor lands. Refactor
> progress is tracked in this document; once R1–R4 are done we clean up
> here and fold the durable architectural notes into
> [JS_ENGINE.md](../docs/JS_ENGINE.md).

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

## Refactor program (R1–R4)

### Why now

The recent globalThis work (commits `be14f3d61` + `1a67fb110`) was a single
narrow fix — give top-level `this` a sane binding and wire reads/writes
through it — but it surfaced enough structural smells across `Engine`,
`ContextRoot`, `CoreContext`, `Bindings`, `Interpreter`, and `JsObject` that
the next sessions are best spent **paying down the structural debt** before
chasing more test262 wins. The slice roadmap below (Number, JSON, Date, …)
will lean on these same files; cleaning them first means each later slice is
cheaper and less likely to get stuck on a layering issue.

The premise is simple: we have **924 unit tests** and a **23,514-test test262
matrix**. Both run in seconds. That's the safety net. Refactor with the
spec as ground truth, fold tests where they encoded a bug, and keep the
test262 pass count moving up not down.

### Non-negotiables (the safety net)

After each session lands, before commit:

1. **Unit tests:** all green. `mvn -f pom.xml -pl karate-js -o test` →
   `Tests run: 924, Failures: 0, Errors: 0`. Update tests where the spec
   disagrees with them; never delete coverage.
2. **test262 built-ins probe:** `etc/run.sh --only 'test/built-ins/**'`.
   Compare against the previous run's `results.jsonl`. **Zero regressions
   (PASS → FAIL).** Net-positive is preferred but not required for a pure
   refactor — a spec-aligned correction may flip a few tests in either
   direction; document any flip in the commit message.
3. **EngineBenchmark profile:** within ±10% of the
   [JS_ENGINE.md reference](../docs/JS_ENGINE.md#performance-benchmarks)
   (1.32 ms array / 0.50 ms object on the M1 Pro). If unavoidable
   (correctness > speed), update the reference table in the same commit.
4. **No new public API**, no host-visible behavior change unless flagged.
   `Engine.put`/`get`/`putRootBinding`/`getBindings`/`getRootBindings`
   contracts stay; the refactor's job is to make the **internals** simpler
   and more spec-aligned, not to rename the host surface.

### Status

| # | Title | Status | Date | Commit | Notes |
|---|---|---|---|---|---|
| R1 | Engine state consolidation (issues 1 + 2 + 3 + 10) | not started | — | — | — |
| R2 | Unify call-site `this` binding; drop `JsObject implements JsCallable` (issues 4 + 5) | not started | — | — | — |
| R3 | Split raw bindings store from auto-unwrapping host view (issues 6 + 9) | not started | — | — | — |
| R4 | EnvironmentRecord-shaped name resolution (issue 7) | optional | — | — | gate on appetite at end of R3 |
| S1 | Fast-path-bypass sweep (issue 8) | not started | — | — | 30-min sweep, can run between sessions |

Update the row when starting (`in progress` + date) and when landing
(`done` + date + commit SHA + a one-line note). Don't delete rows when done
— they're the breadcrumb trail for the cleanup pass.

### Snapshot when refactor started (2026-04-26)

| Slice | Pass | Fail | Skip | Total |
|---|---|---|---|---|
| `test/built-ins/**` (full) | 6260 | 6650 | 10604 | 23514 |
| `test/built-ins/Math/**` | 311 | 0 | 16 | 327 |
| `test/built-ins/Number/**` | 233 | 95 | 12 | 340 |
| `test/built-ins/String/**` | 457 | 716 | 50 | 1223 |
| `test/built-ins/Array/**` | 1280 | 1620 | 181 | 3081 |
| `test/built-ins/Object/**` | 2170 | 1104 | 137 | 3411 |
| `test/built-ins/Date/**` | 501 | 51 | 42 | 594 |
| `test/built-ins/Function/**` | 240 | 141 | 128 | 509 |
| `test/built-ins/Symbol/**` | 2 | 44 | 52 | 98 |

Last full-suite run before R1: `target/test262/run-2026-04-26-144956/`.
Re-probe at the start of each session and pin the baseline run-dir in the
commit so the next session can diff cleanly.

### The catalog (annotated)

The 10 issues that fed R1–R4 + S1, with file pointers and the rationale.
Read in order; later issues reference earlier ones.

#### 1. `ContextRoot extends CoreContext` is a structural smell

`ContextRoot` lives at `depth = -1` and only sometimes participates in the
`CoreContext` machinery. It inherits `currentLevel`, `scopeStack`,
`enterScope`/`exitScope`, `closureContext`, `capturedBindings`, `callArgs`
— **none of which apply** to "the root of the engine." It also bolts
`engine`, `listener`, `bridge`, `interceptor`, `pointFactory`,
`_topLevelBindings`, `evalId`, `onConsoleLog` on top — so the actual
engine-state container is intermixed with per-frame execution state.

This shows up as:
- `assignImplicitGlobal` walking up *while `depth > 0`* (why 0 and not -1?
  because depth=-1 is the root, but the loop stops one level above).
- `findFunctionLevel` returning 0 if no FUNCTION scope on the stack — but
  ContextRoot has `currentLevel = 0` too, so the boundary is fuzzy.
- `declare` checking `if (depth == 0 && root != null && parent == root)`
  to decide whether a top-level declaration also registers in
  `root._topLevelBindings`.

**Better shape:** `ContextRoot` is the **engine's global-state holder**;
`CoreContext` is the **per-frame execution state**. They're not
specializations of each other.

- `ContextRoot` becomes a plain class (no inheritance from CoreContext)
  that owns `Bindings`, the listener/bridge/interceptor/etc.
- `CoreContext.parent` is typed as `ContextRoot | CoreContext` (or a small
  interface like `EnvironmentParent`) — the script-level CoreContext takes
  the root as its parent, but inheritance isn't required for that.
- Drop `depth = -1` as a sentinel; the script context is `depth = 0`, the
  root is "above 0" and named explicitly via `parent` chain.

Files: `ContextRoot.java`, `CoreContext.java`, `Engine.java`,
`Interpreter.java` (every `context.depth` / `context.parent` walk).

**Cost:** moderate. ~50 sites read `context.depth` or walk `parent`. The
clarity win is real and is a precondition for issues 2/3/10.

#### 2. Two binding stores are semantically one thing

`Engine.bindings` (user-visible store, reachable via `Engine.put`/`get`/
`getBindings`) and `ContextRoot._bindings` (hidden store, target of
`Engine.putRootBinding` and the lazy built-in cache populated by
`ContextRoot.initGlobal`) hold the same kind of data. The split exists
solely because `putRootBinding` is meant to be **invisible to host
inspection** — Karate's framework injects a few resources that user code
shouldn't see in `getBindings()`.

That's an enumerability flag, not a separate store.

**Better shape:** one `Bindings` instance, with a per-entry `hidden` (or
`enumerable=false`) flag. `getBindings()` filters out the hidden ones.
`JsGlobalThis` becomes a true **single-store** façade: no more
`userBindings()` vs `root._bindings` two-tier merge logic in `getMember`,
`isOwnProperty`, `getOwnAttrs`, `toMap`. Lazy built-in caching writes
there too with `enumerable=false`.

Files: `Engine.java`, `ContextRoot.java`, `Bindings.java`,
`BindValue.java` (add `hidden` field if needed),
`JsGlobalThis.java` (collapses to ~half its current size).

**Cost:** small to moderate. ~5 callers of `Engine.bindings` /
`getBindings` / `getRootBindings`. The current `JsGlobalThis.toMap`
merge is awkward enough that this would simplify it materially.

#### 3. `assignImplicitGlobal` should target the root, not depth=0

Currently it walks while `depth > 0` and stops at the script context. So
`foo = 1` (free assignment) at top level lands in the script's
`_bindings`. With (1)+(2) fixed, this is the *same store* as the root's,
and walking up is unnecessary — but the dependency is real: without (2),
implicit globals aren't visible to `JsGlobalThis` and we've already had to
work around that.

The one-line fix is `globalContext = root` instead of the `while
(depth > 0) parent` walk, **once** (1)+(2) are in. Until then, the walk is
the only thing keeping current behavior intact. Don't try to fix this in
isolation.

File: `CoreContext.java` (`assignImplicitGlobal` only).

**Cost:** trivial — but only safe in the same session as (1)+(2).

#### 4. `Interpreter`'s `thisObject = receiver == null ? callable : receiver` is non-spec

Spec: a regular function called as `f()` (no receiver, no `f.call(...)`,
no `new`) gets `this = undefined` (strict) or `this = globalThis` (sloppy).
karate-js sets `this = the function itself`, which is **neither** of those.

This is what made `Math()` succeed silently before slice 1 — the dispatch
handed `this = Math` to `JsObject.call`, which created a new object. We
patched `JsMath.call` to throw, but the underlying mis-binding affects
every plain function call.

The substitution exists in **six** places in `Interpreter.java`
(approximately lines 450, 461, 463, 538, 618, 624). All do the same thing.

**Better shape:** extract `bindThisForCall(callable, receiver, context)`
that returns `globalThis` (matching the substitution we already do in
`coerceThisArg` for `call`/`apply` in `JsFunctionPrototype.java`). Single
helper, called from every dispatch site.

Files: `Interpreter.java`, `JsFunctionPrototype.java` (consolidate
`coerceThisArg` into the same helper).

**Cost:** small — 6 call sites + one helper. Likely surfaces a few
test262 wins (constructors that should throw on `Foo()` but currently
don't, e.g. `JSON()`, `Reflect()`).

#### 5. `JsObject implements JsCallable` is the source of "Math() returned a JsObject" silently

`JsObject.call` returning `new JsObject()` is a stand-in for the **`Object`
constructor** — but every JsObject inherits this method, so Math, JSON,
the global registries, every plain object literal: **all silently
callable**. We patched JsMath specifically; every other "should not be
callable" object is still latently broken.

**Better shape:** `JsObject` does **not** implement `JsCallable`. The
`Object` constructor singleton (`JsObjectConstructor`) does, and **its**
`call` returns the new object. Plain objects fail the
`instanceof JsCallable` check at the call site, which already throws the
uniform `"X is not a function"` TypeError.

Files: `JsObject.java` (drop `JsCallable` from `implements`, drop the
`call` override), `JsObjectConstructor.java` (already overrides `call`,
keep), every `instanceof JsCallable` / `o.call(...)` site (audit, expect
some legit callables that need explicit `JsCallable` markers).

**Cost:** moderate. Need to audit every `instanceof JsCallable` /
`o.call(...)` site to confirm only "actual callables" reach them. The
result is a much cleaner type system; likely deletes the `JsMath.call`
override added in slice 1 because it becomes redundant.

Should land in the same session as (4) — both touch the call-dispatch
surface.

#### 6. `Bindings` does double duty: raw store + auto-unwrapping host Map

`Bindings.entrySet()` calls `Engine.toJava` on each value (wraps
`JsFunction` → `JsFunctionWrapper`). My `JsGlobalThis.toMap` had to use
`getRawMap()` instead — a quiet correctness landmine. **Identity-based
descriptor checks fail silently** when iteration goes through the wrapper.

**Better shape:** split into:
- `BindingsStore` (raw, internal, no auto-unwrap). Engine internals use
  this.
- `Bindings` (Java-facing wrapper that auto-unwraps for hosts). `Engine.put`/
  `Engine.get`/`Engine.getBindings` use this.

The wrapper holds a reference to the store. "Iterate without unwrapping"
becomes the obvious default.

Files: `Bindings.java` (split), `Engine.java` (host-facing API stays on
the wrapper), `CoreContext.java`, `JsGlobalThis.java` (use the raw store).

**Cost:** small to moderate. The `Bindings` API is widely used; the split
needs to keep both surfaces working.

#### 7. `update` / `declare` / `put` / `get` / `hasKey` / `assignImplicitGlobal` overlap

Each walks: `_bindings` → `closureContext` → `parent` → `root`, with subtly
different logic and different recursive call shapes. There's a clear
underlying "name resolution algorithm" — the same one ECMA spec defines as
`ResolveBinding` / `GetIdentifierReference` / `EnvironmentRecord`.

**Better shape:** extract one `EnvironmentRecord`-ish helper that, given a
context and a name, returns `{ env, slot }` — i.e. **which environment
contains the binding, and the BindValue itself**. Then `get` is "find
slot, return value"; `update` is "find slot, mutate value (or
`assignImplicitGlobal` if no slot)"; `declare` is "find slot in current
scope, push if absent."

Files: `CoreContext.java` (the heaviest target — half the file), maybe
spawn a new `EnvironmentRecord.java` if the helper grows.

**Cost:** larger refactor. Touches the **hottest path** in the engine
(every variable lookup). Save for **R4**, gated on appetite at the end of
R3 — it's the single biggest clarity win for someone reading the code
fresh, but it's also the riskiest. If declined, leave for a later session
where we touch this area for a feature reason (strict-mode bindings,
modules).

#### 8. Fast-path bypasses that ignore subclass overrides

`JsObject.jsEntries` had `if (_attrs == null || isEnumerable(...))` —
the `_attrs == null` short-circuit skipped subclass `getOwnAttrs`
overrides entirely, treating every intrinsic as enumerable. We patched it
in commit `1a67fb110`. The pattern likely exists elsewhere as
`_attrs == null` / `_map == null` / similar shortcuts.

**Sweep:** grep for `_attrs == null`, `_map == null`, `_tombstones == null`,
`__proto__ == null` (and the `!=` variants) across `JsObject.java`,
`JsArray.java`, `Prototype.java`, `JsFunction.java`. For each, ask: **does
this fast-path bypass a virtual method a subclass might override?** If yes,
delete the fast-path and route through the virtual method. The cost is
typically one method call vs one field read — measurable but not material
for non-hot-path code.

Files: `JsObject.java` and friends.

**Cost:** ~30 minutes plus EngineBenchmark verification.

Standalone — can run any time between R1 and R4.

#### 9. The `toJava` `JsFunction → JsFunctionWrapper` wrap is one-way

Once wrapped, identity is lost — different object. Anything reading raw
values from `Bindings.entrySet`/`values`/`get` then comparing identity
(descriptor checks, capture-and-replay tests) breaks subtly.

Subsumed by **(6)**: separate the storage layer from the presentation
layer, and the wrap becomes a presentation-only concern that hosts opt
into.

#### 10. `_topLevelBindings` is a write-only metadata registry

It tracks names of top-level `const`/`let` declarations for cross-eval REPL
semantics, but `getBindValue` only reads `name`/`scope` — values live
elsewhere. It's a **side-table that exists to plug a gap in the `_bindings`
model**.

With (1)+(2), the same info lives in the unified `Bindings` (`BindValue`
already has `evalId` / `scope` fields). `_topLevelBindings` and
`addBinding`/`getBindValue` can be deleted.

Files: `ContextRoot.java`, `CoreContext.java` (`findConstOrLet` callers
that route through `root.getBindValue`).

**Cost:** trivial — but only safe in the same session as (1)+(2).

### Sessions

#### R1 — Engine state consolidation (issues 1 + 2 + 3 + 10)

**Goal:** one Bindings store, one engine-state container, no `depth=-1`
sentinel, no `_topLevelBindings` side-table. `JsGlobalThis` becomes a
single-store façade.

**Order of operations (each step on its own commit if it stands alone, or
batched if interleaved):**

1. Add `hidden` (or equivalent) to `BindValue`. Default false.
2. Reroute `Engine.putRootBinding` to write a hidden entry into a single
   shared Bindings; reroute `ContextRoot.initGlobal`'s caching path to do
   the same.
3. Make `Engine.getBindings()` / `getRawBindings()` filter out hidden
   entries (preserves the existing "hidden from host inspection" contract).
4. Drop `ContextRoot._bindings` as a separate field; have `ContextRoot`
   own a single `Bindings` reference (point it at `Engine.bindings`, or
   pull `Bindings` ownership entirely into ContextRoot — pick whichever
   reads cleaner).
5. Simplify `JsGlobalThis` to read/write through the single store. Drop
   `userBindings()` helper, drop the merge logic in `toMap`, drop
   `_explicit` (the spec-default-attrs problem goes away because we
   stop having two defaults).
6. Move `assignImplicitGlobal`'s target from `depth==0` to root (one-line
   change, but only safe after step 4).
7. Delete `_topLevelBindings`, `addBinding`, `getBindValue` on
   `ContextRoot`. Update `CoreContext.findConstOrLet` to read from the
   unified store directly.
8. Drop `extends CoreContext` from `ContextRoot`; introduce a small
   parent-of-script-context interface or just type `CoreContext.parent`
   as `Object` and instanceof-check (whichever is cleaner). Update every
   `context.depth == -1` and `context.parent == root` site.

**Validation:** non-negotiables 1–4. Plus: probe `test/built-ins/**` and
diff against the snapshot (`run-2026-04-26-144956`). Net delta should be
zero or positive — this is a refactor, not a feature.

**Potential surfaces:**
- `EngineTest.testRootBindings` / `testLazyRootBindings` test the
  hidden-from-host contract. Must keep passing — adjust if the
  hidden-marker mechanism changes shape.
- `BindingsTest.testBindingsValuesAutoUnwrap` exercises the host-facing
  Map API. Should still pass; if it fails it's signal that issue (6) /
  (9) needs to land alongside.

**Definition of done:**
- Status row R1 → `done`, with commit SHA + the new pass/fail/skip
  numbers in the commit message.
- The `JsGlobalThis` write-through bullet under "Background sweeps" can
  be deleted (it's R1's responsibility).
- `JS_ENGINE.md` is **not** updated yet.

#### R2 — Unify call-site `this` binding; drop `JsObject implements JsCallable` (issues 4 + 5)

**Goal:** every call site routes its `this` binding through one helper
that does the spec substitution (null/undefined → globalThis); plain
objects are no longer callable.

**Order:**

1. Add `Interpreter.bindThisForCall(callable, receiver, context)` (or
   similar). Returns `globalThis` for null/undefined receivers,
   `receiver` otherwise. Special-case `new` (uses the new instance) and
   built-in constructor singletons (use `callable` itself when that's
   what they expect — audit during the change).
2. Replace the six (or so) `thisObject = receiver == null ? callable :
   receiver` sites in `Interpreter.java` with calls to the helper.
3. Replace `coerceThisArg` in `JsFunctionPrototype.java` with the same
   helper.
4. Drop `JsCallable` from `JsObject implements`. Drop `JsObject.call`.
5. Audit every `instanceof JsCallable` / `.call(context, args)` site.
   Plain JsObjects are no longer reachable; plain function literals,
   built-in constructor singletons, `JsBuiltinMethod`, `JsFunctionNode`
   all continue to satisfy the interface.
6. Delete the `JsMath.call` override (slice 1's TypeError-throwing
   patch) — it becomes redundant once plain JsObjects aren't callable.

**Validation:** non-negotiables 1–4. Probe full built-ins, diff. Likely
net-positive (more `Foo()` constructors throw correctly), but the deltas
in `Math/`, `Function/`, and `Object/` should all be wins or neutral.

**Definition of done:** Status row → `done` + commit + delta. The slice 1
[JS_ENGINE.md Globals note](../docs/JS_ENGINE.md#globals) about JsMath's
`call` override is now stale — leave it for the cleanup pass at the end of
the program.

#### R3 — Bindings/raw split (issues 6 + 9)

**Goal:** raw storage and host-facing auto-unwrapping are different
classes. Engine internals can no longer accidentally route through the
auto-unwrapping path and lose identity.

**Order:**

1. Introduce `BindingsStore` (or rename: keep `Bindings` for the host
   wrapper, name the raw class differently). Move all raw operations
   (`getMember`, `putMember`, `pushBinding`, `popLevel`, `getBindValue`,
   `getRawMap`, etc.) onto the store.
2. The host wrapper becomes a thin Map<String, Object> view that
   delegates to the store and applies `Engine.toJava` on read.
3. Switch every internal caller (CoreContext, Interpreter, JsGlobalThis,
   ContextRoot) to use the store directly.
4. `Engine.put`/`get`/`getBindings`/`getRawBindings` use the wrapper.

**Validation:** non-negotiables. The `JsGlobalThis.toMap` `getRawMap`
hack from commit `1a67fb110` should turn into a clean delegation; if it
doesn't, the split isn't quite right.

#### R4 (optional) — EnvironmentRecord-shaped name resolution (issue 7)

**Gate decision at the end of R3.** The clarity win is huge but the risk
is real (touches every variable lookup). Skip if R1+R2+R3 left the code
in a place that reads cleanly without it.

**Goal:** one helper resolves a name to `{ env, slot }`; `get` / `update` /
`declare` / `assignImplicitGlobal` all build on top.

**Order:** sketch a `resolveBinding(context, name)` that returns the
nearest containing `BindingsStore` + the `BindValue` (or null if absent).
Migrate `CoreContext.get` first (read-only, easiest to validate), then
`update`, then `declare`. Each migration on its own commit.

**Validation:** extra strict here — the hot path matters. EngineBenchmark
must stay within ±5%, not the usual ±10%, or we walk it back.

#### S1 — Fast-path bypass sweep (issue 8)

**Standalone, can run any time.** ~30 minutes including benchmark.

`grep -n "_attrs == null\|_map == null\|_tombstones == null\|__proto__ ==
null" karate-js/src/main/java/io/karatelabs/js/`. For each match: does the
short-circuit bypass a virtual method? If yes, route through it.

### When refactor is done

1. Final EngineBenchmark profile run; update reference numbers in
   `JS_ENGINE.md` if needed.
2. Merge the durable architectural decisions from this section into
   `JS_ENGINE.md` (Spec Invariants + Globals subsections specifically).
3. Delete the "Refactor program" section from this document.
4. Restore the test262 slice roadmap (below) as the primary forward focus.
5. Probe full test262 to set a fresh baseline and update the slice rows
   with new fail counts.

---

## Test262 slice roadmap (PAUSED until refactor lands)

Committed slice order. Picked for feedback-loop speed: small, contained slices
first surface ergonomic gaps in the harness and harden type-coercion +
error-propagation foundations that the bigger sweeps (Object/Array/Symbol)
lean on. Symbol is **last**, not first — Math/Number/Date are independent of
Symbol and only ~13% of String fails touch it (regex protocol + iterator).

| # | Slice | Fail size | What's likely in scope |
|---|---|---|---|
| 1 | `test/built-ins/Number/**` | ~95 | `parseInt`/`parseFloat` edge cases; `toFixed`/`toPrecision`/`toExponential` rounding; `Number.isInteger` / `isSafeInteger` / `isFinite` / `isNaN` distinctions |
| 2 | `test/built-ins/JSON/**` | TBD probe | `stringify` replacer-fn / array-filter / nested `toJSON` dispatch; `parse` reviver hooks; circular detection |
| 3 | Error constructors | broad cascade | `RangeError` / `ReferenceError` (and likely `EvalError` / `URIError`) not registered as globals — fixes `assert.throws(RangeError, ...)` cascades suite-wide |
| 4 | `test/built-ins/Date/**` | ~52 | `Date.parse` ISO format edges; UTC vs local hour math; invalid-date propagation |
| 5 | `test/built-ins/RegExp/**` | TBD probe | Constructor + `.source` / `.flags` / `.lastIndex`; `exec` / `test` semantics; flag-set validation; non-Symbol `String.prototype.{match,replace,search,split}` integration |
| 6 | `test/built-ins/String/**` | ~716 (Symbol-free) | `padStart`/`padEnd`, `trimStart`/`trimEnd`, `normalize`, `repeat`, `raw`, `fromCodePoint`; non-`@@`-protocol regex methods |
| 7 | `test/built-ins/Object/**` | ~1104 | `assign` / `keys` / `values` / `entries` corners; `Object.fromEntries`; descriptor-handling residuals |
| 8 | `test/built-ins/Array/**` | ~1620 | Bulk methods using `CreateDataPropertyOrThrow` + `Symbol.species`; iterator-result objects; remaining length-cluster residuals (Uint32 representation, spec-precise pop/shift interleaving — see [JS_ENGINE.md § JsArray length semantics](../docs/JS_ENGINE.md#prototype-machinery)); `Object.freeze(arr)` is a no-op today |
| 9 | `test/built-ins/Symbol/**` + cascades | ~580 SKIPs | Full Symbol primitive: `typeof === "symbol"`, unique identity, `Symbol.for` / `keyFor` registry, `description`, `Object.getOwnPropertySymbols`, `Reflect.ownKeys`. Touches `Terms.typeOf` / `eq` / coercion; property-key abstraction across `JsObject._map` / `_attrs` / `_tombstones` / `isOwnProperty`. 2–4 sessions. Unblocks the Array/Symbol.species cluster from #8 |

**Per-session ritual** (when slice work resumes): re-probe the slice with
`--only` before scoping; record before/after pass counts in the commit
message; check `EngineBenchmark profile` if changes touch the hot path.

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

## Deferred TODOs

Un-scheduled but tracked. Two flavors: large feature gaps that are intentional
non-goals today, and small harness-quality fixes that don't gate work but
should be done when convenient.

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

### Diff two run-dirs (refactor regression check)

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

This is the per-session safety check against the snapshot in the status
table above.

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
| `ReferenceError: <name> is not defined` on common classes (`ReferenceError`/`RangeError`) | Known first-order gap — those constructors are not registered globals yet (slice 3 of paused roadmap). |
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

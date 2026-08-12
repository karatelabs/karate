# JS_ENGINE_PLAN — async / await / Promise (stage 1)

**Status: design under review — not yet implemented.** This file is
transient: once the implementation lands, the durable invariants move into
[JS_ENGINE.md](./JS_ENGINE.md), the roadmap entries in
[TEST262.md](../karate-js-test262/TEST262.md) get struck, and this file is
deleted. The commit log is the audit trail.

Decision record 2026-08-12. Approved direction: **eager execution model +
CompletableFuture bridge**, **real `setTimeout` scheduler**, **unified
lifecycle seam with retrofit**. Alternatives considered are recorded below so
the review can challenge them.

---

## Why now

karate-js's bar is *run real-world JavaScript written in the wild, especially
by LLMs*. A 56-snippet idiomatic-JS battery
([karate-js-test262/etc/smoke/](../karate-js-test262/etc/smoke/)) puts the
synchronous ES2015–ES2022 core at 44/56; the dominant gap is async:
`async`/`await` don't parse, `Promise` is undefined, and
`class C { async m(){} }` silently parses as a *sync* method. test262 skips
~3.3k language tests on `async`/`Promise` features. LLM-written JS is
async-saturated; this is the single largest real-world gap.

## Constraints (recon facts, verified 2026-08-12)

1. **The interpreter is a recursive tree-walk with no continuation
   machinery.** `Interpreter.eval(Node, CoreContext)` → `evalFnCall` →
   `JsFunctionNode.call` → `bindArgsAndExecute` → `Interpreter.eval(body)`.
   Completion state is a mutable per-frame record on `CoreContext`
   (`exitType`/`returnValue`/`errorThrown`) — enough for abrupt completions,
   not suspend/resume. Suspending mid-frame requires threads or CPS.
2. **`Engine.current()` is an eval-scoped ThreadLocal** (save/restore in
   `Engine.evalInternal`/`evalRaw`/`JsFunctionNode.call`). Prototype-overlay
   resolution depends on it. Any code path that ran JS on a foreign thread
   would silently break prototype resolution.
3. **Engines are created ad hoc, one per unit of work, on the caller's
   thread** — per `ScenarioRuntime` (itself on a `Suite` virtual thread), in
   `BootLoader`, `TagSelector`, `match/Operation`, plus karate-ext
   `LintSandbox`/`Sandbox.TimeBox` and veriquant `CalcCore`/`Spec`/
   `AgentLoopService`. "Engine instances are single-threaded at a time"
   (Engine.java comment).
4. **Cancellation is cooperative.** `Interpreter.checkInterrupted()` throws
   `EngineInterruptedException` on thread interrupt; karate-ext
   `Sandbox.TimeBox` (bounded eval via `future.get(timeout)` →
   `cancel(true)`) depends on it. The async design must keep blocking points
   interruptible.
5. **Java 21 bytecode floor across karate, karate-ext, veriquant** —
   virtual threads available and already used (`Suite.runParallel`,
   `ProcessHandle`, `HarnessLoop`, `SessionList`). Callers of `Engine.eval`
   are typically already on virtual threads.
6. **Thread/lifecycle conventions** (see
   [DESIGN.md § Threading & Lifecycle](./DESIGN.md#threading--lifecycle)):
   every long-lived thread is a named daemon via
   `ThreadUtils.daemonFactory`; components keep per-class static active-sets
   with `closeAll()`/`shutdownAll()`; embedders aggregate shutdown by hand;
   there is no registry, no common `Stoppable` type, and no pool inspection.
7. **Parser status:** `async`/`await` lex as IDENT (contextual keywording is
   fully backward-compatible — `var async = 1` works today and must keep
   working). `JsFunctionNode` already carries a flag family (`arrow`,
   `isClassConstructor`, …) that `isAsync` joins. Parse-phase early errors
   live in one fused walk (`JsParser.earlyErrors`) — no new whole-tree walks.

## Alternatives considered

- **(A) Eager + CF bridge — CHOSEN for stage 1.** Async bodies run
  synchronously on the calling thread; `await` unwraps settled promises or
  parks on a `CompletableFuture`. No executor inside karate-js (except the
  shared timer). Ordering is not spec-exact (sequential, not interleaved);
  pure-JS *deferred* resolution exists only via timers/Java futures.
- **(B) Virtual-thread activations + per-Engine interpreter lock.** Each
  async call gets its own virtual thread; `await` releases the lock and
  parks. Spec-like interleaving, but needs the full event-loop +
  `Engine.current()` re-establishment + lifecycle machinery up front, and a
  caller-blocks-until-first-suspension handshake for spec's
  synchronous-start semantics. Deferred to a possible stage 2; the stage-1
  JS-visible API and CF bridge are designed so this scheduler can slot in
  behind them without breaking either the JS surface or the Java interop
  contract.
- **(C) CPS-transform the interpreter.** Rejected: a rewrite of the
  evaluator for a scripting engine that doesn't need preemptive
  interleaving; hostile to the perf budget.

## Stage-1 design

### The iron rule

**JS code only ever executes on the engine thread** — the thread currently
inside `Engine.eval(...)`. Foreign threads (timers, interop executors) only
ever *complete* `CompletableFuture`s and *enqueue* jobs; they never run JS.
This preserves constraint 2 (ThreadLocal), constraint 3 (single-threaded
engines), and needs zero locking in the interpreter.

### Per-Engine job queue (the mini event loop)

- Per-Engine `BlockingQueue<Runnable> jobs` + an `AtomicInteger pending`
  counting outstanding engine-visible completions (unsettled promises with
  subscribers + live timers).
- All promise callbacks (`.then`/`.catch`/`.finally`, await resumptions) are
  enqueued as jobs and run FIFO on the engine thread. CF completions from
  any thread `jobs.add(...)` (at minimum a no-op wake job).
- **Await loop:** evaluate operand → obtain target promise → loop: run all
  queued jobs; if target settled, break; else `jobs.take()` (blocking,
  interruptible → `EngineInterruptedException`, preserving constraint 4).
- **End-of-eval drain** in `Engine.evalInternal` (outermost eval only,
  re-entrancy guarded): drain jobs; while `pending > 0`, `take()` and
  continue. Eval returns only at quiescence — the embedding keeps its
  synchronous `Engine.eval(String) → value` contract. A far-future timer
  therefore blocks eval by design; interrupt/TimeBox is the escape hatch.
- Unhandled rejection at quiescence: WARN log with the reason; not a throw.

### JsPromise

- `JsPromise extends JsObject`, `JsPromisePrototype.INSTANCE`, toStringTag
  `[object Promise]`; constructor registered via the existing
  `ContextRoot.builtinConstructor` pattern (model: `JsMapConstructor` /
  `JsMapPrototype`).
- Backed by `CompletableFuture<Object>`; rejection carried as a wrapper
  exception holding the JS reason (Java consumers see an exceptionally
  completed CF). Public `toFuture()` for Java interop.
- `new Promise(executor)`: executor invoked synchronously with thread-safe
  `resolve`/`reject`; `resolve(thenable/promise)` adopts state.
- `.then/.catch/.finally` return derived promises; callbacks always route
  through the job queue even when already settled ("sync code first,
  callbacks after" ordering).
- Statics: `resolve`, `reject`, `all`, `allSettled`, `race`, `any`.

### await semantics

- JsPromise → await loop; fulfilled → value; rejected → thrown as JS error
  through the normal throw path (`try`/`catch` works).
- Java `CompletionStage` → auto-wrap into JsPromise, same path. **This is
  the interop story: any host method returning a CF is awaitable.**
- Thenable (callable `then`) → adopt via protocol call, `IterUtils`-style
  lookup with cooperative error checks.
- Non-thenable → passes through unchanged (spec would queue a microtask;
  eager model skips that).
- Top-level `await` is allowed (the program body is an async context) —
  scripting-engine decision; LLM-written karate scripts use it.

### async functions

- Body runs synchronously at call time; `isAsync` (set by the parser phase)
  gates result handling: normal return → fulfilled JsPromise (a returned
  promise is adopted, not nested); a JS throw inside the body → *rejected*
  promise, not a synchronous throw. Applies to declarations, expressions,
  arrows, object/class methods (fixing the silent-sync `async m(){}` bug).

### setTimeout / clearTimeout

- Shared lazy single-thread `ScheduledExecutorService`
  (`ThreadUtils.daemonFactory("js-timer-")`), registered with the lifecycle
  registry on first use.
- `setTimeout(fn, delay, ...args)` → numeric id; the timer fire enqueues a
  job into the *owning* Engine's queue (Engine captured at schedule time —
  never `Engine.current()` at fire time). `clearTimeout(id)` cancels and
  decrements `pending`.
- No `setInterval` in stage 1 (it would never quiesce); left undefined so
  feature detection behaves.

### globalThis

Bound in `ContextRoot` to the global object (small P1 item riding along).

### Lifecycle seam (built in parallel; part of this reviewable design)

In `io.karatelabs.common` (karate-js — visible to karate-core, karate-ext,
veriquant with no new dependency edges):

- `Stoppable extends AutoCloseable`: `lifecycleName()`, `lifecycleKind()`,
  `stop()` (graceful, blocking, idempotent), `default close() → stop()`,
  optional `lifecycleExecutor()` for pool inspection.
- `KarateLifecycle`: thread-safe insertion-ordered `register`/`unregister`;
  `running()` snapshot (the "inspect what's running" API);
  `shutdownAll(Duration)` in reverse registration order, per-component
  try/catch; opt-in idempotent `installShutdownHook()`; `wrap(name, kind,
  ExecutorService)` for pool-only owners.
- Retrofit: `HttpServer` and `WsClient` register/unregister, keeping their
  existing static `shutdownAll()`/`closeAll()` APIs behavior-identical.
  karate-ext/veriquant adopt at their own pace; the js-timer scheduler is
  the first new registrant.

## Known deviations from spec (accepted for stage 1)

1. **Ordering:** async bodies run to completion eagerly, so independent
   async calls do not interleave; microtask-vs-macrotask ordering is
   approximated by the job queue. test262 ordering probes will fail; the
   target workload (straight-line await chains, `Promise.all` over
   Java-backed work) behaves correctly.
2. **Starvation-by-design:** `Engine.eval` blocks until quiescence — a
   never-settled promise with a registered continuation, or a far-future
   timer, blocks eval until interrupt. (Open question 3 below.)
3. `await` on a non-thenable does not yield to the queue.
4. No `queueMicrotask`, no `setInterval`, no async iterators/`for await`.

## Questions the review should probe

1. Deadlock surface: is there any path where the engine thread can park in
   `jobs.take()` with `pending == 0` (i.e. a lost-wake bug), especially
   around `clearTimeout` racing a fire, or a CF completed *between* the
   settled-check and the `take()`?
2. Rejection identity: is a wrapper-exception-in-CF the right carrier for JS
   reason values across the Java boundary (vs. completing with a sentinel
   value), given `.exceptionally()` composition by Java consumers?
3. Should end-of-eval drain have a configurable cap (Engine option) instead
   of relying solely on cooperative interrupt for runaway timers?
4. Re-entrant eval: a job that itself calls `Engine.eval` (host callback
   into the same engine) — is the outermost-only drain guard sufficient?
5. Unhandled-rejection policy: WARN-and-continue vs. failing the eval —
   which is right for a test-automation embedding?
6. Is adopting (not nesting) on `resolve(promise)` and async-return-promise
   implemented at the right layer (CF composition vs. JS-level then)?
7. Stage-2 compatibility: does anything in the JS-visible surface or
   `toFuture()` contract paint us into a corner for the virtual-thread
   scheduler (option B)?

## Staging & verification

- **Phase A (parser):** contextual `async`/`await`, `isAsync` flag,
  AWAIT_EXPR (identity eval for now), `for await` rejected, backward-compat
  tests. Zero regressions on 1265+ unit tests; parse benchmark within ±10%.
- **Phase C (lifecycle):** seam + retrofit as above; karate-js and
  karate-core (2550+ tests) both green.
- **Phase B (runtime):** this design; lands only after review.
- **Phase D (verify):** full per-session ritual (unit tests, benchmark,
  karate-core consumer check), smoke battery (the 5 async snippets flip to
  pass; 44 stay green), un-skip `async-functions`/`Promise` in
  test262 expectations and record fresh counts, TEST262.md/JS_ENGINE.md
  updated, this file deleted.

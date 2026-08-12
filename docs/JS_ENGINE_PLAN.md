# JS_ENGINE_PLAN — async / await / Promise (revision 3)

**Status: design under review (round 3) — not yet implemented.** This file is
transient: once the implementation lands, the durable invariants move into
[JS_ENGINE.md](./JS_ENGINE.md), the roadmap entries in
[TEST262.md](../karate-js-test262/TEST262.md) get struck, and this file is
deleted. The commit log is the audit trail.

**Revision history.** Rev 1 (commit `b0cdf0932`) proposed a fully eager model:
async bodies run synchronously to completion, `await` blocks in place. An
external design review (Codex, 2026-08-12) verified the plan against the
source and returned **needs rework** with one structural blocker: *an
unresolved `await` inside an eagerly-executed body parks before the call can
return its promise*, so ordinary code deadlocks —

```js
let release;
const gate = new Promise(r => release = r);
async function f() { await gate; return 1; }
const p = f();      // rev-1: parks here forever
release();          // never reached
```

— and `Promise.all([a(), b()])` serializes instead of overlapping. Rev 2
adopted the reviewer-recommended hybrid (which rev 1 had catalogued as
alternative B): **virtual-thread activations with an engine-lock handoff** —
spec's synchronous-start semantics, suspension at the first unresolved
`await`, caller gets the promise back — plus linearizable quiescence,
interrupt integrity, eval-scoped async state, JsPromise thread-safety, the
rejection-carrier contract, and lifecycle phases.

Review round 2 validated the virtual-thread architecture as viable and
returned ten concrete amendments — chiefly: a startup-outcome protocol for
the activation handshake, a hard termination fence so a cancelled
activation's preserved stack can never re-enter a later eval, idempotent
token handles with an atomic `publishSuccessor`, serialized outer evals
with a shared-scope nesting contract, reaction-level unhandled-rejection
semantics, a leak-safe CompletionStage identity cache, and enforceable
lifecycle deadlines. Rev 3 (this text) folds all ten in.

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
   (`exitType`/`returnValue`/`errorThrown`). Suspending mid-frame requires a
   dedicated thread per suspended activation (stacks cannot migrate).
2. **`Engine.current()` is an eval-scoped ThreadLocal** (save/restore in
   `Engine.evalInternal`/`evalRaw`/`JsFunctionNode.call`). Prototype-overlay
   resolution depends on it. Every thread that runs JS must establish it for
   the duration of its locked execution span.
3. **`JsObject` state is single-threaded by design** — ordinary maps, no
   synchronization. Foreign threads must never mutate it.
4. **Engines are reusable and created ad hoc on the caller's thread** — per
   `ScenarioRuntime` (on a `Suite` virtual thread), `BootLoader`,
   `TagSelector`, karate-ext `LintSandbox`/`Sandbox.TimeBox`, veriquant
   `CalcCore`/`Spec`/`AgentLoopService`. "Single-threaded *at a time*", not
   permanently thread-affine: sequential evals may arrive on different
   threads.
5. **Cancellation is cooperative.** `Interpreter.checkInterrupted()` throws
   `EngineInterruptedException` on thread interrupt; `evalTryStmt` rethrows
   it (JS cannot catch host cancellation) and karate-ext `Sandbox.TimeBox`
   depends on `future.get(timeout)` → `cancel(true)`. All new blocking
   points must be interruptible and preserve this contract.
6. **Java 21 bytecode floor across karate, karate-ext, veriquant** — virtual
   threads available and already used. One vthread per async activation is
   an acceptable cost.
7. **Thread/lifecycle conventions** (see
   [DESIGN.md § Threading & Lifecycle](./DESIGN.md#threading--lifecycle)):
   named daemon threads via `ThreadUtils.daemonFactory`; per-class
   active-sets with `closeAll()`; embedders aggregate shutdown; no registry,
   no common `Stoppable`, no pool inspection (being fixed in this work).
8. **Parser status:** `async`/`await` lex as IDENT (contextual keywording is
   backward-compatible). `JsFunctionNode` carries a flag family (`arrow`,
   `isClassConstructor`, …) that `isAsync` joins. Parse-phase early errors
   live in one fused walk (`JsParser.earlyErrors`) — no new whole-tree
   walks.

## Alternatives considered

- **(A) Fully eager, no threads** — rev 1. Rejected by review: unresolved
  `await` inside a call deadlocks ordinary code (blocker above).
- **(B) Virtual-thread activations + engine lock — CHOSEN (rev 2).** Each
  async invocation runs its body on its own virtual thread under a
  per-Engine mutex; suspension releases the mutex and returns control to the
  caller. Spec-shaped synchronous-start via a start handshake.
- **(C) CPS-transform the interpreter.** Rejected: evaluator rewrite,
  hostile to the perf budget, unnecessary for a scripting engine.

---

## Rev-2 design

### Iron rules

1. **JS executes only under the per-Engine `jsLock`** (a fair
   `ReentrantLock`). Exactly one thread runs JS at any moment; there is no
   other synchronization in the interpreter. Every locked execution span
   establishes/restores `Engine.current()` (constraint 2) around itself.
2. **Foreign threads (timers, interop executors) never run JS and never
   touch `JsObject` state.** They may only: complete `CompletableFuture`s,
   enqueue jobs, and unpark activation threads. All JS-visible promise state
   transitions happen in code holding `jsLock`.
3. **Quiescence accounting is linearizable.** Every unit of outstanding
   async work holds exactly one *token* (see below); successor work is
   always enqueued/armed **before** the token that permitted it is released.

### Async activations (the blocker fix)

Calling an async function:

1. The caller (holding `jsLock`) creates the result `JsPromise`, spawns a
   virtual thread (`Thread.ofVirtual().name("js-async-…")`, no pool) for the
   body, releases `jsLock`, and parks on a **startup-outcome cell** (not a
   bare latch): a single-assignment `SUSPENDED | COMPLETED | FAILED` slot.
2. **Startup-outcome protocol.** The activation's entire startup — context
   creation, `Engine.enter`-equivalent establishment of `Engine.current()`,
   argument binding — runs inside a try whose `finally` **always** publishes
   an outcome: any pre-body failure publishes `FAILED` (after settling the
   result promise as rejected); vthread start failure is detected by the
   caller and treated as `FAILED` too. If the caller's outcome-wait is
   interrupted, it cancels the scope (below) and exits via
   `EngineInterruptedException`. The result promise is always settled
   *before* `COMPLETED`/`FAILED` is published.
3. The activation runs the body under `jsLock`. Completion without
   suspension: settle promise → publish `COMPLETED` → release lock (in
   `finally`) → exit.
4. At the first **unresolved** `await`: arm resumption
   (`whenComplete` → `LockSupport.unpark(activationThread)` — chosen
   precisely because the unpark permit survives an unpark-before-park race;
   a condition flag would not), publish `SUSPENDED`, release `jsLock`
   (release is in a `finally` so an exception between arming and unlocking
   cannot strand the engine), then `LockSupport.park()`. The caller wakes on
   the outcome, reacquires `jsLock`, and continues with the promise in
   hand — spec's run-until-first-suspension semantics.
5. On unpark, the activation **validates its scope gate, reacquires
   `jsLock`, and validates the gate again** (see eval scopes: a stale
   activation whose scope is closed exits without executing any JS),
   re-establishes `Engine.current()`, and continues from the await on its
   preserved stack. Settled-await fast path: an `await` whose target is
   already settled does not suspend (accepted deviation — no microtask
   yield).
6. Each live activation holds one token; released (after settling the
   promise and enqueuing its callback jobs) per iron rule 3.

Top-level `await` (the program body is the one non-activation async
context; a plain non-async function never contains an AWAIT_EXPR — the
parser resets the async context at function boundaries) uses the **pump
loop**: release `jsLock`, `jobs.take()` (interruptible), reacquire, run the
job, recheck the target. The eval thread parked in the pump is what lets
activations and callbacks interleave with top-level code.

### Job queue and tokens

- Per-Engine queue with **two job classes kept structurally distinct** —
  *microtask* (promise callbacks/resumptions) and *timer* — even though the
  stage-1 pump drains both FIFO. (Stage-2 ordering can then change without a
  data-model rewrite; drain order is explicitly not a documented contract.)
- **Tokens are individual idempotent handles, never bulk counter
  decrements.** Each token is an object with a CAS `LIVE → RELEASED` state;
  double-release is a no-op (assertable in tests). Owners (one each): live
  timer, live activation, queued job, armed-but-unsettled external-CF
  subscription.
- **The scope is one linearizable abstraction** — scope state
  (`OPEN`/`CLOSED`), token acquisition, enqueue, and close all go through a
  single guarded facade. Its core operation is
  `publishSuccessor(oldToken, successorWork)`: atomically either (a) the
  scope is `OPEN` → acquire the successor token and enqueue the work,
  *then* release `oldToken`; or (b) the scope is `CLOSED` → reject the
  publication, release only `oldToken`, and drop the work. A foreign
  completion thread can therefore never publish into a closed scope, the
  count can never go negative, and quiescence can never be observed with
  work in flight:
  - timer fire: `publishSuccessor(timerToken, timerJob)` after winning the
    `LIVE → FIRED` CAS (a `LIVE → CANCELLED` winner just releases);
  - subscription completion: `publishSuccessor(subToken, callbackJobs)`;
  - job execution: token released in `finally` after the job runs (a
    throwing job must not strand the pump — see error routing).
- **Quiescence** = no live tokens **and** queue empty, evaluated inside the
  same facade (never by separately reading a queue and an atomic).

### Engine.eval contract and eval scopes

- `Engine.eval` keeps its synchronous `String → value` contract: after the
  program body completes, the **end-of-eval drain** pumps until quiescence,
  then returns. If the program's completion value is a promise, eval awaits
  it and returns the unwrapped fulfillment value (a rejection is thrown as
  the eval result and counts as *observed* — it is not double-reported by
  the unhandled-rejection policy). Blocking eval is a *view over the
  scope's result*; a future non-blocking eval API is a second view over the
  same scope, so nothing here precludes it.
- **Outer evals are serialized per engine.** A second host thread calling
  `eval()` on an engine whose scope is still open (including its drain and
  teardown interval) blocks until the scope fully closes. **Nested eval on
  the same thread** (a job or host callback calling `Engine.eval`
  re-entrantly) does not open a new scope: it shares the outer scope and
  pump owner, tracked by an eval-depth counter; only depth 0 opens/closes a
  scope and runs the end-of-eval drain. Nested top-level `await` pumps the
  shared queue (which may run unrelated outer jobs — documented behavior of
  re-entrant eval). Listener/debug eval-IDs during nesting follow today's
  `evalInternal` behavior.
- Each outermost eval opens an **async scope object** — the scope's
  *object identity* is the generation stamp (explicitly **not**
  `ContextRoot.evalId`, a wrapping `short` incremented by nested evals and
  unsuitable as a generation id). Every job, timer, subscription, and
  activation holds a reference to its scope.
- **Cancellation (interrupt, drain-cap expiry, internal error) is a
  fenced, awaited teardown:** (1) the scope's facade transitions to
  `CLOSED` — from this instant `publishSuccessor` rejects all late
  publications and the **activation reacquisition gate** is shut; (2) live
  timers are CAS-cancelled and subscriptions detached where possible; (3)
  every live activation's vthread is interrupted **and teardown waits for
  each activation to terminate** (bounded by the teardown deadline; parked
  activations exit immediately via the gate check + interrupt, running
  activations exit at their next `checkInterrupted` poll or blocking
  point); (4) queued jobs of the scope are discarded, their tokens
  released. An activation that observes a closed scope at its gate check —
  before *or after* acquiring `jsLock` — exits without executing any JS.
  **No subsequent outer eval may start until teardown completes**, so a
  cancelled activation's preserved stack can never mutate a later eval's
  state. This closes both reused-engine leakage and the zombie-activation
  hazard.
- **Drain cap:** `Engine` option (e.g. `setAsyncDrainTimeout(Duration)`),
  default off (cooperative interrupt / `Sandbox.TimeBox` remain the outer
  bound). On expiry: cancel the scope as above and throw a distinct
  host-level timeout exception that JS cannot catch (same routing class as
  `EngineInterruptedException`). karate-core may choose a default for
  scenario evals.

### Interrupt integrity (review: major)

At every async boundary — job dispatch, activation body wrapper, thenable
adoption, timer callback — `EngineInterruptedException` and the engine's
internal flow-control signals are **rethrown before** any catch-and-reject
conversion. `InterruptedException` from parks/takes is translated to
`EngineInterruptedException` with interrupt status restored. Host
cancellation must never become a catchable promise rejection.

### JsPromise

- `JsPromise extends JsObject` for prototype/JS-surface purposes, but **its
  settlement state lives exclusively in its `CompletableFuture`** — the only
  cross-thread-touchable field. Handler registration lists and any JS-visible
  status are mutated only under `jsLock` via queued jobs (iron rule 2).
  `JsPromise` is **not** a `JsValue` (it must not be auto-unwrapped by
  `Engine.toJava()`); it is exposed to Java as a public type (see identity
  contracts).
- `new Promise(executor)`: executor invoked synchronously under `jsLock`
  with thread-safe `resolve`/`reject` functions (callable later from any
  thread — foreign calls complete the CF and enqueue jobs only).
- Adoption is centralized in one `resolveValue` operation shared by the
  constructor's resolve, `Promise.resolve`, async-function return,
  `.then`-callback results, and CompletionStage wrapping. JS-level rules run
  before any CF composition: self-resolution → `TypeError`;
  `Promise.resolve(p)` preserves native-promise identity; thenable handling
  reads `then` once via a property access that may throw, tolerates
  double/repeated resolve calls (first wins), and converts a `then` that
  throws after resolving per spec. Callback invocation always routes through
  the job queue.
- `.then/.catch/.finally` return derived promises; callbacks go through the
  queue even when already settled ("sync code first, callbacks after").
- Statics: `resolve`, `reject`, `all`, `allSettled`, `race`, `any`
  (`AggregateError` with an `errors` array for `any`).
- `new` on an async function → `TypeError` (async functions are not
  constructors); enforced in the construction paths
  (`Interpreter.evalFnCall` / `invokeAsConstructor`).

### Rejection carrier across the Java boundary (review: major)

A **public** wrapper type, e.g. `JsRejectionException extends
RuntimeException`, carrying the exact JS reason object (`getReason()`).
Contract:

- JS rejection → Java: `toFuture()` completes exceptionally with the
  wrapper; Java consumers see it per CF norms (possibly inside
  `CompletionException`/`ExecutionException`).
- Java → JS unwrap rules, in order: unwrap `CompletionException` /
  `ExecutionException` **one layer**; if the cause is the engine's wrapper →
  the original JS reason; `EngineInterruptedException` → rethrown as host
  cancellation (never a reason); `CancellationException` → rejection with a
  JS `Error` describing cancellation; any other `Throwable` → converted by
  the existing host-error rules (`evalTryStmt`'s conversion table).
- Only the engine's own wrapper is ever unwrapped to a reason; arbitrary
  Java exceptions never masquerade as JS rejections.

### Identity and Java-API contracts (review: omissions)

- `toFuture()` returns the **same CF instance** for the same `JsPromise`,
  representing eventual settlement (scheduler-neutral: no thread, ordering,
  or drain guarantees; cancelling it does **not** cancel the JS activation —
  it is a view, and this is documented).
- Java `CompletionStage` → JS: wrapped on first crossing. Identity is
  **scope-scoped, not engine-permanent**: a per-scope identity cache
  (reference-equality keys — `CompletionStage.equals` must never merge
  distinct stages) guarantees the same stage yields the same `JsPromise`
  *within an eval scope*; the cache is dropped wholesale at scope close, so
  a long-lived shared engine cannot accumulate stage/wrapper graphs
  (weak-key maps were rejected: the wrapper's CF graph can strongly reach
  the stage and defeat weak keys). Identity across scopes is explicitly
  not promised. Round-tripping a `JsPromise`'s own `toFuture()` back into
  JS always recovers the original `JsPromise` (direct reverse association
  on the wrapper, independent of the cache).
- A host call that invokes an async JS function directly
  (`JsFunctionWrapper.call` applies `Engine.toJava` today) receives the
  `JsPromise` (not auto-awaited); `Engine.toJava` is taught to pass it
  through unchanged. Helper `JsPromise.await()`/`join(Duration)` provided
  for Java callers that want blocking unwrap.

### Unhandled rejections and timer-callback throws (review flipped rev 1)

Default: **fail the eval.** Handledness is tracked at **reaction level**,
not as a lineage-wide boolean:

- Attaching a *rejection* handler (`.catch`, `.then(_, h)`, the second
  `finally`-rethrow path) marks that promise handled. Attaching only a
  fulfillment reaction (`.then(f)`, `.then()`) does **not** — it transfers
  rejection responsibility to the derived promise (so
  `Promise.reject(x).then(v => v)` reports the *derived* promise as
  unhandled, exactly once). `.then(null, h)` handles the source even if the
  derived promise later rejects for its own reason (a fresh
  responsibility).
- The decision is made only at scope quiescence, so a later-queued
  `.catch` counts as handled. Each terminal unhandled rejection is
  reported **once**.
- A rejection observed as the eval's own top-level result (thrown from
  `Engine.eval` per the eval contract) is marked handled — no duplicate
  report.
- Reporting belongs to the **async scope**, not to the synchronous drain
  per se — blocking eval merely waits for the scope and relays its result
  (keeps a future non-blocking eval honest).

The first unhandled rejection fails the eval as a JS-level error; the rest
are logged. A thrown timer callback follows the same policy. Engine option
to downgrade to WARN-only; warnings go through the `console` consumer
(`ContextRoot.setOnConsoleLog`) as well as SLF4J.

### setTimeout / clearTimeout

- Shared lazy single-thread `ScheduledExecutorService`
  (`ThreadUtils.daemonFactory("js-timer-")`), registered with
  `KarateLifecycle` on first use.
- Owner resolution: **from the call's `CoreContext.getEngine()`**, validated
  against `Engine.current()`; scheduling without a definite engine is an
  error. Timer ids are engine-local; records use the CAS state machine
  above. Delay coercion: `NaN`/negative/non-numeric → 0; fractional →
  floored; missing/non-callable callback → `TypeError`; extra args passed
  through to the callback.
- No `setInterval` in stage 1 (never quiesces) — left undefined.

### globalThis

Bound in `ContextRoot` to the global object (small P1 item riding along).

### Lifecycle seam (amendments from review)

- Registry gains explicit phases `RUNNING → SHUTTING_DOWN → STOPPED`.
  `register()` during shutdown: the component is stopped immediately (and a
  WARN logged) rather than silently surviving. Identity-based uniqueness;
  re-registration moves to the tail; `running()` is an immutable snapshot.
- `Stoppable.stop()` is the *blocking, graceful* contract.
  `HttpServer.stopAsync()` / `WsClient.closeNow()` keep their existing
  async/immediate semantics as distinct entry points — `shutdownAll()` does
  not redefine them, it calls the blocking path with the deadline.
- `shutdownAll(Duration)` is a **total deadline made enforceable**: each
  component's blocking `stop()` runs in an interruptible worker bounded by
  the *remaining* deadline (a hung first component cannot starve the
  rest — it is interrupted, marked timed-out, and the sequence continues).
  Stops run in reverse registration order with per-component try/catch;
  the call returns a result summary (stopped / failed / timed-out per
  component) as well as logging it. Concurrent or reentrant callers (a
  `stop()` calling `shutdownAll()`) **join the in-progress shutdown's
  result** rather than getting a silent no-op. `register()` after shutdown
  has begun uses the immediate bounded-stop path with whatever deadline
  remains.

## Accepted deviations from spec (stage 1)

1. Settled-`await` fast path does not yield to the microtask queue.
2. Pump/queue ordering approximates microtask-vs-macrotask ordering; drain
   order is explicitly **not** a documented contract (stage-2 freedom).
3. `Engine.eval` blocks to quiescence by design; far-future timers block it
   (interrupt / drain cap are the escape hatches).
4. No `queueMicrotask`, `setInterval`, async iterators / `for await`.
5. Fair-lock scheduling order among runnable activations is approximate,
   not spec's exact job ordering.

## Questions for review round 3

1. Verify each round-2 amendment is now actually specified tightly enough
   to implement without relying on unstated race behavior — in particular
   the startup-outcome cell, the `publishSuccessor` facade, and the fenced
   awaited teardown.
2. The teardown wait is bounded by the teardown deadline; if an activation
   is stuck in host code that never polls interruption, teardown times out.
   Specify-check: is "engine poisoned — subsequent evals fail fast with a
   descriptive error" the right terminal state for that residual case (vs.
   waiting forever)? (Proposed: yes, poison the engine.)
3. Any remaining hole or stage-2 lock-in.

## Staging & verification

- **Phase A (parser): done, green.** Contextual `async`/`await` (no new
  token types; one guarded lookahead ahead of `ref_expr`), `Node.async`
  flag on FN_EXPR/FN_ARROW_EXPR carried into `JsFunctionNode`, AWAIT_EXPR
  (identity eval placeholder), `inAsync` save/restore per function body
  (top level is async; plain functions reset it), `for await` rejected,
  backward-compat pins (`var async = 1`, `{async:1}`, `a.async`, `await`
  as identifier outside async contexts). 1289 karate-js tests green;
  benchmark within noise of baseline; smoke battery unchanged at 44/56
  with the async snippets now failing at *runtime*, not parse.
- **Phase C (lifecycle):** seam + retrofit built and green in a worktree;
  rev-3 amendments (phases, enforceable remaining-deadline workers,
  join-in-progress semantics, result summary) to be applied before merge.
- **Phase B (runtime):** this design; lands only after review sign-off.
- **Phase D (verify):** full per-session ritual (unit tests, benchmark,
  karate-core consumer check), smoke battery (5 async snippets flip to
  pass; 44 stay green), **concurrency barrier tests** for: start failure,
  interrupt-before-started, unpark-before-park, close-vs-publish races,
  cancellation of a running activation, stale reacquisition, nested eval,
  scope-cache reclamation; un-skip `async-functions`/`Promise` in test262
  expectations and record fresh counts, TEST262.md/JS_ENGINE.md updated,
  this file deleted.

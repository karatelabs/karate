# CdpDriver Refactoring Plan

> **Working plan** for refactoring `karate-core/src/main/java/io/karatelabs/driver/cdp/CdpDriver.java`
> to be elegant, DRY, and robust — without destabilizing logic that was stabilized under fire.
> Companion to [DRIVER.md](./DRIVER.md) (architecture reference). This file is the *execution*
> tracker: update the Status columns and the Progress Log as work lands, so any session can
> pick up where the last one stopped.
>
> **Analysis baseline:** 2026-07-15, HEAD `a24051c4e`. `CdpDriver.java` = 4,308 lines;
> `cdp/` package = 6,950 lines. Line references below drift — method names are authoritative.
>
> **Two things happened after the plan was first written; both are already folded into the
> text below, and the Progress Log has the chronology.** (1) A dependency sweep of the known
> external consumer of the CDP surface produced §4 — it *reversed* one planned fix
> (`Network.enable` stays and must be re-armed on tab switch, F6/B6), added the transport-tab
> hazard (F8/B7), and froze the extension-surface API through all tranches. **Read §4 before
> touching anything it names.** (2) A parallel-isolation fix landed ahead of the tranches:
> pooled slots now get **their own incognito browser context** rather than sharing the default
> one. That closed F7, reduced F5/B5 to a residual, half-landed A2 and A8, retired the
> "keep every `@lock`" ground rule, and changed one §4 contract (`switchPage` is now
> context-scoped, not browser-wide).

---

## How to resume

1. Read this file top to bottom (10 minutes). Skim [DRIVER.md](./DRIVER.md) §"Loader-bound
   page-load waits" and §"Readiness waits are event-completed futures" — those two sections
   describe the invariants that must survive any refactor.
2. Check the Progress Log at the bottom for what has already landed.
3. Work tranche by tranche, **in order** (A → B → C → D). Do not start D (test reduction)
   until A–C have soaked in CI for at least a week of normal commit traffic.
4. Validation after every tranche (and after every B item individually):

   ```bash
   # full driver e2e (Docker required) — the parallel(2) crucible
   mvn test -pl karate-core -Pcicd -Dtest=DriverFeatureTest
   # repeat 3–5x locally; one green pass proves little for timing bugs

   # full cicd profile before pushing
   mvn -B verify -Pcicd

   # W3C job (only needed when Driver.java / W3cDriver.java are touched)
   mvn -B verify -Pw3c -pl karate-core -am
   ```

5. Ground rules, non-negotiable:
   - **The loader-binding cascade is relocation-only.** `isDomReady()` /
     `checkDocumentReadyState()` / `verifyJsExecution()` predicates stay byte-identical.
     They encode ~6 forensically-discovered Chrome behaviors and had ONE green CI run at
     baseline.
   - Zero-behavior-change items must produce zero semantic diffs — if a refactor "improves"
     logic along the way, split it out and treat it as a B-item with its own validation.
   - Keep `parallel(2)`. **`@lock` tags are no longer frozen** — but a lock may only be removed
     against a *test that proves the shared state is gone*, never against a green run (a green
     local run proves nothing here; see the cookie/tab entries in the Progress Log, both of
     which pass locally even on the broken code). The 9 surviving tags are not isolation locks
     and no further isolation work will retire them: `@lock=render` (frame/retry/wait-callable)
     and the `@lock=*` on keys/mouse/screenshot/dialog are **CPU-contention** locks — renderer
     starvation is a function of runner size, not of what state drivers share; `call-driver`'s
     scenario-level `@lock=*` is a **driver-lifecycle** lock (it quits and re-inits the driver);
     `@lock=oopif` has no recorded rationale — establish one before touching it.
   - **The CDP extension surface is public API** (§4): signatures frozen through A–C, and the
     behavioral contracts listed there are load-bearing for at least one external consumer. A
     tranche isn't done until that consumer has been rebuilt against the change and its driver
     e2e suite is green (§4, validation protocol).

---

## 1. Why this plan exists — the stability story (evidence)

Verified against git history and GitHub Actions logs at baseline:

- 74 commits, 5,560 lines churned (4,817 ins / 743 del) to reach 4,074 lines. Waves:
  genesis (Dec 2025), race-condition firefight (Jan 2026, 27 commits), OOPIF (May,
  `9d68536d4`), context hardening (June, 14 commits incl. `ca6c9adea` event-completed
  futures), and **nine commits on 2026-07-14** (~945 lines churned in one day — loader
  binding `6e64e0e5e`, event-nudged load wait `43f072a51`, iframe readiness futures
  `51293cfbf`, replaced-loader recovery `0f6a72cda`→`de4268448`, abort retry `d3955a0c1`,
  timer-based pool probe `96c9f950a`).
- **Of the 100 CI runs before baseline, 34 failed — every sampled failure (7/7, spanning
  Jul 9–14) failed on exactly one test: `DriverFeatureTest.testDriverFeatures`.** The
  driver suite was *the* CI failure mode, and the failing scenarios match the Jul 14
  commit messages (page-load timeouts on `/wait`, `/iframe`; stale-document matches;
  `call-chain-login` failures).
- **Exactly one green CI run exists since the final fix.** Stability is plausible but
  statistically unproven — hence: aggressive refactors only where mechanical, and CI soak
  before test reduction.
- Reframing: the flakiness was largely **signal**. Loader identity, session poisoning,
  the Fetch-enable leak, timer-starved renderers — all real production defects that the
  `parallel(2)` suite surfaced. The suite earned its keep; treat it as the safety net.

## 2. What is genuinely good — do not disturb

- The three-tier synchronization design: event-completed futures for event-announced
  state (`mainContextReady`, `frameContextReady`, `pendingTargetRemovals`, the `loadTick`
  nudge latch); capped polls only where no CDP event exists (`readyState` fallback,
  `pruneStaleFrames`, OOPIF readiness, per-context "can run script" probe); `retry()`
  kept deliberately separate as the user-facing diagnostic contract
  (`retryCount × retryInterval` + per-attempt WARN).
- The loader cascade, including its demoted-but-alive branch: the `requireUrl` path in
  `checkDocumentReadyState()` (~1502–1524) is a fallback-of-a-fallback after `de4268448`,
  but it uniquely covers double-event-loss, and the superseded-loader branch's URL check
  is the ONLY completion path for same-document history traversals and BFCache restores.
  **Keep every predicate.**
- `CdpClient`'s two-lane dispatch (frame router vs event dispatcher) — correct fix for
  the handler-blocks-its-own-response deadlock. Untouched since June; leave it.
- `PooledDriverProvider`'s two-checkpoint liveness (probe on release AND after reset).

## 3. Latent bugs found in review (inputs to Tranche B)

| # | Finding | Where (baseline) |
|---|---------|------------------|
| F1 | **`submit()` has never worked on any backend.** The port (`19c61b2d7`) wired `waitIfSubmitRequested()` only into the default `Driver.click()` — but `CdpDriver.click` and `W3cDriver.click` both override it without the call. The wait silently no-ops; the recorded hash is never cleared. No test covers it. | `Driver.java:804-808`, `CdpDriver.click`, `W3cDriver.click` |
| F2 | **`Driver.select(int)` default drifted**: dispatches only `input`+`change`, while the CDP override uses `Locators.commitFieldEventsJs` (full `input/change/blur/focusout`, added by `20467b5c8` for blur-committing frameworks). W3C inherits the stale default. | `Driver.java:915-923` |
| F3 | **Cross-thread fields not `volatile`**: `currentTargetId` written by `activateTarget` (scenario thread), read by the `Target.targetCreated` handler (event thread); `currentFrame` written to `null` by the `Target.detachedFromTarget` handler (event thread), read/written by the scenario thread. | `CdpDriver` fields |
| F4 | **Pool reset doesn't reset frame state.** A scenario ending switched into an OOPIF makes `resetDriver()`'s `setUrl("about:blank")` go out on the *iframe's* CDP session — navigating the iframe, not the page; the main page keeps the previous scenario's document. Same-origin case leaves `currentFrame` stale into the next scenario. Currently masked by loader binding, but the reset silently isn't doing its job. | `PooledDriverProvider.resetDriver` |
| F5 | **`drainOpenedTargets()` queues tabs the driver did not open.** The `onTargetCreated` handler is unfiltered, so every new tab in the browser lands in its queue. *Narrowed by the isolation fix, not closed:* cross-*driver* pollution is gone for **pooled** drivers (siblings sit in other contexts, and `pageTargets()` filters enumeration by `browserContextId`, pinned by `BrowserContextIsolationTest`). What remains is every tab opened in the driver's **own** context — which is not a corner case: it is the normal shape of any driver in the default context attached to a browser a human also uses, i.e. the §4 consumer. Filtering the handler by `browserContextId` does **not** fix that (see B5). | `onTargetCreated` handler |
| F6 | **`Network.enable` is load-bearing for the external event stream — and is LOST on tab switch.** The init comment ("Required for cookie operations", origin `0682c0065`) is stale: cookie *commands* work without it (`activateTarget` never re-enables it, cookie scenarios still pass). But external `CdpEventListener` consumers — the API is documented for traffic recording — depend on the Network event stream, so the enable must **stay** (an earlier draft of this plan proposed removing it as vestigial; the §4 sweep reversed that). The actual bug is the inverse: `activateTarget()` re-arms Runtime/Page/lifecycle/auto-attach but **not Network**, so after any `switchPage`/`switchPageById`/`close()` the stream goes dark on the new session. Same class: `Fetch.enable` isn't re-armed either — an active `intercept()` silently stops pausing requests after a tab switch. | `CdpDriver.initialize` (~line 546), `activateTarget` |
| F7 | `waitForPageLoad` throws raw `RuntimeException` where everything else throws `DriverException`. (F7's other half — `findPageTarget` session-scoped while `getPages` was browser-level — is fixed; both go through `pageTargets()` now.) | `waitForPageLoad` |
| F8 | **Closing the transport tab kills the driver.** The WebSocket is page-bound (`/devtools/page/<id>`); closing that tab — directly, or via `switchPageById` + `close()` — drops the connection, and every later CDP call fails with CONNECTION_CLOSED with nothing pointing at the cause. The transport targetId is extracted at connect into `currentTargetId`, but every `activateTarget` overwrites that field — so the value is not durably retained, `close()` has no guard against it, and there is no public accessor; multi-tab callers are left re-deriving it by parsing the ws URL themselves. | `close()`, `extractTargetIdFromUrl` |

Also load-bearing context: the `Driver` interface already has default implementations for
the entire element-op surface (`Driver.java:804-1035`); `CdpDriver` re-implements ~15 of
them adding only `retryIfNeeded` + `BaseElement.existing`, and `W3cDriver` re-implements
them again. Three near-identical copies is what bred F1 and F2.

## 4. External consumers — the CDP extension surface is public API

`CdpDriver`'s CDP-only surface is consumed by tooling built on karate-core outside this repo
(karate-max — already named in `CdpDriver`'s own comments). A dependency sweep of that consumer
was folded into this plan; the downstream specifics stay in that repo's own backlog. What matters
here: **treat the following as public API through every tranche — signatures frozen, and the
listed behaviors are contracts, not implementation details.**

**Frozen surface (signatures unchanged through A–C):**
`addCdpEventListener`/`removeCdpEventListener` · `CdpEventListener` · `addInitScript`/
`removeInitScript` · `addBinding`/`removeBinding` · `addScriptToEvaluateOnNewDocument`/`remove…` ·
`objectId(locator)` · `getCdpClient()` (and `CdpClient.method/browserMethod` fluency) ·
`getTargetInfos()` · `drainOpenedTargets()` · `switchPageById` · `getFrameOwnerBackendNodeId`/
`describeNode` · `CdpDriver.connect`/`start`/`connectNewContext` · `isResponsive`/`isReady`/`waitUntilReady` ·
`CdpLauncher.getWebSocketUrl` · `CdpDriverOptions.Builder` · `Locators`' public JS generators
(`existsJs`, `findAllJs`, `selector`, `toFunction`, …) · `DialogOpenedException`.

**Behavioral contracts (verified in downstream use):**
- External listeners receive **every** CDP event, in arrival order, on the single serialized
  `cdp-event-*` dispatch thread; consumers design their handlers around exactly those semantics.
  Tranche C must not move external dispatch off that thread or start filtering events by session.
  Promote this from a `CdpClient` implementation note to the `CdpEventListener` javadoc (small
  C-tranche documentation task).
- The **Network event stream flows on the driven session** — keep `Network.enable`, and re-arm it
  in `activateTarget()` (F6/B6).
- Init-script registry semantics: idempotent by name, dependency-ordered, injected into the
  current document immediately and into every new document thereafter; `removeInitScript` leaves
  the live copy in place. `driver.js` **extends, never clobbers**, a partial `window.__kjs`
  seeded by a co-installed module (the guard in `ensureKjsRuntime`).
- A script that itself opens a dialog returns `null` (the `DialogOpenedException` path in
  `cdpEval`), and an unhandled dialog stays observable via `getDialog()` polling — consumers
  drive dialogs without registering an `onDialog` handler.
- `drainOpenedTargets()` keeps reporting popups/new tabs opened *by the driven page* — the
  consumer's post-click tab detection rides this. Both candidate B5 filters preserve that flow
  (a popup shares its opener's context *and* is opener-linked), but only `openerId` scoping
  gives the consumer the fix it needs: it is **default-context** (drivers via `connect`/`start`
  only, never `connectNewContext`), so a `browserContextId` filter is a no-op there and leaves
  it queueing unrelated tabs from the browser it attaches to. See F5/B5 — that is a decision to
  make *before* implementing B5, since the wrong choice is invisible to every test.
- **CHANGED (`a24051c4e`).** `switchPage(String)` substring-matches title/url **within the
  driver's own browser context**, not browser-wide; same for `getPages()` and `getTargetInfos()`
  (which still marks the driven tab `active`). It diverges from the old behavior only for a
  driver in its own incognito context (the pooled case) — which is the entire point. For a
  driver in the DEFAULT context every ordinary tab is in that same context, so it is a no-op.
  **Downstream impact: none, verified by inspection** — the known consumer builds drivers only
  via `CdpDriver.connect(wsUrl, …)` / `CdpDriver.start(…)` and never creates a browser context,
  so all of its drivers are default-context and its enumeration/`switchPage` calls are
  unaffected. The rebuild + e2e leg of the validation protocol is still owed; the contract
  question it was flagged for is settled.
- `PageLoadStrategy.DOMCONTENT` is a first-class strategy downstream — not just the
  `DOMCONTENT_AND_FRAMES` default this repo's suite runs (see test additions below).

**New-API candidates the sweep motivates (small, optional, post-B):**
- `getConnectedTargetId()` + the `close()` transport-tab guard — B7/F8.
- An **awaitPromise variant of `script()`** (e.g. `scriptAwait(js)`): `Runtime.evaluate` with
  `awaitPromise` is already used internally (`isResponsive`); exposing it lets callers await
  page-side async work without hand-rolling injected-state polling.

**Test additions (ride along with Tranche D — the reduction must not orphan this surface):**
Today this surface has near-zero OSS coverage (`InitScriptE2eTest` pins the registry; nothing
pins the rest — it is tested only by downstream e2e). Add contract pins beside
`InitScriptE2eTest`:
- binding round-trip: `addBinding` → page-side call → `Runtime.bindingCalled` observed at an
  external `CdpEventListener`;
- external listener receives Network events on the initial session **and after `switchPage`**
  (guards the B6 re-arm);
- `objectId(locator)` → `DOM.setFileInputFiles` against `input.html` (the file-upload path);
- the partial-`__kjs` extend-don't-clobber guard;
- one `DOMCONTENT`-strategy run/scenario (loader binding under the lighter strategy has no OSS
  sentinel today).

**Validation protocol:** a tranche isn't done until the downstream consumer has been rebuilt
against the changed core and its driver e2e suite is green (that repo documents its own loop).
Compile breaks are cheap to see; the contracts above are what break *silently*.

## 5. Execution plan

### Tranche A — mechanical DRY, zero behavior change

Estimated net: −550 to −650 lines in `CdpDriver.java`. All CDP-local (no `Driver.java` /
W3C changes). Validated by the full e2e suite; each item is independently revertable.

| # | Status | Change | Detail |
|---|--------|--------|--------|
| A1 | ⬜ | Extract event handlers into named methods | `setupEventHandlers()` (~340 lines of inline lambdas) becomes a registration list — `cdp.on("Page.lifecycleEvent", this::onLifecycleEvent)` etc. — plus focused private methods. The dialog handler alone is ~70 inline lines. `onRequestPaused` already shows the pattern. Biggest readability win. |
| A2 | 🟨 | Collapse element-op wrappers via two private helpers | The write helper already exists as `elementAction(locator, js)` (`retryIfNeeded` → `script` → re-resolve on `Locators.ELEMENT_NOT_FOUND`); click/focus/clear/value-set/select/position route through it, and it returns `Object`, not `Element`, so callers still do their own `BaseElement.existing`. **Remaining:** add the `read(locator, js)` helper (`retryIfNeeded` → `script` cast) for text/html/value/attribute/property/enabled, and fold in scroll/highlight. Keep the read helper generic — `property()` returns raw `Object`, `enabled()` collapses to `Boolean.TRUE.equals(...)`, `position(locator, relative)` keeps its bespoke relative branch; `input` keeps its bespoke body. Do **not** re-add a separate `interact` — extend `elementAction`, whose retry is load-bearing (it fixes a real TOCTOU, see Progress Log). |
| A3 | ⬜ | Window management | One `windowId()` lookup + one `setWindowState(String)`; `maximize`/`minimize`/`fullscreen` differ by one string; `getDimensions`/`setDimensions` share the lookup. |
| A4 | ⬜ | Navigation twins | `refresh()`/`reload()` → private `reload(boolean ignoreCache)`; `back()`/`forward()` → private `traverseHistory(int delta)` (bodies are copy-paste with `±1`). Superseding-navigation arm/clear semantics unchanged. |
| A5 | ⬜ | Generic `retryFor<T>` | Value-returning variant of `retry()` with the same WARN-logging contract. Kills both holder-array hacks: `waitForChildFrames`'s `List[] holder` and the OOPIF match's `String[3] matched`. |
| A6 | ⬜ | `rawEval(expression, contextId)` helper | ~10 literal call sites hand-roll one-shot no-retry `Runtime.evaluate` (probes, diagnostics, readyState fallback, OOPIF checks, frame-context probe). Centralize; keep the "bypasses the retry pipeline" distinction explicit in the helper's javadoc. (`isResponsive`'s `awaitPromise` form stays bespoke.) |
| A7 | ⬜ | `waitOrThrow` helper for the `waitFor*` family | Every wait method repeats `pollFor(...); if (null) throw new DriverException("timeout waiting for …")`. Bodies become 1–3 lines. `retry()` vs `pollFor()` separation stays. |
| A8 | 🟨 | Single target enumeration | Enumeration done (landed with the isolation fix, not as a refactor): `pageTargets()` is browser-level, filters by `browserContextId`, and powers `getPages`/`getTargetInfos`/`findPageTarget`, closing F7's session-scoped inconsistency. It turned out **not** to be zero-behavior-change (see the §4 `switchPage` contract), which is why it did not stay in Tranche A. **Remaining:** the shared fast-poll loop for `switchPage(String)`/`switchPageById`. |
| A9 | ⬜ | Screenshot unification | `screenshot(embed, timeout)` and `screenshot(locator, embed)` share one private `capture(clip, timeout, embed)`. |
| A10 | ⬜ | Hygiene | `Frame` → record; fully-qualified `java.util.*` → imports; magic numbers (150ms abort backoff, 1000/2000ms context bounds) → named constants; `waitForPageLoad` throws `DriverException` (F7). |

### Tranche B — targeted robustness fixes

Small, independently verifiable; land as separate commits so a CI regression bisects
cleanly.

| # | Status | Fix | Validation |
|---|--------|-----|------------|
| B1 | ⬜ | `volatile` on `currentTargetId` and `currentFrame`, with a comment naming the threads that touch each (F3). | Full suite. |
| B2 | ⬜ | `PooledDriverProvider.resetDriver()`: call `driver.switchFrame((String) null)` before `setUrl("about:blank")` (F4) — the cast matches house style (`Driver.java:191/534`); only the String overload accepts null. Provider-level, backend-agnostic, inside the existing try/catch. | Full suite; `oopif.feature` + `OopifPooledReuseTest` specifically. |
| B3 | ⬜ | Wire `waitIfSubmitRequested()` into `CdpDriver.click` and `W3cDriver.click` (F1); add ONE scenario to `element.feature` (`submit().click()` on the existing form page) so it can't silently die again. (Alternative considered and rejected: deleting `submit()` — it's documented v1 parity.) | Full suite + `-Pw3c`. |
| B4 | ⬜ | `Driver.select(int)` default → `Locators.commitFieldEventsJs` (F2). One line; heals W3C. | `-Pw3c` + `element.feature`. |
| B5 | ⬜ | Filter `Target.targetCreated` queueing (F5). **Read this before picking the filter — the obvious one fixes nothing for the consumer that needs it.** A `browserContextId` check (the same one `pageTargets()` uses) is a **no-op for any driver in the default context**, and default-context is the shape of every `start()`/`connect(pageUrl)` caller — including the §4 consumer, which builds drivers only that way and never `connectNewContext`. So a context-only filter closes F5 for pooled OSS drivers (already isolated by construction, i.e. the case that needs it least) and leaves the §4 consumer exactly where it is: attached to a shared browser, queueing every tab the user opens. **`openerId` ∈ {current target, known OOPIF targets} is therefore the load-bearing filter, not an optional refinement** — it is the only one that discriminates within a context. Prefer it, or take both (context ∧ opener). The handler captures neither field today; extract from `targetInfo` first (`onTargetCreated`). Must preserve the popup-from-the-driven-page flow, a §4 contract — `openerId` scoping preserves it by construction (that flow *is* opener-linked). | `tab-switch.feature` + downstream e2e (§4) — but note a context-only fix is **unfalsifiable downstream**: it is a no-op there, so that e2e stays green whether or not the bug is fixed. FIRST add an assertion to `TabE2eTest` that a `window.open`/`target=_blank` popup's `openerId` equals the driver's `currentTargetId` — no OSS test observes it yet. |
| B6 | ⬜ | **Keep** `Network.enable` and **re-arm it in `activateTarget()`**; fix the stale init comment to name the real dependent (the external `CdpEventListener` stream, not cookies). Audit `Fetch.enable` the same way: an active `interceptHandler` should re-arm across a tab switch (F6). NOTE: an earlier draft proposed *removing* `Network.enable` as vestigial — the §4 sweep reversed that; do not remove it. | Full suite; the §4 "Network events after `switchPage`" contract pin; `intercept.feature` + a tab-switch/intercept combo check. |
| B7 | ⬜ | Track the transport targetId **in a new final field captured at connect** — `currentTargetId` starts as that value but is overwritten by every `activateTarget`, so it cannot be read back. Make `close()` on that tab throw a clear `DriverException` instead of leaving a dead driver that fails opaquely on the next call, and expose `getConnectedTargetId()` so multi-tab callers can pick a survivor deliberately (F8). | Full suite; `tab-switch.feature`; a small negative test asserting the loud failure. |

### Tranche C — structural decomposition (state machines → unit-testable collaborators)

**Pure relocation. Predicates stay byte-identical**, including the whole loader cascade.
The point: invariants currently enforced by 100-line comment blocks become class
boundaries, and the worst-to-reproduce failure modes become deterministic sub-second
JUnit tests. End state: `CdpDriver` ≈ 2,700–2,900 lines (lifecycle + navigation
orchestration + eval pipeline + API) with three small package-private collaborators.

| # | Status | Extraction | Contents / API sketch |
|---|--------|-----------|----------------------|
| C1 | ⬜ | `PageLoadTracker` | Owns `domContentEventFired`, `framesStillLoading`, the five loaderId fields (`expectedLoaderId`, `supersededLoaderId`, `domContentLoaderId`, `committedLoaderId`, `preNavCommittedLoaderId`), `historyTargetUrl`, `loadTick`. Event-side: `onDomContentLoaded(loaderId)`, `onMainFrameNavigated(loaderId)`, `onFrameStarted/Stopped/Detached(frameId)`, `nudge()`. Scenario-side: `beginNavigation(loaderId)`, `beginSupersedingNavigation(historyUrl)`, `clearNavigation()`, `seedCommitted(loaderId)` (init / activateTarget), `isDomReady()`, `framesIdle()`, `readyStateGate()` → {REJECT, ACCEPT, ACCEPT_IF_URL(url)}, `awaitTick(ms)`, `describe()` (timeout diagnostic). Eval/ownership split: `readyStateGate()` reads only tracker-owned loader/history state and returns the decision plus — for ACCEPT_IF_URL on the superseded branch — the tracker-owned `historyTargetUrl` to match; the driver performs the `Runtime.evaluate` and the `urlsEquivalent` comparison, and supplies `pendingNavigationUrl` (which stays driver-side) itself for the `expectedLoaderId` branch. |
| C2 | ⬜ | `ContextRegistry` | `mainContextReady` + `frameContextReady` + `frameContexts`; `completeMain/Frame`, `invalidateMain`, `awaitMain/Frame`, `releaseFrameWaiters`, quit-unblocking. The documented invariant ("never invalidate from the scenario thread without a following `Runtime.enable`") becomes a method contract. |
| C3 | ⬜ | `TargetTracker` | `knownTargetIds`, `openedTargets`, `pendingTargetRemovals` + the handler logic; likely absorbs OOPIF session routing (`oopifSessions`, `oopifTargets`, `pageSessionId`, `isFromOtherSession`). |
| C4 | ⬜ | Unit tests for C1–C3 | Deterministic event-ordering cases CI produces only probabilistically: loader replaced mid-navigation; late DOMContentLoaded from the previous document; superseded-loader reload; same-document history traversal; total event loss (readyState-gate paths); invalidate/complete interleavings; detach-completes-waiter. Plain JUnit, no browser. |

### Explicitly NOT in scope (decided against at analysis time)

- **Any semantic change to the loader-binding / wait cascade.** Relocation only (C1).
- **Session reuse or eager `Target.detachFromTarget` in `activateTarget`.** Would attack
  the root cause of tab-switch session poisoning, but the pool probe already contains it,
  and Chrome's behavior detaching sessions with live OOPIF children is unverified. Park
  as a future experiment with its own CI soak.
- **Browser-level WebSocket transport** (`/devtools/browser/…` instead of the page-bound
  `/devtools/page/<id>` connection). Would eliminate the F8 transport-tab hazard as a class
  (the connection would survive every tab close) — but it changes connect semantics for every
  consumer (`CdpDriver.connect` callers pass page URLs today) and interacts with session
  routing everywhere. Future experiment; B7's guard + accessor is the contained fix now.
- **Merging `retry()` into `pollFor`/`pollUntil`** — documented, deliberate contract split.
- **Converting `waitForOopifReady` / `waitForFrameContextReady` polls to events** — no CDP
  event announces those transitions (OOPIF sessions deliberately don't enable `Page.*`).
- **Hoisting auto-wait into `Driver` defaults** (the true one-copy end state for the
  element API, deleting most CDP *and* W3C overrides via a `beforeAction(locator)` hook).
  Right destination, but it changes W3C timing behavior and its tests live in a separate
  CI job. Revisit as its own follow-up after A+B soak. B3/B4 fix the two copies that have
  actually drawn blood in the meantime.

## 6. Tranche D — test-suite reduction (LAST, after CI soak)

Inventory at baseline: **227 Gherkin scenarios** (1,835 lines, ~35s green under
`parallel(2)`) + **200 Java `@Test` methods** (~192 serial on one shared driver, ~38s).

Principle: the Gherkin suite is the concurrency crucible — every scenario is a pool
acquire → reset → navigate cycle, exactly the traffic that surfaced the loader, session,
and timer bugs. The Java direct-API classes exercise almost none of that and duplicate
element/frame/cookie/dialog/keys/mouse/navigation near-1:1. **So: keep Gherkin intact, cut
the duplicated half of the Java layer.** (`@lock` tags are no longer frozen — see "Ground
rules" — but removing one still requires a test proving the shared state is gone, and the
survivors are CPU-contention locks that isolation work cannot retire.) Honest payoff:
only ~30s of CI, but roughly half the maintenance surface, and one canonical home per
behavior.

| Status | Java class | Now | Keep | What survives |
|--------|-----------|----:|-----:|---------------|
| ⬜ | ElementE2eTest | 39 | ~3 | Keep the `select()`-no-match negative. The existing `locateAll`/`scriptAll` tests use String overloads (Gherkin-duplicable) — cut them and **add** one new Java test exercising the `Predicate<Element>`/`Predicate<Object>` overloads (`Driver.java:1058`/`1073`), which have no Gherkin form and zero coverage today. |
| ⬜ | KeysE2eTest | 22 | ~3 | numpad keys, `combo()` modifier arrays (not in Gherkin) |
| ⬜ | FrameE2eTest | 19 | ~4 | `DriverException` negative paths (bad index / locator / non-frame element) |
| ⬜ | DialogE2eTest | 12 | ~4 | prompt-default/empty, multi-dialog sequence, `DialogOpenedException` fail-fast |
| ⬜ | MouseE2eTest | 10 | ~2 | DOM `mousedown/mouseup/click` event-generation regression |
| ⬜ | NavigationE2eTest | 8 | 0 | fully subsumed by features |
| ⬜ | CookieE2eTest | 6 | ~1 | no-domain variant |
| ⬜ | LoginE2eTest | 8 | ~2 | failed-login, logout |
| — | LocatorsE2eTest | 51 | 51 | locator engine's only real coverage — deliberately Java, keep. Includes the null-resolve guard pin (`testActionJsOnMissingElementFailsLoudlyNamingLocator`) |
| — | BrowserContextIsolationTest | 3 | 3 | **Never cut.** The only proof the pool is isolated at all — cookie jar + tab enumeration. Both cookie.feature's and tab-switch.feature's unlocks rest on it, and it is the only test here that fails on the pre-fix code |
| — | InterceptE2eTest / InitScriptE2eTest / TabE2eTest | 18 | 18 | broader than or absent from Gherkin — keep |
| — | OopifPooledReuseTest / CallMultiScenarioTest / StepFailureFeatureTest | 3 | 3 | bespoke harnesses (pool-of-1 determinism, `@Timeout` tripwire, `@expect-failure` isolation) — keep |

Net: 200 → ~90 Java tests. The ~20 survivors from the twin classes may consolidate into
one `DriverEdgeCaseE2eTest`. Gherkin side: cut only `navigation.feature` (3 scenarios: one
duplicated by `history.feature`, the other two — `script()` return-value asserts — covered
across `element.feature` and the broader suite). Do **not** thin
`element.feature` — its 76 scenarios are cheap and are the pool's load generator. Leave
the W3C job untouched (parallel-lane protocol regression). C4's unit tests add the
dimension e2e can't: deterministic event orderings.

**Additions that ride along with D:** the §4 contract pins (binding round-trip, listener
event stream incl. after `switchPage`, `objectId`→`setFileInputFiles`, partial-`__kjs`
guard) and the `DOMCONTENT`-strategy sentinel. The reduction must not orphan the extension
surface — it is currently covered only downstream.

## 7. Sequencing summary

```
A (mechanical DRY)  →  B (robustness fixes, one commit each)  →  C (extraction + unit tests)
        └──── full e2e suite after each item/tranche + downstream consumer e2e (§4) ────┘
                                        ↓
                    ≥1 week CI soak on normal commit traffic
                    (doubles as the missing post-refactor stability evidence)
                                        ↓
                          D (delete Java twins, navigation.feature)
```

## Progress Log

Append entries as work lands: date, items, commit SHA(s), suite results (local runs × N,
CI run link/status), surprises.

- 2026-07-15 — Plan written (analysis baseline `96c9f950a`). No implementation started.
- 2026-07-15 — Downstream-consumer dependency sweep folded in: §4 added (frozen surface +
  behavioral contracts + contract-pin test additions + downstream validation step), F6
  rewritten (`Network.enable` stays; re-arm on `activateTarget`; `Fetch.enable` audit), F8/B7
  added (transport-tab close hazard), B5 popup-flow note, browser-level-transport parked in
  not-in-scope. Sections renumbered (execution plan now §5, tests §6, sequencing §7). Still no
  implementation started.
- 2026-07-15 — Executability audit by a second model (cold read of the two plan docs, then
  adversarial code verification in both repos): Tranches A/B EXECUTABLE (HIGH confidence),
  C/D EXECUTABLE-WITH-GAPS; 11/12 factual spot-checks confirmed exactly; validation commands
  verified correct; sanitization clean on both sides. Gaps folded back in: ElementE2eTest
  survivor list corrected (the `Predicate`-overload tests do not exist — cut the String-overload
  tests and ADD a new Predicate pin); B5 gains the openerId-capture prerequisite and a
  verify-`openerId`-in-`TabE2eTest`-first caveat; C1 gains the `readyStateGate` eval/ownership
  split (`historyTargetUrl` tracker-owned, `pendingNavigationUrl` driver-side); B7/F8 note the
  transport id needs a new final field (`currentTargetId` is overwritten on tab switch);
  `navigation.feature` cut-justification corrected; counts trued (1,835 Gherkin lines, ~10
  raw-eval sites); B2 uses the house-style `(String) null` cast; §4's browser-wide
  `switchPage` marked post-A8; A2 notes the generic read-helper return types. Still no
  implementation started.
- 2026-07-15 — **Parallel-isolation work landed ahead of the tranches** (unplanned — it came out
  of chasing the CI flakes the plan's §1 describes, and it changed enough of the plan's
  foundations that §§2–6 were amended in place; see the header note). Commits, in order:
  - `5dbb9d707` — element actions resolve the locator twice (`retryIfNeeded` then the action JS),
    so a document swapping in between left the action dereferencing null. `Locators` disagreed on
    what to do about it: `inputJs`/`clearJs` silently returned, the rest threw a raw TypeError.
    Both bad — the silent no-op worse, since it surfaced as an empty field failing a match many
    steps later. All now throw a marked error naming the locator; `elementAction` re-resolves on
    that marker only (3 attempts). `scrollJs` deliberately keeps its no-op-on-missing contract.
  - `f38e9c4be` — `setUrl()` returned with no barrier for `data:`/`about:`, so the pooled reset's
    `about:blank` was still in flight when the scenario's first real navigation was issued; Chrome
    aborted one against the other (`net::ERR_ABORTED` clustered on post-reset `/input`, `/wait`,
    `/shadow-dom`). Now barriers on the loader committing. **Unverified locally — this machine
    cannot reproduce the race (baseline and patched both show zero spurious aborts); CI is the
    only signal.**
  - `1f2b5f9b9` — **the big one.** Pooled slots shared the default browser context, i.e. one
    cookie jar. `resetDriver()` calls `clearCookies()` on every acquire and that is
    `Network.clearBrowserCookies` — context-wide — so every scenario starting up wiped the
    cookies of every scenario running in parallel. New `CdpDriver.connectNewContext()` gives each
    driver its own incognito context, disposed on `quit()`. That also fixed a tab leak: `quit()`
    closed the socket but never the tab (CI logs showed 7–8 tabs against a pool of 2). Product
    bug found alongside: `PooledDriverProvider` with a `webSocketUrl` had every slot
    `connect()`ing to the SAME page.
  - `a24051c4e` — tab enumeration scoped by `browserContextId` via a new `pageTargets()`
    (= A8's enumeration half; F7's inconsistency fixed; F5 reduced to its residual).
    **`tab-switch.feature` unlocked.**
  - Locks removed: **cookie.feature `@lock=*`**, **tab-switch.feature `@lock=tabs`**, and
    oopif.feature's scenario-level **`@lock=tabs`** (whose own comment said it existed only so its
    popup would not "skew the page-count assertions in the tab tests") — **12 tags → 9**, and the
    `tabs` lock no longer exists at all. All three were isolation locks, and all three are now
    proven unnecessary by `BrowserContextIsolationTest`, which fails on the pre-fix code (`bob
    must NOT see alice's cookie` → was `true`; `clearCookies must not touch alice's jar` → was
    `false`; `bob must NOT see alice's new tab` → `expected: <3> but was: <4>`). Note
    cookie.feature's second stated reason ("a set races its read, reads back null", which
    survived `@lock=render`) was never a timing race — it was another scenario's reset wiping the
    jar. The lock had been masking the bug, and the misdiagnosis is why it got escalated to
    `@lock=*`.
  - **Lesson for whoever removes the next lock:** both features pass unlocked *on the broken
    code* on a fast dev box. Local green is not evidence. The unlocks rest entirely on the
    isolation being proven by a test that fails on the old code.
  - Validation: full `cicd` suite green (2,595 tests), `DriverFeatureTest` 218/218 ×3 with the
    three locks off. **Owed:** the §4 downstream rebuild + e2e, because `switchPage`'s contract
    changed (browser-wide → context-wide). The *contract* question is since settled by
    inspection — the consumer never creates a browser context, so its drivers are all
    default-context and the change is a no-op there (see §4).

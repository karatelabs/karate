# CdpDriver Refactoring Plan

> **Working plan** for refactoring `karate-core/src/main/java/io/karatelabs/driver/cdp/CdpDriver.java`
> to be elegant, DRY, and robust — without destabilizing logic that was stabilized under fire.
> Companion to [DRIVER.md](./DRIVER.md) (architecture reference). This file is the *execution*
> tracker: update the Status columns and the Progress Log as work lands, so any session can
> pick up where the last one stopped.
>
> **Analysis baseline:** 2026-07-15, HEAD `96c9f950a` (the final commit of the 2026-07-14
> refactoring burst). `CdpDriver.java` = 4,074 lines; `cdp/` package = 6,716 lines.
> Line references below are as-of-baseline and will drift — method names are authoritative.
>
> **Amended same day** by a dependency sweep of the known external consumer of the CDP surface
> (§4): it *reversed* one planned fix (`Network.enable` stays and must be re-armed on tab switch
> — F6/B6), added the transport-tab hazard (F8/B7), and froze the extension-surface API through
> all tranches. Read §4 before touching anything the sweep names.

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
   - Keep `parallel(2)` and every `@lock` tag exactly as they are.
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
| F5 | **`drainOpenedTargets()` cross-driver pollution.** `Target.targetCreated` is browser-wide; every pooled driver queues every new tab in the shared browser, including tabs the pool creates for sibling drivers. Latent in this repo (only `TabE2eTest` uses the API) but the API is load-bearing downstream (§4). Correct filter: `TargetInfo.openerId` ∈ {this driver's target, its OOPIF targets} — which by construction preserves the documented "popup opened by the driven page" flow. | `onTargetCreated` handler |
| F6 | **`Network.enable` is load-bearing for the external event stream — and is LOST on tab switch.** The init comment ("Required for cookie operations", origin `0682c0065`) is stale: cookie *commands* work without it (`activateTarget` never re-enables it, cookie scenarios still pass). But external `CdpEventListener` consumers — the API is documented for traffic recording — depend on the Network event stream, so the enable must **stay** (an earlier draft of this plan proposed removing it as vestigial; the §4 sweep reversed that). The actual bug is the inverse: `activateTarget()` re-arms Runtime/Page/lifecycle/auto-attach but **not Network**, so after any `switchPage`/`switchPageById`/`close()` the stream goes dark on the new session. Same class: `Fetch.enable` isn't re-armed either — an active `intercept()` silently stops pausing requests after a tab switch. | `CdpDriver.initialize` (~line 546), `activateTarget` |
| F7 | Minor inconsistencies: `waitForPageLoad` throws raw `RuntimeException` where everything else throws `DriverException`; `findPageTarget` uses session-scoped `Target.getTargets` while `getPages` uses the browser-level form. | `waitForPageLoad`, `findPageTarget` |
| F8 | **Closing the transport tab kills the driver.** The WebSocket is page-bound (`/devtools/page/<id>`); closing that tab — directly, or via `switchPageById` + `close()` — drops the connection, and every later CDP call fails with CONNECTION_CLOSED with nothing pointing at the cause. `CdpDriver` knows the transport targetId (extracted at connect) but neither guards `close()` against it nor exposes it, so multi-tab callers are left re-deriving it by parsing the ws URL themselves. | `close()`, `extractTargetIdFromUrl` |

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
`describeNode` · `CdpDriver.connect`/`start` · `isResponsive`/`isReady`/`waitUntilReady` ·
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
- `drainOpenedTargets()` keeps reporting popups/new tabs opened *by the driven page* (the
  openerId scoping in B5 preserves exactly that flow).
- `switchPage(String)` substring-matches title/url browser-wide; `getTargetInfos()` marks the
  driven tab `active`.
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
| A2 | ⬜ | Collapse element-op wrappers via two private helpers | `interact(locator, js)` = `retryIfNeeded` → `script` → `BaseElement.existing`; `read(locator, js)` = `retryIfNeeded` → `script` cast. Covers click/focus/clear/value-set/scroll/highlight + text/html/value/attribute/property/enabled/position. `input`/`select` keep bespoke bodies. |
| A3 | ⬜ | Window management | One `windowId()` lookup + one `setWindowState(String)`; `maximize`/`minimize`/`fullscreen` differ by one string; `getDimensions`/`setDimensions` share the lookup. |
| A4 | ⬜ | Navigation twins | `refresh()`/`reload()` → private `reload(boolean ignoreCache)`; `back()`/`forward()` → private `traverseHistory(int delta)` (bodies are copy-paste with `±1`). Superseding-navigation arm/clear semantics unchanged. |
| A5 | ⬜ | Generic `retryFor<T>` | Value-returning variant of `retry()` with the same WARN-logging contract. Kills both holder-array hacks: `waitForChildFrames`'s `List[] holder` and the OOPIF match's `String[3] matched`. |
| A6 | ⬜ | `rawEval(expression, contextId)` helper | Eight call sites hand-roll one-shot no-retry `Runtime.evaluate` (probes, diagnostics, readyState fallback, OOPIF checks, frame-context probe). Centralize; keep the "bypasses the retry pipeline" distinction explicit in the helper's javadoc. |
| A7 | ⬜ | `waitOrThrow` helper for the `waitFor*` family | Every wait method repeats `pollFor(...); if (null) throw new DriverException("timeout waiting for …")`. Bodies become 1–3 lines. `retry()` vs `pollFor()` separation stays. |
| A8 | ⬜ | Single target enumeration | One private `pageTargets()` (browser-level) powering `getPages`/`getTargetInfos`/`findPageTarget` (fixes F7's session-scoped inconsistency); one shared fast-poll loop for `switchPage(String)`/`switchPageById`. |
| A9 | ⬜ | Screenshot unification | `screenshot(embed, timeout)` and `screenshot(locator, embed)` share one private `capture(clip, timeout, embed)`. |
| A10 | ⬜ | Hygiene | `Frame` → record; fully-qualified `java.util.*` → imports; magic numbers (150ms abort backoff, 1000/2000ms context bounds) → named constants; `waitForPageLoad` throws `DriverException` (F7). |

### Tranche B — targeted robustness fixes

Small, independently verifiable; land as separate commits so a CI regression bisects
cleanly.

| # | Status | Fix | Validation |
|---|--------|-----|------------|
| B1 | ⬜ | `volatile` on `currentTargetId` and `currentFrame`, with a comment naming the threads that touch each (F3). | Full suite. |
| B2 | ⬜ | `PooledDriverProvider.resetDriver()`: call `driver.switchFrame(null)` before `setUrl("about:blank")` (F4). Provider-level, backend-agnostic, inside the existing try/catch. | Full suite; `oopif.feature` + `OopifPooledReuseTest` specifically. |
| B3 | ⬜ | Wire `waitIfSubmitRequested()` into `CdpDriver.click` and `W3cDriver.click` (F1); add ONE scenario to `element.feature` (`submit().click()` on the existing form page) so it can't silently die again. (Alternative considered and rejected: deleting `submit()` — it's documented v1 parity.) | Full suite + `-Pw3c`. |
| B4 | ⬜ | `Driver.select(int)` default → `Locators.commitFieldEventsJs` (F2). One line; heals W3C. | `-Pw3c` + `element.feature`. |
| B5 | ⬜ | Filter `Target.targetCreated` queueing by `openerId` ∈ {current target, known OOPIF targets} (F5). Must preserve the popup-from-the-driven-page flow — a §4 contract. | `TabE2eTest` (its popups have `openerId` = its tab), `tab-switch.feature`, downstream e2e (§4). |
| B6 | ⬜ | **Keep** `Network.enable` and **re-arm it in `activateTarget()`**; fix the stale init comment to name the real dependent (the external `CdpEventListener` stream, not cookies). Audit `Fetch.enable` the same way: an active `interceptHandler` should re-arm across a tab switch (F6). NOTE: an earlier draft proposed *removing* `Network.enable` as vestigial — the §4 sweep reversed that; do not remove it. | Full suite; the §4 "Network events after `switchPage`" contract pin; `intercept.feature` + a tab-switch/intercept combo check. |
| B7 | ⬜ | Track the transport targetId; make `close()` on that tab throw a clear `DriverException` instead of leaving a dead driver that fails opaquely on the next call, and expose `getConnectedTargetId()` so multi-tab callers can pick a survivor deliberately (F8). | Full suite; `tab-switch.feature`; a small negative test asserting the loud failure. |

### Tranche C — structural decomposition (state machines → unit-testable collaborators)

**Pure relocation. Predicates stay byte-identical**, including the whole loader cascade.
The point: invariants currently enforced by 100-line comment blocks become class
boundaries, and the worst-to-reproduce failure modes become deterministic sub-second
JUnit tests. End state: `CdpDriver` ≈ 2,700–2,900 lines (lifecycle + navigation
orchestration + eval pipeline + API) with three small package-private collaborators.

| # | Status | Extraction | Contents / API sketch |
|---|--------|-----------|----------------------|
| C1 | ⬜ | `PageLoadTracker` | Owns `domContentEventFired`, `framesStillLoading`, the five loaderId fields (`expectedLoaderId`, `supersededLoaderId`, `domContentLoaderId`, `committedLoaderId`, `preNavCommittedLoaderId`), `historyTargetUrl`, `loadTick`. Event-side: `onDomContentLoaded(loaderId)`, `onMainFrameNavigated(loaderId)`, `onFrameStarted/Stopped/Detached(frameId)`, `nudge()`. Scenario-side: `beginNavigation(loaderId)`, `beginSupersedingNavigation(historyUrl)`, `clearNavigation()`, `seedCommitted(loaderId)` (init / activateTarget), `isDomReady()`, `framesIdle()`, `readyStateGate()` → {REJECT, ACCEPT, ACCEPT_IF_URL(url)}, `awaitTick(ms)`, `describe()` (timeout diagnostic). Evals stay in the driver. |
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

Inventory at baseline: **227 Gherkin scenarios** (~1,780 lines, ~35s green under
`parallel(2)`) + **200 Java `@Test` methods** (~192 serial on one shared driver, ~38s).

Principle: the Gherkin suite is the concurrency crucible — every scenario is a pool
acquire → reset → navigate cycle, exactly the traffic that surfaced the loader, session,
and timer bugs. The Java direct-API classes exercise almost none of that and duplicate
element/frame/cookie/dialog/keys/mouse/navigation near-1:1. **So: keep Gherkin intact
(including every `@lock`), cut the duplicated half of the Java layer.** Honest payoff:
only ~30s of CI, but roughly half the maintenance surface, and one canonical home per
behavior.

| Status | Java class | Now | Keep | What survives |
|--------|-----------|----:|-----:|---------------|
| ⬜ | ElementE2eTest | 39 | ~5 | `select()`-no-match negative; Java-only `Predicate` overloads (`locateAll`/`scriptAll`) |
| ⬜ | KeysE2eTest | 22 | ~3 | numpad keys, `combo()` modifier arrays (not in Gherkin) |
| ⬜ | FrameE2eTest | 19 | ~4 | `DriverException` negative paths (bad index / locator / non-frame element) |
| ⬜ | DialogE2eTest | 12 | ~4 | prompt-default/empty, multi-dialog sequence, `DialogOpenedException` fail-fast |
| ⬜ | MouseE2eTest | 10 | ~2 | DOM `mousedown/mouseup/click` event-generation regression |
| ⬜ | NavigationE2eTest | 8 | 0 | fully subsumed by features |
| ⬜ | CookieE2eTest | 6 | ~1 | no-domain variant |
| ⬜ | LoginE2eTest | 8 | ~2 | failed-login, logout |
| — | LocatorsE2eTest | 50 | 50 | locator engine's only real coverage — deliberately Java, keep |
| — | InterceptE2eTest / InitScriptE2eTest / TabE2eTest | 18 | 18 | broader than or absent from Gherkin — keep |
| — | OopifPooledReuseTest / CallMultiScenarioTest / StepFailureFeatureTest | 3 | 3 | bespoke harnesses (pool-of-1 determinism, `@Timeout` tripwire, `@expect-failure` isolation) — keep |

Net: 200 → ~90 Java tests. The ~20 survivors from the twin classes may consolidate into
one `DriverEdgeCaseE2eTest`. Gherkin side: cut only `navigation.feature` (3 scenarios,
strictly subsumed by `history.feature` + every Background). Do **not** thin
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

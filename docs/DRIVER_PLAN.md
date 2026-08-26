# CdpDriver — open defects

> Punch list of verified-open defects in the CDP driver. Companion to
> [DRIVER.md](./DRIVER.md) (architecture reference). Every item below was confirmed still
> present in the code as of 2026-08-04.
>
> **This file used to be a refactoring plan** (mechanical DRY, state-machine extraction,
> test-suite reduction), written when `DriverFeatureTest` was the CI failure mode — 34 of
> 100 runs red, every sampled failure that one test. The parallel-isolation fix
> (`1f2b5f9b9` / `a24051c4e` — each pooled slot gets its own incognito browser context
> instead of sharing the default one) settled that: **55 CI runs since, one failure, and
> that one was an unrelated report-filename regression.** The refactoring tranches existed
> to make an unstable subsystem tractable; the instability is gone and they were retired
> unstarted. What survives here is the set of real bugs that review turned up along the way.

## Validating a fix

```bash
# driver e2e (Docker required) — the parallel(2) crucible
mvn test -pl karate-core -Pcicd -Dtest=DriverFeatureTest

# full cicd profile before pushing
mvn -B verify -Pcicd

# W3C job (needed when Driver.java / W3cDriver.java are touched)
mvn -B verify -Pw3c -pl karate-core -am
```

Two standing cautions, both learned the hard way:

- **A green local run proves nothing about concurrency.** The features whose `@lock` tags
  were removed alongside the isolation fix all passed unlocked *on the broken code* on a
  fast dev box. A lock may only come off against a test that fails on the pre-fix code —
  `BrowserContextIsolationTest` is that test for the isolation work.
- **The loader-binding cascade is not up for tidying.** `isDomReady()` /
  `checkDocumentReadyState()` / `verifyJsExecution()` encode ~6 forensically-discovered
  Chrome behaviors; DRIVER.md §"Loader-bound page-load waits" documents why each predicate
  exists. Fixes below are additive to it, never inside it.

## Defects

| # | Defect | Fix | Validation |
|---|--------|-----|------------|
| F1 | **`submit()` has never worked on any backend.** The port wired `waitIfSubmitRequested()` into the default `Driver.click()` (`Driver.java:806`) only — `CdpDriver.click` (`:2863`) and `W3cDriver.click` (`:374`) both override without it. The wait silently no-ops; the recorded hash is never cleared. No test covers it. | Call it from both overrides. (Deleting `submit()` was considered and rejected — it is documented v1 parity.) | Full suite + `-Pw3c`; add ONE `element.feature` scenario (`submit().click()` on the existing form page) so it can't silently die again. |
| F2 | **`Driver.select(int)` default drifted** — dispatches only `input`+`change` (`Driver.java:915`), while the CDP override uses `Locators.commitFieldEventsJs` (full `input/change/blur/focusout`, added for blur-committing frameworks). W3C inherits the stale default. | Default → `commitFieldEventsJs`. One line; heals W3C. | `-Pw3c` + `element.feature`. |
| F3 | **Cross-thread fields not `volatile`** — `currentTargetId` (`:194`) written by `activateTarget` on the scenario thread, read by the `Target.targetCreated` handler on the event thread; `currentFrame` (`:155`) nulled by the `Target.detachedFromTarget` handler, read/written by the scenario thread. Their neighbours in the same field block are all volatile. | Add `volatile`, with a comment naming the threads that touch each. | Full suite. |
| F4 | **Pool reset doesn't reset frame state.** A scenario ending switched into an OOPIF makes `resetDriver()`'s `setUrl("about:blank")` go out on the *iframe's* CDP session — navigating the iframe, not the page. The same-origin case leaves `currentFrame` stale into the next scenario. Masked by loader binding today, but the reset silently isn't doing its job. | `PooledDriverProvider.resetDriver()`: `driver.switchFrame((String) null)` before `setUrl("about:blank")`, inside the existing try/catch. The cast matches house style and only the String overload accepts null. Provider-level, so backend-agnostic. | Full suite; `oopif.feature` + `OopifPooledReuseTest`. |
| F5 | **`drainOpenedTargets()` queues tabs the driver did not open.** The `onTargetCreated` handler is unfiltered, so every new tab in the browser lands in its queue. Pooled drivers are narrowed by construction (siblings sit in other contexts, `pageTargets()` filters enumeration by `browserContextId`), but that leaves every tab opened in the driver's *own* context — the normal shape of a default-context driver attached to a browser a human also uses, i.e. the external consumer below. | **`openerId` ∈ {current target, known OOPIF targets} is the load-bearing filter, not an optional refinement.** A `browserContextId` check is a *no-op for any default-context driver*, which is every `start()` / `connect(pageUrl)` caller — so it fixes F5 only for the pooled case that needs it least. Prefer `openerId`, or take both (context ∧ opener). The handler captures neither field today; extract from `targetInfo` first. Preserves the popup-from-the-driven-page contract by construction — that flow *is* opener-linked. | `tab-switch.feature` + downstream e2e. Note a context-only fix is **unfalsifiable downstream** — it is a no-op there, so that suite stays green either way. FIRST add an assertion to `TabE2eTest` that a `window.open` / `target=_blank` popup's `openerId` equals the driver's `currentTargetId`; no test observes it today. |
| F6 | **`Network.enable` is load-bearing for the external event stream — and is LOST on tab switch.** The init comment at `:640` ("Required for cookie operations") is stale: cookie *commands* work without it. But external `CdpEventListener` consumers depend on the Network event stream, so the enable must **stay**. The bug is the inverse — `activateTarget()` re-arms Runtime/Page/lifecycle/auto-attach but **not** Network, so after any `switchPage` / `switchPageById` / `close()` the stream goes dark on the new session. Same class: `Fetch.enable` isn't re-armed either, so an active `intercept()` silently stops pausing requests after a tab switch. | Re-arm `Network.enable` in `activateTarget()`; fix the stale comment to name the real dependent. Re-arm `Fetch.enable` when an `interceptHandler` is active. **Do not remove `Network.enable`** — an earlier draft proposed that as vestigial and it is wrong. | Full suite; a "Network events after `switchPage`" pin; `intercept.feature` + a tab-switch/intercept combo check. |
| F7 | `waitForPageLoad` throws raw `RuntimeException` (`:1440`) where everything else throws `DriverException`. | Throw `DriverException`, diagnostic unchanged. | Full suite. |
| F8 | **Closing the transport tab kills the driver.** The WebSocket is page-bound (`/devtools/page/<id>`); closing that tab — directly, or via `switchPageById` + `close()` — drops the connection, and every later CDP call fails with CONNECTION_CLOSED with nothing pointing at the cause. The transport targetId is extracted at connect into `currentTargetId`, but every `activateTarget` overwrites it, so the value isn't durably retained, `close()` has no guard, and there's no accessor — multi-tab callers re-derive it by parsing the ws URL. | Capture the transport targetId in a **new final field** at connect. Make `close()` on that tab throw a clear `DriverException` instead of leaving a driver that fails opaquely on the next call, and expose `getConnectedTargetId()` so multi-tab callers can pick a survivor deliberately. | Full suite; `tab-switch.feature`; a small negative test asserting the loud failure. |

Load-bearing context for F1/F2: `Driver` already has default implementations for the entire
element-op surface (`Driver.java:804-1035`); `CdpDriver` re-implements ~15 of them adding only
`retryIfNeeded` + `BaseElement.existing`, and `W3cDriver` re-implements them again. Three
near-identical copies is what bred both bugs. Hoisting auto-wait into the `Driver` defaults via
a `beforeAction(locator)` hook is the real one-copy end state, but it changes W3C timing
behavior and its tests live in a separate CI job — F1/F2 fix the two copies that have drawn
blood.

**Parked, deliberately:** browser-level WebSocket transport (`/devtools/browser/…` instead of
the page-bound connection) would eliminate F8 as a class, since the connection would survive
every tab close — but it changes connect semantics for every consumer (`CdpDriver.connect`
callers pass page URLs today) and interacts with session routing everywhere. F8's guard +
accessor is the contained fix.

## The CDP extension surface is public API

`CdpDriver`'s CDP-only surface is consumed by tooling built on karate-core outside this repo
(karate-max — already named in `CdpDriver`'s own comments). F5, F6 and F8 exist *because* of
this consumer and make little sense without it. Treat the following as public API: signatures
frozen, listed behaviors are contracts.

**Frozen surface:** `addCdpEventListener` / `removeCdpEventListener` · `CdpEventListener` ·
`addInitScript` / `removeInitScript` · `addBinding` / `removeBinding` ·
`addScriptToEvaluateOnNewDocument` / `remove…` · `objectId(locator)` · `getCdpClient()` (and
`CdpClient.method` / `browserMethod` fluency) · `getTargetInfos()` · `drainOpenedTargets()` ·
`switchPageById` · `getFrameOwnerBackendNodeId` / `describeNode` ·
`CdpDriver.connect` / `start` / `connectNewContext` · `isResponsive` / `isReady` /
`waitUntilReady` · `CdpLauncher.getWebSocketUrl` · `CdpDriverOptions.Builder` · `Locators`'
public JS generators (`existsJs`, `findAllJs`, `selector`, `toFunction`, …) ·
`DialogOpenedException`.

**Behavioral contracts:**
- External listeners receive **every** CDP event, in arrival order, on the single serialized
  `cdp-event-*` dispatch thread. Do not move external dispatch off that thread or start
  filtering events by session.
- The Network event stream flows **on the driven session** — hence F6.
- Init-script registry: idempotent by name, dependency-ordered, injected into the current
  document immediately and into every new document thereafter; `removeInitScript` leaves the
  live copy in place. `driver.js` **extends, never clobbers**, a partial `window.__kjs` seeded
  by a co-installed module (the guard in `ensureKjsRuntime`).
- A script that itself opens a dialog returns `null` (the `DialogOpenedException` path in
  `cdpEval`), and an unhandled dialog stays observable via `getDialog()` polling — consumers
  drive dialogs without registering an `onDialog` handler.
- `drainOpenedTargets()` keeps reporting popups/new tabs opened *by the driven page*; the
  consumer's post-click tab detection rides this. F5's `openerId` filter preserves it.
- `switchPage(String)`, `getPages()` and `getTargetInfos()` are scoped to the driver's **own
  browser context**, not browser-wide (changed by `a24051c4e`). Diverges from the old behavior
  only for a driver in its own incognito context — the pooled case, which is the point. The
  known consumer builds drivers only via `connect` / `start` and never creates a context, so
  all of its drivers are default-context and the change is a no-op there.
- `PageLoadStrategy.DOMCONTENT` is a first-class strategy downstream, not just the
  `DOMCONTENT_AND_FRAMES` default this repo's suite runs.

**Coverage gap.** This surface has near-zero OSS coverage — `InitScriptE2eTest` pins the
registry; nothing pins the rest, it is tested only downstream. Worth pinning beside
`InitScriptE2eTest`: binding round-trip (`addBinding` → page-side call →
`Runtime.bindingCalled` at an external listener); external listener receives Network events on
the initial session **and after `switchPage`** (guards the F6 re-arm); the partial-`__kjs`
extend-don't-clobber guard; one `DOMCONTENT`-strategy scenario. ~~`objectId(locator)` →
`DOM.setFileInputFiles`~~ — now pinned in OSS by the `inputFile()` surface (`UploadE2eTest`
and `upload.feature` against `upload.html`, CDP + W3C lanes).

**Validation protocol.** A change to any of the above isn't done until the downstream consumer
has been rebuilt against it and its driver e2e suite is green. Compile breaks are cheap to see;
these contracts are what break *silently*.

## Log

- 2026-07-15 — Plan written (refactoring tranches + defect list + consumer sweep).
- 2026-07-15 — Parallel-isolation work landed ahead of the tranches, out of chasing the CI
  flakes: element actions re-resolve their locator on a marked not-found error (`5dbb9d707`);
  `setUrl()` barriers on the loader committing for `data:` / `about:` so a pooled reset can't
  still be in flight when the scenario's first real navigation is issued (`f38e9c4be`); each
  pooled slot gets its own incognito browser context, since `clearCookies()` is
  `Network.clearBrowserCookies` and is context-wide — every scenario starting up was wiping
  the cookies of every scenario running in parallel (`1f2b5f9b9`, which also fixed a tab leak
  and a `webSocketUrl` pool bug where every slot connected to the SAME page); tab enumeration
  scoped by `browserContextId` (`a24051c4e`). Three isolation `@lock`s removed, all three
  proven unnecessary by `BrowserContextIsolationTest` — which fails on the pre-fix code and is
  the reason those unlocks are safe. Cookie.feature's second stated reason ("a set races its
  read") was never a timing race; it was another scenario's reset wiping the jar, and the lock
  had been masking the bug.
- 2026-08-04 — Refactoring tranches retired unstarted; file reduced to the defect list and the
  consumer contracts. Basis: 55 CI runs since `a24051c4e` with a single failure, and that one a
  report-filename regression from an unrelated commit — the instability the tranches were
  scaffolding against is gone. All eight defects re-verified present in the code. The
  `switchPage` context-scoping change is still owed a downstream rebuild + e2e, though the
  contract question it was flagged for is settled by inspection (see above).

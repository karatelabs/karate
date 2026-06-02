# karate-image — v1 parity plan

Plan to address jkeys089's review of the v2 `karate-image` ext (the rewrite of his v1
`karate.compareImage` / `configure imageComparison` feature, PR #2885 in the v1 repo). This
doc is the agreed scope + design after an interview pass. Companion reading:
[README.md](./README.md), [docs/EXT.md](../docs/EXT.md), v1 source at
`~/dev/ycode/karate` (`karate-core/.../ImageComparison.java`, `report/karate-report.js`,
`report/Resemble.js`, `examples/image-comparison`).

## 1. Framing — is this a PoC?

**No — the ext architecture is the go-forward home, but the report UI and the API surface
genuinely regressed and we will bring them to full parity.** The v2 Ext SPI (per-scenario
globals, `registerEmbed`, report assets) is the correct v2 design; v1's jQuery/jQuery-UI
monolith cannot come across as-is. What was lost is real: the entire *tuning loop* (live
re-diff + visual ignore-box authoring + options emission), options/baseline separation,
several correctness behaviours (error context, dock, JPEG), and packaging clarity. We close
all of it **in the new ext**.

The honest message to jkeys089: not a PoC in architecture; yes the report UI was reduced to
presentation-only for m3 and several capabilities regressed — here's the parity plan, and
we'd value your review of the rebuilt report UI and Resemble internals.

## 2. Decisions locked in this pass

| Area | Decision |
|---|---|
| Commitment | Full parity in the new ext (not a phased subset, not a separate v1 port). |
| Orchestration | `image.compare()` Java method **removed**. Java exposes **primitives**; the screenGrab-style orchestration is a **documented JS recipe** (one visible, overridable path). |
| Recipe home | Recipe lives in **scenario scope** (a common `*.js` via `karate-config` or a called feature), so `screenshot()` / `karate` bind correctly. **Not** attached to `image` in `karate-boot.js` (that closes over boot scope — wrong bindings). README copy-paste to start; harvest patterns later. |
| Emit surface | Extend the **generic** `karate.embed(...)` to accept the multi-part `{name, parts, meta}` shape (today it is single-part only). Recipe forwards a ready-made embed payload returned by `diff()`. |
| Live re-diff | **In scope** — the tune-without-rerun loop is the actual value. Vendor the **customized Resemble.js** (the "boxes reach edge" patch), run client-side, repaint on option/box change. |
| onShow hooks | Replaced by **declarative command templates** (options/rebase buttons emit JSON + a user-supplied shell-command template with `${baselinePath}` / `${optionsPath}` / `${latestPath}` placeholders). No eval of serialized user JS. |
| Box authoring | Bundle a **small drag/resize lib** (e.g. interact.js) as a vendored static asset (v2 has no jQuery-UI). |
| Deferred loading | **Both**: a generic core "defer embed until visible/expanded" hook **and** the ext defers its heavy `<dialog>` + full-res images + Resemble.js to lightbox-open. |
| macOS dock | `apple.awt.UIElement=true` set **early** (static init, before AWT/ImageIO touch), mac-only no-op elsewhere. **Not** `java.awt.headless=true` — that would break karate-robot's real-AWT desktop automation. |
| Options split | Recipe supports a distinct **`optionsDir`** that falls back to `baselineDir` when unset (options JSON local, baselines in S3, etc.). Missing options/baseline never throws. "options" not "config" to avoid the `karate-config.js` / `configure` collision. |
| Result shape | v2-style names kept (well-designed), **plus restored `error` + path fields** — see §4. |
| Report paths | **Absolute build-time paths** baked into the report (v1 parity; copy-paste-runs on the build machine). |
| Image formats | **Any ImageIO format** via explicit path + mime autodetect; diff image always PNG (lossless); recipe resolves `<name>.*`. |
| Packaging | **Maven dependency only.** `karate-core` is `provided`, `resemble`+`ssim` are `compile` → any Maven/Gradle project depending on `karate-image` transitively pulls the engines (it already has core). The `-Pfatjar` profile stays for `~/.karate/ext` drop-in users. |
| Browser tests | None automated — rely on `VisualDemoTest` writing a real report for manual inspection. |
| UI ownership | We build the v2-native report UI to spec; jkeys089 reviews for parity + Resemble correctness, and is **commit co-author** where his v1 work is carried over (vendored Resemble.js, ported report-UI logic, engine math) — `Co-authored-by:` on those commits. |

## 3. The 12 review points → action

| # | Review point | Action | Layer |
|---|---|---|---|
| 1 | Throws when required options JSON missing | Options/baseline loads are always optional (never throw on absence); orchestration owned by recipe. Reproduce contributor's exact case against the new model. | recipe + ImageApi |
| 2 | Resemble.js from CDN loses edge-box patch | Vendor the **customized** Resemble.js as a static asset; drop the CDN. | ext JS asset |
| 3 | No ignore-box authoring UI | Rebuild draw/drag/resize/right-click-remove in the lightbox using a vendored drag/resize lib; live repaint via vendored Resemble. | ext JS |
| 4 | Resemble options removed from UI | Restore the options controls (ignore mode, errorType, errorColor, transparency, tolerances) driving live re-diff. | ext JS |
| 5 | onShowRebase/onShowConfig unused | Replace with declarative command-template buttons (emit JSON + templated shell cmd). | ext JS + options |
| 6 | Can't store options separately from baselines | `optionsDir` distinct from `baselineDir` (defaults to it); explicit per-call paths supported. | recipe + ImageApi |
| 7 | No fat jar for CI | Documented: normal Maven/Gradle dep pulls engines transitively; `-Pfatjar` retained + verified to shade resemble+ssim. | pom + docs |
| 8 | Only PNG | Accept any ImageIO format; autodetect embed mime (stop hardcoding `image/png`); diff stays PNG; resolve `<name>.*`. | ImageApi + engine |
| 9 | No deferred embed loading (freezes large reports) | Core defer-until-visible hook + ext defers heavy dialog/images/Resemble to open. | core + ext JS |
| 10 | Java in macOS dock (no UIElement) | Set `apple.awt.UIElement=true` early (static init), mac-only. | ImageExt |
| 11 | No error context when failOnMismatch=false | Populate `result.error` (and diagnostics) even when not failing the step. | ImageApi / diff() |
| — | "Is this a PoC?" | §1 framing + this plan. | — |

## 4. Result-shape — finalized

Two distinct shapes. **`diff()` result** = a pure verdict + diagnostics the user asserts on
(no paths — `diff()` can't know them). **Embed `meta`** = the report-facing payload the
**recipe** assembles (it resolved the paths and wrote latest) and forwards via
`karate.embed`. Naming rules settled this pass: keep `pass`/`mismatchPercentage`, drop `is*`
prefixes, error is a **`{message, type}` object omitted on pass**, per-image-options
vocabulary is **"options"** (not "config" — avoids `karate-config.js` / `configure`
collision).

### 4.1 `diff()` result (user-facing `r`)

| Concept | v1 | v2 current | **Final** | Notes |
|---|---|---|---|---|
| Top-line verdict | _(from `error`)_ | `pass` | **`pass`** (bool) | result + meta both use `pass` (meta was `passed`) |
| Mismatch percentage | `mismatchPercentage` | `mismatchPercentage` | **`mismatchPercentage`** (num) | headline = best/min across engines |
| Per-engine % | `resembleMismatchPercentage`/`ssim…` | _missing_ | **`resembleMismatchPercentage`** / **`ssimMismatchPercentage`** (num) | present when that engine ran |
| Pixels exceeded threshold | `isMismatch` | `isMismatch` | **`mismatch`** (bool) | dropped `is` |
| Baseline just created | `isBaselineMissing` | `baselineEstablished` | **`baselineEstablished`** (bool) | event (true only on the establish run) |
| Dimensions differ | `isScaleMismatch` | _missing_ | **`scaleMismatch`** (bool) | restored |
| Effective threshold | `failureThreshold` | _(meta)_ | **`threshold`** (num) | matches `image.threshold` knob |
| Effective engine | `engine` | _(meta)_ | **`engine`** (str) | |
| **Error context** | `error` (str) | **_missing_** | **`error`** `{message, type}` | **point 11**; OMITTED on pass; `type` ∈ mismatch/scaleMismatch/baselineMissing/ioError |
| Name | `name` | `name` | **`name`** (str) | |
| Embed payload | — | — | **`embed`** `{name, parts, meta}` | recipe augments `meta` with paths, then `karate.embed(r.embed)` |

**No paths** in the `diff()` result (placement → meta). **No base64** anywhere — the diff PNG
**bytes** live in the `embed` payload's `diff` part; the report renders from file-based embeds.

### 4.2 Embed `meta` (report-facing)
Mostly assembled by `diff()`; the recipe tops up the two facts `diff()` can't know:
- from `diff()`: `name`, `pass`, `mismatch`, `mismatchPercentage`,
  `resembleMismatchPercentage`/`ssimMismatchPercentage`, `threshold` + `defaultThreshold`,
  `engine` + `defaultEngine`, `scaleMismatch`, `ignoredBoxes` (so the report can
  render/author boxes), and — **when `diff()` was given a name** (so it resolved them) —
  `baselinePath` + `optionsPath` (absolute). `default*` let the report emit *minimal* options.
- added by recipe: **`latestPath`** (latest is bytes — only the recipe knows where it wrote
  it) and **`baselineEstablished`** (an orchestration fact — the recipe knows it just created
  the baseline). When `diff()` was given explicit bytes/paths instead of a name, the recipe
  also supplies `baselinePath`/`optionsPath`.

## 5. API design

### 5.1 Primitives (Java, on the `image` global)
- `image.diff(nameOrBaseline, latest, options?)` (also `image.diff({name|baseline, latest,
  options})`). The v2 replacement for v1 `karate.compareImage`. Behaviour:
  - **Name-aware:** a bare name → `diff()` resolves the baseline, auto-loads `<name>` options,
    and fills `baselinePath`/`optionsPath` into the embed meta. An explicit path/bytes/null
    skips resolution.
  - **Computes** mismatch (+ per-engine numbers) and the diff **PNG bytes** via the
    exception-free engine (§7). Returns the §4 result + an `embed` payload (`{name, parts, meta}`).
  - **Gates the embed:** honours `report` (all/mismatched/null) — returns `embed: null` when a
    diff/embed isn't warranted (the recipe forwards `r.embed` only if non-null).
  - **No throw, no `karate.embed`, no file writes, no auto-establish** — those stay in the
    recipe. Missing baseline → `error:{type:'baselineMissing'}` (+ resolved `baselinePath` so
    the recipe can establish it).
- `image.resolve(name)` → `{ baselinePath, optionsPath }` (absolute), honouring `baselineDir`
  / `optionsDir` / `<name>.*` — exposed standalone for power users / the establish step.
- Config holder (property setters + `image.config = {…}`): `baselineDir`, `optionsDir`,
  `engine`, `threshold`, `report`, `failOnMismatch`, `rebaseCommand`, `optionsCommand`
  (templates), plus any resemble/ssim defaults. (The `image.config` overlay is the ext's own
  config bag, unrelated to the per-image **options** file vocabulary.)

### 5.2 `karate.embed` multi-part (core)
Extend `KarateJsBase.embed()` (currently `(data, mime, name)` → single "primary" part) with a
**multi-part object form** — `karate.embed({ name, parts:[…], meta })`. **Dispatch:** first
arg is a Map carrying a `parts` key → multi-part; otherwise the legacy 3-arg
`(data, mime?, name?)` form (unchanged). No positional multi-part overload.

**Each part** is `{ role, mime?, data | path | url }`, with these conveniences:
- `data` — inline bytes / `Uint8Array` (canonical).
- `path` — a resource string (`this:`/`classpath:`/`file:`/relative); **core reads it to
  bytes** so the recipe can pass `baselinePath` without reading it first.
- `url` — a report-relative URL for an asset the ext wrote itself (Java `Part(role,mime,url)`).
- `mime` **optional** — core auto-detects from the bytes (`detectMimeType`) when omitted.
- `role` **required** in multi-part (the renderer keys on it: `baseline`/`latest`/`diff`).

Backs onto the existing `StepResult.Embed(name, parts, meta)` + `LogContext.embed(Embed)`;
inline/`path` parts are written to `embeds/` by `HtmlReportWriter` exactly as today.
Documented in EXT.md (§7.1).

### 5.3 The default recipe (scenario-scope JS, README copy-paste)
```js
// screenGrab(name): capture → auto-establish if needed → diff (resolves the rest) → embed → verdict
function screenGrab(name) {
  const latest = screenshot(false);
  const p = image.resolve(name);                 // { baselinePath, optionsPath } (absolute)
  const established = !fileExists(p.baselinePath);
  if (established) karate.write(latest, p.baselinePath);   // auto-establish (fixes 6_outline's wish)

  const r = image.diff(name, latest);            // name-aware: resolves baseline+options+paths,
                                                 // computes, fills meta.baselinePath/optionsPath
  // top up the two facts diff() can't know:
  r.baselineEstablished = established;
  if (r.embed) {
    r.embed.meta.baselineEstablished = established;
    r.embed.meta.latestPath = karate.toAbsolutePath(karate.write(latest, name + '.png'));
    karate.embed(r.embed);                        // report-gated: null when not warranted
  }
  if (!r.pass && image.failOnMismatch) karate.fail(r.error.message);
  return r;
}
```
Power users override freely (and can drop to `image.diff(explicitPath, bytes, opts)` +
`resolve`); the report's rebase/options buttons use `r.embed.meta` paths + the
`rebaseCommand` / `optionsCommand` templates.

### 5.4 Engine changes (`ImageComparison`)
- **Exception-free refactor.** Replace `compare(...) throws MismatchException` (control-flow
  exceptions carrying `result`) with a method that **always returns a result** object
  (`pass`, `mismatchPercentage`, per-engine numbers, `diffImage`/diff bytes, nullable
  `error{message,type}`, `scaleMismatch`). Delete `MismatchException`. `diff()` maps it to §4.
  *Diverges from jkeys089's v1 source* → keep the pixel math identical and have him review
  (§11).
- **Strip base64.** Remove `getDataUrl` / `data:` URL generation; the engine deals in
  `byte[]` / `BufferedImage` only. (`diff()` PNG-encodes the diff for the embed part.)
- **Any ImageIO format + autodetect** (point 8): `ImageIO.read` already handles PNG/JPEG/…;
  drop the hardcoded `image/png` at the API boundary (core autodetects the part mime); the
  diff image stays PNG (lossless). `<name>.*` resolution lives in `resolve()`.
- **Unchanged:** resemble/ssim math, `allowScaling`/`scaleMismatch`, engine selection
  (`resemble`/`ssim`/`a|b`), ignoredBoxes/tolerances/errorType/errorColor handling.

## 6. Report UI rebuild (`META-INF/karate-ext/static/`)

Restore the v1 tuning loop, v2-native (scoped `.k-image-ext`, no jQuery).

**Assets:** vendor patched `Resemble.js` (edge-box) + a small drag/resize lib as static assets.

**Structure — hybrid tabs (editing is rare; viewing is the common path).** A single
`<dialog>` lightbox with a tab bar; only the **Diff** tab is editable:

| Tab | Editable | Content |
|---|---|---|
| **Diff** (default) | ✅ | Live re-diff canvas + **Tune** toggle → controls panel + ignore-box authoring. |
| Side-by-side | — | Baseline / latest / diff together. |
| Slider / wipe | — | Drag divider between baseline & latest. |
| Blink | — | Toggle baseline ↔ latest. |
| Onion skin | — | Opacity fade between them. |

- Read-only tabs render any ignore boxes as **non-editable overlays** for context.
- **Show options** / **Rebase** are top-level per-comparison actions (not per-view).
- **All five view modes** are the target; if blink/onion prove hard alongside the editable
  canvas, fall back to **v1 parity** (Diff + Side + Slider).

**Diff tab — view-first default.** Opens showing just the diff image. A **Tune** toggle
reveals:
- **Zoom**: fit-to-dialog by default, with a **100% (1:1) toggle** + scroll for pixel
  inspection. Box draw/handle math maps through the active scale (one of two fixed factors),
  so ignore-box coords stay in image pixels.
- **Live re-diff**: Resemble runs client-side over full-res baseline/latest; repaints on every
  control/box change.
- **Controls (v1 parity):** ignore preset (nothing/less/colors/antialiasing/alpha),
  errorType, errorColor (pink/yellow), transparency. **Stretch:** live tolerance sliders
  (red/green/blue/alpha + min/maxBrightness) if the repaint churn stays manageable.
- **Ignore-box authoring (handles + list + delete):** draw on canvas; selected box shows
  resize **handles**; a side **box list** shows coordinates with explicit **delete** buttons;
  throttled redraw on window resize; clamped to image bounds. (Drop v1's hidden
  right-click-to-remove.)

**Actions:**
- **Show options** → emits *minimal* JSON (only non-default options + boxes) into a copy panel,
  run through the `optionsCommand` template (e.g. `cat <<EOF > ${optionsPath}`).
- **Rebase** → `rebaseCommand` template (e.g. `cp ${latestPath} ${baselinePath}`) or
  download-latest fallback.

**Render weight & edge states:**
- **Uniform weight** — passing comparisons (`report:'all'`) get the *same* full lightbox as
  mismatches; perf is carried entirely by defer-to-open (so a 100s-of-passes report stays
  fast because nothing heavy builds until a card is opened).
- **Best-effort generic edge states** — always render the canvas; when there's no diff image
  (baseline just established, or scale-mismatch where dims differ), fall back to showing the
  latest image and disable re-diff. No bespoke per-state screens.

**Defer (two tiers):** (1) the core hook holds the whole image renderer behind a placeholder
until the embed **scrolls into view** (IntersectionObserver) — so the thumbnail+badge render
on approach, not at initial paint; (2) within the renderer, the heavy `<dialog>` full-res
images + Resemble + drag lib load only on **lightbox open**.

**Not now:** run-level "all diffs" index / bulk-rebase — per-embed lightbox only (see §12).

## 7. Core changes (karate-core)
- `KarateJsBase.embed()` — multi-part object form + part conveniences (§5.2).
- Report JS (`res/karate-report.js`) — generic **defer-until-visible** hook:
  - `_renderEmbed` emits a lightweight **placeholder host** (`data-embed-id` + `data-defer`)
    instead of running the renderer eagerly; an **IntersectionObserver** runs the registered
    renderer and injects its HTML when the host scrolls into view. This is what fixes the
    large-report freeze (point 9) — off-screen embeds cost nothing at initial paint.
  - **Whole-renderer defer** (not a two-phase preview/detail contract) — `registerEmbed(name,
    fn)` stays a single function, so existing exts are unaffected.
  - **Late-registration reconcile:** when an ext registers after first paint, upgrade hosts
    already materialized; not-yet-visible placeholders simply pick up the now-registered
    renderer when the observer later fires.
  - Generic (no-ext) embeds defer the same way.
- Confirm absolute embed-file paths are available to ext meta for command templates.

### 7.1 Docs — `docs/EXT.md` (part of this work, not an afterthought)
The plan changes the **ext contract**, so EXT.md is updated alongside the code:
- **`karate.embed` multi-part** — document the new `{name, parts, meta}` form next to the
  existing `StepResult.Embed` wire shape (§ Embeds), so any ext can emit from JS.
- **Defer-until-visible hook** — document the new core report capability (when it triggers,
  how an ext opts in) in the report-assets / embeds section.
- **Primitives + scenario-scope JS recipe pattern** — capture this as the reference pattern
  for a stateful ext that exposes pure verbs and ships orchestration as overridable JS
  (image is the worked example), and the boot-scope caveat (don't attach recipe fns to the
  global in `karate-boot.js`).
- Repoint image-ext examples/snippets in EXT.md + refresh `karate-image/README.md` (API
  surface changed: `compare()` removed, `diff()`/`resolve()` added, command templates).

## 8. Packaging / build (`karate-image/pom.xml`)
- No structural change required for the common case: `provided` core + `compile`
  resemble/ssim already make a plain dependency "do the right thing" in Maven/Gradle.
- Verify `-Pfatjar` actually shades resemble+ssim (and excludes core); document both paths
  (Maven dep for normal use; fatjar for `~/.karate/ext` drop-in). Refresh
  `dependency-reduced-pom.xml`.

## 9. Examples parity map (`~/dev/ycode/karate/examples/image-comparison`)

| v1 feature | v1 mechanism | v2 equivalent | Status |
|---|---|---|---|
| 1_establish | `compareImage { latest }`, missing baseline auto-adopted | recipe auto-establishes | parity |
| 2_compare | explicit `baseline: this:…` | `image.diff(baselinePath, latest)` | parity |
| 3/4 rebase | `onShowRebase` saveAs/cmd | declarative `rebaseCommand` template | improved (no eval) |
| 5_custom_config | per-call `options.ignoredBoxes` | per-call `options` to `diff()` | parity |
| 6_outline | `firstRun` toggle to auto-create baselines | recipe auto-establishes → toggle gone | **improved** (their own TODO) |
| 7_api | hand-built `screenGrab` w/ `File`/paths, separate `config/` dir (v1's `optionsPath`) | `image.resolve` + `optionsDir` + recipe | **improved** (less boilerplate; same `optionsPath` vocab) |
| result.error check | `if (result.error …) throw` | `r.error` restored (§4) | parity (was broken) |

## 10. Workstreams & sequencing
1. **Core**: `karate.embed` multipart + defer-until-visible hook + path exposure.
2. **Engine/API**: exception-free `ImageComparison` refactor (delete `MismatchException`) +
   strip base64; `image.diff` (name-aware, report-gated `embed`, no throw/emit) + `resolve` +
   `error:{message,type}` + per-engine numbers + `optionsDir` + any-format/autodetect mime;
   remove Java `compare()`/`rebase()`.
3. **Dock**: `apple.awt.UIElement=true` static init.
4. **Recipe + README**: document scenario-scope `screenGrab`; port the demo feature.
5. **Report UI**: vendor Resemble + drag lib; live re-diff; box authoring; options controls;
   show-options/rebase templates; defer.
6. **Packaging**: verify fatjar shade; document dependency story.
7. **Demo/verify**: `VisualDemoTest` writes a report exercising every control; manual pass;
   jkeys089 review.

**Attribution:** commits that carry over jkeys089's v1 work — the vendored (patched)
`Resemble.js`, report-UI logic ported from v1's `newDiffUI`, and the resemble/ssim engine
math — include `Co-authored-by: jkeys089 <1695857+jkeys089@users.noreply.github.com>`
(matching the existing `ImageComparison.java` credit). Net-new v2 code (core `karate.embed`
multipart, defer hook, primitives) doesn't need it.

## 11. Risks / watch-items
- **Live re-diff perf** on big reports — mitigated by defer-to-open; full-res only loads when
  a lightbox opens.
- **Vendored Resemble.js drift** from upstream — we own keeping the edge-box patch current
  (consider proposing upstream later).
- **Engine refactor diverges from jkeys089's v1 source** — keep the pixel math byte-identical,
  isolate the change to control-flow/return-shape, and have him review (he co-maintains it).
- **AWT/ImageIO under parallel scenarios** — `diff()` runs the AWT pixel engine concurrently;
  confirm `ImageIO.read` + resemble/ssim are safe per-call (fresh instances) under parallel
  runs, alongside the `apple.awt.UIElement` static-init timing.
- **Absolute report paths** are non-portable by design — acceptable per decision; note in docs.
- **Recipe scope** — the canonical recipe must live in scenario scope; calling a boot-scope
  function would mis-bind `screenshot()`/`karate`. Document clearly; do **not** advertise
  `image.<fn> = …` until/unless interpreter rebinding is proven.
- **Reproduce point 1** against the new options model before claiming it fixed.
- **Defer vs in-page search / print / PDF** — deferred embeds aren't in the DOM until
  scrolled into view, so Ctrl+F won't find them and a print/PDF pass would miss off-screen
  embeds. Mitigation: force-render all deferred hosts on `beforeprint` (and whenever PDF
  export lands). Note in EXT.md.

## 12. Out of scope / deferred
- PDF export (`image.print.css`) — tracked separately (EXT.md).
- "All diffs in the run" nav index page — optional.
- Live-serve "Accept as baseline" host — not now; rebase stays copy/template/download.
- Automated browser tests — explicitly not now.

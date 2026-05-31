# karate-image

Visual (image) comparison for Karate, packaged as an **ext** — the first real consumer of
the [Ext SPI](../docs/EXT.md). Activated from `karate-boot.js`, it puts an `image` global in
scenario scope and renders an interactive diff lightbox in the HTML report.

> Seed for anyone (LLM or human) reading this module cold. For the ext contract itself read
> [docs/EXT.md](../docs/EXT.md); for the broader codebase, [docs/DESIGN.md](../docs/DESIGN.md).

## Usage

```js
// karate-boot.js — activate + set suite-wide defaults
const image = boot.ext('image');
image.baselineDir = 'baselines';
image.threshold = 0.02;          // max % mismatch tolerated
image.report = 'mismatched';     // attach diff images: 'all' | 'mismatched' | null
```

```gherkin
* def shot = screenshot()                 # bytes (Uint8Array), byte[], or a path string
* def r = image.compare('home', shot)     # baseline auto-resolved by name
* image.rebase('home', shot)              # accept a change: overwrite the baseline
```

`compare(name, latest)` resolves the baseline at `<baselineDir>/<name>.png`, auto-loads
per-name options from `<baselineDir>/<name>.json`, **auto-establishes** a missing baseline
(writes latest, passes with a note), **fails the step on mismatch** unless
`failOnMismatch:false`, returns a result map, and emits the multi-part `image-comparison`
embed. Options precedence (low→high): suite/scenario config → `<name>.json` → per-call inline.
`compare({ name, latest, baseline, threshold, … })` is the one-shot map form.

A runnable, readable walkthrough lives in
[`src/test/resources/demo/visual-demo.feature`](src/test/resources/demo/visual-demo.feature)
(establish → match → catch regression → rebase → per-name options); `VisualDemoTest` asserts
it **and** writes a real report to `target/visual-demo/` — open it to drive the lightbox.

## Architecture

```
karate-boot.js: boot.ext('image')
        │  (name convention → io.karatelabs.ext.image.ImageExt)
        ▼
ImageExt (Ext)  ── onBoot ──┬─ registerGlobal("image", ctx -> new ImageApi(defaults, ctx))   per-scenario
                            └─ registerReportAssets(named("image").js(...).css(...))           report UI
        │
        ▼  per scenario
ImageApi (SimpleObject)  ── compare/rebase ──► ImageComparison (resemble / ssim pixel math)
        │                                              │
        │  emit StepResult.Embed                       │ diff PNG + mismatch %
        ▼                                              ▼
LogContext.embed("image-comparison", [baseline,latest,diff], meta)  ──► report
                                                                         │
META-INF/karate-ext/static/image.js  ── KarateReport.registerEmbed ──────┘  lightbox
```

| File | Role |
|---|---|
| `ImageExt.java` | The `Ext`. Boot-config holder (`SimpleObject`) + registers the per-scenario global factory and report assets. |
| `ImageApi.java` | The `image` global (`SimpleObject`), one **per scenario** (config is scenario-scoped, parallel-safe). `jsGet` exposes `compare`/`rebase` verbs + config reads; `putMember` takes config writes. Resolves paths via the scenario `KarateJsContext`. Emits the embed. |
| `ImageComparison.java` | Pixel-diff engine (resemble + ssim). Pure math; unchanged from its v1 origin. Credit: jkeys089 / Resemble. |
| `META-INF/karate-ext/static/image.js` | Report renderer. Registers an `image-comparison` embed renderer (`KarateReport.registerEmbed`) → thumbnail + `<dialog>` lightbox with side-by-side / draggable slider / blink / onion-skin. Resemble.js lazy-loaded from CDN on open. |
| `META-INF/karate-ext/static/image.css` | Lightbox styles, hand-authored + scoped under `.k-image-ext` (not Tailwind — see EXT.md). |

### Key decisions (why it looks like this)

- **Per-scenario global, not a Suite singleton.** Scenarios set config on `image`
  (`* image.threshold = 0.02`); a shared instance would race under parallel runs. `ImageExt`
  registers an `ExtGlobalFactory`; each scenario gets a fresh `ImageApi` with a copy of the
  boot defaults + its own `KarateJsContext`. This is the reference pattern for any stateful ext.
- **`latest` is always explicit.** The ext never calls `driver`/`screenshot()` itself — it
  stays decoupled; the caller passes bytes or a path. Accepts a `Uint8Array` (idiomatic),
  raw `byte[]`, or a path string (resolved through `this:`/`classpath:`/`file:`).
- **No `karate.compareImage(...)`.** v1’s `karate.*` method is dropped (clean SPI break); the
  `image` global is the only surface. v1’s hand-written `screenGrab` orchestration collapses
  into `image.compare(name, latest)`.
- **Embed roles are `baseline`/`latest`/`diff`** and the meta key is `mismatchPercentage` —
  the wire contract the lightbox reads. Source of truth is `ImageApi.emit(...)`/`meta(...)`.

## Build

```bash
mvn -pl karate-image test                 # unit + e2e (ImageComparisonTest, ImageExtE2ETest, VisualDemoTest)
mvn -pl karate-image -Pfatjar package     # fatjar for ~/.karate/ext/ drop-in (no Maven at run time)
```

## Status / not yet built

- PDF (`static/image.print.css`) — deferred (core Phase 5).
- `nav.pages` “all diffs in the run” index page — optional, not built.
- Report-side “Accept as baseline” affordance — needs a live-serve host; `image.rebase(...)`
  is the programmatic path today.

Engine credit: pixel-diff math from the v1 image-comparison contribution (jkeys089 / Resemble + SSIM).

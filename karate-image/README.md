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
image.optionsDir  = 'baselines';   // where <name>.json tuning files live (defaults to baselineDir)
image.threshold   = 0.02;          // max % mismatch tolerated
image.report      = 'mismatched';  // attach diff images: 'all' | 'mismatched' | null
image.engine      = 'resemble';    // 'resemble' | 'ssim' | 'pixelmatch' | combos: 'resemble,ssim' | 'resemble|pixelmatch'
```

Multiple engines: the **smallest** mismatch wins (pass if any engine is within threshold). The
separator picks how many run — `,` runs all of them every time (most thorough), `|` runs them in
order and stops as soon as one comes in under the threshold (faster; later engines are a fallback
for minor visual noise). See `ImageComparison.run(...)`.

The ext exposes **primitives**; the establish→diff→embed→fail orchestration is an
**overridable JS recipe** you keep in your own project (scenario scope, so `screenshot()` /
`karate` bind correctly — don't attach it to `image` in `karate-boot.js`):

```js
// common.js (loaded via karate-config) — copy + tweak for your workflow
function screenGrab(name) {
  const latest = screenshot();                  // bytes (Uint8Array), byte[], or a path string
  const p = image.resolve(name);                // { baselinePath, optionsPath, baselineExists }
  const established = !p.baselineExists;
  if (established) image.write(name, latest);    // adopt latest as the baseline
  const r = image.diff(name, latest);            // pure: compute + build the embed
  r.baselineEstablished = established;
  if (r.embed) {
    r.embed.meta.baselineEstablished = established;
    karate.embed(r.embed);                       // attach baseline/latest/diff to the report
  }
  if (!r.pass && image.failOnMismatch !== false) karate.fail(r.error.message);
  return r;
}
```
```gherkin
* def r = screenGrab('home')
* match r.pass == true
```

**Primitives:**
- `image.diff(name, latest, options?)` — resolves the baseline + `<name>` options by name (or
  takes an explicit `this:`/`classpath:`/`file:` baseline **path** / bytes), runs the engine,
  and returns `{ pass, mismatch, mismatchPercentage, resembleMismatchPercentage?,
  ssimMismatchPercentage?, threshold, engine, error:{message,type} (omitted on pass), embed }`.
  **Pure** — never throws, emits, writes, or auto-establishes. `embed` is the ready-to-pass
  `karate.embed` payload, or `null` when `report` says no diff is warranted.
- `image.resolve(name)` → `{ baselinePath, optionsPath, baselineExists }` (absolute paths).
- `image.write(name|path, bytes)` → absolute path written (auto-establish / programmatic rebase;
  `karate.write` can't target an absolute path outside the report output dir).

Baselines resolve at `<baselineDir>/<name>.<ext>` (any ImageIO format; defaults to `.png`);
per-name options at `<optionsDir>/<name>.json` — so options can live locally while baselines
live in, say, S3. Options precedence (low→high): suite/scenario config → `<name>.json` →
per-call inline.

## The pixelmatch engine

The third engine, `pixelmatch`, is a Java port of
[mapbox/pixelmatch](https://github.com/mapbox/pixelmatch) v7 (`io.github.t12y:pixelmatch`):
per-pixel OKLab/HyAB perceptual color difference with built-in anti-aliasing detection.
Options (per-call, `<name>.json`, all optional):

- `matchingThreshold` (default `0.1`) — per-pixel OKLab HyAB color threshold, 0..1 where
  black↔white = 1.0; smaller is more sensitive. (Named to avoid clashing with `threshold`,
  which is the *failure* threshold everywhere in karate-image.)
- `includeAA` (default `false`) — count anti-aliased pixels as differences.
- `checkerboard` (default `true`) — blend semi-transparent pixels against a checkerboard
  pattern (vs plain white) when comparing.
- `ignoredBoxes` — the same `[{left, right, top, bottom}, …]` boxes (all bounds inclusive)
  as the resemble/ssim engines; pixels inside are excluded from the comparison entirely,
  including the cluster verdict's raw and significant counts. Use for dynamic regions
  (transaction ids, timestamps).

The pixelmatch engine is **count-only**: the diff image in reports is always produced by
resemble (that is what the HTML lightbox and its live re-diff render), so pixelmatch's own
diff-rendering options are not exposed. The pixelmatch percentages ride on the `diff()`
result and the embed `meta` (`pixelmatchMismatchPercentage`, plus the raw/summary/region
diagnostics when clusters are enabled).

### Cluster verdict (`clusters`)

Pixelmatch-only, **off by default**: a significance layer that separates rendering noise
(anti-aliasing halos, sub-pixel shifts, font hinting changes between browser versions) from
real regressions. Enable with `clusters: true` (defaults) or a tuning map:

```js
image.engine   = 'pixelmatch';
image.clusters = true;             // suite-wide; or per-call / per-name in <name>.json:
// { clusters: { coreRadius: 1, minCoreArea: 16, hardDelta: 2.5,
//               flatness: 0.5, flatFraction: 0.5, minThinArea: 8 } }
```

With clusters on, the reported `mismatchPercentage` becomes the **significant-diff
percentage** — pixels in regions classified as real changes (interior mass surviving
erosion, or thin-but-vivid changes on a flat baseline) — and your existing failure
`threshold` applies to that. Pure rendering noise reports ~0.0% and passes; a missing
button reports its true area share. Because noise no longer inflates the number, you can
tighten `threshold` aggressively (0.01% becomes practical). The raw percentage stays
visible as `pixelmatchRawMismatchPercentage`, a human-readable `pixelmatchSummary`, and
per-region boxes in `pixelmatchRegions` (`{x, y, width, height, area, coreArea, meanDelta,
reason: 'CORE'|'THIN_VIVID'}`) — in both the `diff()` result and the embed `meta`.

| Param | Default | Controls | Raising it | Lowering it |
|---|---|---|---|---|
| `coreRadius` | 1 | Max thickness (2r+1 px) treated as potentially noise | Thicker real changes must rely on the safety net; risk of missing small solid changes rises. Use 2 for 2x/retina captures. | (min 1) Thinner noise survives erosion and is counted as significant; false positives return. |
| `minCoreArea` | 16 | Smallest interior "core" that counts as significant | Small solid changes (icons, single glyphs) may be ignored → false negatives. | Residual noise that survives erosion gets counted → false positives. |
| `hardDelta` | 2.5 | How vivid a thin change must be for the safety net (× `matchingThreshold`) | Real thin changes (underlines, dividers, recolored text) missed → false negatives. | Strong-ish rendering noise gets rescued as significant → false positives. |
| `flatness` | 0.5 | How flat the baseline must be under a thin change (× `matchingThreshold`) | More of the baseline qualifies as "flat" → safety net fires more often. | Stricter flatness → thin changes near existing detail are ignored. |
| `flatFraction` | 0.5 | Share of a thin cluster's pixels that must sit on flat baseline | Thin changes overlapping existing edges (e.g. text recolor) missed. | Noise straddling a flat area may be counted. |
| `minThinArea` | 8 | Smallest thin cluster the safety net will consider | Small thin marks (a text caret is ~2x16 px) ignored → false negatives. | Isolated vivid speckles (dead pixels, dithering) counted → false positives. |

Notes: `clusters` cannot be combined with ssim's `windowSize` option on the same call when
both engines run (pixelmatch ignores `windowSize`; it belongs to ssim). The lightbox does
not yet draw the significant-region boxes on the diff — the coordinates ride on the embed
`meta` (`pixelmatchRegions`) for when it does.

A runnable, readable walkthrough lives in
[`src/test/resources/demo/visual-demo.feature`](src/test/resources/demo/visual-demo.feature)
(establish → match → catch regression → rebase → per-name options); `VisualDemoTest` asserts
it **and** writes a real report to `target/visual-demo/` — open it to drive the lightbox.

## Report lightbox

Each comparison renders a thumbnail + status badge; clicking it opens a `<dialog>` built around
**one image stage**:

- **View toggles** (header, always available — "looking"): **Diff** (default) · **Slider**
  (drag to wipe) · **Blink** · **Onion** (opacity slider). **Side by side** is a separate
  toggle that pins baseline + latest beside the stage. **100%** toggles fit ↔ 1:1 zoom.
- **Advanced** (header) reveals "editing": **live re-diff** controls (ignore / errorType /
  errorColor) that recompute the diff in-browser as you change them, **ignore-box authoring**
  (drag on the diff to draw; resize handles; inline list + delete), and the **Show options** /
  **Rebase** write actions.
- **Show options** emits the minimal tuning JSON via your `optionsCommand` template; **Rebase**
  via `rebaseCommand` (defaults: write `<name>.json` / `cp latest baseline`).
- Live re-diff reads baseline/latest as **base64 inlined in this embed's `meta`** (image-ext
  only — normal screenshots stay file-based, no bloat), so it works even from a `file://`
  report (a file-based `<img>` would taint the canvas and block the pixel read).
- Rendering is **deferred until the embed scrolls into view** (core hook); full-res images +
  the vendored Resemble.js load on first open — large reports stay fast.

## Architecture

```
karate-boot.js: boot.ext('image')
        │  (name convention → io.karatelabs.ext.image.ImageExt)
        ▼
ImageExt (Ext)  ── onBoot ──┬─ registerGlobal("image", ctx -> new ImageApi(defaults, ctx))   per-scenario
                            └─ registerReportAssets(named("image").js(...).css(...))           report UI
        │
        ▼  per scenario
ImageApi (SimpleObject)  ── diff/resolve/write ──► ImageComparison (resemble / ssim pixel math)
        │   (pure: returns result + embed payload)      │
        │                                               │ diff PNG + mismatch %
        ▼  recipe: karate.embed(r.embed)                ▼
LogContext.embed("image-comparison", [baseline,latest,diff], meta)  ──► report
                                                                         │
META-INF/karate-ext/static/image.js  ── KarateReport.registerEmbed ──────┘  lightbox
```

| File | Role |
|---|---|
| `ImageExt.java` | The `Ext`. Boot-config holder (`SimpleObject`) + registers the per-scenario global factory and report assets. |
| `ImageApi.java` | The `image` global (`SimpleObject`), one **per scenario** (config is scenario-scoped, parallel-safe). `jsGet` exposes the `diff`/`resolve`/`write` verbs + config reads; `putMember` takes config writes. Resolves paths via the scenario `KarateJsContext`. `diff` is pure — it builds the `embed` payload but the recipe emits it. |
| `ImageComparison.java` | Pixel-diff engine (resemble + ssim). Pure math; `run(...)` always returns a result (no control-flow exceptions). Credit: jkeys089 / Resemble. |
| `META-INF/karate-ext/static/image.js` | Report renderer. Registers the `image-comparison` embed renderer (`KarateReport.registerEmbed`) → thumbnail + single-stage `<dialog>` lightbox (see [Report lightbox](#report-lightbox)). Vendored (patched) `resemble.js` for live re-diff. |
| `META-INF/karate-ext/static/resemble.js` | jkeys089's patched Resemble.js (boxes may reach the image edge) — vendored, not a CDN. Loaded on first lightbox open to power live re-diff. |
| `META-INF/karate-ext/static/image.css` | Lightbox styles, hand-authored + scoped under `.k-image-ext` (not Tailwind — see EXT.md). |

### Key decisions (why it looks like this)

- **Per-scenario global, not a Suite singleton.** Scenarios set config on `image`
  (`* image.threshold = 0.02`); a shared instance would race under parallel runs. `ImageExt`
  registers an `ExtGlobalFactory`; each scenario gets a fresh `ImageApi` with a copy of the
  boot defaults + its own `KarateJsContext`. This is the reference pattern for any stateful ext.
- **`latest` is always explicit.** The ext never calls `driver`/`screenshot()` itself — it
  stays decoupled; the caller passes bytes or a path. Accepts a `Uint8Array` (idiomatic),
  raw `byte[]`, or a path string (resolved through `this:`/`classpath:`/`file:`).
- **Primitives, not a baked-in `compare`.** v1’s `karate.compareImage` is dropped; the `image`
  global exposes pure verbs (`diff`/`resolve`/`write`). The establish→diff→embed→fail
  orchestration is a **scenario-scope JS recipe** the project owns (one visible, overridable
  path — see Usage), so power users keep v1’s full control (explicit baseline/options paths,
  split storage) without a baked-in policy.
- **`diff` is pure.** It never throws, emits, writes, or auto-establishes — it returns a result
  plus a ready-to-`karate.embed` `embed` payload. The recipe decides whether to emit and
  whether to fail. This keeps the engine testable and the policy in user space.
- **Embed roles are `baseline`/`latest`/`diff`** and the meta key is `mismatchPercentage` —
  the wire contract the lightbox reads. Source of truth is `ImageApi` (`embed(...)`).
- **Base64 source images ride on the embed `meta` (this ext only).** `meta.baselineData` /
  `meta.latestData` are inlined so the lightbox's client-side Resemble can re-diff from a
  `file://` report (canvas-readable). It's scoped to `image-comparison` embeds, so ordinary
  screenshots/other embeds stay file-based with no report bloat.

## Build

```bash
mvn -pl karate-image test                 # unit + e2e (ImageComparisonTest, ImageExtE2ETest, VisualDemoTest)
mvn -pl karate-image -Pfatjar package     # → target/karate-image-<version>.jar (~/.karate/ext/ drop-in)
```

**Two delivery forms, two audiences:**

- **Java teams** — declare a Maven/Gradle dependency on `io.karatelabs:karate-image`. The
  `resemble` + `ssim` engines come transitively and `karate-core` is `provided` (your project
  already has it). No fatjar needed.
- **Non-Java teams (Rust CLI)** — use the `-Pfatjar` build's `karate-image-<version>.jar`
  (engines bundled, `karate-core` excluded) as a `~/.karate/ext/` drop-in, no Maven at run time.

To fold image comparison into your own standalone/uber jar (e.g. for a CI pipeline that runs a
single self-contained jar), add `karate-image` to that module's dependencies and build the fat
jar as usual — the ext registers itself off the classpath, no extra wiring needed.

## Status / not yet built

- PDF (`static/image.print.css`) — deferred (core Phase 5).
- `nav.pages` “all diffs in the run” index page — optional, not built.
- Report-side “Accept as baseline” affordance — needs a live-serve host; the recipe’s
  `image.write(name, latest)` (and the lightbox’s copy-paste rebase command) is the path today.

Engine credit: pixel-diff math from the v1 image-comparison contribution (jkeys089 / Resemble + SSIM).

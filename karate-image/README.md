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
image.engine      = 'resemble';    // 'resemble' | 'ssim' | 'resemble,ssim' | 'resemble|ssim'
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

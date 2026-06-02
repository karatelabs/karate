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
```

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
| `META-INF/karate-ext/static/image.js` | Report renderer. Registers an `image-comparison` embed renderer (`KarateReport.registerEmbed`) → thumbnail + `<dialog>` lightbox: editable Diff tab (live re-diff + ignore-box authoring) + read-only side-by-side / slider / blink / onion-skin. Vendored (patched) Resemble.js. |
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

## Build

```bash
mvn -pl karate-image test                 # unit + e2e (ImageComparisonTest, ImageExtE2ETest, VisualDemoTest)
mvn -pl karate-image -Pfatjar package     # fatjar for ~/.karate/ext/ drop-in (no Maven at run time)
```

For normal projects no fatjar is needed: declare a Maven/Gradle dependency on `karate-image`
and the `resemble` + `ssim` engines come transitively (`karate-core` is `provided` — your
project already has it). The `-Pfatjar` build is only for the `~/.karate/ext/` drop-in.

## Status / not yet built

- PDF (`static/image.print.css`) — deferred (core Phase 5).
- `nav.pages` “all diffs in the run” index page — optional, not built.
- Report-side “Accept as baseline” affordance — needs a live-serve host; the recipe’s
  `image.write(name, latest)` (and the lightbox’s copy-paste rebase command) is the path today.

Engine credit: pixel-diff math from the v1 image-comparison contribution (jkeys089 / Resemble + SSIM).

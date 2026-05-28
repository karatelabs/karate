# Image Comparison Spike — Ext System Dogfood

> **Status:** Plan. Drives the work that supersedes [PR #2885](https://github.com/karatelabs/karate/pull/2885) and proves the v2 ext model end-to-end through the first real out-of-core ext: `karate-image`.
>
> **Related:** [DESIGN.md § Plugin Architecture](./DESIGN.md#plugin-architecture) (post-D17 this section gets renamed § Ext Architecture) · [CLI.md](./CLI.md) · [RELEASING.md](./RELEASING.md) · Prototype (not committed): `~/Downloads/karate-report-redesign/` on the maintainer's machine — design-only reference, intentionally not in repo because it predates the feature/scenario/step data-model decisions Phase 1 makes
>
> **Vocabulary note (D17).** All SPI types and runtime symbols use `Ext` (interface `Ext`, `boot.ext('name')`, `io.karatelabs.ext.<name>.<Name>Ext`, sibling repo `karate-ext`). Historical references to "plugin" survive only in the §2 D-rows (decision provenance) and where citing pre-rename DESIGN.md / source code (e.g. the current `Plugin.java` interface file, the `BootBinding.plugin(name)` method that becomes `BootBinding.ext(name)`). When this doc says "ext" and DESIGN.md says "plugin" it is the same concept; DESIGN.md will be updated as part of Phase 2.

---

## 1. Goals

1. **Restyle the HTML report** by porting the existing three templates (`karate-summary.html`, `karate-feature.html`, `karate-timeline.html` under `karate-core/src/main/resources/io/karatelabs/output/`) to Tailwind, cherry-picking design ideas from the prototype at `~/Downloads/karate-report-redesign/` that align with our actual data model. The prototype was built by a design-first contributor without the feature→scenario→step hierarchy in view, so some of its KPIs and panels assume data we don't have; the port treats it as a visual vocabulary and aesthetic reference, not a one-to-one template port. See Phase 1 for the curated idea-list.
2. **Define a slot/asset/global contract** so exts can contribute UI chunks (per-step embeds, summary panels, per-feature panels, top-level pages) and JS-scope globals to the test runtime without touching `karate-core` Java.
3. **Ship `karate-image`** as the first OSS ext — an in-repo submodule with a fatjar build — covering the v1 `compareImage` use case with a Tailwind+Alpine UI. Image comparison is the *dogfood*: if the SPI can host it, the SPI is real.
4. **Migrate `karate-ext/karate-openapi`** (renamed from `karate-plugins`; see D17) onto the new slot/asset contract — contributes a coverage page + summary panel (and later an "API calls touched" roll-up). Proves the SPI on a second, separately-versioned ext.
5. **Validate fatjar mode** — drop `karate-image-X.Y.Z.jar` into `~/.karate/ext/` with the Rust launcher, no Maven, see the diff lightbox in the report. Same `karate-openapi.jar` in `~/.karate/ext/` activates coverage. The dir name matches the SPI name (D17).

Non-goals (this spike): generic `karate-coverage` base module, PDF-rendering deep design, removal of `karate.channel()` (it stays — boot exts are additive).

### 1.1 Where each extension lives

Per D19, extensions split across two tiers — same SPI, different licence + repo:

| Extension | Tier | Repo | Maven coords | Licence |
|-----------|------|------|--------------|---------|
| `karate-image` | OSS | this repo (`karate/karate-image/`) | `io.karatelabs:karate-image` | Apache 2.0 |
| `karate-openapi` | Proprietary | `karate-ext/karate-openapi/` *(was `karate-plugins`)* | `io.karatelabs.ext:karate-openapi` | Commercial, license-gated |
| `karate-grpc` | Proprietary | `karate-ext/karate-grpc/` | `io.karatelabs.ext:karate-grpc` | Commercial, license-gated |
| `karate-keycheck` | Proprietary (infra) | `karate-ext/karate-keycheck/` | `io.karatelabs.ext:karate-keycheck` | Commercial, license-gated |
| *future:* `karate-axe`, `karate-screenshot-regression`, `karate-pdf` | OSS | this repo | `io.karatelabs:karate-*` | Apache 2.0 |
| *future:* `karate-rules`, `karate-specs`, `karate-coverage` *(generic base)*, `karate-kafka`, `karate-websocket` | TBD per case | TBD | TBD | TBD |

**Runtime cannot tell tiers apart.** `boot.ext('image')` and `boot.ext('openapi')` follow the same resolution path. License gating is enforced inside the proprietary extension's own `onBoot` (via `karate-keycheck`). An OSS extension and a proprietary extension can live side-by-side in the same `karate-boot.js`; users who only need OSS extensions never see the licence machinery.

---

## 2. Decisions Frozen in Interview

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **Real Tailwind for production**, checked-in precompiled CSS + hash-verified rebuild gate. **CDN Tailwind for the dev preview harness only** (§3.1 + §3.1.1). | Production reports stay offline-capable, deterministic, and don't depend on a CDN at view time. The maintainer-side dev harness uses Play CDN so template iteration is reload-fast — no `mvn package` per visual tweak. |
| D2 | **Strict phase 1 → phase 2.** Redesigned report ships first (with slot DOM hooks but no plugin loader); plugin SPI and `karate-image` land after. | Smaller PRs; first PR proves the design language, second proves the SPI. |
| D3 | **All slot types** supported: per-step embed, summary panel, feature panel, extra top-level page | One slot mechanism, four naming conventions. Plugin-owned render. |
| D4 | **Plugin JAR ships its own JS+CSS** under `META-INF/karate-ext/static/`; core copies into `target/karate-reports/ext/<name>/` at write time | Self-contained; versioned with plugin JAR; loaded only when plugin active. |
| D5 | **Alpine init order** via `defer` + `alpine:init` event hook. Plugins register `Alpine.data(...)` inside the hook. | Documented Alpine pattern; no template-ordering coupling. |
| D6 | **Embed schema generalised to `parts[]`** — `{name, parts: [{role, mime, data\|url}], meta}` | One diff = three images + metadata. Future-proofs `grpc-match` etc. |
| D7 | **Plugin distribution = existing `~/.karate/ext/` + `.karate/ext/`** dirs picked up by the Rust launcher | Zero new infra; we only need to test the path. |
| D8 | **Strict core-version match** — plugin manifest carries `coreVersion`; loader fails fast on mismatch | Forces lock-step releases. Cheaper than a half-working SPI. |
| D9 | **No `karate.*` JS method contribution from plugins.** Breaking change from v1's `karate.compareImage()` | Forces a clean SPI; plugin globals land in scenario scope under their own name. |
| D10 | **Plugin global + custom step keyword.** `boot.plugin('image')` → `image` global is in scope inside scenarios. `* image { compare: '...', baseline: '...' }` dispatches to the same global | Unifies channel + boot models; finally lets users extend Karate keywords. |
| D11 | **Resemble.js stays, loaded from CDN** (lazy on lightbox open) | Server emits precomputed diff PNG; Resemble.js powers interactive tools (slider, blink, onion-skin). CDN keeps plugin JAR small. |
| D12 | **No new methods on `Plugin` interface** — assets + init + globals registered imperatively from `onBoot(Suite)` | Compat with existing SPI; everything funnels through `Suite` registries. |
| D13 | **Cherry-pick PR #2885's server-side Java** (`ImageComparison.java`, pixel-diff logic); rewrite UI + step integration to fit plugin model | Don't redo solid pixel math; do redo jQuery UI. Credit contributor in MIGRATION_GUIDE + AUTHORS. |
| D14 | **PDF deferred to Phase 5.** Two paths kept open: print-CSS via JSONL→HTML pipeline (default), headless-Chrome via existing CDP machinery (single-render multi-page with native PDF bookmarks + CSS `target-counter()` for TOC) | PDF design is real work; gating the spike on it is counterproductive. |
| D15 | **Module name: `karate-image`** (over `karate-imgutils`, `karate-visual`) | Matches `karate-junit6`/`karate-gatling` brevity; accurate today; room for image-adjacent additions (screenshot regression, OCR). |
| D16 | **Image comparison ships OSS as in-repo submodule** | Was OSS in v1. Submodule means single repo, single CI, fatjar build runs alongside core's. |
| D17 | **Rename "plugin" → "ext" across the SPI.** `Plugin` interface → `Ext`. `boot.plugin('x')` → `boot.ext('x')`. `io.karatelabs.plugins.foo.FooPlugin` → `io.karatelabs.ext.foo.FooExt`. Sibling repo `karate-plugins` → `karate-ext`. Section header in DESIGN.md "Plugin Architecture" → "Ext Architecture". | Disambiguates from IDE plugins / Maven plugins / Gradle plugins. Aligns with `~/.karate/ext/` + `.karate/ext/` dirs that already exist (DESIGN.md, CLI.md, karate-cli/docs/spec.md). One vocabulary across runtime SPI, on-disk classpath dir, and the umbrella repo. v2 hasn't GA'd — clean rename now or never. |
| D18 | **`karate.channel('type')` resolution moves to the unified registry.** Today `channel()` hard-codes a lookup against `KarateConfig.getChannelFactoryClass(type)`. After the ext rename, the channel SPI resolves via the same `io.karatelabs.ext.<name>.<Name>Channel` convention as ext-globals. | Two name-convention resolvers (one for channels, one for ext) is the kind of duplication that rots into divergence. One resolver, two roles (per-call factory vs Suite-lifetime singleton). |
| D19 | **Extensions split across two licensing tiers, same SPI, same resolver.** OSS extensions live as submodules in *this* repo (`karate-image`, future visual/a11y/screenshot-regression tooling) — Apache/MIT, published under `io.karatelabs:karate-*`. Proprietary extensions live in the sibling `karate-ext` monorepo (renamed from `karate-plugins`, per D17) — license-gated, published under `io.karatelabs.ext:karate-*` (or kept private). Both tiers implement the same `io.karatelabs.core.Ext` SPI, resolve via the same `io.karatelabs.ext.<name>.<Name>Ext` name-convention, and activate the same way (`boot.ext('name')`). The runtime cannot tell them apart; only the JAR's licence header + Maven coordinate group does. We use "extension" / "ext" terminology despite VS Code's overlap — `ext` as a 3-letter token is unambiguous in context (filesystem dir, boot API, SPI name). | Single SPI surface, single mental model for users and contributors. License model is metadata, not architecture. Lets a future OSS extension graduate into core or a proprietary extension be open-sourced without an API break. |
| D20 | **Phase 1 sticks to Tailwind built-ins + reuses the karatelabs-site brand.** No `@layer components` for Karate-specific classes (no `.k-card`, `.k-pill-pass`); use utility classes directly in templates. The `theme.extend` block in `karate-core/src/main/tailwind/tailwind.config.js` mirrors `../karatelabs-site/tailwind.config.js` verbatim — same `brand` slate scale (`#0f172a` → `#475569` + `navbar`), same `accent` blue (`#60a5fa`), same `amber` warning, same `surface` neutral scale, **same system-font stack** (no Google Fonts CDN). Overrides the prototype's Inter + JetBrains Mono + green-accent aesthetic. Per-ext CSS classes (e.g. image-comparison's `.k-image-ext` scope) remain fine — they isolate ext-owned styles, not Karate brand. | Visual continuity with karatelabs.io for free. Zero font CDN dependency keeps reports rendering styled in air-gapped CI. Defers any "design a Karate-OSS distinct brand" question to a later phase. Sticking to utility classes (no component layer) means the brand can be revisited later without a template rewrite — just swap `tailwind.config.js`. |
| D21 | **Icons: Heroicons sourced into an inline SVG sprite, spliced by `HtmlReportWriter` at write time.** Source: [heroicons.com](https://heroicons.com) (MIT, made by Tailwind Labs, 24×24 viewBox matches karatelabs-site convention). Implementation matches the existing `HtmlReportWriter` model (see §3.1.2): one `karate-core/src/main/resources/io/karatelabs/output/_icons.svg` containing `<svg style="display:none"><symbol id="icon-check" viewBox="0 0 24 24">…</symbol>…</svg>`; templates carry a new `<!-- KARATE_ICONS -->` placeholder near `<body>` start; `HtmlReportWriter` string-replaces it with the sprite contents (same mechanism as `/* KARATE_DATA */`). Templates reference icons via `<svg class="w-4 h-4"><use href="#icon-check"/></svg>` — color inherits from `text-*` utilities, size from `w-*`/`h-*`. Inline (not file-referenced) sprite avoids `file://` CORS when reports are opened locally. ~2 KB per template; deduplicates SVG paths across the three pages. Estimated icon count for Phase 1: ~15-25 (check / X / dash for pass/fail/skip, chevrons for sort + expand, sun/moon for theme toggle, clipboard for copy-as-cURL, link/anchor, exclamation-triangle for failures, document for embeds, clock for durations). | Reject Bootstrap Icons / Font Awesome (webfont = CDN + FOIT). Reject Lucide/Tabler/Phosphor (good libs, but Heroicons is the canonical Tailwind pairing). Reject Thymeleaf-fragment approach (templates are static HTML + string-replace — no template engine). Per-ext icons follow the same inline-SVG pattern inside the ext's own `static/` assets. |

---

## 3. Architecture

### 3.1 Tailwind build (production)

**Setup**

- Vendor the `tailwindcss` standalone binary per-OS under `etc/tailwind/` (no node toolchain). One file per `{linux,macos,windows}-{x64,arm64}`. Pinned source: `tailwindcss v3.4.17` standalone CLI, downloaded from `https://github.com/tailwindlabs/tailwindcss/releases/download/v3.4.17/tailwindcss-{os}-{arch}`. SHA-256 checksums in `etc/tailwind/CHECKSUMS.txt`. ~120 MB total checked in (acceptable; binaries are stable across point releases).
  - Alternative considered: download-on-build via `frontend-maven-plugin`. Rejected: every CI run pays the download cost, offline builds break.
- Source: `karate-core/src/main/tailwind/`
  - `tailwind.config.js` — content globs over `karate-core/src/main/resources/io/karatelabs/output/*.html` (plus the dev preview harness under `karate-core/src/main/tailwind/preview/*.html`); `theme.extend` block copied verbatim from `../karatelabs-site/tailwind.config.js` per D20 (slate `brand` scale, `accent` blue, `amber`, `surface`, system-font stack). Initially no ext static asset dir in the content glob — exts own their own CSS and don't need core utilities.
  - `input.css` — `@tailwind base; @tailwind components; @tailwind utilities;` only. **No `@layer components` block** in Phase 1 (D20). If reusable component classes are genuinely needed later, add them in a follow-up phase that also addresses the Karate-OSS brand question.
- Output: `karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css` (checked in).
- Production templates always reference the precompiled CSS via `<link rel="stylesheet" href="res/karate-report.css">`. No CDN dependency in shipped reports — they render styled offline and in air-gapped CI.

**Rebuild gate**

A Maven mojo (under `karate-core/src/build-tools/`, or simpler — a `Mojo` in a new `karate-build-tools` module that core depends on at build time only):

1. SHA-256 of (`tailwind.config.js` + `input.css` + every `*.html` template under `output/`).
2. Compare to `karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css.hash`.
3. On mismatch: **fail the build** with `Tailwind CSS is stale. Run: mvn -pl karate-core karate:tailwind` (custom goal that runs the standalone binary, rewrites the CSS, and rewrites the hash file).
4. CI runs the gate too — no way to land a template change without regenerating CSS.

**Why checked-in.** Most contributors edit Java, not templates. Forcing every Java-only edit through a Tailwind rebuild is friction. The hash gate makes template edits loud without taxing unrelated work.

### 3.1.1 Dev preview harness (CDN-based)

Iterating on templates against `mvn package` per visual tweak is too slow. The dev harness sidesteps the production toolchain entirely for the inner-loop:

```
karate-core/src/main/tailwind/preview/
├── preview.html            # static HTML — loads CDN Tailwind + fixtures + the production template bodies via fetch
├── fixtures/
│   ├── small-suite.jsonl   # ~5 features, mixed pass/fail/skip
│   ├── large-suite.jsonl   # ~100 features, multiple threads — for layout stress
│   └── failure-heavy.jsonl # for testing the failure panels
└── README.md               # "open preview.html in a browser; pick fixture from dropdown"
```

`preview.html`:
- `<script src="https://cdn.tailwindcss.com">` — Play CDN with JIT; same `tailwind.config.js` via `<script>tailwind.config = {...}</script>` inline.
- Loads the fixture JSONL, parses it client-side into the same `FeatureResult` shape the Java side emits.
- Renders the production template bodies (fetched from `../../resources/io/karatelabs/output/*.html`) against the parsed data — using the same Alpine components production uses, just without going through the Java pipeline.
- Live-reload via a watch flag (browser auto-refreshes when template or fixture files change; small standalone script, no build tool).

Two benefits:
1. **Sub-second iteration cycle** on visual changes — save template, browser reloads, see result. No Maven, no binary invocation, no test suite run.
2. **Real data shapes** — the fixtures are real `FeatureResult.toJson()` output captured from genuine runs (commit a curated set as part of Phase 1). Any prototype design idea that doesn't survive contact with the real data fails visibly here, not after merge.

Fixtures are captured by running `karate run --output-jsonl features/` against a representative suite, then trimming the JSONL to the shape needed.

**The harness is dev-only — never bundled in any release artifact.** It lives under `src/main/tailwind/` (build-tooling area), not under `src/main/resources/`, so it doesn't ship in the jar.

### 3.1.2 Template architecture — confirmed reality (not Thymeleaf)

Important orientation for future sessions: the templates are **static HTML with string-replace placeholders**, not Thymeleaf. Verified:

- `HtmlReportWriter.java:95` — `private static final String DATA_PLACEHOLDER = "/* KARATE_DATA */";`
- `karate-summary.html` line 220: `<script id="karate-data" type="application/json">/* KARATE_DATA */</script>`
- Body bootstraps via Alpine: `<body x-data="KarateReport.summaryData()">`. All rendering is client-side; the JSON in the placeholder is the only server-side contribution.
- Current chrome is Bootstrap 5 (`navbar-dark bg-dark`, `data-bs-theme`, `--bs-tertiary-bg`) + jQuery + `res/karate-report.js` exposing `KarateReport.{summaryData,featureData,timelineData}()`. Phase 1 replaces all three (Bootstrap → Tailwind, jQuery → Alpine, the big `karate-report.js` shrinks dramatically as Alpine takes over most interactivity).

**The placeholder model is what scales for Phase 1 + Phase 2.** `HtmlReportWriter` already has one placeholder (`/* KARATE_DATA */`); the spike adds two more in the same form:

| Placeholder | Phase | Replaced with |
|-------------|-------|---------------|
| `/* KARATE_DATA */` | (existing) | `FeatureResult.toJson()` / `SuiteResult.toJson()` |
| `<!-- KARATE_ICONS -->` | Phase 1 (D21) | Contents of `_icons.svg` (hidden SVG sprite, `<symbol>` per icon) |
| `<!-- KARATE_EXTS -->` | Phase 2 | One `<script src="ext/<name>/ext.js" defer></script>` + optional `<link rel="stylesheet" href="ext/<name>/ext.css">` per registered ext, ordered as encountered in `karate-boot.js` |

`HtmlReportWriter` becomes a sequence of three `String.replace` calls per template, plus the static-asset copy step for ext `static/` directories (Phase 2). No template engine introduced; the existing mental model holds.

### 3.2 Slot model

Named DOM containers in templates; ext-owned rendering. Five slot conventions, all data-attribute based:

| Slot | Location | Per-instance keys | Ext renders into |
|------|----------|-------------------|---------------------|
| `summary.cards` | Summary page, KPI row | none | extra metric/widget card |
| `summary.panels` | Summary page, below the fold | none | full-width panel (e.g. "OpenAPI coverage: 47/52") |
| `feature.panels` | Feature page, sidebar | `data-feature-name` | per-feature panel (e.g. "this feature touched 4 ops") |
| `step.embed` | Step row | `data-step-id`, `data-embed-name` | renders one named embed payload |
| `nav.pages` | Topbar nav (Summary / Timeline / *ext pages*) | none | extra `<a href=ext/<name>/page.html>` tab |

**Render contract.** Inside `alpine:init`, an ext script does:

```js
document.addEventListener('alpine:init', () => {
  // Per-step embed renderer — matches by embed name prefix
  Alpine.data('imageComparison', () => ({
    init() {
      const parts = JSON.parse(this.$el.dataset.parts);
      this.baseline = parts.find(p => p.role === 'baseline').url;
      this.current  = parts.find(p => p.role === 'current').url;
      this.diff     = parts.find(p => p.role === 'diff').url;
      this.meta     = JSON.parse(this.$el.dataset.meta);
    },
    // ... lightbox state
  }));
});
```

The core report walks `step.embeds[]`, and for each embed emits:

```html
<div data-slot="step.embed"
     data-step-id="..."
     data-embed-name="image-comparison"
     x-data="imageComparison"
     data-parts='[{"role":"baseline","url":"ext/image/assets/abc.png"}, ...]'
     data-meta='{"mismatchPercent":2.3,"threshold":0.05}'>
</div>
```

If no `Alpine.data(name)` is registered for an embed-name, the slot stays empty (gracefully no-ops; embed still appears in JSONL).

**Why DOM + Alpine over template-side rendering.** Exts are physically separate JARs; we don't want a Java-side template chunk SPI (template language coupling, lifecycle issues). Browser-side rendering keeps the asset pipeline one-way (Java writes embed data → JS renders) and lets the same HTML re-render after print/PDF without re-running the report.

### 3.3 Ext asset contribution

> Builds on the existing [DESIGN.md § Plugin Architecture](./DESIGN.md#plugin-architecture) (rename pending — see vocabulary note) and the `Plugin` interface defined in `karate-core/src/main/java/io/karatelabs/core/Plugin.java` (becomes `Ext.java` in Phase 2). The wire shape below is new; the registration entry point (`onBoot(Suite)`) is unchanged.

**Wire shape** — `META-INF/karate-ext/static/` inside the ext JAR:

```
META-INF/karate-ext/
├── manifest.json           # name, version, coreVersion, init, slots used
├── static/
│   ├── ext.js              # registered in alpine:init (D5)
│   ├── ext.css             # optional; loaded if present
│   └── assets/             # any other files (icons, etc.)
└── pages/                  # optional; static HTML pages for nav.pages slot
    └── image-comparison.html
```

`manifest.json` schema:

```json
{
  "name": "image",
  "version": "2.0.10",
  "coreVersion": "2.0.10",
  "init": "static/ext.js",
  "css":  "static/ext.css",
  "pages": [
    {"slot": "nav.pages", "title": "Image diffs", "href": "pages/image-comparison.html"}
  ]
}
```

**Loader.** `Ext.onBoot(Suite)` calls:

```java
suite.registerReportAssets("image", this.getClass().getClassLoader());
```

`Suite.registerReportAssets(name, ClassLoader)` walks the ext's `META-INF/karate-ext/` resources via `ClassLoader.getResources`, validates `manifest.json`, and stashes the descriptor under a new `Map<String, ExtAssetDescriptor> reportAssets` field on `Suite`. Paired getter `Suite.getReportAssets()` returns the immutable view — read by `HtmlReportWriter` at report-write time.

**Manifest validation failure modes** (all fail the Suite loud at `onBoot`, consistent with DESIGN.md § Plugin Architecture "Exceptions during `onBoot` fail the Suite"):

| Condition | Behaviour |
|-----------|-----------|
| `META-INF/karate-ext/manifest.json` missing | `RuntimeException("ext '<name>': no META-INF/karate-ext/manifest.json on classloader")` |
| `manifest.json` malformed JSON | wrapped `JsonParseException` rethrown with ext name |
| `coreVersion` key absent | `RuntimeException("ext '<name>': manifest.coreVersion required (running core <X.Y.Z>)")` |
| `coreVersion` present but ≠ running `Version.NUMBER` | `RuntimeException("ext '<name>': built for core <A.B.C>, running <X.Y.Z>; rebuild required")` (D8) |
| `init` / `css` paths resolve to missing resources | hard fail with path in message |

When `HtmlReportWriter` runs (post-suite), it copies each registered `static/` dir to `target/karate-reports/ext/<name>/` and splices the `<script src=ext/<name>/ext.js defer></script>` (and `<link rel=stylesheet href=ext/<name>/ext.css>`) tags into the `<!-- KARATE_EXTS -->` placeholder per §3.1.2 (same string-replace mechanism as `/* KARATE_DATA */`).

**No new methods on `Ext`.** Per D12 — the ext is responsible for calling `suite.registerReportAssets(...)` in its `onBoot`. Convention not contract; keeps the interface stable.

### 3.4 Ext globals — `boot.ext('image')` puts `image` in scenario scope

This is the D10 unification. Two pieces:

**(a) Suite-level global registry.**

```java
// new on Suite
public void registerGlobal(String name, Object instance);
public Object getGlobal(String name);
public Map<String,Object> getGlobals();
```

Inside `ImageExt.onBoot(Suite suite)`:

```java
suite.registerGlobal("image", new ImageApi(suite));
```

`ImageApi` is a POJO with whatever methods the ext exposes (`compare`, `mask`, `setBaselineDir`, …). It crosses the JS bridge through the existing `JsValue` adapter — same path as `karate.driver` (see [DESIGN.md § karate.* API](./DESIGN.md#karate-api), `Driver` row).

**(b) Scenario-runtime exposure.**

Seeding point is **`KarateJsBase`** — same class that mounts the `karate` global (DESIGN.md core-classes table, line ~59: "Shared state and infrastructure for KarateJs"). The seed runs *before* `karate-base.js` / `karate-config.js` evaluate; see [DESIGN.md § Dry Run](./DESIGN.md#dry-run) for the existing config-eval pipeline order this slots into. Names cannot collide with built-in globals (`karate`, `driver`, `read`, …) — collision throws at boot with `RuntimeException("ext global '<name>' collides with built-in '<name>'")`.

```gherkin
Scenario: pixel-diff
  * image.setBaselineDir('baselines')
  * image.compare({ baseline: 'home.png', actual: 'screenshots/home.png' })
```

**Per-call shape mirrors `karate.channel`.** See [DESIGN.md § Plugin Architecture](./DESIGN.md#plugin-architecture) for the singleton-per-Suite lifecycle; the only difference is the ext global is *exposed* in scenario scope (channels are returned per call).

### 3.5 Custom step keyword sugar — `* <extName> <expr>`

Follows the `doc` keyword pattern: dispatch at `StepExecutor.java:233` (`case "doc" -> executeDoc(step)` inside the keyword `switch` in `StepExecutor.run`), implementation at `StepExecutor.java:2765`. The new ext-keyword dispatch goes into the same switch, on the `default` branch:

```java
// StepExecutor.run — in the keyword switch, before the existing default JS eval:
default -> {
    String firstToken = StepUtils.firstWhitespaceToken(step.getText()); // NEW helper — needs adding to StepUtils; mirrors findReadCloseParen / findCallArgSeparator helpers already there
    Object global = runtime.getSuite().getGlobal(firstToken);
    if (global instanceof StepHandler handler) {
        executeExtStep(handler, step);
    } else {
        // existing default: JS eval
    }
}
```

`StepHandler` is a single-method functional interface exts implement on their global:

```java
public interface StepHandler {
    void handleStep(Map<String, Object> args, ScenarioRuntime runtime);
}
```

Step syntax:

```gherkin
# rest of the line after the global name is evaluated via evalKarateExpression
# — so it goes through the {/[ JSON-with-embedded-expressions branch
* image { compare: 'home.png', baseline: 'baselines/home.png', threshold: 0.02 }

# strings, ids, refs work the same way the doc keyword's payload works
* image 'home.png'
* image #(currentScreenshot)
```

The map (or scalar) is passed to `image.handleStep(args, runtime)`; the ext interprets keys. The ext gets the same `runtime` that `karate.*` methods get — full access to scenario state, current step, embeds. The "rest of the line" evaluation goes through [`StepExecutor.evalKarateExpression`](./DESIGN.md#karate-expression-evaluation), so `{...}` and `[...]` payloads transit the lenient-JSON + embedded-expression branch (DESIGN.md § Karate-Expression Evaluation).

**Why this is safe.** Dispatch only matches when the first whitespace-delimited token is a registered ext global *and* that global implements `StepHandler`. Falls through to JS eval otherwise — `* foo()` still works, `* foo.bar = 1` still works.

**Why this is overdue.** Users have asked for custom keywords since v1. The reason it was always refused — every new keyword needed a `case` in `StepExecutor` — vanishes once dispatch is data-driven on the registered-globals map. Ext keyword names are namespaced under the ext name; there's no global keyword pollution.

### 3.6 Image-comparison embed shape (D6)

Generalised `StepResult.Embed`:

```java
public class Embed {
    public final String name;            // "image-comparison" (Alpine component lookup key)
    public final List<Part> parts;       // multi-asset; legacy single-asset uses parts.size() == 1
    public final Map<String,Object> meta;
}
public class Part {
    public final String role;            // "baseline" | "current" | "diff" | "primary" | ...
    public final String mime;
    public final String url;             // when bytes were written to assets/
    public final byte[] data;            // inline form (legacy)
}
```

`Embed.toMap()` wire shape (lands in `FEATURE_EXIT.data.scenarioResults[].stepResults[].embeds[]` — see [DESIGN.md § Reports "Where named embeds live on the wire"](./DESIGN.md#reports) for the canonical placement, which the multi-part shape preserves):

```json
{
  "name": "image-comparison",
  "parts": [
    {"role": "baseline", "mime": "image/png", "url": "ext/image/assets/abc.png"},
    {"role": "current",  "mime": "image/png", "url": "ext/image/assets/def.png"},
    {"role": "diff",     "mime": "image/png", "url": "ext/image/assets/ghi.png"}
  ],
  "meta": {"mismatchPercent": 2.3, "threshold": 0.05, "passed": false}
}
```

Single-asset embeds (existing screenshot, `doc` HTML) get a single-element `parts[]` with `role: "primary"`. Backward-compat shim in `Embed.toMap`: when `parts.size() == 1 && parts[0].role == "primary"` *and* the part carries inline `data` (bytes, not a `url`), also emit the legacy `{mime_type, data, name}` top-level fields so consumers reading the v2-RC shape still work. When the single part is `url`-only, the shim is skipped — legacy consumers don't understand asset URLs and the multi-part receivers can already read `parts[]`.

---

## 4. Phased Implementation

Five PRs. Each phase ends with a green build, manual smoke pass, and an explicit exit criterion.

### Phase 1 — Tailwind restyle of existing reports (no ext code)

**Strategy: port, don't rewrite.** The existing three templates already wire `FeatureResult.toJson()` data correctly. Phase 1 replaces only the styling layer (jQuery + jquery-ui + Bootstrap → Tailwind + Alpine) and selectively adopts visual ideas from the prototype that survive contact with our actual data model. Anything in the prototype that assumes data we don't compute (or that's "fluff" — visuals dressed up to look impressive without informing the reader) gets rejected. The data wiring stays put; the chrome around it gets modernised.

**Scope:**
- Build apparatus (§3.1): vendored Tailwind binary + Maven mojo + hash gate.
- Dev preview harness (§3.1.1): CDN-based, with committed fixture JSONLs.
- Port `karate-summary.html`, `karate-feature.html`, `karate-timeline.html` in place — keep the existing Java data bindings (Thymeleaf or whatever's there today), replace the CSS classes + JS interactivity. Each template adopts a subset of prototype design ideas per the matrix below.
- Delete `jquery.min.js`, `jquery-ui.min.js`, `karate-report.js`, old `karate-report.css`.
- Add Alpine (3.x, vendored single file). Replace jQuery-driven interactions with Alpine components.
- Add DOM slot containers (per §3.2) **with no ext loader code**. Slots stay empty.
- Update `HtmlReportWriter` only where the template structure changed materially (e.g. if the data shape passed to the template needs new fields for an adopted design idea).

**Out of scope:**
- Ext asset copying, slot rendering, `karate-image`, `karate-openapi`.
- Any data-model extension to support prototype ideas (deferred to follow-up issues per the matrix).
- Backward-compat for any user who post-processes the old HTML's DOM structure — v2 hasn't GA'd.

**Design idea adoption matrix.** For each prototype idea, the decision and rationale:

| Idea | Decision | Notes |
|------|----------|-------|
| Dark theme default + light toggle (`data-theme="dark\|light"` on `<html>`) | **Adopt** | Pure Tailwind `dark:` variants; toggle in topbar; localStorage persists. |
| Top-bar nav (Summary / Timeline / Feature) | **Adopt** | Replaces today's plain header. Uses `brand.navbar` (`#212529`) per karatelabs-site palette. |
| Inter + JetBrains Mono fonts | **Reject — per D20** | System-font stack from karatelabs-site (`system-ui`, `-apple-system`, `Segoe UI`, etc.) replaces the prototype's Google-Fonts-loaded Inter/JetBrains. Air-gapped CI still gets identical rendering on any platform. |
| Prototype's green `#00d97e` accent | **Reject — per D20** | Use karatelabs-site's `accent` blue (`#60a5fa`) instead. Test reports get karatelabs.io visual continuity for free. |
| Iconography (status pills, sort arrows, theme toggle, copy button, etc.) | **Adopt — per D21** | Inline SVG via Heroicons, sourced into `_icons.html` Thymeleaf fragment. Mirrors karatelabs-site's existing inline-SVG pattern. No icon-font dependency. |
| KPI card row on Summary (total / pass / fail / skipped / duration) | **Adopt** | We compute all these on `SuiteResult`. |
| Animated pass-rate donut | **Adopt** | `passedRate` already exposed on `SUITE_EXIT.summary` per DESIGN.md § Reports. |
| Failures panel (list of failed scenarios + click-through) | **Adopt** | We have the data. |
| Slowest-scenarios panel | **Adopt** | We have durations per scenario. |
| Sortable feature table with tag-count badges + totals row | **Adopt** | Existing feature table — restyle + add tag chips. |
| Sticky scenario sidebar on feature page | **Adopt** | Pure CSS (`position: sticky`); huge usability win on long features. |
| Two-column HTTP block (method pill + status badge + headers + JSON) | **Adopt** | We have request/response data in `HttpRunEvent` / step embeds. |
| Copy-as-cURL button on HTTP block | **Adopt** | Synthesise from request data; client-side button. |
| Screenshot lightbox | **Adopt** | Replaces today's screenshot embed. Same `<dialog>` + Alpine pattern image-comparison plugin will use. |
| Expected-vs-actual diff block on `match` failures | **Adopt** | `Result.Failure` records (DESIGN.md § Match Engine) already carry path/reason/actualValue/expectedValue. |
| Outline + skipped scenario states with distinct styling | **Adopt** | Existing states; just need restyle. |
| Per-thread gantt rows on Timeline | **Adopt** | We have `threadId` on every step result. |
| Top-5 slowest scenarios on Timeline | **Adopt** | Trivial sort. |
| Tag pass-rate rings | **Defer** | Needs per-tag pass/fail aggregation that isn't computed today. Tracked as O13. Re-evaluate after Phase 1 ships and we see whether anyone asks. |
| Critical-path overlay on Timeline | **Reject** | Implies scheduling analysis (which scenarios were on the longest dependency chain) that has no meaning in Karate — scenarios are independent, parallelised by simple work-stealing. Visually impressive, semantically vacuous. |
| Speedup metric ("would have taken Xs serial, took Ys parallel") | **Adopt** | Easy to compute: `sum(scenario.durationMs) / suiteDurationMs`. Genuinely useful. |
| Thread-utilization heatmap | **Defer** | Needs busy-vs-idle per-thread timeline that's expensive to compute and rarely actionable. Re-evaluate if anyone complains the gantt isn't enough. |
| "Realistic-but-illustrative" mock data flourishes (e.g. 47/52 specific OpenAPI numbers in mock cards) | **Reject** | Mock-only data; no equivalent in real runs. |
| `backdrop-filter` + `color-mix()` heavy aesthetic effects | **Reject — per D20** | Skip for Phase 1. Stick to flat Tailwind utility classes; the karatelabs-site brand is restrained by design. These effects are exactly the "premium dev tool look" we're not optimising for. Re-evaluate when (if) a brand phase happens. |

**Files touched:** ~25 (Tailwind build dir, the three HTML templates, asset deletes, `HtmlReportWriter` if data shape needs extending for adopted ideas).

**Exit criteria** (each independently verifiable):
- `mvn -pl karate-core test` green.
- HTML report from `karate-core/src/test/java/.../examples` renders against the new Tailwind classes; visual smoke per the adoption matrix above (each "Adopt" row is a checkbox at PR review time).
- Existing functionality preserved verbatim: tag filter dropdown, scenario expand/collapse, step bodies, embed rendering for screenshots and `doc` HTML, line-number deep-links, the `@report=false` redaction behaviour from DESIGN.md.
- `jquery.min.js`, `jquery-ui.min.js`, old `karate-report.js`, old `karate-report.css` are gone from the repo.
- Hash-gate triggers on a deliberate template edit; build fails with the exact message `Tailwind CSS is stale. Run: mvn -pl karate-core karate:tailwind`.
- Dev preview harness (§3.1.1) loads each committed fixture and renders the templates against it with no console errors.

### Phase 2 — Ext SPI extensions + slot loader

**Scope:**
- Rename `Plugin` → `Ext`, `BootBinding.plugin(name)` → `BootBinding.ext(name)`, `boot.plugin('x')` JS surface → `boot.ext('x')`. Update DESIGN.md § Plugin Architecture → § Ext Architecture. Source files: `Plugin.java`, `BootBinding.java`, `BootLoader.java`, `Suite.java` (per DESIGN.md § Plugin Architecture "Source files" footer).
- Extend `Suite`: `registerReportAssets(name, ClassLoader)`, `getReportAssets()` (returns `Map<String, ExtAssetDescriptor>`), `registerGlobal(name, instance)`, `getGlobal(name)`, `getGlobals()`.
- Extend `HtmlReportWriter`: add the `<!-- KARATE_EXTS -->` string-replace placeholder (matching the existing `/* KARATE_DATA */` and Phase-1's `<!-- KARATE_ICONS -->` model — see §3.1.2). At write time, iterate `Suite.getReportAssets()` → copy each ext's `META-INF/karate-ext/static/` to `target/karate-reports/ext/<name>/` → assemble the `<script src=ext/<name>/ext.js defer></script>` (+ optional `<link rel=stylesheet>`) lines and splice into the placeholder → write `ext/<name>/pages/...` for any nav-page contributions.
- Extend `StepResult.Embed` to the multi-part shape (§3.6) with the single-part legacy shim.
- Add `StepHandler` interface + the `StepExecutor.run` keyword-switch dispatch change (§3.5). Add `StepUtils.firstWhitespaceToken(String)` helper.
- Extend `KarateJsBase` to seed `Suite.getGlobals()` into JS scope before `karate-base.js` / `karate-config.js` evaluate (see DESIGN.md § Dry Run for the existing config-eval pipeline order).
- Ext manifest validation: load `META-INF/karate-ext/manifest.json` on `registerReportAssets`; failure modes per §3.3 table (missing manifest, malformed JSON, missing/mismatched `coreVersion`, missing referenced resources) — all hard fail at boot.

**Out of scope:** any real ext. This phase has no production consumer; tests use a synthetic test-only ext under `karate-core/src/test/java/io/karatelabs/ext/dummy/`.

**Exit criteria** (each independently verifiable):
- Unit test: `DummyExt` in test scope registers a global `dummy`, contributes a `summary.panels` widget, exposes `* dummy { ... }` step keyword.
- End-to-end Suite test (`DummyExtE2ETest`) runs a feature; verification path is **HTML-parse, not Playwright** (no Playwright dependency added in this phase):
  - Global visibility assertion: feature contains `* def x = dummy.echo('hi')` and `* match x == 'hi'` (passes only if global was seeded into scope).
  - Step keyword assertion: feature contains `* dummy { action: 'mark' }` and a later `* match dummy.state == 'mark'`.
  - Asset-copy assertion: `target/karate-reports/ext/dummy/ext.js` exists after run.
  - Panel-render assertion: parse `target/karate-reports/karate-summary.html` with Jsoup, assert `<div data-slot="summary.panels" data-ext-name="dummy">` is present and `<script src="ext/dummy/ext.js">` is in `<head>`.
- Backward-compat: existing `step.embeds[]` JSONL consumers see the legacy `{mime_type, data, name}` shape for single-part inline-bytes embeds (per §3.6 shim rule).

**Files touched:** ~15 (Suite, HtmlReportWriter, StepResult, StepExecutor, StepUtils, KarateJsBase, Plugin→Ext rename, BootBinding, new SPI interfaces).

### Phase 3 — `karate-image` submodule (dogfood)

**Scope:**
- New submodule `karate-image/` (sibling to `karate-junit6`, `karate-gatling`).
- `pom.xml`: depends on `karate-core` (provided scope), brings in Resemble's *server-side* equivalent if needed (we'll likely just use AWT or a tiny pixel-loop — Resemble.js is browser-side only). Confirmed plan: keep the pixel-diff math from PR #2885's `ImageComparison.java` verbatim, just relocate.
- `pom.xml -Pfatjar`: bundles everything needed for `~/.karate/ext/` drop-in.
- `io.karatelabs.ext.image.ImageExt` — implements `Ext` (the renamed `Plugin` interface per Phase 2), registers global + assets in `onBoot`.
- `io.karatelabs.ext.image.ImageApi` — implements `StepHandler`. Methods: `compare(args)`, `setBaselineDir(path)`, `setThreshold(double)`. `handleStep(args, runtime)` dispatches by `args` keys (presence of `compare` → call `compare`).
- `META-INF/karate-ext/manifest.json`, `static/ext.js`, `static/ext.css`.
- `static/ext.js`:
  - Registers `Alpine.data('imageComparison', ...)` per §3.2.
  - Lightbox: `<dialog>` element + Alpine, no Bootstrap modal. Slider/blink/onion-skin toolbar.
  - On lightbox open, lazy-loads Resemble.js from `https://unpkg.com/resemblejs@5.0.0/resemble.js` (CDN per D11).
- `static/ext.css`: scoped under `.k-image-ext` to avoid colliding with core Tailwind.
- Optional: `pages/image-comparison.html` — top-level index of every diff in the run (uses `nav.pages` slot).

**Files cherry-picked from PR #2885:**
- `ImageComparison.java` (489 LOC) — relocated, package-renamed.
- `karate-feature.html` / `karate-summary.html` snippets — discarded (UI is rewritten).
- `KarateConfig.java` config-key additions — discarded; config moves to ext-instance properties set in `karate-boot.js`:
  ```js
  const image = boot.ext('image');             // post-D17 naming
  image.baselineDir = 'baselines';
  image.threshold = 0.02;
  image.report = 'mismatched';   // 'all' | 'mismatched' | null
  ```
- `karate.compareImage` Java method — **dropped** (D9). Migration note in MIGRATION_GUIDE.md.
- `ImageComparisonReport.java` (PDF, 291 LOC) — **deferred** to Phase 5.
- `Resemble.js` (1077 LOC bundled) — **dropped**, replaced by CDN.

**Exit criteria** (each independently verifiable):
- `mvn -pl karate-image test` green.
- `ImageExtE2ETest` runs a Suite with a `karate-boot.js` that calls `boot.ext('image')`; test `.feature` calls `* image { compare: ... }`; HTML-parse asserts the diff `<dialog>` element and three `<img>` tags (baseline/current/diff) are present in the written `karate-feature.html`.
- **Manual smoke step** (mark explicit — no automation): open the generated report in a browser, click a diff thumbnail, confirm lightbox opens and slider/blink/onion-skin toolbar works.
- Fatjar test: build `karate-image-X.Y.Z.jar`, then run in an **isolated home** so the test cannot pollute the developer's real `~/.karate/ext/`:
  ```bash
  TESTHOME=$(mktemp -d) && mkdir -p "$TESTHOME/.karate/ext" \
    && cp karate-image/target/karate-image.jar "$TESTHOME/.karate/ext/" \
    && HOME="$TESTHOME" karate run karate-image/src/test/resources/smoke/visual.feature
  ```
  Assert lightbox elements appear in the resulting `target/karate-reports/karate-feature.html`.

**Files touched:** new submodule ~25 files, plus root `pom.xml` `<modules>`.

### Phase 4 — `karate-openapi` migration

Lives in the separate `karate-ext` repo (proprietary; renamed from `karate-plugins` per D17). Expected on disk at sibling path `../karate-ext/karate-openapi/` relative to this repo. One PR there.

**Scope:**
- Update `karate-openapi` to use the new `Suite.registerReportAssets` + `registerGlobal` paths.
- Ext global name: `openapi`. Methods (TBD by openapi maintainer): probably `expect(op, opts)`, `summary()`.
- Slot contributions:
  - `summary.panels` — "OpenAPI coverage: N / M ops" + drill-down link.
  - `nav.pages` — `pages/openapi-coverage.html` — full coverage matrix (per-op pass/fail/untouched).
  - `step.embed` — name `openapi-match` already exists ([DESIGN.md § Reports "Where named embeds live on the wire"](./DESIGN.md#reports) names it explicitly); render it per-step.
- Rename `OpenapiPlugin` → `OpenapiExt`, package `io.karatelabs.plugins.openapi` → `io.karatelabs.ext.openapi`.
- Move `boot.ext('openapi')` config keys (`path`, `excludes`) — already per-instance property shape per [DESIGN.md § Plugin Architecture](./DESIGN.md#plugin-architecture); only the `boot.plugin` → `boot.ext` call site changes.

**Cross-repo dependency:** `../karate-ext/karate-openapi/pom.xml` declares `karate-core` 2.0.10 (or whatever this spike lands as). Strict version match per D8; `karate-ext` monorepo version mirrors `karate-core` per O12.

**Exit criteria** (each independently verifiable):
- `cd ../karate-ext && mvn test` green.
- karate-openapi's existing example suite: HTML-parse asserts `<div data-slot="summary.panels" data-ext-name="openapi">` is in `karate-summary.html` and `ext/openapi/pages/openapi-coverage.html` exists.
- Fatjar drop-in (use the same isolated-`HOME` pattern as Phase 3): `karate-openapi-X.Y.Z.jar` in `$TESTHOME/.karate/ext/`, no Maven; HTML-parse asserts coverage page is reachable from nav.

**Follow-up tracked separately** (note, not blocking spike): roll up "API calls touched" — a per-run summary like "Suite hit 47 distinct API operations" with click-through to the coverage page. Users have wanted this for years.

### Phase 5 — PDF rendering (deferred design)

Two paths. Both target the same print stylesheet so ext content renders in both.

**Path A — JSONL → HTML → print (default).**

- New module `karate-pdf` (OSS submodule, optional dep). Takes a `karate-events.jsonl` file, regenerates a printable HTML with the print-CSS variant of the Tailwind output, opens in a default browser for the user to print-to-PDF.
- Pro: zero new dependencies. Pure server-side. Always works.
- Con: requires user interaction (print dialog).

**Path B — `boot.ext('pdf-export')`.**

- Ext observes `SUITE_EXIT`, finds the just-written `karate-summary.html`, opens it in headless Chrome via the existing CDP infrastructure (`io.karatelabs.driver.cdp`), runs `Page.printToPDF` with `printBackground: true`, writes `target/karate-reports/karate-report.pdf`.
- One render. Native PDF bookmarks for `<a href="#scenario-x">` anchors. CSS `target-counter()` renders TOC with page numbers.
- Ext contracts (this fully dogfoods the ext system a third time):
  - No global needed (suite-lifetime observer, not user-facing).
  - No slot contribution.
  - Just the `Ext` interface + `onEvent(SUITE_EXIT)`.

**Ext print-CSS contribution.** Exts optionally ship `static/ext.print.css` alongside `static/ext.css`. Print pipeline injects it. Image-comparison's print CSS: stack the three images vertically (instead of lightbox), drop the toolbar.

**Exit criteria for Phase 5** (each independently verifiable when we get there):
- Path A: print-friendly HTML produced at `target/karate-reports/karate-print.html`; opening it in Chrome and triggering print-to-PDF produces a multi-page PDF with all features/scenarios present (manual check, mark explicit).
- Path B: PDF produced unattended at `target/karate-reports/karate-report.pdf`. HTML-parse-equivalent for PDF: use `pdfbox` to assert page count > 0, TOC bookmarks include each feature name, ext-contributed sections (image diffs, OpenAPI coverage) appear by text-search of the extracted PDF text.
- Large suite (100+ features) test: single-render PDF builds in <30s, has working TOC bookmarks (verified by `pdfbox`).

---

## 5. RELEASING.md amendments

Phase 3 introduces a new published artifact. Five additions:

1. **§2 (Publish Maven Artifacts):** confirm `io.karatelabs:karate-image:X.Y.Z` appears on Maven Central alongside `karate-core`.
2. **§3 (GitHub Release), Upload assets:** add bullet:
   - `karate-image-X.Y.Z.jar` (fatjar) — built via `mvn package -pl karate-image -Pfatjar`, output `karate-image/target/karate-image.jar`. Rename to versioned name for upload.
3. **§5 (karate.sh manifest):** add a new artifact entry `karate-image` with the fatjar URL + sha256. Same trim-to-3-versions rule.
4. **§6 (Reference Projects):** if `karate-template` / `karate-todo` use image comparison (likely not), bump. Add a sentence: "New OSS exts (karate-image, future) get the same X.Y.Z bump treatment as karate-core."
5. **§7 (karate-examples):** add an `image-comparison/` example project; bump it with the rest.

Also: the CI workflow that builds + tests `karate-core` should run `mvn -pl karate-image test` once per push (since karate-image is in-repo). A small `karate-image-fatjar-build` job verifies the fatjar profile too.

---

## 6. Open Questions / Follow-ups

These don't block the spike; capture so they don't get lost.

| # | Question | Owner |
|---|----------|-------|
| O1 | **Generic `karate-coverage` base** — punt per interview decision (D: "each ext owns its own page"). Revisit only after 2+ coverage-emitting exts exist (openapi + rules + requirements). | Defer to post-Phase 4. |
| O2 | **karate-openapi "API calls touched" roll-up** — separate panel on summary listing distinct ops the run touched. | karate-openapi maintainer; track issue. |
| O3 | **Custom step keyword conflict detection** — what happens if two exts both want global `data`? Currently boot fails on collision; design a clearer error. | Phase 2. |
| O4 | **Ext print stylesheet automatic detection** — design now or wait for Phase 5? Tentatively wait — only matters when Phase 5 path B lands. | Phase 5. |
| O5 | **Per-step embed naming convention** — `image-comparison` vs `image.comparison` vs `image-comparison-v1`. Mostly cosmetic; matters for the Alpine `Alpine.data(name)` registration key. Pick during Phase 3. | Phase 3. |
| O6 | **Tailwind utility classes for exts** — should exts also use Tailwind, or pure scoped CSS? If Tailwind, they need their own build. Lean toward "scoped CSS for exts, Tailwind for core" — exts stay self-contained, no shared build dependency. | Document in Phase 2. |
| O7 | **`karate.channel(...)` deprecation?** Boot-ext-globals overlap with channels in some uses (gRPC ext could expose `grpc` global instead of `karate.channel('grpc')`). Don't deprecate now — channels are per-call vs ext singletons-per-Suite, both valid. Revisit after several exts ship. | Watch. |
| O8 | **Tailwind binary licensing** — Tailwind standalone CLI is MIT; binaries we vendor are derived works. Confirm with legal before committing 120 MB of binaries; if blocked, fall back to download-on-build. | Pre-Phase 1. |
| O9 | **`@report=false` interaction with ext embeds** — DESIGN.md says `@report=false` strips step detail from artifacts. Ext embeds should respect this (image diffs of redacted scenarios must not leak). Verify in Phase 3 test. | Phase 3. |
| O10 | **JS scope seeding order** — globals must be visible to `karate-config.js`. Need to verify `karate-base.js` ordering doesn't clobber. | Phase 2. |
| O11 | **Channel resolver unification (D18 follow-through)** — refactor `KarateJs.channel()` (KarateJs.java:1202) to drop `KarateConfig.getChannelFactoryClass(type)` in favour of the shared `io.karatelabs.ext.<name>.<Name>Channel` resolver. Existing `karate-plugins/karate-grpc` etc. must be repackaged from `io.karatelabs.plugins.grpc.GrpcChannelFactory` → `io.karatelabs.ext.grpc.GrpcChannel`. Lock-step with D8 strict version match — `karate-ext`'s monorepo version tracks `karate-core` version exactly (a `karate-core` 2.0.10 cuts a `karate-ext` 2.0.10). | Phase 2 (resolver) + sibling repo PR + karate-ext version-sync as part of D17 rollout. |
| O12 | **`karate-ext` monorepo version tied to `karate-core` version** — every karate-core release triggers a matching karate-ext release at the same version number, even when no ext module changed (no-op republish keeps the strict-match contract working). RELEASING.md needs a step for this. | RELEASING.md §2 amendment (Phase 3 in this spike); enforced by the loader check from D8. |
| O13 | **Per-tag pass-rate aggregation** — deferred from Phase 1 design matrix (prototype's "tag pass-rate rings"). Requires `SuiteResult` to expose `Map<String, {passed, failed, skipped}> tagStats`. Light to compute, but no current consumer asks for it; ship Phase 1 without it, add when a user requests. | Post-Phase 1 if asked. |
| O14 | **Karate-OSS distinct brand** — Phase 1 (D20) reuses karatelabs-site's slate-blue brand for visual continuity. Open question whether the OSS report should eventually have its own visual identity distinct from the marketing site (e.g. to signal "this is the OSS product, not the commercial offering"). Triggers a brand-design phase that produces a Karate-OSS palette/typography spec; the spec then drops into `karate-core/src/main/tailwind/tailwind.config.js`'s `theme.extend` and the `@layer components` block we deliberately skipped. No template rewrite needed if Phase 1 stuck to utility classes (D20). | Post-spike; needs design input. |
| O15 | **Thread-utilization on Timeline** — deferred from Phase 1 matrix. If anyone says the per-thread gantt isn't enough to understand parallelism behaviour, revisit. | Post-Phase 1 if asked. |

---

## 7. Out of scope

- Removing or deprecating `karate.channel()` (O7).
- Generic coverage data model (O1).
- Ext-discovery via `META-INF/services/io.karatelabs.core.Ext` (rejected mid-interview — `boot.ext('name')` is the explicit activation; auto-discovery is surprise behavior).
- IDE plugin updates for new report format (separate workstream; "IDE plugin" here is the IntelliJ/VS Code plugin, not the runtime Ext).
- Mock-server report integration.
- Backward-compat shim for the legacy jQuery template — none (D: hard-replace, v2 not yet GA).

---

## 8. Quick reference — what each artifact looks like at the end of the spike

```
# karate (this repo)
karate-core/
  src/main/tailwind/{tailwind.config.js, input.css}
  src/main/resources/io/karatelabs/output/
    karate-summary.html       # Tailwind, dark/light, Alpine
    karate-feature.html       # ditto, with slot containers
    karate-timeline.html      # ditto
    res/karate-report.css     # generated, checked in
    res/karate-report.css.hash
    res/alpine.min.js         # vendored
karate-image/                  # NEW
  pom.xml                      # -Pfatjar profile
  src/main/java/io/karatelabs/ext/image/
    ImageExt.java
    ImageApi.java              # implements StepHandler
    ImageComparison.java       # cherry-picked from PR #2885
  src/main/resources/META-INF/karate-ext/
    manifest.json
    static/ext.js, ext.css
    pages/image-comparison.html
  src/test/java/...

# karate-ext (sibling repo, proprietary; renamed from karate-plugins, D17)
karate-openapi/
  src/main/java/io/karatelabs/ext/openapi/
    OpenapiExt.java         # updated: registerGlobal + registerReportAssets
  src/main/resources/META-INF/karate-ext/
    manifest.json, static/, pages/openapi-coverage.html

# user's project
.
├── karate-boot.js
│   const image = boot.ext('image'); image.baselineDir = 'baselines';
│   const openapi = boot.ext('openapi'); openapi.path = 'api/openapi.yaml';
├── karate-pom.json
├── baselines/home.png
└── features/visual.feature
    * image { compare: 'screenshots/home.png' }
    * call read('login.feature')

# user fatjar mode
~/.karate/ext/karate-image-2.0.10.jar
~/.karate/ext/karate-openapi-1.0.0.jar
$ karate run features/    # Rust launcher; sees both exts; report has everything
```

---

## 9. Credit

PR #2885 contributor's pixel-diff math and v1-parity feature design carry into Phase 3 verbatim, even though the UI and integration are rewritten. Acknowledge in AUTHORS, MIGRATION_GUIDE.md (under "image comparison — moved to karate-image ext"), and Phase 3 PR description. Loop the contributor in for review of the Phase 3 PR before merge.

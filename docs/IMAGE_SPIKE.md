# Image Comparison Spike — Ext System Dogfood

> **Status (resume point).** Supersedes [PR #2885](https://github.com/karatelabs/karate/pull/2885); proves the v2 ext model end-to-end through the first real ext, `karate-image`.
> - **Phase 1 + 1b — complete:** Tailwind report restyle + prototype feature adoption (see those sections; treat `main` + the live templates as the spec).
> - **Phase 2 — complete:** the ext SPI. Ext globals (`Suite.registerGlobal/getGlobal/getGlobals`, seeded in `ScenarioRuntime.initEngine`, `SimpleObject`-based); report assets (imperative `ReportAssets` — no JSON manifest, no version guard — copied + spliced into `<!-- KARATE_EXTS -->` by `HtmlReportWriter`/`HtmlReportListener`); multi-part `StepResult.Embed` `{name, parts[], meta}` (clean break, §3.6). Test-only `DummyExt` fixture + `DummyExtE2ETest` / `ReportAssetsTest` / `StepResultEmbedTest`. Custom step-keyword dispatch (§3.5) **deferred** into O21.
> - **Next: Phase 3 — `karate-image` submodule** (the dogfood; first real consumer of the global + slot + embed machinery). **Do O22 first** — review all ext contracts for future-proofness while breaking them is still free (no shipped consumers yet).
> - Phases 4 (`karate-openapi`) + 5 (PDF) remain as designed below.
>
> **Related:** [DESIGN.md § Ext Architecture](./DESIGN.md#ext-architecture) · [CLI.md](./CLI.md) · [RELEASING.md](./RELEASING.md) · [unified-traceability-substrate memo](../../veriquant/docs/_wip/design-memos/unified-traceability-substrate-2026-05-29.md) — the first *real* O22 consumer (the commercial `karate-xplorer` über-ext); its §6 is the executed O22 audit, and it confirms the SPI contracts hold (its one core gap — nav.pages render — is now closed; see O22 + §3.2) · Prototype (not committed): `~/Downloads/karate-report-redesign/` on the maintainer's machine — design-only reference, intentionally not in repo because it predates the feature/scenario/step data-model decisions Phase 1 makes
>
> **Vocabulary (D17).** All SPI types and runtime symbols use `Ext` — interface `Ext`, `boot.ext('name')`, `io.karatelabs.ext.<name>.<Name>Ext`, sibling repo `karate-ext`. Wire field is `SUITE_ENTER.data.exts[]`. The §2 D-rows describing the rename use historical "plugin" terminology for decision provenance only.

---

## 1. Goals

1. **Restyle the HTML report** by porting the existing three templates (`karate-summary.html`, `karate-feature.html`, `karate-timeline.html` under `karate-core/src/main/resources/io/karatelabs/output/`) to Tailwind, cherry-picking design ideas from the prototype at `~/Downloads/karate-report-redesign/` that align with our actual data model. The prototype was built by a design-first contributor without the feature→scenario→step hierarchy in view, so some of its KPIs and panels assume data we don't have; the port treats it as a visual vocabulary and aesthetic reference, not a one-to-one template port. Phase 1 (chrome swap) + Phase 1b (matrix adoption) are complete; remaining "Adopt" items deferred — see Phase 1b § Deferred.
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
| D1 | **Real Tailwind for production**, checked-in precompiled CSS regenerated by `etc/tailwind/tailwind.sh` (§3.1). CI guards staleness via `git diff --exit-code` on the output CSS after re-running the script. Dev iteration runs the existing `HtmlReportWriterTest` + `bash etc/tailwind/tailwind.sh --watch` (§3.1.1) — same script as CI, no separate toolchain, no node / npm dependency. **Carve-out (O20):** the Timeline page loads `vis-timeline@7` from `unpkg.com`; rarely used, vendoring didn't justify the cost. Every other report page is CDN-free. | Production reports stay offline-capable, deterministic, and don't depend on a CDN at view time (Timeline page excepted, see O20). Dev iteration runs the actual production code path so styling changes can't quietly diverge from what users see. |
| D2 | **Strict phase 1 → phase 2.** Redesigned report ships first (with slot DOM hooks but no plugin loader); plugin SPI and `karate-image` land after. | Smaller PRs; first PR proves the design language, second proves the SPI. |
| D3 | **All slot types** supported: per-step embed, summary panel, feature panel, extra top-level page | One slot mechanism, four naming conventions. Plugin-owned render. |
| D4 | **Plugin JAR ships its own JS+CSS** under `META-INF/karate-ext/static/`; core copies into `target/karate-reports/ext/<name>/` at write time | Self-contained; versioned with plugin JAR; loaded only when plugin active. |
| D5 | **Alpine init order** via `defer` + `alpine:init` event hook. Plugins register `Alpine.data(...)` inside the hook. | Documented Alpine pattern; no template-ordering coupling. |
| D6 | **Embed schema generalised to `parts[]`** — `{name, parts: [{role, mime, data\|url}], meta}` | One diff = three images + metadata. Future-proofs `grpc-match` etc. |
| D7 | **Plugin distribution = existing `~/.karate/ext/` + `.karate/ext/`** dirs picked up by the Rust launcher | Zero new infra; we only need to test the path. |
| D8 | **Strict core-version match** — plugin manifest carries `coreVersion`; loader fails fast on mismatch | Forces lock-step releases. Cheaper than a half-working SPI. **Update (Phase 2 build): dropped for now.** With the JSON manifest gone (§3.3) there's no natural build-time stamp for the built-against version, and exts ship in lockstep with core today. No version guard in `ReportAssets`; revisit if independently-versioned drop-in JARs make a runtime mismatch likely. |
| D9 | **No `karate.*` JS method contribution from plugins.** Breaking change from v1's `karate.compareImage()` | Forces a clean SPI; plugin globals land in scenario scope under their own name. |
| D10 | **Plugin global + custom step keyword.** `boot.plugin('image')` → `image` global is in scope inside scenarios. `* image { compare: '...', baseline: '...' }` dispatches to the same global | Unifies channel + boot models; finally lets users extend Karate keywords. **Update (Phase 2):** only the ext-global half of this ships in Phase 2; the custom step keyword (`* image { ... }`) is deferred into O21 with no ETA — see §3.5. The global covers the surface via `image.compare(...)` + property-setters. |
| D11 | **Resemble.js stays, loaded from CDN** (lazy on lightbox open) | Server emits precomputed diff PNG; Resemble.js powers interactive tools (slider, blink, onion-skin). CDN keeps plugin JAR small. |
| D12 | **No new methods on `Plugin` interface** — assets + init + globals registered imperatively from `onBoot(Suite)` | Compat with existing SPI; everything funnels through `Suite` registries. |
| D13 | **Cherry-pick PR #2885's server-side Java** (`ImageComparison.java`, pixel-diff logic); rewrite UI + step integration to fit plugin model | Don't redo solid pixel math; do redo jQuery UI. Credit contributor in MIGRATION_GUIDE + AUTHORS. |
| D14 | **PDF deferred to Phase 5.** Two paths kept open: print-CSS via JSONL→HTML pipeline (default), headless-Chrome via existing CDP machinery (single-render multi-page with native PDF bookmarks + CSS `target-counter()` for TOC) | PDF design is real work; gating the spike on it is counterproductive. |
| D15 | **Module name: `karate-image`** (over `karate-imgutils`, `karate-visual`) | Matches `karate-junit6`/`karate-gatling` brevity; accurate today; room for image-adjacent additions (screenshot regression, OCR). |
| D16 | **Image comparison ships OSS as in-repo submodule** | Was OSS in v1. Submodule means single repo, single CI, fatjar build runs alongside core's. |
| D17 | **Rename "plugin" → "ext" across the SPI.** `Plugin` interface → `Ext`. `boot.plugin('x')` → `boot.ext('x')`. `io.karatelabs.plugins.foo.FooPlugin` → `io.karatelabs.ext.foo.FooExt`. Sibling repo `karate-plugins` → `karate-ext`. Section header in DESIGN.md "Plugin Architecture" → "Ext Architecture". | Disambiguates from IDE plugins / Maven plugins / Gradle plugins. Aligns with `~/.karate/ext/` + `.karate/ext/` dirs that already exist (DESIGN.md, CLI.md, karate-cli/docs/spec.md). One vocabulary across runtime SPI, on-disk classpath dir, and the umbrella repo. v2 hasn't GA'd — clean rename now or never. |
| D18 | **`karate.channel('type')` resolution moves to the unified registry.** Today `channel()` hard-codes a lookup against `KarateConfig.getChannelFactoryClass(type)`. After the ext rename, the channel SPI resolves via the same `io.karatelabs.ext.<name>.<Name>Channel` convention as ext-globals. | Two name-convention resolvers (one for channels, one for ext) is the kind of duplication that rots into divergence. One resolver, two roles (per-call factory vs Suite-lifetime singleton). |
| D19 | **Extensions split across two licensing tiers, same SPI, same resolver.** OSS extensions live as submodules in *this* repo (`karate-image`, future visual/a11y/screenshot-regression tooling) — Apache/MIT, published under `io.karatelabs:karate-*`. Proprietary extensions live in the sibling `karate-ext` monorepo (renamed from `karate-plugins`, per D17) — license-gated, published under `io.karatelabs.ext:karate-*` (or kept private). Both tiers implement the same `io.karatelabs.core.Ext` SPI, resolve via the same `io.karatelabs.ext.<name>.<Name>Ext` name-convention, and activate the same way (`boot.ext('name')`). The runtime cannot tell them apart; only the JAR's licence header + Maven coordinate group does. We use "extension" / "ext" terminology despite VS Code's overlap — `ext` as a 3-letter token is unambiguous in context (filesystem dir, boot API, SPI name). | Single SPI surface, single mental model for users and contributors. License model is metadata, not architecture. Lets a future OSS extension graduate into core or a proprietary extension be open-sourced without an API break. |
| D20 | **Phase 1 sticks to Tailwind built-ins + the Karate Labs brand palette.** No `@layer components` for Karate-specific classes (no `.k-card`, `.k-pill-pass`); use utility classes directly in templates. The `theme.extend` block in `etc/tailwind/tailwind.config.js` carries the brand palette: slate `brand` scale (`#0f172a` → `#475569` + `navbar` `#212529`), `accent` blue (`#60a5fa`), `amber` warning, `surface` neutral scale, **system-font stack** (no Google Fonts CDN). Overrides the prototype's Inter + JetBrains Mono + green-accent aesthetic. Per-ext CSS classes (e.g. image-comparison's `.k-image-ext` scope) remain fine — they isolate ext-owned styles, not Karate brand. | Restrained brand suited to a utility tool (test reports land in CI artifacts + Jira tickets, where visual quietness > visual flair). Zero font CDN dependency keeps reports rendering styled in air-gapped CI. Sticking to utility classes (no component layer) means the brand can be revisited later without a template rewrite — just swap `tailwind.config.js`. |
| D21 | **Icons: Heroicons sourced into an inline SVG sprite, spliced by `HtmlReportWriter` at write time.** Source: [heroicons.com](https://heroicons.com) (MIT, made by Tailwind Labs, 24×24 viewBox is the standard outline-icon size). Implementation matches the existing `HtmlReportWriter` model (see §3.1.2): one `karate-core/src/main/resources/io/karatelabs/output/_icons.svg` containing `<svg style="display:none"><symbol id="icon-check" viewBox="0 0 24 24">…</symbol>…</svg>`; templates carry a new `<!-- KARATE_ICONS -->` placeholder near `<body>` start; `HtmlReportWriter` string-replaces it with the sprite contents (same mechanism as `/* KARATE_DATA */`). Templates reference icons via `<svg class="w-4 h-4"><use href="#icon-check"/></svg>` — color inherits from `text-*` utilities, size from `w-*`/`h-*`. Inline (not file-referenced) sprite avoids `file://` CORS when reports are opened locally. ~2 KB per template; deduplicates SVG paths across the three pages. Estimated icon count for Phase 1: ~15-25 (check / X / dash for pass/fail/skip, chevrons for sort + expand, sun/moon for theme toggle, clipboard for copy-as-cURL, link/anchor, exclamation-triangle for failures, document for embeds, clock for durations). | Reject Bootstrap Icons / Font Awesome (webfont = CDN + FOIT). Reject Lucide/Tabler/Phosphor (good libs, but Heroicons is the canonical Tailwind pairing). Reject Thymeleaf-fragment approach (templates are static HTML + string-replace — no template engine). Per-ext icons follow the same inline-SVG pattern inside the ext's own `static/` assets. |

---

## 3. Architecture

### 3.1 Tailwind build (production)

**Source dir at `etc/tailwind/`** (out of `karate-core/src/main` so build artifacts don't ship in the jar):

- `tailwind.config.js` — content globs (repo-root-relative) over `karate-core/src/main/resources/io/karatelabs/output/*.html` **and** `res/karate-report.js` (the JS file generates Tailwind class names inside HTML-string template literals; without the JS file in the scan, those classes wouldn't land in the output CSS). `darkMode: ['selector', '[data-theme="dark"]']`. `theme.extend` carries the Karate Labs brand palette per D20 (slate `brand` scale, `accent` blue, `amber`, `surface` neutrals, system-font stack). `safelist: []` is the (currently empty) ext-facing utility seam per O6 — utilities core doesn't itself render but wants to guarantee for exts go here, since the `content` globs never scan ext JARs.
- `input.css` — `@tailwind base; @tailwind components; @tailwind utilities;` plus an `[x-cloak] { display: none !important; }` Alpine-specific shim. **No `@layer components` block** in Phase 1 (D20).
- `tailwind.sh` — single build script (~50 LOC, ported from `studio/conf/tailwind.sh`). On first run, downloads the Tailwind v3.4.17 standalone CLI for the current platform from `https://github.com/tailwindlabs/tailwindcss/releases/download/v3.4.17/tailwindcss-{os}-{arch}` and caches it under `etc/tailwind/.cache/` (gitignored). Subsequent runs reuse the cached binary. Pass `--watch` for the dev iteration loop.
- Output: `karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css` (checked in, generated, ~18.9 KB minified). Production templates reference it via `<link rel="stylesheet" href="res/karate-report.css">`. No CDN dependency in shipped reports — they render styled offline and in air-gapped CI. (Caveat: Timeline page still loads `vis-timeline` from `unpkg.com` — see O20.)

**Why no node, npm, Maven mojo, or vendored binary.** Considered all three; rejected:

- *Vendored standalone binary per-OS* (`etc/tailwind/{linux,macos,windows}-{x64,arm64}/`, ~120 MB checked in): bloats the repo for what is fundamentally a single fetch-once-per-host concern. Also raises a licence question (O8) for what's an MIT-licensed binary we'd be redistributing.
- *Maven mojo* (`mvn -pl karate-core karate:tailwind`): adds a new module just to shell out to one binary. The shell script does it directly without the indirection.
- *node / npm via `frontend-maven-plugin` or `npx`*: drags in a JS toolchain for a single command-line invocation. The Tailwind standalone CLI exists precisely to avoid that.

The `tailwind.sh` + just-in-time download model is what `karate-studio` uses in production, so the pattern is already battle-tested in a sibling project.

**CI staleness gate (`git diff --exit-code`).** `.github/workflows/cicd.yml` runs `bash etc/tailwind/tailwind.sh` and then `git diff --exit-code` on the output CSS:

```yaml
- name: Verify Tailwind CSS is up to date
  run: |
    bash etc/tailwind/tailwind.sh
    if ! git diff --exit-code karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css; then
      echo "::error::karate-report.css is stale. Run 'bash etc/tailwind/tailwind.sh' locally and commit the regenerated CSS."
      exit 1
    fi
```

A contributor who edits a template (or `tailwind.config.js` / `input.css`) without re-running the script gets a red CI job pointing at the exact CSS bytes that changed.

**Why checked-in.** Most contributors edit Java, not templates. Forcing every Java-only edit through a Tailwind rebuild is friction. The CI guard makes template edits loud without taxing unrelated work — Java-only contributors don't need any of `tailwind.sh`, the binary, or the `.cache/` dir.

### 3.1.1 Dev iteration loop (no separate harness needed)

For sub-second iteration on Tailwind class changes while working on templates, **reuse the existing `HtmlReportWriterTest#testHtmlReportGeneration`** — it writes a full report to `target/karate-report-dev/` (persistent dir, designed for visual inspection per the test's docstring) from a representative 33-scenario mixed pass/fail/skip suite.

The inner loop:

```bash
# Terminal 1: generate the report once against real data.
mvn -pl karate-core test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q
open karate-core/target/karate-report-dev/karate-summary.html

# Terminal 2: watch + rebuild Tailwind CSS on every template/config save.
bash etc/tailwind/tailwind.sh --watch

# Edit a template (no need to re-run mvn — the templates+JSON are already on disk,
# and watch regenerates the CSS the report references). Browser reload, see result.
```

Two properties over a separate-harness design:
- **Same production code path** — same `HtmlReportWriter`, same templates, same data wiring. No risk of a parallel "preview-only" code path quietly diverging from production styling or interaction behaviour.
- **Same script as CI** — `tailwind.sh --watch` invokes the exact same binary that the CI guard runs without `--watch`. CI-vs-local skew can't bite you.

The only cost is needing `bash` + `curl` available during template work (the script downloads the CLI on first use). That cost is contained to maintainers iterating on templates; regular contributors editing Java never touch it.

### 3.1.2 Template architecture — confirmed reality (not Thymeleaf)

Important orientation for future sessions: the templates are **static HTML with string-replace placeholders**, not Thymeleaf. Verified:

- `HtmlReportWriter.java` defines three placeholder constants near line 95 (`DATA_PLACEHOLDER`, `ICONS_PLACEHOLDER`, `EXTS_PLACEHOLDER`) and one `inlineJson(template, data)` method that performs all three substitutions before write.
- Each template ends with `<script id="karate-data" type="application/json">/* KARATE_DATA */</script>`; bodies bootstrap via Alpine (`<body x-data="KarateReport.summaryData()">` etc.). All rendering is client-side; the JSON in the placeholder is the only server-side contribution.
- Post-Phase-1-foundation chrome is Tailwind + Alpine (no Bootstrap, no jQuery). `karate-report.js` (~530 lines) holds the Alpine data factories + `_renderStep`/`_renderEmbed` HTML-string generators + the `statusOf` / `_renderErrorBlock` helpers. jQuery was already gone before this spike.

**The placeholder model is what scales for Phase 1 + Phase 2.** `HtmlReportWriter` already has one placeholder (`/* KARATE_DATA */`); the spike adds two more in the same form:

| Placeholder | Phase | Replaced with |
|-------------|-------|---------------|
| `/* KARATE_DATA */` | (existing) | `FeatureResult.toJson()` / `SuiteResult.toJson()` |
| `<!-- KARATE_ICONS -->` | Phase 1 (D21) | Contents of `_icons.svg` (hidden SVG sprite, `<symbol>` per icon) |
| `<!-- KARATE_EXTS -->` | Phase 2 | One `<script src="ext/<name>/ext.js" defer></script>` + optional `<link rel="stylesheet" href="ext/<name>/ext.css">` per registered ext, ordered as encountered in `karate-boot.js` |

`HtmlReportWriter` becomes a sequence of three `String.replace` calls per template, plus the static-asset copy step for ext `static/` directories (Phase 2). No template engine introduced; the existing mental model holds.

### 3.2 Slot model

Named DOM containers in templates; ext-owned rendering. Five slot conventions, all data-attribute based:

| Slot | Location | Per-instance keys | Ext renders into | Wired today? |
|------|----------|-------------------|---------------------|--------------|
| `summary.cards` | Summary page, KPI row | none | extra metric/widget card | ✗ container not in template |
| `summary.panels` | Summary page, below the fold | none | full-width panel (e.g. "OpenAPI coverage: 47/52") | ✅ `<div data-slot="summary.panels">` present |
| `feature.panels` | Feature page, sidebar | `data-feature-name` | per-feature panel (e.g. "this feature touched 4 ops") | ✗ container not in template |
| `step.embed` | Step row | `data-step-id`, `data-embed-name` | renders one named embed payload | ◑ embeds render inline via `_renderEmbed`, not a `data-slot` host |
| `nav.pages` | Topbar nav (Summary / Timeline / *ext pages*) | none | extra `<a href=ext/<name>/page.html>` tab | ✅ server-side splice via `<!-- KARATE_NAV -->` (see below) |

> **Caveat — slot reality vs. design (O22 audit, [memo §6e](../../veriquant/docs/_wip/design-memos/unified-traceability-substrate-2026-05-29.md)).** The five slots above are the *design*; in the shipped templates, `summary.panels` (client-side container) and `nav.pages` (server-side splice) are wired; `summary.cards` and `feature.panels` are not yet. Per-step embeds render inline (`res/karate-report.js` `_renderEmbed`) rather than through a `step.embed` `data-slot` host — fine for Phase 3 (`karate-image` renders its lightbox off the embed name + `Alpine.data`, no container needed).
>
> **`nav.pages` — now wired (server-side splice).** `HtmlReportWriter` carries a third splice placeholder, `<!-- KARATE_NAV -->` (alongside `/* KARATE_DATA */` and `<!-- KARATE_EXTS -->`), in the topbar `<nav>` of all three templates (the nav is hardcoded per-page, not a shared fragment, so the placeholder is in each). `buildNavHtml(reportAssets, relPrefix)` emits one `<a href="<relPrefix>ext/<name>/<page.href>">title</a>` per ext `page("nav.pages", title, href)` contribution, registration-ordered, carrying the same utility classes as the built-in Summary/Timeline links, with the `../` prefix on feature pages — exactly mirroring the `KARATE_EXTS` asset splice so URLs agree. Page titles are HTML-escaped (ext-author strings). So an ext's `page("nav.pages", "Image diffs", "pages/image-comparison.html")` now produces a clickable topbar tab. Covered by `DummyExtE2ETest#extAssetsCopiedAndScriptSpliced` (asserts the tab on summary/feature/timeline with the right prefix, and that the placeholder is fully replaced). `karate-image`'s optional `pages/image-comparison.html` index is therefore unblocked.

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

> Builds on the existing [DESIGN.md § Ext Architecture](./DESIGN.md#ext-architecture) and the `Ext` interface defined in `karate-core/src/main/java/io/karatelabs/core/Ext.java`. The registration entry point (`onBoot(Suite)`) is unchanged.
>
> **Decision (Phase 2 build): imperative registration, no `manifest.json`.** The ext object is already live at `onBoot`, so asset wiring is declared in Java via a fluent `ReportAssets` spec — one source of truth, type-safe, no JSON-vs-file drift, and aligned with D12's "registered imperatively from `onBoot`". The earlier JSON-manifest design (struck through below for provenance) was dropped because it duplicated `onBoot` + `Ext.getManifest()` and its only non-redundant value (build-time `coreVersion` stamping for out-of-process tooling) isn't used — auto-discovery is explicitly rejected (§7). The `coreVersion` strict-match (D8) was **also dropped for now**: exts ship in lockstep with core; revisit only if independently-versioned drop-in JARs make a mismatch likely.

**On-disk shape** — assets live under `META-INF/karate-ext/` inside the ext JAR (no manifest file):

```
META-INF/karate-ext/
├── static/
│   ├── ext.js              # registered in alpine:init (D5)
│   ├── ext.css             # optional; loaded if present
│   └── assets/             # any other files (icons, etc.)
└── pages/                  # optional; static HTML pages for nav.pages slot
    └── image-comparison.html
```

**Registration.** `Ext.onBoot(Suite)` builds a `ReportAssets` spec and hands it over:

```java
suite.registerReportAssets(
    ReportAssets.named("image")
        .js("static/ext.js")          // required
        .css("static/ext.css")        // optional
        .page("nav.pages", "Image diffs", "pages/image-comparison.html"),
    getClass().getClassLoader());
```

`ReportAssets` (`io.karatelabs.core.ReportAssets`) is a fluent builder: `named(name)`, `js(path)` (required), `css(path)`, `page(slot, title, href)` (zero or more). Paths resolve against `META-INF/karate-ext/` on the ext's classloader. `Suite.registerReportAssets(ReportAssets, ClassLoader)` validates + binds the classloader, then stashes it under `Map<String, ReportAssets> reportAssets`; `Suite.getReportAssets()` returns the immutable view — read by `HtmlReportWriter` at report-write time.

**Validation failure modes** (all fail the Suite loud at `onBoot`, consistent with DESIGN.md § Ext Architecture "Exceptions during `onBoot` fail the Suite"):

| Condition | Behaviour |
|-----------|-----------|
| `js(...)` not set | `RuntimeException("ext '<name>': ReportAssets.js(...) is required")` |
| `js` / `css` / `page` href resolves to a missing resource | `RuntimeException("ext '<name>': <key> points at missing resource: META-INF/karate-ext/<path>")` |

When `HtmlReportWriter` runs (post-suite), it copies each registered ext's declared assets to `target/karate-reports/ext/<name>/` (the `static/` prefix is stripped — `static/ext.js` → `ext/<name>/ext.js`; `pages/` is kept) and splices `<script src=ext/<name>/ext.js defer></script>` (and `<link rel=stylesheet href=ext/<name>/ext.css>`) into the `<!-- KARATE_EXTS -->` placeholder per §3.1.2 (same string-replace mechanism as `/* KARATE_DATA */`). Feature pages live under `feature-html/`, so their ext refs carry the `../` prefix, mirroring how the templates reference `res/`.

**No new methods on `Ext`.** Per D12 — the ext is responsible for calling `suite.registerReportAssets(...)` in its `onBoot`. Convention not contract; keeps the interface stable.

<details><summary>Superseded JSON-manifest design (provenance)</summary>

The original §3.3 shipped a `META-INF/karate-ext/manifest.json` (`name`, `version`, `coreVersion`, `init`, `css`, `pages`) read by `registerReportAssets(name, ClassLoader)` into an `ExtAssetDescriptor`, with failure modes for missing/malformed manifest and `coreVersion` absent / mismatched (D8). Dropped during the Phase 2 build in favour of the imperative `ReportAssets` spec above.

</details>

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

`ImageApi` should implement [`io.karatelabs.js.SimpleObject`](../karate-js/src/main/java/io/karatelabs/js/SimpleObject.java) rather than being a reflection-bridged POJO. `SimpleObject` exposes members to the JS engine natively: the ext implements `jsGet(name)` (return a value, or a `JavaCallable` for methods like `compare`), overrides `putMember(name, value)` for settable config properties (`baselineDir`, `threshold`, …), and `jsKeys()` for enumeration. No reflection on the hot path — the engine calls `getMember`/`putMember` directly, which performs better than the reflective `JsValue` POJO adapter that `karate.driver` uses (see [DESIGN.md § karate.* API](./DESIGN.md#karate-api), `Driver` row). The plain-POJO path still works for trivial cases, but `SimpleObject` is the recommended ext-global style.

**Builder / property-setter pattern is the canonical ext-global API style.** Proven in the existing `karate.channel('grpc')` / `karate.channel('kafka')` channels — users set config as properties, then invoke the operation:

```gherkin
* def session = karate.channel('grpc')
* session.host = 'localhost'
* session.port = 9555
* session.proto = 'classpath:karate/hello.proto'
* session.service = 'HelloService'
* session.method = 'Hello'
```

Ext globals should follow the same idiom — readable, no method-name churn for new config keys, plays naturally with `* configure`-style ergonomics. The image ext example becomes:

```gherkin
* image.baselineDir = 'baselines'
* image.threshold = 0.02
* image.compare('home.png')
```

Per-call setters mutate the singleton's state for the rest of the scenario (scoped to the runtime, not the Suite — the property-setter idiom is local-state, not global state). Property writes land in `SimpleObject.putMember`; reads and method lookups in `jsGet`. Phase 3 (`karate-image`) follows this pattern verbatim.

**(b) Scenario-runtime exposure.**

Seeding point is **`KarateJsBase`** — same class that mounts the `karate` global (DESIGN.md core-classes table, line ~59: "Shared state and infrastructure for KarateJs"). The seed runs *before* `karate-base.js` / `karate-config.js` evaluate; see [DESIGN.md § Dry Run](./DESIGN.md#dry-run) for the existing config-eval pipeline order this slots into. Names cannot collide with built-in globals (`karate`, `driver`, `read`, …) — collision throws at boot with `RuntimeException("ext global '<name>' collides with built-in '<name>'")`.

```gherkin
Scenario: pixel-diff
  * image.baselineDir = 'baselines'
  * image.threshold = 0.02
  * image.compare('home.png', 'screenshots/home.png')
```

**Per-call shape mirrors `karate.channel`.** See [DESIGN.md § Ext Architecture](./DESIGN.md#ext-architecture) for the singleton-per-Suite lifecycle; the only difference is the ext global is *exposed* in scenario scope (channels are returned per call).

### 3.5 Custom step keyword sugar — `* <extName> <expr>`

> **Status: Deferred — no ETA (folded into O21).** Phase 2 does **not** ship this. The bare-keyword JSON-arg form (`* image { compare: '...' }`) is the *only* authoring shape that needs a `StepExecutor` change; every other form already works through the existing dispatch once the ext global is seeded into scope:
> - `* image.compare('home.png')` and `* def x = image.compare(...)` route through the `StepUtils.hasPunctuation(keyword)` branch in `StepExecutor.run` (line ~176) → `runtime.eval` — **no change needed**.
> - `* image.threshold = 0.02` (property setter) routes the same way → `SimpleObject.putMember`.
> - Only `* image { ... }` (bare identifier + payload) falls to the keyword-switch `default` branch and throws `unknown keyword`; wiring it up is what §3.5 below describes.
>
> Since the injected `SimpleObject` global already covers the full functional surface (config via property-setters, operations via method calls), the bare-keyword sugar is pure ergonomics. It also touches the hottest switch in the executor (the `default` branch runs for every unrecognized-keyword step) and raises ambiguity questions (collision with `unknown keyword`, shadowing). O21 already frames the *whole* keyword-authoring story (JSON-arg form + Cucumber-like patterns) as a separate spike rather than piecemeal design — this form goes there. The design below is retained as the reference for that future work.

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

**This dispatch is deferred (see the status note at the top of §3.5).** Both the minimal JSON-arg form above *and* the richer DSL (typed parameter patterns à la Cucumber but JS-authored and compile-free) are captured as future work in O21 — designed together as one keyword-authoring conversation rather than the JSON-arg form landing alone in Phase 2.

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

> **Status: built (Phase 2).** `StepResult.Embed` holds `name` + `List<Part>` + `meta`; `Part` is `{role, mime, data|url, fileName}`. The convenience constructor `Embed(byte[], mime, name)` wraps the bytes as one `"primary"` part, so the three existing call sites (screenshot, `doc`, `karate.embed`) construct unchanged, and the helper accessors (`getData`/`getMimeType`/`getName`/`get·setFileName`) delegate to that primary part. Multi-part embeds use `new Embed(name, parts, meta)`.

**Clean break — no legacy flat shape (supersedes the interview's shim sketch).** `Embed.toMap()` emits the **uniform** `{name, parts:[{role, mime, data|url|file}], meta}` shape for *every* embed, single- or multi-asset. There is no `{mime_type, ...}` flat form: v2 has no released embed consumers (all ext consumers are yet to be built and the report is the only reader), so the RC-era shape was dropped rather than carried as a shim. A single-asset embed is simply one `"primary"` part. Per-part serialisation precedence: `file` (core wrote the inline bytes to disk) → `url` (ext wrote the asset itself) → `data` (inline base64).

Karate's own readers were updated for the new shape in lock-step: `HtmlReportWriter.writeEmbedFile` walks `parts[]` (writing each inline part, naming multi-part files `NNN_<name>_<role>.ext`); `res/karate-report.js` `_renderEmbedPart` renders each part by mime (image/iframe/video/pdf/text/download), resolving `../embeds/<file>` or `../<url>`; `CucumberJsonWriter` emits one `{mime_type, data}` per inline part (the Cucumber JSON external spec is unchanged; url-only parts are skipped). Verified green: 37 `HtmlReportWriterTest`, 10 `CucumberJsonWriterTest`, `StepResultEmbedTest`.

### 3.7 The `image` ext API — decided (Phase 3)

> **Provenance.** Designed against the v1 `examples/image-comparison` walkthrough (the 7-feature progression: establish → compare → rebase → custom-config → outline → JS-API) and the Karate Labs channel idiom (`karate.channel('kafka'|'websocket'|'grpc')` — property-setters as builder, command verbs to execute, `configure x = {…}` for bulk). The v1 surface was low-level (`* compareImage { baseline, latest, options }` + `karate.compareImage(path, bytes, opts)`), forcing users to hand-write a `screenGrab(name)` orchestration (locate baseline by name → load per-name options → auto-establish if missing → compare). **v2 bakes that orchestration into `image.compare(name, latest)`.**

The single ext-global `image` (Suite-singleton `SimpleObject`, seeded into scenario scope per §3.4) is **both** the config-holder/builder **and** the command executor. Decisions frozen: **latest is always explicit** (the ext never calls `driver`/`screenshot()` — stays decoupled; user passes bytes or a path); **no standalone builder object** (the one-line `compare({…})` map *is* the builder — a separate `comparer()` would just spread one map across setters with no reuse benefit, unlike kafka's long-lived `producer()`/`consumer()`); **per-name option files** auto-load; **rebase is explicit**.

**(a) Config — three interchangeable ways, all writing the same config map (overlay, not replace).**

```gherkin
# boot-time suite defaults (karate-boot.js)
* const image = boot.ext('image'); image.baselineDir = 'baselines'
# per-scenario property-setters (the builder)
* image.threshold = 0.02
# bulk JSON overlay — preserves boot defaults
* image.config = { engine: 'resemble', ignore: 'antialiasing', report: 'mismatched' }
```

Config keys: `baselineDir`, `engine` (`resemble` | `ssim` | `resemble,ssim` | `resemble|ssim`), `threshold` (max % mismatch; v1's `failureThreshold`), `failOnMismatch` (default `true`; v1's `mismatchShouldPass` inverted), `report` (`all` | `mismatched` | null — when to attach diff images), `ignore`, `allowScaling`, `tolerances`, `errorColor`, `errorType`, `transparency`. `ignoredBoxes` is usually per-name (see option files).

**(b) Compare — latest always explicit; baseline auto-resolved by name.**

```gherkin
* image.compare('home', screenshot())          # name + explicit latest (bytes or path)
* image.compare({ name: 'home', latest: shot, baseline: 'this:baselines/home.png', threshold: 0.05 })
```

`compare(name, latest)` resolves baseline = `<baselineDir>/<name>.png`, auto-loads options from `<baselineDir>/<name>.json` if present, **auto-establishes** the baseline (writes latest as the new baseline, passes with a note) when it's missing, **fails the step on mismatch** unless `failOnMismatch:false`, returns a result, and emits the multi-part diff embed. `latest` accepts a `Uint8Array` (the idiomatic binary type — driver `screenshot()` / `new Uint8Array(...)`, unwrapped canonically via `JsValue.getJavaValue()` → `byte[]`), a raw decoded `byte[]` (which crosses JS scope as a number list), or a path string (resolved through the same prefix system). The `compare({…})` map form is the one-shot full-control path (the "builder" collapsed to one line). **Options precedence (low → high): scenario/suite config → `<baselineDir>/<name>.json` → per-call inline.**

**(c) Rebase — explicit, after an intentional UI change.**

```gherkin
* image.rebase('home', screenshot())     # overwrite baselines/home.png with this latest
```

`image.rebase(name, latest)` is the always-works programmatic path. A report-lightbox "Accept as baseline" affordance is a later add — note a *static* CI report can't write files, so it would show a copy-paste command / download the latest (v1's `onShowRebase` spirit, nicer UI); true one-click rebase only works under a live-serve host (xplorer).

**(d) Result + embed shapes.** `compare()` returns `{ name, pass, mismatchPercentage, threshold, engine, isBaselineMissing, baselineEstablished }` (for `failOnMismatch:false` branching). The embed is `name:"image-comparison"` with `parts:[{role:"baseline"},{role:"latest"},{role:"diff"}]` + `meta:{ name, mismatchPercentage, threshold, engine, passed }` — `ImageExt`'s `static/ext.js` renders it into the lightbox by embed name (§3.2).

**(e) Server engine.** `compare`/`rebase` delegate to the relocated `io.karatelabs.ext.image.ImageComparison` (the resemble/ssim math), mapping the config + resolved options into its `compare(baselineBytes, latestBytes, options, defaultOptions)` call and its `MismatchException`/result map back into the v2 result + embed. The engine itself is unchanged (Phase 3 m1).

> **Status: built (Phase 3 m2).** `ImageExt` + `ImageApi` land, green (`ImageExtE2ETest`: establish → match → mismatch + report embed + asset splice; `ImageComparisonTest` 21). Two design points settled during the build:
>
> **Per-scenario instance, not a Suite singleton.** Phase 2 seeded ext globals as one shared singleton bound into every scenario — unsafe for `image`'s per-scenario config (`image.threshold = 0.02`) under parallel runs. New core affordance `ExtGlobalFactory` + `Suite.registerGlobal(name, ExtGlobalFactory)`: a stateful ext registers a factory, and `ScenarioRuntime` mints a fresh instance **per scenario** (the singleton form still works for stateless globals like `dummy`). Each `ImageApi` is created with the scenario's `KarateJsContext` in hand — mirroring how `karate.channel('kafka')` receives the runtime — so (i) config is scenario-scoped + parallel-safe, and (ii) it resolves `this:`/`classpath:`/`file:` paths via `context.getWorkingDir().resolve(...)` (reusing Karate's `Resource` prefix system — no bespoke path code, no thread-local). `ImageExt` itself is the boot-config holder (`boot.ext('image')` returns it; `image.baselineDir = …` in `karate-boot.js` sets defaults the factory copies per scenario). Multi-part embeds attach via the thread-local `LogContext.get().embed(Embed)`; mismatch fails the step via a thrown `RuntimeException`. This generalises: any future stateful ext uses the same factory + context affordance (the O22(c) gap, now closed).
>
> **`image.builder('compare')` — reserved future pattern (not built).** A *generic* `builder(opName)` (vs a per-op `comparer()`) is the chosen forward-looking convention: it scales to any number of operations without new methods and maps cleanly onto the future MCP/curl "one API, many faces" projection (every op uniformly discoverable for tooling). It's stringly-typed, so it stays a power/programmatic escape-hatch — the first-class discoverable verbs remain `image.compare(...)` / `image.rebase(...)`. Documented here as the intended builder shape if/when a staged-construction tier is added.

### 3.8 Milestone 3 — report frontend (pending spec)

The server side is done (m1 engine + m2 API emit the `image-comparison` multi-part embed). m3 is the interactive report UI. **What core renders today:** `res/karate-report.js` `_renderEmbed` walks `embed.parts[]` and renders each by mime — so the baseline/latest/diff already show as three `<img>` inline, with no ext code. m3 *upgrades* that named embed into a lightbox.

**(a) Embed-render extension hook — the one core-contract decision (locked: delegation hook).** §3.2's render contract sketched an Alpine component in a `data-slot="step.embed"` container, but that slot doesn't exist — embeds render inline via `_renderEmbed`, and there is no `alpine:init`/`Alpine.data` registration hook (D5's pattern was never wired). Rather than resurrect the slot or post-process the DOM, add a small **named-embed renderer registry** to core's report JS:
- `KarateReport.registerEmbed(name, fn)` — exts register a renderer keyed by embed `name`.
- In `_renderEmbed(embed)`: if a renderer is registered for `embed.name`, delegate to it (it returns the embed's markup); else fall through to the existing generic per-part rendering. Graceful no-op when no ext is loaded — the three images still render.
- **Ordering:** ext.js loads via the `<!-- KARATE_EXTS -->` `<script defer>`; registration must run before the Alpine-driven render. Confirm defer-vs-render ordering during build; if render can precede registration, have `registerEmbed` trigger a re-render of already-mounted image embeds (or register at `alpine:init`). This is the timing detail to nail in m3.

This mirrors the `nav.pages` fix (a small, general core report-JS affordance that every future ext reuses) and belongs in §3.2 once built.

**(b) The lightbox (image ext `static/ext.js`, replacing the m2 stub).** Registered via `KarateReport.registerEmbed('image-comparison', fn)`. Reads `parts` (baseline/latest/diff `url`s or inline data) + `meta` (`mismatchPercentage`, `threshold`, `engine`, `passed`, `baselineEstablished`). Renders a thumbnail (diff, or latest when established) that opens a `<dialog>` lightbox with the three views and a **slider / blink / onion-skin** toolbar. Resemble.js is **lazy-loaded from CDN on lightbox open** (D11) — the server already wrote the precomputed diff PNG; Resemble powers only the interactive tools. CSS scoped under `.k-image-ext` (D20/O6). `meta.passed`/`baselineEstablished` drive a pass/fail/established badge.

**(c) Optional `nav.pages` "all diffs in the run" index — under-specified, lower priority.** A static `pages/image-comparison.html` can't see the run's embeds on its own. Decide its data source before building: (i) read the suite/feature report JSON (`KARATE_DATA`) and filter `image-comparison` embeds, or (ii) have the ext accumulate a manifest written alongside assets. Ship the per-step lightbox (a/b) first; the index is additive and now unblocked by the `nav.pages` work.

**Out of m3:** print CSS (`ext.print.css`) is Phase 5; the rebase "Accept as baseline" report affordance (§3.7c) rides on (a) and live-serve, also later.

**(d) Deliverables to land with m3 (docs + dev-flow test):**
- **Developer-flow test in `karate-image` itself — automated *and* demo.** Port the v1 `examples/image-comparison` progression (`1_establish_baseline` → `2_compare_baseline` → `3/4_rebase` → `5_custom_config` → `6_outline` → `7_api`) into a Karate feature + JUnit runner under `karate-image/src/test/` (driverless: pass `Uint8Array`/`byte[]` fixtures, not live screenshots). **Two jobs at once:** (i) it runs as part of the normal unit/CI suite and *asserts* the flow (establish → match → custom-config → rebase → re-match → outline/multi-name), locking behaviour end-to-end; (ii) it doubles as the runnable demo — readable, self-contained, and it writes a real HTML report (open it to see the m3 lightbox), so it's the live showcase of the v2 API. It supersedes the hand-written `screenGrab` walkthrough — the feature *is* the story now (`image.compare(name, latest)` + `image.rebase`). Keep small committed PNG fixtures + per-name `.json` options under `src/test/resources` so the run is deterministic and offline. Complements the existing focused `ImageExtE2ETest`.
- **`karate-image/README.md`.** Document the main design: the `image` API (config → `compare`/`rebase`, §3.7), the per-scenario ext-global factory model, the embed/lightbox, and the engine credit (jkeys089 / resemble + ssim). Module has none today.
- **TODO: user-facing docs in `../karate-docs`.** When Phase 3 is complete (frontend shipped), update the user-facing documentation site (`../karate-docs`) — the image-comparison guide, migration note from v1 `compareImage`, and the ext-authoring page (per-scenario factory + `KarateJsContext`). Track as the Phase 3 doc close-out; do *not* block m3 build on it.

---

## 4. Phased Implementation

Five PRs. Each phase ends with a green build, manual smoke pass, and an explicit exit criterion.

### Phase 1 — Tailwind restyle of existing reports (no ext code)

> **Status:** *Complete.* Chrome swap landed: Bootstrap → Tailwind, dark/light, navbar, Heroicons sprite, `KARATE_*` placeholders in `HtmlReportWriter`, `etc/tailwind/` build script, CI staleness gate. Phase 1b (next section) owns adoption of matrix items that need real Alpine work.

**Strategy: port, don't rewrite.** The existing three templates already wire `FeatureResult.toJson()` data correctly. Phase 1 replaces only the styling layer and selectively adopts visual ideas from the prototype that survive contact with the actual data model. Anything that assumes data we don't compute, or that's fluff dressed up to look impressive, gets rejected.

**Out of scope:**
- Ext asset copying, slot rendering, `karate-image`, `karate-openapi`.
- Any data-model extension to support prototype ideas (deferred to follow-up issues per the matrix).
- Backward-compat for any user who post-processes the old HTML's DOM structure — v2 hasn't GA'd.

**Design idea adoption matrix.** For each prototype idea, the decision and rationale:

| Idea | Decision | Notes |
|------|----------|-------|
| Dark theme default + light toggle (`data-theme="dark\|light"` on `<html>`) | **Adopt** | Pure Tailwind `dark:` variants; toggle in topbar; localStorage persists. |
| Top-bar nav (Summary / Timeline / Feature) | **Adopt** | Replaces today's plain header. Uses `brand.navbar` (`#212529`) per D20 palette. |
| Inter + JetBrains Mono fonts | **Reject — per D20** | System-font stack (`system-ui`, `-apple-system`, `Segoe UI`, etc.) replaces the prototype's Google-Fonts-loaded Inter/JetBrains. Air-gapped CI still gets identical rendering on any platform. |
| Prototype's green `#00d97e` accent | **Reject — per D20** | Use the `accent` blue (`#60a5fa`) from the Karate Labs brand palette. |
| Iconography (status pills, sort arrows, theme toggle, copy button, etc.) | **Adopt — per D21** | Inline SVG sprite (Heroicons), spliced by `HtmlReportWriter` via the `<!-- KARATE_ICONS -->` placeholder. No icon-font dependency. |
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
| `backdrop-filter` + `color-mix()` heavy aesthetic effects | **Reject — per D20** | Skip for Phase 1. Stick to flat Tailwind utility classes; the Karate Labs brand is restrained by design. These effects are exactly the "premium dev tool look" we're not optimising for. Re-evaluate when (if) a brand phase happens. |

### Phase 1b — Prototype feature adoption

> **Status:** *Complete; some items deferred (see below).* Owns the matrix "Adopt" features that needed real template + Alpine work — everything tractable without upstream Java/data-model changes landed (donut, KPI cards, Failures tile, sticky sidebar with All/Failed filter, step-keyword color coding, screenshot lightbox, Timeline speedup/wall-clock cards + per-thread groups + Top-5 slowest panel). For specifics consult git history and the live templates.

**Deferred — good to have, no ETA.** Investigated during Phase 1b and each one needs more upstream work than fits the chrome-restyle scope. Captured here so they're not lost.

- **Two-column HTTP block** *(deferred)* — method pill + status badge + foldable headers + JSON body. Wire-shape today: `step.logs` is a free-form text blob with `1 > / 1 <` request/response markers (verified in `http-demo.feature` output). Client-side regex parsing is possible but heuristic; the cleaner path is upstream — emit structured `request`/`response` on the step before reaching `HtmlReportWriter.buildStepData`. Revisit when the http machinery has structured runtime events.
- **Copy-as-cURL** *(deferred)* — `navigator.clipboard.writeText(...)`. Depends on the HTTP block having structured request fields. Unblocked when the HTTP block lands.
- **Expected-vs-actual diff** on match failures *(deferred)* — wire shape today is a single pre-formatted string (recursive match output flattened, verified in `test-report.feature` output). DESIGN.md § Match Engine says `Result.Failure` carries `path`/`reason`/`actualValue`/`expectedValue` but `HtmlReportWriter.buildStepData` only passes `step.getError().getMessage()`. Needs a structured failure serializer (probably recursive, since match failures nest) before the side-by-side renderer can do anything beyond regex-parsing the existing text.
- **Outline examples table** *(deferred)* — aggregate sibling outline examples into a per-example table (status pill, refId, vars binding, duration). Today each example appears as its own scenario row; aggregation is straightforward but the *vars binding* isn't on the wire — `buildScenarioData` doesn't expose the row's column values. Needs the per-example vars added to the wire first.
- **Skipped-scenario reason block** *(deferred)* — callout above a skipped scenario's steps showing why it was skipped. Today the synthetic `@skipped` tag is the only marker; `karate.abort()`-style skips show the abort step inline already, making the callout redundant. Worth doing only when tag-filter / `@ignore`-driven skips can be distinguished from abort skips — needs runtime metadata that isn't on `ScenarioResult` today.

**Out of scope** (per matrix decisions): Tag pass-rate rings (O13). Critical-path overlay on Timeline (rejected). Thread-utilization heatmap (O15). Mock data flourishes / heavy aesthetic effects (rejected per D20).

### Phase 2 — Ext SPI extensions + slot loader

> **Status: complete.** D17 rename (Plugin → Ext) landed earlier as prep. This session landed all three pieces — the **ext-global path**, the **report-asset path**, and the **multi-part embed shape** (§3.6) — each green. The remaining spike work is downstream phases (Phase 3 `karate-image` is the first real consumer of the multi-part embed + slot machinery).

**Scope:**
- Extend `Suite`: `registerGlobal(name, instance)` / `getGlobal(name)` / `getGlobals()`, and `registerReportAssets(ReportAssets, ClassLoader)` / `getReportAssets()` (returns `Map<String, ReportAssets>`). ✅ done.
- Seed ext globals into JS scope before `karate-base.js` / `karate-config.js` evaluate — done in `ScenarioRuntime.initEngine` (hidden root bindings, same mechanism as `karate`/`read`/`match`; applies to called features too). Ext globals are `SimpleObject` instances (§3.4) — they cross into scope natively, no reflective adapter. Built-in-name collision (`karate`/`read`/`match`/`driver`) fails the Suite loud at boot. ✅ done.
- `HtmlReportWriter` already had the `<!-- KARATE_EXTS -->` placeholder wired (Phase 1 foundation; empty string). Phase 2 fills it: at write time, copy each registered ext's declared assets to `target/karate-reports/ext/<name>/` (static/ stripped; pages/ kept), assemble the `<script src=ext/<name>/ext.js defer></script>` (+ optional `<link rel=stylesheet>`) lines and splice into the placeholder. Root pages use `ext/<name>/…`; feature pages under `feature-html/` use `../ext/<name>/…`. Threaded through `HtmlReportListener` (captures `suite.getReportAssets()` at suite start). ✅ done.
- Ext asset registration is imperative via `ReportAssets` (§3.3) — no JSON manifest, no `coreVersion` check. Validation: `js` required + referenced resources must exist; failures hard-fail at boot. ✅ done.
- Extend `StepResult.Embed` to the multi-part shape (§3.6). ✅ done (`Embed` = `name` + `List<Part>` + `meta`). **Clean break, not a shim:** every embed (single- or multi-asset) emits the uniform `{name, parts[], meta}` shape — no legacy flat `{mime_type, …}` form, since v2 has no shipped embed consumers. Karate's own readers (report JS, `HtmlReportWriter`, `CucumberJsonWriter`) were updated in lock-step.
- **Not in Phase 2:** the `StepHandler` interface and the `StepExecutor.run` keyword-switch dispatch (§3.5) are deferred into O21 with no ETA. The injected `SimpleObject` global already exposes the full surface via method calls (`dummy.echo('hi')`) and property-setters (`dummy.threshold = 0.02`), both of which ride the existing `StepExecutor` dispatch unchanged. No `StepExecutor` / `StepUtils` edits this phase.

**Out of scope:** any real ext. This phase has no production consumer; tests use a synthetic test-only ext under `karate-core/src/test/java/io/karatelabs/ext/dummy/` (`DummyExt`).

**Exit criteria** (each independently verifiable):
- Unit test: `ReportAssetsTest` — valid spec resolves + copies (static/ stripped, pages/ kept); `js` required and missing-resource both fail loud. ✅
- End-to-end Suite test (`DummyExtE2ETest`) runs a feature; verification path is **HTML string-parse, not Playwright** (no Playwright/Jsoup dependency added in this phase):
  - Global visibility: feature contains `* def x = dummy.echo('hi')` and `* match x == 'hi'` (passes only if the `SimpleObject` global was seeded; exercises `jsGet` → `JavaCallable`). ✅
  - Global visible to `karate-config.js` (seed ordering). ✅
  - Property-setter: `* dummy.state = 'mark'` then `* match dummy.state == 'mark'` (exercises `putMember` / `jsGet`). ✅
  - Asset-copy: `ext/dummy/ext.js`, `ext.css`, `pages/dummy.html` exist after run. ✅
  - Splice: `karate-summary.html` contains `<script src="ext/dummy/ext.js" defer></script>` (+ `<link>`); the feature page carries the `../` prefix. ✅
- Embed wire shape is the uniform `{name, parts[], meta}` for all embeds — clean break, no legacy flat form (no released v2 consumers). ✅ (`StepResultEmbedTest` asserts the single `"primary"`-part shape, the file-reference variant, and multi-part `parts[]`+`meta`; report JS / Cucumber / writer updated in lock-step).

> **Exit-criteria note.** The original "panel-render assertion" (Jsoup-parse for `<div data-slot="summary.panels" data-ext-name="dummy">`) assumed core *emits* a panel container per ext. That contradicts the §3.2 slot model where the *container* is in the template and the ext renders into it **client-side** (Alpine) — invisible to a static HTML parse with no browser. Replaced with the asset-copy + `<script>`-splice assertions above, which are the server-side-observable facts. Verifying the live panel render belongs to a browser-driven check (Phase 3 manual smoke).

**Files touched (globals + assets slices):** `Suite`, `ScenarioRuntime`, `HtmlReportWriter`, `HtmlReportListener`, new `ReportAssets`; test fixtures `DummyExt` + assets, `DummyExtE2ETest`, `ReportAssetsTest`. `StepExecutor` / `StepUtils` **not** touched (§3.5 deferred). `StepResult` still to change for §3.6.

### Phase 3 — `karate-image` submodule (dogfood)

**Scope:**
- New submodule `karate-image/` (sibling to `karate-junit6`, `karate-gatling`).
- `pom.xml`: depends on `karate-core` (provided scope), brings in Resemble's *server-side* equivalent if needed (we'll likely just use AWT or a tiny pixel-loop — Resemble.js is browser-side only). Confirmed plan: keep the pixel-diff math from PR #2885's `ImageComparison.java` verbatim, just relocate.
- `pom.xml -Pfatjar`: bundles everything needed for `~/.karate/ext/` drop-in.
- `io.karatelabs.ext.image.ImageExt` — implements `Ext` (the renamed `Plugin` interface per Phase 2), registers global + assets in `onBoot`.
- `io.karatelabs.ext.image.ImageApi` — implements `io.karatelabs.js.SimpleObject` (§3.4). `jsGet("compare")` returns a `JavaCallable` (`compare(args)`); `putMember` accepts the config properties (`baselineDir`, `threshold`, `report`). Used as `* image.baselineDir = '...'` then `* image.compare('home.png')`. (The `* image { compare: ... }` keyword form is deferred — §3.5 / O21.)
- `static/ext.js`, `static/ext.css` under `META-INF/karate-ext/` (no manifest — assets registered imperatively via `ReportAssets` in `onBoot`, §3.3).
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
- `ImageExtE2ETest` runs a Suite with a `karate-boot.js` that calls `boot.ext('image')`; test `.feature` calls `* image.compare('home.png')` (with baseline/threshold set via property-setters); HTML-parse asserts the diff `<dialog>` element and three `<img>` tags (baseline/current/diff) are present in the written `karate-feature.html`.
- **Manual smoke step** (mark explicit — no automation): open the generated report in a browser, click a diff thumbnail, confirm lightbox opens and slider/blink/onion-skin toolbar works.
- Fatjar test: build `karate-image-X.Y.Z.jar`, then run in an **isolated home** so the test cannot pollute the developer's real `~/.karate/ext/`:
  ```bash
  TESTHOME=$(mktemp -d) && mkdir -p "$TESTHOME/.karate/ext" \
    && cp karate-image/target/karate-image.jar "$TESTHOME/.karate/ext/" \
    && HOME="$TESTHOME" karate run karate-image/src/test/resources/smoke/visual.feature
  ```
  Assert lightbox elements appear in the resulting `target/karate-reports/karate-feature.html`.

**Files touched:** new submodule ~25 files, plus root `pom.xml` `<modules>`.

### Phase 4 — second-consumer migration (`karate-openapi` → folds into the `karate-xplorer` über-ext)

> **Pivot ([unified-traceability-substrate memo](../../veriquant/docs/_wip/design-memos/unified-traceability-substrate-2026-05-29.md), UD1–UD2).** The downstream design has moved: the karate-agent server is retired, and the separately-versioned proprietary `karate-openapi` ext **folds into one commercial über-ext, `karate-xplorer`** (requirements + rules + coverage + openapi). So the spike's "prove the SPI on a second, separately-versioned ext" goal stands, but the *consumer* is now the über-ext, not a standalone `karate-openapi`. The §1.1 tier table and D19 still hold for the OSS/proprietary split; only openapi's standalone identity changes. **Prerequisite:** `nav.pages` rendering (O22 action 1) is **done** — the über-ext's traceability/coverage pages get topbar tabs. The remaining SPI-side question is the concat-vs-list decision on `ReportAssets` for a multi-capability ext (O22 action 2); land it before or alongside this phase. The detailed über-ext design (artifact model, reporting substrate, the four report surfaces) lives in the veriquant memo, not here; below is the original standalone-`karate-openapi` migration, retained as the SPI-validation reference.

Lives in the separate `karate-ext` repo (proprietary; renamed from `karate-plugins` per D17). Expected on disk at sibling path `../karate-ext/karate-openapi/` relative to this repo. One PR there.

**Scope:**
- Update `karate-openapi` to use the new `Suite.registerReportAssets` + `registerGlobal` paths.
- Ext global name: `openapi`. Methods (TBD by openapi maintainer): probably `expect(op, opts)`, `summary()`.
- Slot contributions:
  - `summary.panels` — "OpenAPI coverage: N / M ops" + drill-down link.
  - `nav.pages` — `pages/openapi-coverage.html` — full coverage matrix (per-op pass/fail/untouched).
  - `step.embed` — name `openapi-match` already exists ([DESIGN.md § Reports "Where named embeds live on the wire"](./DESIGN.md#reports) names it explicitly); render it per-step.
- Rename `OpenapiPlugin` → `OpenapiExt`, package `io.karatelabs.plugins.openapi` → `io.karatelabs.ext.openapi`.
- Move `boot.ext('openapi')` config keys (`path`, `excludes`) — already per-instance property shape per [DESIGN.md § Ext Architecture](./DESIGN.md#ext-architecture); only the `boot.plugin` → `boot.ext` call site changes.

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
| O6 | **Tailwind utility classes for exts** — should exts also use Tailwind, or pure scoped CSS? If Tailwind, they need their own build. **Decided (this session): core owns all CSS; exts do NOT run their own Tailwind build.** An ext gets its styling from one of three channels: **(a)** reuse Tailwind utilities core *already renders* (already in `karate-report.css`, free); **(b)** bespoke shapes shipped as the ext's own scoped CSS (`ReportAssets.css()` → `.k-<ext>-ext` scope) or inlined into the ext's HTML — immune to core's build entirely; **(c)** utilities core does *not* itself use but we want to *promise* to exts — forced into the output via a maintainer-managed **safelist** in `etc/tailwind/tailwind.config.js`. **Why a safelist is required:** Tailwind v3 is content-driven — the `content` globs scan only core templates + `karate-report.js`, so a utility an ext references that core never renders is purged from the shipped CSS and the ext renders unstyled. The safelist is the only mechanism that makes "core pre-includes the shapes downstream exts need" actually work. **Scaffolded empty** (`safelist: []`) — verified inert (zero CSS delta, so the §3.1 CI staleness gate stays green) *and* functional (a safelisted class core doesn't use does land in the output). **Caveat — CI blind spot:** the staleness gate only scans core content, so a separate-repo ext adopting a non-safelisted utility won't change core's CSS and CI won't flag it; breakage only shows when viewing that ext's report. Hence: keep genuinely bespoke shapes in channel (b), reserve the safelist for a deliberately-small shared utility vocabulary. `karate-image` (Phase 3) needs no core-CSS change — its lightbox CSS is channel (b). | **Decided + scaffolded.** Safelist seam exists; populate only when a shared ext utility vocabulary is actually needed (Phase 4 / über-ext territory). |
| O7 | **`karate.channel(...)` deprecation?** Boot-ext-globals overlap with channels in some uses (gRPC ext could expose `grpc` global instead of `karate.channel('grpc')`). Don't deprecate now — channels are per-call vs ext singletons-per-Suite, both valid. Revisit after several exts ship. | Watch. |
| O9 | **`@report=false` interaction with ext embeds** — DESIGN.md says `@report=false` strips step detail from artifacts. Ext embeds should respect this (image diffs of redacted scenarios must not leak). Verify in Phase 3 test. | Phase 3. |
| O10 | **JS scope seeding order** — globals must be visible to `karate-config.js`. Need to verify `karate-base.js` ordering doesn't clobber. | Phase 2. |
| O11 | **Channel resolver unification (D18 follow-through)** — refactor `KarateJs.channel()` (KarateJs.java:1202) to drop `KarateConfig.getChannelFactoryClass(type)` in favour of the shared `io.karatelabs.ext.<name>.<Name>Channel` resolver. Existing `karate-plugins/karate-grpc` etc. must be repackaged from `io.karatelabs.plugins.grpc.GrpcChannelFactory` → `io.karatelabs.ext.grpc.GrpcChannel`. Lock-step with D8 strict version match — `karate-ext`'s monorepo version tracks `karate-core` version exactly (a `karate-core` 2.0.10 cuts a `karate-ext` 2.0.10). | Phase 2 (resolver) + sibling repo PR + karate-ext version-sync as part of D17 rollout. |
| O12 | **`karate-ext` monorepo version tied to `karate-core` version** — every karate-core release triggers a matching karate-ext release at the same version number, even when no ext module changed (no-op republish keeps the strict-match contract working). RELEASING.md needs a step for this. | RELEASING.md §2 amendment (Phase 3 in this spike); enforced by the loader check from D8. |
| O13 | **Per-tag pass-rate aggregation** — deferred from Phase 1 design matrix (prototype's "tag pass-rate rings"). Requires `SuiteResult` to expose `Map<String, {passed, failed, skipped}> tagStats`. Light to compute, but no current consumer asks for it; ship Phase 1 without it, add when a user requests. | Post-Phase 1 if asked. |
| O14 | **Karate-OSS distinct brand** — Phase 1 (D20) uses the slate-blue Karate Labs brand palette. Open question whether the OSS report should eventually have its own visual identity (e.g. to signal "this is the OSS product, not the commercial offering"). Triggers a brand-design phase that produces a Karate-OSS palette/typography spec; the spec then drops into `etc/tailwind/tailwind.config.js`'s `theme.extend` and the `@layer components` block we deliberately skipped. No template rewrite needed if Phase 1 stuck to utility classes (D20). | Post-spike; needs design input. |
| O15 | **Thread-utilization on Timeline** — deferred from Phase 1 matrix. If anyone says the per-thread gantt isn't enough to understand parallelism behaviour, revisit. | Post-Phase 1 if asked. |
| O16 | **D17 rename rollout — karate-core landed; karate-ext sibling repo + Rust launcher still need lockstep.** karate-core rename merged. Outstanding: (1) sibling `karate-ext` (was `karate-plugins`) repackage from `io.karatelabs.plugins.*` to `io.karatelabs.ext.*`, (2) verify Rust launcher's `~/.karate/ext/` + `.karate/ext/` recognition (likely already aligned). A window where `~/.karate/ext/*.jar` manifests still say `karate-plugins` against a core that resolves `karate-ext` would break user setups — coordinate the sibling repo + first karate-core release that requires it. | Phase 2 release planning. |
| O17 | **Channel resolver shares name lookup but lifecycle wrappers differ (D18 follow-through).** Per-call channels (`karate.channel('grpc')`) must instantiate per call from the factory; Suite-lifetime ext globals (`boot.ext('image')`) return the cached singleton. Phase 2 design: factor `ExtNameResolver` as a pure `String → Class<?>` lookup utility; both `BootBinding.ext()` and `KarateJs.channel()` call it but wrap the result differently. Avoids the trap of conflating the two roles into one method. | Phase 2 (resolver refactor). |
| O18 | **Beef up collision detection for ext global names.** A registered ext global `image` shadows any user variable also named `image`. Today's collision detection (per DESIGN.md) covers built-in globals only. Phase 2 should extend the check: at scenario-runtime seed time, warn (or error, TBD) when an ext global name collides with the current scenario's `def`-bound variables. Note: `* image = 1` (raw assignment) is already invalid Karate syntax — users must write `* def image = 1` — so the *assignment* dispatch concern is bounded; the *shadowing* concern is real. | Phase 2. |
| O19 | **Screenshot embed shape when the model generalised.** ~~Preserve the legacy flat shape via a shim…~~ **Resolved (Phase 2) by a clean break, not a shim.** All embeds — screenshots included — now serialise to the uniform `{name, parts:[{role, mime, data\|file}], meta}` shape; a screenshot is one inline-bytes `"primary"` part (still written to `embeds/` and shown in HTML exactly as before). No legacy `{mime_type, ...}` form is emitted. Safe because v2 has shipped no embed consumers — the only readers are Karate's own report JS / writers, all updated in lock-step. | **Resolved in Phase 2 (clean break).** |
| O21 | **Marry Gherkin steps to complex JS/Java methods, commands, and builder patterns — the unifying authoring story.** Karate already exposes ext functionality through several syntactically distinct patterns; the design question is how they coexist coherently and what (if any) richer DSL layers on top. Inventory of forms today + proposed: (1) **property-setter / builder** — `* session.host = 'x'; * session.port = 9555` — proven in `karate.channel('grpc')` / `karate.channel('kafka')` per §3.4. Covers most configuration cases; needs no DSL design. (2) **plain JS method call** — `* image.compare('a.png', 'b.png')` or `* def x = image.compare(...)`. Works today via the JS bridge; no special dispatch. (3) **JSON-arg dispatch** — `* image { compare: 'home.png', baseline: '...' }` — the minimal §3.5 form; handler routes by keys in the arg map. **Deferred — pulled out of Phase 2 (no ETA); designed here alongside form (4) rather than landing alone.** Reason: forms (1) and (2) already cover the full functional surface once the `SimpleObject` global is in scope, and (3) is the only form that touches `StepExecutor`'s hot keyword-switch default branch. (4) **Cucumber-like keyword-pattern** — `* image compare "x.png" against "baseline/x.png" within 0.02` matched against a registered pattern `compare "{path}" against "{baseline}" within {threshold:double}`. Form (4) is the genuinely new design work — JS-authored handlers registered in `karate-boot.js` (no compile step), typed parameter binding, Cucumber's annotated-classpath-scanning model replaced by something declarative and runtime-registered. The win over Cucumber: keyword authoring without leaving JS, parameter validation, IDE autocompletion possible via the registered patterns. The harder question: *when does an ext author pick (1), (2), (3), or (4)?* Probably property-setter for config, plain JS for computed results (you want the return value), JSON-arg for one-shot side-effecting commands, keyword-pattern for "this should read like English." Worth a separate spike doc that thinks about all four together rather than designing the keyword-pattern form in isolation. | Post-Phase 3; separate spike doc that covers all four forms as one design conversation. |
| O23 | **Tag `@`-prefix inconsistency on the wire (karate-core consistency fix).** Surfaced by the traceability prototype ([memo §10, DF1](../../veriquant/docs/_wip/design-memos/unified-traceability-substrate-2026-05-29.md)): `SCENARIO_ENTER` emits tags via `RunUtils.tagTexts` (no leading `@`), while `ScenarioResult` / `SCENARIO_EXIT` emit `tag.toString()` (with `@`). Any consumer joining scenarios by tag (coverage, `@req=`/`@cov=` traceability) must normalize. Decide whether to emit tags uniformly across events (strip or keep `@` consistently) — a small core change that removes a foot-gun for every tag-consuming ext. | Low priority; fold into Phase 4 prep or a standalone wire-consistency pass. |
| O22 | **Review all ext contracts for future-proofness before the first real ext (Phase 3) locks them in.** The Phase 2 SPI surface was built ahead of any consumer — every ext-facing contract is still uncommitted in practice and cheap to change *now*, expensive once `karate-image` / `karate-openapi` ship against it. Audit each for forward-compat: **(a)** `Ext` interface (`onBoot`/`onShutdown`/`getManifest`/`onEvent`) — is `getManifest()`'s flat `Map` enough, or should it be versioned/typed? **(b)** `ReportAssets` builder — does `{js, css, page}` cover real exts (multiple JS/CSS files? load order? inline vs file? non-`nav.pages` slot pages)? **(c)** ext-global seeding + `SimpleObject` — collision policy (currently built-in names only; O18 shadowing of user `def` vars still open), and whether `jsGet`/`putMember`/`jsKeys` is the contract we want exts coding against. **(d)** `StepResult.Embed` `{name, parts[], meta}` — is `role` an open string or an enum; is `meta` free-form; how do receivers discover embed names? **(e)** slot DOM contract (§3.2 `data-slot`/`data-*` attribute names) + the `alpine:init` registration timing (D5). **(f)** the `<!-- KARATE_EXTS -->` splice ordering + asset path convention (`ext/<name>/`, `static/` stripping). Do this as one focused pass; treat the clean break we just took on the embed shape as the template — breaking now is free, breaking post-GA is not. **Update — audit performed (see [unified-traceability-substrate memo §6](../../veriquant/docs/_wip/design-memos/unified-traceability-substrate-2026-05-29.md), the first real consumer).** Verdict: the global / embed / asset-copy contracts are **forward-compatible as-is** — keep them. Three concrete actions fell out: **(1) [top] ~~`nav.pages` is declared but not rendered~~ — DONE.** `HtmlReportWriter` now splices a `<!-- KARATE_NAV -->` placeholder in all three template navs, emitting one `<a>` tab per ext `page("nav.pages", …)` (registration-ordered, `../`-prefixed on feature pages); covered by `DummyExtE2ETest`. See §3.2 "now wired". **(2)** `ReportAssets` allows only a single `js`/`css` — an ext bundling several capabilities must either concat into one bundle (no SPI change) or the builder grows lists. Decide concat-first. **(3)** of the documented slots, `summary.panels` (client-side) + `nav.pages` (server-side) are wired in templates today; `feature.panels` needs its container added *if* feature-page panels are wanted. Audit (a)/(c)/(d)/(f): no change. | **Audit done (memo §6); action (1) nav.pages render landed.** Remaining: concat-vs-list decision (2), optional `feature.panels` container (3). |

---

## 7. Out of scope

- Removing or deprecating `karate.channel()` (O7).
- Generic coverage data model (O1).
- Ext-discovery via `META-INF/services/io.karatelabs.core.Ext` (rejected mid-interview — `boot.ext('name')` is the explicit activation; auto-discovery is surprise behavior).
- IDE plugin updates for new report format (separate workstream; "IDE plugin" here is the IntelliJ/VS Code plugin, not the runtime Ext).
- Mock-server report integration.
- Backward-compat shim for the legacy jQuery template — none (D: hard-replace, v2 not yet GA).

---

## 8. Quick reference — what each forward artifact looks like at the end of the spike

The Tailwind report + ext SPI scaffolding (templates, `_icons.svg`, `KARATE_*` placeholders, `etc/tailwind/`, CI staleness gate) is in `main`; treat the repo itself as the spec for those. Below shows the still-to-build layout for Phases 3 + 4 + how a user's project consumes the result.

```
karate-image/                              🔨  Phase 3 — NEW in-repo submodule
  pom.xml                                  🔨  with -Pfatjar profile
  src/main/java/io/karatelabs/ext/image/
    ImageExt.java                          🔨  registers global + ReportAssets in onBoot
    ImageApi.java                          🔨  implements SimpleObject
    ImageComparison.java                   🔨  cherry-picked from PR #2885
  src/main/resources/META-INF/karate-ext/
    static/ext.js, ext.css                 🔨  (no manifest.json — imperative ReportAssets)
    pages/image-comparison.html            🔨
  src/test/java/...                        🔨

# karate-ext (sibling repo, proprietary; renamed from karate-plugins, D17)
karate-openapi/                            🔨  Phase 4
  src/main/java/io/karatelabs/ext/openapi/
    OpenapiExt.java                        🔨  updated: registerGlobal + registerReportAssets
  src/main/resources/META-INF/karate-ext/
    static/, pages/openapi-coverage.html   🔨  (no manifest.json)

# user's project at the end of the spike
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

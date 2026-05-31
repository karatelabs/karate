# Karate Ext SPI

> The extension (“ext”) contract: how a separate JAR contributes runtime behaviour
> (a JS-scope global, event observation) and report UI (assets, embed renderers, nav
> pages) to a Karate run **without touching `karate-core`**. The first real consumer is
> [`karate-image`](../karate-image/README.md); a worked example lives there.
>
> Companion to [DESIGN.md § Ext Architecture](./DESIGN.md#ext-architecture), which covers
> the `karate-boot.js` activation surface and lifecycle. This doc is the **authoring**
> reference for the SPI types an ext implements / calls.

---

## What an ext can contribute

| Contribution | Mechanism | Lifecycle |
|---|---|---|
| Observe the run | implement `Ext extends RunListener`, `onEvent(RunEvent)` | every event `SUITE_ENTER`→`SUITE_EXIT` |
| A JS-scope global (`* image.compare(...)`) | `Suite.registerGlobal(name, …)` in `onBoot` | per-Suite singleton **or** per-scenario instance |
| Report assets (JS/CSS/pages) | `Suite.registerReportAssets(ReportAssets, ClassLoader)` in `onBoot` | copied + spliced at report-write time |
| A custom embed payload + its UI | emit `StepResult.Embed` at runtime + `KarateReport.registerEmbed` in ext JS | embed on the wire; renderer in the browser |

All wiring is **imperative, from `onBoot(Suite)`** — there is no `manifest.json` and no
annotation/ServiceLoader discovery for exts (activation is the explicit `boot.ext('name')`
in `karate-boot.js`; see DESIGN.md). Resolution is by name convention:
`boot.ext('image')` → `io.karatelabs.ext.image.ImageExt`.

---

## `Ext` interface

`io.karatelabs.core.Ext` — extends `RunListener`; every method has a default, so an ext
implements only what it needs.

```java
public interface Ext extends RunListener {
    default void onBoot(Suite suite) {}                 // register globals + assets here
    default void onShutdown() {}                         // after SUITE_EXIT
    default Map<String, Object> getManifest() {          // → SUITE_ENTER.data.exts[]
        return Collections.emptyMap();
    }
    default boolean onEvent(RunEvent event) { return true; }   // observe; false skips (from RunListener)
}
```

- **`onBoot`** runs once per Suite, before `SUITE_ENTER`. Exceptions here **fail the Suite
  loudly** — do eager validation (bad config path, missing resource) here. Exceptions
  inside `onEvent` are logged WARN and dropped (the run continues, that signal is lost).
- **`getManifest`** is a free-form `Map` surfaced on `SUITE_ENTER.data.exts[]` so receivers
  know which exts were active and with what config. Keep it small (name/version/key config).

**Source:** `Ext.java`, and DESIGN.md § Ext Architecture for the `boot.*` namespace + lifecycle.

---

## Ext globals — a name in scenario JS scope

`onBoot` registers an object under a name; the runtime seeds it into JS scope **before**
`karate-base.js` / `karate-config.js` evaluate (same mechanism as `karate`/`read`/`match`,
applies to called features too). A name that collides with a built-in
(`karate`/`read`/`match`/`driver`/…) **fails the Suite at boot**.

Two registration forms on `Suite`:

```java
// (a) one shared instance for the whole Suite — fine for STATELESS globals
public void registerGlobal(String name, Object instance);

// (b) a factory — a FRESH instance per scenario, handed that scenario's context.
//     Use this whenever the global holds per-scenario config (parallel-safe).
public void registerGlobal(String name, ExtGlobalFactory factory);

public Object getGlobal(String name);
public Map<String, Object> getGlobals();
```

```java
public interface ExtGlobalFactory {
    Object create(KarateJsContext context);   // context.getWorkingDir() resolves this:/classpath:/file:
}
```

**Choose the factory form for any stateful global.** A single shared instance is unsafe
once scenarios set config on it (`* image.threshold = 0.02`) under parallel execution.
`karate-image` registers a factory so each scenario gets its own `ImageApi` seeded with a
copy of the boot defaults plus the scenario’s `KarateJsContext` (which resolves
`this:`/`classpath:`/`file:` paths — no bespoke path code, no thread-locals).

### Implement the global as a `SimpleObject`, not a reflection POJO

`io.karatelabs.js.SimpleObject` exposes members to the JS engine natively (no reflective
adapter on the hot path). The contract is three methods:

```java
public class ImageApi implements SimpleObject {
    public Object jsGet(String name) { ... }            // member read OR a JavaInvokable for a method
    public void putMember(String name, Object value) {} // member/config write  → image.threshold = 0.02
    public Collection<String> jsKeys() { ... }          // enumeration
}
```

- A **method** (`image.compare(...)`) is `jsGet("compare")` returning a `JavaInvokable`
  (`Object call(Object... args)`).
- A **config property** (`image.baselineDir = '...'`) lands in `putMember`; reads come back
  through `jsGet`.

This **property-setter + verb** idiom is the canonical ext-global style (it mirrors
`karate.channel('grpc')`): set config as properties, then call the operation —
no method-name churn when config keys are added.

```gherkin
* image.baselineDir = 'baselines'      # putMember
* image.threshold = 0.02               # putMember
* def r = image.compare('home', shot)  # jsGet('compare') → JavaInvokable
```

> **Binary args.** A JS `Uint8Array` arrives as a `JsValue` whose `getJavaValue()` is
> `byte[]`; a raw decoded `byte[]` crosses JS scope as a number list. Unwrap canonically
> via `JsValue.getJavaValue()` (see `ImageApi.toBytes`).

**Source:** `Suite.registerGlobal` / `getGlobal` / `getGlobals`, `ExtGlobalFactory.java`,
`SimpleObject.java` (karate-js), `ScenarioRuntime.initEngine` (seeding).

---

## Report assets — `ReportAssets`

An ext ships JS/CSS/HTML inside its JAR under `META-INF/karate-ext/` and declares them in
`onBoot`. `io.karatelabs.core.ReportAssets` is a fluent spec:

```java
suite.registerReportAssets(
    ReportAssets.named("image")
        .js("static/image.js")                                   // required
        .css("static/image.css")                                 // optional
        .page("nav.pages", "Image diffs", "pages/image.html"),   // zero or more
    getClass().getClassLoader());
```

On-disk shape inside the JAR (`META-INF/karate-ext/` is the implicit root):

```
META-INF/karate-ext/
├── static/   # ext.js/css + any assets — the static/ prefix is stripped for the web path
└── pages/    # optional standalone HTML pages (nav.pages slot) — pages/ prefix kept
```

**Validation** runs at `onBoot` and fails the Suite loudly: `js(...)` is required, and every
referenced resource must exist on the classloader. **No core-version guard** today (exts ship
in lockstep with core).

**At report-write time** (`HtmlReportWriter` via `HtmlReportListener`):
- each ext’s assets are copied to `target/karate-reports/ext/<name>/` (`static/` stripped,
  `pages/` kept) — so `static/image.js` → `ext/image/image.js`;
- a `<script src="ext/<name>/<js>" defer></script>` (+ optional `<link>`) is spliced into
  the `<!-- KARATE_EXTS -->` placeholder on every page, registration-ordered;
- each `page("nav.pages", title, href)` becomes an `<a>` topbar tab spliced into
  `<!-- KARATE_NAV -->`, linking `ext/<name>/<href>`.
- Feature pages live under `feature-html/`, so their refs carry a `../` prefix
  (root/summary/timeline use no prefix) — the writer handles this.

> **Convention: name the asset file after the ext** (`image.js`, not a generic `ext.js`).
> The per-ext dir already prevents collisions; ext-named files are self-identifying in
> browser DevTools / stack traces, and the source leaf becomes the URL leaf
> (`ext/image/image.js`).
>
> **CSS: scope it, don’t Tailwind it.** Core owns the Tailwind build and scans only core
> templates, so a Tailwind class an ext references that core never renders is purged. Ext
> CSS is **hand-authored and scoped** under a `.k-<name>-ext` class (channel b). (A
> maintainer-managed `safelist` in `etc/tailwind/tailwind.config.js` exists for a future
> shared utility vocabulary, but is empty today.)

**Source:** `ReportAssets.java`, `Suite.registerReportAssets` / `getReportAssets`,
`HtmlReportWriter` (`buildExtsHtml` / `buildNavHtml` / `copyExtAssets`).

---

## Embeds — custom payload + custom UI

A step can attach a named, multi-part embed; the report renders it. This is also how exts
coordinate (each writes its own named embed; receivers decode by name).

### The wire shape — `StepResult.Embed`

```java
public static class Embed {
    Embed(byte[] data, String mime, String name);          // single-asset convenience → one "primary" part
    Embed(String name, List<Part> parts, Map<String,Object> meta);  // multi-asset
}
public static class Part {
    Part(String role, String mime, byte[] data);   // inline bytes (core writes to embeds/)
    Part(String role, String mime, String url);    // ext-written asset (report-relative URL)
}
```

Every embed serialises to the **uniform** shape
`{name, parts:[{role, mime, data|url|file}], meta}` (no legacy flat form). A screenshot is
one `"primary"` part; image-comparison carries `baseline`/`latest`/`diff`. Per-part
serialisation precedence: `file` (core wrote inline bytes to disk) → `url` (ext wrote the
asset) → `data` (inline base64). `role` is an open string; `meta` is free-form.

Emit from a global/step via the thread-local log context:

```java
LogContext.get().embed(new StepResult.Embed("image-comparison", parts, meta));
```

Embeds land **only** at `FEATURE_EXIT.data.scenarioResults[i].stepResults[j].embeds[]`
(not duplicated onto `SCENARIO_EXIT`) — see DESIGN.md § Reports for why.

### The render hook — `KarateReport.registerEmbed`

Core report JS (`res/karate-report.js`) renders each embed; an ext can take over rendering
for **its** embed name. The ext’s `<script defer>` calls:

```js
window.KarateReport.registerEmbed('image-comparison', (embed, api) => {
    // embed = {name, parts, meta}; return an HTML string.
    // api helpers: api._embedPartSrc(part) → page-relative src; api._esc(str) → HTML-escape.
    return '<div class="k-image-ext">…</div>';
});
```

- `_renderEmbed` delegates to a registered renderer keyed by `embed.name`, else falls back
  to the generic per-part render (so the embed still shows with **no** ext loaded — graceful
  degradation).
- **Timing:** Alpine (`defer`) renders the report *before* the ext `<script defer>` runs, so
  registration is typically late. `registerEmbed` handles this by **upgrading
  already-rendered embeds in place** (each embed host carries a stable `data-embed-id`). Ext
  authors don’t manage ordering.
- `KarateReport` is exposed on `window` precisely so a separate ext script can reach it (a
  top-level `const` is a global lexical binding, not a `window` property).

**Source:** `StepResult.java` (`Embed` / `Part` / `toMap`), `res/karate-report.js`
(`registerEmbed` / `_renderEmbed` / `_embedPartSrc`), `LogContext.embed`.

---

## Slot model (report DOM)

Named DOM containers / splice points the report exposes for ext UI. Reality vs. design:

| Slot | Where | Wired today? |
|---|---|---|
| `step.embed` | per-step | ✅ via `KarateReport.registerEmbed` (above) — no DOM container needed |
| `nav.pages` | topbar nav | ✅ server-side splice (`<!-- KARATE_NAV -->`) from `ReportAssets.page(...)` |
| `summary.panels` | summary, below fold | ◑ container present; client-side render |
| `summary.cards` / `feature.panels` | summary KPI row / feature sidebar | ✗ container not yet in templates |

`step.embed` and `nav.pages` are the two an ext relies on today.

---

## Checklist: authoring a new ext

1. `io.karatelabs.ext.<name>.<Name>Ext implements Ext`; in `onBoot`:
   - `suite.registerGlobal("<name>", ctx -> new <Name>Api(defaults, ctx))` (factory if stateful);
   - `suite.registerReportAssets(ReportAssets.named("<name>").js("static/<name>.js")…, getClass().getClassLoader())`.
2. `<Name>Api implements SimpleObject` — `jsGet` (verbs as `JavaInvokable` + config reads),
   `putMember` (config writes), `jsKeys`.
3. Emit `StepResult.Embed` via `LogContext.get().embed(...)` for any rich step output.
4. Ship `META-INF/karate-ext/static/<name>.js` that calls
   `window.KarateReport.registerEmbed('<embed-name>', fn)`; CSS scoped under `.k-<name>-ext`.
5. Activate from `karate-boot.js`: `const x = boot.ext('<name>'); x.someConfig = …`.

See [`karate-image`](../karate-image/README.md) for a complete, shipping example.

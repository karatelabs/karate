# Karate Report — Tailwind Sources

Source files for the Karate v2 report's Tailwind CSS build.

See [`docs/IMAGE_SPIKE.md`](../../docs/IMAGE_SPIKE.md) §3.1 (production build),
§3.1.1 (dev iteration loop), §3.1.2 (template architecture) and D20 (brand +
utility-class-only constraints).

## Files

- `tailwind.config.js` — Tailwind config. `theme.extend` carries the Karate Labs
  brand palette (slate `brand`, `accent` blue, `amber`, `surface` neutrals) plus
  a system-font stack (no Google Fonts). Content globs (repo-root-relative)
  cover the production templates under
  `karate-core/src/main/resources/io/karatelabs/output/*.html` **and**
  `res/karate-report.js` (the JS file emits Tailwind class names inside
  HTML-string template literals — without scanning it, those classes would be
  missing from the generated CSS).
- `input.css` — Tailwind entry point. No `@layer components` block (D20) —
  utility classes only. One Alpine shim appended: `[x-cloak] { display: none !important; }`.
- `tailwind.sh` — single build script. Downloads the Tailwind standalone CLI
  (v3.4.17) to `.cache/` on first run, then invokes it. No node / npm needed.
- `.cache/` — gitignored; holds the downloaded CLI binary per platform.

## Build

```
bash etc/tailwind/tailwind.sh
```

Reads from this directory, writes the minified CSS to
`karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css`.

CI runs the same script then `git diff --exit-code` on the output — a stale
CSS in main fails the build, the diff shows the contributor what they missed
regenerating.

## Dev iteration loop

```
# 1. Generate a report once against representative test data.
mvn -pl karate-core test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q
open karate-core/target/karate-report-dev/karate-summary.html

# 2. In a second terminal, watch + rebuild the CSS on every template/config save.
bash etc/tailwind/tailwind.sh --watch
```

Edit a template; CSS rebuilds; reload the browser. Same toolchain as the CI
build; no separate preview harness, no CDN dependency, no node toolchain.

# Karate Report — Tailwind Sources

Source files for the Karate v2 report's Tailwind CSS build.

See [`docs/IMAGE_SPIKE.md`](../../../../docs/IMAGE_SPIKE.md) §3.1 (production
build), §3.1.1 (dev preview harness), §3.1.2 (template architecture) and D20
(brand + utility-class-only constraints).

## Files

- `tailwind.config.js` — Tailwind config; `theme.extend` carries the Karate
  Labs brand palette (slate `brand`, `accent` blue, `amber`, `surface` neutrals)
  plus a system-font stack (no Google Fonts). Content globs cover the
  production templates under `../resources/io/karatelabs/output/*.html` plus
  the dev preview harness under `./preview/*.html` (added in a later session).
- `input.css` — Tailwind entry point. Phase 1 has no `@layer components`
  block (D20) — utility classes only.

## Building (when the mojo lands)

```
mvn -pl karate-core karate:tailwind
```

Reads from this directory, writes the precompiled CSS to
`../resources/io/karatelabs/output/res/karate-report.css` and updates the
hash file. Build will fail if the hash is stale after a template edit —
re-run the mojo to refresh.

The Maven mojo is **not yet wired** as of the foundation commit. Production
templates still reference the legacy `res/karate-report.css` (currently
Bootstrap-driven). Subsequent sessions:

1. Wire the `karate:tailwind` Maven mojo + vendored binary download.
2. Port `karate-summary.html` / `karate-feature.html` / `karate-timeline.html`
   from Bootstrap classes to Tailwind classes.

## Dev iteration loop

For sub-second iteration on Tailwind class changes:

```
# 1. Generate a report once against representative test data.
mvn -pl karate-core test -Dtest=HtmlReportWriterTest#testHtmlReportGeneration -q
open ../../target/karate-report-dev/karate-summary.html

# 2. In a second terminal, watch + rebuild the CSS on every template/config save.
npx tailwindcss --watch -i input.css -o ../resources/io/karatelabs/output/res/karate-report.css
```

Edit a template; CSS rebuilds; reload the browser. Same toolchain as the
production mojo will use; no separate preview harness, no CDN dependency.

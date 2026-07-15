# Release Checklist

Steps to publish a new Karate release. Replace `X.Y.Z` with the version being released.

> **TODO (before/with the next release that ships an ext): ext distribution + CLI v2-only.**
> An ext has **two delivery forms for two audiences**: the Maven Central `io.karatelabs:karate-image`
> thin jar is for **Java teams** (declare it as a Maven/Gradle test dependency; `karate-core` is
> `provided`, engines come transitively) — this path already ships and is verified. The
> `karate-image-X.Y.Z.jar` **drop-in fatjar** (engines bundled) is for **non-Java teams** driving
> Karate via the Rust CLI. The deferred work below is only the CLI / non-Java side.
>
> For 2.0.10 we shipped core artifacts only and kept the `karate-image-X.Y.Z.jar` drop-in local.
> (Drop-in jars are named per-ext: `karate-image-X.Y.Z.jar`, `karate-max-X.Y.Z.jar`, etc.) To ship
> the drop-in path properly we still need to:
> - Switch the Rust CLI (`../karate-cli`) to **v2 `io.karatelabs.Main` only** — drop the
>   `com.intuit.karate.Main` v1 shim support in `delegate.rs`.
> - Teach the CLI to **load exts from the manifest** (managed ext install), instead of the
>   current manual `~/.karate/ext/` drop-in only.
> - Likely a matching change in **`../karate-vscode-v2`**.
> - Then per-release: attach `karate-ext-image-X.Y.Z.jar` to the GitHub release, add a
>   `karate.sh` manifest ext entry, and add a CI fatjar-build job
>   (`mvn package -pl karate-image -am -Pfatjar -DskipTests`).

## 1. Prepare the Release

- [ ] Verify `main` is green on CI
- [ ] Update version in `pom.xml` (remove any `-SNAPSHOT` or `.RC*` suffix):
  ```bash
  mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
  ```
- [ ] Commit and push (use `[no ci]` — `main` is already green and the Maven release job runs the full test suite). The `vX.Y.Z` tag is created later via the GitHub release UI, not from the CLI:
  ```bash
  git add -A && git commit -m "release X.Y.Z [no ci]"
  git push
  ```

## 2. Generate the CVE & SBOM Report

The scan lives in its own workflow (`.github/workflows/cve.yml`), separate from the release. It is
deliberately **not** on the release path — an NVD scan can take hours and a release should not wait
on one, so the release does **not** block on CVEs. `cve.yml` is the gate: it fails on CVSS >= 9.0.

- [ ] Trigger **CVE & SBOM** manually (`workflow_dispatch`) on `main` with `version: X.Y.Z`. The
      input rewrites the pom inside the runner before the scan (nothing is pushed), so the report
      header, the SBOM's own `io.karatelabs:*` component entries and the artifact name all agree on
      `X.Y.Z`. Leave it blank — as the weekly schedule does — to scan the pom as-is. Can run in
      parallel with step 3.
- [ ] Expect minutes on a warm NVD cache, but **2+ hours on a cold one** (the cache is keyed
      `nvd-db-` and only a completed run saves it). If the wait is unacceptable, use the local
      fallback below.
- [ ] Download the `cve-sbom-report-X.Y.Z` artifact from the run — you want `cve-sbom-report.html`
- [ ] Confirm the CVE Summary shows **no active Critical (>= 9)** rows. Criticals under
      **Suppressed** are fine — those are declared false positives, see `etc/cve-suppressions.xml`

  Local fallback — produces the identical report without waiting on CI:
  ```bash
  mvn -B -ntp org.owasp:dependency-check-maven:12.2.2:check \
    -DossIndexAnalyzerEnabled=false -DskipProvidedScope=true -DskipTestScope=true \
    -DsuppressionFiles=etc/cve-suppressions.xml -Dformat=JSON
  python3 etc/generate-cve-report.py --version X.Y.Z
  ```
  The report lands at `target/cve-sbom-report.html`. Pass `--version` explicitly — unlike CI it does
  not read the pom, and an omitted flag drops the version from the header entirely. Note it stamps
  only the *header*: the SBOM's own `io.karatelabs:*` entries come from the jars on disk, so build
  from a pom already at `X.Y.Z` if you need the two to agree.

  If dep-check flags a **false positive** (a CVE for a same-named library in another ecosystem is
  the usual culprit), add a rule to `etc/cve-suppressions.xml` rather than waving the release
  through — the suppression is what makes the gate meaningful on the next run.

## 3. Publish Maven Artifacts

- [ ] Trigger the **maven-release** job **manually** (`workflow_dispatch`)
- [ ] Use version `X.Y.Z` (must match the tag exactly), `maven: enabled`, `tests: enabled`
- [ ] The job sets the pom version itself — it does not rely on the commit from step 1
- [ ] Verify artifacts appear on Maven Central
- [ ] Download the `karate-release-X.Y.Z` artifact — it holds `karate-X.Y.Z.zip`, which unzips to the
      `karate-X.Y.Z.jar` fat jar you attach in step 4

## 4. GitHub Release

- [ ] Go to https://github.com/karatelabs/karate/releases/new and create tag `vX.Y.Z` on the release form (target: `main`) — this creates the tag as part of publishing the release
- [ ] Set the **release title** to `vX.Y.Z` explicitly (must match the tag) — if left blank, the GitHub UI defaults the title to the most recent commit message
- [ ] Write release notes following the template below — one-line bullets, issue refs at the end (e.g. `#2843`), milestone link, compare link, 2.0.0 migration note, then `### Artifacts`. See [v2.0.8](https://github.com/karatelabs/karate/releases/tag/v2.0.8) / [v2.0.9](https://github.com/karatelabs/karate/releases/tag/v2.0.9) for reference renderings.

  ```markdown
  ## ⚠️ Breaking Changes
  * <one-line description of the behavior change AND the migration needed to keep old behavior, ending with the issue ref> #NNNN
  * ...

  ## Important Fixes
  * <one-line description of the fix, ending with the issue ref> #NNNN
  * ...

  ## New Features & Enhancements
  * <one-line description — issue ref optional, only when there's a tracking issue>
  * ...

  View the complete list of [all issues fixed in this release](https://github.com/karatelabs/karate/milestone/NN?closed=1).

  **Full Changelog**: https://github.com/karatelabs/karate/compare/vPREVIOUS...vX.Y.Z

  **Important**: refer [2.0.0 release notes](https://github.com/karatelabs/karate/releases/tag/v2.0.0) for those upgrading from 1.X

  ### Artifacts
  * [Maven artifacts](https://central.sonatype.com/artifact/io.karatelabs/karate-core/X.Y.Z)
  * [Standalone JAR](https://docs.karatelabs.io/getting-started/standalone-execution) (download below)
  * CVE / SBOM report (download below)
  ```

  Style notes:
  - **Breaking Changes** = changes that require user action to preserve existing behavior (new defaults, removed/renamed APIs, changed semantics). Lead the notes with this section and state the migration inline on each bullet. Omit the section entirely when there are none.
  - **Important Fixes** = user-visible bugs, regressions, and v1→v2 parity restorations. Almost always carries an issue ref.
  - **New Features & Enhancements** = additive changes, internal improvements, dep bumps worth surfacing. Issue ref optional.
  - Keep each bullet to one line. Lead with the change, not the file/module. Put the issue ref at the end.
  - Skip dependabot-only / chore-only releases of either section if there's nothing in it — don't ship empty headers.

- [ ] Upload release assets (attach to the GitHub release):
  - `karate-X.Y.Z.jar` (fat jar — unzipped from the step 3 `karate-release-X.Y.Z` artifact)
  - `cve-sbom-report.html` (from step 2)

## 5. Close Issues and Milestone

- [ ] Close each fixed issue on the `X.Y.Z` milestone, leaving a `vX.Y.Z released` comment on each (always — including issues already labeled `fixed`):
  ```bash
  gh issue close <NUM> -R karatelabs/karate -c "vX.Y.Z released"
  ```
- [ ] Move any remaining open issues to the next milestone
- [ ] Close the GitHub milestone for `X.Y.Z`

## 6. Update karate.sh CLI Manifest

This is critical — the CLI installer pulls versions from this manifest.

- [ ] Go to the `karate-sh` repo (sibling directory `../karate-sh`)
- [ ] Edit `public/manifest.json`:
  - Add a new version entry under the `karate` artifact
  - Set `url` to the fat jar download URL on GitHub Releases
  - Set `sha256` from the release asset or compute locally:
    ```bash
    shasum -a 256 karate-X.Y.Z.jar
    ```
  - Move the `stable` channel from the previous version to the new one
  - Update `channel_defaults.stable` to point to `X.Y.Z`
  - Update `generated_at` timestamp
  - **Trim old entries** — keep only the new release + the previous two on the current major line (N, N-1, N-2), plus the latest `1.5.2` legacy anchor for v1 users. Drop everything else. The JARs stay on GitHub Releases either way; the manifest is just the installer's lookup index, so this only affects what `karate.sh` can resolve by version name.
- [ ] Commit and push to `main` (Netlify auto-deploys)
- [ ] Verify: `curl -s https://karate.sh/manifest.json | jq '.channel_defaults.stable'`
- [ ] Refer to `../karate-sh/README.md` for full manifest schema details

## 7. Update Reference Projects

- [ ] Update `../karate-template` to use version `X.Y.Z`
- [ ] Update `../karate-todo` to use version `X.Y.Z`
- [ ] Verify both projects build and tests pass
- [ ] Commit and push both repos

## 8. Update karate-examples

> **TODO**: Automate this step with CI/CD — a workflow that bumps the Karate version
> across all example projects and runs their tests.

- [ ] Update `../karate-examples` — bump Karate version to `X.Y.Z` in all example `pom.xml` / `build.gradle` files
- [ ] Build and test all examples to catch any breaking changes
- [ ] Commit and push

## 9. Bump to Next Development Version

- [ ] Update `pom.xml` to the next RC version:
  ```bash
  mvn versions:set -DnewVersion=X.Y.Z+1.RC1 -DgenerateBackupPoms=false
  ```
- [ ] Commit and push (use `[no ci]` to skip the CI build for this bump):
  ```bash
  git add -A && git commit -m "prepare for next development iteration [no ci]"
  git push
  ```

## 10. Announce

- [ ] LinkedIn
- [ ] Twitter / X
- [ ] GitHub Discussions (optional, for major releases)

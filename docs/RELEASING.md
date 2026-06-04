# Release Checklist

Steps to publish a new Karate release. Replace `X.Y.Z` with the version being released.

> **TODO (before/with the next release that ships an ext): ext distribution + CLI v2-only.**
> `karate-image` is the first ext but for 2.0.10 we shipped core artifacts only and kept the
> `karate-image-X.Y.Z.jar` drop-in local. (Drop-in jars are named per-ext: `karate-image-X.Y.Z.jar`,
> `karate-max-X.Y.Z.jar`, etc.) To ship exts properly we still need to:
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

## 2. Publish Maven Artifacts

- [ ] Trigger the Maven release CI/CD job **manually**
- [ ] Use version `X.Y.Z` (must match the tag exactly)
- [ ] Verify artifacts appear on Maven Central

## 3. GitHub Release

- [ ] Go to https://github.com/karatelabs/karate/releases/new and create tag `vX.Y.Z` on the release form (target: `main`) — this creates the tag as part of publishing the release
- [ ] Set the **release title** to `vX.Y.Z` explicitly (must match the tag) — if left blank, the GitHub UI defaults the title to the most recent commit message
- [ ] Write release notes following the template below — one-line bullets, issue refs at the end (e.g. `#2843`), milestone link, compare link, 2.0.0 migration note, then `### Artifacts`. See [v2.0.8](https://github.com/karatelabs/karate/releases/tag/v2.0.8) / [v2.0.9](https://github.com/karatelabs/karate/releases/tag/v2.0.9) for reference renderings.

  ```markdown
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
  - **Important Fixes** = user-visible bugs, regressions, and v1→v2 parity restorations. Almost always carries an issue ref.
  - **New Features & Enhancements** = additive changes, internal improvements, dep bumps worth surfacing. Issue ref optional.
  - Keep each bullet to one line. Lead with the change, not the file/module. Put the issue ref at the end.
  - Skip dependabot-only / chore-only releases of either section if there's nothing in it — don't ship empty headers.

- [ ] Upload release assets (attach to the GitHub release):
  - `karate-X.Y.Z.jar` (fat jar)
  - `cve-sbom-report.html`

## 4. Close Issues and Milestone

- [ ] Close each fixed issue on the `X.Y.Z` milestone, leaving a `vX.Y.Z released` comment on each (always — including issues already labeled `fixed`):
  ```bash
  gh issue close <NUM> -R karatelabs/karate -c "vX.Y.Z released"
  ```
- [ ] Move any remaining open issues to the next milestone
- [ ] Close the GitHub milestone for `X.Y.Z`

## 5. Update karate.sh CLI Manifest

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

## 6. Update Reference Projects

- [ ] Update `../karate-template` to use version `X.Y.Z`
- [ ] Update `../karate-todo` to use version `X.Y.Z`
- [ ] Verify both projects build and tests pass
- [ ] Commit and push both repos

## 7. Update karate-examples

> **TODO**: Automate this step with CI/CD — a workflow that bumps the Karate version
> across all example projects and runs their tests.

- [ ] Update `../karate-examples` — bump Karate version to `X.Y.Z` in all example `pom.xml` / `build.gradle` files
- [ ] Build and test all examples to catch any breaking changes
- [ ] Commit and push

## 8. Bump to Next Development Version

- [ ] Update `pom.xml` to the next RC version:
  ```bash
  mvn versions:set -DnewVersion=X.Y.Z+1.RC1 -DgenerateBackupPoms=false
  ```
- [ ] Commit and push (use `[no ci]` to skip the CI build for this bump):
  ```bash
  git add -A && git commit -m "prepare for next development iteration [no ci]"
  git push
  ```

## 9. Announce

- [ ] LinkedIn
- [ ] Twitter / X
- [ ] GitHub Discussions (optional, for major releases)

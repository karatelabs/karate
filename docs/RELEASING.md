# Release Checklist

Steps to publish a new Karate release. Replace `X.Y.Z` with the version being released.

## 1. Prepare the Release

- [ ] Verify `main` is green on CI
- [ ] Update version in `pom.xml` (remove any `-SNAPSHOT` or `.RC*` suffix):
  ```bash
  mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
  ```
- [ ] Commit and push (the `vX.Y.Z` tag is created later via the GitHub release UI, not from the CLI):
  ```bash
  git add -A && git commit -m "release X.Y.Z"
  git push
  ```

## 2. Publish Maven Artifacts

- [ ] Trigger the Maven release CI/CD job **manually**
- [ ] Use version `X.Y.Z` (must match the tag exactly)
- [ ] Verify artifacts appear on Maven Central

## 3. GitHub Release

- [ ] Go to https://github.com/karatelabs/karate/releases/new and create tag `vX.Y.Z` on the release form (target: `main`) — this creates the tag as part of publishing the release
- [ ] Set the **release title** to `vX.Y.Z` explicitly (must match the tag) — if left blank, the GitHub UI defaults the title to the most recent commit message
- [ ] Write release notes:
  - Summary of important fixes / features
  - Link to the milestone: `https://github.com/karatelabs/karate/milestone/NN?closed=1`
  - Full changelog link: `https://github.com/karatelabs/karate/compare/vPREVIOUS...vX.Y.Z`
  - Reference to the 2.0.0 migration guide if applicable
- [ ] Upload release assets:
  - `karate-X.Y.Z.jar` (fat jar)
  - `cve-sbom-report.html`
- [ ] See [v2.0.1](https://github.com/karatelabs/karate/releases/tag/v2.0.1) for reference

## 4. Close Issues and Milestone

- [ ] Close each fixed issue on the `X.Y.Z` milestone via the GitHub UI, leaving a comment `vX.Y.Z released` on each (issues labeled `fixed` typically just need to be closed)
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

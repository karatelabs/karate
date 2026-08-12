#!/usr/bin/env bash
# Build a karate-js jar from any git ref, for the js-* A/B family:
#
#   etc/js-arm.sh <git-ref>        # -> target/js-arms/karate-js-<shortsha>.jar
#
# Prints the jar path on stdout (everything else goes to stderr), so it composes:
#
#   jar_a=$(etc/js-arm.sh <base-ref>)
#   jar_b=$(etc/js-arm.sh <candidate-ref>)
#   etc/run.sh js-functions --js-jar "$jar_a" --run-tag mymatrix:p1:a --no-jfr
#
# Why not just `mvn -pl karate-js package` on a checkout: an A/B needs BOTH arms' jars,
# built from exact commits, without touching the working tree — and both arms usually carry
# the same Maven version, so the version string identifies nothing. So this script:
#
#   - builds in a detached, temporary `git worktree` (the checkout is never disturbed);
#   - names the jar by the resolved short sha, which IS the identity;
#   - writes a sidecar manifest (<jar>.manifest: full commit, jar sha256, build jdk) that
#     --js-jar verifies on every use — a stale or half-copied jar fails the run instead of
#     relabelling it;
#   - caches by sha: an existing jar whose manifest still matches its bytes is returned
#     without rebuilding. The build lands under a temporary name and is renamed into place
#     only after the manifest is written, so an interrupted build can never become a cache hit.
set -euo pipefail
cd "$(dirname "$0")/.."   # karate-profiling
repo_root="$(cd .. && pwd)"

ref="${1:-}"
[[ -n "$ref" ]] || { echo "usage: etc/js-arm.sh <git-ref>" >&2; exit 2; }

full_sha="$(git -C "$repo_root" rev-parse --verify "$ref^{commit}")" \
    || { echo "!! '$ref' does not resolve to a commit" >&2; exit 2; }
short_sha="$(git -C "$repo_root" rev-parse --short "$full_sha")"

arms_dir="$PWD/target/js-arms"
jar="$arms_dir/karate-js-$short_sha.jar"
manifest="$jar.manifest"
mkdir -p "$arms_dir"

sha256_of() {
    if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1
    else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

# Cache hit only when jar AND manifest exist and still agree — anything else rebuilds.
if [[ -f "$jar" && -f "$manifest" ]]; then
    declared="$(grep '^sha256:' "$manifest" | awk '{print $2}')"
    if [[ -n "$declared" && "$declared" == "$(sha256_of "$jar")" ]]; then
        echo "[js-arm] cached: $jar (commit $full_sha)" >&2
        echo "$jar"
        exit 0
    fi
    echo "[js-arm] cache entry for $short_sha is stale or corrupt — rebuilding" >&2
    rm -f "$jar" "$manifest"
fi

worktree="$(mktemp -d "${TMPDIR:-/tmp}/karate-js-arm-XXXXXX")"
cleanup() {
    git -C "$repo_root" worktree remove --force "$worktree" >/dev/null 2>&1 || true
    rm -rf "$worktree"
}
trap cleanup EXIT

echo "[js-arm] building karate-js at $short_sha ($ref) in a temporary worktree" >&2
git -C "$repo_root" worktree add --detach "$worktree" "$full_sha" >&2

# -o (offline) deliberately, like the rest of the harness: a surprise dependency download is
# not something a measured arm should contain. Both arm commits share the checkout's deps.
mvn -o -q -f "$worktree/pom.xml" -pl karate-js -am package -DskipTests >&2

built="$(find "$worktree/karate-js/target" -maxdepth 1 -name 'karate-js-*.jar' \
    ! -name '*sources*' ! -name '*javadoc*' ! -name 'original-*' | head -1)"
[[ -n "$built" ]] || { echo "!! build produced no karate-js jar under $worktree" >&2; exit 1; }

# Temp name -> hash -> manifest -> atomic rename. Order matters: the manifest names bytes
# that are already final, and the jar only appears under its real name complete.
tmp_jar="$jar.tmp.$$"
cp "$built" "$tmp_jar"
jar_sha="$(sha256_of "$tmp_jar")"
cat > "$manifest" <<MANIFEST
commit: $full_sha
sha256: $jar_sha
jdk: $(java -version 2>&1 | head -1)
built: $(date -u +%Y-%m-%dT%H:%M:%SZ)
ref: $ref
MANIFEST
mv "$tmp_jar" "$jar"

echo "[js-arm] built: $jar" >&2
echo "[js-arm] manifest: commit $full_sha, sha256 $jar_sha" >&2
echo "$jar"

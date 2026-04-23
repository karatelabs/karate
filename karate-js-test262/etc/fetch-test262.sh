#!/usr/bin/env bash
#
# Fetches the tc39/test262 suite at a pinned SHA. Idempotent.
#
# Re-run to update: edit TEST262_SHA below, then run this script.
# The checkout is shallow (single commit, blobless) so the clone stays small.

set -euo pipefail

# Pinned test262 commit (edit to bump). Use a full 40-char SHA.
TEST262_SHA="d5e73fc8d2c663554fb72e2380a8c2bc1a318a33"

# Resolve the module root (parent of etc/) so the suite is cloned at a
# predictable location regardless of the caller's cwd.
HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE_ROOT="$(cd "$HERE/.." && pwd)"
DIR="$MODULE_ROOT/test262"

if [ ! -d "$DIR/.git" ]; then
    echo "cloning tc39/test262 into $DIR ..."
    git clone --filter=blob:none --no-checkout https://github.com/tc39/test262.git "$DIR"
fi

cd "$DIR"

# Fetch just the one commit we want.
git fetch --depth 1 origin "$TEST262_SHA"
git checkout --force "$TEST262_SHA"

echo ""
echo "test262 checked out at:"
echo "  $DIR"
echo "  SHA: $TEST262_SHA"
echo ""
echo "Suite sizes:"
find test -type f -name '*.js' 2>/dev/null | awk -F/ '
    { top = $2; count[top]++; total++ }
    END {
        for (t in count) printf "  %-16s %6d\n", t, count[t]
        printf "  %-16s %6d\n", "TOTAL", total
    }
' | sort

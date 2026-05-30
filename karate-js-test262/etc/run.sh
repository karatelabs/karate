#!/usr/bin/env bash
# Run the karate-js test262 conformance suite. HTML report is opt-in via --full.
#
# Usage (from any directory):
#   etc/run.sh                                  # dev mode: FAIL/SKIP rows only, no HTML
#   etc/run.sh --only 'test/language/**'        # narrow slice (dev mode)
#   etc/run.sh --full                           # write PASS rows + generate HTML report
#   etc/run.sh --only '...' --max-duration 300000   # 5-min cap (dev mode)
#
# Anything you pass is forwarded to Test262Runner — see --help for flags.
#
# Each invocation writes a fresh, self-contained directory:
#   target/test262/run-<timestamp>/{results.jsonl, run-meta.json, progress.log}
# Plus html/ only when --full is set. Old runs are never touched; clean up
# with `mvn clean` when desired.
#
# Steps:
#   1. Install the current karate-js into the local Maven repo so the runner
#      picks up any engine changes you just made.
#   2. Run the conformance runner inside the per-run directory.
#   3. If --full was passed: also generate the HTML report inside that dir.

set -euo pipefail

# Resolve the module root (parent of etc/) so cwd doesn't matter.
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/.."

if [[ ! -d test262 ]]; then
    echo "error: test262/ directory not found. Run etc/fetch-test262.sh first." >&2
    exit 2
fi

# Compute the run-dir ONCE so the runner and the report write into the same
# directory. The timestamp format mirrors the runner's default
# (yyyy-MM-dd-HHmmss).
RUN_DIR="target/test262/run-$(date +%Y-%m-%d-%H%M%S)"

echo "==> installing karate-js to local Maven repo"
mvn -f ../pom.xml -pl karate-js -o install -DskipTests -q

# Compile the runner itself. `exec:java` does NOT trigger compilation, so without
# this step it runs whatever stale target/classes happen to exist — meaning edits
# to Test262Runner / the harness silently never take effect (this masked the
# onlyStrict strict-directive prepend for an entire iteration cycle). Always rebuild.
echo "==> compiling test262 runner"
mvn -f ../pom.xml -pl karate-js-test262 -o test-compile -q

echo "==> running conformance suite  (run-dir: $RUN_DIR)"
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -q \
    -Dexec.args="--run-dir $RUN_DIR $*"

# Only generate the HTML report when --full is in the forwarded args.
# Dev-mode runs (no --full) skip both PASS rows and the report.
FULL=0
for a in "$@"; do
    if [[ "$a" == "--full" ]]; then FULL=1; break; fi
done

if [[ "$FULL" == "1" ]]; then
    echo "==> generating HTML report"
    mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -q \
        -Dexec.mainClass=io.karatelabs.js.test262.Test262Report \
        -Dexec.args="--run-dir $RUN_DIR"
    echo
    echo "Open: $(pwd)/$RUN_DIR/html/index.html"
fi

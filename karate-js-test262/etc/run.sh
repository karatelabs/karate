#!/usr/bin/env bash
# Run the karate-js test262 conformance suite and generate the HTML report.
#
# Usage (from any directory):
#   etc/run.sh                                  # full suite, unlimited duration
#   etc/run.sh --only 'test/language/**'        # narrow slice
#   etc/run.sh --only 'test/language/**' --max-duration 300000   # 5-min cap
#
# Anything you pass is forwarded to Test262Runner — see --help for flags.
#
# Steps:
#   1. Install the current karate-js into the local Maven repo so the runner
#      picks up any engine changes you just made.
#   2. Run the conformance runner (writes target/test262/results.jsonl,
#      target/test262/run-meta.json and a timestamped session log under
#      target/test262/).
#   3. Generate the static HTML report under target/test262/html/.

set -euo pipefail

# Resolve the module root (parent of etc/) so cwd doesn't matter.
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/.."

if [[ ! -d test262 ]]; then
    echo "error: test262/ directory not found. Run etc/fetch-test262.sh first." >&2
    exit 2
fi

echo "==> installing karate-js to local Maven repo"
mvn -f ../pom.xml -pl karate-js -o install -DskipTests -q

echo "==> running conformance suite"
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -q \
    -Dexec.args="$*"

echo "==> generating HTML report"
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -q \
    -Dexec.mainClass=io.karatelabs.js.test262.Test262Report

echo
echo "Open: $(pwd)/target/test262/html/index.html"

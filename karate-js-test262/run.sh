#!/usr/bin/env bash
# Run the karate-js test262 conformance suite and generate the HTML report.
#
# Usage (from this directory):
#   ./run.sh                                  # full suite, unlimited duration
#   ./run.sh --only 'test/language/**'        # narrow slice
#   ./run.sh --only 'test/language/**' --max-duration 300000   # with 5-min cap
#
# Anything you pass is forwarded to Test262Runner — see --help for flags.
#
# Steps:
#   1. Install the current karate-js into the local Maven repo so the runner
#      picks up any engine changes you just made.
#   2. Run the conformance runner.
#   3. Generate the static HTML report under ./html/ and print its path.

set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -d test262 ]]; then
    echo "error: test262/ directory not found. Run ./fetch-test262.sh first." >&2
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
echo "Open: $(pwd)/html/index.html"

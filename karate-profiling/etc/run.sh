#!/usr/bin/env bash
# Run a profiling workload. See docs/PROFILING.md for what to do with the output.
#
# Usage (from any directory):
#   etc/run.sh --list                          # catalogue
#   etc/run.sh harness-smoke                   # is the harness alive?
#   etc/run.sh <workload> --xmx 768m --gc zgc  # flags are passed straight through
#
# Each invocation writes a fresh, self-contained directory:
#   target/profiling/<workload>-<timestamp>/{digest.md, run.jfr, stdout.log, run-meta.txt}
# Old runs are never touched. Heap dumps are roughly the size of --xmx, so prune
# target/profiling/ (or `mvn clean`) once you have what you need.
#
# Steps:
#   1. Install the current karate-core into the local Maven repo, so the run picks up
#      whatever you just changed rather than a stale artifact.
#   2. Materialise the exact classpath the forked child JVM needs. The parent cannot
#      borrow its own: under exec:java this JVM is Maven's, so java.class.path points
#      at Maven rather than at us.
#   3. Hand off to the Profiler, which forks the child (and a sibling mock if the
#      workload needs one).

set -euo pipefail

# Resolve the module root (parent of etc/) so cwd doesn't matter.
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/.."

SKIP_BUILD="${PROFILING_SKIP_BUILD:-}"

if [[ -z "$SKIP_BUILD" ]]; then
    echo "==> installing karate-core to local Maven repo"
    mvn -f ../pom.xml -pl karate-core -am -o install -DskipTests -q

    echo "==> compiling karate-profiling"
    mvn -o compile -q

    echo "==> resolving child classpath"
    mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
fi

if [[ ! -f target/cp.txt ]]; then
    echo "error: target/cp.txt not found. Re-run without PROFILING_SKIP_BUILD set." >&2
    exit 2
fi

# exec:java runs in Maven's JVM; the Profiler only forks from here, so this JVM's own
# heap and collector are irrelevant to what gets measured.
exec mvn -o -q exec:java -Dexec.args="$*"

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

# The Gatling workloads live behind -Pgatling, because Gatling and its Scala runtime are
# ~40 MB of classpath every other workload has no use for. Turn the profile on for them —
# and for --list, so the catalogue is never silently short of the ones you can run.
# Plain strings rather than arrays, expanded unquoted: macOS still ships bash 3.2, where
# "${arr[@]}" on an *empty* array is an unbound-variable error under `set -u`. Neither value
# ever contains a space, so word splitting is exactly what we want.
MODULE="karate-core"
PROFILE=""
case "${1:-}" in
    gatling-*|--list)
        PROFILE="-Pgatling"
        MODULE="karate-gatling"   # -am pulls karate-core along
        ;;
esac

if [[ -z "$SKIP_BUILD" ]]; then
    echo "==> installing karate to local Maven repo"
    mvn -f ../pom.xml -pl "$MODULE" -am -o install -DskipTests -q

    echo "==> compiling karate-profiling"
    mvn -o compile -q $PROFILE

    echo "==> resolving child classpath"
    mvn -o -q dependency:build-classpath $PROFILE -Dmdep.outputFile=target/cp.txt
fi

if [[ ! -f target/cp.txt ]]; then
    echo "error: target/cp.txt not found. Re-run without PROFILING_SKIP_BUILD set." >&2
    exit 2
fi

# exec:java runs in Maven's JVM; the Profiler only forks from here, so this JVM's own
# heap and collector are irrelevant to what gets measured.
exec mvn -o -q exec:java $PROFILE -Dexec.args="$*"

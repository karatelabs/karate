#!/usr/bin/env bash
# Tests the bench scripts' pure decision logic. No AWS, no hosts, no cost.
#
#   etc/ec2/selftest.sh
#
# This exists because of a specific failure: collect.sh copied a one-hour soak's run
# directory, found every file it was asked for, exited 0 — and left the digest behind,
# because the parent writes it AFTER the child exits. Nothing caught it. The guard that
# now catches it is only worth having if it has been shown to fire, so each case below
# names the real situation it stands for.
#
# It builds a throwaway env file: lib.sh requires one, and every value here is fake
# because nothing under test talks to a host.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/env" <<EOF
export AWS_PROFILE=selftest
export AWS_REGION=us-east-2
export KP_AZ=us-east-2b
export KP_SUBNET=subnet-selftest
export KP_VPC=vpc-selftest
export KP_INSTANCE_TYPE=c7g.4xlarge
export KP_AMI=ami-selftest
export KP_KEY_NAME=selftest
export KP_KEY_FILE=$tmp/key.pem
export KP_PREFIX=selftest
export KP_RESULTS=$tmp/results
EOF
KARATE_PROFILING_ENV="$tmp/env"
export KARATE_PROFILING_ENV
# shellcheck disable=SC1091
source "$here/lib.sh"

# A collected matrix: one run parked under a label, one left at the root. Both count.
mkdir -p "$tmp/results/50ms-label/gatling-http-karate-A" "$tmp/results/gatling-http-plain-B"
touch "$tmp/results/50ms-label/gatling-http-karate-A/digest.md" \
      "$tmp/results/gatling-http-plain-B/digest.md"

failures=0
check() {
    local name="$1" expected="$2" actual="$3"
    if [[ "$expected" == "$actual" ]]; then
        printf '  ok   %s\n' "$name"
    else
        printf '  FAIL %s\n         expected: %s\n         actual:   %s\n' "$name" "$expected" "$actual"
        failures=$((failures + 1))
    fi
}

# The caller's severity rule, mirrored here so the test asserts the DECISION rather than
# three variables the reader then has to combine in their head.
verdict() {
    kp_classify_runs "$tmp/results" <<< "$1"
    if [[ -n "$kp_dropped" ]]; then
        echo "FATAL-dropped:$(echo $kp_dropped)"
    elif [[ -z "$kp_pending" ]]; then
        echo "OK"
    elif $kp_busy; then
        echo "NOTE-pending:$(echo $kp_pending)"
    else
        echo "FATAL-pending:$(echo $kp_pending)"
    fi
}

echo "kp_classify_runs:"

check "everything collected, nothing running" \
    "OK" \
    "$(verdict $'HAS gatling-http-karate-A\nHAS gatling-http-plain-B\nIDLE x')"

check "rsync dropped a digest that exists remotely" \
    "FATAL-dropped:gatling-http-karate-MISSING" \
    "$(verdict $'HAS gatling-http-karate-A\nHAS gatling-http-karate-MISSING\nIDLE x')"

# The soak. The run is over, the digest was never written here or there, and the old
# collect.sh reported success — which is what made tearing down look safe.
check "finished run with no digest anywhere is fatal" \
    "FATAL-pending:gatling-http-karate-SOAK" \
    "$(verdict $'HAS gatling-http-karate-A\nNONE gatling-http-karate-SOAK\nIDLE x')"

# The same inventory while a run is alive must NOT fail: collecting between matrices is
# a supported workflow, and a guard that blocks it would be turned off.
check "same state while a Child is alive is only a note" \
    "NOTE-pending:gatling-http-karate-INFLIGHT" \
    "$(verdict $'HAS gatling-http-karate-A\nNONE gatling-http-karate-INFLIGHT\nBUSY x')"

check "empty injector is not a failure" \
    "OK" \
    "$(verdict $'IDLE x')"

# A run whose name is a prefix of another must not satisfy the check for its sibling.
check "prefix names are not confused" \
    "FATAL-dropped:gatling-http-karate-A2" \
    "$(verdict $'HAS gatling-http-karate-A2\nIDLE x')"

echo
if ((failures)); then
    echo "$failures failure(s)"
    exit 1
fi
echo "all checks passed"

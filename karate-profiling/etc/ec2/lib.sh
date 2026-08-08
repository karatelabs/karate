#!/usr/bin/env bash
# Shared settings and helpers for the EC2 bench scripts. Sourced, never run.
#
# Every account-specific value lives in an env file you write yourself and point
# KARATE_PROFILING_ENV at — nothing here contains an account id, an address or a
# key. See docs/PROFILING_EC2.md for the contract and a template.

set -euo pipefail

# Load the operator's env file. Not optional: every one of these scripts creates
# or destroys billable resources, and defaulting the target would be the wrong
# kind of convenient.
: "${KARATE_PROFILING_ENV:?set KARATE_PROFILING_ENV to your private env file — see docs/PROFILING_EC2.md}"
[[ -f "$KARATE_PROFILING_ENV" ]] || { echo "no such env file: $KARATE_PROFILING_ENV" >&2; exit 2; }
# shellcheck disable=SC1090
source "$KARATE_PROFILING_ENV"

for required in AWS_PROFILE AWS_REGION KP_AZ KP_SUBNET KP_VPC KP_INSTANCE_TYPE KP_AMI \
                KP_KEY_NAME KP_KEY_FILE KP_PREFIX KP_RESULTS; do
    : "${!required:?$required is not set in $KARATE_PROFILING_ENV}"
done
export AWS_PROFILE AWS_REGION

KP_SG_NAME="${KP_PREFIX}-sg"
KP_PG_NAME="${KP_PREFIX}-pg"
KP_INJECTOR_NAME="${KP_PREFIX}-injector"
KP_MOCK_NAME="${KP_PREFIX}-mock"
KP_SSH_USER="${KP_SSH_USER:-ec2-user}"

# StrictHostKeyChecking is off because these hosts are rebuilt constantly and
# their keys legitimately change; the addresses are elastic IPs in an account we
# control, so the warning it suppresses carries no information here.
SSH_OPTS=(-i "$KP_KEY_FILE" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null
          -o LogLevel=ERROR -o ServerAliveInterval=30 -o ConnectTimeout=10)

log() { echo "[$(date +%H:%M:%S)] $*"; }
die() { echo "error: $*" >&2; exit 1; }

# The remote half of the completeness guard: emit `HAS <run>` / `NONE <run>` for every
# run directory below the current one. Printed as a script rather than run here, because
# it executes over ssh on the injector — and lives in lib.sh rather than inline in
# collect.sh so selftest.sh can run the EXACT text against a local fixture tree.
#
# It matches on the RUN STAMP, not on a workload prefix. The first version matched
# `gatling-*`, which left every other workload outside the guard — including the suite
# soak, whose two hours produce one artifact and whose whole risk is losing it. Every run
# directory is `<workload>-<yyyy-MM-dd-HHmmss>`, so the stamp covers any workload that
# will ever exist while still excluding a matrix's label directories and jfr-repo/.
kp_inventory_script() {
    cat <<'EOS'
    for d in $(find . -mindepth 1 -maxdepth 2 -type d \
            -name '*-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]' \
            | sed 's|^\./||'); do
        if [ -f "$d/digest.md" ]; then echo "HAS ${d##*/}"; else echo "NONE ${d##*/}"; fi
    done
EOS
}

# Decide, from the injector's run inventory, whether a collection is complete.
#
# Pure: reads the inventory on stdin and sets three variables, so it can be tested
# without a bench — see selftest.sh. That matters more than it looks. The failure this
# encodes produced a SUCCESSFUL collection that silently omitted a one-hour soak's
# digest, and a guard that has never been shown to fire is not a guard.
#
# Input lines, as produced by collect.sh's remote probe:
#   HAS <run>   the injector holds a digest.md for that run
#   NONE <run>  the injector holds the run directory but no digest
#   BUSY x      a Child process is alive, so some run is still being written
#   IDLE x      nothing is running
#
# Sets: kp_dropped (runs whose digest exists there and not here — always fatal),
#       kp_pending (runs with no digest anywhere yet), kp_busy (true/false).
# The caller decides severity, because "no digest yet" is benign mid-matrix and fatal
# when nothing is running.
kp_classify_runs() {
    local results_dir="$1" state run
    kp_dropped=""
    kp_pending=""
    kp_busy=false
    while read -r state run; do
        case "$state" in
            BUSY) kp_busy=true ;;
            HAS)
                # -name, not a fixed path: a matrix parks its runs under a label, and an
                # unparked run sits at the root. Either location counts as collected.
                if ! find "$results_dir" -type d -name "$run" \
                        -exec test -f '{}/digest.md' \; -print -quit 2>/dev/null | grep -q .; then
                    kp_dropped="${kp_dropped}  $run"$'\n'
                fi
                ;;
            NONE) kp_pending="${kp_pending}  $run"$'\n' ;;
        esac
    done
}

# The public IP of a named instance, or empty. Filters on running/pending only,
# so a terminated instance from a previous cycle never answers.
kp_ip_of() {
    aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=$1" "Name=instance-state-name,Values=running,pending" \
        --query 'Reservations[].Instances[0].PublicIpAddress' --output text 2>/dev/null | grep -v '^None$' || true
}

kp_id_of() {
    aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=$1" "Name=instance-state-name,Values=running,pending" \
        --query 'Reservations[].Instances[0].InstanceId' --output text 2>/dev/null | grep -v '^None$' || true
}

# Every instance this bench has ever tagged that is not already terminated — including
# stopped and stopping ones, which kp_id_of deliberately ignores and teardown must not.
# A stopped instance bills its EBS volume forever, blocks the security group from
# deleting, and is invisible to a "nothing is running" check that filters on running.
kp_all_alive() {
    aws ec2 describe-instances \
        --filters "Name=tag:$KP_PREFIX,Values=true" \
        "Name=instance-state-name,Values=running,pending,stopping,stopped,shutting-down" \
        --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null | tr '\t' '\n' | grep -v '^None$' || true
}

# Private address — what the injector actually talks to. Traffic between two
# instances in one AZ should not leave via the public interface: it would add a
# NAT hop to every connection and bill for it, on the one axis (connection
# setup) where the two arms already differ.
kp_private_ip_of() {
    aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=$1" "Name=instance-state-name,Values=running,pending" \
        --query 'Reservations[].Instances[0].PrivateIpAddress' --output text 2>/dev/null | grep -v '^None$' || true
}

kp_ssh() {
    local host="$1"; shift
    ssh "${SSH_OPTS[@]}" "${KP_SSH_USER}@${host}" "$@"
}

kp_wait_ssh() {
    local host="$1" tries="${2:-60}"
    for ((i = 1; i <= tries; i++)); do
        if kp_ssh "$host" true 2>/dev/null; then
            return 0
        fi
        sleep 5
    done
    die "ssh to $host never came up"
}

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

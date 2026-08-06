#!/usr/bin/env bash
# ssh to a bench host by role, or run a command on it.
#
#   etc/ec2/ssh.sh injector
#   etc/ec2/ssh.sh mock 'nproc; ulimit -n'
#   etc/ec2/ssh.sh injector-ip          # just print the address, for scripting
source "$(dirname "$0")/lib.sh"

role="${1:?usage: ssh.sh injector|mock|injector-ip|mock-ip|mock-private [command...]}"
shift || true

case "$role" in
    injector)     host="$(kp_ip_of "$KP_INJECTOR_NAME")" ;;
    mock)         host="$(kp_ip_of "$KP_MOCK_NAME")" ;;
    injector-ip)  kp_ip_of "$KP_INJECTOR_NAME"; exit 0 ;;
    mock-ip)      kp_ip_of "$KP_MOCK_NAME"; exit 0 ;;
    mock-private) kp_private_ip_of "$KP_MOCK_NAME"; exit 0 ;;
    *) die "unknown role: $role" ;;
esac

[[ -n "$host" ]] || die "$role is not running"

if (($#)); then
    kp_ssh "$host" "$@"
else
    ssh "${SSH_OPTS[@]}" "${KP_SSH_USER}@${host}"
fi

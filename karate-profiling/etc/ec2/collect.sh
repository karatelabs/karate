#!/usr/bin/env bash
# Pull digests off the injector into $KP_RESULTS, then derive the table locally.
#
#   etc/ec2/collect.sh            # digests + run-meta + mock.log only
#   etc/ec2/collect.sh --all      # ...plus stdout.log and any heap dumps
#
# Recordings are NOT pulled by default. A run.jfr is up to 512 MB, the digest is
# kilobytes, and the digest is the durable artifact — see PROFILING.md on disk
# hygiene. Pull a .jfr by hand when you actually intend to run jfr recipes on it.
source "$(dirname "$0")/lib.sh"

want_all=false
[[ "${1:-}" == "--all" ]] && want_all=true

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
[[ -n "$injector_ip" ]] || die "injector is not running"

mkdir -p "$KP_RESULTS"

includes=(--include '*/' --include 'digest.md' --include 'run-meta.txt' --include 'mock.log')
$want_all && includes+=(--include 'stdout.log' --include '*.hprof')

log "pulling digests to $KP_RESULTS"
rsync -az "${includes[@]}" --exclude '*' \
    -e "ssh ${SSH_OPTS[*]}" \
    "${KP_SSH_USER}@${injector_ip}:karate/karate-profiling/target/profiling/" "$KP_RESULTS/"

count="$(find "$KP_RESULTS" -name digest.md | wc -l | tr -d ' ')"
log "$count digests in $KP_RESULTS"

# Derive the table from the local checkout — the tool reads digest.md and nothing
# else, so it does not care that these runs happened on another machine.
if compgen -G "$KP_RESULTS/gatling-http-*" >/dev/null; then
    log "deriving the parity table"
    "$(dirname "$0")/../run.sh" compare "$KP_RESULTS"/gatling-http-*
fi

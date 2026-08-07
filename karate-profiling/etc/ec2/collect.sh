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
# One table per labelled matrix, and one for anything unlabelled. Never one
# table across all of them: `compare` pairs by timestamp adjacency, so a single
# invocation spanning two matrices would pair the last run of one with the first
# of the next and report two experiments as one.
# The tables are a convenience, NOT the collection. `compare` exits non-zero for
# perfectly good reasons — duration-bounded runs cannot be paired, and a soak
# directory contains nothing but those — and its status used to become this
# script's, so a collection that copied everything correctly reported failure.
# That is the wrong way round and trains you to ignore the exit code, which is
# the one that matters the day rsync fails. The copy above decides the outcome.
derive() {
    local dir="$1" name="$2"
    compgen -G "$dir/gatling-http-*" >/dev/null || return 0
    echo
    log "=== $name ==="
    "$(dirname "$0")/../run.sh" compare "$dir"/gatling-http-* || \
        log "   (no table — compare declined these runs; the digests are collected regardless)"
}

for labelled in "$KP_RESULTS"/*/; do
    [[ -d "$labelled" ]] || continue
    compgen -G "$labelled/gatling-http-*" >/dev/null || continue
    derive "${labelled%/}" "$(basename "$labelled")"
done
derive "$KP_RESULTS" "unlabelled"

# Explicit, so the last `derive` cannot decide it.
exit 0 

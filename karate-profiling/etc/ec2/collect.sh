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

includes=(--include '*/' --include 'digest.md' --include 'run-meta.txt' --include 'mock.log'
          --include 'calibration-*.txt')
$want_all && includes+=(--include 'stdout.log' --include '*.hprof')

log "pulling digests to $KP_RESULTS"
rsync -az "${includes[@]}" --exclude '*' \
    -e "ssh ${SSH_OPTS[*]}" \
    "${KP_SSH_USER}@${injector_ip}:karate/karate-profiling/target/profiling/" "$KP_RESULTS/"

count="$(find "$KP_RESULTS" -name digest.md | wc -l | tr -d ' ')"
log "$count digests in $KP_RESULTS"

# --- completeness, and why a count cannot decide it --------------------------
# The parent writes digest.md AFTER the child exits. A collection triggered by the
# child going away therefore copies run-meta.txt and mock.log, finds every file rsync
# was asked for, and returns 0 having missed the one artifact the run existed to
# produce. That happened: a one-hour soak collected clean and arrived with no digest.
# The total printed above cannot catch it either — hundreds of older digests
# comfortably conceal one missing new one. So compare SETS, not counts.
#
# Two conditions, deliberately not conflated:
#
#   1. a digest exists on the injector but not here -> the copy dropped it. Always
#      fatal; it cannot be a timing artifact, because the file is already written.
#   2. a run directory on the injector has no digest at all -> either it is still
#      being written (benign — collecting mid-matrix is a supported workflow), or its
#      parent finished and never wrote one (the soak case). A live `Child` process is
#      what separates those, so that is what gets tested rather than guessed from names.
remote_state="$(kp_ssh "$injector_ip" '
    cd karate/karate-profiling/target/profiling 2>/dev/null || exit 0
    for d in $(find . -mindepth 1 -maxdepth 2 -type d -name "gatling-*" | sed "s|^\./||"); do
        if [ -f "$d/digest.md" ]; then echo "HAS ${d##*/}"; else echo "NONE ${d##*/}"; fi
    done
    # Bracketed so the ssh command line carrying the pattern cannot match itself.
    if ps -eo cmd | grep -q "[C]hild"; then echo "BUSY x"; else echo "IDLE x"; fi' || true)"

kp_classify_runs "$KP_RESULTS" <<< "$remote_state"

if [[ -n "$kp_dropped" ]]; then
    log "!! the injector has a digest.md for these runs and this machine does not:"
    printf '%s' "$kp_dropped"
    die "the copy is incomplete - do NOT tear down; re-run collect.sh"
fi

if [[ -z "$kp_pending" ]]; then
    log "every run on the injector has its digest here"
elif $kp_busy; then
    log "note: a run is in flight, so these have no digest yet - expected:"
    printf '%s' "$kp_pending"
    log "      re-run collect.sh once it finishes, and before tearing down."
else
    log "!! nothing is running, yet these run directories have no digest.md at all:"
    printf '%s' "$kp_pending"
    log "   The parent writes the digest AFTER the child exits, so a run that just ended"
    log "   may need a few more seconds - re-run collect.sh. If it persists, the parent"
    log "   died and the run is unrecoverable once the host is gone."
    die "refusing to report success - do NOT tear down until this is understood"
fi

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
    compgen -G "$dir/gatling-*" >/dev/null || return 0
    echo
    log "=== $name ==="
    "$(dirname "$0")/../run.sh" compare "$dir"/gatling-* || \
        log "   (no table — compare declined these runs; the digests are collected regardless)"
}

for labelled in "$KP_RESULTS"/*/; do
    [[ -d "$labelled" ]] || continue
    compgen -G "$labelled/gatling-*" >/dev/null || continue
    derive "${labelled%/}" "$(basename "$labelled")"
done
derive "$KP_RESULTS" "unlabelled"

# Explicit, so the last `derive` cannot decide it.
exit 0 

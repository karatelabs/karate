#!/usr/bin/env bash
# Drive a karate-js A/B matrix on the injector: two builds of the karate-js jar, the js-*
# workload rows, alternating pairs. Single host, no mock — the js family is in-JVM.
#
#   jar_a=$(etc/js-arm.sh <base-ref>)          # A = base
#   jar_b=$(etc/js-arm.sh <candidate-ref>)     # B = candidate
#   etc/ec2/js-matrix.sh --jar-a "$jar_a" --jar-b "$jar_b" --pairs 4 --label my-ab
#
# Protocol notes, all deliberate:
#   - every run is --no-jfr: recording cost tracks allocation rate, so two builds that
#     allocate differently pay it differently. Diagnose with a separate JFR-on matrix.
#   - --pairs defaults to 4 and odd counts are warned about: alternation cancels linear
#     drift over an even count only (odd counts leave one arm ahead by half a run slot).
#   - every run carries --run-tag <label>:p<N>:<a|b>; `compare` pairs by tag, never by
#     timestamp adjacency, so a failed run orphans its cell instead of shifting the pairing.
#   - jars are rsynced with their manifests and re-hashed on the injector before anything
#     runs; a transfer that corrupted a jar fails here, not as a mislabelled result.
#   - one discarded warmup run (arm A, first row) settles page cache and cpufreq before
#     the first measured cell; each child JVM still does its own in-process warmup.
source "$(dirname "$0")/lib.sh"

jar_a=
jar_b=
pairs=4
gap=10
label=
rows=(js-arithmetic js-strings js-objects js-functions js-mixed js-large-1k)
sysprops_a=()
sysprops_b=()
iterations=
warmup=
quick=false

while (($#)); do
    case "$1" in
        --jar-a)  jar_a="$2"; shift 2 ;;
        --jar-b)  jar_b="$2"; shift 2 ;;
        --pairs)  pairs="$2"; shift 2 ;;
        --gap)    gap="$2"; shift 2 ;;
        --label)  label="$2"; shift 2 ;;
        # Comma-separated subset of the six rows, for a rehearsal or a targeted probe.
        --rows)   IFS=',' read -r -a rows <<<"$2"; shift 2 ;;
        # Override every run's shape. For rehearsals; a decision matrix runs the workload
        # defaults, which are sized for a stable measured window.
        --iterations) iterations="$2"; shift 2 ;;
        --warmup)     warmup="$2"; shift 2 ;;
        # The ~90-second plumbing check: one row, one pair, tiny window, short gaps. It
        # proves the whole path — arm shipping, hash verification, tags, digests, parking —
        # and its numbers are startup-shaped garbage, which `compare` also says. Iterate on
        # the harness against THIS (or fully locally, see docs/PROFILING.md), never against
        # a full matrix.
        --quick)  quick=true; shift ;;
        # Per-arm -Dkey=value, repeatable. This is how a flag-equivalence cell runs: the
        # candidate jar with its feature flag off against the base jar, expecting ~0 —
        # without any feature-specific code in this harness.
        --sysprop-a) sysprops_a+=("-D$2"); shift 2 ;;
        --sysprop-b) sysprops_b+=("-D$2"); shift 2 ;;
        *) die "unknown flag: $1" ;;
    esac
done

if $quick; then
    pairs=1
    rows=(js-functions)
    iterations="${iterations:-2000}"
    warmup="${warmup:-2s}"
    gap=3
    log "QUICK mode: 1 pair, ${rows[*]}, $iterations iterations — plumbing check, not a measurement"
fi
[[ -z "$iterations" || "$iterations" != *[!0-9]* ]] || die "--iterations must be a number: $iterations"
[[ -z "$warmup" || "$warmup" != *[!0-9smh]* ]] || die "--warmup must look like 2s/1m: $warmup"

[[ -n "$jar_a" && -n "$jar_b" ]] || die "--jar-a and --jar-b are required (build with etc/js-arm.sh <ref>)"
[[ -f "$jar_a" && -f "$jar_b" ]] || die "arm jar missing: $jar_a / $jar_b"
[[ -f "$jar_a.manifest" && -f "$jar_b.manifest" ]] \
    || die "arm manifest missing — rebuild with etc/js-arm.sh so provenance is verifiable"
[[ -n "$label" ]] || die "--label is required: it names the matrix, parks its runs, and is the run-tag prefix"
[[ "$label" == *[!A-Za-z0-9._-]* ]] && die "--label must be [A-Za-z0-9._-] only (it travels inside run tags): $label"
# Rows and sysprops are interpolated into a remote shell command, so their alphabet is a
# correctness constraint, not pedantry — a value with whitespace or a metacharacter would
# split into extra remote arguments or worse.
for row in "${rows[@]}"; do
    [[ "$row" == *[!a-z0-9-]* || "$row" != js-* ]] \
        && die "--rows entries must be js-* workload names ([a-z0-9-]), got: $row"
done
for prop in "${sysprops_a[@]:-}" "${sysprops_b[@]:-}"; do
    [[ -n "$prop" && "$prop" == *[!A-Za-z0-9._=-]* ]] \
        && die "--sysprop values must be [A-Za-z0-9._=-] only (they cross a remote shell): $prop"
done
if ((pairs % 2 == 1)); then
    log "WARNING: $pairs pairs is odd — alternation cancels linear drift over an even count only"
fi

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
[[ -n "$injector_ip" ]] || die "injector is not running — etc/ec2/provision.sh (--single is enough)"

# A reused label would interleave two matrices under one name — the exact hazard labels
# exist to prevent — so it is refused, not warned about.
kp_ssh "$injector_ip" "[[ ! -e ~/karate/karate-profiling/target/profiling/$label ]]" \
    || die "label '$label' already exists on the injector — pick a fresh one"

# --- ship the arms, verify the bytes ------------------------------------------------------
log "shipping arm jars to the injector"
rsync -az -e "ssh ${SSH_OPTS[*]}" "$jar_a" "$jar_a.manifest" "$jar_b" "$jar_b.manifest" \
    "${KP_SSH_USER}@${injector_ip}:karate/karate-profiling/target/js-arms/" \
    --rsync-path="mkdir -p karate/karate-profiling/target/js-arms && rsync"

verify_remote() {
    local jar_base declared remote
    jar_base="$(basename "$1")"
    declared="$(grep '^sha256:' "$1.manifest" | awk '{print $2}')"
    remote="$(kp_ssh "$injector_ip" "sha256sum ~/karate/karate-profiling/target/js-arms/$jar_base | cut -d' ' -f1")"
    [[ -n "$declared" && "$declared" == "$remote" ]] \
        || die "transfer verification failed for $jar_base: manifest $declared vs remote ${remote:-<none>}"
    log "  $jar_base verified on the injector ($declared)"
}
verify_remote "$jar_a"
verify_remote "$jar_b"

remote_jar_a="target/js-arms/$(basename "$jar_a")"
remote_jar_b="target/js-arms/$(basename "$jar_b")"

# --- run one cell --------------------------------------------------------------------------
failures=0

run_cell() {
    local row="$1" arm="$2" tag="$3" jar extra out
    if [[ "$arm" == a ]]; then jar="$remote_jar_a"; extra="${sysprops_a[*]:-}"; else jar="$remote_jar_b"; extra="${sysprops_b[*]:-}"; fi
    if ! out="$(kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && PROFILING_SKIP_BUILD=1 \
            etc/run.sh $row --no-jfr --js-jar $jar --run-tag $tag \
            ${iterations:+--iterations $iterations} ${warmup:+--warmup $warmup} $extra 2>&1")"; then
        log "  !! $row [$arm] FAILED"
        echo "$out" | tail -5 | sed 's/^/     /'
        failures=$((failures + 1))
        return 0
    fi
    if ! grep -q '^\[parent\] digest:' <<<"$out"; then
        log "  !! $row [$arm] produced no digest"
        echo "$out" | tail -5 | sed 's/^/     /'
        failures=$((failures + 1))
        return 0
    fi
    grep '^\[parent\] digest:' <<<"$out" | tail -1 | sed 's/^/  /'
}

# Marker: everything created after this belongs to this matrix (same discipline as matrix.sh —
# a warmup that dies must not delete a previous session's newest run).
marker=".js-matrix-start-$$"
kp_ssh "$injector_ip" "mkdir -p ~/karate/karate-profiling/target/profiling && touch ~/karate/karate-profiling/target/profiling/$marker"

log "one discarded warmup run (${rows[0]}, arm a)"
warmup_failures_before=$failures
run_cell "${rows[0]}" a "$label:warmup:a" >/dev/null 2>&1 || true
failures=$warmup_failures_before
kp_ssh "$injector_ip" "cd ~/karate/karate-profiling/target/profiling && \
    victim=\$(find . -maxdepth 1 -name 'js-*' -newer '$marker' | sort | tail -1) && \
    [[ -n \"\$victim\" ]] && rm -rf \"\$victim\" && echo \"discarded \$victim\" || \
    echo 'warmup produced no run directory — nothing discarded'"
sleep "$gap"

total_runs=$((${#rows[@]} * pairs * 2))
log "$pairs pairs x ${#rows[@]} rows = $total_runs runs, ${gap}s gaps — A=$(basename "$jar_a") B=$(basename "$jar_b")"
for row in "${rows[@]}"; do
    for ((pair = 1; pair <= pairs; pair++)); do
        # Alternate which arm leads, so drift over the matrix loads both arms equally.
        if ((pair % 2 == 1)); then first=a; second=b; else first=b; second=a; fi
        log "$row pair $pair/$pairs: $first then $second"
        run_cell "$row" "$first" "$label:p$pair:$first"
        sleep "$gap"
        run_cell "$row" "$second" "$label:p$pair:$second"
        sleep "$gap"
    done
done

if [[ $failures -gt 0 ]]; then
    # Loud and non-zero — but the completed cells still get parked under the label rather
    # than left loose: tag-based pairing means the intact pairs are still usable, and loose
    # runs would otherwise land in the next collection as an unlabelled experiment.
    log "!! $failures run(s) failed — those cells are broken; the tagged pairs that completed are still valid"
fi

log "parking this matrix's runs under $label/"
# The label directory itself matches a js-* glob when the label starts with js-, so it is
# excluded by name — without that, mv tries to park the label inside itself. And a parking
# failure (an ssh blip, under lib.sh's set -e) must not abort the script silently: the runs
# are tag-paired either way, so they stay derivable — what is lost is only the labelling,
# and that is worth a loud note, not a dead script with no manifest.
if ! kp_ssh "$injector_ip" "cd ~/karate/karate-profiling/target/profiling
    mkdir -p '$label'
    find . -maxdepth 1 -name 'js-*' ! -name '$label' -newer '$marker' -exec mv {} '$label'/ \;
    rm -f '$marker'
    echo \"  \$(ls -1d '$label'/js-* 2>/dev/null | wc -l) runs in $label/\""; then
    log "!! parking failed — the runs remain loose in target/profiling but are tag-paired;"
    log "   collect.sh will still derive them (as unlabelled). Re-run the parking by hand."
fi

# The durable description of what this matrix WAS — arms, rows, shape, host — so the label
# directory that comes home is self-describing even months later. Failure tolerated for the
# same reason as parking above.
kp_ssh "$injector_ip" "cat > ~/karate/karate-profiling/target/profiling/$label/matrix-manifest.txt" <<MANIFEST \
    || log "!! matrix-manifest write failed — record the arms by hand from the digests"
label:      $label
arm a:      $(basename "$jar_a")  $(grep '^commit:' "$jar_a.manifest")  $(grep '^sha256:' "$jar_a.manifest")
arm b:      $(basename "$jar_b")  $(grep '^commit:' "$jar_b.manifest")  $(grep '^sha256:' "$jar_b.manifest")
sysprops a: ${sysprops_a[*]:-(none)}
sysprops b: ${sysprops_b[*]:-(none)}
rows:       ${rows[*]}
pairs:      $pairs
gap:        ${gap}s
shape:      $($quick && printf 'QUICK — plumbing check, not a measurement; ')${iterations:+iterations=$iterations }${warmup:+warmup=$warmup }$([ -z "$iterations$warmup" ] && printf 'workload defaults')
jfr:        off (timing matrix)
instance:   ${KP_INSTANCE_TYPE:-?} in ${KP_AZ:-?}
started:    $(date -u +%Y-%m-%dT%H:%M:%SZ)
failures:   $failures
MANIFEST

if [[ $failures -gt 0 ]]; then
    exit 1
fi
log "done — etc/ec2/collect.sh to pull the digests and derive the table"

#!/usr/bin/env bash
# Drive a js A/B matrix on the injector: two arms over the js-* workload rows, alternating
# pairs. Single host, no mock — the js family is in-JVM. An arm is either a karate-js jar
# (--jar-X, built by etc/js-arm.sh — the karate arm's identity mechanism) or a named engine
# (--engine-X, currently `rhino-best` only — the R1 head-to-head reference arm, whose
# identity is engine + Rhino version + resolved-jar sha256, recorded by the harness in each
# digest's run-meta).
#
#   jar_a=$(etc/js-arm.sh <base-ref>)          # A = base
#   jar_b=$(etc/js-arm.sh <candidate-ref>)     # B = candidate
#   etc/ec2/js-matrix.sh --jar-a "$jar_a" --jar-b "$jar_b" --pairs 4 --label my-ab
#
#   # the R1 lane: cross-engine head-to-head, and its two null controls
#   etc/ec2/js-matrix.sh --jar-a "$jar" --engine-b rhino-best --pairs 4 --label r1-h2h
#   etc/ec2/js-matrix.sh --jar-a "$jar" --jar-b "$jar"                 --label r1-null-karate
#   etc/ec2/js-matrix.sh --engine-a rhino-best --engine-b rhino-best   --label r1-null-rhino
#
# Engine arms need the bench build rhino-ready: bootstrap.sh builds the profiling module
# with -Prhino (adapter compiled, pinned Rhino jar in ~/.m2 and on target/cp.txt). The
# parent refuses an engine run against a classpath built without it, so a stale host fails
# loudly rather than measuring the wrong thing.
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
engine_a=
engine_b=
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
        # An engine arm instead of a jar arm — see the header. Validated below to the one
        # name the harness accepts, so nothing unvetted crosses the remote shell.
        --engine-a) engine_a="$2"; shift 2 ;;
        --engine-b) engine_b="$2"; shift 2 ;;
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

# Each arm is exactly one identity: a jar or an engine, never both, never neither. The
# Profiler refuses --engine with --js-jar too; the matrix refuses it first, before anything
# ships. The only engine name accepted is the one the harness knows — it travels into a
# remote shell command, so the closed set is a correctness constraint as well as a UX one.
for arm in a b; do
    jar_var="jar_$arm" eng_var="engine_$arm"
    [[ -n "${!jar_var}" && -n "${!eng_var}" ]] \
        && die "--jar-$arm and --engine-$arm are mutually exclusive — an arm is one identity"
    [[ -z "${!jar_var}" && -z "${!eng_var}" ]] \
        && die "arm $arm needs --jar-$arm (build with etc/js-arm.sh <ref>) or --engine-$arm rhino-best"
    [[ -n "${!eng_var}" && "${!eng_var}" != rhino-best ]] \
        && die "--engine-$arm must be rhino-best (a karate arm's identity is a jar, via --jar-$arm): ${!eng_var}"
done
for j in "$jar_a" "$jar_b"; do
    [[ -z "$j" ]] && continue
    [[ -f "$j" ]] || die "arm jar missing: $j"
    [[ -f "$j.manifest" ]] \
        || die "arm manifest missing for $j — rebuild with etc/js-arm.sh so provenance is verifiable"
done
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

# Engine cells fail-closed at the parent, but only per cell — a stale classpath (an
# ordinary non-skip run.sh on the injector rebuilds cp.txt without -Prhino) would let every
# karate cell run before the first engine cell died, wasting the matrix. Probe once, up
# front: the pinned Rhino jar on the child classpath and the compiled adapter class, so a
# stale host aborts before anything measured.
if [[ -n "$engine_a$engine_b" ]]; then
    kp_ssh "$injector_ip" "grep -q 'org/mozilla/rhino' ~/karate/karate-profiling/target/cp.txt \
            && [[ -f ~/karate/karate-profiling/target/classes/io/karatelabs/profiling/rhino/RhinoBest.class ]]" \
        || die "the injector is not rhino-ready (cp.txt without the Rhino jar, or the adapter is not compiled) — re-run etc/ec2/bootstrap.sh --rebuild"
fi

# --- ship the arms, verify the bytes ------------------------------------------------------
# Only jar arms ship anything; an engine arm's runtime is resolved on the injector from the
# pom's pin, and the parent hashes it into the digest — an engine-only matrix rsyncs nothing.
ship=()
[[ -n "$jar_a" ]] && ship+=("$jar_a" "$jar_a.manifest")
[[ -n "$jar_b" && "$jar_b" != "$jar_a" ]] && ship+=("$jar_b" "$jar_b.manifest")
if ((${#ship[@]} > 0)); then
    log "shipping arm jars to the injector"
    rsync -az -e "ssh ${SSH_OPTS[*]}" "${ship[@]}" \
        "${KP_SSH_USER}@${injector_ip}:karate/karate-profiling/target/js-arms/" \
        --rsync-path="mkdir -p karate/karate-profiling/target/js-arms && rsync"
fi

verify_remote() {
    local jar_base declared remote
    jar_base="$(basename "$1")"
    declared="$(grep '^sha256:' "$1.manifest" | awk '{print $2}')"
    remote="$(kp_ssh "$injector_ip" "sha256sum ~/karate/karate-profiling/target/js-arms/$jar_base | cut -d' ' -f1")"
    [[ -n "$declared" && "$declared" == "$remote" ]] \
        || die "transfer verification failed for $jar_base: manifest $declared vs remote ${remote:-<none>}"
    log "  $jar_base verified on the injector ($declared)"
}
[[ -n "$jar_a" ]] && verify_remote "$jar_a"
[[ -n "$jar_b" ]] && verify_remote "$jar_b"

remote_jar_a=; [[ -n "$jar_a" ]] && remote_jar_a="target/js-arms/$(basename "$jar_a")"
remote_jar_b=; [[ -n "$jar_b" ]] && remote_jar_b="target/js-arms/$(basename "$jar_b")"

# What each arm IS, for logs and the manifest — a jar basename or an engine name.
arm_desc_a="${engine_a:+engine=$engine_a}"; [[ -n "$jar_a" ]] && arm_desc_a="$(basename "$jar_a")"
arm_desc_b="${engine_b:+engine=$engine_b}"; [[ -n "$jar_b" ]] && arm_desc_b="$(basename "$jar_b")"

# --- run one cell --------------------------------------------------------------------------
failures=0

run_cell() {
    local row="$1" arm="$2" tag="$3" armflag extra out
    # A jar arm identifies itself with --js-jar, an engine arm with --engine — the two are
    # mutually exclusive at the Profiler too, and validation above guarantees exactly one.
    if [[ "$arm" == a ]]; then
        if [[ -n "$engine_a" ]]; then armflag="--engine $engine_a"; else armflag="--js-jar $remote_jar_a"; fi
        extra="${sysprops_a[*]:-}"
    else
        if [[ -n "$engine_b" ]]; then armflag="--engine $engine_b"; else armflag="--js-jar $remote_jar_b"; fi
        extra="${sysprops_b[*]:-}"
    fi
    if ! out="$(kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && PROFILING_SKIP_BUILD=1 \
            etc/run.sh $row --no-jfr $armflag --run-tag $tag \
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
log "$pairs pairs x ${#rows[@]} rows = $total_runs runs, ${gap}s gaps — A=$arm_desc_a B=$arm_desc_b"
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
arm a:      $(if [[ -n "$jar_a" ]]; then echo "$(basename "$jar_a")  $(grep '^commit:' "$jar_a.manifest")  $(grep '^sha256:' "$jar_a.manifest")"; else echo "engine=$engine_a  (identity: engine + runtime version + resolved-jar sha256, in each digest's run-meta)"; fi)
arm b:      $(if [[ -n "$jar_b" ]]; then echo "$(basename "$jar_b")  $(grep '^commit:' "$jar_b.manifest")  $(grep '^sha256:' "$jar_b.manifest")"; else echo "engine=$engine_b  (identity: engine + runtime version + resolved-jar sha256, in each digest's run-meta)"; fi)
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

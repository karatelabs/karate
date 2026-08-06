#!/usr/bin/env bash
# Drive parity pairs across the two hosts: the mock on one, the injector on the
# other, alternating which arm leads so drift cancels between the arms.
#
#   etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8
#   etc/ec2/matrix.sh --tier 50ms --pairs 10 --iterations 1600 --users 8
#
# Each pair is two runs; each run writes a digest on the injector. Pull them with
# etc/ec2/collect.sh and derive the table with `etc/run.sh compare`.
source "$(dirname "$0")/lib.sh"

tier=10ms
pairs=3
iterations=4000
users=8
# TIME_WAIT is 60s on Linux, but the bench sets tcp_tw_reuse=1 and a 64k port
# range, so the laptop's 35s convention is no longer load-bearing here. Kept as a
# knob rather than removed: the gap is also what lets a previous run's sockets
# drain, and `since prev:` in run-meta.txt records what each run actually got.
gap=20
mock_port=8090
local_mock=false
label=

while (($#)); do
    case "$1" in
        --tier)       tier="$2"; shift 2 ;;
        --pairs)      pairs="$2"; shift 2 ;;
        --iterations) iterations="$2"; shift 2 ;;
        --users)      users="$2"; shift 2 ;;
        --gap)        gap="$2"; shift 2 ;;
        --port)       mock_port="$2"; shift 2 ;;
        # Fork the mock on the injector itself, as a single-host run does. This is
        # the CONTROL for the whole two-host phase: same machine, same JDK, same
        # commit, and the only thing that changes is whether the mock shares the
        # cores and whether connection setup crosses a network. Without it, any
        # difference from the laptop's numbers is confounded three ways —
        # architecture, OS, and co-location — and cannot be attributed to any of
        # them. Run one of these against every tier you publish.
        --local-mock) local_mock=true; shift ;;
        # Park this matrix's runs in their own subdirectory when it finishes.
        # NOT cosmetic: `compare` pairs runs by timestamp adjacency, so two
        # matrices sharing a directory interleave — the last run of one pairs
        # with the first of the next, and a table of two different experiments
        # is reported as one. Always label when running more than one matrix.
        --label)      label="$2"; shift 2 ;;
        *) die "unknown flag: $1" ;;
    esac
done

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"
[[ -n "$injector_ip" && -n "$mock_private" ]] || die "hosts not up — run etc/ec2/provision.sh"

# --- the mock ----------------------------------------------------------------
if $local_mock; then
    # The harness forks its own mock per run, exactly as a single-host run does.
    mock_args="--mock-latency $tier"
    log "CONTROL: mock forked on the injector, co-located"
else
    # Restarted for every matrix, because the tier is fixed at construction — see
    # mock.sh for why a leftover mock is worse than no mock.
    "$(dirname "$0")/mock.sh" start "$tier" "$mock_port"
    mock_url="http://${mock_private}:${mock_port}"
    kp_ssh "$injector_ip" "curl -sf --max-time 5 $mock_url/config >/dev/null" \
        || die "injector cannot reach the mock at $mock_url — check the security group"
    log "injector reaches the mock at $mock_url"
    mock_args="--mock-url $mock_url"
fi

# --- the pairs ---------------------------------------------------------------
failures=0

run_arm() {
    local arm="$1" out
    # Capture rather than stream, so a failed run is visible as a missing digest
    # line instead of merely producing no output. A matrix that quietly drops an
    # arm leaves an odd run count, and `compare` then pairs run N with run N+2 —
    # two arms from different pairs, reported as one.
    if ! out="$(kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && PROFILING_SKIP_BUILD=1 \
            etc/run.sh gatling-http-$arm --iterations $iterations --threads $users \
            $mock_args 2>&1")"; then
        log "  !! $arm FAILED"
        echo "$out" | tail -5 | sed 's/^/     /'
        failures=$((failures + 1))
        return 0
    fi
    if ! grep -q '^\[parent\] digest:' <<<"$out"; then
        log "  !! $arm produced no digest"
        echo "$out" | tail -5 | sed 's/^/     /'
        failures=$((failures + 1))
        return 0
    fi
    grep '^\[parent\] digest:' <<<"$out" | tail -1 | sed 's/^/  /'
}

# Marker: everything created after this belongs to this matrix.
marker=".matrix-start-$$"
kp_ssh "$injector_ip" "mkdir -p ~/karate/karate-profiling/target/profiling && touch ~/karate/karate-profiling/target/profiling/$marker"

# Discard one run against the freshly started mock. Its JVM is still JIT-compiling:
# measured, the first run against a cold mock showed the mock burning 3.86 core-s and
# a service p99 of 231 us against 0.70 and 10 us for every later run, and the arm that
# absorbs that lands ~0.5 ms/iteration slower. Because pair 1 always leads with karate,
# the bias was structural and always against Karate — it inflated a 10-pair mean from
# +1.79 to +1.84 and tripled its standard deviation on its own.
log "warming the mock (one discarded run)"
run_arm karate >/dev/null 2>&1 || true
kp_ssh "$injector_ip" "cd ~/karate/karate-profiling/target/profiling && rm -rf \$(ls -1dt gatling-http-* | head -1)"
sleep "$gap"

log "$pairs pairs at $tier — $iterations iterations, $users users, ${gap}s between runs"
for ((pair = 1; pair <= pairs; pair++)); do
    # Alternate which arm leads. Any drift over the matrix — thermal, neighbour,
    # the mock's own sleep overshoot — then loads the two arms equally instead of
    # accumulating against one of them.
    if ((pair % 2 == 1)); then first=karate; second=plain; else first=plain; second=karate; fi
    log "pair $pair/$pairs: $first then $second"
    run_arm "$first"
    sleep "$gap"
    run_arm "$second"
    ((pair < pairs)) && sleep "$gap"
done

if [[ $failures -gt 0 ]]; then
    # Loud, and non-zero: an incomplete matrix must not read as a complete one.
    # Delete the orphaned run before collecting, or `compare` will pair across
    # the gap and report two arms from different pairs as one.
    log "!! $failures run(s) failed — the pairing is broken, not just short"
    exit 1
fi

if [[ -n "$label" ]]; then
    log "parking this matrix's runs under $label/"
    kp_ssh "$injector_ip" "cd ~/karate/karate-profiling/target/profiling
        mkdir -p '$label'
        find . -maxdepth 1 -name 'gatling-http-*' -newer '$marker' -exec mv {} '$label'/ \;
        rm -f '$marker'
        echo \"  \$(ls -1d '$label'/gatling-http-* | wc -l) runs in $label/\""
else
    kp_ssh "$injector_ip" "rm -f ~/karate/karate-profiling/target/profiling/$marker"
fi

log "done — etc/ec2/collect.sh to pull the digests"

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
pooled=false
label=
# Which workload each arm runs: `<family>-<arm>`. --control swaps exactly one of them,
# and --body-size switches the family, because the body tier has its own workloads.
karate_arm=karate
plain_arm=plain
family=gatling-http
body_size=
control=
tls=false

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
        # Give the KARATE arm a shared connection pool, so it stops opening a
        # connection per iteration. Applies to the karate arm only, deliberately:
        # the plain arm already keep-alives, so pooling both would change nothing
        # on one side and the pair would still be a valid parity cell. That keeps
        # the resulting table directly comparable to an unpooled matrix at the
        # same tier — which is the actual A/B, since `compare` pairs plain against
        # karate and cannot pair two karate arms against each other.
        --pooled)     pooled=true; shift ;;
        # Serve the tier over TLS. The headline experiment for public endpoints: a saved
        # handshake is worth 2 RTT plus asymmetric crypto here, against ~0.1 ms for a plaintext
        # connection inside a placement group — which is why the plaintext A/B could only ever
        # measure pooling's floor. Both arms trust the mock's self-signed cert, so what differs
        # between them is the handshake and not the trust configuration.
        --tls)        tls=true; shift ;;
        # The body-size tier. Switches to the gatling-body-* family, whose Karate arm matches
        # the whole padded document and whose plain arm reads three small fields — the
        # difference the tier exists to measure. A SINGLE size answers nothing: the question is
        # whether the deficit scales, so run several and read the trend. Pair each size with
        # --control fat, which is what makes a rising deficit attributable to Karate rather
        # than to the cost of checking a large field.
        --body-size)  body_size="$2"; family=gatling-body; shift 2 ;;
        # Swap ONE arm for its equivalence control, leaving the other ordinary. The parity
        # number assumes both arms do equivalent work and they do not: Karate matches three
        # fields and the id round-trip where the plain reference checks one JSONPath, so Karate
        # does strictly more and the published deficit is an upper bound.
        #
        #   fat   plain raised to Karate's checks, against the ordinary karate arm
        #   lean  karate lowered to plain's checks, against the ordinary plain arm
        #
        # Run BOTH. One control only says which way the gap moves; the pair brackets the
        # like-for-like cost from above and below, and they should meet in the middle.
        --control)
            case "$2" in
                fat)  plain_arm=plain-fat ;;
                lean) karate_arm=karate-lean ;;
                *) die "--control takes 'fat' or 'lean', got: $2" ;;
            esac
            control="$2"; shift 2 ;;
        *) die "unknown flag: $1" ;;
    esac
done

# Never leave two changed arms facing each other — that compares two variants and answers
# nothing. Reachable only by passing --control twice.
if [[ "$plain_arm" != plain && "$karate_arm" != karate ]]; then
    die "both arms are controls ($plain_arm vs $karate_arm) — pass --control once"
fi

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"
[[ -n "$injector_ip" && -n "$mock_private" ]] || die "hosts not up — run etc/ec2/provision.sh"

# --- the mock ----------------------------------------------------------------
if $local_mock; then
    # The harness forks its own mock per run, exactly as a single-host run does.
    mock_args="--mock-latency $tier"
    if $tls; then
        mock_args="$mock_args --mock-tls"
    fi
    log "CONTROL: mock forked on the injector, co-located"
else
    # Restarted for every matrix, because the tier is fixed at construction — see
    # mock.sh for why a leftover mock is worse than no mock.
    "$(dirname "$0")/mock.sh" start "$tier" "$mock_port" $($tls && printf 'tls')
    mock_url="$($tls && printf 'https' || printf 'http')://${mock_private}:${mock_port}"
    kp_ssh "$injector_ip" "curl -sfk --max-time 5 $mock_url/config >/dev/null" \
        || die "injector cannot reach the mock at $mock_url — check the security group"
    log "injector reaches the mock at $mock_url"
    mock_args="--mock-url $mock_url"
fi

# --- the pairs ---------------------------------------------------------------
failures=0

# Per-arm extra flags. Only the karate arm can pool — the plain arm is Gatling's
# own client and has no HttpClientFactory to give.
arm_extra() {
    if [[ "$1" == karate* && "$pooled" == true ]]; then
        printf -- '-Dkarate.profiling.pooled=true'
    fi
}

run_arm() {
    local arm="$1" out
    # Capture rather than stream, so a failed run is visible as a missing digest
    # line instead of merely producing no output. A matrix that quietly drops an
    # arm leaves an odd run count, and `compare` then pairs run N with run N+2 —
    # two arms from different pairs, reported as one.
    if ! out="$(kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && PROFILING_SKIP_BUILD=1 \
            etc/run.sh $family-$arm --iterations $iterations --threads $users \
            ${body_size:+--body-size $body_size} \
            $mock_args $(arm_extra "$arm") 2>&1")"; then
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
#
# Two things this loop does NOT do, both deliberate. It does not share the pairs' failure
# counter: a discarded run that fails has not broken the pairing, and counting it ends the
# matrix with "the pairing is broken" when it is intact. And it does not delete "the newest
# run directory" — if the warmup dies before creating one, the newest is a previous session's
# and would be silently destroyed. Both bound the deletion to this matrix, via the marker.
log "warming the mock (one discarded run)"
warmup_failures_before=$failures
run_arm "$karate_arm" >/dev/null 2>&1 || true
failures=$warmup_failures_before
kp_ssh "$injector_ip" "cd ~/karate/karate-profiling/target/profiling && \
    victim=\$(find . -maxdepth 1 -name '$family-*' -newer '$marker' | sort | tail -1) && \
    [[ -n \"\$victim\" ]] && rm -rf \"\$victim\" && echo \"discarded \$victim\" || \
    echo 'warmup produced no run directory — nothing discarded'"
sleep "$gap"

log "$pairs pairs at $tier — $iterations iterations, $users users, ${gap}s between runs$($pooled && printf ', karate arm POOLED')${control:+, CONTROL=$control ($karate_arm vs $plain_arm)}$($tls && printf ', TLS')${body_size:+, BODY ${body_size}B}"
for ((pair = 1; pair <= pairs; pair++)); do
    # Alternate which arm leads. Any drift over the matrix — thermal, neighbour,
    # the mock's own sleep overshoot — then loads the two arms equally instead of
    # accumulating against one of them.
    if ((pair % 2 == 1)); then first="$karate_arm"; second="$plain_arm"; else first="$plain_arm"; second="$karate_arm"; fi
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
        find . -maxdepth 1 -name '$family-*' -newer '$marker' -exec mv {} '$label'/ \;
        rm -f '$marker'
        echo \"  \$(ls -1d '$label'/$family-* | wc -l) runs in $label/\""
else
    kp_ssh "$injector_ip" "rm -f ~/karate/karate-profiling/target/profiling/$marker"
fi

log "done — etc/ec2/collect.sh to pull the digests"

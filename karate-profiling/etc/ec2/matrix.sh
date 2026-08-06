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

while (($#)); do
    case "$1" in
        --tier)       tier="$2"; shift 2 ;;
        --pairs)      pairs="$2"; shift 2 ;;
        --iterations) iterations="$2"; shift 2 ;;
        --users)      users="$2"; shift 2 ;;
        --gap)        gap="$2"; shift 2 ;;
        --port)       mock_port="$2"; shift 2 ;;
        *) die "unknown flag: $1" ;;
    esac
done

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"
[[ -n "$injector_ip" && -n "$mock_private" ]] || die "hosts not up — run etc/ec2/provision.sh"

# --- the mock ----------------------------------------------------------------
# Restarted for every matrix, because the tier is fixed at construction — see
# mock.sh for why a leftover mock is worse than no mock.
"$(dirname "$0")/mock.sh" start "$tier" "$mock_port"

mock_url="http://${mock_private}:${mock_port}"
kp_ssh "$injector_ip" "curl -sf --max-time 5 $mock_url/config >/dev/null" \
    || die "injector cannot reach the mock at $mock_url — check the security group"
log "injector reaches the mock at $mock_url"

# --- the pairs ---------------------------------------------------------------
run_arm() {
    local arm="$1"
    kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && PROFILING_SKIP_BUILD=1 \
        etc/run.sh gatling-http-$arm --iterations $iterations --threads $users \
        --mock-url $mock_url 2>&1 | grep -E '^\\[parent\\] (run dir|digest)' | tail -1"
}

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

log "done — etc/ec2/collect.sh to pull the digests"

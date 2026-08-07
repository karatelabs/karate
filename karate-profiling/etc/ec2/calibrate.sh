#!/usr/bin/env bash
# Establish the mock's envelope on THIS machine, before trusting any cell taken
# against it. Step 1 of §10's order, and not skippable: the published calibration
# is a laptop's, and §10's acceptance rules are written against a local one.
#
#   etc/ec2/calibrate.sh --tier 10ms --ramp 1,4,16,64 --per-user 100
#
# Driven from the injector against the mock host, so the path under test is the
# same one a parity cell uses — a calibration taken on the mock's own loopback
# would certify a network the matrix never runs on.
source "$(dirname "$0")/lib.sh"

tier=10ms
ramp=1,4,16,64
per_user=100
settle=20s
mock_port=8090
tls=false

while (($#)); do
    case "$1" in
        --tier)     tier="$2"; shift 2 ;;
        --ramp)     ramp="$2"; shift 2 ;;
        --per-user) per_user="$2"; shift 2 ;;
        --settle)   settle="$2"; shift 2 ;;
        --port)     mock_port="$2"; shift 2 ;;
        # Calibrate over TLS, for the TLS matrix. Not cosmetic: the envelope is a different
        # one — a close-mode point pays a full handshake per request, which is the cost the
        # TLS experiment is about, so certifying a plaintext envelope and then running a TLS
        # matrix against it certifies a path the matrix never takes.
        --tls)      tls=true; shift ;;
        *) die "unknown flag: $1" ;;
    esac
done

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"
[[ -n "$injector_ip" && -n "$mock_private" ]] || die "hosts not up — run etc/ec2/provision.sh"

"$(dirname "$0")/mock.sh" start "$tier" "$mock_port" $($tls && printf 'tls')
mock_url="$($tls && printf 'https' || printf 'http')://${mock_private}:${mock_port}"

# The mock's certificate is self-signed and carries no name matching a private IP, so the
# calibrator needs BOTH halves: it builds a trust-all context itself, and hostname
# verification is disabled here. It has to be here — the JDK http client reads this property
# once at class-initialisation, so setting it from inside the process is too late.
tls_flags=""
$tls && tls_flags="-Djdk.internal.httpclient.disableHostnameVerification=true"

# Persisted, not just printed. The calibration is load-bearing evidence — it fixes the knee
# every matrix cell is chosen against, and prices a connection — but it used to exist only as
# terminal output, so a results document could cite a table that no artifact backed. It has
# already been lost once to a backgrounded ssh that kept only the tail. Written on the injector
# first so the record survives a dropped connection, then copied here; collect.sh also picks it
# up, since it sits alongside the run directories.
# Built with an if, not `$tls && ...` inside the assignment: a command substitution that
# returns non-zero becomes the ASSIGNMENT's exit status, and under `set -e` that aborts the
# script. The bare `$tls && var=x` form elsewhere is exempt only because a failing command in
# an AND-OR list is not what -e acts on. Cost 20 minutes of bench time to find.
tls_suffix=""
tls_arg=""
if $tls; then
    tls_suffix="-tls"
    tls_arg=" --tls"
fi
stamp="$(date -u +%Y-%m-%d-%H%M%S)"
name="calibration-${tier}${tls_suffix}-${stamp}.txt"
remote_out="karate/karate-profiling/target/profiling/$name"

log "calibrating from the injector against $mock_url"
kp_ssh "$injector_ip" "cd ~/karate/karate-profiling && mkdir -p target/profiling && \
    { echo '# calibrate.sh --tier $tier --ramp $ramp --per-user $per_user --settle $settle$tls_arg'; \
      echo '# mock $mock_url'; \
      echo \"# build \$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\"; \
      echo; } > ~/$remote_out && \
    /opt/jdk/bin/java $tls_flags \
        -cp target/classes:\$(cat target/cp.txt) io.karatelabs.profiling.MockCalibrator \
        --url $mock_url --ramp $ramp --per-user $per_user --settle $settle 2>&1 | tee -a ~/$remote_out"

# Copied off immediately rather than left for collect.sh: the whole point is that the table
# survives, and the injector is the thing that goes away.
mkdir -p "$KP_RESULTS"
if scp "${SSH_OPTS[@]}" -q "${KP_SSH_USER}@${injector_ip}:$remote_out" "$KP_RESULTS/$name"; then
    log "calibration archived: $KP_RESULTS/$name"
else
    log "!! could not copy the calibration off the injector — it is still at ~/$remote_out"
fi

cat <<'EOF'

Three checks, in order — two of them exist because skipping them produced a
confident wrong answer (PROFILING.md §10):

  1. ko must be 0. A point with failures is not a slower point, it is a point
     with holes: failed requests leave the sample and take the slow ones with
     them, so throughput collapses while percentiles stay clean.
  2. The baseline repeat must match the first row. Disagreement means the arm
     was still warming up, and the gap IS that arm's noise floor.
  3. The knee is where `unowned mean` departs from baseline — NOT where
     throughput stops rising. In a closed loop throughput is capped by
     users / iteration time, so it plateaus with or without a knee.

Run the matrix at half the knee. And read the CLOSE arm's baseline repeat
carefully: that is Karate's connection shape, and on the laptop its noise floor
(0.842 ms) was as large as the signal the matrix reads.
EOF

#!/usr/bin/env bash
# Start, stop or inspect the LatencyMock on the mock host.
#
#   etc/ec2/mock.sh start 10ms [port]
#   etc/ec2/mock.sh status
#   etc/ec2/mock.sh stop
#
# matrix.sh calls start itself, because the injected latency is fixed at
# construction: a mock left over from a previous tier answers with the previous
# latency while the digests carry the new label, and that error survives into a
# published table.
source "$(dirname "$0")/lib.sh"

action="${1:?usage: mock.sh start <latency> [port] | stop | status}"
latency="${2:-10ms}"
port="${3:-8090}"

mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
mock_private="$(kp_private_ip_of "$KP_MOCK_NAME")"
[[ -n "$mock_ip" ]] || die "mock host is not running"

# Killing by pidfile, not by pattern. `pkill -f LatencyMock` matches the ssh
# command line that contains the word LatencyMock — including its own — so the
# obvious version silently kills the shell that was about to start the mock, and
# the symptom is a missing log file rather than an error. Found the hard way.
STOP='[[ -f ~/mock.pid ]] && kill "$(cat ~/mock.pid)" 2>/dev/null; rm -f ~/mock.pid; sleep 1; true'

case "$action" in
    start)
        log "starting LatencyMock on $mock_ip: $latency injected, port $port"
        kp_ssh "$mock_ip" "$STOP"
        kp_ssh "$mock_ip" "cd ~/karate/karate-profiling
            # Bound to the private address, not 0.0.0.0. The security group already
            # confines it, but an unauthenticated instrument reachable from the
            # internet is one whose measured window a stranger can reset.
            nohup /opt/jdk/bin/java -cp target/classes:\$(cat target/cp.txt) \
                io.karatelabs.profiling.LatencyMock \
                --bind $mock_private --port $port --latency $latency --standalone \
                > ~/mock.log 2>&1 &
            echo \$! > ~/mock.pid
            sleep 4
            grep -h PROFILING-MOCK ~/mock.log || { echo 'mock did not start:'; tail -20 ~/mock.log; exit 1; }"
        ;;
    stop)
        log "stopping the mock"
        kp_ssh "$mock_ip" "$STOP"
        ;;
    status)
        kp_ssh "$mock_ip" 'if [[ -f ~/mock.pid ]] && kill -0 "$(cat ~/mock.pid)" 2>/dev/null; then
                echo "running (pid $(cat ~/mock.pid))"; grep -h PROFILING-MOCK-CONFIG ~/mock.log | tail -1
            else echo "not running"; fi'
        ;;
    *) die "unknown action: $action" ;;
esac

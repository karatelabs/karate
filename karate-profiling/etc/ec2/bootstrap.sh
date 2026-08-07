#!/usr/bin/env bash
# Turn two bare AL2023 hosts into a measurement bench: JDK, build, and the
# kernel settings without which the numbers measure the kernel.
#
#   KARATE_PROFILING_ENV=~/private/.env etc/ec2/bootstrap.sh [--rebuild]
#
# --rebuild re-pulls and rebuilds karate without touching packages or sysctls,
# which is the common case while iterating on a fix.
source "$(dirname "$0")/lib.sh"

rebuild_only=false
sync_local=false
for arg in "$@"; do
    case "$arg" in
        --rebuild) rebuild_only=true ;;
        # Ship the LOCAL working tree instead of pulling from GitHub. The bench
        # otherwise measures whatever is on the remote branch, which is the right
        # default — a published number should name a commit anyone can check out.
        # A prototype worth benching often lives on no
        # branch and no stash, and "push it to main first" is not an option for a
        # throwaway. Runs made this way are marked dirty in the build stamp.
        --sync) sync_local=true; rebuild_only=true ;;
        *) die "unknown flag: $arg (--rebuild, --sync)" ;;
    esac
done

injector_ip="$(kp_ip_of "$KP_INJECTOR_NAME")"
mock_ip="$(kp_ip_of "$KP_MOCK_NAME")"
[[ -n "$injector_ip" ]] || die "injector not up — run etc/ec2/provision.sh first"
# A --single bench has no mock host, and a soak does not need one.
hosts=("$injector_ip")
[[ -n "$mock_ip" ]] && hosts+=("$mock_ip") || log "no mock host — bootstrapping the injector only"

# --- the kernel settings -----------------------------------------------------
# Every one of these is a limit the harness would otherwise measure instead of
# Karate. docs/PROFILING_EC2.md explains each; the digest reports what actually
# took effect, so verify rather than assume.
read -r -d '' TUNE <<'TUNE_EOF' || true
set -euo pipefail
sudo tee /etc/sysctl.d/99-karate-profiling.conf >/dev/null <<'CONF'
# Ephemeral ports: the karate arm opens one connection per iteration, and each
# lands in TIME_WAIT. The AL2023 default (32768-60999) is ~28k ports over a
# 60s TIME_WAIT = ~470 conn/s, which a 0 ms tier exceeds by an order of magnitude.
net.ipv4.ip_local_port_range = 1024 65535
# 1, not the default 2: 2 is loopback-only, and this bench's mock is on another
# host, so 2 gives the traffic that matters no relief at all.
net.ipv4.tcp_tw_reuse = 1
# listen() silently clamps the requested backlog to this; the mock asks for 1024.
net.core.somaxconn = 8192
net.ipv4.tcp_max_syn_backlog = 8192
CONF
sudo sysctl -p /etc/sysctl.d/99-karate-profiling.conf >/dev/null

# File descriptors. Clients are released now, but an unpooled arm opens one connection
# per iteration, so TIME_WAIT churn alone needs this. Originally sized because sockets
# sit ESTABLISHED until a cleaner runs rather than moving promptly to TIME_WAIT —
# a 10-pair sweep churns tens of thousands against a 1024 soft default.
sudo tee /etc/security/limits.d/99-karate-profiling.conf >/dev/null <<'CONF'
*  soft  nofile  65536
*  hard  nofile  65536
CONF
# limits.d covers login shells; systemd-spawned and ssh non-interactive sessions
# need this too, and a bench that only raises the limit half the time is worse
# than one that never does.
sudo mkdir -p /etc/systemd/system.conf.d
sudo tee /etc/systemd/system.conf.d/99-karate-profiling.conf >/dev/null <<'CONF'
[Manager]
DefaultLimitNOFILE=65536:65536
CONF
TUNE_EOF

# --- packages and JDK --------------------------------------------------------
read -r -d '' INSTALL <<INSTALL_EOF || true
set -euo pipefail
if [[ ! -d /opt/jdk ]]; then
    sudo dnf install -q -y git tar gzip maven-openjdk21 >/dev/null 2>&1 || sudo dnf install -q -y git tar gzip maven
    curl -sL "$KP_JDK_URL" -o /tmp/jdk.tar.gz
    sudo mkdir -p /opt/jdk
    sudo tar -xzf /tmp/jdk.tar.gz -C /opt/jdk --strip-components=1
    rm -f /tmp/jdk.tar.gz
fi
echo 'export JAVA_HOME=/opt/jdk'      | sudo tee /etc/profile.d/karate-jdk.sh >/dev/null
echo 'export PATH=\$JAVA_HOME/bin:\$PATH' | sudo tee -a /etc/profile.d/karate-jdk.sh >/dev/null
INSTALL_EOF

# Refresh the sources: pull the named branch, or ship the local tree.
read -r -d '' FETCH <<FETCH_EOF || true
set -euo pipefail
if [[ ! -d ~/karate/.git ]]; then
    git clone --depth 50 -b "$KP_BRANCH" "$KP_REPO" ~/karate
fi
cd ~/karate
git fetch --depth 50 origin "$KP_BRANCH"
git checkout -q "$KP_BRANCH"
git reset -q --hard "origin/$KP_BRANCH"
FETCH_EOF

read -r -d '' BUILD <<BUILD_EOF || true
set -euo pipefail
export JAVA_HOME=/opt/jdk PATH=/opt/jdk/bin:\$PATH
cd ~/karate
# Populate ~/.m2 ONLINE here, deliberately: run.sh drives maven offline so that
# no measured run can stall on a dependency download, which means the cache has
# to be complete before the first run rather than during it.
mvn -q -pl karate-gatling -am install -DskipTests
cd karate-profiling
mvn -q compile -Pgatling
mvn -q dependency:build-classpath -Pgatling -Dmdep.outputFile=target/cp.txt
# Warm the exec plugin ONLINE too. run.sh hands off with 'mvn -o exec:java', and
# compiling never resolves exec-maven-plugin's own transitive dependencies — so a
# freshly built host fails at the first workload with a PluginResolutionException
# that reads like a code problem. Found the hard way; --list is the cheapest call
# that exercises the same path a real run takes.
mvn -q exec:java -Pgatling -Dexec.args="--list" >/dev/null
echo "built \$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\$(git diff --quiet 2>/dev/null || echo ' +DIRTY') with \$(java -version 2>&1 | head -1)"
BUILD_EOF

# The local repo root, so --sync knows what to ship.
KP_LOCAL_REPO="$(cd "$(dirname "$0")/../../.." && pwd)"

for host in "${hosts[@]}"; do
    if ! $rebuild_only; then
        log "tuning kernel on $host"
        kp_ssh "$host" "$TUNE"
        log "installing jdk + tools on $host"
        kp_ssh "$host" "$INSTALL"
    fi
    if $sync_local; then
        log "syncing local tree to $host"
        # --delete so a file removed locally is removed there: a bench running code
        # the operator thinks they deleted is the worst kind of stale.
        # target/ is excluded in both directions — a macOS build directory landing in
        # a Linux build is how a stale class file gets into a measurement — but the
        # remote target/profiling is what the results live in, so it is protected too.
        #
        # -rlpgoDz, NOT -az: `-a` implies `-t`, which preserves the LAPTOP's modification
        # times. Combined with excluding target/, that hands Maven a source file older than
        # the class the previous build produced from a different commit — so incremental
        # compilation skips it and the bench measures code that was never shipped, silently
        # and with a build stamp that claims otherwise. Without -t the synced files land with
        # the remote's current time. The cost is that rsync's size+time quick check no longer
        # short-circuits, so every file transfers each sync; on a repo this size that is
        # seconds, and it buys away an entire class of wrong measurement.
        rsync -rlpgoDz --delete \
            --exclude 'target/' --exclude '.git/' --exclude '*.iml' --exclude '.idea/' \
            -e "ssh ${SSH_OPTS[*]}" \
            "$KP_LOCAL_REPO/" "${KP_SSH_USER}@${host}:karate/"
        # And belt-and-braces: delete the compiled outputs outright, so correctness does
        # not depend on any clock agreeing with any other. Only build products are removed
        # — never target/profiling, which is where the results live and which a `mvn clean`
        # would take with it. That asymmetry is why this is an explicit rm and not a clean.
        log "discarding compiled classes on $host so nothing stale can survive the sync"
        kp_ssh "$host" 'set -e
            # karate-profiling/target is thinned rather than removed: target/profiling
            # inside it holds every run this host has produced.
            rm -rf ~/karate/karate-profiling/target/classes ~/karate/karate-profiling/target/cp.txt
            for m in ~/karate/*/target; do
                case "$m" in
                    *karate-profiling/target) continue ;;
                    *"*"*) continue ;;   # glob matched nothing
                    *) rm -rf "$m" ;;
                esac
            done
            # Every io.karatelabs artifact is a module of this repo, so the installed copies
            # are all rebuilt from the synced sources. Left in place they would satisfy the
            # reactor and reintroduce exactly the staleness this is here to prevent.
            rm -rf ~/.m2/repository/io/karatelabs'
    else
        log "fetching $KP_BRANCH on $host"
        kp_ssh "$host" "$FETCH"
    fi
    log "building karate on $host"
    kp_ssh "$host" "$BUILD"
done

if ! $rebuild_only; then
    log "verifying the settings actually took"
    for host in "${hosts[@]}"; do
        echo "--- $host"
        kp_ssh "$host" 'echo "  ports     : $(cat /proc/sys/net/ipv4/ip_local_port_range)"
                        echo "  tw_reuse  : $(cat /proc/sys/net/ipv4/tcp_tw_reuse)"
                        echo "  somaxconn : $(cat /proc/sys/net/core/somaxconn)"
                        echo "  nofile    : $(ulimit -n)"
                        echo "  cpus      : $(nproc)"'
    done
fi

log "bootstrap done"

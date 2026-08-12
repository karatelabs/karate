# Running the profiling bench on EC2

> The two-host phase of [PROFILING.md](./PROFILING.md) §10, as a runbook. Everything here
> creates billable resources and **nothing stops on its own** — read
> [Teardown](#7-teardown) before you read anything else.
>
> **Why two machines rather than a laptop.** §10's 50 ms tier was run six times on a laptop
> and never resolved: the effect is ~0.5% of an iteration and the machine's run-to-run floor
> is 3–6%. On a quiet dedicated instance the same measurement lands at sd **0.05 ms** — the
> noise problem is solved. Co-location was the second reason, and it turned out to be the
> smaller one: measured against a same-instance control, topology accounts for ≲0.15 ms of a
> 1.2 ms difference ([§6](#6-what-moving-off-the-laptop-changed)).

---

## 1. What it builds

```
       your laptop                     us-east-2b, one cluster placement group
  ┌──────────────────┐            ┌──────────────────────┬──────────────────────┐
  │ etc/ec2/*.sh     │──── ssh ──►│ injector             │ mock                 │
  │ private .env     │            │ c7g.4xlarge, 16 vCPU │ c7g.4xlarge, 16 vCPU │
  │ results/         │◄─ rsync ───│ gatling + karate     │ LatencyMock only     │
  └──────────────────┘            └──────────┬───────────┴──────────────────────┘
                                             └── private IP, same AZ ──►
```

Two instances, because one is what this phase exists to stop doing. The mock is alone on
its host and the injector reaches it on the **private** address — same AZ, no NAT hop, and
the security group opens the mock's port to the injector only.

**Graviton3 (`c7g`) on purpose**, and it is not a cost decision: 1 vCPU is 1 physical core
with no SMT, and there is no turbo-bin jitter. Those are two of the variance sources that
made the laptop's 50 ms tier unreadable. **Never a T-series** — burst credits throttle
silently, which is the same failure this phase exists to escape.

---

## 2. Prerequisites

- `aws` CLI with a profile that can run EC2, and `rsync`.
- vCPU quota ≥ 32 for on-demand standard instances (`L-1216C47A`). Two `c7g.4xlarge` is 32.
- An EC2 key pair, with the private key on your machine.

**Nothing is copied from your laptop.** `bootstrap.sh` clones the public repo *on each host*
and builds there, so `~/.m2` is populated on the instance from Maven Central. The only thing
crossing is the ssh command text — which means **the bench measures whatever is on the
branch, not your working tree**. See [`--sync`](#43-testing-unpushed-work) for the exception.

---

## 3. The env file

Every script reads one file and contains no account details of its own. Keep it **outside
this repo** — the repo is public OSS.

```bash
export KARATE_PROFILING_ENV=~/somewhere/private/karate-ec2.env
```

```bash
# --- AWS ---
export AWS_PROFILE=your-profile
export AWS_REGION=us-east-2
export KP_AZ=us-east-2b              # ONE az: a cluster placement group cannot span them
export KP_SUBNET=subnet-xxxxxxxx     # a public subnet in that az
export KP_VPC=vpc-xxxxxxxx

# --- the bench ---
export KP_INSTANCE_TYPE=c7g.4xlarge
export KP_AMI=ami-xxxxxxxx           # AL2023 arm64; resolve fresh, see below
export KP_KEY_NAME=your-key
export KP_KEY_FILE=/path/to/your-key.pem
export KP_PREFIX=karate-profiling    # names every resource; teardown finds things by it

# --- optional: elastic IPs, so addresses survive a rebuild ---
export KP_EIP_INJECTOR=eipalloc-xxxxxxxx
export KP_EIP_MOCK=eipalloc-xxxxxxxx

# --- local ---
export KP_RESULTS=/path/to/results
export KP_REPO=https://github.com/karatelabs/karate.git
export KP_BRANCH=main
export KP_JDK_URL=https://corretto.aws/downloads/latest/amazon-corretto-24-aarch64-linux-jdk.tar.gz
```

Resolve the current AL2023 arm64 AMI rather than pinning one that ages out:

```bash
aws ssm get-parameters --names \
  /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query 'Parameters[0].Value' --output text
```

**Pin the JDK deliberately.** The existing baselines were taken on JDK 24, and §3 says never
to compare two digests whose child JDK differs. `run-meta.txt` records what actually ran.

---

## 4. The cycle

```bash
cd karate-profiling
export KARATE_PROFILING_ENV=~/somewhere/private/karate-ec2.env

etc/ec2/provision.sh                                   # ~1 min
etc/ec2/bootstrap.sh                                   # ~3.5 min
etc/ec2/calibrate.sh --tier 10ms                       # §10 step 1 — establishes the
                                                       # mock's envelope on THIS machine.
                                                       # Archived to $KP_RESULTS as
                                                       # calibration-<tier>-<stamp>.txt: it
                                                       # fixes the knee every cell is chosen
                                                       # against, so it is evidence, and it
                                                       # used to exist only on your terminal
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8
etc/ec2/collect.sh                                     # pulls digests, prints the table
etc/ec2/teardown.sh                                    # ← do not skip
```

### 4.1 provision

Creates the security group (ssh from your current public IP only, plus all traffic within
the group), the cluster placement group, two instances, and associates the elastic IPs if
you named any. Idempotent, and it re-adds the ssh rule when your address has changed —
a stale rule is the most common reason a re-run cannot reach hosts it just made.

### 4.2 bootstrap

Applies the kernel settings, installs the JDK, clones and builds. `--rebuild` skips packages
and sysctls and just refreshes the source, which is the common case while iterating.

The sysctls are not tuning, they are the difference between measuring Karate and measuring
the kernel:

| | Why |
|---|---|
| `ip_local_port_range = 1024 65535` | The karate arm opens a connection per iteration. The AL2023 default (~28k ports over a 60 s TIME_WAIT) is ~470 conn/s; a 0 ms tier exceeds that by an order of magnitude, and the stall reads as "Karate is slower". |
| `tcp_tw_reuse = 1` | **1, not the default 2.** 2 is loopback-only — no relief at all for a mock on another host, which is exactly this bench. |
| `somaxconn = 8192` | `listen()` silently clamps the requested backlog to it; the mock asks for 1024. |
| `nofile = 65536` | Per-execution HTTP clients *are* released now, but an unpooled arm still opens a connection per iteration and TIME_WAIT churn alone runs to tens of thousands across a 10-pair sweep, against a 1024 default. (The original reason — clients never closed at all — was fixed by the lifecycle work; the limit is still needed.) |

bootstrap prints what actually took effect, and every digest carries the derived ceiling in
`run-meta.txt`. Check it — a sysctl that did not apply looks exactly like one that did.

### 4.3 Testing unpushed work

```bash
etc/ec2/bootstrap.sh --sync
```

rsyncs the local working tree instead of pulling the branch, and stamps the build `+DIRTY`.
This exists so a run can be taken from a tree that is not on any branch — a throwaway prototype
lives on no branch and no stash, and "push it to main first" is not an option for one.

**Publish numbers from a named commit.** `--sync` is for iterating; when a result is going
into a table, push it, `--rebuild`, and let the build stamp name the commit.

The stamp is recorded for you, in both places that matter: `run-meta.txt` carries
`build: <sha>`, and `digest.md` echoes it in the run summary — the latter being the one
`profiler compare` can see, since it reads digests and nothing else. A tree with uncommitted
changes is marked `+DIRTY`, which is the case that most needs saying: the sha alone would name
a build that is not the one that ran. Runs taken before the stamp existed have no build line at
all, so a comparison reaching back past them stays undecidable — say so rather than assuming.

### 4.4 matrix

```bash
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8 --label 10ms-2host
etc/ec2/matrix.sh --tier 50ms --pairs 10 --iterations 1600 --users 8 --label 50ms-2host
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8 \
                  --local-mock --label 10ms-1host      # the co-location control
```

**The pooled-connection A/B — run on 2026-08-07; result in [PROFILING.md §9](./PROFILING.md).**

```bash
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8 --pooled --label 10ms-pooled
etc/ec2/matrix.sh --tier 50ms --pairs 10 --iterations 1600 --users 8 --pooled --label 50ms-pooled
```

**Run the unpooled half in the same session, on the same hosts, rather than reusing an older
table.** That is what the 2026-08-07 run did, and it is not caution for its own sake: the effect
is ~0.4 ms/iteration, which is the same order as the drift between two sessions — and the earlier
10 ms table turned out to disagree with a fresh one by 0.27 ms (PROFILING.md §9,
"Settled — do not re-run", the 10 ms tier entry). Re-running both halves
also buys a free control, because the **plain** arm is untouched by `--pooled`: if plain's req/s
matches across the two matrices (it drifted 0.08% and 0.00%), the halves are comparable, and if it
does not, you have found that out instead of publishing it.

`--pooled` gives the **karate arm only** a shared connection pool
(`-Dkarate.profiling.pooled=true` → `PooledHttpClientFactory`). The plain arm is Gatling's own
client and already keep-alives, so there is nothing to pool there — and leaving it alone is what
keeps the run a normal parity cell. That matters because `compare` pairs *plain against karate*
and cannot pair two karate arms against each other: **the A/B is this table against the unpooled
table at the same tier**, not two karate runs against each other.

Confirm the arms really differ before reading anything else — `distinctPeerPorts` in each digest
should be ≈ iterations unpooled and ≈ users pooled. Locally that was 400 → 4 at 4 users.

Running it through `matrix.sh` rather than by hand is not a convenience: the warmup discard, the
alternating arm order, the failure counting and `--label`'s interleave protection are all in there,
and a hand-run pair silently loses every one of them.

Restarts the mock at the requested latency — the tier is fixed at construction, and a
leftover mock from the previous tier answers with the old latency while the digests carry
the new label. Discards one warmup run against the fresh mock (see §6 — without it the
first pair is biased against Karate by ~0.5 ms). Then runs the pairs, **alternating which
arm leads**, so drift over the matrix loads both arms equally instead of accumulating
against one.

**Always pass `--label`, and a distinct one per matrix.** `compare` pairs runs by timestamp
adjacency, so two unlabelled matrices in one directory interleave — the last run of one
pairs with the first of the next and two experiments are reported as a single table with
nothing amiss on the face of it. `--label` parks each matrix in its own directory and
`collect.sh` derives one table per label. Re-using a label across two runs re-creates the
same hazard inside it.

**`--local-mock`** forks the mock on the injector as a single-host run does. It is the
control for the whole phase: same instance, same JDK, same commit, and only the topology
changes — without it, any difference from the laptop is confounded by architecture and OS
as well. Note it is not a perfectly clean control, because `run.sh` forks a fresh mock per
run while the two-host mock persists warmed (§6).

### 4.5 soaks (one host, no mock)

> **Only the no-HTTP soaks.** This section covers `scope-capture-*` and friends, which make no HTTP
> calls and so need one JVM and no mock. **The HTTP soaks are TWO-host runs** — the completed
> pooled Gatling soak, and the open one in [PROFILING.md](./PROFILING.md) §9 (E1, the suite
> soak: reports on, TLS, JS and feature calls; it subsumes the unpooled-client question).
> `mock.sh start` dies "mock host is not running" on a
> `--single` bench, which is the correct failure but an expensive place to discover the topology
> was wrong. The shape that ran the 2026-08-07 Gatling soak, for reuse:
>
> ```bash
> etc/ec2/mock.sh start 10ms 8090
> mock=$(etc/ec2/ssh.sh mock-private)
> etc/ec2/ssh.sh injector "cd ~/karate/karate-profiling && nohup env PROFILING_SKIP_BUILD=1 \
>     etc/run.sh gatling-http-karate --duration 1h --soak --threads 8 \
>     --mock-url http://$mock:8090 -Dkarate.profiling.pooled=true --timeout 90m \
>     > ~/soak.log 2>&1 &"
> ```
>
> The **suite soak** (E1) is the same topology with a TLS mock, and is *iteration*-bounded — so
> the timeout is not optional, and `--duration` is refused for it. This is the canonical command
> from [PROFILING.md §9](./PROFILING.md), copied verbatim; if the two ever differ, §9 is the one
> that carries the reasoning. Note the larger root volume: the run writes ~12 GB of reports it
> never sends home, and ENOSPC mid-soak wastes the session.
>
> ```bash
> etc/ec2/provision.sh --volume-gb 150
> etc/ec2/bootstrap.sh
> etc/ec2/mock.sh start 50ms 8443 tls
> mock=$(etc/ec2/ssh.sh mock-private)
> etc/ec2/ssh.sh injector "cd ~/karate/karate-profiling && nohup env PROFILING_SKIP_BUILD=1 \
>     etc/run.sh suite-soak --iterations 350000 --threads 8 --soak --xmx 8g \
>     --mock-url https://$mock:8443 -Dkarate.profiling.reports=all \
>     -Dprofiling.soak.suites=4 --timeout 3h > ~/soak.log 2>&1 &"
> ```
>
> Collect **after the parent finishes**, not when the child exits — the digest is written about a
> minute later, and `collect.sh` now fails closed when it is missing rather than reporting a
> successful copy without it. Its completeness probe matches the run stamp rather than
> `gatling-*`, so a `suite-soak-*` directory is inside the guard; `selftest.sh` proves that
> branch for free.

```bash
etc/ec2/provision.sh --single          # a soak needs time and one JVM, not the topology
etc/ec2/bootstrap.sh
etc/ec2/ssh.sh injector 'cd ~/karate/karate-profiling && \
  nohup env PROFILING_SKIP_BUILD=1 etc/run.sh scope-capture-bound \
  --duration 1h --threads 8 --soak --gc-roots > ~/soak.log 2>&1 &'
```

`nohup` matters: the run must survive your ssh session, and the digest is written **on exit**, so
nothing appears until it finishes. Watch it with
`etc/ec2/ssh.sh injector 'tail -3 ~/soak.log'`.

**`--soak` is not optional for a long run** — without it the recording rolls and the digest
describes only the last ~25 minutes. The parent now warns when `--duration` exceeds 20 minutes
without it. See [PROFILING.md](./PROFILING.md) on soak mode.

**Check the summary's `elapsedMs` against the window you asked for.** A `--duration` run that
truncates used to do so silently. It now sets `truncated=true` in the summary line, reads as
**TRUNCATED** in the digest's outcome row, and exits non-zero — but `elapsedMs` is still the
one-second check that catches any future variant, and it is the check that caught this one.

**An iteration-bounded soak needs an explicit `--timeout`.** The default for that shape is a flat
**one hour**, not "duration plus slack", because the runtime is unknown by construction. Sizing an
iteration count at "about an hour" from a throughput estimate therefore lands on the kill boundary,
and the estimate usually comes from a shorter, warmer run — so it errs optimistic. The parent
prints a note when you are in that case.

**Do not use `pgrep -f`/`pkill -f` to check on it over ssh** — the pattern matches the ssh command
line carrying it, so "is the child alive" answers yes forever. Use `ps -eo etime,cmd | grep "[C]hild"`.

### 4.6 the karate-js A/B matrix (one host, no mock)

The js-* family compares **two builds of the karate-js jar** on elapsed time —
[PROFILING.md](./PROFILING.md) has the family's design and the reading rules. On the bench it
is a `--single` topology: no mock, no second host, roughly $0.58/hour.

```bash
etc/ec2/provision.sh --single
etc/ec2/bootstrap.sh                        # or --sync for an unpushed harness tree

jar_a=$(etc/js-arm.sh <base-ref>)           # built LOCALLY — the arms may be commits that
jar_b=$(etc/js-arm.sh <candidate-ref>)      # exist on no pushed branch; jars are portable
etc/ec2/js-matrix.sh --jar-a "$jar_a" --jar-b "$jar_b" --pairs 4 --label my-ab

etc/ec2/collect.sh                          # derives the js table per label
etc/ec2/teardown.sh
```

Notes specific to running it here:

- **The arms are built on your machine and rsynced**, manifest and all; `js-matrix.sh`
  re-hashes both jars on the injector against their manifests before the first run, so a bad
  transfer fails before it can spend bench time. The measurement runs on the instance's JVM
  either way — the jar is bytecode, and `run-meta.txt` records the child JDK that executed it.
- **`--label` is mandatory and must be fresh** — it parks the matrix, prefixes every run tag,
  and a reused label is refused on the injector rather than warned about.
- Runs are `--no-jfr` (timing) and tag-paired, so a failed run orphans one cell; the matrix
  reports the failure, still parks the completed cells, and the derived table names the
  orphan. A partial matrix is usable evidence, not a discard.
- The per-arm flag cell: `--sysprop-b some.engine.flag=false` runs the candidate jar
  flag-off against the base — the equivalence control that separates "the change" from "the
  changed build".

### 4.7 collect

Pulls `digest.md`, `run-meta.txt`, `mock.log` and any archived `calibration-*.txt` — not
recordings. A `run.jfr` is up to 512 MB and the digest is kilobytes; pull a recording by hand
when you actually intend to run §5's `jfr` recipes on it. Then it runs `etc/run.sh compare`
locally, which reads digests and does not care which machine produced them.

**It fails closed, and the exit code is now worth reading.** The parent writes `digest.md`
*after* the child exits, so a collection triggered by the child going away can copy everything
rsync was asked for and still miss the one artifact the run existed to produce — a one-hour soak
did exactly that, and returned 0. A digest count cannot catch it, because older digests conceal
one missing new one. So collect compares *sets*:

| condition | what happens |
|---|---|
| the injector has a `digest.md` this machine does not | **fatal** — the copy dropped it, which is never a timing artifact |
| a run directory has no digest **and a `Child` is running** | a note; collecting mid-matrix is supported |
| a run directory has no digest **and nothing is running** | **fatal** — either the parent has not finished, or it died and the run dies with the host |

A non-zero exit here means **do not tear down**. It is the check `teardown.sh --force` cannot
make for you.

### 4.8 aborting a run that is going wrong

Watching the first suite boundary is only useful if deciding "this is mis-sized" has a clean
exit, and the obvious moves are both wrong: killing the *parent* loses the digest, and walking
away leaves two hosts billing until the timeout.

**Kill the child, not the parent.** The parent writes `digest.md` after the child exits, so this
is the same path `--timeout` takes — you get a digest for what actually ran, and the Suite
outcome panel shows fewer suites than were asked for, which is what marks it partial:

```bash
# the bracket trick is load-bearing, for the reason above: the shell sshd spawns carries this
# pattern on its own command line, so the unbracketed form matches itself and kills the shell
etc/ec2/ssh.sh injector 'pkill -f "[p]rofiling.Child"'
etc/ec2/ssh.sh injector 'ps -eo etime,cmd | grep "[C]hild"'   # empty; now wait for the parent
```

Then collect and tear down normally. Only reach for the discard path — `teardown.sh --force`
without a successful collect — when you have decided the partial run is worth nothing, because
it throws away the evidence of *why* it was mis-sized, which is usually the point of aborting.

---

## 5. Reading the result

Everything in [PROFILING.md §10](./PROFILING.md) applies unchanged. Two checks are specific
to running on real hosts:

- **Both `injector cores` columns should be a small fraction of 16.** That column exists
  because "the arms achieved the same throughput" means nothing if either client was
  saturated. This is the leg §10 carried as *"not recorded anywhere"*.
- **The mock host should be near-idle.** Its CPU row is the co-location bias as a number.
  On two hosts it is what finally licenses "not co-located" rather than asserting it.

**The gate on the 10 ms control tier is internal consistency across its own pairs — a spread
comfortably under the effect — not agreement with the laptop's 0.6–0.8 ms.** Graviton is a
different microarchitecture and a genuinely different fixed cost is an expected outcome, not
a broken rig. Only a spread that stays larger than the effect says the machine is not quiet.

---

## 6. What moving off the laptop changed

Recorded because the move turned several of §10's predictions into measurements — and refuted
one of them.

| | Laptop (machine A) | EC2 two-host |
|---|---|---|
| Sustainable connection rate | 16384 ports / 30 s → **~546/s** | 64512 ports / 60 s → **~1075/s**, plus `tcp_tw_reuse=1` |
| 10 ms iteration | ~28 ms (sleep overshot to ~14 ms) | **~21 ms** — the injected 10 ms is 10.06 ms |
| Injector headroom | never recorded | **0.9–1.7 of 16 cores**, in every digest |
| Karate's added serial time | +0.59 ms/iter, sd 0.29, n=3 | **+1.79 ms/iter, sd 0.05, n=9** |

**The first result is that the cost is machine-dependent, and the absolute number does not
travel.** The laptop's 3-pair figure carries a 95% interval of roughly −0.13…+1.31 ms, so the
ratio between the two machines is anywhere from ~1.4x to ~14x. Any statement of the form
"Karate costs X ms per iteration" has to name the machine.

**The second is that co-location was *not* the confound it was thought to be.** §10 predicted
the two-host move would expose a per-iteration connection cost that loopback priced at zero.
It did not: a co-located control on the *same instance, same commit* came in at **+1.94 ms
(sd 0.09, n=4)** against the two-host **+1.79** — so topology accounts for about **0.15 ms**,
roughly a tenth of the 1.2 ms gap from the laptop. The prediction is not refuted, though: at a
same-AZ RTT of ~100 µs there is almost no round trip to pay for. It remains **untested at
realistic RTT with TLS**, which is where it was always expected to bite.

Two cautions that cost a wrong conclusion here, both worth carrying into the next run:

- **A freshly started mock is cold, and the arm that meets it pays.** The first run against a
  new `LatencyMock` showed the mock burning 3.86 core-s with a service p99 of 231 µs, against
  0.70 core-s and 10 µs once warm — and that arm ran ~0.5 ms/iteration slower. Since pair 1
  always leads with karate, the bias was structural and always against Karate: it moved a
  10-pair mean from +1.79 to +1.84 and tripled its standard deviation single-handedly.
  `matrix.sh` now discards one warmup run after starting the mock.
- **Do not read a co-located mock's high CPU as core contention.** The control's mock showed
  4.8 core-s and a 303 µs p99 against the two-host mock's 0.7 and 10 µs — which looks like
  co-location damage and is not. `run.sh` forks a fresh mock per run, so every co-located run
  meets a cold JVM; the *plain* arm, which opens 8 connections in total, shows the identical
  signature. It is warmup, not contention. (It also means the control differs from the
  two-host arm in two ways — topology *and* mock lifecycle — so the 0.15 ms above is an upper
  bound on the topology term.)

## 7. Teardown

```bash
etc/ec2/teardown.sh
```

Terminates both instances, deletes the placement group and security group, and prints what
is still running so the answer is visible rather than assumed. **Elastic IPs are
disassociated, never released** — they are account resources this bench borrows, and
releasing one loses the address.

Two `c7g.4xlarge` is roughly **$1.16/hour**, about $28 a day if forgotten. There is no
auto-stop and no budget alarm. If you want one, that is a real gap worth filling — see below.

```bash
# the paranoid check, from anywhere — ALL states, because a STOPPED instance
# still bills its EBS and is invisible to a running-only filter
aws ec2 describe-instances --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --filters Name=instance-state-name,Values=running,pending,stopping,stopped,shutting-down \
  --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name,Type:InstanceType,Launched:LaunchTime}' \
  --output table
```

---

## 8. Not built

- **No auto-stop and no budget alarm.** Teardown is a command someone has to run.
- **No spot instances.** On-demand is deliberate for now: a spot reclaim mid-matrix
  invalidates the pairs it interrupted, and cheap runs that silently lose their arm ordering
  are worse than expensive ones that finish.
- **One AZ, one region, one instance type.** The bench cannot yet answer whether the result
  is Graviton-specific — an `m7i` arm would cost one env file change and is the obvious next
  variation.
- **The mock host runs no recording.** `--record mock` needs a mock this process forked, so
  profiling the mock itself still means the single-host configuration.

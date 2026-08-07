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
                                                       # mock's envelope on THIS machine
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
| `nofile = 65536` | The per-execution HTTP client is never closed, so its sockets sit ESTABLISHED until a cleaner runs. A 10-pair sweep churns tens of thousands against a 1024 default. |

bootstrap prints what actually took effect, and every digest carries the derived ceiling in
`run-meta.txt`. Check it — a sysctl that did not apply looks exactly like one that did.

### 4.3 Testing unpushed work

```bash
etc/ec2/bootstrap.sh --sync
```

rsyncs the local working tree instead of pulling the branch, and stamps the build `+DIRTY`.
This exists because the plan's own next experiment — the AST prototype behind
[Parsed-JS reuse](./PROFILING.md) — lives on no branch and no stash, and "push it to main
first" is not an option for a throwaway.

**Publish numbers from a named commit.** `--sync` is for iterating; when a result is going
into a table, push it, `--rebuild`, and let the build stamp name the commit.

### 4.4 matrix

```bash
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8 --label 10ms-2host
etc/ec2/matrix.sh --tier 50ms --pairs 10 --iterations 1600 --users 8 --label 50ms-2host
etc/ec2/matrix.sh --tier 10ms --pairs 10 --iterations 4000 --users 8 \
                  --local-mock --label 10ms-1host      # the co-location control
```

**The pooled-connection A/B — the next run this bench is for.** `matrix.sh` has no flag for it
yet; pass the property through to both arms of a normal matrix, or run the two arms by hand:

```bash
# unpooled (today's shape: one connection per iteration)
etc/run.sh gatling-http-karate --iterations 4000 --threads 8 --mock-url http://<mock-private-ip>:8090
# pooled (one connection per virtual user)
etc/run.sh gatling-http-karate --iterations 4000 --threads 8 --mock-url http://<mock-private-ip>:8090 \
           -Dkarate.profiling.pooled=true
```

Read `distinctPeerPorts` in each digest to confirm the arms really differ (expect ~iterations
versus ~users), then compare added-ms-per-iteration. **Run it at 10 ms and 50 ms.** The connection
question is already answered locally; what this bench adds is the *price* of those handshakes,
which loopback cannot show because a connection there is nearly free. A TLS tier would show the
most, since it saves 1–2 RTT rather than one.

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

### 4.6 collect

Pulls `digest.md`, `run-meta.txt` and `mock.log` — not recordings. A `run.jfr` is up to
512 MB and the digest is kilobytes; pull a recording by hand when you actually intend to run
§5's `jfr` recipes on it. Then it runs `etc/run.sh compare` locally, which reads digests and
does not care which machine produced them.

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
# the paranoid check, from anywhere
aws ec2 describe-instances --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --filters Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].{Id:InstanceId,Type:InstanceType,Launched:LaunchTime}' \
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

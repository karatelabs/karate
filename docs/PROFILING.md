# Profiling Karate

> Runbook for JFR-based profiling of karate-core, the mock server and the JS engine — and the
> evidence base for the karate-gatling parity claims. Written for an LLM operator: every step is
> a command you run and an artifact you read.
>
> **Everything here reports. Nothing asserts.** There is no CI job, no committed baseline
> file and no pass/fail gate. An OOM is a legitimate outcome — for some workloads it is
> the *expected* one. Judgement lives with the reader.
>
> Structure: **§0 is the thesis and the claims register** — what is established, on what
> evidence, with what qualifiers. **§9 is the steering surface** — the open experiments, what is
> settled and must not be re-run, and what is parked on evidence. **§10 is the method** — the
> instrument every §0 claim was measured with. §§1–7 are the operator's manual — running the
> harness, the workloads, reading digests, symptom recipes — and §8 records what the
> parallel-execution memory investigation settled.
>
> **If you are here to run something, go to [§9](#9-the-steering-surface).**
> The bench is automated end to end in **[PROFILING_EC2.md](./PROFILING_EC2.md)**.

---

## 0. The thesis, and the evidence

**The thesis:** *karate-gatling is sufficient for enterprise continuous performance testing.*
Concretely: a team reusing functional Karate features as load tests, against an API at realistic
latency, measures the same thing a hand-written Gatling simulation would measure — the reported
latencies are the server's, the throughput deficit is on the order of 1%, and nothing degrades
over sustained load. What they pay is injector CPU, and that is a sizing line-item, not a
distortion.

### The claims register

Every public claim, its figure, its evidence, and the qualifier that must travel with it.
All parity rows: two-host EC2 bench (`c7g.4xlarge` ×2, Graviton3/aarch64, same-AZ placement
group), build `745a408`, JDK 24, 50 ms injected latency, 8 virtual users, karate arm pooled
(`pooledConnections()`, the shipped load-test configuration), **0 KO in every cell**, and
`distinctPeerPorts` equal to the user count on both arms in every run.

| # | Claim | Figure | Evidence | Scope / qualifier |
|---|---|---|---|---|
| C1 | **Throughput parity at realistic latency** | **+1.46 ms per two-request iteration — a 1.4% deficit** | TLS, 10 pairs, sd 0.09 | 34-byte body; closed loop; injector far below saturation |
| C2 | **TLS costs nothing extra once pooled** | TLS − plaintext = −0.016 ms, SE 0.038 (Welch 95% ≈ −0.09…+0.06) | same-session plaintext control, 5 pairs | pooled only — a TLS *connection* costs ~2.7 ms, so the unpooled story is entirely different |
| C3 | **The comparison is fair** | fat +1.49, lean +1.43 — both 1.4% | equivalence controls, 5 pairs each, sd 0.05 | raising Gatling's checks and lowering Karate's both leave the deficit unmoved; the cost is per-execution, not per-assertion |
| C4 | **Payload scaling is sub-linear** | 1 KB → 1.5%; 64 KB → 2.3%; ~9 µs/KB like-for-like | body tier + fat control at each size, 5 pairs each | plaintext; both arms already pay an O(size) parse — never publish this as "reads the bytes vs skips them" |
| C5 | **No leak in karate-gatling as shipped** | live set −2.3 MB, descriptors flat (233→234), **0 closed by the probe's GC**, over 1 h / 1,359,297 iterations / 0 KO | pooled soak, 10 ms tier, all integrity checks passed | pooled lane only — the unpooled path and a Runner suite with reports on are both open, and both fold into E1 (§9) |
| C6 | **Reported latencies are honest** | percentiles match the server's injected latency; Karate's overhead sits *between* `PerfEvent` brackets | every digest's load profile; mechanism in [§10](#what-the-current-result-does-not-license) | this is why a Karate load test does not distort the measurement it exists to take |
| C7 | **Capacity costs ~2× CPU** | 1.57× CPU/iteration at 8 users, 1.94× at 32 | `cpuNanos` from the same cells | below saturation this never reaches wall clock; at an injector's ceiling it means roughly half the virtual users per host. A sizing fact, not a distortion |

**How the thesis is worded when quoted** (GATLING.md is the user-facing place): *within ~1.5% of
vanilla Gatling's throughput at 50 ms server latency and payloads up to ~1 KB, growing
sub-linearly with payload (2.3% at 64 KB); latencies reported are the server's; no leak under
sustained load; budget ~2× injector CPU.* The 1.5% figure is honest for the measured cells;
the realistic-suite *parity* cell (E2 in §9, currently paused with the rest of the Gatling
arc) is what would close the remaining gap between those cells and a claim about a *realistic
suite* — one whose karate-config.js does work, calls JS functions and reuses features, which
no measured parity cell yet does. (E1's soak workload has that realistic shape, but it has no
Gatling arm, so it informs the leak claim, not the parity one.)

### What is NOT established

- **No measurement against a real public endpoint at a real RTT.** The TLS result removes the
  transport objection; it does not substitute for that measurement.
- **Open-loop / overload behaviour.** Every cell is closed-loop, so throughput is capped by
  `users ÷ iteration time` and the knee is optimistic by construction (E4, paused).
- **A large Runner suite with reports on, over hours** — the largest remaining gap, and
  **the next experiment** (E1).
- **The unpooled client path** — where the original client-lifecycle defect lived, and
  karate-core's default for functional suites. E1's per-scenario clients cover it.
- **The 8-vs-32-user trend.** Each cell's own figure stands; the trend between them is
  confounded with run length (E3, paused).

Full write-up of the 2026-08-07 session, with every table and its caveats, is kept privately
alongside the artifacts; [§9](#settled--do-not-re-run) carries the durable conclusions.

---

## 1. Quick start

```bash
cd karate-profiling

etc/run.sh --list                          # what can I run?
etc/run.sh harness-smoke --iterations 50000 # smoke: is the harness alive?
etc/run.sh call-accumulation                # a memory workload

# the Gatling parity lane — always in pairs, always against an instrumented mock
etc/run.sh gatling-http-plain  --iterations 4000 --threads 8 --mock-latency 10ms
etc/run.sh gatling-http-karate --iterations 4000 --threads 8 --mock-latency 10ms

# then derive the pair table — never scrape it by hand, see §10
etc/run.sh compare target/profiling/gatling-http-*
```

`run.sh` drives Maven **offline** (`-o`) throughout, deliberately: a profiling run should not
depend on the network, and a surprise dependency download inside a measured window is exactly
the kind of thing that ruins a comparison. The cost is that a machine with an unpopulated
`~/.m2` fails at the first step — build the project once online before profiling it.

Every invocation writes one self-contained, never-overwritten directory:

```
karate-profiling/target/profiling/<workload>-<yyyy-MM-dd-HHmmss>/
    digest.md          ← READ THIS FIRST
    run.jfr            ← drill into it with the recipes in §5
    jfr-repo/          ← live recording chunks; recovery source if run.jfr is unusable
    stdout.log         ← the child JVM's output
    run-meta.txt       ← child command line, build commit, child JDK, OS, workload config
    heapdump.hprof     ← only written if the child OOM'd
```

**Read `digest.md` first.** It is a few hundred lines of markdown distilled from a
multi-hundred-MB binary recording. Only open `run.jfr` with the raw `jfr` CLI (§5) when
the digest doesn't answer your question — the raw output is enormous and will eat your
context.

### How a run is structured

The `Profiler` you invoke is a **parent** process that does no measuring itself:

```
[parent]  etc/run.sh scope-capture-bound
   ├─ mock JVM      (no JFR)   → prints its port, then serves
   └─ workload JVM  (JFR on)   -Xmx768m -XX:+UseG1GC -Dkarate.profiling.mockUrl=…
                                └─ on exit → JfrDigest → digest.md
```

The mock server is a **sibling process** deliberately. If it shared a JVM with the load
driver, its CPU samples and allocations would land in the same recording and every number
below would be a blend of client and server. Forking costs a localhost socket hop, which
is closer to reality anyway.

### Disk hygiene — prune after every result

**A sweep will fill your disk, and nothing prunes anything automatically.** Getting one
result costs on the order of a gigabyte, and a single afternoon of sweeps once reached
**19 GB** and hit ENOSPC mid-matrix — which aborts the run *and* the tooling around it, and
the partial matrix is worthless because the runs that did complete were competing for a full
disk.

Three consumers, in order of size:

| What | Where | Size |
|---|---|---|
| Per-run HTML/Cucumber/JUnit reports | `karate-profiling/target/karate-reports*` | ~1 GB per run **on memory workloads** |
| JFR recording + live chunk repository | `target/profiling/<run>/{run.jfr,jfr-repo/}` | up to `maxsize` (512m) per run |
| Heap dump, when a run OOMs | `target/profiling/<run>/heapdump.hprof` | roughly `--xmx` — so 3 GB at `--xmx 3g` |

The report directory is the one that surprises people. Karate's `backupOutputDir` defaults
to **true**, which *renames* the previous `karate-reports/` to a timestamped backup rather
than replacing it — correct for a test run, wrong for a sweep, where it silently keeps
every run you have ever done. The profiling workloads disable it (`ReportMode.applyTo`);
if you write a workload that builds its own `Runner`, disable it there too.

**The rule: extract the numbers into §6, then delete the run.** A `digest.md` is kilobytes
and is the durable artifact; `run.jfr` and the reports are working files with a lifetime of
one analysis. Keep a `.jfr` only while you are actively running `jfr` recipes (§5) against
it, and a heap dump only while Eclipse MAT is open on it.

```bash
# after recording a result — keeps digests, drops the bulk
find karate-profiling/target/profiling -name 'run.jfr' -delete
find karate-profiling/target/profiling -name 'heapdump.hprof' -delete
rm -rf karate-profiling/target/profiling/*/jfr-repo

# before a sweep — start from clean, and never keep report backups
rm -rf karate-profiling/target/profiling karate-profiling/target/karate-reports*

# check before committing to a long matrix; a 3x3 sweep wants ~10 GB free
df -h .
```

Workloads that generate features or scratch output write under the system temp directory
and clean up in `teardown()` — but `teardown()` is skipped when a run times out or is
killed, which during an investigation is often. Sweep those too:

```bash
rm -rf "${TMPDIR:-/tmp}"/karate-feature-spread-* "${TMPDIR:-/tmp}"/karate-report-cost-*
```

### Flags

| Flag | Default | Notes |
|---|---|---|
| `--threads N` | per workload | Concurrency (virtual threads). For `gatling-*` these become Gatling virtual users instead — see §2. |
| `--iterations N` | per workload | Fixed iteration count, as a TOTAL split across users for `gatling-*`. Mutually exclusive with `--duration`. |
| `--duration 10m` | per workload | Run for a wall-clock window instead. Use for soaks. |
| `--xmx 768m` | per workload | Child heap. **The single most important knob** — a leak that OOMs at 768m may never surface at 4g. |
| `--gc g1\|zgc` | `g1` | See [Reproducing a specific collector](#reproducing-a-specific-collector). |
| `--warmup Ns` | per workload | Excluded from the measured window — the recording is delayed past it. A workload that drives its own concurrency runs no warmup, so nothing is delayed for it; `matrix.sh` discards a whole warmup *run* instead. |
| `--timeout` | duration + warmup + 5m — but a **flat 1 hour** for an iteration-bounded run | Wall-clock cap. On expiry the parent dumps the recording and thread state, then kills the child. **Pass this explicitly for any long iteration-bounded run** — its runtime is unknown by construction, and an iteration count sized at "about an hour" sits right on the kill boundary. The parent prints a note when you are in that case. |
| `--record workload\|mock` | `workload` | Flips which JVM gets the recording, so the profile is of the mock server rather than the load driver. Only meaningful for a workload that uses a mock; `gatling-http-plain --record mock` is the cheaper driver of the two. |
| `--mock feature\|latency` | `feature` | Which mock tier to fork. `feature` is the Karate-feature mock (a *subject* — `--record mock` profiles it); `latency` is `LatencyMock`, the instrument (§10). |
| `--mock-latency 10ms` | none | Injected server latency. Implies `--mock latency`. **This is what makes a parity comparison mean anything** — against a localhost mock both clients queue behind the server and report identical numbers that prove nothing. |
| `--mock-url URL` | none | Use a `LatencyMock` already running elsewhere — **the flag a two-host run needs**. The parent resets the remote mock's counters before the load and scrapes them after. Start the far side with `LatencyMock --bind 0.0.0.0 --standalone --latency 10ms`; without `--standalone` it reads EOF on stdin and exits before the first request. Incompatible with `--record mock`. |
| `--body-size N` | none | The body-size tier (`gatling-body-*` family): both arms send and receive an N-byte JSON document. `compare` buckets on the recorded size, so two sizes cannot average together. |
| `--soak` | off | **Required for any multi-hour run.** Records a much smaller event set so the recording spans the whole run instead of rolling, and starts the live-set/descriptor probe — see [soak mode](#soak-mode--what-a-multi-hour-recording-has-to-drop). Costs you *Allocation by site* and *Hot methods*, which a soak does not read. |
| `--gc-roots` | off | Makes `jdk.OldObjectSample` report reference chains — the *holder* of retained objects, not just the allocating stack. Costs a full reference walk at every sample. |

### Reproducing a specific collector

`--gc zgc` does not mean the same thing on every JDK, and issue #2972 was reported under
**generational** ZGC:

| Child JDK | What `--gc zgc` must expand to |
|---|---|
| 21, 22 | `-XX:+UseZGC -XX:+ZGenerational` — bare `-XX:+UseZGC` is *non*-generational here |
| 23 | `-XX:+UseZGC` — generational is the default |
| 24+ | `-XX:+UseZGC` — non-generational was removed |

The child JDK is recorded in `run-meta.txt`. Always check it before comparing two runs:
this repo targets release 21 but development machines commonly run 24, and that alone
changes what `--gc zgc` did.

---

## 2. Workload catalogue

`etc/run.sh --list` prints this from the code, so it can't drift. Reproduced here with
the reasoning behind each. (The `#2972` framing below is doc-side only — per
[CLAUDE.md](../CLAUDE.md), the `describe()` strings in source must describe *behaviour*,
not cite issue numbers.)

### `call-accumulation` — are completed call results ever released?

60 sequential `karate.call()`s per scenario, results never bound, across many scenarios in
**one** suite. Targets an append-only retention chain:

```
SuiteResult.featureResults        (whole-run lifetime)
  └─ FeatureResult
       └─ ScenarioResult.stepResults
            └─ StepResult.callResults : List<FeatureResult>
```

**The measurement is the scale sweep, not any single run** — run several sizes and compare
peak heap; flat means no accumulation, proportional means there is:

```bash
etc/run.sh call-accumulation --iterations 250
etc/run.sh call-accumulation --iterations 2000
```

This workload owns its suite (`drivesOwnConcurrency`), so `--iterations` means *scenarios in
the suite* and `--threads` is the suite's own parallelism. Its shape is **one feature holding
an N-row `Scenario Outline`** — deliberately the worst case for anything that releases per
feature, which also means it cannot say how an ordinary suite behaves. Always pair it with
`feature-spread`.

*Healthy result:* peak heap roughly flat as scenario count rises. See §6.

### `feature-spread` — the same work, many features

The same total scenario count, spread over many features (default 10 scenarios each) instead
of concentrated in one. **Run them as a pair — neither number means much alone**: they are the
two extremes of identical work, so a change that moves one and not the other has told you
which bound it achieved. This is also the only workload exercising per-feature report writing
more than once, which is what catches queueing and per-feature-lifetime problems. Knobs:
`-Dprofiling.spread.scenarios=N` (per feature), `-Dprofiling.spread.calls=N` (per scenario,
default 60, matching `call-accumulation` so the two differ only in distribution).

### Report modes — `off` | `html` | `all`

Both memory workloads take `-Dkarate.profiling.reports=off|html|all`, or a comma-separated
subset of `html,jsonl,junit,cucumber`. This is three experiments, not a boolean:

- **`off`** — no `ResultListener` at all. Measures execution.
- **`html`** — what `Runner.Builder` gives you by default, i.e. the config users ship. This
  is the one to check against a reported OOM.
- **`all`** — every format. Costs the most work but not the most memory.

`-Dkarate.profiling.reportCost=true` additionally times each report operation per feature and
prints whether a single writer thread could keep up with the suite — see §8.

### `suite-soak` — the enterprise suite shape, for hours

The Runner-lane soak, and the workload [E1](#e1--the-karate-suite-soak-2-hours-reports-on-tls-js-and-feature-calls)
is run with. Many small generated features; each scenario evaluates a config that computes,
calls a shared auth-shaped feature over HTTP, invokes a JS helper, then does POST + GET at the
`LatencyMock` **over TLS** with a closed match. Per-step capture stays on — it is the Runner
default and part of what is under test. It owns its suite, so `--iterations` is *total
scenarios* and `--duration` is refused; size it from a rehearsal and **pass `--timeout`
explicitly**.

```bash
# rehearsal — a probe every 20s instead of every 5 minutes
etc/run.sh suite-soak --iterations 2000 --threads 4 --soak --timeout 30m \
    --mock-url https://<mock>:8443 -Dkarate.profiling.reports=all \
    -Dkarate.profiling.liveSetSeconds=20
```

Knobs: `-Dprofiling.soak.scenarios=N` (per feature, default 10 — never one giant outline,
that shape is known-unbounded by design and would swamp the signal),
`-Dprofiling.soak.suites=N` (**consecutive suites in one JVM** — nothing at all should survive
a suite's end, so this is the sharper leak discriminator and the shape `karate serve` and the
IDE plugin actually have), `-Dprofiling.soak.allowedFailures=N`.

Two connections per scenario, not one: the called feature gets its own runtime and therefore
its own client, which is a second release path with its own way to abandon a socket.
`distinctPeerPorts` in the digest is the check — 2× scenarios, or something is being reused
that should not be.

**A failed scenario does not throw**, so this workload counts failures itself, stops when they
exceed the allowance, exits non-zero, and prints them where the digest can see them (the *Suite
outcome* panel, §3). Without that a soak in which every scenario failed reaches `errors=0`,
exit 0 and a full set of healthy-looking panels — the failure mode §10 is written around.

### Leak-watch family — the leak question, and what is still open

**Separating the cases matters, because it decides what another soak would even be looking
for:**

| | Status |
|---|---|
| Retention that grows with **suite size** | **Fixed and verified** — §8 found two real mechanisms (call-result accumulation, the report-writing queue); peak heap is flat across a 4x scale sweep |
| One feature holding thousands of scenarios, reports on | **Known-unbounded, accepted.** Not a leak: retention by design until suite end. See [per-scenario spill](#per-scenario-spill--designed-reviewed-deliberately-not-built) |
| A slow leak over hours, **pooled Gatling HTTP** | **Answered 2026-08-07 — none detected** (C5 in §0) |
| The **unpooled** client path | 🔴 **Open** — folds into [E1](#e1--the-karate-suite-soak-2-hours-reports-on-tls-js-and-feature-calls), whose Runner suite builds a client per scenario |
| A large Runner suite with **reports on** | 🔴 **Open — [E1](#e1--the-karate-suite-soak-2-hours-reports-on-tls-js-and-feature-calls), the next experiment**; the `suite-soak` workload is built and locally exercised, the bench run is not taken |

#### The false positive every soak walks into by construction

Both soaks run so far showed a **heap-after-GC floor rising monotonically** — 11.4 → 27.8 MB
over an hour in one, +11.0 MB (116%) in the other — and **neither was a leak**. On a long run
with the heap sized well above the working set, G1 never approaches its occupancy threshold,
so every collection is a young evacuation pause (104,980 of 104,980 in the first; 10,140 of
10,170 in the second) and promoted garbage accumulates in an old generation nothing revisits.
The floor climbs identically with nothing leaked.

**Read the live set after a forced full GC, never the floor.** That is what `--soak`'s
live-set probe samples, and `LiveSetPanelTest` pins that the panel can actually report a rise
— the first descriptor panel could not, and an instrument that cannot say the bad thing is not
an instrument.

### The bound-scope-capture pair — regression guard

| Workload | Shape |
|---|---|
| `scope-capture-bound` | 13 sequential bare `karate.call()`s, each result **bound** to a variable, over a ~100-record base payload. |
| `scope-capture-unbound` | Identical, plus `* def capN = null` after each capture. |

These reproduce a reported geometric blow-up in which each capture contains all previous ones.
**That shape does not occur on current main** — a bare call returns only the callee's own
variables — and the pair is kept as a regression guard: if a change ever made a call return
the caller's scope again, `bound` would diverge sharply from `unbound`. Do not read a passing
run as "memory is fine generally"; verify object-graph shape directly with `Probe` (below).

### Gatling parity family — what does driving Karate from Gatling cost?

Every workload here exists to be **compared with its pair** on the same machine, back to back;
a single number means nothing. The families:

| Family | Workloads | The question | Status |
|---|---|---|---|
| null | `gatling-null-{plain,karate}` | What does one `karateFeature()` exec cost before any user work? | diagnostic — isolates fixed per-execution cost with no HTTP in the way |
| http | `gatling-http-{plain,karate}` | A Karate-driven virtual user against a Gatling-native one, POST + GET | **the C1/C2 instrument**; also has `-fat` (plain raised to Karate's checks) and `-lean` (Karate lowered to plain's) equivalence variants — **settled, do not re-run** (§9) |
| body | `gatling-body-{plain,karate,plain-fat}` | Does the deficit scale with payload? Driven by `--body-size` | **settled at 1 KB / 64 KB** (C4); a third size only if the slope becomes a public claim |

These live behind `-Pgatling` — Gatling and its Scala runtime are ~40 MB of classpath the
other workloads have no use for; `etc/run.sh` turns the profile on automatically for any
`gatling-*` workload. Gatling owns the users and the pacing, so these are self-driving:
`--threads` becomes virtual users injected at once, `--iterations` stays the **total** split
across them (rounded up to a multiple of the user count — the child prints what it actually
ran), and `--duration` becomes `during()` in the injection profile. Gatling's own chart
generation is off — it is a second pass over the simulation log that would land in the digest
as if it were load-driving cost.

**Do not "fix" the harness `karate-config.js`** — it deliberately reads an *absent* property
via `karate.properties[...]`, because a missed property read is the expensive shape and this
is the only regression guard on that path. The file's comment says the same; the change looks
like tidying and is not.

**Against the *feature* mock, wall-clock on the http pair measures the mock, not the clients**
— both variants saturate it. Use `--mock-latency` and the `LatencyMock` tier ([§10](#10-method--the-latency-mock-and-the-parity-protocol));
against the feature mock only **allocation** is usable.

#### What "acceptable overhead" means here — and the answer

Not a ratio against plain Gatling picked as a threshold. **The question a load tester actually
has is whether the client's overhead distorts the measurement of the system under test**, and
the practical form of that test is: same target, both clients, compare throughput and the
response-time distribution — *while the injector demonstrably has headroom*, because two
saturated clients queued behind the same overloaded server also report identical numbers.
That headroom condition is what the instrumented mock in §10 exists to establish.

**The answer is in §0**: at 50 ms of server latency, pooled, the deficit is 1.4% at small
bodies and 2.3% at 64 KB, the percentiles are the server's, and the real cost is ~2× injector
CPU. The historical shape is worth one line: the same fixed per-iteration cost is a **2.1–2.4×
throughput gap at 0 ms** — which is why nothing here is measured against a localhost-speed
mock, and why a "Karate is half as fast as Gatling" claim from one is not wrong so much as
about a system nobody load-tests.

### `Probe` — what does a call actually return?

Not a workload; a one-shot diagnostic that prints the *shape* of the variables a feature
leaves in scope, counting distinct container nodes by identity so shared substructure is
counted once.

```bash
mvn -o -q compile
# target/cp.txt is written by run.sh, not by compile — materialise it if you have not
# run a workload yet in this checkout
mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" io.karatelabs.profiling.Probe \
     classpath:workload/probe-forms.feature
```

Use it whenever a memory theory depends on how much a call hands back — a one-run question
about object-graph shape, answered directly instead of inferred from a heap curve.

---

## 3. Reading `digest.md`

The digest has these panels, in this order. Sections are stable, so you can diff two
digests directly.

**Run summary.** Duration, exit status, **build commit** (echoed from `run-meta.txt`; absent
for runs predating the stamp), child JDK, and the child's JVM/GC/heap flags echoed back. Check
this first — the most common analysis mistake is comparing two runs that had different
`--xmx`, `--gc`, a different child JDK, or different source.

**CPU headroom.** What the injector and the mock each burned, **over their own measured
window**. `cores busy` near the machine's cpu count means the run measured the machine rather
than what it was pointed at. The two windows are different windows: read each against `cpus`,
never one against the other. The mock's row is the co-location bias as a number — on a
two-host run it is what shows the mock host was idle.

**Allocation by site.** From `jdk.ObjectAllocationSample`, weighted bytes, stacks collapsed to
`io.karatelabs.*` frames. This answers *what is churning*, not *what is retained* — different
questions, and conflating them is the trap in §4. Attributes correctly across virtual threads.
It is a **top-25 table** with the remainder counted but not listed, so "gone from the panel"
means "below the cutoff" (~1% on these workloads), not zero. It collapses to the topmost
Karate frame, so a site's *callers* are invisible here — that is a `jfr print` with
`--stack-depth` (§5), not a digest read.

**Hot methods.** From `jdk.ExecutionSample`, same collapsing. CPU time, not allocation.
**Read with the virtual-thread caveat in §7 firmly in mind** — for any parallel Runner
workload it under-samples scenario code severely. Trustworthy for the mock JVM and for the
Gatling lane (which runs scenarios inline on platform threads).

**Heap-after-GC series.** From `jdk.GCHeapSummary` — heap after each collection, over time.
See §4 for the churn/leak/live-mid-copy classification — and §2 for why a rising floor on a
soak is usually *not* a leak.

**GC pauses.** Count and histogram. A sharp rise in pause *frequency* (rather than duration)
usually means allocation pressure, not retention.

**Live set (after forced full GC).** Only present for `--soak`. The heap in use immediately
after two forced full collections, sampled every five minutes, beside the **open descriptor
count** (sampled *before* the forced GCs — since JDK 13 a collection closes abandoned sockets,
which is exactly the population a leak hunt is looking for; the `closed by the probe's GC` row
is that difference). **This is the leak panel.** A rising live set is retention; a rising
descriptor count is a leaked socket and can happen with a flat live set; a flat non-zero
descriptor count is healthy — a pool holds descriptors on purpose. The final probe is taken
with load stopped and is excluded from drift.

**Retained objects.** From `jdk.OldObjectSample` — JFR's built-in leak profiler. It samples
sparsely by design and reports the *allocator*, not the *holder* (unless `--gc-roots`); a
one-hour soak produced 19 samples dominated by the harness's own infrastructure threads. **A
detector, not a locator.** To name a leak, use a class histogram against the live child:

```bash
jcmd <child-pid> GC.run && jcmd <child-pid> GC.class_histogram > histo-1.txt
# ...minutes later...
jcmd <child-pid> GC.run && jcmd <child-pid> GC.class_histogram > histo-2.txt
```

The diff names what grew. Find the pid with `jcmd -l | grep profiling.Child` — and note that
`pgrep -f`/`pkill -f` over ssh match the ssh command line carrying the pattern, so they answer
"yes, alive" forever.

**Top classes.** Only present when a heap dump exists — a class histogram read from
`heapdump.hprof`.

**Suite outcome.** Only present for a Runner-lane workload (`suite-soak`). Suites completed,
scenarios, passed/failed, elapsed. **Read it before anything else in such a digest**: a `Suite`
reports a failed scenario in its result rather than by throwing, so every other field —
`errors=0`, exit 0, a full heap series — reads healthy for a run in which nothing worked. A
failed scenario also stops early, so it allocates less, retains less and opens fewer
connections than a passing one, which flatters every panel above it. `SuiteOutcomePanelTest`
pins that the panel can actually say the bad thing.

---

## 4. Symptom → recipe

### First: did the run actually finish?

Before interpreting anything, confirm the run terminated rather than hung. The digest is
produced *on child exit*, so a missing `digest.md` means the child is still alive or died
badly — not that nothing happened.

- **`digest.md` present** → clean exit, interpret normally.
- **`digest.md` absent, `stdout.log` still growing** → still running.
- **`digest.md` absent, `stdout.log` idle** → hung. Karate runs scenarios on a virtual-thread
  executor inside try-with-resources, and that `close()` waits indefinitely; an
  `OutOfMemoryError` swallowed in a worker can leave the child alive-but-dead forever.
  The parent's `--timeout` handles this automatically, but if you are attached manually:

  ```bash
  jcmd <child-pid> Thread.print            # where is it stuck?
  jcmd <child-pid> JFR.dump filename=rescue.jfr
  ```

- **`run.jfr` present but 0 bytes / unreadable** → the JVM died without flushing. Recover
  from the live chunk repository:

  ```bash
  jfr assemble jfr-repo/ rescue.jfr
  jfr summary rescue.jfr
  ```

### Did it OOM?

Exit code alone is **not** sufficient — Karate can swallow a worker `OutOfMemoryError` and
still exit 0. Check, in order: `heapdump.hprof` exists (the primary signal), then
`grep OutOfMemoryError stdout.log`. The digest reports both.

### Telling churn from a leak from a live-mid-copy

Three different failures look similar from the outside and need three different fixes.
The **heap-after-GC series** distinguishes them in one glance:

```
churn                leak                  live mid-copy
(allocates a lot)    (retains a lot)       (one huge in-flight structure)

 /|/|/|/|/|/|          /|  /|  /|/|          ▁▁▁▁▁▁▁▁█  ← OOM, no warning
▁▁▁▁▁▁▁▁▁▁▁▁         ▁▁▁▂▂▂▃▃▃▄▄▄
constant floor       rising floor          flat floor, then a cliff
```

- **Constant floor, any sawtooth amplitude → churn.** Nothing is leaking. Look at
  *Allocation by site*. Fix by allocating less.
- **Rising floor → retention, _but check what collected_ first.** Only valid if something
  actually collected the old generation during the run — on a soak it usually did not, and
  the floor is promoted garbage (§2). Prefer the **Live set** panel from a `--soak` run, then
  *Retained objects*, and if it OOMs, *Top classes* from the heap dump.
- **Flat floor then an abrupt cliff → live mid-copy.** Not a leak at all: a single structure
  being built right now is larger than the heap. The tell is that the heap dump shows the
  bulk of the live set on **one thread's stack locals**, in a deep self-recursion. Issue
  #2972 was exactly this, and the first two rounds of diagnosis went to the wrong mechanism
  because only the class histogram was consulted, not where the objects were rooted.

#### The standing constraint: footprint is fine, a rising floor is not

**Using more memory is acceptable as long as it is collected and the floor stays flat.** That
is a design licence: sawtooth amplitude is not a budget anyone has to defend, and trading
allocation for retained-but-released memory is a fair trade. What is not acceptable is
anything that moves the floor.

Two consequences that cut in opposite directions:

- **It clears a whole class of objection to caching.** A cache reachable only from something
  with a bounded lifetime — a Suite, a parsed `Feature`, a `Step` — is footprint, not leak.
  Do not design an LRU for one of those.
- **It does not clear a keyed cache in a long-lived process.** `karate serve`, MCP and the
  IDE plugin outlive any Suite, and a map keyed on generated text grows for as long as they
  run. That is a rising floor — the fact that it is called a cache does not change what the
  heap-after-GC series will show.

### "A long scenario OOMs"

1. `etc/run.sh scope-capture-bound` — does the known shape reproduce on this machine?
2. Read *Heap-after-GC* and classify with the chart above.
3. If it is live-mid-copy: in the heap dump, find what holds the largest retained size and
   check whether the root is a thread stack. A geometric decay down the recursion (each level
   roughly half the one above) means level N *contains* level N+1 — nesting, not repetition.
   Karate runs scenarios on virtual threads, so an unmounted thread's frames root at a
   heap-allocated `jdk.internal.vm.StackChunk` — the same finding wearing a different hat.
4. Compare against `scope-capture-unbound`. If unbinding fixes it, the driver is the number
   of *bound collections*, not the number of calls.

### "Turning reports on costs far more memory than running the tests"

Check for a **producer/consumer race before assuming retention**. The tell is that the number
is *unstable*: two identical runs differing by hundreds of MB means you are measuring a queue
depth, not a live set. Retention is boringly reproducible; races are not.

1. Run with `-Dkarate.profiling.reportCost=true` and read **writer-thread load** — total
   deferred work divided by suite wall-clock. Above 1.0, one thread cannot keep up and its
   queue grows for the entire run.
2. In *Retained objects*, a race shows up as `[B` attributed to a **rendering or
   serializing** site, not to result-model classes. Result objects mean retention; rendered
   strings mean a queue.
3. The counter-intuitive confirmation: enable *more* formats. If peak heap goes **down**,
   there is no leak — the extra work slowed the producer enough for the writer to keep up.

The fix for this class of problem is not to bound the queue but to remove it: do the work on
the thread that produced it. See §8.

### "Karate got slower"

1. Run the same pair on the suspect commit and on its parent — `call-accumulation --iterations
   2000` and `feature-spread --iterations 2000`; a change that moves one and not the other has
   already told you which shape it affected. For a CPU-shaped regression under Gatling, the
   `gatling-null` pair isolates fixed per-execution cost with no HTTP in the way. For "slower
   over time" as opposed to "slower per iteration", a `--duration` + `--soak` run on both
   commits is the better instrument.
2. Diff the two `digest.md` files — *Allocation by site* first, *Hot methods* second.
   Allocation sampling is trustworthy under virtual threads; CPU sampling largely is not (§7).
3. Ignore wall-clock differences under ~10% on a laptop; see §7.

### "Is the mock server fast enough?"

`--record mock` puts the recording on the mock JVM, attributing cost to gherkin matching, JS
evaluation and response building — and it is the one configuration where *Hot methods* is
fully trustworthy (the mock serves on platform threads).

```bash
etc/run.sh gatling-http-plain --record mock    # cheapest driver available
```

### "Something is retained but I can't see what"

*Retained objects* is the cheap first look. If it is too coarse, force the heap-dump path
by lowering `--xmx` until the workload OOMs, then read *Top classes*. Deliberately
provoking the OOM is usually faster than reasoning about a run that merely grows.

---

## 5. Raw `jfr` recipes

For drilling past what the digest anticipated. **These produce a lot of output** — always
pipe through a reducer, never dump a whole event type into your context.

```bash
cd target/profiling/<workload>-<timestamp>

# What's even in this recording, and how much of it?
jfr summary run.jfr

# Allocation, by allocating type, biggest first.
# Note: --json emits JVM-internal class names (java/util/LinkedHashMap, [B, [I),
# not the dotted names the plain-text `jfr print` shows.
jfr print --events jdk.ObjectAllocationSample --json run.jfr \
  | jq -r '.recording.events[].values | "\(.weight) \(.objectClass.name)"' \
  | awk '{a[$2]+=$1} END {for (k in a) printf "%15d  %s\n", a[k], k}' \
  | sort -rn | head -25

# Hot Karate frames (karate packages only). See the §7 virtual-thread caveat.
jfr print --events jdk.ExecutionSample run.jfr \
  | grep -o 'io\.karatelabs\.[A-Za-z0-9_.$]*\.[a-zA-Z0-9_]*' \
  | sort | uniq -c | sort -rn | head -25

# Heap-after-GC floor over time — the §4 chart. GCHeapSummary emits a
# "Before GC" and an "After GC" row per collection; the floor is the latter.
jfr print --events jdk.GCHeapSummary run.jfr \
  | grep -A6 'when = "After GC"' | grep -E 'startTime|heapUsed'

# What survived, and who allocated it
jfr print --events jdk.OldObjectSample run.jfr | head -200

# Which threads were doing what
jfr print --events jdk.ThreadPark,jdk.JavaMonitorEnter run.jfr | head -100
```

Heap dump, when one exists: **`jhat` was removed from the JDK in Java 9 — do not reach for
it.** Use the digest's *Top classes* panel first; for anything deeper (dominator tree,
retained sizes, reference chains) open `heapdump.hprof` in Eclipse MAT or VisualVM. There
is no JDK CLI that reads an `.hprof` file — `jmap -histo` only works against a *live*
process.

Two recording options worth knowing about:

- `-XX:FlightRecorderOptions:stackdepth=128` — **always applied** by the harness. The JVM
  default of 64 truncates Karate-through-JS stacks, which collapses distinct allocation sites
  into one.
- `path-to-gc-roots=true` — makes `jdk.OldObjectSample` report reference chains (the
  *holder*). Opt-in per run via **`--gc-roots`**, because it costs a full reference walk at
  every sample.

---

## 6. Current baseline

Hand-maintained. Update it when you take a run you trust, and always record the machine, the
**build commit** and the child JDK — absolute numbers are meaningless without them; only
*shapes* and *ratios* travel. The parity headline numbers live in [§0](#the-claims-register)
and are not repeated here.

### What is settled, and what is not

| | Status |
|---|---|
| **Fixed and verified** | Parallel-execution *memory* on ordinary suite shapes: call-result retention (flat across a 4x scale sweep) and the report-writing queue (~4.5x → ~1.3x, wall-clock fell). The external reproducer passes at `-Xmx768m`. |
| **Measured, known-unbounded, accepted** | One feature holding thousands of scenarios, with reports on — still linear (502 / 1108 / 2079 MB). Only per-scenario release changes the slope; §9 records why that was not built. |
| **Measured (parity + leak, Gatling lane)** | The §0 claims register: 1.4% at 50 ms TLS pooled, sub-linear payload scaling, no leak over 1.36 M pooled iterations. |
| **Never measured** | A Runner-suite soak with reports on, and the unpooled client path under sustained load — both now folded into [E1](#e1--the-karate-suite-soak-2-hours-reports-on-tls-js-and-feature-calls); CPU inside scenario code under a *parallel Runner* suite — `jdk.ExecutionSample` is blind there (§7), not in the Gatling lane. |

**Machine A** — Apple Silicon (aarch64), Darwin 25.5, 10 cores, JDK 24.0.2, G1.
**Machine A2** — the EC2 bench: `c7g.4xlarge` ×2, Graviton3/aarch64, AL2023, JDK 24.

#### `call-accumulation` scale sweep — machine A, 60 calls per scenario, 16 threads

Peak heap, reports off. **Before** = 2026-08-04, karate 2.1.2.RC1 as released.
**After** = the same build with completed call results released at scenario end.

| scenarios | before (`-Xmx3g`) | after (`-Xmx3g`) |
|---:|---:|---:|
| 500 | 343 MB | 209 MB |
| 2000 | 859 MB | 272 MB |
| 5000 | exceeds 768m, saturates | **233 MB at `-Xmx768m`, 5.3s** |

Before, peak grew ~143 KB per scenario, held for the whole suite. After, a 4x increase in
scenarios moves peak heap barely at all. **Flat is the property to check on any future run.**

#### Reports on — machine A, `-Xmx3g`, 16 threads, 60 calls per scenario

Peak heap, before and after the report-writing changes in §8.

**`feature-spread` — 200 features x 10 scenarios (the ordinary suite shape):**

| scenarios | off | **html before → after** | all before → after |
|---:|---:|---|---|
| 500 | 222 MB | 564 → **349 MB** | 210 → 372 MB |
| 1000 | 331 MB | 1231 → **415 MB** | 297 → 242 MB |
| 2000 | 482 MB | 2093–2489 → **618 MB** | 426–475 → 400 MB |

**`call-accumulation` — 1 feature x N scenarios (the mega-outline shape):**

| scenarios | off | **html before → after** | all before → after |
|---:|---:|---|---|
| 500 | 230 MB | 1330 → **502 MB** | 1755 → 1121 MB |
| 1000 | 253 MB | 2852 → **1108 MB** | 2334 → 1984 MB |
| 2000 | 355 MB | 2690 (saturating) → **2079 MB** | 2995 → 3017 MB |

Read together: **the ordinary shape is bounded** (reporting now costs ~1.3x running the tests,
and wall-clock *fell* — the work removed was larger than the parallelism lost); **the single
mega-outline shape is still linear** — a known, accepted limit (§9). Ignore the `all` column
moving the wrong way in three cells: those before-numbers were artificially low because JSONL
was accidentally throttling the producer (§8) — never a budget to regress against. The
before-`html` range at 2000 was not reproducible run to run; that instability was itself the
symptom.

#### Cross-check against the external reproducer — machine A, `-Xmx768m`, 5000 scenarios

| variant | karate 2.0.10 | 2.1.2.RC1 as released | with the fix |
|---|---|---|---|
| J (13 bound captures) | heap pinned, killed at 10 min | passed, 1.96s | passed, 2.7s |
| C (60 calls via `karate.repeat`) | OOM | saturates | **passed, 6.5s** |
| E (60 calls, individual statements) | OOM | pinned at 99.8%, killed | **passed, 6.7s** |

Two distinct mechanisms, and only measurement separated them: the scope-capture nesting J was
written to demonstrate was already gone before this work started; the call-result accumulation
the reporter had *retracted* was the one still live.

#### Gatling per-execution cost — machine A, `-Xmx1g`, G1, JDK 24

The `null` pair (no HTTP), measured at two sizes so the marginal separates from startup: one
`karateFeature()` exec costs **~0.45 ms of CPU** (build a Suite, parse the feature, evaluate
`karate-config.js`, run one scenario, hand the session maps back) against ~0.02 ms for a no-op
Gatling exec, plus ~2 core-seconds of one-time initialisation per JVM. CPU measured directly
under `/usr/bin/time` with JFR off — wall-clock × concurrency is *not* CPU. **That figure
predates the allocation fixes below and has not been re-taken**; sampled allocation has since
fallen ~30% (1.71 GB → 1.01–1.12 GB), and re-taking the `/usr/bin/time` replay is the first
thing to do if anyone quotes the per-exec cost again.

Where it went, and what was done (all built and in the code; before-and-after measured at
20000 iterations): the per-scenario Logback level snapshot (10% of the profile — now lazy);
happy-path exceptions — `karate.properties['x'] || 'default'` cost four exceptions and three
`getMethods()` copies per read to answer "no" (now cached member resolution, a probing
`Resource.optional`, a live properties view — no exception constructor left in the panel);
the in-memory resource encoding its text to bytes nothing read (gone); config parsed once per
Suite instead of per scenario; and per-step log capture gated off in the Gatling lane before
the string is built (override with `Runner.Builder.captureStepLogs`; design in
[GATLING.md §14.9](./GATLING.md)). *The lesson that generalises: a miss is an answer, not an
event — and costs surface in sequence, so expect the profile to reveal a new top entry after
each fix rather than simply shrinking.* What remains at the top is
parsing — mostly **JS, not Gherkin** (three quarters of `BaseParser.<init>` sits under
`JsParser` re-parsing `karate-config.js`/step expressions) — measured and deliberately parked:
see [parsed-JS reuse](#parsed-js-reuse--measured-half-gated-not-built).

The `http` pair on the same machine: **roughly 2–3x the sampled allocation for the same 4000
requests** (190–215 MB plain vs 460–560 MB karate; a sampled magnitude, not a measurement).
The Karate HTTP client is the bulk — `ApacheHttpClient.invoke`, `buildResponse`, and
`initHttpClient` at ~5–6%, the last being the per-execution client construction that
`pooledConnections()` now makes optional. Per-step log capture is off by default in this lane;
the `Json.parseLenient` / `LogContext` rows disappeared with it.

*Baselines are shapes, not thresholds. Absolute numbers move with hardware and JDK; the
linear trend and the ratios are what travel.*

---

## 7. Caveats

- **CPU sampling barely sees virtual threads, and a parallel Runner suite puts every
  scenario on one.** `jdk.ExecutionSample` samples platform threads; a Runner-driven
  workload can produce single-digit sample counts over seconds of saturated CPU. The *Hot
  methods* panel is near-useless for scenario code there, and absence proves nothing. Prefer
  *Allocation by site*, which attributes across virtual threads. *Hot methods* IS trustworthy
  for the mock JVM (`--record mock`) and for the whole Gatling lane — `Runner.runFeature`
  builds a non-parallel Suite, so the scenario runs inline on the Gatling thread.
- **Throughput numbers off a laptop are shape-only.** Thermal throttling, other processes and
  the mock sharing the cores make absolute req/s unusable — and ratios only mean something
  when the thing being ratioed is the bottleneck. Two clients saturating the same mock report
  identical numbers that ratio to exactly nothing. With `--mock-latency` and a calibrated
  mock (§10) the ratios mean something; the definitive numbers came from the EC2 bench, and
  §10 records why no pair count fixes a noisy machine. Which mock tier a `gatling-http-*` run
  used, and the gap since the previous run, are recorded in `run-meta.txt` (`mock:` /
  `since prev:`) — runs predating those fields need `mock.log` checked instead, since only
  `LatencyMock` writes a `PROFILING-MOCK-CONFIG` line.
- **Collector artefacts are not findings.** ZGC returns committed memory to the OS, so
  "committed shrank" is ZGC behaving normally. G1 and ZGC populate `jdk.GCHeapSummary` and
  `jdk.OldObjectSample` differently — never compare a G1 digest against a ZGC one. And
  `--gc zgc` means different things on different JDKs (§1).
- **`settings=profile` is not free.** A few percent, biased toward whatever allocates in
  large enough chunks to be sampled. Fine for finding a 10× problem; not a microbenchmark.
- **Sampling is sampling.** A site absent from the digest allocated too little to be sampled
  during the window — not nothing.
- **Warmup matters.** A digest dominated by class loading and JIT means the warmup was too
  short for what you ran.
- **These runs eat disk, and a full disk ruins a matrix.** See
  [Disk hygiene](#disk-hygiene--prune-after-every-result) — a precondition for a sweep
  completing, not housekeeping advice.

---

## 8. What the parallel-execution memory investigation settled

Kept because two of these findings reversed a confident, well-argued reading of the code.

A user reported an OOM under parallel execution on 2.0.10, heap dump showing 89% of a 3.87 GB
live heap in the stack locals of a 13-deep self-recursion. Three mechanisms were on the table;
only measurement separated them:

| Mechanism | Verdict |
|---|---|
| Scope-capture nesting (each capture containing all previous) | **Already fixed before the investigation began** — `Probe` measured it directly: every call form returns 2 container nodes |
| Retained call results (`SuiteResult → … → callResults`) | **Real.** ~143 KB per scenario held for the whole suite; released at scenario end now |
| Report writing | **Real, and the largest — and not retention at all.** Each report listener owned a single-thread executor with an unbounded queue; rendering one feature's HTML cost ~3.4x the suite's wall-clock summed over features, so the queue grew for the whole run, holding a full page model per entry. Evidence: enabling *more* formats used *less* memory (JSONL throttled the producer into range); 99.6% of retained bytes were rendered strings, not result objects. **Fix: write on the feature's own thread** — N-way parallelism beat one background thread and wall-clock *fell*. `HtmlReportWriter` also stopped splicing the large report data first (each later `String.replace` copied the whole page) and stopped pretty-printing JSON nothing but the page's own JS reads |

Lessons that generalise:

- **An unstable number is a race.** Retention reproduces; queue depth does not. Two identical
  runs 400 MB apart is a diagnosis, not noise to average away.
- **Allocation-site attribution names the allocator, never the holder.** Deciding what to
  change needs the holder — `--gc-roots`.
- **One workload shape will mislead you.** `call-accumulation` is a single mega-feature, so it
  silently scores every per-feature strategy at zero; `feature-spread` exists because a design
  was nearly chosen on the evidence of the one shape that forced it.
- **Measure the cost before designing around it.** "Writing HTML inline would block execution"
  was the premise behind the queue that caused the leak; one measurement disproved it.

---

## 9. The steering surface

**Read this section first when planning a session.** Open experiments in priority order, each
with its question, decision rule and cost; then what is settled and must not be re-run; then
what is parked on evidence. Finished work is stated as a result and its plan text deleted —
the code and git history record how it was built.

### Before any session — verification runs

**A script that has not been run since it was last edited is unproven** — five for five were
broken on 2026-08-07, and not one was visible without running. Fold this into the *start* of
the next session, before anything that produces a number (~15 min, ~$0.30):

```bash
etc/ec2/selftest.sh                       # free, no bench: the collect guard's six cases
etc/ec2/provision.sh && etc/ec2/bootstrap.sh          # full bootstrap first: --sync implies
                                                      # --rebuild and skips package install,
                                                      # so it fails on a fresh host
etc/ec2/bootstrap.sh --sync               # then sync. CHECK: the digest of the next run must
                                          # carry `| build | <sha> +DIRTY |` — if it does not,
                                          # the sync shipped source the build ignored
etc/ec2/calibrate.sh --tier 10ms --ramp 1,4 --per-user 40 --settle 5s
                                          # CHECK: $KP_RESULTS/calibration-10ms-*.txt exists
etc/ec2/matrix.sh --tier 10ms --pairs 2 --iterations 400 --users 4 --label verify
etc/ec2/collect.sh                        # CHECK: "every run on the injector has its digest here"
```

Then prove the collect guard actually fires, which is the whole point of it:

```bash
# Named suite-soak-*, not gatling-*: the probe used to match only the latter, so a
# gatling-named plant can no longer prove the branch that matters for a soak.
etc/ec2/ssh.sh injector 'mkdir -p ~/karate/karate-profiling/target/profiling/suite-soak-2026-01-01-000000'
etc/ec2/collect.sh; echo "exit=$?"    # MUST be 1, naming that directory
etc/ec2/ssh.sh injector 'rm -rf ~/karate/karate-profiling/target/profiling/suite-soak-2026-01-01-000000'
```

**Exercised 2026-08-07 (~16:45–17:10 UTC), in a short second bench session**: `--sync`
shipped the then-uncommitted fixes and the runs' `build: 745a408 +DIRTY` stamps prove the
build used them; the calibration archived itself to `$KP_RESULTS`
(`calibration-10ms-2026-08-07-164553.txt`); the collect guard was fired on a planted
digest-less directory; and three duration-bounded rehearsals ran (100 s / 4 m / 200 s — the
4 m one being the BUSY-branch live-test shape).

**Exercised again 2026-08-08 (~30 min, ~$0.58)**, after the E1 development, because the
scripts it touched were in exactly the "edited but not run" state that was five for five
broken the previous session. Everything above was re-run and passed, plus the two things that
were still open: **`matrix.sh`'s `find -newer` run identification is now proven** — a 2-pair
10 ms matrix discarded its warmup run by name and parked four runs under its label — and the
**widened collect guard fired on a planted `suite-soak-2026-01-01-000000`**, exiting 1 and
naming it, which is the branch no `gatling-*` fixture could reach. `teardown.sh` refused
while run directories existed and only `--force` got past it. The TLS calibration at 50 ms
archived itself and is clean: ko 0 throughout, both baseline repeats matching (−0.05 / −0.13),
no knee to 8 users, and close mode pricing a TLS connection at ~2.9 ms over keepalive — which
is the envelope the suite soak's client-per-scenario arm actually runs in.

### Open experiments, in priority order

**The Gatling arc is PAUSED as of 2026-08-07.** The §0 claims are sufficient for now; the
next work is the karate-core suite soak below. The paused experiments keep their designs
(E2–E4, further down) so they can be resumed without re-derivation — do not start them
before E1 is done.

#### E1 — the karate suite soak: 2 hours, reports on, TLS, JS and feature calls

**The question.** Does a long-running Karate *Runner* suite — the shape an enterprise
regression suite actually has — retain memory or descriptors beyond what reporting is
*designed* to retain? This is the "no memory leak in karate" claim, and nothing measured so
far touches it:

| workload | HTTP? | reports? | soakable? |
|---|---|---|---|
| `gatling-http-karate` (the 2026-08-07 soak) | yes | **no** — that lane gates step capture off | yes |
| `scope-capture-bound` (1 h, 35.7 M soak) | **no** | no | yes |
| `call-accumulation` / `feature-spread` | **no** | **yes** | drive their own suite; sized by iterations, not a window |

**One run answers three open questions, which is why it was promoted:**

- **the reports-on retention question** — the largest gap in the leak table (§2);
- **the unpooled client lifecycle under sustained load** — a Runner suite builds an HTTP
  client per scenario, karate-core's default, so two hours of per-scenario connect → use →
  close **over TLS** is exactly the soak the `fds`/`fdsAfterGc` instrument was built for.
  In pooled mode a missed release cannot abandon a socket (the pool owns them); here it can,
  and would show as a growing gap between `fds` and `fdsAfterGc`. This absorbs the
  previously-planned separate unpooled soak;
- **realism** — the features exercise `karate-config.js` functions, a shared-feature
  `call` per scenario, a JS helper invocation, and HTTP over TLS, so the workload is shaped
  like what enterprises run rather than like an instrument.

**Design.** Two-host EC2 bench, mock on the second host — load-bearing, not a default: with
per-step capture on, the step log holds the rendered HTTP request and response text, so the
retention most worth watching is precisely the captured bodies, and a no-HTTP suite cannot
produce them. A new Runner-lane workload, modeled on `FeatureSpreadWorkload`:

- **many small features** (never one giant outline — that shape is known-unbounded by design
  and would swamp the signal), generated at setup with the mock URL baked in;
- each scenario: config evaluation (a `karate-config.js` with an env read and two JS
  functions), a `call` to a shared auth-shaped feature, one JS helper invocation, then the
  POST + GET at the `LatencyMock` **over TLS** with a closed match. Per-step capture stays
  on — it is the Runner default and the thing under test;
- **iteration-bounded, not duration-bounded**: the workload drives its own Runner suite, so
  `--duration` is rejected for it — size the total scenario count from the rehearsal's
  measured rate to land ≈2 h, and pass `--timeout 3h` explicitly (iteration-bounded runs
  default to a flat 1 h). The rehearsal itself is iteration-bounded too, from a guessed
  scenario count — the timeout makes a bad guess safe;
- `--soak`, `-Dkarate.profiling.reports=all` (HTML, JSONL, JUnit XML, Cucumber JSON).
  Threads and tier come out of the budgets below, not out of a throughput ambition.

**Pre-register two budgets from a ~10-minute rehearsal, because "no leak" here is not
"flat":**

1. **Heap.** A suite retains per-scenario/per-feature result skeletons — including the
   captured request/response text — until suite end, *by design*. Measure
   retained-per-scenario from the rehearsal's live-set series, predict the 2-hour end state,
   and choose threads × tier × scenario count so the prediction fits the heap with margin
   (expect this to push toward the 50 ms tier and moderate thread counts — the designed
   linear growth, not wall clock, is the binding constraint; `--xmx` can also simply be
   raised, the host has 32 GB). The claim's falsifiable form: **live-set slope ≈ the
   predicted designed growth, descriptors flat, `closed by the probe's GC` ~0.** Unexplained
   excess → class histograms (§3) and a `--gc-roots` run.
2. **Disk and connections.** `reports=all` writes per-feature output for the whole run on a
   **40 GB** volume — measure report-directory growth per feature in the rehearsal and size
   accordingly (`backupOutputDir` is already disabled by `ReportMode`). And each scenario
   opens its own connection with a TLS handshake, so check the scenario rate against the
   connection ceiling `run-meta.txt` derives for the host.

**Development — done, and exercised locally.** What was built, and what each thing is for:

1. **The `suite-soak` workload** ([§2](#suite-soak--the-enterprise-suite-shape-for-hours)),
   including the repeated-suites adjunct as `-Dprofiling.soak.suites=N`. Verified locally
   against a forked TLS `LatencyMock`: 100 scenarios, 0 failures, `reports=all`, and
   `distinctPeerPorts` = 200 — the two-clients-per-scenario shape the unpooled question is
   about, counted rather than assumed. Reports cost ~45 KB/scenario on disk, so the 40 GB
   volume is not the binding budget; heap is.
2. **TLS plumbing already existed** — `--mock-tls` for a forked mock, or
   `mock.sh start <tier> <port> tls` plus `--mock-url https://…` on the two-host bench.
   karate-core trusts all certificates by default, so the self-signed mock needs no
   `configure ssl` in the features.
3. **`collect.sh`'s completeness probe now matches the run stamp, not `gatling-*`** — the
   old pattern left every non-Gatling workload outside the guard, including the one whose
   two hours produce a single artifact. The inventory moved into `lib.sh` as
   `kp_inventory_script` precisely so `selftest.sh` runs the *exact* text against a fixture
   tree: a digest-less `suite-soak-*` is now seen, label directories and `jfr-repo/` still
   are not. That closes the "an unproven guard is not a guard" trap without bench time.
4. **Two instruments the Runner lane did not have**, both of which the doctrine in §10
   demanded and neither of which existed: the *Suite outcome* panel (§3), because a suite
   reports failures in its result rather than by throwing and the digest could not otherwise
   say a run failed; and **reconciliation for a non-Gatling lane** — `appendReconciliation`
   returned early without a Gatling report, so the "nothing was dropped between injector and
   handler" check silently did not exist for the only workload that runs for hours. It now
   compares passing scenarios × requests-per-scenario against the mock's `served`, and
   refuses to compare them at all when anything failed.

**The rehearsal — taken 2026-08-08**, two-host bench, 50 ms TLS mock, `reports=all`,
4 threads, 4,000 scenarios, `66486a7 +DIRTY`. It ran clean (0 failures, exit 0, 12,000
requests reconciling exactly against the mock's `served`) and it is the sizing evidence:

| measured | value | what it constrains |
|---|---|---|
| scenario rate | **24.3/s** at 4 threads | how many scenarios fit in 2 h |
| live-set slope under load | 43.4 → 48.8 MB over 3,400 scenarios — **~1.6 KB/scenario** | heap. Far below the 143 KB/scenario of the mega-outline shape, because these scenarios are small |
| live set after the suite ended | **12.5 MB**, from 48.8 | the designed retention *is* released at suite end — the shape a leak would break |
| descriptors | **flat at 151**, `closed by the probe's GC` 0, over 12,000 TLS requests with a client per scenario | early evidence on the unpooled question |
| reports on disk | 133 MB / 4,000 scenarios — **34 KB/scenario**, 37 GB free | **the binding budget**, not heap |
| injector CPU | **0.29 of 16 cores** | thread count is free to rise |

**So the 2 h soak is disk-bound, and the sizing follows from that**: at 8 threads (≈49/s)
two hours is ~350,000 scenarios → ~12 GB of reports and ~560 MB of designed retention, both
comfortable. 16 threads would reach ~700,000 scenarios and ~24 GB, which is inside 37 GB but
without much margin for a run that overshoots. `--xmx 8g`, `--timeout 3h`.

**Run it with `-Dprofiling.soak.suites=4` rather than one long suite.** The rehearsal showed
why: retention climbs by design and collapses at suite end, so four ~30-minute suites produce
four ramps that each return to the same floor — and a floor that creeps between them is a leak
with no interpretation needed. One monotonic ramp can only be read against a predicted slope,
which is the weaker claim.

**Cost.** ~2.5 h of bench (~$2.90 including the rehearsal already taken). Read it per §3's
Live set panel — the heap-after-GC floor will rise and mean nothing, as it has in both soaks
so far (§2).

### Paused — the Gatling arc

Paused 2026-08-07: the §0 register answers the parity question well enough for now, and
resuming any of these is a deliberate decision, not a default. Designs kept so nothing is
re-derived.

#### E2 (paused) — the enterprise parity cell: a realistic workload at 50 ms

Every measured parity cell runs a deliberately minimal feature; a real suite's config
computes things and its features call shared features and JS helpers — work with no
vanilla-Gatling analogue. The cell: one new pair at 50 ms / TLS / pooled / 8 users /
10 pairs with a ~1 KB body. Karate arm: config functions, **auth via `karate.callSingle`**
(karate-gatling shares one `callSingleCache` across executions — `KarateProtocolBuilder` —
so the cell also verifies that amortisation empirically), a JS helper per iteration, padded
POST + GET with an auth header and a closed match. Plain arm: the same requests written the
way a Gatling user would (token fetched once, a session function for the id). **Deliberately
not an equivalence cell** — the arms do *idiomatic* work, so the difference prices the
authoring model, which is the number the enterprise claim should quote. Decision rule:
≤1.5% → the §0 thesis holds as worded on a realistic workload; 1.5–2.5% → the claim gains an
itemised qualifier (the allocation panel names the cost — expect JS parse/eval); >2.5% →
profile before claiming. Harness work: two features, a config, two workload classes, a
`matrix.sh` family flag; then ~35 min of bench.

#### E3 (paused) — density vs run length: 8 users × 6400 over TLS

The clearest confound in the published table: the 8- and 32-user cells differ in *both*
density and total iterations, and per-iteration CPU fell on both arms between them — the
signature of a fixed per-run cost amortising. One cell at 8 users × 6400 iterations (TLS,
pooled) separates them: deficit ≈1.0% → run length (JIT warm-up amortising); ≈1.4% →
density. 5 pairs suffice (the effect gap is ~0.4 ms against sd ≤0.1); ~19 min — note an
8u × 6400 run is an ~82 s window.

#### E4 (paused) — capacity: the knee, and open-loop arrival

Two cells turn C7 from a caveat into sizing guidance: **TLS at 64 users** (at/above the
calibrated knee — does the 2× CPU stop being absorbed by idle cores, and where does the
deficit go when it stops?), and **open-loop arrival** (`constantUsersPerSec` — overload
behaviour, without the closed loop's self-throttling safety net; the calibrated knee is void
for an open-loop cell until re-calibrated open-loop). Run only when capacity guidance is
about to be published; ~20 + ~30 min.

### Bench budget

Two `c7g.4xlarge` are ~$1.16/hr; provision + bootstrap is ~6 min of every session. The next
session is: verification runs (only if a script changed since they last ran) → E1 rehearsal →
E1 soak.

| | settles | bench time | ~cost |
|---|---|---:|---:|
| **E1 suite soak** (rehearsal + 2 h) | "no leak in karate" — reports on, TLS, and the unpooled client lifecycle | ~2.5 h (dev done) | ~$3 |
| E2 enterprise cell *(paused)* | the thesis on a realistic workload | ~35 min (+ harness work) | $0.70 |
| E3 8u × 6400 TLS *(paused)* | the density/run-length confound | ~19 min | $0.40 |
| E4 knee + open-loop *(paused)* | capacity guidance | ~50 min | $1.00 |
| verification runs | that edited scripts still work | ~15 min | $0.30 |

### Settled — do not re-run

Each entry: the result, and what would have to change to reopen it. Re-running any of these
without a reopening condition is spend without information.

- **The 2026-08-07 first pass** (one session, one pair of hosts, build `745a408`, 3h02m,
  ~$3.55) — the §0 claims register rows C1–C5, C7. Every cell pooled, 0 KO, ports == users
  throughout. The TLS calibration licensed the cells (keepalive knee at 64; a connection
  ~2.7 ms over TLS vs ~0.2 plaintext — which is *why* pooling makes TLS free: the cost is
  per-connection, and pooling removes the connections). The soak's integrity block:
  `elapsedMs` filled its window, `truncated=false`, child exit 0, 13/13 probes valid.
  *Reopens if:* the client stack, the JS engine or the parser changes materially — re-run the
  ordinary 50 ms TLS cell as a regression check, nothing else.
- **The equivalence controls (fat/lean)** — raising vanilla Gatling's checks (+1.49) and
  lowering Karate's (+1.43) both leave 1.4%; at 64 KB the fat control splits the slope
  (~0.28 ms of it is Karate comparing bytes plain never compares; ~0.59 ms is like-for-like).
  **The assertion-depth question is answered. Do not re-run these as a micro-optimisation
  exercise** — the deficit is per-execution cost, not matching cost. *Reopens if:* the match
  engine is rewritten, or a body size ≥256 KB becomes a public claim (then run the fat
  control at that size, interleaved).
- **The unpooled A/B** — pooling is worth ~24% of Karate's overhead on plaintext (n=10 per
  cell, corroborated by whole-process CPU with the untouched plain arm as control). Settled;
  karate-gatling ships pooled for load tests. *Reopens:* never for karate-gatling; the
  unpooled *leak* question folds into E1, a different question.
- **The 10 ms tier** — retired. The baseline moved between sessions (+1.79 → +1.52) and the
  artifacts of that era cannot say which build produced which. The thesis is 50 ms and above,
  where the figure reproduces across sessions. Do not quote a 10 ms number. The provenance
  gap that made it undecidable is closed (`build:` in run-meta since `6e94645e3`, echoed into
  the digest) — but runs predating the stamp still have no build line, so comparisons
  reaching back past it stay undecidable and must say so.
- **Harness fail-closed items — done 2026-08-07.** `collect.sh` compares digest *sets* (a
  digest present remotely and absent locally is fatal; a run with no digest is fatal only
  when nothing is running — `selftest.sh` covers the six cases). `calibrate.sh` archives its
  table to `$KP_RESULTS` — it was the one piece of evidence with no artifact. `bootstrap.sh
  --sync` no longer preserves laptop mtimes and discards compiled outputs, so Maven cannot
  skip a synced file against a stale class.

**The general lesson, which cost a retraction:** a documented gap that has since been closed
is a claim like any other, and goes stale silently. "run-meta.txt records no commit" outlived
its own fix and was repeated into a results document its artifacts refuted. When a gap is
closed, hunt down every place that asserts it.

### Parked designs

#### Pooling in karate-gatling — shipped, with one thing still open

`KarateProtocolBuilder.pooledConnections()`, closed at simulation end through
`ActorSystem.registerOnTermination` — the hook Gatling's own `HttpEngine` uses, *not*
`ProtocolComponents.onExit`, which fires per virtual user and would close a shared pool while
other users were on it. karate-profiling drives the shipped class, not a copy.

**Still open, and it is what would let pooling be a default anywhere:** a pooled client
cannot honour a scenario's `configure ssl` and ignores it *silently* — the connection manager
is shared and already built, and neither `HttpClientFactory.create()` nor
`ApacheHttpClient.sharedConnectionManager()` receives the configuration, so the factory can
neither warn nor keep one pool per distinct configuration. Widening that seam is the
prerequisite. (Timeouts are no longer on this list — they are applied per request as well,
see `PooledTimeoutTest`.) NTLM is incompatible with pooling outright: it authenticates the
connection, not the request.

#### Per-scenario spill — designed, reviewed, deliberately not built

The remaining unbounded case is a single feature holding thousands of scenarios: feature-end
is suite-end, so only releasing per *scenario* changes the slope. The design — serialize each
scenario's record to a per-feature temp file at scenario end, strip the retained skeleton,
reassemble at feature end — went through **three adversarial reviews, which found enough to
stop**:

- v2 has no deserialization layer (v1's `fromKarateJson` twins are deleted; all three writers
  consume live objects) — rebuilding it is the largest cost, and it appeared in no estimate;
- a `toJson()` spill record cannot reproduce today's HTML (`stripAnsi` removes the
  syntax-highlight sentinels the page model needs);
- ten-plus load-bearing special cases (afterFeature mutating the last scenario post-spill,
  feature-level synthetic scenarios, JSON-mime embeds, JUnit stack traces absent from
  `toJson()`, …);
- the concurrency bound is illusory: every feature is submitted immediately with the
  semaphore acquired *inside* the task, so "one temp file per in-flight feature" is O(all
  features), not O(threads).

If revived: bound feature dispatch first, spill per-format *fragments* produced by today's
writer code, and replace "merge spilled with never-spilled" with *every scenario is spilled
exactly once, when it becomes final*. A cheaper partial alternative, also unbuilt: strip the
`FeatureResult` at feature end — bounds memory at O(threads × feature size) with no
compatibility break, but scores zero on the mega-outline shape, which is the only case left.

#### "Karate doesn't play nice with Gatling's async model" — what would actually settle it

`KarateScalaAction.execute` runs the whole feature synchronously on the thread Gatling handed
it, and Karate's steps block on I/O — so a waiting Karate feature occupies a scheduling slot a
native Gatling user would have yielded. **The cost is concurrency density per injector, not
per-request CPU.** The signature would be achieved throughput plateauing below the requested
user count with *clean-looking* latencies (the queue-for-a-thread time sits between `PerfEvent`
brackets and never reaches a percentile). **Unmeasured, and it should be measured before it is
designed around** — E4's ramp is the experiment (paused). The options, cheapest first, if a number ever
demands one:

| Option | What it buys | What it costs |
|---|---|---|
| **Run the feature off Gatling's thread** (virtual thread; `PerfHook.submit()` is the seam and currently runs inline) | Frees the slot with no change to Karate's engine or user-visible behaviour | Care that blocking calls park rather than pin the carrier — httpclient5's internal synchronization is the thing to check |
| Apache HttpClient's async API | Keeps the whole config surface in the same client family | Karate's step model is synchronous, so async transport alone yields nothing without a way for the caller to suspend |
| Gatling's own HTTP client | Native to the model the criticism is about | The largest behavioural break available: a feature behaving differently under `karate perf` than `karate test` destroys the one property that makes karate-gatling worth having |

#### Parsed-JS reuse — measured, half-gated, not built

Every JS step expression is re-lexed and re-parsed on every step execution; a callee feature
is re-read and re-parsed per `karate.call()`. A throwaway process-wide AST cache measured the
ceiling on `call-accumulation --iterations 2000`: **8.04 GB → 5.33 GB of sampled allocation**
(`BaseParser.<init>` 13.5% → 3.4%). A third of all allocation, and deliberately not built:

- **The load-test lane is answered** — Karate's *entire* per-execution overhead is ~1.4% at
  50 ms (§0), so whatever share a cache recovers is not worth a cache, an eviction policy and
  a new mutable object graph.
- **The ordinary-suite lane is still open** — the win was measured on a workload with no
  HTTP at all, where "does it hide in network time" cannot even be posed. What a cache buys
  that lane in **CPU and wall-clock has never been measured** (the table above is allocation
  only; §4's footprint constraint makes churn *acceptable*, which is not evidence it costs no
  CPU). The cheap closing measurement: recreate the prototype and A/B under `/usr/bin/time`
  on `call-accumulation` and `feature-spread` at two sizes each.
- **The design objection stands regardless:** a keyed cache needs a scope, a bound and an
  identity rule. The better design — the AST as a field on the `Step`, bounded and released
  with the parsed model — only pays if `Step` objects are *reused*, and none of the three
  repeating paths reuse them today (a call re-parses the callee, an outline row copies its
  steps, karate-gatling re-parses per execution). So it is not a simpler alternative to a
  shareable parsed model; it is what falls out of one. Three mutations would have to move off
  the model (`Scenario.selected`, `setName` for dynamic names, `ScenarioOutline.numScenarios`)
  — and one trap: `Scenario.replace()` rewrites step text for `<placeholder>` substitution, so
  a step copy must not inherit its template's cached AST.

#### Prior art — the 0.9.x-era overhead thread

[karatelabs/karate#845](https://github.com/karatelabs/karate/issues/845) is a long 0.9.x-era
thread on exactly this question, not yet mined. Worth extracting before any deeper measurement
work: what was measured, on what workload shape, against what baseline; which costs still
exist in v2; and the dead ends, which are the part that does not go stale. The decision it
leaves open is bigger than the measurement: keep driving Karate through Gatling's actor model,
or write a perf framework native to Karate. The parity workloads exist precisely so that
choice can be made on numbers.

#### Other deferred items

| Item | Note |
|---|---|
| Copy-on-first-change in `processEmbeddedExpressions` | A real inefficiency independent of any leak: fresh containers rebuilt for every node walked, with no check whether `#(...)` appears at all. Three traps if revived: `processInlineEmbedded` must return the original string when nothing substituted (identity-based change detection); the XML branch mutates in place; `resolveConfigMap` has a javadoc promising a defensive copy. Pursue on allocation numbers, not a leak report. |
| JS-engine workloads | `js-array`, `js-object`, `js-engine-init` from `EngineBenchmark`'s generators, so JS tuning gets forking, JFR and digests too. |
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table. `LatencyMock` is the cheap tier; the *table* is unbuilt. |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest`. A CPU-tuning need, not a memory one — build when the question becomes "where is CPU going during a parallel run", exactly where `ExecutionSample` goes blind. |
| Per-iteration residue | `action elapsed − Σ PerfEvent` — would attribute Karate's own overhead exactly rather than by subtraction of throughputs. A *reporting* number, not a gate. The signals that can gate are designed in **[GATLING.md §14.12](./GATLING.md)** (injector health — designed, not built). |
| Machine-readable baselines + CI | Committed `baselines/*.json`, scheduled job, thresholds. Out of scope until the manual playbook has proven itself. |
| Heap-dump class histogram in `JfrDigest` | Deliberately not implemented: no JDK API or CLI reads an `.hprof`. The digest points at Eclipse MAT. |

---

## 10. Method — the latency mock and the parity protocol

The instrument behind every §0 claim. **The question it exists to ask:** does Karate's
per-execution overhead distort a load test, or does it disappear into the network time of a
real API? Against a localhost-speed mock both clients queue behind the server and report
identical numbers that prove nothing — so the mock injects latency, measures itself, and every
cell must prove its own preconditions. The design reasoning lives in the class javadocs, which
are written to be read.

### The pieces

| | Where | What it is |
|---|---|---|
| `LatencyMock` | `karate-profiling/.../profiling/LatencyMock.java` | The instrument: JDK HTTP server on virtual threads, `--latency` and `--tls` knobs, and no shared parser/client/allocator with what it measures |
| `MockStats` | `.../profiling/MockStats.java` | Its self-instrumentation — served, own service time excluding the injected sleep, peak in-flight, distinct peer ports, `/stats` and `/stats/reset` |
| `MockCalibrator` | `.../profiling/MockCalibrator.java` | Finds where the mock stops being free, per request, in both connection modes |
| `Compare` | `.../profiling/Compare.java` | Derives the pair table from digests — pairing, shape-bucketing, integrity flags. Never scrape a table by hand |
| `LoadProfile` | `.../profiling/LoadProfile.java` | Puts the client-side distribution into `digest.md` so two runs diff as text |

**The feature mock is not replaced.** `profiling-mock.feature` is a *subject* (`--record mock`
profiles gherkin matching and JS evaluation inside it); `LatencyMock` is an *instrument*.
Conflating them is how the throughput ceiling stayed stuck.

### Running it

```bash
# a parity cell — both arms, same settings, back to back
etc/run.sh gatling-http-plain  --iterations 1600 --threads 8 --mock-latency 50ms
etc/run.sh gatling-http-karate --iterations 1600 --threads 8 --mock-latency 50ms

# derive the table rather than reading it off the digests
etc/run.sh compare target/profiling/gatling-http-*

# the two-host form, which is where publishable numbers come from:
# PROFILING_EC2.md wraps all of this — calibrate.sh, matrix.sh, collect.sh
etc/ec2/matrix.sh --tier 50ms --pairs 10 --iterations 1600 --users 8 --tls --pooled --label 50ms-tls-8u
```

### The protocol, and the checks a cell must pass

The instrument has a documented history of producing **confident, well-formed, wrong output**
rather than crashing — a soak that reported completion after 4 minutes, a leak panel that
could not have reported a leak, a digest saying TRUNCATED under exit code 0. So a run
reporting zero failures is not by itself evidence; each cell carries its own checks, shown
rather than asserted:

- **Pairs, alternating arm order** (k→p, p→k, …) so drift across a matrix — thermal,
  neighbour, the mock's own sleep overshoot — loads both arms equally instead of accumulating
  against one. `matrix.sh` owns this, plus the warmup discard and `--label` interleave
  protection; a hand-run pair silently loses all of them.
- **A warmup run is discarded per matrix.** A cold `LatencyMock` costs 3.86 core-s and a
  231 µs service p99 against 0.70 and 10 µs warm, and because pair 1 always led with karate
  the bias was structural — it alone moved a 10-pair mean by 0.05 ms and tripled its sd.
- **Throughput comes from the mock, never from Gatling.** Gatling divides by a duration
  rounded to whole seconds, which quantises the rate by several percent at these run lengths
  — exactly how the first matrix concluded the arms were identical. `servedPerSecond` is the
  same requests over a nanosecond window.
- **KO must be 0.** A point with failures is not a slower point, it is a point with holes:
  failed requests leave the sample and take the slow ones with them, so throughput collapses
  while percentiles stay clean.
- **Connection shape must be what the cell claims.** `distinctPeerPorts` == user count on
  both arms for a pooled cell; == iteration count for an unpooled karate arm. If not, the
  cell is measuring something else.
- **Reconciliation.** Gatling's ok+ko must equal the mock's `served` — nothing dropped
  between injector and handler. The digest prints it per run.
- **Headroom.** Injector CPU with slack (the digest's CPU panel; `compare` flags >80% of
  cores) and the mock's `peakInFlight` == users with service p99 far under the tier. For a
  sub-millisecond CPU effect, compute per-iteration CPU from `cpuNanos` in the digest —
  `compare`'s cores column is rounded to 0.1 and is too coarse.
- **Sleep parity.** The injected sleep is a real `Thread.sleep` whose overshoot varies with
  load; the mock reports what it actually slept per arm, and `compare` prints a
  sleep-corrected column beside the raw one. The raw figure has run consistently in Karate's
  favour, making it the conservative one to quote.
- **Inter-run gap.** TIME_WAIT churn from a previous run reads as "Karate is slower".
  `run-meta.txt` records `since prev:` and flags gaps under 35 s; `matrix.sh` sleeps its
  `--gap` between runs.

### Reading a calibration, and the acceptance rules

Run `calibrate.sh` before any matrix on any machine or transport — the cells are chosen at
**half the measured knee**, and the calibration is archived to `$KP_RESULTS` as evidence.
Three checks, in order:

1. **`ko` must be 0.**
2. **The baseline repeat must match the first row.** Each arm re-runs its lowest point at the
   end; the gap IS that arm's noise floor, and the matrix's resolution depends on it.
3. **The knee is where `unowned mean` departs from baseline** — *not* where throughput stops
   rising; in a closed loop throughput is capped by users ÷ iteration time and plateaus with
   or without a knee.

`unowned` is per-request — what the client waited minus what the mock says it spent on that
same request; an **upper bound** on server queueing, since it also contains the client's own
scheduling. The knee is void for an open-loop cell (`constantUsersPerSec`); re-calibrate
open-loop before trusting one.

The EC2 bench's 50 ms calibrations, for orientation (plaintext then TLS, `c7g.4xlarge`):
plaintext keepalive flat ~0.17–0.27 ms through 64 users with a 0.06–0.07 ms repeat gap; TLS
keepalive flat 0.26–0.28 ms to 32 users, departing at 64 (0.461 ms, p99 2.22) — the knee at
64 either way, so 8- and 32-user cells sit at or under half of it. **Close mode prices a
connection: ~0.2 ms plaintext, ~2.7 ms TLS** — the number that makes pooling decisive, and
the close-mode noise floor (repeat gap up to ~0.26 ms) is why close-mode readings carry wider
error bars. On machine A (laptop) the close-arm repeat gap was **0.842 ms — as large as the
signal**, which is why laptop cells could never resolve this.

**Acceptance for a parity cell is three things together, never TPS alone:** parity
(throughput and the percentile distribution), headroom (mock in-flight below the calibrated
knee, injector CPU with slack), and — on a run long enough to read it — a flat live set. The
flat-floor leg is **out of scope for short cells**: every 14-second matrix run drifts
+11–19 MB of warmup in both arms, and a short window cannot tell that from retention. Do not
read a matrix digest's drift row as a leak signal.

### Environment settings that fake a knee, and one hard ceiling

Neither of the first two is a capacity limit, and both were mistaken for one. The mock sets
and echoes them (`PROFILING-MOCK-CONFIG`, and the digest carries it); the kernel one is
printed for the operator because `listen()` silently clamps to it:

| | Default | Why it matters |
|---|---|---|
| `sun.net.httpserver.maxIdleConnections` | **200** | Above that many parked keep-alive connections the JDK server *closes* them — churn that reads as a capacity knee. `LatencyMock` sets 8192. |
| `somaxconn` (macOS 128 / bench 8192) | clamps backlog | A "generously sized" 1024 in Java is 128 in the kernel. |

The hard ceiling is **ephemeral ports**: an unpooled karate arm opens one connection per
iteration and each lands in TIME_WAIT (60 s on Linux, compiled in). The bench raises the port
range and sets `tcp_tw_reuse=1` (bootstrap.sh); `run-meta.txt` derives the sustainable
connection rate for the host it ran on, and every digest reports the run's own rate against
it — including the "survived on brevity rather than margin" case, which is reported, not
enforced, because a short burst over the ceiling is fine and a long one is not. Three rates —
executions/s, requests/s, connections/s — are different numbers; `distinctPeerPorts` is in
every digest so the third never has to be derived again.

### Connection shape — measured, and now optional

Unpooled, Karate builds an HTTP client per execution: `distinctPeerPorts` == the iteration
count (4000 for a 4000-iteration run) against plain Gatling's one-per-user (8). Pooled
(`pooledConnections()` — `-Dkarate.profiling.pooled=true` in this harness), the karate arm
drops to one connection per virtual user, **exactly plain Gatling's shape**, verified in
every pooled cell. The TLS calibration prices what that avoids: ~2.7 ms per avoided
connection over TLS. Against a real API it is also what connection-rate limits, load
balancers and accept queues punish — overhead that scales *with* the network rather than
disappearing into it, and the reason the unpooled configuration must never be quoted for a
public-endpoint scenario.

### Soak mode — what a multi-hour recording has to drop

**At `settings=profile` the harness cannot record a soak at all**, and the failure is silent:
`maxsize` is a cap, so the recording *rolls* — an eight-hour soak would produce a digest
describing its last ~25 minutes. The file is dominated by GC internals (`jdk.GCPhaseParallel`
at over a million events/hour), not by sampling, so `--soak` uses an explicit disable list:
the same two-minute run writes **2 MB instead of 41**. (Both `-XX:StartFlightRecording=` forms
honour per-event settings — a prior claim here that one silently ignored them was false,
likely from reading a stale `run.jfr`.)

**What it keeps, deliberately:** `jdk.GCHeapSummary` (the floor series), `jdk.GarbageCollection`
(pauses), and `jdk.OldObjectSample` **with `stackTrace=true` set explicitly** —
`settings=default` enables it *without* stacks, and a leak profiler that cannot name an
allocator is not one. **What it gives up:** *Allocation by site* and *Hot methods* — do not
use `--soak` for the questions those answer.

`--soak` also starts the **live-set probe** (both drive paths — it was once started on only
one, so every `gatling-*` soak silently had no leak panel): every 300 s
(`-Dkarate.profiling.liveSetSeconds` to override for rehearsals), sample open descriptors
*before* forcing two full GCs, then record what survived, with `valid=` proof the collection
actually happened (`DisableExplicitGC` / `ExplicitGCInvokesConcurrent` would otherwise turn
the probe into a resident-heap meter — detected and flagged). The final probe runs with load
stopped and is excluded from drift. Read it per §3's Live set panel.

### The noise lesson — machines, not pair counts

The 50 ms tier was unreadable on a laptop: six pairs spanning −6.9 to +6.9 ms, sd 4.83,
against a ~1.5 ms effect. Averaging converges as `sd/√n`, so resolving that mean to half
itself needed **~260 pairs at one standard error, ~1000 at 95%** — 10 to 40 hours — against a
quiet dedicated machine that resolved it same-day at sd 0.05–0.09 with 10 pairs. **Halving
the noise is worth quadrupling the runs; when a sweep is not converging, suspect the machine
before adding pairs.** `compare` prints the needed-pairs figure per tier so the trade is
explicit. Graviton was chosen deliberately: 1 vCPU = 1 physical core, no SMT, no turbo-bin
jitter — and never a T-series, whose burst credits throttle silently and read as a client
regression.

**Co-location is the confound a single host cannot argue away** — a mock sharing the
injector's cores works hardest against exactly the arm that costs it more — which is why the
bench is two hosts and why every digest carries the mock's own CPU row: "the mock host was
idle" is a number per run, not an assertion. A same-instance control measured the topology
term at ≲0.15 ms of the per-iteration cost — small, but proved rather than assumed.

### What the current result does not license

- **A public-endpoint claim.** No cell runs over the open internet at a real RTT against a
  server that is not a latency mock. The TLS result removes the transport objection; the
  measurement itself remains unmade.
- **Overload / open-loop claims.** Every cell is closed-loop: when the mock stalls, these
  clients stop offering load, so the knee is optimistic by construction (E4, paused).
- **Capacity parity.** C7: ~2× CPU per iteration is invisible below saturation and binding at
  it. Until E4 runs, injector sizing guidance is "budget double", not a measured curve.
- **Percentile-level claims beyond honesty.** The karate arm's reported response time is the
  HTTP bracket only — `PerfEvent(start, start + responseTime)` built per request inside the
  client. Suite construction, config evaluation, parsing and `match` sit *between* brackets
  and can never reach a percentile — which is exactly why reported latencies are the
  server's (C6), why **throughput is the only sensitive metric in this experiment**, and why
  scheduler starvation (the async-model question) would also hide from percentiles and shows
  up as a TPS shortfall with clean-looking latencies.
- **Configurations the lane does not ship.** Per-step log capture is off under Gatling by
  default; anyone enabling `logReplay` turns it back on, and no cell measures that.

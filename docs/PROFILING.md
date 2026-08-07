# Profiling Karate

> Runbook for JFR-based profiling of karate-core, the mock server and the JS engine.
> Written for an LLM operator: every step is a command you run and an artifact you read.
>
> **Everything here reports. Nothing asserts.** There is no CI job, no committed baseline
> file and no pass/fail gate. An OOM is a legitimate outcome — for some workloads it is
> the *expected* one. Judgement lives with the reader, and last-known numbers live in
> [Current baseline](#6-current-baseline) below.
>
> §8 records what the parallel-execution memory investigation settled. §9 is the mixed bag: what
> was deliberately **not** built and why, *and* the finished pieces whose measurements belong next
> to the parked ones — each headed "— built". §10 is the Gatling parity instrument and the answer
> it produced. Read all three before re-opening anything: several things that look like obvious
> wins are parked there *on evidence*.
>
> **If you are here to run something:** the next phase is
> [the two-host phase](#the-two-host-phase-what-is-built-and-what-it-is-for) in §10 — two quiet
> dedicated machines, because the 50 ms tier was shown to be limited by the laptop rather than by
> the number of pairs taken on it. It is automated end to end in
> **[PROFILING_EC2.md](./PROFILING_EC2.md)**: provision, bootstrap, run, collect, tear down.

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
    run-meta.txt       ← child command line, child JDK, OS, workload config
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
   └─ workload JVM  (JFR on)   -Xmx768m -XX:+UseG1GC -Dmock.url=…
                                └─ on exit → JfrDigest → digest.md
```

The mock server is a **sibling process** deliberately. If it shared a JVM with the load
driver, its CPU samples and allocations would land in the same recording and every number
below would be a blend of client and server. Forking costs a localhost socket hop, which
is closer to reality anyway.

### Disk hygiene — prune after every result

**A sweep will fill your disk, and nothing prunes anything automatically.** Getting one
result costs on the order of a gigabyte, and getting a trend costs a run per data point.
A single afternoon of sweeps reached **19 GB** and hit ENOSPC mid-matrix, which does not
fail cleanly — it aborts the run *and* the tooling around it, and the partial matrix is
worthless because the runs that did complete were competing for a full disk.

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

**The rule: extract the numbers into the baseline table below, then delete the run.** A
`digest.md` is kilobytes and is the durable artifact; `run.jfr` and the reports are working
files with a lifetime of one analysis. Keep a `.jfr` only while you are actively running
`jfr` recipes (§5) against it, and a heap dump only while Eclipse MAT is open on it.

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
| `--iterations N` | per workload | Fixed iteration count. Mutually exclusive with `--duration`. |
| `--duration 10m` | per workload | Run for a wall-clock window instead. Use for soaks. |
| `--xmx 768m` | per workload | Child heap. **The single most important knob** — a leak that OOMs at 768m may never surface at 4g. |
| `--gc g1\|zgc` | `g1` | See [Reproducing a specific collector](#reproducing-a-specific-collector). |
| `--warmup Ns` | per workload | Excluded from the measured window — the recording is delayed past it. A workload that drives its own concurrency runs no warmup, so nothing is delayed for it. |
| `--timeout` | duration + warmup + 5m — but a **flat 1 hour** for an iteration-bounded run | Wall-clock cap. On expiry the parent dumps the recording and thread state, then kills the child. See §4. **Pass this explicitly for any long iteration-bounded run:** its runtime is unknown by construction, so the default is a constant, and an iteration count sized at "about an hour" from a throughput estimate sits right on the kill boundary. The parent now prints a note when you are in that case. |
| `--record workload\|mock` | `workload` | Flips which JVM gets the recording, so the profile is of the mock server rather than the load driver. Only meaningful for a workload that uses a mock — today that is the `gatling-http-*` pair, and `gatling-http-plain --record mock` is the cheaper driver of the two. For a mock that must stay out of the way rather than be profiled, use `--mock-latency` instead (§10). |
| `--mock feature\|latency` | `feature` | Which mock tier to fork. `feature` is the Karate-feature mock (a *subject* — `--record mock` profiles it); `latency` is `LatencyMock`, the instrumented one for runs where the mock must stay out of the way. See [§10](#10-the-latency-mock-and-what-the-parity-matrix-found). |
| `--mock-latency 10ms` | none | Injected server latency. Implies `--mock latency`, because the feature mock has no such knob. **This is what makes a Gatling parity comparison mean anything** — against a localhost mock both clients queue behind the server and report identical numbers that prove nothing. |
| `--mock-url URL` | none | Use a `LatencyMock` already running elsewhere instead of forking one — **the flag a two-host run needs**, since co-location is the confound §10 cannot argue away. The parent resets the remote mock's counters before the load and scrapes them after, so a shared mock still reports one window per run. Start the far side with `LatencyMock --bind 0.0.0.0 --standalone --latency 10ms`; without `--standalone` it reads EOF on stdin and exits before the first request. Incompatible with `--record mock`. |
| `--soak` | off | **Required for any multi-hour run.** Records a much smaller event set so the recording spans the whole run instead of rolling — see [soak mode](#soak-mode--what-a-multi-hour-recording-has-to-drop). Costs you *Allocation by site* and *Hot methods*, which a soak does not read. |
| `--gc-roots` | off | Makes `jdk.OldObjectSample` report reference chains — the *holder* of retained objects, not just the allocating stack. Costs a full reference walk at every sample, which is why it is per-run rather than always on. |

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

**The measurement is the scale sweep, not any single run.** Run it at several sizes and
compare peak heap — flat means no accumulation, proportional means there is:

```bash
etc/run.sh call-accumulation --iterations 250
etc/run.sh call-accumulation --iterations 1000
etc/run.sh call-accumulation --iterations 2000
```

This workload owns its suite (it declares `drivesOwnConcurrency`), so `--iterations` means
*scenarios in the suite* and `--threads` is the suite's own parallelism. Driving it the
usual way would build a fresh `Suite` per iteration, letting each one become garbage
immediately — collecting the very accumulation being hunted and reporting all clear.

Its shape is **one feature holding an N-row `Scenario Outline`**, which makes feature-end
and suite-end the same instant. That is deliberate — it is the worst case for anything that
releases per feature — but it also means this workload alone cannot tell you how an ordinary
suite behaves. Always run it paired with `feature-spread`.

*Healthy result:* peak heap roughly flat as scenario count rises. See
[Current baseline](#6-current-baseline) for what it does today.

### `feature-spread` — the same work, many features

The same total scenario count as `call-accumulation`, spread over many features (default
10 scenarios each) instead of concentrated in one. **Run them as a pair — neither number
means much alone.** They are the two extremes of identical work, so a change that moves one
and not the other has told you exactly which bound it achieved:

```bash
etc/run.sh call-accumulation --iterations 2000   #   1 feature x 2000 scenarios
etc/run.sh feature-spread    --iterations 2000   # 200 features x   10 scenarios
```

This is the only workload that exercises per-feature report writing more than once, which
is what makes it the one that catches queueing and per-feature-lifetime problems. Knobs:
`-Dprofiling.spread.scenarios=N` (per feature, default 10) and
`-Dprofiling.spread.calls=N` (per scenario, default 60, matching `call-accumulation` so the
two differ only in distribution). Features are generated into a temp directory rather than
committed, because how many there are is the variable under study.

### Report modes — `off` | `html` | `all`

Both memory workloads take `-Dkarate.profiling.reports=off|html|all`, or a comma-separated
subset of `html,jsonl,junit,cucumber`. This is three experiments, not a boolean, and the
distinction is load-bearing:

- **`off`** — no `ResultListener` at all. Measures execution.
- **`html`** — what `Runner.Builder` gives you by default, i.e. the config users actually
  ship. This is the one to check against a reported OOM.
- **`all`** — every format. Costs the most work but not the most memory.

A run recorded only as "reports on" cannot be told apart afterwards, which cost one round
of measurement. `-Dkarate.profiling.reportCost=true` additionally attaches a listener that
times each report operation per feature and prints whether a single writer thread could keep
up with the suite — see §8.

### Leak-watch family — NOT BUILT, and it is the biggest gap in this document

**"Does Karate leak?" is not answered.** What is answered is narrower and worth separating,
because the difference decides what a soak would even be looking for:

| | Status |
|---|---|
| Retention that grows with **suite size** | **Fixed and verified** — §8 found two real mechanisms (call-result accumulation, the report-writing queue) and peak heap is now flat across a 4x scale sweep. |
| One feature holding thousands of scenarios, reports on | **Known-unbounded, accepted** — still linear, §6. Not a leak: it is retention by design until suite end. |
| A slow leak over **hours** | **Still unmeasured, but the instrument now works.** Three silent faults that made a soak impossible are fixed (below); the first real run is pending. |

The instrument for the third row already exists: the **heap-after-GC floor** in every digest is
the detector, and §4's chart is the classification. What is missing is a run long enough for a
floor to have a slope. **Absence of evidence, and the digests do not distinguish it from evidence
of absence** — a workload that runs for four seconds cannot report a leak that takes an hour.

#### These are two different questions, and the second is untouched

**A long Runner suite** — thousands of scenarios in one JVM, one `Suite`. A leak here grows with
scenario count and is what §8's work was about. This is the better-understood lane, and a soak is
mostly confirmation.

**Gatling** — hours of execution, and **a fresh `Suite` per virtual-user iteration** against a
thread pool that lives for the whole simulation. Anything retained per `Suite`, or anchored to a
carrier thread, accumulates across hundreds of thousands of executions. **Nothing has ever looked
at this**, and it is the more likely place for a real leak precisely because the object churn is
per-execution rather than per-suite.

Three candidates to test there — **hypotheses, not findings**, listed so a soak knows where to
point `--gc-roots`:

- `Suite` holds two **instance** `ThreadLocal`s (`threadListeners`, `currentLane`,
  `Suite.java:138,153`). A new Suite per execution means a new ThreadLocal object per execution,
  each leaving an entry in the ThreadLocalMap of every Gatling thread that touched it. The keys
  are weak and cleanup is opportunistic, so this is usually self-limiting — but the *values* are
  held until that cleanup runs, and one of them is a listener list.
- `LogContext` holds **static** `ThreadLocal`s (`CURRENT`, `PENDING`, `LogContext.java:43,258`).
  Static plus a long-lived pool means anything not explicitly removed at the end of an execution
  stays reachable from the thread that ran it.
- The caches karate-gatling **deliberately** shares across virtual users (`callSingleCache`,
  `callOnceCacheStore`, injected via `Runner.Builder`). These are designed to outlive a Suite and
  are bounded by distinct keys — worth confirming that is actually true rather than assumed.

#### What it needs first

**`--duration` is not supported for the `gatling-*` workloads** (`SimShape` has no `during()`; it
injects a repetition count), so the Gatling soak is blocked on that one small change.

**And the claim that "the Runner lane already takes `--duration` and could be soaked today" was
false for three separate reasons, all now fixed.** Each failed silently, which is why the sentence
survived so long:

1. **`--duration` truncated every long run to `threads x 30s`.** `Child.drive()` joined each worker
   with a flat 30-second timeout, in a loop over the workers. A `--duration 7h` soak at 8 threads
   ran for **four minutes**, exited 0, and wrote a clean digest whose only tell was
   `elapsedMs=240007`. The join is now one deadline for the whole loop, derived from the window,
   and covered by a regression test that fails at 8 s where the fix costs 1.2 s
   (`ChildDriveTest`) — the original bug degrades *proportionally to thread count*, so it looks
   fine in every short run and only bites where verification is expensive.

   A run with workers still alive at the deadline now carries `truncated=true` in the machine-read
   summary line, renders as **TRUNCATED** in the digest's outcome row, and **exits non-zero**. The
   first version of this fix printed a warning to stdout and stopped there, which is not a signal:
   on an eight-hour soak it is one line among thousands, and a truncated run's `elapsedMs` is
   `window + grace`, which passes a glance. `completed` also no longer counts stragglers — it
   counted iterations *claimed*, overstating the run at the one moment its numbers are least
   trustworthy.
2. **`call-accumulation` and `feature-spread` cannot be soaked at all.** Both declare
   `drivesOwnConcurrency`, so `Child` runs one pass and ignores `--duration`. The soakable Runner
   workloads are the **scope-capture pair** and `harness-smoke`.
3. **The recording could not span a soak.** See [`--soak`](#soak-mode--what-a-multi-hour-recording-has-to-drop).

**What to soak, and why it is better than it looks.** `scope-capture-bound` runs
`FeatureWorkload.iterate()`, which calls `Runner.runFeature()` — **a fresh `Suite` per
iteration**, which is exactly what `KarateExecutor` does under Gatling. So it exercises the
Suite-per-iteration hypothesis below **without** the HTTP client's file-descriptor confound, and
is therefore *not* blocked on the client-lifecycle fix. **Read that last clause narrowly**: it is
unblocked because this workload makes *zero HTTP calls*, which also means it cannot observe
HTTP-client or file-descriptor retention **at all** — and that is the leak class most plausibly
attached to the Gatling lane's long-lived pool. It also runs with a null `PerfHook` and no
log-replay buffers, both of which are live in the Gatling path. A flat floor here clears the
Suite-per-iteration hypothesis and nothing else.

**Prefer iterations over wall-clock when choosing a length.** The named suspects below are all
per-*execution*, so they scale with iteration count, not time. At ~9,500 iterations/s an hour is
~34 million executions — far more than any real suite or Gatling run, and enough that a 10-byte
per-iteration leak would show as ~340 MB against a 768m heap.

That is a heuristic, not a theorem, and it is worth knowing where it bends. At 9,500 it/s the JVM
runs ~30 young collections a second — a regime no real suite is in. Tenuring, promotion and
`OldObjectSample`'s age-based sampling all behave differently under that compression, so a
retention bug whose trigger is *promotion* rather than *allocation count* can present differently
here than it would over hours at a realistic rate. Compressing time buys iterations cheaply; it
does not make a long run redundant.

One prerequisite is now met: a soak needs a server that stays stable for hours without
saturating, and `LatencyMock` ([§10](#10-the-latency-mock-and-what-the-parity-matrix-found)) is
that — it reports its own in-flight and service time, so "the mock degraded" cannot be mistaken
for "the client leaked".

**Two more are not, and both are about sockets rather than heap.** A Gatling soak run today would
measure them instead of Karate:

- **The per-execution HTTP client is never closed.** `ApacheHttpClient.close()` exists and nothing
  in the repository calls it, so every execution abandons its client, its connection manager and
  its pooled socket to the collector — sockets close when cleaners run, at a time no test controls.
  A file descriptor is not heap, so the heap-after-GC floor cannot see this at all.
- **The ephemeral-port ceiling is ~550 connections/second** (§10), and the karate arm opens one
  connection per iteration — measured, not assumed. Which tiers that rules out depends on the tier,
  and the two numbers are worth keeping straight: at 10 ms the arm offers **263 conn/s**, under the
  ceiling, so a soak there is not obviously doomed; at 0 ms it offers **~4,200 conn/s**, eight times
  it, and a duration run there exhausts the range in seconds. **But the first number is only safe
  if the sockets are actually released**, and the bullet above says they are not — a connection the
  collector has not got to yet still holds its port, so port *occupancy* at 263/s is bounded by GC
  timing rather than by TIME_WAIT. That is the real reason to fix the lifecycle before soaking, and
  it is why the 10 ms tier's headroom cannot be taken on the arithmetic alone. Apply the port
  sysctl in §10 either way, and watch the port count rather than assuming it.

Fix the lifecycle — close at scenario end, or pool per virtual user — before reading any soak
number from this lane.

The workload shape to build is a realistic one, not a microbenchmark: `karate-config.js` doing a
call, `callSingle`, `callonce` in `Background`, `call read()` with an args object, JS function
defs, HTTP against the forked mock, `match` assertions — plus thin single-mechanism variants
(`leak-callonce`, `leak-callsingle`, `leak-shared-scope`, `leak-isolated-scope`) so a rising floor
can be attributed rather than just observed. v1's
`examples/profiling-test/src/test/java/perf/{main,called,mock}.feature` is the shape to copy.

*Healthy result:* a **flat** heap-after-GC floor across the whole run, at any sawtooth amplitude
(§4). Run it long enough that a floor has a slope — hours, not seconds — and read the floor, not
the peak.

### The bound-scope-capture pair — regression guard

| Workload | Shape |
|---|---|
| `scope-capture-bound` | 13 sequential bare `karate.call()`s, each result **bound** to a variable, over a ~100-record base payload. |
| `scope-capture-unbound` | Identical, plus `* def capN = null` after each capture. |

Defaults: 16 threads, 5000 iterations, `-Xmx768m`, G1.

These were written to reproduce a reported geometric blow-up in which each capture contains
all previous ones. **That shape does not occur on current main** — a bare
`karate.call('f.feature')` returns only the callee's own variables, not the caller's scope,
so the nest never forms and both variants behave identically.

They are kept as a **regression guard**: if a change ever made a call return the caller's
scope again, `bound` would diverge sharply from `unbound` and this pair would catch it.
Do not read a passing `scope-capture-bound` as evidence that memory is fine generally —
it only says this particular nesting is absent. Verify the shape directly with `Probe`
(below) rather than inferring it from a heap curve.

### Gatling parity family — what does driving Karate from Gatling cost?

Four workloads in two pairs, each pair being the same work done by Karate and by plain
Gatling. **A single number here means nothing** — every one of these exists to be subtracted
from or divided by its partner, on the same machine, back to back.

| Pair | Workloads | The question |
|---|---|---|
| null | `gatling-null-plain` / `gatling-null-karate` | What does one `karateFeature()` exec cost before any user work? |
| http | `gatling-http-plain` / `gatling-http-karate` | What does a Karate-driven virtual user cost against a Gatling-native one, doing a POST + GET a user would actually write? |

```bash
etc/run.sh gatling-null-plain  --iterations 20000
etc/run.sh gatling-null-karate --iterations 20000    # the difference is the answer
```

**Do not "fix" the harness `karate-config.js`.** It reads `karate.properties['mock.url']`, and
we tell users to prefer `karate.sysprop()` — but a workload with no mock reads that key when it
is **absent**, and a missed property read is the expensive shape (§9, exceptions on the happy
path). Switching it to the recommended form would quietly retire the only regression guard on
that path. The file says so in a comment; this is the second copy, because the comment is easy
to miss and the change looks like tidying.

These live behind `-Pgatling` — Gatling and its Scala runtime are ~40 MB of classpath the
other workloads have no use for. `etc/run.sh` turns the profile on automatically for any
`gatling-*` workload and for `--list`, so there is nothing extra to remember; it also means
those invocations build `karate-gatling` first and take longer.

Gatling owns the users and the pacing, so these are self-driving: `--threads` becomes virtual
users injected at once, and `--iterations` stays the **total**, split across them —
`--threads 16 --iterations 20000` is 16 users repeating 1250 times each. When it does not
divide evenly the remainder is rounded up, so the true total can exceed what you asked for by
up to `threads - 1`; the child prints what it actually ran. `--duration` is not supported (see
§9). Gatling's own chart generation is off — it is a second pass over the simulation log that
would land in the digest as if it were load-driving cost.

**Against the *feature* mock, wall-clock on the `http` pair measures the mock, not the clients** —
both variants saturate it and finish within noise of each other, in either order. Use
`--mock-latency` and the `LatencyMock` tier instead ([§10](#10-the-latency-mock-and-what-the-parity-matrix-found));
against the feature mock only **allocation** is usable, because it is a client-side fact a saturated
server cannot hide. The `null` pair has no HTTP at all and is the one whose per-exec numbers are
worth quoting.

#### What "acceptable overhead" means here — and the answer

Not a ratio against plain Gatling. That comparison is unavoidably apples-to-oranges — Karate parses
each response into a document and runs a structural `match` where the Gatling variant extracts one
JSONPath — so a ratio measures *different work*, and picking a threshold for it (GATLING.md's
"< 5% vs plain Gatling") invites tuning towards a number that was never the question.

**The question a load tester actually has is whether the client's overhead disappears into the
network time of the system under test**, and the practical form of that test is to run both against
the same target and compare the reports. Same throughput and same response-time distribution means
the client is not distorting the measurement, which is the job.

It carries one condition: **equal TPS only means anything when the client has headroom.** Two
saturated clients queued behind the same overloaded server also report identical numbers. So the
test is "same TPS *and* the injector is demonstrably not the bottleneck" — which is what the
instrumented mock in §10 exists to establish.

**That test has now been run, and the answer is a shape rather than a yes.** Karate adds roughly
half a millisecond to a millisecond of serial time per iteration. Because a closed loop makes
throughput exactly `users ÷ iteration time`, that cost shows up in full at 0 ms — a 2.1–2.4x gap —
and then shrinks as a share of the iteration: **~2% at 10 ms of injected server latency**, and
below what this machine can resolve at 50 ms — six pairs, spread more than three times the effect.
Two qualifiers travel with that and must
not be dropped: **it shrinks, it does not disappear**, and the result covers loopback, ~100-byte
bodies and a client that opens a connection per iteration, none of which is a real API. See
[§10](#10-the-latency-mock-and-what-the-parity-matrix-found) for the matrix, the caveats, and what
is still unmeasured (the user ramp, TLS and body size, and co-location).

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

Use it whenever a memory theory depends on how much a call hands back. That is a one-run
question about object-graph shape, and answering it directly beats inferring it from a heap
curve — as the scope-capture pair above demonstrates.

---

## 3. Reading `digest.md`

The digest has these panels, in this order. Sections are stable, so you can diff two
digests directly.

**Run summary.** Duration, exit status, child JDK, and the child's JVM/GC/heap flags
echoed back. Check this first — the most common analysis mistake is comparing two runs
that had different `--xmx`, `--gc`, or a different child JDK.

**CPU headroom.** What the injector and the mock each burned, **over their own measured window** —
the child's from `[child] measuring`, the mock's across its load window, so neither carries JVM
startup or an idle tail. `cores busy` near the machine's cpu count means the run measured the
machine rather than what it was pointed at, and a throughput comparison taken there is not a
comparison of clients. The two windows are different windows: read each against `cpus`, never one
against the other. The mock's row is the co-location bias as a number — on a two-host run it is
what shows the mock host was idle.

**Allocation by site.** From `jdk.ObjectAllocationSample`, weighted bytes, stacks
collapsed to `io.karatelabs.*` frames so the top entries are Karate methods rather than
`java.util.HashMap.newNode`. This answers *what is churning*, not *what is retained* —
those are different questions and conflating them is the trap described in §4. This panel
does attribute correctly across virtual threads.

It is a **top-25 table** with the remainder counted but not listed, so "gone from the panel"
means "below the cutoff", which lands around 1% on these workloads — not zero. It also
collapses to the topmost Karate frame, so a site's *callers* are invisible here: when the
question is who is calling the expensive thing, that is a `jfr print` with `--stack-depth`
(§5), not a digest read.

**Hot methods.** From `jdk.ExecutionSample`, same collapsing. CPU time, not allocation.
**Read this panel with the virtual-thread caveat in §7 firmly in mind** — for any
Runner-driven workload it under-samples scenario code severely.

**Heap-after-GC series.** From `jdk.GCHeapSummary` — live-set after each collection, over
time. **This is the most important panel in the file.** See §4.

**GC pauses.** Count and histogram. A sharp rise in pause *frequency* (rather than
duration) usually means allocation pressure, not retention.

**Retained objects.** From `jdk.OldObjectSample` — JFR's built-in leak profiler. Samples
objects that survived a collection, with the stack that allocated them. Read it as "who
allocated the things that are still alive". Note it gives you the *allocator*, not the
*holder*, unless the run enabled `path-to-gc-roots` (see §4).

**Top classes.** Only present when a heap dump exists — a class histogram read from
`heapdump.hprof`.

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
- **Rising floor → retention.** Something is held. Look at *Retained objects*, and if it
  OOMs, at *Top classes* from the heap dump. If you need the *holder* rather than the
  allocator, re-run with `path-to-gc-roots` enabled — it is off by default because it
  costs a full reference walk at each sample.
- **Flat floor then an abrupt cliff → live mid-copy.** Not a leak at all: a single
  structure being built right now is larger than the heap. The tell is that the heap dump
  shows the bulk of the live set on **one thread's stack locals**, in a deep self-recursion
  — not stranded in a stale cache. Issue #2972 was exactly this, and the first two rounds
  of diagnosis on that thread went to the wrong mechanism because only the class histogram
  was consulted, not where the objects were rooted.

#### The standing constraint: footprint is fine, a rising floor is not

**Using more memory is acceptable as long as it is collected and the floor stays flat.** That is
a design licence, and it is deliberately phrased in the terms of the chart above: sawtooth
amplitude is not a budget anyone has to defend, and trading allocation for retained-but-released
memory is a fair trade. What is not acceptable is anything that moves the floor.

Two consequences worth stating, because they cut in opposite directions and both get misread:

- **It clears a whole class of objection to caching.** A cache whose entries are reachable only
  from something with a bounded lifetime — a Suite, a parsed `Feature`, a `Step` — is footprint,
  not leak: it is released when its owner is, and it needs no eviction policy to say so. Do not
  design an LRU for one of those.
- **It does not clear a keyed cache in a long-lived process.** `karate serve`, MCP and the IDE
  plugin outlive any Suite, and a map keyed on generated text grows for as long as they run. That
  is a rising floor, i.e. precisely the thing the constraint rules out — the fact that it is
  called a cache does not change what the heap-after-GC series will show.

The practical test is the same one §4 already gives: run it long enough for the floor to be
readable, and look at the floor.

### "A long scenario OOMs"

1. `etc/run.sh scope-capture-bound` — does the known shape reproduce on this machine?
2. Read *Heap-after-GC* and classify with the chart above.
3. If it is live-mid-copy: in the heap dump, find what holds the largest retained size and
   check whether the root is a thread stack. If it is, read the recursion depth and the
   size decay down the stack — a geometric decay (each level roughly half the one above)
   means level N *contains* level N+1, i.e. nesting, not repetition.

   Karate runs scenarios on virtual threads (`Suite.runParallel`, `Suite.java:872`), so "rooted at a thread
   stack" is conditional: a virtual thread's frames sit on its carrier platform thread's
   stack only while **mounted**. Unmounted, they live in heap-allocated
   `jdk.internal.vm.StackChunk` objects and MAT will root them there instead. An OOM
   during an in-flight copy happens while mounted, so the conventional stack root is what
   you should see — but if the dominator root looks like a `StackChunk`, that is the same
   finding wearing a different hat, not a different problem.
4. Compare against `scope-capture-unbound`. If unbinding fixes it, the cost is per-step
   work over live scope, and the number of *bound collections* is the driver — not the
   number of calls.

### "Turning reports on costs far more memory than running the tests"

Check for a **producer/consumer race before assuming retention**. The tell is that the
number is *unstable*: two identical runs differing by hundreds of MB means you are measuring
a queue depth, not a live set. Retention is boringly reproducible; races are not.

The mechanism to look for is work handed to a background writer faster than it can drain.
Confirm it rather than infer it:

1. Run with `-Dkarate.profiling.reportCost=true` and read **writer-thread load** — total
   deferred work divided by suite wall-clock. Above 1.0, one thread cannot keep up and its
   queue grows for the entire run, holding one whole-feature payload per entry.
2. In the digest's *Retained objects*, a race shows up as retained bytes dominated by `[B`
   attributed to a **rendering or serializing** site, not to result-model classes. Result
   objects mean retention; rendered strings mean a queue.
3. The counter-intuitive confirmation: enable *more* formats. If peak heap goes **down**,
   there is no leak — the extra work slowed the producer enough for the writer to keep up,
   which is backpressure arriving by accident.

The fix for this class of problem is not to bound the queue but to remove it: do the work on
the thread that produced it. That trades a background thread for N-way parallelism across
the feature threads and makes memory O(threads) instead of O(features). See §8.

### "Karate got slower"

1. Run the same pair on the suspect commit and on its parent — `call-accumulation
   --iterations 2000` and `feature-spread --iterations 2000`, because a change that moves one
   and not the other has already told you which shape it affected (§2). For a CPU-shaped
   regression under Gatling, the `gatling-null` pair isolates fixed per-execution cost with no
   HTTP in the way.
2. Diff the two `digest.md` files — *Allocation by site* first, *Hot methods* second.
   That order is deliberate: allocation sampling is trustworthy under virtual threads and
   CPU sampling largely is not (§7).
3. Ignore wall-clock differences under ~10% on a laptop; see §7.

Once the leak-watch family exists (§2), a `--duration` soak on both commits replaces step 1 —
it is the better instrument for "slower over time" as opposed to "slower per iteration". It
does not exist yet, so do not reach for it.

### "Is the mock server fast enough?"

`--record mock` puts the recording on the mock JVM instead of the load driver, so the profile
attributes cost to gherkin matching, JS evaluation and response building. This is also the one
configuration where *Hot methods* is fully trustworthy — the mock serves on platform threads.

Today the only workloads that fork a mock are the Gatling http pair, so:

```bash
etc/run.sh gatling-http-plain --record mock    # cheapest driver available
```

`gatling-http-plain` is the one to use: driving with Gatling's own client rather than Karate's
keeps the client's own cost out of the picture as far as currently possible. It is not
*absent* from the picture — the raw-`java.net.http` driver that would remove it entirely is
the unbuilt "Mock throughput tiers" item in §9, and until it exists a mock profile still
contains some client.

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

Two recording options worth knowing about, one already on and one you ask for:

- `-XX:FlightRecorderOptions:stackdepth=128` — **always applied** by the harness. The JVM
  default of 64 truncates Karate-through-JS stacks, which collapses distinct allocation sites
  into one; there is nothing to enable.
- `path-to-gc-roots=true` — makes `jdk.OldObjectSample` report reference chains (the
  *holder*), not just the allocation stack. Opt-in per run via the **`--gc-roots`** flag,
  because it costs a full reference walk at every sample. This is the flag §4 means whenever
  it says "re-run with `path-to-gc-roots` enabled".

---

## 6. Current baseline

Hand-maintained. Update it when you take a run you trust, and always record the machine
**and the child JDK** — absolute numbers are meaningless without them, only *shapes* and
*ratios* travel.

### What is settled, and what is not

The one paragraph to read before assuming "Karate's bottlenecks are handled". The four
categories are not interchangeable and the difference decides what the next phase may take for
granted:

| | Status |
|---|---|
| **Fixed and verified** | Parallel-execution *memory* on ordinary suite shapes: call-result retention (flat across a 4x scale sweep) and the report-writing queue (~4.5x → ~1.3x, wall-clock fell). The external reproducer passes at `-Xmx768m`. |
| **Measured, known-unbounded, accepted** | One feature holding thousands of scenarios, with reports on — still linear (502 / 1108 / 2079 MB). Only per-scenario release changes the slope, and §9 records why that was not built. |
| **Never measured** | Soaks — "does Karate leak over hours". Every workload is an iteration-bounded reproduction finishing in seconds, so this is unverified rather than verified, **in both lanes**. The Gatling lane is entirely untouched and is the more likely place for one, because it builds a `Suite` per iteration against a long-lived thread pool. See [the leak-watch family](#leak-watch-family--not-built-and-it-is-the-biggest-gap-in-this-document) in §2. |
| **Never measured** | CPU inside scenario code *under a parallel Runner suite*. All of §8 is allocation and retention, and `jdk.ExecutionSample` is blind on virtual threads there (§7). It is **not** blind in the Gatling lane, which runs scenarios inline on a platform thread. |
| **Measured, and scoped rather than settled** | Whether Karate's per-execution overhead distorts a load test. **It adds ~0.5–1 ms of serial time per iteration on machine A, and ~1.8 ms on a Graviton3 EC2 instance — the absolute figure is machine-specific and must always be quoted with its machine** (§10's two-host result)**.** — 2.1–2.4x throughput at 0 ms, **~2% at 10 ms**, and **unresolved at 50 ms, where the limit is the machine rather than the pair count**: six pairs leave a spread more than three times the effect, because a 0.5% deficit sits an order of magnitude under this laptop's 3–6% floor. Loopback, 8 users, closed loop, ~100-byte bodies, log capture off ([§10](#10-the-latency-mock-and-what-the-parity-matrix-found)). Read the qualifiers as load-bearing: it *shrinks* as a share of the iteration rather than disappearing, and none of it generalises to TLS, to larger bodies, or to the one connection Karate opens per iteration, which **scales with** the network instead of hiding inside it and which this harness prices at zero. It also scopes the *load-test* lane only: the ordinary-suite half of the parse-cache decision was never measured (§9). |

The Gatling baseline below started as a fifth category: **leads, not findings** — real numbers
from real runs, none chased to a cause. Three have since been chased (the per-scenario Logback
snapshot, happy-path exception construction with the uncached reflection and map copies
underneath it, and re-parsing — §9 has all three, with before-and-after). Chasing the last of
those moved it out of the Gatling column entirely: **the re-parsing that costs is JS step
expressions, re-parsed on every step execution, and it is an ordinary-suite cost that the
Gatling lane merely made visible** — measured, and deliberately not acted on. Note what that
sentence implies about the gate: the latency mock answers "does it survive a network" for the
Gatling lane, and the lane the win was measured in has no network in it at all. See
[Parsed-JS reuse](#parsed-js-reuse--measured-half-gated-not-built).
**Still a lead, still unchased:** per-execution HTTP client construction. Driving Karate under
load surfaces karate-core costs the memory workloads never touched, which is the main reason
the Gatling family earns its keep beyond the parity question.

**Machine A** — Apple Silicon (aarch64), Darwin 25.5 (kernel version, not the macOS
marketing one), 10 cores, JDK 24.0.2, G1.

#### `call-accumulation` scale sweep — machine A, 60 calls per scenario, 16 threads

Peak heap, reports off. **Before** = 2026-08-04, karate 2.1.2.RC1 as released.
**After** = the same build with completed call results released at scenario end.

| scenarios | before (`-Xmx3g`) | after (`-Xmx3g`) |
|---:|---:|---:|
| 500 | 343 MB | 209 MB |
| 1000 | — | 299 MB |
| 2000 | 859 MB | 272 MB |
| 5000 | exceeds 768m, saturates | **233 MB at `-Xmx768m`, 5.3s** |

Before, peak grew linearly with scenario count — roughly 143 KB retained per scenario,
held for the whole suite. After, it is flat: a 4x increase in scenarios moves peak heap
barely at all. Flat is the property to check on any future run; the absolute numbers are
machine-specific.

#### Reports on — machine A, `-Xmx3g`, 16 threads, 60 calls per scenario

Peak heap, before and after the report-writing changes in §8. `html` is the shipped default
config; `all` adds Cucumber, JUnit and JSONL.

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

Read these together, because they say two different things:

- **The ordinary shape is bounded.** Reporting now costs ~1.3x running the tests, down from
  ~4.5x, and wall-clock *fell* (5200 → 3223 ms at 2000 scenarios) — the work removed was
  larger than the parallelism lost. The before-numbers for `html` are quoted as a range
  because they were not reproducible run to run; that instability was itself the symptom.
- **The single mega-outline shape is still linear** — 502 / 1108 / 2079. Improved ~2.5x, but
  with one feature there is no feature-end seam to release at, so nothing short of releasing
  per *scenario* changes its slope. This is a known, accepted limit; see §9.

**Ignore the `all` column moving the wrong way** in three of six cells. Those before-numbers were
artificially low for the reason §8 sets out — JSONL was accidentally throttling the producer
enough for the writer thread to keep up — so they were never a budget the fix could regress
against. The `html` column is the one that carries meaning, because `html` is what users ship.

A `feature-spread html` run at `-Xmx768m` and 2000 scenarios peaked at 739 MB before the
change — 96% of the heap, no headroom, on a suite that needs 248 MB to execute. That is the
shape a reported OOM takes.

#### Cross-check against the external reproducer — machine A, `-Xmx768m`, 5000 scenarios

| variant | karate 2.0.10 | 2.1.2.RC1 as released | with the fix |
|---|---|---|---|
| J (13 bound captures) | heap pinned at 785,383K of 786,432K, killed at 10 min | passed, 1.96s | passed, 2.7s |
| C (60 calls via `karate.repeat`) | OOM | saturates | **passed, 6.5s** |
| E (60 calls, individual statements) | OOM | heap pinned at 99.8%, killed at 12 min | **passed, 6.7s** |

Two distinct mechanisms, and only measurement separated them: the scope-capture nesting
J was written to demonstrate was already gone before this work started, while the
call-result accumulation the reporter had retracted was the one still live.

#### Gatling parity — machine A, `-Xmx1g`, 16 users, G1, JDK 24

First measurement of this family, 2026-08-05, karate 2.1.2.RC1.

**`null` pair — no HTTP.** Wall-clock at 20 000 execs is ~1150 ms plain against ~2020 ms
Karate, but **do not read the difference as per-exec cost** — a large part of it is one-time
startup, and it only separates if you measure at two sizes:

| | 2000 execs | 20 000 execs | marginal |
|---|---:|---:|---:|
| plain, CPU (user+sys) | 1.63 core-s | 1.98 core-s | ~19 µs/exec |
| karate, CPU (user+sys) | 4.52 core-s | 12.47 core-s | **~440 µs/exec** |

So one `karateFeature()` exec costs **~0.45 ms of CPU** — building a `Suite`, parsing the
feature, evaluating `karate-config.js`, running one scenario, handing the session maps back —
against ~0.02 ms for a no-op Gatling exec. Separately, Karate adds **~2 core-seconds of
one-time initialisation** per JVM (class loading, JS engine); at 2000 execs that fixed cost is
most of the gap, which is why a single-size measurement of this pair overstates the per-exec
number by roughly half.

CPU here is measured directly — the child command replayed under `/usr/bin/time` with JFR
off — not inferred from wall-clock. **Wall-clock × concurrency is not CPU** and derives a
number this machine cannot produce: 16 users on 10 cores cannot burn 16 core-seconds per
second. Marginal *wall* per exec works out at ~30–50 µs depending on the run, which at
~440 µs of CPU says the run is CPU-saturated across ~9 of the 10 cores.

Run-to-run spread on the wall-clock numbers is ±10–15%, so treat anything under about 20% as
noise. The CPU figures above are the stable ones, and two independent measurements put the
marginal cost at 0.42–0.46 ms.

**That 0.45 ms predates the allocation fixes below and has not been re-measured.** Sampled
allocation for this workload has since fallen ~30% (1.71 GB → 1.01–1.12 GB), which ought to
show up in CPU too — less allocation means less GC and fewer reflective lookups — but *ought to*
is not a measurement. Re-taking it is the `/usr/bin/time` replay described above, not a digest
read, and it is the first thing to do if anyone quotes the per-exec cost again.

Where it goes, from the digest of the Karate run — an *empty* feature, so every entry here is
fixed cost:

| site | share | reading |
|---|---:|---|
| `BaseParser.<init>` | 9–10% | the feature and its JS re-parsed every time — mostly the **JS**, see below |
| `PathResource.computeRelativePath` | 7–9% | path resolution, per execution |
| `Resource.urlToPath` | ~8% | classpath URL → Path, per execution |
| ~~`FileUtils.toBytes`~~ | ~~8.0%~~ | **fixed** — an in-memory resource no longer encodes its text to bytes nothing reads; see [§9](#parsing-and-reading-the-same-file-per-execution--partly-built) |
| ~~`LogContext.setLevelOn` + `captureRuntimeLevels`~~ | ~~6.1%~~ | **fixed** — the Logback level snapshot is now lazy; see [§9](#per-scenario-logback-level-snapshot--built) |
| ~~`JsErrorException.<init>` + `ResourceNotFoundException.<init>`~~ | ~~5.9%~~ | **fixed** — with the reflection and map copies around them; see [§9](#exceptions-on-the-happy-path--built) |

Two rows are kept struck through because they are the entries with a before-and-after, and both
are the shape of a real removal rather than a redistribution: total sampled allocation for this
workload went **1.71 GB → 1.36–1.58 GB → 1.01–1.12 GB** across the two fixes, measured at
20000 iterations throughout. Note the width of those bands: run-to-run spread on the same build
is ±15%, so only non-overlapping ranges mean anything here.

The parse row was chased next, and the obvious reading of it was wrong. `BaseParser.<init>` is
shared by the Gherkin and the JS parser, and splitting it by caller (`jfr print --stack-depth`,
§5 — the digest collapses to the innermost Karate frame and hides this) puts roughly three
quarters of it under `JsParser`, not `GherkinParser`: re-parsing **karate-config.js**, not the
feature file. Path resolution splits the same way — `Suite.<init>` and
`Suite.tryLoadConfigResource` resolving the config file, not the feature. See
[§9](#parsing-and-reading-the-same-file-per-execution--partly-built) for what that led to, and
note that the null pair turned out to be the *wrong workload* for the biggest finding: a
one-step feature barely exercises the thing that dominates a real suite.

**`http` pair — 2000 iterations = 4000 requests against the sibling mock.** Wall-clock is
mock-bound and says nothing (see §2); allocation and heap do:

| | sampled allocation | peak heap |
|---|---:|---:|
| `gatling-http-plain` | 190–215 MB | ~45–65 MB |
| `gatling-http-karate` | 460–560 MB | ~70–110 MB |

**Roughly 2–3x the allocation for the same 4000 requests** — a sampled profile, so the ratio
is a magnitude, not a measurement, and quoting it to a decimal place would overstate what it
can support. The Karate HTTP client is the bulk of it: `ApacheHttpClient.invoke` ~15–25%,
`buildResponse` ~2–7%, and `initHttpClient` **~5–6%** — the last being the notable one, because
it means a client is being constructed per execution rather than reused. The port plan's
`PooledHttpClientFactory` (GATLING.md §2.1) was never built, and this is what that costs. Not
investigated further here.

The `buildResponse` and `invoke` bands are wide because allocation sampling attributes the same
`byte[]` work differently run to run — read them as "the client dominates", not as a series.
Part of what `buildResponse` used to carry was the per-step log capture, which is now off by
default in this lane ([§9](#default-log-fidelity-under-gatling--built)): `Json.parseLenient`,
`LogContext.log` and `LogContext.collect` no longer appear in this profile at all. The
**total** band above is unchanged by that — six or seven points of a ~500 MB sampled profile is
inside the run-to-run spread.

*Baselines are shapes, not thresholds. Absolute numbers move with hardware and JDK; the
linear trend and the ratios are what travel.*

---

### Soak mode — what a multi-hour recording has to drop

**At `settings=profile` the harness cannot record a soak at all**, and the way it fails is silent:
`maxsize` is a cap, so the recording *rolls*, discarding its oldest chunks. Measured on
`scope-capture-bound`, the recording grows at **~1.2 GB/hour**, so the 512 MB default starts
dropping history after ~25 minutes — and the heap-after-GC floor **over hours** is the entire
detector. An eight-hour soak would have produced a digest describing its last twenty-five minutes.

The thing that had to be measured inverted the obvious guess:

**The file is dominated by GC internals, not by sampling.** `jdk.GCPhaseParallel` 1,123,457
events, `PromoteObjectInNewPLAB` 126,183, `TenuringDistribution` 55,560 — against the 7,408
`jdk.GCHeapSummary` events a soak actually reads. Thirty young collections a second times a few
hundred phase events each is the whole file. Disabling the allocation and CPU samplers moved 41 MB
to 37 MB — a 10% dent, because the samplers were never the bulk. Switching `settings=profile` to
`settings=default` moved it to 35 MB: the phase events are on in both, and while it does turn the
two `PromoteObject*` events off (they are profile-only), those are a tenth of the volume. Only the
full disable list reaches ~2 MB.

`--soak` therefore uses an explicit disable list. The same two-minute run writes **2 MB instead of
41**, so ten hours projects to ~600 MB against a 4 GB cap.

> **A correction, left here because the wrong version was published.** This section previously
> claimed that per-event settings are *silently ignored* in the `-XX:StartFlightRecording=` form
> and need the JDK 14+ colon form. **That is false.** Both forms honour them — measured on JDK 24
> with a GC-churning workload: the `=` form recorded 15,300 `jdk.TenuringDistribution` events with
> no disable and **0** with one; the colon form recorded 14,760 and **0**. The observation behind
> the false claim (a "disabled" event still appearing 7,740 times) was never explained, and the
> likeliest cause is reading a stale `run.jfr` from a previous run directory, which would produce
> exactly that. The claim was also self-contradicting on its own terms: the same text said the
> disables "did nothing" *and* that they shrank the file by 10%. `--soak` still uses the colon
> form, because it is the documented modern syntax — not because the other one is broken.

**What it keeps, deliberately:** `jdk.GCHeapSummary` (the floor series), `jdk.GarbageCollection`
(the pause panel), and `jdk.OldObjectSample` — the leak profiler — **with `stackTrace=true` set
explicitly**. Those three are the whole of what a soak digest reads. The top-level
`jdk.GCPhasePause` survives too, but only as a side effect of naming just its sub-levels; no panel
reads it, and at one event per collection it is not worth the extra token.
That last one is not redundant: `settings=default` enables `OldObjectSample` *without* stacks, and
the first soak proved what that costs, reporting retained types under `by allocating site: (no
stack) 100%`. A leak profiler that cannot name an allocator is not one.

**What it gives up:** *Allocation by site* and *Hot methods*. Do not use `--soak` for the questions
those panels answer — it is for the floor, and nothing else.

## 7. Caveats

- **CPU sampling barely sees virtual threads, and a parallel Runner suite puts every
  scenario on one.** `jdk.ExecutionSample` samples platform threads; a Runner-driven
  workload can produce single-digit sample counts over several seconds of saturated CPU.
  The *Hot methods* panel is therefore near-useless for scenario code there, and the
  absence of a method from it proves nothing. Prefer *Allocation by site*, which does
  attribute across virtual threads. *Hot methods* is trustworthy for the mock JVM
  (`--record mock`) and for platform-thread paths — **which includes the whole Gatling
  lane**: `Runner.runFeature` builds a non-parallel Suite, so `FeatureRuntime` runs the
  scenario inline on the Gatling thread that called it. Read this caveat as being about
  *parallel Runner suites* specifically, not about Karate generally; see
  [§10](#what-the-result-does-not-license-and-what-is-next).
- **Throughput numbers off a laptop are shape-only.** Thermal throttling, other processes
  and the mock sharing the same cores make absolute req/s unusable. Ratios between two
  runs taken back-to-back on the same machine are fine; anything else is not — **and only
  when the thing being ratioed is the bottleneck.** Two clients saturating the same mock
  report identical numbers that ratio to exactly nothing, which is what the `http` pair does
  against the **feature** mock — `--mock feature`, still the default. With `--mock-latency`
  it is the other way round: that pair is now the one configuration here whose ratios mean
  something, because the mock is demonstrably out of the way ([§10](#10-the-latency-mock-and-what-the-parity-matrix-found)).
  Which mock a `gatling-http-*` run used is now recorded in `run-meta.txt` (`mock:`), alongside the
  gap since the previous run; `mock.log` remains the corroborating tell, since only `LatencyMock`
  writes a `PROFILING-MOCK-CONFIG` line. Runs taken before that field existed have neither — check
  `mock.log` for those before ratioing anything.
- **Collector artefacts are not findings.** ZGC returns committed memory to the OS, so
  "committed shrank to match used" is ZGC behaving normally, not a fix taking effect. G1
  and ZGC also populate `jdk.GCHeapSummary` and `jdk.OldObjectSample` differently — never
  compare a G1 digest against a ZGC one. And see
  [Reproducing a specific collector](#reproducing-a-specific-collector): `--gc zgc` means
  different things on different JDKs.
- **`settings=profile` is not free.** It costs a few percent and biases toward whatever
  allocates in large enough chunks to be sampled. Fine for finding a 10× problem; not a
  microbenchmark.
- **Sampling is sampling.** A site absent from the digest is not proof it allocates
  nothing — it is proof it didn't allocate enough to be sampled during the measured
  window.
- **Warmup matters.** The recording is delayed past the workload's warmup. A digest
  dominated by class loading and JIT means the warmup was too short for what you ran.
- **These runs eat disk, and a full disk ruins a matrix.** See
  [Disk hygiene](#disk-hygiene--prune-after-every-result) — it is not housekeeping advice,
  it is a precondition for a sweep completing.

---

## 8. What the parallel-execution memory investigation settled

Kept because two of these findings reversed a confident, well-argued reading of the code,
and the reasoning is worth more than the conclusions.

### Three mechanisms, only one of which was the reported one

A user reported an `OutOfMemoryError` under parallel execution on 2.0.10, with a heap dump
putting 89% of a 3.87 GB live heap in the stack locals of a 13-deep self-recursion — the
geometric-decay signature of nesting described in §4.

| Mechanism | Verdict |
|---|---|
| Scope-capture nesting (each capture containing all previous ones) | **Already fixed before the investigation began.** No call form returns the caller's scope on main, so the nest cannot form. `Probe` measured this directly instead of inferring it: every call form returns 2 container nodes. |
| Retained call results, `SuiteResult` → `FeatureResult` → `stepResults` → `callResults` | **Real.** ~143 KB per scenario held for the whole suite. Fixed by releasing at scenario end when nothing will read it. |
| Report writing | **Real, and the largest.** Not retention at all — see below. |

The reporter had *retracted* the second as a rounding error and pointed at the first. Both
judgements were inverted. Only measurement separated them, and the reason the harness was
built before any fix was attempted is that every code reading was consistent with the
reported cause while still pointing at the wrong thing.

### The reports-on problem was a queue, not a leak

With reports enabled, peak heap was ~4.5x the reports-off run and grew with suite size. It
read exactly like retention. It was not.

Each of the three report listeners owned a single-thread executor with an **unbounded**
queue. Rendering one feature's HTML cost ~3.4x the suite's entire wall-clock when summed
over all features, so that thread could never keep up: the queue grew for the whole run,
holding a complete page model per queued feature. Peak heap tracked the number of features
rather than the number in flight.

Three pieces of evidence, none conclusive alone:

- **Format isolation.** Every mode containing JSONL peaked at ~340–475 MB; every mode
  without it, up to 2023 MB — with HTML *alone* the worst. Enabling more formats used less
  memory, because JSONL slowed the producer enough for the writer to keep up.
- **The digest.** 99.6% of retained bytes were `[B` attributed to `inlineJson` (51.6%) and
  `renderFeatureHtml` (27.8%) — rendered strings, not result objects.
- **Timing.** Writer-thread load of 3.4, with HTML render at 92% of all deferred work
  (p50 21 ms, p99 1023 ms per feature) while the Cucumber and JUnit writer threads sat
  idle at 23% and 4% of wall-clock.

**What changed:** all three listeners now write on the feature's own thread. The worry that
this would block scenario execution was measured first and proved backwards — spreading the
work across N feature threads beat one background thread, and wall-clock *fell*.

Also in `HtmlReportWriter`: the report data was spliced into the page **first** and four
smaller fragments after it, so each of those four `String.replace()` calls copied the whole
page. Reordering to splice the small fragments into the template and the data last, then
writing prefix / data / suffix straight to the file rather than building the page at all,
removed roughly six full copies of every page. The inlined JSON is also no longer
pretty-printed — nothing but the page's own JavaScript reads it, and indentation on a deeply
nested result tree was showing up as `StringUtils.pad` in the allocation profile.

### Lessons that generalise

- **An unstable number is a race.** Retention reproduces; queue depth does not. Two
  identical runs 400 MB apart is a diagnosis, not noise to average away.
- **Allocation-site attribution names the allocator, never the holder.** It is a ranking of
  where bytes were born. Deciding *what to change* needs the holder — enable
  `path-to-gc-roots` and report by root.
- **One workload shape will mislead you.** `call-accumulation` is a single mega-feature, so
  it silently scores every per-feature strategy at zero. `feature-spread` exists because a
  design was nearly chosen on the evidence of the one shape that forced it.
- **Measure the cost before designing around it.** "Writing HTML inline would block
  execution" was the premise behind the queue that caused the leak. It took one measurement
  to disprove and would have taken the same measurement at any point in the preceding years.

---

## 9. Parked, and not built

Recorded so none of it is re-derived from scratch. Nothing parked here is scheduled.

**Not everything here is unbuilt.** Sections headed "— built" are finished work, kept in this
section because §6 points at them and because each one left something parked behind it. As of
2026-08-06 those are: default log fidelity under Gatling, the per-scenario Logback level
snapshot, exceptions on the happy path, and the built half of per-execution reading and parsing.
The **harness** gained four things the same day — injector CPU in every digest, `profiler compare`,
`--mock-url`, and per-host network limits — all described in
[The two-host phase](#the-two-host-phase-what-is-built-and-what-it-is-for).
Everything else is a lead or a design, not code — including
[Parsed-JS reuse](#parsed-js-reuse--measured-half-gated-not-built), which is the largest measured
win recorded in this document that has not been taken. It was gated on a measurement rather than on
judgement, and the measurement that came back covers **one of the two lanes the cost lives in**.

### If you are picking up the Gatling thread — the order

The instrument and the first matrix are **built**
([§10](#10-the-latency-mock-and-what-the-parity-matrix-found)), and what they measured has already
retired one item and reshaped two others. This list is the steering surface — it is what the next
session reads — so it is ordered by information per unit of work, not by appeal:

**Read item 0 first.** The harness gained four things it was missing, and they are what the
two-host phase runs on: injector CPU in every digest, `profiler compare`, `--mock-url`, and
per-host network limits. Each is described in
[The two-host phase](#the-two-host-phase-what-is-built-and-what-it-is-for).

0. **Run the matrix on a quiet, dedicated pair of hosts.** This is now the top item, because the
   50 ms attempt established that the laptop cannot resolve the tier at any pair count — and
   because everything below it inherits the same noise floor. It also retires the confound §10
   has been carrying since the first matrix. See the phase description in §10 for the shape,
   the order and the checks.

1. **~~More pairs at the 50 ms tier~~ — tried, and it answered a different question.** Three more
   pairs were run on this laptop and made the spread *worse*: sd 6.90 for pairs 4–6 against 2.72 for
   1–3, individual pairs from −6.9 to +6.9 ms (§10). The remedy was wrong because the diagnosis was:
   **the tier is machine-limited, not pair-limited.** A ~0.6 ms cost on a 110 ms iteration is 0.5%,
   this laptop's floor is 3–6%, and no pair count closes an order of magnitude. Superseded by the
   two-host phase below — the runs are the same runs, on a machine where they can resolve.
2. **The AST prototype on the lane where its win lives** — a `/usr/bin/time` A/B against main on
   `call-accumulation` and `feature-spread` at two sizes, recording wall and CPU. This is the
   measurement the parse-cache decision actually needs
   ([Parsed-JS reuse](#parsed-js-reuse--measured-half-gated-not-built)); a Gatling latency result
   cannot substitute for it. The prototype is on no branch or stash — recreate it from the
   description there.
3. **HTTP client lifecycle — half built.** *Release* is done: `HttpClientFactory.release()` is
   called at scenario end, `DefaultHttpClientFactory` closes, and a custom factory keeps today's
   behaviour unless it opts in (the default is a no-op, because a custom factory's client may be
   shared). That removes the fd leak — measured, a feature making three calls created 5 clients and
   released 0. **Pooling is not done**, so Karate still opens a connection per iteration, which is
   the part that costs +1 RTT (plus TLS) against a real API. The factory seam is where it goes.
4. **A pooled-client A/B in the harness.** Prices the per-execution client — §6's last unchased
   lead — and removes the one structural difference between the arms, which is what would let the
   parity result generalise past loopback.
5. **A TLS tier and a body-size tier on `LatencyMock`**, plus the two equivalence-control runs
   (plain checking all three fields; karate doing a minimal extraction). The smallest experiments
   that can falsify "at any realistic API latency" before separate hosts exist.
6. **The user ramp**, paired with one open-arrival (`constantUsersPerSec`) lane so offered-versus-
   completed load is visible — the closed model structurally hides it. It answers the
   concurrency-density question below. Kept after 1–5 because those change what its numbers mean.
7. **`--duration` for the `gatling-*` workloads** — `during()` in the injection profile instead of a
   repetition count. Small, and with item 3 it is what unblocks a Gatling **soak**, the largest
   unanswered question in this document (§2).
8. **~~Separate hosts~~ — promoted to item 0** and now unblocked on the harness side (`--mock-url`,
   `LatencyMock --bind/--standalone`). Co-location is the one confound §10 cannot argue away, and
   it lifts the ephemeral-port ceiling.
9. **The injector-health check** — designed in **[GATLING.md §14.12](./GATLING.md)**, not built.
   It is the mitigation for this document's own sharpest finding: Karate's overhead sits *between*
   `PerfEvent` brackets, so an under-provisioned injector reads as a throughput shortfall with
   clean percentiles and nothing in the report looks wrong. Gate on process CPU and heartbeat
   jitter; report the per-iteration residue but never gate on it, since Karate legitimately spends
   a millisecond or two. Ordered here rather than higher because item 6's ramp is what says how
   much the underlying overhead matters.
10. **Mine [#845](#prior-art--the-09x-era-overhead-thread).** Reading, not building. Now worth
   reconciling *against* a result rather than before one.

### Per-scenario spill — designed, reviewed, deliberately not built

The remaining unbounded case is a **single feature holding thousands of scenarios** (a
data-driven `Scenario Outline` with many rows). Feature-end is suite-end there, so no
feature-scoped release helps; only releasing per *scenario* changes the slope.

The design that would fix it: at scenario end, serialize each scenario's record, append it
to one temp file per in-flight feature, strip `callResults` / step logs / binary embed bytes
from the retained skeleton, then at feature end assemble each output format by streaming
those records back in `compareTo` order and delete the temp file.

**Three adversarial reviews found enough to stop.** Recorded so the next person weighing it
starts from the objections rather than the idea:

- **v2 has no deserialization layer.** v1's equivalent worked because every result class had
  a `fromKarateJson` twin maintained in lockstep. v2 deleted all of it, and all three writers
  consume live objects. The spill therefore needs either that layer rebuilt or Map-consuming
  twins of three writers — the largest cost in the design, and it appeared in no estimate.
- **A `toJson()` spill record cannot reproduce today's HTML.** `StepResult.toJson()` passes
  the step log through `stripAnsi`, which removes the syntax-highlight sentinels the page
  model needs. Every HTTP body would lose its highlighting.
- **Ten-plus load-bearing special cases**, including: post-spill mutation of the last
  scenario by the afterFeature hook, feature-level synthetic scenarios that never pass the
  scenario seam, JSON-mime embeds that must survive the strip, JUnit stack traces absent from
  `toJson()`, and synthetic step display text that lives in the `log` field being stripped.
- **The concurrency bound is not what it looks like.** Every feature and every scenario is
  submitted to the executor immediately, with the semaphore acquired *inside* the task, so
  nearly every feature is in flight for most of a run. "One temp file per in-flight feature"
  is O(all features) in practice, not O(threads).

**If it is ever revived:** bound feature dispatch first, spill per-format *fragments*
produced by today's writer code rather than a canonical record (which makes output fidelity
structural instead of test-enforced), and replace "merge spilled with never-spilled" with the
invariant *every scenario is spilled exactly once, when it becomes final*, handling the two
known exceptions explicitly.

A cheaper partial alternative, also unbuilt: strip the `FeatureResult` at feature end, after
the listeners have returned. Bounds memory at O(threads x feature size) with no compatibility
break — but scores exactly zero on the mega-outline shape, which is the only case left.

### "Karate doesn't play nice with Gatling's async model" — what would actually settle it

The standing criticism is that karate-gatling drives Karate's own blocking HTTP client instead
of Gatling's, and so sits outside the non-blocking scheduling everything else in Gatling is
built around. The mechanism is real and is worth stating precisely, because the fix people
reach for is not the cheapest one.

`KarateScalaAction.execute` runs the whole feature synchronously on the thread Gatling handed
it, and Karate's steps block on I/O. Gatling advances its virtual users on a small fixed pool,
so a Karate feature waiting on a response occupies a scheduling slot that a native Gatling user
would have yielded. **The cost of that is concurrency density — how many virtual users one
injector can hold — not per-request CPU**, which is what §6 measures. It would show up as
achieved throughput plateauing well below the requested user count, and, worse, as *reported
response times inflating* once users queue for a thread rather than for the server.

**None of that is measured yet, and it should be measured before it is designed around.** The
experiment: drive the `http` pair at rising user counts against a mock with injected latency
(the same knob §2 wants for the overhead question), and watch whether Karate's achieved
concurrency tracks the requested users or flattens. A plateau at a fixed number is the
signature, and the number would be the thread pool. Until that exists, every option below is a
solution to an unquantified problem.

The options, cheapest first:

| Option | What it buys | What it costs |
|---|---|---|
| **Run the feature off Gatling's thread** — on a virtual thread, completing the action asynchronously | Frees the scheduling slot while Karate blocks, with no change to Karate's engine, its client, or any user-visible behaviour. `PerfHook.submit()` already exists as the seam and currently runs inline (`runnable.run()`) | Needs care that a blocking call on a virtual thread parks rather than pins the carrier — httpclient5's internal synchronization is the thing to check |
| **Apache HttpClient's async API** (httpclient5 supports both) | Keeps the whole config surface — cookies, SSL, retry, logging, masking — in the same client family it is written against today | Karate's step model is synchronous: `When method get` returns a response. An async transport under a synchronous caller yields nothing on its own, so this only pays off combined with a way for the caller to suspend |
| **Gatling's own HTTP client** | Native to the model the criticism is about | The largest behavioural break available. Karate's HTTP config surface is user-facing and years deep; a second client means either reimplementing it or accepting that a feature behaves differently under `karate perf` than under `karate test` — which destroys the one property that makes karate-gatling worth having |

The ranking is not close, and it is the same lesson as §8: the expensive option is the one
that changes semantics, and none of them should be started before a number says which
constraint is actually binding.

### Default log fidelity under Gatling — built

Kept here because §6 points at it and because what it left behind is still parked. **Under
Gatling, assume no HTML report and no logging except on errors**, everything else opt-in — the
per-step log capture is now off in that lane, gated before the string is built, with
`Runner.Builder.captureStepLogs(boolean)` as the override and `logReplay` (the one reader)
turning it back on for itself. Design and verification: **[GATLING.md §14.9](./GATLING.md)**.

Measured on `gatling-http-karate --iterations 2000`: `Json.parseLenient` 3.7%, `LogContext.log`
1.6% and `LogContext.collect` 1.2% are all gone from the profile — the ~6.5 points the
throwaway guard predicted. The workload's *total* allocation band is unchanged, which is what
six or seven points of a sampled ~500 MB profile looks like; §14.9 has the table and the
caveats.

### Per-scenario Logback level snapshot — built

What the entry above left behind, and the next thing done. `LogContext.snapshot()` walked eight
logger names reflectively on every scenario, and `restore()` walked them again — to put back
levels that nothing had changed, because only `configure logging = { console: ... }` changes
them and almost no scenario runs it.

The snapshot is now lazy: it registers on a thread-local chain and records nothing, and
`setRuntimeLogLevel` captures the "before" for every live snapshot in a single walk immediately
before it changes anything — the last moment that state exists. Nesting still restores one
level at a time (a `call` snapshots inside its caller's, so the inner restore lands on what the
outer scenario configured). One deliberate semantic change: a level changed by any other route
is no longer clobbered on restore with a value this thread never observed.

Measured on `gatling-null-karate --iterations 20000`, the workload where per-execution fixed
cost is the whole profile:

| | before | after (2 runs) |
|---|---:|---:|
| `LogContext.captureRuntimeLevels` | 5.6% (97 MB) | — |
| `LogContext.setLevelOn` | 4.4% (77 MB) | — |
| total sampled allocation | 1.71 GB | 1.57 GB / 1.58 GB |

**10% of the null-pair profile, and this time the total moved with it** — unlike the log-capture
entry above, where the removal was real but too small to see against sampling spread. Both sites
are also gone from `gatling-http-karate`, where they had been 1.5–3.4% and ~1%.

### Exceptions on the happy path — built

§6's null-pair breakdown had a row reading "**exceptions constructed on a happy path** — nothing
in this feature fails", never chased. Chasing it found the exceptions were the symptom; each one
sat on top of a lookup that was being redone from scratch every time.

**What `karate.properties['mock.url']` cost when the key is absent** — the
`karate.properties['x'] || 'default'` shape every `karate-config.js` uses. The read falls
through the JS property chain to the Java bridge, whose caller (`PropertyAccess.accessViaBridge`)
turns whatever comes back into `undefined`. Getting to that `undefined` took, per read:

1. `getMethod("getMock.url")` → `NoSuchMethodException` + a full `Class.getMethods()` array copy
   and scan;
2. `getMethod("isMock.url")` → the same again;
3. `getField("mock.url")` → `NoSuchFieldException`;
4. a third `getMethods()` copy and scan;
5. `JsErrorException.typeError(...)` → a `JsError`, a `HashMap` for its message, and a stack
   trace — all discarded by the catch.

Four exceptions and three array copies to answer "no". Alongside it, two more O(n) costs on the
same read: `PropertyAccess` built a **whole JsObject copy of the Map** whenever a key missed
(just to reach `Object.prototype`), and `karate.properties` / `karate.sysprop` each materialised
a fresh `HashMap` of *every* JVM system property. And once per Suite, the config probe for a
`karate-base.js` / `karate-config-<env>.js` that most projects do not have threw
`ResourceNotFoundException` to say so.

**The fixes**, all "a miss is an answer, not an event":

| | |
|---|---|
| `JavaUtils` | member resolution per (class, name) — getter / field / method / not-found — kept in a `ClassValue` map, and a non-throwing miss for the bridge that swallows it |
| `PropertyAccess` | a shared empty probe object to reach `Object.prototype`, instead of copying the Map |
| `Suite` | `karate.properties` is a live view (`get`/`containsKey` straight through, iteration still materialises) and `karate.sysprop(name)` is a single lookup |
| `Resource.optional(path)` | the probing form of `Resource.path`, returning null; used by the config lookup |

Measured on `gatling-null-karate --iterations 20000`, in the order they were applied:

| after | total | what left the panel |
|---|---:|---|
| (baseline) | 1.36–1.58 GB | — |
| `JavaUtils` + `sysprop` | 1.23 GB | `findMethodDirect` 8.7%, `JavaUtils.get` 5.0%, `JsErrorException` 1.4% |
| prototype probe | 1.11 GB | the map copy, which had surfaced at 13.6% once `sysprop` stopped hiding it |
| `Resource.optional` | 1.01–1.12 GB | `ResourceNotFoundException` 3.1% |

**No exception constructor appears in the allocation panel any more**, and the before and after
bands do not overlap. Each step is worth reading as "the next cost became visible once the one
in front of it was gone" — the map copy was always there, charged to
`Suite.getSystemProperties` until that stopped copying.

Everything here is generic, not config-specific: `response.absentField` on a large JSON body was
paying the same map copy, and any `x.y` miss on a Java object paid the same four exceptions.

### Parsing and reading the same file per execution — partly built

§6's last unchased row. Two pieces are built, and the third — the big one — is measured and
deliberately left for a decision, below.

**Built: an in-memory resource no longer encodes its text to bytes.** `MemoryResource` computed
`FileUtils.toBytes(text)` in its constructor, and nothing on the eval path ever asks for the
bytes: it is built to be read as text (parse, eval). `karate-config.js` is wrapped into one of
these *per scenario*, and so is every step expression. **8.0% of the null-pair panel**, and
`FileUtils.toBytes` is gone from it. The workload total did not move outside its ±15% band, so
read this as a site removed, not as a total proved.

**Built: config is parsed once per Suite, evaluated per scenario.** `karate-config.js` was
lexed and parsed for *every scenario* — a 2000-scenario suite parsed the same file 2000 times —
because config evaluation is per-scenario by design and the parse rode along with it. The Suite
now holds the parsed AST (`Suite.ConfigScript`) and every scenario evaluates that. The
wrapped-in-parentheses / direct-eval decision is cached with it; the fallback is still triggered
by *evaluation* failing, exactly as before, so a config that throws at runtime still gets the
second chance it had.

Two things to be honest about here. The AST is shared across scenarios running in parallel,
which is safe because a `Node` is read-only once parsed — but that is a property of today's
parser and interpreter, not a guarantee anything enforces. And **no current workload measures
this fix**: in `call-accumulation` the config parse sits below the sampling floor (0 samples,
before *and* after), and in the null pair the cost relocates rather than disappears, because
that lane builds a Suite per execution and so parses once either way. The saving is structural —
N parses become 1 — and the guard is a test asserting config still *evaluates* per scenario
(`ScenarioConfigTest`), which is the property a parse cache could plausibly break.

### Parsed-JS reuse — measured, half-gated, not built

**Every JS step expression is re-lexed and re-parsed on every step execution.** `* def x = 1` in a
scenario that runs 2000 times is parsed 2000 times, and `karate.call('x.feature')` re-reads and
re-parses the callee, so 60 calls in each of 2000 scenarios is 120 000 parses of one small file.

A throwaway process-wide cache in `Engine.evalInternal`, keyed on (path, line offset, source),
measured the ceiling on `call-accumulation --iterations 2000`:

| | total sampled allocation | `BaseParser.<init>` |
|---|---:|---:|
| main | 8.04 GB | 1.09 GB (13.5%) |
| prototype AST cache | **5.33 GB** | 184 MB (3.4%) |

**A third of all allocation, and it is deliberately not built.**
[§10](#10-the-latency-mock-and-what-the-parity-matrix-found) was the gate — and the gate has been
read, but it covers one of the two lanes this cost lives in, and not the one the win was measured
in. Both halves, stated separately, because it is easy to generalise the first to the second:

- **The load-test lane — answered.** At 10 ms of server latency Karate's *entire* per-execution
  overhead, of which re-parsing is one part, costs about 2% of throughput, and it shrinks with
  latency from there (§10). Whatever share of 2% this cache would recover is not worth a cache, an
  eviction policy and a new mutable object graph, and 0 ms — where the overhead is a 2x gap — is
  not a system anyone tests against.
- **The ordinary-suite lane — still open.** The 8.04 → 5.33 GB above was measured on
  `call-accumulation --iterations 2000`: an ordinary parallel Runner suite, sixty `karate.call()`s
  per scenario, and **no HTTP in it at all**. "Does the cost survive contact with a network" cannot
  even be posed there — there is no network time for it to hide in, and there is none in the
  JS-heavy stretches between a real suite's HTTP calls either. §6 says this itself: the re-parsing
  is "an ordinary-suite cost that the Gatling lane merely made visible". What a cache would buy
  that lane in **CPU and wall-clock has never been measured** — the table above is allocation only,
  and §4's [footprint constraint](#the-standing-constraint-footprint-is-fine-a-rising-floor-is-not)
  makes the churn *acceptable*, which is a licence not to worry about it as memory, not evidence
  that 120 000 re-parses cost no CPU.

**The measurement that would close the second half is cheap, and it is not the matrix:** recreate
the throwaway prototype and A/B it against main on `call-accumulation` and `feature-spread` at two
sizes each under `/usr/bin/time`, recording wall and CPU next to the allocation numbers — the §6
replay method, for the same reason §6 needed it. If they do not move, the park is supported in both
lanes and this section can say so; if they do, the decision was made on the wrong evidence.

**The second reason to park it does not depend on either lane, and it stands.** A keyed cache needs
a scope, a bound and an identity rule; the better design — the AST on the `Step` — needs the
shareable-model work below first. That argument would justify not building this now even if the
CPU A/B came back positive.

**If it is ever revived, hang the AST off the `Step`, not off a map.** A keyed cache needs a scope,
a bound and an identity rule; a field on the Step needs none of them — no key, so no identity rule;
reachable only from the parsed model, so bounded and released with it, which is what §4's
[footprint constraint](#the-standing-constraint-footprint-is-fine-a-rising-floor-is-not) is happy
to pay for.

The catch is that it only pays if `Step` objects are *reused*, and none of the three repeating paths
reuse them today — a `karate.call()` re-parses the callee, an outline row copies its steps, and
karate-gatling re-parses per execution. **So it is not a simpler alternative to sharing the parsed
model; it is what falls out of it.** Making the model shareable is the real work: an immutable
`Feature`/`Scenario`/`Step` plus a runtime-side composite holding what execution mutates. Three
mutations have to move, all on the plain-scenario path (outline rows already take copies):

| What is written today | Where | Where it would live |
|---|---|---|
| `Scenario.selected` — a per-suite tag-selection memo on the model | `Suite.sectionCanMatch`, undone by `clearScenarioSelectionCache` | per-Suite, keyed by scenario identity |
| `scenario.setName(...)` for an evaluated dynamic name | `ScenarioRuntime.evaluateScenarioName` | the runtime / the `ScenarioResult` |
| `ScenarioOutline.numScenarios`, an ever-incrementing counter feeding `karate.info` | `ScenarioOutline.toScenario` | per-run counter |

One trap to carry in: `Scenario.replace()` rewrites step text for `<placeholder>` substitution, so a
step copy must not inherit its template's cached AST — the AST belongs to the text, and that is the
one place text changes after parsing.

### Prior art — the 0.9.x-era overhead thread

[karatelabs/karate#845](https://github.com/karatelabs/karate/issues/845) is a long thread from
the karate 0.9.x days about exactly this question, and it has not been mined. The recollection
going in is that **Karate's overhead came out roughly comparable to Gatling's** — which, if the
thread bears it out, is a prior that the numbers in §6 should be reconciled against rather than
replacing silently. Worth extracting before any deeper measurement work:

- what was measured, on what shape of workload, and against what baseline — a "comparable"
  from a thread that only ever drove a saturated localhost mock says less than one taken
  against a realistic target;
- which specific costs were identified and whether they still exist (v2 rewrote the engine, so
  a v1-era hot spot may be gone, moved, or newly introduced);
- the failure modes and dead ends, which are the part that does not go stale.

The decision it leaves open is bigger than the measurement: keep paying the cost of driving
Karate through Gatling's actor model and Scala runtime, or **write a perf framework native to
Karate** that starts from what both the thread and this document have already learned. Nothing
here forecloses either; the parity workloads exist precisely so that choice can be made on
numbers.

### Other deferred items

| Item | Note |
|---|---|
| Copy-on-first-change in `processEmbeddedExpressions` | A real inefficiency independent of any leak: a fresh `LinkedHashMap`/`ArrayList` is rebuilt for every Map/List node walked, unconditionally, with no check for whether `#(...)` appears at all — v1 never copied the payload. Three traps if revived: `processInlineEmbedded` returns a *new equal String* for every string, defeating identity-based change detection unless it returns the original when nothing substituted; the XML branch mutates in place, so "source intact" holds for Maps and Lists only; and `resolveConfigMap` has a javadoc promising a defensive copy. Pursue on allocation numbers, not on a leak report. |
| Leak-watch family | See §2. The soak question is genuinely unanswered — every current workload is an iteration-bounded reproduction. |
| JS-engine workloads | `js-array`, `js-object`, `js-engine-init` built from `EngineBenchmark`'s generators, so JS tuning gets forking, JFR and digests too. |
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table. `--record mock` already exists to support this. **Partly built:** `LatencyMock` (§10) is the cheap tier and carries the latency knob; what is unbuilt is the *table* comparing raw handler against JS handler against feature mock. |
| Gatling parity — **partly built** | The null-overhead probe, the parity sim and the allocation comparison exist (§2, §6). What is left: a **throughput ceiling**, which needs a mock tier cheap enough not to be the bottleneck — today both variants saturate the feature mock, so req/s measures the mock. That tier is the "Mock throughput tiers" row below, not a separate piece of work; see the ordering note at the top of this section. Also unbuilt: `--duration` support, which needs `during()` in the injection profile rather than a repetition count, and is the prerequisite for a Gatling soak. Note the entry point is `Gatling.fromArgs`, not the `fromMap` this row used to name — `fromMap` was removed in Gatling 3.15. |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest`, so a recording carries Karate semantics and allocation attributes to a *feature line*. **A CPU-tuning need, not a memory one.** The virtual-thread gap is specific to `jdk.ExecutionSample`; everything the memory work relied on attributes correctly regardless of thread model. Build these when the question becomes "where is the CPU going during a parallel run" — exactly where `ExecutionSample` goes blind. |
| `profiler compare A B` | Side-by-side delta table from two run directories. |
| Machine-readable baselines + CI | Committed `baselines/*.json`, a scheduled job, regression thresholds. Out of scope until the manual playbook has proven itself. |
| `jcmd GC.class_histogram` checkpoints | Per-class growth over time. Forces a GC, so it perturbs the measurement. |
| Heap-dump class histogram in `JfrDigest` | Deliberately not implemented: no JDK API or CLI reads an `.hprof` (`jhat` removed in Java 9, `jmap -histo` is live-process only). The digest points at Eclipse MAT instead. Revisit only by hand-rolling a histogram-only reader or taking a dependency. |

---

## 10. The latency mock, and what the parity matrix found

Everything in §6 was measured against a mock that answers in about a millisecond — the
configuration that most flatters client-side cost. This section is the instrument built to ask the
question properly, and the answer it gave.

**The question:** does Karate's per-execution overhead distort a load test, or does it disappear
into the network time of a real API? **The answer, measured rather than bounded: roughly half a
millisecond to a millisecond of added serial time per iteration — 2.1–2.4x throughput when the
iteration is ~1 ms, ~2% when it is 28 ms, and below what this machine can resolve when it is
110 ms. It shrinks as a share of the iteration; it does not vanish.** The 110 ms case is
unresolved rather than small: six pairs put the spread at more than three times the effect, and
[the 50 ms tier](#50-ms-tier--1600-iterations-200-per-user-110-ms-iteration) explains why more
pairs on this machine cannot fix that. And the one overhead that would not
shrink at all — a fresh TCP connection per iteration, now counted rather than assumed — is priced
at approximately zero by this harness and cannot be anything else on loopback. Details below; the
reasoning behind each design decision lives in the class javadocs, which are written to be read.

### The pieces

| | Where | What it is |
|---|---|---|
| `LatencyMock` | `karate-profiling/.../profiling/LatencyMock.java` | The instrument: JDK HTTP server on virtual threads, the same three endpoints as `profiling-mock.feature`, a `--latency` knob, and no shared parser/client/allocator with what it measures |
| `MockStats` | `.../profiling/MockStats.java` | Its self-instrumentation — served, own service time excluding the injected sleep, held time, peak in-flight, `/stats` and `/stats/reset` |
| `MockCalibrator` | `.../profiling/MockCalibrator.java` | Finds where the mock stops being free, per request, in both connection modes |
| `LoadProfile` | `.../profiling/LoadProfile.java` | Puts the client-side distribution into `digest.md` so two runs diff as text |

**The feature mock is not replaced.** The two are different kinds of thing: `profiling-mock.feature`
is a *subject* (`--record mock` profiles gherkin matching and JS evaluation inside it), `LatencyMock`
is an *instrument*. Conflating them is how the throughput ceiling stayed stuck for so long.

### Running it

```bash
# a parity cell — both arms, same settings, back to back
etc/run.sh gatling-http-plain  --iterations 4000 --threads 8 --mock-latency 10ms
etc/run.sh gatling-http-karate --iterations 4000 --threads 8 --mock-latency 10ms

# derive the table rather than reading it off the digests
etc/run.sh compare target/profiling/gatling-http-*

# the same cell against a mock on another host — see "The two-host phase"
# on the mock host:
java -cp target/classes:$(cat target/cp.txt) io.karatelabs.profiling.LatencyMock \
     --bind 0.0.0.0 --port 8090 --standalone --latency 10ms
# on the injector host:
etc/run.sh gatling-http-karate --iterations 4000 --threads 8 --mock-url http://MOCK_HOST:8090

# re-establish the mock's envelope after any change to it
java -cp target/classes:$(cat target/cp.txt) io.karatelabs.profiling.LatencyMock --latency 10ms
java -cp target/classes:$(cat target/cp.txt) io.karatelabs.profiling.MockCalibrator \
     --url http://127.0.0.1:PORT --ramp 1,4,16,64 --per-user 100 --settle 20s
```

`--mock-latency` implies `--mock latency`; `--mock feature` (the default) keeps the old behaviour.

### The result — machine A, 8 users, zero KO in every cell

The exact commands, so the table can be reproduced rather than trusted. `--threads 8` throughout;
everything else is the workload's default (`--xmx 1g`, G1, JDK 24):

```bash
etc/run.sh gatling-http-plain  --iterations <size> --threads 8 --mock-latency <tier>
sleep 35   # not optional — see below
etc/run.sh gatling-http-karate --iterations <size> --threads 8 --mock-latency <tier>
```

**Leave ~35 s between runs.** TIME_WAIT is 30 s at the default MSL and the karate arm opens a
connection per iteration, so back-to-back runs can meet the ephemeral-port ceiling for reasons that
have nothing to do with Karate — and it reads as "Karate is slower". Nothing in the harness
enforces the gap.

**Throughput is read from the mock, not from Gatling.** Gatling's `count/s` is requests divided by
a duration rounded to **whole seconds**, which at these run lengths quantises the rate in steps of
several percent — enough to render two arms a second apart as an identical figure. `MockStats`
stamps its first handler entry and last handler exit, so `servedPerSecond` is the same requests
over a nanosecond-resolution window. Every figure below is that number, never Gatling's. Runs are
2026-08-06 17:04–17:19; each row names its two directories.

**Three back-to-back pairs per tier, arm order alternating**, so that drift over the matrix cancels
between the arms rather than loading one of them. And there is drift: rates rise ~10% across the
10 ms tier, but **that is the mock, not the clients** — its measured sleep overshoot falls from
~14.1 ms to ~12.25 ms over the same runs, which is the whole of it. Read `sleepMicrosMean`, not the
knob, before attributing anything. "Added ms/iteration" is the window difference divided by
iterations per user.

#### 10 ms tier — 4000 iterations (500 per user), ~28 ms iteration

| pair | order | plain req/s | karate req/s | karate deficit | added ms/iteration | run directories |
|---:|---|---:|---:|---:|---:|---|
| 1 | k→p | 534.8 | 526.4 | 1.6% | +0.48 | `…karate-…-170459` / `…plain-…-170554` |
| 2 | p→k | 547.5 | 530.7 | 3.1% | +0.93 | `…plain-…-170649` / `…karate-…-170744` |
| 3 | k→p | 590.8 | 582.6 | 1.4% | +0.38 | `…karate-…-170839` / `…plain-…-170933` |
| **mean** | | **557.7** | **546.6** | **2.0%** | **+0.59** (sd 0.29) | |

#### 50 ms tier — 1600 iterations (200 per user), ~110 ms iteration

**Six pairs, and the tier is still not resolved — because the limit is the machine, not the pair
count.** Pairs 4–6 were run to close the loose end the first three left. They made the spread
*worse*, and the reason is recorded rather than averaged away: the laptop was in use during them.

| pair | order | plain req/s | karate req/s | karate deficit | added ms/iteration | sleep-corrected |
|---:|---|---:|---:|---:|---:|---:|
| 1 | p→k | 132.6 | 133.1 | −0.4% | −0.45 | +0.64 |
| 2 | k→p | 141.5 | 135.6 | +4.2% | +4.92 | +5.05 |
| 3 | p→k | 140.7 | 137.1 | +2.5% | +2.96 | +2.92 |
| 4 | k→p | 125.4 | 132.5 | −5.7% | −6.88 | −4.87 |
| 5 | p→k | 133.2 | 131.8 | +1.0% | +1.24 | +3.61 |
| 6 | k→p | 137.4 | 129.8 | +5.6% | +6.85 | +7.21 |
| **mean 1–3** | | **138.3** | **135.3** | **2.1%** | **+2.48** (sd 2.72) | +2.87 (sd 2.21) |
| **mean 1–6** | | **135.1** | **133.3** | **1.2%** | **+1.44** (sd 4.83) | +2.43 (sd 4.19) |

Run directories are in the digests; regenerate this table with
`etc/run.sh compare target/profiling/gatling-http-*` rather than reading it off by hand.

**Doubling the pairs nearly doubled the standard deviation.** Pairs 4–6 alone are sd 6.90 against
sd 2.72 for 1–3, and individual pairs now range from −6.9 to +6.9 ms on a ~110 ms iteration. That
is not a noisier estimate of the same quantity converging slowly; it is a different noise floor.

**The contention is visible inside the instrument, which is how it is known rather than guessed.**
The mock's own measured sleep overshoot rose from 54.4–55.2 ms in pairs 1–3 to 54.8–56.3 ms in
4–6, and plain-arm p50 drifted 56 → 62 ms. Both are the machine, not either client.

**So the arithmetic that matters is this:** a fixed ~0.6 ms cost on a 110 ms iteration is a **0.5%**
deficit, and this laptop's run-to-run floor is **3–6%**. The effect is an order of magnitude below
the noise.

**More pairs do converge — that is worth stating precisely, because the first draft of this section
said they would not.** The arm order alternates, so noise hitting both arms leaves the mean
unbiased; averaging works, at `sd/√n`. What it costs is the point. From sd 4.83, resolving a 0.6 ms
effect to within half itself needs **~260 pairs** at one standard error, or **~1000** at roughly 95%
confidence — 10 to 40 hours of machine time, against a fix that costs an afternoon. Halving the
noise is worth quadrupling the runs, which is why the answer is a quieter machine and not a longer
sweep. `profiler compare` prints this figure per tier so the trade is explicit rather than asserted.

**This tier needs a quiet, dedicated machine, and until it has one the honest statement is that
50 ms is unresolved.** See [The two-host phase](#the-two-host-phase-what-is-built-and-what-it-is-for).

**How strong is the "it was the laptop" attribution? Weaker than the wording above implies, and
worth holding loosely.** Three pairs against three is `F(2,2) ≈ 6.4, p ≈ 0.27` — not separable from
sampling noise at these degrees of freedom, and the sd 4.83 estimate itself carries a 95% interval
of roughly 3–12. The sleep-overshoot and p50 drifts are real (verified in the digests) but they move
**both** arms, so they cannot arithmetically explain a ±7 ms swing in the *between-arm* difference;
they establish that the machine was busier, not that busyness produced these particular pairs. And
nothing in any digest can confirm what *other* software was running — `SelfCpu` sees only the
harness's own processes. What is solid without the attribution: **six pairs leave the spread more
than three times the effect**, and that alone is the case for a different machine.

#### 0 ms tier — the pure client-overhead tier, two sizes

| size | plain req/s | karate req/s | ratio | run directories |
|---|---:|---:|---:|---|
| 4000 it | 13 629 | 6 428 | 2.12x | `…plain-…-171730` / `…karate-…-171650` |
| 8000 it | 19 891 | 8 384 | 2.37x | `…plain-…-171811` / `…karate-…-171851` |

Two sizes, so the marginal separates from whatever the first iterations paid for JIT: **karate
1.33 ms per additional iteration, plain 0.43 ms, so ~0.89 ms of it is Karate's.** These windows are
0.6–1.9 s, short enough that they are substantially warmup; read the marginal, not the ratio.

**And the connection counts, which are new and settle a question this harness could not previously
ask.** In every cell, `distinctPeerPorts` equals the **iteration** count for the karate arm (4000,
1600, 8000 — never the request count) and is **8** for the plain arm. So Karate opens exactly one
connection per iteration: its per-execution client pools the POST and the GET, and nothing survives
the execution. Plain Gatling holds one keep-alive connection per virtual user for the whole run.

What the numbers now support, which is less than "identical" and more useful:

- **There is a real, resolvable throughput deficit at 10 ms: ~2%, in 3 pairs out of 3.** The
  earlier "identical" was the whole-second denominator. The size of it is **~0.6 ms of added serial
  time per iteration** (sd 0.29 over three pairs) — which is *below* the ~2 ms the old table
  inferred at 0 ms, so the direction of the old story was right; its arithmetic and its wording
  were not.
- **The 50 ms tier is not resolved, and the honest report is the spread.** Over six pairs the mean
  says +1.4 ms/iteration against a standard deviation of 4.8, with two pairs *negative*. A fixed
  ~0.6 ms cost would predict a ~0.5% deficit on a 110 ms iteration, which is an order of magnitude
  under this machine's floor. **The original reading of this — "more pairs, not a different
  instrument, is what this tier needs" — was tested and is wrong**: three further pairs made the
  spread worse, because they ran while the laptop was in use. What it needs is a quiet machine.
- **The three tiers are consistent with one fixed per-iteration cost of roughly half a millisecond
  to a millisecond**, which is 2.1–2.4x throughput when the iteration is a millisecond or two, ~2% when it is
  28 ms, and below this machine's resolution when it is 110 ms. That is the claim this section
  now makes, and it is a shape rather than a disappearance.
- **Check sleep parity within a pair before believing the difference — it moves this number, and
  it moves it against Karate.** The injected sleep is a real `Thread.sleep` whose overshoot varies
  with load, and the mock reports what it actually took. In all three 10 ms pairs the mock slept
  *less* on the karate side (13 957 vs 14 180 µs; 13 799 vs 13 909; 12 250 vs 12 260), which at two
  requests per iteration hands the karate arm 0.45 / 0.22 / 0.02 ms of iteration back. Add it in
  and the added serial time is **0.92 / 1.15 / 0.40, mean 0.82 ms** (sd 0.38) rather than 0.59. So
  the raw-window figure is the *conservative* one, and the supportable statement for this tier is
  0.6–0.8 ms per iteration. Whether to correct is arguable — the sleep is meant to be the same
  simulated network for both arms, and a systematic difference is itself a confound rather than a
  quantity to subtract — but the check is not arguable, and it is one field in the digest.
- **Some of that ~0.6 ms is not Karate's.** The karate arm opens 4000 connections where the plain
  arm opens 8, and every accept costs the co-located mock work *upstream of its own clock*, on the
  same ten cores. So the figure is an upper bound on client cost — and at the same time the
  connection churn is a real Karate-arm cost that a remote, TLS-terminating API would charge far
  more for. See the two bullets on that in "what the result does not license" below.
- **The percentile tails do not have a stable direction, and the first matrix's reading of them
  does not replicate.** At 10 ms the karate arm is tighter in all three pairs (p99 17/17/16 ms vs
  18/18/33 ms), which matched the original finding; at 50 ms pairs 1–3 it reverses in two of three
  (p99 71/98/88 ms vs 83/62/63 ms). Across those first six pairs p50 matches in five and differs by
  2 ms in the sixth (50 ms pair 1: plain 58, karate 56); in the contended 50 ms pairs 4–6 the plain
  arm's p50 drifts to 57–62 ms, which is the machine rather than the client. Whatever produces the
  tail difference is not a property of the client that survives a change of tier, and it should not
  be reported as one in either direction.
- **Percentiles could not have detected the overhead anyway.** The karate arm's reported response
  time is the HTTP bracket only — `PerfEvent(start = response.getStartTime(), end = start +
  responseTime)`, built per request inside the Apache client. Suite construction, config
  evaluation, feature parse, `match`, and `KarateScalaAction`'s per-execution session-map copying
  sit *between* brackets and can never reach a percentile. §10 knows this for scheduler starvation;
  it applies to its own evidence too. **Throughput is the only sensitive metric in this experiment**,
  which is why the whole-second denominator mattered so much.
- **The two arms do not check the same things**, and it loads Karate's side. The plain arm's GET
  verifies `$.name` only — every cat is named "Billie", so it would accept the wrong record; the
  karate `match` checks id, name and age. The comparison never controls for that.
- **The single-size trap, kept as a worked example.** A 10 ms / 500-iteration karate cell reports
  471.8 req/s against the tier's ~530 — the difference is ~2 core-seconds of one-time
  initialisation inside a 2-second window, not per-iteration cost. §6 warns about this for the null
  pair; it bites here identically, and it is why every row above is at 1600 iterations or more.

Every cell was checked against the mock's own report: `peakInFlight` equal to the user count in all
seventeen runs, and service p99 between 115 µs and 2.2 ms — the worst of them still 4% of the
50 ms tier's own latency, so the server was never the bottleneck at 8 users. Every run also
reconciles: Gatling's ok+ko equals the mock's `served`, in all seventeen, at two requests per
iteration — against the iteration count Gatling actually ran, which is the requested one rounded
up to a multiple of the user count (the 500-iteration cell ran 504, and its digest says so).
That check, not the calibrated knee, is what carries the headroom argument in these cells.

#### The two-host result — machine A2 (EC2 `c7g.4xlarge` x2, Graviton3, AL2023, JDK 24), 8 users

First measurement on a quiet dedicated machine, 2026-08-06, commit `10090cc`, 4000 iterations
per run, 10 pairs, arm order alternating, `--mock-url` to a `LatencyMock` on its own instance.
Reproduce with [PROFILING_EC2.md](./PROFILING_EC2.md).

| | added ms/iteration | sd | n |
|---|---:|---:|---:|
| **two hosts, warmed** | **+1.79** | **0.05** | 9 |
| two hosts, including the cold-mock pair | +1.84 | 0.17 | 10 |
| co-located control, same instance | +1.94 | 0.09 | 4 |
| machine A (laptop), co-located | +0.59 | 0.29 | 3 |

**The noise problem is solved: sd 0.05 ms against an effect of 1.79.** The laptop could not
resolve 0.5% of an iteration against a 3–6% floor; this machine resolves it with room to spare,
which is what the phase was for.

**Three findings, and one of them refutes a prediction this document made.**

- **The cost is machine-dependent and the absolute number does not travel.** 0.59 ms on Apple
  silicon, 1.79 ms on Graviton3. No ratio between them should be quoted: machine A's figure carries
  a 95% interval of roughly −0.13…+1.31 ms (n=3), and an interval that **crosses zero** puts no
  finite upper bound on the ratio at all. What survives is the direction and the instruction —
  every quotation of "Karate adds X ms" from here on must name the machine.
- **Co-location is not the confound it was billed as** — on the evidence available, which is
  thinner than one line of table suggests. §10 predicted the two-host move would expose
  per-iteration connection setup that loopback priced at zero. It did not: the same-instance
  control is *higher*, at +1.94, putting topology at ≲0.15 ms of the 1.2 ms machine gap. Treat
  that as **consistent with small, not as settled**: it rests on n=4 against n=9, where a 0.15 ms
  difference carries ~±0.1 ms of standard error, and the control ran with the cold-mock confound
  described below. The prediction is not refuted either, because a same-AZ RTT of ~100 µs leaves
  almost no round trip to pay for; it remains **untested at realistic RTT with TLS**, which is
  where it was always expected to bite.
- **Injector headroom is now evidence.** 0.9–1.0 cores (plain) and 1.6–1.7 (karate) of 16,
  in every digest. The extra CPU reconciles with the extra serial time: ~0.7 cores of difference
  at ~345 iterations/s is ~2 ms/iteration, against +1.79 ms measured — so the overhead is CPU
  work on the critical path of each iteration, not the client waiting on something.

**Two measurement traps this run walked into, both now guarded:**

- **A freshly started mock is cold and the arm that meets it pays.** The first run against a new
  `LatencyMock` had the mock at 3.86 core-s and a 231 µs service p99, against 0.70 and 10 µs once
  warm; that arm came in ~0.5 ms/iteration slower. Because pair 1 always led with karate, the
  bias was structural and always against Karate — it alone moved the mean from +1.79 to +1.84 and
  tripled the standard deviation. `matrix.sh` now discards a warmup run.
- **High mock CPU under co-location is not core contention.** The control's mock showed 4.8 core-s
  and a 303 µs p99 against the two-host mock's 0.7 and 10 — which reads as co-location damage and
  is not. `run.sh` forks a fresh mock *per run*, so every co-located run meets a cold JVM, and the
  **plain** arm — 8 connections in total — shows the identical signature. It also means the control
  differs from the two-host arm in two ways, topology and mock lifecycle, so ≲0.15 ms is an upper
  bound on the topology term.

**What this run did not do**, recorded because the phase's own order (below) says to do it first
and it was skipped: **no calibration was taken on this machine**, and **the 50 ms tier was never
run** — the tier whose unresolvability is the reason the phase exists. The 10 ms tier and the
co-location control are what these numbers cover.

### Reading a calibration, and the acceptance rules

Three checks, in order — two of them exist because skipping them produced a confident wrong answer:

1. **`ko` must be 0.** A point with failures is not a slower point, it is a point with holes:
   failed requests leave the sample *and take the slow ones with them*, so throughput collapses
   while percentiles stay clean. Rows are marked `INVALID`.
2. **The baseline repeat must match the first row.** Each arm re-runs its lowest point at the end;
   disagreement means the arm was still warming up, and the gap is the arm's noise floor.
3. **The knee is where `unowned mean` departs from baseline** — *not* where throughput stops
   rising. In a closed loop throughput is capped by users ÷ iteration time.

`unowned` is per-request: what the client waited minus what the mock says it spent on that same
request. **The pairing is what makes it meaningful** — an aggregate difference of percentiles
compares two different requests and has no per-request interpretation. Run the matrix at half the
knee: a closed loop makes any knee optimistic, because when the mock stalls these clients stop
offering load. The knee is void for an open-model cell (`constantUsersPerSec`); re-calibrate
open-loop before trusting one.

#### The calibration this instrument was run at — machine A, 10 ms injected, 100 iterations per user, 20 s settle

Kept here rather than pruned, because "run the matrix at half the knee" is unusable without it and
because a result that cannot be reproduced from what is written down is not a result. `unowned`
mean growth over the 1-user baseline, in milliseconds; zero failures in every row:

| users | keepalive growth | close growth |
|---:|---:|---:|
| 1 (baseline) | 0.000 | 0.000 |
| 4 | 0.179 | 0.070 |
| 16 | 0.579 | 0.783 |
| 64 | 1.315 | 0.374 |
| 1 (repeat) | **0.094** | **0.842** |

Two things it establishes, and no more. Both are easy to lose and both bound what the matrix above
can claim:

- **Keep-alive is trustworthy and shows no knee through 64 users.** Its baseline repeat lands at
  0.094 ms, so its noise floor is under 0.1 ms and the 1.3 ms of growth at 64 users is real.
- **The churn arm resolves nothing here — and churn is Karate's connection shape.** Its baseline
  repeat is 0.842 ms, as large as its own growth values, so the honest statement is "no detectable
  growth through 64 users, at a resolution of about 0.8 ms". That floor is the same size as the
  per-request signal the matrix reads, which is why the 8-user cells lean on the mock's own
  per-window report (`peakInFlight`, service p99) rather than on this table.

And `unowned` is an **upper bound** on server queueing, not a measure of it: it is client elapsed
minus server elapsed, so it also contains the client's own scheduling on a machine the mock is
sharing. Growth that rises with concurrency is partly the injector — the co-location problem
below, showing up inside the number.

Acceptance for a parity cell is three things together, never TPS alone: **parity** (throughput and
the percentile distribution), **headroom** (mock in-flight below its calibrated knee, injector CPU
with slack), and **a flat heap-after-GC floor** per §4's standing constraint. Do not pick a
percentage tolerance — §2 sets out why. Report where the two diverge and how the gap scales with
latency.

**Be honest about which legs the published cells actually cleared**, because two of them did not,
and the wording above reads as though all three did:

- **Headroom, mock side: cleared** — every cell's digest carries `peakInFlight` 8 and a service p99
  between 115 µs and 2.2 ms, the worst of which is 4% of its own tier's injected latency. Each run
  also reconciles: iterations × 2 = Gatling's ok+ko = the mock's `served`.
- **Headroom, injector side: ~~not recorded anywhere~~ — now in every digest.** It was a safe
  inference at the 10 ms tier's 526–591 req/s and genuinely in doubt at the 0 ms tier. The CPU
  headroom panel puts an 800-iteration 10 ms injector at **1.4 of 10 cores** — a floor, since a
  self-driving workload's window includes Gatling's engine boot. The cells in
  the table above predate the panel and still carry no figure, so the *published* rows remain
  inferred — re-running them on the two-host phase is what puts a number in this column.
- **Flat floor: not readable at these window lengths, so not met — unevaluated.** Every digest in
  the matrix prints its own drift, and none of them is flat: the karate arms land at +11–14 MB
  (117–143%) and the plain arms at +13–19 MB (135–194%), reproducibly, in all seventeen runs. At 14 seconds that is warmup — classes, JIT,
  Gatling's own accumulation — climbing then plateauing, not retention. But that is precisely §2's
  point about short windows: they cannot tell warmup from a leak. **A 14-second cell cannot
  evaluate this leg at all**, so either lengthen one confirmation run until the floor has a
  readable slope, or state that the leg is out of scope for short cells rather than implying it
  passed.

### The two-host phase: what is built, and what it is for

The 50 ms attempt above established the constraint: **this laptop's run-to-run floor is 3–6%, the
effect is 0.5%, and no pair count closes that gap.** Everything here exists so the same runs can be
taken somewhere they resolve — and so that when they are, the digest can prove its own conditions
instead of asking to be trusted.

#### The four things the harness was missing

| | What it closes |
|---|---|
| **CPU headroom panel** in every digest | §10 carried "headroom, injector side: **not recorded anywhere**" as an admitted hole in one of its three acceptance legs. Both the injector and the mock now self-report CPU over their own measured window — not process lifetime, so no JVM startup and no idle tail. First reading: an 800-iteration 10 ms cell puts the injector at **1.4 of 10 cores**, so the inference §10 made looks right. Read it as a **floor**: a `gatling-*` workload is self-driving, so its window wraps the whole simulation including engine boot (wall 4.8s against a 2.9s load window), which dilutes the ratio. The published 4000-iteration cells predate the panel and carry no figure. |
| **`profiler compare <run-dir>…`** | The published tables were scraped by hand, and `6ae522b39` ("skipped a decimal cell and shifted every column") is what that cost on eight runs. It derives the tier tables, the sleep correction and the spread, and says when a tier is unresolved. It reproduces both published tiers to within 0.01 ms — not "to the digit": the published cells were hand-computed from 1-decimal rates, the tool uses the digests' 3-decimal ones, so 10 ms pair 2 reads +0.92/+1.14 where the table says +0.93/1.15. The tool is the more accurate of the two; the tables below are left as published. |
| **`--mock-url`**, plus `LatencyMock --bind` / `--standalone` / `/config` | Nothing could point a run at a mock it had not forked. The parent now resets the remote mock's counters before the load and scrapes them after, writing the same `PROFILING-MOCK-CONFIG` / `PROFILING-MOCK-STATS` prefixed lines a forked mock prints (not byte-for-byte — a forked log also carries `PROFILING-MOCK-URL`, and this one an `[external] attached…` line), so the digest, the reconciliation and `compare` cannot tell the two apart. Verified: two runs against one mock alive for 67s each report their own 1600 over their own 2.8s window. **Verified against `127.0.0.1` only — no cross-host path has ever been exercised.** |
| **Host network limits** in `run-meta.txt`, and a per-run connection-rate check | Every published figure was macOS. The ceiling is now read from the kernel (this laptop: 16384 ports / 30s TIME_WAIT → **~546 conn/s**, which is where the remembered "~550" came from) and each run reports its own rate against it. A 0 ms cell now states its own hazard: *"1849 connections/s … 3.4x the sustainable rate … survived on brevity rather than margin."* |

Two provenance gaps closed alongside: `run-meta.txt` now records **which mock tier** served the run
(§7's "the tell is `mock.log`") and **the gap since the previous run**, so the 35-second TIME_WAIT
convention — a correctness condition that lived only in an operator's shell script — is auditable
after the fact rather than assumed.

#### The machine

Built and automated — see **[PROFILING_EC2.md](./PROFILING_EC2.md)** for the runbook, the env-file
contract and the teardown. What follows is why it is shaped the way it is.

**Two instances, not one**, and the second one is the whole point — a co-located mock is the
confound being removed.

- **`c7g.4xlarge` ×2** (Graviton3, 16 vCPU, 32 GB), same AZ, **cluster placement group** so
  inter-host RTT is ~50–100 µs and stable: the injected latency stays the dominant term rather than
  the network becoming the variable.
- **Graviton on purpose.** 1 vCPU = 1 physical core with no SMT, and no turbo-bin jitter — two
  sources of exactly the variance that made the 50 ms tier unreadable. aarch64 also keeps machine A's
  baselines shape-comparable.
- **Never a T-series.** Burst credits throttle silently, which is the same failure this phase exists
  to escape.
- **sysctls on both**: `net.ipv4.ip_local_port_range="1024 65535"`, `net.ipv4.tcp_tw_reuse=1`,
  `net.core.somaxconn=8192`. The digest reports what it actually got — check it rather than assume
  the sysctl took. **`tcp_tw_reuse=1`, not the default 2**: 2 is loopback-only, which is no relief
  at all when the mock is on the other host, and that is exactly the configuration this phase runs.
- **Raise `ulimit -n` on the injector, and watch it.** This is the one client-side ceiling nothing
  records: §2 notes the per-execution HTTP client is never closed, so its sockets sit ESTABLISHED
  until a cleaner runs rather than moving promptly to TIME_WAIT. A 10-pair sweep churns tens of
  thousands of them against a default soft limit that can be 1024. Set it to 65536 and check
  `ls /proc/<pid>/fd | wc -l` on a long cell — file descriptors are not heap, and the heap-after-GC
  floor cannot see this.
- Cost is not a factor: roughly $0.58/hr each, so a full day of runs is under $15.

#### The order, on the box

1. **Re-calibrate.** `MockCalibrator` ramp 1/4/16/64, both connection modes. Non-negotiable: the
   published calibration is machine A's, and the acceptance rules below are written against a local
   one. It also re-establishes the churn arm's noise floor, which on machine A was 0.842 ms — as
   large as the signal — and is the number the whole matrix's resolution depends on.
2. **10 pairs at 10 ms.** The control — but the gate is **internal consistency across its own ten
   pairs** (a spread comfortably under the effect), *not* agreement with machine A's 0.6–0.8 ms.
   Graviton3 is a different microarchitecture, so a genuinely different fixed cost is an expected
   outcome and must not be read as a broken rig. Only a spread that stays larger than the effect
   says the machine is not quiet, and that is the finding that would stop the day.
3. **10 pairs at 50 ms.** The open item, on a machine where 0.5% is above the floor.
4. **The user ramp**, 1→64 users, plus one open-arrival (`constantUsersPerSec`) lane. §10 calls this
   the cheapest remaining run that would change a conclusion, and it answers the concurrency-density
   question §9 has carried unmeasured. Expect a TPS shortfall with clean-looking latencies.
5. **The AST prototype A/B** ([Parsed-JS reuse](#parsed-js-reuse--measured-half-gated-not-built)).
   Needs one quiet machine rather than two, and a quiet machine is exactly what it has never had.

**Run every cell with `--mock-url` against the second host**, and read the CPU panel on both sides:
the mock host's row should be near-idle, which is what finally licenses the phrase "not co-located"
rather than merely asserting it.

### Environment settings that fake a knee, and one hard ceiling

Neither of the first two is a capacity limit, and both were mistaken for one:

| | Default | Why it matters |
|---|---|---|
| `sun.net.httpserver.maxIdleConnections` | **200** | Above that many parked keep-alive connections the JDK server *closes* them, so a high-user run churns connections and degrades at a tunable default. `LatencyMock` sets it to 8192. |
| `kern.ipc.somaxconn` (macOS) | **128** | `listen()` silently clamps the requested backlog to it — a "generously sized" 1024 in Java is 128 in the kernel. The mock prints what it asked for; check what it got. |

The hard one is **ephemeral ports**: 16,384 (49152–65535) with MSL 15 s, so TIME_WAIT is 30 s and
sustained connection-per-execution load tops out near **550 connections/second**. No tuning of the
mock touches it, and the *Karate* arm builds an HTTP client per execution, so its connections churn
where plain Gatling's are held open per virtual user.

**Three rates, three different numbers — do not quote one for another, and this is now measured.**
At the 10 ms tier the karate arm's window carries 4000 iterations and 8000 requests, and the mock
counted **4000 distinct client ports**: one connection per *iteration*, because both requests go
through that iteration's client and its pooled connection. Over a 15.2 s window that is **263
executions/s, 526 requests/s, 263 connections/s** — comfortably under the ceiling. Quote the wrong
one of those three and the arm looks like it is over it; `distinctPeerPorts` is in every digest so
the connection rate never has to be derived again.

**None of those three numbers has to be remembered any more.** `run-meta.txt` reads the port range,
TIME_WAIT and `somaxconn` off the kernel and derives the sustainable rate — on machine A that comes
out at **~546 conn/s**, which is where the figure above came from — and every digest reports the
run's own connection rate against it. That matters most on Linux, where the range differs and
TIME_WAIT is compiled in at 60 s rather than being twice a tunable MSL, so the arithmetic above
gives a different answer and `tcp_tw_reuse` changes it again.

**The 0 ms tier is the one that survives on brevity, not on margin.** The karate 0 ms / 8000 run
opened 8000 connections in 1.9 s — about **4,200/s, roughly eight times the sustainable rate** —
and passed only because the total stayed under 16,384 and the next churn-heavy run was a minute
away. A 0 ms cell now says this about itself, in the digest: *"1849 connections/s … 3.4x the
sustainable rate … survived on brevity rather than margin"*. It is still **reported, not
enforced** — a short run over the ceiling is fine and a long one is not, and that distinction is
the finding. The inter-run gap is likewise recorded (`since prev:` in `run-meta.txt`, flagged when
under 35 s) rather than imposed: in the first matrix it was an accident of Maven startup time, and
in the rerun a `sleep 35` in the operator's script. A short burst can beat a sustained ceiling until the port range
fills, which also means **the matrix has never actually tested the failure mode this section
describes**. When it does bite it will read as
"Karate is slower". Watch `netstat -an | grep -c TIME_WAIT` — and note it undercounts, because the
per-execution client is never closed (§2), so its sockets sit ESTABLISHED until a cleaner runs
rather than moving promptly to TIME_WAIT. `sudo sysctl -w net.inet.ip.portrange.first=32768
net.inet.tcp.msl=5000` raises the ceiling to ~3,200 conn/s and reverts on reboot.

### What the result does not license, and what is next

- **One cell shape.** 8 users, one machine, one feature — three pairs at 10 ms and six at 50 ms
  now, but all at the same point. **The user ramp is what answers §9's concurrency-density question**, and it is the
  cheapest remaining run of the ones that would change a conclusion. Expect the signature to be a **TPS shortfall
  with clean-looking latencies** — queue-for-a-thread time sits between actions, outside every
  `PerfEvent` bracket, so it never reaches a percentile. §9's prediction that starvation would
  inflate reported response times is wrong for that reason.
- **~~Co-location is unresolved~~ — measured, and smaller than expected.** A same-instance control
  against a two-host matrix puts topology at ≲0.15 ms of a 1.79 ms per-iteration cost (above). The
  reasoning that co-location biases *against* Karate still holds in direction; it was simply not
  large enough to be the thing worth worrying about. Separate hosts did remove the port ceiling and
  did settle the question — the answer was just "not much".
- **On connections the bias runs the other way, and it is the larger effect at realistic latency.**
  The injected latency sits *inside* the handler, after accept, so connection establishment is
  served at loopback speed at every tier — a 50 ms cell delays responses by 50 ms and handshakes by
  microseconds. The arms differ on precisely that axis: plain Gatling holds 8 keep-alive
  connections for the whole run, while Karate builds a client per execution
  (`Runner.runFeature` → fresh `Suite` and `FeatureRuntime`; `ScenarioRuntime` → fresh `KarateJs` →
  fresh `ApacheHttpClient`), so no pooling survives an iteration — now counted rather than
  inferred: 4000 distinct client ports against the plain arm's 8, in the 10 ms cells.
  **This harness prices those 4000 handshakes at approximately zero, in every cell.** Against a real API it is +1 RTT per iteration for the TCP
  handshake and 1–2 more for TLS — at a 50 ms RTT, on the order of +100 ms on a ~110 ms iteration,
  which is roughly half the throughput, from the arm the matrix scores as equal. It is also what
  connection-rate limits, load balancers and accept queues punish, and none of those exist here.
  This is overhead that **scales with** the network rather than disappearing into it, and it is the
  single strongest reason the result must not be quoted as "at any realistic API latency".
- **The workload is deliberately small, and the headline does not inherit that qualifier.** The
  ~100-byte body is chosen so neither arm becomes a measurement of the JSON parser — but building
  Karate's variable graph and running a structural `match` scale with payload size and match
  complexity on the karate side only, and that scaling has never been measured. A realistic
  10–50 KB response could move the divergence point well above 10 ms. Plaintext HTTP, ~100-byte
  bodies and loopback are three unstated conditions on every cell above.
- **Log capture is off, which is the shipped default for this lane and therefore the right thing to
  measure — but it is not every user's configuration.** Anyone enabling `logReplay` turns the
  per-step capture back on (§9), and the matrix says nothing about that configuration.
- **`jdk.ExecutionSample` works in this lane**, unlike a parallel Runner suite: `Runner.runFeature`
  builds a non-parallel Suite, so `FeatureRuntime` runs the scenario inline on the Gatling thread
  that called it. *Hot methods* is trustworthy here, which is why the custom JFR events in §9 were
  not needed. See §7.
- **Not built:** the per-iteration residue (`action elapsed − Σ PerfEvent`), which would attribute
  Karate's own overhead exactly rather than by subtraction of throughputs. It needs a timing point
  around the whole body of the per-iteration execute path. Worth building only if the ramp says the
  overhead matters — and note it is a *reporting* number, not a gate: Karate legitimately spends a
  millisecond or two per iteration, so a residue threshold would fire on healthy runs. The signals
  that can gate, and the detector this all argues for, are designed in
  **[GATLING.md §14.12](./GATLING.md)** (injector health — designed, not built).

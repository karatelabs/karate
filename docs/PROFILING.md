# Profiling Karate

> Runbook for JFR-based profiling of karate-core, the mock server and the JS engine.
> Written for an LLM operator: every step is a command you run and an artifact you read.
>
> **Everything here reports. Nothing asserts.** There is no CI job, no committed baseline
> file and no pass/fail gate. An OOM is a legitimate outcome — for some workloads it is
> the *expected* one. Judgement lives with the reader, and last-known numbers live in
> [Current baseline](#6-current-baseline) below.
>
> §8 records what the parallel-execution memory investigation settled, and §9 records what
> was deliberately **not** built and why — read those before re-opening either.

---

## 1. Quick start

```bash
cd karate-profiling

etc/run.sh --list                          # what can I run?
etc/run.sh harness-smoke --iterations 50000 # smoke: is the harness alive?
etc/run.sh call-accumulation                # the real thing
```

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
| `--threads N` | per workload | Concurrency (virtual threads). |
| `--iterations N` | per workload | Fixed iteration count. Mutually exclusive with `--duration`. |
| `--duration 10m` | per workload | Run for a wall-clock window instead. Use for soaks. |
| `--xmx 768m` | per workload | Child heap. **The single most important knob** — a leak that OOMs at 768m may never surface at 4g. |
| `--gc g1\|zgc` | `g1` | See [Reproducing a specific collector](#reproducing-a-specific-collector). |
| `--warmup Ns` | per workload | Excluded from the measured window — the recording is delayed past it. A workload that drives its own concurrency runs no warmup, so nothing is delayed for it. |
| `--timeout` | duration + slack | Wall-clock cap. On expiry the parent dumps the recording and thread state, then kills the child. See §4. |
| `--record workload\|mock` | `workload` | Flips which JVM gets the recording. `mock` profiles the Karate mock server itself, driven by a deliberately cheap raw-`java.net.http` client. |

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

### Leak-watch family — NOT BUILT

`leak-watch` (a realistic soak: `karate-config.js` doing a call, `callSingle`, `callonce` in
`Background`, `call read()` with an args object, JS function defs, HTTP against the forked
mock, `match` assertions) plus thin single-mechanism variants `leak-callonce`,
`leak-callsingle`, `leak-shared-scope`, `leak-isolated-scope`.

**None of these exist yet.** They are the right shape for the question "is Karate leaking
over a long run" — which the current workloads, all iteration-bounded reproductions, do not
answer. Build them when that question comes up; the shape to copy is v1's
`examples/profiling-test/src/test/java/perf/{main,called,mock}.feature`, driven with
`--duration` rather than `--iterations`, with the mock in the sibling JVM.

*Healthy result, when they exist:* a **flat** heap-after-GC floor across the whole run, at
any sawtooth amplitude. See §4.

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

These live behind `-Pgatling` — Gatling and its Scala runtime are ~40 MB of classpath the
other workloads have no use for. `etc/run.sh` turns the profile on automatically for any
`gatling-*` workload and for `--list`, so there is nothing extra to remember; it also means
those invocations build `karate-gatling` first and take longer.

Gatling owns the users and the pacing, so these are self-driving: `--threads` becomes virtual
users injected at once and `--iterations` becomes repetitions per user. `--duration` is not
supported (see §9). Gatling's own chart generation is off — it is a second pass over the
simulation log that would land in the digest as if it were load-driving cost.

**Wall-clock on the `http` pair measures the mock, not the clients.** At 16 users both
variants saturate the sibling Karate mock and finish within noise of each other, in either
order; at 2 users the per-request latency dominates instead and the difference is still inside
run-to-run spread. What *is* usable from that pair is the digest: allocation and peak heap
separate the two cleanly, because those are client-side facts that a saturated server does not
hide. The `null` pair has no HTTP at all and so is the one whose wall-clock number is worth
quoting. A throughput comparison needs a mock tier cheap enough to stay out of the way — see
§9.

#### What "acceptable overhead" should mean here

Not a ratio against plain Gatling. That comparison is unavoidably apples-to-oranges — Karate
parses each response into a document and runs a structural `match` where the Gatling variant
extracts one JSONPath — so a ratio measures *different work*, and picking a threshold for it
(GATLING.md's "< 5% vs plain Gatling") invites tuning towards a number that was never the
question.

**The question a load tester actually has is whether the client's overhead disappears into the
network time of the system under test.** A localhost mock answers 1000 µs away; a real API is
one to two orders of magnitude further. Against a service with a 50 ms median response, the
~0.7 ms of CPU a `karateFeature()` exec costs is ~1.4% of one user's iteration — and the
faster the target, the more it matters, which is why the number to quote is the fixed cost in
milliseconds, not a percentage against another tool.

What would settle it, and is not built: give the mock a **configurable injected latency**, then
run the `http` pair at a fixed concurrency against, say, 0 / 10 / 50 ms. The gap between the
two variants should stay roughly constant in absolute terms while shrinking as a share of the
iteration — and if it does not, the overhead is proportional to something and is worth
chasing. That is a small change to the mock feature and a knob, and it is the honest version
of the "< 5%" claim.

### `Probe` — what does a call actually return?

Not a workload; a one-shot diagnostic that prints the *shape* of the variables a feature
leaves in scope, counting distinct container nodes by identity so shared substructure is
counted once.

```bash
mvn -q compile
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

**Allocation by site.** From `jdk.ObjectAllocationSample`, weighted bytes, stacks
collapsed to `io.karatelabs.*` frames so the top entries are Karate methods rather than
`java.util.HashMap.newNode`. This answers *what is churning*, not *what is retained* —
those are different questions and conflating them is the trap described in §4. This panel
does attribute correctly across virtual threads.

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

### "A long scenario OOMs"

1. `etc/run.sh scope-capture-bound` — does the known shape reproduce on this machine?
2. Read *Heap-after-GC* and classify with the chart above.
3. If it is live-mid-copy: in the heap dump, find what holds the largest retained size and
   check whether the root is a thread stack. If it is, read the recursion depth and the
   size decay down the stack — a geometric decay (each level roughly half the one above)
   means level N *contains* level N+1, i.e. nesting, not repetition.

   Karate runs scenarios on virtual threads (`Suite.java:810`), so "rooted at a thread
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

1. `etc/run.sh leak-watch --duration 5m` on the suspect commit and on its parent.
2. Diff the two `digest.md` files — *Allocation by site* first, *Hot methods* second.
   That order is deliberate: allocation sampling is trustworthy under virtual threads and
   CPU sampling largely is not (§7).
3. Ignore wall-clock differences under ~10% on a laptop; see §7.

### "Is the mock server fast enough?"

`etc/run.sh <mock workload> --record mock` puts the recording on the mock JVM and drives
it with a raw HTTP client, so the profile attributes cost to gherkin matching, JS
evaluation and response building without any Karate-client noise. This is also the one
configuration where *Hot methods* is fully trustworthy — the mock serves on platform
threads.

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

Two recording options worth knowing about, both off by default and both costly enough that
they are opt-in per run rather than always on:

- `path-to-gc-roots=true` — makes `jdk.OldObjectSample` report reference chains (the
  *holder*), not just the allocation stack.
- `-XX:FlightRecorderOptions:stackdepth=128` — the default 64 truncates Karate-through-JS
  stacks, which collapses distinct allocation sites into one.

---

## 6. Current baseline

Hand-maintained. Update it when you take a run you trust, and always record the machine
**and the child JDK** — absolute numbers are meaningless without them, only *shapes* and
*ratios* travel.

**Machine A** — Apple Silicon (aarch64), macOS 25.5, 10 cores, JDK 24.0.2, G1.

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

**`null` pair — 20 000 execs, no HTTP.** Two runs each, back to back:

| | run 1 | run 2 |
|---|---:|---:|
| `gatling-null-plain` | 1157 ms | 1150 ms |
| `gatling-null-karate` | 2029 ms | 2015 ms |

Reproducible to ~1%, and most of the plain number is Gatling's fixed startup (it is the same
at 2000 iterations as at 20 000 — the no-op exec itself is free). The difference is what
matters: **~865 ms per 20 000 execs**, i.e. ~43 µs of wall-clock per `karateFeature()` exec at
16-way concurrency, or roughly **0.7 ms of CPU per exec**. That is the floor a Karate-driven
virtual user pays before doing anything: build a `Suite`, parse the feature, evaluate
`karate-config.js`, run one scenario, hand the session maps back.

Where it goes, from the digest of the Karate run — an *empty* feature, so every entry here is
fixed cost:

| site | share | reading |
|---|---:|---|
| `JavaUtils.findMethodDirect` | 11.0% | reflective method lookup, per execution |
| `BaseParser.<init>` | 9.1% | the feature and its JS re-parsed every time |
| `PathResource.computeRelativePath` | 7.6% | path resolution, per execution |
| `LogContext.setLevelOn` + `captureRuntimeLevels` | 6.1% | the per-scenario Logback level snapshot/restore, reflectively, across every category |
| `JsErrorException.<init>` + `ResourceNotFoundException.<init>` | 5.9% | **exceptions constructed on a happy path** — nothing in this feature fails |

The last two rows are the interesting ones and neither has been chased down. Building
exceptions in a feature that does nothing but `* def x = 1` is a lead, not a finding.

**`http` pair — 2000 iterations = 4000 requests against the sibling mock.** Wall-clock is
mock-bound and says nothing (see §2); allocation and heap do:

| | sampled allocation | peak heap |
|---|---:|---:|
| `gatling-http-plain` | 189 MB | 43 MB |
| `gatling-http-karate` | 540 MB | 70 MB |

**~2.9x the allocation for the same 4000 requests**, of which the Karate HTTP client is the
bulk: `ApacheHttpClient.invoke` 14.6%, `buildResponse` 8.6%, and `initHttpClient` **6.1%** —
the last being the notable one, because it means a client is being constructed per execution
rather than reused. The port plan's `PooledHttpClientFactory` (GATLING.md §2.1) was never
built, and this is what that costs. Not investigated further here.

*Baselines are shapes, not thresholds. Absolute numbers move with hardware and JDK; the
linear trend and the ratios are what travel.*

---

## 7. Caveats

- **CPU sampling barely sees virtual threads, and Karate runs every scenario on one.**
  `jdk.ExecutionSample` samples platform threads; a Runner-driven workload can produce
  single-digit sample counts over several seconds of saturated CPU. The *Hot methods*
  panel is therefore near-useless for scenario code, and the absence of a method from it
  proves nothing. Prefer *Allocation by site*, which does attribute across virtual
  threads. *Hot methods* is trustworthy for the mock JVM (`--record mock`) and for
  platform-thread paths.
- **Throughput numbers off a laptop are shape-only.** Thermal throttling, other processes
  and the mock sharing the same cores make absolute req/s unusable. Ratios between two
  runs taken back-to-back on the same machine are fine; anything else is not.
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

Recorded so none of it is re-derived from scratch. Nothing here is scheduled.

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
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table. `--record mock` already exists to support this. |
| Gatling parity — **partly built** | The null-overhead probe, the parity sim and the allocation comparison exist (§2, §6). What is left: a **throughput ceiling**, which needs a mock tier cheap enough not to be the bottleneck — today both variants saturate the feature mock, so req/s measures the mock. Also unbuilt: `--duration` support, which needs `during()` in the injection profile rather than a repetition count, and is the prerequisite for a Gatling soak. Note the entry point is `Gatling.fromArgs`, not the `fromMap` this row used to name — `fromMap` was removed in Gatling 3.15. |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest`, so a recording carries Karate semantics and allocation attributes to a *feature line*. **A CPU-tuning need, not a memory one.** The virtual-thread gap is specific to `jdk.ExecutionSample`; everything the memory work relied on attributes correctly regardless of thread model. Build these when the question becomes "where is the CPU going during a parallel run" — exactly where `ExecutionSample` goes blind. |
| `profiler compare A B` | Side-by-side delta table from two run directories. |
| Machine-readable baselines + CI | Committed `baselines/*.json`, a scheduled job, regression thresholds. Out of scope until the manual playbook has proven itself. |
| `jcmd GC.class_histogram` checkpoints | Per-class growth over time. Forces a GC, so it perturbs the measurement. |
| Heap-dump class histogram in `JfrDigest` | Deliberately not implemented: no JDK API or CLI reads an `.hprof` (`jhat` removed in Java 9, `jmap -histo` is live-process only). The digest points at Eclipse MAT instead. Revisit only by hand-rolling a histogram-only reader or taking a dependency. |

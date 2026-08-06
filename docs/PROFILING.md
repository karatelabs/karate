# Profiling Karate

> Runbook for JFR-based profiling of karate-core, the mock server and the JS engine.
> Written for an LLM operator: every step is a command you run and an artifact you read.
>
> **Everything here reports. Nothing asserts.** There is no CI job, no committed baseline
> file and no pass/fail gate. An OOM is a legitimate outcome — for some workloads it is
> the *expected* one. Judgement lives with the reader, and last-known numbers live in
> [Current baseline](#6-current-baseline) below.
>
> §8 records what the parallel-execution memory investigation settled. §9 is the mixed bag:
> what was deliberately **not** built and why, *and* the finished pieces whose measurements
> belong next to the parked ones — each of those is headed "— built". §10 is the one piece of
> work that is *scheduled* rather than parked, and several §9 items are gated on it. Read all
> three before re-opening anything.

---

## 1. Quick start

```bash
cd karate-profiling

etc/run.sh --list                          # what can I run?
etc/run.sh harness-smoke --iterations 50000 # smoke: is the harness alive?
etc/run.sh call-accumulation                # the real thing
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
| `--timeout` | duration + slack | Wall-clock cap. On expiry the parent dumps the recording and thread state, then kills the child. See §4. |
| `--record workload\|mock` | `workload` | Flips which JVM gets the recording, so the profile is of the mock server rather than the load driver. Only meaningful for a workload that uses a mock — today that is the `gatling-http-*` pair, and `gatling-http-plain --record mock` is the cheaper driver of the two. The deliberately-cheap raw-`java.net.http` driver this was designed for is the unbuilt "Mock throughput tiers" item in §9. |
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

**Wall-clock on the `http` pair measures the mock, not the clients.** At 16 users both
variants saturate the sibling Karate mock and finish within noise of each other, in either
order; at 2 users the per-request latency dominates instead and the difference is still inside
run-to-run spread. What *is* usable from that pair is **allocation**, which separates the two
cleanly because it is a client-side fact that a saturated server cannot hide. Peak heap points
the same way but is not clean — the Karate side is unstable run to run, so read it as a
direction, not a measurement. The `null` pair has no HTTP at all and so is the one whose
per-exec numbers are worth quoting. A throughput comparison needs a mock tier cheap enough to
stay out of the way — see §9.

#### What "acceptable overhead" should mean here

Not a ratio against plain Gatling. That comparison is unavoidably apples-to-oranges — Karate
parses each response into a document and runs a structural `match` where the Gatling variant
extracts one JSONPath — so a ratio measures *different work*, and picking a threshold for it
(GATLING.md's "< 5% vs plain Gatling") invites tuning towards a number that was never the
question.

**The question a load tester actually has is whether the client's overhead disappears into the
network time of the system under test.** A localhost mock answers 1000 µs away; a real API is
one to two orders of magnitude further. Against a service with a 50 ms median response, the
~0.45 ms of CPU a `karateFeature()` exec costs is ~1% of one user's iteration — and the
faster the target, the more it matters, which is why the number to quote is the fixed cost in
milliseconds, not a percentage against another tool.

**The practical form of that test: run both against the same target and compare the reports.**
If karate-gatling and plain Gatling show approximately the same TPS and the same response-time
distribution, the client is not distorting the measurement, which is the job — and that is a
far better acceptance test than any micro-ratio, because it exercises the thing a user actually
relies on.

It carries one condition, and it is the condition this harness keeps running into: **equal TPS
only means anything when the client has headroom.** Two saturated clients queued behind the
same overloaded server also report identical numbers — that is exactly what the `http` pair
does today against the local mock, and it is why those runs prove nothing about the client. So
the test is "same TPS *and* the injector is demonstrably not the bottleneck": check CPU
headroom on the injector, or ramp until the two diverge and report where. Where they diverge is
the number worth having, because past that point the response times a load test reports stop
being the server's.

What would settle both questions: give the mock a **configurable injected latency**, then run the
`http` pair at a fixed concurrency against, say, 0 / 10 / 50 ms. The gap between the two variants
should stay roughly constant in absolute terms while shrinking as a share of the iteration — and
if it does not, the overhead is proportional to something and is worth chasing. The same setup
answers the concurrency question in §9, and it is the honest version of the "< 5%" claim.

**This is now scoped in [§10](#10-the-latency-mock-experiment--scoped-being-built), and it is
not the "small change to the mock feature and a knob" this paragraph used to promise.** A knob on
the feature mock cannot do it: that mock saturates before either client does, so it would answer
the overhead-share question with two clients queued behind it. §10 builds a separate server whose
own cost is instrumented, and leaves the feature mock alone as a *subject* of profiling rather
than an instrument.

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
| **Never measured** | Soaks. Every workload is an iteration-bounded reproduction, so "no slow leak over hours" is unverified rather than verified — see the leak-watch family in §2. |
| **Never measured** | CPU inside scenario code. All of §8 is allocation and retention; `jdk.ExecutionSample` is blind on virtual threads (§7) and the custom JFR events that would fix it are unbuilt (§9). |

The Gatling baseline below started as a fifth category: **leads, not findings** — real numbers
from real runs, none chased to a cause. Three have since been chased (the per-scenario Logback
snapshot, happy-path exception construction with the uncached reflection and map copies
underneath it, and re-parsing — §9 has all three, with before-and-after). Chasing the last of
those moved it out of the Gatling column entirely: **the re-parsing that costs is JS step
expressions, re-parsed on every step execution, and it is an ordinary-suite cost that the
Gatling lane merely made visible** — measured, and deliberately not acted on until a mock with
injected latency says it survives real network time; see
[Parsed-JS reuse](#parsed-js-reuse--measured-not-built).
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

**Ignore the `all` column moving the wrong way** in three of six cells (210 → 372, 297 → 242,
2995 → 3017). Those before-numbers were artificially low for the reason §8 sets out — JSONL was
accidentally throttling the producer enough for the writer thread to keep up — so they were
never a budget the fix could regress against. The `html` column is the one that carries meaning,
because `html` is what users actually ship.

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
  [§10](#why-no-custom-jfr-events-on-the-karate-gatling-side).
- **Throughput numbers off a laptop are shape-only.** Thermal throttling, other processes
  and the mock sharing the same cores make absolute req/s unusable. Ratios between two
  runs taken back-to-back on the same machine are fine; anything else is not — **and only
  when the thing being ratioed is the bottleneck.** Two clients saturating the same mock
  report identical numbers that ratio to exactly nothing, which is what the `http` pair does
  today (§2).
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
Everything else is a lead or a design, not code — including
[Parsed-JS reuse](#parsed-js-reuse--measured-not-built), which is the largest measured win
recorded in this document that has not been taken, and is deliberately gated on the
latency-injected mock rather than on anyone's judgement about whether it matters.

### If you are picking up the Gatling thread — the order

The Gatling items below and in the table depend on each other, and building them out of order
wastes the work. There are **two separate pieces of mock work**, which is the thing easiest to
get wrong:

1. **Mine [#845](#prior-art--the-09x-era-overhead-thread) first.** It is reading, not building,
   and it may change what is worth measuring at all.
2. **A latency knob on the existing feature mock** — a *change* to `profiling-mock.feature`, not
   a replacement. This alone unblocks two experiments: whether the overhead disappears into
   network time (§2) and whether Karate blocking Gatling's threads caps concurrency (below).
   Cheapest thing here with the highest information return.
3. **A cheap mock tier** — the raw-Java-handler item in the table below, a *different artifact*
   from (2). This is what the throughput ceiling waits on, because the feature mock saturates
   before either client does. Give it the same latency knob as (2) or it answers half the
   question.
4. **`--duration` support** for the Gatling workloads is independent of all of the above and
   gates only the Gatling soak. Do it when the soak is the question.

Steps 2 and 3 both say "mock work" and are not the same task. Doing (2) does not unblock the
throughput ceiling, and doing (3) does not by itself make the overhead-share experiment
runnable.

**(2) and (3) are now scoped as one artefact in [§10](#10-the-latency-mock-experiment--scoped-being-built),
and they gate more than the Gatling questions.** They also decide whether the per-execution
parsing work in [Parsed-JS reuse](#parsed-js-reuse--measured-not-built) is worth doing at all —
a third of allocation against a localhost mock may be nothing at all against an API that answers
in 50 ms. Do not start on caches or on making the parsed model immutable until a latency-injected
run says the cost survives realistic network time.

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

### Parsed-JS reuse — measured, not built

The finding the two fixes above turned up, and the largest single cost left in an ordinary
suite. **Every step expression is re-lexed and re-parsed on every step execution.** `* def x =
1` in a scenario that runs 2000 times is parsed 2000 times; `karate.call('x.feature')` re-reads
and re-parses the callee, so 60 calls in each of 2000 scenarios is 120 000 parses of one small
file.

A throwaway process-wide cache in `Engine.evalInternal`, keyed on (relative path, line offset,
source text) — about five lines, unbounded, correctness unaddressed — measures the ceiling:

| `call-accumulation --iterations 2000` | total sampled allocation | `BaseParser.<init>` |
|---|---:|---:|
| main | 8.04 GB | 1.09 GB (13.5%) |
| prototype AST cache | **5.33 GB** | 184 MB (3.4%) |

**A third of all allocation, on the workload shape closest to a real suite.** The same prototype
on `gatling-null-karate` gives 1.22 → 1.05 GB, near the noise band — that feature has one step,
which is exactly why the null pair could not have found this.

What is left on top after the prototype is the **Gherkin** side: `GherkinLexer` ~14% and
`GherkinParser.extractDocString` 5.7%, i.e. the callee feature re-parsed per `karate.call()`,
plus `PathResource.computeRelativePath` 7.9% for resolving its path again each time.

#### Deliberately not scheduled, and the order that decides it

**This is not the next thing to build, and the reason is a question the harness cannot answer
yet.** A third of allocation is a large number in a profile; it is not obviously a large number
in a load test. Against a real API a virtual user spends most of its iteration waiting on the
network, and an allocation cost that vanishes into that wait is not worth a cache, an eviction
policy and a new mutable object graph. Everything above was measured against a localhost mock,
which is precisely the configuration that makes client-side cost look important (§2, §7).

So the gate is the experiment §2 and §9 already ask for and neither has built: **give the mock
an injected latency, then re-run this comparison at 0 / 10 / 50 ms.** If the gap between cached
and uncached shrinks to nothing at realistic latency, this section is closed on evidence rather
than parked on judgement. Note the instrument matters here — a mock that is itself a Karate
feature saturates before either client does, so the tier to build is one **we own and can
instrument, in a separate process and not necessarily a JVM**, which is the same "cheap mock
tier" the throughput ceiling waits on. Build that once and it settles three questions.

#### If it is ever taken: hang the AST off the Step, not off a map

The prototype's design is the wrong one, and the better one dissolves the questions it raised
rather than answering them. A keyed cache needs a scope, a bound and an identity rule:

- **Scope.** Suite-scoped helps ordinary runs and does nothing for karate-gatling, which builds a
  Suite per execution; process-wide helps both and is global mutable state in the JS engine.
- **Bounding.** Keyed by source text, the key set is normally bounded by the source files — but
  not when the text is generated: `eval("read('" + path + "')")`, `evalAsStep(userText)`, and
  outline rows whose `<placeholder>` substitution makes every row's step text distinct. An
  unbounded map in a long-lived process (serve, MCP, the IDE plugin) is a leak with a nicer name.
  Note this is the *only* one of the three that the
  [footprint constraint](#the-standing-constraint-footprint-is-fine-a-rising-floor-is-not)
  does not forgive — the memory a cache holds is not the objection, the fact that this one is
  never released is.
- **Identity.** The key must carry the resource, not just the text: an AST's tokens name the file
  they came from, and two files sharing a step line would otherwise report positions against
  whichever parsed first.

**Storing the parsed AST on the `Step` itself has none of those.** There is no key, so no
identity rule; the AST is reachable only from the step that owns the source, so it is bounded by
the parsed model and dies with it; and the scope question disappears because a Step lives exactly
as long as the Feature it belongs to. It is the shape the
[footprint constraint](#the-standing-constraint-footprint-is-fine-a-rising-floor-is-not) is
happy to pay for: more retained per parsed feature, released with it, no eviction policy to get
wrong.

The catch is that it only pays if `Step` objects are *reused*, and today none of the three
repeating paths reuse them — a `karate.call()` re-parses the callee, an outline row copies its
steps, and karate-gatling re-parses per execution. **So the Step-held AST is not a separate,
simpler option; it is what the feature-parse cache buys you once it exists.** The order is:
make the parsed model shareable, then the expression cache is a field, not a subsystem.

Making it shareable is the real work, and the shape to aim at is **an immutable
`Feature`/`Scenario`/`Step` plus a runtime-side composite holding everything an execution
mutates**. Three mutations have to move for that, all found while measuring and all on the
plain-scenario path (outline rows already take copies via `toScenario` / `copy`):

| What is written today | Where | Where it would live |
|---|---|---|
| `Scenario.selected` — a per-suite tag-selection memo on the model | `Suite.sectionCanMatch`, undone by `clearScenarioSelectionCache` | per-Suite, keyed by scenario identity |
| `scenario.setName(...)` for an evaluated dynamic name | `ScenarioRuntime.evaluateScenarioName` | the runtime / the `ScenarioResult` |
| `ScenarioOutline.numScenarios`, an ever-incrementing counter feeding `karate.info` | `ScenarioOutline.toScenario` | per-run counter |

One trap to carry into that work: `Scenario.replace()` mutates step text for `<placeholder>`
substitution, so a step copy must not share its template's cached AST once replaced — the AST
belongs to the text, and that is the one place the text changes after parsing.

A Step-held AST also covers only expressions that *have* a Step. `evalAsStep`, the sub-parses
inside `processEmbeddedExpressions`, and generated `eval("read(...)")` strings do not, and would
keep parsing per call — which is fine, because they are also the unbounded ones.

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
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table. `--record mock` already exists to support this. **The raw-Java tier is step 3 of the ordering above** — it is what the Gatling throughput ceiling waits on, and it should carry the same latency knob. |
| Gatling parity — **partly built** | The null-overhead probe, the parity sim and the allocation comparison exist (§2, §6). What is left: a **throughput ceiling**, which needs a mock tier cheap enough not to be the bottleneck — today both variants saturate the feature mock, so req/s measures the mock. That tier is the "Mock throughput tiers" row below, not a separate piece of work; see the ordering note at the top of this section. Also unbuilt: `--duration` support, which needs `during()` in the injection profile rather than a repetition count, and is the prerequisite for a Gatling soak. Note the entry point is `Gatling.fromArgs`, not the `fromMap` this row used to name — `fromMap` was removed in Gatling 3.15. |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest`, so a recording carries Karate semantics and allocation attributes to a *feature line*. **A CPU-tuning need, not a memory one.** The virtual-thread gap is specific to `jdk.ExecutionSample`; everything the memory work relied on attributes correctly regardless of thread model. Build these when the question becomes "where is the CPU going during a parallel run" — exactly where `ExecutionSample` goes blind. |
| `profiler compare A B` | Side-by-side delta table from two run directories. |
| Machine-readable baselines + CI | Committed `baselines/*.json`, a scheduled job, regression thresholds. Out of scope until the manual playbook has proven itself. |
| `jcmd GC.class_histogram` checkpoints | Per-class growth over time. Forces a GC, so it perturbs the measurement. |
| Heap-dump class histogram in `JfrDigest` | Deliberately not implemented: no JDK API or CLI reads an `.hprof` (`jhat` removed in Java 9, `jmap -histo` is live-process only). The digest points at Eclipse MAT instead. Revisit only by hand-rolling a histogram-only reader or taking a dependency. |

---

## 10. The latency-mock experiment — scoped, being built

Everything above measures Karate against a mock that answers in about a millisecond. That is
the configuration which most flatters client-side cost, and every open question in §9 turns on
whether the costs it exposes survive contact with a real API. This section is the scope for the
artefact that settles it. Unlike §9, this is **scheduled work, not a parked design** — prune it
into §2 and §6 once the numbers exist and the pieces are documented where they live.

### The question, and the answer that would close it

**Does Karate's per-execution overhead disappear into the network time of the system under
test?** §2 argues this is the only form of the overhead question a load tester actually has, and
that the honest test is not a micro-ratio but this: run karate-gatling and plain Gatling against
the same target and compare the reports. Same TPS and the same response-time distribution means
the client is not distorting the measurement, which is the whole job.

That test carries one condition, and it is the condition this harness keeps failing:

> **Equal TPS only means anything when the client has headroom.** Two saturated clients queued
> behind the same overloaded server also report identical numbers.

Today both variants saturate the Karate feature mock. **Running the comparison now would pass,
and prove nothing** — the most dangerous outcome available, because it looks like an answer. The
mock is therefore not a convenience for this experiment; it is the experiment's validity.

Acceptance is three things together, not TPS alone:

1. **Parity** — TPS and the p50/p95/p99 distribution match between the two variants;
2. **Headroom** — the injector is demonstrably not the bottleneck (CPU on the injector, the
   mock's own in-flight count well below its calibrated knee, and the **queueing residue** below
   flat);
3. **A flat floor** — heap-after-GC does not drift, per §4's
   [standing constraint](#the-standing-constraint-footprint-is-fine-a-rising-floor-is-not).
   Allocation *rate* is explicitly not an acceptance criterion; it never was the thing that
   mattered, only the proxy the localhost mock made look important.

**Do not pick a percentage tolerance up front.** §2 already sets out why a threshold invites
tuning toward it. The deliverable is a shape: *at what concurrency do the two diverge, and by how
much at each latency tier*. If the gap stays roughly constant in absolute milliseconds while
shrinking as a share of the iteration, the overhead disappears into network time and the parse
work in §9 stays parked — closed on evidence rather than on judgement. If it does not shrink,
the overhead is proportional to something and that question reopens with a number behind it.

### A — `LatencyMock`: a server whose own cost is known

A mock we own and can instrument, in its own process, serving the same three endpoints as
`profiling-mock.feature` (`/ping`, `POST /cats`, `GET /cats/{id}`) so both existing simulations
run against it unchanged.

| | Decision |
|---|---|
| Transport | JDK `com.sun.net.httpserver.HttpServer` on a virtual-thread executor. No new dependency, and its cost is easy to reason about. |
| Latency | `--latency 10ms`, injected as a sleep. Virtual threads are what make this cheap: a mock holding hundreds of sleeping requests must not need hundreds of platform threads, or the *mock* becomes the concurrency limit being measured. |
| Instrumentation | Requests served, **own service time excluding the injected sleep** (histogram), and peak in-flight. Reported on a `/stats` endpoint and as a summary line at shutdown. |

**The instrumentation is the saturation detector — but only in one direction, and the scope was
wrong about this until review caught it.** A handler-side clock starts at handler entry, so it
cannot see the accept queue, the dispatcher thread that owns socket readiness, or the handoff to
the executor. A mock saturated *upstream of its own clock* reports flat handler p99 while clients
observe rising latency. So: **p99 climbing proves the mock is the bottleneck; p99 flat proves
nothing.** Three things close that gap, and none is optional:

- **Calibrate before the matrix, not after.** Between B and D, drive the mock alone with a
  known-cheap client at each latency tier and find its knee — in *both* keep-alive and
  connection-per-request modes. Then run the matrix demonstrably inside that envelope.
- **Derive the queueing residue** in the digest:
  `client-observed p99 − (injected latency + mock handler service time)`. That difference is
  where invisible queueing shows up, whoever owns it. It is the number that makes "the mock
  stayed out of the way" a measurement rather than an assumption.
- **Set the backlog explicitly** — `HttpServer.create(addr, backlog)`; the default delegates to
  a small system value, which is itself a silent ceiling.

**The two variants do not present the same load to the mock, and they differ on exactly its
weakest axis.** Karate constructs an HTTP client per execution — §6's own unchased
`initHttpClient` lead — so the karate variant is roughly one TCP connection per iteration, while
plain Gatling holds per-user keep-alive connections. Connection establishment through a single
dispatcher thread is the JDK server's softest spot. "Run both against the same target" is not
satisfied by running them against the same *process* if the target's cost is connection-shaped,
which is the second reason calibration must cover both connection modes.

Starting on the JDK server rather than Netty is deliberate and reversible — but it is defensible
*only together with that calibration*. Without it, "the mock reports its own numbers, so it will
say plainly if it is the ceiling" is the part that is not true.

Two more constraints on the mock itself: keep its state **bounded** (`profiling-mock.feature`
grows a `cats` map on every POST and the plain sim genuinely reads back the id it created, so the
store is required — synthesize the GET from the id, or cap it, or the instrument's own heap
becomes a variable over a long ramp); and keep stats collection to adders, a fixed-bucket
histogram and an atomic max. **No per-request logging and no synchronized histogram** — that is
the one way this instrumentation would perturb what it measures.

**JFR is not embedded in it**, and that is a considered choice. What this needs is exact
accounting — counts, service time, in-flight — not sampling. JFR answers "*why* is the mock
slow", which is the follow-up question, asked only once the counters say it is one; and
`--record mock` already exists to answer it when that day comes.

### B — harness wiring

`--mock latency|feature` selects the tier and `--mock-latency <d>` sets the knob. The feature
mock stays, because it is the thing being profiled in the "is the mock server fast enough" recipe
(§4). Two tiers, two purposes: the feature mock is a *subject*, the latency mock is an
*instrument*, and conflating them is how the throughput ceiling got stuck in the first place.

### B2 — calibrate the mock, before anything is compared against it

Drive `LatencyMock` alone with a known-cheap client at each latency tier, in **both** keep-alive
and connection-per-request mode, and find the knee: the concurrency at which client-observed
latency departs from `injected latency + handler service time`. That number is the envelope every
cell of the matrix must run inside, and it is what converts "the mock stayed out of the way" from
an assumption into a measurement. Skipping this is how the experiment passes while the mock
silently queues upstream of its own clock.

Cheap to build: the calibration client is the raw-`java.net.http` driver §9 has wanted all along
for the "Mock throughput tiers" row, so this step pays a debt rather than adding one.

### C — make the comparison numeric

Gatling's HTML report is off in this harness on purpose (`-nr`: chart generation is a second pass
over the simulation log that would land in the digest as if it were load-driving cost). Note what
that reason is actually about — *when* the pass runs, not whether. **Run it in the parent, after
the child has exited** (`-ro`, reports-only) and it contaminates nothing, then scrape its stats.

That is also the only sane route, because **`simulation.log` is binary**: Gatling has written it
through `BufferedFileChannelWriter` since 3.12, and this repo is on 3.15.1. "Parse simulation.log"
sounds like a text scrape and is not one — the alternative to a reports-only pass is coding
against Gatling's internal reader, which is the same version-fragility that already bit the
`fromMap` → `fromArgs` change recorded in §9.

Into `digest.md`, for both variants alongside the mock's own stats: count, TPS, p50/p95/p99, KO.
Two requirements on those numbers:

- **KO must break down by error type**, not just count — see the 0 ms trap below.
- **Report a trimmed window as well as the raw one.** Both sims are self-driving, so per §1 they
  run *no warmup* and every statistic starts at a cold JVM. Karate carries ~2 core-seconds of
  one-time init and far more JIT surface than plain Gatling; at 20 000 iterations, p99 is the
  worst 200 samples and cold start contributes hundreds of slow ones **asymmetrically**. Left
  raw, acceptance criterion 1 can fail on class loading alone.

Also in the digest, and the sharpest instrument here:

> **Per-iteration residue** = `action elapsed − Σ(PerfEvent.end − PerfEvent.start)`.
>
> Karate's own overhead per virtual-user iteration. `PerfEvent` already carries per-request
> start/end and `KarateExecutor`'s hook receives every one, so karate-core needs no new
> instrumentation — but **the karate-gatling side does**: "action elapsed" does not exist yet.
> Put the clock around the *whole body* of `KarateScalaAction.execute`, not just around
> `executor.execute`, so the Scala bridge's session-map copying is counted — a user pays for it.

**Quote the mean residue; never its distribution.** `PerfEvent` start/end are
`System.currentTimeMillis()` stamps (`ApacheHttpClient.process` / `buildResponse`), so every event
duration carries ±1 ms of clock-phase noise — *larger than the ~0.45 ms signal the residue exists
to measure*. The error is unbiased, so a mean over thousands of iterations is meaningful and
per-iteration values and residue percentiles are quantization noise. This is exact accounting,
ms-quantized — not the "exactly, in milliseconds" the first draft of this section claimed.

Four things land inside that subtraction besides Karate's own work. Three are excluded by
definition, one is a caveat for whoever reuses the number later:

| Contaminant | Handling |
|---|---|
| Gatling scheduling delay once the injector is saturated | The reason the headroom check is load-bearing, not hygiene — a rising residue is otherwise ambiguous between "Karate got slower" and "the injector ran out of threads" |
| Protocol pauses / `karate.pause` — `reportPerfEvent` sleeps *inline* on the Gatling thread, inside elapsed and outside Σ | The residue is defined only for sims with no pauses. Today's sims configure none; say so, because the first person to copy this recipe onto a sim with think time gets garbage |
| KO iterations — a scenario failing before its first request emits a synthetic PerfEvent spanning the whole scenario, driving residue to ~0 | Exclude KOs from the residue population |
| Response-body read time — `buildResponse` stamps `endTime` at entry, *then* reads and decompresses the entity, so body transfer falls outside Σ and inflates the residue | Negligible for these payloads on loopback; structural against a real API with real bodies. Recorded so the number is not reused blind |

### D — the matrix, then the doc

Latency ∈ {0, 10, 50} ms × a user ramp, plain and karate back to back, same machine. Report where
they diverge. Fold the result into §6 and retire whichever §9 items it settles. The divergence
point is **a shape, not a number** — it is machine-bound in the way §7 describes, and more so
than usual here, because Karate's extra CPU starves a co-located mock in a way a remote target
would not.

**Two outcomes are predictable enough to pre-register, so they are not mistaken for discoveries:**

- **At 0 ms, expect connection churn, not CPU.** One client per execution means one TCP
  connection per iteration, client-side close, client-side TIME_WAIT. macOS gives ~16K ephemeral
  ports and holds TIME_WAIT for 15 s, so a few thousand karate iterations/s exhausts the range
  mid-run and produces `EADDRNOTAVAIL` KOs that poison the percentiles. That is not a divergence
  measurement, it is a ruined cell — hence the KO breakdown in C, and watch TIME_WAIT at this
  tier. Note the honest ambivalence: per-execution client construction *is* Karate's real
  behaviour and a load tester would suffer it too, so the experiment surfacing it is a feature.
  It may be what finally justifies chasing §6's `PooledHttpClientFactory` lead.
- **At 10/50 ms, expect the thread-blocking cap to divergence-first, not per-iteration CPU.**
  `KarateScalaAction.execute` occupies a Gatling thread for the whole feature and that pool is
  roughly core-count. **And it will not look the way §9 predicts.** §9 says thread starvation
  shows up as "reported response times inflating"; as instrumented here it will not, because the
  karate variant's response times come solely from `PerfEvent` brackets *inside* the action,
  while queue-for-a-thread time sits between actions, outside every bracket. The real signature is
  **TPS shortfall with clean-looking latencies** — sneakier than §9 promises. Read a TPS-only
  divergence as the density cap; do not hunt it as a CPU mystery.

The matrix inherits `injectOpen(atOnceUsers(N))` + `repeat`, i.e. a **closed** model. That is the
right default here — both variants are symmetric, TPS becomes the divergence detector, and the
user ramp doubles as §9's concurrency-density experiment, which is the economy this section is
after. If the distortion question ever needs its cleanest form, one open-model cell
(`constantUsersPerSec` at a fixed arrival rate, both variants) is the direct test and is cheap to
add later.

### Why no custom JFR events on the karate-gatling side

They were considered and are not needed for this, for a reason worth recording because it
corrects what §7 used to claim (§7 now carries the correction, so the two agree — this is the
reasoning behind it, not a live contradiction):

**`Runner.runFeature` never calls `parallel()`**, so `FeatureRuntime` takes its sequential branch
and runs the scenario **inline on the Gatling thread that called it** — a platform thread. So
`jdk.ExecutionSample` *does* see Karate's work in the Gatling lane, and the *Hot methods* panel is
usable there. §7's virtual-thread blindness is a **Runner-parallel-suite** problem, not a Gatling
one. The `gatling-null-karate` digest corroborates it: `KarateScalaAction.execute`,
`Interpreter.evalStatement` and `StepExecutor.evalKarateExpression` all appear with real samples.

So the standard profiler already works in this lane, and the question being asked here is a
latency-breakdown question anyway, which the residue answers exactly and a sampler answers
approximately. The custom-events row in §9 stands as written — build it when the question becomes
"*where inside* Karate's overhead", and only if the residue says that overhead is worth chasing.

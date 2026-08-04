# Profiling Karate

> Runbook for JFR-based profiling of karate-core, the mock server and the JS engine.
> Written for an LLM operator: every step is a command you run and an artifact you read.
> Companion to [PROFILING_PLAN.md](./PROFILING_PLAN.md) (build-out status — what of this
> exists yet).
>
> **Everything here reports. Nothing asserts.** There is no CI job, no committed baseline
> file and no pass/fail gate. An OOM is a legitimate outcome — for some workloads it is
> the *expected* one. Judgement lives with the reader, and last-known numbers live in
> [Current baseline](#6-current-baseline) below.

---

## 1. Quick start

```bash
cd karate-profiling

etc/run.sh --list                        # what can I run?
etc/run.sh leak-callonce --iterations 50 # smoke: is the harness alive?
etc/run.sh scope-capture-bound           # the real thing
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

### Flags

| Flag | Default | Notes |
|---|---|---|
| `--threads N` | per workload | Concurrency (virtual threads). |
| `--iterations N` | per workload | Fixed iteration count. Mutually exclusive with `--duration`. |
| `--duration 10m` | per workload | Run for a wall-clock window instead. Use for soaks. |
| `--xmx 768m` | per workload | Child heap. **The single most important knob** — a leak that OOMs at 768m may never surface at 4g. |
| `--gc g1\|zgc` | `g1` | See [Reproducing a specific collector](#reproducing-a-specific-collector). |
| `--warmup Ns` | per workload | Excluded from the measured window — the recording is delayed past it. |
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

*Healthy result:* peak heap roughly flat as scenario count rises. See
[Current baseline](#6-current-baseline) for what it does today.

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

### Leak-watch family — does a realistic suite retain over time?

| Workload | Exercises |
|---|---|
| `leak-watch` | The realistic soak, and the canary: `karate-config.js` doing a call, `callSingle`, `callonce` in `Background`, `call read()` with an args object, a bare `karate.call()` sharing scope, JS function defs, HTTP against the forked mock, `match` assertions. |
| `leak-callonce` | `callonce` only. |
| `leak-callsingle` | `karate.callSingle()` only. |
| `leak-shared-scope` | Bare `karate.call()`, no args object — callee inherits caller scope. |
| `leak-isolated-scope` | `call` **with** an args object — the control for the above. |

Run `leak-watch` when you want to know "is Karate leaking". Run the thin variants when
`leak-watch` has moved and you need to know *which mechanism* moved it — that is the only
reason they exist. `leak-shared-scope` and `leak-isolated-scope` are a matched pair in the
same sense as the scope-capture workloads.

*Healthy result:* a **flat** heap-after-GC floor across the whole run, at any sawtooth
amplitude. See §4.

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

#### `call-accumulation` scale sweep — 2026-08-04, machine A, karate 2.1.2.RC1

60 calls per scenario, 16 threads, `-Xmx768m`.

| scenarios | peak heap | Δ per +1000 scenarios | elapsed |
|---:|---:|---:|---:|
| 250 | 206 MB | — | 1.5s |
| 500 | 281 MB | +75 MB | 2.0s |
| 1000 | 420 MB | +139 MB | 3.2s |
| 2000 | 706 MB | +286 MB | 4.5s |

**Peak heap grows linearly with scenario count — roughly 143 KB retained per scenario,
held for the whole suite run.** This is accumulation, not churn: nothing is released
between scenarios. Extrapolating, ~5000 scenarios of this shape needs more than 768 MB,
which matches the independent observation below.

#### Cross-check against the external reproducer — 2026-08-04, machine A

Their harness, unchanged, varying only the karate version:

| variant | karate | `-Xmx` | scenarios | result |
|---|---|---:|---:|---|
| J (13 bound captures) | 2.0.10 | 768m | 5000 | heap pinned at 785,383K of 786,432K, ~760% CPU, killed at 10 min |
| J | 2.1.2.RC1 | 768m | 5000 | **all passed, 1.96s** |
| E (60 calls/scenario) | 2.1.2.RC1 | 768m | 500 | passed, 4.5s |
| E | 2.1.2.RC1 | 768m | 2000 | passed, 7.5s |
| E | 2.1.2.RC1 | 768m | 5000 | heap pinned at 99.8%, ~805% CPU, killed at 12 min |
| E | 2.1.2.RC1 | **3g** | 5000 | **all passed, 8.8s** |

Two conclusions, and they point in opposite directions — which is why both runs matter:
the scope-nesting mechanism is gone on current main, and the call-result accumulation
mechanism is not.

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
- **These runs eat disk.** Each OOM leaves a heap dump roughly the size of `--xmx`
  (a 768m run leaves ~768 MB), and run directories are never overwritten. Prune
  `target/profiling/` periodically, or `mvn clean` the module.

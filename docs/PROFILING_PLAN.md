# karate-profiling — build-out plan

> Companion to [PROFILING.md](./PROFILING.md) (the runbook — permanent). **This file is
> temporary**: it tracks building the harness and lands the #2972 fix. Retire it when the
> checklists are done, the way [DRIVER_PLAN.md](./DRIVER_PLAN.md) was repurposed once its
> tranches were settled.
>
> Written 2026-08-04. Line references verified against the tree at that date.

---

## Why

Three threads converge.

**1. Issue #2972 reports an `OutOfMemoryError` under parallel execution** on
`karate-junit6:2.0.10`. The reporter gave two analyses. Their first — unbounded
`ScenarioResult.stepResults` / `SuiteResult.featureResults` accumulation — they later
described as a rounding error, having captured a MAT heap dump putting 89% of a 3.87 GB
live heap in the stack locals of a 13-deep self-recursion of
`StepExecutor.processEmbeddedExpressions`, each frame holding a `LinkedHashMap` about half
the size of the one above it. That geometric decay is the signature of nesting: level N
contains level N+1.

> **Phase 4 measured both, and reversed the verdict.** The nesting mechanism does not exist
> on current main — a bare `karate.call()` no longer returns the caller's scope, so captures
> cannot nest, and the reproducer's J variant passes in 1.96s where it saturates a 768m heap
> on 2.0.10. The mechanism they retracted is the one still live: retained call results grow
> linearly with suite size, ~143 KB per scenario. Details and numbers in
> [Phase 4](#phase-4--demonstrate-the-problem--done-2026-08-04).
>
> This is why the plan put the harness before the fix. Every code reading below was
> consistent with the reported cause and still pointed at the wrong thing; only measurement
> separated them.

**2. There is no way to demonstrate or measure any of this.** v1 had
`examples/profiling-test` (a headless `Main.java` loop plus a Gatling
`TestSimulation.scala`), which was useful for finding bottlenecks and hotspots. v2 has
nothing equivalent.

**3. [GATLING.md](./GATLING.md) Phase 6** has open TODOs to compare karate-gatling against
vanilla Gatling and to port v1's profiling-test. Same machinery, so it eventually folds in
here — but Gatling workloads are **not** in the first cut, and GATLING.md is left untouched
for now.

**Order is deliberate: harness and runbook first, demonstrate the problem, then fix.** No
core change is attempted until the harness has reproduced the behaviour on its own evidence.
Phase 4 is the vindication of that ordering — the reported cause was already fixed, and
changing code on the strength of the report alone would have been wasted work aimed at the
wrong mechanism.

---

## Decisions

| Question | Decision | Why |
|---|---|---|
| Module | New `karate-profiling` reactor module | Not mixed into karate-gatling — that module's tests are CI smoke tests, these are heavy deliberate runs |
| Publication | Deploy-skipped, mirroring `karate-js-test262` | Internal harness; must never reach Maven Central. Always compiles, so it can't bit-rot |
| Entry point | One `Profiler` CLI + Java `Workload` SPI | Structural probes (raw JS engine, HTTP client) need Java; a declarative-only manifest couldn't express them |
| Load driver | Plain executor / virtual threads, throwaway | No pretence of being a load-test framework. Lessons transfer; code needn't |
| JVM control | Parent self-forks a child with the workload's exact flags | `-Xmx768m` + collector choice *is* the experiment; it can't live outside the workload definition |
| Mock server | Forked as a **sibling** JVM | In-process, its CPU and allocations land in the same recording and every number is a client/server blend |
| Mock as subject | `--record mock` flips which JVM is recorded | "How fast is the Karate mock?" is a recurring user question and nothing else stresses it. Also the only config where CPU sampling is trustworthy (mock serves on platform threads) |
| Gatling | `-Pgatling` profile + programmatic launch, **not first cut** | Keeps Scala out of every build; scaffold only for now |
| Leak detection | Heap-after-GC series + `jdk.OldObjectSample`, heap dump on OOM | The series is what distinguishes churn from retention — the exact confusion that cost #2972 several rounds. `jcmd GC.class_histogram` deferred: it forces a GC and perturbs the measurement |
| Analysis | Built-in `JfrDigest` → `digest.md`, plus raw `jfr` recipes in the runbook | A `.jfr` is binary and enormous; the reader is an LLM with a context budget |
| Custom JFR events | **Deferred** | Reconsider `karate.Step` / `karate.Call` events in karate-core and karate-js once initial results show whether JDK events are insufficient. The virtual-thread CPU-sampling gap (below) is an argument in favour |
| Outcomes | Always report, never assert | Saturation is a legitimate outcome for some workloads. Assertions and baselines can come later |
| Baselines / CI | None. Runbook holds the numbers | No CI job, no committed baseline files, no automation. LLM-assisted manual playbook to start |
| GC | G1 default, `--gc zgc` opt-in | G1 is what most users get; ZGC reproduces the reporter's environment. Expansion is JDK-dependent — see runbook |
| Repro fidelity | Native workloads, cross-checked **once** against the reporter's repo | Ours to maintain, but validated. This earned its keep immediately: our workload did not reproduce, and the cross-check is what turned that into a finding rather than a false all-clear |
| JS-engine workloads | Deferred to phase 2 | Keep the first cut on the problem that motivated it |
| Fix shape | **Retargeted after Phase 4** — see [Phase 5](#phase-5--the-fix-retargeted) | The original copy-on-first-change plan lost its justification when the nesting turned out not to exist |
| Issue write-up | Commit message only for now | Ask the reporter to re-verify against the 2.1.2 release rather than asking them to build from source |

---

## Phase 1 — The module

```
karate-profiling/
├── pom.xml                       # deploy/gpg/javadoc/source skip; exec.mainClass
├── etc/run.sh                    # build classpath, install karate-core, exec:java
└── src/main/java/io/karatelabs/profiling/
    ├── Profiler.java             # CLI (run | list) + forking parent
    ├── Workload.java             # SPI
    ├── WorkloadConfig.java       # threads, iterations, duration, xmx, gc, warmup, timeout
    ├── Workloads.java            # registry
    ├── Child.java                # child-JVM main: runs one workload, never forks
    ├── MockJvm.java              # sibling mock JVM main + port handshake on stdout
    ├── jfr/JfrDigest.java        # RecordingFile → digest.md
    └── workload/…
```

- [ ] `pom.xml` — copy the property block from `karate-js-test262/pom.xml:23-34`
      (`maven.deploy.skip`, `maven.install.skip=false`, `maven.javadoc.skip`,
      `maven.source.skip`, `gpg.skip`, `skipPublishing`, `exec.mainClass`) plus the
      explicit source-attach skip. Depends on `karate-core` only.
- [ ] Add `karate-profiling` to the root `<modules>`, after `karate-gatling`.
- [ ] Scaffold a `<profile><id>gatling</id>` adding `karate-gatling`. Unused in the first
      cut. **No `scala-maven-plugin`, no `gatling-maven-plugin`** in this module — we write
      no Scala; karate-gatling already provides the Scala bits.
- [ ] `Workload` SPI. **`describe()` text must describe behaviour, not cite issue numbers**
      — per [CLAUDE.md](../CLAUDE.md), and because `--list` output is source, not docs.
      ```java
      public interface Workload {
          String name();
          String describe();                 // powers `--list` and the runbook catalogue
          default JvmConfig jvm() { … }      // default xmx/gc, CLI-overridable
          default boolean needsMock() { … }
          void setup(WorkloadContext ctx);
          void iterate(int vu, long iteration);
          default void teardown() {}
      }
      ```

### The forking parent

This is the piece that has to be right, because it is what makes runs reproducible — and
several of its details are load-bearing for whether a recording survives at all.

- [ ] Resolve the child classpath — `etc/run.sh` runs `mvn dependency:build-classpath
      -Dmdep.outputFile=target/cp.txt` once, the parent reads that file.
- [ ] If `needsMock()`, fork the mock JVM **without** JFR, read its port from a handshake
      line on stdout, pass `-Dmock.url=…` to the child.
- [ ] Fork the workload child:
      ```
      -Xmx<xmx> -XX:+UseG1GC
      -XX:StartFlightRecording=settings=profile,delay=<warmup>,maxsize=<cap>,filename=<run>/run.jfr
      -XX:FlightRecorderOptions:repository=<run>/jfr-repo
      -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<run>/heapdump.hprof
      ```
      Four things here are deliberate:
      - **Never add `-XX:+ExitOnOutOfMemoryError`.** It halts the JVM without flushing, and
        `run.jfr` comes out **0 bytes** — on the one workload whose *expected* outcome is
        an OOM. An OOM that propagates through orderly shutdown writes both the recording
        and the heap dump correctly.
      - `repository=` keeps the live chunks on disk so `jfr assemble` can rescue a run
        whose `run.jfr` never got written (runbook §4).
      - `maxsize=` is required: the default is 0, i.e. **unbounded**, and a 10-minute soak
        at `settings=profile` will produce a very large file.
      - `delay=<warmup>` is what actually makes "the measured window excludes warmup" true.
        Alternative: have `Child` drive a programmatic `jdk.jfr.Recording` after its warmup
        loop. Pick one and make the runbook match — the two must not drift.
- [ ] Stream child stdout/stderr to console and `stdout.log`.
- [ ] **Timeout / watchdog.** Karate runs scenarios on a virtual-thread executor inside
      try-with-resources (`Suite.java:811`) whose `close()` waits indefinitely, so a
      swallowed worker OOME can leave the child alive-but-dead forever. On `--timeout`
      expiry the parent must `jcmd <pid> Thread.print` (into `stdout.log`),
      `jcmd <pid> JFR.dump`, then kill. Without this the operator waits on a `digest.md`
      that will never appear.
- [ ] **Orphan prevention.** Parent shutdown hook kills both children; children exit on
      stdin EOF. A mock JVM that outlives a Ctrl-C'd parent silently holds its port and
      poisons the next run.
- [ ] **OOM detection.** Exit code is not sufficient — Karate can swallow a worker OOME and
      exit 0. Primary signal is the presence of `heapdump.hprof`; secondary is grepping
      `stdout.log` for `OutOfMemoryError`. Report both in the digest.
- [ ] On exit, run `JfrDigest` over `run.jfr` → `digest.md`.
- [ ] **Report, never assert.** Record exit code, wall time, whether a heap dump appeared,
      whether the run timed out. No PASS/FAIL.
- [ ] Record the **child JDK version** in `run-meta.txt` — `--gc zgc` expands differently
      per JDK (runbook), so a digest without it can't be compared.
- [ ] `--record mock` flips which JVM gets `StartFlightRecording`, driving the mock with a
      cheap raw-`java.net.http` client.
- [ ] Per-run output directory, never overwritten (layout in runbook §1).

Borrow from `EngineBenchmark`
(`karate-js/src/test/java/io/karatelabs/parser/EngineBenchmark.java` — reference only,
left where it is): a warmup phase before the measured window, a loop-for-N-seconds
profiling mode, median-of-N rather than mean.

---

## Phase 2 — `JfrDigest`

`jdk.jfr.consumer.RecordingFile` for everything JFR-sourced. Panels, in order:

- [ ] **Run summary** — duration, exit status, timed-out flag, child JDK, JVM/GC/heap flags.
- [ ] **Allocation by site** — `jdk.ObjectAllocationSample` weighted bytes, stacks
      collapsed to `io.karatelabs.*` frames. Attributes correctly across virtual threads.
- [ ] **Hot methods** — `jdk.ExecutionSample`, same collapsing. **Must carry an inline
      warning**: `ExecutionSample` samples platform threads, and Karate runs every scenario
      on a virtual thread, so this panel severely under-samples scenario code. Measured:
      8 virtual threads saturating CPU for 5 s produced 9 samples. It is trustworthy only
      for the mock JVM and platform-thread paths. Revisit if JDK 25's `jdk.CPUTimeSample`
      becomes available to us, or if custom `karate.Step` events land.
- [ ] **Heap-after-GC series** — `jdk.GCHeapSummary`, filtered to `when = "After GC"`
      (each collection emits a Before and an After row). The most important panel; see
      runbook §4.
- [ ] **GC pauses** — count + histogram.
- [ ] **Retained objects** — `jdk.OldObjectSample` with allocation stacks. Enabled by
      `settings=profile`. Note in the output that this gives the *allocator*, not the
      *holder*, unless the run enabled `path-to-gc-roots` (off by default).
- [ ] **Top classes** — class histogram from `heapdump.hprof`, when one exists.
      **There is no JDK API or CLI for this** (`jhat` was removed in Java 9; `jmap -histo`
      is live-process only), so pick one deliberately: hand-roll a histogram-only `.hprof`
      reader (the format is stable and this is a few hundred lines), take a named
      dependency, or drop the panel and delegate to Eclipse MAT in the runbook. Do not
      leave it as "no external dependencies" plus an hprof requirement — that combination
      is unimplementable.

Output must be stable-sectioned so two digests diff cleanly.

---

## Phase 3 — First-cut workloads

- [ ] `scope-capture-bound` / `scope-capture-unbound` — native re-implementation of the
      reporter's J/K variants. 13 sequential bare `karate.call()`s over a ~100-record base
      payload, each result bound; `unbound` adds `* def capN = null` after each capture.
      Defaults 16 threads / 5000 iterations / `-Xmx768m` / G1.
- [ ] **One-time cross-check** against the reporter's public reproducer, linked from the
      #2972 thread. Run their bound and unbound variants per that repo's README (do not
      assume test-method names — they may have changed) and confirm our workloads
      reproduce the same *shape*: bound saturates and OOMs early, unbound completes at a
      fraction of the heap. If ours doesn't, ours is wrong; catch that before drawing any
      conclusion. Nothing from their repo is vendored in, and the durable pointer is the
      issue thread, not a personal repo URL.
- [ ] Leak-watch family — `leak-watch` (realistic canary), `leak-callonce`,
      `leak-callsingle`, `leak-shared-scope`, `leak-isolated-scope`. Shape informed by
      v1's `examples/profiling-test/src/test/java/perf/{main,called,mock}.feature`,
      modernised for v2. Fixed concurrency for N minutes via
      `Runner.builder().path(…).parallel(threads)` (`Runner.java:283`, `:860`); the mock is
      `MockServer.feature(…).start()` in the sibling JVM (`MockServer.java:76`, as
      `CatsMockServer.java:44` uses it).

---

## Phase 4 — Demonstrate the problem ✅ DONE (2026-08-04)

The gate was: do not touch core until the harness reproduces the reported behaviour on its
own evidence. It ran, and it overturned the premise. **Both halves below were measured on
machine A (see the runbook baseline); the numbers live there, not here.**

### The reported mechanism is already fixed

The scope-capture nesting this plan was built around **does not occur on current main**.
Running the external reproducer unchanged, varying only `-Dkarate.version`, its J variant
pins a 768m heap at 99.9% on 2.0.10 and passes 5000 scenarios in 1.96s on 2.1.2.RC1.

The reason is structural, and `Probe` measured it directly rather than inferring it: on
main, *every* call form returns only the callee's own variables — 2 container nodes —
where the reported mechanism requires a bare call to return the caller's entire scope so
that capture N contains captures 1..N-1.

```
karate.call('f.feature')               -> Map(3) keys=[sawCallerBase, marker, fn]  nodes=2
call read('f.feature')                 -> Map(3) keys=[sawCallerBase, marker, fn]  nodes=2
call read('f.feature') { seed: 1 }     -> Map(4) ...                               nodes=2
karate.call('f.feature', { seed: 1 })  -> Map(4) ...                               nodes=2
base (100-record payload, for scale)   -> Map(1)                                   nodes=302
```

The nest cannot form, so the geometric copy cost cannot arise. `scope-capture-bound` peaked
at 173 MB against a 768m heap — a failed reproduction, which is the cross-check doing its
job. Reporting "no problem found" from that alone would have been right by accident and
wrong in reasoning.

### A different mechanism is live

`call-accumulation` — many sequential calls from one long scenario, across many scenarios
in one suite — shows **peak heap growing linearly with scenario count, ~143 KB retained per
scenario, held for the whole run**. The external reproducer agrees independently: its E
variant needs more than 768m for 5000 scenarios and passes comfortably at 3g.

This is the reporter's *first* analysis, the one they later described as a rounding error.
Against their production profile it may well have been. As a bug in its own right it is
real, and it is the one still worth fixing.

---

## Phase 5 — The fix (RETARGETED)

> The original plan here was copy-on-first-change in
> `StepExecutor.processEmbeddedExpressions`. **Phase 4 removed its justification**: without
> scope nesting the copy is linear in the value's own size, not geometric. It remains a
> genuine inefficiency — an unconditional deep copy of every Map/List-valued `def`, with no
> check for whether a `#(...)` is present — but it is an optimisation, not an OOM fix, and
> it should be argued on allocation numbers rather than on this issue. The analysis is kept
> under [Deferred](#deferred) so it is not re-derived.

The target is the append-only retention chain, every link of which grows without bound:

```
SuiteResult.featureResults        SuiteResult.java:37   synchronizedList, whole-run lifetime
  └─ FeatureResult
       └─ ScenarioResult.stepResults      ScenarioResult.java:43   ArrayList, append-only
            └─ StepResult.callResults     StepResult.java:48       List<FeatureResult>
```

`StepResult.callResults` is commented "For call steps — called feature results (V1 style)".
Every `karate.call()` retains the callee's entire result tree; nothing is released until
the suite ends. At 60 calls × 5000 scenarios that is 300,000 nested trees live at once.

Open question, to settle before writing code: **who actually consumes these?** They exist
for nested call display in the HTML report. If that is the only consumer, then a run with
reporting disabled has no reason to retain them at all — and the measured sweep above was
taken with every output format off. Options, in rough order of appetite:

- [ ] Retain call results only when a consumer needs them (reporting on), drop otherwise.
- [ ] Stream completed feature results to disk and release the in-memory tree, keeping
      only the counts `SuiteResult` needs for its summary.
- [ ] Bound the depth or breadth of retained nested call results.

- [ ] Whatever is chosen, re-run the `call-accumulation` sweep and require the peak-heap
      line to go flat. A single run cannot show this — the trend is the test.

---

## Phase 6 — Prove it

- [ ] Re-run the `call-accumulation` sweep at 250 / 500 / 1000 / 2000 scenarios; the
      peak-heap line must go flat instead of rising ~143 KB per scenario.
- [ ] Confirm the external reproducer's E variant passes 5000 scenarios at `-Xmx768m`.
- [ ] `leak-watch --duration 10m` before and after — the heap-after-GC floor must be flat
      in both. The fix must not have introduced retention.
- [ ] Update the runbook's baseline table.

---

## Verification

1. `mvn -q install -DskipTests` — module builds, joins the reactor cleanly.
2. `etc/run.sh --list` — catalogue prints with descriptions.
3. `etc/run.sh leak-callonce --iterations 50` — smoke: parent forks mock and child, port
   handshake works, all artifacts appear, both children are gone afterwards.
4. Kill the parent mid-run (Ctrl-C) — confirm no orphan mock or workload JVM survives.
5. `etc/run.sh scope-capture-bound` — expect saturation and OOM; confirm `heapdump.hprof`
   is written, **`run.jfr` is non-empty and readable**, and the digest's allocation panel
   names `processEmbeddedExpressions`.
6. `etc/run.sh scope-capture-unbound` — expect completion at a fraction of peak heap. The
   gap between 5 and 6 is the reproduction.
7. Cross-check once against the reporter's reproducer (Phase 3).
8. `etc/run.sh scope-capture-bound --gc zgc` — reproduces under the reporter's collector;
   confirm `run-meta.txt` records what `zgc` expanded to on this JDK.
9. Apply the fix; re-run 5 and 6.
10. `mvn test` across all modules green.
11. `etc/run.sh leak-watch --duration 10m` before and after.
12. Record every number from 5, 6, 9 and 11 in the runbook, with machine and child JDK.

---

## Deferred

Recorded so they aren't re-derived. None are in the first cut.

| Item | Note |
|---|---|
| Copy-on-first-change in `processEmbeddedExpressions` | The original Phase 5. Still a real inefficiency: `StepExecutor.java:3254-3296` rebuilds a fresh `LinkedHashMap`/`ArrayList` for every Map/List node it walks, unconditionally, with no check for whether `#(...)` appears anywhere — and v1 never copied the payload at all. Eight callsites (1051, 1106, 1129, 1377, 1829, 1853, 1867, **3225**). Three traps if revived: `processInlineEmbedded` (`StepExecutor.java:3377`) returns a *new equal String* for every string, which defeats identity-based change detection unless it returns the original when nothing substituted; the XML branch mutates in place so "source intact" holds for Maps and Lists only; and `resolveConfigMap` (3225) has a javadoc promising a defensive copy. Pursue on allocation numbers, not on this issue |
| Call-heavy result-retention workload | The reporter's C–I shape, targeting `ScenarioResult.stepResults` / `SuiteResult.featureResults` unbounded accumulation. Their production heap dump put these at 0.76% of the live set, so it may be a rounding error at realistic scale — but it is untested at other shapes |
| JS-engine workloads | `js-array`, `js-object`, `js-engine-init` built from `EngineBenchmark`'s generators, so JS tuning gets forking, JFR and digests too. Phase 2 |
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table |
| Gatling parity | Null-workload overhead probe, realistic parity sim, throughput ceiling, allocation-rate comparison. Behind `-Pgatling`, launched programmatically via `io.gatling.app.Gatling.fromMap` so the single entry point survives. Absorbs GATLING.md Phase 6 |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest` from karate-core and karate-js, so a recording carries Karate semantics and allocation can be attributed to a *feature line*. **This is a CPU-tuning need, not a memory one, and is not on the critical path for #2972.** The virtual-thread gap is specific to `jdk.ExecutionSample`; everything the memory work relies on — `jdk.ObjectAllocationSample`, `jdk.GCHeapSummary`, `jdk.OldObjectSample`, heap dumps — attributes correctly regardless of thread model. Build these when the question becomes "where is the CPU going during a parallel suite run", which is exactly where `ExecutionSample` goes blind |
| `profiler compare A B` | Side-by-side delta table from two run directories |
| Machine-readable baselines + CI | Committed `baselines/*.json`, a scheduled job, regression thresholds. Explicitly out of scope until the manual playbook has proven itself |
| `jcmd GC.class_histogram` checkpoints | Per-class growth over time. Forces a GC, so it perturbs the measurement |
| GATLING.md Phase 6 | Left untouched. Revisit when the Gatling parity workloads actually land |

# karate-profiling — build-out plan

> Companion to [PROFILING.md](./PROFILING.md) (the runbook — permanent). **This file is
> temporary**: it tracks building the harness and lands the #2972 fix. Retire it when the
> checklists are done, the way [DRIVER_PLAN.md](./DRIVER_PLAN.md) was repurposed once its
> tranches were settled.
>
> Written 2026-08-04. Line references verified against the tree at that date.
>
> **This document now carries the full Phase 6 design**, its sources, its measured numbers
> and a review brief — enough to resume the work cold in a new session without re-deriving
> anything. Start at [Resuming in a new session](#resuming-in-a-new-session).

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

## Phase 1 — The module ✅ DONE (2026-08-04)

> Built and committed (`359916a2c`). Checklist kept as the record of what each decision was for.

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

## Phase 2 — `JfrDigest` ✅ DONE (2026-08-04)

> Built. The heap-dump histogram panel was deliberately **not** implemented — no JDK API or CLI reads an `.hprof`; the digest points at Eclipse MAT instead.

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

## Phase 3 — First-cut workloads ✅ PARTIALLY DONE (2026-08-04)

> `harness-smoke`, `scope-capture-bound`/`-unbound`, `call-accumulation` and `Probe` are built and committed (`0b1f8e370`). **The leak-watch family is NOT built** — still open, see below.

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

## Phase 5 — Retention fix, part 1 ✅ DONE (2026-08-04)

Three commits landed. All of karate-core is green (2483 tests).

| Commit | What |
|---|---|
| `3ffda3fc7` | `HtmlReportListener` stops retaining the full `toJson()` per feature; all three report listeners extract/serialize synchronously instead of racing an async read; `retainCallResults` flag + `CallResultReleaseTest` |
| `596d14155` | Nested call results released at **scenario** end when no listener will read them |

**Measured, machine A, `call-accumulation`, reports OFF, `-Xmx3g`:**

| scenarios | before | after |
|---:|---:|---:|
| 500 | 343 MB | 209 MB |
| 1000 | — | 299 MB |
| 2000 | 859 MB | 272 MB |
| 5000 @`-Xmx768m` | saturated, killed at 12 min | **233 MB, 5.3s** |

External reproducer at `-Xmx768m` / 5000 scenarios / 16 threads: variants **C and E both go
from saturated-and-killed to passing in under 7 s**. Variant J: 2.7 s.

**What is NOT fixed: reports ON.** The release is gated on
`Suite.canReleaseCallResultsAtScenarioEnd()` (`Suite.java:1148-1150`) which returns false
whenever any `ResultListener` is registered — and HTML is one by default. Reports-ON peak
heap is still linear: ~1330 MB @500, ~2550 MB @2000 (`-Xmx3g`). That is Phase 7.

---

## Phase 6 — Bounded-memory reporting (THE NEXT WORK)

**Goal: peak memory O(concurrent scenarios), not O(suite size), with HTML + Cucumber +
JUnit + JSONL all enabled.**

### Why the obvious answer is wrong

v1's design was: write every format at feature completion, keep only a `File` handle,
rehydrate from disk for the summary. Verified: `Suite.saveFeatureResults`
(v1 `Suite.java:264-279`) → `ReportUtils.saveKarateJson` + per-feature HTML render +
cucumber + junit, all synchronous; only `Set<File> featureResultFiles` (v1 `Suite.java:85`)
survives; `getFeatureResults()` (v1 `Suite.java:315-319`) returns a `Stream` that
deserializes one file at a time; `Results` keeps counters + `List<String> errors` +
`List<Map>` summary rows (v1 `Results.java:45-54, 60-107`). Design intent is stated in v1
commit `93562156a`.

**But v1 bounded memory per *feature*, not per scenario.** `FeatureRuntime.processScenario`
does `result.addResult(sr.result)` (v1 `FeatureRuntime.java:190-197`) with no per-scenario
release anywhere; the only `callResults = null` (v1 `ScenarioRuntime.java:540`) is the
per-step buffer reset. So **v1 would fail this benchmark too** — one Scenario Outline with
N rows means feature-end *is* run-end. Copying v1 as-is fixes nothing here.

Also note v1 wrote embed bytes to disk at capture time and held only a `File`
(v1 `ScenarioRuntime.java:166-170`, `Embed.java:39-45`). v2 holds `byte[]` in memory.

### The design

**Spill unit: the scenario. Spill file: one temp file per IN-FLIGHT feature, deleted once
that feature's outputs are written.** Nothing accumulates on disk; concurrently-open files
track concurrent features.

1. **At scenario end**, after listeners have seen the live object — same seam as today's
   `releaseCallResultsIfUnwatched` (`FeatureRuntime.java:374-377, 388-392`; note
   `SCENARIO_EXIT` fires earlier still, inside `ScenarioRuntime.java:1224-1234`) —
   serialize the already-redacted scenario record, append it to the in-flight feature's
   temp spill file, then **unconditionally strip** from the retained skeleton:
   `callResults`, per-step `log`, binary embed `byte[]`. The listener gate goes away.
2. **Retained in memory:** `FeatureResult`/`ScenarioResult`/`StepResult` skeletons (status,
   timing, `Step` ref, failure `Throwable`) plus small per-scenario summary rows for the
   suite-end pages, **built directly** — never via full `toJson()`-then-prune.
3. **At feature end:** assemble the per-feature HTML page, Cucumber JSON, JUnit XML and
   (if enabled) JSONL `FEATURE_EXIT` by streaming the temp file's records back in
   `compareTo` order (`ScenarioResult.java:437-463`) via an in-memory offset index. Write
   outputs, delete the temp file.
4. **At suite end:** summary + timeline from the retained summary rows only. Verified
   sufficient: `buildFeatureSummaryList` and `buildTimelineData` read feature identity /
   status / duration and per-scenario name, refId, passed, skipped, duration, tags,
   start/end, executorName — and never touch `stepResults`.
5. **`SuiteResult.toJson()` becomes summary-only.** This also fixes a real bug:
   `SuiteRunEvent.java:61` returns `result.toJson()` for SUITE_EXIT, which at
   `SuiteResult.java:159-160` serializes **every feature in full** — duplicating every
   FEATURE_EXIT payload and contradicting DESIGN.md:590's "heavy payload lands exactly
   once" rule.
6. **Public JSONL stays an opt-in product surface fed from the spill** — never *becomes*
   the spill. It is a frozen public schema (schemaVersion 1) consumed by IDEs/exts; an
   internal memory mechanism must not be coupled to it. There is currently **no reader** of
   `karate-events.jsonl` anywhere in the repo — `HtmlReportWriter.writeReports`' comment
   about "both JSON Lines and SuiteResult paths" is aspirational; its only caller is
   `write(SuiteResult…)` (`HtmlReportWriter.java:134-151`), which has no production callers.

### Six amendments — all load-bearing, all verified

1. **Spill record = scenario `toJson()` + a stack-trace field on failed steps.** JUnit
   renders `getStackTrace(sr.getError())` (`JunitXmlWriter.java:289-295, 322-325`) but
   `StepResult.toJson()` emits only `error.getMessage()` (`StepResult.java:246-248`).
   Without this every JUnit failure silently loses its stack.
2. **Hold the last-completed scenario live until feature end, and assemble by MERGING**
   spill records with any in-memory result that was never spilled. Two reasons, both
   verified: `invokeAfterFeatureHook` (`FeatureRuntime.java:214-216`) on failure calls
   `appendHookFailure` (`:502-507`) which does `addStepResult` + `setEndTime` on the
   last-executed scenario **after** it was spilled and stripped; and synthetic
   iteration-error scenarios are created at feature level (`FeatureRuntime.java:209-210`,
   `:287-291`) and never pass through the scenario seam at all.
3. **Exempt JSON-mime embed parts from the strip.** They are deliberately kept inline
   (`HtmlReportWriter.java:245-247`) as the structured-evidence carrier for exts
   (`openapi-match`, `grpc-match` — DESIGN.md:598). Blanket-nulling embed bytes breaks
   those pipelines silently. Strip **binary** parts only, after externalising.
4. **Cucumber must read externalised embed bytes back from `embeds/`.** It inlines base64
   today and skips data-less parts (`CucumberJsonWriter.java:358-370`); it also walks
   `getCallResults()` for synthetic call steps (`:253`) and base64s step logs (`:345-352`).
   Ships in the same commit as the embed change or Cucumber output regresses invisibly.
5. **Build `FEATURE_EXIT` by splicing pre-serialized spill strings** into the envelope —
   never materialize the feature as one Map and `Json.stringifyStrict` it. That whole-feature
   String is one of the transients being removed. In-process `RunListener`s receiving the
   event see a skeleton; hydrate from spill on demand if `toJson()` is called.
6. **"O(threads) temp files" is a SOFT bound.** `Suite.runParallel` submits one virtual
   thread per feature immediately (`Suite.java:813-834`) against an unfair
   `Semaphore(threadCount)` (`:809`), so worst case is O(features started − finished).
   Mitigate with lazy file creation + open-append-close so *file descriptors* stay O(1);
   bounding feature dispatch is optional hardening.

### Rejected alternatives (do not re-derive)

- **No spill, fully incremental writers.** Scenarios complete out of order and all three
  writers guarantee `compareTo` order, so this needs an unbounded reorder buffer — the
  memory problem reintroduced through the back door. Also `FEATURE_EXIT` would have no
  whole-feature data, and a crash leaves syntactically broken output files per format.
- **Persistent per-feature spill files (v1 style).** Rejected by the maintainer as file
  explosion; the temp variant also cleanly avoids read-while-write, since all of a feature's
  appends complete before assembly opens the file.
- **One suite-wide spill file.** Considered; loses the write-then-read separation and gains
  nothing once skeletons stay in memory.

### Sequencing, with honest expected outcomes

| # | Work | Expected effect on the benchmark |
|---|---|---|
| 1 | Remove transient giants: `HtmlReportListener.summaryJson`'s full-`toJson()`-then-prune (`HtmlReportListener.java:168-185`), the full page-model map held in the executor closure (`:130-145`), and the whole-feature Strings queued by the cucumber/junit listeners | reports-ON ~3026 MB → ~900 MB @2000. **Still linear** |
| 2 | Embed externalisation: make `Part.data` non-final (`StepResult.java:374`), write bytes at capture or scenario end, null the array, move it out of `HtmlReportWriter` so it runs format-independently — **plus** amendment 4 | **Zero** on this benchmark (a 60-call workload has no embeds). Required spill-prep; bounds UI/driver suites |
| 3 | Scenario spill + strip + merge-at-assembly (amendments 1, 2, 5, 6) | reports-ON goes **flat**, ~270 MB |
| 4 | Summary-only `SuiteResult.toJson()` + the SUITE_EXIT duplication fix | Removes a second full serialization of the whole suite when JSONL is on |

Empirical attribution for reports-ON retention (`--gc-roots`, 2000 scenarios, by allocating
site): `StringUtils.formatRecurse` 57.5%, `HtmlReportWriter.buildFeatureData` 12.8%,
`buildStepData` 12.2%, `inlineJson` 11.9%, `renderFeatureHtml` 4.7%. Read as a ranking, not
as retained sizes — `OldObjectSample` weighting is a proxy.

### What breaks, and the compat story

- Custom `ResultListener.onFeatureEnd` sees a **stripped** result (logs, call trees, binary
  embeds gone; statuses, timings, failure Throwables, JSON embeds intact). `onScenarioEnd`
  still sees everything. Needs a loud MIGRATION_GUIDE entry.
- `retainCallResults(true)` (`Runner.java:248, 523`) widens to mean "retain full detail" —
  consider renaming to `retainResultDetail`.
- `SuiteResult.getFeatureResults()` keeps returning real objects. `printSummary`
  (`SuiteResult.java:216-275`), `getErrors()` (`:366-376`) and the junit6 bridge
  (`JUnitBridgeListener.java:94`) read only skeleton fields — unaffected.
- Tests asserting `getCallResults()` / step logs post-run flip to the retain flag;
  `TestUtils.createTestSuite` already opts in.
- `HtmlReportWriter.write(SuiteResult…)` would render hollow step detail — deprecate or gate
  it rather than leave a public entry point producing empty reports.
- Exclude `exampleData` from retained summary rows (it rides in via
  `ScenarioResult.java:373-379`) or fat data-driven outlines re-inflate the accumulation.

### Open questions for the next review

- **Mega-features.** Retained step skeletons are ~80 bytes each: 100k steps ≈ 8 MB (fine),
  3M steps (50k generated scenarios × 60 steps) ≈ 240 MB (not fine). Proposal: keep
  skeletons by default, add a flag to drop passed-step skeletons if it bites. Is that the
  right call, or should summary rows replace skeletons outright from the start?
- **Called-feature `FEATURE_EXIT` duplication.** `FeatureRuntime.java:222-224` fires the
  event unguarded while `notifyListeners` is `isTopLevel()`-gated (`:152-161`), so a nested
  call's tree goes on the wire twice — once as its own FEATURE_EXIT, once inside the
  caller's `callResults`. Thin the payload for `callDepth > 0`, or document that consumers
  filter? Decide while the schema is being touched.
- **Per-feature HTML page for a mega-feature** is inherently enormous (all scenarios inlined
  as JSON). Pagination is a product question, deliberately out of scope here.
- **Disk-full / spill write failure** must degrade per scenario (keep that one un-stripped,
  warn once), never fail the run.
- **Orphan temp files** after a hard kill: put them under `outputDir/karate-json/` and sweep
  stale ones at suite start, reusing the existing backup/truncate flow (`Suite.java:1033-1051`).

---

## Phase 7 — Prove it

- [ ] `call-accumulation` sweep at 500 / 1000 / 2000 scenarios with **reports ON**: the
      peak-heap line must go flat, matching the reports-OFF curve (~270 MB @2000).
- [ ] Same sweep with reports OFF must not regress from its current 209 / 299 / 272 MB.
- [ ] External reproducer variants C and E still pass at `-Xmx768m` / 5000 scenarios, with
      **every** report format enabled (they currently run with reports off).
- [ ] A run with HTML + Cucumber + JUnit + JSONL all on produces byte-identical report
      content to a pre-change run of the same suite — the whole point is that only *when*
      data is held changes, not *what* is reported.
- [ ] `leak-watch --duration 10m` before and after — heap-after-GC floor flat in both.
- [ ] `mvn test` across all modules green.
- [ ] Update the runbook's baseline table with machine and child JDK.

---

## Running one more design review

The Phase 6 design has already had two adversarial passes. Both changed it materially — the
first established that v1 bounded per *feature* not per scenario (so copying v1 fixes
nothing here), the second found two silent-data-loss defects (JUnit stack traces, the
`appendHookFailure` mutation). A third pass should be aimed at what those two did **not**
cover, or it will just re-derive them.

Paste this as the brief:

> Review the Phase 6 design in `docs/PROFILING_PLAN.md` (repo `/Users/peter/dev/zcode/karate`;
> v1 for comparison at `/Users/peter/dev/ycode/karate`). The design and every code citation
> in it are in that document — verify the citations rather than trusting them. Two prior
> reviews already established: (a) v1 bounded memory per feature, not per scenario; (b) the
> six amendments listed. Do not re-litigate those; attack what they missed.
>
> Specifically:
> 1. **Correctness of the strip.** Walk every consumer of `StepResult.log`,
>    `StepResult.callResults` and `Embed.Part.data` in karate-core, karate-junit6 and
>    karate-gatling. Is there any reader between scenario end and suite end that the design
>    does not account for? Include `@report=false`, `continueOnStepFailure`, `@fail`, retry,
>    driver teardown, `karate.abort()`, suite-abort, and the mock server.
> 2. **Ordering and merge.** The design assembles a feature from spill records plus
>    never-spilled in-memory results. Enumerate every way a `ScenarioResult` can reach a
>    `FeatureResult` and confirm each is either spilled or merged. Outline expansion,
>    dynamic `@setup` outlines, and called features are the likely gaps.
> 3. **Concurrency.** Scenarios of one feature run on many virtual threads and append to one
>    temp file. Specify the exact synchronization, and prove the offset index cannot be
>    corrupted or interleaved. Consider a scenario failing mid-serialize.
> 4. **Is the spill needed at all for HTML specifically?** HTML's per-feature page is one
>    template + one inlined JSON blob. Could it be stream-spliced scenario-by-scenario at
>    scenario end, removing HTML from the spill path entirely and leaving spill only for
>    Cucumber/JUnit/JSONL? What would that cost in report fidelity?
> 5. **Numbers.** The design predicts ~900 MB @2000 after step 1 and ~270 MB flat after
>    step 3. Sanity-check those against the attribution table in Phase 6. If they are
>    optimistic, say by how much and why.
> 6. **Anything that makes this not worth doing** — a simpler change that gets most of the
>    win, or a reason the whole approach is wrong.
>
> Be blunt, cite file:line, do not edit files.

---

## Resuming in a new session

1. Read this file, then [PROFILING.md](./PROFILING.md) §6 for the current baseline numbers.
2. Reproduce the starting point:
   ```bash
   cd karate-profiling
   etc/run.sh call-accumulation --iterations 2000 --xmx 3g -Dkarate.profiling.reports=true
   etc/run.sh call-accumulation --iterations 2000 --xmx 3g          # reports off, for contrast
   ```
   Expect ~2550 MB with reports on, ~272 MB with them off. The gap is Phase 7's target.
3. Start at Phase 6 sequencing step 1 (transient giants). Each step is independently
   verifiable — re-run the sweep after each and record it in the runbook before moving on.
4. `mvn -o test -pl karate-core` must stay green (2483 tests) at every step.

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

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

**1. Issue #2972 is a real, unfixed memory bug.** A user hits `OutOfMemoryError` under
parallel execution with `karate-junit6:2.0.10`. Their first diagnosis — unbounded
`ScenarioResult.stepResults` / `SuiteResult.featureResults` accumulation — they later
overturned themselves with a MAT heap dump: 89% of a 3.87 GB live heap sat in the **stack
locals of a 13-deep self-recursion of `StepExecutor.processEmbeddedExpressions`**, each
frame holding a `LinkedHashMap` roughly half the size of the one above it
(1.08 GB → 798 MB → 407 MB → 205 MB → …). That geometric decay is the tell: level N
*contains* level N+1. Live, mid-copy — not stranded in a stale cache.

Reading the code confirms the mechanism. `StepExecutor.evalKarateExpression`
(`StepExecutor.java:1859-1869`) is the *fallback* branch — it fires after `call`,
`$jsonpath`, `get`, XML literals, xpath and JSON literals have all been ruled out, i.e.
for **every `* def x = <plain expression>` whose result is a Map or List**:

```java
Object value = runtime.eval(wrapJsonLikeExpression(expr));
if (value instanceof Map || value instanceof List) {
    value = processEmbeddedExpressions(value, true);   // unconditional deep copy
}
```

`processEmbeddedExpressions` (`StepExecutor.java:3254-3296`) rebuilds a fresh
`LinkedHashMap`/`ArrayList` for every Map/List node it walks — **unconditionally**, with
no check for whether a `#(...)`/`##(...)` string appears anywhere in the value. Combined
with a bare `karate.call('f.feature')` (no args object), which runs the callee in the
caller's scope and returns that scope wholesale, each bound capture contains every
previous capture — so the copy cost is geometric in capture count, not linear in call
count. The reporter measured 768 MB saturated / OOM at 8 s (13 bound captures) against
331 MB / 14 s passing (same 13 calls, captures nulled).

Two supporting facts found while reading:

- **v1 never copied the payload.** v1's `ScenarioEngine.recurseEmbeddedExpressions`
  mutated the tree in place and returned `null` to mean "nothing changed" — the same
  reference came back. It was not allocation-free (a `Variable` wrapper per node, a
  `HashSet`/`ArrayList` per container, and it *did* copy a List when `##()` removed an
  element — v1 `ScenarioEngine.java:1427-1434`), but that garbage is small and fixed-size,
  never payload-sized. **v2's payload copy is the regression; the aliasing is not.**
- **The copy already needed an escape hatch.** `configure headers`/`cookies` routes around
  it via `evalConfigReferenceExpression` (`StepExecutor.java:2991+`; the routing block and
  its rationale are at 2969-2989) precisely because "evalKarateExpression rebuilds Maps via
  processEmbeddedExpressions, which detaches the value into a snapshot" — and that detach
  broke v1 parity.

**2. There is no way to demonstrate or measure any of this.** v1 had
`examples/profiling-test` (a headless `Main.java` loop plus a Gatling
`TestSimulation.scala`), which was useful for finding bottlenecks and hotspots. v2 has
nothing equivalent.

**3. [GATLING.md](./GATLING.md) Phase 6** has open TODOs to compare karate-gatling against
vanilla Gatling and to port v1's profiling-test. Same machinery, so it eventually folds in
here — but Gatling workloads are **not** in the first cut, and GATLING.md is left untouched
for now.

**Order is deliberate: harness and runbook first, demonstrate the problem, then fix.**
The fix is not attempted until the harness has independently reproduced the reported
behaviour and the digest points at `processEmbeddedExpressions` on its own evidence.

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
| Outcomes | Always report, never assert | OOM is the *expected* outcome for `scope-capture-bound` pre-fix. Assertions and baselines can come later |
| Baselines / CI | None. Runbook holds the numbers | No CI job, no committed baseline files, no automation. LLM-assisted manual playbook to start |
| GC | G1 default, `--gc zgc` opt-in | G1 is what most users get; ZGC reproduces the reporter's environment. Expansion is JDK-dependent — see runbook |
| Repro fidelity | Native workloads, cross-checked **once** against the reporter's repo | Ours to maintain, but validated — if ours doesn't OOM where theirs does, ours is wrong |
| JS-engine workloads | Deferred to phase 2 | Keep the first cut on the problem that motivated it |
| Fix shape | Copy-on-first-change | v1's payload-allocation behaviour without v1's in-place-mutation hazard |
| Fix scope | All **eight** callsites, one change | The walker itself becomes identity-preserving, so they all benefit |
| Issue write-up | Commit message + GitHub issue only | No case-study section in the docs |

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

## Phase 4 — Demonstrate the problem

- [ ] Run `scope-capture-bound` and `scope-capture-unbound` under JFR.
- [ ] Record both in the runbook's baseline table, with machine and child JDK.
- [ ] **Gate:** do not start Phase 5 until the digest independently fingers
      `processEmbeddedExpressions`.

---

## Phase 5 — The fix

**Copy-on-first-change** in `StepExecutor.processEmbeddedExpressions`
(`StepExecutor.java:3254-3296`). A node returns **itself** when nothing beneath it
changed; a new `LinkedHashMap`/`ArrayList` is built only along paths that actually
contained a resolvable `#(...)`/`##(...)`.

```
clean tree (the overwhelming majority of steps):
  v1        → same ref, small fixed-size garbage
  proposed  → same ref, no garbage                     ← at least as good as v1

Map/List containing #(x):
  v1        → same ref, SOURCE MUTATED
  proposed  → new nodes on that path, source Map/List intact
```

```java
Map<String, Object> out = null;                       // created lazily
for (var e : map.entrySet()) {
    Object p = walk(e.getValue(), lenient);
    if (out == null && p != e.getValue()) {
        out = new LinkedHashMap<>(map);               // copy on first change
    }
    if (out != null) { … }                            // incl. REMOVE_MARKER handling
}
return out == null ? value : out;                     // identity when clean
```

### Callsites — there are eight, not seven

| Line | Method | Note |
|---|---|---|
| 1051 | `executeJson` | **Not literal-only** — `* json foo = someMapVar` accepts an arbitrarily large variable-referenced tree |
| 1106 | `executeString` | stringified afterwards |
| 1129 | `executeBytes` | byte-ified afterwards |
| 1377 | `evalMatchExpression` | inline JSON literal |
| 1829 | `evalKarateExpression` | inline XML literal |
| 1853 | `evalKarateExpression` | inline JSON literal |
| 1867 | `evalKarateExpression` | **the geometric case** — JS eval result, `lenient=true` |
| **3225** | **`resolveConfigMap`** | **was missed in the first draft — see below** |

- [ ] Preserve exactly: `REMOVE_MARKER` handling for `##()`, the `lenient`
      fail-fast-vs-defer contract, the `runtime.isRequestDerived` short-circuit, `Node`
      (XML) in-place handling, `JavaCallable` pass-through.

### Three correctness traps in the sketch

1. **`processInlineEmbedded` breaks identity for every string.**
   `StepExecutor.java:3377` builds a `StringBuilder` unconditionally and returns
   `.toString()`, so it hands back a **new, equal** String even when nothing substituted.
   Under identity-based change detection that marks every string node as changed, which
   marks every containing Map/List as changed, which copies the whole spine — on exactly
   the lenient call-result path the fix targets. **It must return the original `str`
   reference when no substitution occurred.** Without this the fix does nothing.
2. **The XML branch mutates in place** (`StepExecutor.java:3264-3266`): it walks a `Node`,
   rewrites it, and returns the same reference. So a Map containing XML can report
   "unchanged" while its XML child *was* mutated. That is pre-existing behaviour and is
   preserved, but the "source intact" guarantee above applies to Maps and Lists only —
   don't state it more broadly.
3. **List removal needs index bookkeeping.** The Map case can copy-then-overwrite; the
   List case must skip `REMOVE_MARKER` entries while copying, so the lazily-created copy
   is built by appending survivors, not by `set(i, …)`.

Also: an explicit `p == REMOVE_MARKER` test in the change check is redundant —
`REMOVE_MARKER` is a private sentinel that can never equal an entry's existing value, so
`p != e.getValue()` already covers it.

### `resolveConfigMap` (line 3225) — the callsite whose contract depends on the copy

Its javadoc (`StepExecutor.java:3216-3222`) promises the config map "is never polluted by
per-request header/cookie additions", and that promise currently rests on the copy. Under
copy-on-first-change, a headers map with no embedded expressions comes back as the **same
live reference the user configured**.

Verified safe today: `HttpRequestBuilder.headers` (`HttpRequestBuilder.java:273-281`)
copies entries out, and `applyCookiesFromMap` (`StepExecutor.java:1909-1913`) only reads.
So no current consumer mutates it.

- [ ] Update the 3216-3222 javadoc to state the real invariant — the map is not copied
      defensively, and consumers must not mutate it — so a future consumer doesn't
      silently start polluting user config.

### Behaviour change, accepted

Returning the original reference for clean trees means `* def b = a` aliases (as it did in
v1), and a Map/List result of a JS eval is no longer silently detached into a plain
`LinkedHashMap` — the variable holds whatever the engine returned (e.g. a `JsObject`,
which is itself a `Map`). Watch for fallout in serialization, `match` and equality.

`configure headers`/`cookies` routes around the copy via `evalConfigReferenceExpression`
(`StepExecutor.java:2991+`) *because* of the detach. Once the walker preserves identity
that escape hatch may be redundant — verify, and retire it only in a separate follow-up
commit, and only if the tests agree.

- [ ] Full `mvn test` across all modules green. Watch karate-core HTTP/config tests
      (`configure headers`), match/assertion tests, and the karate-gatling smoke simulation.
- [ ] **Recorded fallback:** if aliasing proves load-bearing somewhere, narrow the change
      to lines 1867 and 1051 — the JS-result path (the geometric case) and `executeJson`
      (which also accepts an arbitrarily large variable reference). The remaining six fire
      only on literals the user typed inline at that step, or on values that get
      stringified immediately afterwards.
- [ ] Commit message carries the `#2972` backlink and the before/after numbers. Per house
      practice the issue stays open until the Maven Central release.

---

## Phase 6 — Prove it

- [ ] Re-run `scope-capture-bound` / `-unbound`; both should complete, with allocation
      dominated by something other than `processEmbeddedExpressions`.
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
| Call-heavy result-retention workload | The reporter's C–I shape, targeting `ScenarioResult.stepResults` / `SuiteResult.featureResults` unbounded accumulation. Their production heap dump put these at 0.76% of the live set, so it may be a rounding error at realistic scale — but it is untested at other shapes |
| JS-engine workloads | `js-array`, `js-object`, `js-engine-init` built from `EngineBenchmark`'s generators, so JS tuning gets forking, JFR and digests too. Phase 2 |
| Mock throughput tiers | Raw Java handler vs JS handler vs feature mock, as a floor-and-multiplier table |
| Gatling parity | Null-workload overhead probe, realistic parity sim, throughput ceiling, allocation-rate comparison. Behind `-Pgatling`, launched programmatically via `io.gatling.app.Gatling.fromMap` so the single entry point survives. Absorbs GATLING.md Phase 6 |
| Custom JFR events | `karate.Step` / `karate.Call` / `karate.HttpRequest` from karate-core and karate-js, so a recording carries Karate semantics and allocation can be attributed to a *feature line*. **This is a CPU-tuning need, not a memory one, and is not on the critical path for #2972.** The virtual-thread gap is specific to `jdk.ExecutionSample`; everything the memory work relies on — `jdk.ObjectAllocationSample`, `jdk.GCHeapSummary`, `jdk.OldObjectSample`, heap dumps — attributes correctly regardless of thread model. Build these when the question becomes "where is the CPU going during a parallel suite run", which is exactly where `ExecutionSample` goes blind |
| `profiler compare A B` | Side-by-side delta table from two run directories |
| Machine-readable baselines + CI | Committed `baselines/*.json`, a scheduled job, regression thresholds. Explicitly out of scope until the manual playbook has proven itself |
| `jcmd GC.class_histogram` checkpoints | Per-class growth over time. Forces a GC, so it perturbs the measurement |
| GATLING.md Phase 6 | Left untouched. Revisit when the Gatling parity workloads actually land |

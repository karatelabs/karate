# karate-js Performance Plan — mechanism attribution and the road to closing the Rhino gap

> Status: **Tier 1 shipped and EC2-confirmed; Tier-2 #8 shipped and EC2-confirmed
> 2026-08-16** (status blocks inside §4). Current karate ÷ rhino-best five-row
> geomean: **1.10** — inside Tier 3's do-not-start band.
> Originally the planning/research output of the 2026-08-15 session.
> Evidence: local JFR-on profiling of the `js-*` rows (both engine arms), the R1
> Graviton baseline, a Rhino-interpreted-mode source deep-dive, and a quickjs-ng
> source deep-dive. See [PROFILING.md](./PROFILING.md) §9 R1 for the bench
> baseline and [JS_ENGINE.md](./JS_ENGINE.md) for the architecture this plan
> must respect.
>
> **Revision history.** Round 1 external review (Codex, 2026-08-15): verdict
> *implementable-with-amendments* — one blocker (the builtin-cache guard as
> originally specified was unsound against own-property shadowing and
> per-object `__proto__` swaps), three majors (dense-write guard must reuse
> the existing `specIterate` invariant; `Node.meta` is a tagged union, not a
> free cache slot; `SlotTable.indexOf` misattributed as a linear scan — it is
> a `HashMap` get), plus measurement-protocol tightening. All folded in below.

---

## 1. The instrument: local profiling works, and is enough for attribution

EC2 owns *timing decisions* (the J1 lesson: laptop layout noise swamps ~1–4%
effects). But *mechanism attribution* — which methods burn CPU, which sites
allocate — needs only JFR sample attribution, which is trustworthy locally on
platform threads. The protocol used here, repeatable in ~1–2 min per cell:

```sh
cd karate-profiling
jar=$(etc/js-arm.sh HEAD)
etc/run.sh js-mixed --js-jar "$jar"  --run-tag prof:p1:a     # karate arm, JFR on
etc/run.sh js-mixed --engine rhino-best --run-tag prof:p1:b  # rhino arm, JFR on
# read digest.md → Hot methods + Allocation by site panels
```

Cross-check that the local machine reproduces the bench: karate ÷ rhino-best
elapsed on this M1 Pro (JFR on both arms, single runs — directional):

| row | local ratio | Graviton bench (R1, 4 pairs) |
|---|---:|---:|
| js-mixed | 1.80 | 1.771 |
| js-strings | 1.84 | 1.686 |
| js-arithmetic | 1.83 | 1.588 |

Ordering and magnitude reproduce. **Conclusion: short-lived local runs are a
fully adequate instrument for deciding *what* to fix; the EC2 4-pair matrix
remains the acceptance gate for *whether a change moved the number*.**

---

## 2. What the profiles say — the gap, attributed

Four karate-arm cells (mixed / strings / arithmetic / functions) and two
rhino-arm cells (mixed / strings / arithmetic). Headline: **karate-js loses in
the runtime, not the parser** — Rhino's own parse is allocation-heavier
(51% of its mixed-row allocation is AST `ast.Name` nodes), yet it wins every
row because its interpreted runtime is nearly allocation-free.

### A. Scope/binding machinery for non-function code — the #1 cost

Slot frames exist **only for function bodies** (`JsFunctionNode.java:249`).
All top-level code — the whole of `js-arithmetic`/`js-strings`, the driver
loops of `js-mixed`/`js-objects`, and every real-world script's top level —
runs on the name-keyed `BindingsStore`:

- every block entry allocates a `ScopeEntry` (`CoreContext.enterScope`), every
  per-iteration `let` re-binding a `BindingSlot`;
- every block **exit** calls `BindingsStore.popLevel`, which **iterates the
  entire binding map** (`BindingsStore.java:259-273`) and allocates a
  `HashMap$EntryIterator` — per loop iteration;
- every identifier read is a `HashMap` lookup (`readSlot`/`getSlot`), and
  `PropertyAccess.getRefExprByName:581` does `hasKey(name)` **then**
  `get(name)` — two resolution passes for name-keyed reads that reach this
  tail (slot-annotated function identifiers bypass it; `this`/`arguments`/
  frame hits have fast paths — so the win concentrates on top-level rows).

Measured share, arithmetic row: `popLevel` 19.5% CPU + `readSlot` 12.4% +
`getSlot` 8.0% + `pushBinding` 3.8% ≈ **44% of CPU**; the same family is
~39% of allocation. Strings row: `popLevel` alone is **28% of CPU**.
Rhino/quickjs both resolve locals to array indices at compile time; scope exit
is a frame pop.

### B. Call-path allocation — the functions/mixed tax

Per JS-to-JS call (functions row: `enterScope` **47.2% of allocation**,
`invokeCallable` 14.6%, `evalCallArgs` 13.1%):

- args are materialized **three times**: `evalCallArgs` builds an `ArrayList`
  (Interpreter.java:1194), `invokeCallable` copies it into a second
  `ArrayList` (:656), then `toArray()` makes a third `Object[]` (:673);
- `PropertyAccess.getCallable` allocates an `Object[2]` callable/receiver
  tuple per call (:201, :703-707);
- `Interpreter.isFunctionDeclarationStatement` (:945) is re-derived **twice
  per statement per block entry** (:495, :930) — 7.0% of CPU on the functions
  row — for a fact fully determined at parse time;
- `SlotTable.indexOf` shows 5.4% CPU — it is a `HashMap<String,Integer>.get`
  (SlotTable.java:145-148), so the cost is String hashing/equality + boxed
  `Integer`, i.e. name-keyed frame lookups happening at eval time where a
  parse-time slot annotation (`node.slot`) should already have the answer.
  Profile the callers before picking a remedy.

Rhino: exact-size frames precomputed at compile time, lazy activation and
`arguments` objects, no arg re-copying.

### C. String concatenation is O(n²) — owns the strings row

`Terms.add` → `concatOperand(lhs) + concatOperand(rhs)` (Terms.java:703): a
fresh immutable `String` per `+`. The `s += 'item' + i + ','` loop copies the
whole accumulated string every iteration: **49.6% of the strings row's
allocation (27 GB of `byte[]`)**. Rhino's fix is `ConsString` — an O(1)
two-pointer cons cell, lazily flattened, flowing through the runtime as
`CharSequence`. quickjs-ng added ropes too. This is the dominant mechanism
behind strings 1.69×, and string building is a real Karate workload shape
(JSON assembly, report text).

### D. Array element writes stringify the index and walk the spec path

`JsArrayPrototype.push` (:625) does `specSet(target, String.valueOf(len+i), …)`
— every push allocates the index **as a String** and routes through the
generic proto-chain-aware Set so that a setter installed on
`Array.prototype["0"]` would fire. Mixed row: **39% of all allocation** in
`push`, and `String` is 37.6% of allocated bytes in a workload with almost no
string work. This inverts TEST262.md working principle #4 (pay edge-case cost
on the edge case). Reads are also double-lookup (`getByName:810` does
`containsKey` then `getMember`).

### E. Built-in method resolution walks the chain on every call

`arr.push` per call: `JsArray.getMember` → namedProps miss → `"__proto__"`
check → `resolveOwnIntrinsic` → `Prototype.getMember` → `userProps()` →
`resolveBuiltin` → `LinkedHashMap.get` → `LazyRef` volatile read — **no
per-callsite or per-object cache**. `Prototype.resolveBuiltin` is 8.1% of CPU
on the mixed row. `Node.meta`/`Node.slot` already exist as the natural home
for a monomorphic per-AST-node cache.

### F. Boxing policy

`Terms.narrow` (Terms.java:739) boxes every integral arithmetic result to
`Integer`/`Long` (fresh object beyond the −128..127 cache) and pays an fmod
(`d % 1`) per op — 6.5% of arithmetic-row allocation. Note Rhino's arithmetic
row is itself 78.8% `Double` allocation and it still wins by 1.8× — boxing is
a real but *secondary* lever; scope machinery (A) dominates that row.

### G. Parse-side churn — real, but not the gap

Parse is ~30–44% of karate-arm allocation on these 6-line scripts:
speculative `new Node(type)` on every `BaseParser.enter` including alternatives
that rewind (`exit(false)` discards them — the parser's dominant garbage),
`Token`/`TokenBuffer` arrays, a fresh `ArrayList` per comment group. Rhino
pays comparably (its AST is *heavier*). Worth a diet pass, but it is not what
separates the engines. Note the parse-once/eval-many seam **already exists**
(`Engine.parse(String)` / `eval(Node)`) — relevant to the karate-core lane
(PROFILING.md "Parsed-JS reuse": a measured ceiling of ~⅓ of suite allocation,
parked for design reasons that are about karate-core's `Step` model, not the
engine).

---

## 3. What Rhino and quickjs-ng teach (and what they don't)

Full reports in session log; the durable conclusions:

- **Rhino interpreted mode is not a tree-walker.** It compiles the AST once to
  flat Icode with side tables; locals are byte indices; dispatch is one array
  index into a handler table; doubles live on a parallel `double[]` stack and
  survive calls unboxed (`CallFrame` copies both stacks). Roughly **half** its
  advantage is structurally tied to that linearization (unboxed dual stack,
  fused superinstructions); the **other half is transferable to a tree-walker**:
  ConsString, dense arrays that never stringify integer indices, interned-name
  identity compares, embedded slot maps, lazy activation/`arguments`, and a
  one-shot resolve pass that bakes slot indices + decoded constants +
  operator handlers into the tree.
- **quickjs-ng's ranked transferables:** (1) compile-time slot resolution of
  locals/args — never look up a local by name at runtime; (2) closure capture
  as per-variable cells (capture only what's captured, close cells on frame
  exit) instead of retaining scope maps; (3) prototype-purity flags — a bit
  that says "no user props on this prototype" lets lookup short-circuit to the
  immutable singleton (karate-js already has *both* halves: the JVM-wide
  monotonic `anyUserProps` and the per-Engine `Engine.numericPropPolluted` /
  `Prototype.isNumericPropPolluted()` used by `specIterate`'s dense read
  path — the transfer is extending their use, not inventing them);
  (4) numeric-string keys normalized to integer indices at the boundary, so
  array paths never see `"3"`. Notably, **quickjs-ng removed its inline
  caches** — shapes alone were judged enough at its complexity budget; and
  hidden-class shapes themselves are a *weak* fit for karate-js
  (fresh-Engine-per-eval + `JsObject`-as-`LinkedHashMap` for Java interop).
- Neither engine's headline trick (NaN-boxing; computed-goto dispatch) has a
  useful Java analogue — the JVM gives us those (references, tableswitch)
  or forbids them.

---

## 4. The plan

Three tiers. Every item independently measurable on the local rows, gated by
the full net (1265+ unit tests, `test/language/**` zero-regression diff,
`EngineBenchmark profile` ±10%, karate-core 2550+), accepted on the EC2
4-pair matrix per the J1 protocol (arithmetic-row host qualification first:
same-jar null must resolve sd ≲ 1%). Behavior-preserving refactors and perf
changes in separate commits (TEST262.md principle #9 discipline).

### Tier 1 — surgical wins, high confidence (each hours, not days)

> **Status 2026-08-15 — items 1–6 SHIPPED** (commits `b0445d2f8`,
> `b1581e948`, `34c83b6f8`, `7f0713194`), plus a seventh micro-item found by
> the post-batch re-profile: `Terms.narrow` autobox/fmod removal
> (`7d69db6aa`). Item 7 (builtin cache) is **deferred on evidence**: after
> items 1–6, `Prototype.resolveBuiltin` no longer appears in the mixed row's
> top CPU lines. Every commit passed the full net (1483 unit tests,
> `test/language/**` byte-identical FAIL set, `built-ins/Array/**`
> byte-identical for the push change, smoke 53/56, karate-core 2573 green,
> EngineBenchmark within noise). Local 2-pair tag-paired screens, base
> `555f5b5e4` vs `7f0713194` (M1 Pro, --no-jfr, lead alternated):
> mixed **−17%**, objects **−13.6%**, arithmetic **−12.6%**, strings
> **−10.6%**, functions **−4.5%**, large-1k guard **−5.2%** (improved) —
> five-row geomean **≈ −12%**. **EC2-confirmed 2026-08-16** (single
> `c7g.4xlarge`, 4 pairs, JFR off, `$KP_RESULTS/tier1-ab/`): mixed
> **−19.7 ± 1.6**, functions **−19.5 ± 4.6**, objects **−13.0 ± 2.1**,
> arithmetic **−11.7**, strings **−9.6**, guard **−5.4 ± 0.7** (improved) —
> **five-row geomean −14.8%** (the M1 screens *underestimated* functions).
> Same-session R2 head-to-head vs rhino-best: five-row geomean **1.52 →
> 1.29** (per-row figures in PROFILING.md §9 R1). Post-batch profile state: arithmetic is now owned by the remaining
> name-keyed scope machinery (`readSlot`/`getSlot`/`pushBinding`/
> `enterScope` ≈ 46% CPU, 58% alloc — exactly Tier-2 #8's target); mixed's
> top allocation line is now parse-side `TokenBuffer.getText` (41%) with
> `JsObject.put` second (19%) — Tier-2 #11 rises in priority; a new
> visible cost is `JsArray.parseIndex` via `getByName`'s un-fused JsArray
> branch (`isOwnProperty` + `getMember` double work, ~5% CPU on mixed) —
> fuse it the way the JsObject branch was fused, minding the literal-null
> own-value preservation the branch comment documents.
> *That fuse shipped 2026-08-16 (`0fa5fb85d`), semantics pinned against the
> Array and defineProperty test262 slices.*

Ordered by expected value ÷ risk:

1. **`popLevel` without the full-map walk.** Track a per-level chain
   (`BindingSlot` already has `previous`; add a per-level head list or an
   intrusive next-in-level pointer) so block exit touches only that level's
   slots and allocates nothing. Kills ~20-28% CPU on arithmetic/strings plus
   the `EntryIterator` churn. Pure implementation detail — but the level
   chain must reproduce today's semantics exactly: `BindingSlot.previous`
   shadow restoration, initialization/TDZ state, eval-ids, and hidden
   bindings all survive pop identically (pin with tests before switching).
2. **Single arg materialization.** `evalCallArgs` fills one `Object[]` sized
   from the AST (spread is the rare case — count at parse or fall back to a
   list only when a spread is present); delete the defensive copy and
   `toArray`. Kills most of `invokeCallable`+`evalCallArgs` (28% of
   functions-row allocation).
3. **Parse-time statement flags.** `isFunctionDeclarationStatement` (and any
   sibling shape probes) become parse-time facts; `evalBlock` reads a bit.
   ~7% CPU on functions row. Constraint: **do not grow `Node`** — J1
   attributes a measured structural regression to `Node` footprint growth.
   Encode in existing storage (a `NodeType` variant, a bit in an existing
   short/byte field, or a child-index list on the *block* node only).
4. **Kill the double lookups.** `getByName` containsKey+getMember →
   single-pass; `getRefExprByName` hasKey+get → resolve once and use the
   slot. Mechanical.
5. **Dense-array write fast path.** The engine *already has* the read-side
   precedent and the guard vocabulary: `JsArrayPrototype.specIterate`
   (:315-330) fast-paths exact-class `JsArray` with no own descriptors, the
   standard prototype, and `!Prototype.isNumericPropPolluted()` (the
   per-Engine numeric-pollution flag, `Engine.numericPropPolluted`). Extend
   that exact invariant to writes: `push`/indexed store additionally require
   **writable length** and an **extensible / non-sealed** receiver, then go
   straight to `list.add`/`list.set` — no `String.valueOf(index)`, no spec
   walk — preserving the final length-Set behavior and failure ordering.
   `defineProperty`, `freeze`/`seal`, length manipulation, own numeric
   accessors, and `__proto__` replacement all degrade to the spec path.
   ~39% of mixed-row allocation.
6. **Callable tuple diet.** Return receiver via a per-`CoreContext` scratch
   field (single-threaded under `jsLock`) or restructure `getCallable` to two
   calls; removes an `Object[2]` per call. Re-entrancy contract: the scratch
   must be written *after* any resolution step that can run user code
   (getters, `toPrimitive`) and consumed immediately with no intervening
   callback; the two-call alternative must not re-evaluate the base
   expression or a computed key.
7. **Per-callsite builtin method cache — with a sound guard.** The per-call
   chain walk (`resolveBuiltin`, 8.1% CPU on mixed) is cacheable, but
   **receiver class alone is not a valid key**: an own property
   (`a.push = fn`) or a per-object `__proto__` swap changes resolution
   without touching any prototype overlay or monotonic flag. Sound guard for
   a cache hit: exact receiver class **and** receiver has no own slot for the
   name **and** receiver's prototype is the standard singleton **and** the
   overlay purity flag still holds — i.e. the same shape of invariant as
   item 5, checked per call (each check is cheap; the win is skipping the
   two-map, multi-hop resolution, not the checks). Cache only immutable
   `JsBuiltinMethod` results — never `AccessorSlot` / `ConstructorRef`
   resolutions, which are Engine-dependent. Storage: **not** `Node.meta`
   (it is a tagged union owned by SlotTable — `SlotTable`/`DEFERRED`/`BAIL`/
   rearm `int[]`); use a dedicated side structure or a composite meta object
   designed with SlotTable, and measure `Node` footprint if any field is
   added (see item 3's constraint).

Estimated combined effect (attribution shares, not additive promises):
arithmetic and strings rows lose their top CPU line, mixed loses its top two
allocation lines, functions loses ~40% of its allocation. Plausibly moves the
five-row geomean by a double-digit percentage. Measure, don't trust.

### Tier 2 — structural, designed-and-reviewed items (days each)

> **Status 2026-08-16 — item 8 SHIPPED** (commit `590539fd8`), scoped per the
> round-1 amendment: frames only for let/const confined to top-level blocks
> and C-style for-inits — the program body's own declarations (var, function,
> top-level let/const) stay in the store, which is the `Engine.bindings` host
> contract, and the table's `byName` stays empty so no name-keyed path routes
> to the frame. Analysis is gated on a top-level loop and cached on the
> PROGRAM node (BAIL permanent — a program has no second-call hook), so
> loop-free programs (karate-core per-step expressions, the large-1k IIFE)
> pay one shallow walk per parsed AST; the frame is per-eval. External
> (Codex) review of the commit: **ship-worthy, zero findings survived
> verification**; its omission list is pinned in `ProgramFrameTest`
> (24 tests, `677ef15fe`), including the one deliberate observable change —
> indirect `eval()` no longer sees a loopy top-level block let mid-block (the
> legacy shared-store leak-through; the same shape in a function already read
> undefined, which is also what the spec says — the kill switch restores the
> legacy read, and the test asserts both modes).
>
> **EC2 acceptance** (single `c7g.4xlarge`, 4 pairs, JFR off, base
> `2a35d314c` vs `590539fd8`, tables in `$KP_RESULTS/t2-ab/`): arithmetic
> **−31.5 ± 2.3**, strings **−25.9 ± 1.3**, objects **−13.2 ± 2.1**,
> functions **−3.0 ± 2.8**, mixed **+0.5 ± 2.0** (flat), large-1k guard
> **−0.4 ± 0.7** (flat); **five-row geomean −15.6%**. Host note per the J1
> qualification rule: this instance's same-jar arithmetic null read
> **+2.7 ± 4.1** (`t2-qual/` — a noisy-host floor), so the arithmetic row's
> exact magnitude is approximate; the effect is ~8σ above the floor and the
> decision does not depend on the digit. Same-session R-lane rerun
> (`t2-h2h*/`, functions and mixed in their own 450k-iteration matrix, no
> window caveats): **karate ÷ rhino-best on Graviton** — arithmetic
> **0.983**, strings **1.108**, objects **0.944**, functions **1.074**,
> mixed **1.459**, large-1k guard **1.099**; **five-row geomean 1.29 →
> 1.10**. karate-js is now *faster* than rhino-best on two of the five rows.
>
> **Item 11 is measured and mostly refuted.** The comment-group list is now
> lazy (`e2d349c69`), but commit-before-allocate in `BaseParser.enter` is
> **not built**: a counter run over the five rows showed `exit(false)` fires
> once per parse and always on an empty node — the gated `enterIf` variants
> already fail before allocating, so the "rewound speculative Nodes" premise
> does not hold. The parser's real allocation mass is token-wrapper Nodes
> (`consumeNext`'s `new Node(token)` — ~46% of all Nodes on the mixed row);
> storing tokens directly as children would be the lever, and that is
> interpreter-wide surgery, not a diet pass.
>
> **The remaining gap now concentrates in mixed (1.46)** — the other four
> rows sit between −6% and +11% of rhino-best. Items 9 and 10 as ranked
> below buy less than the pre-#8 profile suggested (strings 1.11, functions
> 1.07); re-profile the mixed row before choosing the next item.

8. **Slot frames for top-level/program scope.** Extend the SlotTable frame
   from function bodies to the Program node (and block scopes within it),
   with the same DEFERRED heuristics and rearm machinery. This is the single
   biggest lever on arithmetic/strings (§A) and benefits every real-world
   script's top level. Design constraints to resolve first: the
   `Engine.bindings` external get/set contract between evals, karate-core's
   hidden root bindings + `JsLazy`, `evalWith` isolation, indirect `eval`,
   and implicit-global assignment — the same seams JS_ENGINE.md's scoping
   sections document. The J1 validation-debt list (TDZ, labeled continue,
   async interplay) is the test checklist to mine.
9. **ConsString-style rope.** A ~100-line `CharSequence` cons cell built by
   `Terms.add` for string+string, flattened lazily and iteratively. The cost
   is the seam audit, and it is **wider than the obvious boundaries**: beyond
   `Engine.toJava` / `getJavaValue()` / key normalization / Java-interop arg
   conversion, `JsArray` implements `List` and its iterators / `set` /
   collection views hand out raw stored values, and there are many internal
   `instanceof String` dispatch sites (regex, JSON, iteration, errors,
   constructors, property access). Precondition: an **enumerated audit** of
   every `instanceof String` site with a decision per site (accept
   CharSequence / flatten here), pinned by tests. Mechanical but wide; the
   test net is the license. Owns the strings row.
10. **Call-frame diet.** Exact-size frames (SlotTable already knows the
    counts), `ScopeEntry` elimination or pooling (parallel int stack instead
    of an object per level), lazy `arguments` stays, closure capture moves
    from snapshot-`HashMap`-per-function-object toward per-variable cells
    (quickjs `var_refs`) — capture only captured names, computed at parse.
    §B's remaining half, plus memory-retention benefit for closures.
11. **Parser allocation diet.** Commit-before-allocate in `BaseParser.enter`
    (allocate `Node` on successful exit, or reuse a node stack of
    pre-allocated shells), skip the comment-group `ArrayList` when no
    comments. Helps every fresh eval including karate-core's per-step evals.

### Tier 3 — the big bet, deliberately deferred

12. **An internal linear IR (Icode) tier with an unboxed operand stack.**
    This is the remaining ~half of Rhino's advantage (dual `Object[]`/`double[]`
    stack, fused instructions, byte-indexed locals) and quickjs's whole
    design. It is a rewrite of the execution core behind the same `Engine`
    surface. **Decision rule: do not start it until Tiers 1–2 are measured.**
    If they close the geomean to ~1.1–1.2× of rhino-best, the residual doesn't
    justify a second execution engine to maintain; if a large arithmetic/
    functions residual remains and profiling shows it's dispatch+boxing
    (the parts only linearization fixes), design it then — with the test262
    net as the migration harness, and Rhino's `InterpreterV2` (optLevel −2)
    plus quickjs's three-phase compiler as the references.
    **Measured 2026-08-16: the band is reached.** Tiers 1–2 closed the
    five-row geomean to **1.10×**, and the residual is not the
    dispatch+boxing shape — it concentrates in js-mixed (1.46), which the
    post-Tier-1 profile attributes to parse-side allocation and object/array
    write paths. Tier 3 is **not justified on current evidence**.

### Out of scope here, tracked elsewhere

- **Parsed-AST reuse in karate-core** (the real-world fresh-eval eliminator):
  parked design in PROFILING.md with the `Step`-model reasons; the engine-side
  seam (`Engine.parse`/`eval(Node)`) already exists. Revisit after Tier 1.
- **karate-js-benchmark hygiene** — ✅ done 2026-08-16, and further: the
  repo's canonical run moved off GitHub CI onto a pinned dedicated EC2 host
  (`etc/ec2-benchmark.sh` there — median of 3, sha printed, local checkout
  shipped). Its 2.1.3.RC1 publish reads **parity with rhino-best** (x64
  fresh-row geomean ~1.0; Mixed 1.33 the one row behind — consistent with
  the R3 Graviton picture). Bonus finding recorded in that script's header:
  on 4 vCPU hosts JIT compiler threads race the measured thread and made
  single karate rows swing 2–4x between runs; 8 vCPU collapses the spread —
  GitHub runners are 4 vCPU, which retro-explains much of the CI-era noise.

---

## 5. Measurement protocol per change (the loop)

1. Local attribution cell(s) with JFR on (§1 protocol) — confirm the
   mechanism moved (the hot line shrank), not just the clock. **Archive the
   before/after digests per item** so a later batch result can be explained
   or bisected.
2. Local tag-paired `--no-jfr` A/B, 2 pairs, on the affected rows + the two
   guard rows (`js-large-1k`, and whichever row the change should NOT move).
   **Screening only, never acceptance** — and use J1's raised iteration
   counts (`js-large-1k` ~400k, `js-functions` ~450k; defaults measured
   under compare's 20 s startup-shaped check on the laptop).
3. Full net: karate-js unit tests, `test/language/**` slice diff (zero
   regressions), `EngineBenchmark profile` vs JS_ENGINE.md reference,
   karate-core consumer check, smoke battery.
4. Batch acceptance on EC2: one 4-pair matrix per *batch* of landed items
   (not per item — bench spend discipline). **Qualify the host first** per
   J1: same-jar arithmetic null must resolve sd ≲ 1% before the decision
   matrix; an unqualified host cannot resolve the question. Exception,
   exercised 2026-08-16: when the expected effect is many multiples of the
   measured null floor, proceeding on a noisy host is sound — quote the
   null beside the result and treat that row's exact digit as approximate.
5. Update the R1 baseline in PROFILING.md §9 and the JS_ENGINE.md reference
   table in the same commit when numbers move.

---

## 6. Open questions — answered by round-1 review

- **Tier-1 #5 guard**: a bare per-array bit + global flag is NOT sufficient.
  Use the full `specIterate`-shaped invariant (exact class, standard
  prototype, no own descriptors, per-Engine numeric purity) plus writable
  length and extensibility/integrity state — folded into item 5 above.
- **Tier-2 #8 (top-level frames)**: keep the externally visible global
  environment map-backed (the `Engine.bindings` cross-eval host contract);
  apply frames to lexically confined top-level *blocks and loop bindings*,
  which is where the measured per-iteration cost lives anyway.
- **Tier-2 #9 (ropes)**: the boundary set as originally listed is
  incomplete — see the enumerated-audit precondition folded into item 9.
- **Tier 3 vs LLM-debuggable errors**: compatible *conditionally* — every
  instruction must retain a compact source-position mapping and route errors
  through the existing JS error-shape layer; acceptable only if Tiers 1–2
  leave a measured dispatch/boxing residual that justifies a second
  execution representation.

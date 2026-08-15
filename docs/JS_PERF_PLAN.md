# karate-js Performance Plan — mechanism attribution and the road to closing the Rhino gap

> Status: **reviewed draft** — planning/research output of the 2026-08-15 session.
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

### Out of scope here, tracked elsewhere

- **Parsed-AST reuse in karate-core** (the real-world fresh-eval eliminator):
  parked design in PROFILING.md with the `Step`-model reasons; the engine-side
  seam (`Engine.parse`/`eval(Node)`) already exists. Revisit after Tier 1.
- **karate-js-benchmark hygiene** (medians of 3, print the sha) — cheap,
  separate repo.

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
4. Batch acceptance on EC2: one 4-pair matrix per *batch* of Tier-1 items
   (not per item — bench spend discipline). **Qualify the host first** per
   J1: same-jar arithmetic null must resolve sd ≲ 1% before the decision
   matrix; an unqualified host cannot resolve the question.
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

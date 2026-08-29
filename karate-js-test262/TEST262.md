# karate-js-test262

ECMAScript [test262](https://github.com/tc39/test262) conformance harness for
karate-js. Reproducible pass/fail matrix across the ES surface area, declarative
skip list ([`etc/expectations.yaml`](etc/expectations.yaml)), and the roadmap
for what to tackle next. **Not** published to Maven Central.

The bar is *can karate-js run real-world JavaScript written in the wild,
especially by LLMs?* test262 is the scorecard; pragmatic ES6 coverage of
idiomatic code is the goal — not spec-lawyer compliance for its own sake.

> **See also:**
> [../karate-js/README.md](../karate-js/README.md) — what karate-js is ·
> [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, slot
> family, prototype machinery, **spec invariants**, benchmarks. **Design
> reference for every TODO below.** ·
> [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design ·
> [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md)
> — authoritative test-runner spec.

This file is the **roadmap**. For *why* a TODO exists or *how* a subsystem is
shaped, follow the JS_ENGINE.md anchors below.

---

## Completion-record semantics: language side closed; residual lives in built-ins

The abrupt-completion / evaluation-order family — assignment LHS-Reference-
before-RHS, nothing-evaluates-past-a-throw, user errors forwarded through
iterator acquisition and destructuring instead of being overwritten by the
machinery's own TypeErrors — is **closed for `test/language/**`** — with one known counterexample
found 2026-08-16: argument expressions are *skipped* when the callee is
not callable (`o.bar(foo())` throws TypeError without evaluating
`foo()`; 5 FAILs). The poisoned-probe filter below misses it because
those tests probe with a side-effect flag, not a thrown error. The
invariants are pinned by `SpecPinTest.assignment_* / compoundAssignment_* /
iteration_* / iteratorClose_* / destructuring_* / superAssignment_*`,
alongside
[`TryFinallyCompletionTest`](../karate-js/src/test/java/io/karatelabs/js/TryFinallyCompletionTest.java),
[`AbruptCompletionShortCircuitTest`](../karate-js/src/test/java/io/karatelabs/js/AbruptCompletionShortCircuitTest.java), and
[`HostCallThrowTest`](../karate-js/src/test/java/io/karatelabs/js/HostCallThrowTest.java).
Load-bearing seams for future work in this area: `PropertyAccess.assign` /
`compound` (take the RHS *node*, evaluate it mid-sequence), `resolveWriteSite`
(stops on abrupt completion), `IterUtils` (cooperative-error checks after every
call into user code).

**The hunting method stays valid — poisoned probes, not constructs.** test262
probes evaluation order with poisoned objects (`{toString(){ throw new
Test262Error() }}`), so the filter for this family is the failure message:

```sh
python3 -c "
import json,collections,re
rows=[json.loads(l) for l in open('<run-dir>/results.jsonl') if l.strip()]
p=[r for r in rows if r['status']=='FAIL' and re.search(r'Expected a \w*Error but got a (TypeError|ReferenceError)', r.get('message') or '')]
print(len(p)); print(collections.Counter('/'.join(r['path'].split('/')[:3]) for r in p).most_common(8))"
```

**Where it stands.** `test/language/**` is at **zero** matches. The
remaining concentration is `test/built-ins/**`: mostly absent-global noise
(`Iterator`, `Promise` — feature coverage, not ordering), with the real
signal in `RegExp` and `Array` — built-in
*entry-point* coercion order (a poisoned argument's error vs. the built-in's
own arg-validation TypeError). That is the
[JS_ENGINE.md § Spec preamble at built-in entry points](../docs/JS_ENGINE.md#spec-preamble-at-built-in-entry-points)
track, not interpreter machinery — **spec-only by the real-world bar,
deprioritized**. Sample a few with `--single -v` before assuming they share
one cause.

---

## Working principles

Operating-mode maxims for the test262 conformance loop. Treat as load-bearing.

1. **Real-world JS first; test262 is the scorecard, spec is ground truth.** A
   fix that unblocks 500 idiomatic tests beats one that tightens a rare spec
   corner. Existing JUnit tests can be wrong: when the spec disagrees, the
   spec wins — fix the test along with the engine.

2. **Errors must look like JavaScript, not Java.** A raw
   `IndexOutOfBoundsException` or `at io.karatelabs.js.Interpreter.eval(...)`
   frame escaping `Engine.eval(...)` is a correctness bug, not cosmetic
   noise. See [JS_ENGINE.md § Exception Handling](../docs/JS_ENGINE.md#exception-handling)
   and [§ Error routing & shape](../docs/JS_ENGINE.md#error-routing--shape).

3. **Fix friction before moving on.** Bad error messages in `results.jsonl`,
   parse-vs-runtime classification gaps, missing report fields, `--single -vv`
   not showing what you need — stop and fix the tooling rather than working
   around it.

4. **Protect the hot path — pay edge-case cost on the edge case.** Sentinels
   over thrown signals, type-check rare cases after the common-case miss,
   parse-time analysis over inner-loop checks. After any non-trivial engine
   change, run `EngineBenchmark profile` and compare against
   [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).

5. **Code should be DRY and aligned with the JS spec.** Near-duplicate
   dispatch and wrong-layer workarounds are clues that the layer below is
   wrong; collapse to a single spec-shaped seam. Fix it inline or file a
   [Deferred TODO](#deferred-todos) with concrete pointers (file, method,
   what the unification looks like) — vague "this could be cleaner" notes
   are worthless.

6. **Batched commits are fine if the message enumerates the changes.** What
   matters is that the commit message lets a future bisect attribute
   regressions.

7. **Aggregate, don't dump — context is precious.** A full run is ~53k
   JSONL rows. Treat run output as files to query, not streams to tail.
   Full rules in [Context discipline](#context-discipline).

8. **Playbook hygiene is the work, not a chore.** Stale counts, "past
   wins" narration, log patterns that flood context, JSONL the queries
   can't parse — fix the rot inline in the session that surfaced it.
   Fix it at the writer, not in a workaround. A playbook future
   sessions can trust is worth more than a museum piece.

9. **Refactor — or rewrite — boldly; the regression net is the license.**
   This repo carries an unusually strong safety net: the test262 language
   slice with byte-for-byte FAIL-set diffing ([Diff two run-dirs](#diff-two-run-dirs)),
   1265+ unit tests with `SpecPinTest` spec-invariant pins, 2550+ karate-core
   consumer tests, and JIT-stable benchmarks. That net exists so you can do the
   *right* structural thing instead of accreting another local workaround. When a
   subsystem is fighting you — near-duplicate traversals, a check at the wrong
   layer, a seam that every new feature has to special-case — you are empowered to
   restructure or rewrite it, not just patch around it. This is the active form of
   principle #5: #5 says *spot* the wrong-layer smell; #9 says *act* on it. The
   discipline that makes boldness safe, not reckless: (a) state the smell and the
   target shape before cutting; (b) keep behavior-preserving refactors and new
   behavior in **separate commits**; (c) gate every such change on the **full** net
   — unit tests, `test/language/**` 0-regression diff, `EngineBenchmark profile`
   within budget, karate-core consumer check — and quote the before/after in the
   commit. A refactor that the net certifies as behavior-identical is always
   cheaper than the compounding cost of the workaround it removes. *(Worked example:
   the 2026-05-30 fused early-error walk — three full-tree validation passes
   collapsed to one, ~13% of parse CPU reclaimed, FAIL set byte-for-byte identical.
   See [Engine — cleanup → Fuse the early-error parse walks](#engine--cleanup).)*

---

## Per-session ritual

Each session that touches the engine should:

1. **Re-probe the slice baseline** with `--only` before scoping. Old slice
   numbers go stale fast — record fresh before/after pass counts in the
   commit message and pin the run-dir. If `target/test262/` has no
   `run-*` dirs yet (clean clone, or after `mvn clean`), your first
   `--only` invocation *is* the baseline; pin its run-dir in the commit
   so the next session has a diff target.
2. **Unit tests:** `mvn -f pom.xml -pl karate-js -o test` →
   `Tests run: 1265+, Failures: 0, Errors: 0, Skipped: 2` (count grows as
   `SpecPinTest` accretes invariants). **If you edited
   `etc/expectations.yaml` or anything under this module's `src/`, also
   run `mvn -f ../pom.xml -pl karate-js-test262 -o test`** — the harness
   has its own unit tests (`ExpectationsTest` et al.) that CI runs and the
   karate-js/karate-core gates do not cover.
3. **test262 built-ins probe:** diff `results.jsonl` against the previous
   run. **Zero regressions (PASS → FAIL).** Document any flip in the commit
   message.
4. **EngineBenchmark profile:** within ±10% of the
   [JS_ENGINE.md reference](../docs/JS_ENGINE.md#performance-benchmarks);
   ±5% on hot-path refactors. If unavoidable (correctness > speed), update
   the reference table in the same commit.
5. **karate-core consumer check:**
   ```sh
   mvn -f pom.xml -pl karate-js -o install -DskipTests
   mvn -f pom.xml -o test -pl karate-core
   ```
   Expect `Tests run: 2550+, Failures: 0, Errors: 0, Skipped: 3`.
6. **Update this file's TODOs in the same commit.** This is a roadmap,
   not a changelog. For each item the commit addressed (active priority
   bullet, background sweep, deferred TODO, or implicit assumption a bug
   broke), strike or rewrite it here. If the work surfaced a new
   architectural invariant — a contract that future code must respect —
   push the *why* into [JS_ENGINE.md](../docs/JS_ENGINE.md) under the
   relevant spec-invariant anchor, then leave a one-line pointer here
   from any TODO that still depends on it. Yesterday's done work doesn't
   belong in this file; the commit log is the audit trail.

---

## Context discipline

A full conformance run is ~53k JSONL rows. A slice (`test/language/**`)
emits one `FAIL <path> — <type>: <msg>` line per failure on stdout plus a
growing `results.jsonl.partial`. Per-test `-vv` dumps full source. Pulling
any of this raw into your context burns the budget you need for the
actual engineering work. **Treat run output as files to be queried, not
streams to be tailed.**

**Rules:**

1. **Never `tail -f` or `cat` a full `progress.log` / `results.jsonl`.**
   For liveness, `tail -n 1 <progress.log>` returns the last heartbeat
   (`processed N pass M fail K skip L @ rate`) — that's tests-done
   authoritatively in either mode. (`wc -l <partial>` counts only
   FAIL+SKIP in dev mode, so don't use it for total-processed.) For
   slicing use the [Failure triage](#failure-triage) `jq` one-liners.

2. **Default `--single` to `-v`, not `-vv`.** `-v` prints metadata +
   classification + the engine's `location: <path>:<line>:<col>` —
   usually enough to find the call site. Escalate to `-vv` (full test
   source) only after `-v` fails to localize the cause.

3. **Cap diff output.** When comparing two run-dirs, emit counts +
   top-N representative paths + per-slice cluster breakdown. Never the
   full regressed / new-pass lists. The
   [Diff two run-dirs](#diff-two-run-dirs) recipe is already capped —
   use it as written.

4. **Delegate slice runs to a sub-agent with a strict return contract.**
   Spawn a `general-purpose` agent (it has Bash) and require a ≤200-word
   digest: pass/fail/skip counts, top 3 failure clusters with one
   example each, anything surprising. The agent reads the full output;
   you receive the digest. See [Delegate a slice run](#delegate-a-slice-run)
   for the exact prompt template.

5. **Prefer reading engine source over reading log streams.** A FAIL
   line tells you *what* threw; the engine source tells you *why*.
   Once you have one representative failing path and the call site
   from `--single -v`, close the JSONL and work from the code — the
   slice re-run to confirm the fix is a single `etc/run.sh --only`
   away (delegate it).

6. **Mvn output is verbose — pipe to `tail -n 30`.** Unit tests,
   benchmark, and karate-core consumer check from the
   [per-session ritual](#per-session-ritual) all dump compile noise
   before the summary. `mvn ... -o test 2>&1 | tail -n 30` is enough
   to see `Tests run: ...` and any failures. Use `-q` to suppress
   compile chatter when you don't need it.

7. **`etc/expectations.yaml` is 175 lines** — fine to `Read` whole when
   editing the skip list. Long-form files in `target/test262/run-*/`
   are not — query them.

---

## Active priorities

Reordered 2026-08-12 against the real-world bar; re-probed at the end of
the 2026-08-16 generators/doneprintHandle session (latest pins:
`run-doneprint-lang` — 7597 pass / 2163 fail / 13885 skip, up from 6433
pass at the session's `run-baseline-lang`, 0 PASS→FAIL regressions at
every step — and `run-doneprint-bi` — 10050 pass / 3140 fail / 10324
skip, the first built-ins baseline with generators and `flags: [async]`
live; plus the idiomatic-JS smoke battery at
[Real-world smoke battery](#real-world-smoke-battery), **64/64** — that
battery's `typeof Symbol('a')` snippet passes; it is not a statement about
Symbol conformance, which stays parked).
The ES2015–ES2022 core an LLM leans on hardest — arrows, destructuring in
every position, spread, optional chaining, `??`/`??=`, template + tagged
literals, classes with getters/`extends`/`super`/public+private fields and
methods, arrow fields, custom `Error` subclasses, Map/Set + WeakMap/WeakSet,
JSON round-trips, spec-shaped Number→string at all magnitudes, the modern
String/Array/Object method sets, regex named groups + `matchAll` + `/g`
function replacers, labeled statements, and **async / await / Promise /
setTimeout** (vthread-activation model; see
[JS_ENGINE.md § Async](../docs/JS_ENGINE.md#async--await--promise)) — is
solid. The 2026-08-12 session closed the entire P0 tier and the two small
P1s. **2026-08-16: a four-track gap hunt** (SyntaxError-FAIL triage,
Test262Error triage, class skip-shadow re-measure, 65-snippet LLM-idiom
battery cross-checked against Node) **refilled the tiers below** — every
repro was independently verified against HEAD before being listed.

### Next session — the recommended order, real-world first

The symbol-store session (`f7a568523`) probed the engine from the outside — a
downstream prototype ran real JS against the shipped jar — and this is what it
found, ranked by how often LLM-written or customer JS hits it per hour of fix,
not by spec distance. Everything here was verified against HEAD; the last
group is named so it is not picked up by accident.

1. **`structuredClone()` and `performance.now()` are absent** (P1 — surface
   LLMs write constantly). Both are `ReferenceError` today; modern LLM output
   reaches for `structuredClone(x)` as *the* deep copy and `performance.now()`
   for timing, and a check or rulebook dies on the first line. `structuredClone`
   over the JSON-safe subset plus `Date`/`Map`/`Set`/`RegExp` with cycle
   detection (`DataCloneError` for functions/symbols per the HTML spec) is a
   morning; `performance.now()` is `System.nanoTime()` behind a `performance`
   object — an hour. Highest value per hour on this list.
2. **The ASI-`[` / ASI-`(` error message** (P2 — error UX). A line that starts
   with `[` or `(` continues the previous statement — real JS does the same —
   but the failure surfaces as a raw parser dump that names neither cause nor
   fix. LLMs omit semicolons; a downstream drive measured this costing a run
   three iterations. The message names the line, says "the previous line has no
   `;` and this line starts with `[`", and shows the fix. Message only — the
   behaviour is spec.
3. **`Object.getOwnPropertyDescriptor` returns a raw Java `Map`** (P0 —
   silent wrong answer): `'get' in desc` throws, `desc.hasOwnProperty` is
   absent, spreading a descriptor loses it. Real idiom in cloning/decorator
   code. Small; details under [Engine — spec alignment](#engine--spec-alignment).
4. **`BaseLexer` drops a comment after the last primary token.** A trailing
   file comment is unreachable from the token stream; the downstream formatter
   works around it with a raw-tail scan. Flush to the EOF token. Small.
5. **`JsArray` is both `ObjectLike` and `List`, and `toMap()` drops the
   elements** — the Java bridge, not the language: an embedder that inspects a
   result by `instanceof ObjectLike` first renders an array as `{}` and carries a
   check-`List`-first workaround forever. Fix the bridge so an array's primary
   conversion is the list; one contract, every consumer.
6. **Well-known symbols off their `"@@…"` string keys** — P0 but narrow:
   `Object.keys` of any object or class instance carrying `[Symbol.iterator]()`
   shows a phantom `"@@iterator"` key. Real code writes iterables; it rarely
   enumerates them. The cost is rewriting the `IterUtils`/`Terms` dispatch that
   is built on the strings — pull it when a real case bites, not for the slice
   counts, and do the `PropertyKey` unification in the same cut.
7. **Downstream asks, product-driven:** a `COMPARE` event for membership
   (`in`, `.includes()`) so the branch-learning explorer can witness that
   spelling; `Reflect.get/set/has/deleteProperty` only alongside `Proxy` and only
   if a real library needs them.

**Deliberately last — conformance, not real life:** `Symbol.prototype` /
`description` / the registry, cross-realm well-known identity, `Symbol.species`
and the RegExp symbol protocol, `JSON.stringify` leaving U+2028 raw, and lifting
the `Symbol` / `Reflect/ownKeys` / `super` / `class/definition` feature skips for
their own sake. Pick any of these up only when it is the blocker in front of an
item above.

### P0 — silent wrong answers (valid code, wrong result, no error)

The most dangerous class: nothing throws, output is just wrong.

*(Shipped 2026-08-12: P0.1 Number→string / parseFloat — `Terms.numberToString`
is the shared spec seam; P0.2 `/g` function replacer; P0.3 arrow `this` —
which was two bugs: arrows never bound lexical `this` at call time at all,
plus the field-initializer save/restore, see JS_ENGINE.md § the class
section. P0.5 optional-chaining was re-verified and closed as a
misdiagnosis: JS-observable behavior was already correct — the raw-null
report came from probing at the `Engine.eval` host seam, which unwraps
`undefined` to Java `null` by design; the write-site exits were hardened
with a distinct `SHORT_CIRCUIT_SITE` anyway. Original numbering kept.)*

*(P0.4 shipped 2026-08-12 as a full private-elements implementation —
fields, methods, accessors, `#x in obj`; see JS_ENGINE.md § the class
section. P0.6 — base-class instance fields not running for derived
instances — shipped in the same change, along with a bare-field parse fix:
`class C { x; }` used to define a field named `";"`. That tier is closed.)*

Refilled 2026-08-16 (fresh numbering; ranked by real-world weight):

*(P0.1 loose-equality / relational ToPrimitive shipped 2026-08-16:
`Terms.looseEq` / `Terms.strictEq` / `Terms.isLessThan(lhs, rhs, leftFirst,
ctx)` replaced `Terms.eq/lt/gt/ltEq/gtEq` at the `Interpreter.evalLogicExpr`
seam. Also closed by the same rewrite: `<` never compared two strings as
strings, `'' == false` missed the Boolean→Number leg, `+0 == -0` was
`false`, and StringToBigInt rejected the empty string and `0x`-prefixed
input. 70 language FAILs flipped to PASS, 0 regressions. Residual in those
slices: 4 rows needing `\u{...}` string escapes (lexer gap) and one
`Symbol.toPrimitive` getter probe. Original numbering kept.)*

*(P0.2 shipped 2026-08-16: `Terms.copyDataProperties(target, source,
excluded, ctx)` is the single §7.3.26 seam — `Object.assign`, object spread
and destructuring rest all route through it, so getters fire at copy time,
non-enumerable own properties are skipped, and a throwing getter stops the
copy. See [JS_ENGINE.md § Own-key ordering](../docs/JS_ENGINE.md#own-key-ordering).)*
*(P0.3 shipped 2026-08-16: `JsJson.serializeProperty` /
`JsJson.internalize` are the spec-shaped §25.5.2 / §25.5.1 seams —
reviver, `toJSON`, holder-`this` for replacer functions, replacer arrays
on nested objects, quoted root strings, and the sparse-array
`"<<hole>>"` leak, all in one rewrite. Date still special-cased in the
shared `StringUtils.formatRecurse` for karate-core / raw-Java-`Date`
bindings; JS `Date` now routes through `Date.prototype.toJSON`.)*
*(P0.4 shipped 2026-08-16: `AsyncScope` now keeps a microtask queue and a
macrotask queue, `AsyncJob.macrotask()` is the one place the split is
decided, and `takeJob` gives microtasks strict priority — the HTML
microtask checkpoint, since the pump takes one job at a time. Pinned by
`JsPromiseTest.testMicrotasksDrainBeforeAnyTimer` /
`testEachTimerIsFollowedByAFullMicrotaskDrain` /
`testPromiseResolvedInsideATimerReactsBeforeTheNextTimer` /
`testTimersOrderByDelayThenRegistration` /
`testAwaitContinuationsInterleaveWithTimers`. Residual: a **suspended
activation's resumption is an unpark, not a queued job**, so it still
loses to an already-pending timer — see
[JS_ENGINE.md § Accepted deviations](../docs/JS_ENGINE.md#accepted-deviations) 2.)*
*(P0.5 shipped 2026-08-16: super references now carry the method's `this` as
receiver end to end — `AccessSite.receiver` + the receiver-aware
`PropertyAccess.getByName`/`setByName` overloads are the §13.3.7.3 /
§10.1.9 seams. Covers dot + bracket reads, method calls (`super['m']()`
previously bound `this` to the parent prototype), setter writes, data
writes (previously mutated the shared prototype; now create an own
property on the instance), compound/logical assignment, and inc/dec.
Pinned by `JsClassTest.testSuper*` — 8 new pins.)*
*(P0.6 shipped 2026-08-16: B.3.1 `__proto__:` in an object literal now sets
[[Prototype]], with `JsParser.isProtoSetter` the one shape predicate shared
by the interpreter and the §13.2.5.1 duplicate early error — the
`__proto__`-duplicate edge in the expressions/object slice below is closed
with it.)*
7. **Wrong-answer tail** (each battery-verified, smaller blast radius):
   *(The last three shipped 2026-08-16: arrows now resolve `arguments`
   lexically — `CoreContext.arrowFrame` + `argumentsForRead()` walk the
   declaring chain, identity shared with the enclosing frame (3 test262
   flips). `class S extends Array` / `extends Map/Set/Date` allocate the
   real exotic instance — `JsFunctionNode.baseBuiltinCtor` resolved at
   class-eval, `Interpreter.allocateInstance` at `new`, and the super()
   shim initializes the receiver's internal state in place — so
   `Array.isArray`, stringification, `length`, and the Map/Set/Date
   prototype methods work on subclass instances. Residuals: `extends
   RegExp` stays on the copy-shim (immutable compiled state; exec/test
   unsupported on subclasses), and private fields on `extends Array`
   instances are skipped (private state is JsObject-only). Pinned by
   `JsFunctionTest.testArrow*Arguments*` + `JsClassTest.testExtends*`.)*
   *(Five of the eight shipped 2026-08-16: `fn.length` now follows
   §15.1.5 ExpectedArgumentCount and `bind` subtracts its partials;
   `indexOf`/`lastIndexOf` use IsStrictlyEqual and `includes` real
   SameValueZero (it was loosely equal, so `[1].includes('1')` was true);
   `String.prototype.split` on a regex is the spec's §22.2.6.14 anchored
   walk in `JsStringPrototype.regexSplit`, so capture groups interleave
   and the limit is a real ToUint32; `JsRegex.matchFrom` is one seam for
   the `g`/`y` lastIndex protocol; `parseInt` strips `0x` for an explicit
   radix 16. Each carries a permanent smoke snippet.)*

### P1 — missing surface LLMs write constantly

*(async/await/Promise/setTimeout and `globalThis` shipped 2026-08-12 —
struck from this list. Remaining async tail: `for await`, async
generators/iterators, `setInterval`, `queueMicrotask` — deferred.)*

*(P1.1 Generators shipped 2026-08-16 — `function*` / `yield` / `yield*` as
vthread coroutines on the async foundation, including class/object-literal
generator methods, yield-inside-finally, and full §27.5.3.7 `yield*`
delegation; design externally reviewed over 3 rounds. See
[JS_ENGINE.md § Generators](../docs/JS_ENGINE.md#generators-function--yield--yield)
for the invariants. The `generators` feature skip is removed: language slice
went 6433 → 7470 pass with 0 regressions; the ~330 newly-attempted FAILs are
conformance tail (escaped keywords, early errors, GeneratorFunction
intrinsic, poisoned-probe ordering). The same landing fixed a pre-existing
deadlock: `eval()` called on any activation thread — async function bodies
included — hung the engine; `Engine.enterEvalScope` is now scope-aware.
Pinned by `JsGeneratorTest` (44 tests).)*
*(P1.2-2026-08-16, the eight-item parse-gap batch, shipped same day —
seven of eight in one sweep: `delete` as a unary expression
(`DELETE_EXPR`, statement dispatch removed, `evalDeleteExpr` returns the
real boolean); `try {} catch {} finally {}` (the try grammar was
linearized — the old eval hard-coded the finally offset and silently
dropped the finally block for the shorter node shape); hashbang at 1:1;
`default:` in any clause position (parse + a §14.12.9 `evalSwitchStmt`
rewrite + at-most-one-default early error in the fused walk + the
follow-up shared-CaseBlock-environment fix, see the spec-alignment
list); trailing-dot numeric literals (`1.` / `1.e3`, with `1..toString()`
pinned; `1.toString()` is now a spec-correct SyntaxError); ASI across
comments (`lineTerminatorBetween` in `BaseParser` is now the single
line-terminator scan for `eos()` and all `lineTerminatorFollows` call
sites — block comments containing a newline count for ASI); rest params
with destructuring patterns (`f(...[a, b])`, parse + binding via the
`evalAssign` seam). The unary-base-of-`**` early error (one fused-walk
arm) shipped in the external-review round-1 commit — all 7
`exp-operator-syntax-error-*` negatives PASS. Still open from the
batch: **class static initialization
blocks** `static { ... }` — parser rejects with "class member name"
(also the #2 gap inside the class skip-shadow).)*
*(P1.2 labeled statements shipped 2026-08-12 — LABELLED_STMT node,
label-aware break/continue via CoreContext.exitLabel, and the full
label early-error family in the fused walk: undefined/duplicate labels,
continue-to-non-iteration, function boundaries. Labelled function
declarations are a clean SyntaxError in both modes.)*
*(P1.3 WeakMap / WeakSet shipped 2026-08-12 as Map/Set-backed non-weak
stand-ins — full method surface, object-key TypeErrors, no size/iteration;
feature skips removed.)*

### P2 — error-UX Java leaks (principle #2)

All edge-input, but each is a Java stack trace where a JS error belongs:
`RegExp` `exec`/`test` null-arg `Cannot invoke Object.toString()`; one
catastrophic-backtracking `Timeout` (`RegExp/.../S15.10.2.8_A3_T17.js`);
a `JSON.parse` reviver ClassCastException (`parse/text-object.js`); three
`replaceAll` poisoned-probe coercion-order rows. *(Fixed 2026-08-12: JSON
circular → `TypeError`, JSON replacer-array `ClassCastException`,
`replaceAll`/`endsWith` range leaks.)*
*(The near-2³² `Array` family shipped 2026-08-16 — the Unknown-row
population dropped 29 → 6, all remaining rows being the RegExp/JSON/
replaceAll items above. The seams: `JsArray.parseIndex` no longer
overflows int (10-digit indices become named properties);
`PropertyAccess.denseIndex` routes negative/fractional/beyond-int Number
indices through the name path instead of wrapping into crashing raw list
accesses; `JsArray.DENSE_PAD_LIMIT` (10M) refuses catastrophic hole-pads
with a catchable RangeError instead of a JVM OutOfMemoryError;
`checkResultLength` makes map/splice/toReversed/toSorted/toSpliced on a
lying `{length: 2**32}` array-like throw the spec's RangeError before
allocating; and unshift/splice get the §23.1.3 2⁵³−1 TypeError checks,
gated on the length-clamp marker so ordinary receivers pay no extra
observable length read. The `length-near-integer-limit` timeout rows
(genuine 4-billion-step walks) stay accepted until the sparse-storage
rework. Pinned by 11 `JsArrayTest` hardening tests.)*
Known-flaky under an accepted deviation (surfaced by the async-flag
unskip, timing-dependent PASS/FAIL): `await-non-promise-thenable.js` —
the settled-await fast path skips a microtask tick, so its interleaving
race with a thenable job is nondeterministic. See
[JS_ENGINE.md § Accepted deviations](../docs/JS_ENGINE.md#accepted-deviations).

Added 2026-08-16:

*(Both engine-side items shipped the same day. Deep recursion:
`JsFunctionNode.bindArgsAndExecute` — the one seam every JS-to-JS call
funnels through, `call` and the Interpreter's inlined call/construct/super
paths alike — catches `StackOverflowError` and rethrows
`RangeError: Maximum call stack size exceeded`. A depth counter was
rejected: no fixed ceiling is portable (the surefire JVM blows at ~450
nested JS frames, run to run, where a default main thread takes far more).
The catch is free on the hot path and self-correcting on the cold one — a
second overflow while building the error lands in the caller's identical
catch, one JS frame further out. The one real hazard is a class
initializer running on the exhausted stack, which fails permanently, so
`JsFunctionNode`'s static block builds one throwaway RangeError to warm
the path. `.stack`: `JsError.captureStack` records `at <source>:<line>:<col>`
frames at construction and renders the `"<name>: <message>"` header on
first read; a non-enumerable writable intrinsic, so it stays out of
`toMap()` and `Object.keys`. Engine-raised errors are stamped where they
become JS values in `evalTryStmt`; `class X extends Error` carries it via
the copy-shim.)*

- **Harness (principle #3):** thrown non-Error *primitives*
  (`throw "str"`) classify as `Unknown` in `ErrorUtils` — all 8 current
  Unknown rows are this, not Java leaks. Add a `ThrownValue` bucket so
  `Unknown` stays reserved for genuine engine crashes.

### Deprioritized spec-conformance backlog

Touch only when a priority above drags it in:

- **Negative parse-phase early errors** (`MissingParseError` — negative
  `phase: parse` tests the engine parses instead of rejecting; scope with
  `jq -r 'select(.error_type=="MissingParseError").path'`). Roughly a third
  is regexp-literal validation, plus a fragmented destructuring-pattern
  tail, escaped-keyword misuse,
  getter/setter arity, the non-simple-param `"use strict"` prologue,
  and (new with async support, ~30
  tests) the async-function early errors — `await` in formals, `super()`
  in an async-method body, async redeclaration rules. Those 30 previously
  "passed" vacuously because `async` itself failed to parse. Same shape,
  new with labels (8 tests, 2026-08-12): `await`/`yield` as label
  identifiers in async/strict contexts, `let [`-at-statement-start
  disambiguation under a label, one `break`-scope corner
  (`S12.8_A8_T2`) — vacuous pre-label flips, all MissingParseError. Rejecting invalid code that no LLM
  writes is spec-lawyering by this file's own bar. When touched: **add each
  early error as a per-node helper inside `JsParser.earlyErrors` — never
  another whole-tree walk** (load-bearing for parse CPU; see
  [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks)).
- **Strict-mode residual** — machinery done; see Deferred TODOs →
  [Engine — feature gaps](#engine--feature-gaps) for the canonical list.
- **Built-in entry-point coercion order** (poisoned-argument probes in
  RegExp/Array) — the
  [§ Spec preamble](../docs/JS_ENGINE.md#spec-preamble-at-built-in-entry-points)
  track.
- **Symbol primitive** — parked. `JsSymbol` answers `typeof`, and a minted
  symbol keys its own identity-keyed `PropertySlot` store on `JsObject`: it is
  invisible to every string-key surface by construction, carries real
  writable/enumerable/configurable attributes, supports accessors, descriptors,
  freeze/seal, strict-mode rejection and prototype-aware `[[Set]]`, and
  `getOwnPropertySymbols` / `Reflect.ownKeys` are real. Implicit coercion throws
  per spec. Residuals: no
  `Symbol.prototype`, no registry (`Symbol.for` / `keyFor`), no `description`;
  `sym instanceof Object` is true; well-known symbols stay engine-internal
  string keys and so remain visible to `Object.keys` as `"@@…"`; a symbol key
  on a non-`JsObject` target has nowhere to go; `Reflect.get` / `set` / `has` /
  `deleteProperty` absent. Well-known identity is per Engine — a deliberate
  deviation (the spec shares well-knowns across an agent's realms).
  See [JS_ENGINE.md § Iteration](../docs/JS_ENGINE.md#iteration).
- **Skip-list hygiene:** built-ins FAIL counts are dominated by absent
  feature families that should be `features:` skips, not FAILs —
  `Iterator` helpers, `ArrayBuffer`/`DataView`, `ShadowRealm`. Adding
  those rules makes every future FAIL count signal instead of noise.
  *(`explicit-resource-management` rule added 2026-08-16 — it was also
  polluting the language slice with 45 `using`/`await-using` FAILs.)*

For current pass/fail/skip counts, query the latest run-dir
(Recipes → [Failure triage](#failure-triage)) — counts go stale fast and
don't belong in this file.

### Built-in health — the business-rules / logic-scripting surface

Qualitative verdict from a scoped probe of the data-type built-ins (the
methods business-rules and logic scripts actually lean on). Counts rot,
so they're omitted — re-probe with `--only 'test/built-ins/<X>/**'` for
fresh numbers. The *shape* of what's solid vs. gapped is the durable part:

- **Number, Date, Object — solid, with one P0 caveat.** `toFixed`/
  `parseInt`/`toString(radix)`/`isNaN`/`isInteger`; Date parse/format/
  `getTime`/`getFullYear`/arithmetic; `keys`/`values`/`entries`/`assign`/
  `freeze`/`create`/`getPrototypeOf`/`hasOwn`/spread/`fromEntries` all
  work, and Number→string formatting is spec-shaped at all magnitudes.
  Other residual fails are spec-corner arg-validation, descriptor-attribute
  edges, and Symbol gates — not core method behavior. Object had **zero**
  Java-leak rows.
- **String, Array — solid on the common path.** `split`/`replace`/`slice`/
  `substring`/`indexOf`/`includes`/`trim`/`pad`/case and `push`/`map`/
  `filter`/`reduce`/`slice`/`concat`/`find`/`sort`/`from`/spread all work.
  The low raw pass-% is dominated by strict coercion-error semantics and
  Symbol/feature gates, *not* everyday breakage. Caveat: `Array` at
  near-2³² lengths still leaks Java errors (`Index out of bounds` /
  VM-size / heap) — see P2.
- **RegExp — solid for everyday patterns.**
  `test`/`exec`/`match`/`split`/`search`, string and function replacers
  (incl. `/g`, which fires the function per match), `g`/`i`/`m` flags, and
  **named-group capture** (`m.groups.name`, `$<name>` substitution, the
  function-replacer `groups` arg) all work — Java `Pattern` is the
  backend. Remaining gaps: lookbehind, unicode property escapes, `/v` flag,
  group-name early-error validation; plus null-arg Java leaks in
  `exec`/`test` and one catastrophic-backtracking timeout (P2). The
  `Symbol.{replace,match,matchAll,split,search}` *protocol* fails are
  conformance-only — the everyday `str.replace(re, fn)` path does not
  route through them.

**Bottom line for the target workload:** String/Number/Date/Array/Object
are dependable and RegExp covers the common path including named groups.
The residual RegExp tail (lookbehind / unicode escapes / `/v` /
early-error validation) is advanced-pattern territory.

| Slice | What's blocking it |
|---|---|
| `test/language/statements/for-of` | IteratorClose machinery is in place (`Interpreter.destructurePattern`/`evalForStmt` + `JsIterator.close`). Remaining: assignment-pattern target-eval-order (`[ obj[sideEffect()] ] of …` must evaluate the target reference before stepping the iterator — the `*thrw-close*` family, a rare spec corner); fn-name inference for `[x = (function(){})] of …`; negative-parse tightenings. |
| `test/language/expressions/object` | Escaped-keyword cover-name dominates; computed-key / method-def tail. *(`__proto__` set/duplicate and the spread CopyDataProperties family shipped 2026-08-16.)* |
| `test/language/expressions/assignment` | Corrected 2026-08-16: the "destructuring parse tail" attribution was wrong — `({[a]:b, ...rest} = vals)` parses fine. Actual causes: escaped-keyword cover-names (~87) + the trailing-dot numeric literal `1.` (all 7 `dstr/obj-rest-non-string-computed-property-*`). |
| `test/language/{statements,expressions}/function` + `arrow-function` | fn-name inference for `[x = (function(){})]`-style defaults; IteratorClose-on-throw; rest-element edges. |
| `test/language/expressions/compound-assignment` | Strict-mode ReferenceError on undeclared LHS now fires under in-body `"use strict"` (the `onlyStrict`-flagged variants stay SKIP until the runner runs a strict pass). Corrected 2026-08-16: the `A5.*_T2/T3` family is **not** an Annex-B non-identifier-LHS issue — all 44 FAILs here (and ~90 suite-wide, incl. prefix/postfix inc/dec and `identifier-resolution`) are the **`with` statement**: `with` isn't a token, lexes as an identifier, `with (x)` parses as a call, and the following block derails. Real-world value low (illegal in strict mode); the path-skip only covers `statements/with/`. |
| `test/language/statements/{try,for,switch}` | Control-flow tail; abrupt-completion and empty-`for`-header semantics handle the headline cases. 2026-08-16 closed: optional-catch-binding+`finally`, `default`-in-any-position, shared CaseBlock environment. Residual in `for/`: loop completion-value `undefined`-vs-`null` (`head-init-*-check-empty-inc-empty-completion.js`) and `let` as a plain identifier in a for head (`head-lhs-let.js`, parser); in `switch/`: `scope-lex-open-*` TDZ corners + feature-gated variants. |
| `test/built-ins/Array/**` | `splice` / `concat` `Symbol.species` (Symbol-gated). |
| `test/built-ins/RegExp/**` | Group-name early-error validation, `Symbol.{match,replace,search,split,matchAll}` protocol (Symbol-gated, conformance-only — everyday `str.replace(re,fn)` doesn't use it), lookbehind / unicode-property-escapes / `/v` flag (feature-gated). Null-arg Java leaks + one catastrophic-backtracking timeout in `exec`/`test` (P2). |
| `test/built-ins/String/**` | `substring` / `lastIndexOf` / `charAt` ToInteger corners; parser-blocked; Symbol-gated tail. See [JS_ENGINE.md § Spec preamble at built-in entry points](../docs/JS_ENGINE.md#spec-preamble-at-built-in-entry-points). |
| `test/built-ins/Object/**` | Descriptor edges; `seal` (TypedArray-gated); Annex-B `arguments` aliasing. See [JS_ENGINE.md § Property attributes](../docs/JS_ENGINE.md#property-attributes). |
| `test/built-ins/JSON/**` | *(Shipped: `parse` reviver, `stringify` `toJSON`, replacer-function holder-`this`, PropertyList at nested levels — `JsJson.serializeProperty` is now the single SerializeJSONProperty walk.)* Residual: non-finite numbers emit `NaN`/`Infinity` instead of `null`; a root that serializes to nothing (`undefined`, a function) returns the text `"undefined"`/`"null"` instead of the value `undefined`; accessor properties serialize as `null` instead of invoking the getter (`toMap` is a raw-value view); `-0`/`__proto__` parser tail. Calibration: run JSONTestSuite — see [JS_ENGINE.md § Future TODO Items](../docs/JS_ENGINE.md#2-future-todo-items). |
| `test/built-ins/Promise/**` | Residual after the async landing: resolve-element-function property shape (`name`/`length`/extensible), some combinator ordering corners, `Promise.try`/`withResolvers` (unimplemented, left to FAIL). *(`flags: [async]` tests now run — doneprintHandle shipped 2026-08-16.)* |
| `test/built-ins/Number/**` | `[object Number]` (Symbol-gated) + a literal-form parser edge. |
| `test/built-ins/Date/**` | ISO format edges + invalid-date propagation. See [JS_ENGINE.md § Date](../docs/JS_ENGINE.md#date). |
| `test/built-ins/Symbol/**` (parked) | Symbol primitive. Deprioritized — no real-world code uses it. Pick up after the language work. |

### Background sweeps

Picked off opportunistically when nearby — not session-sized on their own.

- **`Array.prototype.keys()` / `entries()` return raw `List`** — same class
  of bug `values()` had (now fixed via
  `IterUtils.toIteratorObject(listIterator(...))`); lower-value since
  `arr.keys().next()` is rare. Apply the same fix when a workload surfaces it.

- **`.length` / `.name` rollout to remaining prototypes** —
  `JsBuiltinMethod` infra in place; most residual `name.js` fails are
  Symbol-gated.

- **RegExp group-name early-error validation** (capture access itself
  works): `(?<__proto__>…)` / `(?<_>…)` should SyntaxError — engine
  accepts; part of the parser-tightening sweep. (The functional-replace
  misbehavior that used to sit here turned out to be the `/g`
  fire-once bug — promoted to P0.2.)

- **BoundNames walk residual**: object-method simple-param duplicate in
  sloppy code (rare).

- **Cleanup residuals** — the concrete principle-#2 Java-leak list now
  lives in [Active priorities → P2](#p2--error-ux-java-leaks-principle-2).
  Also seen occasionally: `"null"` NPE paths and an `IllegalName` JDK
  lambda leak. All confined to edge/pathological inputs.

---

## Deferred TODOs

Tracked but un-scheduled. Each item: a one-line *what* + *why parked* +
file pointer. For *how the subsystem is shaped*, read the file. For
*spec invariants worth honoring*, see
[JS_ENGINE.md § Spec Invariants](../docs/JS_ENGINE.md#spec-invariants-test262-driven).

### Engine — feature gaps

- **Strict mode — machinery done; residual only.** Runtime flips and the
  parse-phase early errors are in place; see
  [JS_ENGINE.md § Strict Mode Policy](../docs/JS_ENGINE.md#strict-mode-policy)
  for the flip table (pinned by `SpecPinTest.strict_*`). The runner prepends a
  strict directive for `flags: [onlyStrict]` (`Test262Runner.evaluate`).
  Remaining: ~16 runtime `SyntaxError` not thrown; ~14 strict-assignment
  runtime `TypeError` (arguments-object write guards); `with`-statement early
  error deferred (path-skipped, lexes as a call); the non-simple-param
  `"use strict"` prologue corner (see [Active priorities](#active-priorities)).
- **Promises / async / await / setTimeout — DONE** (vthread activations +
  per-Engine lock; see
  [JS_ENGINE.md § Async](../docs/JS_ENGINE.md#async--await--promise)).
  Job ordering is the HTML microtask checkpoint (two queues, microtasks
  first) as of 2026-08-16. Remaining async tail, deferred: `for await` /
  async iterators / `Symbol.asyncIterator` (still feature-skipped), async
  generators, `queueMicrotask`, `setInterval` (deliberately undefined —
  never quiesces). Sync generators shipped 2026-08-16 (see Active
  priorities P1.1 note); async generators would extend the same
  GeneratorActivation coroutine with promise-shaped step results.
- **ES modules (`import` / `export`) — deliberately unplanned.** The karate
  embedding evaluates script blocks, not modules; the require-role is
  filled by `karate.call()` / `read()`, and full module support is mostly
  host work (specifier resolution, module records, cyclic linking) plus
  `phase: resolution` harness plumbing. Revisit only if the embedding
  grows a module host. Cheap error-UX middle step if demand appears: parse
  `import`/`export` far enough to throw a clear SyntaxError pointing at
  `karate.call()`.
- **Class syntax (ES6) — core works; only the conformance tail remains.**
  Declarations + expressions evaluate: constructor, instance/static methods,
  accessors, computed names, `extends` + `super(...)` + `super.method()`,
  public instance/static fields. Desugared at eval time onto the
  constructor-function + prototype machinery (`Interpreter.evalClassExpr`;
  super dispatch via `JsFunctionNode.homeObject` + `CoreContext.activeFunction`;
  `extends Error`/built-ins via a copy-own-props shim). Covered by `JsClassTest`.
  Private `#x` fields/methods/accessors and derived-class parent-field
  initialization shipped 2026-08-12 (see JS_ENGINE.md § the class section).
  **Remaining tail:** decorators, static-init blocks, class early-errors,
  object-literal-method `super` (needs object [[HomeObject]]), two super edge
  cases (`this`-TDZ before `super()`, `super()` return-override),
  numeric/string-literal method-name canonicalization (`get 0x10(){}` → key
  `"16"`; shared with object literals' NUMBER-key path), escaped-keyword
  method names. Most have existing `feature:`-tag skips
  (`class-fields-private` / `class-methods-private` / `generators` /
  `async-functions` / `decorators`); see the [Skip list](#skip-list) note for
  the path-skip un-skip plan. Fresh un-skip re-measure (2026-08-16) with
  bucket counts lives in the `expectations.yaml` comment on the class
  path rule; dominant blocker is the member-name identifier lexer, not
  the public-field tail.
- **Symbol primitive — the remaining half.** The identity-keyed slot store
  and `typeof` landed (see Active priorities); what is left: `Symbol.prototype`
  (`description`, `toString`, `valueOf`), the registry (`Symbol.for` / `keyFor`),
  `Reflect.get` / `set` / `has` / `deleteProperty`, and moving the well-known
  symbols off their `"@@…"` string keys onto the symbol store (the iteration /
  ToPrimitive / toStringTag dispatch in `IterUtils` and `Terms` is built on the
  strings — that is the real cost). Gates a long tail across String / Array /
  RegExp / Object (`Symbol.species`, the RegExp protocol). Note the test262
  slices that would cover the store — `Reflect/ownKeys`, `language/expressions/super`,
  `class/definition`, `class/accessor-name-*`, most of `built-ins/Symbol` — are
  feature-skipped, so the store's semantics rest on `JsSymbolTest` alone until
  those skips lift.

### Engine — cleanup

Benchmark-gated or coordinated with other work.

- **Parse-phase early errors live in ONE fused walk — `JsParser.earlyErrors`.**
  The three former post-parse traversals were fused after a JFR profile put
  them at ~13% of parse CPU. **Add each new early error as a per-node helper
  inside `earlyErrors`, never another whole-tree walk.** Reference numbers in
  [JS_ENGINE.md § Performance Benchmarks](../docs/JS_ENGINE.md#performance-benchmarks).
- **`Prototype.toMap()` rebuilds per call** — memoize on slot-map mod
  stamp or expose a non-materializing iterator. Defer until benchmark
  shows it matters.
- **`HOLE` → tombstone full elimination.** Sparse-array storage rework;
  pair with parser `in` support. Pinned in `SpecPinTest`. ~6–8 h.
- **HOLE leak audit at `JsArray` Java-interop seams** —
  `iterator()` / `toArray()` / `subList()` / `contains()` / `indexOf()` /
  `lastIndexOf()` route raw; only `get(int)` translates HOLE→null.
  Centralize on one unwrap helper. ~30 min. Pairs with above.
- **`PropertyKey` abstraction.** Half landed as `JsObject`'s identity-keyed
  symbol slot store beside `privates`; a unified string|symbol key type is
  still the right end state once the well-known symbols leave their string
  keys (see Symbol, above) — do not introduce it before then.
- **`Arguments` → spec exotic Arguments object.** Cached `JsArray`
  today; missing `arguments.callee` (strict TypeError), non-strict
  alias-to-formal-parameters, and `[object Arguments]` toStringTag.
  Subclass when a workload demands.
- **`CreateDataPropertyOrThrow` + `ArraySpeciesCreate`.** Array
  result-allocation (`slice` / `concat` / `splice` / `map` / `filter` /
  `flat` / `flatMap`) bypasses spec sequence; depends on `Symbol.species`.
  Defer until Symbol.

### Engine — spec alignment

Observably non-spec; pick up when the owning slice surfaces them.

- **Runtime block/eval environments for lexical bindings.** *(Switch half
  shipped 2026-08-16: `evalSwitchStmt` gives the whole CaseBlock one shared
  declarative environment per §14.12.11 — pinned by
  `SpecPinTest.switch_caseBlockIsOneSharedLexicalEnvironment`;
  `scope-lex-close-{case,dflt}` now PASS. Residual `scope-lex-open-*` rows
  are TDZ-visibility corners, not environment placement.)* Remaining:
  indirect `(0,eval)('const x…')` must evaluate in a
  NewDeclarativeEnvironment off the global env
  (`eval-code/indirect/lex-env-distinct-{const,let}.js`). Runtime scoping
  gap, independent of the parse-phase early-error work.
- **`JsArray.handleLengthAssign` strict TypeError on non-writable length.**
  Strict-mode plumbing has landed (`CoreContext.strict`), but the `length`
  write still routes through `handleLengthAssign(value, ctx)` with no strict
  arg — `PropertyAccess.setByName` special-cases `"length"` *before* the
  strict-aware `putMember`, so `'use strict'; arr.length = 0` on a
  non-writable length is still a silent no-op. Thread `strict` into
  `handleLengthAssign` to finish the flip; everyday code doesn't hit it.
- **`ToObject` for non-empty string descriptor sources** — short-circuits
  to TypeError (correct end-state), skips wrapper pipeline.
- **`JsArray.jsEntries` vs `[[OwnPropertyKeys]]` asymmetry** —
  `jsEntries` is indices only; for-in / `Object.keys` /
  `defineProperties` want indices + named. `JsObjectConstructor.ownKeys`
  works around it. Split into `arrayEntries(ctx)` / `ownEntries(ctx)`
  when a 4th caller surfaces.
- **`ToPropertyKey` no-ctx callers** — `JsObjectConstructor.hasOwn` and
  `getOwnPropertyDescriptor` still on the no-ctx path. Migrate when a
  workload passes non-string keys.
- **`Object.getOwnPropertyDescriptor` returns a raw Java `Map`, not a
  `JsObject`** (`JsObjectConstructor.buildDescriptor`), so `'get' in desc`
  throws `Cannot use 'in' operator` and `desc.hasOwnProperty` is absent —
  string and symbol keys alike. Return a `JsObject` built through the ordinary
  path; one test per descriptor shape.
- **`BaseLexer.tokenize` drops a comment after the last primary token** —
  the comment buffer is attached to the *next* primary token and is never
  flushed at EOF, so a trailing file comment is unreachable from the token
  stream. Flush to the EOF token (it is primary). The consumer that needs it
  is a source formatter in a downstream repo, which scans the raw tail until
  this lands.
- **Integer-index accessors beyond `JsArray.list.size()`** — high-index
  accessor via `defineProperty` is missed by `jsEntries`. Current
  workaround: `defineOwnAccessor` HOLE-pads. Real fix: merge integer-index
  `namedProps` into Phase 1. Pairs with HOLE elimination.
- **`JsGlobalThis` two-store reads** — data in `BindingsStore`,
  accessors in `JsObject.props`. Extend `BindingSlot` with accessor
  side-table OR commit to a unified two-store contract. ~2 h.
- **`(0, fn)()` indirect-call `this`-binding** — comma should drop
  reference base (→ `this = undefined`); today falls through to
  globalThis. Audit `evalCallExpr` for the parenthesized-comma case.

### Harness quality

*(doneprintHandle shipped 2026-08-16: `Test262Runner.evaluate` loads
`harness/doneprintHandle.js` for `flags: [async]` tests, captures the
engine console sink, and reads the `Test262:AsyncTestComplete` /
`Test262:AsyncTestFailure:` protocol after `Engine.eval` drains to
quiescence; a test completing without calling `$DONE` FAILs as `Harness`.
The `flags: [async]` skip rule is removed.)*
- Replace hand-rolled YAML parser with SnakeYAML (`Expectations.java` /
  `Test262Metadata.java` — breaks on `#` in quoted reasons, block scalars).
- `--resume` echoes records for deleted / now-SKIP'd tests — gate or
  rename to `--resume-crash-only`.
- Cache parsed harness ASTs in `HarnessLoader` (~50k re-parses per run).
- Plumb per-test console capture into `ResultRecord` (currently wired
  and discarded by `evaluate(...)`).
- `phase: resolution` (module-resolution) negatives conflated with
  `runtime` — latent (modules skipped).
- `$262` surface stubs (`AbstractModuleSource`, `IsHTMLDDA`,
  `agent.*`) — add when a feature unblocks.
- Parallel execution — prior attempts showed no speedup; engine
  doesn't poll `Thread.interrupt()`. Revisit when per-test cost grows.
- `Test262Runner.readHeadSha` walks parent chain — prefer
  `git rev-parse HEAD` or `--karate-sha`.
- Commit `target/test262/results.jsonl` once engine churn slows.

---

## Running

**All commands run from `karate-js-test262/`** (the runner resolves
`etc/expectations.yaml` and `test262/` relative to cwd). Use `-f ../pom.xml`
so Maven finds the parent reactor. After any change under `karate-js/`,
**re-install it first** — the runner uses the karate-js jar from your local
Maven repo, not from the reactor.

### Quick start

[`etc/run.sh`](etc/run.sh) does install + run (+ HTML on `--full`):

```sh
cd karate-js-test262
etc/fetch-test262.sh                                       # first time only — shallow clone
etc/run.sh                                                 # dev mode, full suite
etc/run.sh --only 'test/language/**' --max-duration 300000 # scoped, 5-min cap
etc/run.sh --full                                          # PASS rows + HTML
```

**`etc/run.sh` cannot be used for an A/B against a different build.** Its first
step installs karate-js from the reactor, so pointing it at a jar you staged
from a worktree or a previous commit silently overwrites that jar and measures
the current tree against itself — a clean zero-delta that means nothing. For a
before/after, stage the jar and then drive the runner directly, skipping the
install:

```sh
mvn -f ../pom.xml -pl karate-js-test262 -o test-compile -q
mvn -f ../pom.xml -pl karate-js-test262 -o exec:java -q \
    -Dexec.args="--run-dir target/test262/run-<label> --max-duration 900000"
```

Confirm the staged jar is really the one in play before trusting the run — a
test you expect to fail against it, failing, is the cheapest proof.

Each run writes a fresh `target/test262/run-<timestamp>/` (the runner
prints the path) containing `results.jsonl`, `results.jsonl.partial`,
`run-meta.json`, `progress.log`; `html/` only with `--full`. Old runs
are immutable; `mvn clean` wipes them.

**Dev mode (default)** keeps `results.jsonl` to FAIL+SKIP only; the
pass count is in `run-meta.json` (`counts.pass`). `--full` adds PASS
rows (for CI artifacts / audits / HTML).

**Liveness sampling** (never `tail -f` — see
[Context discipline](#context-discipline)):

```sh
tail -n 1 <run-dir>/progress.log    # last heartbeat: processed N pass M fail K skip L
tail -n 5 <run-dir>/progress.log
```

### Driving by hand

If you need to invoke the runner or HTML report without `etc/run.sh`,
read [`etc/run.sh`](etc/run.sh) — it documents the install step and the
`-am` gotcha (`exec:java` is a direct goal; with `-am` the reactor
includes `karate-parent`, which has no `mainClass`, and aborts before
this module). Install karate-js separately, then run without `-am`.

⚠️ `exec:java` does **not** recompile. Changes under `karate-js-test262/src`
need a `test-compile` of this module first (run.sh does it); only `karate-js`
engine changes are picked up by the `install` step alone. An edit that
silently doesn't take effect measures as a confusing no-op.

### Flags

Most-used flags below. Full set + defaults: read `main(...)` in
[`Test262Runner`](src/main/java/io/karatelabs/js/test262/Test262Runner.java).

| Flag | Purpose |
|---|---|
| `--only <glob>` | restrict to matching paths |
| `--single <path> [-v] [-vv]` | run one test, no file writes. `-v` prints metadata + classification + engine location; `-vv` adds full source |
| `--full` | write PASS rows (default is FAIL+SKIP only); also gates HTML render in `etc/run.sh` |
| `--max-duration <ms>` | overall wall-clock cap (default unlimited); writes partial results + prints `Aborted:` on hit |
| `--timeout-ms <n>` | per-test watchdog (default 10s) |
| `--run-dir <path>` | output dir (default `target/test262/run-<ts>/`) |

Runs are silent except FAIL lines + periodic `[progress]`. FAIL lines
on stdout are capped at 20 (footer `(… N more FAILs, see results.jsonl)`
fires after). `[progress]` lines emit every 5000 tests or 60 s and are
mirrored to `<run-dir>/progress.log`. Per-FAIL detail lives only in
JSONL — sample `progress.log` for liveness, never `tail -f`
`results.jsonl.partial`.

### Hang handling

The runner uses a single-thread `ExecutorService` to enforce `--timeout-ms`
per test. The karate-js engine doesn't poll `Thread.interrupt()`, so
`cancel(true)` can't stop the underlying thread. When a timeout fires, the
runner **retires the executor** (shuts it down, creates a fresh one) so
subsequent tests don't queue behind the stuck thread. Net cost of a genuine
hang: one abandoned daemon thread, one Timeout row in `results.jsonl`, a few
ms of recreate overhead.

For scripts / agents driving the runner: pass `--max-duration <ms>` as a
safety net and follow [Context discipline](#context-discipline).

---

## Skip list

There is only one concept: **SKIP**. A test matching any rule in
[`etc/expectations.yaml`](etc/expectations.yaml) is not run and appears as
`{"status":"SKIP",...}` in results. Everything else is attempted; failures
are failures.

Match order: `paths → flags → features → includes`. **First match wins.**
Every entry requires a `reason`.

**Precedence example.** A test at `test/language/statements/class/foo.js`
with `flags: [module]` and `features: [Symbol]` is skipped with the *module*
reason (the `flags` match fires before `features` is consulted). If you want
`features: [Symbol]` to win, don't have a matching flag rule.

Starter set covers Symbol, BigInt, generators, class syntax, Proxy, Reflect,
async iteration (`for await` / async generators / `Symbol.asyncIterator`),
`flags: [async]` tests (harness doneprintHandle gap, not an engine gap),
Temporal, TypedArray beyond Uint8Array, WeakRef, ArrayBuffer, and the suite
directories `test/intl402/`, `test/staging/`, `test/annexB/`. The
`async-functions` / `Promise` / `globalThis` feature skips were removed
when that surface landed. To add a skip: edit the YAML under the right section with a
`reason`. To remove a skip: delete the entry, re-run the relevant `--only`
glob, debug failures with `--single -v`.

**Adding a new unimplemented feature.** If you hit FAILs for an ES surface
the engine genuinely doesn't implement (e.g. `JSON.rawJSON` / `isRawJSON`
from ES2024), add a `features:` rule with the test262 feature flag name —
not a `paths:` rule. The feature names match what the tests declare in
their YAML frontmatter (`features: [json-parse-with-source]`), which is
also what `--single -v` prints under `features:`. See existing entries
for the exact shape; precedence rules above still apply.

---

## Results schema

Two JSONL files during a run:

- **`<run-dir>/results.jsonl.partial`** — appended per test as results
  arrive, flushed per write. **Run order, not sorted.** Deleted on clean
  exit; preserved on abort (`--max-duration` hit, Ctrl-C, JVM kill).
- **`<run-dir>/results.jsonl`** — canonical output, **sorted alphabetically
  by path**, atomically written at end-of-run (tmp + rename). This is what
  tooling reads.

**Dev mode (default):** only FAIL and SKIP rows are written. The pass
count comes from `run-meta.json` (`counts.pass`). The `Failure triage`
and `Diff two run-dirs` recipes are designed to work without PASS rows.

**`--full` mode:** PASS rows are also written, one per attempted test
that didn't fail or get skipped. Use when you need the canonical full
record (CI artifact, deep audit) or want the HTML report (which
`etc/run.sh` gates on `--full`).

Example line shape (same in both):

```jsonl
{"path":"test/language/expressions/addition/S11.6.1_A1.js","status":"PASS"}
{"path":"test/.../something.js","status":"FAIL","error_type":"TypeError","message":"foo is not a function"}
{"path":"test/.../bigint-test.js","status":"SKIP","reason":"BigInt not supported"}
```

(The PASS row only appears in `--full` mode.)

Error types are classified into:
`SyntaxError | TypeError | ReferenceError | RangeError | Error | Timeout |
Harness | Unknown` by inspecting message prefixes (the engine emits
`"TypeError: ..."` style messages at most failure sites). The classifier
itself is in `ErrorUtils`. Two buckets are assigned by the runner (not the
classifier) for negative tests: **`ExpectedThrow`** (a non-parse negative test
completed normally) and **`MissingParseError`** (a `phase: parse` negative test
parsed instead of being rejected — the engine is missing that early error; the
code then ran and usually tripped the harness `$DONOTEVALUATE()` marker). Keeping
`MissingParseError` distinct stops the unimplemented-early-error backlog from
hiding inside `Unknown` alongside genuine engine crashes.

---

## Recipes

### Debug one failing test

```sh
# Default: -v gives metadata + classification + location — usually enough
# to find the engine call site without dumping test source into context.
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -v" 2>&1 | tail -n 40

# Escalate to -vv (full source) only if -v didn't pinpoint the cause:
mvn -pl karate-js-test262 -o exec:java \
    -Dexec.args="--single <path> -vv" 2>&1 | tail -n 200
```

`-v` prints parsed YAML metadata (description / flags / features / includes
/ negative), the classification, and — if the engine attached a position
— a `location: <path>:<line>:<col>` line. `-vv` additionally prints the
full test source. `--single` does no file writes. **No HTML drill-down
page is generated** — the details.html report shows path + error_type +
message inline.

**Location-line caveat.** `location:` only appears when the engine
itself threw and attached a position. Two common FAIL shapes carry no
location and you should skip straight to reading the relevant built-in
source:
- `Test262Error: <expectation>` — the harness assertion fired
  inside the test's own JS, not the engine. Find the failure inside
  the test source (or look at what the test is asserting) and trace
  back to the engine method that built the wrong value.
- `Unknown: java.lang.StackOverflowError` / `NullPointerException` /
  other Java exceptions — uncaught Java throwables surface without a
  JS-level position. Grep the stack for the engine class.

### Failure triage

Compact rollups over `results.jsonl`. All return tens of lines, not
thousands. Use these instead of reading the raw JSONL when scoping a
slice or hunting for clusters.

```sh
RD=target/test262/run-<ts>            # the run-dir to analyze
JSONL=$RD/results.jsonl               # use .partial during an in-progress run

# PASS / FAIL / SKIP counts.
jq -r .status "$JSONL" | sort | uniq -c

# FAIL histogram by error_type — which classifier buckets dominate.
jq -r 'select(.status=="FAIL").error_type' "$JSONL" \
  | sort | uniq -c | sort -rn

# Top 20 FAIL message clusters (numbers normalized so near-duplicates merge).
jq -r 'select(.status=="FAIL").message' "$JSONL" \
  | sed 's/[0-9][0-9]*/N/g' \
  | sort | uniq -c | sort -rn | head -20

# FAIL counts per slice (two path components deep).
jq -r 'select(.status=="FAIL") | .path | split("/")[1:3] | join("/")' "$JSONL" \
  | sort | uniq -c | sort -rn | head -30

# One example failing path per error_type — for `--single -v` follow-up.
jq -r 'select(.status=="FAIL") | "\(.error_type)\t\(.path)"' "$JSONL" \
  | sort -u -k1,1 | head -20

# All FAILs under a specific slice — bounded with head, never raw.
jq -r 'select(.status=="FAIL" and (.path|startswith("test/language/statements/for-of"))) | .path' \
  "$JSONL" | head -30
```

### Diff two run-dirs (regression check)

FAIL-set difference — works in dev mode (no PASS rows needed). Capped
output: counts + first 10 of each list + per-slice cluster breakdown.
Assumes both runs covered the same `--only` scope (recorded in each
`run-meta.json` if you want to verify).

```sh
PREV=target/test262/run-<prev>/results.jsonl
CURR=target/test262/run-<curr>/results.jsonl

python3 - "$PREV" "$CURR" <<'PY'
import json, sys, collections
def fails(p):
    return {json.loads(l)['path'] for l in open(p) if json.loads(l)['status']=='FAIL'}
prev, curr = fails(sys.argv[1]), fails(sys.argv[2])
regr = sorted(curr - prev)   # newly failing — likely regressions
fixed = sorted(prev - curr)  # newly passing (or removed/skipped)
def by_slice(paths):
    c = collections.Counter('/'.join(p.split('/')[1:3]) for p in paths)
    return c.most_common(10)
def show(label, paths):
    print(f'{label}: {len(paths)}')
    for p in paths[:10]: print(f'  {p}')
    if len(paths) > 10: print(f'  ... {len(paths)-10} more')
    if paths: print(f'  by slice: {by_slice(paths)}')
show('Regressed (newly FAIL)', regr)
show('Fixed   (no longer FAIL)', fixed)
PY
```

Per-session safety check against the slice baseline. If `Regressed` is
non-zero, drill into a couple of representative paths with
`--single -v` — do **not** paste the full list into context. Note: a
path appearing under "Fixed" could mean it now PASSes *or* that it was
moved to SKIP / removed from scope; cross-check with the SKIP set if
ambiguous.

### Delegate a slice run

For re-probing a slice or triaging a cluster, spawn a `general-purpose`
sub-agent and require a digest. The sub-agent reads the full output;
you receive only the summary.

Prompt template (copy, fill in `<glob>`, paste into `Agent`):

> Run `etc/run.sh --only '<glob>' --max-duration 600000` from
> `karate-js-test262/`. After it completes, query the run-dir's
> `results.jsonl` (the runner prints `Run dir: <path>` on completion)
> and return **≤200 words**:
>
> - PASS / FAIL / SKIP counts for the slice.
> - Top 3 FAIL clusters (group by error_type + normalized message
>   prefix). For each: count, one example path, one example message.
> - Anything surprising: Timeouts, NPE-shaped errors, `Java heap space`,
>   `IllegalName` lambda leaks, parse-vs-runtime classification gaps.
>
> Do **not** paste raw FAIL lines, full test source, or JSONL
> contents. If you need to inspect a specific test, use
> `--single -v` and quote ≤3 relevant lines.

Use it for: slice probes, cluster triage, "did my engine change regress
anything" checks, post-edit slice re-runs. Skip for small targeted
lookups (one test, one symbol) — run those inline.

### Real-world smoke battery

A ~64-snippet battery of idiomatic modern JS (the constructs LLMs actually
emit: arrows, destructuring, spread, optional chaining, classes, async,
generators, regex named groups, …), each snippet self-checking and throwing
on a wrong result. Lives in [`etc/smoke/`](etc/smoke/) — `Smoke.java` evals
every `etc/smoke/snippets/*.js` in a fresh `Engine` and prints one
PASS/FAIL line per snippet plus a total.

```sh
mvn -f ../pom.xml -pl karate-js -o test-compile -q
# slf4j-api alone is not enough — JsParser's static init needs the module's
# full dependency set, or every snippet fails with NoClassDefFoundError.
mvn -f ../pom.xml -pl karate-js -o dependency:build-classpath -Dmdep.outputFile=/tmp/kjs-cp.txt -q
CP="../karate-js/target/classes:$(cat /tmp/kjs-cp.txt)"
javac -cp "$CP" -d target/smoke etc/smoke/Smoke.java
java -cp "$CP:target/smoke" Smoke etc/smoke/snippets
```

This is the fastest end-to-end answer to "does everyday JS still work" —
run it after engine changes for a 2-second gut check (it complements, not
replaces, the unit tests and slice diffs). Add a snippet when a real-world
breakage is found; a failing snippet is a roadmap item by definition.
TODO: promote into a proper JUnit test under `karate-js` so it runs in CI.
A 65-snippet extension battery (2026-08-16, each snippet Node-verified
first) scored 44/65 and refilled the P0/P1 tiers above; as each gap is
fixed, add its minimal repro here as a permanent snippet.

### Check performance after an engine change

The conformance suite allocates a fresh `Engine` per test (~50k tests); small
regressions compound into minutes of wall time. **Prefer profile mode** — the
30 s warm loop is JIT-stable and directly comparable to the
[reference table in JS_ENGINE.md](../docs/JS_ENGINE.md#performance-benchmarks).
For deliberate performance *work* (as opposed to this regression gut-check),
the plan, protocol and shipped-state live in
[docs/JS_PERF_PLAN.md](../docs/JS_PERF_PLAN.md).

```sh
mvn -pl karate-js -q test-compile

# Profile mode (30 s warm loop; JIT-stable, ~16k iterations averaged).
java -cp "karate-js/target/classes:karate-js/target/test-classes:$(find ~/.m2/repository -name 'slf4j-api-*.jar' | head -1)" \
    io.karatelabs.parser.EngineBenchmark profile

# Fast mode (median of 10 cold runs) — noisy, gut-check only
java -cp "…same classpath…" io.karatelabs.parser.EngineBenchmark
```

If averages move >±10%, understand why before merging. If unavoidable
(correctness > speed), update the reference table in `JS_ENGINE.md` in the
same commit.

### Bump the pinned test262 SHA

Edit `TEST262_SHA=...` at the top of `etc/fetch-test262.sh`, delete the local
`test262/` directory, re-run the script. All subsequent runs use the new
commit. Coordinate bumps with whoever else is iterating — the suite itself
evolves.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `expectations file not found: etc/expectations.yaml` | Wrong directory. `cd karate-js-test262` first. |
| `test262 directory not found: test262` | Haven't run `etc/fetch-test262.sh` yet. |
| `Failed to execute goal ... exec-maven-plugin ... on project karate-parent: 'mainClass' ... missing` | Used `-am` with `exec:java`. Don't — install `karate-js` separately and run without `-am`. |
| Engine change has no effect on test262 output | Forgot `mvn ... -pl karate-js -o install -DskipTests`. The runner uses the local Maven repo jar, not the reactor classpath. |
| `Test262Report` says `--run-dir <path> is required` | Pass the path the runner printed on completion: `--run-dir target/test262/run-<ts>`. `etc/run.sh` does this for you. |
| Where's my report? | The runner prints `Run dir: <path>` on completion. Look in `<path>/html/index.html`. Each invocation creates a fresh `run-<timestamp>/` dir; nothing is overwritten. |
| Suite hangs on one test | Infinite loop; watchdog kicks in at `--timeout-ms`. The inner executor is retired and replaced; a genuine hang leaks one daemon thread and keeps going. Bisect with `--only`, or add `--max-duration` as a safety net. |
| Driving from a script that must not block | Pass `--max-duration <ms>`. On hit, partial results written and `Aborted:` replaces `Summary:`. |
| Tests that used to pass now fail | Run `EngineBenchmark` too — perf regression sometimes manifests as timeouts before correctness. |
| `target/test262/` growing unbounded across iteration sessions | No auto-pruning; each run writes its own `run-<ts>/`. `mvn clean` wipes the lot. |

---

## Directory layout

```
karate-js-test262/
├── TEST262.md                         # this file
├── pom.xml                            # Maven module (deploy explicitly disabled)
├── etc/
│   ├── expectations.yaml              # declarative SKIP list (committed)
│   ├── fetch-test262.sh               # shallow clone of tc39/test262 at pinned SHA
│   └── run.sh                         # one-shot: install + run + HTML
├── src/main/java/…/test262/           # runner + report + helpers
├── src/test/java/…/test262/           # unit tests for the harness itself
├── src/main/resources/report/         # HTML/CSS/JS templates for the report
├── src/main/resources/logback.xml     # logger config (file appender → target/test262/)
├── test262/                           # [gitignored] the cloned suite
└── target/test262/                    # [gitignored] one subdir per run
    └── run-<timestamp>/               # self-contained per-run dir
        ├── results.jsonl              # per-test pass/fail/skip, sorted by path (end of run)
        ├── results.jsonl.partial      # live feed — appended per test, flushed; deleted on clean exit, kept on abort
        ├── run-meta.json              # per-run context (test262 SHA, karate-js ver+SHA, JDK, OS, started/ended, counts)
        ├── progress.log               # banner + [progress] lines + final summary
        └── html/                      # two-file static HTML report
            ├── index.html             # tree + per-slice summary tiles
            └── details.html           # full per-test list with search + status filter
```

Each run is self-contained and immutable; old runs persist until `mvn clean`.
The CI workflow uploads `target/test262/` (parent) as a single artifact.

---

## CI

A `workflow_dispatch`-only workflow at
[`.github/workflows/test262.yml`](../.github/workflows/test262.yml) runs
`etc/fetch-test262.sh` + the runner + the report, and uploads the whole
`target/test262/` directory as a single artifact. Never triggered
automatically — kick off from the Actions tab when you want a fresh run. Two
inputs (`only` and `timeout_ms`) default to full-suite / 10 s per test.

The module's `pom.xml` sets `maven.deploy.skip=true` / `gpg.skip=true` /
`skipPublishing=true` so the release workflow does not publish this module to
Maven Central.

---

## References

- [tc39/test262](https://github.com/tc39/test262) — the suite
- [test262 INTERPRETING.md](https://github.com/tc39/test262/blob/main/INTERPRETING.md) — authoritative runner spec
- [../docs/JS_ENGINE.md](../docs/JS_ENGINE.md) — engine architecture, slot family, prototype machinery, **spec invariants**, benchmarks
- [../karate-js/README.md](../karate-js/README.md) — what karate-js is and isn't
- [../docs/DESIGN.md](../docs/DESIGN.md) — wider project design principles

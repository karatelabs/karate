# ES6 `class` syntax — implementation plan

**STATUS (2026-05-30): Phase 1 LANDED** — constructor, instance/static methods,
get/set accessors, computed keys, class declarations + expressions, default
constructor, always-strict, constructor-without-`new` TypeError. Covered by
`JsClassTest`. Phases 2 (extends/super) and 3 (fields) below are NOT yet done;
this doc remains the reviewed design reference for them.


Goal: support real-world / LLM-written `class` code in karate-js. Pragmatic
desugaring onto the existing constructor-function + prototype machinery, not
spec-lawyer compliance. test262 `class` slice is the scorecard.

## Strategy

Desugar at **eval time** (no AST rewrite). A `class` evaluates to a constructor
`JsFunctionNode` whose `.prototype` object holds the instance methods, with
static methods/fields installed directly on the constructor function object.
This reuses the entire existing `new` / prototype-chain / `this`-binding path
(`Interpreter.invokeCallable` lines 564-607, `JsObject.setPrototype`,
`JsFunction.getFunctionPrototype`).

## Phasing (each phase independently commit-able + testable)

### Phase 1 — classes without inheritance
- `class Foo { constructor(){} method(){} get x(){} set x(v){} static s(){} [computed](){} }`
- Class declaration **and** class expression (`const C = class {...}`, named).
- Default constructor synthesis when none declared (`constructor(){}`).
- Always strict (force `strict=true` on the constructor + method JsFunctionNodes).

### Phase 2 — inheritance
- `class Bar extends Foo {}`, `super(...)` in constructor, `super.m()` in methods.
- `Bar.__proto__ = Foo` (static inheritance) and
  `Bar.prototype.__proto__ = Foo.prototype` (instance inheritance).

### Phase 3 — public fields (ES2022)
- Instance fields `x = 1` (evaluated with `this` bound, before constructor body
  for base classes / after `super()` returns for derived).
- Static fields `static x = 1`.

### Explicitly deferred (skip-listed, not attempted this session)
- Private `#x` fields/methods, static init blocks, `extends` of a built-in
  exotic (Array/Error subclassing with exotic behavior), full parser early
  errors (duplicate `constructor`, `super` outside derived ctor as SyntaxError),
  `new.target`.

---

## 1. Lexer (`JsLexer.java` / `TokenType.java`)

- Add keyword tokens `CLASS`, `EXTENDS`, `SUPER` to the `TokenType` enum
  (keyword=true) and to `JsLexer.keywordOrIdent()` length-switch (`class`=5,
  `super`=5, `extends`=7).
- `static` / `get` / `set` stay **contextual identifiers** (lex as `IDENT`),
  matched in the parser exactly like the existing `get`/`set` handling in
  `object_elem` (lines 1302-1304).

## 2. Parser (`JsParser.java` / `NodeType.java`)

New `NodeType`s: `CLASS_EXPR` (covers both decl + expr — disambiguated at eval
by whether it sits at statement position with a name), `CLASS_BODY`,
`CLASS_METHOD` (carries flags: static? kind=method|get|set|ctor),
`CLASS_FIELD`, `SUPER_EXPR`.

- **`class_expr()`**: `enter(CLASS_EXPR, CLASS)` → optional `IDENT` (name) →
  optional `extends` + `expr(13,…)` (the heritage expression) → `{` →
  loop `class_element()` → `}`. No `eos()` for declarations (ASI, mirrors
  `fn_expr` at statement line 578).
- Wire into `statement()` OR-chain (line 578-579 area): add `class_expr()`
  before `block(false)` so `class X {}` parses as a declaration; also reachable
  from the expression path (`expr_…`) so `const C = class {}` /
  `(class{})` work. **Disambiguation note**: `class` is now a keyword so the
  expression-start token set must include `CLASS`.
- **`class_element()`**: skip leading `;` (allowed); detect contextual
  `static` (IDENT + next is a key-start); detect `get`/`set` (mirror
  `object_elem` 1302-1323); parse method name (`IDENT` / string / number /
  `[expr]` computed); if next is `(` → method: nest an `FN_EXPR`
  (`fn_decl_args()` + `block(true)`) exactly like `object_elem` 1340-1348;
  else treat as a **field** (`= expr` optional, then ASI) for Phase 3.
- **`super`**: handled in the expression/postfix path. `super(...)` →
  `SUPER_EXPR` followed by `FN_CALL_ARGS`; `super.x` / `super[x]` → reuse the
  existing `REF_DOT_EXPR` / `REF_BRACKET_EXPR` builders with a `SUPER_EXPR`
  operand. `super` only needs to be recognized as a primary that yields
  `SUPER_EXPR`.

## 3. Interpreter (`Interpreter.java`)

### evalClassExpr (new, dispatched from `eval` switch + hoist)
1. If `extends` present, eval heritage → must be a constructable `JsCallable`
   `Parent` (or `null` for `extends null`).
2. Locate the `constructor` `CLASS_METHOD`; if absent synthesize a default
   (base: empty body; derived: `constructor(...args){ super(...args); }`).
3. Build the constructor `JsFunctionNode` from the ctor method's args+body
   with `strict=true`. Mark it `isClassConstructor=true`,
   `isDerived=(extends present)`.
4. Build the prototype object (`JsObject` = `ctorFn.getFunctionPrototype()`).
   For derived: `prototype.setPrototype(Parent.getMember("prototype"))` and
   `ctorFn.setPrototype(Parent)` (static inheritance).
5. For each non-ctor method: build a method `JsFunctionNode` (strict, with
   `homeObject` = prototype for instance / ctorFn for static). Install:
   - plain method → `target.putMember(name, fn)`
   - get/set → install as an `AccessorSlot` on the target
     (`Object.defineProperty`-style; same machinery used by object-literal
     accessors today).
   - computed name → eval key expr to a string first.
6. Bind the class name (declaration → `context.put(name, ctorFn)`, hoisted;
   named class expr → bind name in an inner scope visible to methods).
7. Return `ctorFn`.

### super dispatch — the load-bearing 20%
Add two fields to `JsFunctionNode`: `ObjectLike homeObject` and
`boolean isDerivedConstructor`. On **every** JsFunctionNode invocation, set
`callContext.activeFunction = jsFunc` (new transient field on CoreContext) so
`super` can resolve relative to the running method.

- **`super.m()` / `super.x`** (`evalSuperExpr` for the member case): resolve
  `proto = Object.getPrototypeOf(activeFunction.homeObject)`; read `m` off
  `proto` **with `receiver = this`** (so the inherited method/getter runs with
  the current instance as `this`). This is exactly the existing 3-arg
  `getMember(name, receiver, ctx)` seam.
- **`super(...)`** in a derived constructor: parent constructor is
  `Object.getPrototypeOf(activeFunction)` (= `ctorFn.__proto__` = `Parent`,
  set in step 4). Invoke `Parent` **with `this` = the current `newInstance`**
  (run its body to initialize the same instance), rather than letting it
  allocate a fresh object. Implement via a variant of `invokeAsConstructor`
  that takes an explicit `thisObject` instead of creating one.

### Pragmatic deviations (acceptable for the target workload, documented)
- Derived classes pre-create `this` (proto already chains to Parent.prototype)
  and run `super()` against it — no TDZ-on-`this`, no return-override of exotic
  built-ins. `class X extends Error/Array` won't get exotic behavior; flag +
  skip those test262 cases.
- `new.target` unsupported (deferred).

## 4. Tests / harness
- New `JsClassTest` (JUnit) covering: basic class, methods, static, get/set,
  computed keys, class expression, extends, super(), super.method(), default
  ctor, instance/static fields.
- A few `EvalTest` smoke cases.
- `etc/expectations.yaml`: remove the `class` syntax skip (or narrow it to the
  deferred sub-features by `features:` name — `class-fields-private`,
  `class-static-block`, etc.). Re-probe `--only 'test/language/statements/class/**'`
  and `expressions/class/**`; record before/after in the commit.
- Per-session ritual: unit tests, test262 no-regression diff, EngineBenchmark
  (parser hot-path: confirm adding `class` keyword + statement branch doesn't
  regress — a new keyword in `keywordOrIdent` and one more `||` in
  `statement()` should be negligible), karate-core consumer check.

## Review corrections (folded in — verified against source)

1. **`super(...)` needs a real construction refactor, not a "variant."** Both
   `invokeCallable` (572) and `invokeAsConstructor` (660) hard-code
   `newInstance = new JsObject()` — no seam to inject an explicit `this`.
   Extract a shared `construct(callable, thisOverride, args, …)`. `super()`
   must also *discard* the parent's return value (keep derived `this`),
   unlike the normal return-override at 602-607/676. **This touches the
   hottest path in the interpreter — it is the Phase-2 budget risk.**
2. **`activeFunction` must be set on EVERY JsFunctionNode invocation**, in all
   three call paths: `invokeCallable` (567-586), `invokeAsConstructor`
   (656-667), AND `JsFunctionNode.call()` (the host-callable path used by
   getters/callbacks, line 98). CallInfo is null on plain method calls, so it
   is NOT a usable hook. CoreContext does not auto-inherit the field — set it
   explicitly each frame (auto-restored since each call gets its own context).
3. **Use `getPrototype()` / `setPrototype()` directly** (JsObject 112-117) —
   no need to route super through a JS-level `Object.getPrototypeOf`.
4. **`extends Error` / `extends Array` is a genuine SKIP, not a shim.**
   `JsErrorConstructor.call()` does `new JsError(...)` and returns it, ignoring
   `this` (JsErrorConstructor 84-89), so `super(msg)` can't set `.message` on
   the derived instance. Skip-list these `features:` until exotic-subclassing
   is tackled separately.
5. **Constructor called without `new` must throw** `TypeError: Class
   constructor X cannot be invoked without 'new'` — add an `isClassConstructor`
   guard in `invokeCallable`. Missed in the draft.
6. **Method `.name` is never set** (`JsFunctionNode` only sets `length` from
   argCount, line 67) — set the method name so `obj.m.name` is correct.
7. **`JsFunctionNode` fields are `final`** — adding `homeObject` /
   `isDerivedConstructor` means either a wider constructor signature across all
   call sites or non-final post-construction setters. Pick the setter approach.
8. **TDZ is not free.** Class-decl name-before-decl ReferenceError and the
   named-class-expr inner binding need explicit handling; TDZ behavior should
   be skip-listed, not assumed. Named-expr inner scope is low-cost (the
   let/const capture at JsFunctionNode 72-87 picks it up if the name is bound
   in `declaredContext` before methods are built).
9. **Freebie:** `instanceof` already walks the proto chain (`Terms.instanceOf`,
   Interpreter 1070), so subclass `instanceof` works for free once the
   prototype link is set.

**Revised scope verdict (reviewer + me): ship Phase 1 alone this session**
(commit + test262 diff), then Phase 2 (extends/super) as a dedicated session
because its construction refactor risks regressing every existing `new`.
Defer Phase 3 (fields) entirely.

## Risks / open questions (original — now resolved above)
1. **super home-object threading** — is `callContext.activeFunction` the right
   seam, or does the existing `CallInfo` already carry enough? Any re-entrancy
   issue (method A calls method B; B's frame must see B's homeObject, restored
   to A's on return — should be automatic since each call gets its own
   CoreContext).
2. **Hoisting**: class declarations are NOT hoisted like functions (TDZ), but
   the engine hoists function decls (`hoistFunctionDeclarations` ~line 771).
   Need to ensure class decls are evaluated in source order, not hoisted, and
   the re-eval-skip logic (lines 407-421) doesn't wrongly skip them.
3. **Named class expression scope**: the class name binding visible inside
   methods but not outside — needs an inner scope. Worth the complexity for
   the target workload, or defer?
4. **`extends` of built-in** (`class AppError extends Error`) is extremely
   common in real code. Is the pragmatic "run Parent ctor on this" enough to
   make `new AppError('x').message === 'x'` work, given `JsErrorConstructor`
   allocates its own `JsError`? May need a targeted shim. Reviewer: assess.
5. Scope realism: is Phases 1+2 (+maybe 3) achievable in one focused session,
   or should fields be a clean follow-up?

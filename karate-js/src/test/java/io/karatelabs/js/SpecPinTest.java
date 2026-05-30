package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec invariants pinned ahead of the whole-engine refactor sweep
 * (plan: humble-conjuring-lighthouse). Tests here lock down behavior
 * that the sealed-Slot / eager-intrinsic / PropertyKey / property-access /
 * strict-mode / coercion changes must preserve.
 *
 * <p>If any of these tests start failing, the refactor has changed an
 * observable invariant and needs to be re-evaluated.
 */
class SpecPinTest extends EvalBase {

    // -------------------------------------------------------------------------
    // Sparse arrays — hasOwnProperty distinguishes HOLE from undefined.
    // Critical for HOLE-elimination experiment in mega-commit 1F.
    // (The `in` operator isn't supported by the parser today, so all these
    // sparse-vs-undefined invariants are pinned through hasOwnProperty.)
    // -------------------------------------------------------------------------

    @Test
    void sparseHole_hasOwnProperty_false() {
        assertEquals(false, eval("[,,,].hasOwnProperty(0)"));
        assertEquals(false, eval("[,,,1].hasOwnProperty(0)"));
        assertEquals(true, eval("[,,,1].hasOwnProperty(3)"));
    }

    @Test
    void explicitUndefined_hasOwnProperty_true() {
        assertEquals(true, eval("[undefined].hasOwnProperty(0)"));
    }

    @Test
    void sparseHole_readReturnsUndefined() {
        assertEquals("undefined", eval("typeof [,,,1][0]"));
        assertEquals(1, eval("[,,,1][3]"));
    }

    @Test
    void sparseHole_lengthIsCorrect() {
        assertEquals(4, eval("[,,,1].length"));
        assertEquals(3, eval("[,,,].length")); // trailing comma elision
    }

    // -------------------------------------------------------------------------
    // Accessor PropertyDescriptor shape — must remain {get, set, enumerable,
    // configurable}, NOT {value, writable, ...}. Critical for sealed Slot
    // (DataSlot vs AccessorSlot) and JsAccessor deletion.
    // -------------------------------------------------------------------------

    @Test
    void accessorDescriptor_hasAccessorShape() {
        assertEquals(true, eval(
                "var o = {}; Object.defineProperty(o, 'x', {get: function(){return 1;}});"
                        + " var d = Object.getOwnPropertyDescriptor(o, 'x');"
                        + " typeof d.get === 'function'"));
        assertEquals("undefined", eval(
                "var o = {}; Object.defineProperty(o, 'x', {get: function(){return 1;}});"
                        + " var d = Object.getOwnPropertyDescriptor(o, 'x');"
                        + " typeof d.value"));
        assertEquals("undefined", eval(
                "var o = {}; Object.defineProperty(o, 'x', {get: function(){return 1;}});"
                        + " var d = Object.getOwnPropertyDescriptor(o, 'x');"
                        + " typeof d.writable"));
    }

    @Test
    void dataDescriptor_hasDataShape() {
        assertEquals("undefined", eval(
                "var d = Object.getOwnPropertyDescriptor({a: 1}, 'a'); typeof d.get"));
        assertEquals("undefined", eval(
                "var d = Object.getOwnPropertyDescriptor({a: 1}, 'a'); typeof d.set"));
        assertEquals(1, eval("Object.getOwnPropertyDescriptor({a: 1}, 'a').value"));
        assertEquals(true, eval("Object.getOwnPropertyDescriptor({a: 1}, 'a').writable"));
    }

    @Test
    void getOnlyAccessor_writeIsLenient() {
        // get-only accessor: writes are silently ignored in sloppy mode.
        // (The strict-mode TypeError flip is pinned in strict_setGetterOnlyThrows.)
        assertEquals(7, eval(
                "var o = {}; Object.defineProperty(o, 'x', {get: function(){return 7;}});"
                        + " o.x = 99; o.x"));
    }

    @Test
    void setOnlyAccessor_readReturnsUndefined() {
        assertEquals("undefined", eval(
                "var captured; var o = {};"
                        + " Object.defineProperty(o, 'x', {set: function(v){captured = v;}});"
                        + " typeof o.x"));
    }

    // -------------------------------------------------------------------------
    // Spec-correct iteration — Object.values/entries on accessor descriptors
    // must invoke the getter, not surface as null. Pins refactor E's
    // ctx-aware {@link JsObject#jsEntries(CoreContext)} variant.
    // -------------------------------------------------------------------------

    @Test
    void objectValues_invokesAccessorGetter() {
        assertEquals(42, eval(
                "var o = {}; Object.defineProperty(o, 'x',"
                        + " {get: function(){return 42;}, enumerable: true});"
                        + " Object.values(o)[0]"));
    }

    @Test
    void objectEntries_invokesAccessorGetter() {
        assertEquals(7, eval(
                "var o = {}; Object.defineProperty(o, 'a',"
                        + " {get: function(){return 7;}, enumerable: true});"
                        + " Object.entries(o)[0][1]"));
    }

    // -------------------------------------------------------------------------
    // Method identity — eager intrinsic install must preserve `===` across
    // repeated reads. Critical for mega-commit 1C.
    // -------------------------------------------------------------------------

    @Test
    void prototypeMethod_identityStable() {
        assertEquals(true, eval("Array.prototype.push === Array.prototype.push"));
        assertEquals(true, eval("Object.prototype.hasOwnProperty === Object.prototype.hasOwnProperty"));
        assertEquals(true, eval("String.prototype.indexOf === String.prototype.indexOf"));
    }

    @Test
    void userPrototypeAssignment_winsOverIntrinsic() {
        // Foo.prototype = X must surface X verbatim — even when X is itself a
        // function. The auto-allocated prototype is the fallback for
        // un-assigned functions; explicit own-slot writes must win. (Locked
        // down to prevent regression of the old "fromSuper instanceof JsFunction"
        // carve-out that silently discarded function-valued assignments.)
        assertEquals(true, eval(
                "function Foo() {} function Bar() {} Foo.prototype = Bar; Foo.prototype === Bar"));
        assertEquals(true, eval(
                "function Foo() {} var p = {x: 1}; Foo.prototype = p; Foo.prototype === p"));
    }

    @Test
    void instanceMethodLookup_identityStable() {
        // arr.push and (same arr).push resolve to the same function via prototype chain.
        assertEquals(true, eval("var a = []; a.push === a.push"));
        // Across two arrays sharing Array.prototype, identity holds.
        assertEquals(true, eval("[].push === [].push"));
        // Instance method === prototype method.
        assertEquals(true, eval("var a = []; a.push === Array.prototype.push"));
    }

    @Test
    void mathConstantIdentity_stableForSameEngine() {
        assertEquals(true, eval("Math.PI === Math.PI"));
        assertEquals(true, eval("Math.abs === Math.abs"));
    }

    // -------------------------------------------------------------------------
    // Captured-binding mutability — closures mutate cells but cannot
    // structurally extend captured scope. Critical for BindingsStore
    // immutable flag in mega-commit 1E.
    // -------------------------------------------------------------------------

    @Test
    void closure_mutatesEnclosingLet() {
        assertEquals(2, eval(
                "function makeCounter() {"
                        + "  var n = 0;"
                        + "  return function() { return ++n; };"
                        + "}"
                        + "var c = makeCounter(); c(); c()"));
    }

    @Test
    void closure_capturesByReference_notValue() {
        assertEquals(42, eval(
                "var x = 1;"
                        + "function f() { return x; }"
                        + "x = 42;"
                        + "f()"));
    }

    @Test
    void closure_isolatedPerInvocation() {
        // Two counters share a function but have independent `n` cells.
        assertEquals(1, eval(
                "function makeCounter() { var n = 0; return function(){ return ++n; }; }"
                        + "var c1 = makeCounter();"
                        + "var c2 = makeCounter();"
                        + "c1(); c2()")); // c2's first call → 1, not 2
    }

    // -------------------------------------------------------------------------
    // Per-Engine isolation — built-in intrinsics survive across Engine
    // instances; user-set state on prototypes does not leak.
    // Critical for mega-commit 1D (per-Engine constructors).
    // -------------------------------------------------------------------------

    @Test
    void perEngine_intrinsicsAlwaysAvailable() {
        // Each fresh Engine sees Array.prototype.push, Math.PI, etc.
        assertEquals(true, new Engine().eval("typeof Array.prototype.push === 'function'"));
        assertEquals(true, new Engine().eval("typeof Math.PI === 'number'"));
        assertEquals(true, new Engine().eval("typeof Object.keys === 'function'"));
    }

    @Test
    void perEngine_userExtensionsDoNotLeak() {
        // Engine A extends Array.prototype; Engine B does not see it.
        Engine a = new Engine();
        a.eval("Array.prototype.fooBar = function() { return 42; };");
        assertEquals(42, a.eval("[].fooBar()"));
        Engine b = new Engine();
        assertEquals("undefined", b.eval("typeof [].fooBar"));
    }

    @Test
    void perEngine_freezeDoesNotLeak() {
        // freeze on Engine A's Object doesn't affect Engine B.
        Engine a = new Engine();
        a.eval("Object.freeze(Array.prototype);");
        Engine b = new Engine();
        // Engine B can still extend Array.prototype.
        b.eval("Array.prototype.fresh = function() { return 'ok'; };");
        assertEquals("ok", b.eval("[].fresh()"));
    }

    // -------------------------------------------------------------------------
    // Property attribute defaults — built-in intrinsic methods are W|C, no E.
    // Built-in numeric constants (Math.PI) are no-W, no-E, no-C.
    // length/name on functions are C-only. Critical for mega-commit 1A
    // (sealed Slot attrs byte must preserve these defaults).
    // -------------------------------------------------------------------------

    @Test
    void builtinMethod_attributes_writableConfigurable_notEnumerable() {
        assertEquals(true, eval(
                "var d = Object.getOwnPropertyDescriptor(Array.prototype, 'push');"
                        + " d.writable === true && d.configurable === true && d.enumerable === false"));
        assertEquals(true, eval(
                "var d = Object.getOwnPropertyDescriptor(Object.prototype, 'hasOwnProperty');"
                        + " d.writable === true && d.configurable === true && d.enumerable === false"));
    }

    @Test
    void mathPI_attributes_allFalse() {
        assertEquals(false, eval(
                "var d = Object.getOwnPropertyDescriptor(Math, 'PI'); d.writable"));
        assertEquals(false, eval(
                "var d = Object.getOwnPropertyDescriptor(Math, 'PI'); d.enumerable"));
        assertEquals(false, eval(
                "var d = Object.getOwnPropertyDescriptor(Math, 'PI'); d.configurable"));
    }

    @Test
    void functionLength_attributes_configurableOnly() {
        assertEquals(true, eval(
                "var d = Object.getOwnPropertyDescriptor(Math.cos, 'length');"
                        + " d.writable === false && d.enumerable === false && d.configurable === true"));
    }

    // -------------------------------------------------------------------------
    // Optional chaining (?.) propagation — critical for PropertyAccess unify
    // (mega-commit 2H, ?. baked into resolveProperty).
    // -------------------------------------------------------------------------

    @Test
    void optionalChain_propagatesNull() {
        assertEquals("undefined", eval("typeof (null?.foo)"));
        assertEquals("undefined", eval("typeof (undefined?.foo)"));
        assertEquals("undefined", eval("typeof (null?.foo?.bar)"));
        assertEquals("undefined", eval("typeof (null?.foo.bar)")); // short-circuit BEFORE the .bar
    }

    @Test
    void optionalChain_normalAccessOnNonNull() {
        assertEquals(1, eval("var o = {a: 1}; o?.a"));
        assertEquals(2, eval("var o = {a: {b: 2}}; o?.a?.b"));
    }

    @Test
    void optionalCall_short_circuit() {
        assertEquals("undefined", eval("typeof (null?.())"));
        assertEquals("undefined", eval("var o = {f: null}; typeof o.f?.()"));
    }

    @Test
    void optionalBracket_short_circuit() {
        assertEquals("undefined", eval("typeof (null?.[0])"));
        assertEquals(1, eval("[1, 2, 3]?.[0]"));
    }

    // -------------------------------------------------------------------------
    // Coercion — pinning current behavior so Terms.* in-place fixes
    // (mega-commit 2M) preserve the visible JS semantics.
    // -------------------------------------------------------------------------

    @Test
    void objectToNumber_NaN_notNull() {
        // Number({}) → NaN (spec ToNumber on object → ToPrimitive("number") → ...)
        assertEquals(true, eval("isNaN(Number({}))"));
        assertEquals(true, eval("isNaN(+{})"));
    }

    @Test
    void toPrimitive_hintNumber_callsValueOf() {
        assertEquals(7, eval(
                "var o = {valueOf: function(){return 7;}, toString: function(){return 'x';}};"
                        + " +o"));
    }

    @Test
    void toPrimitive_objectWithOnlyToString_concatsAsString() {
        // ES §13.15.3 binary `+`: ToPrimitive(o, "default") on a plain object,
        // which OrdinaryToPrimitive runs as valueOf-then-toString. With no
        // valueOf override, falls through to toString.
        assertEquals("x", eval(
                "var o = {toString: function(){return 'x';}};"
                        + " o + ''"));
    }

    @Test
    void toBoolean_emptyArrayIsTruthy() {
        // Spec: any object including [] is truthy under ToBoolean.
        assertEquals(true, eval("Boolean([])"));
        assertEquals(true, eval("Boolean({})"));
    }

    @Test
    void toBoolean_zeroAndEmptyAreFalsy() {
        assertEquals(false, eval("Boolean(0)"));
        assertEquals(false, eval("Boolean('')"));
        assertEquals(false, eval("Boolean(null)"));
        assertEquals(false, eval("Boolean(undefined)"));
        assertEquals(false, eval("Boolean(NaN)"));
    }

    // -------------------------------------------------------------------------
    // Sloppy mode (no directive) stays lenient: rejected [[Set]] / [[Delete]]
    // are silent no-ops. The strict-mode counterparts below pin the TypeError /
    // ReferenceError flips that a "use strict" directive activates. Both halves
    // are load-bearing — the lenient default is the documented engine policy.
    // -------------------------------------------------------------------------

    @Test
    void lenient_writeToFrozenIsSilent() {
        assertEquals(1, eval(
                "var o = {a: 1}; Object.freeze(o); o.a = 99; o.a"));
    }

    @Test
    void lenient_writeToReadOnlyIsSilent() {
        assertEquals(1, eval(
                "var o = {};"
                        + " Object.defineProperty(o, 'x', {value: 1, writable: false});"
                        + " o.x = 99; o.x"));
    }

    @Test
    void lenient_deleteNonConfigurableIsSilent() {
        assertEquals(true, eval(
                "var o = {};"
                        + " Object.defineProperty(o, 'x', {value: 1, configurable: false});"
                        + " delete o.x;"
                        + " o.hasOwnProperty('x')"));
    }

    // --- strict-mode flips (assert via JS try/catch so the engine's own error
    //     routing is part of the invariant) ------------------------------------

    /** Helper: run {@code body} under "use strict" and return the caught
     *  error's {@code .name}, or "no-throw" if it completed normally. */
    private Object strictErrName(String body) {
        return eval("'use strict';"
                + " try { " + body + "; 'no-throw'; } catch (e) { e.name; }");
    }

    @Test
    void strict_undeclaredAssignThrowsReferenceError() {
        assertEquals("ReferenceError", strictErrName("undeclaredName = 1"));
        // sloppy: same code creates an implicit global (no throw).
        assertEquals(1, eval("(function(){ sloppyGlobal = 1; return sloppyGlobal; })()"));
    }

    @Test
    void strict_writeToFrozenThrowsTypeError() {
        assertEquals("TypeError",
                strictErrName("var o = {a: 1}; Object.freeze(o); o.a = 99"));
    }

    @Test
    void strict_writeToReadOnlyThrowsTypeError() {
        assertEquals("TypeError", strictErrName(
                "var o = {}; Object.defineProperty(o, 'x', {value: 1, writable: false}); o.x = 9"));
    }

    @Test
    void strict_setGetterOnlyThrowsTypeError() {
        assertEquals("TypeError", strictErrName(
                "var o = {}; Object.defineProperty(o, 'x', {get: function(){return 7;}}); o.x = 9"));
    }

    @Test
    void strict_addToNonExtensibleThrowsTypeError() {
        assertEquals("TypeError", strictErrName(
                "var o = {}; Object.preventExtensions(o); o.y = 1"));
    }

    @Test
    void strict_deleteNonConfigurableThrowsTypeError() {
        assertEquals("TypeError", strictErrName(
                "var o = {}; Object.defineProperty(o, 'x', {value: 1, configurable: false}); delete o.x"));
    }

    @Test
    void strict_thisInPlainCallIsUndefined() {
        // sloppy: `this` substitutes to the global object → typeof "object".
        assertEquals("object", eval("function f(){ return typeof this; } f()"));
        // strict: no substitution → undefined.
        assertEquals("undefined", eval(
                "'use strict'; function f(){ return typeof this; } f()"));
    }

    @Test
    void strict_isLexicallyInherited() {
        // A nested function defined inside a strict function is itself strict,
        // even without its own directive: the undeclared assign throws.
        assertEquals("ReferenceError", strictErrName(
                "function outer(){ function inner(){ leaked = 1; } inner(); } outer()"));
    }

    @Test
    void strict_directiveOnlyAppliesToOwnAndNestedScopes() {
        // A "use strict" *inside* a function does not leak to the caller's
        // sloppy scope: the implicit global after the call still works.
        assertEquals(2, eval(
                "function strictFn(){ 'use strict'; return 1; }"
                        + " strictFn(); afterCall = 2; afterCall"));
    }

    // -------------------------------------------------------------------------
    // Object.freeze / seal / preventExtensions on JsArray — indexed-write
    // enforcement that the dense `list` backing store also honors (the
    // namedProps overrides honored attrs all along; the dense list path
    // didn't until the freeze-enforcement work landed).
    // -------------------------------------------------------------------------

    @Test
    void lenient_writeToFrozenArrayIndexIsSilent() {
        assertEquals(1, eval(
                "var a = [1, 2, 3]; Object.freeze(a); a[0] = 99; a[0]"));
    }

    @Test
    void lenient_extendFrozenArrayIsSilent() {
        assertEquals(3, eval(
                "var a = [1, 2, 3]; Object.freeze(a); a[5] = 'x'; a.length"));
    }

    @Test
    void frozenArrayDescriptorReportsNonWritableNonConfigurable() {
        assertEquals(false, eval(
                "var a = [1]; Object.freeze(a);"
                        + " Object.getOwnPropertyDescriptor(a, 0).writable"));
        assertEquals(false, eval(
                "var a = [1]; Object.freeze(a);"
                        + " Object.getOwnPropertyDescriptor(a, 0).configurable"));
    }

    @Test
    void sealedArrayAllowsExistingIndexWriteButBlocksNewIndex() {
        // Sealed: existing-index modification still allowed (writable=true);
        // adding a new index is blocked (non-extensible).
        assertEquals(99, eval(
                "var a = [1, 2, 3]; Object.seal(a); a[0] = 99; a[0]"));
        assertEquals(3, eval(
                "var a = [1, 2, 3]; Object.seal(a); a[5] = 'x'; a.length"));
    }

    @Test
    void sealedArrayDescriptorReportsNonConfigurableButWritable() {
        assertEquals(true, eval(
                "var a = [1]; Object.seal(a);"
                        + " Object.getOwnPropertyDescriptor(a, 0).writable"));
        assertEquals(false, eval(
                "var a = [1]; Object.seal(a);"
                        + " Object.getOwnPropertyDescriptor(a, 0).configurable"));
    }

    @Test
    void nonExtensibleArrayBlocksNewIndexButAllowsExisting() {
        assertEquals(99, eval(
                "var a = [1, 2, 3]; Object.preventExtensions(a); a[0] = 99; a[0]"));
        assertEquals(3, eval(
                "var a = [1, 2, 3]; Object.preventExtensions(a); a[5] = 'x'; a.length"));
    }

    @Test
    void nonExtensibleArrayBlocksLengthExtension() {
        assertEquals(3, eval(
                "var a = [1, 2, 3]; Object.preventExtensions(a); a.length = 10; a.length"));
    }

    @Test
    void frozenArrayBlocksHoleFill() {
        // Filling a HOLE creates a new own property — frozen blocks it.
        // Use [1,,3] not [,,,] because the implementation reads list[i] for
        // hole detection; HOLE positions count as "key absent".
        assertEquals(false, eval(
                "var a = [1,,3]; Object.freeze(a); a[1] = 2; a.hasOwnProperty(1)"));
    }

    @Test
    void pastEndIndexedWritePadsWithHoles() {
        // Spec: arr[i] = x for i >= length extends length and leaves
        // [oldLen, i) as absent indices (sparse holes), not as explicit
        // undefined. So hasOwnProperty must report false for the gap.
        assertEquals(false, eval("var a = []; a[5] = 'x'; a.hasOwnProperty(0)"));
        assertEquals(false, eval("var a = []; a[5] = 'x'; a.hasOwnProperty(4)"));
        assertEquals(true, eval("var a = []; a[5] = 'x'; a.hasOwnProperty(5)"));
        assertEquals(6, eval("var a = []; a[5] = 'x'; a.length"));
        // The gap reads as undefined per JS semantics — same as a literal
        // hole. typeof of a hole is "undefined".
        assertEquals("undefined", eval("var a = []; a[5] = 'x'; typeof a[0]"));
    }

    @Test
    void joinEmitsEmptyForHoles() {
        // Spec §23.1.3.18: join walks 0..length-1, holes contribute "".
        // Pre-rewrite, join used the hole-skipping {@code jsEntries} which
        // produced "0,3" — wrong. Locked down so a future "use jsEntries
        // for join" simplification doesn't regress.
        assertEquals("0,,,3", eval("[0,,,3].join()"));
        assertEquals("0,,,3", eval("var x=[]; x[0]=0; x[3]=3; x.join()"));
        assertEquals(",,,", eval("[,,,,].join()")); // 4 holes → 3 commas
    }

    @Test
    void toStringEmitsEmptyForHoles() {
        // Array.prototype.toString delegates to join — same hole semantics.
        assertEquals("0,,,3", eval("[0,,,3].toString()"));
    }

    // -------------------------------------------------------------------------
    // Object.getPrototypeOf dispatches via ObjectLike for all three storage
    // shapes — JsObject, JsArray, and Prototype singletons. Without the
    // unified ObjectLike branch, Set.prototype / Map.prototype etc. wrongly
    // returned null. See JS_ENGINE.md § Prototype machinery.
    // -------------------------------------------------------------------------

    @Test
    void getPrototypeOf_setPrototype_returnsObjectPrototype() {
        assertEquals(true, eval("Object.getPrototypeOf(Set.prototype) === Object.prototype"));
        assertEquals(true, eval("Object.getPrototypeOf(Map.prototype) === Object.prototype"));
        assertEquals(true, eval("Object.getPrototypeOf(Array.prototype) === Object.prototype"));
    }

    // -------------------------------------------------------------------------
    // ES2025 Set.prototype set-algebra (§24.2.4) — bypasses Set.prototype.add
    // (verified by test262 add-not-called.js); honors size-based ordering;
    // normalizes -0 to +0 on values pulled from foreign keys() iteration.
    // -------------------------------------------------------------------------

    @Test
    void setUnion_combinesSetsPreservingOrder() {
        assertEquals("1,2,3", eval(
                "Array.from(new Set([1,2]).union(new Set([2,3]))).join(',')"));
    }

    @Test
    void setIntersection_orderFollowsSmallerOperand() {
        // this.size <= other.size: order from this.
        assertEquals("1,3", eval(
                "Array.from(new Set([1,3,5]).intersection(new Set([3,2,1]))).join(',')"));
        // this.size > other.size: order from other.
        assertEquals("1,3", eval(
                "Array.from(new Set([3,2,1,0]).intersection(new Set([1,3,5]))).join(',')"));
    }

    @Test
    void setDifferenceAndSymDiff_basics() {
        assertEquals("1", eval(
                "Array.from(new Set([1,2]).difference(new Set([2,3]))).join(',')"));
        assertEquals("1,3", eval(
                "Array.from(new Set([1,2]).symmetricDifference(new Set([2,3]))).join(',')"));
    }

    @Test
    void setSubsetSupersetDisjoint_basics() {
        assertEquals(true, eval("new Set([1,2]).isSubsetOf(new Set([1,2,3]))"));
        assertEquals(false, eval("new Set([1,2]).isSubsetOf(new Set([2,3]))"));
        assertEquals(true, eval("new Set([1,2,3]).isSupersetOf(new Set([1,2]))"));
        assertEquals(true, eval("new Set([1,2]).isDisjointFrom(new Set([3,4]))"));
        assertEquals(false, eval("new Set([1,2]).isDisjointFrom(new Set([2,3]))"));
    }

    // -------------------------------------------------------------------------
    // ES2024 grouping — Object.groupBy returns a null-prototype object;
    // Map.groupBy returns a fresh Map with -0 → +0 key normalization.
    // -------------------------------------------------------------------------

    @Test
    void objectGroupBy_returnsNullPrototype() {
        assertEquals(true, eval(
                "var o = Object.groupBy([1,2,3], i => i % 2 ? 'odd' : 'even');"
                        + " Object.getPrototypeOf(o) === null"));
        assertEquals("undefined", eval(
                "typeof Object.groupBy([1], () => 'k').hasOwnProperty"));
    }

    @Test
    void mapGroupBy_normalizesNegativeZero() {
        // -0 and +0 collapse into a single Map key after spec normalization.
        assertEquals(1, eval(
                "var m = Map.groupBy([-0, +0], i => i); m.size"));
        assertEquals(2, eval(
                "var m = Map.groupBy([-0, +0], i => i); m.get(0).length"));
    }

    // -------------------------------------------------------------------------
    // ES2025 upsert: Map.prototype.{getOrInsert, getOrInsertComputed}.
    // getOrInsertComputed must NOT invoke the callback when the key is
    // already present, and must surface Java-null callback returns as
    // undefined (no `null` leaking into JS land).
    // -------------------------------------------------------------------------

    @Test
    void mapGetOrInsert_returnsExistingValueWithoutOverwrite() {
        assertEquals("a", eval("var m = new Map([[1,'a']]); m.getOrInsert(1, 'b')"));
        assertEquals("a", eval("var m = new Map([[1,'a']]); m.getOrInsert(1, 'b'); m.get(1)"));
    }

    @Test
    void mapGetOrInsertComputed_callbackSkippedWhenKeyPresent() {
        assertEquals(false, eval(
                "var called = false;"
                        + " var m = new Map([[1,'a']]);"
                        + " m.getOrInsertComputed(1, () => { called = true; return 'b'; });"
                        + " called"));
    }

    @Test
    void mapGetOrInsertComputed_voidReturnSurfacesAsUndefined() {
        assertEquals("undefined", eval(
                "var m = new Map();"
                        + " m.getOrInsertComputed(1, () => {});"
                        + " typeof m.get(1)"));
    }

    @Test
    void popInvokesPrototypeGetterForHoleAtLastIndex() {
        // Spec: Array.prototype.pop step 4d Get(O, ToString(len-1)) walks
        // the proto chain when the index is a hole (own property absent).
        // A getter installed on Array.prototype["0"] for [<hole>] fires
        // exactly once during pop. Pre-rewrite never invoked the getter.
        assertEquals(1, eval(
                "var a = new Array(1); var calls = 0;"
                        + " Object.defineProperty(Array.prototype, '0',"
                        + "   {get: function(){ calls++; }, configurable: true});"
                        + " try { a.pop(); } catch (e) {}"
                        + " var c = calls;"
                        + " delete Array.prototype['0'];"
                        + " c"));
    }

    // -------------------------------------------------------------------------
    // Abrupt completion from an if-statement test expression.
    //
    // The engine propagates errors via {@code context.stopAndThrow} (a
    // sentinel-style mechanism) rather than Java exceptions, so each
    // control-flow site that evaluates a sub-expression must check
    // {@code isStopped()} before acting on the result. {@link
    // Interpreter#evalIfStmt} previously ignored the stop signal; a thrown
    // error inside the condition surfaced as an undefined return, the
    // truthy check read it as falsy, and the else-branch silently ran. The
    // pending throw was eaten before reaching the surrounding try/catch.
    //
    // Pinned because a regression here is silent in production code (a
    // catch block runs with the wrong message or the throw never surfaces
    // at all) and was the root cause of ~10 test262 length-descriptor
    // verifyProperty failures whose isWritable RangeError got swallowed by
    // the inner if.
    // -------------------------------------------------------------------------

    @Test
    void ifConditionThrowPropagatesToCatch() {
        // Spec: a thrown exception in the if condition skips both branches
        // and propagates to the surrounding catch.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { if (thrower()) msg='if-true'; else msg='if-false'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void ifConditionThrowInOrChain_propagates() {
        // Same invariant exercised through a complex condition (||, !==).
        // Mirrors {@code propertyHelper.verifyProperty}'s
        // {@code desc.writable !== isWritable(obj, name)} call shape.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { if (false || true !== thrower()) msg='if-true'; else msg='if-false'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void whileConditionThrow_propagates() {
        // §14.7.3 abrupt completion in the while loop-test must skip the body
        // and surface the throw to the surrounding catch — was: body silently ran
        // because Terms.isTruthy(thrownJsError) returned true.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { while (thrower()) msg='body-ran'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void doWhileConditionThrow_propagates() {
        // §14.7.2 doWhile evaluates body first, then test; if test throws,
        // the second iteration must not run and the throw must propagate.
        // Body should have run exactly once before the throw.
        assertEquals("caught:boom:1", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch'; var count = 0;"
                        + " try { do { count++; } while (thrower()); }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg + ':' + count"));
    }

    @Test
    void forConditionThrow_propagates() {
        // §14.7.4 abrupt completion in the C-style for loop-test must exit
        // without running the body or the increment.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { for (var i=0; thrower(); i++) msg='body-ran'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void switchDiscriminantThrow_propagates() {
        // §14.12 abrupt completion in the switch discriminant must skip the body.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { switch (thrower()) { case 1: msg='one'; break; default: msg='dflt'; } }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void switchCaseLabelThrow_propagates() {
        // Abrupt completion in a CaseClause expression must propagate.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { switch (1) { case thrower(): msg='matched'; break; default: msg='dflt'; } }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void ternaryTestThrow_propagates() {
        // §13.14 abrupt completion in the ?: test must skip both branches.
        assertEquals("caught:boom", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var msg = 'no-catch';"
                        + " try { msg = thrower() ? 'a' : 'b'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg"));
    }

    @Test
    void logicAndShortCircuitOnThrow_propagates() {
        // §13.13 abrupt completion in the LHS of && / || must not evaluate the RHS.
        // rhsRan must remain false because the throw short-circuits.
        assertEquals("caught:boom:false", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var rhsRan = false;"
                        + " function rhs(){ rhsRan = true; return 1; }"
                        + " var msg = 'no-catch';"
                        + " try { var x = thrower() && rhs(); msg='unreached'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg + ':' + rhsRan"));
    }

    @Test
    void logicNullishShortCircuitOnThrow_propagates() {
        // §13.13 ?? must propagate LHS throw without evaluating RHS.
        assertEquals("caught:boom:false", eval(
                "function thrower(){ throw new Error('boom'); }"
                        + " var rhsRan = false;"
                        + " function rhs(){ rhsRan = true; return 1; }"
                        + " var msg = 'no-catch';"
                        + " try { var x = thrower() ?? rhs(); msg='unreached'; }"
                        + " catch (e) { msg = 'caught:' + e.message; }"
                        + " msg + ':' + rhsRan"));
    }

    // -------------------------------------------------------------------------
    // Generic descriptor preserves existing accessor type.
    //
    // Spec ValidateAndApplyPropertyDescriptor §10.1.6.3: when the new
    // descriptor specifies neither value/writable nor get/set, the existing
    // descriptor's *type* is preserved — only the attribute byte changes.
    // Pre-fix {@link JsObjectConstructor#defineProperty} clobbered an
    // existing AccessorSlot with a fresh DataSlot carrying undefined when
    // the new descriptor had only enumerable/configurable fields.
    // -------------------------------------------------------------------------

    @Test
    void genericDescriptor_onAccessor_preservesGetSet() {
        assertEquals("data", eval(
                "var obj = {}; obj.v = 'data';"
                        + " Object.defineProperty(obj, '0',"
                        + "   {get: function(){ return obj.v; },"
                        + "    set: function(x){ obj.v = x; },"
                        + "    enumerable: true, configurable: true});"
                        + " Object.defineProperty(obj, '0', {enumerable: false});"
                        + " obj['0']"));
    }

    @Test
    void genericDescriptor_onAccessor_preservesShape() {
        assertEquals(true, eval(
                "var obj = {};"
                        + " Object.defineProperty(obj, 'x', {get: function(){ return 7; }, configurable: true});"
                        + " Object.defineProperty(obj, 'x', {enumerable: false});"
                        + " var d = Object.getOwnPropertyDescriptor(obj, 'x');"
                        + " typeof d.get === 'function' && d.value === undefined"));
    }

    // -------------------------------------------------------------------------
    // for-in walks the prototype chain for enumerable string keys.
    //
    // Spec §14.7.5.6 EnumerateObjectProperties yields enumerable own string
    // keys at every chain level, dedup'd. {@link Terms#forInIterable} is
    // the back-end. Earlier toIterable-only path yielded own properties
    // only — silent miss for the inherited case (test262
    // {@code defineProperty/15.2.3.6-4-{404,414,419,580,585,590,595}}).
    // -------------------------------------------------------------------------

    @Test
    void forIn_walksPrototypeChain_yieldsInheritedEnumerable() {
        assertEquals(true, eval(
                "Object.defineProperty(Function.prototype, 'p',"
                        + "   {value: 1, writable: true, enumerable: true, configurable: true});"
                        + " var fn = function(){};"
                        + " var found = false;"
                        + " for (var k in fn) { if (k === 'p') found = true; }"
                        + " delete Function.prototype.p;"
                        + " found"));
    }

    @Test
    void forIn_skipsInheritedNonEnumerable() {
        assertEquals(false, eval(
                "Object.defineProperty(Function.prototype, 'p',"
                        + "   {value: 1, writable: true, enumerable: false, configurable: true});"
                        + " var fn = function(){};"
                        + " var found = false;"
                        + " for (var k in fn) { if (k === 'p') found = true; }"
                        + " delete Function.prototype.p;"
                        + " found"));
    }

    // -------------------------------------------------------------------------
    // defineProperty on a Prototype with a data descriptor stores the attrs.
    //
    // {@link JsObjectConstructor#applyDefine} previously fell through to
    // {@code putMember} for Prototype targets, dropping the descriptor's
    // attribute byte. {@link Prototype#defineOwn} carries it now so that
    // the for-in enumerable filter (above) and getOwnPropertyDescriptor
    // both see the spec-correct attrs.
    // -------------------------------------------------------------------------

    @Test
    void defineProperty_onPrototype_dataDescriptor_storesAttrs() {
        assertEquals(false, eval(
                "Object.defineProperty(Function.prototype, 'p',"
                        + "   {value: 1, writable: true, enumerable: false, configurable: true});"
                        + " var d = Object.getOwnPropertyDescriptor(Function.prototype, 'p');"
                        + " var enumerable = d.enumerable;"
                        + " delete Function.prototype.p;"
                        + " enumerable"));
    }

    // -------------------------------------------------------------------------
    // Reading a literal-null own value on a JsArray surfaces as null,
    // not undefined.
    //
    // {@link PropertyAccess#getByName}'s {@code isFound} fallback wrongly
    // converted a found-but-null result to undefined when the receiver was
    // a JsArray (the JsObject branch already had a containsKey-first
    // bypass; the JsArray branch did not). Test262
    // {@code defineProperty/15.2.3.6-4-216} writes
    // {@code defineProperty(arr, "0", {value: null})} and reads back —
    // {@code arr[0] === null} must hold.
    // -------------------------------------------------------------------------

    @Test
    void jsArray_definePropertyValueNull_readsAsNull() {
        assertEquals(true, eval(
                "var a = []; Object.defineProperty(a, '0', {value: null});"
                        + " a[0] === null"));
    }

    // -------------------------------------------------------------------------
    // Object.defineProperty rejects a non-callable, non-undefined getter or
    // setter with TypeError. Per spec ToPropertyDescriptor §6.2.5.5: the
    // get/set field must be undefined or callable; null is non-callable
    // non-undefined → TypeError. Pre-fix our engine accepted null silently.
    // -------------------------------------------------------------------------

    @Test
    void definePropertyNullGetter_throwsTypeError() {
        assertEquals(true, eval(
                "var threw = false;"
                        + " try { Object.defineProperty({}, 'x', {get: null}); }"
                        + " catch (e) { threw = e instanceof TypeError; }"
                        + " threw"));
    }

    @Test
    void definePropertyNullSetter_throwsTypeError() {
        assertEquals(true, eval(
                "var threw = false;"
                        + " try { Object.defineProperty({}, 'x', {set: null}); }"
                        + " catch (e) { threw = e instanceof TypeError; }"
                        + " threw"));
    }

    // -------------------------------------------------------------------------
    // Parser strict-mode early errors (JsParser.checkStrictEarlyErrors). Each is
    // a SyntaxError at parse phase under strict code AND a no-op under sloppy
    // code — the dual invariant is load-bearing: the checks are strict-gated, so
    // a regression that fires them in sloppy mode would break idiomatic JS.
    // Strictness is lexical — program prologue, function-body prologue, or class
    // body — so we pin a representative spread of each.
    // -------------------------------------------------------------------------

    private static void assertParseError(String src) {
        // A strict early error must abort at parse phase, before any evaluation.
        assertThrows(io.karatelabs.parser.ParserException.class, () -> new Engine().eval(src));
    }

    @Test
    void strict_octalLiteralIsParseError() {
        assertParseError("'use strict'; var n = 0755;");
        assertParseError("'use strict'; var n = 08;");          // NonOctalDecimal
        assertParseError("'use strict'; var n = 09;");
        // sloppy: legacy octal / non-octal-decimal forms still parse (the early
        // error is strict-only; their Annex-B numeric value is a separate concern).
        assertEquals("number", eval("typeof 0755"));
        assertEquals("number", eval("typeof 08"));
        // strict but legitimately non-octal numeric forms still parse.
        assertEquals(255, eval("'use strict'; var n = 0xff; n"));
        assertEquals(0.5, eval("'use strict'; var n = 0.5; n"));
        assertEquals(0, eval("'use strict'; var n = 0; n"));
    }

    @Test
    void strict_assignToEvalOrArgumentsIsParseError() {
        assertParseError("'use strict'; eval = 1;");
        assertParseError("'use strict'; arguments = 1;");
        assertParseError("'use strict'; eval += 1;");
        assertParseError("'use strict'; ++arguments;");
        // sloppy: assigning to eval / arguments is allowed.
        assertEquals(1, eval("eval = 1; eval"));
        // member targets are fine even in strict mode.
        assertEquals("undefined", eval("'use strict'; var o = {}; o.eval = 1; typeof undefinedX"));
    }

    @Test
    void strict_bindEvalOrArgumentsIsParseError() {
        assertParseError("'use strict'; var eval = 1;");
        assertParseError("'use strict'; let arguments = 1;");
        assertParseError("'use strict'; function eval() {}");
        assertParseError("'use strict'; function f(eval) {}");
        assertParseError("'use strict'; function f(arguments) {}");
    }

    @Test
    void strict_duplicateParametersIsParseError() {
        assertParseError("'use strict'; function f(a, a) {}");
        assertParseError("'use strict'; function f(a, b, a) {}");
        assertParseError("'use strict'; var g = function(x, x) {};");
        // sloppy: duplicate simple parameters are permitted (last wins).
        assertEquals(2, eval("function f(a, a) { return a; } f(1, 2)"));
    }

    @Test
    void strict_isLexical_functionBodyAndInheritance() {
        // A directive inside a function body makes that function (and nested
        // functions) strict — the octal in the nested body is a parse error.
        assertParseError("function outer() { 'use strict'; function inner() { return 0755; } }");
        // ...but a sibling sloppy function in the same program is unaffected.
        assertEquals("number", eval(
                "function strictFn() { 'use strict'; return 1; }"
                        + " function sloppyFn() { return 0755; } typeof sloppyFn()"));
    }

    @Test
    void strict_classBodyIsAlwaysStrict() {
        // Class bodies are strict regardless of any enclosing directive (§15.7):
        // a duplicate-parameter method is a parse error even with no directive.
        assertParseError("class C { m(a, a) {} }");
        assertParseError("class C { m() { return 0755; } }");
    }

    @Test
    void strict_parenthesizedDirectiveDoesNotActivate() {
        // ("use strict") is a ParenthesizedExpression, not a directive prologue,
        // so the octal that follows stays legal (parses without a SyntaxError).
        assertEquals("number", eval("(\"use strict\"); typeof 0755"));
    }

    // -------------------------------------------------------------------------
    // Duplicate-BoundNames early errors over binding patterns
    // (JsParser.checkFormalParameters / checkCatchParameter / collectBoundNames).
    // Duplicate bound names are a SyntaxError for arrow params, non-simple
    // parameter lists, and catch params ALWAYS (not strict-gated); simple
    // duplicate params in a sloppy non-arrow function stay legal. BoundNames
    // mirror the binding structure — keys, defaults, and renamed targets must
    // NOT false-positive, the load-bearing guard the sweep is built around.
    // -------------------------------------------------------------------------

    @Test
    void dupParams_arrowIsAlwaysParseError() {
        assertParseError("var f = (a, a) => a;");           // arrow: UniqueFormalParameters
        assertParseError("var f = (a, b, a) => a;");
        // sloppy non-arrow simple list stays legal (last wins).
        assertEquals(2, eval("function f(a, a) { return a; } f(1, 2)"));
    }

    @Test
    void dupParams_nonSimpleListIsAlwaysParseError() {
        assertParseError("function f(a, a = 1) {}");         // has a default -> non-simple
        assertParseError("function f({a}, a) {}");           // has a pattern  -> non-simple
        assertParseError("function f(a, ...a) {}");          // has a rest     -> non-simple
        assertParseError("function f([a, a]) {}");           // dup inside one array pattern
        assertParseError("function f({a, b: a}) {}");        // shorthand a + target a -> dup
    }

    @Test
    void boundNames_mirrorStructure_noFalsePositive() {
        // Key, default, and renamed targets are NOT bound names of the property.
        assertEquals(12, eval("var f = ({a: x = 9, b: y}) => x + y; f({b: 3})"));  // x defaults to 9, y=3
        assertEquals(3, eval("function f({x: a, y: b}) { return a + b; } f({x: 1, y: 2})"));
        // Duplicate property KEY but distinct binding targets is valid (only
        // duplicate bound NAMES are an error).
        assertEquals(2, eval("function f({x: a, x: b}) { return b; } f({x: 2})"));
        // Nested patterns with all-distinct names parse and bind correctly.
        assertEquals(6, eval("var f = ([a, {b}, [c]]) => a + b + c; f([1, {b: 2}, [3]])"));
    }

    @Test
    void dupCatchParam_isAlwaysParseError() {
        assertParseError("try {} catch ([x, x]) {}");
        assertParseError("try {} catch ({a, b: a}) {}");
        // distinct destructured catch names are fine; simple catch never collides.
        assertEquals(3, eval("try { throw [1, 2]; } catch ([x, y]) { x + y; }"));
        assertEquals("ok", eval("try { throw 'ok'; } catch (e) { e; }"));
    }

    @Test
    void evalArguments_boundInsidePattern_strictOnly() {
        assertParseError("'use strict'; function f({eval}) {}");
        assertParseError("'use strict'; function f([arguments]) {}");
        assertParseError("'use strict'; var {eval} = {};");
        assertParseError("'use strict'; try {} catch ({arguments}) {}");
        // sloppy: eval/arguments bound inside a pattern is permitted.
        assertEquals(7, eval("function f({eval}) { return eval; } f({eval: 7})"));
    }

    // -------------------------------------------------------------------------
    // C-style for-loop with a `let` binding: §14.7.4.3 CreatePerIterationEnvironment
    // copies the just-completed iteration's binding values forward. A body-internal
    // update with no increment clause must carry across iterations — otherwise the
    // binding resets to its initial value every iteration and the loop never ends.
    // -------------------------------------------------------------------------

    @Test
    void forLet_inBodyUpdate_carriesForward_noIncrementClause() {
        // Without copy-forward this loops forever (x resets to 0 each iteration).
        assertEquals(10, eval("var count = 0; for (let x = 0; x < 10;) { x++; count++; } count;"));
        // continue does not skip the copy-forward.
        assertEquals(10, eval("var count = 0; for (let x = 0; x < 10;) { x++; count++; continue; } count;"));
        // a shadowing inner block before the continue must not clobber the outer binding.
        assertEquals(10, eval(
                "var count = 0; for (let x = 0; x < 10;) { x++; count++; { let x = 'hi'; continue; } } count;"));
        // nested let-bound loops, inner in-body update.
        assertEquals(20, eval(
                "var count = 0; for (let x = 0; x < 10;) { x++; for (let y = 0; y < 2;) { y++; count++; } } count;"));
    }

    @Test
    void forLet_initializerNamesAreNotCaptured() {
        // The binding is only `i`; the initializer references the outer `const digits`,
        // which the per-iteration capture must NOT attempt to re-declare or reassign.
        assertEquals("012", eval(
                "const digits = [2, 1, 0]; var out = '';"
                        + " for (let i = digits.length - 1; i >= 0; i--) { out += digits[i]; } out;"));
    }

    @Test
    void forLet_perIterationBindingCapturedByClosures() {
        // Each iteration captures a distinct `i` binding (classic loop-closure test).
        assertEquals("0,1,2", eval(
                "var fns = []; for (let i = 0; i < 3; i++) { fns.push(function() { return i; }); }"
                        + " fns.map(function(f) { return f(); }).join(',');"));
    }

    // -------------------------------------------------------------------------
    // FunctionDeclaration is a StatementListItem, never a Statement — so it may
    // not be the sole body of a loop or (in strict mode) an `if` clause (§13.6,
    // §13.7). Loop bodies are an early error in BOTH modes (no Annex B carve-out);
    // the `if` clause is sloppy-legal (Annex B.3.4) but a strict-mode early error.
    // -------------------------------------------------------------------------

    @Test
    void functionDeclAsLoopBody_isAlwaysParseError() {
        assertParseError("for (;;) function f() {}");
        assertParseError("while (x) function f() {}");
        assertParseError("do function f() {} while (x);");
        assertParseError("for (k in o) function f() {}");
        assertParseError("for (k of o) function f() {}");
        // a braced body is a Block (StatementList) — function declaration is fine there.
        assertEquals(1, eval("for (var i = 0; i < 1; i++) { function f() { return 1; } } f();"));
    }

    @Test
    void functionDeclAsIfBody_isStrictOnlyParseError() {
        assertParseError("'use strict'; if (x) function f() {}");
        assertParseError("'use strict'; if (x) {} else function f() {}");
        // Annex B.3.4: sloppy-mode `if`-clause function declaration is legal and runs.
        assertEquals("function", eval("if (true) function f() {} typeof f;"));
        assertEquals("function", eval("if (false) {} else function f() {} typeof f;"));
        // braced body stays legal even in strict mode.
        assertEquals(7, eval("'use strict'; if (true) { function f() { return 7; } return f(); }"));
    }

    // -------------------------------------------------------------------------
    // A LexicalDeclaration (let/const) and a ClassDeclaration are Declarations,
    // not Statements, so they may not be the sole body of an `if`/`else`/loop
    // clause (§13.6, §14.x). Unlike FunctionDeclaration there is NO Annex B
    // carve-out, so this is an early error in BOTH sloppy and strict code. A
    // braced body (Block) is fine; a `var` declaration hoists and stays legal.
    // -------------------------------------------------------------------------

    @Test
    void lexicalOrClassDeclAsClauseBody_isAlwaysParseError() {
        assertParseError("if (x) let y = 1;");
        assertParseError("if (x) {} else const y = 1;");
        assertParseError("for (;;) let y = 1;");
        assertParseError("while (x) const y = 1;");
        assertParseError("do let y = 1; while (x);");
        assertParseError("for (k in o) let y = 1;");
        assertParseError("for (k of o) class C {}");
        assertParseError("if (x) class C {}");
        // `var` hoists — legal as a clause body; braced lexical bodies are fine.
        assertEquals(1, eval("if (true) var y = 1; y;"));
        assertEquals(2, eval("if (true) { let y = 2; y; }"));
    }

    @Test
    void letAsIdentifierWithLineTerminator_isNotALexicalDeclaration() {
        // `let` is not reserved in sloppy mode: a LineTerminator after it forces ASI,
        // so the clause body is the standalone ExpressionStatement identifier `let`
        // (skipped here, condition false) and what follows is a separate statement —
        // this must parse, not raise the lexical-declaration-as-clause-body error.
        // (The trailing literal confirms parse success without depending on the
        // engine's ASI handling of the skipped body.)
        assertEquals(99, eval("if (false) let\n x = 1; 99"));
        assertEquals(7, eval("while (false) let\n x; 7"));
        // `const` is a reserved word, so it has no such ASI escape — still an error.
        assertParseError("if (x) const\n y = 1;");
    }

    // -------------------------------------------------------------------------
    // Per-scope redeclaration early errors (§14.2.1 Block, §14.12.1 CaseBlock,
    // §16.1.1 Script): LexicallyDeclaredNames must have no duplicates and must
    // not intersect VarDeclaredNames. The sole Annex B.3.3 sloppy relaxation is
    // that a duplicate bound ONLY by FunctionDeclarations is legal (still a
    // strict error). FunctionDeclarations are lexical in a Block/CaseBlock but
    // var-scoped at Script / function-body top level.
    // -------------------------------------------------------------------------

    @Test
    void duplicateLexicalNamesInScope_isParseError() {
        // switch CaseBlock — all clauses share one lexical scope.
        assertParseError("switch (0) { case 1: let f; default: const f = 0 }");
        assertParseError("switch (0) { case 1: let f; default: let f }");
        assertParseError("switch (0) { case 1: class f {} default: const f = 0 }");
        // plain Block and Script top level.
        assertParseError("{ let x; let x; }");
        assertParseError("let x; const x = 1;");
        assertParseError("function g() { let x; let x; }");
        // duplicate BoundNames WITHIN a single lexical declaration's pattern.
        assertParseError("let {a, a} = obj;");
        assertParseError("const [b, b] = arr;");
    }

    @Test
    void lexicalNameClashingWithVar_isParseError() {
        // lexical ∩ var has NO Annex B carve-out — error in both modes.
        assertParseError("switch (0) { case 1: var f; default: let f }");
        assertParseError("switch (0) { case 1: var f; default: function f() {} }");
        assertParseError("switch (0) { case 1: function f() {} default: var f }");
        assertParseError("{ let x; var x; }");
        assertParseError("let x; var x;");
        // a `var` nested in an inner block still hoists into the outer scope.
        assertParseError("{ let x; { var x; } }");
    }

    @Test
    void duplicateFunctionDeclarations_areSloppyLegalStrictError() {
        // Annex B.3.3: duplicate names bound ONLY by FunctionDeclarations are legal
        // in sloppy code (in a Block / CaseBlock) ...
        assertEquals(5, eval("{ function f() {} function f() {} } 5"));
        assertEquals(6, eval("switch (0) { case 1: function f() {} default: function f() {} } 6"));
        // ... but a strict-mode early error.
        assertParseError("'use strict'; { function f() {} function f() {} }");
        // At Script / function-body top level a FunctionDeclaration is var-scoped, so a
        // duplicate is legal even in strict mode (no lexical name involved).
        assertEquals(7, eval("function f() {} function f() {} 7"));
        assertEquals(9, eval("function g() { 'use strict'; function f() {} function f() {} return 9; } g()"));
        // top-level function + var share var scope — legal.
        assertEquals(1, eval("function f() {} var f; 1"));
    }
}

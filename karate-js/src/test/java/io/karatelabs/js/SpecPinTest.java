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
        // get-only accessor: writes are silently ignored in lenient mode.
        // After strict-mode work (mega-commit 2J), writing to a get-only
        // accessor under "use strict" should throw TypeError.
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
    // Strict mode — currently engine is fully lenient. After mega-commit 2J,
    // these flips activate under "use strict". For now, pin lenient behavior.
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
}

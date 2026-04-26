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
}

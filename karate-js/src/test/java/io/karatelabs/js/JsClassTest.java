package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES6 class syntax — Phase 1: constructor, instance/static methods,
 * get/set accessors, computed keys, class expressions, default constructor.
 * No extends / super / fields yet (deferred).
 */
class JsClassTest extends EvalBase {

    @Test
    void testConstructorAndMethod() {
        assertEquals(10, eval("class Foo { constructor(a) { this.a = a } twice() { return this.a * 2 } }\n"
                + "new Foo(5).twice()"));
    }

    @Test
    void testMethodOnPrototypeShared() {
        assertEquals(true, eval("class C { m() { return 1 } }\n"
                + "var a = new C(); var b = new C();\n"
                + "a.m === b.m"));
    }

    @Test
    void testFieldSetInConstructor() {
        assertEquals(42, eval("class P { constructor() { this.v = 42 } }\nnew P().v"));
    }

    @Test
    void testDefaultConstructor() {
        assertEquals(true, eval("class C {}\nnew C() instanceof C"));
    }

    @Test
    void testInstanceOf() {
        assertEquals(true, eval("class C { m() {} }\nvar c = new C();\nc instanceof C"));
    }

    @Test
    void testStaticMethod() {
        assertEquals(9, eval("class C { static s() { return 9 } }\nC.s()"));
    }

    @Test
    void testStaticMethodNotOnInstance() {
        assertEquals(true, eval("class C { static s() {} m() {} }\n"
                + "var c = new C();\ntypeof c.s === 'undefined' && typeof c.m === 'function'"));
    }

    @Test
    void testGetterSetter() {
        assertEquals(5, eval("class C { constructor() { this._x = 1 } get x() { return this._x } set x(v) { this._x = v } }\n"
                + "var c = new C(); c.x = 5; c.x"));
    }

    @Test
    void testGetterComputesEachAccess() {
        assertEquals(7, eval("class C { constructor() { this.n = 3 } get plusFour() { return this.n + 4 } }\n"
                + "new C().plusFour"));
    }

    @Test
    void testComputedMethodName() {
        assertEquals(7, eval("var k = 'hi';\nclass C { [k]() { return 7 } }\nnew C().hi()"));
    }

    @Test
    void testClassExpression() {
        assertEquals(3, eval("var C = class { m() { return 3 } };\nnew C().m()"));
    }

    @Test
    void testNamedClassExpression() {
        assertEquals(true, eval("var C = class Named { m() { return 1 } };\nnew C() instanceof C"));
    }

    @Test
    void testMethodName() {
        assertEquals("foo", eval("class C { foo() {} }\nnew C().foo.name"));
    }

    @Test
    void testClassName() {
        assertEquals("Foo", eval("class Foo {}\nFoo.name"));
    }

    @Test
    void testMethodsNonEnumerable() {
        assertEquals(0, eval("class C { m() {} n() {} }\n"
                + "var keys = []; for (var k in new C()) { keys.push(k) }\nkeys.length"));
    }

    @Test
    void testConstructorRequiresNew() {
        assertEquals("TypeError", eval("class C {}\ntry { C() } catch (e) { e.name }"));
    }

    @Test
    void testClassBodyIsStrict() {
        // strict mode: assignment to an undeclared identifier throws ReferenceError
        assertEquals("ReferenceError", eval("class C { m() { undeclaredX = 1 } }\n"
                + "try { new C().m() } catch (e) { e.name }"));
    }

    @Test
    void testConstructorExplicitObjectReturnOverrides() {
        assertEquals(99, eval("class C { constructor() { return { v: 99 } } }\nnew C().v"));
    }

    @Test
    void testPrototypeConstructorBackReference() {
        assertEquals(true, eval("class C {}\nnew C().constructor === C"));
    }

    @Test
    void testStaticAndInstanceSameName() {
        assertEquals(true, eval("class C { static who() { return 'static' } who() { return 'instance' } }\n"
                + "C.who() === 'static' && new C().who() === 'instance'"));
    }

    // ===== Phase 2: extends / super =====

    @Test
    void testExtendsSuperConstructor() {
        assertEquals(15, eval("class A { constructor(x) { this.x = x } }\n"
                + "class B extends A { constructor(x) { super(x); this.y = x * 2 } }\n"
                + "var b = new B(5); b.x + b.y"));
    }

    @Test
    void testInheritedMethod() {
        assertEquals("hi", eval("class A { greet() { return 'hi' } }\nclass B extends A {}\nnew B().greet()"));
    }

    @Test
    void testSuperMethod() {
        assertEquals(2, eval("class A { m() { return 1 } }\n"
                + "class B extends A { m() { return super.m() + 1 } }\nnew B().m()"));
    }

    @Test
    void testSuperMethodStringConcat() {
        assertEquals("AB", eval("class A { name() { return 'A' } }\n"
                + "class B extends A { name() { return super.name() + 'B' } }\nnew B().name()"));
    }

    @Test
    void testInstanceOfBothLevels() {
        assertEquals(true, eval("class A {} class B extends A {}\nvar b = new B();\nb instanceof B && b instanceof A"));
    }

    @Test
    void testDefaultDerivedConstructorForwardsArgs() {
        assertEquals(7, eval("class A { constructor(x) { this.x = x } }\nclass B extends A {}\nnew B(7).x"));
    }

    @Test
    void testStaticInheritance() {
        assertEquals(9, eval("class A { static s() { return 9 } }\nclass B extends A {}\nB.s()"));
    }

    @Test
    void testSuperThreeLevels() {
        assertEquals(111, eval("class A { m() { return 1 } }\n"
                + "class B extends A { m() { return super.m() + 10 } }\n"
                + "class C extends B { m() { return super.m() + 100 } }\nnew C().m()"));
    }

    @Test
    void testSuperInConstructorMethodCall() {
        assertEquals("base", eval("class A { tag() { return 'base' } }\n"
                + "class B extends A { constructor() { super(); this.t = super.tag() } }\nnew B().t"));
    }

    @Test
    void testSuperGetterRunsWithDerivedThis() {
        assertEquals(20, eval("class A { get area() { return this.w * this.h } }\n"
                + "class B extends A { constructor() { super(); this.w = 4; this.h = 5 } get area() { return super.area } }\n"
                + "new B().area"));
    }

    @Test
    void testSuperSetterRunsWithDerivedThis() {
        assertEquals(7, eval("class A { set val(v) { this._v = v } }\n"
                + "class B extends A { set val(v) { super.val = v } get val() { return this._v } }\n"
                + "var b = new B(); b.val = 7; b.val"));
    }

    @Test
    void testSuperDataWriteCreatesOwnPropertyOnInstance() {
        assertEquals(true, eval("class A {}\n"
                + "class B extends A { m() { super.x = 42 } }\n"
                + "var b = new B(); b.m();\n"
                + "b.hasOwnProperty('x') && b.x === 42 && !A.prototype.hasOwnProperty('x') && !B.prototype.hasOwnProperty('x')"));
    }

    @Test
    void testSuperBracketGetterRunsWithDerivedThis() {
        assertEquals(3, eval("class A { get z() { return this.n } }\n"
                + "class B extends A { constructor() { super(); this.n = 3 } read() { return super['z'] } }\n"
                + "new B().read()"));
    }

    @Test
    void testSuperBracketMethodCallBindsDerivedThis() {
        assertEquals(6, eval("class A { m() { return this.n * 2 } }\n"
                + "class B extends A { constructor() { super(); this.n = 3 } call() { return super['m']() } }\n"
                + "new B().call()"));
    }

    @Test
    void testStaticSuperGetterRunsWithDerivedThis() {
        assertEquals("B", eval("class A { static get tag() { return this.id } }\n"
                + "class B extends A { static get tag() { return super.tag } }\n"
                + "B.id = 'B'; B.tag"));
    }

    @Test
    void testSuperCompoundAssignmentThroughAccessorPair() {
        assertEquals(6, eval("class A { get v() { return this._v } set v(x) { this._v = x } }\n"
                + "class B extends A { constructor() { super(); this._v = 5 } bump() { super.v += 1 } }\n"
                + "var b = new B(); b.bump(); b._v"));
    }

    @Test
    void testSuperIncrementThroughAccessorPair() {
        assertEquals(6, eval("class A { get v() { return this._v } set v(x) { this._v = x } }\n"
                + "class B extends A { constructor() { super(); this._v = 5 } bump() { super.v++ } }\n"
                + "var b = new B(); b.bump(); b._v"));
    }

    // ===== extends built-ins with internal slots (Array / Map / Set / Date) =====

    @Test
    void testExtendsArrayIsArray() {
        assertEquals(true, eval("class S extends Array {}\nvar s = new S();\n"
                + "Array.isArray(s) && s instanceof S && s instanceof Array"));
    }

    @Test
    void testExtendsArrayPushLengthAndIteration() {
        assertEquals(true, eval("class S extends Array { sum() { let t = 0; for (const x of this) t += x; return t } }\n"
                + "var s = new S(); s.push(1); s.push(2);\n"
                + "s.length === 2 && s.sum() === 3"));
    }

    @Test
    void testExtendsArraySuperForwardsElements() {
        assertEquals("[1,2,3]", eval("class S extends Array {}\nJSON.stringify(new S(1, 2, 3))"));
    }

    @Test
    void testExtendsArrayStringConversion() {
        assertEquals("1,2,3", eval("class S extends Array {}\nString(new S(1, 2, 3))"));
    }

    @Test
    void testExtendsArrayPublicField() {
        assertEquals(true, eval("class S extends Array { tag = 'x' }\nvar s = new S();\n"
                + "s.tag === 'x' && Array.isArray(s)"));
    }

    @Test
    void testExtendsMapHasInternalSlots() {
        assertEquals(true, eval("class M extends Map {}\nvar m = new M();\n"
                + "m.set('a', 1);\nm.get('a') === 1 && m.size === 1 && m instanceof Map"));
    }

    @Test
    void testExtendsMapSuperForwardsEntries() {
        assertEquals(2, eval("class M extends Map {}\nnew M([['a', 1], ['b', 2]]).size"));
    }

    @Test
    void testExtendsMapWithFieldAndMethod() {
        assertEquals(true, eval("class M extends Map { count = 0; put(k, v) { this.set(k, v); this.count++ } }\n"
                + "var m = new M(); m.put('a', 1); m.put('b', 2);\n"
                + "m.count === 2 && m.get('b') === 2"));
    }

    @Test
    void testExtendsSetHasInternalSlots() {
        assertEquals(true, eval("class S extends Set {}\nvar s = new S();\n"
                + "s.add(1); s.add(1); s.add(2);\ns.size === 2 && s.has(2) && s instanceof Set"));
    }

    @Test
    void testReflectConstructRunsSuperChainAndFields() {
        assertEquals("[1,2,3]", eval("class S extends Array {}\nJSON.stringify(Reflect.construct(S, [1, 2, 3]))"));
        assertEquals(5, eval("class C { a = 5 }\nReflect.construct(C, []).a"));
        assertEquals(7, eval("class A { constructor(x) { this.x = x } }\nclass B extends A {}\n"
                + "Reflect.construct(B, [7]).x"));
    }

    @Test
    void testSuperDataWriteWithPrimitiveReceiverNeverHitsPrototype() {
        // a super write against a primitive `this` must not mutate the shared
        // parent prototype (TypeError in strict mode, no-op in sloppy)
        assertEquals(1, eval("class A {}\nA.prototype.x = 1;\n"
                + "class B extends A { m() { super.x = 2 } }\n"
                + "try { B.prototype.m.call(3) } catch (e) {}\nA.prototype.x"));
    }

    @Test
    void testExtendsDateHasTimeSlot() {
        assertEquals(true, eval("class D extends Date { epoch() { return this.getTime() === 0 } }\n"
                + "var d = new D(0);\nd.getTime() === 0 && d.epoch() && d instanceof Date"));
    }

    @Test
    void testExtendsErrorMessage() {
        assertEquals("boom", eval("class E extends Error { constructor(m) { super(m) } }\nnew E('boom').message"));
    }

    @Test
    void testExtendsErrorInstanceOf() {
        assertEquals(true, eval("class E extends Error {}\nnew E('x') instanceof Error"));
    }

    @Test
    void testExtendsNonConstructorThrows() {
        assertEquals("TypeError", eval("var notCtor = 42;\n"
                + "try { class B extends notCtor {} } catch (e) { e.name }"));
    }

    // ===== Phase 3: public fields =====

    @Test
    void testInstanceField() {
        assertEquals(1, eval("class C { x = 1 }\nnew C().x"));
    }

    @Test
    void testInstanceFieldNoInitializer() {
        assertEquals(true, eval("class C { x }\nvar c = new C();\n'x' in c && c.x === undefined"));
    }

    @Test
    void testFieldInitializerSeesEarlierField() {
        assertEquals(6, eval("class C { a = 2; b = this.a * 3 }\nnew C().b"));
    }

    @Test
    void testFieldWithSemicolons() {
        assertEquals(3, eval("class C { a = 1; b = 2; sum() { return this.a + this.b } }\nnew C().sum()"));
    }

    @Test
    void testFieldsAsiNoSemicolons() {
        assertEquals(3, eval("class C {\n  a = 1\n  b = 2\n  sum() { return this.a + this.b }\n}\nnew C().sum()"));
    }

    @Test
    void testStaticField() {
        assertEquals(42, eval("class C { static n = 42 }\nC.n"));
    }

    @Test
    void testStaticFieldNotOnInstance() {
        assertEquals(true, eval("class C { static n = 1 }\ntypeof new C().n === 'undefined'"));
    }

    @Test
    void testComputedFieldName() {
        assertEquals(5, eval("var k = 'dyn';\nclass C { [k] = 5 }\nnew C().dyn"));
    }

    @Test
    void testFieldsAreEnumerable() {
        assertEquals("x,y", eval("class C { x = 1; y = 2; m() {} }\n"
                + "var k = []; for (var p in new C()) { k.push(p) }\nk.join(',')"));
    }

    @Test
    void testStaticFieldReferencingClass() {
        assertEquals(2, eval("class C { static count = 0; static inc() { return ++C.count } }\n"
                + "C.inc(); C.inc(); C.count"));
    }

    @Test
    void testDerivedFieldsAfterSuper() {
        assertEquals("1,2,3", eval("class A { constructor() { this.a = 1 } }\n"
                + "class B extends A { b = this.a + 1; constructor() { super(); this.c = this.b + 1 } }\n"
                + "var o = new B();\n[o.a, o.b, o.c].join(',')"));
    }

    @Test
    void testDefaultDerivedConstructorWithFields() {
        assertEquals("5,9", eval("class A { constructor(x) { this.x = x } }\n"
                + "class B extends A { y = 9 }\nvar o = new B(5);\no.x + ',' + o.y"));
    }

    @Test
    void testArrowFieldSeesInstance() {
        assertEquals(5, eval("class C { x = 5; f = () => this.x }\nnew C().f()"));
    }

    @Test
    void testArrowFieldThisIsTheInstance() {
        assertEquals(true, eval("class C { f = () => this }\nvar c = new C();\nc.f() === c"));
    }

    @Test
    void testArrowFieldKeepsThisWhenDetached() {
        assertEquals(7, eval("class C { x = 7; f = () => this.x }\n"
                + "var g = new C().f;\ng()"));
    }

    @Test
    void testArrowFieldIgnoresExplicitReceiver() {
        assertEquals(1, eval("class C { x = 1; f = () => this.x }\n"
                + "new C().f.call({ x: 99 })"));
    }

    @Test
    void testArrowFieldPerInstance() {
        assertEquals("1,2", eval("class C { constructor(n) { this.n = n } f = () => this.n }\n"
                + "var a = new C(1); var b = new C(2);\na.f() + ',' + b.f()"));
    }

    @Test
    void testNestedArrowField() {
        assertEquals(3, eval("class C { x = 3; f = () => () => this.x }\nnew C().f()()"));
    }

    @Test
    void testArrowFieldInsideCallback() {
        assertEquals("2,4", eval("class C { x = 2; f = () => [1, 2].map(n => n * this.x) }\n"
                + "new C().f().join(',')"));
    }

    @Test
    void testArrowFieldSeesConstructorState() {
        assertEquals(4, eval("class C { f = () => this.v; constructor() { this.v = 4 } }\nnew C().f()"));
    }

    @Test
    void testArrowFieldInDerivedClass() {
        assertEquals(8, eval("class A { constructor() { this.v = 8 } }\n"
                + "class B extends A { f = () => this.v }\nnew B().f()"));
    }

    @Test
    void testArrowFieldInDerivedClassWithExplicitConstructor() {
        assertEquals(6, eval("class A { constructor() { this.v = 5 } }\n"
                + "class B extends A { f = () => this.v + 1; constructor() { super() } }\nnew B().f()"));
    }

    @Test
    void testStaticArrowFieldSeesClass() {
        assertEquals(true, eval("class C { static g = () => this }\nC.g() === C"));
    }

    @Test
    void testStaticArrowFieldReadsStaticField() {
        assertEquals(2, eval("class C { static n = 2; static g = () => this.n }\nC.g()"));
    }

    @Test
    void testPlainFieldInitializerStillSeesThis() {
        assertEquals(2, eval("class C { a = 1; b = this.a + 1 }\nnew C().b"));
    }

    @Test
    void testFieldInitializerDoesNotLeakThis() {
        assertEquals(true, eval("class C { x = 1 }\nnew C();\nthis === globalThis"));
    }

    @Test
    void testArrowFieldThrowPropagates() {
        assertEquals("boom", eval("class C { x = (() => { throw new Error('boom') })() }\n"
                + "try { new C() } catch (e) { e.message }"));
    }

    //==== ES2022 private class elements

    private static void assertParseError(String src) {
        assertThrows(io.karatelabs.parser.ParserException.class, () -> new Engine().eval(src));
    }

    @Test
    void testPrivateFieldInitAndRead() {
        assertEquals(7, eval("class C { #n = 7; get v() { return this.#n } }\nnew C().v"));
    }

    @Test
    void testPrivateFieldDefaultsToUndefined() {
        assertEquals(true, eval("class C { #n; has() { return this.#n === undefined } }\nnew C().has()"));
    }

    @Test
    void testPrivateFieldWrite() {
        assertEquals(9, eval("class C { #n = 1; set(v) { this.#n = v; return this.#n } }\nnew C().set(9)"));
    }

    @Test
    void testPrivateFieldCompoundAssign() {
        assertEquals(7, eval("class C { #n = 2; add(v) { this.#n += v; return this.#n } }\nnew C().add(5)"));
    }

    @Test
    void testPrivateFieldIncrement() {
        assertEquals("0,2,2", eval("class C { #n = 0; bump() { var a = this.#n++; var b = ++this.#n; return [a, b, this.#n].join(',') }\n"
                + "}\nnew C().bump()"));
    }

    @Test
    void testPrivateFieldPerInstance() {
        assertEquals("2,3", eval("class C { #n = 0; inc() { return ++this.#n } }\n"
                + "var a = new C(); var b = new C();\na.inc(); b.inc(); b.inc();\na.inc() + ',' + b.inc()"));
    }

    @Test
    void testStaticPrivateField() {
        assertEquals(2, eval("class C { static #t = 0; static bump() { return ++C.#t } }\nC.bump(); C.bump()"));
    }

    @Test
    void testStaticPrivateFieldFromInstanceMethod() {
        assertEquals(2, eval("class Counter { #count = 0; static #total = 0;\n"
                + "  increment() { this.#count++; Counter.#total++; return this.#count }\n"
                + "  static get total() { return Counter.#total } }\n"
                + "var c = new Counter(); c.increment(); c.increment(); Counter.total"));
    }

    @Test
    void testPrivateMethod() {
        assertEquals(42, eval("class C { #secret() { return 42 } reveal() { return this.#secret() } }\nnew C().reveal()"));
    }

    @Test
    void testStaticPrivateMethod() {
        assertEquals(6, eval("class C { static #twice(n) { return n * 2 } static run() { return C.#twice(3) } }\nC.run()"));
    }

    @Test
    void testPrivateAccessor() {
        assertEquals(10, eval("class C { #v = 1; get #d() { return this.#v * 2 } set #d(x) { this.#v = x }\n"
                + "  run() { this.#d = 5; return this.#d } }\nnew C().run()"));
    }

    @Test
    void testPrivateFieldInArrowInsideMethod() {
        assertEquals(3, eval("class C { #n = 3; get() { var f = () => this.#n; return f() } }\nnew C().get()"));
    }

    @Test
    void testTwoClassesDoNotSharePrivateName() {
        assertEquals("1,2", eval("class A { #x = 1; get() { return this.#x } }\n"
                + "class B { #x = 2; get() { return this.#x } }\n"
                + "new A().get() + ',' + new B().get()"));
    }

    @Test
    void testPrivateReadFromForeignInstanceIsTypeError() {
        assertEquals("TypeError", eval("class A { #x = 1; static read(o) { return o.#x } }\n"
                + "class B { #x = 2 }\n"
                + "try { A.read(new B()) } catch (e) { e.name }"));
    }

    @Test
    void testPrivateReadFromPlainObjectIsTypeError() {
        assertEquals("TypeError", eval("class A { #x = 1; static read(o) { return o.#x } }\n"
                + "try { A.read({}) } catch (e) { e.name }"));
    }

    @Test
    void testUndeclaredPrivateNameIsParseError() {
        assertParseError("class C { m() { return this.#nope } }");
    }

    @Test
    void testPrivateNameOutsideClassIsParseError() {
        assertParseError("var o = {}; o.#x");
        assertParseError("function f() { return this.#x }");
    }

    @Test
    void testDeletePrivateIsParseError() {
        assertParseError("class C { #x = 1; m() { delete this.#x } }");
    }

    @Test
    void testDuplicatePrivateNameIsParseError() {
        assertParseError("class C { #x = 1; #x = 2 }");
        assertParseError("class C { #x = 1; #x() {} }");
    }

    @Test
    void testLonePrivateNameIsParseError() {
        assertParseError("var x = # 1");
        assertParseError("class C { #x = 1; m() { return #x } }");
    }

    @Test
    void testPrivateStateIsNotObservable() {
        assertEquals("[]", eval("class C { #n = 1; m() {} }\nJSON.stringify(Object.keys(new C()))"));
        assertEquals("[]", eval("class C { #n = 1 }\nJSON.stringify(Object.getOwnPropertyNames(new C()))"));
        assertEquals("{}", eval("class C { #n = 1 }\nJSON.stringify(new C())"));
        assertEquals("", eval("class C { #n = 1 }\nvar k = []; for (var p in new C()) { k.push(p) }\nk.join(',')"));
        assertEquals("{}", eval("class C { #n = 1 }\nJSON.stringify({ ...new C() })"));
    }

    @Test
    void testBracketAccessIsUnrelatedToPrivate() {
        assertEquals("undefined", eval("class C { #n = 1 }\ntypeof new C()['#n']"));
        assertEquals("1,5", eval("class C { #n = 1; get() { return this.#n } }\n"
                + "var c = new C(); c['#n'] = 5;\nc.get() + ',' + c['#n']"));
    }

    @Test
    void testPrivateBrandCheck() {
        assertEquals("true,false", eval("class C { #n = 1; static has(o) { return #n in o } }\n"
                + "C.has(new C()) + ',' + C.has({})"));
        assertEquals(false, eval("class A { #x = 1; static has(o) { return #x in o } }\n"
                + "class B { #x = 2 }\nA.has(new B())"));
    }

    @Test
    void testPrivateFieldOnDerivedClass() {
        assertEquals("1,2", eval("class A { #a = 1; getA() { return this.#a } }\n"
                + "class B extends A { #b = 2; getB() { return this.#b } }\n"
                + "var o = new B();\no.getA() + ',' + o.getB()"));
    }

    @Test
    void testPrivateElementsAndLabelsCompose() {
        // an outer label around a class; a private method with its own label works,
        // and the method body cannot target the outer label (function boundary)
        assertEquals(1, eval("var r; o: { class C { #n = 1; m() { x: for (var i = 0; i < 2; i++) { break x } return this.#n } }\n"
                + "r = new C().m() } r"));
        assertParseError("o: { class C { m() { break o } } }");
    }

    //==== ES2022 class static initialization blocks

    @Test
    void testStaticBlockRunsAtClassDefinition() {
        assertEquals(42, eval("class C { static { this.x = 42 } }\nC.x"));
    }

    @Test
    void testStaticBlockThisIsTheConstructor() {
        assertEquals(true, eval("class C { static { this.self = this } }\nC.self === C"));
    }

    @Test
    void testStaticBlockCanAssignViaClassName() {
        assertEquals(7, eval("class C { static { C.x = 7 } }\nC.x"));
    }

    @Test
    void testStaticBlockReadsStaticFieldDeclaredAbove() {
        assertEquals(3, eval("class C { static a = 1; static { this.b = this.a + 2 } }\nC.b"));
    }

    @Test
    void testStaticBlocksInterleaveWithStaticFieldsInSourceOrder() {
        assertEquals("f1,b1,f2,b2", eval("var log = [];\n"
                + "class C { static a = log.push('f1'); static { log.push('b1') }\n"
                + "  static b = log.push('f2'); static { log.push('b2') } }\n"
                + "log.join(',')"));
    }

    @Test
    void testMultipleStaticBlocksRunInOrder() {
        assertEquals("1,2,3", eval("class C { static v = ''; static { C.v += '1' } static { C.v += ',2' } static { C.v += ',3' } }\nC.v"));
    }

    @Test
    void testStaticBlockDeclarationsDoNotLeak() {
        assertEquals("undefined,undefined", eval("var n = 1;\n"
                + "class C { static { let n = 2; const m = 3; var v = 4; this.n = n } }\n"
                + "typeof m + ',' + typeof v"));
        assertEquals(1, eval("var n = 1;\nclass C { static { let n = 2 } }\nn"));
    }

    @Test
    void testStaticBlockSeesEnclosingScopeAndPrivateNames() {
        assertEquals(5, eval("var k = 5;\nclass C { static #p; static { C.#p = k } static get p() { return C.#p } }\nC.p"));
    }

    @Test
    void testStaticBlockThrowPropagates() {
        assertEquals("boom", eval("try { class C { static { throw new Error('boom') } } } catch (e) { e.message }"));
    }

    @Test
    void testStaticBlockSuperProperty() {
        assertEquals(11, eval("class A { static m() { return 11 } }\n"
                + "class B extends A { static { this.v = super.m() } }\nB.v"));
    }

    @Test
    void testReturnInStaticBlockIsSyntaxError() {
        assertParseError("class C { static { return } }");
        // a function nested inside the block keeps its own return
        assertEquals(4, eval("class C { static { this.f = function () { return 4 } } }\nC.f()"));
    }

    @Test
    void testBreakAndContinueCannotCrossAStaticBlock() {
        assertParseError("label: while (false) { class C { static { break } } }");
        assertParseError("label: while (false) { class C { static { continue } } }");
        assertParseError("label: while (false) { class C { static { continue label } } }");
        assertParseError("label: while (false) { class C { static { break label } } }");
        assertParseError("switch (1) { case 1: class C { static { break } } }");
        // a loop, switch or label inside the block is still a legal target
        assertEquals(1, eval("class C { static { while (true) { this.v = 1; break } } }\nC.v"));
        assertEquals(2, eval("class C { static { for (var i = 0; i < 5; i++) { if (i) continue; this.v = 2 } } }\nC.v"));
        assertEquals(3, eval("class C { static { switch (1) { case 1: this.v = 3; break } } }\nC.v"));
        assertEquals(4, eval("class C { static { out: { this.v = 4; break out } } }\nC.v"));
        // a function inside the block is its own boundary again
        assertEquals(5, eval("class C { static { this.f = function () { while (true) { return 5 } } } }\nC.f()"));
    }

    @Test
    void testAwaitIsReservedInsideAStaticBlock() {
        assertParseError("class C { static { await } }");
        assertParseError("class C { static { await: 0 } }");
        assertParseError("class C { static { let await } }");
        assertParseError("class C { static { const await = 0 } }");
        assertParseError("class C { static { var await } }");
        assertParseError("class C { static { var [await] = [] } }");
        assertParseError("class C { static { var {await} = {} } }");
        assertParseError("class C { static { function await() {} } }");
        assertParseError("class C { static { try {} catch (await) {} } }");
        assertParseError("class C { static { ({ await }) } }");
        assertParseError("class C { static { (await => 0) } }");
        assertParseError("class C { static { ((x = await) => 0) } }");
    }

    @Test
    void testAwaitIsAnOrdinaryNameAgainInsideANestedFunction() {
        assertEquals(1, eval("class C { static { this.f = function () { var await = 1; return await } } }\nC.f()"));
        assertEquals(2, eval("class C { static { this.f = () => { let await = 2; return await } } }\nC.f()"));
        // property names are never references
        assertEquals("3,4", eval("class C { static { var o = { await: 3 }; this.v = o.await + ',' + ({ await() { return 4 } }).await() } }\nC.v"));
        // a function's parameters are [~Await]; only its name is not
        assertEquals(5, eval("class C { static { this.f = function (await) { return await } } }\nC.f(5)"));
    }

    @Test
    void testStaticBlockIsNotAFieldNamedStatic() {
        assertEquals("undefined", eval("class C { static { } }\ntypeof C.static"));
        // `static` with no member name after it is still an ordinary field name
        assertEquals(1, eval("class C { static = 1 }\nnew C().static"));
        assertEquals(2, eval("class C { static() { return 2 } }\nnew C().static()"));
    }
}

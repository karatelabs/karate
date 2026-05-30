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
}

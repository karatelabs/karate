package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FunctionDeclarationInstantiation (§10.2.11) and GlobalDeclarationInstantiation (§16.1.7)
 * for {@code var}: every VarDeclaredName is bound to undefined at entry, wherever the
 * declaration sits. Also the two environments a function body can see besides its own:
 * a named function expression's own name (§15.2.5) and a {@code with} object (§14.11).
 */
class DeclarationInstantiationTest extends EvalBase {

    @Test
    void varIsBoundAtFunctionEntry() {
        assertEquals("undefined", eval("function f() { var r = typeof x; var x = 1; return r } f()"));
        assertEquals(true, eval("function f() { return x === undefined; var x = 1 } f()"));
        // a nested block, a loop head, a for-in head and a catch block all hoist
        assertEquals(true, eval("function f() { if (false) { var a = 1 } return a === undefined } f()"));
        assertEquals(true, eval("function f() { var r = i; for (var i = 0; i < 2; i++) {} return r === undefined && i === 2 } f()"));
        assertEquals(true, eval("function f() { var r = k; for (var k in {p: 1}) {} return r === undefined && k === 'p' } f()"));
        assertEquals(true, eval("function f() { var r = e; try { throw 1 } catch (x) { var e = x } return r === undefined && e === 1 } f()"));
        assertEquals(true, eval("function f() { var r = q; { { var q = 3 } } return r === undefined && q === 3 } f()"));
        // destructuring declarators bind every name
        assertEquals(true, eval("function f() { var r = typeof b; var {a, b} = {a: 1, b: 2}; return r === 'undefined' && b === 2 } f()"));
    }

    @Test
    void varAssignmentBeforeDeclarationIsLocal() {
        assertEquals(1, eval("var x = 1; function f() { x = 2; var x; return x } f(); x"));
        assertEquals(2, eval("var x = 1; function f() { x = 2; var x; return x } f()"));
        // the `C = C || …` idiom: no outer C, no ReferenceError, the inner var
        assertEquals("obj", eval("function f() { var C = C || 'obj'; return C } f()"));
        assertEquals("obj", eval("var C = C || 'obj'; C"));
    }

    @Test
    void varWithoutInitializerDoesNotReset() {
        assertEquals(1, eval("var x = 1; var x; x"));
        assertEquals(5, eval("function f(a) { var a; return a } f(5)"));
        assertEquals("1,2", eval("function f() { var out = []; for (var i = 1; i <= 2; i++) { var acc; acc = acc || []; acc.push(i) } return acc.join() } f()"));
        assertEquals(5, eval("function f() { var v = 5; { var v; } return v } f()"));
        assertEquals("undefined", eval("function f() { var v = 5; { var v = undefined; } return typeof v } f()"));
    }

    @Test
    void varKeepsParameterAndHoistedFunction() {
        assertEquals(5, eval("function f(a) { var a; return a } f(5)"));
        assertEquals("function", eval("function f() { var g; return typeof g; function g() {} } f()"));
        assertEquals(1, eval("function f() { return g(); function g() { return 1 } } f()"));
        assertEquals(2, eval("function f(a) { var a = 2; return a } f(5)"));
    }

    @Test
    void parameterDefaultDoesNotSeeBodyVar() {
        assertEquals("outer", eval("var x = 'outer'; function f(a = x) { var x = 'inner'; return a } f()"));
    }

    @Test
    void argumentsSurvivesVarArguments() {
        assertEquals(2, eval("function f() { var arguments; return arguments.length } f(1, 2)"));
    }

    @Test
    void closuresShareTheHoistedVar() {
        assertEquals(3, eval("function f() { var g = function() { return v }; var v = 3; return g() } f()"));
        assertEquals(3, eval("function f() { function g() { return v } var v = 3; return g() } f()"));
        assertEquals("1,2,3", eval("function f() { var out = []; for (var i = 1; i <= 3; i++) { (function() { var n = i; out.push(n) })() } return out.join() } f()"));
    }

    @Test
    void scriptVarsAreBoundBeforeTheFirstStatement() {
        assertEquals("undefined", eval("var r = typeof later; var later = 1; r"));
        assertEquals(true, eval("var r = later === undefined; var later = 1; r"));
        assertEquals(true, eval("function f() { return typeof g === 'undefined' } var r = f(); var g = 1; r"));
        // a hoisted global var is a plain binding: Math is not shadowed by `var Math`
        assertEquals(2, eval("var Math; Math.floor(2.5)"));
        assertEquals("number", eval("var r = typeof Infinity; var Infinity; r"));
    }

    @Test
    void scriptVarKeepsHostBinding() {
        assertEquals(5, eval("var x; x", "{ x: 5 }"));
        assertEquals(6, eval("var x = x + 1; x", "{ x: 5 }"));
    }

    @Test
    void letAndConstKeepTheirTemporalDeadZone() {
        assertThrows(EngineException.class, () -> eval("function f() { var r = x; let x = 1; return r } f()"));
        assertThrows(EngineException.class, () -> eval("function f() { x = 2; let x = 1; return x } f()"));
        assertThrows(EngineException.class, () -> eval("function f() { var r = typeof x; const x = 1; return r } f()"));
        assertEquals(1, eval("function f() { { let x = 1; return x } } f()"));
    }

    @Test
    void varInsideGeneratorAndAsyncBodies() {
        assertEquals("undefined", eval("function* g() { yield typeof x; var x = 1 } g().next().value"));
        assertEquals(1, eval("function f() { var r; (function() { r = typeof y; var y = 1 })(); return r === 'undefined' ? 1 : 0 } f()"));
    }

    @Test
    void namedFunctionExpressionNameIsItsOwn() {
        // visible inside, for recursion
        assertEquals(120, eval("var f = function fact(n) { return n <= 1 ? 1 : n * fact(n - 1) }; f(5)"));
        // not bound in the enclosing scope
        assertEquals("undefined", eval("var f = function g() {}; typeof g"));
        assertThrows(EngineException.class, () -> eval("var f = function g() {}; g"));
        // does not clobber a same-named declaration, in a block or an object literal
        assertEquals("decl", eval("function parse() { return 'decl' } var o = { parse: function parse() { return 'expr' } }; parse()"));
        assertEquals("decl", eval("function f() { function parse() { return 'decl' } var o = { parse: function parse() { return 'expr' } }; return parse() } f()"));
        // nor an outer var of the same name
        assertEquals(1, eval("var parse = 1; (function() { var o = { parse: function parse() {} } })(); parse"));
        // immutable: a sloppy assignment is ignored, a strict one throws
        assertEquals("function", eval("var f = function g() { g = 1; return typeof g }; f()"));
        assertThrows(EngineException.class, () -> eval("'use strict'; var f = function g() { g = 1 }; f()"));
        // a declaration still binds in the enclosing scope, before its statement
        assertEquals("function", eval("var r = typeof d; function d() {} r"));
        assertEquals("f", eval("var f = function f() {}; f.name"));
    }

    @Test
    void withStatementResolvesOnTheObject() {
        assertEquals(3, eval("var o = {a: 1, b: 2}; var r; with (o) { r = a + b } r"));
        assertEquals(10, eval("var o = {a: 1}; with (o) { a = 10 } o.a"));
        assertEquals("undefined", eval("var o = {a: 1}; with (o) { var z = typeof c } z"));
        assertEquals(2, eval("function f() { var q = 1; with ({q: 2}) { return q } } f()"));
        assertEquals("outer", eval("var q = 'outer'; var o = {}; with (o) { var r = q } r"));
        // a closure keeps the object environment
        assertEquals(5, eval("var fns = []; with ({x: 5}) { fns.push(function() { return x }) } fns[0]()"));
        // the function's arguments and this are reachable
        assertEquals(2, eval("(function() { with ({}) { return arguments.length } })(7, 8)"));
        // a var in the body is the function's; its initializer writes like an assignment
        assertEquals(true, eval("(function() { var t; with ({t: 3}) { var t = 5 } return t === undefined })()"));
        assertEquals(5, eval("var o = {t: 3}; (function() { with (o) { var t = 5 } })(); o.t"));
        // a let in the body shadows the object
        assertEquals(1, eval("with ({x: 9}) { let x = 1; var r = x } r"));
        // @@unscopables hides a property
        assertEquals("outer", eval("var k = 'outer'; var o = {k: 'own'}; o[Symbol.unscopables] = {k: true}; with (o) { var r = k } r"));
        assertEquals("number,function,object", eval("with (Math) { var r = [typeof PI, typeof floor, typeof JSON] } r.join()"));
        // completions pass through
        assertEquals("1,2", eval("var out = []; for (var i = 1; i < 5; i++) { with ({}) { if (i > 2) break; out.push(i) } } out.join()"));
        assertThrows(EngineException.class, () -> eval("with (null) {}"));
        assertThrows(RuntimeException.class, () -> eval("'use strict'; with ({}) {}")); // a parse error
        assertEquals(2, eval("var r = 0; try { with ({}) { throw 2 } } catch (e) { r = e } r"));
        // delete of a name the object binds removes the property; of a binding it is false
        assertEquals("true,false,false", eval("var o = {p: 1}; var v = 2; with (o) { var r = [delete p, delete v, 'p' in o] } r.join()"));
        assertThrows(RuntimeException.class, () -> eval("with ({}) let x = 1"));
        assertThrows(RuntimeException.class, () -> eval("with ({}) function f() {}"));
    }

}

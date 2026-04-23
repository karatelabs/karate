package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvalTest extends EvalBase {

    @Test
    void testDev() {
        // single-line if/else with blocks (baseline - already worked)
        assertEquals(true, eval("if (false) { false } else { true }"));
        assertEquals(false, eval("if (false) { false } else { false }"));
        // multi-line if/else - the fix for } else { on separate line
        assertEquals(true, eval("if (false) {\n  false\n} else {\n  true\n}"));
        assertEquals(false, eval("if (true) {\n  false\n} else {\n  true\n}"));
        // multi-line try/catch
        eval("var a\ntry {\n  throw 'err'\n} catch (e) {\n  a = e\n}");
        assertEquals("err", get("a"));
        // multi-line try/catch/finally
        eval("var a, b\ntry {\n  throw 'err'\n} catch (e) {\n  a = e\n} finally {\n  b = 'done'\n}");
        assertEquals("err", get("a"));
        assertEquals("done", get("b"));
    }

    @Test
    void testBoxedPrimitives() {
        // Number() vs new Number()
        assertEquals("number", eval("typeof Number(5)"));
        assertEquals("object", eval("typeof new Number(5)"));
        assertEquals(true, eval("new Number(5) == 5"));
        assertEquals(false, eval("new Number(5) === 5"));
        assertEquals(true, eval("new Number(5) instanceof Number"));
        assertEquals(false, eval("(5) instanceof Number"));
        assertEquals(6, eval("new Number(5) + 1"));
        assertEquals(5, eval("new Number(5).valueOf()"));
        // String() vs new String()
        assertEquals("string", eval("typeof String('x')"));
        assertEquals("object", eval("typeof new String('x')"));
        assertEquals(true, eval("new String('hello') == 'hello'"));
        assertEquals(false, eval("new String('hello') === 'hello'"));
        assertEquals(true, eval("new String('x') instanceof String"));
        assertEquals(false, eval("'x' instanceof String"));
        // Boolean() vs new Boolean()
        assertEquals("boolean", eval("typeof Boolean(true)"));
        assertEquals("object", eval("typeof new Boolean(true)"));
        assertEquals(true, eval("!!new Boolean(false)")); // truthy object!
        assertEquals(true, eval("new Boolean(false) instanceof Boolean"));
        assertEquals(false, eval("false instanceof Boolean"));
    }

    @Test
    void testTypeofCallable() {
        // JsInvokable-registered globals must report "function", not "object"
        assertEquals("function", eval("typeof parseInt"));
        assertEquals("function", eval("typeof parseFloat"));
        assertEquals("function", eval("typeof isNaN"));
        assertEquals("function", eval("typeof isFinite"));
        assertEquals("function", eval("typeof eval"));
        assertEquals("function", eval("typeof encodeURIComponent"));
        assertEquals("function", eval("typeof decodeURIComponent"));
        // JsFunction constructor singletons
        assertEquals("function", eval("typeof String"));
        assertEquals("function", eval("typeof Number"));
        assertEquals("function", eval("typeof Object"));
        assertEquals("function", eval("typeof Array"));
        assertEquals("function", eval("typeof Date"));
        // Namespace objects stay "object"
        assertEquals("object", eval("typeof Math"));
        assertEquals("object", eval("typeof JSON"));
        // Math methods are JsInvokable
        assertEquals("function", eval("typeof Math.max"));
        assertEquals("function", eval("typeof Math.random"));
        assertEquals("function", eval("typeof JSON.stringify"));
        assertEquals("function", eval("typeof JSON.parse"));
        // User functions
        assertEquals("function", eval("typeof (function(){})"));
        assertEquals("function", eval("var f = () => 1; typeof f"));
        // Constructor singletons that extend JsObject (not JsFunction):
        // Boolean/RegExp/Error/TypeError/... must still report "function".
        assertEquals("function", eval("typeof Boolean"));
        assertEquals("function", eval("typeof RegExp"));
        assertEquals("function", eval("typeof Error"));
        assertEquals("function", eval("typeof TypeError"));
        assertEquals("function", eval("typeof RangeError"));
        assertEquals("function", eval("typeof ReferenceError"));
        assertEquals("function", eval("typeof SyntaxError"));
        // Instances of those constructors are objects (not functions).
        assertEquals("object", eval("typeof new Boolean(true)"));
        assertEquals("object", eval("typeof new RegExp('x')"));
        assertEquals("object", eval("typeof new Error('oops')"));
        assertEquals("object", eval("typeof new TypeError('nope')"));
        assertEquals("object", eval("typeof /foo/"));
    }

    @Test
    void testFunctionNameInference() {
        // ES6 name inference: anonymous function/arrow assigned to a binding picks up the key.
        assertEquals("f", eval("var f = function() {}; f.name"));
        assertEquals("g", eval("var g = () => {}; g.name"));
        // Named function expression keeps its own name, not the binding key.
        assertEquals("named", eval("var x = function named() {}; x.name"));
        // Named function declaration: passing it as a parameter or via var must NOT rename it.
        assertEquals("Foo", eval("function Foo() {} var x = Foo; x.name"));
        assertEquals("Foo", eval("function Foo() {} function take(p) { return p.name; } take(Foo)"));
        // Mutation guard: calling take(Foo) must not mutate Foo.name globally.
        assertEquals("Foo", eval("function Foo() {} function take(p) { return p; } take(Foo); Foo.name"));
        // instance.constructor.name chain via a parameter binding.
        assertEquals("Test262Error",
                eval("function Test262Error(m) { this.message = m; }" +
                        " function rethrow(ctor) { throw new Test262Error('x'); }" +
                        " try { rethrow(Test262Error); } catch (e) { e.constructor.name }"));
    }

    @Test
    void testThrownErrorTypeFallsBackToConstructorName() {
        // User-defined error classes that omit .name on the prototype must still
        // surface a meaningful type to host callers via constructor.name lookup.
        assertEquals("MyErr",
                eval("function MyErr(m) { this.message = m; } try { throw new MyErr('oops'); } catch (e) { e.constructor.name }"));
    }

    @Test
    void testNewExpressionChaining() {
        // new binds to the first call expression only
        // new Foo().bar() should parse as (new Foo()).bar()
        assertEquals("hello", eval("new String('hello').valueOf()"));
        assertEquals(5, eval("new Number(5).valueOf()"));
        // Chained method calls on constructed object
        assertEquals("HELLO", eval("new String('hello').toUpperCase()"));
        assertEquals(5, eval("new String('hello').length"));
    }

    @Test
    void testArrayOutOfBounds() {
        // Empty array index access should return undefined/null (JS behavior)
        assertNull(eval("var arr = []; arr[0]"));
        assertNull(eval("var arr = []; arr[99]"));
        assertNull(eval("var arr = [1]; arr[5]"));
        assertNull(eval("var arr = [1, 2, 3]; arr[-1]"));
        // String out of bounds
        assertNull(eval("var s = ''; s[0]"));
        assertNull(eval("var s = 'abc'; s[10]"));
    }

    @Test
    void testIsNaNGlobal() {
        assertEquals(true, eval("isNaN(NaN)"));
        assertEquals(true, eval("isNaN('foo')"));
        assertEquals(true, eval("isNaN(undefined)"));
        assertEquals(true, eval("isNaN()"));
        assertEquals(false, eval("isNaN(0)"));
        assertEquals(false, eval("isNaN(42)"));
        assertEquals(false, eval("isNaN('42')"));
        assertEquals(false, eval("isNaN('3.14')"));
        // The idiom from the D1 benchmark — parseFloat then isNaN
        assertEquals(false, eval("isNaN(parseFloat('850.00'))"));
        assertEquals(true, eval("isNaN(parseFloat('abc'))"));
    }

    @Test
    void testIsFiniteGlobal() {
        assertEquals(true, eval("isFinite(0)"));
        assertEquals(true, eval("isFinite(42)"));
        assertEquals(true, eval("isFinite('42')"));
        assertEquals(false, eval("isFinite(NaN)"));
        assertEquals(false, eval("isFinite(Infinity)"));
        assertEquals(false, eval("isFinite(-Infinity)"));
        assertEquals(false, eval("isFinite('foo')"));
        assertEquals(false, eval("isFinite(undefined)"));
    }

    @Test
    void testOptionalChainingBracket() {
        // Optional chaining with bracket on null/undefined
        assertNull(eval("var arr = null; arr?.[0]"));
        assertNull(eval("var arr = undefined; arr?.[0]"));
        assertEquals(1, eval("var arr = [1, 2, 3]; arr?.[0]"));
    }

    @Test
    void testNullishCoalescing() {
        // Nullish coalescing operator (??)
        assertEquals("default", eval("var obj = null; obj ?? 'default'"));
        assertEquals("default", eval("var obj = undefined; obj ?? 'default'"));
        assertEquals("value", eval("var obj = 'value'; obj ?? 'default'"));
        assertEquals(0, eval("var obj = 0; obj ?? 'default'")); // 0 is not nullish
        assertEquals("", eval("var obj = ''; obj ?? 'default'")); // '' is not nullish
        assertEquals(false, eval("var obj = false; obj ?? 'default'")); // false is not nullish
        // Nullish coalescing with optional chaining
        assertEquals("default", eval("var obj = null; obj?.foo ?? 'default'"));
        assertEquals("default", eval("var obj = { foo: null }; obj?.foo ?? 'default'"));
        assertEquals("value", eval("var obj = { foo: 'value' }; obj?.foo ?? 'default'"));
    }

    @Test
    void testNumbers() {
        assertEquals(1, eval("1"));
        assertEquals(1, eval("1.0"));
        assertEquals(0.5d, eval(".5"));
        assertEquals(65280, eval("0x00FF00"));
        assertEquals(2000001813346120L, eval("2.00000181334612E15"));
    }

    @Test
    void testPost() {
        assertEquals(1, eval("a = 1; a++"));
        assertEquals(2, get("a"));
        assertEquals(1, eval("a = 1; a--"));
        assertEquals(0, get("a"));
        assertEquals(1, eval("a = { b: 1 }; a.b++"));
        NodeUtils.match(get("a"), "{ b: 2 }");
    }

    @Test
    void testPre() {
        assertEquals(2, eval("a = 1; ++a"));
        assertEquals(2, get("a"));
        assertEquals(0, eval("a = 1; --a"));
        assertEquals(0, get("a"));
        assertEquals(-5, eval("a = 5; -a"));
        assertEquals(5, get("a"));
        assertEquals(2, eval("a = 2; +a"));
        assertEquals(2, get("a"));
        assertEquals(3, eval("a = '3'; +a"));
        assertEquals("3", get("a"));
        assertEquals(2, eval("a = { b: 1 }; ++a.b"));
        NodeUtils.match(get("a"), "{ b: 2 }");
    }

    @Test
    void testLiterals() {
        assertNull(eval("null"));
        assertEquals(true, eval("true"));
        assertEquals(false, eval("false"));
        assertEquals("foo", eval("'foo'"));
        assertEquals("bar", eval("\"bar\""));
    }

    @Test
    void testExprList() {
        assertEquals(3, eval("1, 2, 3"));
    }

    @Test
    void testAssign() {
        assertEquals(1, eval("a = 1"));
        assertEquals(1, get("a"));
        assertEquals(2, eval("a = 2; b = a"));
        assertEquals(2, get("b"));
        assertEquals(3, eval("a = 1 + 2"));
        assertEquals(3, get("a"));
        assertEquals(2, eval("a = 1; a += 1"));
        assertEquals(2, get("a"));
        assertEquals(2, eval("a = 3; a -= 1"));
        assertEquals(2, get("a"));
        assertEquals(6, eval("a = 2; a *= 3"));
        assertEquals(6, get("a"));
        assertEquals(3, eval("a = 6; a /= 2"));
        assertEquals(3, get("a"));
        assertEquals(1, eval("a = 3; a %= 2"));
        assertEquals(1, get("a"));
        assertEquals(9, eval("a = 3; a **= 2"));
        assertEquals(9, get("a"));
        eval("var a = { foo: 'bar' }");
        match(get("a"), "{ foo: 'bar' }");
        eval("var a = { 0: 'a', 1: 'b' }");
        match(get("a"), "{ '0': 'a', '1': 'b' }");
    }

    @Test
    void testVarStatement() {
        assertNull(eval("var a"));
        assertNull(get("a")); // undefined is converted to null by toJava()
        assertEquals(1, eval("var a = 1"));
        assertEquals(1, get("a"));
        // Each declarator carries its own (optional) initializer per spec.
        assertEquals(2, eval("var a, b = 2"));
        assertNull(get("a"));
        assertEquals(2, get("b"));
        eval("var x = 1, y = 2, z = 3");
        assertEquals(1, get("x"));
        assertEquals(2, get("y"));
        assertEquals(3, get("z"));
    }

    @Test
    void testExp() {
        assertEquals(512, eval("2 ** 3 ** 2"));
        assertEquals(64, eval("(2 ** 3) ** 2"));
    }

    @Test
    void testBitwise() {
        assertEquals(3, eval("1 | 2"));
        assertEquals(7, eval("3 | 2 | 4"));
        assertEquals(20, eval("5 << 2"));
        assertEquals(4294967295L, eval("var a = -1; a >>>= 0"));
        assertEquals(1, eval("a = 5; a >>= 2"));
        assertEquals(1073741822, eval("a = -5; a >>>= 2"));
    }

    @Test
    void testLogic() {
        assertEquals(true, eval("2 > 1"));
        assertEquals(2, eval("2 || 1"));
        assertEquals("b", eval("'a' && 'b'"));
        assertEquals(true, eval("2 > 1 && 3 < 5"));
        assertEquals(true, eval("2 == '2'"));
        assertEquals(false, eval("2 === '2'"));
    }

    @Test
    void testLogicNonNumbers() {
        assertEquals(false, eval("'a' == 'b'"));
        assertEquals(false, eval("'a' === 'b'"));
        assertEquals(true, eval("'a' == 'a'"));
        assertEquals(true, eval("'a' === 'a'"));
        assertEquals(true, eval("'a' != 'b'"));
        assertEquals(true, eval("'a' !== 'b'"));
        assertEquals(false, eval("'a' != 'a'"));
        assertEquals(false, eval("'a' !== 'a'"));
    }

    @Test
    void testLogicSpecial() {
        assertEquals(false, eval("a = undefined; b = 0; a < b"));
        assertEquals(true, eval("a = ''; b = ''; a == b"));
        assertEquals(false, eval("a = 0; b = -0; a == b"));
        assertEquals(true, eval("a = 0; b = -0; a === b"));
        assertEquals(false, eval("a = 0; b = -0; 1 / a === 1 / b"));
        assertEquals(true, eval("a = Infinity; 1 / a === 0"));
        assertEquals(true, eval("a = -Infinity; 1 / a === -0"));
        assertEquals(true, eval("a = 0; 1 / a === Infinity"));
        assertEquals(true, eval("a = 0; -1 / a === -Infinity"));
        assertEquals(false, eval("a = {}; b = {}; a === b"));
        assertEquals(false, eval("a = []; b = []; a === b"));
        assertEquals(false, eval("a = {}; b = {}; a !== a && b !== b"));
        assertEquals(false, eval("a = []; b = []; a !== a && b !== b"));
        assertEquals(false, eval("!!null"));
        assertEquals(false, eval("!!NaN"));
        assertEquals(false, eval("!!undefined"));
        assertEquals(false, eval("!!''"));
        assertEquals(false, eval("!!0"));
        assertEquals(true, eval("a = null; b = undefined; a == b"));
        assertEquals(false, eval("a = null; b = undefined; a === b"));
        assertEquals(true, eval("a = undefined; b = null; a == b"));
        assertEquals(false, eval("a = undefined; b = null; a === b"));
        assertEquals(true, eval("a = null; b = null; a == b"));
        assertEquals(true, eval("a = null; b = null; a === b"));
        assertEquals(true, eval("a = undefined; b = undefined; a == b"));
        assertEquals(true, eval("a = undefined; b = undefined; a === b"));
        assertEquals(true, eval("a = null; b = null; a == b"));
        assertEquals(true, eval("a = null; b = undefined; a == b"));
        assertEquals(false, eval("a = NaN; b = NaN; a == b"));
        assertEquals(false, eval("a = NaN; b = NaN; a === b"));
        assertEquals(true, eval("a = NaN; b = NaN; a != b"));
        assertEquals(true, eval("a = NaN; b = NaN; a !== b"));
        assertEquals(true, eval("a = NaN; b = NaN; a !== a && b !== b"));
        assertEquals(false, eval("a = NaN; b = NaN; a < b"));
        assertEquals(false, eval("a = NaN; b = NaN; a > b"));
        assertEquals(false, eval("a = NaN; b = NaN; a <= b"));
        assertEquals(false, eval("a = NaN; b = NaN; a >= b"));
        // ES6: null/undefined are only loosely equal to each other, not to other falsy values
        assertEquals(false, eval("0 == null"));
        assertEquals(false, eval("null == 0"));
        assertEquals(false, eval("0 == undefined"));
        assertEquals(false, eval("undefined == 0"));
        assertEquals(false, eval("'' == null"));
        assertEquals(false, eval("null == ''"));
        assertEquals(false, eval("'' == undefined"));
        assertEquals(false, eval("undefined == ''"));
        assertEquals(false, eval("false == null"));
        assertEquals(false, eval("null == false"));
        assertEquals(false, eval("false == undefined"));
        assertEquals(false, eval("undefined == false"));
    }

    @Test
    void testNaNInequality() {
        // NaN != anything (including numbers) should be true
        assertEquals(true, eval("NaN != 5"));
        assertEquals(true, eval("NaN !== 5"));
        assertEquals(true, eval("5 != NaN"));
        assertEquals(true, eval("5 !== NaN"));
        // NaN != NaN should also be true
        assertEquals(true, eval("NaN != NaN"));
        assertEquals(true, eval("NaN !== NaN"));
    }

    @Test
    void testLogicShortCircuit() {
        assertNull(eval("var a = {}; a.b && a.b.c"));
        assertEquals(0, eval("var a = { b: 0 }; a && a.b"));
        assertEquals(0, eval("var a = { b: 0 }; a.b && a.b.c"));
        assertEquals(2, eval("var a = { b: 1, c: 2 }; a.b && a.c"));
        assertEquals(1, eval("var a = { b: 1 }; a.b || a.x.y"));
        assertEquals(2, eval("var a = { b: 0, c: 2 }; a.b || a.c"));
        assertEquals(0, eval("var a = { b: undefined, c: 0 }; a.b || a.c"));
        assertEquals(2, eval("var a = { b: undefined, c: 2 }; a.b || a.c"));
    }

    @Test
    void testLogicAndOrPrecedence() {
        // && has higher precedence than ||
        assertEquals(true, eval("true || false && false"));
        assertEquals(false, eval("false || false && true"));
        assertEquals(true, eval("false && true || true"));
        assertEquals(false, eval("false && true || false"));
    }

    @Test
    void testRelationalEqualityPrecedence() {
        // relational operators (<, >, <=, >=) have higher precedence than equality (==, ===)
        // 2 < 3 == 1 should be (2 < 3) == 1 => true == 1 => true (not (2 < 3) == 1 parsed left-to-right as ((2 < 3) == 1))
        // Actually in JS: true == 1 is true (coercion), so this won't catch the bug
        // Better test: 5 > 3 == 2 should be (5 > 3) == 2 => true == 2 => false
        // If parsed wrong as (5 > 3) == 2, it's still false
        // Even better: 1 == 2 < 3 should be 1 == (2 < 3) => 1 == true => true
        // If parsed wrong: (1 == 2) < 3 => false < 3 => true (same result!)
        // Best test: 0 == 1 < 2 should be 0 == (1 < 2) => 0 == true => false
        // If wrong: (0 == 1) < 2 => false < 2 => true (different!)
        assertEquals(false, eval("0 == 1 < 2"));
        assertEquals(true, eval("1 == 1 < 2"));
    }

    @Test
    void testBitwisePrecedence() {
        // In JS: & has higher precedence than ^, and ^ has higher precedence than |
        // Also: bitwise operators have lower precedence than equality
        // Test: 1 | 2 & 4 should be 1 | (2 & 4) => 1 | 0 => 1
        // If wrong (left-to-right): (1 | 2) & 4 => 3 & 4 => 0
        assertEquals(1, eval("1 | 2 & 4"));
        // Test: 1 ^ 2 & 4 should be 1 ^ (2 & 4) => 1 ^ 0 => 1
        // If wrong: (1 ^ 2) & 4 => 3 & 4 => 0
        assertEquals(1, eval("1 ^ 2 & 4"));
        // Test: 1 | 2 ^ 3 should be 1 | (2 ^ 3) => 1 | 1 => 1
        // If wrong: (1 | 2) ^ 3 => 3 ^ 3 => 0
        assertEquals(1, eval("1 | 2 ^ 3"));
    }

    @Test
    void testInstanceofPrecedence() {
        // instanceof has same precedence as relational operators, higher than equality
        // Create a constructor function
        String js = "function Foo() {}; var foo = new Foo(); ";
        // First verify instanceof works
        assertEquals(true, eval(js + "foo instanceof Foo"));
        // Test precedence: foo instanceof Foo == true should be (foo instanceof Foo) == true => true == true => true
        // If wrong precedence: foo instanceof (Foo == true) would fail or give wrong result
        assertEquals(true, eval(js + "foo instanceof Foo == true"));
        assertEquals(false, eval(js + "foo instanceof Foo == false"));
    }

    @Test
    void testErrorInstanceOf() {
        // same-type: an instance is instanceof its own constructor
        assertEquals(true, eval("new Error() instanceof Error"));
        assertEquals(true, eval("new TypeError() instanceof TypeError"));
        assertEquals(true, eval("new RangeError() instanceof RangeError"));
        assertEquals(true, eval("new ReferenceError() instanceof ReferenceError"));
        assertEquals(true, eval("new SyntaxError() instanceof SyntaxError"));
        // base-class: all NativeError types inherit from Error
        assertEquals(true, eval("new TypeError() instanceof Error"));
        assertEquals(true, eval("new RangeError() instanceof Error"));
        assertEquals(true, eval("new ReferenceError() instanceof Error"));
        assertEquals(true, eval("new SyntaxError() instanceof Error"));
        assertEquals(true, eval("new URIError() instanceof Error"));
        assertEquals(true, eval("new EvalError() instanceof Error"));
        // cross-type: different NativeError types are NOT instanceof each other
        assertEquals(false, eval("new RangeError() instanceof TypeError"));
        assertEquals(false, eval("new TypeError() instanceof RangeError"));
        assertEquals(false, eval("new SyntaxError() instanceof ReferenceError"));
        // base-error is not an instanceof any NativeError subtype
        assertEquals(false, eval("new Error() instanceof TypeError"));
        assertEquals(false, eval("new Error() instanceof RangeError"));
    }

    @Test
    void testErrorConstructor() {
        // .constructor on a thrown/newed error points to the global constructor
        assertEquals(true, eval("new Error().constructor === Error"));
        assertEquals(true, eval("new TypeError().constructor === TypeError"));
        assertEquals(true, eval("new RangeError().constructor === RangeError"));
        assertEquals(true, eval("new ReferenceError().constructor === ReferenceError"));
        // cross-type: constructor identity is strict, not name-based
        assertEquals(false, eval("new RangeError().constructor === TypeError"));
        assertEquals(false, eval("new TypeError().constructor === Error"));
        // the test262 pattern: assert.throws(RangeError, fn) equivalent
        assertEquals(true, eval(
                "var caught; try { throw new RangeError('x') } catch(e) { caught = e }; " +
                        "caught.constructor === RangeError"));
        assertEquals(false, eval(
                "var caught; try { throw new RangeError('x') } catch(e) { caught = e }; " +
                        "caught.constructor === TypeError"));
    }

    @Test
    void testUnary() {
        assertEquals(true, eval("!false"));
        assertEquals(-6, eval("~5"));
        assertEquals(2, eval("~~(2.7)"));
    }

    @Test
    void testUnaryPrecedence() {
        assertEquals(true, eval("var foo = false; var bar = true; !foo || bar"));
        assertEquals(false, eval("var foo = true; var bar = false; !foo || bar"));
        assertEquals(true, eval("var foo = false; var bar = true; !foo && bar"));
        assertEquals(false, eval("var foo = true; var bar = true; !foo && bar"));
        assertEquals(-3, eval("~5 + 3"));
        assertEquals(false, eval("var a = false; var b = false; !a == b"));
    }

    @Test
    void testParseInt() {
        assertEquals(42, eval("parseInt('42')"));
        assertEquals(42, eval("parseInt('042')"));
        assertEquals(42, eval("parseInt('42px')"));
        assertEquals(3, eval("parseInt('3.14')"));
        assertEquals(255, eval("parseInt('0xFF')"));
        assertEquals(Double.NaN, eval("parseInt('abc')"));
        // radix support
        assertEquals(5, eval("parseInt('101', 2)"));
        assertEquals(42, eval("parseInt('42', 10)"));
        assertEquals(255, eval("parseInt('ff', 16)"));
        assertEquals(255, eval("parseInt('FF', 16)"));
        assertEquals(8, eval("parseInt('10', 8)"));
        assertEquals(Double.NaN, eval("parseInt('', 10)"));
        assertEquals(1, eval("parseInt('1z', 10)"));
    }

    @Test
    void testParseFloat() {
        assertEquals(3.14, eval("parseFloat('3.14')"));
        assertEquals(42, eval("parseFloat('42')"));
        assertEquals(42.99, eval("parseFloat('42.99px')"));
        assertEquals(Double.NaN, eval("parseFloat('abc')"));
        // assertEquals( 1000, eval("parseFloat('1e3')")); TODO
    }

    @Test
    void testIfStatement() {
        eval("if (true) a = 1");
        assertEquals(1, get("a"));
        eval("if (false) a = 1");
        assertNull(get("a"));
        eval("if (false) a = 1; else a = 2");
        assertEquals(2, get("a"));
        eval("a = 1; if (a) b = 2");
        assertEquals(2, get("b"));
        eval("a = 0; if (a) b = 2");
        assertNull(get("b"));
        eval("a = ''; if (a) b = 1; else b = 2");
        assertEquals(2, get("b"));
        assertEquals(true, eval("if (false) { false } else { true }"));
        assertEquals(false, eval("if (false) { false } else { false }"));
    }

    @Test
    void testForLoop() {
        eval("var a = []; for (var i = 0; i < 3; i++) a.push(i)");
        match(get("a"), "[0, 1, 2]");
        eval("var a = []; for (var i = 0; i < 3; i++) { if (i == 1) break; a.push(i) }");
        match(get("a"), "[0]");
        eval("var a = []; for (var i = 0; i < 3; i++) { if (i == 1) continue; a.push(i) }");
        match(get("a"), "[0, 2]");
    }

    @Test
    void testBreakInForInsideIf() {
        // break inside for loop nested in if block should only exit the for loop
        eval("""
                var items = [{id: 'a'}, {id: 'b'}, {id: 'c'}]
                var target = 'b'
                var found = -1
                if (true) {
                    for (var i = 0; i < items.length; i++) {
                        if (items[i].id === target) {
                            found = i
                            break
                        }
                    }
                    var afterLoop = 'reached'
                }
                """);
        assertEquals(1, get("found"));
        assertEquals("reached", get("afterLoop"));
    }

    @Test
    void testBreakInForInInsideIf() {
        // break in for-in loop nested in if block
        eval("""
                var obj = {a: 1, b: 2, c: 3}
                var found = ''
                if (true) {
                    for (var k in obj) {
                        if (k === 'b') break
                    }
                    found = 'reached'
                }
                """);
        assertEquals("reached", get("found"));
    }

    @Test
    void testBreakInForOfInsideIf() {
        // break in for-of loop nested in if block
        eval("""
                var arr = [1, 2, 3]
                var found = ''
                var x
                if (true) {
                    for (x of arr) {
                        if (x === 2) break
                    }
                    found = 'reached'
                }
                """);
        assertEquals("reached", get("found"));
    }

    @Test
    void testBreakInWhileInsideIf() {
        // break in while loop nested in if block
        eval("""
                var i = 0
                if (true) {
                    while (i < 10) {
                        if (i === 3) break
                        i++
                    }
                    var afterWhile = 'reached'
                }
                """);
        assertEquals(3, get("i"));
        assertEquals("reached", get("afterWhile"));
    }

    @Test
    void testBreakInDoWhileInsideIf() {
        // break in do-while loop nested in if block
        eval("""
                var i = 0
                if (true) {
                    do {
                        if (i === 3) break
                        i++
                    } while (i < 10)
                    var afterDoWhile = 'reached'
                }
                """);
        assertEquals(3, get("i"));
        assertEquals("reached", get("afterDoWhile"));
    }

    @Test
    void testReturnStillPropagatesFromLoop() {
        // return inside a loop should still propagate out of the function
        Object result = eval("""
                function findIndex(items, target) {
                    for (var i = 0; i < items.length; i++) {
                        if (items[i] === target) {
                            return i
                        }
                    }
                    return -1
                }
                findIndex(['a', 'b', 'c'], 'b')
                """);
        assertEquals(1, result);
    }

    @Test
    void testForInLoop() {
        eval("var a = []; for (var x in {a: 1, b: 2, c: 3}) a.push(x)");
        match(get("a"), "['a', 'b', 'c']");
        eval("var a = []; for (var x in {a: 1, b: 2, c: 3}) { if (x == 'b') break; a.push(x) }");
        match(get("a"), "['a']");
        eval("var a = []; for (var x in {a: 1, b: 2, c: 3}) { if (x == 'b') continue; a.push(x) }");
        match(get("a"), "['a', 'c']");
        eval("var a = []; for (var x in [1, 2, 3]) a.push(x)");
        match(get("a"), "['0', '1', '2']");
        eval("var a = []; for (var x in [1, 2, 3]) { if (x == '1') break; a.push(x) }");
        match(get("a"), "['0']");
        eval("var a = []; for (var x in [1, 2, 3]) { if (x == '1') continue; a.push(x) }");
        match(get("a"), "['0', '2']");
    }

    @Test
    void testForOfLoop() {
        eval("var a = []; var x; for (x of {a: 1, b: 2, c: 3}) a.push(x)");
        match(get("a"), "[1, 2, 3]");
        eval("var a = []; var x; for (x of {a: 1, b: 2, c: 3}) { if (x == 2) break; a.push(x) }");
        match(get("a"), "[1]");
        eval("var a = []; var x; for (x of {a: 1, b: 2, c: 3}) { if (x == 2) continue; a.push(x) }");
        match(get("a"), "[1, 3]");
        eval("var a = []; var x; for (x of [1, 2, 3]) a.push(x)");
        match(get("a"), "[1, 2, 3]");
        eval("var a = []; var x; for (x of [1, 2, 3]) { if (x == 2) break; a.push(x) }");
        match(get("a"), "[1]");
        eval("var a = []; var x; for (x of [1, 2, 3]) { if (x == 2) continue; a.push(x) }");
        match(get("a"), "[1, 3]");
        eval("var a = []; for (const x of {a: 1, b: 2, c: 3}) a.push(x)");
        match(get("a"), "[1, 2, 3]");
    }

    @Test
    void testWhileLoop() {
        eval("var i = 0; var a = []; while (i < 3) { a.push(i); i++ }");
        match(get("a"), "[0, 1, 2]");
        eval("var i = 0; var a = []; while (i < 3) { if (i == 1) break; a.push(i); i++ }");
        match(get("a"), "[0]");
        eval("var i = 0; var a = []; while (i < 3) { if (i == 1) { i++; continue }; a.push(i); i++ }");
        match(get("a"), "[0, 2]");
        // ensure extra semicolons in blocks are ignored
        eval("var i = 0; var a = []; while (i < 3) { if (i == 1) { i++;; continue;; };; a.push(i);; i++;; };;");
        match(get("a"), "[0, 2]");
    }

    @Test
    void testDoWhileLoop() {
        eval("var i = 0; var a = []; do { a.push(i); i++ } while (i < 3)");
        match(get("a"), "[0, 1, 2]");
        eval("var i = 0; var a = []; do { if (i == 1) break; a.push(i); i++ } while (i < 3)");
        match(get("a"), "[0]");
        eval("var i = 0; var a = []; do { if (i == 1) { i++; continue }; a.push(i); i++ } while (i < 3)");
        match(get("a"), "[0, 2]");
    }

    @Test
    void testTernary() {
        eval("a = true ? 1 : 2");
        assertEquals(1, get("a"));
        eval("a = false ? 1 : 2");
        assertEquals(2, get("a"));
        eval("a = 5; b = a > 3 ? 'foo' : 4 + 5");
        assertEquals("foo", get("b"));
        eval("a = 5; b = a < 3 ? 'foo' : 4 + 5");
        assertEquals(9, get("b"));
        match(eval("1 && 0 ? 'foo' : 'bar'"), "bar");
        match(eval("0 && 1 ? 'foo' : 'bar'"), "bar");
        match(eval("var a = { b: false }; a && a.b ? 1 : 2"), "2");
    }

    @Test
    void testTypeOf() {
        assertEquals("string", eval("typeof 'foo'"));
        assertEquals("function", eval("var a = function(){}; typeof a"));
        assertEquals("object", eval("typeof new Error('foo')"));
        assertEquals(true, eval("typeof 'foo' === 'string'"));
        assertEquals("undefined", eval("typeof bar"));
    }

    @Test
    void testTryCatch() {
        eval("var a; try { throw 'foo' } catch (e) { a = e }");
        assertEquals("foo", get("a"));
        eval("var a, b; try { throw 'foo' } catch (e) { a = e; if (true) return; a = null } finally { b = 2 }");
        assertEquals("foo", get("a"));
        assertEquals(2, get("b"));
        eval("var a; try { } finally { a = 3 }");
        assertEquals(3, get("a"));
        eval("var a; try { throw 'foo' } catch { a = 'bar' }");
        assertEquals("bar", get("a"));
    }

    @Test
    void testBracketExpression() {
        assertNull(eval("var foo = {}; foo['bar']"));
    }

    @Test
    void testDotExpressionUndefined() {
        assertNull(eval("var foo = {}; foo.bar"));
    }

    @Test
    void testDotExpressionFailure() {
        try {
            eval("foo.bar");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("foo is not defined"));
        }
        try {
            eval("var obj = { a: null }; obj.a.b");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot read properties of null (reading 'b')"));
        }
        try {
            eval("var obj = { }; obj.a.b");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot read properties of undefined (reading 'b')"));
        }
    }

    @Test
    void testDotExpressionOptional() {
        assertNull(eval("var foo = null; foo?.bar"));
        assertNull(eval("var foo = undefined; foo?.bar"));
        assertNull(eval("var foo; foo?.bar"));
        assertEquals("baz", eval("var foo = { bar: 'baz' }; foo?.bar"));
        assertEquals(42, eval("var obj = { num: 42 }; obj?.num"));
        assertNull(eval("var obj = null; obj?.a?.b?.c"));
        assertNull(eval("var obj = { a: null }; obj?.a?.b?.c"));
        assertNull(eval("var obj = { a: { b: null } }; obj?.a?.b?.c"));
        assertEquals("deep", eval("var obj = { a: { b: { c: 'deep' } } }; obj?.a?.b?.c"));
        assertEquals("value", eval("var obj = { a: { b: 'value' } }; obj?.a.b"));
        try {
            eval("var obj = { a: null }; obj?.a.b");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot read properties of null (reading 'b')"));
        }
        // bracket
        assertNull(eval("var obj = null; obj?.['key']"));
        assertEquals("value", eval("var obj = { key: 'value' }; obj?.['key']"));
        assertEquals("dynamic", eval("var obj = { foo: 'dynamic' }; var key = 'foo'; obj?.[key]"));
        // function call
        assertNull(eval("var obj = null; obj?.method()"));
        assertEquals("called", eval("var obj = { method: function() { return 'called'; } }; obj?.method()"));
        assertNull(eval("var obj = { method: null }; obj?.method?.()"));
    }

    @Test
    void testThrow() {
        try {
            eval("function a(b){ b() }; a(() => { throw new Error('foo') })");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("foo"));
        }
    }

    @Test
    void testThrowFunction() {
        try {
            eval("function a(b){ this.bar = 'baz'; b() }; a(function(){ throw new Error('foo') })");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("foo"));
            JsFunction fn = (JsFunction) get("a");
            assertEquals("baz", fn.getMember("bar"));
        }
    }

    @Test
    void testSwitch() {
        eval("var a = 2; var b; switch (a) { case 2: b = 1 }");
        assertEquals(1, get("b"));
        eval("var a = 2; var b; switch (a) { case 1: b = 1; break; case 2: b = 2; break }");
        assertEquals(2, get("b"));
        eval("var a = 2; var b; switch (a) { case 1: b = 1; break; default: b = 2 }");
        assertEquals(2, get("b"));
        eval("var a = 1; var b; switch (a) { case 1: b = 1; default: b = 2 }");
        assertEquals(2, get("b"));
        // multiple statements in case body
        eval("var a = 'x'; var b; switch (a) { case 'x': var c = 1; b = c }");
        assertEquals(1, get("b"));
        eval("var a = 'x'; var b; switch (a) { case 'x': let c = 1; b = c }");
        assertEquals(1, get("b"));
    }

    @Test
    void testSwitchBreakScoping() {
        // break inside switch must not escape the enclosing if block
        eval("var after = false; if (true) { switch ('a') { case 'a': break; case 'b': break } after = true }");
        assertEquals(true, get("after"));
        // break inside switch must not escape the enclosing for loop
        eval("var count = 0; for (var i = 0; i < 3; i++) { switch (i) { case 0: break; case 1: break } count++ }");
        assertEquals(3, get("count"));
        // break inside switch must not escape the enclosing while loop
        eval("var count = 0; var i = 0; while (i < 3) { switch (i) { case 0: break; case 1: break } count++; i++ }");
        assertEquals(3, get("count"));
        // break only unwinds the innermost switch (nested switches)
        eval("var after = false; switch ('x') { case 'x': switch ('y') { case 'y': break } after = true; break }");
        assertEquals(true, get("after"));
        // default branch hit but break still confined to switch
        eval("var after = false; if (true) { switch ('z') { case 'a': break; default: break } after = true }");
        assertEquals(true, get("after"));
        // return inside a switch inside a function still propagates out
        eval("function f() { switch (1) { case 1: return 42 } return 0 } var r = f()");
        assertEquals(42, get("r"));
        // continue inside a switch inside a for loop still propagates to the loop
        eval("var hits = 0; for (var i = 0; i < 3; i++) { switch (i) { case 1: continue } hits++ }");
        assertEquals(2, get("hits"));
    }

    @Test
    void testLetAndConstBasic() {
        assertEquals(1, eval("let a = 1; a"));
        assertEquals(1, get("a"));
        assertEquals(2, eval("let b = 1; b = 2"));
        assertEquals(2, get("b"));
        assertEquals(1, eval("const a = 1; a"));
        assertEquals(1, get("a"));
    }

    @Test
    void testConstReassign() {
        try {
            eval("const a = 1; a = 2");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("assignment to constant: a"));
        }
    }

    @Test
    void testConstRedeclare() {
        try {
            eval("const a = 1; const a = 2");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("identifier 'a' has already been declared"));
        }
        try {
            eval("let b = 1; let b = 2");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("identifier 'b' has already been declared"));
        }
        try {
            eval("const c = 1; let c = 2");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("identifier 'c' has already been declared"));
        }
    }

    @Test
    void testBlockScopeVarHoisting() {
        eval("{ var a = 1; { var b = 2; } }");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
    }

    @Test
    void testBlockScopeLet() {
        assertEquals(2, eval("{ let a = 1; } var a = 2; a"));
        assertEquals(1, eval("var a = 1; { let a = 2; } a"));
        try {
            eval("{ let a = 1; } a");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("a is not defined"));
        }
        assertEquals(3, eval("let a = 1; { let a = 2; { let a = 3; a } }"));
    }

    @Test
    void testForLoopScope() {
        matchEval("var funcs = []; for (let i = 0; i < 3; i++) { funcs.push(() => i) }; funcs.map(f => f())", "[0, 1, 2]");
        matchEval("var funcs = []; for (var i = 0; i < 3; i++) { funcs.push(() => i) }; funcs.map(f => f())", "[3, 3, 3]");
        matchEval("var funcs = []; for (let x of [10, 20, 30]) { funcs.push(() => x) }; funcs.map(f => f())", "[10, 20, 30]");
        matchEval("var funcs = []; for (var x of [10, 20, 30]) { funcs.push(() => x) }; funcs.map(f => f())", "[30, 30, 30]");
        matchEval("var funcs = []; var obj = {a: 1, b: 2, c: 3}; for (let k in obj) { funcs.push(() => k) }; funcs.map(f => f())", "[a, b, c]");
        matchEval("var funcs = []; var obj = {a: 1, b: 2, c: 3}; for (var k in obj) { funcs.push(() => k) }; funcs.map(f => f())", "[c, c, c]");
    }

    @Test
    void testVarArrayDestructuring() {
        eval("var [a, b] = [1, 2]");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        eval("let [a, , c] = [1, 2, 3]");
        assertEquals(1, get("a"));
        assertEquals(3, get("c"));
        eval("let [a = 5] = []");
        assertEquals(5, get("a"));
        eval("let [a = 5] = [undefined]");
        assertEquals(5, get("a"));
        eval("let [a = 5] = [null]");
        assertNull(get("a"));
        eval("let [a, ...rest] = [1, 2, 3]");
        assertEquals(1, get("a"));
        match(get("rest"), "[2, 3]");
        eval("let [a, ...rest] = [1]");
        assertEquals(1, get("a"));
        match(get("rest"), "[]");
        eval("var res = []; var o = { a: 1, b: 2 }; for (const [k, v] of Object.entries(o)) { res.push(k + v) }");
        match(get("res"), "['a1', 'b2']");
    }

    @Test
    void testVarObjectDestructuring() {
        eval("let { a: x, b: y } = { a: 1, b: 2 }");
        assertEquals(1, get("x"));
        assertEquals(2, get("y"));
        eval("let { a, b } = { a: 1, b: 2 }");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        eval("let {a = 5} = {}");
        assertEquals(5, get("a"));
        eval("let {a, ...rest} = {a: 1, b: 2, c: 3}");
        assertEquals(1, get("a"));
        match(get("rest"), "{b: 2, c: 3}");
        eval("let {a, ...rest} = {a: 1}");
        assertEquals(1, get("a"));
        match(get("rest"), "{}");
        eval("var res = []; var list = [{ a: 1 }, { a: 2 }]; for (const {a: x} of list) { res.push(x) }");
        match(get("res"), "[1, 2]");
    }

    @Test
    void testAssignDestructuring() {
        eval("[x, y] = [1, 2]");
        assertEquals(1, get("x"));
        assertEquals(2, get("y"));
        eval("({ a: x } = { a: 2 })");
        assertEquals(2, get("x"));
    }

    @Test
    void testCommaOperatorInParens() {
        // sequence operator returns the last value
        assertEquals(3, eval("(1, 2, 3)"));
        assertEquals("c", eval("('a', 'b', 'c')"));
        // side effects in earlier expressions are observed
        eval("var x = 0; var y = (x = 1, x + 1)");
        assertEquals(1, get("x"));
        assertEquals(2, get("y"));
        // in an assignment rhs
        eval("var a; a = (1, 2, 3)");
        assertEquals(3, get("a"));
    }

    @Test
    void testCommaOperatorInForLoop() {
        // idiomatic for-loop with multi-declarator var and comma-sequence update
        eval("var sum = 0; for (var i = 0, j = 10; i < 3; i++, j--) { sum += i + j; }");
        // iterations: (0,10)+(1,9)+(2,8) = 10+10+10 = 30
        assertEquals(30, get("sum"));
    }

    @Test
    void testCommaOperatorInReturnAndThrow() {
        eval("function f() { return 1, 2, 3; } var r = f()");
        assertEquals(3, get("r"));
        eval("var caught; try { throw (1, 'err-msg'); } catch (e) { caught = e; }");
        assertEquals("err-msg", get("caught"));
    }

    @Test
    void testMultiDeclaratorVar() {
        eval("var a = 1, b = 2, c = 3");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        assertEquals(3, get("c"));
        // mixed — no-init declarators are undefined; initialized ones use their own expr
        eval("var p, q = 10, r, s = 20");
        assertNull(get("p"));
        assertEquals(10, get("q"));
        assertNull(get("r"));
        assertEquals(20, get("s"));
        // initializer side effects observable during the declaration
        eval("var m = 1, n = m + 1");
        assertEquals(1, get("m"));
        assertEquals(2, get("n"));
    }

    @Test
    void testMultiDeclaratorLet() {
        eval("let a = 1, b = 2");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        // let per-iteration isolation with multiple loop vars — each closure snapshots (i, j)
        eval("var captured = [];\n" +
             "for (let i = 0, j = 10; i < 3; i++, j--) {\n" +
             "  captured.push(function() { return i + '/' + j; });\n" +
             "}\n" +
             "var out = captured.map(function (f) { return f(); }).join(',')");
        assertEquals("0/10,1/9,2/8", get("out"));
    }

    @Test
    void testMultiDeclaratorConstRequiresInit() {
        // const without initializer is a parse error
        assertThrows(Exception.class, () -> eval("const a = 1, b"));
    }

    @Test
    void testCommaOperatorInConditions() {
        // if condition
        eval("var taken; if (0, 1) { taken = 'then'; } else { taken = 'else'; }");
        assertEquals("then", get("taken"));
        // while condition — stops when last value is falsy
        eval("var n = 0; while (n++, n < 3) { }");
        assertEquals(3, get("n"));
    }

}

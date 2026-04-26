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
        // Prototype method refs — raw (JsCallable) this::method lambdas must
        // still report "function", not "object".
        assertEquals("function", eval("typeof [1,2,3].map"));
        assertEquals("function", eval("typeof [1,2,3].filter"));
        assertEquals("function", eval("typeof [1,2,3].slice"));
        assertEquals("function", eval("typeof 'x'.charAt"));
        assertEquals("function", eval("typeof 'x'.indexOf"));
        assertEquals("function", eval("typeof 'x'.toUpperCase"));
        assertEquals("function", eval("typeof ({}).hasOwnProperty"));
        assertEquals("function", eval("typeof ({}).toString"));
        assertEquals("function", eval("typeof (5).toFixed"));
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
        // Bitwise / shift compound assignments — &=, |=, ^=, <<=, >>=, >>>=
        assertEquals(1, eval("a = 5; a &= 3"));
        assertEquals(1, get("a"));
        assertEquals(7, eval("a = 5; a |= 3"));
        assertEquals(7, get("a"));
        assertEquals(6, eval("a = 5; a ^= 3"));
        assertEquals(6, get("a"));
        assertEquals(8, eval("a = 1; a <<= 3"));
        assertEquals(8, get("a"));
        assertEquals(2, eval("a = 16; a >>= 3"));
        assertEquals(2, get("a"));
        assertEquals(2, eval("a = 16; a >>>= 3"));
        assertEquals(2, get("a"));
        // Binary / octal numeric literals
        assertEquals(2, eval("0b10"));
        assertEquals(13, eval("0b1101"));
        assertEquals(15, eval("0o17"));
        assertEquals(63, eval("0O77"));
        // Binary / octal literals as computed object keys
        assertEquals("get string", eval("var o = { get [0b10]() { return 'get string'; } }; o['2']"));
        assertEquals("v", eval("var o = { [0o17]: 'v' }; o['15']"));
        // void unary operator
        assertNull(eval("void 0"));
        assertNull(eval("void 'anything'"));
        eval("var sideEffect = false; void (sideEffect = true);");
        assertEquals(true, get("sideEffect"));
        // void with object literal at statement start (parser-disambiguation case)
        assertNull(eval("void { a: 1, b: 2 }"));
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
        // Per spec, for-of requires an iterable — plain objects without @@iterator
        // throw TypeError. Use an array for value iteration; for-in is the right
        // construct for enumerating object properties.
        eval("var a = []; var x; for (x of [1, 2, 3]) a.push(x)");
        match(get("a"), "[1, 2, 3]");
        eval("var a = []; var x; for (x of [1, 2, 3]) { if (x == 2) break; a.push(x) }");
        match(get("a"), "[1]");
        eval("var a = []; var x; for (x of [1, 2, 3]) { if (x == 2) continue; a.push(x) }");
        match(get("a"), "[1, 3]");
        eval("var a = []; for (const x of [1, 2, 3]) a.push(x)");
        match(get("a"), "[1, 2, 3]");
        // String iteration yields code-unit chars per spec
        eval("var a = []; for (const c of 'abc') a.push(c)");
        match(get("a"), "['a', 'b', 'c']");
    }

    @Test
    void testForOfNonIterableThrows() {
        // for-of on null/undefined/plain-object/number must throw TypeError
        // (ECMAScript 13.7.5.13 ForIn/OfHeadEvaluation step 6).
        try {
            eval("for (const x of null) {}");
            throw new AssertionError("expected TypeError on for-of null");
        } catch (RuntimeException expected) {
            // ok
        }
        try {
            eval("for (const x of undefined) {}");
            throw new AssertionError("expected TypeError on for-of undefined");
        } catch (RuntimeException expected) {
            // ok
        }
        try {
            eval("for (const x of {a: 1}) {}");
            throw new AssertionError("expected TypeError on for-of plain object");
        } catch (RuntimeException expected) {
            // ok
        }
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
    void testOptionalChainLongShortCircuit() {
        // When `?.` fires (LHS is nullish), the WHOLE chain short-circuits to undefined,
        // not just the immediate access. Per spec 13.3.9: a chain head's nullish base
        // makes the whole OptionalExpression evaluate to undefined.
        assertNull(eval("var a = undefined; a?.b.c"));
        assertNull(eval("var a = undefined; a?.b.c.d"));
        assertNull(eval("var a = null; a?.b.c.d.e"));
        assertNull(eval("var a = undefined; a?.b().c"));
        assertNull(eval("var a = null; a?.b.c().d"));
        assertNull(eval("var a = undefined; a?.[0].x"));
        assertNull(eval("var a = null; a?.[0].x.y"));
        // chain succeeds, then short-circuits at a NESTED ?. that goes nullish
        assertNull(eval("var a = { b: null }; a?.b?.c.d"));
        // chain succeeds and reads through normally
        assertEquals(42, eval("var a = { b: { c: { d: 42 } } }; a?.b.c.d"));
        // ?. does NOT short-circuit when an intermediate value is null but the
        // optional was on a non-nullish step — `null.b` still throws TypeError.
        try {
            eval("var obj = { a: null }; obj?.a.b.c");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot read properties of null"));
        }
    }

    @Test
    void testOptionalChainSideEffectShortCircuit() {
        // When the chain head is nullish, no further sub-expressions in the chain
        // are evaluated — so increments / function args inside the tail must not run.
        assertEquals(1, eval("var a = undefined; var x = 1; a?.[++x]; x"));
        assertEquals(1, eval("var a = null; var x = 1; a?.b.c(++x).d; x"));
        assertEquals(1, eval("var a = undefined; var x = 1; a?.b(++x); x"));
        // when the chain succeeds, side effects DO run
        assertEquals(20, eval("var a = [10, 20]; var x = 0; a?.[++x]"));
        assertEquals(1, eval("var a = [10, 20]; var x = 0; a?.[++x]; x"));
    }

    @Test
    void testOptionalCallActuallyInvokes() {
        // `a?.()` must call `a`, not just return its value (regression: previously
        // returned the function value without invoking).
        assertEquals(99, eval("var f = function(){ return 99; }; f?.()"));
        assertEquals(10, eval("var f = function(x){ return x * 2; }; f?.(5)"));
        // null/undefined target short-circuits
        assertNull(eval("var f = null; f?.()"));
        assertNull(eval("var f = undefined; f?.(throwIfCalled())"));
    }

    @Test
    void testOptionalCallPreservesThis() {
        // `a.b?.()` must keep `a` as receiver — otherwise `this` inside b is wrong.
        // Spec EvaluateCall passes baseValue as thisValue.
        String setup = "var a = { b: function(){ return this._b; }, _b: 42 }; ";
        assertEquals(42, eval(setup + "a.b?.()"));
        assertEquals(42, eval(setup + "a?.b?.()"));
        assertEquals(42, eval(setup + "a?.b()"));
        // method chain after optional call
        String setup2 = "var a = { b: function(){ return this._b; }, _b: { c: 99 } }; ";
        assertEquals(99, eval(setup2 + "a.b?.().c"));
        assertEquals(99, eval(setup2 + "a?.b?.().c"));
    }

    @Test
    void testParenPreservesReceiver() {
        // Per spec, parens preserve the Reference Record — `(a.b)()` and `(a?.b)()`
        // call with `this = a`. Previously parens dropped the receiver.
        String setup = "var a = { b: function(){ return this._b; }, _b: 42 }; ";
        assertEquals(42, eval(setup + "(a.b)()"));
        assertEquals(42, eval(setup + "(a?.b)()"));
        assertEquals(42, eval(setup + "(a.b)?.()"));
        assertEquals(42, eval(setup + "(a?.b)?.()"));
        // BUT — parens terminate the optional chain. `(a?.b).c` does NOT
        // short-circuit when a is nullish; instead `(undefined).c` throws.
        try {
            eval("var a = null; (a?.b).c");
            fail("error expected — parens terminate optional chain");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot read properties"));
        }
    }

    @Test
    void testQuesDotDecimalLookahead() {
        // Per spec `OptionalChainingPunctuator :: ?. [lookahead ∉ DecimalDigit]`,
        // so `1 ?.5 : 0` lexes as ternary `1 ? .5 : 0`, not `1 ?. 5 : 0`.
        assertEquals(0.5d, eval("true ?.5 : 0"));
        assertEquals(0.5d, eval("var x = true; x?.5 : 0"));
        // and `?.` is still recognized when followed by non-digit
        assertEquals(1, eval("var a = { b: 1 }; a?.b"));
    }

    @Test
    void testNullishCoalescingShortCircuit() {
        // RHS not evaluated when LHS is non-nullish.
        assertEquals(0, eval("var x = 0; (function(){ x = 1; return 'r'; })(), 0 ?? (function(){ x = 2; return 'r'; })()"));
        // chained ??
        assertEquals(1, eval("null ?? null ?? 1"));
        assertEquals(2, eval("null ?? 2 ?? 1"));
        // RHS-only side effects fire when LHS IS nullish
        assertEquals(7, eval("var x = 0; var r = null ?? (x = 7); x"));
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
    void testNestedDeclarationDestructuring() {
        // Nested array pattern
        eval("var [a, [b, c]] = [1, [2, 3]]");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        assertEquals(3, get("c"));
        // Nested object pattern
        eval("var {x: {y}} = {x: {y: 99}}");
        assertEquals(99, get("y"));
        // Rename + default: {a: b = default}
        eval("var {a: b = 5} = {a: 10}");
        assertEquals(10, get("b"));
        eval("var {a: b = 5} = {}");
        assertEquals(5, get("b"));
        // Shorthand default fires only on undefined
        eval("var {a = 5} = {a: 10}");
        assertEquals(10, get("a"));
        eval("var {a = 5} = {a: null}");
        assertNull(get("a"));
        // Nested pattern with default
        eval("var [[a = 5] = []] = [[]]");
        assertEquals(5, get("a"));
        // let/const work the same way
        eval("let {foo: {bar}} = {foo: {bar: 'deep'}}");
        assertEquals("deep", get("bar"));
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

    @Test
    void testObjectGettersAndSetters() {
        // Basic getter.
        assertEquals(42, eval("({ get foo() { return 42; } }).foo"));
        // Basic setter updates `this`.
        assertEquals(5, eval("var o = { set bar(v) { this._x = v; } }; o.bar = 5; o._x"));
        // Paired getter + setter share state on same key.
        assertEquals(14, eval(
                "var o = { _n: 10, get n() { return this._n; }, set n(v) { this._n = v * 2; } };"
                        + " o.n = 7; o.n"));
        // Getter-only: assignment is silently ignored.
        assertEquals(1, eval("var o = { get x() { return 1; } }; o.x = 999; o.x"));
        // Setter-only: read is undefined.
        assertEquals("undefined", eval("var o = { set x(v) {} }; typeof o.x"));
        // `get` / `set` as regular property names still work.
        assertEquals(1, eval("({ get: 1 }).get"));
        assertEquals(2, eval("({ set: 2 }).set"));
        // Shorthand method named `get` or `set`.
        assertEquals(99, eval("({ get() { return 99; } }).get()"));
        // String-literal accessor key.
        assertEquals("bar", eval("({ get 'foo'() { return 'bar'; } }).foo"));
        // Computed accessor key.
        assertEquals("computed", eval("var k='dyn'; ({ get [k]() { return 'computed'; } }).dyn"));
    }

    @Test
    void testObjectComputedKeys() {
        // Simple computed key
        assertEquals(42, eval("var k = 'x'; ({[k]: 42})[k]"));
        // Computed key from an expression
        assertEquals(1, eval("({['a' + 'b']: 1}).ab"));
        // Number expression coerces to string key
        assertEquals("four", eval("({[2+2]: 'four'})[4]"));
        // Mixed with regular and shorthand keys
        assertEquals(true, eval(
                "var k = 'x'; var o = {a: 1, [k]: 2, [k + '2']: 3};"
                        + " o.a === 1 && o.x === 2 && o.x2 === 3"));
        // Computed key with shorthand method
        assertEquals(99, eval("var k = 'f'; ({[k]() { return 99; }}).f()"));
    }

    @Test
    void testObjectShorthandMethods() {
        // Zero-arg shorthand method
        assertEquals(1, eval("({ foo() { return 1; } }).foo()"));
        // Multi-arg shorthand method
        assertEquals(7, eval("({ add(a, b) { return a + b; } }).add(3, 4)"));
        // `this` inside shorthand method refers to the object
        assertEquals(42, eval("({ x: 42, get() { return this.x; } }).get()"));
        // typeof reports "function"
        assertEquals("function", eval("typeof ({ foo() {} }).foo"));
        // Mixed with regular properties and arrow/function values
        assertEquals(true, eval(
                "var o = { a: 1, f() { return 2; }, g: function() { return 3; } };"
                        + " o.a === 1 && o.f() === 2 && o.g() === 3"));
    }

    @Test
    void testDestructuringAssignmentExpression() {
        // Array destructuring assignment as expression
        eval("var a, b; [a, b] = [1, 2]");
        assertEquals(1, get("a"));
        assertEquals(2, get("b"));
        // Object destructuring assignment
        eval("var x, y; ({x, y} = {x: 10, y: 20})");
        assertEquals(10, get("x"));
        assertEquals(20, get("y"));
        // Nested patterns recurse
        eval("var p, q, r; [p, [q, r]] = [1, [2, 3]]");
        assertEquals(1, get("p"));
        assertEquals(2, get("q"));
        assertEquals(3, get("r"));
        // Default values fire only when source is undefined
        assertEquals("5:7", eval("var a, b; [a = 5, b = 6] = [undefined, 7]; a+':'+b"));
        assertEquals(10, eval("var a; ({a = 5} = {a: 10}); a"));
        assertEquals(5, eval("var a; ({a = 5} = {}); a"));
        // Rename + default in object pattern
        assertEquals(10, eval("var p; ({a: p = 5} = {a: 10}); p"));
        assertEquals(5, eval("var p; ({a: p = 5} = {}); p"));
        // Nested object destructuring
        assertEquals(99, eval("var y; ({x: {y}} = {x: {y: 99}}); y"));
        // Property reference as destructuring target
        assertEquals(99, eval("var o = {x: 0}; [o.x] = [99]; o.x"));
        // Rest: array
        assertEquals("1:2:3", eval("var a, r; [a, ...r] = [1, 2, 3]; a + ':' + r[0] + ':' + r[1]"));
        // Rest: object
        assertEquals(true, eval("var a, r; ({a, ...r} = {a: 1, b: 2, c: 3}); a === 1 && r.b === 2 && r.c === 3"));
        // Return value is the RHS
        assertEquals(true, eval("var a, b; var r = ([a, b] = [1, 2]); r[0] === 1 && r[1] === 2"));
        // Chained destructuring assignment
        assertEquals(true, eval("var a, b, c; a = [b, c] = [10, 20]; b === 10 && c === 20 && a[0] === 10 && a[1] === 20"));
    }

    @Test
    void testImmediatelyInvokedFunctionExpression() {
        // Named function expression immediately invoked
        assertEquals(1, eval("var x = function f1(){ return 1; }(); x"));
        // Anonymous function expression immediately invoked
        assertEquals(2, eval("var y = function (){ return 2; }(); y"));
        // Function expression wrapped in parens, then invoked
        assertEquals(3, eval("var z = (function(){ return 3; })(); z"));
        // Invoke-from-inside-parens form: (function(){ ... }())
        assertEquals(4, eval("var w = (function(){ return 4; }()); w"));
        // Arrow IIFE still works (was already supported via PAREN_EXPR)
        assertEquals(10, eval("(x => x * 2)(5)"));
        assertEquals(5, eval("((a, b) => a + b)(2, 3)"));
        // IIFE with arguments
        assertEquals(6, eval("(function(a, b){ return a + b; })(2, 4)"));
    }

    @Test
    void testTaggedTemplate() {
        // Basic no-substitution: strings[0] === 'hello', strings.raw[0] === 'hello'
        assertEquals(true, eval("var s; function t(x){ s = x; return x[0] + '|' + x.raw[0]; }; t`hello` === 'hello|hello'"));
        // N substitutions yield N+1 string slots
        assertEquals("a|1|b|2|c", eval("function t(s, a, b){ return s[0]+'|'+a+'|'+s[1]+'|'+b+'|'+s[2]; }; t`a${1}b${2}c`"));
        // Empty leading / trailing slot
        assertEquals("||42||", eval("function t(s, x){ return s[0]+'|'+s[0]+'|'+x+'|'+s[1]+'|'+s[1]; }; t`${42}`"));
        // Raw vs cooked: escape `\n` cooked as newline, raw as literal backslash-n
        assertEquals(true, eval("function t(s){ return s[0].charCodeAt(0) === 10 && s.raw[0] === '\\\\n'; }; t`\\n`"));
        // Tag is a method reference: `obj.tag\`x\`` receives obj as 'this'
        assertEquals(99, eval("var o = {k: 99, t: function(s){ return this.k; }}; o.t`anything`"));
        // `new tag\`x\`` — per spec, the tagged template evaluates first and new applies to the result
        assertEquals(7, eval("function Ctor(){ this.v = 7; }; function tag(){ return Ctor; }; (new tag`first`).v"));
    }

    @Test
    void testSymbolWellKnownKeys() {
        // Well-known symbols are exposed as string-keyed stand-ins on the Symbol global.
        // No primitive symbol type — these are the literal "@@<name>" strings the engine
        // uses internally as property keys.
        assertEquals("@@iterator", eval("Symbol.iterator"));
        assertEquals("@@asyncIterator", eval("Symbol.asyncIterator"));
        assertEquals("@@toPrimitive", eval("Symbol.toPrimitive"));
        assertEquals("@@toStringTag", eval("Symbol.toStringTag"));
        assertEquals("@@hasInstance", eval("Symbol.hasInstance"));
        assertEquals("@@isConcatSpreadable", eval("Symbol.isConcatSpreadable"));
        assertEquals("@@species", eval("Symbol.species"));
        assertEquals("@@match", eval("Symbol.match"));
        assertEquals("@@matchAll", eval("Symbol.matchAll"));
        assertEquals("@@replace", eval("Symbol.replace"));
        assertEquals("@@search", eval("Symbol.search"));
        assertEquals("@@split", eval("Symbol.split"));
        assertEquals("@@unscopables", eval("Symbol.unscopables"));
    }

    @Test
    void testSymbolToPrimitiveDispatch() {
        // @@toPrimitive overrides valueOf/toString in ToPrimitive. Spec hints:
        //   binary +  → "default"   numeric ops → "number"   String() → "string"
        // Binary + with hint "default": numeric branch
        assertEquals(11, eval(
                "var o = { [Symbol.toPrimitive]: function(h){ return h === 'default' ? 10 : -1; } };"
                        + "o + 1"));
        // Binary + with hint "default": string branch (returning a string makes + concatenate)
        assertEquals("hi1", eval(
                "var o = { [Symbol.toPrimitive]: function(h){ return h === 'default' ? 'hi' : 'x'; } };"
                        + "o + 1"));
        // @@toPrimitive takes precedence over valueOf/toString
        assertEquals(99, eval(
                "var o = { valueOf: function(){ return 1; }, toString: function(){ return '2'; },"
                        + "  [Symbol.toPrimitive]: function(){ return 99; } };"
                        + "o + 0"));
        // Returning a non-primitive must throw TypeError per spec
        assertThrows(Exception.class, () -> eval(
                "var o = { [Symbol.toPrimitive]: function(){ return {}; } }; o + 0"));
        // Set-but-not-callable @@toPrimitive must TypeError (spec GetMethod)
        assertThrows(Exception.class, () -> eval(
                "var o = { [Symbol.toPrimitive]: 1 }; o + 0"));
    }

    @Test
    void testDateToPrimitive() {
        // Date.prototype[@@toPrimitive] treats hint "default" as "string", so binary +
        // string-concatenates Dates instead of adding their timestamps. Spec §21.4.4.45.
        assertEquals(true, eval("var d = new Date(0); (d + d) === (d.toString() + d.toString())"));
        // Hint "number" still routes to valueOf (the timestamp)
        assertEquals(true, eval("var d = new Date(0); +d === d.getTime()"));
        // Invalid hint TypeErrors
        assertThrows(Exception.class, () -> eval(
                "new Date()[Symbol.toPrimitive]('bogus')"));
    }

    @Test
    void testSymbolToStringTag() {
        // @@toStringTag (string) overrides the host-class-derived tag
        assertEquals("[object Foo]", eval(
                "var o = { [Symbol.toStringTag]: 'Foo' };"
                        + "Object.prototype.toString.call(o)"));
        // Non-string @@toStringTag is ignored — falls through to builtinTag
        assertEquals("[object Object]", eval(
                "var o = { [Symbol.toStringTag]: 42 };"
                        + "Object.prototype.toString.call(o)"));
        // Built-in tags still work when @@toStringTag is absent
        assertEquals("[object Array]", eval("Object.prototype.toString.call([])"));
        assertEquals("[object Object]", eval("Object.prototype.toString.call({})"));
        // @@toStringTag inherited via prototype chain — set on parent, read on child
        assertEquals("[object Bar]", eval(
                "var parent = { [Symbol.toStringTag]: 'Bar' };"
                        + "var child = Object.create(parent);"
                        + "Object.prototype.toString.call(child)"));
    }

    @Test
    void testDestructuringDefaultUndefinedVsNull() {
        // Present-with-undefined: default fires
        assertEquals(1, eval("var x; ({ x = 1 } = { x: undefined }); x"));
        // Present-with-null: default does NOT fire (null is a valid value)
        assertNull(eval("var x; ({ x = 1 } = { x: null }); x"));
        // Absent: default fires
        assertEquals(5, eval("var x; ({ x = 5 } = {}); x"));
        // Present-with-value: value wins
        assertEquals(42, eval("var x; ({ x = 1 } = { x: 42 }); x"));
        // Array destructuring on non-iterable throws TypeError (spec 13.3.3.5)
        assertThrows(Exception.class, () -> eval("var a; [a] = true"));
        assertThrows(Exception.class, () -> eval("[,] = 1"));
        // String is iterable (destructures by code unit)
        assertEquals("h", eval("var a; [a] = 'hello'; a"));
    }

}

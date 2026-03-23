package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsFunctionTest extends EvalBase {

    @Test
    void testDev() {

    }

    @Test
    void testFunction() {
        assertEquals(true, eval("var a = function(){ return true }; a()"));
        assertEquals(2, eval("var a = 2; var b = function(){ return a }; b()"));
        assertEquals(5, eval("var fn = function(x, y){ return x + y }; fn(2, 3)"));
        assertEquals(5, eval("function add(x, y){ return x + y }; add(2, 3)"));
    }

    @Test
    void testFunctionNested() {
        assertEquals(true, eval("var a = {}; a.b = function(){ return true }; a.b()"));
        assertEquals(true, eval("var a = {}; a.b = function(){ return true }; a['b']()"));
        assertEquals(2, eval("var a = function(){}; a.b = [1, 2, 3]; a['b'][1]"));
    }

    @Test
    void testArrowFunction() {
        assertEquals(true, eval("var a = () => true; a()"));
        assertEquals(true, eval("var a = x => true; a()"));
        assertEquals(2, eval("var a = x => x; a(2)"));
        assertEquals(2, eval("var a = (x) => x; a(2)"));
        assertEquals(5, eval("var fn = (x, y) => x + y; fn(2, 3)"));
    }

    @Test
    void testArrowFunctionUndefinedArg() {
        // Single-param arrow function should preserve undefined
        assertEquals(true, eval("var a = x => x === undefined; a(undefined)"));
        assertEquals(true, eval("var a = (x) => x === undefined; a(undefined)"));
        // Multi-param arrow function
        assertEquals(true, eval("var a = (x, y) => x === undefined; a(undefined, 1)"));
        // Regular function for comparison
        assertEquals(true, eval("var a = function(x) { return x === undefined }; a(undefined)"));
        // Arrow with implicit undefined (missing arg)
        assertEquals(true, eval("var a = x => x === undefined; a()"));
        assertEquals(true, eval("var a = (x) => x === undefined; a()"));
    }

    @Test
    void testArrowFunctionPassThroughUndefined() {
        // Arrow function identity should preserve undefined
        assertEquals(true, eval("var identity = x => x; identity(undefined) === undefined"));
        assertEquals(true, eval("var identity = (x) => x; identity(undefined) === undefined"));
        // Map with arrow function preserving undefined
        assertEquals(true, eval("[undefined].map(x => x)[0] === undefined"));
        assertEquals(true, eval("[undefined].map(function(x) { return x })[0] === undefined"));
    }

    @Test
    void testFunctionBlocksAndReturn() {
        assertNull(eval("var a = function(){ }; a()"));
        assertEquals(true, eval("var a = function(){ return true; 'foo' }; a()"));
        assertEquals("foo", eval("var a = function(){ if (true) return 'foo'; return 'bar' }; a()"));
        assertEquals("foo", eval("var a = function(){ for (var i = 0; i < 2; i++) { return 'foo' }; return 'bar' }; a()"));
        assertNull(eval("var a = () => {}; a()"));
        assertNull(eval("var a = () => { true }; a()"));
        assertEquals(true, eval("var a = () => { return true }; a()"));
        assertEquals(true, eval("var a = () => { return true; 'foo' }; a()"));
    }

    @Test
    void testFunctionArgsMissing() {
        assertEquals(true, eval("var a = function(b){ return b }; a() === undefined"));
    }

    @Test
    void testFunctionNew() {
        assertEquals("foo", eval("var a = function(x){ this.b = x }; c = new a('foo'); c.b"));
    }

    @Test
    void testFunctionNewNoBrackets() {
        assertEquals("foo", eval("var a = function(){ this.b = 'foo' }; c = new a; c.b"));
    }

    @Test
    void testFunctionArguments() {
        assertEquals(List.of(1, 2), eval("var a = function(){ return arguments }; a(1, 2)"));
    }

    @Test
    void testFunctionCallSpread() {
        assertEquals(List.of(1, 2), eval("var a = function(){ return arguments }; var b = [1, 2]; a(...b)"));
    }

    @Test
    void testFunctionPrototypeToString() {
        // ES6: Function.prototype.toString returns actual source for user-defined functions
        assertEquals("function(){ }", eval("var a = function(){ }; a.toString()"));
        assertEquals("function a(){ }", eval("function a(){ }; a.toString()"));
        assertEquals("a", eval("var a = function(){ }; a.constructor.name"));
        assertEquals("a", eval("function a(){ }; a.constructor.name"));
        // ES6: a.prototype.toString does NOT affect a.toString() - prototype is only for instances
        assertEquals("function(){ }", eval("var a = function(){ }; a.prototype.toString = function(){ return 'foo' }; a.toString()"));
    }

    @Test
    void testFunctionToStringReflection() {
        // Function source should be available for reflection
        // Regular function expression
        assertEquals("function(a, b){ return a + b }", eval("var add = function(a, b){ return a + b }; add.toString()"));
        // Named function declaration
        assertEquals("function multiply(x, y){ return x * y }", eval("function multiply(x, y){ return x * y }; multiply.toString()"));
        // Arrow function
        assertEquals("x => x * 2", eval("var double = x => x * 2; double.toString()"));
        // Arrow function with block body
        assertEquals("(a, b) => { return a + b }", eval("var add = (a, b) => { return a + b }; add.toString()"));
    }

    @Test
    void testFunctionSerializationForDriverExpressions() {
        // Arrow functions with underscore placeholder (for driver.script(locator, fn))
        assertEquals("_ => _.value", eval("var fn = _ => _.value; fn.toString()"));
        assertEquals("_ => _.textContent", eval("var fn = _ => _.textContent; fn.toString()"));
        assertEquals("_ => !_.disabled", eval("var fn = _ => !_.disabled; fn.toString()"));

        // Property access with method calls
        assertEquals("_ => _.getAttribute('data-id')", eval("var fn = _ => _.getAttribute('data-id'); fn.toString()"));
        assertEquals("_ => _.querySelector('.child')", eval("var fn = _ => _.querySelector('.child'); fn.toString()"));

        // Nested arrow functions (for scriptAll/findAll)
        assertEquals("_ => _.map(e => e.textContent)", eval("var fn = _ => _.map(e => e.textContent); fn.toString()"));
        assertEquals("els => els.filter(e => e.disabled)", eval("var fn = els => els.filter(e => e.disabled); fn.toString()"));

        // Complex expressions with template literals would need escaping in string form
        assertEquals("_ => _.style.display === 'none'", eval("var fn = _ => _.style.display === 'none'; fn.toString()"));

        // Block body with multiple statements
        assertEquals("_ => { var v = _.value; return v.trim() }",
                eval("var fn = _ => { var v = _.value; return v.trim() }; fn.toString()"));
    }

    @Test
    void testFunctionCanBePassedAndSerialized() {
        // Simulate what happens when a function is passed to a Java method
        // The Java code receives JsFunction and can call getSource()

        // First verify we can get a reference to the function
        eval("var fn = _ => _.value");
        Object fn = get("fn");
        assertNotNull(fn);

        // The function should be a JsFunction
        // When passed to Java, we can serialize it back to source via getSource()
        assertTrue(fn instanceof JsFunction);

        // Use the public getSource() method
        JsFunction jsFn = (JsFunction) fn;
        String source = jsFn.getSource();
        assertEquals("_ => _.value", source);
    }

    @Test
    void testFunctionDeclarationRest() {
        assertEquals(List.of(1, 2, 3), eval("function sum(...args) { return args }; sum(1, 2, 3)"));
        assertEquals(6, eval("function sum(...numbers) { return numbers.reduce((a, b) => a + b, 0) }; sum(1,2,3)"));
        assertEquals("hello world", eval("function concat(first, ...rest) { return first + ' ' + rest.join(' ') }; concat('hello', 'world')"));
        assertEquals("hello world and more", eval("function concat(first, ...rest) { return first + ' ' + rest.join(' ') }; concat('hello', 'world', 'and', 'more')"));
    }

    @Test
    void testArrowFunctionRest() {
        assertEquals("[1,2,3]", eval("var sum = (...args) => args; JSON.stringify(sum(1,2,3))"));
        assertEquals(6, eval("var sum = (...numbers) => numbers.reduce((a, b) => a + b, 0); sum(1,2,3)"));
        assertEquals("hello world", eval("var concat = (first, ...rest) => first + ' ' + rest.join(' '); concat('hello', 'world')"));
    }

    @Test
    void testFunctionDeclarationDefault() {
        assertEquals(3, eval("function foo(a, b = 2) { return a + b }; foo(1)"));
        assertEquals(2, eval("function foo(a, b = 2) { return a + b }; foo(1, 1)"));
        assertEquals(1, eval("function foo(a, b = 2) { return a + b }; foo(1, null)"));
        assertEquals(3, eval("function foo(a, b = 2) { return a + b }; foo(1, undefined)"));
        assertEquals(2, eval("function foo(a, b = a + 1) { return b }; foo(1)"));
    }

    @Test
    void testFunctionDeclarationDestructuring() {
        assertEquals(5, eval("function foo([a, b]) { return a + b }; foo([2, 3])"));
        assertEquals(5, eval("function foo({ a, b }) { return a + b }; foo({ a: 2, b: 3 })"));
    }

    @Test
    void testCurrying() {
        matchEval("function multiply(a) { return function(b) { return a * b } }; multiply(4)(7)", "28");
    }

    @Test
    void testNestedFunctionReturnValue() {
        // Regression test: nested function return should not clobber outer function's return
        assertEquals("outer", eval("""
            function outer() {
                function inner() {
                    return 'inner';
                }
                var x = inner();
                return 'outer';
            }
            outer();
        """));
        // Multiple nested calls
        assertEquals(12, eval("""
            function outer(n) {
                function double(x) { return x * 2; }
                function half(x) { return x / 2; }
                var a = double(n);
                var b = half(a);
                return a + b;
            }
            outer(4);
        """));
        // Nested with different return types
        assertEquals("result: 42", eval("""
            function outer() {
                function getNumber() { return 42; }
                var num = getNumber();
                return 'result: ' + num.toString();
            }
            outer();
        """));
    }

    @Test
    void testIife() {
        matchEval("(function(){ return 'hello' })()", "'hello'");
    }

    @Test
    void testIifeConstShadowing() {
        // Verify that const declarations inside IIFE properly shadow outer const
        Engine engine = new Engine();
        engine.eval("const testVar = 1;");
        assertEquals(1, engine.eval("testVar"));
        // IIFE should create a new scope where const can be redeclared
        Object result = engine.eval("(function(){ const testVar = 3; return testVar; })()");
        assertEquals(3, result);
        // Outer const should still be 1
        assertEquals(1, engine.eval("testVar"));
    }

    @Test
    void testCallAndApply() {
        matchEval("function sum(a, b, c){ return a + b + c }; sum(1, 2, 3)", "6");
        matchEval("function sum(a, b, c){ return a + b + c }; sum.call(null, 1, 2, 3)", "6");
        matchEval("function sum(a, b, c){ return a + b + c }; sum.apply(null, [1, 2, 3])", "6");
        matchEval("function greet(pre){ return pre + this.name }; var p = { name: 'john' }; greet.call(p, 'hi ')", "hi john");
    }

    @Test
    void testGetReferenceAndInvoke() {
        eval("var a = function(name){ return 'hello ' + name }");
        JsCallable fn = (JsCallable) get("a");
        Object result = fn.call(null, new Object[]{"world"});
        assertEquals("hello world", result);
    }

    @Test
    void testEscapedQuotesInStringThenEval() {
        // This test replicates the agent.eval() escaping issue:
        // When JS code containing escaped double quotes is passed as a string,
        // then that string is extracted and re-evaluated, the escaping can fail.

        // Simulates: curl -d 'agent.eval("arr.filter(function(x) { return x.includes(\"foo\"); })")'
        // The outer eval parses the string literal with \" escape sequences
        // The resulting string value should be valid JS that can be re-evaluated

        // First, parse a string containing escaped quotes
        eval("var code = \"arr.filter(function(x) { return x.includes(\\\"foo\\\"); })\"");
        String code = (String) get("code");

        // The string value should have the escapes resolved
        assertEquals("arr.filter(function(x) { return x.includes(\"foo\"); })", code);

        // Now re-evaluate that string as JS code (simulating driver.script())
        eval("var arr = ['foo', 'bar', 'foobar']");
        Object result = eval("var arr = ['foo', 'bar', 'foobar']; " + code);

        // Should return filtered array containing "foo"
        assertEquals(List.of("foo", "foobar"), result);
    }

    @Test
    void testEscapedQuotesVariousPatterns() {
        // Test the specific pattern that reportedly fails: escaped quotes followed by })

        // Pattern 1: escaped quote at end before closing braces
        eval("var s1 = \"x.includes(\\\"foo\\\")\"");
        assertEquals("x.includes(\"foo\")", get("s1"));

        // Pattern 2: escaped quotes with }); ending (the reported problematic pattern)
        eval("var s2 = \"return x.includes(\\\"foo\\\"); })\"");
        assertEquals("return x.includes(\"foo\"); })", get("s2"));

        // Pattern 3: full function with escaped quotes
        eval("var s3 = \"function(x) { return x.includes(\\\"foo\\\"); }\"");
        assertEquals("function(x) { return x.includes(\"foo\"); }", get("s3"));

        // Pattern 4: nested escaping (what happens with JSON + JS layers)
        // In JSON: \\\" becomes \"
        // In JS: \" becomes "
        eval("var s4 = \"arr.filter(function(x) { return x.includes(\\\"foo\\\"); })\"");
        assertEquals("arr.filter(function(x) { return x.includes(\"foo\"); })", get("s4"));
    }

    @Test
    void testDoubleEscapingLayerSimulation() {
        // Simulates bash → curl → HTTP → JSON → JS escaping chain
        // When sending via JSON POST, the escaping is: \\\" in JSON → \" after JSON parse → " in JS string

        // This is what the AgentServer receives after JSON parsing:
        // {"command":"eval","payload":"agent.eval(\"arr.filter(function(x) { return x.includes(\\\"foo\\\"); })\")"}

        // After JSON.parse, payload becomes:
        // agent.eval("arr.filter(function(x) { return x.includes(\"foo\"); })")

        // Then the Karate engine parses this, extracting the string argument:
        // arr.filter(function(x) { return x.includes("foo"); })

        // Test that chain works correctly
        String jsonPayload = "agent.eval(\"arr.filter(function(x) { return x.includes(\\\"foo\\\"); })\")";

        // Parse the JS - this should extract the inner string
        eval("var payload = \"" + jsonPayload.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        String extractedPayload = (String) get("payload");
        assertEquals(jsonPayload, extractedPayload);
    }

}

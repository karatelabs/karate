package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void testFunctionJavaToStringReturnsSource() {
        // Regression: Java-side toString() on a JS function must return parseable
        // source, never the default Object.toString() (e.g. "JsFunctionWrapper@...")
        // nor a node-debug form (e.g. "[FN_ARROW_EXPR] () => ..."). karate-core's
        // driver layer stringifies callables when dispatching, and non-JS output
        // then fails in V8 with "Malformed arrow function parameter list" or
        // "Invalid or unexpected token".

        // Arrow function, retrieved from Java (goes through Engine.toJava → JsFunctionWrapper)
        eval("var fn = _ => _.value");
        Object fn = get("fn");
        assertTrue(fn instanceof JsFunction);
        String s = fn.toString();
        assertFalse(s.startsWith("io.karatelabs.js."),
                "toString() leaked Object default: " + s);
        assertFalse(s.startsWith("["),
                "toString() leaked node-debug form: " + s);
        assertEquals("_ => _.value", s);

        // Arrow with no params (the exact shape from the regression report)
        eval("var fn = () => locateAll('.border-bottom').length == 0");
        assertEquals("() => locateAll('.border-bottom').length == 0", get("fn").toString());

        // Arrow with single paren-less param
        eval("var fn = x => x + 1");
        assertEquals("x => x + 1", get("fn").toString());

        // Classic function expression
        eval("var fn = function(a, b){ return a + b }");
        assertEquals("function(a, b){ return a + b }", get("fn").toString());

        // Named function declaration
        eval("function add(a, b){ return a + b }");
        assertEquals("function add(a, b){ return a + b }", get("add").toString());
    }

    @Test
    void testFunctionWrapperToStringNotObjectDefault() {
        // Flavour B from the regression: a JsFunctionWrapper's toString() used to
        // return "io.karatelabs.js.JsFunctionWrapper@<hash>" — the @ then breaks V8.
        eval("function find(title){ return title }");
        Object fn = get("find");
        assertTrue(fn instanceof JsFunctionWrapper);
        String s = fn.toString();
        assertFalse(s.contains("@"), "@ in toString would break V8 parsing: " + s);
        assertEquals("function find(title){ return title }", s);
    }

    @Test
    void testStringConcatWithFunctionIsSource() {
        // Java string concatenation invokes toString() implicitly.
        // Must produce parseable JS source, not an internal debug form.
        eval("var fn = x => x * 2");
        String concatenated = "result: " + get("fn");
        assertEquals("result: x => x * 2", concatenated);
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

    // ====================================================================================
    // Lexical scoping regression suite — issue #2802
    //
    // Bug shape: a callee's closure-captured outer var was being shadowed by
    // a same-named variable in the caller's scope, because the function context's
    // `parent` (dynamic call chain) was searched before its lexical
    // closureContext. Fix: function contexts use closureContext only.
    // ====================================================================================

    @Test
    void testCalleeClosureNotShadowedByCallerParam() {
        // Issue #2802: factory captures `config` by closure; caller's parameter
        // is also named `config`. Lexical scoping must win — closure must see
        // the factory's captured config.
        assertEquals("jdbc:fake", eval(
                "var dbConfig = { url: 'jdbc:fake' };\n" +
                        "var factory = function(config) {\n" +
                        "  return { read: function() { return config.url; } };\n" +
                        "};\n" +
                        "var db = factory(dbConfig);\n" +
                        "var caller = function(config) { return config.db.read(); };\n" +
                        "caller({ db: db });"));
    }

    @Test
    void testCalleeClosureNotShadowedByCallerVar() {
        // var-declared shadow in caller, not just parameter
        assertEquals(1, eval(
                "var x = 1;\n" +
                        "var inner = function() { return x; };\n" +
                        "var outer = function() { var x = 99; return inner(); };\n" +
                        "outer();"));
    }

    @Test
    void testCalleeClosureNotShadowedByCallerLet() {
        assertEquals(1, eval(
                "var x = 1;\n" +
                        "var inner = function() { return x; };\n" +
                        "var outer = function() { let x = 99; return inner(); };\n" +
                        "outer();"));
    }

    @Test
    void testNestedFunctionUsesLexicalNotDynamicScope() {
        // inner is defined inside outer; called from a third function.
        // The intermediate function declares `secret`, which must not leak in.
        assertEquals("from-outer", eval(
                "var inner;\n" +
                        "function outer() {\n" +
                        "  var secret = 'from-outer';\n" +
                        "  inner = function() { return secret; };\n" +
                        "}\n" +
                        "outer();\n" +
                        "function middleman() { var secret = 'from-middleman'; return inner(); }\n" +
                        "middleman();"));
    }

    @Test
    void testArrowFunctionClosureLexical() {
        assertEquals("captured", eval(
                "var captured = 'captured';\n" +
                        "var arrow = () => captured;\n" +
                        "var caller = function(captured) { return arrow(); };\n" +
                        "caller('caller-local');"));
    }

    @Test
    void testCallbackPassedToHostShapedHelper() {
        // [].map(fn) - the callback's closure must resolve `multiplier` lexically
        // even though it's invoked from inside Array.prototype.map.
        assertEquals(List.of(2, 4, 6), eval(
                "var multiplier = 2;\n" +
                        "function run() { return [1, 2, 3].map(function(n){ return n * multiplier; }); }\n" +
                        "run();"));
    }

    @Test
    void testCallerCannotReadCalleesLocals() {
        // Reverse direction - caller must NOT see callee's locals after call returns,
        // and must not see them during the call either.
        assertEquals("undefined", eval(
                "function inner() { var hidden = 42; }\n" +
                        "function outer() { inner(); return typeof hidden; }\n" +
                        "outer();"));
    }

    @Test
    void testAssignmentToOuterVarFollowsLexicalScope() {
        // Assignment to a free variable should hit the LEXICAL outer scope,
        // not the caller's same-named var.
        assertEquals("lexical-was-mutated", eval(
                "var x = 'lexical-original';\n" +
                        "function mutator() { x = 'lexical-was-mutated'; return null; }\n" +
                        "function caller() { var x = 'caller-local'; mutator(); return x; }\n" +
                        "caller();" +
                        "x;"));
    }

    @Test
    void testAssignmentToOuterVarLeavesCallerLocalAlone() {
        // Companion to the above: caller's local x must remain its own value
        // because mutator's assignment goes through the lexical chain.
        assertEquals("caller-local", eval(
                "var x = 'lexical-original';\n" +
                        "function mutator() { x = 'lexical-was-mutated'; return null; }\n" +
                        "function caller() { var x = 'caller-local'; mutator(); return x; }\n" +
                        "caller();"));
    }

    @Test
    void testFunctionStoredInObjectInvokedFromAnotherFunction() {
        // The exact issue 2802 shape: factory returns {method}, that method is
        // invoked via `obj.method()` from inside a different function whose
        // parameter shadows the closure var.
        assertEquals(7, eval(
                "function makeAdder(x) { return { add: function(y) { return x + y; } }; }\n" +
                        "var a = makeAdder(3);\n" +
                        "function callIt(x) { return x.adder.add(4); }\n" +
                        "callIt({ adder: a });"));
    }

    @Test
    void testTwoDeepClosuresUseTheirOwnLexicalScopes() {
        // Two factories with overlapping var names; calling the second must
        // not perturb the first's closure.
        assertEquals(List.of("a-val", "b-val"), eval(
                "function makeA() { var v = 'a-val'; return function(){ return v; }; }\n" +
                        "function makeB() { var v = 'b-val'; return function(){ return v; }; }\n" +
                        "var fa = makeA();\n" +
                        "var fb = makeB();\n" +
                        "[fa(), fb()];"));
    }

    @Test
    void testBindThisAndArgs() {
        // bind binds `this` and prepends args; subsequent invocations append actuals.
        matchEval("function f(x, y) { return this.n + x + y; }\n"
                + "var bound = f.bind({ n: 10 }, 1);\n"
                + "bound(2)", "13");
        // bind can be used to give built-ins a fixed receiver — the test262
        // pattern `[].flat.bind(a)()` for unmounted method invocation.
        matchEval("var a = [[1, 2], [3, 4]];\n"
                + "var fn = [].flat.bind(a);\n"
                + "fn()", "[1, 2, 3, 4]");
        // No prebound args, undefined this — passes all actuals through.
        matchEval("function add(x, y) { return x + y; }\n"
                + "add.bind()(3, 4)", "7");
    }

    @Test
    void testFunctionDeclarationHoisting() {
        // function declarations hoist: code earlier in the script can still
        // reference them and assign properties (e.g. .prototype) to them.
        matchEval("foo();\n"
                + "function foo() { return 42; }", "42");
        // assignment to hoisted function before its lexical position survives
        // — re-evaluating the FN_EXPR in normal flow must not clobber it.
        matchEval("foo.tag = 'tagged';\n"
                + "function foo() {}\n"
                + "foo.tag", "'tagged'");
    }

    @Test
    void testJavaTypeReferenceSurvivesAcrossCallBoundary() {
        // 2.0.3 symptom from issue #2802: Java.type wrapper captured in a
        // closure must remain functional when the function is called via
        // an indirect path. We don't have Java.type in the pure-JS test
        // engine, but we can simulate the same shape with any host object
        // by passing it via `eval` vars.
        Map<String, Object> vars = Map.of("host", java.util.Collections.singletonMap("ping", "pong"));
        assertEquals("pong", new EvalBase() {{ engine = new Engine(); vars.forEach(engine::put); }}
                .engine.eval(
                        "var captured = host;\n" +
                                "var factory = function() { return function() { return captured.ping; }; };\n" +
                                "var f = factory();\n" +
                                "var caller = function(host) { return f(); };\n" +
                                "caller({ ping: 'wrong' });"));
    }

}

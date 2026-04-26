package io.karatelabs.js;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.JsParser;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.Token;
import io.karatelabs.parser.TokenType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    static final Logger logger = LoggerFactory.getLogger(EngineTest.class);

    @Test
    void testLazyContextVariables() {
        Engine engine = new Engine();
        engine.put("x", (Supplier<String>) () -> "foo");
        assertEquals("foo", engine.eval("x"));
    }

    @Test
    void testEvalWith() {
        Engine engine = new Engine();
        engine.put("x", 10);
        Map<String, Object> vars = new HashMap<>();
        vars.put("y", 20);
        // evalWith returns result, vars map provides initial values only
        Object result = engine.evalWith("x + y", vars);
        assertEquals(30, result);
        // vars from evalWith don't leak to engine bindings
        assertFalse(engine.getBindings().containsKey("y"));
    }

    @Test
    void testEvalWithConstLetIsolation() {
        Engine engine = new Engine();
        // evalWith scopes are isolated - same names don't conflict
        Map<String, Object> vars1 = new HashMap<>();
        vars1.put("x", 1);
        Object result1 = engine.evalWith("const a = x; a", vars1);
        assertEquals(1, result1);

        Map<String, Object> vars2 = new HashMap<>();
        vars2.put("x", 10);
        Object result2 = engine.evalWith("const a = x; a", vars2);
        assertEquals(10, result2);

        // Engine bindings should not have these
        assertFalse(engine.getBindings().containsKey("a"));
        assertFalse(engine.getBindings().containsKey("x"));
    }

    @Test
    void testEvalWithImplicitGlobalStaysLocal() {
        Engine engine = new Engine();
        // Setup shared object in engine
        engine.put("shared", new HashMap<String, Object>());
        // evalWith with implicit global - should stay local, not leak to engine
        Map<String, Object> vars = new HashMap<>();
        vars.put("value", 99);
        engine.evalWith("shared.value = value;", vars);
        // shared object mutation should work
        assertEquals(99, ((Map<?, ?>) engine.get("shared")).get("value"));
        // vars don't leak to engine bindings
        assertFalse(engine.getBindings().containsKey("value"));
    }

    @Test
    void testIifeConstLetIsolation() {
        Engine engine = new Engine();
        // Setup a shared object like Postman's pm
        engine.put("shared", new HashMap<String, Object>());
        // First IIFE with const/let - should not conflict with subsequent IIFEs
        engine.eval("(function(){ const json = {a: 1}; shared.first = json.a; })()");
        assertEquals(1, ((Map<?, ?>) engine.get("shared")).get("first"));
        // Second IIFE with same const name - should work without redeclaration error
        engine.eval("(function(){ const json = {b: 2}; shared.second = json.b; })()");
        assertEquals(2, ((Map<?, ?>) engine.get("shared")).get("second"));
        // Third IIFE with let - also no conflict
        engine.eval("(function(){ let json = {c: 3}; shared.third = json.c; })()");
        assertEquals(3, ((Map<?, ?>) engine.get("shared")).get("third"));
        // var declared inside IIFE stays inside (function-scoped)
        engine.eval("(function(){ var local = 'inside'; shared.local = local; })()");
        assertEquals("inside", ((Map<?, ?>) engine.get("shared")).get("local"));
        assertNull(engine.get("local")); // var inside function doesn't leak
        // implicit global assignment (no var/let/const) persists to global scope
        engine.eval("(function(){ implicitGlobal = 42; })()");
        assertEquals(42, engine.get("implicitGlobal"));
        // subsequent IIFE can access it
        engine.eval("(function(){ shared.fromImplicit = implicitGlobal; })()");
        assertEquals(42, ((Map<?, ?>) engine.get("shared")).get("fromImplicit"));
    }

    @Test
    void testEvalResult() {
        Engine engine = new Engine();
        Object result = engine.eval("""
                var foo = 'foo';
                var bar = foo + 'bar';
                """);
        assertEquals("foobar", result);
        assertEquals("foo", engine.get("foo"));
    }

    @Test
    void testStringEscapeSequences() {
        Engine engine = new Engine();
        // Standard JS: \n in a string becomes an actual newline
        assertEquals("hello\nworld", engine.eval("'hello\\nworld'"));
        assertEquals("hello\nworld", engine.eval("\"hello\\nworld\""));
        // Standard JS: \t becomes a tab
        assertEquals("hello\tworld", engine.eval("'hello\\tworld'"));
        // Standard JS: \r becomes carriage return
        assertEquals("hello\rworld", engine.eval("'hello\\rworld'"));
        // Standard JS: \\ becomes a single backslash
        assertEquals("hello\\world", engine.eval("'hello\\\\world'"));
        // Standard JS: \" in double-quoted string becomes a quote
        assertEquals("say \"hello\"", engine.eval("\"say \\\"hello\\\"\""));
        // Standard JS: \' in single-quoted string becomes a quote
        assertEquals("it's", engine.eval("'it\\'s'"));
    }

    @Test
    void testQuotesWithinStringLiterals() {
        Engine engine = new Engine();
        // This tests JSON embedded in a JS string
        // JS source: {"data": "{\"myKey\":\"myValue\"}"}
        // The inner string has escaped quotes which become literal quotes
        Object result = engine.eval("""
                var data = {"data": "{\\"myKey\\":\\"myValue\\"}"}
                """);
        // Expected: the inner string value is {"myKey":"myValue"} (quotes, no backslashes)
        assertEquals(Map.of("data", "{\"myKey\":\"myValue\"}"), result);
    }

    @Test
    void testWhiteSpaceTrackingWithinTemplates() {
        String js = """
                var request = {
                    body: `{
                                "RequesterDetails": {
                                    "InstructingTreasuryId": "000689",
                                    "ApiRequestReference": "${idempotencyKey}",
                                    "entity": "000689"
                                 }
                           }`
                };
                const foo = 'bar';
                """;
        JsParser parser = new JsParser(Resource.text(js));
        Node node = parser.parse();
        Node lastLine = node.findFirstChild(TokenType.CONST);
        assertEquals(9, lastLine.token.line);
    }

    @Test
    void testSwitchCaseDefaultSyntax() {
        String js = """
                a.b(() => {
                    function c() {
                        switch (d) {
                            case "X":
                                break;
                            default:
                                break;
                        }
                    }
                });
                var e = 1;
                """;
        JsParser parser = new JsParser(Resource.text(js));
        Node node = parser.parse();
        assertEquals(3, node.size());
        for (int i = 0, n = node.size(); i < n; i++) {
            Node child = node.get(i);
            if (!child.isEof()) {
                assertEquals(NodeType.STATEMENT, child.type);
            }
        }
    }

    @Test
    void testSwitchMultipleCaseWithReturn() {
        String js = """
                var fun = x => {
                    switch (x) {
                        case 'foo':
                        case 'fie':
                            return 'FOO';
                        default:
                            return 'BAR';
                    }
                };
                fun('foo');
                """;
        Engine engine = new Engine();
        String result = (String) engine.eval(js);
        assertEquals("FOO", result);
    }

    @Test
    void testFunctionWithinFunction() {
        String js = """
                function generateCardNumber(firstSix, length) {
                
                    function luhnCheck(input) {
                        const number = input.toString();
                        const digits = number.replace(/\\D/g, '').split('').map(Number);
                        let sum = 0;
                        let isSecond = false;
                        for (let i = digits.length - 1; i >= 0; i--) {
                            let digit = digits[i];
                            if (isSecond) {
                                digit *= 2;
                                if (digit > 9) {
                                    digit -= 9;
                                }
                            }
                            sum += digit;
                            isSecond = !isSecond;
                        }
                        return sum % 10;
                    }
                
                    function randomDigit() {
                        return Math.floor(Math.random() * 9);
                    }
                
                    let cardNumber = firstSix;
                    while (cardNumber.length < length - 1) {
                        cardNumber = cardNumber + randomDigit();
                    }
                    cardNumber = cardNumber + '9';
                    let luhnVal = luhnCheck(cardNumber);
                    cardNumber = cardNumber - luhnVal;
                    return cardNumber.toString();
                
                }
                
                generateCardNumber('411111',16);
                """;
        Engine engine = new Engine();
        String result = (String) engine.eval(js);
        assertTrue(result.startsWith("411111"));
        assertEquals(16, result.length());
    }

    @Test
    void testUndefined() {
        Engine engine = new Engine();
        Object result = engine.eval("1 * 'a'");
        assertEquals(Double.NaN, result);
    }

    @Test
    void testOnBind() {
        Engine engine = new Engine();
        Map<String, Object> map = new HashMap<>();
        ContextListener listener = new ContextListener() {
            @Override
            public void onBind(BindEvent event) {
                map.put("type", event.type);
                map.put("name", event.name);
                map.put("value", event.value);
                map.put("scope", event.scope);
            }
        };
        engine.setListener(listener);
        engine.eval("var a = 'a'");
        assertEquals(BindType.DECLARE, map.get("type"));
        assertEquals("a", map.get("name"));
        assertEquals("a", map.get("value"));
        assertEquals(BindScope.VAR, map.get("scope"));
        engine.eval("b = 'b'");
        assertEquals(BindType.DECLARE, map.get("type"));
        assertEquals("b", map.get("name"));
        assertEquals("b", map.get("value"));
    }

    @Test
    void testOnBindPropertySet() {
        Engine engine = new Engine();
        List<BindEvent> events = new ArrayList<>();
        ContextListener listener = new ContextListener() {
            @Override
            public void onBind(BindEvent event) {
                events.add(event);
            }
        };
        engine.setListener(listener);
        engine.eval("var obj = {}");
        events.clear();
        engine.eval("obj.foo = 'bar'");
        assertEquals(1, events.size());
        BindEvent event = events.get(0);
        assertEquals(BindType.PROPERTY_SET, event.type);
        assertEquals("foo", event.name);
        assertEquals("bar", event.value);
        assertNull(event.oldValue);
        assertNotNull(event.target);
    }

    @Test
    void testOnBindPropertyDelete() {
        Engine engine = new Engine();
        List<BindEvent> events = new ArrayList<>();
        ContextListener listener = new ContextListener() {
            @Override
            public void onBind(BindEvent event) {
                events.add(event);
            }
        };
        engine.setListener(listener);
        engine.eval("var obj = { foo: 'bar' }");
        events.clear();
        engine.eval("delete obj.foo");
        assertEquals(1, events.size());
        BindEvent event = events.get(0);
        assertEquals(BindType.PROPERTY_DELETE, event.type);
        assertEquals("foo", event.name);
        assertNull(event.value);
        assertEquals("bar", event.oldValue);
        assertNotNull(event.target);
    }

    @Test
    void testOnBindAssign() {
        Engine engine = new Engine();
        List<BindEvent> events = new ArrayList<>();
        ContextListener listener = new ContextListener() {
            @Override
            public void onBind(BindEvent event) {
                events.add(event);
            }
        };
        engine.setListener(listener);
        engine.eval("var x = 1");
        events.clear();
        engine.eval("x = 2");
        assertEquals(1, events.size());
        BindEvent event = events.get(0);
        assertEquals(BindType.ASSIGN, event.type);
        assertEquals("x", event.name);
        assertEquals(2, event.value);
        assertEquals(1, event.oldValue);
        assertNull(event.scope);
    }

    @Test
    void testOnBindLetConst() {
        Engine engine = new Engine();
        List<BindEvent> events = new ArrayList<>();
        ContextListener listener = new ContextListener() {
            @Override
            public void onBind(BindEvent event) {
                events.add(event);
            }
        };
        engine.setListener(listener);
        engine.eval("let a = 1");
        assertEquals(1, events.size());
        assertEquals(BindType.DECLARE, events.get(0).type);
        assertEquals(BindScope.LET, events.get(0).scope);
        events.clear();
        engine.eval("const b = 2");
        assertEquals(1, events.size());
        assertEquals(BindType.DECLARE, events.get(0).type);
        assertEquals(BindScope.CONST, events.get(0).scope);
    }

    @Test
    void testOnConsoleLog() {
        Engine engine = new Engine();
        StringBuilder sb = new StringBuilder();
        engine.setOnConsoleLog(sb::append);
        engine.eval("console.log('foo');");
        assertEquals("foo", sb.toString());
        sb.setLength(0);
        // ES6: a.prototype.toString does NOT affect a.toString() - prototype is only for instances
        // console.log uses Function.prototype.toString which returns actual source
        engine.eval("var a = function(){ }; a.prototype.toString = function(){ return 'bar' }; console.log(a)");
        assertEquals("function(){ }", sb.toString());
    }

    @Test
    void testErrorLog() {
        Engine engine = new Engine();
        try {
            engine.eval("var a = 1;\nvar b = a();");
            fail("expected error");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("a is not a function"));
        }
    }

    @Test
    void testWith() {
        Engine engine = new Engine();
        engine.put("foo", "parent");
        Map<String, Object> vars = new HashMap<>();
        vars.put("foo", "child");
        assertEquals("child", engine.evalWith("foo", vars));
        assertEquals("parent", engine.eval("foo"));
    }

    @Test
    void testProgramContextParentIsNotNull() {
        Engine engine = new Engine();
        ContextListener listener = new ContextListener() {
            @Override
            public void onEvent(Event event) {
                assertNotNull(event.context.getParent());
            }
        };
        engine.setListener(listener);
        engine.eval("'hello'");
    }

    @Test
    void testArrayForEachIterationTracking() {
        Engine engine = new Engine();
        StringBuilder sb = new StringBuilder();
        ContextListener listener = new ContextListener() {
            @Override
            public void onEvent(Event event) {
                if (event.type == EventType.CONTEXT_ENTER
                        && event.context.getScope() == ContextScope.FUNCTION
                        && event.node.type == NodeType.REF_DOT_EXPR
                        && "b.push".equals(event.node.getText())) {
                    Object[] args = event.context.getCallArgs();
                    sb.append(event.context.getParent().getParent().getIteration()).append(":").append(args[0]).append("|");
                }
            }
        };
        engine.setListener(listener);
        engine.eval("""
                var a = [1, 2, 3];
                var b = [];
                a.forEach(x => b.push(x));
                """);
        assertEquals(List.of(1, 2, 3), engine.get("b"));
        assertEquals("0:1|1:2|2:3|", sb.toString());
        //====
        sb.setLength(0);
        engine.eval("""
                var a = { a: 1, b: 2, c: 3 };
                var b = [];
                Object.keys(a).forEach(x => b.push(x));
                """);
        assertEquals(List.of("a", "b", "c"), engine.get("b"));
        assertEquals("0:a|1:b|2:c|", sb.toString());
    }

    @Test
    void testForLoopIterationTracking() {
        Engine engine = new Engine();
        StringBuilder sb = new StringBuilder();
        ContextListener listener = new ContextListener() {
            @Override
            public void onEvent(Event event) {
                if (event.type == EventType.CONTEXT_ENTER
                        && event.context.getScope() == ContextScope.FUNCTION
                        && event.node.type == NodeType.REF_DOT_EXPR
                        && "b.push".equals(event.node.getText())) {
                    Object[] args = event.context.getCallArgs();
                    sb.append(event.context.getParent().getIteration()).append(":").append(args[0]).append("|");
                }
            }
        };
        engine.setListener(listener);
        engine.eval("""
                var a = [1, 2, 3];
                var b = [];
                for (var i = 0; i < a.length; i++) b.push(a[i]);
                """);
        assertEquals(List.of(1, 2, 3), engine.get("b"));
        assertEquals("0:1|1:2|2:3|", sb.toString());
        //====
        sb.setLength(0);
        engine.eval("""
                var a = { a: 1, b: 2, c: 3 };
                var b = [];
                for (x in a) b.push(x);
                """);
        assertEquals(List.of("a", "b", "c"), engine.get("b"));
        assertEquals("0:a|1:b|2:c|", sb.toString());
        //====
        // for-of requires an iterable — use an array, not a plain object,
        // so the iteration tracker observes value enumeration through GetIterator.
        sb.setLength(0);
        engine.eval("""
                var a = [10, 20, 30];
                var b = [];
                for (x of a) b.push(x);
                """);
        assertEquals(List.of(10, 20, 30), engine.get("b"));
        assertEquals("0:10|1:20|2:30|", sb.toString());
    }

    @Test
    void testCommentExtraction() {
        String js = """
                console.log('foo');
                // hello world
                console.log('bar');
                """;
        Resource resource = Resource.text(js);
        JsParser parser = new JsParser(resource);
        Node node = parser.parse();
        assertTrue(node.getFirstToken().getComments().isEmpty());
        Node secondStatement = node.get(1);
        Token thenFirst = secondStatement.getFirstToken();
        assertEquals("// hello world", thenFirst.getComments().getFirst().getText());
    }

    @Test
    void testRootBindings() {
        Engine engine = new Engine();
        engine.putRootBinding("magic", "secret");
        engine.put("normal", "visible");

        // Root binding is accessible in eval
        assertEquals("secret", engine.eval("magic"));

        // But not in getBindings()
        assertFalse(engine.getBindings().containsKey("magic"));
        assertTrue(engine.getBindings().containsKey("normal"));
    }

    @Test
    void testLazyRootBindings() {
        Engine engine = new Engine();

        // Simulate a suite-level resource that may or may not exist
        String[] suiteDriver = { null };

        // Root binding with Supplier - lazily evaluated each time
        engine.putRootBinding("driver", (Supplier<String>) () -> {
            // This simulates: return local driver if exists, else suite driver
            return suiteDriver[0];
        });

        // Initially null
        assertNull(engine.eval("driver"));

        // Suite creates the driver
        suiteDriver[0] = "suite-driver";

        // Now it returns the suite driver (lazy evaluation)
        assertEquals("suite-driver", engine.eval("driver"));

        // Still not in getBindings() (hidden)
        assertFalse(engine.getBindings().containsKey("driver"));
    }

    @Test
    void testJsDocCommentExtraction() {
        String js = """
                /**
                 * @schema schema
                 */

                 console.log('hello world');
                """;
        Resource resource = Resource.text(js);
        JsParser parser = new JsParser(resource);
        Node node = parser.parse();
        String comment = node.getFirstToken().getComments().getFirst().getText();
        assertTrue(comment.startsWith("/**"));
    }

    @Test
    void testConstRedeclareAcrossEvals() {
        // REPL semantics: top-level const/let re-declaration across evals is allowed
        Engine engine = new Engine();
        engine.eval("const a = 1");
        assertEquals(1, engine.eval("a"));
        // Re-declare const with new value
        engine.eval("const a = 2");
        assertEquals(2, engine.eval("a"));
        // Re-declare as let
        engine.eval("let a = 3");
        assertEquals(3, engine.eval("a"));
        // Re-declaration within the SAME eval is still an error
        try {
            engine.eval("let b = 1; let b = 2");
            fail("error expected");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("identifier 'b' has already been declared"));
        }
    }

    @Test
    void testEnginePutCanBeOverwritten() {
        // engine.put() should not create const/let bindings
        Engine engine = new Engine();
        engine.put("x", 1);
        engine.put("x", 2); // should work
        assertEquals(2, engine.eval("x"));
        engine.eval("x = 3"); // should also work
        assertEquals(3, engine.eval("x"));
    }

    @Test
    void testBuiltinPrototypeExtension() {
        // Per spec, built-in prototype methods are configurable: true / writable: true.
        // Extending Array.prototype etc. is legitimate JS and the new property must be
        // visible to all instances via prototype lookup. Common in real-world libraries
        // (polyfills) and idiomatic LLM-written code.
        Engine engine = new Engine();
        engine.eval("Array.prototype.customMethod = function() { return 'custom'; }");
        assertEquals("custom", engine.eval("[].customMethod()"));
        engine.eval("String.prototype.shout = function() { return this + '!'; }");
        assertEquals("hi!", engine.eval("'hi'.shout()"));
        engine.eval("Object.prototype.tag = 'tagged';");
        assertEquals("tagged", engine.eval("({}).tag"));
        // User objects/arrays/functions remain mutable (regression check).
        engine.eval("var obj = {}; obj.foo = 'bar';");
        assertEquals("bar", engine.eval("obj.foo"));
        engine.eval("var arr = [1, 2, 3]; arr.push(4);");
        assertEquals(4, engine.eval("arr.length"));
        engine.eval("function myFunc() { return 1; }; myFunc.customProp = 'hello';");
        assertEquals("hello", engine.eval("myFunc.customProp"));
    }

    @Test
    void testNullValueInScopeChain() {
        Engine engine = new Engine();
        // Test 1: engine.put with null, then read from nested scope
        engine.put("nullVar", null);
        assertNull(engine.eval("nullVar"));
        // Read null from within a function (child scope)
        assertNull(engine.eval("(function() { return nullVar; })()"));

        // Test 2: overwrite null with a value from child scope
        engine.eval("(function() { nullVar = 'notNull'; })()");
        assertEquals("notNull", engine.eval("nullVar"));

        // Test 3: overwrite back to null from child scope
        engine.eval("(function() { nullVar = null; })()");
        assertNull(engine.eval("nullVar"));

        // Test 4: local variable shadows parent null
        engine.put("shadowMe", null);
        Object result = engine.eval("(function() { var shadowMe = 'local'; return shadowMe; })()");
        assertEquals("local", result);
        // Parent still has null
        assertNull(engine.eval("shadowMe"));

        // Test 5: null values in evalWith context
        Map<String, Object> vars = new HashMap<>();
        vars.put("localNull", null);
        assertNull(engine.evalWith("localNull", vars));
        // Can overwrite and return new value
        assertEquals("changed", engine.evalWith("localNull = 'changed'; localNull", vars));
    }

    @Test
    void testContextListenerExpressionTracking() {
        Engine engine = new Engine();
        List<Node> expressionNodes = new ArrayList<>();

        engine.setListener(new ContextListener() {
            @Override
            public void onEvent(Event event) {
                if (event.type == EventType.EXPRESSION_ENTER) {
                    expressionNodes.add(event.node);
                }
            }
        });

        // Evaluate a simple expression
        engine.eval("1 + 2");
        assertFalse(expressionNodes.isEmpty(), "Should capture at least one expression");

        // Check that we can find EXPR_LIST parent
        Node lastNode = expressionNodes.get(expressionNodes.size() - 1);
        Node exprList = lastNode.findParent(NodeType.EXPR_LIST);
        assertNotNull(exprList, "Should find EXPR_LIST parent. Node type: " + lastNode.type);
        assertTrue(exprList.getTextIncludingWhitespace().contains("1 + 2"),
            "EXPR_LIST should contain the source. Got: " + exprList.getTextIncludingWhitespace());
    }

    @Test
    void testContextListenerAssignmentTracking() {
        Engine engine = new Engine();
        List<Node> expressionNodes = new ArrayList<>();

        // Create a simple object to assign to
        engine.eval("var obj = {}");

        engine.setListener(new ContextListener() {
            @Override
            public void onEvent(Event event) {
                if (event.type == EventType.EXPRESSION_ENTER) {
                    expressionNodes.add(event.node);
                }
            }
        });

        // Evaluate an assignment expression (similar to tests['foo'] = true)
        expressionNodes.clear();
        engine.eval("obj['foo'] = 1 + 1 === 2");

        assertFalse(expressionNodes.isEmpty(), "Should capture expressions");

        // The last expression should be able to find its EXPR_LIST parent
        Node lastNode = expressionNodes.get(expressionNodes.size() - 1);
        Node exprList = lastNode.findParent(NodeType.EXPR_LIST);

        // Debug output
        logger.info("Last node type: {}", lastNode.type);
        logger.info("Last node text: {}", lastNode.getTextIncludingWhitespace());
        if (exprList != null) {
            logger.info("EXPR_LIST text: {}", exprList.getTextIncludingWhitespace());
        } else {
            logger.info("EXPR_LIST is null, walking parent chain:");
            Node parent = lastNode.getParent();
            while (parent != null) {
                logger.info("  Parent type: {}", parent.type);
                parent = parent.getParent();
            }
        }

        // Either EXPR_LIST should be found, or we fall back to the expression itself
        String source = exprList != null ? exprList.getTextIncludingWhitespace() : lastNode.getTextIncludingWhitespace();
        assertTrue(source.contains("obj['foo']") || source.contains("1 + 1"),
            "Source should contain the expression. Got: " + source);
    }

    @Test
    void testReEvalWithConstFunction() {
        // Simulates the veriquant Calc.java use case:
        // A JS file with top-level const + function is eval'd multiple times
        // on the same engine to re-load and re-invoke the function
        Engine engine = new Engine();
        engine.put("input", 5);
        String calcJs = """
                const lookup = { multiplier: 10 };
                function execute(calc) {
                    return calc.input * lookup.multiplier;
                }
                """;
        // First eval + invoke works fine
        engine.eval(calcJs);
        JsFunctionWrapper execute = (JsFunctionWrapper) engine.get("execute");
        assertNotNull(execute);
        // Second eval of the same file works (REPL semantics: top-level re-declaration allowed)
        engine.eval(calcJs);
        execute = (JsFunctionWrapper) engine.get("execute");
        assertNotNull(execute);
    }

    @Test
    void testStatementEnterScope() {
        Engine engine = new Engine();
        List<Node> statementNodes = new ArrayList<>();

        engine.setListener(new ContextListener() {
            @Override
            public void onEvent(Event event) {
                if (event.type == EventType.STATEMENT_ENTER) {
                    statementNodes.add(event.node);
                }
            }
        });

        engine.eval("var obj = {}");
        statementNodes.clear();
        engine.eval("obj['foo'] = 1 + 1 === 2");

        // Verify root context has ROOT scope
        assertEquals(ContextScope.ROOT, engine.getRootContext().getScope());

        // Verify STATEMENT_ENTER was captured
        assertFalse(statementNodes.isEmpty(), "Should capture STATEMENT_ENTER events");

        // For expression statements, the statement node IS the EXPR_LIST
        Node lastStmt = statementNodes.get(statementNodes.size() - 1);
        assertEquals(NodeType.EXPR_LIST, lastStmt.type);
        assertTrue(lastStmt.getTextIncludingWhitespace().contains("obj['foo']"));
    }

    @Test
    void testEncodeDecodeURIComponent() {
        Engine engine = new Engine();
        assertEquals("hello%20world", engine.eval("encodeURIComponent('hello world')"));
        assertEquals("hello%26world", engine.eval("encodeURIComponent('hello&world')"));
        assertEquals("hello%3Dworld", engine.eval("encodeURIComponent('hello=world')"));
        assertEquals("hello%2Fworld", engine.eval("encodeURIComponent('hello/world')"));
        assertEquals("-_.!~*'()", engine.eval("encodeURIComponent(\"-_.!~*'()\")"));
        assertEquals("hello world", engine.eval("decodeURIComponent('hello%20world')"));
        assertEquals("hello&world", engine.eval("decodeURIComponent('hello%26world')"));
        assertEquals("a=1&b=2", engine.eval("decodeURIComponent(encodeURIComponent('a=1&b=2'))"));
    }

    @Test
    void testEncodeDecodeURI() {
        Engine engine = new Engine();
        assertEquals("https://example.com/path?q=hello%20world&x=1",
                engine.eval("encodeURI('https://example.com/path?q=hello world&x=1')"));
        assertEquals("hello%20world", engine.eval("encodeURI('hello world')"));
        assertEquals(":/?#@!$&'()*+,;=", engine.eval("encodeURI(\":/?#@!$&'()*+,;=\")"));
        assertEquals("hello world", engine.eval("decodeURI('hello%20world')"));
    }

    @Test
    void testJavaCallableExceptionIsJsCatchable() {
        // RuntimeException thrown by a JavaCallable becomes a JS-catchable Error
        // with the original message exposed via e.message
        Engine engine = new Engine();
        engine.put("boom", (JavaCallable) (ctx, args) -> {
            throw new RuntimeException("signature verification failed");
        });
        Object result = engine.eval(
                "var msg;\n" +
                        "try { boom(); msg = 'no-throw'; } catch (e) { msg = 'caught:' + e.message }\n" +
                        "msg");
        assertEquals("caught:signature verification failed", result);
    }

    @Test
    void testJavaCallableExceptionExposesNameAndToString() {
        // Wrapped errors have name="Error" and a JS-compatible toString ("Error: <msg>")
        Engine engine = new Engine();
        engine.put("boom", (JavaCallable) (ctx, args) -> {
            throw new IllegalStateException("bad state");
        });
        Object result = engine.eval(
                "try { boom(); '' } catch (e) { e.name + '|' + e.message + '|' + ('' + e) }");
        assertEquals("Error|bad state|Error: bad state", result);
    }

    @Test
    void testJavaCallableExceptionUncaughtBubbles() {
        // When NOT inside try/catch, the original message still surfaces to Java callers
        Engine engine = new Engine();
        engine.put("boom", (JavaCallable) (ctx, args) -> {
            throw new RuntimeException("kaboom");
        });
        try {
            engine.eval("boom()");
            fail("expected error");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("kaboom"), "message should bubble: " + e.getMessage());
        }
    }

    @Test
    void testJavaInvokableExceptionIsJsCatchable() {
        // JavaInvokable is also external — same JS-catchable behavior
        Engine engine = new Engine();
        engine.put("boom", (JavaInvokable) args -> {
            throw new RuntimeException("invoke failed");
        });
        Object result = engine.eval(
                "try { boom() } catch (e) { 'got:' + e.message }");
        assertEquals("got:invoke failed", result);
    }

    @Test
    void testJavaCallableNullMessageHandled() {
        // RuntimeException with null message falls back to class simple name
        Engine engine = new Engine();
        engine.put("boom", (JavaCallable) (ctx, args) -> {
            throw new IllegalStateException();
        });
        Object result = engine.eval(
                "try { boom() } catch (e) { e.message }");
        assertEquals("IllegalStateException", result);
    }

    @Test
    void testJavaCallableExceptionRethrowAsString() {
        // Common downstream pattern: catch JS, rethrow as plain string,
        // outer handler concatenates 'error:' + rethrown
        Engine engine = new Engine();
        engine.put("decode", (JavaCallable) (ctx, args) -> {
            throw new RuntimeException("signature verification failed");
        });
        Object result = engine.eval(
                "try {\n" +
                        "  try { decode('bad') } catch (e) { throw e.message }\n" +
                        "} catch (msg) { 'error:' + msg }");
        assertEquals("error:signature verification failed", result);
    }

    @Test
    void testFlowControlSignalNotCatchableByJs() {
        // FlowControlSignal exceptions must pass through unchanged — JS catch{} cannot intercept
        Engine engine = new Engine();
        engine.put("redirect", (JavaCallable) (ctx, args) -> {
            throw new TestFlowSignal("redirect to /home");
        });
        try {
            engine.eval("try { redirect() } catch (e) { /* should NOT catch */ }");
            fail("expected FlowControlSignal to propagate");
        } catch (TestFlowSignal e) {
            assertEquals("redirect to /home", e.getMessage());
        }
    }

    @Test
    void testThrownErrorObjectWorks() {
        // ES6 baseline: throw new Error('foo'); catch(e) { e.message }
        Engine engine = new Engine();
        Object result = engine.eval(
                "try { throw new Error('boom') } catch (e) { e.name + ':' + e.message }");
        assertEquals("Error:boom", result);
    }

    @Test
    void testThrownTypeErrorPreservesName() {
        // ES6: new TypeError('foo') has name='TypeError', not 'Error'
        Engine engine = new Engine();
        Object result = engine.eval(
                "try { throw new TypeError('bad type') } catch (e) { e.name + ':' + e.message }");
        assertEquals("TypeError:bad type", result);
    }

    @Test
    void testErrorToStringEs6() {
        // ES6: '' + new Error('foo') === 'Error: foo'
        Engine engine = new Engine();
        assertEquals("Error: boom", engine.eval("'' + new Error('boom')"));
        assertEquals("TypeError: bad", engine.eval("'' + new TypeError('bad')"));
        // No-message case: just the name
        assertEquals("Error", engine.eval("'' + new Error()"));
    }

    @Test
    void testErrorWithoutNewKeyword() {
        // ES6: Error('foo') (without new) returns an Error instance, same as new Error('foo')
        Engine engine = new Engine();
        assertEquals("boom", engine.eval("Error('boom').message"));
        assertEquals("Error", engine.eval("Error('boom').name"));
        assertEquals("TypeError", engine.eval("TypeError('x').name"));
    }

    @Test
    void testErrorSubtypeConstructorsAreGlobals() {
        // The five additional NativeError constructors from ES1/ES3 must be
        // globally visible — test262 fixtures constantly do `throw new RangeError(...)`
        // and `assert.throws(ReferenceError, ...)`.
        Engine engine = new Engine();
        assertEquals("ReferenceError:r", engine.eval(
                "try { throw new ReferenceError('r') } catch (e) { e.name + ':' + e.message }"));
        assertEquals("RangeError:rg", engine.eval(
                "try { throw new RangeError('rg') } catch (e) { e.name + ':' + e.message }"));
        assertEquals("SyntaxError:s", engine.eval(
                "try { throw new SyntaxError('s') } catch (e) { e.name + ':' + e.message }"));
        assertEquals("URIError:u", engine.eval(
                "try { throw new URIError('u') } catch (e) { e.name + ':' + e.message }"));
        assertEquals("EvalError:ev", engine.eval(
                "try { throw new EvalError('ev') } catch (e) { e.name + ':' + e.message }"));
        // Without-new form and toString() should work uniformly for all subtypes
        assertEquals("RangeError: oops", engine.eval("'' + RangeError('oops')"));
        assertEquals("ReferenceError", engine.eval("'' + new ReferenceError()"));
    }

    @Test
    void testJsThrownErrorPropagatesToJavaCaller() {
        // Uncaught JS-thrown Error surfaces to Java caller with its message
        Engine engine = new Engine();
        try {
            engine.eval("throw new Error('uncaught')");
            fail("expected error");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("uncaught"), "message should propagate: " + e.getMessage());
        }
    }

    /**
     * Used by testFlowControlSignalNotCatchableByJs.
     */
    static class TestFlowSignal extends RuntimeException implements FlowControlSignal {
        TestFlowSignal(String msg) {
            super(msg);
        }
    }

    @Test
    void testErrorMessagesUseJsTypeNames() {
        // JavaUtils.invoke errors should use JS type names, not Java class names
        // Test: calling non-existent method on a Map (Object in JS terms)
        try {
            JavaUtils.invoke(new HashMap<>(), "find", new Object[]{});
            fail("expected error");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertFalse(msg.contains("java.util."), "Error should not expose Java types: " + msg);
            assertTrue(msg.contains("Object"), "Error should use JS type name 'Object': " + msg);
        }
        // Test: calling non-existent method on a List (Array in JS terms)
        try {
            JavaUtils.invoke(new ArrayList<>(), "find", new Object[]{});
            fail("expected error");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertFalse(msg.contains("java.util."), "Error should not expose Java types: " + msg);
            assertTrue(msg.contains("Array"), "Error should use JS type name 'Array': " + msg);
        }
        // Test: calling non-existent method on a String
        try {
            JavaUtils.invoke("hello", "foo", new Object[]{});
            fail("expected error");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertFalse(msg.contains("java.lang."), "Error should not expose Java types: " + msg);
            assertTrue(msg.contains("String"), "Error should use JS type name 'String': " + msg);
        }
        // Test: calling non-existent method on a Number
        try {
            JavaUtils.invoke(42, "foo", new Object[]{});
            fail("expected error");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertFalse(msg.contains("java.lang."), "Error should not expose Java types: " + msg);
            assertTrue(msg.contains("Number"), "Error should use JS type name 'Number': " + msg);
        }
    }

    @Test
    void testBuiltinConstructorStateResetBetweenEngines() {
        // Built-in constructor singletons (JsNumberConstructor.INSTANCE etc.) live for the
        // JVM's lifetime. Without per-Engine state reset, a user-set property or a delete
        // on one Engine would leak into the next — propertyHelper.js does exactly this in
        // a tight loop. Cover the user-set / delete / configurability-flip paths.
        Engine e1 = new Engine();
        e1.eval("Number.foo = 123");
        e1.eval("delete Number.isFinite");
        e1.eval("Object.bar = 456");

        Engine e2 = new Engine();
        // Fresh engine should NOT see properties added by the previous engine.
        assertEquals(Terms.UNDEFINED, e2.evalRaw("Number.foo"));
        assertEquals(Terms.UNDEFINED, e2.evalRaw("Object.bar"));
        // Deleted intrinsics should be back.
        assertEquals(true, e2.eval("Number.isFinite(1)"));
    }

}

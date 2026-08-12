package io.karatelabs.js;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.JsParser;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.ParserException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * async / await — parse phase. The AST records async-ness on the function node and
 * {@code await} parses as a unary operator; the runtime is deliberately unchanged, so an
 * async function still runs synchronously and {@code await} passes its operand through.
 * The behavior these lock down is what a later runtime phase has to keep parsing.
 */
class JsAsyncAwaitTest extends EvalBase {

    private static Node parse(String text) {
        return new JsParser(Resource.text(text)).parse();
    }

    private static Node firstFn(String text) {
        Node fn = parse(text).findFirstChild(NodeType.FN_EXPR);
        assertNotNull(fn, "no FN_EXPR in: " + text);
        return fn;
    }

    private static Node firstArrow(String text) {
        Node fn = parse(text).findFirstChild(NodeType.FN_ARROW_EXPR);
        assertNotNull(fn, "no FN_ARROW_EXPR in: " + text);
        return fn;
    }

    /** The function bound to {@code name} after evaluating — read from the raw bindings, as
     *  the engine's own return path wraps functions for host callers. */
    private JsFunctionNode fn(String text, String name) {
        eval(text);
        Object value = engine.getRawBindings().get(name);
        assertInstanceOf(JsFunctionNode.class, value, "not a JS function: " + value);
        return (JsFunctionNode) value;
    }

    // ===== async functions =====

    @Test
    void testAsyncFunctionDeclaration() {
        assertTrue(firstFn("async function f() {}").async);
        assertFalse(firstFn("function f() {}").async);
        // the name is still the name — the modifier token precedes the `function` keyword
        assertEquals(1, eval("async function f() { return 1 }\nf()"));
        // and it is still hoisted, like any other function declaration
        assertEquals(2, eval("var r = f();\nasync function f() { return 2 }\nr"));
    }

    @Test
    void testAsyncFunctionExpression() {
        assertTrue(firstFn("var f = async function () {}").async);
        assertTrue(firstFn("var f = async function named() {}").async);
        assertEquals(3, eval("var f = async function () { return 3 }\nf()"));
        assertEquals(4, eval("var f = async function g() { return 4 }\nf()"));
    }

    @Test
    void testAsyncFunctionFlagOnFunctionObject() {
        assertTrue(fn("async function f() {}", "f").async);
        assertFalse(fn("function f() {}", "f").async);
        assertTrue(fn("var f = async () => 1", "f").async);
        assertTrue(fn("var f = async x => x", "f").async);
        assertFalse(fn("var f = () => 1", "f").async);
    }

    // ===== async arrows =====

    @Test
    void testAsyncArrowWithParens() {
        assertTrue(firstArrow("var f = async (a, b) => {}").async);
        assertTrue(firstArrow("var f = async () => 1").async);
        assertFalse(firstArrow("var f = (a, b) => {}").async);
        assertEquals(7, eval("var f = async (a, b) => { return a + b }\nf(3, 4)"));
        assertEquals(7, eval("var f = async (a, b) => a + b\nf(3, 4)"));
    }

    @Test
    void testAsyncArrowSingleParam() {
        Node arrow = firstArrow("var f = async x => x");
        assertTrue(arrow.async);
        // the lone parameter is wrapped so every async arrow has the same shape as `(x) =>`
        assertNotNull(arrow.findFirstChild(NodeType.FN_DECL_ARGS));
        assertEquals(10, eval("var f = async x => x * 2\nf(5)"));
        assertEquals(6, eval("var f = async x => { return x + 1 }\nf(5)"));
    }

    // ===== methods =====

    @Test
    void testObjectLiteralAsyncMethod() {
        Node fn = firstFn("var o = { async m() { return 1 } }");
        assertTrue(fn.async);
        assertEquals(1, eval("var o = { async m() { return 1 } }\no.m()"));
        assertEquals(2, eval("var o = { async m(a) { return a } }\no.m(2)"));
        // sibling entries are unaffected
        assertEquals(3, eval("var o = { a: 3, async m() { return 1 } }\no.a"));
    }

    @Test
    void testClassAsyncMethod() {
        // this used to parse as a FIELD named `async` plus an ordinary method — the async
        // was silently dropped, which is exactly what the flag ends
        Node fn = firstFn("class C { async m() { return 1 } }");
        assertTrue(fn.async);
        assertEquals(1, eval("class C { async m() { return 1 } }\nnew C().m()"));
        assertEquals(2, eval("class C { static async m() { return 2 } }\nC.m()"));
        assertTrue(fn("class C { async m() {} }\nvar m = C.prototype.m", "m").async);
        assertTrue(fn("class C { static async m() {} }\nvar m = C.m", "m").async);
        assertFalse(fn("class C { m() {} }\nvar m = C.prototype.m", "m").async);
        // and no phantom `async` field is left behind (undefined surfaces as null to a host)
        assertNull(eval("class C { async m() { return 1 } }\nnew C().async"));
    }

    // ===== await =====

    @Test
    void testAwaitAtTopLevel() {
        assertEquals(1, eval("await 1"));
        assertEquals(2, eval("var x = await 2\nx"));
        assertNotNull(parse("await 1").findFirstChild(NodeType.AWAIT_EXPR));
    }

    @Test
    void testAwaitInsideAsyncFunction() {
        assertEquals(1, eval("async function f() { return await 1 }\nf()"));
        assertEquals(5, eval("async function f(a) { var x = await a\nreturn x + 1 }\nf(4)"));
        assertEquals(3, eval("var f = async () => await 3\nf()"));
        assertEquals(4, eval("var o = { async m() { return await 4 } }\no.m()"));
        assertEquals(5, eval("class C { async m() { return await 5 } }\nnew C().m()"));
    }

    @Test
    void testAwaitPrecedenceIsUnary() {
        // `await a + b` is `(await a) + b`, as for every other unary operator
        assertEquals(3, eval("async function f() { return await 1 + 2 }\nf()"));
        assertEquals(6, eval("async function f() { return await 2 * 3 }\nf()"));
        assertEquals(1, eval("async function f() { return await await 1 }\nf()"));
    }

    @Test
    void testForAwaitIsRejected() {
        ParserException e = assertThrows(ParserException.class,
                () -> parse("async function f() { for await (const x of xs) {} }"));
        assertTrue(e.getMessage().contains("for await"), e.getMessage());
        assertTrue(e.getMessage().contains("not supported"), e.getMessage());
    }

    // ===== contextual-keyword behavior that must not regress =====

    @Test
    void testAsyncStaysAnIdentifier() {
        assertEquals(1, eval("var async = 1\nasync"));
        assertEquals(1, eval("({ async: 1 }).async"));
        assertEquals(2, eval("var o = {}\no.async = 2\no.async"));
        assertEquals(7, eval("function f(async) { return async }\nf(7)"));
        assertEquals(3, eval("var o = { async() { return 3 } }\no.async()"));
        assertEquals(4, eval("var async = function () { return 4 }\nasync()"));
        // `async` is a plain single-parameter arrow parameter here, not a modifier
        assertEquals(5, eval("var f = async => async\nf(5)"));
    }

    @Test
    void testAwaitStaysAnIdentifierWhereItMust() {
        assertEquals(5, eval("var await = 5\nawait"));
        assertEquals(6, eval("var await = 5\nawait = 6\nawait"));
        // inside an ordinary (non-async) function, even one nested in an async one
        assertEquals(7, eval("function f() { var await = 7\nreturn await }\nf()"));
        assertEquals(8, eval("async function f() { function g() { var await = 8\nreturn await }\nreturn g() }\nf()"));
    }

    @Test
    void testLineTerminatorAfterAsync() {
        // no LineTerminator is allowed between `async` and `function` / the arrow params,
        // so this is the identifier `async` and then a separate function declaration
        assertEquals(1, eval("var async = 1\nasync\nfunction f() { return 2 }\nasync"));
        assertFalse(firstFn("var async = 1\nasync\nfunction f() {}").async);
        assertEquals(2, eval("var async = 1\nasync\nfunction f() { return 2 }\nf()"));
        // a class member likewise: `async` on its own line is a field, `m` a sync method
        Node classFn = firstFn("class C { async\nm() { return 1 } }");
        assertFalse(classFn.async);
    }

    @Test
    void testAsyncFunctionCallSyntaxUnaffected() {
        // `async(...)` is still a call, not an arrow — the `=>` is what makes an arrow
        assertEquals(9, eval("function async(a) { return a }\nasync(9)"));
        assertEquals(2, eval("var f = function (a) { return a + 1 }\nvar async = f\nasync(1)"));
    }

    @Test
    void testForAwaitInErrorRecoveryMode() {
        // the IDE path collects the error instead of throwing — it must still terminate
        JsParser parser = new JsParser(Resource.text("for await (const x of xs) {}"), true);
        assertNotNull(parser.parse());
        assertTrue(parser.hasErrors());
    }

    @Test
    void testAwaitScopeFollowsTheNearestFunction() {
        // an async arrow inside an ordinary function still gets `await`
        assertEquals(1, eval("function f() { var g = async () => await 1\nreturn g() }\nf()"));
        // and an ordinary arrow inside an async function does not — it is an identifier
        assertEquals(2, eval("async function f() { var g = () => { var await = 2\nreturn await }\nreturn g() }\nf()"));
        // a non-async method of an object built inside an async function, likewise
        assertEquals(3, eval("async function f() { var o = { m() { var await = 3\nreturn await } }\nreturn o.m() }\nf()"));
    }

    @Test
    void testAsyncMethodWithComputedKey() {
        assertEquals(1, eval("var o = { async ['m' + 1]() { return 1 } }\no.m1()"));
        assertEquals(2, eval("class C { async ['m' + 2]() { return 2 } }\nnew C().m2()"));
        assertTrue(firstFn("var o = { async ['m'](){} }").async);
    }

    @Test
    void testAsyncFunctionSubtreeIsIntact() {
        // the `async` token stays in the tree, so the node's source text is complete
        Node fn = firstFn("async function f(a, b) { return a }");
        assertEquals("async function f(a, b) { return a }", fn.getTextIncludingWhitespace());
        List<Node> args = fn.findFirstChild(NodeType.FN_DECL_ARGS).findImmediateChildren(NodeType.FN_DECL_ARG);
        assertEquals(2, args.size());
    }

}

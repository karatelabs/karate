/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.js;

import io.karatelabs.parser.ParserException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserExceptionTest {

    @Test
    void testParseFailureSurfacesAsParserException() {
        Engine engine = new Engine();
        // malformed: var declaration with no initializer expression
        assertThrows(ParserException.class, () -> engine.eval("var x = ;"));
    }

    @Test
    void testParseFailureNotWrappedAsEngineException() {
        Engine engine = new Engine();
        try {
            engine.eval("function foo( { }");
            fail("expected a parser exception");
        } catch (ParserException pe) {
            // expected
        } catch (EngineException ee) {
            fail("parse error should not be wrapped as EngineException: " + ee);
        }
    }

    @Test
    void testRuntimeErrorIsNotParserException() {
        Engine engine = new Engine();
        try {
            engine.eval("throw new Error('runtime')");
            fail("expected a runtime error");
        } catch (ParserException pe) {
            fail("runtime error should not surface as ParserException: " + pe);
        } catch (EngineException ee) {
            // expected
        }
    }

    // A line starting with '[' or '(' continues the previous line when that line has no ';'.
    // The parse is spec-correct; only the message changes - it now names the omitted ';'.

    @Test
    void testMissingSemicolonBeforeBracketLineHintsAtAsi() {
        Engine engine = new Engine();
        ParserException pe = assertThrows(ParserException.class,
                () -> engine.eval("const a = 1\n[1,2].forEach(function(x){})"));
        assertEquals("expected: [R_BRACKET]\n"
                        + "2:3 ,\n"
                        + "parser state: | const a = 1 [ 1 >>, 2 ] . forEach ( function |\n"
                        + "current node: VAR_DECL >> EXPR >> [REF_BRACKET_EXPR]\n"
                        + "hint: line 2 starts with '[' — without a ';' ending line 1 it continues that"
                        + " statement (as an index); add ';' to the end of line 1 if a new statement was intended",
                pe.getMessage());
    }

    @Test
    void testAsiHintDoesNotFireOnUnrelatedParseErrors() {
        Engine engine = new Engine();
        // nothing line-initial anywhere near the failure
        assertNoAsiHint(engine, "var x = ;");
        assertNoAsiHint(engine, "function foo( { }");
        // the previous line already ended with a ';'
        assertNoAsiHint(engine, "const c = 1;\n[1,2].forEach(function(x){");
        // a genuine continuation - the previous line ends with an operator
        assertNoAsiHint(engine, "const d =\n[1,2].forEach(function(x){}");
        // an `if` head never wanted a ';' after it
        assertNoAsiHint(engine, "if (true)\n(1 +)");
    }

    @Test
    void testDoubledQuoteHintsAtStrayQuote() {
        Engine engine = new Engine();
        ParserException pe = assertThrows(ParserException.class,
                () -> engine.eval("bot.act('#a', 'input', 'x');\nbot.act('[data-test=\"username\"]', 'input', ''standard_user');"));
        assertTrue(pe.getMessage().endsWith("\nhint: line 2 col 44: '' is an empty string followed directly by"
                + " standard_user — a doubled or stray quote?"), pe.getMessage());
        // the failure lands on the statement start, the object value, or the operand before it
        assertHint(engine, "const s = ''x';", "hint: line 1 col 11: '' is an empty string followed directly by x");
        assertHint(engine, "x = {a: ''b'}", "hint: line 1 col 9: '' is an empty string followed directly by b");
        assertHint(engine, "f(a + ''x')", "hint: line 1 col 7: '' is an empty string followed directly by x");
        assertHint(engine, "f(\"a\" \"b\")", "hint: line 1 col 3: \"a\" is a string followed directly by \"b\""
                + " — a stray quote, or a missing ',' or '+'?");
    }

    @Test
    void testStrayQuoteHintDoesNotFireOnValidNeighbours() {
        Engine engine = new Engine();
        assertNoAsiHint(engine, "f('a' + b, )x");
        assertNoAsiHint(engine, "f(`a${b}` c)");
        assertNoAsiHint(engine, "var s = 'a'\nfoo(;");
        assertNoAsiHint(engine, "var o = {'a': b, 'c' in d +};");
    }

    @Test
    void testSameLineTokenAfterStatementIsParseError() {
        Engine engine = new Engine();
        for (String script : new String[]{
                "var x = 'a' b;", "x = 1 y = 2", "let a = b c", "const c = 1 d", "var e = 1, f = 2 g",
                "function h() { return x y }", "throw x y", "l: for (;;) { break l z }",
                "l: for (;;) { continue l z }", "a = 1 'b'", "f() g()"}) {
            assertThrows(ParserException.class, () -> engine.eval(script), script);
        }
        assertHint(engine, "var x = 'a' b;", "hint: line 1 col 9: 'a' is a string followed directly by b");
    }

    @Test
    void testAsiStillInsertedWhereSpecAllows() {
        Engine engine = new Engine();
        assertEquals(3, ((Number) engine.eval("var a = 1\nvar b = 2\na + b")).intValue());
        assertEquals(7, ((Number) engine.eval("function f() { return 7 } f()")).intValue());
        engine.eval("var g = 4");
        assertEquals(4, ((Number) engine.eval("g")).intValue());
        assertEquals(2, ((Number) engine.eval("var c = 1, d = 1\nc\n++d\nd")).intValue());
        assertEquals(1, ((Number) engine.eval("c")).intValue());
        assertNull(engine.eval("function r() { return\n42 } r()"));
        assertEquals(3, ((Number) engine.eval("var i = 0; do i++; while (i < 3) i")).intValue());
        assertEquals(5, ((Number) engine.eval("var k = 0; if (true) { k = 5 } k")).intValue());
    }

    @Test
    void testEscapedAndAstralIdentifiers() {
        Engine engine = new Engine();
        assertEquals(1, ((Number) engine.eval("var \\u0062 = 1; b")).intValue());
        assertEquals(2, ((Number) engine.eval("var a\\u{62}c = 2; abc")).intValue());
        assertEquals(3, ((Number) engine.eval("var 𐀀x = 3; 𐀀x")).intValue());
        assertThrows(ParserException.class, () -> engine.eval("var \\u0063ase = 1"));
        assertEquals(4, ((Number) engine.eval("({ \\u0069f: 4 }).i\\u{66}")).intValue());
        assertThrows(ParserException.class, () -> engine.eval("var \\u0030x = 1"));
        assertThrows(ParserException.class, () -> engine.eval("var a\\u00 = 1"));
        assertThrows(ParserException.class, () -> engine.eval("a \\ b"));
        // an escaped contextual keyword is only ever an identifier, and only where that word may be one
        assertEquals(5, ((Number) engine.eval("var st\\u0061tic = 5; static")).intValue());
        assertThrows(ParserException.class, () -> engine.eval("'use strict'; var st\\u0061tic = 1"));
        assertThrows(ParserException.class, () -> engine.eval("function* g() { var yi\\u0065ld; }"));
        assertThrows(ParserException.class, () -> engine.eval("async function f() { \\u0061wait: 1 }"));
        assertThrows(ParserException.class, () -> engine.eval("({ g\\u0065t m() { return 1 } })"));
        engine.eval("function* h() { (function yield() {}) }");
    }

    private static void assertHint(Engine engine, String script, String hint) {
        ParserException pe = assertThrows(ParserException.class, () -> engine.eval(script));
        assertTrue(pe.getMessage().contains("\n" + hint), pe.getMessage());
    }

    private static void assertNoAsiHint(Engine engine, String script) {
        ParserException pe = assertThrows(ParserException.class, () -> engine.eval(script));
        assertFalse(pe.getMessage().contains("hint:"), pe.getMessage());
    }

    // The four spec-defined Static Semantics: Early Errors involving optional
    // chaining. Each must surface as ParserException — the test262 runner
    // classifies that as `phase: parse, type: SyntaxError`, matching what the
    // negative tests expect.

    @Test
    void testOptionalChainAssignmentIsParseError() {
        // `OptionalExpression` is not a valid simple-assignment target.
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.a = 1;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.a += 1;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.a.b = 1;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.[k] = 1;"));
    }

    @Test
    void testOptionalChainUpdateIsParseError() {
        // `++expr` / `expr--` operands must be valid simple-assignment targets.
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; ++obj?.a;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; --obj?.a;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.a++;"));
        assertThrows(ParserException.class, () -> engine.eval("var obj = {}; obj?.a--;"));
    }

    @Test
    void testOptionalChainTaggedTemplateIsParseError() {
        // `OptionalChain :: ?. TemplateLiteral` is explicitly listed as a Syntax Error.
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("var a = {fn(){}}; a?.fn`hello`;"));
        assertThrows(ParserException.class, () -> engine.eval("var a = {fn(){}}; a?.fn`x${1}y`;"));
    }

    // §14.13.1 Static Semantics: Early Errors for labelled statements.

    @Test
    void testUndefinedLabelIsParseError() {
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("for (var i = 0; i < 1; i++) { break nope }"));
        assertThrows(ParserException.class, () -> engine.eval("for (var i = 0; i < 1; i++) { continue nope }"));
        assertThrows(ParserException.class, () -> engine.eval("a: for (var i = 0; i < 1; i++) { break b }"));
        // labels do not cross a function boundary
        assertThrows(ParserException.class,
                () -> engine.eval("a: for (var i = 0; i < 1; i++) { function f() { break a } }"));
        assertThrows(ParserException.class,
                () -> engine.eval("a: for (var i = 0; i < 1; i++) { var f = () => { continue a } }"));
    }

    @Test
    void testDuplicateLabelIsParseError() {
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("a: a: for (var i = 0; i < 1; i++) {}"));
        assertThrows(ParserException.class, () -> engine.eval("a: { b: { a: ; } }"));
    }

    @Test
    void testContinueToNonIterationLabelIsParseError() {
        Engine engine = new Engine();
        // the label names a block, not a loop
        assertThrows(ParserException.class,
                () -> engine.eval("a: { for (var i = 0; i < 1; i++) { continue a } }"));
        assertThrows(ParserException.class, () -> engine.eval("a: { continue a }"));
        // the label names a switch
        assertThrows(ParserException.class,
                () -> engine.eval("a: switch (1) { case 1: for (var i = 0; i < 1; i++) { continue a } }"));
    }

    @Test
    void testLabelledDeclarationIsParseError() {
        // LabelledItem is a Statement; a declaration is not one. karate-js rejects the
        // Annex B.3.1 sloppy-mode function form too — see JsParser.earlyErrorNodeChecks.
        Engine engine = new Engine();
        assertThrows(ParserException.class, () -> engine.eval("a: function f() {}"));
        assertThrows(ParserException.class, () -> engine.eval("a: let x = 1"));
        assertThrows(ParserException.class, () -> engine.eval("a: class C {}"));
    }
}

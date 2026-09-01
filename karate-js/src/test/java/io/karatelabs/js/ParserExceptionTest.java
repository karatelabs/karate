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

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
}

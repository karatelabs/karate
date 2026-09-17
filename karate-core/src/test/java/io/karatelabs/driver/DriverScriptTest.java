/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@link Driver#script(Object)} actually hands to the browser: a function value is
 * invoked, everything else travels verbatim.
 */
class DriverScriptTest {

    private final DriverStub stub = new DriverStub();

    private String sent(Object expression) {
        stub.driver.script(expression);
        return stub.scripts.get(stub.scripts.size() - 1);
    }

    @Test
    void testIifeSentVerbatim() {
        // an IIFE already invokes itself — a second "()" would call its result
        assertEquals("(() => { return true; })()", sent("(() => { return true; })()"));
        assertEquals("(function(){ return true; })()", sent("(function(){ return true; })()"));
    }

    @Test
    void testIifeReturningObjectSentVerbatim() {
        // the object literal's braces must not read as a function body
        assertEquals("(() => { return {value: 42}; })()", sent("(() => { return {value: 42}; })()"));
    }

    @Test
    void testDeclarationThenCallSentVerbatim() {
        String js = """

                function answer(){ return 42; }
                answer();
                """;
        assertEquals(js, sent(js));
    }

    @Test
    void testValueExpressionSentVerbatim() {
        assertEquals("document.body != null", sent("document.body != null"));
        assertEquals("list.filter(x => x.ok)", sent("list.filter(x => x.ok)"));
    }

    @Test
    void testFunctionDefinitionInvoked() {
        assertEquals("(() => document.title)()", sent("() => document.title"));
        assertEquals("(function(){ return 1 })()", sent("function(){ return 1 }"));
    }

    @Test
    void testGroupedFunctionInvoked() {
        // grouping parens are not a call — the wrap is what makes it run
        assertEquals("((() => 42))()", sent("(() => 42)"));
        assertEquals("(async () => 1)()", sent("async () => 1"));
    }

    @Test
    void testFunctionWithRegexLiteralInvoked() {
        assertEquals("(function(){ return /}/.test('}'); })()", sent("function(){ return /}/.test('}'); }"));
        assertEquals("(async()=>42)()", sent("async()=>42"));
    }

    @Test
    void testSyntaxKarateJsDoesNotParseStillInvoked() {
        String fn = "function(){ window.chunks = async function*(){ yield 42; }; return true; }";
        assertEquals("(" + fn + ")()", sent(fn));
        assertEquals("(async function*(){ yield 1 })()", sent("(async function*(){ yield 1 })()"));
        assertEquals("class {", sent("class {"));
    }

    @Test
    void testJsFunctionInvoked() {
        Engine engine = new Engine();
        engine.eval("var fn = () => document.title");
        assertEquals("(() => document.title)()", sent(engine.getBindings().get("fn")));
    }

}

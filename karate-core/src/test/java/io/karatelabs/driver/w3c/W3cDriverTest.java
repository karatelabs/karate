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
package io.karatelabs.driver.w3c;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class W3cDriverTest {

    // ========== prefixReturnIfNeeded ==========

    @Test
    void testPrefixReturnBareExpression() {
        assertEquals("return document.title", W3cDriver.prefixReturnIfNeeded("document.title"));
        assertEquals("return 1 + 2", W3cDriver.prefixReturnIfNeeded("1 + 2"));
        assertEquals("return foo()", W3cDriver.prefixReturnIfNeeded("foo()"));
    }

    @Test
    void testPrefixReturnAlreadyPrefixed() {
        assertEquals("return x", W3cDriver.prefixReturnIfNeeded("return x"));
        assertEquals("return;", W3cDriver.prefixReturnIfNeeded("return;"));
    }

    @Test
    void testPrefixReturnStatementKeywords() {
        // var/let/const must not be prefixed — they're declarations
        assertEquals("var x = 1", W3cDriver.prefixReturnIfNeeded("var x = 1"));
        assertEquals("let x = 1", W3cDriver.prefixReturnIfNeeded("let x = 1"));
        assertEquals("const x = 1", W3cDriver.prefixReturnIfNeeded("const x = 1"));
        assertEquals("if (x) foo()", W3cDriver.prefixReturnIfNeeded("if (x) foo()"));
        assertEquals("throw new Error()", W3cDriver.prefixReturnIfNeeded("throw new Error()"));
    }

    @Test
    void testPrefixReturnMultiStatementLeftAlone() {
        // Regression for https://github.com/karatelabs/karate/issues/2803
        // Prefixing `return` to the first statement makes every statement after the
        // first `;` dead code. Multi-statement scripts must pass through untouched so
        // W3C executeScript runs the whole function body.
        String multi = "window.a = 1; window.b = 2";
        assertEquals(multi, W3cDriver.prefixReturnIfNeeded(multi));

        String trailing = "a = 1; b = 2; c = 3";
        assertEquals(trailing, W3cDriver.prefixReturnIfNeeded(trailing));
    }

    @Test
    void testPrefixReturnTrailingSemicolonIsSingleStatement() {
        // A single expression with just a trailing `;` (or trailing whitespace after)
        // is still one statement — `return` is safe and useful. The trailing `;`
        // itself is preserved; `return foo();` is valid JS.
        assertEquals("return foo();", W3cDriver.prefixReturnIfNeeded("foo();"));
        assertEquals("return foo();  ", W3cDriver.prefixReturnIfNeeded("foo();  "));
    }

    @Test
    void testPrefixReturnSemicolonInsideStringLiteral() {
        // `;` inside a string literal must not be mistaken for a statement separator.
        // foo('a;b') is still a single call whose value we want.
        assertEquals("return foo('a;b')", W3cDriver.prefixReturnIfNeeded("foo('a;b')"));
        assertEquals("return bar(\"x;y\")", W3cDriver.prefixReturnIfNeeded("bar(\"x;y\")"));
        assertEquals("return baz(`t;m`)", W3cDriver.prefixReturnIfNeeded("baz(`t;m`)"));
    }

    // ========== indexOfTopLevelSemicolon ==========

    @Test
    void testIndexOfTopLevelSemicolonPlain() {
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon("foo()"));
        assertEquals(5, W3cDriver.indexOfTopLevelSemicolon("a = 1; b = 2"));
        assertEquals(0, W3cDriver.indexOfTopLevelSemicolon(";"));
    }

    @Test
    void testIndexOfTopLevelSemicolonSkipsStrings() {
        // ; inside 'single' / "double" / `backtick` quotes is skipped
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon("foo('a;b')"));
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon("foo(\"a;b\")"));
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon("foo(`a;b`)"));
        // First top-level ; is returned even if later ; hide inside strings
        // "a = 1;foo('x;y')" — the first ; sits at index 5
        assertEquals(5, W3cDriver.indexOfTopLevelSemicolon("a = 1;foo('x;y')"));
    }

    @Test
    void testIndexOfTopLevelSemicolonHandlesEscapes() {
        // An escaped quote doesn't close the string, so ; inside stays hidden
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon("foo('a\\';b')"));
    }

    @Test
    void testIndexOfTopLevelSemicolonSkipsBracedBodies() {
        // Karate's internal helpers generate IIFEs whose body uses `;`. These
        // are single top-level expressions, not multi-statement scripts, so the
        // scanner must ignore ; inside () {} [] groups.
        String iife = "(function(){ var e = document.querySelector('#x'); return fun(e) })()";
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon(iife));

        String objLiteral = "({ a: 1, b: (function(){ var x; return x })() })";
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon(objLiteral));

        String callWithCallback = "waitFor(fn, function(){ var i = 0; return i })";
        assertEquals(-1, W3cDriver.indexOfTopLevelSemicolon(callWithCallback));
    }

    @Test
    void testPrefixReturnIifeGetsReturnPrefix() {
        // Regression: an IIFE is a value-producing expression, so it must get
        // `return` prefixed so W3C executeScript returns the value. Before the
        // brace-depth fix, the `;` inside the function body looked top-level and
        // blocked the return prefix — Karate's internal helpers (position(),
        // waitForText(), etc.) then ran but returned null, timing out.
        String iife = "(function(){ var e = document.querySelector('#x'); return e.getBoundingClientRect() })()";
        assertEquals("return " + iife, W3cDriver.prefixReturnIfNeeded(iife));
    }
}

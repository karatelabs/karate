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
package io.karatelabs.parser;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the token text / literal-value memoization in {@link TokenBuffer}. A cache is only
 * correct if it is invisible, so these assert that memoizing changes nothing observable — and
 * that the two things it must NOT cache stay uncached.
 */
class TokenCacheTest {

    private static Object eval(String source) {
        return new Engine().eval(source);
    }

    @Test
    void repeatedGetTextIsStableAndInterned() {
        Token token = JsLexer.getTokens(io.karatelabs.common.Resource.text("hello world")).get(0);
        String first = token.getText();
        assertEquals("hello", first);
        // memoized, so the same instance comes back rather than a fresh substring
        assertSame(first, token.getText());
        assertSame(first, token.getText());
    }

    @Test
    void literalValuesSurviveRepeatedEvaluation() {
        // the loop re-evaluates each literal node 100 times; a bad cache shows up as a wrong total.
        // Integer 350, not 350.0 - Terms.narrow hands back the narrowest type, and memoizing must
        // not disturb that. The same mixed Integer/Long/Double model is what any future unboxed
        // numeric representation has to preserve.
        Object result = eval("""
                var t = 0;
                for (var i = 0; i < 100; i++) { t += 1.5; t += 2; }
                t;
                """);
        assertEquals(350, result);
        assertInstanceOf(Integer.class, result);
    }

    @Test
    void narrowedLiteralTypesAreUnchangedByMemoization() {
        assertInstanceOf(Integer.class, eval("2;"));
        assertInstanceOf(Double.class, eval("1.5;"));
        assertInstanceOf(java.math.BigInteger.class, eval("2n;"));
        assertInstanceOf(Boolean.class, eval("true;"));
        assertInstanceOf(String.class, eval("'x';"));
        // and again after the value has been memoized by a first evaluation
        assertInstanceOf(Integer.class, eval("var t = 0; for (var i = 0; i < 2; i++) { t = 2; } t;"));
    }

    @Test
    void nullSentinelDoesNotBreakNullLiterals() {
        // `null` evaluates to null, which is also the cache's "not computed" marker
        assertNull(eval("var x = null; x;"));
        assertNull(eval("var t = null; for (var i = 0; i < 3; i++) { t = null; } t;"));
        assertEquals("object", eval("typeof null;"));
    }

    @Test
    void numberThatCannotParseIsNotCachedAsNull() {
        // NaN, not null - so it never collides with the sentinel and never re-parses forever
        assertEquals(Double.NaN, eval("var t = 0/0; t;"));
        assertTrue((Boolean) eval("isNaN(0/0);"));
    }

    @Test
    void regexLiteralStaysFreshPerEvaluation() {
        // JsRegex carries mutable lastIndex, so caching it would leak state between evaluations
        assertEquals(List.of(true, true, true), eval("""
                var out = [];
                for (var i = 0; i < 3; i++) { out.push(/a/g.test('a')); }
                out;
                """));
    }

    @Test
    void escapedAndEmptyStringLiteralsRoundTrip() {
        assertEquals("", eval("'';"));
        assertEquals("a\nb", eval("'a\\nb';"));
        assertEquals("it's", eval("\"it's\";"));
        // repeated evaluation of the same string literal node
        assertEquals("xxx", eval("var s = ''; for (var i = 0; i < 3; i++) { s += 'x'; } s;"));
    }

    @Test
    void bigIntAndRadixLiteralsRoundTrip() {
        assertEquals(java.math.BigInteger.valueOf(255), eval("0xFFn;"));
        assertEquals(255, eval("0xFF;"));
        assertEquals(10, eval("0b1010;"));
        assertEquals(1000000, eval("1_000_000;"));
    }

    @Test
    void oneSharedAstEvaluatedFromManyThreads() throws Exception {
        // the exact pattern the cache's unsynchronized publication is justified by: karate parses
        // karate-config.js once per suite and every scenario evaluates that one AST concurrently
        String source = """
                function calc() {
                    var t = 0;
                    for (var i = 0; i < 50; i++) { t += i * 1.5 + 2; }
                    return t + 'x' + true + null;
                }
                calc();
                """;
        Node ast = new JsParser(io.karatelabs.common.Resource.text(source)).parse();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<Object> results = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 200; i++) {
                            results.add(new Engine().eval(ast));
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "threads did not finish");
        } finally {
            pool.shutdownNow();
        }
        assertNull(failure.get(), () -> "concurrent evaluation threw: " + failure.get());
        assertEquals(1, results.size(), () -> "threads disagreed: " + results);
    }

    @Test
    void emptyTokenDoesNotAccumulateState() {
        // Token.EMPTY shares one static buffer across the whole JVM - make sure it stays inert
        assertEquals("", Token.EMPTY.getText());
        assertSame(Token.EMPTY.getText(), Token.EMPTY.getText());
        assertEquals(Collections.emptyList(), Token.EMPTY.getComments());
    }
}

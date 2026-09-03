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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slot-frame conformance: the analyzer engages on the shapes it must speed up,
 * declines the shapes it must not touch, and — the load-bearing part — the
 * flag changes nothing observable: results and exact {@link BindEvent}
 * sequences are identical with {@code SlotTable.ENABLED} on and off.
 */
class SlotFrameTest {

    static Object evalFlag(String src, boolean enabled) {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = enabled;
        try {
            return new Engine().eval(src);
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    /** Evaluate with the flag on and off; assert identical results; return the flag-on result. */
    static Object evalBoth(String src) {
        Object on = evalFlag(src, true);
        Object off = evalFlag(src, false);
        assertEquals(off, on, "flag-on result differs from flag-off for:\n" + src);
        return on;
    }

    static JsFunctionNode fnOf(String src, String fnRef) {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = true;
        try {
            Engine engine = new Engine();
            engine.eval(src);
            Object fn = engine.eval(fnRef);
            if (fn instanceof JsFunctionWrapper wrapper) {
                try {
                    java.lang.reflect.Field f = JsFunctionWrapper.class.getDeclaredField("delegate");
                    f.setAccessible(true);
                    fn = f.get(wrapper);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
            return (JsFunctionNode) fn;
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    static SlotTable tableOf(String src, String fnRef) {
        return fnOf(src, fnRef).slotTable;
    }

    static int countAnnotated(io.karatelabs.parser.Node node) {
        int c = node.slot >= 0 ? 1 : 0;
        for (int i = 0, n = node.size(); i < n; i++) {
            c += countAnnotated(node.get(i));
        }
        return c;
    }

    @Test
    void annotationsAreActuallyApplied() {
        // the fast paths are invisible to correctness tests (name routing keeps
        // results right even with zero annotations) — pin that analysis stamps
        // slots on identifier occurrences, or the whole speedup silently dies
        JsFunctionNode loop = fnOf("function run() { var result = 0; for (var i = 0; i < 10; i++) { result += i; } return result; }", "run");
        assertNotNull(loop.slotTable);
        assertTrue(countAnnotated(loop.body) >= 5,
                "expected >=5 annotated identifier occurrences, got " + countAnnotated(loop.body));
        JsFunctionNode mixed = fnOf("function p(items) { let r = 0; for (let i = 0; i < items.length; i++) { let item = items[i]; r += item; } return r; }", "p");
        assertNotNull(mixed.slotTable);
        assertTrue(countAnnotated(mixed.body) >= 7,
                "expected >=7 annotated occurrences incl. scoped lets, got " + countAnnotated(mixed.body));
    }

    //=== analyzer engagement ==========================================================================================

    @Test
    void analyzerEngagesOnLoopFunction() {
        SlotTable t = tableOf("function run() { var result = 0; for (var i = 0; i < 10; i++) { result += i; } return result; }", "run");
        assertNotNull(t, "analyzer must engage on the E1-fn shape");
        assertTrue(t.indexOf("result") >= 0);
        assertTrue(t.indexOf("i") >= 0);
    }

    @Test
    void analyzerEngagesOnRecursion() {
        SlotTable t = tableOf("function fib(n) { if (n <= 1) return n; return fib(n - 1) + fib(n - 2); } fib(10);", "fib");
        assertNotNull(t);
        assertTrue(t.indexOf("n") >= 0);
        assertEquals(-1, t.indexOf("fib"), "the function's own name is a free name, not a local");
    }

    @Test
    void capturedNamesStayInStore() {
        SlotTable t = tableOf("function outer() { var a = 1; var b = 2; var f = function () { return a; }; return f() + b; } outer(); outer();", "outer");
        assertNotNull(t);
        assertEquals(-1, t.indexOf("a"), "captured by nested function");
        assertTrue(t.indexOf("b") >= 0, "not captured");
    }

    @Test
    void arrowCapturesForceStore() {
        SlotTable t = tableOf("function outer() { var a = 5; var unused = 1; return [1].map(x => x + a)[0]; } outer(); outer();", "outer");
        assertNotNull(t);
        assertEquals(-1, t.indexOf("a"), "captured by arrow");
        assertTrue(t.indexOf("unused") >= 0);
    }

    @Test
    void analyzerDeclinesUnsupportedShapes() {
        assertNull(tableOf("function f(...rest) { var x = 1; return x; } f(); f();", "f"), "rest param bails");
        assertNull(tableOf("function f([a, b]) { var x = 1; return x; } f([1, 2]); f([1, 2]);", "f"), "destructuring param bails");
    }

    @Test
    void letConstClassificationAndCatchStaysInStore() {
        SlotTable t = tableOf("function f() { var v = 1; let l = 2; const c = 3; try { v = l; } catch (e) { v = c; } return v; } f(); f();", "f");
        assertNotNull(t);
        assertTrue(t.indexOf("v") >= 0);
        // body-top-level let/const are function-scoped frame names in stage B
        assertTrue(t.indexOf("l") >= 0);
        assertTrue(t.indexOf("c") >= 0);
        assertEquals(-1, t.indexOf("e"), "catch param stays in the store");
    }

    @Test
    void scopedLetsAreSlottedButNotNameRouted() {
        SlotTable t = tableOf("function f(a) { let total = 0; for (let i = 0; i < a.length; i++) { let item = a[i]; total += item; } return total; }", "f");
        assertNotNull(t);
        assertTrue(t.indexOf("total") >= 0, "body-top let is name-routed");
        assertEquals(-1, t.indexOf("i"), "loop let is scope-confined: no name routing");
        assertEquals(-1, t.indexOf("item"), "block let is scope-confined: no name routing");
        assertEquals(3 + 1, t.size(), "a, total, i, item all have slots");
    }

    @Test
    void shadowingForcesStore() {
        SlotTable t = tableOf("function f() { var x = 1; { let x = 2; } return x; } f(); f();", "f");
        // both declarations of x drop to the store — shadowing stays dynamic
        if (t != null) {
            assertEquals(-1, t.indexOf("x"));
        }
        assertEquals(1, evalBoth("function f() { var x = 1; { let x = 2; } return x; } f()"));
        assertEquals(21, evalBoth("function f() { var x = 1; var r; { let x = 2; r = x; } return r * 10 + x; } f()"));
    }

    @Test
    void mixedWorkloadShape() {
        // the published Mixed row's processData, verbatim shape
        Object r = evalBoth("""
                function processData(items) {
                    let result = { sum: 0, count: 0, values: [] };
                    for (let i = 0; i < items.length; i++) {
                        let item = items[i];
                        if (item.active) {
                            result.sum += item.value;
                            result.count++;
                            result.values.push(item.value * 2);
                        }
                    }
                    result.average = result.count > 0 ? result.sum / result.count : 0;
                    return result;
                }
                let data = [];
                for (let i = 0; i < 20; i++) {
                    data.push({ id: i, value: i * 10, active: i % 3 !== 0 });
                }
                let output = processData(data);
                output.sum + output.average;
                """);
        assertInstanceOf(Number.class, r);
    }

    @Test
    void tdzAndConstSemantics() {
        // uninitialized let: read throws until first assignment
        assertEquals("caught", evalBoth("function f() { let x; try { return x + 1; } catch (e) { } return 'caught'; } f()"));
        assertEquals(5, evalBoth("function f() { let x; x = 5; return x; } f()"));
        // const reassignment throws; catch proves it's the typeError path
        assertEquals("caught", evalBoth("function f() { const c = 1; try { c = 2; } catch (e) { return 'caught'; } return 'no'; } f()"));
        // loop-collapsed const still throws on increment
        assertEquals("caught", evalBoth("function f() { try { for (const i = 0; i < 3; i++) { } } catch (e) { return 'caught'; } return 'no'; } f()"));
    }

    @Test
    void blockLetRearmsPerIteration() {
        // reading the block let before its declaration line must resolve
        // outward (the engine declares dynamically), every iteration — the
        // slot must not leak the previous iteration's value
        assertEquals("ggg", evalBoth(
                "var x = 'g'; function f() { var acc = ''; for (var i = 0; i < 3; i++) { acc += x; let x = 'L'; } return acc; } f()"));
        // and the loop-collapse must not change what the loop computes
        assertEquals(10, evalBoth("function f(n) { let t = 0; for (let i = 0; i < n; i++) { t += i; } return t; } f(5)"));
        assertEquals(3, evalBoth("function f() { for (let i = 0; i < 99; i++) { if (i >= 3) return i; } } f()"));
    }

    @Test
    void scopedLetPlainAssignmentWritesTheSlot() {
        // a plain `=` to a let confined to a loop body, a nested block or a
        // for-init has only the stamped slot to reach it — the name-keyed
        // update path never held it, and would fall through to an outer
        // binding or an implicit global (compound `+=` always wrote the slot)
        assertEquals("zz", evalBoth("function f() { let acc = ''; let n = 0; while (n < 2) { let s = 'a'; s = 'z'; acc += s; n++; } return acc; } f()"));
        assertEquals("zz", evalBoth("function f() { let acc = ''; let n = 0; while (n < 2) { let s = 'a'; if (true) { s = 'z'; } acc += s; n++; } return acc; } f()"));
        assertEquals("z0z1", evalBoth("function f() { let acc = ''; for (let i = 0; i < 2; i++) { let s = 'a'; s = 'z'; acc += s + i; } return acc; } f()"));
        // loop-free body: the slot table is built on the second call, and the
        // write must land the same way after it exists
        assertEquals("zzz", evalBoth("function f() { if (true) { let s = 'a'; s = 'z'; return s; } } f() + f() + f()"));
        // the missed write must not leak: no implicit global, no clobbered outer binding
        assertEquals("g", evalBoth("var s = 'g'; function f() { let n = 0; while (n < 1) { let s = 'a'; s = 'z'; n++; } return s; } f()"));
        assertEquals("undefined", evalBoth("function f() { let n = 0; while (n < 1) { let s = 'a'; s = 'z'; n++; } return typeof s; } f()"));
    }

    //=== behavior parity (flag on == flag off) ========================================================================

    @Test
    void hotShapesComputeCorrectly() {
        assertEquals(55, evalBoth("function fib(n) { if (n <= 1) return n; return fib(n - 1) + fib(n - 2); } fib(10)"));
        Object r = evalBoth("function run() { var result = 0; for (var i = 0; i < 400; i++) { result += i * 2 + i / 2 - i % 7; result = result * 1.01; } return result; } run()");
        assertInstanceOf(Number.class, r);
    }

    @Test
    void dynamicVarSemanticsPreserved() {
        // this engine declares var at its statement (no hoisting): a read
        // before the var line resolves the outer/global binding
        assertEquals(42, evalBoth("var x = 42; function f() { var y = x; var x = 1; return y; } f()"));
        // pre-declaration write goes to the outer binding, as today
        assertEquals(32, evalBoth("var g = 1; function f() { g = 2; var g = 3; return g; } f() * 10 + g"));
    }

    @Test
    void paramDefaultsAndArguments() {
        assertEquals(6, evalBoth("function f(a, b = a + 1) { return b; } f(5)"));
        assertEquals(2, evalBoth("function f(a) { return arguments[1]; } f(1, 2)"));
        assertEquals(7, evalBoth("function f(a, b) { return a + (b === undefined ? 6 : b); } f(1)"));
    }

    @Test
    void nameInferenceOnSlotWrites() {
        assertEquals("g", evalBoth("function f() { var g = function () {}; return g.name; } f()"));
        assertEquals("p", evalBoth("function f(p) { return p.name; } f(function () {})"));
        assertEquals("a", evalBoth("function f() { var a; a = function () {}; return a.name; } f()"));
    }

    @Test
    void closureCellsShared() {
        assertEquals(2, evalBoth(
                "function counter() { var n = 0; return { inc: function () { n++; return n; } }; } var c = counter(); c.inc(); c.inc()"));
    }

    @Test
    void loopAndCompoundOperators() {
        assertEquals(6, evalBoth("function f(a) { var t = 0; for (var v of a) t += v; return t; } f([1, 2, 3])"));
        assertEquals(3, evalBoth("function f(p) { var q = p; q ||= 3; return q; } f(null)"));
        assertEquals(123, evalBoth("function f() { var i = 1; var a = i++; var b = i; return a * 100 + b * 10 + ++i; } f()"));
    }

    @Test
    void storeAndSlotInterop() {
        assertEquals(3, evalBoth("function f(p) { var [a, b] = p; return a + b; } f([1, 2])"));
        assertEquals("undefined", evalBoth("function f() { return typeof zzz; } f()"));
        Object caught = evalBoth("function f() { var r = 0; try { throw 5; } catch (e) { r = e; } return r; } f()");
        assertEquals(5, caught);
    }

    @Test
    void hoistedFunctionDeclarations() {
        assertEquals(9, evalBoth("function f() { var r = g(); function g() { return 9; } return r; } f()"));
    }

    //=== BindEvent conformance ========================================================================================

    static List<String> bindEvents(String src, boolean enabled) {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = enabled;
        try {
            List<String> events = new ArrayList<>();
            Engine engine = new Engine();
            engine.setListener(new ContextListener() {
                @Override
                public void onBind(BindEvent event) {
                    events.add(event.type + ":" + event.name + "=" + event.value
                            + " (old=" + event.oldValue + ", scope=" + event.scope + ")");
                }
            });
            engine.eval(src);
            return events;
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    static void assertSameBindEvents(String src) {
        List<String> off = bindEvents(src, false);
        List<String> on = bindEvents(src, true);
        assertEquals(off, on, "BindEvent sequences differ for:\n" + src);
        assertFalse(off.isEmpty(), "conformance script produced no events — not probative:\n" + src);
    }

    @Test
    void bindEventSequencesIdentical() {
        assertSameBindEvents("function run() { var result = 0; for (var i = 0; i < 5; i++) { result += i; result = result * 2; } return result; } run()");
        assertSameBindEvents("function f(a, b) { var c = a + b; c++; c ||= 9; return c; } f(1, 2)");
        assertSameBindEvents("function outer() { var a = 1; var f = function () { a = 2; return a; }; f(); return a; } outer()");
        assertSameBindEvents("function f() { var r = 0; try { throw 3; } catch (e) { r = e; } return r; } f()");
        assertSameBindEvents("function f(p) { var [a, b] = p; for (var v of p) { a += v; } return a + b; } f([1, 2])");
    }

}

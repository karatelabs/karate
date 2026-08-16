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

import io.karatelabs.parser.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Program-scope slot-frame conformance — the top-level counterpart of
 * {@link SlotFrameTest}. Only the scope-confined class exists at program
 * scope: every direct program-body declaration stays in the store (the
 * {@code Engine.bindings} host contract), while let/const confined to nested
 * blocks and C-style for-inits get frame slots. As in the function lane, the
 * load-bearing assertions are flag parity: results, host-visible bindings and
 * {@link BindEvent} sequences must be identical with {@code SlotTable.ENABLED}
 * on and off.
 */
class ProgramFrameTest {

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

    static SlotTable tableOf(String src) {
        return SlotTable.forProgram(Engine.parse(src));
    }

    static int countAnnotated(Node node) {
        int c = node.slot >= 0 ? 1 : 0;
        for (int i = 0, n = node.size(); i < n; i++) {
            c += countAnnotated(node.get(i));
        }
        return c;
    }

    //=== analyzer engagement ==========================================================================================

    @Test
    void engagesOnLoopyTopLevelAndSlotsOnlyConfinedNames() {
        SlotTable t = tableOf("let total = 0; for (let i = 0; i < 10; i++) { let d = i * 2; total += d; } total;");
        assertNotNull(t, "must engage on the arithmetic-row shape");
        assertEquals(2, t.size(), "i and d have slots; total is the store's");
        // byName stays empty at program scope: no name-keyed path may route
        // to the frame — that map is the Engine.bindings host contract
        assertEquals(-1, t.indexOf("i"));
        assertEquals(-1, t.indexOf("d"));
        assertEquals(-1, t.indexOf("total"));
    }

    @Test
    void annotationsAreActuallyApplied() {
        Node program = Engine.parse("let s = ''; for (let i = 0; i < 50; i++) { s += 'item' + i + ','; } s.split(',').length;");
        SlotTable t = SlotTable.forProgram(program);
        assertNotNull(t);
        // i is read in the test, the increment and the body — the speedup
        // dies silently if annotation stops stamping occurrences
        assertTrue(countAnnotated(program) >= 3,
                "expected >=3 annotated occurrences of the loop var, got " + countAnnotated(program));
    }

    @Test
    void declinesLoopFreeAndUnconfinedShapes() {
        assertNull(tableOf("let a = 1; let b = a + 2; b;"), "loop-free top level never pays analysis");
        assertNull(tableOf("(function() { for (var i = 0; i < 3; i++) { } })();"),
                "a loop inside a function belongs to that function's analysis");
        assertNull(tableOf("var t = 0; for (t = 0; t < 5; t++) { } t;"),
                "nothing confined — top-level var loop counter is the store's");
    }

    @Test
    void capturedAndShadowedConfinedNamesStayInStore() {
        // captured by a nested function: per-iteration environment semantics
        // must keep running through the store
        SlotTable captured = tableOf("var fns = []; for (let i = 0; i < 3; i++) { fns.push(function () { return i; }); } fns.length;");
        assertNull(captured, "the only confined name is captured — no frame at all");
        // name collision with a top-level var: shadowing stays dynamic
        SlotTable shadowed = tableOf("var x = 1; var r = 0; for (let q = 0; q < 3; q++) { let x = 2; r += x; } r;");
        assertNotNull(shadowed);
        assertEquals(1, shadowed.size(), "only q is slotted; both x declarations drop to the store");
    }

    //=== behavior parity (flag on == flag off) ========================================================================

    @Test
    void benchmarkRowShapesComputeCorrectly() {
        Object arith = evalBoth("let result = 0; for (let i = 0; i < 100; i++) { result += i * 2 + i / 2 - i % 7; result = result * 1.01; } result;");
        assertInstanceOf(Number.class, arith);
        assertEquals(51, evalBoth("let s = ''; for (let i = 0; i < 50; i++) { s += 'item' + i + ','; } s.split(',').length;"));
        assertEquals(10, evalBoth("let t = 0; for (let i = 0; i < 5; i++) { t += i; } t;"));
        assertEquals(3, evalBoth("for (let i = 0; i < 99; i++) { if (i >= 3) break; } 3;"));
    }

    @Test
    void closuresOverLoopVarCapturePerIteration() {
        assertEquals(3, evalBoth("var fns = []; for (let i = 0; i < 3; i++) { fns.push(function () { return i; }); } fns[0]() + fns[1]() + fns[2]();"));
    }

    @Test
    void blockLetRearmsPerIteration() {
        // pre-declaration reads must see UNDECLARED again on every iteration,
        // not the previous iteration's value left in the slot
        assertEquals("undefined,undefined,undefined,", evalBoth(
                "var acc = ''; for (var i = 0; i < 3; i++) { acc += (typeof x) + ','; let x = 'L'; } acc;"));
        // and a pre-declaration read of a same-named OUTER binding resolves it
        assertEquals("ggg", evalBoth(
                "var x = 'g'; var acc = ''; for (var i = 0; i < 3; i++) { acc += x; let x = 'L'; } acc;"));
    }

    @Test
    void tdzAndConstSemantics() {
        assertEquals("caught", evalBoth("var r; { let x; try { r = x + 1; } catch (e) { r = 'caught'; } } r;"));
        assertEquals(5, evalBoth("var r; { let x; x = 5; r = x; } r;"));
        assertEquals("caught", evalBoth("var r = 'no'; { const c = 1; try { c = 2; } catch (e) { r = 'caught'; } } r;"));
        assertEquals("caught", evalBoth("var r = 'no'; try { for (const i = 0; i < 3; i++) { } } catch (e) { r = 'caught'; } r;"));
    }

    @Test
    void labelledBreakAndContinue() {
        assertEquals(6, evalBoth("var t = 0; outer: for (let i = 0; i < 3; i++) { for (let j = 0; j < 3; j++) { if (j > i) continue outer; t += 1; } } t;"));
        assertEquals(4, evalBoth("var t = 0; outer: for (let i = 0; i < 9; i++) { for (let j = 0; j < 9; j++) { t += 1; if (t >= 4) break outer; } } t;"));
    }

    @Test
    void confinedNamesInvisibleAfterTheirBlock() {
        assertEquals("undefined", evalBoth("for (let i = 0; i < 3; i++) { } typeof i;"));
        assertEquals("undefined", evalBoth("{ let q = 1; while (q < 2) { q++; } } typeof q;"));
    }

    //=== host contract ================================================================================================

    @Test
    void engineBindingsNeverSeeConfinedNames() {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = true;
        try {
            Engine engine = new Engine();
            engine.eval("let top = 7; for (let i = 0; i < 3; i++) { let inner = i; top += inner; }");
            assertTrue(engine.getBindings().containsKey("top"), "top-level let stays host-visible");
            assertFalse(engine.getBindings().containsKey("i"));
            assertFalse(engine.getBindings().containsKey("inner"));
            // cross-eval: the confined name is gone; a fresh loop re-declares freely
            assertEquals("undefined", engine.eval("typeof i"));
            assertEquals(3, engine.eval("var n = 0; for (let i = 0; i < 3; i++) { n++; } n;"));
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    @Test
    void evalWithIsolationUnchanged() {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = true;
        try {
            Engine engine = new Engine();
            Map<String, Object> vars = new HashMap<>();
            Object r = engine.evalWith("let sum = 0; for (let i = 0; i < 4; i++) { sum += i; } sum;", vars);
            assertEquals(6, r);
            assertFalse(engine.getBindings().containsKey("sum"), "evalWith keeps declarations out of engine bindings");
            assertFalse(engine.getBindings().containsKey("i"));
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    @Test
    void sharedAstAcrossEnginesGetsFreshFrames() {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = true;
        try {
            Node program = Engine.parse("let t = 0; for (let i = 0; i < 5; i++) { t += i; } t;");
            assertEquals(10, new Engine().eval(program));
            assertEquals(10, new Engine().eval(program), "cached analysis, fresh per-eval frame");
        } finally {
            SlotTable.ENABLED = prev;
        }
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
        assertSameBindEvents("let total = 0; for (let i = 0; i < 3; i++) { total += i; } total;");
        assertSameBindEvents("var acc = ''; for (var i = 0; i < 2; i++) { let piece = 'p' + i; acc += piece; } acc;");
        assertSameBindEvents("var r = 0; { let x = 1; x = 2; r = x; } r;");
    }

}

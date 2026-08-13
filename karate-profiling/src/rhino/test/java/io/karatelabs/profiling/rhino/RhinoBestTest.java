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
package io.karatelabs.profiling.rhino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rhino-best adapter's timed-lifecycle contract (PROFILING.md §9, R1): the version
 * pin actually fires, every eval gets a genuinely fresh scope, and each eval reflects its own
 * source — the properties a favorable adapter would be most tempted to amortize away.
 */
class RhinoBestTest {

    @Test
    void versionPinAcceptsThePinnedRuntimeAndNamesItInDescribe() {
        RhinoBest rhino = new RhinoBest();
        assertTrue(rhino.describe().contains(RhinoBest.PINNED_VERSION), rhino.describe());
    }

    @Test
    void everyEvalGetsAFreshScope() {
        RhinoBest rhino = new RhinoBest();
        // The first eval writes an implicit global; on a reused scope the second eval would
        // see it. A fresh child scope prototyped off the sealed root must not.
        assertEquals(42.0, ((Number) rhino.eval("leak = 42; leak;")).doubleValue());
        assertEquals("undefined", rhino.eval("typeof leak;"));
    }

    @Test
    void eachEvalReflectsItsOwnSource() {
        // A stale compiled-script cache keyed on anything short of the full source would
        // return an earlier value here — this is the family's unique-suffix discipline in
        // miniature, with the varying part inside the program rather than a comment.
        RhinoBest rhino = new RhinoBest();
        for (int i = 0; i < 5; i++) {
            assertEquals((double) i, ((Number) rhino.eval("var n = " + i + "; n;")).doubleValue());
            assertEquals((double) i,
                    ((Number) rhino.eval("var n = " + i + "; n; //suffix" + i)).doubleValue());
        }
    }

    @Test
    void theSealedRootIsNotWritableThroughAnEval() {
        RhinoBest rhino = new RhinoBest();
        // Assigning onto a standard object that lives on the sealed root must not survive
        // into a later eval — whether Rhino throws or the write lands on a shadow, the next
        // fresh scope has to see the standard library untouched.
        try {
            rhino.eval("Math.polluted = 1;");
        } catch (RuntimeException e) {
            // a sealed-object violation is an acceptable outcome
        }
        assertEquals("undefined", rhino.eval("typeof Math.polluted;"));
    }

}

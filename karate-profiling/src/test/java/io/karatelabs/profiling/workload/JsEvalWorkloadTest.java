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
package io.karatelabs.profiling.workload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two things the js family's numbers depend on and nothing else can check:
 *
 * <ul>
 *   <li><b>The scripts are the published benchmark's scripts.</b> Each source's length is
 *       pinned to the {@code chars} column of the published CSV — an edit that changed a
 *       script would silently rename what the row measures while keeping its name.</li>
 *   <li><b>The oracles are right for the engine on this classpath.</b> Each workload's
 *       {@code iterate} runs a real eval and compares against the Java-recomputed value, so
 *       an engine change that alters a result fails here before it fails on a bench.</li>
 * </ul>
 */
class JsEvalWorkloadTest {

    private static final List<JsEvalWorkload> ALL = List.of(
            new JsEvalWorkload.Arithmetic(), new JsEvalWorkload.Strings(),
            new JsEvalWorkload.Objects(), new JsEvalWorkload.Functions(),
            new JsEvalWorkload.Mixed(), new JsEvalWorkload.Large1k());

    @Test
    void everyWorkloadOraclePassesAgainstTheRealEngine() {
        for (JsEvalWorkload workload : ALL) {
            workload.setup(null);
            // Two iterations, so the uniqueness suffix path (a different source text every
            // time) is exercised, not just the raw script.
            workload.iterate(0, 0);
            workload.iterate(0, 1);
        }
    }

    /** The published CSV's `chars` column, row for row. */
    @Test
    void scriptsAreTheBenchmarksScripts() {
        assertEquals(123, JsEvalWorkload.JS_ARITHMETIC.length());
        assertEquals(93, JsEvalWorkload.JS_STRINGS.length());
        assertEquals(329, JsEvalWorkload.JS_OBJECTS.length());
        assertEquals(247, JsEvalWorkload.JS_FUNCTIONS.length());
        assertEquals(576, JsEvalWorkload.JS_MIXED.length());
        String large = JsEvalWorkload.Large1k.generate(JsEvalWorkload.Large1k.TARGET_BYTES);
        assertEquals(1133, large.length());
        // Content, not just length — a same-length edit to the generator would silently
        // change what the guard row measures while keeping its name. String.hashCode is
        // specified, so this constant is stable across JVMs.
        assertEquals(1721604224, large.hashCode());
    }

    @Test
    void oracleMismatchFailsLoudlyOnceThenCheaply() {
        JsEvalWorkload broken = new JsEvalWorkload() {
            @Override
            public String name() {
                return "js-broken-for-test";
            }

            @Override
            public String describe() {
                return "test fixture";
            }

            @Override
            protected String script() {
                return "1 + 1;";
            }

            @Override
            protected double expected() {
                return 3; // deliberately wrong
            }

            @Override
            protected long defaultIterations() {
                return 1;
            }
        };
        IllegalStateException first = assertThrows(IllegalStateException.class,
                () -> broken.iterate(0, 0));
        assertTrue(first.getMessage().contains("oracle mismatch"), first.getMessage());
        // Every later iteration must fail fast and stackless — a broken run should burn its
        // remaining budget in milliseconds, not spend minutes formatting identical messages.
        RuntimeException later = assertThrows(RuntimeException.class, () -> broken.iterate(0, 1));
        assertEquals(0, later.getStackTrace().length);
    }

    @Test
    void singleThreadedShapeAndNoMock() {
        for (JsEvalWorkload workload : ALL) {
            assertEquals(1, workload.shape().threads(), workload.name());
            assertTrue(workload.shape().iterations() > 0, workload.name());
            assertEquals(false, workload.needsMock(), workload.name());
        }
    }

}

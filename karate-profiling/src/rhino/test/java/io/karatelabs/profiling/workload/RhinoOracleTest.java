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

import io.karatelabs.profiling.rhino.RhinoBest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R1 gate that both engines compute the same thing: every row's script, evaluated by the
 * rhino-best adapter, must match the same Java-recomputed oracle the karate arm is checked
 * against — with the unique-suffix discipline applied, since that is how the workload actually
 * evaluates. Same package as the workloads on purpose: the scripts and oracles are reused,
 * not restated, so this test cannot drift from what the rows measure.
 */
class RhinoOracleTest {

    private static final JsEvalWorkload[] ROWS = {
            new JsEvalWorkload.Arithmetic(), new JsEvalWorkload.Strings(),
            new JsEvalWorkload.Objects(), new JsEvalWorkload.Functions(),
            new JsEvalWorkload.Mixed(), new JsEvalWorkload.Large1k()};

    @AfterEach
    void clearEngineProperty() {
        System.clearProperty(JsEvaluator.PROPERTY);
    }

    @Test
    void allSixRowsAreOracleGreenUnderRhinoBest() {
        RhinoBest rhino = new RhinoBest();
        for (JsEvalWorkload row : ROWS) {
            Object result = rhino.eval(row.script() + "\n//0");
            assertTrue(result instanceof Number
                            && ((Number) result).doubleValue() == row.expected(),
                    row.name() + ": expected " + row.expected() + ", got " + result);
        }
    }

    @Test
    void theWorkloadResolvesTheRhinoAdapterThroughTheEngineProperty() {
        System.setProperty(JsEvaluator.PROPERTY, "rhino-best");
        JsEvalWorkload row = new JsEvalWorkload.Arithmetic();
        row.setup(null);
        // Two iterations through the real workload path: unique suffixes, oracle checks.
        row.iterate(0, 0);
        row.iterate(0, 1);
    }

    @Test
    void anUnknownEngineNameFailsAtSetupWithTheSupportedList() {
        System.setProperty(JsEvaluator.PROPERTY, "nashorn");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new JsEvalWorkload.Arithmetic().setup(null));
        assertTrue(e.getMessage().contains("karate, rhino-best"), e.getMessage());
    }

}

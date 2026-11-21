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
package io.karatelabs.junit6;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When Runner.Builder.parallel(...) throws before any scenario runs, the stream must not end
 * as if the suite had simply found no features: that reports "0 tests, 0 failures" and a green
 * build while nothing ran. The failure has to surface as a failing test.
 */
class SuiteSetupFailureTest {

    @Test
    void suiteSetupFailureSurfacesAsFailingTest() {
        for (boolean hierarchical : new boolean[]{true, false}) {
            List<DynamicNode> nodes = Karate.run("classpath:io/karatelabs/junit6/does-not-exist.feature")
                    .hierarchical(hierarchical)
                    .stream()
                    .toList();
            assertEquals(1, nodes.size(), "hierarchical=" + hierarchical + " nodes: " + nodes);
            DynamicTest test = assertInstanceOf(DynamicTest.class, nodes.get(0));
            assertTrue(test.getDisplayName().startsWith("Karate suite failed"), test.getDisplayName());
            Throwable thrown = assertThrows(Throwable.class, () -> test.getExecutable().execute());
            assertTrue(String.valueOf(thrown.getMessage()).contains("does-not-exist.feature"),
                    "cause should name the path: " + thrown);
        }
    }

}

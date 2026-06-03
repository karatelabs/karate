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

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces a defect where a caller feature whose scenarios each invoke
 * {@code call read('other.feature@tag')} produced the wrong set of JUnit tests:
 * the called feature's FeatureStart event leaked into the bridge and wiped the
 * caller's collected scenarios. See FeatureRuntime listener notifications, which
 * must only fire for top-level (caller == null) features.
 */
class CallingFeatureTest {

    private static List<DynamicTest> collectLeaves(Iterable<? extends DynamicNode> nodes) {
        List<DynamicTest> leaves = new ArrayList<>();
        for (DynamicNode node : nodes) {
            if (node instanceof DynamicTest test) {
                leaves.add(test);
            } else if (node instanceof DynamicContainer container) {
                leaves.addAll(collectLeaves(container.getChildren().toList()));
            }
        }
        return leaves;
    }

    private static final String F1 = "classpath:io/karatelabs/junit6/calling/f1.feature";
    private static final String F2 = "classpath:io/karatelabs/junit6/calling/f2.feature";

    @Test
    void testHierarchicalKeepsAllCallerScenarios() {
        List<DynamicNode> nodes = new ArrayList<>();
        Karate.run(F1).hierarchical(true).iterator().forEachRemaining(nodes::add);
        List<DynamicTest> leaves = collectLeaves(nodes);
        List<String> names = leaves.stream().map(DynamicTest::getDisplayName).toList();
        assertEquals(2, leaves.size(), "expected both caller scenarios, got: " + names);
        assertTrue(names.stream().anyMatch(n -> n.contains("Scenario 1")), "missing Scenario 1, got: " + names);
        assertTrue(names.stream().anyMatch(n -> n.contains("Scenario 2")), "missing Scenario 2, got: " + names);
    }

    @Test
    void testFlatKeepsAllCallerScenarios() {
        List<DynamicNode> nodes = new ArrayList<>();
        Karate.run(F1).hierarchical(false).iterator().forEachRemaining(nodes::add);
        List<DynamicTest> leaves = collectLeaves(nodes);
        assertEquals(2, leaves.size(), "expected both caller scenarios in flat mode");
    }

    @Test
    void testCallerScenarioFailureSurfaces() {
        List<DynamicNode> nodes = new ArrayList<>();
        Karate.run(F2).hierarchical(true).iterator().forEachRemaining(nodes::add);
        List<DynamicTest> leaves = collectLeaves(nodes);
        assertEquals(2, leaves.size(), "expected both caller scenarios");
        List<String> failures = new ArrayList<>();
        for (DynamicTest test : leaves) {
            try {
                test.getExecutable().execute();
            } catch (Throwable t) {
                failures.add(test.getDisplayName() + " -> " + t.getMessage());
            }
        }
        assertEquals(1, failures.size(), "expected exactly one failing scenario, got: " + failures);
        assertTrue(failures.get(0).contains("Scenario 1"), "the failing test should be Scenario 1, got: " + failures);
    }

}

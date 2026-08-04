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
package io.karatelabs.profiling;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.ScenarioResult;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs one feature and prints the <em>shape</em> of the variables it leaves in scope:
 * type, size, and the number of distinct container nodes reachable from each.
 *
 * <p>Written to settle a question a load test cannot answer — how much does a given call
 * form actually hand back? A report of geometric memory growth from chained
 * {@code karate.call()} captures rests entirely on a bare call returning the
 * <em>caller's</em> whole scope, so that capture N contains captures 1..N-1. Whether that
 * is true is a one-run question about object graph shape, not something to infer from a
 * heap curve. Measuring it directly is what showed the nesting does not form here:
 *
 * <pre>
 *   mvn -q compile
 *   java -cp "target/classes:$(cat target/cp.txt)" io.karatelabs.profiling.Probe \
 *        classpath:workload/probe-forms.feature
 * </pre>
 *
 * <p>Node counts use identity, so shared substructure is counted once — which is the
 * point: it distinguishes "returned a reference to the same big map" from "returned a
 * copy of it".
 */
public final class Probe {

    private static final int MAX_DEPTH = 40;

    private Probe() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: Probe <feature-path>");
            System.exit(2);
        }
        FeatureResult result = Runner.runFeature(args[0], null, null, null, Runner.builder());
        for (ScenarioResult scenario : result.getScenarioResults()) {
            if (scenario.isFailed()) {
                System.out.println("SCENARIO FAILED: " + scenario.getErrorWithLocation());
            }
        }
        Map<String, Object> variables = result.getResultVariables();
        if (variables == null) {
            System.out.println("no result variables");
            return;
        }
        System.out.println("variables in scope: " + variables.keySet());
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + describe(entry.getValue())
                    + "   [nodes=" + countNodes(entry.getValue()) + "]");
        }
    }

    private static String describe(Object value) {
        if (value instanceof Map<?, ?> map) {
            return "Map(" + map.size() + ") keys=" + map.keySet();
        }
        if (value instanceof List<?> list) {
            return "List(" + list.size() + ")";
        }
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    /** Distinct container nodes reachable — a proxy for what a deep copy would rebuild. */
    private static int countNodes(Object root) {
        return walk(root, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static int walk(Object node, Set<Object> seen, int depth) {
        if (node == null || depth > MAX_DEPTH || !seen.add(node)) {
            return 0;
        }
        int count = 0;
        if (node instanceof Map<?, ?> map) {
            count = 1;
            for (Object value : map.values()) {
                count += walk(value, seen, depth + 1);
            }
        } else if (node instanceof Collection<?> collection) {
            count = 1;
            for (Object value : collection) {
                count += walk(value, seen, depth + 1);
            }
        }
        return count;
    }

}

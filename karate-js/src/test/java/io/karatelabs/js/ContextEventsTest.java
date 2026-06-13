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
package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link EventType#BRANCH}, {@link EventType#COMPARE} and
 * {@link EventType#PROPERTY_GET} events used by coverage and debugger tooling.
 */
class ContextEventsTest {

    static class EventCollector implements ContextListener {

        final List<Event> branches = new ArrayList<>();
        final List<Event> compares = new ArrayList<>();
        final List<Event> propertyGets = new ArrayList<>();

        @Override
        public void onEvent(Event event) {
            if (event.type == EventType.BRANCH) {
                branches.add(event);
            } else if (event.type == EventType.COMPARE) {
                compares.add(event);
            } else if (event.type == EventType.PROPERTY_GET) {
                propertyGets.add(event);
            }
        }

        // (line, outcome) pairs for readable assertions, lines 1-based
        List<String> branchSummary() {
            List<String> list = new ArrayList<>();
            for (Event e : branches) {
                list.add((e.node.getFirstToken().line + 1) + ":" + e.value);
            }
            return list;
        }

    }

    private static EventCollector run(String script, Object input) {
        Engine engine = new Engine();
        EventCollector collector = new EventCollector();
        engine.setListener(collector);
        engine.put("input", input);
        engine.eval(script);
        return collector;
    }

    @Test
    void testIfElseBranch() {
        String script = "if (input > 5) { 'big' } else { 'small' }";
        EventCollector c = run(script, 10);
        assertEquals(List.of("1:true"), c.branchSummary());
        c = run(script, 3);
        assertEquals(List.of("1:false"), c.branchSummary());
    }

    @Test
    void testTernaryBranch() {
        String script = "var x = input ? 'a' : 'b'";
        EventCollector c = run(script, true);
        assertEquals(List.of("1:true"), c.branchSummary());
        c = run(script, false);
        assertEquals(List.of("1:false"), c.branchSummary());
    }

    @Test
    void testLogicalAndOrBranch() {
        String script = "var x = input && 'a';\n"
                + "var y = input || 'b';";
        EventCollector c = run(script, true);
        assertEquals(List.of("1:true", "2:true"), c.branchSummary());
        c = run(script, false);
        assertEquals(List.of("1:false", "2:false"), c.branchSummary());
    }

    @Test
    void testNullishBranch() {
        String script = "var x = input ?? 'fallback'";
        EventCollector c = run(script, "value");
        assertEquals(List.of("1:true"), c.branchSummary());
        c = run(script, null);
        assertEquals(List.of("1:false"), c.branchSummary());
    }

    @Test
    void testSwitchBranch() {
        String script = "switch (input) {\n"
                + "case 'a': 1; break;\n"
                + "case 'b': 2; break;\n"
                + "default: 3\n"
                + "}";
        EventCollector c = run(script, "b");
        // first case misses, second matches — no further case tests
        assertEquals(List.of("2:false", "3:true"), c.branchSummary());
        c = run(script, "z");
        assertEquals(List.of("2:false", "3:false"), c.branchSummary());
    }

    @Test
    void testCompareOperands() {
        EventCollector c = run("input >= 25", 22);
        assertEquals(1, c.compares.size());
        Object[] operands = (Object[]) c.compares.get(0).value;
        assertEquals(22, operands[0]);
        assertEquals(">=", operands[1]);
        assertEquals(25, operands[2]);
    }

    @Test
    void testCompareOperators() {
        String script = "input < 1; input > 2; input <= 3; input >= 4; input == 5; input === 6; input != 7; input !== 8;";
        EventCollector c = run(script, 5);
        assertEquals(8, c.compares.size());
        List<String> ops = new ArrayList<>();
        for (Event e : c.compares) {
            ops.add((String) ((Object[]) e.value)[1]);
        }
        assertEquals(List.of("<", ">", "<=", ">=", "==", "===", "!=", "!=="), ops);
    }

    @Test
    void testBranchInsideCallbackFiresPerInvocation() {
        String script = "var count = 0;\n"
                + "[1, 2, 3].forEach(function(x){ if (x > 1) count++ });\n"
                + "count";
        EventCollector c = run(script, null);
        // one BRANCH per callback invocation, same site
        assertEquals(List.of("2:false", "2:true", "2:true"), c.branchSummary());
        assertEquals(3, c.compares.size());
    }

    @Test
    void testNoListenerNothingBreaks() {
        Engine engine = new Engine();
        Object result = engine.eval("var x = 5 > 3 ? (1 && 2) : 0; x");
        assertEquals(2, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBracketReadFiresPropertyGet() {
        // a keyed lookup over a small table — the bracket read should report [target, key, result]
        String script = "var table = { a: { rate: 15 }, b: { rate: 20 } };\n"
                + "var key = input;\n"
                + "var row = table[key];\n"
                + "row.rate";
        EventCollector c = run(script, "b");
        // exactly one bracket read: table[key]  (row.rate is dot access, table/key/input are refs)
        assertEquals(1, c.propertyGets.size());
        Object[] v = (Object[]) c.propertyGets.get(0).value;   // [target, key, result]
        assertEquals("b", v[1]);
        assertEquals(20, ((java.util.Map<String, Object>) v[2]).get("rate"));   // the selected row
        assertTrue(((java.util.Map<String, Object>) v[0]).containsKey("a"));    // the table object
    }

    @Test
    void testDotReadDoesNotFirePropertyGet() {
        // dot access is intentionally not reported (kept cheap on hot paths)
        EventCollector c = run("var o = { x: 1 }; o.x", null);
        assertEquals(0, c.propertyGets.size());
    }

    @Test
    void testArrayIndexReadFiresPropertyGet() {
        // numeric bracket access reports too — key is the index, result the element
        EventCollector c = run("var xs = [10, 20, 30]; xs[input]", 2);
        assertEquals(1, c.propertyGets.size());
        Object[] v = (Object[]) c.propertyGets.get(0).value;
        assertEquals(2, v[1]);
        assertEquals(30, v[2]);
    }

}

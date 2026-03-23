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
package io.karatelabs.core;

import org.junit.jupiter.api.Test;

import static io.karatelabs.core.TestUtils.*;

/**
 * Tests for table keyword and set-with-table functionality.
 * Based on V1's js-arrays.feature.
 */
class StepTableTest {

    // ========== Table Keyword ==========

    @Test
    void testTableToJsonWithExpressions() {
        ScenarioRuntime sr = run("""
            * def one = 'hello'
            * def two = { baz: 'world' }
            * table json
                | foo     | bar |
                | one     | 1   |
                | two.baz | 2   |
            * match json == [{ foo: 'hello', bar: 1 }, { foo: 'world', bar: 2 }]
            """);
        assertPassed(sr);
    }

    @Test
    void testTableToJsonWithEmptyAndNulls() {
        ScenarioRuntime sr = run("""
            * def one = { baz: null }
            * table json
                | foo     | bar    |
                | 'hello' |        |
                | one.baz | (null) |
                | 'world' | null   |
            * match json == [{ foo: 'hello' }, { bar: null }, { foo: 'world' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testTableToJsonWithNestedJson() {
        ScenarioRuntime sr = run("""
            * def one = 'hello'
            * def two = { baz: 'world' }
            * table json
                | foo     | bar            |
                | one     | { baz: 1 }     |
                | two.baz | ['baz', 'ban'] |
                | true    | one == 'hello' |
            * match json == [{ foo: 'hello', bar: { baz: 1 } }, { foo: 'world', bar: ['baz', 'ban'] }, { foo: true, bar: true }]
            """);
        assertPassed(sr);
    }

    // ========== Set Via Table (path/value format) ==========

    @Test
    void testSetViaTable() {
        ScenarioRuntime sr = run("""
            * def cat = { name: '' }
            * set cat
            | path   | value |
            | name   | 'Bob' |
            | age    | 5     |
            * match cat == { name: 'Bob', age: 5 }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetNestedViaTable() {
        ScenarioRuntime sr = run("""
            * def cat = { name: 'Wild', kitten: null }
            * set cat $.kitten
            | path   | value |
            | name   | 'Bob' |
            | age    | 5     |
            * match cat == { name: 'Wild', kitten: { name: 'Bob', age: 5 } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetVariablePlusPathViaTable() {
        ScenarioRuntime sr = run("""
            * def cat = { name: 'Wild', kitten: null }
            * set cat.kitten
            | path   | value |
            | name   | 'Bob' |
            | age    | 5     |
            * match cat == { name: 'Wild', kitten: { name: 'Bob', age: 5 } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetViaTableWhereVariableDoesNotExist() {
        ScenarioRuntime sr = run("""
            * set foo
            | path | value      |
            | bar  | 'baz'      |
            | a.b  | 'c'        |
            | fizz | { d: 'e' } |
            * match foo == { bar: 'baz', a: { b: 'c' }, fizz: { d: 'e' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetViaTableWithArrayPaths() {
        ScenarioRuntime sr = run("""
            * set foo
            | path   | value   |
            | bar[0] | 'baz'   |
            | a[0].b | 'ban'   |
            | c[0]   | [1, 2]  |
            | c[1]   | [3, 4]  |
            * match foo == { bar: [ 'baz'], a: [{ b: 'ban' }], c: [[1, 2], [3, 4]] }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetViaTableComplexPaths() {
        ScenarioRuntime sr = run("""
            * set expected
            | path            | value   |
            | first           | 'hello' |
            | client.id       | 'goodbye'            |
            | client.foo.bar  | 'world' |
            * match expected == { first: 'hello', client: { id: 'goodbye', foo: { bar: 'world' }}}
            """);
        assertPassed(sr);
    }

    @Test
    void testSetViaTableRepeatedPaths() {
        ScenarioRuntime sr = run("""
            * set foo.bar
            | path   | value |
            | one    | 1     |
            | two[0] | 2     |
            | two[1] | 3     |
            * match foo == { bar: { one: 1, two: [2, 3] } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetViaTableDifferentNestingOptions() {
        ScenarioRuntime sr = run("""
            * set first
            | path | value          |
            | one  | { bar: 'baz' } |
            | two  | { bar: 'ban' } |
            * match first == { one: { bar: 'baz' }, two: { bar: 'ban' } }

            * set second
            | path     | value |
            | one.bar  | 'baz' |
            | two.bar  | 'ban' |
            * match second == first
            """);
        assertPassed(sr);
    }

    @Test
    void testSetNullViaTable() {
        ScenarioRuntime sr = run("""
            * set foo
                | path       | value  |
                | name.first | null   |
                | name.last  | (null) |
                | age        |        |
            * match foo == { name: { last: null } }
            """);
        assertPassed(sr);
    }

    // ========== Set Via Table (array column format) ==========

    @Test
    void testSetArrayViaTable() {
        ScenarioRuntime sr = run("""
            * set foo
            | path | 0     |
            | bar  | 'baz' |
            * match foo == [{ bar: 'baz' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testSetArrayViaTableMultipleItems() {
        ScenarioRuntime sr = run("""
            * set foo
            | path | 0     | 1     |
            | bar  | 'baz' | 'ban' |
            * match foo == [{ bar: 'baz' }, { bar: 'ban' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testSetArrayViaTableWithIndexes() {
        ScenarioRuntime sr = run("""
            * def foo = [{ bar: 'a' }, { bar: 'b' }, { bar: 'c' }, { bar: 'd' }]
            * set foo
            | path | 3     | 1     |
            | bar  | 'baz' | 'ban' |
            * match foo == [{ bar: 'a' }, { bar: 'ban' }, { bar: 'c' }, { bar: 'baz' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testSetArrayViaTableNoIndexes() {
        // If column headings are not integers, use column position
        ScenarioRuntime sr = run("""
            * set foo
            | path | one   | two   |
            | bar  | 'baz' | 'ban' |
            * match foo == [{ bar: 'baz' }, { bar: 'ban' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testSetArrayViaTableWithBlanksSkipped() {
        // Blank expressions skip setting the value
        ScenarioRuntime sr = run("""
            * set search
                | path       | 0        | 1      | 2       |
                | name.first | 'John'   | 'Jane' |         |
                | name.last  | 'Smith'  | 'Doe'  | 'Waldo' |
                | age        | 20       |        |         |

            * match search[0] == { name: { first: 'John', last: 'Smith' }, age: 20 }
            * match search[1] == { name: { first: 'Jane', last: 'Doe' } }
            * match search[2] == { name: { last: 'Waldo' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testSetArrayViaTableWithParensRetainsNull() {
        // (expr) keeps null value, expr without parens skips if null
        ScenarioRuntime sr = run("""
            * table data
                | first  | last    | age  |
                | 'John' | 'Smith' | 20   |
                | 'Jane' | 'Doe'   |      |
                |        | 'Waldo' |      |

            * set search
                | path       | 0             | 1             | 2               |
                | name.first | data[0].first | data[1].first | (data[2].first) |
                | name.last  | data[0].last  | data[1].last  | data[2].last    |
                | age        | data[0].age   | data[1].age   | data[2].age     |

            * match search[0] == { name: { first: 'John', last: 'Smith' }, age: 20 }
            * match search[1] == { name: { first: 'Jane', last: 'Doe' } }
            * match search[2] == { name: { first: null, last: 'Waldo' } }
            """);
        assertPassed(sr);
    }

}

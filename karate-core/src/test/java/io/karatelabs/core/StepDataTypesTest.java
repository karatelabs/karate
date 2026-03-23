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

import java.util.List;
import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StepDataTypesTest {

    @Test
    void testDefNumber() {
        ScenarioRuntime sr = run("""
            * def a = 1 + 2
            """);
        assertPassed(sr);
        assertEquals(3, get(sr, "a"));
    }

    @Test
    void testDefString() {
        ScenarioRuntime sr = run("""
            * def name = 'hello'
            """);
        assertPassed(sr);
        assertEquals("hello", get(sr, "name"));
    }

    @Test
    void testDefJson() {
        ScenarioRuntime sr = run("""
            * def foo = { name: 'bar' }
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("name", "bar"));
    }

    @Test
    void testDefArray() {
        ScenarioRuntime sr = run("""
            * def arr = [1, 2, 3]
            """);
        assertPassed(sr);
        matchVar(sr, "arr", List.of(1, 2, 3));
    }

    @Test
    void testDefNestedJson() {
        ScenarioRuntime sr = run("""
            * def data = { user: { name: 'john', age: 30 } }
            """);
        assertPassed(sr);
        Object data = get(sr, "data");
        assertInstanceOf(Map.class, data);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data;
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) map.get("user");
        assertEquals("john", user.get("name"));
        assertEquals(30, user.get("age"));
    }

    @Test
    void testDefWithExpression() {
        ScenarioRuntime sr = run("""
            * def x = 10
            * def y = x * 2
            """);
        assertPassed(sr);
        assertEquals(20, get(sr, "y"));
    }

    @Test
    void testSetNested() {
        ScenarioRuntime sr = run("""
            * def foo = { a: 1 }
            * set foo.b = 2
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("a", 1, "b", 2));
    }

    @Test
    void testSetArrayIndex() {
        ScenarioRuntime sr = run("""
            * def arr = [1, 2, 3]
            * set arr[1] = 99
            """);
        assertPassed(sr);
        matchVar(sr, "arr", List.of(1, 99, 3));
    }

    @Test
    void testRemove() {
        ScenarioRuntime sr = run("""
            * def foo = { a: 1, b: 2 }
            * remove foo.b
            """);
        assertPassed(sr);
        matchVar(sr, "foo", Map.of("a", 1));
    }

    @Test
    void testCopy() {
        ScenarioRuntime sr = run("""
            * def original = { a: 1, b: { c: 2 } }
            * copy clone = original
            * set clone.b.c = 99
            """);
        assertPassed(sr);
        // Original should be unchanged
        @SuppressWarnings("unchecked")
        Map<String, Object> original = (Map<String, Object>) get(sr, "original");
        @SuppressWarnings("unchecked")
        Map<String, Object> originalB = (Map<String, Object>) original.get("b");
        assertEquals(2, originalB.get("c"));

        // Clone should have new value
        @SuppressWarnings("unchecked")
        Map<String, Object> clone = (Map<String, Object>) get(sr, "clone");
        @SuppressWarnings("unchecked")
        Map<String, Object> cloneB = (Map<String, Object>) clone.get("b");
        assertEquals(99, cloneB.get("c"));
    }

    @Test
    void testText() {
        ScenarioRuntime sr = run("""
            * text myText =
            \"\"\"
            hello world
            this is multi-line
            \"\"\"
            """);
        assertPassed(sr);
        String text = (String) get(sr, "myText");
        assertTrue(text.contains("hello world"));
        assertTrue(text.contains("this is multi-line"));
    }

    @Test
    void testDocStringIndentation() {
        // Docstring indentation should be normalized based on the first line's margin
        ScenarioRuntime sr = run("""
            * text myText =
              \"\"\"
              line one
                indented line
              line three
              \"\"\"
            """);
        assertPassed(sr);
        String text = (String) get(sr, "myText");
        // First line sets the margin, indented line preserves relative indentation
        assertTrue(text.startsWith("line one"));
        assertTrue(text.contains("  indented line")); // 2 spaces relative to margin
        assertTrue(text.contains("line three"));
    }

    @Test
    void testJson() {
        ScenarioRuntime sr = run("""
            * json myJson = { name: 'test', value: 123 }
            """);
        assertPassed(sr);
        matchVar(sr, "myJson", Map.of("name", "test", "value", 123));
    }

    @Test
    void testDefNull() {
        ScenarioRuntime sr = run("""
            * def x = null
            """);
        assertPassed(sr);
        assertNull(get(sr, "x"));
    }

    @Test
    void testDefBoolean() {
        ScenarioRuntime sr = run("""
            * def t = true
            * def f = false
            """);
        assertPassed(sr);
        assertEquals(true, get(sr, "t"));
        assertEquals(false, get(sr, "f"));
    }

    @Test
    void testReplace() {
        // Replace <token> with value in a string variable
        ScenarioRuntime sr = run("""
            * def text = 'hello <name> world'
            * replace text.name = 'foo'
            * match text == 'hello foo world'
            """);
        assertPassed(sr);
        assertEquals("hello foo world", get(sr, "text"));
    }

    @Test
    void testReplaceMultipleTokens() {
        ScenarioRuntime sr = run("""
            * def text = '<greeting> <name>!'
            * replace text.greeting = 'Hello'
            * replace text.name = 'World'
            * match text == 'Hello World!'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithTable() {
        // Replace multiple tokens using table syntax
        ScenarioRuntime sr = run("""
            * def text = 'hello <one> world <two> bye'
            * replace text
              | token | value   |
              | one   | 'cruel' |
              | two   | 'good'  |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
        assertEquals("hello cruel world good bye", get(sr, "text"));
    }

    @Test
    void testReplaceWithTableExpressions() {
        // Replace with table where values are expressions
        ScenarioRuntime sr = run("""
            * def text = 'hello <one> world <two> bye'
            * def first = 'cruel'
            * def second = 'good'
            * replace text
              | token | value  |
              | one   | first  |
              | two   | second |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithTableCustomPlaceholders() {
        // Replace with custom placeholder syntax (not <...>)
        ScenarioRuntime sr = run("""
            * def text = 'hello ${one} world @@two@@ bye'
            * replace text
              | token   | value   |
              | ${one}  | 'cruel' |
              | @@two@@ | 'good'  |
            * match text == 'hello cruel world good bye'
            """);
        assertPassed(sr);
    }

    @Test
    void testReplaceWithJsonObject() {
        // Replace tokens in a JSON object, then convert back to JSON
        ScenarioRuntime sr = run("""
            * def data = { foo: '<foo>', bar: { hello: '<bar>'} }
            * replace data
              | token | value |
              | foo   | 'one' |
              | bar   | 'two' |
            * json data = data
            * match data == { foo: 'one', bar: { hello: 'two' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testCsvKeywordWithDocString() {
        // csv keyword with doc string
        ScenarioRuntime sr = run("""
            * csv data =
            \"\"\"
            first,second
            a1,a2
            b1,b2
            \"\"\"
            * match data == [{ first: 'a1', second: 'a2'}, { first: 'b1', second: 'b2' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testCsvKeywordWithVariable() {
        // csv keyword with variable reference
        ScenarioRuntime sr = run("""
            * text foo =
            \"\"\"
            name,type
            Billie,LOL
            Bob,Wild
            \"\"\"
            * csv bar = foo
            * match bar == [{ name: 'Billie', type: 'LOL' }, { name: 'Bob', type: 'Wild' }]
            """);
        assertPassed(sr);
    }

    @Test
    void testYamlKeywordWithDocString() {
        // yaml keyword with doc string
        ScenarioRuntime sr = run("""
            * yaml data =
            \"\"\"
            name: John
            age: 30
            \"\"\"
            * match data == { name: 'John', age: 30 }
            """);
        assertPassed(sr);
    }

    @Test
    void testYamlNestedStructure() {
        // yaml keyword with nested objects
        ScenarioRuntime sr = run("""
            * yaml data =
            \"\"\"
            name: John
            input:
              id: 1
              subType:
                name: Smith
                deleted: false
            \"\"\"
            * match data == { name: 'John', input: { id: 1, subType: { name: 'Smith', deleted: false } } }
            """);
        assertPassed(sr);
    }

    @Test
    void testPropertyAssignmentWithDocString() {
        // Property assignment with docstring value (e.g., foo.bar = """json""")
        ScenarioRuntime sr = run("""
            * def foo = { bar: 'one' }
            * foo.bar =
            \"\"\"
            {
              some: 'big',
              message: 'content'
            }
            \"\"\"
            * match foo == { bar: { some: 'big', message: 'content' } }
            """);
        assertPassed(sr);
    }

    // ========== String Keyword ==========

    @Test
    void testStringKeyword() {
        // string keyword converts JSON to string
        ScenarioRuntime sr = run("""
            * def json = { 'sp ace': 'foo', 'hy-phen': 'bar', 'full.stop': 'baz' }
            * string jsonString = json
            * match jsonString == '{"sp ace":"foo","hy-phen":"bar","full.stop":"baz"}'
            """);
        assertPassed(sr);
    }

    @Test
    void testJsonPathOnStringAutoConvert() {
        // V1 compatibility: JSONPath on a string should auto-convert if it looks like JSON
        ScenarioRuntime sr = run("""
            * def response = "{ foo: { hello: 'world' } }"
            * def foo = $.foo
            * match foo == { hello: 'world' }
            """);
        assertPassed(sr);
    }

    // ========== Variable Name Validation ==========

    @Test
    void testValidVariableNames() {
        // V2 allows variable names starting with letter or underscore
        // Note: V1 only allowed letter as first character, V2 is more permissive
        ScenarioRuntime sr = run("""
            * def foo = 1
            * def foo_bar = 2
            * def foo_ = 3
            * def foo1 = 4
            * def a = 5
            * def a1 = 6
            * def _foo = 7
            * def _foo_ = 8
            """);
        assertPassed(sr);
        assertEquals(1, get(sr, "foo"));
        assertEquals(2, get(sr, "foo_bar"));
        assertEquals(3, get(sr, "foo_"));
        assertEquals(4, get(sr, "foo1"));
        assertEquals(5, get(sr, "a"));
        assertEquals(6, get(sr, "a1"));
        assertEquals(7, get(sr, "_foo"));
        assertEquals(8, get(sr, "_foo_"));
    }

    @Test
    void testInvalidVariableNameLeadingDigit() {
        // Variable names cannot start with a digit
        ScenarioRuntime sr = run("""
            * def 2foo = 1
            """);
        assertFailed(sr);
    }

    @Test
    void testInvalidVariableNameReservedKarate() {
        // 'karate' is a reserved variable name
        ScenarioRuntime sr = run("""
            * def karate = 1
            """);
        assertFailed(sr);
    }

}

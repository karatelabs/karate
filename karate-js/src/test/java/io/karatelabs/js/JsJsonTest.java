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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsJsonTest extends EvalBase {

    @Test
    void testStringifyBasic() {
        assertEquals("{\"a\":\"b\"}", eval("JSON.stringify({a:'b'})"));
    }

    @Test
    void testStringifyWithReplacerArray() {
        assertEquals("{\"a\":\"b\"}", eval("JSON.stringify({a:'b',c:'d'}, ['a'])"));
    }

    @Test
    void testStringifyWithNullReplacerAndSpace() {
        String result = (String) eval("JSON.stringify({a:'b',c:'d'}, null, 2)");
        String expected = "{\n  \"a\": \"b\",\n  \"c\": \"d\"\n}";
        assertEquals(expected, result);
    }

    @Test
    void testStringifyWithSpaceString() {
        String result = (String) eval("JSON.stringify({a:'b'}, null, '  ')");
        String expected = "{\n  \"a\": \"b\"\n}";
        assertEquals(expected, result);
    }

    @Test
    void testStringifyWithSpaceNumber() {
        String result = (String) eval("JSON.stringify({a:'b',c:{d:'e'}}, null, 4)");
        String expected = "{\n    \"a\": \"b\",\n    \"c\": {\n        \"d\": \"e\"\n    }\n}";
        assertEquals(expected, result);
    }

    @Test
    void testStringifyNestedObject() {
        String result = (String) eval("JSON.stringify({a:{b:{c:'d'}}}, null, 2)");
        String expected = "{\n  \"a\": {\n    \"b\": {\n      \"c\": \"d\"\n    }\n  }\n}";
        assertEquals(expected, result);
    }

    @Test
    void testStringifyWithArray() {
        String result = (String) eval("JSON.stringify({a:[1,2,3]}, null, 2)");
        String expected = "{\n  \"a\": [\n    1,\n    2,\n    3\n  ]\n}";
        assertEquals(expected, result);
    }

    @Test
    void testParse() {
        assertEquals(Map.of("a", "b"), eval("JSON.parse('{\"a\":\"b\"}')"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testParseComplex() {
        Map<String, Object> result = (Map<String, Object>) eval("JSON.parse('{\"a\":\"b\",\"c\":{\"d\":\"e\"}}')");
        assertEquals("b", result.get("a"));
        Map<String, Object> nested = (Map<String, Object>) result.get("c");
        assertEquals("e", nested.get("d"));
    }

    @Test
    void testStringifyWithFunctionReplacerFilterKeys() {
        // Filter out password field
        String result = (String) eval("""
            var obj = {username: 'john', password: 'secret', email: 'john@example.com'};
            JSON.stringify(obj, function(key, value) {
                if (key === 'password') return undefined;
                return value;
            })
        """);
        assertEquals("{\"username\":\"john\",\"email\":\"john@example.com\"}", result);
    }

    @Test
    void testStringifyWithFunctionReplacerTransformValues() {
        // Transform string values to uppercase
        String result = (String) eval("""
            var obj = {name: 'alice', city: 'paris'};
            JSON.stringify(obj, function(key, value) {
                if (typeof value === 'string') return value.toUpperCase();
                return value;
            })
        """);
        assertEquals("{\"name\":\"ALICE\",\"city\":\"PARIS\"}", result);
    }

    @Test
    void testStringifyWithFunctionReplacerOnNestedObject() {
        // Replacer should be called for nested values
        String result = (String) eval("""
            var obj = {a: 1, b: {c: 2, d: 3}};
            JSON.stringify(obj, function(key, value) {
                if (typeof value === 'number') return value * 10;
                return value;
            })
        """);
        assertEquals("{\"a\":10,\"b\":{\"c\":20,\"d\":30}}", result);
    }

    @Test
    void testStringifyWithFunctionReplacerAndSpace() {
        // Function replacer combined with space parameter
        String result = (String) eval("""
            var obj = {keep: 'yes', remove: 'no'};
            JSON.stringify(obj, function(key, value) {
                if (key === 'remove') return undefined;
                return value;
            }, 2)
        """);
        String expected = "{\n  \"keep\": \"yes\"\n}";
        assertEquals(expected, result);
    }

    @Test
    void testStringifyUndefinedInArray() {
        // undefined in array becomes null in JSON
        String result = (String) eval("JSON.stringify([1, undefined, 3])");
        assertEquals("[1,null,3]", result);
    }

    @Test
    void testStringifyJsNumber() {
        // JsNumber wraps a number but should stringify as number
        String result = (String) eval("JSON.stringify([new Number(42)])");
        assertEquals("[42]", result);
    }

    @Test
    void testStringifyJsString() {
        // JsString wraps a string but should stringify as string
        String result = (String) eval("JSON.stringify([new String('hello')])");
        assertEquals("[\"hello\"]", result);
    }

    @Test
    void testStringifyJsBoolean() {
        // JsBoolean wraps a boolean but should stringify as boolean
        String result = (String) eval("JSON.stringify([new Boolean(true)])");
        assertEquals("[true]", result);
    }

    @Test
    void testStringifyJsDate() {
        // JsDate should stringify as ISO date string
        String result = (String) eval("JSON.stringify([new Date(0)])");
        // Date(0) is 1970-01-01T00:00:00.000Z
        assertEquals("[\"1970-01-01T00:00:00.000Z\"]", result);
    }

    @Test
    void testStringifyMixedJsTypes() {
        // Test array with mixed JS wrapper types
        String result = (String) eval("""
            var arr = [new Number(1), new String('two'), new Boolean(false), undefined];
            JSON.stringify(arr)
        """);
        assertEquals("[1,\"two\",false,null]", result);
    }

    @Test
    void testStringifyObjectWithUndefinedValue() {
        // undefined value in object should be omitted
        String result = (String) eval("JSON.stringify({a: 1, b: undefined, c: 3})");
        assertEquals("{\"a\":1,\"c\":3}", result);
    }
}

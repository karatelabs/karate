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

    @Test
    void testStringifyNumberUsesSpecForm() {
        assertEquals("{\"a\":1e+21}", eval("JSON.stringify({a:1e21})"));
        assertEquals("{\"a\":1e-7}", eval("JSON.stringify({a:1e-7})"));
        assertEquals("{\"a\":0}", eval("JSON.stringify({a:-0})"));
        assertEquals("[1,1.5]", eval("JSON.stringify([1, 1.5])"));
    }

    @Test
    void testStringifyCircularThrowsTypeError() {
        assertEquals("TypeError", eval(
                "var a = {}; a.self = a; var n;"
                        + " try { JSON.stringify(a) } catch (e) { n = e.name } n"));
        assertEquals("TypeError", eval(
                "var a = []; a.push(a); var n;"
                        + " try { JSON.stringify(a) } catch (e) { n = e.name } n"));
        // repeated (non-circular) references are not cycles
        assertEquals("{\"x\":{\"v\":1},\"y\":{\"v\":1}}", eval(
                "var s = {v:1}; JSON.stringify({x:s, y:s})"));
    }

    @Test
    void testStringifyReplacerArrayWithNonStringEntries() {
        assertEquals("{\"1\":\"x\"}", eval("JSON.stringify({1:'x',b:'y'}, [1])"));
        assertEquals("{\"a\":\"b\"}", eval("JSON.stringify({a:'b',c:'d'}, ['a', null, true, 2])"));
    }

    @Test
    void testStringifyReplacerFunctionCycles() {
        // a replacer that prunes the cyclic edge makes the value serializable
        assertEquals("{}", eval(
                "var a = {}; a.self = a;"
                        + " JSON.stringify(a, function(k, v) { return k === 'self' ? undefined : v })"));
        // a cycle the replacer does not prune is a TypeError, not unbounded recursion
        assertEquals("TypeError", eval(
                "var a = {}; a.self = a; var n;"
                        + " try { JSON.stringify(a, function(k, v) { return v }) } catch (e) { n = e.name } n"));
        // repeated (non-circular) references survive the replacer walk
        assertEquals("{\"x\":{\"v\":1},\"y\":{\"v\":1}}", eval(
                "var s = {v:1}; JSON.stringify({x:s, y:s}, function(k, v) { return v })"));
    }

    @Test
    void testStringifyReplacerFunctionThrowPropagates() {
        assertEquals("true|boom", eval(
                "var r; try { JSON.stringify({a:1}, function() { throw new TypeError('boom') }) }"
                        + " catch (e) { r = (e instanceof TypeError) + '|' + e.message } r"));
    }

    @Test
    void testStringifyReplacerFunctionSeesHolderAsThis() {
        // spec §25.5.2: the replacer's `this` is the holder — the synthetic
        // { "": value } wrapper at the root, then each containing object
        assertEquals(":{\"\":{\"a\":{\"b\":1}}} | a:{\"a\":{\"b\":1}} | b:{\"b\":1}", eval("""
            var seen = [];
            JSON.stringify({a: {b: 1}}, function(k, v) {
                seen.push(k + ':' + JSON.stringify(this));
                return v;
            });
            seen.join(' | ')
        """));
    }

    @Test
    void testStringifyReplacerArrayFiltersNestedObjects() {
        // spec §25.5.2: PropertyList applies at every object level, not just the root
        assertEquals("{\"a\":{\"a\":1}}", eval("JSON.stringify({a:{a:1,b:2},b:9}, ['a'])"));
        // ... but never to arrays, whose elements are always all serialized
        assertEquals("{\"a\":[{\"a\":1},{\"a\":2}]}", eval(
                "JSON.stringify({a:[{a:1,b:2},{a:2,b:3}]}, ['a'])"));
    }

    @Test
    void testStringifyCallsToJSON() {
        assertEquals("\"X\"", eval("JSON.stringify({toJSON: function() { return 'X' }})"));
        assertEquals("{\"a\":\"X\"}", eval("JSON.stringify({a: {toJSON: function() { return 'X' }}})"));
        // a toJSON returning an object is serialized in place of the original
        assertEquals("{\"a\":{\"z\":1}}", eval("JSON.stringify({a: {toJSON: function() { return {z:1} }}})"));
        assertEquals("[7]", eval("JSON.stringify([{toJSON: function() { return 7 }}])"));
    }

    @Test
    void testStringifyToJSONReceivesKeyAndThis() {
        assertEquals("{\"p\":\"p=1\"}", eval(
                "var o = {v: 1, toJSON: function(k) { return k + '=' + this.v }};"
                        + " JSON.stringify({p: o})"));
        // the root's toJSON is called with the empty-string key
        assertEquals("\"[]\"", eval(
                "JSON.stringify({toJSON: function(k) { return '[' + k + ']' }})"));
    }

    @Test
    void testStringifyToJSONFromPrototype() {
        assertEquals("\"proto\"", eval("class C { toJSON() { return 'proto' } } JSON.stringify(new C())"));
    }

    @Test
    void testStringifyToJSONRunsBeforeReplacer() {
        // spec §25.5.2: toJSON first, then the replacer sees its result
        assertEquals("{\"a\":\"X!\"}", eval(
                "JSON.stringify({a: {toJSON: function() { return 'X' }}},"
                        + " function(k, v) { return k === 'a' ? v + '!' : v })"));
    }

    @Test
    void testStringifyDateRoutesThroughToJSON() {
        // Date has no special case in the serializer — it is reached via the
        // generic toJSON step, and the output has to be unchanged by that
        assertEquals("\"1970-01-01T00:00:00.000Z\"", eval("JSON.stringify(new Date(0))"));
        assertEquals("{\"d\":\"1970-01-01T00:00:00.000Z\"}", eval("JSON.stringify({d: new Date(0)})"));
        assertEquals("[\"1970-01-01T00:00:00.000Z\"]", eval("JSON.stringify([new Date(0)])"));
        // an invalid Date serializes as null (Date.prototype.toJSON returns null)
        assertEquals("null", eval("JSON.stringify(new Date(NaN))"));
        assertEquals("{\"d\":null}", eval("JSON.stringify({d: new Date(NaN)})"));
    }

    @Test
    void testStringifyStringValueIsQuoted() {
        assertEquals("\"abc\"", eval("JSON.stringify('abc')"));
        assertEquals("\"a\\\"b\"", eval("JSON.stringify('a\"b')"));
        // a string that looks like JSON is still a JSON string, not a nested document
        assertEquals("\"{\\\"a\\\":1}\"", eval("JSON.stringify('{\"a\":1}')"));
    }

    @Test
    void testStringifySparseArrayHole() {
        assertEquals("[1,null,3]", eval("JSON.stringify([1,,3])"));
    }

    @Test
    void testParseWithReviverTransformsValues() {
        assertEquals(10, eval("JSON.parse('{\"n\":1}', function(k, v) {"
                + " return typeof v === 'number' ? v * 10 : v }).n"));
        assertEquals("[2,4,6]", eval("JSON.stringify(JSON.parse('[1,2,3]', function(k, v) {"
                + " return typeof v === 'number' ? v * 2 : v }))"));
    }

    @Test
    void testParseWithReviverDropsUndefined() {
        assertEquals("{\"a\":1}", eval("JSON.stringify(JSON.parse('{\"a\":1,\"b\":2}',"
                + " function(k, v) { return k === 'b' ? undefined : v }))"));
        // a dropped array element leaves a hole, which serializes as null
        assertEquals("[null,2]", eval("JSON.stringify(JSON.parse('[1,2]',"
                + " function(k, v) { return k === '0' ? undefined : v }))"));
    }

    @Test
    void testParseWithReviverIsBottomUp() {
        assertEquals("b,a,", eval("""
            var seen = [];
            JSON.parse('{"a":{"b":1}}', function(k, v) { seen.push(k); return v });
            seen.join(',')
        """));
        assertEquals("0,1,a,", eval("""
            var seen = [];
            JSON.parse('{"a":[1,2]}', function(k, v) { seen.push(k); return v });
            seen.join(',')
        """));
    }

    @Test
    void testParseWithReviverSeesHolderAsThis() {
        // spec §25.5.1: the reviver's `this` is the holder — the containing
        // object / array, and the synthetic { "": value } wrapper at the root
        assertEquals("b@{\"b\":1} | a@{\"a\":{\"b\":1}} | @{\"\":{\"a\":{\"b\":1}}}", eval("""
            var seen = [];
            JSON.parse('{"a":{"b":1}}', function(k, v) {
                seen.push(k + '@' + JSON.stringify(this));
                return v;
            });
            seen.join(' | ')
        """));
    }

    @Test
    void testParseWithReviverOnRootValue() {
        assertEquals(2, eval("JSON.parse('1', function(k, v) { return v + 1 })"));
        assertEquals("", eval("var r = JSON.parse('1', function(k) { return k }); r"));
    }

    @Test
    void testParseWithoutReviverUnaffected() {
        assertEquals(Map.of("a", "b"), eval("JSON.parse('{\"a\":\"b\"}', null)"));
        assertEquals(Map.of("a", "b"), eval("JSON.parse('{\"a\":\"b\"}', 'not a function')"));
    }
}

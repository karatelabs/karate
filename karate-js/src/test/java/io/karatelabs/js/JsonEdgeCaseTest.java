package io.karatelabs.js;

import io.karatelabs.common.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON formatting edge cases: circular references, functions, etc.
 */
class JsonEdgeCaseTest extends EvalBase {

    @Test
    void testCircularReferenceInMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "test");
        map.put("self", map); // circular reference

        String json = StringUtils.formatJson(map, false, false, false);
        assertTrue(json.contains("\"[Circular]\""), "Should detect circular reference");
        assertTrue(json.contains("\"name\":\"test\""));
    }

    @Test
    void testCircularReferenceInJsObject() {
        eval("var obj = { name: 'test' }; obj.self = obj");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertTrue(json.contains("\"[Circular]\""), "Should detect circular reference in JsObject");
    }

    @Test
    void testNestedCircularReference() {
        eval("var a = { name: 'a' }; var b = { name: 'b', ref: a }; a.ref = b");
        Object obj = engine.evalRaw("a");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertTrue(json.contains("\"[Circular]\""), "Should detect nested circular reference");
    }

    @Test
    void testFunctionOmittedFromJson() {
        eval("var obj = { name: 'test', fn: function() { return 1; } }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertFalse(json.contains("fn"), "Function should be omitted from JSON");
        assertTrue(json.contains("\"name\":\"test\""));
    }

    @Test
    void testArrowFunctionOmittedFromJson() {
        eval("var obj = { name: 'test', fn: () => 1 }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertFalse(json.contains("fn"), "Arrow function should be omitted from JSON");
    }

    @Test
    void testFunctionAsTopLevelValue() {
        eval("var fn = function() { return 1; }");
        Object fn = engine.evalRaw("fn");

        String json = StringUtils.formatJson(fn, false, false, false);
        assertEquals("null", json, "Top-level function should serialize as null");
    }

    @Test
    void testJsDateInJson() {
        eval("var obj = { date: new Date(0) }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertTrue(json.contains("1970-01-01T00:00:00.000Z"), "JsDate should serialize as ISO string");
    }

    @Test
    void testUndefinedInJson() {
        eval("var obj = { a: 1, b: undefined, c: 3 }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertFalse(json.contains("\"b\""), "undefined values should be omitted from JSON object");
        assertTrue(json.contains("\"a\":1"));
        assertTrue(json.contains("\"c\":3"));
    }

    @Test
    void testBoxedPrimitivesInJson() {
        eval("var obj = { n: new Number(42), s: new String('hello'), b: new Boolean(true) }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, false, false, false);
        assertTrue(json.contains("42"));
        assertTrue(json.contains("\"hello\""));
        assertTrue(json.contains("true"));
    }

    @Test
    void testDeepNestedStructure() {
        eval("var obj = { a: { b: { c: { d: 'deep' } } } }");
        Object obj = engine.evalRaw("obj");

        String json = StringUtils.formatJson(obj, true, false, false);
        assertTrue(json.contains("\"deep\""));
    }

    @Test
    void testArrayWithCircularReference() {
        eval("var arr = [1, 2, 3]; arr.push(arr)");
        Object arr = engine.evalRaw("arr");

        String json = StringUtils.formatJson(arr, false, false, false);
        assertTrue(json.contains("\"[Circular]\""), "Should detect circular reference in array");
    }

    @Test
    void testMixedArrayWithFunctions() {
        eval("var arr = [1, function() {}, 'hello', () => 1, 2]");
        Object arr = engine.evalRaw("arr");

        String json = StringUtils.formatJson(arr, false, false, false);
        // Functions in arrays serialize as null
        assertTrue(json.contains("null"));
        assertTrue(json.contains("1"));
        assertTrue(json.contains("\"hello\""));
        assertTrue(json.contains("2"));
    }

    @Test
    void testJavaArraysSerializeAsJsonArrays() {
        // a Java array used to fall through to its JVM identity string ("[B@1a2b3c"), so a
        // byte[] read from a file showed up as garbage wherever this formatter is used —
        // karate.pretty, karate.log, the HTTP log, the callSingle disk cache
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bytes", new byte[]{1, 2, -3});
        map.put("ints", new int[]{10, 20});
        map.put("doubles", new double[]{1.5});
        map.put("bools", new boolean[]{true, false});
        map.put("chars", new char[]{'a', 'b'});
        map.put("strings", new String[]{"x", "y"});
        map.put("objects", new Object[]{"x", Map.of("k", "v")});

        String json = StringUtils.formatJson(map, false, false, false);
        assertEquals("{\"bytes\":[1,2,-3],\"ints\":[10,20],\"doubles\":[1.5],\"bools\":[true,false],"
                + "\"chars\":[\"a\",\"b\"],\"strings\":[\"x\",\"y\"],\"objects\":[\"x\",{\"k\":\"v\"}]}", json);
    }

    @Test
    void testTopLevelJavaArraySerializesAsJsonArray() {
        assertEquals("[1,2,3]", StringUtils.formatJson(new byte[]{1, 2, 3}, false, false, false));
        assertEquals("[]", StringUtils.formatJson(new String[0], false, false, false));
    }

    @Test
    void testNestedJavaArrayIsSerializedRecursively() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rows", new byte[][]{{1, 2}, {3}});
        assertEquals("{\"rows\":[[1,2],[3]]}", StringUtils.formatJson(map, false, false, false));
    }

    @Test
    void testJavaArrayWithCircularReference() {
        Object[] arr = new Object[2];
        arr[0] = "a";
        arr[1] = arr;
        String json = StringUtils.formatJson(arr, false, false, false);
        assertEquals("[\"a\",\"[Circular]\"]", json);
    }

    @Test
    void testJavaArrayInLenientMode() {
        // the lenient form is what the HTTP log and log masking render with
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bytes", new byte[]{1, 2});
        map.put("names", new String[]{"x"});
        assertEquals("{bytes:[1,2],names:['x']}", StringUtils.formatJson(map, false, true, false));
    }

    @Test
    void testJavaArrayPrettyPrinted() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bytes", new byte[]{1, 2});
        assertEquals("""
                {
                  "bytes": [
                    1,
                    2
                  ]
                }""", StringUtils.formatJson(map, true, false, false));
    }

}

package io.karatelabs.js;

import io.karatelabs.common.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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

}

package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JS/Java interop.
 * <p>
 * Design principles:
 * 1. JsArray implements List, JsObject implements Map - seamless Java interop
 * 2. ES6 semantics preserved within JS (undefined, prototype chain, etc.)
 * 3. Lazy auto-unwrap at Java interface boundary:
 * - List.get(int) / Map.get(Object) → unwrapped values (null, Date, etc.)
 * - JsArray.getElement(int) / JsObject.getMember(String) → raw JS values
 * 4. Conversion at boundaries: Engine.eval() top-level, SimpleObject args
 * <p>
 * Access patterns:
 * - Java user: casts to List/Map, gets unwrapped values automatically
 * - JS internal: uses getElement()/getMember(), sees raw JS values
 */
@SuppressWarnings("unchecked")
class JavaMirrorTest {

    // =================================================================================================================
    // Top-Level Conversion Tests (Engine.eval return values)
    // These test that top-level values returned from eval() are converted
    // =================================================================================================================

    @Test
    void testTopLevelUndefinedBecomesNull() {
        Engine engine = new Engine();
        Object result = engine.eval("undefined");
        assertNull(result, "top-level undefined should be converted to null");
    }

    @Test
    void testTopLevelJsDateBecomesDate() {
        Engine engine = new Engine();
        Object result = engine.eval("new Date(86400000)");
        assertInstanceOf(Date.class, result, "top-level JsDate should be converted to Date");
        assertEquals(86400000L, ((Date) result).getTime());
    }

    @Test
    void testTopLevelNullRemainsNull() {
        Engine engine = new Engine();
        Object result = engine.eval("null");
        assertNull(result, "top-level null should remain null");
    }

    @Test
    void testTopLevelPrimitivesPassThrough() {
        Engine engine = new Engine();
        assertEquals(42, engine.eval("42"));
        assertEquals("hello", engine.eval("'hello'"));
        assertEquals(true, engine.eval("true"));
        assertEquals(3.14, (Double) engine.eval("3.14"), 0.001);
    }

    // =================================================================================================================
    // JsArray as List Tests
    // These test that JsArray can be used as java.util.List
    // =================================================================================================================

    @Test
    void testJsArrayIsInstanceOfList() {
        Engine engine = new Engine();
        Object result = engine.eval("[1, 2, 3]");
        Object rawResult = engine.evalRaw("[1, 2, 3]");
        assertInstanceOf(List.class, result, "JsArray should be instanceof List");
    }

    @Test
    void testJsArrayListOperations() {
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval("[1, 2, 3]");

        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    void testJsArrayListIteration() {
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval("['a', 'b', 'c']");

        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            sb.append(item);
        }
        assertEquals("abc", sb.toString());
    }

    @Test
    void testJsArrayContainsRawValues() {
        // Elements inside JsArray are stored as raw JS values
        // Use getElement() for raw access, List.get() auto-unwraps
        Engine engine = new Engine();
        Object result = engine.evalRaw("[new Date(0), undefined, null, 'hello']");

        assertInstanceOf(JsArray.class, result);
        JsArray arr = (JsArray) result;
        assertEquals(4, arr.size());

        // Raw access via getElement() returns raw JS values
        assertInstanceOf(JsDate.class, arr.getElement(0), "getElement() returns raw JsDate");
        assertEquals(Terms.UNDEFINED, arr.getElement(1), "getElement() returns raw undefined");
        assertNull(arr.getElement(2), "null remains null");
        assertEquals("hello", arr.getElement(3));

        // List.get() auto-unwraps for Java consumers
        assertInstanceOf(java.util.Date.class, arr.get(0), "List.get() unwraps JsDate");
        assertNull(arr.get(1), "List.get() converts undefined to null");
        assertNull(arr.get(2), "null remains null");
        assertEquals("hello", arr.get(3));
    }

    @Test
    void testObjectValuesReturnsUsableList() {
        Engine engine = new Engine();
        Object result = engine.eval("Object.values({a: 1, b: 2, c: 3})");

        assertInstanceOf(List.class, result);
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertTrue(list.contains(1));
        assertTrue(list.contains(2));
        assertTrue(list.contains(3));
    }

    @Test
    void testArrayMapReturnsUsableList() {
        Engine engine = new Engine();
        Object result = engine.eval("[1, 2, 3].map(function(x) { return x * 2; })");

        assertInstanceOf(List.class, result);
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertEquals(2, list.get(0));
        assertEquals(4, list.get(1));
        assertEquals(6, list.get(2));
    }

    @Test
    void testArrayFilterReturnsUsableList() {
        Engine engine = new Engine();
        Object result = engine.eval("[1, 2, 3, 4, 5].filter(function(x) { return x > 2; })");

        assertInstanceOf(List.class, result);
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertEquals(3, list.get(0));
        assertEquals(4, list.get(1));
        assertEquals(5, list.get(2));
    }

    @Test
    void testArraySliceReturnsUsableList() {
        Engine engine = new Engine();
        Object result = engine.eval("['a', 'b', 'c', 'd'].slice(1, 3)");

        assertInstanceOf(List.class, result);
        List<Object> list = (List<Object>) result;
        assertEquals(2, list.size());
        assertEquals("b", list.get(0));
        assertEquals("c", list.get(1));
    }

    // =================================================================================================================
    // JsObject as Map Tests
    // These test that JsObject can be used as java.util.Map
    // =================================================================================================================

    @Test
    void testJsObjectIsInstanceOfMap() {
        Engine engine = new Engine();
        Object result = engine.eval("({a: 1, b: 2})");
        assertInstanceOf(Map.class, result, "JsObject should be instanceof Map");
    }

    @Test
    void testJsObjectMapOperations() {
        Engine engine = new Engine();
        Map<String, Object> map = (Map<String, Object>) engine.eval("({name: 'John', age: 30})");

        assertEquals(2, map.size());
        assertEquals("John", map.get("name"));
        assertEquals(30, map.get("age"));
        assertTrue(map.containsKey("name"));
        assertTrue(map.containsKey("age"));
        assertFalse(map.containsKey("missing"));
    }

    @Test
    void testJsObjectMapIteration() {
        Engine engine = new Engine();
        Map<String, Object> map = (Map<String, Object>) engine.eval("({a: 1, b: 2})");

        int sum = 0;
        for (Object value : map.values()) {
            sum += (Integer) value;
        }
        assertEquals(3, sum);
    }

    @Test
    void testJsObjectContainsRawValues() {
        // Values inside JsObject are stored as raw JS values
        // Use getMember() for raw access, Map.get() auto-unwraps
        Engine engine = new Engine();
        Object result = engine.evalRaw("({date: new Date(0), missing: undefined, empty: null})");

        assertInstanceOf(ObjectLike.class, result);
        ObjectLike obj = (ObjectLike) result;

        // Raw access via getMember() returns JS types
        assertInstanceOf(JsDate.class, obj.getMember("date"), "raw value should be JsDate");
        assertEquals(Terms.UNDEFINED, obj.getMember("missing"), "undefined should be Terms.UNDEFINED");
        assertNull(obj.getMember("empty"), "null remains null");

        // Map.get() auto-unwraps for Java consumers
        Map<String, Object> map = (Map<String, Object>) result;
        assertInstanceOf(java.util.Date.class, map.get("date"), "Map.get() auto-unwraps to java.util.Date");
        assertNull(map.get("missing"), "Map.get() converts undefined to null");
    }

    @Test
    void testObjectAssignReturnsUsableMap() {
        Engine engine = new Engine();
        Object result = engine.eval("Object.assign({}, {a: 1}, {b: 2})");

        assertInstanceOf(Map.class, result);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    @Test
    void testObjectFromEntriesReturnsUsableMap() {
        Engine engine = new Engine();
        Object result = engine.eval("Object.fromEntries([['a', 1], ['b', 2]])");

        assertInstanceOf(Map.class, result);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    // =================================================================================================================
    // Prototype Methods Still Work
    // These test that JS prototype methods still work on JsArray/JsObject
    // =================================================================================================================

    @Test
    void testArrayPrototypeMethods() {
        Engine engine = new Engine();
        assertEquals(true, engine.eval("[1, 2, 3].includes(2)"));
        assertEquals(1, engine.eval("[1, 2, 3].indexOf(2)"));
        assertEquals(6, engine.eval("[1, 2, 3].reduce(function(a, b) { return a + b; }, 0)"));
        assertEquals("1,2,3", engine.eval("[1, 2, 3].join(',')"));
    }

    @Test
    void testObjectPrototypeMethods() {
        Engine engine = new Engine();
        assertEquals(true, engine.eval("({a: 1}).hasOwnProperty('a')"));
        assertEquals(false, engine.eval("({a: 1}).hasOwnProperty('b')"));
    }

    // =================================================================================================================
    // Nested Structures
    // These test that nested JsArray/JsObject are usable as List/Map
    // =================================================================================================================

    @Test
    void testNestedArraysUsableAsList() {
        Engine engine = new Engine();
        Object result = engine.eval("[[1, 2], [3, 4]]");

        assertInstanceOf(List.class, result);
        List<Object> outer = (List<Object>) result;
        assertEquals(2, outer.size());

        assertInstanceOf(List.class, outer.get(0));
        List<Object> inner = (List<Object>) outer.get(0);
        assertEquals(2, inner.size());
        assertEquals(1, inner.get(0));
        assertEquals(2, inner.get(1));
    }

    @Test
    void testNestedObjectsUsableAsMap() {
        Engine engine = new Engine();
        Object result = engine.eval("({outer: {inner: 'value'}})");

        assertInstanceOf(Map.class, result);
        Map<String, Object> outer = (Map<String, Object>) result;

        assertInstanceOf(Map.class, outer.get("outer"));
        Map<String, Object> inner = (Map<String, Object>) outer.get("outer");
        assertEquals("value", inner.get("inner"));
    }

    @Test
    void testMixedNestedStructures() {
        Engine engine = new Engine();
        Object result = engine.eval("({items: [1, 2, 3], meta: {count: 3}})");

        assertInstanceOf(Map.class, result);
        Map<String, Object> map = (Map<String, Object>) result;

        assertInstanceOf(List.class, map.get("items"));
        List<Object> items = (List<Object>) map.get("items");
        assertEquals(3, items.size());

        assertInstanceOf(Map.class, map.get("meta"));
        Map<String, Object> meta = (Map<String, Object>) map.get("meta");
        assertEquals(3, meta.get("count"));
    }

    // =================================================================================================================
    // Dual Access Pattern Tests - Java interface vs JS internal
    // Demonstrates: List.get() → unwrapped, JsArray.getElement() → raw
    // Demonstrates: Map.get() → unwrapped, ObjectLike.getMember() → raw
    // =================================================================================================================

    @Test
    void testListGetUnwrapsUndefined() {
        // List.get() converts undefined to null for Java consumers
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("[1, undefined, 3]");

        // Raw access via getElement() returns Terms.UNDEFINED
        assertEquals(Terms.UNDEFINED, arr.getElement(1), "getElement() returns raw undefined");

        // List.get() converts undefined to null
        assertNull(arr.get(1), "List.get() unwraps undefined to null");
    }

    @Test
    void testMapGetUnwrapsUndefined() {
        // Map.get() converts undefined to null for Java consumers
        Engine engine = new Engine();
        Object result = engine.evalRaw("({a: 1, b: undefined, c: 3})");
        ObjectLike obj = (ObjectLike) result;
        Map<String, Object> map = (Map<String, Object>) result;

        // Raw access via getMember() returns Terms.UNDEFINED
        assertEquals(Terms.UNDEFINED, obj.getMember("b"), "getMember() returns raw undefined");

        // Map.get() converts undefined to null
        assertNull(map.get("b"), "Map.get() unwraps undefined to null");
    }

    @Test
    void testListGetUnwrapsJsDate() {
        // List.get() unwraps JsDate to java.util.Date
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("[new Date(0)]");

        // Raw access via getElement() returns JsDate
        assertInstanceOf(JsDate.class, arr.getElement(0), "getElement() returns raw JsDate");

        // List.get() unwraps to java.util.Date
        assertInstanceOf(java.util.Date.class, arr.get(0), "List.get() unwraps to java.util.Date");
    }

    @Test
    void testMapGetUnwrapsJsDate() {
        // Map.get() unwraps JsDate to java.util.Date
        Engine engine = new Engine();
        Object result = engine.evalRaw("({date: new Date(0)})");
        ObjectLike obj = (ObjectLike) result;
        Map<String, Object> map = (Map<String, Object>) result;

        // Raw access via getMember() returns JsDate
        assertInstanceOf(JsDate.class, obj.getMember("date"), "getMember() returns raw JsDate");

        // Map.get() unwraps to java.util.Date
        assertInstanceOf(java.util.Date.class, map.get("date"), "Map.get() unwraps to java.util.Date");
    }

    @Test
    void testMapNullVsUndefinedBothBecomeNull() {
        // Both null and undefined become null when accessed via Map.get()
        Engine engine = new Engine();
        Object result = engine.evalRaw("({a: null, b: undefined})");
        Map<String, Object> map = (Map<String, Object>) result;
        ObjectLike obj = (ObjectLike) result;

        // Via Map.get(), both are null
        assertNull(map.get("a"), "null stays null via Map.get()");
        assertNull(map.get("b"), "undefined becomes null via Map.get()");

        // Via getMember(), we can distinguish them
        assertNull(obj.getMember("a"), "null stays null via getMember()");
        assertEquals(Terms.UNDEFINED, obj.getMember("b"), "undefined stays undefined via getMember()");
    }

    @Test
    void testListNullVsUndefinedBothBecomeNull() {
        // Both null and undefined become null when accessed via List.get()
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("[null, undefined]");

        // Via List.get(), both are null
        assertNull(arr.get(0), "null stays null via List.get()");
        assertNull(arr.get(1), "undefined becomes null via List.get()");

        // Via getElement(), we can distinguish them
        assertNull(arr.getElement(0), "null stays null via getElement()");
        assertEquals(Terms.UNDEFINED, arr.getElement(1), "undefined stays undefined via getElement()");
    }

    @Test
    void testMixedTypesUnwrappedViaListInterface() {
        // Test that JS wrapper types are unwrapped when accessed via List interface
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("[new Number(42), new String('hello'), new Boolean(true)]");

        // Via List.get(), wrappers are unwrapped to Java types
        assertEquals(42, arr.get(0), "JsNumber unwraps to Number");
        assertEquals("hello", arr.get(1), "JsString unwraps to String");
        assertEquals(true, arr.get(2), "JsBoolean unwraps to Boolean");

        // Via getElement(), raw JS types are returned
        assertInstanceOf(JsNumber.class, arr.getElement(0), "getElement() returns raw JsNumber");
        assertInstanceOf(JsString.class, arr.getElement(1), "getElement() returns raw JsString");
        assertInstanceOf(JsBoolean.class, arr.getElement(2), "getElement() returns raw JsBoolean");
    }

    @Test
    void testMixedTypesUnwrappedViaMapInterface() {
        // Test that JS wrapper types are unwrapped when accessed via Map interface
        Engine engine = new Engine();
        Object result = engine.evalRaw("({num: new Number(42), str: new String('hello'), bool: new Boolean(true)})");
        Map<String, Object> map = (Map<String, Object>) result;
        ObjectLike obj = (ObjectLike) result;

        // Via Map.get(), wrappers are unwrapped to Java types
        assertEquals(42, map.get("num"), "JsNumber unwraps to Number");
        assertEquals("hello", map.get("str"), "JsString unwraps to String");
        assertEquals(true, map.get("bool"), "JsBoolean unwraps to Boolean");

        // Via getMember(), raw JS types are returned
        assertInstanceOf(JsNumber.class, obj.getMember("num"), "getMember() returns raw JsNumber");
        assertInstanceOf(JsString.class, obj.getMember("str"), "getMember() returns raw JsString");
        assertInstanceOf(JsBoolean.class, obj.getMember("bool"), "getMember() returns raw JsBoolean");
    }

    // =================================================================================================================
    // Array methods producing undefined in results
    // =================================================================================================================

    @Test
    void testArrayMapReturningUndefined() {
        // map() callback explicitly returns undefined
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, 2, 3].map(function(x) { return undefined; })");

        assertEquals(3, list.size());
        assertSame(Terms.UNDEFINED, list.get(0), "map returning undefined should preserve raw undefined");
        assertSame(Terms.UNDEFINED, list.get(1));
        assertSame(Terms.UNDEFINED, list.get(2));
    }

    @Test
    void testArrayMapReturningNothing() {
        // map() callback returns nothing (implicit undefined)
        // NOTE: Current behavior converts implicit return to null (potential bug)
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, 2, 3].map(function(x) { })");

        assertEquals(3, list.size());
        // Current behavior: implicit return becomes null, not Terms.UNDEFINED
        // This may be a deviation from ES6 spec
        assertNull(list.get(0), "implicit return currently becomes null");
        assertNull(list.get(1));
        assertNull(list.get(2));
    }

    @Test
    void testArrayMapOnUndefinedElements() {
        // map() over array containing undefined - callback receives undefined
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, undefined, 3].map(function(x) { return x; })");

        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertSame(Terms.UNDEFINED, list.get(1), "undefined element passed through map should stay undefined");
        assertEquals(3, list.get(2));
    }

    @Test
    void testArrayMapPartialUndefined() {
        // map() returning undefined for some elements (implicit return)
        // NOTE: Current behavior converts implicit return to null
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, 2, 3, 4].map(function(x) { if (x % 2 === 0) return x * 2; })");

        assertEquals(4, list.size());
        // Current behavior: implicit return becomes null
        assertNull(list.get(0), "odd element currently becomes null (implicit return)");
        assertEquals(4, list.get(1));
        assertNull(list.get(2), "odd element currently becomes null (implicit return)");
        assertEquals(8, list.get(3));
    }

    @Test
    void testArrayFilterOnUndefinedElements() {
        // filter() on array with undefined - undefined is falsy so filtered out
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, undefined, 2, undefined, 3].filter(function(x) { return x; })");

        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    void testArrayFlatMapWithUndefined() {
        // flatMap() returning arrays with undefined
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, 2].flatMap(function(x) { return [x, undefined]; })");

        assertEquals(4, list.size());
        assertEquals(1, list.get(0));
        assertSame(Terms.UNDEFINED, list.get(1), "undefined in flatMap result should be preserved");
        assertEquals(2, list.get(2));
        assertSame(Terms.UNDEFINED, list.get(3));
    }

    @Test
    void testArrayFromWithUndefined() {
        // Array.from() with undefined elements
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "Array.from({length: 3})");

        assertEquals(3, list.size());
        // Array.from({length: 3}) creates [undefined, undefined, undefined]
        assertSame(Terms.UNDEFINED, list.get(0));
        assertSame(Terms.UNDEFINED, list.get(1));
        assertSame(Terms.UNDEFINED, list.get(2));
    }

    @Test
    void testObjectValuesWithUndefined() {
        // Object.values() on object with undefined values
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "Object.values({a: 1, b: undefined, c: 3})");

        assertEquals(3, list.size());
        assertTrue(list.contains(1));
        assertTrue(list.contains(3));
        // One of them should be Terms.UNDEFINED
        long undefinedCount = list.stream().filter(x -> x == Terms.UNDEFINED).count();
        assertEquals(1, undefinedCount, "Object.values should include raw undefined");
    }

    @Test
    void testObjectEntriesWithUndefined() {
        // Object.entries() on object with undefined values
        Engine engine = new Engine();
        List<Object> entries = (List<Object>) engine.eval(
                "Object.entries({a: undefined})");

        assertEquals(1, entries.size());
        List<Object> entry = (List<Object>) entries.get(0);
        assertEquals("a", entry.get(0));
        assertSame(Terms.UNDEFINED, entry.get(1), "entry value should be raw undefined");
    }

    @Test
    void testSpreadWithUndefined() {
        // Spread operator preserves undefined in array
        // List.get() auto-unwraps undefined to null
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("var a = [undefined]; [...a, 1, ...a]");

        assertEquals(3, arr.size());
        // Raw access via getElement() returns undefined
        assertEquals(Terms.UNDEFINED, arr.getElement(0));
        assertEquals(1, arr.getElement(1));
        assertEquals(Terms.UNDEFINED, arr.getElement(2));
        // List.get() auto-unwraps undefined to null
        assertNull(arr.get(0), "List.get() unwraps undefined to null");
        assertEquals(1, arr.get(1));
        assertNull(arr.get(2), "List.get() unwraps undefined to null");
    }

    @Test
    void testArrayConcatWithUndefined() {
        // concat() preserves undefined
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[undefined].concat([1, undefined])");

        assertEquals(3, list.size());
        assertSame(Terms.UNDEFINED, list.get(0));
        assertEquals(1, list.get(1));
        assertSame(Terms.UNDEFINED, list.get(2));
    }

    // =================================================================================================================
    // Function.call() and Function.apply() edge cases
    // =================================================================================================================

    @Test
    void testFunctionCallReturningUndefined() {
        // Function returning undefined via call()
        Engine engine = new Engine();
        Object result = engine.eval(
                "var fn = function() { return undefined; }; fn.call(null)");

        // Top-level undefined is converted to null
        assertNull(result);
    }

    @Test
    void testFunctionCallInArrayContext() {
        // Using call() within map to produce undefined in array
        // NOTE: Current behavior converts implicit return to null
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "var fn = function(x) { if (x > 2) return x; }; [1, 2, 3, 4].map(function(x) { return fn.call(null, x); })");

        assertEquals(4, list.size());
        // Current behavior: implicit return becomes null
        assertNull(list.get(0), "fn.call returning nothing currently becomes null");
        assertNull(list.get(1));
        assertEquals(3, list.get(2));
        assertEquals(4, list.get(3));
    }

    @Test
    void testFunctionApplyInArrayContext() {
        // Using apply() within map to produce undefined in array
        // NOTE: Current behavior converts implicit return to null
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "var fn = function(x) { if (x > 2) return x; }; [1, 2, 3, 4].map(function(x) { return fn.apply(null, [x]); })");

        assertEquals(4, list.size());
        // Current behavior: implicit return becomes null
        assertNull(list.get(0), "fn.apply returning nothing currently becomes null");
        assertNull(list.get(1));
        assertEquals(3, list.get(2));
        assertEquals(4, list.get(3));
    }

    @Test
    void testArrayMapWithCallOnPrototype() {
        // Array.prototype.map.call() on array-like object
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[].map.call([1, undefined, 3], function(x) { return x; })");

        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertSame(Terms.UNDEFINED, list.get(1), "undefined preserved through map.call");
        assertEquals(3, list.get(2));
    }

    @Test
    void testArraySliceWithUndefined() {
        // slice() preserves undefined elements
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, undefined, 3, undefined, 5].slice(1, 4)");

        assertEquals(3, list.size());
        assertSame(Terms.UNDEFINED, list.get(0));
        assertEquals(3, list.get(1));
        assertSame(Terms.UNDEFINED, list.get(2));
    }

    @Test
    void testArraySpliceReturnsUndefined() {
        // splice() return value contains undefined
        Engine engine = new Engine();
        List<Object> removed = (List<Object>) engine.eval(
                "var arr = [1, undefined, 3]; arr.splice(1, 1)");

        assertEquals(1, removed.size());
        assertSame(Terms.UNDEFINED, removed.get(0), "splice should return raw undefined");
    }

    // =================================================================================================================
    // Mixed JS wrapper types (JsBoolean, JsNumber, JsDate, JsString) in collections
    // =================================================================================================================

    @Test
    void testListWithJsWrapperTypes() {
        // List containing JS wrapper types: new Boolean(), new Number(), new String()
        // List.get() auto-unwraps for Java consumers, getElement() returns raw values
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw(
                "[new Boolean(true), new Number(42), new String('hello'), new Date(0)]");

        assertEquals(4, arr.size());

        // List.get() returns unwrapped Java types
        assertEquals(true, arr.get(0), "JsBoolean unwraps to Boolean");
        assertEquals(42, arr.get(1), "JsNumber unwraps to Number");
        assertEquals("hello", arr.get(2), "JsString unwraps to String");
        assertInstanceOf(java.util.Date.class, arr.get(3), "JsDate unwraps to Date");

        // getElement() returns raw JS types
        assertInstanceOf(JsBoolean.class, arr.getElement(0), "getElement() returns raw JsBoolean");
        assertInstanceOf(JsNumber.class, arr.getElement(1), "getElement() returns raw JsNumber");
        assertInstanceOf(JsString.class, arr.getElement(2), "getElement() returns raw JsString");
        assertInstanceOf(JsDate.class, arr.getElement(3), "getElement() returns raw JsDate");
    }

    @Test
    void testMapWithJsWrapperTypes() {
        // Map containing JS wrapper types
        // Map.get() auto-unwraps for Java consumers, getMember() returns raw values
        Engine engine = new Engine();
        Object result = engine.eval(
                "({bool: new Boolean(false), num: new Number(3.14), str: new String('world'), date: new Date(1000)})");

        assertInstanceOf(ObjectLike.class, result);
        ObjectLike obj = (ObjectLike) result;
        Map<String, Object> map = (Map<String, Object>) result;

        assertEquals(4, map.size());
        assertInstanceOf(java.util.Date.class, map.get("date"), "Map.get() auto-unwraps to java.util.Date");

        // getMember() returns raw JS types
        assertInstanceOf(JsDate.class, obj.getMember("date"), "getMember() returns raw JsDate");
    }

    @Test
    void testMixedPrimitivesAndWrappers() {
        // Mix of primitives and wrapper objects
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[true, new Boolean(true), 42, new Number(42), 'hello', new String('hello')]");

        assertEquals(6, list.size());

        // Primitives
        assertEquals(true, list.get(0));   // boolean primitive
        assertEquals(42, list.get(2));      // number primitive
        assertEquals("hello", list.get(4)); // string primitive
    }

    @Test
    void testArrayMapProducingWrappers() {
        // map() producing wrapper objects
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[1, 2, 3].map(function(x) { return new Date(x * 1000); })");

        assertEquals(3, list.size());
        // Each element should be a JsDate (raw, not converted to java.util.Date)
        assertInstanceOf(JsDate.class, list.get(0));
        assertInstanceOf(JsDate.class, list.get(1));
        assertInstanceOf(JsDate.class, list.get(2));
    }

    @Test
    void testMixedWrapperAndUndefined() {
        // Mix of wrapper types and undefined
        // List.get() auto-unwraps for Java consumers
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw(
                "[new Date(0), undefined, new Boolean(true), null, new Number(99)]");

        assertEquals(5, arr.size());
        // List.get() returns unwrapped values
        assertInstanceOf(java.util.Date.class, arr.get(0), "JsDate unwraps to Date");
        assertNull(arr.get(1), "undefined unwraps to null");
        assertEquals(true, arr.get(2), "JsBoolean unwraps to Boolean");
        assertNull(arr.get(3), "null stays null");
        assertEquals(99, arr.get(4), "JsNumber unwraps to Number");

        // getElement() returns raw values
        assertInstanceOf(JsDate.class, arr.getElement(0));
        assertEquals(Terms.UNDEFINED, arr.getElement(1));
        assertInstanceOf(JsBoolean.class, arr.getElement(2));
        assertNull(arr.getElement(3));
        assertInstanceOf(JsNumber.class, arr.getElement(4));
    }

    @Test
    void testObjectValuesWithWrapperTypes() {
        // Object.values() on object with wrapper type values
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "Object.values({d: new Date(0), b: new Boolean(false), n: new Number(0)})");

        assertEquals(3, list.size());
        // Check that raw wrapper types are preserved
        boolean hasJsDate = list.stream().anyMatch(x -> x instanceof JsDate);
        assertTrue(hasJsDate, "Should contain raw JsDate");
    }

    @Test
    void testNestedStructureWithWrappers() {
        // Nested arrays/objects containing wrapper types
        // Both Map.get() and List.get() auto-unwrap for Java consumers
        Engine engine = new Engine();
        Map<String, Object> map = (Map<String, Object>) engine.eval(
                "({dates: [new Date(0), new Date(1000)], numbers: [new Number(1), new Number(2)]})");

        assertEquals(2, map.size());

        // Map.get() returns the nested JsArray (which is a List)
        // Then List.get() on that array returns unwrapped values
        List<Object> dates = (List<Object>) map.get("dates");
        assertEquals(2, dates.size());
        assertInstanceOf(java.util.Date.class, dates.get(0), "JsDate unwraps to Date");
        assertInstanceOf(java.util.Date.class, dates.get(1), "JsDate unwraps to Date");

        List<Object> numbers = (List<Object>) map.get("numbers");
        assertEquals(2, numbers.size());
        assertEquals(1, numbers.get(0), "JsNumber unwraps to Number");
        assertEquals(2, numbers.get(1), "JsNumber unwraps to Number");
    }

    @Test
    void testArrayConcatWithWrappers() {
        // concat() with wrapper types
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[new Date(0)].concat([new Boolean(true), new Number(42)])");

        assertEquals(3, list.size());
        assertInstanceOf(JsDate.class, list.get(0));
    }

    @Test
    void testSpreadWithWrappers() {
        // Spread operator with wrapper types
        // List.get() auto-unwraps for Java consumers
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw(
                "var a = [new Date(0)]; var b = [new Number(1)]; [...a, ...b]");

        assertEquals(2, arr.size());
        // List.get() returns unwrapped values
        assertInstanceOf(java.util.Date.class, arr.get(0), "JsDate unwraps to Date");
        assertEquals(1, arr.get(1), "JsNumber unwraps to Number");
        // getElement() returns raw values
        assertInstanceOf(JsDate.class, arr.getElement(0));
        assertInstanceOf(JsNumber.class, arr.getElement(1));
    }

    @Test
    void testFilterWithWrappers() {
        // filter() preserving wrapper types
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval(
                "[new Date(0), null, new Date(1000), undefined].filter(function(x) { return x instanceof Date; })");

        assertEquals(2, list.size());
        assertInstanceOf(JsDate.class, list.get(0));
        assertInstanceOf(JsDate.class, list.get(1));
    }

    @Test
    void testComplexMixedTypes() {
        // Complex scenario with all types mixed
        // List.get() auto-unwraps for Java consumers
        Engine engine = new Engine();
        JsArray arr = (JsArray) engine.evalRaw("""
                [
                    1,                      // number primitive
                    'hello',                // string primitive
                    true,                   // boolean primitive
                    null,                   // null
                    undefined,              // undefined
                    new Date(0),            // Date wrapper
                    new Number(3.14),       // Number wrapper
                    new Boolean(false),     // Boolean wrapper
                    new String('world'),    // String wrapper
                    [1, 2],                 // nested array
                    {a: 1}                  // nested object
                ]
                """);

        assertEquals(11, arr.size());
        // List.get() returns unwrapped values for Java consumers
        assertEquals(1, arr.get(0));
        assertEquals("hello", arr.get(1));
        assertEquals(true, arr.get(2));
        assertNull(arr.get(3));
        assertNull(arr.get(4), "undefined unwraps to null via List.get()");
        assertInstanceOf(java.util.Date.class, arr.get(5), "JsDate unwraps to Date");
        // Wrapper types are unwrapped
        assertEquals(3.14, arr.get(6), "JsNumber unwraps to Number");
        assertEquals(false, arr.get(7), "JsBoolean unwraps to Boolean");
        assertEquals("world", arr.get(8), "JsString unwraps to String");
        // Nested structures remain as List/Map
        assertInstanceOf(List.class, arr.get(9));
        assertInstanceOf(Map.class, arr.get(10));

        // getElement() returns raw values
        assertEquals(Terms.UNDEFINED, arr.getElement(4), "getElement() returns raw undefined");
        assertInstanceOf(JsDate.class, arr.getElement(5), "getElement() returns raw JsDate");
    }

    // =================================================================================================================
    // Other edge cases
    // =================================================================================================================

    @Test
    void testNumericStringIndexOnObject() {
        Engine engine = new Engine();
        Object result1 = engine.eval("var obj = {'0': 'a'}; obj[0]");
        Object result2 = engine.eval("var obj = {'0': 'a'}; obj['0']");

        assertEquals("a", result1, "numeric index should work on object with string key '0'");
        assertEquals("a", result2, "string index '0' should work on object");
    }

    @Test
    void testEmptyArrayAndObject() {
        Engine engine = new Engine();

        List<Object> emptyArray = (List<Object>) engine.eval("[]");
        assertEquals(0, emptyArray.size());
        assertTrue(emptyArray.isEmpty());

        Map<String, Object> emptyObject = (Map<String, Object>) engine.eval("({})");
        assertEquals(0, emptyObject.size());
        assertTrue(emptyObject.isEmpty());
    }

    @Test
    void testJsUint8ArrayConvertsToByteArray() {
        // Uint8Array is a JavaMirror, should be converted at boundary
        Engine engine = new Engine();
        Object result = engine.eval("new Uint8Array([1, 2, 3])");

        assertInstanceOf(byte[].class, result, "Uint8Array should be converted to byte[]");
        byte[] bytes = (byte[]) result;
        assertArrayEquals(new byte[]{1, 2, 3}, bytes);
    }

    @Test
    void testMapNullValueVsMissingKey() {
        // Within JS, null value and missing key are different
        // At boundary, both become null but containsKey differs
        Engine engine = new Engine();
        Map<String, Object> map = (Map<String, Object>) engine.eval("({a: null, b: 'exists'})");

        assertTrue(map.containsKey("a"), "key 'a' should exist");
        assertNull(map.get("a"), "null value should be null");

        assertFalse(map.containsKey("missing"), "missing key should not exist");
        assertNull(map.get("missing"), "missing key returns null from Map.get");
    }

    @Test
    void testListNullElementVsOutOfBounds() {
        Engine engine = new Engine();
        List<Object> list = (List<Object>) engine.eval("[null, 'exists']");

        assertEquals(2, list.size());
        assertNull(list.get(0), "null element should be null");
        assertEquals("exists", list.get(1));

        // Out of bounds throws IndexOutOfBoundsException (standard List behavior)
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(99));
    }

}

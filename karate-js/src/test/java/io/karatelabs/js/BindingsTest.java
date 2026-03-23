package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Bindings auto-unwrapping and Engine external map support.
 */
class BindingsTest {

    @Test
    void testBindingsAutoUnwrapDate() {
        Engine engine = new Engine();
        engine.eval("var d = new Date(86400000)");

        // get() returns unwrapped Date
        Object result = engine.get("d");
        assertInstanceOf(Date.class, result);
        assertEquals(86400000L, ((Date) result).getTime());

        // getBindings().get() also returns unwrapped Date
        Object fromBindings = engine.getBindings().get("d");
        assertInstanceOf(Date.class, fromBindings);
    }

    @Test
    void testBindingsAutoUnwrapUndefined() {
        Engine engine = new Engine();
        engine.eval("var x");

        // undefined becomes null
        assertNull(engine.get("x"));
        assertNull(engine.getBindings().get("x"));
    }

    @Test
    void testBindingsAutoUnwrapBoxedPrimitives() {
        Engine engine = new Engine();
        engine.eval("var n = new Number(42); var s = new String('hello'); var b = new Boolean(true)");

        assertEquals(42, engine.get("n"));
        assertEquals("hello", engine.get("s"));
        assertEquals(true, engine.get("b"));
    }

    @Test
    void testEvalWithSeesInitialValues() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", 10);

        Engine engine = new Engine();

        // evalWith sees initial values from map
        Object result = engine.evalWith("x + 5", vars);
        assertEquals(15, result);
    }

    @Test
    void testEvalWithIsolatedFromEngineBindings() {
        Engine engine = new Engine();
        engine.eval("var a = 100");

        Map<String, Object> vars = new HashMap<>();
        vars.put("b", 5);

        // evalWith can see engine bindings via parent chain
        Object result = engine.evalWith("a + b", vars);
        assertEquals(105, result);
    }

    @Test
    void testBindingsValuesAutoUnwrap() {
        Engine engine = new Engine();
        engine.eval("var d1 = new Date(0); var d2 = new Date(1000)");

        // values() returns unwrapped values
        for (Object value : engine.getBindings().values()) {
            if (value != null) {
                assertInstanceOf(Date.class, value);
            }
        }
    }

    @Test
    void testBindingsEntrySetAutoUnwrap() {
        Engine engine = new Engine();
        engine.eval("var d = new Date(0)");

        // entrySet() returns unwrapped values
        for (Map.Entry<String, Object> entry : engine.getBindings().entrySet()) {
            if ("d".equals(entry.getKey())) {
                assertInstanceOf(Date.class, entry.getValue());
            }
        }
    }

    @Test
    void testFunctionWrapperAutoConvertsReturnValue() {
        Engine engine = new Engine();
        engine.eval("function getDate() { return new Date(0); }");

        Object fn = engine.get("getDate");
        assertInstanceOf(JsFunctionWrapper.class, fn);

        // Call the wrapped function via eval - should return unwrapped Date
        Object result = engine.eval("getDate()");
        assertInstanceOf(Date.class, result);
    }

    @Test
    void testFunctionWrapperConvertsUndefined() {
        Engine engine = new Engine();
        engine.eval("function returnsUndefined() { return undefined; }");

        // Call via eval
        Object result = engine.eval("returnsUndefined()");
        assertNull(result);
    }

    @Test
    void testFunctionWrapperConvertsJsObject() {
        Engine engine = new Engine();
        engine.eval("function getObj() { return { date: new Date(0) }; }");

        // Call via eval
        Object result = engine.eval("getObj()");

        // Result should be a Map (JsObject implements Map)
        assertInstanceOf(Map.class, result);

        // The map's get() auto-unwraps via JsObject.get()
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertInstanceOf(Date.class, map.get("date"));
    }

    @Test
    void testFunctionWrapperPreservesSource() {
        Engine engine = new Engine();
        engine.eval("var fn = (x) => x * 2");

        Object fn = engine.get("fn");
        assertInstanceOf(JsFunctionWrapper.class, fn);

        JsFunctionWrapper wrapper = (JsFunctionWrapper) fn;
        assertEquals("(x) => x * 2", wrapper.getSource());
    }

    @Test
    void testFunctionWrapperPreservesMember() {
        Engine engine = new Engine();
        engine.eval("function foo() {}; foo.bar = 'baz'");

        Object fn = engine.get("foo");
        assertInstanceOf(JsFunctionWrapper.class, fn);

        JsFunctionWrapper wrapper = (JsFunctionWrapper) fn;
        assertEquals("baz", wrapper.getMember("bar"));
    }

    @Test
    void testRawBindingsReturnsUnwrappedMap() {
        Engine engine = new Engine();
        engine.eval("var d = new Date(0)");

        // getRawBindings returns the raw map
        Map<String, Object> raw = engine.getRawBindings();
        // Note: Engine bindings and root bindings are different
        // getRawBindings() returns root context bindings
    }

    //=== Tests for array-based storage and BindValue integration ===

    @Test
    void testSmallMapModeBasicOperations() {
        Bindings bindings = new Bindings();

        // Put and get
        bindings.putMember("x", 1);
        bindings.putMember("y", 2);
        bindings.putMember("z", 3);

        assertEquals(1, bindings.getMember("x"));
        assertEquals(2, bindings.getMember("y"));
        assertEquals(3, bindings.getMember("z"));

        // hasMember
        assertTrue(bindings.hasMember("x"));
        assertFalse(bindings.hasMember("w"));

        // size
        assertEquals(3, bindings.size());
    }

    @Test
    void testSmallMapModeWithNullValues() {
        Bindings bindings = new Bindings();

        bindings.putMember("x", null);

        // Should be able to distinguish null from missing
        assertTrue(bindings.hasMember("x"));
        assertNull(bindings.getMember("x"));
        assertFalse(bindings.hasMember("y"));
    }

    @Test
    void testSmallMapModeGrowsArrays() {
        Bindings bindings = new Bindings();

        // Add more than initial capacity (4)
        for (int i = 0; i < 6; i++) {
            bindings.putMember("key" + i, i);
        }

        assertEquals(6, bindings.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i, bindings.getMember("key" + i));
        }
    }

    @Test
    void testManyBindings() {
        Bindings bindings = new Bindings();

        // Add many bindings
        for (int i = 0; i < 20; i++) {
            bindings.putMember("key" + i, i);
        }

        assertEquals(20, bindings.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(i, bindings.getMember("key" + i));
        }
    }

    @Test
    void testBindValueStorage() {
        Bindings bindings = new Bindings();

        // const binding
        bindings.putMember("x", 42, BindScope.CONST, true);

        // let binding
        bindings.putMember("y", "hello", BindScope.LET, true);

        // var binding (no scope)
        bindings.putMember("z", 100);

        // Get BindValue
        assertEquals(BindScope.CONST, bindings.getBindValue("x").scope);
        assertEquals(BindScope.LET, bindings.getBindValue("y").scope);
        assertNull(bindings.getBindValue("z").scope);  // var has no binding scope
    }

    @Test
    void testClearBindingScope() {
        Bindings bindings = new Bindings();

        bindings.putMember("x", 1, BindScope.LET, true);

        assertNotNull(bindings.getBindValue("x").scope);

        bindings.clearBindingScope("x");

        assertNull(bindings.getBindValue("x").scope);
        assertEquals(1, bindings.getMember("x"));  // value still there
    }

    @Test
    void testCopyConstructorCopiesValuesAndBindValues() {
        Bindings original = new Bindings();

        original.putMember("x", 1, BindScope.LET, true);
        original.putMember("y", 2);

        Bindings copy = new Bindings(original);

        // Values copied
        assertEquals(1, copy.getMember("x"));
        assertEquals(2, copy.getMember("y"));

        // BindValue copied
        assertNotNull(copy.getBindValue("x"));
        assertEquals(BindScope.LET, copy.getBindValue("x").scope);

        // Changes to copy don't affect original
        copy.putMember("x", 100);
        assertEquals(1, original.getMember("x"));
    }

    @Test
    void testMapInterfaceRemove() {
        Bindings bindings = new Bindings();
        bindings.putMember("x", 1);
        bindings.putMember("y", 2);
        bindings.putMember("z", 3);

        bindings.remove("y");

        assertEquals(2, bindings.size());
        assertFalse(bindings.hasMember("y"));
        assertTrue(bindings.hasMember("x"));
        assertTrue(bindings.hasMember("z"));
    }

    @Test
    void testMapInterfaceClear() {
        Bindings bindings = new Bindings();
        bindings.putMember("x", 1);
        bindings.putMember("y", 2);

        bindings.clear();

        assertEquals(0, bindings.size());
        assertTrue(bindings.isEmpty());
    }

    @Test
    void testMapInterfaceKeySet() {
        Bindings bindings = new Bindings();
        bindings.putMember("x", 1);
        bindings.putMember("y", 2);

        assertEquals(2, bindings.keySet().size());
        assertTrue(bindings.keySet().contains("x"));
        assertTrue(bindings.keySet().contains("y"));
    }

    @Test
    void testUpdateExistingKey() {
        Bindings bindings = new Bindings();
        bindings.putMember("x", 1);
        assertEquals(1, bindings.getMember("x"));

        bindings.putMember("x", 2);
        assertEquals(2, bindings.getMember("x"));
        assertEquals(1, bindings.size());  // still one entry
    }

    @Test
    void testUpdateExistingKeyWithBindingScope() {
        Bindings bindings = new Bindings();
        bindings.putMember("x", 1);

        bindings.putMember("x", 2, BindScope.CONST, true);

        assertEquals(2, bindings.getMember("x"));
        assertNotNull(bindings.getBindValue("x").scope);
        assertEquals(BindScope.CONST, bindings.getBindValue("x").scope);
    }

}

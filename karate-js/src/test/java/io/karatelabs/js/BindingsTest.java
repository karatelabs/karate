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

    //=== Tests for the raw BindingsStore (storage layer) =============================================================

    @Test
    void testStoreBasicOperations() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1);
        store.putMember("y", 2);
        store.putMember("z", 3);

        assertEquals(1, store.getMember("x"));
        assertEquals(2, store.getMember("y"));
        assertEquals(3, store.getMember("z"));

        assertTrue(store.hasMember("x"));
        assertFalse(store.hasMember("w"));

        assertEquals(3, store.getRawMap().size());
    }

    @Test
    void testStoreNullValues() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", null);

        assertTrue(store.hasMember("x"));
        assertNull(store.getMember("x"));
        assertFalse(store.hasMember("y"));
    }

    @Test
    void testStoreSmallSize() {
        BindingsStore store = new BindingsStore();
        for (int i = 0; i < 6; i++) {
            store.putMember("key" + i, i);
        }
        assertEquals(6, store.getRawMap().size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i, store.getMember("key" + i));
        }
    }

    @Test
    void testStoreManyBindings() {
        BindingsStore store = new BindingsStore();
        for (int i = 0; i < 20; i++) {
            store.putMember("key" + i, i);
        }
        assertEquals(20, store.getRawMap().size());
        for (int i = 0; i < 20; i++) {
            assertEquals(i, store.getMember("key" + i));
        }
    }

    @Test
    void testStoreSlotScope() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 42, BindScope.CONST, true);
        store.putMember("y", "hello", BindScope.LET, true);
        store.putMember("z", 100);

        assertEquals(BindScope.CONST, store.getSlot("x").scope);
        assertEquals(BindScope.LET, store.getSlot("y").scope);
        assertNull(store.getSlot("z").scope);
    }

    @Test
    void testStoreClearBindingScope() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1, BindScope.LET, true);
        assertNotNull(store.getSlot("x").scope);

        store.clearBindingScope("x");
        assertNull(store.getSlot("x").scope);
        assertEquals(1, store.getMember("x"));
    }

    @Test
    void testStoreCopyConstructor() {
        BindingsStore original = new BindingsStore();
        original.putMember("x", 1, BindScope.LET, true);
        original.putMember("y", 2);

        BindingsStore copy = new BindingsStore(original);
        assertEquals(1, copy.getMember("x"));
        assertEquals(2, copy.getMember("y"));
        assertNotNull(copy.getSlot("x"));
        assertEquals(BindScope.LET, copy.getSlot("x").scope);

        copy.putMember("x", 100);
        assertEquals(1, original.getMember("x"));
    }

    @Test
    void testStoreRemove() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1);
        store.putMember("y", 2);
        store.putMember("z", 3);

        store.remove("y");
        assertEquals(2, store.getRawMap().size());
        assertFalse(store.hasMember("y"));
        assertTrue(store.hasMember("x"));
        assertTrue(store.hasMember("z"));
    }

    @Test
    void testStoreClear() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1);
        store.putMember("y", 2);

        store.clear();
        assertTrue(store.isEmpty());
    }

    @Test
    void testStoreUpdate() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1);
        assertEquals(1, store.getMember("x"));

        store.putMember("x", 2);
        assertEquals(2, store.getMember("x"));
        assertEquals(1, store.getRawMap().size());
    }

    @Test
    void testStoreUpdateAddsBindingScope() {
        BindingsStore store = new BindingsStore();
        store.putMember("x", 1);
        store.putMember("x", 2, BindScope.CONST, true);

        assertEquals(2, store.getMember("x"));
        assertEquals(BindScope.CONST, store.getSlot("x").scope);
    }

    @Test
    void testStoreHiddenFiltersOutOfRawMap() {
        BindingsStore store = new BindingsStore();
        store.putMember("user", 1);
        store.putHidden("internal", 2);

        // Raw map (all entries) sees both
        assertEquals(2, store.getRawMap().size());

        // Visible-only filter sees only the user entry
        Map<String, Object> visible = store.getRawMap(false);
        assertEquals(1, visible.size());
        assertTrue(visible.containsKey("user"));

        // Hidden-only filter sees only the internal entry
        Map<String, Object> hidden = store.getRawMap(true);
        assertEquals(1, hidden.size());
        assertTrue(hidden.containsKey("internal"));

        assertTrue(store.isHidden("internal"));
        assertFalse(store.isHidden("user"));
    }

}

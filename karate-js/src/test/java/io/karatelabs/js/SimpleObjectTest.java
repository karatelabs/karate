package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleObject interface - the primary pattern for exposing Java objects to JavaScript.
 *
 * SimpleObject provides:
 * - jsGet(String) - property accessor
 * - jsKeys() - property enumeration for toMap()/toString()
 * - Default implementations for getMember(), toMap(), toString()
 */
class SimpleObjectTest {

    @Test
    void testDev() {

    }

    // =================================================================================================================
    // Basic SimpleObject Usage
    // =================================================================================================================

    @Test
    void testSimpleObjectPropertyAccess() {
        Engine engine = new Engine();

        SimpleObject person = name -> switch (name) {
            case "name" -> "John";
            case "age" -> 30;
            default -> null;
        };

        engine.put("person", person);
        assertEquals("John", engine.eval("person.name"));
        assertEquals(30, engine.eval("person.age"));
    }

    @Test
    void testSimpleObjectWithMethods() {
        Engine engine = new Engine();

        SimpleObject utils = name -> switch (name) {
            case "greet" -> (JsCallable) (ctx, args) -> "Hello, " + args[0];
            case "add" -> (JsInvokable) args -> ((Number) args[0]).intValue() + ((Number) args[1]).intValue();
            default -> null;
        };

        engine.put("utils", utils);
        assertEquals("Hello, World", engine.eval("utils.greet('World')"));
        assertEquals(5, engine.eval("utils.add(2, 3)"));
    }

    @Test
    void testSimpleObjectWithJsKeys() {
        Engine engine = new Engine();

        SimpleObject config = new SimpleObject() {
            @Override
            public Collection<String> jsKeys() {
                return List.of("host", "port", "debug");
            }

            @Override
            public Object jsGet(String name) {
                return switch (name) {
                    case "host" -> "localhost";
                    case "port" -> 8080;
                    case "debug" -> true;
                    default -> null;
                };
            }
        };

        engine.put("config", config);

        // Property access works
        assertEquals("localhost", engine.eval("config.host"));
        assertEquals(8080, engine.eval("config.port"));
        assertEquals(true, engine.eval("config.debug"));

        // toMap() uses jsKeys()
        var map = config.toMap();
        assertEquals(3, map.size());
        assertEquals("localhost", map.get("host"));
        assertEquals(8080, map.get("port"));
        assertEquals(true, map.get("debug"));
    }

    @Test
    void testSimpleObjectWithoutJsKeysSerializesToEmpty() {
        // SimpleObject without jsKeys() override serializes to {}
        SimpleObject minimal = name -> switch (name) {
            case "secret" -> "value";
            default -> null;
        };

        // Property access works
        Engine engine = new Engine();
        engine.put("obj", minimal);
        assertEquals("value", engine.eval("obj.secret"));

        // But toMap() is empty (jsKeys() returns empty by default)
        assertTrue(minimal.toMap().isEmpty());
    }

    // =================================================================================================================
    // Nested SimpleObject
    // =================================================================================================================

    @Test
    void testNestedSimpleObject() {
        Engine engine = new Engine();

        SimpleObject inner = name -> switch (name) {
            case "value" -> 42;
            default -> null;
        };

        SimpleObject outer = name -> switch (name) {
            case "inner" -> inner;
            default -> null;
        };

        engine.put("outer", outer);
        assertEquals(42, engine.eval("outer.inner.value"));
    }

    // =================================================================================================================
    // Boundary Conversion Tests (moved from JavaMirrorTest)
    // Arguments passed to SimpleObject methods are converted at JS/Java boundary
    // =================================================================================================================

    @Test
    void testUndefinedPassedToSimpleObjectMethodBecomesNull() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("""
                var user = { name: 'John' };
                utils.captureArg(user.version);
                """);

        assertEquals(1, capturedArgs.size());
        assertNull(capturedArgs.get(0), "undefined should be converted to null at JS/Java boundary");
    }

    @Test
    void testExplicitUndefinedPassedToSimpleObjectMethodBecomesNull() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("utils.captureArg(undefined)");

        assertEquals(1, capturedArgs.size());
        assertNull(capturedArgs.get(0), "explicit undefined should be converted to null");
    }

    @Test
    void testNullPassedToSimpleObjectMethodRemainsNull() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("utils.captureArg(null)");

        assertEquals(1, capturedArgs.size());
        assertNull(capturedArgs.get(0), "null should remain null");
    }

    @Test
    void testDefinedValuePassedToSimpleObjectMethodWorks() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("""
                var user = { name: 'John', version: 42 };
                utils.captureArg(user.version);
                """);

        assertEquals(1, capturedArgs.size());
        assertEquals(42, capturedArgs.get(0), "defined value should pass through normally");
    }

    @Test
    void testUndefinedPassedToInvokableBecomesNull() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaInvokable) args -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("""
                var user = { name: 'John' };
                utils.captureArg(user.version);
                """);

        assertEquals(1, capturedArgs.size());
        assertNull(capturedArgs.get(0), "undefined should be converted to null for Invokable");
    }

    @Test
    void testMultipleArgsWithUndefinedBecomesNull() {
        Engine engine = new Engine();
        List<Object[]> capturedCalls = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("updateUser".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedCalls.add(args);
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("""
                var user = { userId: '123', name: 'John' };
                utils.updateUser(user.userId, user.name, user.version);
                """);

        assertEquals(1, capturedCalls.size());
        Object[] args = capturedCalls.get(0);
        assertEquals(3, args.length);
        assertEquals("123", args[0]);
        assertEquals("John", args[1]);
        assertNull(args[2], "undefined version should be converted to null");
    }

    @Test
    void testJsDatePassedToSimpleObjectBecomesDate() {
        Engine engine = new Engine();
        List<Object> capturedArgs = new ArrayList<>();

        SimpleObject utils = name -> {
            if ("captureArg".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedArgs.add(args.length > 0 ? args[0] : "NO_ARG");
                    return null;
                };
            }
            return null;
        };

        engine.put("utils", utils);
        engine.eval("utils.captureArg(new Date(0))");

        assertEquals(1, capturedArgs.size());
        assertInstanceOf(Date.class, capturedArgs.get(0), "JsDate should be converted to java.util.Date");
        assertEquals(0L, ((Date) capturedArgs.get(0)).getTime());
    }

    // =================================================================================================================
    // Dynamic/Lazy Values in SimpleObject
    // Note: jsGet() is called on every property access, so values are inherently lazy
    // =================================================================================================================

    @Test
    void testPropertyAccessIsInherentlyLazy() {
        // jsGet() is called on every access - no Supplier needed
        Engine engine = new Engine();
        int[] counter = {0};

        SimpleObject lazy = name -> switch (name) {
            case "count" -> ++counter[0];  // computed fresh each access
            default -> null;
        };

        engine.put("lazy", lazy);

        // Each property access calls jsGet()
        assertEquals(1, engine.eval("lazy.count"));
        assertEquals(2, engine.eval("lazy.count"));
        assertEquals(3, engine.eval("lazy.count"));
    }

    // =================================================================================================================
    // Exception Handling in SimpleObject
    // =================================================================================================================

    @Test
    void testSimpleObjectMethodThrowsException() {
        Engine engine = new Engine();
        engine.put("demo", new DemoSimpleObject());

        try {
            engine.eval("demo.doWorkException()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("failed"));
        }
    }

    @Test
    void testNestedSimpleObjectThrowsException() {
        Engine engine = new Engine();
        engine.put("demo", new DemoSimpleObject());

        try {
            engine.eval("demo.inner.doWorkException");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("failed"));
        }
    }

}

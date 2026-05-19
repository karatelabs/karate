package io.karatelabs.core;

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.http.ErrorHttpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for KarateJs engine functionality.
 * Note: JS HTTP client tests are in ServerIntegrationTest to share the test server.
 */
class KarateJsTest {

    @Test
    void testJsClientNoServerConnection() {
        KarateJs context = new KarateJs(Resource.path(""), new ErrorHttpClient());
        String js = """
                var http = karate.http('http://localhost:99');
                var response = http.path('cats').post({ name: 'Billie' }).body;
                """;
        try {
            context.engine.eval(js);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("expression: http.path('cats').post({name:'Billie'}) - failed"));
        }
    }

    @Test
    void testRead() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        String js = """
                var cat = read('json/cat.json');
                """;
        context.engine.eval(js);
        assertEquals(Map.of("name", "Billie", "age", 5), context.engine.get("cat"));
    }

    @Test
    void testReadScalarJsonString() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var token = read('json/scalar-string.json');");
        assertEquals("abc123", context.engine.get("token"));
    }

    @Test
    void testReadScalarJsonNumber() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var num = read('json/scalar-number.json');");
        assertEquals(42, context.engine.get("num"));
    }

    @Test
    void testReadScalarJsonBoolean() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var flag = read('json/scalar-boolean.json');");
        assertEquals(true, context.engine.get("flag"));
    }

    @Test
    void testMatch() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        String js = """
                var cat = read('json/cat.json');
                var result = match(cat).contains({ name: 'Billie' });
                """;
        context.engine.eval(js);
        assertEquals(Json.of("{ pass: true, message: null }").asMap(), context.engine.get("result"));
    }

    @Test
    void testMatchFail() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        String js = """
                var cat = read('json/cat.json');
                match(cat).equals({ name: 'Billie', age: 4 });
                """;
        try {
            context.engine.eval(js);
        } catch (Exception e) {
            assertEquals("""
                    js failed:
                    ==========
                    match(cat).equals({ name: 'Billie', age: 4 });
                    2:1 match failed: EQUALS
                      $ | not equal | match failed for name: 'age' (MAP:MAP)
                      {"name":"Billie","age":5}
                      {"name":"Billie","age":4}

                        $.age | not equal (NUMBER:NUMBER)
                        5
                        4
                    ----------
                    """, e.getMessage());
        }
    }

    @Test
    void testSysenv() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        // PATH is virtually always set on the platforms we ship to.
        context.engine.eval("var p = karate.sysenv('PATH');");
        Object p = context.engine.get("p");
        assertTrue(p instanceof String && !((String) p).isEmpty(),
                "karate.sysenv('PATH') should return the OS PATH");
        // Unset variable returns null.
        context.engine.eval("var missing = karate.sysenv('__KARATE_SHOULD_NEVER_BE_SET_98765__');");
        assertEquals(null, context.engine.get("missing"));
    }

    @Test
    void testSysenvDefault() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        // Unset → default returned.
        context.engine.eval("var v = karate.sysenv('__KARATE_SHOULD_NEVER_BE_SET_98765__', 'fallback');");
        assertEquals("fallback", context.engine.get("v"));
        // Set → real value wins over default.
        context.engine.eval("var p = karate.sysenv('PATH', 'fallback');");
        Object p = context.engine.get("p");
        assertTrue(p instanceof String && !"fallback".equals(p),
                "real env var should win over default");
    }

    @Test
    void testSysprop() {
        System.setProperty("__karate_sysprop_test__", "hello");
        try {
            KarateJs context = new KarateJs(Resource.path("src/test/resources"));
            context.engine.eval("var v = karate.sysprop('__karate_sysprop_test__');");
            assertEquals("hello", context.engine.get("v"));
            // Unset → null.
            context.engine.eval("var u = karate.sysprop('__karate_sysprop_unset_99999__');");
            assertEquals(null, context.engine.get("u"));
            // Unset with default → default.
            context.engine.eval("var d = karate.sysprop('__karate_sysprop_unset_99999__', 'fallback');");
            assertEquals("fallback", context.engine.get("d"));
            // Set with default → real value wins.
            context.engine.eval("var w = karate.sysprop('__karate_sysprop_test__', 'fallback');");
            assertEquals("hello", context.engine.get("w"));
        } finally {
            System.clearProperty("__karate_sysprop_test__");
        }
    }

}

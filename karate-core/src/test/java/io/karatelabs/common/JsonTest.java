package io.karatelabs.common;

import io.karatelabs.match.Match;
import io.karatelabs.match.Result;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    static final Logger logger = LoggerFactory.getLogger(JsonTest.class);

    private void match(Json json, String expected) {
        Result mr = Match.evaluate(json.value(), null, null)._equals(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testParsingParentAndLeafName() {
        assertEquals(Pair.of("", "$"), Json.toParentAndLeaf("$"));
        assertEquals(Pair.of("$", "foo"), Json.toParentAndLeaf("$.foo"));
        assertEquals(Pair.of("$", "['foo']"), Json.toParentAndLeaf("$['foo']"));
        assertEquals(Pair.of("$.foo", "bar"), Json.toParentAndLeaf("$.foo.bar"));
        assertEquals(Pair.of("$.foo", "['bar']"), Json.toParentAndLeaf("$.foo['bar']"));
        assertEquals(Pair.of("$.foo", "bar[0]"), Json.toParentAndLeaf("$.foo.bar[0]"));
        assertEquals(Pair.of("$.foo", "['bar'][0]"), Json.toParentAndLeaf("$.foo['bar'][0]"));
        assertEquals(Pair.of("$.foo[2]", "bar[0]"), Json.toParentAndLeaf("$.foo[2].bar[0]"));
        assertEquals(Pair.of("$.foo[2]", "['bar'][0]"), Json.toParentAndLeaf("$.foo[2]['bar'][0]"));
        assertEquals(Pair.of("$.foo[2]", "bar"), Json.toParentAndLeaf("$.foo[2].bar"));
        assertEquals(Pair.of("$.foo[2]", "['bar']"), Json.toParentAndLeaf("$.foo[2]['bar']"));
    }

    @Test
    void testSetAndRemove() {
        Json json = Json.object();
        match(json, "{}");
        assertTrue(json.pathExists("$"));
        assertFalse(json.pathExists("$.foo"));
        json.set("foo", "bar");
        match(json, "{ foo: 'bar' }");
        assertTrue(json.pathExists("$.foo"));
        assertFalse(json.pathExists("bar.baz"));
        assertFalse(json.pathExists("bar"));
        json.set("bar.baz", "ban");
        match(json, "{ foo: 'bar', bar: { baz: 'ban' } }");
        json.remove("foo");
        match(json, "{ bar: { baz: 'ban' } }");
        json.remove("bar.baz");
        match(json, "{ bar: { } }");
        json.remove("bar");
        match(json, "{}");
        json.set("foo.bar", "[1, 2]");
        match(json, "{ foo: { bar: [1, 2] } }");
        json.set("foo.bar[0]", 9);
        match(json, "{ foo: { bar: [9, 2] } }");
        json.set("foo.bar[]", 8);
        match(json, "{ foo: { bar: [9, 2, 8] } }");
        json.remove("foo.bar[0]");
        match(json, "{ foo: { bar: [2, 8] } }");
        json.remove("foo.bar[1]");
        match(json, "{ foo: { bar: [2] } }");
        json.remove("foo.bar");
        match(json, "{ foo: {} }");
        json.remove("foo");
        match(json, "{}");
        json = Json.of("[]");
        match(json, "[]");
        json.set("$[0].foo", "[1, 2]");
        match(json, "[{ foo: [1, 2] }]");
        json.set("$[1].foo", "[3, 4]");
        match(json, "[{ foo: [1, 2] }, { foo: [3, 4] }]");
        json.remove("$[0]");
        match(json, "[{ foo: [3, 4] }]");
        json = Json.object().set("$.foo[]", "a");
        match(json, "{ foo: ['a'] }");
    }

    @Test
    void testSetNestedObject() {
        Json json = Json.of("{ name: 'Wild', kitten: null }");
        json.set("$.kitten.name", "Bob");
        match(json, "{ name: 'Wild', kitten: { name: 'Bob' } }");
    }

    @Test
    void testSetNestedArray() {
        Json json = Json.of("[]");
        json.set("$[0].name.first", "Bob");
        match(json, "[{ name: { first: 'Bob' } }]");
    }

    @Test
    void testSetMultipleNestedArray() {
        Json json = Json.object();
        json.set("first.second[0].third[0].fourth[0]", "hello");
        match(json, "{ first :{ second :[{ third :[{ fourth :[ hello ]}]}]}}");
        json.set("first.fifth[0].sixth[1].seventh[2]", "hello");
        match(json, "{ first :{ second :[{ third :[{ fourth :[ 'hello' ]}]}], fifth :[{ sixth :[null,{ seventh :[null,null, hello ]}]}]}}");
    }

    @Test
    void testJsonApi() {
        Json json = Json.of("{ a: 1, b: { c: 2 } }");
        assertEquals(2, (int) json.get("b.c"));
        assertEquals(2, (int) json.getOptional("b.c").get());
        assertNull(json.get("b.d", null));
        assertEquals(3, (int) json.get("b.d", 3));
        try {
            json.getOptional("b.d").get();
            fail("expected exception");
        } catch (Exception e) {
            assertInstanceOf(NoSuchElementException.class, e);
        }
    }

    @Test
    void testGetAsJson() {
        Json json = Json.of("{ a: { b: [1, 2], c: { d: 1, e: 2 } } }");
        Json child1 = json.getJson("a.b");
        assertTrue(child1.isArray());
        assertEquals(Arrays.asList(1, 2), child1.asList());
        Json child2 = json.getJson("a.c");
        assertFalse(child2.isArray());
        Map expected = Json.of("{ d: 1, e: 2 }").asMap();
        assertEquals(expected, child2.asMap());
    }

    @Test
    void testGetAsJava() {
        Map<String, Object> map = Json.of("{ a: 1 }").value();
        assertEquals(map, Collections.singletonMap("a", 1));
        List<Object> list = Json.of("[ 1, 2 ]").value();
        assertEquals(list, Arrays.asList(1, 2));
    }

    @Test
    void testJsonParseStrict() {
        Exception e = assertThrows(RuntimeException.class, () -> {
            Json.parseStrict("{ hello: 'world' }");
        });
        assertTrue(e.getMessage().startsWith("invalid json"));
        Object o = Json.parseStrict("""
                    { "hello": "world" }
                """);
        assertEquals(Map.of("hello", "world"), o);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testJsonParseStrictKeepOrder() {
        String json = """
                { "z": 1, "a": 2, "m": 3 }
                """;
        Map<String, Object> ordered = (Map<String, Object>) Json.parseStrict(json, true);
        // Verify order is preserved (z, a, m - not alphabetically sorted)
        List<String> keys = new ArrayList<>(ordered.keySet());
        assertEquals(Arrays.asList("z", "a", "m"), keys);
    }

    @Test
    void testJsonParseLenient() {
        // Lenient accepts unquoted keys and single quotes
        Object o = Json.parseLenient("{ hello: 'world' }");
        assertEquals(Map.of("hello", "world"), o);
        // Still throws on truly invalid JSON
        Exception e = assertThrows(RuntimeException.class, () -> {
            Json.parseLenient("not json at all");
        });
        assertTrue(e.getMessage().startsWith("invalid json"));
    }

    @Test
    void testJsonStringifyStrict() {
        Object o = Json.of("{redirect:{url:'/index'}}").value();
        String s = Json.stringifyStrict(o);
        assertEquals("{\"redirect\":{\"url\":\"/index\"}}", s);
    }

    @Test
    void testJsonFormat() {
        Object o = Json.of("{redirect:{url:'/index'}}").value();
        String s = StringUtils.formatJson(o, false, false, false);
        // ES6-compliant compact JSON (no spaces)
        assertEquals("{\"redirect\":{\"url\":\"/index\"}}", s);
    }

    @Test
    void testJsonCircularReferences() {
        Map<String, Object> map1 = new HashMap<>();
        Map<String, Object> map2 = new HashMap<>();
        map2.put("b", map1);
        map1.put("a", map2);
        Object o = Json.copy(map1, true, true);
        String s = Json.stringifyStrict(o);
        assertEquals("{\"a\":{\"b\":\"#java.util.HashMap\"}}", s);
    }

    @Test
    void testJsonOfNullThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            Json.of(null);
        });
        assertTrue(e.getMessage().contains("must not be null"));
    }

    @Test
    void testJsonOfEmptyStringThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            Json.of("");
        });
        assertTrue(e.getMessage().contains("must not be empty or blank"));
    }

    @Test
    void testJsonOfBlankStringThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            Json.of("   ");
        });
        assertTrue(e.getMessage().contains("must not be empty or blank"));
    }

}

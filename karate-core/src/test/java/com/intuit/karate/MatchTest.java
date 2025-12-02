package com.intuit.karate;

import static com.intuit.karate.Match.Type.*;
import static org.junit.jupiter.api.Assertions.*;

import com.intuit.karate.Match;
import com.intuit.karate.Match.Type;
import com.intuit.karate.graal.JsEngine;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intuit.karate.MatchOperation;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pthomas3
 */
class MatchTest {

    static final Logger logger = LoggerFactory.getLogger(MatchTest.class);

    private static final boolean FAILS = true;

    private void match(Object actual, String mt, Object expected) {
        match(actual, Match.Type.valueOf(mt), expected, false);
    }

    private void match(Object actual, Match.Type mt, Object expected) {
        match(actual, mt, expected, false);
    }

    String message;

    private void message(String expected) {
        assertEquals(expected, message);
    }

    private void log() {
        logger.debug("{}", message);
    }

    private void match(Object actual, String mt, Object expected, boolean fails) {
        match(actual, Type.valueOf(mt), expected, fails);
    }

    private void match(Object actual, Match.Type mt, Object expected, boolean fails) {
        Match.Result mr = Match.evaluate(actual).is(mt, expected);
        message = mr.message;
        if (!fails) {
            assertTrue(mr.pass, mr.message);
        } else {
            assertFalse(mr.pass);
        }
    }

    @Test
    void testApi() {
        assertTrue(Match.that(null).isEqualTo(null).pass);
    }

    @Test
    void testNull() {
        match(null, EQUALS, null);
        match("", EQUALS, null, FAILS);
        message("""
                match failed: EQUALS
                  $ | data types don't match (STRING:NULL)
                  ''
                  null
                """);
        match("", NOT_EQUALS, null);
        match(null, NOT_EQUALS, null, FAILS);
    }

    @Test
    void testBoolean() {
        match(true, EQUALS, true);
        match(false, EQUALS, false);
        match(true, EQUALS, false, FAILS);
        match(true, NOT_EQUALS, false);
        match(true, NOT_EQUALS, true, FAILS);
    }

    @Test
    void testNumber() {
        match(1, EQUALS, 1);
        match(0.1, EQUALS, .100);
        match(100, EQUALS, 200, FAILS);
        match(100, NOT_EQUALS, 2000);
        match(300, NOT_EQUALS, 300, FAILS);
    }

    @Test
    void testBigDecimal() {
        match(new BigDecimal("1000"), EQUALS, 1000);
        match(300, NOT_EQUALS, new BigDecimal("300"), FAILS);
    }

    @Test
    void testString() {
        match("hello", EQUALS, "hello");
        match("foo", EQUALS, "bar", FAILS);
    }

    @Test
    void testStringContains() {
        match("hello", CONTAINS, "hello");
        match("hello", NOT_CONTAINS, "hello", FAILS);
        match("foobar", CONTAINS, "bar");
        match("foobar", CONTAINS, "baz", FAILS);
        match("foobar", NOT_CONTAINS, "baz");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testStringStartingWithHash(String matchType) {
        match("#bob", matchType, "#bob");
    }

    @Test
    void testBytes() {
        match("hello".getBytes(), EQUALS, "hello".getBytes());
        match("hello".getBytes(), NOT_EQUALS, "helloo".getBytes());
        match("hello".getBytes(), NOT_EQUALS, "hello".getBytes(), FAILS);
    }

    @Test
    void testJavaSet() {
        Set<String> set = new HashSet();
        set.add("foo");
        set.add("bar");
        Match.that(set).containsOnly("['foo', 'bar']");
    }

    @Test
    void testJavaArray() {
        String[] strArray = new String[]{"foo", "bar"};
        Match.that(strArray).containsOnly("['foo', 'bar']");
        int[] intArray = new int[]{1, 2, 3};
        Match.that(intArray).isEqualTo("[1, 2, 3]");
    }

    @Test
    void testNotEquals() {
        match("[1, 2]", NOT_EQUALS, "#[1]");
        match("[1, 2]", NOT_EQUALS, "#[]? _ > 2");
        log();
    }

    @Test
    void testList() {
        match("[1, 2, 3]", EQUALS, "[1, 2, 3]");
        match("[1, 2, 3]", NOT_EQUALS, "[1, 2, 4]");
        match("[1, 2]", EQUALS, "[1, 2, 4]", FAILS);
        match("[1, 2, 3]", CONTAINS, "[1, 2, 3]");
        match("[1, 2, 3]", CONTAINS_ONLY, "[1, 2, 3]");
        match("[1, 2, 3]", CONTAINS_ONLY, "[3, 2, 1]");
        match("[1, 5, 10]", CONTAINS_ONLY, "[10, 5, 1]");
        match("[4, 2]", CONTAINS_ONLY, "[2, 4]");
        match("[5]", CONTAINS_ONLY, "[5]");
        match("[4, 4]", CONTAINS_ONLY, "[4, 4]");
        match("[1, 2, 2]", CONTAINS_ONLY, "[2, 2, 1]");
        match("[1, 2, 3]", CONTAINS_ONLY, "[2, 2, 3]", FAILS);
        match("[2, 3, 2]", CONTAINS_ONLY, "[2, 2, 3]");
        match("[2, 2, 3]", CONTAINS_ONLY, "[1, 2, 3]", FAILS);
        match("[1, 4, 7]", CONTAINS_ONLY, "[4, 7]", FAILS);
        match("[1, 2, 3]", CONTAINS, "[1, 2, 4]", FAILS);
        match("[1, 2, 3]", NOT_CONTAINS, "[1, 2, 4]");
        match("[1, 2, 3]", CONTAINS_ANY, "[1, 2, 4]");
        match("[1, 2, 3]", CONTAINS_ANY, "[1, 2, 4, 5]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", EQUALS, "[{ a: 1 }, { b: 2 }, { c: 3 }]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", EQUALS, "[{ a: 1 }, { b: 2 }, { c: 4 }]", FAILS);
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS, "[{ a: 1 }, { b: 2 }, { c: 3 }]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS_ONLY, "[{ a: 1 }, { b: 2 }, { c: 3 }]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS, "[{ a: 1 }, { c: 3 }]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS_ANY, "[{ a: 9 }, { c: 3 }]");
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS_ANY, "[{ a: 9 }, { c: 9 }]", FAILS);
        match("[{ a: 1 }, { b: 2 }, { c: 3 }]", CONTAINS_DEEP, "[{ a: 1 }, { c: 3 }]");
        match("[{ a: 1 }, { b: [1, 2, 3] }]", CONTAINS_DEEP, "[{ b: [2] }]");
        match("{ a: { foo: 'bar' } }", CONTAINS_DEEP, "{ a: '#object' }");
    }

    @Test
    void testContainsOnlyDeep() {
        match("{ a: [1, 2, 3] }", CONTAINS_ONLY_DEEP, "{ a: [1, 2, 3], b: 4 }", FAILS);
        match("{ a: [1, 2, 3] }", CONTAINS_ONLY_DEEP, "{ a: [3, 2, 1] }");
        match("[{a: [1, 2, 3]}]", CONTAINS_ONLY_DEEP, "[{a: [3, 2, 1]}]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTAINS", "CONTAINS_DEEP"})
    void testListContains(String containsType) {
        match("['foo', 'bar']", containsType, "baz", FAILS);
        message("""
                match failed: %s
                  $ | actual does not contain expected | actual array does not contain expected item - baz (LIST:STRING)
                  ["foo","bar"]
                  'baz'
                
                    $[1] | not equal (STRING:STRING)
                    'bar'
                    'baz'
                
                    $[0] | not equal (STRING:STRING)
                    'foo'
                    'baz'
                """.formatted(containsType));
        match("['foo', 'bar']", containsType, "['baz']", FAILS);
        message("""
                match failed: %s
                  $ | actual does not contain expected | actual array does not contain expected item - baz (LIST:LIST)
                  ["foo","bar"]
                  ["baz"]
                
                    $[1] | not equal (STRING:STRING)
                    'bar'
                    'baz'
                
                    $[0] | not equal (STRING:STRING)
                    'foo'
                    'baz'
                """.formatted(containsType));
    }

    @Test
    void testListContainsRegex() {
        match("['foo', 'bar']", CONTAINS, "#regex .{3}");
        match("['foo', 'bar']", CONTAINS_DEEP, "#regex .{3}");
        match("['foo', 'bar']", CONTAINS_ANY, "#regex .{3}");
        match("['foo', 'bar']", CONTAINS_ANY_DEEP, "#regex .{3}");
        match("{ array: ['foo', 'bar'] }", EQUALS, "{ array: '#[] #regex .{3}' }");
        match("{ array: ['foo', 'bar'] }", CONTAINS, "{ array: '#[] #regex .{3}' }");
        match("{ array: ['foo', 'bar'] }", CONTAINS_DEEP, "{ array: '#[] #regex .{3}' }");
        match("{ array: ['foo', 'bar'] }", CONTAINS_DEEP, "{ array: '#array' }");
        match("{ array: ['foo', 'bar'] }", CONTAINS_ANY, "{ array: '#[] #regex .{3}' }");
        match("{ array: ['foo', 'bar'] }", CONTAINS_ANY_DEEP, "{ array: '#[] #regex .{3}' }");

        match("['foo', 'barr']", CONTAINS, "#regex .{4}");
        match("['foo', 'barr']", CONTAINS_ANY, "#regex .{4}");

        match("['foo', 'bar']", CONTAINS, "#regex .{4}", FAILS);
        message("""
                match failed: CONTAINS
                  $ | actual does not contain expected | actual array does not contain expected item - #regex .{4} (LIST:STRING)
                  ["foo","bar"]
                  '#regex .{4}'
                
                    $[1] | regex match failed (STRING:STRING)
                    'bar'
                    '#regex .{4}'
                
                    $[0] | regex match failed (STRING:STRING)
                    'foo'
                    '#regex .{4}'
                """);

    }

    @Test
    void testListNotContains() {
        match("['foo', 'bar']", NOT_CONTAINS, "baz");
        match("['foo', 'bar']", NOT_CONTAINS, "bar", FAILS);
        message("""
                match failed: NOT_CONTAINS
                  $ | actual contains expected (LIST:STRING)
                  ["foo","bar"]
                  'bar'
                
                    $[0] | not equal (STRING:STRING)
                    'foo'
                    'bar'
                """);
        match(
                "[{ foo: 1 }, { foo: 2 }, { foo: 3 }]",
                CONTAINS,
                "[{ foo: 0 }, { foo: 2 }, { foo: 3 }]",
                FAILS);
        message("""
                match failed: CONTAINS
                  $ | actual does not contain expected | actual array does not contain expected item - {"foo":0} (LIST:LIST)
                  [{"foo":1},{"foo":2},{"foo":3}]
                  [{"foo":0},{"foo":2},{"foo":3}]
                
                    $[2] | not equal | match failed for name: 'foo' (MAP:MAP)
                    {"foo":3}
                    {"foo":0}
                
                      $[2].foo | not equal (NUMBER:NUMBER)
                      3
                      0
                
                        $[1] | not equal | match failed for name: 'foo' (MAP:MAP)
                        {"foo":2}
                        {"foo":0}
                
                          $[1].foo | not equal (NUMBER:NUMBER)
                          2
                          0
                
                            $[0] | not equal | match failed for name: 'foo' (MAP:MAP)
                            {"foo":1}
                            {"foo":0}
                
                              $[0].foo | not equal (NUMBER:NUMBER)
                              1
                              0
                """); // TODO improve error message for this case
    }

    @Test
    void testEach() {
        match("[1, 2, 3]", EACH_EQUALS, "#number");
        match("[1, 2, 3]", EACH_EQUALS, "#number? _ > 0");
        match("[1, 2, 3]", EACH_EQUALS, "#number? _ < 2", FAILS);
        String expected = """
                match failed: EACH_EQUALS
                  $ | match each failed at index 1 (LIST:STRING)
                  [1,2,3]
                  '#number? _ < 2'
                
                    $[1] | evaluated to 'false' (NUMBER:STRING)
                    2
                    '#number? _ < 2'
                """;
        message(expected);
        match("[1, 'a', 3]", EACH_EQUALS, "#number", FAILS);
        expected = """
                match failed: EACH_EQUALS
                  $ | match each failed at index 1 (LIST:STRING)
                  [1,"a",3]
                  '#number'
                
                    $[1] | not a number (STRING:STRING)
                    'a'
                    '#number'
                """;
        message(expected);
        match("[{ a: 1 }, { a: 2 }]", EACH_EQUALS, "#object");
        match("[{ a: 1 }, { a: 2 }]", EACH_EQUALS, "{ a: '#number' }");
    }

    @Test
    void testEachEmpty() {
        match("[]", EACH_EQUALS, "#number", FAILS);
        message("""
                match failed: EACH_EQUALS
                  $ | match each failed, empty array / list (LIST:STRING)
                  []
                  '#number'
                """);
    }

    @Test
    void testEachWithMagicVariables() {
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_EQUALS, "{ a: '#number', b: '#(_$.a * 2)' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_EQUALS, "{ a: '#number', b: '#? _ == _$.a * 2' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_CONTAINS, "{ b: '#(_$.a * 2)' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_CONTAINS, "{ b: '#? _ == _$.a * 2' }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testArray(String matchType) {
        match("[{ a: 1 }, { a: 2 }]", matchType, "#[2]");
        match("[{ a: 1 }, { a: 2 }]", matchType, "#[] #object");
    }

    @Test
    void testIssue2515() {
        Json cat = Json.of(
                """
                        {
                          name: 'Billie',
                          kittens: [
                            { id: 23, name: 'Bob', bla: [{ b: '1'}] },
                            { id: 42, name: 'Wild' }
                          ]
                        }
                        """);
        Json expectedKittens1 = Json.of("[{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob', bla: [{ b: '1'}]}]");
        JsEngine.global().put("expectedKittens1", expectedKittens1.asList());
        match(cat.asMap(), EQUALS, Json.of("{ name: 'Billie', kittens: '#(^^expectedKittens1)' }").asMap());

        Json expectedKittens2 = Json.of("[{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob', bla: { b: '1'}}]");
        JsEngine.global().put("expectedKittens2", expectedKittens2.asList());
        match(cat.asMap(), EQUALS, Json.of("{ name: 'Billie', kittens: '#(^^expectedKittens2)' }").asMap(), true);
    }

    @Test
    void testIssue2727() {
        Json pattern1 = Json.of("{ a: 1, b: [ { c: 3 } ] }");
        JsEngine.global().put("pattern1", pattern1.asMap());
        // should work
        match("[ { a: 1, b: [ { c: 3, d: 4 } ] } ]", CONTAINS, "#(^+pattern1)");
        // works
        Json pattern2 = Json.of("{ c: 3 }");
        JsEngine.global().put("pattern2", pattern2.asMap());
        match("[ { a: 1, b: [ { c: 3, d: 4 } ] } ]", CONTAINS, "{ a: 1, b: [ '#(^pattern2)' ] }");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "EQUALS;EACH_EQUALS",
            "CONTAINS;EACH_CONTAINS",
            "CONTAINS_DEEP;EACH_CONTAINS_DEEP"}, delimiter = ';')
    void testSchema(String matchType, String matchEachType) {
        Json json = Json.of("{ a: '#number' }");
        Map map = json.asMap();
        match("[{ a: 1 }, { a: 2 }]", matchEachType, map);
        JsEngine.global().put("schema", map);
        match("[{ a: 1 }, { a: 2 }]", matchType, "#[] schema");
        match("{ a: 'x', b: { c: 'y' } }", matchType, "{ a: '#string', b: { c: '#string' } }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testSchemaOptionalObject(String matchType) {
        Json part = Json.of("{ bar: '#string' }");
        JsEngine.global().put("part", part.asMap());
        match("{ foo: null }", matchType, "{ foo: '##(bar)' }");
    }


    @Test
    void testMap() {
        match("{ a: 1, b: 2, c: 3 }", CONTAINS, "{}");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS, "{ b: 2, c: 3, a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_ONLY, "{ b: 2, c: 3, a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_DEEP, "{ c: 3, a: 1 }");
        match("{ a: 1, b: 2, c: [1, 2] }", CONTAINS_DEEP, "{ a: 1, c: [2] }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS, "{ b: 2 }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS, "{ }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_ANY, "{ }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_DEEP, "{ }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_ANY, "{ z: 9, b: 2 }");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS, "{ z: 9, x: 2 }", FAILS);

        message("""
                match failed: CONTAINS
                  $ | actual does not contain expected | actual does not contain key - 'z' (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"z":9,"x":2}
                
                """);
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_ANY, "{ z: 9, x: 2 }", FAILS);
        message("""
                match failed: CONTAINS_ANY
                  $ | actual does not contain expected | no key-values matched (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"z":9,"x":2}
                
                    $.x | data types don't match (NULL:NUMBER)
                    null
                    2
                
                    $.z | data types don't match (NULL:NUMBER)
                    null
                    9
                """);
        match("{ a: 1, b: 2, c: 3 }", NOT_CONTAINS, "{ a: 1 }", FAILS);
        message("""
                match failed: NOT_CONTAINS
                  $ | actual contains expected (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"a":1}
                """);
        match("{ a: 1, b: 2, c: 3 }", NOT_CONTAINS, "{}");

    }

    @Test
    void testJsonFailureMessages() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ a: 1, b: 9, c: 3 }", FAILS);
        String expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'b' (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"a":1,"b":9,"c":3}
                
                    $.b | not equal (NUMBER:NUMBER)
                    2
                    9
                """;
        message(expected);
        match("{ a: { b: { c: 1 } } }", EQUALS, "{ a: { b: { c: 2 } } }", FAILS);
        expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'a' (MAP:MAP)
                  {"a":{"b":{"c":1}}}
                  {"a":{"b":{"c":2}}}
                
                    $.a | not equal | match failed for name: 'b' (MAP:MAP)
                    {"b":{"c":1}}
                    {"b":{"c":2}}
                
                      $.a.b | not equal | match failed for name: 'c' (MAP:MAP)
                      {"c":1}
                      {"c":2}
                
                        $.a.b.c | not equal (NUMBER:NUMBER)
                        1
                        2
                """;
        message(expected);
    }

    @Test
    void testXmlFailureMessages() {
        match("<a><b><c>1</c></b></a>", EQUALS, "<a><b><c>2</c></b></a>", FAILS);
        String expected = """
                match failed: EQUALS
                  / | not equal | match failed for name: 'a' (XML:XML)
                  <a><b><c>1</c></b></a>
                  <a><b><c>2</c></b></a>
                
                    /a | not equal | match failed for name: 'b' (MAP:MAP)
                    <b><c>1</c></b>
                    <b><c>2</c></b>
                
                      /a/b | not equal | match failed for name: 'c' (MAP:MAP)
                      <c>1</c>
                      <c>2</c>
                
                        /a/b/c | not equal (STRING:STRING)
                        1
                        2
                """;
        message(expected);
        match("<hello foo=\"bar\">world</hello>", EQUALS, "<hello foo=\"baz\">world</hello>", FAILS);
        expected = """
                match failed: EQUALS
                  / | not equal | match failed for name: 'hello' (XML:XML)
                  <hello foo="bar">world</hello>
                  <hello foo="baz">world</hello>
                
                    /hello/@foo | not equal (STRING:STRING)
                    bar
                    baz
                """;
        message(expected);
    }

    @Test
    void testMapFuzzyIgnores() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, z: '#ignore', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, z: '#notpresent', a: 1 }");
        match(
                "{ a: 1, b: 2, c: 3 }",
                EQUALS,
                "{ b: 2, c: 3, z: '##anything', a: 1 }"); // not really correct, TODO !
    }

    @Test
    void testMapFuzzy() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#number', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#present', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#notnull', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#null', a: 1 }", FAILS);
        String expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'c' (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"a":1,"b":2,"c":"#null"}
                
                    $.c | not null (NUMBER:STRING)
                    3
                    '#null'
                """;
        message(expected);
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#string', a: 1 }", FAILS);
        expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'c' (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"a":1,"b":2,"c":"#string"}
                
                    $.c | not a string (NUMBER:STRING)
                    3
                    '#string'
                """;
        message(expected);
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#notpresent', a: 1 }", FAILS);
        expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'c' (MAP:MAP)
                  {"a":1,"b":2,"c":3}
                  {"a":1,"b":2,"c":"#notpresent"}
                
                    $.c | present (NUMBER:STRING)
                    3
                    '#notpresent'
                """;
        message(expected);
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex foo', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .+', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .{3}', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .{2}', c: 2, a: 1 }", FAILS);
        expected = """
                match failed: EQUALS
                  $ | not equal | match failed for name: 'b' (MAP:MAP)
                  {"a":1,"b":"foo","c":2}
                  {"a":1,"b":"#regex .{2}","c":2}
                
                    $.b | regex match failed (STRING:STRING)
                    'foo'
                    '#regex .{2}'
                """;
        message(expected);
    }

    @Test
    void testNotPresentOnLhs() {
        match("#notpresent", EQUALS, 2, FAILS);
        message("""
                match failed: EQUALS
                  $ | actual path does not exist (STRING:NUMBER)
                  '#notpresent'
                  2
                """);
        match("#notpresent", EQUALS, "foo", FAILS);
        message("""
                match failed: EQUALS
                  $ | actual path does not exist (STRING:STRING)
                  '#notpresent'
                  'foo'
                """);
    }

    @Test
    void testXml() {
        match("<root>foo</root>", EQUALS, "<root>foo</root>");
        match("<root>foo</root>", CONTAINS, "<root>foo</root>");
        match("<root>foo</root>", EQUALS, "<root>bar</root>", FAILS);
        match("<root>foo</root>", CONTAINS, "<root>bar</root>", FAILS);
        match("<root><a>1</a><b>2</b></root>", EQUALS, "<root><a>1</a><b>2</b></root>");
        match("<root><a>1</a><b>2</b></root>", EQUALS, "<root><b>2</b><a>1</a></root>");
        match("<root><a>1</a><b>2</b></root>", CONTAINS, "<root><b>2</b><a>1</a></root>");
        match("<root><a>1</a><b>2</b></root>", CONTAINS, "<root><a>1</a><b>9</b></root>", FAILS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testXmlSchema(String matchType) {
        match("<root></root>", matchType, "<root>#null</root>"); // TODO controversial
        match("<root></root>", matchType, "<root>#present</root>");
        match(
                "<root><a>x</a><b><c>y</c></b></root>",
                matchType,
                "<root><a>#string</a><b><c>#string</c></b></root>");
        match(
                "<root><a>x</a><b></b></root>",
                matchType,
                "<root><a>#string</a><b><c>#string</c></b></root>",
                FAILS);
        match(
                "<root><a>x</a><b><c></c></b></root>",
                matchType,
                "<root><a>#string</a><b><c>#string</c></b></root>",
                FAILS);
        match(
                "<root><a>x</a><b><c>y</c></b></root>",
                matchType,
                "<root><a>#string</a><b><c>#string</c></b></root>");
    }

    @Test
    void testApiUsage() {
        Match.that("[1, 2, 3]").contains(2);
        Match.that("[1, 2, 3]").isEachEqualTo("#number");
        Match.that("[1, 2, 3]").containsOnly("[3, 2, 1]");
        Match.that("{ a: 1, b: 2 }").contains("{ b: 2 }");
        Match.that("{ a: 1, b: 2, c: { d: 3, e: 4} }").containsDeep("{ b: 2, c: { e: 4 } }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testRegex(String matchType) {
        match(
                "{ number: '/en/search?q=test' }",
                matchType,
                "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
        match(
                "{ number: '/us/search?q=test' }",
                matchType,
                "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
        match(
                "{ number: '/en/search?q=test+whatever' }",
                matchType,
                "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
    }

    @Test
    void testOptionalNotEquals() {

        match("{ a: 1}", NOT_EQUALS, " { a: '#null' }");
        match("{ a: 1}", NOT_EQUALS, " { a: '##null' }");
        match("{ a: null}", NOT_EQUALS, " { a: '#notnull' }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUALS", "CONTAINS", "CONTAINS_DEEP"})
    void testOptional(String matchType) {
        match("{ number: '1234' }", matchType, "{ number: '##regex \\\\d+' }");
        match("{ }", matchType, "{ number: '##regex \\\\d+' }");
        match("{ 'foo': 'bar' }", matchType, "{ foo: '#string', number: '##regex \\\\d+' }");
        match("{ number: null }", matchType, "{ number: '##regex \\\\d+' }");

        match("{ number: 1234 }", matchType, "{ number: '##number' }");
        match("{ }", matchType, "{ number: '##number' }");
        match("{ 'foo': 'bar' }", matchType, "{ foo: '#string', number: '##number' }");
        match("{ number: null }", matchType, "{ number: '##number' }");

        match("{ 'foo': 'bar' }", matchType, "{ 'foo': '##string' }");
        match("{ }", matchType, "{ foo: '##string' }");
        match("{ 'foo': 'bar' }", matchType, "{ 'foo': '#string', 'bar': '##string' }");
        match("{ 'foo': null }", matchType, "{ 'foo': '##string' }");

        match("{ 'foo': 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }", matchType, "{ 'foo': '##uuid' }");
        match("{ }", matchType, "{ foo: '##uuid' }");
        match("{ 'foo': 'bar' }", matchType, "{ 'foo': '#string', 'bar': '##uuid' }");
        match("{ 'foo': null }", matchType, "{ 'foo': '##uuid' }");

        match("{ 'foo': true }", matchType, "{ 'foo': '##boolean' }");
        match("{ }", matchType, "{ foo: '##string' }");
        match("{ 'foo': 'bar' }", matchType, "{ 'foo': '#string', 'bar': '##boolean' }");
        match("{ 'foo': null }", matchType, "{ 'foo': '##boolean' }");

        match("{ 'foo': { 'bar': 'bar' } }", matchType, "{ 'foo': '##object' }");
        match("{ }", matchType, "{ foo: '##object' }");
        match(
                "{ 'foo': 'test', 'bar': { 'bar': 'bar' } }",
                matchType,
                "{ 'foo': '#string', 'bar': '##object' }");
        match("{ 'foo': null }", matchType, "{ 'foo': '##object' }");

        match("{ 'foo': [ { 'bar': 'bar' }] }", matchType, "{ 'foo': '##array' }");
        match("{ }", matchType, "{ 'foo': '##array' }");
        match(
                "{ 'foo': 'test', 'bar' : [ { 'bar': 'bar' } ] }",
                matchType,
                "{ 'foo': '#string', 'bar': '##array' }");


        match("{ 'foo': null }", matchType, "{ 'foo': '##array' }");

        match("{ a: null}", matchType, " { a: '##notnull' }");
    }
}

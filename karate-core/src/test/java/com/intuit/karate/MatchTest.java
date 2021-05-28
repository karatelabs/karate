package com.intuit.karate;

import com.intuit.karate.graal.JsEngine;
import static org.junit.jupiter.api.Assertions.*;
import static com.intuit.karate.Match.Type.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MatchTest {

    static final Logger logger = LoggerFactory.getLogger(MatchTest.class);

    private static final boolean FAILS = true;

    private void match(Object actual, Match.Type mt, Object expected) {
        match(actual, mt, expected, false);
    }

    String message;

    private void message(String expected) {
        assertTrue(message != null && message.contains(expected), message);
    }

    private void log() {
        logger.debug("{}", message);
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
        message("data types don't match");
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

    @Test
    void testStringStartingWithHash() {
        match("#bob", EQUALS, "#bob");
        match("#bob", CONTAINS, "#bob");
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
    }

    @Test
    void testListContains() {
        match("['foo', 'bar']", CONTAINS, "baz", FAILS);
        message("actual array does not contain expected item - baz");
        match("['foo', 'bar']", CONTAINS, "['baz']", FAILS);
        message("actual array does not contain expected item - baz");
    }
    
    @Test
    void testListContainsRegex() {
        match("['foo', 'bar']", CONTAINS, "#regex .{3}");
    }    

    @Test
    void testListNotContains() {
        match("['foo', 'bar']", NOT_CONTAINS, "baz");
        match("['foo', 'bar']", NOT_CONTAINS, "bar", FAILS);
        message("actual contains expected");
        match("[{ foo: 1 }, { foo: 2 }, { foo: 3 }]", CONTAINS, "[{ foo: 0 }, { foo: 2 }, { foo: 3 }]", FAILS);
        message("$[0] | not equal"); // TODO improve error message for this case
    }

    @Test
    void testEach() {
        match("[1, 2, 3]", EACH_EQUALS, "#number");
        match("[1, 2, 3]", EACH_EQUALS, "#number? _ > 0");
        match("[1, 2, 3]", EACH_EQUALS, "#number? _ < 2", FAILS);
        message("match each failed at index 1");
        match("[1, 'a', 3]", EACH_EQUALS, "#number", FAILS);
        message("$[1] | not a number");
        match("[{ a: 1 }, { a: 2 }]", EACH_EQUALS, "#object");
        match("[{ a: 1 }, { a: 2 }]", EACH_EQUALS, "{ a: '#number' }");
    }

    @Test
    void testEachWithMagicVariables() {
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_EQUALS, "{ a: '#number', b: '#(_$.a * 2)' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_EQUALS, "{ a: '#number', b: '#? _ == _$.a * 2' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_CONTAINS, "{ b: '#(_$.a * 2)' }");
        match("[{a: 1, b: 2}, {a: 2, b: 4}]", EACH_CONTAINS, "{ b: '#? _ == _$.a * 2' }");
    }

    @Test
    void testArray() {
        match("[{ a: 1 }, { a: 2 }]", EQUALS, "#[2]");
        match("[{ a: 1 }, { a: 2 }]", EQUALS, "#[] #object");
    }

    @Test
    void testSchema() {
        Json json = Json.of("{ a: '#number' }");
        Map map = json.asMap();
        match("[{ a: 1 }, { a: 2 }]", EACH_EQUALS, map);
        JsEngine.global().put("schema", map);
        match("[{ a: 1 }, { a: 2 }]", EQUALS, "#[] schema");
        match("{ a: 'x', b: { c: 'y' } }", EQUALS, "{ a: '#string', b: { c: '#string' } }");
    }

    @Test
    void testSchemaOptionalObject() {
        Json part = Json.of("{ bar: '#string' }");
        JsEngine.global().put("part", part.asMap());
        match("{ foo: null }", EQUALS, "{ foo: '##(bar)' }");
    }

    @Test
    void testMap() {
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
        message("$ | actual does not contain expected | actual does not contain key - 'z'");
        match("{ a: 1, b: 2, c: 3 }", CONTAINS_ANY, "{ z: 9, x: 2 }", FAILS);
        message("$ | actual does not contain expected | no key-values matched");
        message("$.x | data types don't match");
        message("$.z | data types don't match");
        match("{ a: 1, b: 2, c: 3 }", NOT_CONTAINS, "{ a: 1 }", FAILS);
        message("$ | actual contains expected");
        match("{ a: 1, b: 2, c: 3 }", NOT_CONTAINS, "{}");  
    }

    @Test
    void testJsonFailureMessages() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ a: 1, b: 9, c: 3 }", FAILS);
        message("$.b | not equal");
        match("{ a: { b: { c: 1 } } }", EQUALS, "{ a: { b: { c: 2 } } }", FAILS);
        message("$.a.b.c | not equal");
    }

    @Test
    void testXmlFailureMessages() {
        match("<a><b><c>1</c></b></a>", EQUALS, "<a><b><c>2</c></b></a>", FAILS);
        message("/ | not equal | match failed for name: 'a'");
        message("/a | not equal | match failed for name: 'b'");
        message("/a/b | not equal | match failed for name: 'c'");
        message("/a/b/c | not equal");
        match("<hello foo=\"bar\">world</hello>", EQUALS, "<hello foo=\"baz\">world</hello>", FAILS);
        message("/ | not equal | match failed for name: 'hello'");
        message("/hello/@foo | not equal");
    }

    @Test
    void testMapFuzzyIgnores() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, z: '#ignore', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, z: '#notpresent', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: 3, z: '##anything', a: 1 }"); // not really correct, TODO !        
    }

    @Test
    void testMapFuzzy() {
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#number', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#present', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#notnull', a: 1 }");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#null', a: 1 }", FAILS);
        message("$.c | not null");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#string', a: 1 }", FAILS);
        message("$.c | not a string");
        match("{ a: 1, b: 2, c: 3 }", EQUALS, "{ b: 2, c: '#notpresent', a: 1 }", FAILS);
        message("$.c | present");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex foo', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .+', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .{3}', c: 2, a: 1 }");
        match("{ a: 1, b: 'foo', c: 2 }", EQUALS, "{ b: '#regex .{2}', c: 2, a: 1 }", FAILS);
        message("$.b | regex match failed");
    }

    @Test
    void testNotPresentOnLhs() {
        match("#notpresent", EQUALS, 2, FAILS);
        message("actual path does not exist");
        match("#notpresent", EQUALS, "foo", FAILS);
        message("actual path does not exist");
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

    @Test
    void testXmlSchema() {
        match("<root></root>", EQUALS, "<root>#null</root>"); // TODO controversial
        match("<root></root>", EQUALS, "<root>#present</root>");
        match("<root><a>x</a><b><c>y</c></b></root>", EQUALS, "<root><a>#string</a><b><c>#string</c></b></root>");
        match("<root><a>x</a><b></b></root>", EQUALS, "<root><a>#string</a><b><c>#string</c></b></root>", FAILS);
        match("<root><a>x</a><b><c></c></b></root>", EQUALS, "<root><a>#string</a><b><c>#string</c></b></root>", FAILS);
        match("<root><a>x</a><b><c>y</c></b></root>", EQUALS, "<root><a>#string</a><b><c>#string</c></b></root>");
    }

    @Test
    void testApiUsage() {
        Match.that("[1, 2, 3]").contains(2);
        Match.that("[1, 2, 3]").isEachEqualTo("#number");
        Match.that("[1, 2, 3]").containsOnly("[3, 2, 1]");
        Match.that("{ a: 1, b: 2 }").contains("{ b: 2 }");
        Match.that("{ a: 1, b: 2, c: { d: 3, e: 4} }").containsDeep("{ b: 2, c: { e: 4 } }");
    }

    @Test
    void testRegex() {
        match("{ number: '/en/search?q=test' }", EQUALS, "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
        match("{ number: '/us/search?q=test' }", EQUALS, "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
        match("{ number: '/en/search?q=test+whatever' }", EQUALS, "{ number: '#regex /\\\\w{2}/search\\\\?q=(.*)+' }");
    }

    @Test
    void testOptional() {
        match("{ number: '1234' }", EQUALS, "{ number: '##regex \\\\d+' }");
        match("{ }", EQUALS, "{ number: '##regex \\\\d+' }");
        match("{ 'foo': 'bar' }", EQUALS, "{ foo: '#string', number: '##regex \\\\d+' }");
        match("{ number: null }", EQUALS, "{ number: '##regex \\\\d+' }");

        match("{ number: 1234 }", EQUALS, "{ number: '##number' }");
        match("{ }", EQUALS, "{ number: '##number' }");
        match("{ 'foo': 'bar' }", EQUALS, "{ foo: '#string', number: '##number' }");
        match("{ number: null }", EQUALS, "{ number: '##number' }");

        match("{ 'foo': 'bar' }", EQUALS, "{ 'foo': '##string' }");
        match("{ }", EQUALS, "{ foo: '##string' }");
        match("{ 'foo': 'bar' }", EQUALS, "{ 'foo': '#string', 'bar': '##string' }");
        match("{ 'foo': null }", EQUALS, "{ 'foo': '##string' }");

        match("{ 'foo': 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }", EQUALS, "{ 'foo': '##uuid' }");
        match("{ }", EQUALS, "{ foo: '##uuid' }");
        match("{ 'foo': 'bar' }", EQUALS, "{ 'foo': '#string', 'bar': '##uuid' }");
        match("{ 'foo': null }", EQUALS, "{ 'foo': '##uuid' }");

        match("{ 'foo': true }", EQUALS, "{ 'foo': '##boolean' }");
        match("{ }", EQUALS, "{ foo: '##string' }");
        match("{ 'foo': 'bar' }", EQUALS, "{ 'foo': '#string', 'bar': '##boolean' }");
        match("{ 'foo': null }", EQUALS, "{ 'foo': '##boolean' }");

        match("{ 'foo': { 'bar': 'bar' } }", EQUALS, "{ 'foo': '##object' }");
        match("{ }", EQUALS, "{ foo: '##object' }");
        match("{ 'foo': 'test', 'bar': { 'bar': 'bar' } }", EQUALS, "{ 'foo': '#string', 'bar': '##object' }");
        match("{ 'foo': null }", EQUALS, "{ 'foo': '##object' }");

        match("{ 'foo': [ { 'bar': 'bar' }] }", EQUALS, "{ 'foo': '##array' }");
        match("{ }", EQUALS, "{ 'foo': '##array' }");
        match("{ 'foo': 'test', 'bar' : [ { 'bar': 'bar' } ] }", EQUALS, "{ 'foo': '#string', 'bar': '##array' }");
        match("{ 'foo': null }", EQUALS, "{ 'foo': '##array' }");

        match("{ a: 1}", NOT_EQUALS, " { a: '#null' }");
        match("{ a: 1}", NOT_EQUALS, " { a: '##null' }");
        match("{ a: null}", NOT_EQUALS, " { a: '#notnull' }");
        match("{ a: null}", EQUALS, " { a: '##notnull' }");
    }

}

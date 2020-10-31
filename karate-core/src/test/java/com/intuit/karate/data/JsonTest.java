package com.intuit.karate.data;

import com.intuit.karate.StringUtils;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class JsonTest {

    static final Logger logger = LoggerFactory.getLogger(JsonTest.class);

    private void match(Json json, String expected) {
        MatchResult mr = Match.that(json.asMapOrList()).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    @Test
    void testParsingParentAndLeafName() {
        assertEquals(StringUtils.pair("", "$"), Json.toParentAndLeaf("$"));
        assertEquals(StringUtils.pair("$", "foo"), Json.toParentAndLeaf("$.foo"));
        assertEquals(StringUtils.pair("$", "['foo']"), Json.toParentAndLeaf("$['foo']"));
        assertEquals(StringUtils.pair("$.foo", "bar"), Json.toParentAndLeaf("$.foo.bar"));
        assertEquals(StringUtils.pair("$.foo", "['bar']"), Json.toParentAndLeaf("$.foo['bar']"));
        assertEquals(StringUtils.pair("$.foo", "bar[0]"), Json.toParentAndLeaf("$.foo.bar[0]"));
        assertEquals(StringUtils.pair("$.foo", "['bar'][0]"), Json.toParentAndLeaf("$.foo['bar'][0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "bar[0]"), Json.toParentAndLeaf("$.foo[2].bar[0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "['bar'][0]"), Json.toParentAndLeaf("$.foo[2]['bar'][0]"));
        assertEquals(StringUtils.pair("$.foo[2]", "bar"), Json.toParentAndLeaf("$.foo[2].bar"));
        assertEquals(StringUtils.pair("$.foo[2]", "['bar']"), Json.toParentAndLeaf("$.foo[2]['bar']"));
    }

    @Test
    void testSetAndRemove() {
        Json json = new Json();
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
        json = new Json("[]");
        match(json, "[]");
        json.set("$[0].foo", "[1, 2]");
        match(json, "[{ foo: [1, 2] }]");
        json.set("$[1].foo", "[3, 4]");
        match(json, "[{ foo: [1, 2] }, { foo: [3, 4] }]");
        json.remove("$[0]");
        match(json, "[{ foo: [3, 4] }]");        
        json = new Json();
        json.set("$.foo[]", "a");
        match(json, "{ foo: ['a'] }");
    }

}

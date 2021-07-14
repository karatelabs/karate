package com.intuit.karate;

import java.util.Arrays;
import java.util.List;

import com.intuit.karate.StringUtils.Pair;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class StringUtilsTest {

    @Test
    void testPair() {
        assertEquals(new Pair("foo", "bar"), StringUtils.pair("foo", "bar"));
    }

    @Test
    void testTrimToEmpty() {
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("foo", StringUtils.trimToEmpty("   foo   "));
    }

    @Test
    void testTrimToNull() {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull("   "));
        assertEquals("foo", StringUtils.trimToNull("   foo   "));
    }

    @Test
    void testRepeat() {
        assertEquals("\u0000", StringUtils.repeat('\u0000', 1));
        assertEquals("aaaaa", StringUtils.repeat('a', 5));
        assertEquals("", StringUtils.repeat('\u0000', 0));
    }

    @Test
    void testSplit() {
        List<String> list = StringUtils.split("", '/', false);
        assertEquals(1, list.size());
        assertEquals("", list.get(0));
        list = StringUtils.split("//", '/', false);
        assertEquals(0, list.size());
        list = StringUtils.split("foo", '/', false);
        assertEquals(1, list.size());
        assertEquals("foo", list.get(0));
        list = StringUtils.split("foo/", '/', false);
        assertEquals(1, list.size());
        assertEquals("foo", list.get(0));
        list = StringUtils.split("/foo", '/', false);
        assertEquals(1, list.size());
        assertEquals("foo", list.get(0));
        list = StringUtils.split("/foo/bar", '/', false);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        list = StringUtils.split("|pi\\|pe|blah|", '|', true);
        assertEquals(2, list.size());
        assertEquals("pi|pe", list.get(0));
        assertEquals("blah", list.get(1));
    }

    @Test
    void testJoin() {
        String[] foo = {"a", "b"};
        assertEquals("a,b", StringUtils.join(foo, ','));
        assertEquals("a,b", StringUtils.join(Arrays.asList(foo), ","));
    }

    @Test
    void testJsFunction() {
        assertTrue(StringUtils.isJavaScriptFunction("function(){ return { bar: 'baz' } }"));
        assertTrue(StringUtils.isJavaScriptFunction("function() {   \n"
                + "  return { someConfig: 'someValue' }\n"
                + "}"));
        assertTrue(StringUtils.isJavaScriptFunction("function fn(){ return { bar: 'baz' } }"));
        assertEquals("function(){}", StringUtils.fixJavaScriptFunction("function fn(){}"));
    }

    void testIsBlank() {
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank(null));
        assertFalse(StringUtils.isBlank("foo"));
    }

    @Test
    void testToIdString() {
        assertEquals("foo-bar", StringUtils.toIdString("foo_bar"));
        assertEquals("foo-bar", StringUtils.toIdString("foo_bar"));
        assertEquals("foo-bar", StringUtils.toIdString("foo bar"));
        assertEquals("foo--bar", StringUtils.toIdString("foo//bar"));
        assertEquals("foo-bar", StringUtils.toIdString("foo\\bar"));
        assertEquals("foo-bar", StringUtils.toIdString("foo:bar"));
        assertEquals("", StringUtils.toIdString(null)); // TODO
    }

    @Test
    void testSplitByFirstLineFeed() {
        assertEquals(new Pair("", ""),
                StringUtils.splitByFirstLineFeed(null));
        assertEquals(new Pair("foo", ""),
                StringUtils.splitByFirstLineFeed("foo"));
        assertEquals(new Pair("foo", "bar"),
                StringUtils.splitByFirstLineFeed("foo\nbar"));
    }

    @Test
    void testToStringLines() {
        List<String> expected = Arrays.asList("foo", "bar");
        assertEquals(expected, StringUtils.toStringLines("foo\nbar\n"));
    }

    @Test
    void testCountLineFeeds() {
        assertEquals(2, StringUtils.countLineFeeds("foo\nbar\n"));
        assertEquals(0, StringUtils.countLineFeeds("foobar"));
    }

    @Test
    void testWrappedLinesEstimate() {
        assertEquals(6,
                StringUtils.wrappedLinesEstimate("foobarbazfoobarbaz", 3));
        assertEquals(1,
                StringUtils.wrappedLinesEstimate("foobarbazfoobarbaz", 20));
        assertEquals(0,
                StringUtils.wrappedLinesEstimate("", 2));
    }

}

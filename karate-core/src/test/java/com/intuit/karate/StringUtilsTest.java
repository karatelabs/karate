/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate;

import java.util.Arrays;
import java.util.List;

import com.intuit.karate.StringUtils.Pair;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class StringUtilsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testPair() {
        assertEquals(new Pair("foo", "bar"), StringUtils.pair("foo", "bar"));
    }

    @Test
    public void testTrimToEmpty() {
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("foo", StringUtils.trimToEmpty("   foo   "));
    }

    @Test
    public void testTrimToNull() {
        assertNull(StringUtils.trimToNull(null));
        assertNull(StringUtils.trimToNull("   "));
        assertEquals("foo", StringUtils.trimToNull("   foo   "));
    }

    @Test
    public void testRepeat() {
        assertEquals("\u0000", StringUtils.repeat('\u0000', 1));
        assertEquals("aaaaa", StringUtils.repeat('a', 5));
        assertEquals("", StringUtils.repeat('\u0000', 0));
    }

    @Test
    public void testSplit() {
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
    public void testJoin() {
        String[] foo = {"a", "b"};
        assertEquals("a,b", StringUtils.join(foo, ','));
        assertEquals("a,b", StringUtils.join(Arrays.asList(foo), ','));
    }

    @Test
    public void testJsFunction() {
        assertTrue(StringUtils.isJavaScriptFunction("function(){ return { bar: 'baz' } }"));
        assertTrue(StringUtils.isJavaScriptFunction("function() {   \n"
                + "  return { someConfig: 'someValue' }\n"
                + "}"));
        assertTrue(StringUtils.isJavaScriptFunction("function fn(){ return { bar: 'baz' } }"));
        assertEquals("function(){}", StringUtils.fixJavaScriptFunction("function fn(){}"));
    }

    public void testIsBlank() {
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank(null));
        assertFalse(StringUtils.isBlank("foo"));
    }

    @Test
    public void testToIdString() {
        assertEquals("foo-bar", StringUtils.toIdString("foo_bar"));
        thrown.expect(NullPointerException.class);
        StringUtils.toIdString(null);
        // Method is not expected to return due to exception thrown
    }

    @Test
    public void testSplitByFirstLineFeed() {
        assertEquals(new Pair("", ""),
                StringUtils.splitByFirstLineFeed(null));
        assertEquals(new Pair("foo", ""),
                StringUtils.splitByFirstLineFeed("foo"));
        assertEquals(new Pair("foo", "bar"),
                StringUtils.splitByFirstLineFeed("foo\nbar"));
    }

    @Test
    public void testToStringLines() {
        List<String> expected = Arrays.asList("foo", "bar");
        assertEquals(expected, StringUtils.toStringLines("foo\nbar\n"));
    }

    @Test
    public void testCountLineFeeds() {
        assertEquals(2, StringUtils.countLineFeeds("foo\nbar\n"));
        assertEquals(0, StringUtils.countLineFeeds("foobar"));
    }

    @Test
    public void testWrappedLinesEstimate() {
        assertEquals(6,
                StringUtils.wrappedLinesEstimate("foobarbazfoobarbaz", 3));
        assertEquals(1,
                StringUtils.wrappedLinesEstimate("foobarbazfoobarbaz", 20));
        assertEquals(0,
                StringUtils.wrappedLinesEstimate("", 2));
    }
    
}

/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.core;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TagTest {

    final Tag tag = new Tag(5, "@foo=bar,baz");

    @Test
    public void testGetLine() {
        assertEquals(5, tag.getLine());
    }

    @Test
    public void testGetText() {
        assertEquals("foo=bar,baz", tag.getText());
    }

    @Test
    public void testGetName() {
        assertEquals("foo", tag.getName());
    }

    @Test
    public void testGetValues() {
        assertEquals(Arrays.asList("bar", "baz"), tag.getValues());
    }

    @Test
    public void testToString() {
        assertEquals("@foo=bar,baz", new Tag(5, "@foo=bar,baz").toString());
        assertEquals("@foo=", new Tag(5, "@foo=").toString());
        assertEquals("@foobar,baz", new Tag(5, "@foobar,baz").toString());
    }

    @Test
    public void testHashcode() {
        assertEquals(894422763, tag.hashCode());
    }

    @Test
    public void testEquals() {
        assertTrue(tag.equals(tag));
        assertFalse(tag.equals(null));
        assertFalse(tag.equals(new Tag(0, "@baz=bar,foo")));
        assertFalse(tag.equals("foo"));
    }
    
}

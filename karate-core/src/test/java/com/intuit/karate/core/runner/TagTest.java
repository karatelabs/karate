package com.intuit.karate.core.runner;

import com.intuit.karate.core.Tag;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

  class TagTest {

    final Tag tag = new Tag(5, "@foo=bar,baz");

    @Test
    void testGetLine() {
        assertEquals(5, tag.getLine());
    }

    @Test
    void testGetText() {
        assertEquals("foo=bar,baz", tag.getText());
    }

    @Test
    void testGetName() {
        assertEquals("foo", tag.getName());
    }

    @Test
    void testGetValues() {
        assertEquals(Arrays.asList("bar", "baz"), tag.getValues());
    }

    @Test
    void testToString() {
        assertEquals("@foo=bar,baz", new Tag(5, "@foo=bar,baz").toString());
        assertEquals("@foo=", new Tag(5, "@foo=").toString());
        assertEquals("@foobar,baz", new Tag(5, "@foobar,baz").toString());
    }

    @Test
    void testHashcode() {
        assertEquals(894422763, tag.hashCode());
    }

    @Test
    void testEquals() {
        assertTrue(tag.equals(tag));
        assertFalse(tag.equals(null));
        assertFalse(tag.equals(new Tag(0, "@baz=bar,foo")));
        assertFalse(tag.equals("foo"));
    }
    
}

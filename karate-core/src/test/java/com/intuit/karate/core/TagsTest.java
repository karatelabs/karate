/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class TagsTest {   
    
    @Test
    public void testCucumberOptionsTagsConversion() {
        assertEquals("anyOf('@foo')", Tags.fromKarateOptionsTags("@foo"));
        assertEquals("anyOf('@foo','@bar')", Tags.fromKarateOptionsTags("@foo,@bar"));
        assertEquals("anyOf('@foo') && anyOf('@bar')", Tags.fromKarateOptionsTags("@foo", "@bar"));
        assertEquals("anyOf('@foo') && not('@bar')", Tags.fromKarateOptionsTags("@foo", "~@bar"));
        // detect new syntax and use as-is
        assertEquals("anyOf('@foo')", Tags.fromKarateOptionsTags("anyOf('@foo')"));
    }    
    
    private boolean eval(String tagSelector, String ... strs) {
        List<Tag> list = new ArrayList(strs.length);
        for (String s : strs) {
            list.add(new Tag(0, s));
        }
        Tags tags = new Tags(list);
        return tags.evaluate(tagSelector);
    }
    
    @Test
    public void testTagSelectors() {
        assertTrue(eval(null));
        assertTrue(eval(null, "@foo", "@bar"));        
        assertTrue(eval("anyOf('@foo')", "@foo", "@bar"));
        assertTrue(eval("not('@ignore')"));
        assertTrue(eval("not('@ignore')", "@foo", "@bar"));
        assertTrue(eval("anyOf('@foo', '@bar')", "@foo", "@bar"));
        assertTrue(eval("anyOf('@foo', '@baz')", "@foo", "@bar"));
        assertTrue(eval("allOf('@foo')", "@foo", "@bar"));
        assertTrue(eval("allOf('@foo', '@bar')", "@foo", "@bar"));
        assertTrue(eval("allOf('@foo', '@bar') && not('@ignore')", "@foo", "@bar"));
        assertTrue(eval("anyOf('@foo') && !anyOf('@ignore')", "@foo", "@bar"));
        assertFalse(eval("!anyOf('@ignore')", "@ignore"));
        assertFalse(eval("not('@ignore')", "@ignore"));
        assertFalse(eval("not('@ignore', '@foo')", "@ignore"));
        assertFalse(eval("!anyOf('@ignore')", "@foo", "@bar", "@ignore"));
        assertFalse(eval("anyOf('@foo') && !anyOf('@ignore')", "@foo", "@bar", "@ignore"));
        assertFalse(eval("anyOf('@foo')", "@bar", "@ignore"));        
        assertFalse(eval("allOf('@foo', '@baz')", "@foo", "@bar"));
        assertFalse(eval("anyOf('@foo') && anyOf('@baz')", "@foo", "@bar"));
        assertFalse(eval("!anyOf('@foo')", "@foo", "@bar"));
        assertFalse(eval("allOf('@foo', '@bar') && not('@ignore')", "@foo", "@bar", "@ignore"));        
    }
    
    @Test
    public void testTagValueSelectors() {
        assertFalse(eval("valuesFor('@id').isPresent"));
        assertFalse(eval("valuesFor('@id').isPresent", "@foo"));
        assertFalse(eval("valuesFor('@id').isPresent", "@id"));
        assertFalse(eval("valuesFor('@id').isPresent", "@foo", "@id"));
        assertFalse(eval("valuesFor('@id').isPresent", "@id="));
        assertTrue(eval("valuesFor('@id').isPresent", "@id=1"));
        assertTrue(eval("valuesFor('@id').isOnly(1)", "@id=1"));
        assertTrue(eval("valuesFor('@id').isAnyOf(1)", "@id=1"));
        assertTrue(eval("valuesFor('@id').isAllOf(1)", "@id=1"));
        assertTrue(eval("valuesFor('@id').isAllOf(1)", "@id=1,2"));
        assertFalse(eval("valuesFor('@id').isAnyOf(2)", "@id=1"));
        assertTrue(eval("valuesFor('@id').isAnyOf(1)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isAnyOf(2)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isAllOf(1, 2)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isOnly(1, 2)", "@id=1,2"));
        assertFalse(eval("valuesFor('@id').isOnly(1, 3)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isAnyOf(1, 2)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isAnyOf(1, 3)", "@id=1,2"));
        assertTrue(eval("valuesFor('@id').isEach(function(s){return s.startsWith('1')})", "@id=100,1000"));
        assertTrue(eval("valuesFor('@id').isEach(function(s){return /^1.*/.test(s)})", "@id=100,1000"));
    }
    
}

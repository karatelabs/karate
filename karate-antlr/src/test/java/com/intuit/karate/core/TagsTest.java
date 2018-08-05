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
    
    private boolean evaluate(String tagSelector, String ... strs) {
        List<Tag> tags = new ArrayList(strs.length);
        for (String s : strs) {
            tags.add(new Tag(s));
        }
        return Tags.evaluate(tagSelector, tags);
    }
    
    @Test
    public void testTagSelector() {
        assertTrue(evaluate(null));
        assertTrue(evaluate(null, "@foo", "@bar"));        
        assertTrue(evaluate("anyOf('@foo')", "@foo", "@bar"));
        assertTrue(evaluate("not('@ignore')"));
        assertTrue(evaluate("not('@ignore')", "@foo", "@bar"));
        assertTrue(evaluate("anyOf('@foo', '@bar')", "@foo", "@bar"));
        assertTrue(evaluate("anyOf('@foo', '@baz')", "@foo", "@bar"));
        assertTrue(evaluate("allOf('@foo')", "@foo", "@bar"));
        assertTrue(evaluate("allOf('@foo', '@bar')", "@foo", "@bar"));
        assertTrue(evaluate("allOf('@foo', '@bar') && not('@ignore')", "@foo", "@bar"));
        assertTrue(evaluate("anyOf('@foo') && !anyOf('@ignore')", "@foo", "@bar"));
        assertFalse(evaluate("!anyOf('@ignore')", "@ignore"));
        assertFalse(evaluate("not('@ignore')", "@ignore"));
        assertFalse(evaluate("not('@ignore', '@foo')", "@ignore"));
        assertFalse(evaluate("!anyOf('@ignore')", "@foo", "@bar", "@ignore"));
        assertFalse(evaluate("anyOf('@foo') && !anyOf('@ignore')", "@foo", "@bar", "@ignore"));
        assertFalse(evaluate("anyOf('@foo')", "@bar", "@ignore"));        
        assertFalse(evaluate("allOf('@foo', '@baz')", "@foo", "@bar"));
        assertFalse(evaluate("anyOf('@foo') && anyOf('@baz')", "@foo", "@bar"));
        assertFalse(evaluate("!anyOf('@foo')", "@foo", "@bar"));
        assertFalse(evaluate("allOf('@foo', '@bar') && not('@ignore')", "@foo", "@bar", "@ignore"));
        
    }
    
}

/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.core;

import io.karatelabs.gherkin.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagSelectorTest {

    @Test
    void testCucumberOptionsTagsConversion() {
        assertEquals("anyOf('@foo')", TagSelector.fromKarateOptionsTags("@foo"));
        assertEquals("anyOf('@foo','@bar')", TagSelector.fromKarateOptionsTags("@foo, @bar"));
        assertEquals("anyOf('@foo') && anyOf('@bar')", TagSelector.fromKarateOptionsTags("@foo", "@bar"));
        assertEquals("anyOf('@foo') && not('@bar')", TagSelector.fromKarateOptionsTags("@foo", "~@bar"));
        // Detect new syntax and use as-is
        assertEquals("anyOf('@foo')", TagSelector.fromKarateOptionsTags("anyOf('@foo')"));
    }

    private boolean eval(String tagSelector, String... strs) {
        List<Tag> list = new ArrayList<>(strs.length);
        for (String s : strs) {
            list.add(new Tag(0, s));
        }
        TagSelector selector = new TagSelector(list);
        return selector.evaluate(tagSelector, null);
    }

    @Test
    void testTagSelectors() {
        assertTrue(eval(null));
        assertFalse(eval(null, "@ignore"));
        assertTrue(eval(null, "@foo", "@bar"));
        assertFalse(eval(null, "@foo", "@ignore"));
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
    void testTagValueSelectors() {
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
        assertTrue(eval("valuesFor('@id').isEach(s => s.startsWith('1'))", "@id=100,1000"));
        assertTrue(eval("valuesFor('@id').isEach(s => /^1.*/.test(s))", "@id=100,1000"));
    }

    private boolean evalEnv(String tagSelector, String karateEnv, String... strs) {
        List<Tag> list = new ArrayList<>(strs.length);
        for (String s : strs) {
            list.add(new Tag(0, s));
        }
        TagSelector selector = new TagSelector(list);
        return selector.evaluate(tagSelector, karateEnv);
    }

    @Test
    void testEnvSelectors() {
        assertFalse(evalEnv(null, null, "@env=foo"));
        assertTrue(evalEnv(null, "foo", "@env=foo"));
        assertTrue(evalEnv(null, null, "@envnot=foo"));
        assertFalse(evalEnv(null, "foo", "@envnot=foo"));
        assertTrue(evalEnv(null, "foo", "@env=foo", "@bar"));
        assertTrue(evalEnv("anyOf('@bar')", "foo", "@env=foo", "@bar"));
        assertFalse(evalEnv("anyOf('@baz')", "foo", "@env=foo", "@bar"));
        assertFalse(evalEnv(null, "baz", "@env=foo", "@bar"));
        assertFalse(evalEnv(null, "foo", "@envnot=foo", "@bar"));
        assertTrue(evalEnv(null, "foo", "@envnot=baz", "@bar"));
        assertTrue(evalEnv("anyOf('@bar')", "foo", "@envnot=baz", "@bar"));
        assertFalse(evalEnv("anyOf('@baz')", "foo", "@envnot=baz", "@bar"));
    }

    @Test
    void testSetupTagSkipped() {
        // @setup scenarios should be skipped by default (only run via karate.setup())
        assertFalse(eval(null, "@setup"));
        assertFalse(eval(null, "@setup", "@foo"));
        assertFalse(eval("anyOf('@foo')", "@setup", "@foo"));
    }

    @Test
    void testEmptyTags() {
        TagSelector selector = new TagSelector(null);
        assertTrue(selector.evaluate(null, null));
        assertFalse(selector.evaluate("anyOf('@foo')", null));
    }

    @Test
    void testValuesForMissingTag() {
        TagSelector selector = new TagSelector(List.of(new Tag(0, "@foo")));
        TagSelector.Values values = selector.valuesFor("@missing");
        assertFalse(values.isPresent());
        assertFalse(values.isAnyOf("x"));
        assertFalse(values.isAllOf("x"));
        assertFalse(values.isOnly("x"));
    }

}

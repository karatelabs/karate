package com.intuit.karate.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
class TagsTest {

    @Test
    public void testCucumberOptionsTagsConversion() {
        assertEquals("anyOf('@foo')", Tags.fromKarateOptionsTags("@foo"));
        assertEquals("anyOf('@foo','@bar')", Tags.fromKarateOptionsTags("@foo, @bar"));
        assertEquals("anyOf('@foo') && anyOf('@bar')", Tags.fromKarateOptionsTags("@foo", "@bar"));
        assertEquals("anyOf('@foo') && not('@bar')", Tags.fromKarateOptionsTags("@foo", "~@bar"));
        // detect new syntax and use as-is
        assertEquals("anyOf('@foo')", Tags.fromKarateOptionsTags("anyOf('@foo')"));
    }

    private boolean eval(String tagSelector, String... strs) {
        List<Tag> list = new ArrayList(strs.length);
        for (String s : strs) {
            list.add(new Tag(0, s));
        }
        Tags tags = new Tags(list);
        return tags.evaluate(tagSelector, null);
    }

    @Test
    public void testTagSelectors() {
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
        assertTrue(eval("valuesFor('@id').isEach(s => s.startsWith('1'))", "@id=100,1000"));
        assertTrue(eval("valuesFor('@id').isEach(s => /^1.*/.test(s))", "@id=100,1000"));
    }

    private boolean evalEnv(String tagSelector, String karateEnv, String... strs) {
        List<Tag> list = new ArrayList(strs.length);
        for (String s : strs) {
            list.add(new Tag(0, s));
        }
        Tags tags = new Tags(list);
        return tags.evaluate(tagSelector, karateEnv);
    }

    @Test
    public void testEnvSelectors() {
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

}

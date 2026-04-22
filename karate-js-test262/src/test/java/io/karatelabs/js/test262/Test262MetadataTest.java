package io.karatelabs.js.test262;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Test262MetadataTest {

    @Test
    void testNoFrontmatter() {
        Test262Metadata m = Test262Metadata.parse("var x = 1;");
        assertTrue(m.flags().isEmpty());
        assertTrue(m.features().isEmpty());
        assertTrue(m.includes().isEmpty());
        assertNull(m.negative());
        assertFalse(m.raw());
    }

    @Test
    void testFlagsAndFeatures() {
        String src = """
            /*---
            description: foo
            flags: [noStrict]
            features: [Symbol, BigInt]
            ---*/
            var x;
            """;
        Test262Metadata m = Test262Metadata.parse(src);
        assertEquals(1, m.flags().size());
        assertTrue(m.flags().contains("noStrict"));
        assertEquals(2, m.features().size());
        assertTrue(m.features().contains("Symbol"));
        assertTrue(m.features().contains("BigInt"));
        assertEquals("foo", m.description());
    }

    @Test
    void testIncludes() {
        String src = """
            /*---
            includes: [assert.js, sta.js, propertyHelper.js]
            ---*/
            """;
        Test262Metadata m = Test262Metadata.parse(src);
        assertEquals(3, m.includes().size());
        assertEquals("assert.js", m.includes().get(0));
        assertEquals("propertyHelper.js", m.includes().get(2));
    }

    @Test
    void testNegative() {
        String src = """
            /*---
            negative:
              phase: parse
              type: SyntaxError
            ---*/
            """;
        Test262Metadata m = Test262Metadata.parse(src);
        assertNotNull(m.negative());
        assertEquals("parse", m.negative().phase());
        assertEquals("SyntaxError", m.negative().type());
    }

    @Test
    void testRaw() {
        String src = """
            /*---
            flags: [raw]
            ---*/
            """;
        Test262Metadata m = Test262Metadata.parse(src);
        assertTrue(m.raw());
    }

    @Test
    void testEmptyInlineArray() {
        assertTrue(Test262Metadata.parseInlineArray("[]").isEmpty());
        assertTrue(Test262Metadata.parseInlineArray("  [ ] ").isEmpty());
    }

    @Test
    void testNegativeFollowedByOtherKey() {
        // ensure state transition out of "negative:" block works
        String src = """
            /*---
            negative:
              phase: runtime
              type: TypeError
            features: [Symbol]
            ---*/
            """;
        Test262Metadata m = Test262Metadata.parse(src);
        assertNotNull(m.negative());
        assertEquals("runtime", m.negative().phase());
        assertEquals("TypeError", m.negative().type());
        assertEquals(1, m.features().size());
        assertEquals("Symbol", m.features().get(0));
    }
}

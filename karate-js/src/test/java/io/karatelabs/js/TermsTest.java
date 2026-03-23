package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TermsTest {

    @Test
    void testTruthy() {
        assertTrue(Terms.isTruthy(true));
        assertFalse(Terms.isTruthy(false));
        assertFalse(Terms.isTruthy(null));
        assertFalse(Terms.isTruthy(Terms.UNDEFINED));
        assertFalse(Terms.isTruthy(""));
        assertFalse(Terms.isTruthy(0));
        assertFalse(Terms.isTruthy(-0));
        assertFalse(Terms.isTruthy(0.0));
        assertTrue(Terms.isTruthy(1));
        assertTrue(Terms.isTruthy(100));
        assertTrue(Terms.isTruthy(" "));
        assertTrue(Terms.isTruthy("foo"));
        assertTrue(Terms.isTruthy(Collections.emptyMap()));
        assertTrue(Terms.isTruthy(Collections.emptyList()));
    }

    @Test
    void testHex() {
        Number num = Terms.toNumber("0xffffffff");
        assertEquals(4294967295L, num.longValue());
    }

}

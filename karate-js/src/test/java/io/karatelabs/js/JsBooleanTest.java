package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsBooleanTest extends EvalBase {

    @Test
    void testConstructor() {
        assertEquals(false, eval("Boolean()"));
        assertEquals(true, eval("Boolean(true)"));
        assertEquals(false, eval("Boolean('')"));
    }

    @Test
    void testPrototype() {
        assertEquals(1, eval("[ true, false, undefined ].filter(Boolean).length"));
    }

}

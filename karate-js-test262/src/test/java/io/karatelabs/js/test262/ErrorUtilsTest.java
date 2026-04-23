package io.karatelabs.js.test262;

import io.karatelabs.js.EngineException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorUtilsTest {

    @Test
    void testStructuredNameTakesPrecedence() {
        // Even if the message mentions TypeError, the structured name wins.
        EngineException ee = new EngineException(
                "Error: TypeError mentioned but actually Error", null, "Error");
        assertEquals("Error", ErrorUtils.classify(ee));
    }

    @Test
    void testStructuredNameCanonicalized() {
        EngineException ee = new EngineException("whatever", null, "typeerror");
        assertEquals("TypeError", ErrorUtils.classify(ee));
    }

    @Test
    void testClassifyTypeErrorPrefix() {
        assertEquals("TypeError", ErrorUtils.classify(new RuntimeException("TypeError: foo")));
    }

    @Test
    void testClassifyRangeErrorPrefix() {
        assertEquals("RangeError", ErrorUtils.classify(new RuntimeException("RangeError: out of bounds")));
    }

    @Test
    void testClassifyFramedErrorFindsTypedName() {
        // Engine framing often decorates: "js failed: ... Error: TypeError: x"
        String msg = "js failed:\n==========\n  Error: TypeError: nope\n==========";
        assertEquals("TypeError", ErrorUtils.classify(new RuntimeException(msg)));
    }

    @Test
    void testClassifyReferenceErrorFromIsNotDefined() {
        assertEquals("ReferenceError", ErrorUtils.classify(new RuntimeException("foo is not defined")));
    }

    @Test
    void testClassifyUnknownReturnsNull() {
        assertNull(ErrorUtils.classify(new RuntimeException("something weird happened")));
    }

    @Test
    void testClassifyEmbeddedErrorName() {
        // Wrapper messages that embed the real error name after a non-word separator.
        String msg = "expression: $262.createRealm().global - TypeError: cannot read properties of null";
        assertEquals("TypeError", ErrorUtils.classify(new RuntimeException(msg)));
    }

    @Test
    void testClassifyEmbeddedErrorNameNotInsideIdentifier() {
        // A substring that's part of a larger identifier must not classify.
        assertNull(ErrorUtils.classify(new RuntimeException("myTypeError: should not match")));
    }

    @Test
    void testClassifyEmbeddedErrorNameAfterSpace() {
        assertEquals("RangeError",
                ErrorUtils.classify(new RuntimeException("wrapped up: RangeError: out of bounds")));
    }

    @Test
    void testClassifyWalksCauseChainForStructuredName() {
        EngineException inner = new EngineException("TypeError: x", null, "TypeError");
        RuntimeException outer = new RuntimeException("wrapped", inner);
        assertEquals("TypeError", ErrorUtils.classify(outer));
    }

    @Test
    void testClassifyWalksCauseChainForMessagePrefix() {
        Throwable inner = new RuntimeException("TypeError: deep");
        Throwable outer = new RuntimeException("generic wrapper", inner);
        assertEquals("TypeError", ErrorUtils.classify(outer));
    }

    @Test
    void testFirstLineTruncation() {
        assertEquals("abc", ErrorUtils.firstLine("abc\ndef", 100));
        assertNull(ErrorUtils.firstLine(null, 100));
        assertTrue(ErrorUtils.firstLine("x".repeat(500), 50).length() <= 50);
    }

    @Test
    void testFirstLineUnwrapsFraming() {
        String msg = "js failed:\n==========\n  File: foo.js\n  Code: bar\n  Error: real problem here\n==========";
        assertEquals("real problem here", ErrorUtils.firstLine(msg, 200));
    }
}

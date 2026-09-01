package io.karatelabs.js.test262;

import io.karatelabs.js.Engine;
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
    void testClassifyThrownNonErrorValue() {
        // A JS-side message with no error name is a thrown non-Error value.
        EngineException ee = new EngineException("js failed: boom", null, null, "boom");
        assertEquals("ThrownValue", ErrorUtils.classify(ee));
    }

    @Test
    void testClassifyThrownValueWalksCauseChain() {
        EngineException inner = new EngineException("js failed: 42", null, null, "42");
        assertEquals("ThrownValue", ErrorUtils.classify(new RuntimeException("wrapped", inner)));
    }

    @Test
    void testStructuredNameBeatsThrownValue() {
        EngineException ee = new EngineException("js failed: x", null, "TypeError", "x");
        assertEquals("TypeError", ErrorUtils.classify(ee));
    }

    @Test
    void testEngineCrashStaysUnclassified() {
        // Neither name nor JS-side message — a Java-origin crash, not a JS throw.
        assertNull(ErrorUtils.classify(new EngineException("something weird happened", null)));
    }

    @Test
    void testClassifyThrownStringFromEngine() {
        Throwable t = assertThrows(Throwable.class, () -> new Engine().eval("throw 'boom'"));
        assertEquals("ThrownValue", ErrorUtils.classify(t));
    }

    @Test
    void testClassifyThrownNumberFromEngine() {
        Throwable t = assertThrows(Throwable.class, () -> new Engine().eval("throw 42"));
        assertEquals("ThrownValue", ErrorUtils.classify(t));
    }

    @Test
    void testClassifyThrownErrorFromEngineKeepsItsName() {
        Throwable t = assertThrows(Throwable.class, () -> new Engine().eval("throw new TypeError('x')"));
        assertEquals("TypeError", ErrorUtils.classify(t));
    }

    @Test
    void testClassifyThrownErrorWithClobberedName() {
        // An Error whose own `name` is wiped still classifies via the
        // constructor.name fallback — it is not a thrown non-Error value.
        Throwable undef = assertThrows(Throwable.class,
                () -> new Engine().eval("var e = new TypeError('x'); e.name = undefined; throw e"));
        assertEquals("TypeError", ErrorUtils.classify(undef));
        Throwable nul = assertThrows(Throwable.class,
                () -> new Engine().eval("var e = new TypeError('x'); e.name = null; throw e"));
        assertEquals("TypeError", ErrorUtils.classify(nul));
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

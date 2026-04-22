/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineExceptionTest {

    private static EngineException assertThrowsEngineException(String script) {
        Engine engine = new Engine();
        return assertThrows(EngineException.class, () -> engine.eval(script));
    }

    @Test
    void testTopLevelThrowTypeError() {
        EngineException ex = assertThrowsEngineException("throw new TypeError('bad arg')");
        assertTrue(ex.getMessage().contains("TypeError:"), ex.getMessage());
        assertTrue(ex.getMessage().contains("bad arg"), ex.getMessage());
        assertEquals("TypeError", ex.getJsErrorName());
    }

    @Test
    void testTopLevelThrowError() {
        EngineException ex = assertThrowsEngineException("throw new Error('boom')");
        assertTrue(ex.getMessage().contains("Error:"), ex.getMessage());
        assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
        assertEquals("Error", ex.getJsErrorName());
    }

    @Test
    void testTopLevelThrowCustomNamedError() {
        // simulate a RangeError via user-assigned name (engine does not pre-register RangeError)
        EngineException ex = assertThrowsEngineException(
                "var e = new Error('out of bounds'); e.name = 'RangeError'; throw e;");
        assertTrue(ex.getMessage().contains("RangeError:"), ex.getMessage());
        assertTrue(ex.getMessage().contains("out of bounds"), ex.getMessage());
        assertEquals("RangeError", ex.getJsErrorName());
    }

    @Test
    void testStructuredNameDistinguishesErrorFromInnerTypeErrorMention() {
        // new Error('TypeError: x') must classify as Error, not TypeError —
        // structured name trumps any substring match on the message.
        EngineException ex = assertThrowsEngineException(
                "throw new Error('TypeError: look elsewhere')");
        assertEquals("Error", ex.getJsErrorName());
    }

    @Test
    void testReferenceToUndefinedVar() {
        EngineException ex = assertThrowsEngineException("noSuchVariable");
        assertTrue(ex.getMessage().contains("noSuchVariable"), ex.getMessage());
        // engine-origin error — not from a JS `throw`, so no structured name
        assertNull(ex.getJsErrorName());
    }

    @Test
    void testEngineProducedTypeErrorPrefix() {
        // Prototype.putMember emits "TypeError: Cannot add property ..."
        EngineException ex = assertThrowsEngineException("Array.prototype.foo = 1");
        assertTrue(ex.getMessage().contains("TypeError:"), ex.getMessage());
        // engine-origin; structured name is null, message-prefix classification kicks in
        assertNull(ex.getJsErrorName());
    }

    @Test
    void testEngineProducedRangeErrorPrefix() {
        // JsNumberPrototype emits "RangeError: precision must be between 1 and 100"
        EngineException ex = assertThrowsEngineException("(123).toPrecision(0)");
        assertTrue(ex.getMessage().contains("RangeError:"), ex.getMessage());
        assertNull(ex.getJsErrorName());
    }

    @Test
    void testEngineExceptionPreservesCause() {
        EngineException ex = assertThrowsEngineException("throw new TypeError('x')");
        assertNotNull(ex.getCause(), "cause should not be null");
    }
}

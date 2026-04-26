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

import java.util.Map;

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
        // engine-origin ReferenceError — message prefix "ReferenceError: " is parsed
        // into structured name so host callers (and `e instanceof ReferenceError`) work.
        assertEquals("ReferenceError", ex.getJsErrorName());
    }

    @Test
    void testEngineProducedTypeErrorPrefix() {
        // Calling a non-callable emits "TypeError: ... is not a function"
        EngineException ex = assertThrowsEngineException("var x = 1; x()");
        assertTrue(ex.getMessage().contains("TypeError:"), ex.getMessage());
        assertEquals("TypeError", ex.getJsErrorName());
    }

    @Test
    void testEngineProducedRangeErrorPrefix() {
        // JsNumberPrototype emits "RangeError: precision must be between 1 and 100"
        EngineException ex = assertThrowsEngineException("(123).toPrecision(0)");
        assertTrue(ex.getMessage().contains("RangeError:"), ex.getMessage());
        assertEquals("RangeError", ex.getJsErrorName());
    }

    @Test
    void testPropertyAccessOnUndefinedIsTypeError() {
        EngineException ex = assertThrowsEngineException("var o; o.foo;");
        assertTrue(ex.getMessage().contains("TypeError:"), ex.getMessage());
        assertEquals("TypeError", ex.getJsErrorName());
    }

    @Test
    void testTryCatchPreservesTypeErrorIdentity() {
        // A property access on undefined, caught by JS try/catch, should produce a
        // JsError whose name is "TypeError" — so `e instanceof TypeError` works.
        Engine engine = new Engine();
        Object result = engine.eval(
                "var r = {}; try { var o; o.foo; } catch (e) {" +
                " r.name = e.name; r.isType = e instanceof TypeError;" +
                " r.isRef = e instanceof ReferenceError; r.isError = e instanceof Error; } r;");
        assertNotNull(result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("TypeError", map.get("name"));
        assertEquals(Boolean.TRUE, map.get("isType"));
        assertEquals(Boolean.FALSE, map.get("isRef"));
        assertEquals(Boolean.TRUE, map.get("isError")); // TypeError is-a Error
    }

    @Test
    void testTryCatchPreservesReferenceErrorIdentity() {
        Engine engine = new Engine();
        Object result = engine.eval(
                "var r = {}; try { noSuchVar; } catch (e) {" +
                " r.name = e.name; r.isRef = e instanceof ReferenceError; } r;");
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("ReferenceError", map.get("name"));
        assertEquals(Boolean.TRUE, map.get("isRef"));
    }

    @Test
    void testEngineErrorConstructorIdentity() {
        // The test262 harness's assert.throws reads `thrown.constructor.name` —
        // so engine-generated errors must expose a non-null .constructor that
        // points to the registered global (matching `e.constructor === TypeError`).
        Engine engine = new Engine();
        Object result = engine.eval(
                "var r = {}; try { noSuchVar; } catch (e) {" +
                " r.ctorName = e.constructor.name; r.ctorIs = (e.constructor === ReferenceError); } r;");
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("ReferenceError", map.get("ctorName"));
        assertEquals(Boolean.TRUE, map.get("ctorIs"));
    }

    @Test
    void testEvalGlobalIsCallable() {
        // `eval` must be defined as a global so indirect invocation via (0, eval)(...)
        // works. Real test262 harness uses $262.evalScript which is built on indirect eval.
        // `typeof eval === "function"` is a separate issue — all JsInvokable globals
        // currently report "object" (tracked separately).
        Engine engine = new Engine();
        assertNotEquals("undefined", engine.eval("typeof eval"));
        assertEquals(3.0, ((Number) engine.eval("eval('1 + 2')")).doubleValue());
        assertEquals(3.0, ((Number) engine.eval("(0, eval)('1 + 2')")).doubleValue());
    }

    @Test
    void testEngineExceptionPreservesCause() {
        EngineException ex = assertThrowsEngineException("throw new TypeError('x')");
        assertNotNull(ex.getCause(), "cause should not be null");
    }

    @Test
    void testGetJsMessageReturnsUnframedJsSideMessage() {
        // getMessage() carries the host-facing "js failed: / Code: / Error: ..." frame
        // for logs; getJsMessage() exposes the raw JS-side message that JS code would
        // see via `e.message` inside a catch. Lets host callers (e.g. the test262
        // runner's ErrorUtils.firstLine) skip the framing-parse step.
        EngineException ex = assertThrowsEngineException("throw new TypeError('bad arg')");
        assertEquals("bad arg", ex.getJsMessage());
        assertTrue(ex.getMessage().contains("TypeError"), ex.getMessage());
    }

    @Test
    void testGetJsMessageForEngineProducedRuntimeError() {
        // Engine-emitted TypeError (calling a non-function) — JsError payload's
        // message is the unframed body without the "TypeError: " prefix.
        EngineException ex = assertThrowsEngineException("var x = 1; x()");
        assertNotNull(ex.getJsMessage());
        assertTrue(ex.getJsMessage().contains("not a function"), ex.getJsMessage());
        // Must not carry the host frame in the JS-side surface.
        assertFalse(ex.getJsMessage().contains("=========="), ex.getJsMessage());
        assertFalse(ex.getJsMessage().contains("js failed"), ex.getJsMessage());
    }

    @Test
    void testJsErrorExceptionFactoriesCarryStructuredPayload() {
        // JsErrorException is the Java carrier engine code uses to throw JS errors.
        // Each factory produces a JsError payload with the right .name, and the
        // exception's own Java-side message uses the "<name>: <msg>" form.
        JsErrorException type = JsErrorException.typeError("bad arg");
        assertEquals("TypeError", type.payload.getName());
        assertEquals("bad arg", type.payload.getMessageString());
        assertEquals("TypeError: bad arg", type.getMessage());

        JsErrorException ref = JsErrorException.referenceError("x is not defined");
        assertEquals("ReferenceError", ref.payload.getName());

        JsErrorException range = JsErrorException.rangeError("out of range");
        assertEquals("RangeError", range.payload.getName());

        JsErrorException syntax = JsErrorException.syntaxError("bad token");
        assertEquals("SyntaxError", syntax.payload.getName());

        JsErrorException generic = JsErrorException.error("oops");
        assertEquals("Error", generic.payload.getName());
    }

    @Test
    void testJsErrorToStringDoesNotDoublePrefixWhenMessageStartsWithName() {
        // Regression: JsError.toString must not produce "Error: Error: x" when
        // the message already carries the "Error: " prefix. This happens both
        // when the engine has injected the prefix at evalProgram, and when
        // sta.js's Test262Error.prototype.toString has run on the receiver.
        Engine engine = new Engine();
        Object plain = engine.eval("'' + new Error('plain message')");
        assertEquals("Error: plain message", plain);
        Object preFixed = engine.eval("'' + new Error('Error: already prefixed')");
        assertEquals("Error: already prefixed", preFixed);
        Object typeErr = engine.eval("'' + new TypeError('TypeError: x')");
        assertEquals("TypeError: x", typeErr);
    }

    @Test
    void testNonJsJavaExceptionCaughtAsGenericError() {
        // When a raw Java exception (not JsErrorException) escapes host code and
        // is caught by JS try/catch, it surfaces as a generic Error with the
        // Java message — the JS side should see name === "Error" and the
        // message text intact.
        Engine engine = new Engine();
        engine.put("boom", (io.karatelabs.js.JsInvokable) args -> {
            throw new IllegalStateException("host-side boom");
        });
        Object result = engine.eval(
                "var r = {}; try { boom(); } catch (e) {" +
                " r.name = e.name; r.msg = e.message;" +
                " r.isError = e instanceof Error; } r;");
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("Error", map.get("name"));
        assertTrue(((String) map.get("msg")).contains("host-side boom"));
        assertEquals(Boolean.TRUE, map.get("isError"));
    }
}

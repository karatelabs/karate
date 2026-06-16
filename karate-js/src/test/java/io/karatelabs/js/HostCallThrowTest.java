package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A host (Java) caller that invokes a JS function directly — {@code fn.call(host, args)} with no JS
 * caller context — must observe an uncaught JS {@code throw} as a Java {@link EngineException}, exactly
 * as {@code engine.eval} surfaces it at the statement boundary. Before the host-invocation boundary was
 * wired in {@link JsFunctionNode#call}, the throw was silently swallowed and the call returned normally.
 */
class HostCallThrowTest extends EvalBase {

    private JavaCallable define(String fnDecl) {
        eval(fnDecl);
        return (JavaCallable) engine.get("f");
    }

    @Test
    void explicitStringThrowSurfacesToHost() {
        JavaCallable f = define("function f() { throw 'boom' }");
        EngineException e = assertThrows(EngineException.class, () -> f.call(null, new Object[0]));
        // same JS-side surface engine.eval would expose for the same throw
        assertEquals("boom", e.getJsMessage());
        EngineException viaEval = assertThrows(EngineException.class, () -> new Engine().eval("(function(){ throw 'boom' })()"));
        assertEquals(viaEval.getJsMessage(), e.getJsMessage());
    }

    @Test
    void errorObjectThrowCarriesNameAndMessage() {
        JavaCallable f = define("function f() { throw new TypeError('nope') }");
        EngineException e = assertThrows(EngineException.class, () -> f.call(null, new Object[0]));
        assertEquals("TypeError", e.getJsErrorName());
        assertEquals("nope", e.getJsMessage());
    }

    @Test
    void throwInsideConditionalBranchSurfaces() {
        JavaCallable f = define("function f(x) { if (x === 'silver') { throw 'restricted' } return 'ok' }");
        assertEquals("ok", f.call(null, new Object[]{"gold"}));
        EngineException e = assertThrows(EngineException.class, () -> f.call(null, new Object[]{"silver"}));
        assertEquals("restricted", e.getJsMessage());
    }

    @Test
    void cleanReturnIsUnaffected() {
        JavaCallable f = define("function f(a, b) { return a + b }");
        assertEquals(5, f.call(null, new Object[]{2, 3}));
    }

    @Test
    void hostErrorDoesNotLeaveDeclaringContextDirty() {
        // a throwing host call must not poison the next host call on the same engine's function
        JavaCallable f = define("function f(x) { if (x) { throw 'bad' } return 'good' }");
        assertThrows(EngineException.class, () -> f.call(null, new Object[]{true}));
        assertEquals("good", f.call(null, new Object[]{false}));
    }

    @Test
    void jsToJsTryCatchStillCatches() {
        // the JS-to-JS path is unchanged: a surrounding JS try/catch still intercepts the throw
        Object caught = eval("function f() { throw 'x' } var r; try { f() } catch (e) { r = 'caught:' + e } r");
        assertEquals("caught:x", caught);
    }

    @Test
    void uncaughtThrowAtEvalBoundaryUnchanged() {
        // regression guard: the existing top-level boundary still throws for an uncaught throw
        EngineException e = assertThrows(EngineException.class, () -> new Engine().eval("function f() { throw 'top' } f()"));
        assertEquals("top", e.getJsMessage());
        assertInstanceOf(EngineException.class, e);
        assertTrue(e.getMessage().contains("top"));
    }
}

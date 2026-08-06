package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a {@code finally} block interacts with the completion the {@code try} or {@code catch} left
 * behind. The rule is one sentence — evaluate the finally block against a <i>clean</i> completion
 * state; if it completes abruptly its completion wins, and otherwise the saved one is restored —
 * and every case below is a way of getting that wrong.
 *
 * <p>Two of them were: a finally block after a clean {@code return} ran only its first statement,
 * because the context was still flagged as stopped and block evaluation halts on that; and an
 * abrupt completion produced <i>by</i> the finally block was overwritten by the saved one instead
 * of replacing it. The first silently skipped cleanup code, which is the entire purpose of a
 * finally block.</p>
 */
class TryFinallyCompletionTest extends EvalBase {

    // ---------------------------------------------------------------- the finally block runs whole

    @Test
    void everyStatementOfAFinallyRunsAfterACleanReturn() {
        assertEquals("ab", eval("var s = ''; function f() { try { return 7 } finally { s += 'a'; s += 'b' } } f(); s"));
    }

    @Test
    void everyStatementOfAFinallyRunsAfterAThrow() {
        assertEquals("ab", eval("var s = ''; function f() { try { throw 'x' } finally { s += 'a'; s += 'b' } }"
                + " try { f() } catch (e) { } s"));
    }

    @Test
    void everyStatementOfAFinallyRunsAfterABreak() {
        assertEquals("ab", eval("var s = ''; for (var i = 0; i < 3; i++) { try { break } finally { s += 'a'; s += 'b' } } s"));
    }

    @Test
    void everyStatementOfAFinallyRunsAfterAContinue() {
        assertEquals("abab", eval("var s = ''; for (var i = 0; i < 2; i++) { try { continue } finally { s += 'a'; s += 'b' } } s"));
    }

    @Test
    void everyStatementOfAFinallyRunsOnANormalCompletion() {
        assertEquals("tab", eval("var s = ''; function f() { try { s += 't' } finally { s += 'a'; s += 'b' } } f(); s"));
    }

    // ---------------------------------------------------------------- the saved completion survives

    @Test
    void aReturnValueSurvivesTheFinally() {
        assertEquals(7, eval("function f() { try { return 7 } finally { 'ignored' } } f()"));
    }

    @Test
    void aReturnedNullSurvivesTheFinallyAsNullNotUndefined() {
        // `return null` and falling off the end are different completions, and restoring the saved
        // one by testing its VALUE for null cannot tell them apart — it has to test the type.
        assertEquals("null", eval("function f() { try { return null } finally { 'ignored' } } String(f())"));
    }

    @Test
    void aPendingThrowSurvivesTheFinally() {
        assertEquals("caught:x", eval("function f() { try { throw 'x' } finally { 'ignored' } }"
                + " var r; try { f() } catch (e) { r = 'caught:' + e } r"));
    }

    @Test
    void aBreakSurvivesTheFinally() {
        // break on the first pass, so neither the rest of the loop body nor another iteration runs
        assertEquals("0:0", eval("var n = 0; for (var i = 0; i < 5; i++) { try { break } finally { 'ignored' } n++ } i + ':' + n"));
    }

    @Test
    void aContinueSurvivesTheFinally() {
        assertEquals(0, eval("var n = 0; for (var i = 0; i < 3; i++) { try { continue } finally { 'ignored' } n++ } n"));
    }

    // ---------------------------------------------------------------- an abrupt finally wins

    @Test
    void aReturnInFinallyOverridesAPendingThrow() {
        assertEquals(42, eval("function f() { try { throw 'x' } finally { return 42 } } f()"));
    }

    @Test
    void aReturnInFinallyOverridesAThrowArrivingThroughAReturnExpression() {
        assertEquals(42, eval("function inner() { throw 'x' } function f() { try { return inner() } finally { return 42 } } f()"));
    }

    @Test
    void aReturnInFinallyOverridesAPendingReturn() {
        assertEquals(42, eval("function f() { try { return 7 } finally { return 42 } } f()"));
    }

    @Test
    void aThrowInFinallyReplacesThePendingThrowAndStaysCatchableFromJs() {
        assertEquals("caught:fin", eval("function f() { try { throw 'first' } finally { throw 'fin' } }"
                + " var r; try { f() } catch (e) { r = 'caught:' + e } r"));
    }

    @Test
    void aThrowInFinallyAfterACleanTryIsCatchableFromJs() {
        assertEquals("caught:fin", eval("function f() { try { 'ok' } finally { throw 'fin' } }"
                + " var r; try { f() } catch (e) { r = 'caught:' + e } r"));
    }

    @Test
    void aBreakInFinallyOverridesAPendingThrow() {
        assertEquals("done", eval("var s = 'none';"
                + " for (var i = 0; i < 3; i++) { try { throw 'x' } finally { break } }"
                + " s = 'done'; s"));
    }

    // ---------------------------------------------------------------- catch, then finally

    @Test
    void aFinallyAfterACatchStillRunsWholeAndKeepsTheCatchResult() {
        assertEquals("ab", eval("var s = ''; function f() { try { throw 'x' } catch (e) { return 'r' } finally { s += 'a'; s += 'b' } }"
                + " f(); s"));
    }

    @Test
    void aReturnFromCatchSurvivesTheFinally() {
        assertEquals("r", eval("function f() { try { throw 'x' } catch (e) { return 'r' } finally { 'ignored' } } f()"));
    }

    @Test
    void aThrowFromCatchSurvivesTheFinally() {
        assertEquals("caught:rethrown", eval("function f() { try { throw 'x' } catch (e) { throw 'rethrown' } finally { 'ignored' } }"
                + " var r; try { f() } catch (e) { r = 'caught:' + e } r"));
    }

}

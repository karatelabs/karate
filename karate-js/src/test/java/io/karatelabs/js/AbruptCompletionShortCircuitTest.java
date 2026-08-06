package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Once a sub-expression has thrown, nothing after it in the same expression may run.
 *
 * <p>This was wrong in six places and was invisible in all of them, because the throw still
 * surfaced: the exception a caller saw was correct, and only the side effects along the way gave
 * it away. Three of the six went further and <b>invoked the function anyway</b> — an argument
 * expression failed, and the callee ran regardless, which for anything with real side effects (an
 * HTTP call in a Karate step, say) is not a cosmetic difference.</p>
 *
 * <p>Each case appends a letter as it evaluates, so the assertion is the trace and not the value:
 * {@code b()} records its letter and throws, {@code ok()} records its letter and returns.</p>
 */
class AbruptCompletionShortCircuitTest extends EvalBase {

    /** {@code b} throws after recording, {@code ok} records and returns, {@code s} is the trace. */
    private static final String SETUP =
            "var s = ''; function b(t) { s += t; throw 'x' } function ok(t) { s += t; return 1 } ";

    private void trace(String expected, String expression) {
        assertEquals(expected, eval(SETUP + "try { " + expression + " } catch (e) { } s"));
    }

    @Test
    void aThrownLeftOperandStopsTheRightOne() {
        trace("L", "b('L') + ok('R')");
        trace("L", "b('L') * ok('R')");
        trace("L", "b('L') instanceof ok('R')");
    }

    @Test
    void aThrownElementStopsTheRestOfAnArrayLiteral() {
        trace("L", "[b('L'), ok('R')]");
    }

    @Test
    void aThrownValueStopsTheRestOfAnObjectLiteral() {
        trace("L", "({ a: b('L'), c: ok('R') })");
    }

    @Test
    void aThrownArgumentStopsTheRestOfTheArgumentsAndTheCallItself() {
        trace("L", "(function f(x, y) { s += 'F' })(b('L'), ok('R'))");
    }

    @Test
    void aThrownArgumentStopsAConstructorCall() {
        trace("L", "new (function C(x) { s += 'C' })(b('L'))");
    }

    @Test
    void aThrownSpreadArgumentStopsTheCall() {
        trace("L", "(function f() { s += 'F' })(...[b('L')])");
    }

    @Test
    void aThrownLeftOperandStopsACommaExpression() {
        trace("L", "(b('L'), ok('R'))");
    }

    /**
     * The cases that were already right, kept so a future change to the guards above cannot
     * quietly make one of them evaluate too much — or too little.
     */
    @Test
    void theShortCircuitingFormsAreUnchanged() {
        trace("L", "b('L') ? ok('T') : ok('F')");
        trace("L", "b('L') && ok('R')");
        trace("L", "`${b('L')}${ok('R')}`");
        trace("L", "if (b('L')) { s += 'T' } else { s += 'F' }");
        trace("L", "while (b('L')) { s += 'B' }");
        trace("L", "for (var i = b('L'); i < 2; i++) { s += 'B' }");
        trace("L", "switch (b('L')) { case 1: s += 'C' }");
        trace("L", "b('L').foo");
        trace("L", "({})[b('L')]");
    }

    /** And an expression that does not throw still evaluates all of itself. */
    @Test
    void aCleanExpressionStillEvaluatesEverything() {
        trace("LR", "ok('L') + ok('R')");
        trace("LR", "[ok('L'), ok('R')]");
        trace("LRF", "(function f(x, y) { s += 'F' })(ok('L'), ok('R'))");
    }

}

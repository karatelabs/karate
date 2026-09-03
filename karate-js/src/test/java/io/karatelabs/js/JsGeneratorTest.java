package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generators — function* / yield / yield*, driven through the vthread
 * coroutine in GeneratorActivation. Covers the externally-reviewed design's
 * test plan: sequencing, sent values, edge states, try/finally interaction,
 * yield* delegation, and the iteration-protocol consumers.
 */
class JsGeneratorTest extends EvalBase {

    @Test
    void testBasicYieldSequence() {
        assertEquals(List.of(1, 2, 3), eval("function* g() { yield 1; yield 2; yield 3 }\n[...g()]"));
    }

    @Test
    void testNextResultShape() {
        assertEquals(true, eval("function* g() { yield 'a' }\nvar it = g();\n"
                + "var r1 = it.next(); var r2 = it.next();\n"
                + "r1.value === 'a' && r1.done === false && r2.value === undefined && r2.done === true"));
    }

    @Test
    void testReturnValueAppearsInFinalResult() {
        assertEquals(true, eval("function* g() { yield 1; return 42 }\nvar it = g();\n"
                + "it.next();\nvar r = it.next();\nr.value === 42 && r.done === true"));
    }

    @Test
    void testSentValues() {
        assertEquals(30, eval("function* g() { var a = yield 1; var b = yield 2; return a + b }\n"
                + "var it = g();\nit.next();\nit.next(10);\nit.next(20).value"));
    }

    @Test
    void testFirstNextValueIsDiscarded() {
        assertEquals(1, eval("function* g() { yield 1 }\ng().next(99).value"));
    }

    @Test
    void testBodyDoesNotRunUntilFirstNext() {
        assertEquals(0, eval("var ran = 0;\nfunction* g() { ran = 1; yield 1 }\ng();\nran"));
    }

    @Test
    void testInfiniteGeneratorIsLazy() {
        assertEquals(List.of(0, 1, 2), eval("function* nat() { var i = 0; while (true) yield i++ }\n"
                + "var it = nat();\n[it.next().value, it.next().value, it.next().value]"));
    }

    @Test
    void testGeneratorsAreIndependent() {
        assertEquals(true, eval("function* g() { yield 1; yield 2 }\n"
                + "var a = g(); var b = g();\n"
                + "a.next(); a.next();\nb.next().value === 1"));
    }

    // ===== edge states (spec §27.5.1) =====

    @Test
    void testNextAfterDone() {
        assertEquals(true, eval("function* g() { yield 1 }\nvar it = g();\n"
                + "it.next(); it.next();\nvar r = it.next();\nr.value === undefined && r.done === true"));
    }

    @Test
    void testNextImmediatelyAfterCompletionNeverSeesRunning() {
        // the body's final outcome must retire the generator before the
        // driver can take the lock back — a next() issued the instant the
        // completing step returns used to race the DONE transition and throw
        // "already running" (timing-dependent, so many rounds)
        assertEquals(true, eval("function* g() { yield 1 }\n"
                + "for (var i = 0; i < 2000; i++) {\n"
                + "  var it = g(); it.next(); it.next();\n"
                + "  var r = it.next();\n"
                + "  if (r.value !== undefined || r.done !== true) throw new Error('round ' + i);\n"
                + "}\ntrue"));
    }

    @Test
    void testReturnOnFreshGeneratorRunsNoBody() {
        assertEquals(true, eval("var ran = false;\nfunction* g() { ran = true; yield 1 }\n"
                + "var it = g();\nvar r = it.return(7);\n"
                + "!ran && r.value === 7 && r.done === true && it.next().done === true"));
    }

    @Test
    void testThrowOnFreshGeneratorThrowsToCaller() {
        assertEquals("boom", eval("function* g() { yield 1 }\nvar it = g();\n"
                + "try { it.throw(new Error('boom')) } catch (e) { e.message }"));
    }

    @Test
    void testThrowAtYieldIsCatchableInBody() {
        assertEquals("caught:x", eval("function* g() { try { yield 1 } catch (e) { yield 'caught:' + e } }\n"
                + "var it = g();\nit.next();\nit.throw('x').value"));
    }

    @Test
    void testThrowUncaughtInBodyPropagatesToCaller() {
        assertEquals("up", eval("function* g() { yield 1 }\nvar it = g();\nit.next();\n"
                + "try { it.throw('up') } catch (e) { e }"));
    }

    @Test
    void testBodyThrowPropagatesToDriver() {
        assertEquals("bad", eval("function* g() { yield 1; throw new Error('bad') }\n"
                + "var it = g();\nit.next();\ntry { it.next() } catch (e) { e.message }"));
    }

    @Test
    void testReentrancyIsTypeError() {
        assertEquals("TypeError", eval("var it;\nfunction* g() { yield it.next() }\nit = g();\n"
                + "try { it.next() } catch (e) { e.name }"));
    }

    @Test
    void testBrandCheckOnBorrowedNext() {
        assertEquals("TypeError", eval("function* g() { yield 1 }\nvar n = g().next;\n"
                + "try { n.call({}) } catch (e) { e.name }"));
    }

    // ===== try/finally interaction =====

    @Test
    void testReturnRunsFinally() {
        assertEquals(true, eval("var cleaned = false;\n"
                + "function* g() { try { yield 1 } finally { cleaned = true } }\n"
                + "var it = g();\nit.next();\nvar r = it.return(9);\n"
                + "cleaned && r.value === 9 && r.done === true"));
    }

    @Test
    void testYieldInsideFinallySuspendsDuringReturn() {
        // return(9) first surfaces the finally's yield ({2,false}); the next()
        // then completes with the parked return completion ({9,true})
        assertEquals(true, eval("function* g() { try { yield 1 } finally { yield 2 } }\n"
                + "var it = g();\nit.next();\n"
                + "var r1 = it.return(9);\nvar r2 = it.next();\n"
                + "r1.value === 2 && r1.done === false && r2.value === 9 && r2.done === true"));
    }

    @Test
    void testFinallyReturnOverridesInjectedReturn() {
        assertEquals(true, eval("function* g() { try { yield 1 } finally { return 55 } }\n"
                + "var it = g();\nit.next();\nvar r = it.return(9);\n"
                + "r.value === 55 && r.done === true"));
    }

    @Test
    void testForOfBreakClosesGeneratorAndRunsFinally() {
        assertEquals(true, eval("var cleaned = false;\n"
                + "function* g() { try { yield 1; yield 2; yield 3 } finally { cleaned = true } }\n"
                + "var seen = [];\nfor (var x of g()) { seen.push(x); if (x === 2) break }\n"
                + "cleaned && seen.length === 2"));
    }

    // ===== yield* delegation =====

    @Test
    void testYieldStarSequencing() {
        assertEquals(List.of(1, 2, 3, 4), eval("function* inner() { yield 2; yield 3 }\n"
                + "function* outer() { yield 1; yield* inner(); yield 4 }\n[...outer()]"));
    }

    @Test
    void testYieldStarInnerReturnValue() {
        assertEquals(42, eval("function* inner() { return 42 }\n"
                + "function* outer() { return yield* inner() }\nouter().next().value"));
    }

    @Test
    void testYieldStarForwardsSentValues() {
        assertEquals(11, eval("function* inner() { var v = yield 1; return v + 1 }\n"
                + "function* outer() { return yield* inner() }\n"
                + "var it = outer();\nit.next();\nit.next(10).value"));
    }

    @Test
    void testYieldStarForwardsThrowToDelegate() {
        assertEquals("inner:x", eval("function* inner() { try { yield 1 } catch (e) { yield 'inner:' + e } }\n"
                + "function* outer() { yield* inner() }\n"
                + "var it = outer();\nit.next();\nit.throw('x').value"));
    }

    @Test
    void testYieldStarOverIterables() {
        assertEquals(List.of("a", "b", 1, 2), eval("function* g() { yield* 'ab'; yield* [1, 2] }\n[...g()]"));
    }

    @Test
    void testYieldStarReturnDoneFalseKeepsDelegating() {
        // a delegate whose return() reports done:false keeps the outer
        // generator suspended and delegation alive (spec §27.5.3.7)
        assertEquals(true, eval("var delegate = {\n"
                + "  next() { return { value: 1, done: false } },\n"
                + "  'return'(v) { return { value: 2, done: false } },\n"
                + "  '@@iterator'() { return this }\n"
                + "};\n"
                + "function* g() { yield* delegate }\n"
                + "var it = g();\nit.next();\nvar r = it.return(9);\n"
                + "r.value === 2 && r.done === false"));
    }

    // ===== iteration-protocol consumers =====

    @Test
    void testSpreadAndDestructuring() {
        assertEquals(true, eval("function* g() { yield 1; yield 2; yield 3 }\n"
                + "var [a, ...rest] = g();\na === 1 && rest.length === 2 && rest[1] === 3"));
    }

    @Test
    void testArrayFrom() {
        assertEquals(List.of(2, 4), eval("function* g() { yield 1; yield 2 }\n"
                + "Array.from(g(), x => x * 2)"));
    }

    @Test
    void testGeneratorIsItsOwnIterator() {
        assertEquals(true, eval("function* g() { yield 1 }\nvar it = g();\nit['@@iterator']() === it"));
    }

    // ===== method forms =====

    @Test
    void testClassGeneratorMethod() {
        assertEquals(List.of(1, 2), eval("class C { *pair() { yield 1; yield 2 } }\n[...new C().pair()]"));
    }

    @Test
    void testStaticClassGeneratorMethod() {
        assertEquals(List.of("s"), eval("class C { static *s() { yield 's' } }\n[...C.s()]"));
    }

    @Test
    void testObjectLiteralGeneratorMethod() {
        assertEquals(List.of(7), eval("var o = { *g() { yield 7 } };\n[...o.g()]"));
    }

    @Test
    void testGeneratorMethodThisBinding() {
        assertEquals(List.of(5), eval("class C { constructor() { this.n = 5 } *vals() { yield this.n } }\n"
                + "[...new C().vals()]"));
    }

    @Test
    void testGeneratorExpressionAndHoisting() {
        assertEquals(List.of(1), eval("var g = function*() { yield 1 };\n[...g()]"));
    }

    // ===== misc semantics =====

    @Test
    void testYieldAsIdentifierOutsideGenerators() {
        assertEquals(5, eval("var yield = 5; yield"));
    }

    @Test
    void testNewOnGeneratorFunctionIsTypeError() {
        assertEquals("TypeError", eval("function* g() {}\ntry { new g() } catch (e) { e.name }"));
    }

    @Test
    void testTypeofGeneratorFunction() {
        assertEquals(true, eval("function* g() { yield 1 }\n"
                + "typeof g === 'function' && typeof g() === 'object'"));
    }

    @Test
    void testYieldOperandAssignmentPrecedence() {
        // yield a + b is yield (a + b)
        assertEquals(3, eval("function* g() { yield 1 + 2 }\ng().next().value"));
    }

    @Test
    void testYieldWithoutOperand() {
        assertEquals(true, eval("function* g() { yield }\nvar r = g().next();\n"
                + "r.value === undefined && r.done === false"));
    }

    @Test
    void testGeneratorClosureState() {
        assertEquals(List.of(10, 20), eval("function make(base) { return function*() { yield base * 1; yield base * 2 } }\n"
                + "[...make(10)()]"));
    }

    @Test
    void testCrossEvalResume() {
        engine = new Engine();
        engine.eval("function* g() { yield 1; yield 2 }\nvar it = g();\nit.next()");
        Object v = engine.eval("it.next().value");
        assertEquals(2, v);
    }

    @Test
    void testEvalInsideGeneratorBodySharesTheScope() {
        // a nested eval() on the generator's vthread is part of the current
        // eval — the thread-identity nesting check used to deadlock here
        assertEquals(42, eval("function* g() { yield eval('40 + 2') }\ng().next().value"));
    }

    @Test
    void testEvalInsideAsyncBodySharesTheScope() {
        // same seam, async-activation flavor (pre-existing deadlock); the
        // second eval reads the drained result
        engine = new Engine();
        engine.eval("async function f() { return eval('40 + 2') }\nvar out;\nf().then(v => out = v)");
        assertEquals(42, engine.eval("out"));
    }

    @Test
    void testYieldStarNonCallableReturnIsTypeError() {
        // GetMethod: a PRESENT non-callable `return` throws — it is not
        // treated as absent (external-review round 4)
        assertEquals("TypeError", eval("var delegate = { next() { return { value: 1, done: false } },\n"
                + "  'return': 42, '@@iterator'() { return this } };\n"
                + "function* g() { yield* delegate }\n"
                + "var it = g();\nit.next();\ntry { it.return(9); 'no throw' } catch (e) { e.name }"));
    }

    @Test
    void testYieldStarThrowGetterErrorWinsWithoutClosing() {
        // a throwing `throw` accessor propagates immediately — return() is
        // never consulted (external-review round 4)
        assertEquals(true, eval("var touched = 0;\n"
                + "var delegate = { next() { return { value: 1, done: false } },\n"
                + "  get 'throw'() { throw 'original' },\n"
                + "  get 'return'() { touched++; return function() {} },\n"
                + "  '@@iterator'() { return this } };\n"
                + "function* g() { yield* delegate }\n"
                + "var it = g();\nit.next();\nvar caught;\n"
                + "try { it.throw('sent') } catch (e) { caught = e }\n"
                + "caught === 'original' && touched === 0"));
    }

    @Test
    void testParamBindingWithDefaults() {
        assertEquals(List.of(5, 6), eval("function* g(a, b = a + 1) { yield a; yield b }\n[...g(5)]"));
    }

}

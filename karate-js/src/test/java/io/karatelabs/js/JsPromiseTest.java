package io.karatelabs.js;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * async / await / Promise — runtime behavior. An async call returns a promise
 * and its body runs on its own activation; {@code await} suspends until the
 * target settles; callbacks always run after the synchronous code that
 * registered them.
 */
@Timeout(20)
class JsPromiseTest extends EvalBase {

    // ===== the shape of an async call =====

    @Test
    void testAsyncCallReturnsAPromise() {
        assertEquals("object", eval("async function f() { return 1 }\ntypeof f()"));
        assertEquals("function", eval("async function f() { return 1 }\ntypeof f().then"));
        assertEquals(true, eval("async function f() { return 1 }\nf() instanceof Promise"));
    }

    @Test
    void testTypeofPromiseAndInstanceof() {
        assertEquals("function", eval("typeof Promise"));
        assertEquals(true, eval("Promise.resolve(1) instanceof Promise"));
        assertEquals(true, eval("new Promise(function (r) { r(1) }) instanceof Promise"));
        assertEquals(false, eval("({}) instanceof Promise"));
    }

    @Test
    void testClassAsyncMethodReturnsAPromise() {
        assertEquals("function", eval("class C { async m() { return 1 } }\ntypeof new C().m().then"));
        assertEquals(5, eval("class C { async m() { return 5 } }\nawait new C().m()"));
        assertEquals(6, eval("class C { static async m() { return 6 } }\nawait C.m()"));
    }

    @Test
    void testNewOnAsyncFunctionIsATypeError() {
        assertEquals("TypeError", eval("async function f() {}\ntry { new f() } catch (e) { e.name }"));
        assertEquals("TypeError", eval("var f = async function () {}\ntry { new f() } catch (e) { e.name }"));
    }

    // ===== await =====

    @Test
    void testTopLevelAwait() {
        assertEquals(1, eval("await Promise.resolve(1)"));
        assertEquals(2, eval("var x = await Promise.resolve(2)\nx"));
    }

    @Test
    void testAwaitNonPromisePassesThrough() {
        assertEquals(5, eval("await 5"));
        assertEquals("x", eval("async function f() { return await 'x' }\nawait f()"));
        assertNull(eval("await null"));
    }

    @Test
    void testSequentialAwaitChain() {
        assertEquals(14, eval("""
                async function inner() { return 7 }
                async function outer() { const v = await inner(); return v * 2 }
                await outer()"""));
        assertEquals(10, eval("""
                async function step(n) { return n + 1 }
                async function run() {
                  let n = 0
                  for (let i = 0; i < 10; i++) { n = await step(n) }
                  return n
                }
                await run()"""));
    }

    @Test
    void testAsyncThrowBecomesARejectedPromiseNotASyncThrow() {
        // the call itself must complete normally — the throw lands on the promise
        assertEquals("function", eval("async function f() { throw new Error('x') }\nvar p = f()\np.catch(function () {})\ntypeof p.then"));
        assertEquals("x", eval("async function f() { throw new Error('x') }\nawait f().catch(function (e) { return e.message })"));
    }

    @Test
    void testTryCatchAroundRejectedAwait() {
        assertEquals("caught:nope", eval("""
                async function bad() { throw new Error('nope') }
                async function run() {
                  try { await bad(); return 'no-throw' } catch (e) { return 'caught:' + e.message }
                }
                await run()"""));
        // a non-Error rejection reason comes back through the same throw path
        assertEquals(42, eval("await (async function () { try { await Promise.reject(42) } catch (e) { return e } })()"));
    }

    @Test
    void testReturnedPromiseIsAdoptedNotNested() {
        assertEquals(3, eval("async function f() { return Promise.resolve(3) }\nawait f()"));
        assertEquals(4, eval("async function f() { return Promise.resolve(Promise.resolve(4)) }\nawait f()"));
        assertEquals("boom", eval("""
                async function f() { return Promise.reject(new Error('boom')) }
                await f().catch(function (e) { return e.message })"""));
    }

    // ===== ordering =====

    @Test
    void testSyncCodeRunsBeforeCallbacks() {
        assertEquals("sync,cb", eval("""
                var log = []
                Promise.resolve(1).then(function () { log.push('cb') })
                log.push('sync')
                await new Promise(function (r) { setTimeout(r, 10) })
                log.join(',')"""));
    }

    @Test
    void testExecutorRunsSynchronously() {
        assertEquals("exec,after", eval("""
                var log = []
                new Promise(function (r) { log.push('exec'); r(1) })
                log.push('after')
                log.join(',')"""));
    }

    @Test
    void testPromiseChain() {
        assertEquals(20, eval("await Promise.resolve(1).then(function (v) { return v + 1 })"
                + ".then(function (v) { return v * 10 }).catch(function () { return -1 })"));
        assertEquals(-1, eval("await Promise.reject(new Error('x')).then(function (v) { return v + 1 })"
                + ".catch(function () { return -1 })"));
        assertEquals("done", eval("""
                var log = []
                await Promise.resolve('done').finally(function () { log.push('f') })"""));
    }

    // ===== combinators =====

    @Test
    void testPromiseAll() {
        assertEquals("2,4,6", eval("var vs = await Promise.all([1, 2, 3].map(function (n) { return Promise.resolve(n * 2) }))\nvs.join(',')"));
        assertEquals("", eval("var vs = await Promise.all([])\nvs.join(',')"));
        // non-promise members pass through
        assertEquals("1,2", eval("var vs = await Promise.all([1, Promise.resolve(2)])\nvs.join(',')"));
        assertEquals("bad", eval("await Promise.all([Promise.resolve(1), Promise.reject(new Error('bad'))])"
                + ".catch(function (e) { return e.message })"));
    }

    @Test
    void testPromiseAllSettled() {
        assertEquals("fulfilled,rejected", eval("""
                var rs = await Promise.allSettled([Promise.resolve(1), Promise.reject(new Error('x'))])
                rs.map(function (r) { return r.status }).join(',')"""));
        assertEquals(1, eval("var rs = await Promise.allSettled([Promise.resolve(1)])\nrs[0].value"));
        assertEquals("x", eval("var rs = await Promise.allSettled([Promise.reject(new Error('x'))])\nrs[0].reason.message"));
    }

    @Test
    void testPromiseRace() {
        assertEquals("a", eval("await Promise.race([Promise.resolve('a'), Promise.resolve('b')])"));
        assertEquals("fast", eval("""
                var slow = new Promise(function (r) { setTimeout(function () { r('slow') }, 200) })
                var fast = new Promise(function (r) { setTimeout(function () { r('fast') }, 10) })
                await Promise.race([slow, fast])"""));
    }

    @Test
    void testPromiseAny() {
        assertEquals("ok", eval("await Promise.any([Promise.reject(new Error('a')), Promise.resolve('ok')])"));
        assertEquals("AggregateError:2", eval("""
                try {
                  await Promise.any([Promise.reject(new Error('a')), Promise.reject(new Error('b'))])
                } catch (e) {
                  e.name + ':' + e.errors.length
                }"""));
        assertEquals("a,b", eval("""
                try {
                  await Promise.any([Promise.reject(new Error('a')), Promise.reject(new Error('b'))])
                } catch (e) {
                  e.errors.map(function (x) { return x.message }).join(',')
                }"""));
    }

    @Test
    void testPromiseResolvePreservesNativeIdentity() {
        assertEquals(true, eval("var p = Promise.resolve(1)\nPromise.resolve(p) === p"));
    }

    // ===== combinators: abrupt completion inside the iteration loop =====

    /** An iterator that never reports done, plus a {@code return} spy. */
    private static final String ENDLESS_ITER = """
            var returnCount = 0
            var iter = {}
            iter[Symbol.iterator] = function () {
              return {
                next: function () { return { value: null, done: false } },
                return: function () { returnCount += 1; return {} }
              }
            }
            """;

    private static final String POISON_RESOLVE = """
            var poison = new Error('poison')
            Promise.resolve = function () { throw poison }
            """;

    private static String awaitCombinator(String name) {
        return "try {\n"
                + "  await Promise." + name + "(iter)\n"
                + "  'not rejected'\n"
                + "} catch (e) {\n"
                + "  (e === poison) + ':' + returnCount\n"
                + "}";
    }

    @Test
    void testCombinatorsRejectAndCloseTheIteratorWhenResolveThrows() {
        // the loop must terminate on the first abrupt `Promise.resolve` even though
        // the iterator would otherwise yield forever, close it exactly once, and
        // surface the thrown value as the rejection reason
        long started = System.currentTimeMillis();
        for (String name : new String[]{"all", "allSettled", "race", "any"}) {
            assertEquals("true:1", eval(ENDLESS_ITER + POISON_RESOLVE + awaitCombinator(name)), name);
        }
        long elapsed = System.currentTimeMillis() - started;
        assertTrue(elapsed < 2000, "combinators took " + elapsed + "ms — expected a prompt abort");
    }

    @Test
    void testCombinatorsRejectWhenTheResolveLookupThrows() {
        // the `resolve` read happens before GetIterator, so nothing is closed —
        // the @@iterator method is never even invoked
        String source = """
                var poison = new Error('poison')
                var reached = false
                var iter = {}
                iter[Symbol.iterator] = function () { reached = true }
                Object.defineProperty(Promise, 'resolve', { get: function () { throw poison } })
                try {
                  await Promise.all(iter)
                  'not rejected'
                } catch (e) {
                  (e === poison) + ':' + reached
                }""";
        assertEquals("true:false", eval(source));
    }

    @Test
    void testAThrowingIteratorCloseDoesNotDisplaceTheOriginalError() {
        String source = """
                var poison = new Error('poison')
                var iter = {}
                iter[Symbol.iterator] = function () {
                  return {
                    next: function () { return { value: null, done: false } },
                    return: function () { throw new Error('close boom') }
                  }
                }
                Promise.resolve = function () { throw poison }
                try {
                  await Promise.all(iter)
                  'not rejected'
                } catch (e) {
                  e.message
                }""";
        assertEquals("poison", eval(source));
    }

    @Test
    void testAnIteratorSideThrowRejectsWithoutClosing() {
        // a throw out of next() / the `done` getter leaves [[done]] true, so
        // IteratorClose must not run (spec 7.4.6 is only reached when not done)
        String source = """
                var stepError = new Error('step')
                var returnCount = 0
                var iter = {}
                iter[Symbol.iterator] = function () {
                  return {
                    next: function () { throw stepError },
                    return: function () { returnCount += 1; return {} }
                  }
                }
                try {
                  await Promise.all(iter)
                  'not rejected'
                } catch (e) {
                  (e === stepError) + ':' + returnCount
                }""";
        assertEquals("true:0", eval(source));
    }

    @Test
    void testANonIterableArgumentRejectsRatherThanThrowing() {
        assertEquals("TypeError", eval("""
                var thrown = 'none'
                var p = Promise.all(null)
                try {
                  await p
                } catch (e) {
                  thrown = e.name
                }
                thrown"""));
    }

    @Test
    void testCombinatorsRejectAndCloseWhenTheElementThenThrows() {
        // `then` is a user-code call site too — a tampered one on the element
        // must abort the loop, not settle the element promise and iterate on
        String source = ENDLESS_ITER.replace("value: null", "value: pending") + """
                var poison = new Error('poison')
                Object.defineProperty(pending, 'then', { value: function () { throw poison } })
                try {
                  await Promise.all(iter)
                  'not rejected'
                } catch (e) {
                  (e === poison) + ':' + returnCount
                }""";
        assertEquals("true:1", eval("var pending = new Promise(function () {})\n" + source));
    }

    @Test
    void testCombinatorsRejectAndCloseWhenTheElementThenGetterThrows() {
        String source = ENDLESS_ITER.replace("value: null", "value: pending") + """
                var poison = new Error('poison')
                Object.defineProperty(pending, 'then', { get: function () { throw poison } })
                try {
                  await Promise.all(iter)
                  'not rejected'
                } catch (e) {
                  (e === poison) + ':' + returnCount
                }""";
        assertEquals("true:1", eval("var pending = new Promise(function () {})\n" + source));
    }

    @Test
    void testATamperedElementThenIsActuallyInvoked() {
        assertEquals("via-then", eval("""
                var p = new Promise(function () {})
                Object.defineProperty(p, 'then', { value: function (res) { res('via-then') } })
                var vs = await Promise.all([p])
                vs[0]"""));
    }

    @Test
    void testCombinatorOnANonConstructorIsASyncTypeError() {
        // NewPromiseCapability(C) precedes everything IfAbruptRejectPromise covers,
        // so this throws rather than handing back a rejected promise
        assertEquals("TypeError", eval("try { Promise.all.call(Promise.resolve, []) } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { Promise.race.call(42, []) } catch (e) { e.name }"));
    }

    @Test
    void testTheResolveStepUsesTheReceiversOwnResolve() {
        assertEquals("2,4", eval("""
                class C { constructor(executor) { executor(function () {}, function () {}) } }
                C.resolve = function (v) { return Promise.resolve(v * 2) }
                var vs = await Promise.all.call(C, [1, 2])
                vs.join(',')"""));
    }

    @Test
    void testCombinatorsInvokePromiseResolveOncePerElement() {
        assertEquals("2,4,6|1,2,3", eval("""
                var seen = []
                var original = Promise.resolve
                Promise.resolve = function (v) { seen.push(v); return original(v * 2) }
                var vs = await Promise.all([1, 2, 3])
                vs.join(',') + '|' + seen.join(',')"""));
    }

    @Test
    void testCombinatorsRejectWhenACustomResolveReturnsSomethingUnthenable() {
        // spec: Invoke(nextPromise, "then", …) on whatever C.resolve handed back —
        // a primitive has no callable `then`, so the combinator rejects
        for (String name : new String[]{"all", "allSettled", "race", "any"}) {
            assertEquals("TypeError", eval("""
                    function C() {}
                    C.resolve = function () { return 1 }
                    try { await Promise.NAME.call(C, [42]) } catch (e) { e.name }""".replace("NAME", name)), name);
            assertEquals("TypeError", eval("""
                    function C() {}
                    C.resolve = function () { return { then: 'not callable' } }
                    try { await Promise.NAME.call(C, [42]) } catch (e) { e.name }""".replace("NAME", name)), name);
        }
        // and a custom resolve that does return a thenable is honored
        assertEquals("2,4", eval("""
                function C() {}
                C.resolve = function (v) { return { then: function (r) { r(v * 2) } } }
                var vs = await Promise.all.call(C, [1, 2])
                vs.join(',')"""));
        // the intrinsic fast path is untouched
        assertEquals("1,2", eval("var vs = await Promise.all([1, Promise.resolve(2)])\nvs.join(',')"));
    }

    // ===== thenables =====

    @Test
    void testThenableAdoption() {
        assertEquals(99, eval("await ({ then: function (resolve) { resolve(99) } })"));
        assertEquals(7, eval("await Promise.resolve({ then: function (resolve) { resolve(7) } })"));
        // first call wins; repeated calls are ignored
        assertEquals(1, eval("await ({ then: function (resolve) { resolve(1); resolve(2) } })"));
        assertEquals("no", eval("""
                try { await ({ then: function (res, rej) { rej(new Error('no')) } }) }
                catch (e) { e.message }"""));
    }

    @Test
    void testPoisonedThenableRejects() {
        assertEquals("caught:boom", eval("""
                try { await ({ get then() { throw new Error('boom') } }) }
                catch (e) { 'caught:' + e.message }"""));
        // a `then` that throws after already resolving is swallowed per spec
        assertEquals(5, eval("await ({ then: function (resolve) { resolve(5); throw new Error('late') } })"));
    }

    @Test
    void testThenableIsInvokedFromAJobNotSynchronously() {
        // spec: resolving with a thenable enqueues a job that calls `then` — it
        // never runs reentrantly inside the resolve function itself
        assertEquals(false, eval("""
                var sync = true
                var observed
                Promise.resolve({ then: function (resolve) { observed = sync; resolve(1) } })
                sync = false
                await new Promise(function (r) { setTimeout(r, 10) })
                observed"""));
        // same for `await` on a bare thenable, and for a thenable returned from
        // an async function
        assertEquals("sync,then:7", eval("""
                var log = []
                var t = { then: function (resolve) { log.push('then'); resolve(7) } }
                var p = Promise.resolve(t)
                log.push('sync')
                var v = await p
                log.join(',') + ':' + v"""));
        assertEquals(false, eval("""
                var sync = true
                var observed
                async function f() { return { then: function (resolve) { observed = sync; resolve(1) } } }
                f()
                sync = false
                await new Promise(function (r) { setTimeout(r, 10) })
                observed"""));
    }

    @Test
    void testSelfResolutionIsATypeError() {
        assertEquals("TypeError", eval("""
                var resolveFn
                var p = new Promise(function (r) { resolveFn = r })
                resolveFn(p)
                try { await p } catch (e) { e.name }"""));
    }

    // ===== finally =====

    @Test
    void testFinallyAwaitsAPromiseReturnedByTheCallback() {
        // spec: the callback's result goes through C.resolve(...).then(...), so a
        // pending promise returned by onFinally gates the rest of the chain
        assertEquals("gate-open,then:1", eval("""
                var release
                var gate = new Promise(function (r) { release = r })
                var log = []
                Promise.resolve(1).finally(function () { return gate }).then(function (v) { log.push('then:' + v) })
                await new Promise(function (r) { setTimeout(r, 30) })
                log.push('gate-open')
                release(9)
                await new Promise(function (r) { setTimeout(r, 30) })
                log.join(',')"""));
    }

    @Test
    void testFinallyPropagatesARejectionFromItsCallbackResult() {
        assertEquals("boom", eval("""
                await Promise.resolve(1)
                  .finally(function () { return Promise.reject(new Error('boom')) })
                  .catch(function (e) { return e.message })"""));
        // a fulfilled result is discarded — the original settlement still wins
        assertEquals(1, eval("await Promise.resolve(1).finally(function () { return Promise.resolve(99) })"));
        assertEquals("x", eval("""
                await Promise.reject(new Error('x'))
                  .finally(function () { return Promise.resolve(99) })
                  .catch(function (e) { return e.message })"""));
        // a thenable works the same way
        assertEquals(1, eval("await Promise.resolve(1).finally(function () { return { then: function (r) { r(2) } } })"));
    }

    // ===== timers =====

    @Test
    void testAwaitOnSetTimeoutReallyDelays() {
        Object elapsed = eval("var t = Date.now()\nawait new Promise(function (r) { setTimeout(r, 50) })\nDate.now() - t");
        assertTrue(((Number) elapsed).longValue() >= 40, "elapsed was " + elapsed);
    }

    @Test
    void testClearTimeoutPreventsTheJob() {
        assertEquals(false, eval("""
                var hit = false
                var id = setTimeout(function () { hit = true }, 10)
                clearTimeout(id)
                await new Promise(function (r) { setTimeout(r, 60) })
                hit"""));
    }

    @Test
    void testFireAndForgetTimerRunsBeforeEvalReturns() {
        // no await anywhere — the end-of-eval drain is what runs the callback
        eval("var hit = false\nsetTimeout(function () { hit = true }, 5)");
        assertEquals(true, get("hit"));
        eval("var log = []\nsetTimeout(function () { log.push('t') }, 5)");
        assertEquals(List.of("t"), get("log"));
    }

    @Test
    void testTimerExtraArgsAndDelayCoercion() {
        assertEquals("a-b", eval("""
                var out = ''
                setTimeout(function (x, y) { out = x + '-' + y }, 0, 'a', 'b')
                await new Promise(function (r) { setTimeout(r, 20) })
                out"""));
        // NaN / negative / non-numeric delays all floor to 0
        assertEquals(true, eval("""
                var hit = false
                setTimeout(function () { hit = true }, -5)
                setTimeout(function () { hit = hit && true }, 'abc')
                await new Promise(function (r) { setTimeout(r, 20) })
                hit"""));
        assertEquals("TypeError", eval("try { setTimeout(1, 0) } catch (e) { e.name }"));
    }

    // ===== Java interop =====

    @Test
    void testAwaitOnJavaCompletableFutureCompletedOnAnotherThread() {
        engine = new Engine();
        engine.put("later", (JsCallable) (ctx, args) -> {
            CompletableFuture<Object> cf = new CompletableFuture<>();
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cf.complete("from-java");
            }, "cf-completer");
            t.setDaemon(true);
            t.start();
            return cf;
        });
        Object elapsedAndValue = engine.eval("var t = Date.now()\nvar v = await later()\nv + ':' + ((Date.now() - t) >= 40)");
        assertEquals("from-java:true", elapsedAndValue);
    }

    @Test
    void testJavaFutureFailureBecomesARejection() {
        engine = new Engine();
        engine.put("failing", (JsCallable) (ctx, args) -> {
            CompletableFuture<Object> cf = new CompletableFuture<>();
            cf.completeExceptionally(new IllegalStateException("java blew up"));
            return cf;
        });
        assertEquals("java blew up", engine.eval("try { await failing() } catch (e) { e.message }"));
    }

    @Test
    void testHostCallOfAsyncFunctionReceivesThePromise() {
        engine = new Engine();
        engine.eval("async function f(a) { return a * 2 }");
        JavaCallable f = (JavaCallable) engine.get("f");
        Object result = f.call(null, new Object[]{21});
        assertInstanceOf(JsPromise.class, result, "expected a JsPromise, got " + result);
        JsPromise promise = (JsPromise) result;
        assertEquals(42, promise.await());
        // toFuture() is a stable view of the same promise
        assertSame(promise.toFuture(), promise.toFuture());
        assertEquals(42, promise.toFuture().join());
    }

    @Test
    void testHostCallOfRejectingAsyncFunction() {
        engine = new Engine();
        engine.eval("async function f() { throw new Error('host-visible') }");
        JavaCallable f = (JavaCallable) engine.get("f");
        JsPromise promise = (JsPromise) f.call(null, new Object[0]);
        JsRejectionException e = assertThrows(JsRejectionException.class, promise::await);
        assertInstanceOf(ObjectLike.class, e.getReason());
        assertEquals("host-visible", ((ObjectLike) e.getReason()).getMember("message"));
    }

    @Test
    void testPromiseRoundTripsThroughJavaAndBack() {
        engine = new Engine();
        engine.eval("async function f() { return 1 }");
        JavaCallable f = (JavaCallable) engine.get("f");
        JsPromise promise = (JsPromise) f.call(null, new Object[0]);
        engine.put("stage", promise.toFuture());
        // handing a promise's own future back in recovers the original promise
        assertEquals(true, engine.eval("stage === undefined ? false : true"));
        assertEquals(1, engine.eval("await stage"));
    }

    // ===== unhandled rejections =====

    @Test
    void testUnhandledRejectionFailsTheEval() {
        EngineException e = assertThrows(EngineException.class,
                () -> new Engine().eval("Promise.reject(new Error('boom'))\n1"));
        assertTrue(e.getMessage().contains("unhandled promise rejection"), e.getMessage());
        assertEquals("boom", e.getJsMessage());
    }

    @Test
    void testHandledRejectionDoesNotFailTheEval() {
        assertEquals(1, eval("Promise.reject(new Error('boom')).catch(function () {})\n1"));
        // a `.catch` queued later still counts — handledness is decided at quiescence
        assertEquals(1, eval("""
                var p = Promise.reject(new Error('boom'))
                setTimeout(function () { p.catch(function () {}) }, 5)
                1"""));
    }

    @Test
    void testFulfillmentOnlyThenTransfersResponsibilityToTheDerivedPromise() {
        // the source is not the terminal promise; the derived one is, and it is
        // reported exactly once
        EngineException e = assertThrows(EngineException.class,
                () -> new Engine().eval("Promise.reject(new Error('x')).then(function (v) { return v })\n1"));
        assertEquals("x", e.getJsMessage());
    }

    @Test
    void testWarnOnlyModeDoesNotFailTheEval() {
        Engine engine = new Engine();
        List<String> warnings = new ArrayList<>();
        engine.setOnConsoleLog(warnings::add);
        engine.setAsyncRejectionWarnOnly(true);
        assertEquals(1, engine.eval("Promise.reject(new Error('boom'))\n1"));
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertTrue(warnings.get(0).contains("boom"), warnings.get(0));
    }

    @Test
    void testWarnOnlyModeAlsoCoversTheEvalResult() {
        Engine engine = new Engine();
        List<String> warnings = new ArrayList<>();
        engine.setOnConsoleLog(warnings::add);
        engine.setAsyncRejectionWarnOnly(true);
        // the reason comes back as the eval's result instead of unwinding
        assertEquals("boom", engine.eval("Promise.reject('boom')"));
        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertTrue(warnings.get(0).contains("uncaught (in promise)"), warnings.get(0));
    }

    @Test
    void testEvalResultRejectionIsObservedNotDoubleReported() {
        EngineException e = assertThrows(EngineException.class,
                () -> new Engine().eval("Promise.reject(new Error('result'))"));
        assertTrue(e.getMessage().contains("uncaught (in promise)"), e.getMessage());
        assertEquals("result", e.getJsMessage());
    }

    @Test
    void testThrowingTimerCallbackFailsTheEval() {
        EngineException e = assertThrows(EngineException.class,
                () -> new Engine().eval("setTimeout(function () { throw new Error('timer boom') }, 1)\n1"));
        assertEquals("timer boom", e.getJsMessage());
    }

    // ===== the rev-1 blocker =====

    @Test
    void testUnresolvedAwaitDoesNotBlockTheCaller() {
        // the shape that deadlocked the eager design: the call must return its
        // promise before the gate is released
        assertEquals(1, eval("""
                let release
                const gate = new Promise(function (r) { release = r })
                async function f() { await gate; return 1 }
                const p = f()
                release()
                await p"""));
    }

    @Test
    void testConcurrentActivationsOverlap() {
        Object result = eval("""
                async function a() { await new Promise(function (r) { setTimeout(r, 60) }); return 'a' }
                async function b() { await new Promise(function (r) { setTimeout(r, 60) }); return 'b' }
                var t = Date.now()
                var vs = await Promise.all([a(), b()])
                vs.join(',') + ':' + ((Date.now() - t) < 120)""");
        assertEquals("a,b:true", result);
    }

    // ===== identity =====

    @Test
    void testCompletionStageIdentityIsScopeScoped() {
        engine = new Engine();
        CompletableFuture<Object> stage = CompletableFuture.completedFuture("v");
        engine.put("stage", stage);
        assertEquals(true, engine.eval("Promise.resolve(stage) === Promise.resolve(stage)"));
        // the cache is dropped wholesale at scope close, so a later eval wraps
        // afresh — identity across scopes is explicitly not promised
        engine.eval("globalThis.first = Promise.resolve(stage)");
        assertInstanceOf(JsPromise.class, engine.getRawBindings().get("first"));
        assertEquals(false, engine.eval("first === Promise.resolve(stage)"));
    }

}

package io.karatelabs.js;

import io.karatelabs.common.KarateLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The async concurrency protocol, exercised at its barriers rather than through
 * its happy path: token unwind ownership, the close-vs-publish race, the
 * unpark-before-park race, cancellation of a running activation, the stale
 * reacquisition gate, nested eval sharing one scope, scope-cache reclamation,
 * the poison transition, and the atomic wake for activation-initiated
 * cancellation.
 */
@Timeout(60)
class JsAsyncConcurrencyTest {

    private static Thread runAsync(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static AsyncJob noopJob() {
        return new AsyncJob.Microtask("test", () -> {
        });
    }

    // ===== tokens and the publish facade =====

    @Test
    void testTokenReleaseIsIdempotent() {
        AsyncScope scope = new AsyncScope(new Engine());
        AsyncToken token = scope.acquire("test");
        assertNotNull(token);
        assertEquals(1, scope.liveTokenCount());
        assertTrue(token.release(), "first release must win the CAS");
        assertFalse(token.release(), "double release must be a no-op");
        assertFalse(token.release());
        assertEquals(0, scope.liveTokenCount());
        assertTrue(scope.isQuiescent());
    }

    @Test
    void testPublishIntoClosedScopeDropsTheWorkAndReleasesOnlyTheOldToken() {
        AsyncScope scope = new AsyncScope(new Engine());
        AsyncToken old = scope.acquire("old");
        assertEquals(1, scope.liveTokenCount());
        scope.close("test", 100);
        assertFalse(scope.publishSuccessor(old, AsyncJob.one(noopJob())),
                "a closed scope must reject the publication");
        assertEquals(0, scope.liveTokenCount(), "the count must never go negative or leak");
        assertFalse(old.isLive());
        // and no new work can be acquired at all
        assertNull(scope.acquire("after-close"));
    }

    @Test
    void testSpawnIntoAClosedScopeIsRefused() {
        AsyncScope scope = new AsyncScope(new Engine());
        scope.close("test", 100);
        // the token acquisition is the gate every activation start goes through
        assertNull(scope.acquire("activation"));
    }

    @Test
    void testCloseVersusPublishRaceNeverLeaksOrGoesNegative() throws Exception {
        for (int round = 0; round < 25; round++) {
            AsyncScope scope = new AsyncScope(new Engine());
            int publishers = 8;
            CountDownLatch ready = new CountDownLatch(publishers);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(publishers);
            for (int i = 0; i < publishers; i++) {
                runAsync("publisher-" + i, () -> {
                    ready.countDown();
                    try {
                        go.await();
                        for (int n = 0; n < 40; n++) {
                            AsyncToken token = scope.acquire("work");
                            if (token == null) {
                                break;
                            }
                            scope.publishSuccessor(token, AsyncJob.one(noopJob()));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            Thread.sleep(1);
            scope.close("racing close", 500);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            // whatever was published before the close was drained by it; whatever
            // came after was dropped with only its own token released
            assertTrue(scope.liveTokenCount() >= 0, "live token count went negative");
            assertEquals(0, drainRemaining(scope), "work survived a closed scope");
        }
    }

    private static int drainRemaining(AsyncScope scope) {
        int count = 0;
        AsyncJob job;
        while ((job = scope.takeJob(1)) != null) {
            job.token.release();
            count++;
        }
        return count;
    }

    // ===== activation-initiated cancellation =====

    @Test
    void testCancelRequestWakesAPumpOwnerBlockedOnAnEmptyQueue() throws Exception {
        AsyncScope scope = new AsyncScope(new Engine());
        // outstanding work with nothing queued: exactly the state in which the
        // pump owner blocks
        AsyncToken keepAlive = scope.acquire("activation");
        assertNotNull(keepAlive);
        AtomicReference<AsyncJob> taken = new AtomicReference<>();
        AtomicReference<Long> wokeNanos = new AtomicReference<>();
        Thread pump = runAsync("pump-owner", () -> {
            taken.set(scope.takeJob(30_000));
            wokeNanos.set(System.nanoTime());
        });
        Thread.sleep(150);
        assertTrue(pump.isAlive(), "the pump owner should still be blocked");
        long before = System.nanoTime();
        scope.requestCancel("fatal internal error");
        pump.join(5000);
        assertFalse(pump.isAlive(), "the control job did not wake the pump owner");
        assertInstanceOf(AsyncJob.Control.class, taken.get());
        assertTrue(wokeNanos.get() - before < TimeUnit.SECONDS.toNanos(2), "wake took too long");
        assertTrue(scope.isCancelRequested());
    }

    @Test
    void testCancelRequestBeforeTheOwnerWaitsIsSeenImmediately() {
        AsyncScope scope = new AsyncScope(new Engine());
        AsyncToken keepAlive = scope.acquire("activation");
        assertNotNull(keepAlive);
        scope.requestCancel("fatal internal error");
        // race-safe in the other order too: the request is already queued
        assertInstanceOf(AsyncJob.Control.class, scope.takeJob(5000));
        assertTrue(scope.isCancelRequested());
    }

    // ===== suspension / resumption races =====

    @Test
    void testUnparkBeforeParkIsSurvived() {
        // a zero-delay timer routinely completes the future before the
        // activation reaches its park — the unpark permit is what makes that safe
        Engine engine = new Engine();
        Object result = engine.eval("""
                async function once() { await new Promise(function (r) { setTimeout(r, 0) }); return 1 }
                var total = 0
                for (var i = 0; i < 200; i++) { total = total + await once() }
                total""");
        assertEquals(200, result);
    }

    @Test
    void testInterruptBeforeTheActivationStarts() throws Exception {
        // interrupt on a schedule that lands all over the startup handshake
        for (int i = 0; i < 40; i++) {
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Engine engine = new Engine();
            Thread worker = runAsync("start-race-" + i, () -> {
                try {
                    engine.eval("""
                            async function f() { await new Promise(function (r) { setTimeout(r, 300) }); return 1 }
                            await f()""");
                } catch (Throwable t) {
                    thrown.set(t);
                }
            });
            Thread.sleep(i % 5);
            worker.interrupt();
            worker.join(10_000);
            assertFalse(worker.isAlive(), "worker did not terminate on interrupt");
            Throwable t = thrown.get();
            if (t != null) {
                assertInstanceOf(EngineException.class, t);
            }
            assertFalse(engine.isPoisoned(), "a clean cancellation must not poison the engine");
        }
    }

    // ===== cancellation of a running activation =====

    @Test
    void testInterruptStopsARunningActivation() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Engine engine = new Engine();
        Thread worker = runAsync("busy-activation", () -> {
            try {
                engine.eval("async function f() { while (true) {} }\nawait f()");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Thread.sleep(200);
        worker.interrupt();
        worker.join(10_000);
        assertFalse(worker.isAlive(), "the eval did not unwind");
        assertInstanceOf(EngineInterruptedException.class, thrown.get());
        assertFalse(engine.isPoisoned(), "an activation that polls must not need poisoning");
    }

    @Test
    void testStaleActivationExitsWithoutExecutingAnyJs() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Engine engine = new Engine();
        Thread worker = runAsync("stale-activation", () -> {
            try {
                engine.eval("""
                        var marker = 'unset'
                        async function f() { await new Promise(function () {}); marker = 'ran'; return 1 }
                        f()
                        await new Promise(function (r) { setTimeout(r, 30000) })""");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Thread.sleep(300);
        worker.interrupt();
        worker.join(10_000);
        assertFalse(worker.isAlive());
        assertInstanceOf(EngineInterruptedException.class, thrown.get());
        // the activation was parked past the await; the closed-scope gate must
        // stop it before the assignment on the resumption path
        assertEquals("unset", engine.getBindings().get("marker"));
        assertFalse(engine.isPoisoned());
    }

    // ===== nested eval =====

    @Test
    void testNestedEvalSharesTheOuterScope() {
        Engine engine = new Engine();
        AtomicReference<Object> nestedScope = new AtomicReference<>();
        engine.put("probeScope", (JsCallable) (ctx, args) -> engine.currentScope());
        engine.put("nested", (JsCallable) (ctx, args) -> {
            nestedScope.set(engine.eval("probeScope()"));
            return engine.eval("1 + 1");
        });
        AtomicReference<Object> outerScope = new AtomicReference<>();
        engine.put("outerScope", (JsCallable) (ctx, args) -> {
            outerScope.set(engine.currentScope());
            return Terms.UNDEFINED;
        });
        Object result = engine.eval("outerScope()\nvar v = nested()\nvar w = await Promise.resolve(3)\nv + w");
        assertEquals(5, result);
        assertInstanceOf(AsyncScope.class, nestedScope.get());
        // a nested eval does not open a scope of its own — same object identity,
        // which is what makes scope identity a usable generation stamp
        assertSame(outerScope.get(), nestedScope.get());
        // and each outermost eval does get a fresh one
        assertFalse(engine.eval("probeScope()") == outerScope.get());
    }

    @Test
    void testNestedEvalRunsInsideAPromiseCallback() {
        Engine engine = new Engine();
        AtomicReference<Object> scopeInJob = new AtomicReference<>();
        AtomicReference<Object> scopeOutside = new AtomicReference<>();
        engine.put("fromJob", (JsCallable) (ctx, args) -> {
            scopeInJob.set(engine.currentScope());
            return engine.eval("21 * 2");
        });
        engine.put("outside", (JsCallable) (ctx, args) -> {
            scopeOutside.set(engine.currentScope());
            return Terms.UNDEFINED;
        });
        Object result = engine.eval("outside()\nawait Promise.resolve(1).then(function () { return fromJob() })");
        assertEquals(42, result);
        assertSame(scopeOutside.get(), scopeInJob.get(), "a job shares the eval's scope");
    }

    @Test
    void testOuterEvalsAreSerializedAcrossThreads() throws Exception {
        Engine engine = new Engine();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        engine.put("enter", (JsCallable) (ctx, args) -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            return Terms.UNDEFINED;
        });
        engine.put("leave", (JsCallable) (ctx, args) -> {
            concurrent.decrementAndGet();
            return Terms.UNDEFINED;
        });
        CountDownLatch done = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            runAsync("outer-eval-" + i, () -> {
                try {
                    engine.eval("enter()\nawait new Promise(function (r) { setTimeout(r, 20) })\nleave()");
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertEquals(1, maxConcurrent.get(), "two outer evals ran against one engine at once");
    }

    // ===== scope-scoped caches =====

    @Test
    void testStageCacheIsScopeScopedAndReclaimed() {
        Engine engine = new Engine();
        engine.put("stage", CompletableFuture.completedFuture("v"));
        engine.put("cacheSize", (JsCallable) (ctx, args) -> engine.currentScope().stageCacheSize());
        assertEquals(0, engine.eval("cacheSize()"));
        assertEquals(1, engine.eval("Promise.resolve(stage)\ncacheSize()"));
        assertEquals(1, engine.eval("Promise.resolve(stage)\nPromise.resolve(stage)\ncacheSize()"));
        // the scope — and with it the whole cache — is gone once the eval returns
        assertNull(engine.currentScope());
        assertEquals(1, engine.eval("Promise.resolve(stage)\ncacheSize()"));
    }

    // ===== poisoning =====

    @Test
    void testStuckHostCallPoisonsTheEngineAndExitsAtTheHostReturnFence() throws Exception {
        Engine engine = new Engine();
        engine.asyncTeardownMillis = 150;
        AtomicBoolean hostReturned = new AtomicBoolean();
        engine.put("stuck", (JsCallable) (ctx, args) -> {
            // deliberately non-interruptible host code — the residual case
            // engine poisoning exists for
            long end = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    // swallowed, exactly as badly-behaved host code does
                }
            }
            hostReturned.set(true);
            return 1;
        });
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = runAsync("poison-worker", () -> {
            try {
                engine.eval("""
                        var afterHost = 'unset'
                        async function f() { stuck(); afterHost = 'ran'; return 1 }
                        f()
                        await new Promise(function (r) { setTimeout(r, 30000) })""");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Thread.sleep(300);
        worker.interrupt();
        worker.join(15_000);
        assertFalse(worker.isAlive());
        assertNotNull(thrown.get());
        assertTrue(engine.isPoisoned(), "a teardown that timed out must poison the engine");
        // subsequent evals fail fast rather than waiting on a lock the stuck
        // activation may still hold
        EngineException e = assertThrows(EngineException.class, () -> engine.eval("1 + 1"));
        assertTrue(e.getMessage().contains("poisoned"), e.getMessage());
        // and the stuck activation exits cleanly at the host-return fence
        long deadline = System.currentTimeMillis() + 10_000;
        while (!hostReturned.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(hostReturned.get(), "the host call never returned");
        Thread.sleep(300);
        assertEquals("unset", engine.getBindings().get("afterHost"),
                "a stale activation must execute no JS after its host call returns");
    }

    // ===== lifecycle shutdown vs. live timers =====

    @Test
    void testLifecycleShutdownRetiresLiveTimerTokensInsteadOfHangingTheEval() throws Exception {
        Engine engine = new Engine();
        CountDownLatch armed = new CountDownLatch(1);
        engine.put("armed", (JsCallable) (ctx, args) -> {
            armed.countDown();
            return Terms.UNDEFINED;
        });
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = runAsync("timer-shutdown", () -> {
            try {
                result.set(engine.eval("""
                        var fired = 'no'
                        setTimeout(function () { fired = 'yes' }, 30000)
                        armed()
                        'done'"""));
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        try {
            assertTrue(armed.await(10, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertTrue(worker.isAlive(), "the eval should still be draining its live timer");
            // shutdownNow() cancels the scheduled runnable; without the registry
            // retiring the record too, the timer token stays live forever and this
            // eval never reaches quiescence
            KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
            worker.join(10_000);
            assertFalse(worker.isAlive(), "the eval hung on a timer token the shutdown orphaned");
        } finally {
            KarateLifecycle.reset();
        }
        assertNull(thrown.get());
        assertEquals("done", result.get());
        assertEquals("no", engine.getBindings().get("fired"), "a retired timer must not fire");
        assertFalse(engine.isPoisoned());
    }

    @Test
    void testSetTimeoutRacingLifecycleShutdownNeverStrandsAnEval() throws Exception {
        // Arming timers while the shared scheduler is being stopped, hammered from
        // several threads so the arm lands on every side of the stop handshake:
        // before the closed flag is published, between that and the executor
        // shutdown, and after the registry sweep. Whichever side wins, the record
        // and its token must be unwound and the eval must terminate.
        int workers = 4;
        for (int round = 0; round < 20; round++) {
            List<Engine> engines = new ArrayList<>();
            List<AtomicReference<Throwable>> failures = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(workers);
            for (int w = 0; w < workers; w++) {
                Engine engine = new Engine();
                AtomicReference<Throwable> thrown = new AtomicReference<>();
                engines.add(engine);
                failures.add(thrown);
                runAsync("timer-race-" + round + "-" + w, () -> {
                    try {
                        engine.eval("for (var i = 0; i < 200; i++) { setTimeout(function () {}, 30000) }\n1");
                    } catch (Throwable t) {
                        thrown.set(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            try {
                Thread.sleep(round % 3);
                KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
                assertTrue(done.await(15, TimeUnit.SECONDS),
                        "an eval was stranded by an orphaned timer token, round " + round);
            } finally {
                KarateLifecycle.reset();
            }
            for (int w = 0; w < workers; w++) {
                Throwable t = failures.get(w).get();
                if (t != null) {
                    assertInstanceOf(EngineInterruptedException.class, t, "round " + round + ": " + t);
                }
                assertFalse(engines.get(w).isPoisoned(), "round " + round);
                // the scope is gone, so every token it handed out was accounted for
                assertNull(engines.get(w).currentScope(), "round " + round);
            }
        }
    }

    @Test
    void testSetTimeoutIsRefusedOnceTheLifecycleIsStopped() {
        Engine engine = new Engine();
        try {
            KarateLifecycle.shutdownAll(Duration.ofSeconds(5));
            EngineInterruptedException e = assertThrows(EngineInterruptedException.class,
                    () -> engine.eval("setTimeout(function () {}, 30000)\n1"));
            assertTrue(e.getMessage().contains("shutting down"), e.getMessage());
            // refused before any token was acquired, and JS cannot swallow it
            assertThrows(EngineInterruptedException.class,
                    () -> engine.eval("try { setTimeout(function () {}, 30000) } catch (e) { 'caught' }\n1"));
        } finally {
            KarateLifecycle.reset();
        }
        assertNull(engine.currentScope());
        assertFalse(engine.isPoisoned());
        // and the next eval gets a freshly created scheduler
        assertEquals(1, engine.eval("await new Promise(function (r) { setTimeout(function () { r(1) }, 5) })"));
    }

    // ===== teardown under an interrupt storm =====

    @Test
    void testInterruptStormDuringTeardownDoesNotFalselyPoison() throws Exception {
        // The first activation is cooperative but not instantaneous — it is inside
        // a host call that ignores its interrupt and returns a few hundred ms
        // later, at which point the host-return fence stops it. An interrupt that
        // lands while teardown is waiting for it must be absorbed, not allowed to
        // abandon that wait and every wait behind it.
        for (int round = 0; round < 3; round++) {
            Engine engine = new Engine();
            engine.put("slow", (JsCallable) (ctx, args) -> {
                long end = System.currentTimeMillis() + 400;
                while (System.currentTimeMillis() < end) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // swallowed, exactly as badly-behaved host code does
                    }
                }
                return 1;
            });
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread worker = runAsync("teardown-storm-" + round, () -> runCatching(engine, """
                    var afterHost = 'unset'
                    async function busy() { await new Promise(function (r) { setTimeout(r, 250) }); slow(); afterHost = 'ran'; return 1 }
                    async function idle(n) { await new Promise(function () {}); return n }
                    busy()
                    for (var i = 0; i < 6; i++) { idle(i) }
                    await new Promise(function (r) { setTimeout(r, 30000) })""", thrown));
            Thread.sleep(300);
            for (int i = 0; i < 100; i++) {
                worker.interrupt();
                Thread.sleep(3);
            }
            worker.join(15_000);
            assertFalse(worker.isAlive(), "the eval did not unwind, round " + round);
            assertInstanceOf(EngineInterruptedException.class, thrown.get());
            assertFalse(engine.isPoisoned(),
                    "cooperative activations were misread as stuck, round " + round);
            assertEquals("unset", engine.getBindings().get("afterHost"));
        }
    }

    // ===== a smoke pass over the whole protocol =====

    @Test
    void testManyOverlappingActivationsAllSettle() {
        Engine engine = new Engine();
        Object result = engine.eval("""
                async function work(n) {
                  await new Promise(function (r) { setTimeout(r, n % 7) })
                  return n * 2
                }
                var ps = []
                for (var i = 0; i < 40; i++) { ps.push(work(i)) }
                var vs = await Promise.all(ps)
                vs.reduce(function (a, b) { return a + b }, 0)""");
        assertEquals(1560, result);
        assertFalse(engine.isPoisoned());
    }

    @Test
    void testDrainCapCancelsTheScope() {
        Engine engine = new Engine();
        engine.setAsyncDrainTimeout(java.time.Duration.ofMillis(200));
        EngineTimeoutException e = assertThrows(EngineTimeoutException.class,
                () -> engine.eval("setTimeout(function () {}, 30000)\n1"));
        assertTrue(e.getMessage().contains("timed out"), e.getMessage());
        // JS cannot catch it — it routes exactly like host cancellation
        assertInstanceOf(EngineInterruptedException.class, e);
        Engine other = new Engine();
        other.setAsyncDrainTimeout(java.time.Duration.ofMillis(200));
        assertThrows(EngineTimeoutException.class,
                () -> other.eval("try { setTimeout(function () {}, 30000) } catch (e) { 'caught' }\n1"));
    }

    @Test
    void testEngineIsReusableAfterACancelledEval() throws Exception {
        Engine engine = new Engine();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = runAsync("reuse-worker", () ->
                runCatching(engine, "async function f() { while (true) {} }\nawait f()", thrown));
        Thread.sleep(200);
        worker.interrupt();
        worker.join(10_000);
        assertInstanceOf(EngineInterruptedException.class, thrown.get());
        // teardown completed before ownership was released, so the next eval is clean
        assertEquals(3, engine.eval("await Promise.resolve(3)"));
        assertEquals(List.of(), engine.currentScope() == null ? List.of() : List.of("scope leaked"));
    }

    private static void runCatching(Engine engine, String script, AtomicReference<Throwable> thrown) {
        try {
            engine.eval(script);
        } catch (Throwable t) {
            thrown.set(t);
        }
    }

}

/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterruptTest {

    private static void runAndInterrupt(String script) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            entered.countDown();
            try {
                new Engine().eval(script);
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "interrupt-test-worker");
        worker.setDaemon(true);
        worker.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Thread.sleep(50);
        worker.interrupt();
        worker.join(2000);
        assertTrue(!worker.isAlive(), "worker did not stop after interrupt");
        Throwable t = thrown.get();
        assertNotNull(t, "expected EngineInterruptedException, got nothing");
        assertInstanceOf(EngineInterruptedException.class, t);
        assertInstanceOf(EngineException.class, t);
    }

    @Test
    @Timeout(5)
    void interruptStopsInfiniteWhileLoop() throws Exception {
        runAndInterrupt("while (true) {}");
    }

    @Test
    @Timeout(5)
    void interruptStopsForOfLoop() throws Exception {
        String script = "var a = []; for (var i = 0; i < 100000; i++) a.push(i); "
                + "while (true) { for (var x of a) {} }";
        runAndInterrupt(script);
    }

    @Test
    @Timeout(5)
    void interruptStopsNestedLoops() throws Exception {
        runAndInterrupt("while (true) { while (true) {} }");
    }

    @Test
    @Timeout(5)
    void interruptBypassesJsTryCatch() throws Exception {
        runAndInterrupt("try { while (true) {} } catch (e) { /* swallow */ }");
    }

    @Test
    void uninterruptedLoopCompletesNormally() {
        Engine engine = new Engine();
        Object result = engine.eval("var s = 0; for (var i = 0; i < 10000; i++) s += i; s");
        assertEquals(49995000, ((Number) result).intValue());
    }

}

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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CdpClientFenceTest {

    private final CdpClient client = CdpClient.detached(Duration.ofSeconds(5));

    @AfterEach
    void close() {
        client.close();
    }

    private static String event(String method, int n) {
        return "{\"method\":\"" + method + "\",\"params\":{\"n\":" + n + "}}";
    }

    @Test
    void framesRoutedBeforeTheFenceReachListenersBeforeItCompletes() throws Exception {
        List<Integer> seen = new ArrayList<>();
        CountDownLatch slow = new CountDownLatch(1);
        client.addExternalListener((method, params) -> {
            if (((Number) params.get("n")).intValue() == 1) {
                try {
                    slow.await(5, TimeUnit.SECONDS); // the first event holds the dispatcher
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (seen) {
                seen.add(((Number) params.get("n")).intValue());
            }
        });
        for (int i = 1; i <= 50; i++) {
            client.route(event("Network.requestWillBeSent", i));
        }
        List<Integer> atFence = new ArrayList<>();
        Thread release = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
                // the release is best-effort
            }
            slow.countDown();
        });
        release.start();
        client.awaitEventsDispatched(() -> {
            synchronized (seen) {
                atFence.addAll(seen);
            }
        });
        assertEquals(0, slow.getCount(), "the fence waited for the held event, not around it");
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            expected.add(i);
        }
        assertEquals(expected, atFence, "every frame routed before the fence reached the listener before the fence task ran");
    }

    @Test
    void fenceRunsItsTaskOnTheDispatcherBetweenEvents() throws Exception {
        AtomicReference<String> fenceThread = new AtomicReference<>();
        AtomicReference<String> listenerThread = new AtomicReference<>();
        client.addExternalListener((method, params) -> listenerThread.set(Thread.currentThread().getName()));
        client.route(event("Target.targetCreated", 1));
        client.awaitEventsDispatched(() -> fenceThread.set(Thread.currentThread().getName()));
        assertNotNull(listenerThread.get());
        assertEquals(listenerThread.get(), fenceThread.get(), "the fence task runs on the single event dispatcher");
    }

    @Test
    void fenceFromEitherLaneThreadThrows() throws Exception {
        AtomicReference<Throwable> fromDispatcher = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        client.addExternalListener((method, params) -> {
            try {
                client.awaitEventsDispatched();
            } catch (Throwable t) {
                fromDispatcher.set(t);
            }
            done.countDown();
        });
        client.route(event("Network.loadingFinished", 1));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, fromDispatcher.get(), "joining the fence on the dispatcher is refused");

        AtomicReference<Throwable> fromRouter = new AtomicReference<>();
        CountDownLatch routed = new CountDownLatch(1);
        client.frameRouter().execute(() -> {
            try {
                client.awaitEventsDispatched();
            } catch (Throwable t) {
                fromRouter.set(t);
            }
            routed.countDown();
        });
        assertTrue(routed.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, fromRouter.get(), "joining the fence on the router is refused");
    }
}

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
package io.karatelabs.gatling;

import io.karatelabs.http.HttpClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * The pooled-connection wiring: that the pool reaches the protocol, and that Gatling's
 * termination hook actually runs what it is handed.
 *
 * <p><b>The second one is here because getting it wrong is silent.</b>
 * {@code ActorSystem.registerOnTermination} takes a <i>by-name</i> parameter, so
 * {@code registerOnTermination(() -> pool.close())} compiles, type-checks, and does nothing at
 * all — termination evaluates the expression to a function object and never calls it. The pool is
 * simply never closed, every connection it holds is leaked to the collector, and nothing anywhere
 * says so. That is exactly the class of defect a soak is meant to find, so it should not be
 * introduced by the code the soak runs.
 *
 * <p>What is NOT covered here: that {@code KarateProtocolKey} does the registering. That needs a
 * real {@code CoreComponents}, which needs a controller, a stats engine and an event loop group —
 * so it is verified by running a simulation and watching the pool close, rather than by a unit
 * test that would cost seconds of build time to assemble the world. The two tests below pin the
 * halves either side of it.
 */
class PooledConnectionsTest {

    @Test
    void testPooledConnectionsHandsTheFactoryToTheProtocolForClosing() {
        KarateProtocolBuilder builder = new KarateProtocolBuilder();
        assertNull(builder.build().getCloseAtSimulationEnd(),
                "a protocol that was not asked to pool has nothing to close");

        KarateProtocolBuilder pooled = new KarateProtocolBuilder().pooledConnections(8, 8);
        AutoCloseable closeable = pooled.build().getCloseAtSimulationEnd();
        assertNotNull(closeable,
                "the pool must reach the protocol, or nothing can close it at simulation end");

        // And it is the same object the scenarios are given, not a second pool that would leave
        // the real one open.
        HttpClient client = ((PooledHttpClientFactory) closeable).create();
        assertNotNull(client, "the factory handed to the protocol must be the working one");
    }

    /**
     * Pins the by-name contract this depends on. If a future Gatling changes
     * {@code registerOnTermination} to take a function value, this fails and the call site has to
     * be revisited — rather than the pool quietly stopping being closed.
     */
    @Test
    void testGatlingRunsWhatIsRegisteredOnTermination() {
        AtomicInteger ran = new AtomicInteger();
        io.gatling.core.actor.ActorSystem system = new io.gatling.core.actor.ActorSystem();
        try {
            KarateTerminationSupport.registerClose(system, ran::incrementAndGet);
            assertEquals(0, ran.get(), "nothing may run before termination");
        } finally {
            system.close();
        }
        assertEquals(1, ran.get(),
                "Gatling's termination hook must invoke what was registered — a by-name argument "
                        + "handed a lambda is never called, and the pool would never be closed");
    }

    /** Closing twice must be safe: an explicit close can race the one at simulation end. */
    @Test
    void testClosingThePoolTwiceIsSafe() {
        PooledHttpClientFactory factory = new PooledHttpClientFactory(4, 4);
        factory.close();
        factory.close();
    }

    /** Release returns a wrapper to the pool rather than shutting it, so the pool survives. */
    @Test
    void testReleasingAWrapperLeavesThePoolUsable() {
        PooledHttpClientFactory factory = new PooledHttpClientFactory(4, 4);
        try {
            HttpClient first = factory.create();
            factory.release(first);
            HttpClient second = factory.create();
            assertNotNull(second, "the pool must still hand out clients after one is released");
            org.junit.jupiter.api.Assertions.assertNotSame(first, second,
                    "each scenario gets its own wrapper — sharing one is unsafe for reasons "
                            + "unrelated to pooling, see HttpClientFactory.release");
        } finally {
            factory.close();
        }
    }
}

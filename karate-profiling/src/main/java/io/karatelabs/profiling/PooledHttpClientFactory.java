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
package io.karatelabs.profiling;

import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpClientFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * One connection pool for a whole run, one thin client wrapper per scenario.
 *
 * <p><b>This is an experiment, and it lives here rather than in karate-core on purpose.</b> The
 * question it exists to answer is whether Karate's connection-per-iteration shape is what stops
 * docs/PROFILING.md from saying karate-gatling is good enough for public TLS endpoints: measured,
 * a Karate arm opened <b>4000 distinct client ports</b> where plain Gatling opened 8. On loopback
 * and same-AZ that is free; against a real API it is +1 RTT plus TLS on every iteration. Pooling
 * should collapse it. Until that is measured, shipping this as API would be committing to an
 * answer nobody has.
 *
 * <p><b>Why one wrapper per scenario rather than one shared client.</b> {@link ApacheHttpClient}
 * closes its own transport in {@code apply()}, which fires on every {@code configure} of nine
 * common keys and after every shared-scope call — so a second scenario holding the same instance
 * has its in-flight request killed by the first scenario's {@code configure} step. It also keeps
 * per-request state ({@code request}, {@code startTime}, {@code currentRequest}) in instance
 * fields, so concurrent callers already attach one request's headers to another's response. The
 * pooling win is in the connection manager, not in the wrapper, so sharing only the manager gives
 * up nothing. See the contract on {@link HttpClientFactory#release}.
 *
 * <p><b>What it gives up.</b> The SSL socket factory and the socket/connect timeouts are
 * configured on the connection manager, so per-scenario {@code configure ssl} and
 * {@code configure connectTimeout} do not reach a pooled client. Fine for a parity workload
 * pointed at one mock; not fine as a default, which is the other reason this is not in
 * karate-core.
 */
public final class PooledHttpClientFactory implements HttpClientFactory {

    private final PoolingHttpClientConnectionManager manager;
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger released = new AtomicInteger();

    /**
     * @param maxTotal   total pooled connections. Size it above the virtual-user count or the
     *                   pool becomes the bottleneck and the A/B measures the pool rather than
     *                   Karate — the same "measured the machine, not the client" trap §10 keeps
     *                   running into.
     * @param perRoute   connections per target host. A parity run has exactly one route, so this
     *                   is the number that actually binds.
     */
    public PooledHttpClientFactory(int maxTotal, int perRoute) {
        manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotal)
                .setMaxConnPerRoute(perRoute)
                // Keep idle connections alive across the gaps a closed-loop parity run leaves
                // between iterations. Without this an eviction policy would reintroduce exactly
                // the reconnects this factory exists to remove — silently, and only at some tier
                // timings, which is the worst way for it to happen.
                .setDefaultConnectionConfig(org.apache.hc.client5.http.config.ConnectionConfig.custom()
                        .setTimeToLive(TimeValue.ofHours(1))
                        .build())
                .build();
    }

    @Override
    public HttpClient create() {
        created.incrementAndGet();
        return new PooledApacheHttpClient();
    }

    /**
     * Closes the scenario's wrapper, which returns its connections to the pool rather than
     * shutting it: {@code ApacheHttpClient} builds with {@code setConnectionManagerShared(true)}
     * whenever {@code sharedConnectionManager()} is non-null.
     */
    @Override
    public void release(HttpClient client) {
        released.incrementAndGet();
        try {
            client.close();
        } catch (Exception e) {
            // A wrapper that will not close is not worth failing a run over; the pool survives it.
            System.out.println("[pool] error closing a client wrapper: " + e);
        }
    }

    /** For the harness to print at the end of a run — the check that release actually balances. */
    public String describe() {
        return "created=" + created.get() + " released=" + released.get()
                + " leased=" + manager.getTotalStats().getLeased()
                + " available=" + manager.getTotalStats().getAvailable()
                + " pending=" + manager.getTotalStats().getPending();
    }

    /** Shuts the pool. After this the factory is unusable; call it once, at the end of a run. */
    public void shutdown() {
        manager.close();
    }

    private final class PooledApacheHttpClient extends ApacheHttpClient {
        @Override
        protected org.apache.hc.client5.http.io.HttpClientConnectionManager sharedConnectionManager() {
            return manager;
        }
    }
}

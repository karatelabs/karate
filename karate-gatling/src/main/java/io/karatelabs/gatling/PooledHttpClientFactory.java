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

import io.karatelabs.http.ApacheHttpClient;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpClientFactory;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;

import javax.net.ssl.SSLContext;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One connection pool for a whole simulation, one thin client wrapper per scenario.
 *
 * <p><b>What it is for.</b> Karate builds an HTTP client per scenario execution, so under Gatling
 * it opens a connection per iteration where plain Gatling keeps one per virtual user — measured on
 * a two-host bench, 4000 distinct client ports against 8. Sharing the connection manager collapses
 * that. On a plaintext endpoint on the same network it is worth about 24% of Karate's per-iteration
 * overhead; against a public TLS endpoint it should be worth more, because the handshake it avoids
 * costs two round trips and asymmetric crypto rather than a fraction of a millisecond.
 *
 * <p><b>Opt in, per protocol.</b> See {@link KarateProtocolBuilder#pooledConnections()}. This is
 * deliberately not karate-core's default — see the limitation below, which is fine for a load test
 * pointed at one endpoint and not fine for a functional suite whose scenarios configure themselves
 * independently.
 *
 * <h2>What it cannot honour</h2>
 *
 * <p><b>{@code configure ssl} set inside a scenario does not reach a pooled client.</b> The SSL
 * socket factory belongs to the connection manager, and the manager here is shared and already
 * built. Set it once for the simulation — karate-config.js, or the runner — instead.
 *
 * <p>It is <b>ignored silently</b>, which is worth stating plainly because this class cannot
 * currently do better. {@link HttpClientFactory#create()} is handed no configuration, and
 * {@code ApacheHttpClient.sharedConnectionManager()} takes no argument, so when it chooses a
 * manager it cannot see what the scenario asked for. Detecting the conflict and warning, or
 * keeping one manager per distinct connection configuration, both need that seam to carry the
 * connection settings.
 *
 * <p>The timeouts used to be on this list and are not any more: {@code configure readTimeout} and
 * {@code configure connectTimeout} are applied per request as well as on the manager, so they are
 * honoured here. That mattered more than it sounds — before it, a pooled client had <i>no</i> read
 * timeout, so a stalled server held a virtual user indefinitely. See {@code PooledTimeoutTest}.
 *
 * <p><b>NTLM is incompatible with pooling, and that one is a correctness bug rather than a
 * fidelity gap.</b> NTLM authenticates the <i>connection</i>, not the request, so a pooled
 * connection keeps the identity of whichever scenario authenticated it and later scenarios inherit
 * it. Do not combine {@code configure ntlmAuth} with this. The same reasoning is why pooling can
 * never be karate-core's default: TLS session state and server affinity are shared along with the
 * socket, and a functional suite buys per-scenario isolation deliberately.
 *
 * <h2>Why a wrapper per scenario rather than one shared client</h2>
 *
 * <p>{@link ApacheHttpClient} closes its own transport whenever configuration is applied, and it
 * keeps per-request state in instance fields. Sharing one instance across concurrent scenarios
 * would let one scenario's {@code configure} kill another's in-flight request, and would attach
 * one request's headers to another's response. The win is in the connection manager, not in the
 * wrapper, so sharing only the manager gives up nothing. See {@link HttpClientFactory#release}.
 */
public class PooledHttpClientFactory implements HttpClientFactory, AutoCloseable {

    /**
     * A ceiling, not a preallocation — the manager opens connections as they are needed. Sized
     * well above any realistic virtual-user count on one injector, because a pool smaller than the
     * concurrency is a bottleneck that looks exactly like the server being slow.
     */
    public static final int DEFAULT_MAX_CONNECTIONS = 4096;

    private final PoolingHttpClientConnectionManager manager;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PooledHttpClientFactory() {
        this(DEFAULT_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * @param maxTotal total pooled connections across all routes
     * @param perRoute connections per target host — the one that binds when a simulation points
     *                 at a single endpoint, which is the common case
     */
    public PooledHttpClientFactory(int maxTotal, int perRoute) {
        manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotal)
                .setMaxConnPerRoute(perRoute)
                // Match karate-core's default, which is sslTrustAll. Without this the pool falls
                // back to the JDK trust store while an unpooled client trusts anything, so turning
                // pooling on would silently change TLS behaviour — and against a self-signed or
                // internal certificate it does not degrade, it fails every request with a PKIX
                // path error. Measured: a TLS tier went from 100 OK to 100 KO on this line alone.
                .setTlsSocketStrategy(trustAllTlsStrategy())
                // Keep idle connections alive across the gaps between iterations. Without this an
                // eviction policy reintroduces exactly the reconnects this class exists to remove
                // — silently, and only at some pacings, which is the worst way for it to happen.
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setTimeToLive(TimeValue.ofHours(1))
                        .build())
                .build();
    }

    /**
     * Accepts any certificate, which is what an unpooled Karate client does by default.
     *
     * <p>A load test points at an endpoint it already owns, and this class cannot see a scenario's
     * {@code configure ssl} anyway — the trust material would have to come through the same seam
     * the class javadoc describes. Matching the default is the behaviour least likely to surprise;
     * it is not a recommendation to skip validation where validation is the thing under test.
     *
     * <p><b>Deliberately the same hostname verifier karate-core uses.</b> Every SSL branch in
     * {@code ApacheHttpClient} builds its socket factory with a {@code NoopHostnameVerifier}, and
     * so does this. Only the plumbing differs — core reaches it through the deprecated
     * {@code SSLConnectionSocketFactory}, which applies the verifier directly, while the strategy
     * used here needs the policy below to reach the same place.
     */
    private static TlsSocketStrategy trustAllTlsStrategy() {
        try {
            SSLContext context = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            // TlsSocketStrategy rather than the SSLConnectionSocketFactory karate-core still uses:
            // that one is deprecated in httpclient5 5.6 and this is new code, so it takes the
            // replacement instead of a @SuppressWarnings.
            //
            // The policy has to be CLIENT and cannot be left to default. Since 5.4 the default is
            // BUILTIN, which asks the JDK to verify the hostname during the handshake — before the
            // supplied verifier is consulted at all, so a NoopHostnameVerifier is simply never
            // reached. Measured: against a certificate with no subject alternative names, the
            // default failed every request with "No subject alternative names present" while the
            // trust-all context was working exactly as intended.
            return new DefaultClientTlsStrategy(context, HostnameVerificationPolicy.CLIENT,
                    NoopHostnameVerifier.INSTANCE);
        } catch (Exception e) {
            throw new IllegalStateException("could not build the pooled client's SSL context", e);
        }
    }

    @Override
    public HttpClient create() {
        return new PooledApacheHttpClient();
    }

    /**
     * Closes the scenario's wrapper, which returns its connections to the pool rather than
     * shutting it: {@code ApacheHttpClient} builds with {@code setConnectionManagerShared(true)}
     * whenever {@code sharedConnectionManager()} is non-null.
     */
    @Override
    public void release(HttpClient client) {
        try {
            client.close();
        } catch (Exception e) {
            // A wrapper that will not close is not worth failing a simulation over; the pool
            // survives it, and the connection it held is reclaimed when the pool is closed.
            System.err.println("[karate] error closing a pooled client wrapper: " + e);
        }
    }

    /**
     * Shuts the pool, releasing every connection it holds.
     *
     * <p>Called for you at the end of the simulation:
     * {@link KarateProtocolBuilder#pooledConnections()} hands this to the protocol, and
     * {@code KarateProtocolKey} registers it on Gatling's
     * {@code ActorSystem.registerOnTermination} — the same hook Gatling's own {@code HttpEngine}
     * uses to dispose itself. {@code ProtocolComponents.onExit} would have been the obvious place
     * and is the wrong one: it runs once per virtual user, so it would close the shared pool the
     * first time any user finished, while the rest were still using it.
     *
     * <p>Idempotent, so calling it yourself as well is safe.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            manager.close();
        }
    }

    /** Leased, available and pending counts — for anyone checking the pool is actually reused. */
    public String describe() {
        return "leased=" + manager.getTotalStats().getLeased()
                + " available=" + manager.getTotalStats().getAvailable()
                + " pending=" + manager.getTotalStats().getPending();
    }

    private final class PooledApacheHttpClient extends ApacheHttpClient {
        @Override
        protected org.apache.hc.client5.http.io.HttpClientConnectionManager sharedConnectionManager() {
            return manager;
        }
    }

}

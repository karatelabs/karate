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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A {@link LatencyMock} this process did not fork — on another host, or simply outlasting the run.
 *
 * <p><b>This is what a two-host measurement needs.</b> docs/PROFILING.md §10 names co-location as
 * the one confound it cannot argue away: the mock shares ten cores with the injector, and on CPU
 * the bias runs <i>against</i> Karate, whose arm opens a connection per iteration and so makes the
 * co-located server work harder than the plain arm does. "Separate hosts is the phase that makes
 * these numbers defensible" — and until now nothing in the harness could point at a mock it had not
 * started itself. Moving the mock also lifts the ephemeral-port ceiling, which the karate arm is
 * the only one to press against.
 *
 * <p><b>The window is the whole problem with a mock that outlives the run.</b> A forked mock is
 * born with the run and dies with it, so its counters can only describe that run. A shared one
 * carries whatever the previous cell left in it, and a rate computed over a window that opened
 * during someone else's run is not a rate. So {@link #attach} resets before the load starts and
 * {@link #collect} reads once it has stopped — the same {@code /stats/reset} the calibration ramp
 * uses to give each point numbers of its own.
 *
 * <p><b>What it writes is what a forked mock would have printed.</b> The two prefixed lines go into
 * {@code mock.log} exactly as the sibling process emits them on its own stdout, so the digest, the
 * reconciliation, {@link Compare} and every recipe in §5 keep working without knowing which kind of
 * mock served the run. The alternative — a second path through the digest for remote mocks — is how
 * two formats drift until one of them is quietly wrong.
 */
final class ExternalMock {

    /**
     * Generous, and not a measurement. These calls happen before and after the load, never during
     * it, so the only thing a timeout protects against is a hung harness — and a mock that takes
     * ten seconds to answer {@code /stats} has already told you something worth stopping for.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ExternalMock() {
    }

    /**
     * Check the mock is there, record what it is configured as, and clear its counters.
     *
     * <p>Fails the run rather than continuing on any of the three. An unreachable mock is obvious
     * within seconds either way, but a mock that is reachable and <i>not</i> reset produces a
     * complete, plausible digest whose throughput is computed over someone else's window — the
     * failure this is here to prevent, and one nothing downstream could detect.
     */
    static String attach(String url, Path runDir) throws IOException, InterruptedException {
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String config = get(base + "/config");
        if (config == null) {
            throw new IllegalStateException("no LatencyMock answering at " + base + "/config — start one with"
                    + " `java -cp … io.karatelabs.profiling.LatencyMock --bind 0.0.0.0 --latency 10ms`");
        }
        // Reset last, so a failure above leaves the mock's own state untouched. The 409 is called
        // out separately because it is the interesting failure and the only actionable one: the
        // mock refuses to reset while requests are in flight, which on a shared mock means someone
        // else is still driving it. Collapsing that into "unreachable" would send the operator to
        // check the network when the answer is to wait.
        int reset = status(base + "/stats/reset");
        if (reset == 409) {
            throw new IllegalStateException(base + " has requests in flight and refused to reset — "
                    + "another injector is still running against this mock; wait for it to finish");
        }
        if (reset != 200) {
            throw new IllegalStateException("could not reset " + base + " (HTTP " + reset + ") — refusing "
                    + "to measure a window that may already contain another run's load");
        }
        write(runDir, LatencyMock.CONFIG_PREFIX + config);
        write(runDir, "[external] attached to " + base + ", counters reset");
        return base;
    }

    /**
     * Read the closed window into {@code mock.log}, in the shape a forked mock would have printed.
     *
     * <p>Unlike {@link #attach} this only warns: the load has already happened, and a digest
     * missing its mock panel is worth more than a run reported as a failure after doing all the
     * work. The absence is visible — every check that reads these numbers reports "no data" rather
     * than a wrong number.
     */
    static void collect(String url, Path runDir) {
        try {
            String stats = get(url + "/stats");
            if (stats == null) {
                System.err.println("[parent] external mock at " + url + " did not answer /stats — "
                        + "the digest will have no mock panel and no reconciliation");
                return;
            }
            write(runDir, LatencyMock.STATS_PREFIX + stats);
        } catch (Exception e) {
            System.err.println("[parent] could not collect stats from " + url + ": " + e);
        }
    }

    /**
     * A connection to the mock that verifies nothing about its certificate.
     *
     * <p><b>Without this the TLS two-host bench could not complete a single run.</b> The mock's
     * certificate is self-signed, so {@link #get} turned the handshake rejection into {@code null}
     * and {@link #attach} reported "no LatencyMock answering" — pointing the operator at the
     * network while the mock was up and serving. {@code matrix.sh}'s own reachability check did not
     * catch it because that one uses {@code curl -sfk}, which skips verification, so the script
     * said the mock was fine and the harness said it was absent.
     *
     * <p><b>Two checks had to go, not one.</b> Trusting the certificate is not enough: the JDK then
     * verifies the hostname, and this bench reaches the mock at a private IP that no certificate
     * could match. Measured against this very mock, a trust-all context alone still failed with
     * "No name matching localhost found" — the same second-layer trap that
     * {@code PooledHttpClientFactory} hit after its own trust-all context was already working.
     *
     * <p><b>Why not {@code java.net.http.HttpClient}, which this used to use.</b> It overrides a
     * null {@code endpointIdentificationAlgorithm} in supplied {@code SSLParameters} and re-imposes
     * HTTPS identification. The only lever is the internal
     * {@code jdk.internal.httpclient.disableHostnameVerification} property, which is read once at
     * class initialisation — so setting it from code works only if nothing has touched the JDK
     * client first, and it worked here only when passed on the command line. Both were measured
     * failing. A per-connection verifier is local, ordered, and cannot be defeated by whatever
     * loaded a class before us.
     *
     * <p>Skipping verification is right here rather than merely convenient: this talks only to a
     * mock the operator just started, at a host they named, and it carries nothing worth
     * intercepting. Karate's client is what is under test, not this one.
     */
    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout((int) TIMEOUT.toMillis());
        connection.setReadTimeout((int) TIMEOUT.toMillis());
        connection.setRequestMethod("GET");
        // No keep-alive. These calls happen twice per run, outside the measured window, but a
        // connection left open to the mock would show up in its own distinctPeerPorts count — the
        // harness inflating the number it exists to report.
        connection.setRequestProperty("Connection", "close");
        if (connection instanceof HttpsURLConnection https) {
            https.setSSLSocketFactory(trustAllSocketFactory());
            https.setHostnameVerifier((hostname, session) -> true);
        }
        return connection;
    }

    private static SSLSocketFactory trustAllSocketFactory() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IOException("could not build a trust-all SSL context", e);
        }
    }

    /** The status code of a GET, or -1 if the host did not answer at all. */
    private static int status(String url) throws IOException, InterruptedException {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            return connection.getResponseCode();
        } catch (IOException e) {
            System.err.println("[parent] GET " + url + " failed: " + e);
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return body.toString().trim();
            }
        } catch (IOException e) {
            // Printed rather than swallowed. The caller only sees null, and "no mock answering" is
            // the wrong diagnosis for most of the ways this fails — a rejected TLS handshake and a
            // closed port are the same null and very different problems.
            System.err.println("[parent] GET " + url + " failed: " + e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void write(Path runDir, String line) throws IOException {
        Files.writeString(runDir.resolve("mock.log"), line + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

}

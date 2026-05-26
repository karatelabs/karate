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
package io.karatelabs.http;

import io.karatelabs.common.ThreadUtils;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.test.LogSilencer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Proxy / SSL end-to-end tests for {@link ApacheHttpClient}.
 *
 * Each test owns an in-process {@link FakeProxy} via try-with-resources, so the bound
 * loopback ServerSocket and its worker threads are guaranteed to be released. Worker
 * threads are daemons (no JVM-shutdown blocking), every accepted socket has a read
 * timeout, and every response stream is closed in a finally block. Even if a test
 * throws or the JVM is yanked, no listener leaks past the test method.
 *
 * Regression coverage:
 *   - issue #2877: HTTPS-through-proxy must send the target host as SNI, not "" / proxy host.
 */
class ApacheHttpClientProxySslE2eTest {

    /**
     * Issue #2877: after a successful proxy CONNECT, the TLS ClientHello must carry the
     * actual HTTPS target host as the SNI server_name. Before the fix,
     * {@code LenientSslConnectionSocketFactory.createLayeredSocket} forced an empty SNI,
     * which broke handshakes against any SNI-routed backend (CDN, k8s ingress, etc.).
     *
     * We don't need a working TLS server: we accept CONNECT, parse the next bytes
     * as a TLS ClientHello, extract SNI, then drop the socket. The client-side
     * handshake will (intentionally) fail — the assertion is on the captured SNI.
     */
    @Test
    void testHttpsThroughProxyUsesTargetSni() throws Exception {
        try (FakeProxy proxy = new FakeProxy()) {
            String target = "example.test";
            int proxyPort = proxy.port();
            ScenarioRuntime sr = LogSilencer.silenced("karate.http", () ->
                    runFeature(new ApacheHttpClient(), """
                Feature: HTTPS through proxy uses target SNI

                Scenario: ClientHello server_name is the target host, not the proxy host
                * configure ssl = true
                * configure proxy = 'http://127.0.0.1:%d'
                * url 'https://%s'
                * path '/anything'
                * method get
                """.formatted(proxyPort, target)));
            // request will fail (we kill the TLS handshake) — that's expected
            assertFailed(sr);
            assertTrue(proxy.awaitConnect(5, TimeUnit.SECONDS), "proxy never saw CONNECT");
            assertTrue(proxy.awaitSni(5, TimeUnit.SECONDS), "proxy never captured a ClientHello");
            assertEquals(List.of(target + ":443"), proxy.connectTargets);
            assertEquals(List.of(target), proxy.sniHosts,
                    "SNI must be the HTTPS target host, not the proxy host (issue #2877)");
        }
    }

    /**
     * Plain HTTP through an HTTP proxy: Apache sends the request in absolute-URI form
     * (request-line target is the full {@code http://host/path}), the proxy replies
     * directly, the scenario passes. Verifies the non-CONNECT proxy path end-to-end.
     */
    @Test
    void testPlainHttpThroughProxy() throws Exception {
        try (FakeProxy proxy = new FakeProxy()) {
            int proxyPort = proxy.port();
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: Plain HTTP through proxy

                Scenario: request line carries absolute URI, proxy forwards
                * configure proxy = 'http://127.0.0.1:%d'
                * url 'http://example.test'
                * path '/hello'
                * method get
                * status 200
                * match response == { ok: true }
                """.formatted(proxyPort));
            assertPassed(sr);
            assertEquals(1, proxy.requestLines.size(), "proxy should see exactly one request");
            String requestLine = proxy.requestLines.get(0);
            assertTrue(requestLine.startsWith("GET http://example.test/hello "),
                    "expected absolute-URI request-line, got: " + requestLine);
        }
    }

    /**
     * Proxy basic-auth challenge/response: configured credentials must be replayed after
     * a 407. Apache HttpClient handles this through the {@code BasicCredentialsProvider}
     * we install at {@code ApacheHttpClient.java:267-274}.
     */
    @Test
    void testProxyBasicAuthReplayedOn407() throws Exception {
        try (FakeProxy proxy = new FakeProxy()) {
            proxy.requireProxyAuth = true;
            int proxyPort = proxy.port();
            ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: Proxy basic auth replayed on 407

                Scenario: Proxy-Authorization sent after 407 challenge
                * configure proxy = { uri: 'http://127.0.0.1:%d', username: 'alice', password: 's3cret' }
                * url 'http://example.test'
                * path '/hello'
                * method get
                * status 200
                * match response == { ok: true }
                """.formatted(proxyPort));
            assertPassed(sr);
            // base64('alice:s3cret')
            String expected = "Basic YWxpY2U6czNjcmV0";
            assertTrue(proxy.proxyAuthHeaders.contains(expected),
                    "expected Proxy-Authorization '" + expected + "' but got: " + proxy.proxyAuthHeaders);
        }
    }

    // ===== FakeProxy ============================================================

    /**
     * Minimal in-process HTTP/HTTPS-CONNECT proxy for tests.
     *
     * Lifecycle follows the same daemon-thread + explicit-close pattern used by
     * {@code HttpServer} and {@code WsClient}: every test owns its instance via
     * try-with-resources, {@link #close()} releases the {@link ServerSocket} and
     * interrupts workers, and all threads are daemons so JVM exit is never blocked.
     */
    static final class FakeProxy implements AutoCloseable {

        final ServerSocket server;
        final ExecutorService pool;

        final List<String> connectTargets = Collections.synchronizedList(new ArrayList<>());
        final List<String> sniHosts = Collections.synchronizedList(new ArrayList<>());
        final List<String> requestLines = Collections.synchronizedList(new ArrayList<>());
        final List<String> proxyAuthHeaders = Collections.synchronizedList(new ArrayList<>());

        private final CountDownLatch connectSeen = new CountDownLatch(1);
        private final CountDownLatch sniSeen = new CountDownLatch(1);

        volatile boolean requireProxyAuth;
        volatile String plainResponseBody = "{\"ok\":true}";

        FakeProxy() throws IOException {
            this.server = new ServerSocket();
            this.server.setReuseAddress(true);
            this.server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            this.pool = Executors.newCachedThreadPool(ThreadUtils.daemonFactory("fake-proxy-" + server.getLocalPort() + "-"));
            pool.submit(this::acceptLoop);
        }

        int port() {
            return server.getLocalPort();
        }

        boolean awaitConnect(long timeout, TimeUnit unit) throws InterruptedException {
            return connectSeen.await(timeout, unit);
        }

        boolean awaitSni(long timeout, TimeUnit unit) throws InterruptedException {
            return sniSeen.await(timeout, unit);
        }

        @Override
        public void close() {
            try {
                server.close();
            } catch (IOException ignored) {
            }
            pool.shutdownNow();
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                final Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    return; // socket closed
                }
                pool.submit(() -> handle(client));
            }
        }

        private void handle(Socket client) {
            try (client) {
                client.setSoTimeout(5000);
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                String requestLine = readLine(in);
                if (requestLine == null) return;
                Map<String, String> headers = readHeaders(in);
                if (requestLine.startsWith("CONNECT ")) {
                    handleConnect(requestLine, headers, in, out);
                } else {
                    handlePlain(requestLine, headers, in, out);
                }
            } catch (IOException ignored) {
            }
        }

        private void handleConnect(String requestLine, Map<String, String> headers,
                                   InputStream in, OutputStream out) throws IOException {
            // CONNECT host:port HTTP/1.1
            String[] parts = requestLine.split(" ");
            connectTargets.add(parts.length > 1 ? parts[1] : "");
            connectSeen.countDown();
            if (requireProxyAuth && !headers.containsKey("proxy-authorization")) {
                sendResponse(out, 407, "Proxy Authentication Required", Map.of(
                        "Proxy-Authenticate", "Basic realm=\"karate\"",
                        "Content-Length", "0",
                        "Connection", "close"), null);
                return;
            }
            if (headers.containsKey("proxy-authorization")) {
                proxyAuthHeaders.add(headers.get("proxy-authorization"));
            }
            sendResponse(out, 200, "Connection established", Map.of(), null);
            String sni = parseClientHelloSni(in);
            if (sni != null) {
                sniHosts.add(sni);
            }
            sniSeen.countDown();
            // socket closes via try-with-resources — client's handshake will fail; expected
        }

        private void handlePlain(String requestLine, Map<String, String> headers,
                                 InputStream in, OutputStream out) throws IOException {
            if (requireProxyAuth && !headers.containsKey("proxy-authorization")) {
                requestLines.add(requestLine);
                sendResponse(out, 407, "Proxy Authentication Required", Map.of(
                        "Proxy-Authenticate", "Basic realm=\"karate\"",
                        "Content-Length", "0",
                        "Connection", "close"), null);
                return;
            }
            // only record the authenticated request to keep assertions clean
            requestLines.add(requestLine);
            if (headers.containsKey("proxy-authorization")) {
                proxyAuthHeaders.add(headers.get("proxy-authorization"));
            }
            int contentLength = parseInt(headers.get("content-length"));
            if (contentLength > 0) {
                in.readNBytes(contentLength);
            }
            byte[] body = plainResponseBody.getBytes(StandardCharsets.UTF_8);
            sendResponse(out, 200, "OK", new LinkedHashMap<>(Map.of(
                    "Content-Type", "application/json",
                    "Content-Length", String.valueOf(body.length),
                    "Connection", "close")), body);
        }

        // ===== wire helpers =====================================================

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
            int prev = -1, b;
            while ((b = in.read()) != -1) {
                if (prev == '\r' && b == '\n') {
                    byte[] arr = buf.toByteArray();
                    return new String(arr, 0, arr.length - 1, StandardCharsets.ISO_8859_1);
                }
                buf.write(b);
                prev = b;
            }
            return buf.size() == 0 ? null : buf.toString(StandardCharsets.ISO_8859_1);
        }

        private static Map<String, String> readHeaders(InputStream in) throws IOException {
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(),
                            line.substring(colon + 1).trim());
                }
            }
            return headers;
        }

        private static void sendResponse(OutputStream out, int status, String reason,
                                         Map<String, String> headers, byte[] body) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            sb.append("\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) {
                out.write(body);
            }
            out.flush();
        }

        private static int parseInt(String s) {
            if (s == null) return 0;
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /**
         * Parse a TLS 1.2/1.3 ClientHello record and return the SNI server_name
         * (host_name type), or null if absent / malformed. Used to verify that the
         * Apache client sends the correct SNI after a proxy CONNECT.
         */
        static String parseClientHelloSni(InputStream in) throws IOException {
            byte[] header = in.readNBytes(5);
            if (header.length != 5 || (header[0] & 0xff) != 0x16) {
                return null; // not a TLS handshake record
            }
            int recordLen = ((header[3] & 0xff) << 8) | (header[4] & 0xff);
            byte[] record = in.readNBytes(recordLen);
            if (record.length != recordLen) return null;
            ByteBuffer buf = ByteBuffer.wrap(record);
            if (!buf.hasRemaining() || (buf.get() & 0xff) != 1) return null; // client_hello
            skip(buf, 3); // handshake length
            skip(buf, 2); // legacy_version
            skip(buf, 32); // random
            int sidLen = buf.get() & 0xff;
            skip(buf, sidLen);
            int csLen = buf.getShort() & 0xffff;
            skip(buf, csLen);
            int cmLen = buf.get() & 0xff;
            skip(buf, cmLen);
            if (!buf.hasRemaining()) return null;
            int extTotal = buf.getShort() & 0xffff;
            int extEnd = buf.position() + extTotal;
            while (buf.position() < extEnd) {
                int extType = buf.getShort() & 0xffff;
                int extLen = buf.getShort() & 0xffff;
                int extStart = buf.position();
                if (extType == 0) { // server_name
                    int listLen = buf.getShort() & 0xffff;
                    int listEnd = buf.position() + listLen;
                    while (buf.position() < listEnd) {
                        int nameType = buf.get() & 0xff;
                        int nameLen = buf.getShort() & 0xffff;
                        byte[] name = new byte[nameLen];
                        buf.get(name);
                        if (nameType == 0) {
                            return new String(name, StandardCharsets.US_ASCII);
                        }
                    }
                    return null;
                }
                buf.position(extStart + extLen);
            }
            return null;
        }

        private static void skip(ByteBuffer buf, int n) {
            buf.position(buf.position() + n);
        }
    }
}

package io.karatelabs.http;

import io.karatelabs.core.MockServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientTest {

    @Test
    void testMatchNonProxyHostExact() {
        assertTrue(ApacheHttpClient.matchNonProxyHost("localhost", "localhost"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("my-api.com", "my-api.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("localhost", "other-host"));
    }

    @Test
    void testMatchNonProxyHostExactCaseInsensitive() {
        assertTrue(ApacheHttpClient.matchNonProxyHost("Localhost", "localhost"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("MY-API.COM", "my-api.com"));
    }

    @Test
    void testMatchNonProxyHostWildcardPrefix() {
        // *.example.com should match any subdomain
        assertTrue(ApacheHttpClient.matchNonProxyHost("*.example.com", "api.example.com"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("*.example.com", "www.example.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("*.example.com", "example.com"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("*.example.com", "other.com"));
    }

    @Test
    void testMatchNonProxyHostWildcardSuffix() {
        // 192.168.* should match any host starting with that prefix
        assertTrue(ApacheHttpClient.matchNonProxyHost("192.168.*", "192.168.1.1"));
        assertTrue(ApacheHttpClient.matchNonProxyHost("192.168.*", "192.168.0.100"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("192.168.*", "10.0.0.1"));
    }

    @Test
    void testMatchNonProxyHostNulls() {
        assertFalse(ApacheHttpClient.matchNonProxyHost(null, "host"));
        assertFalse(ApacheHttpClient.matchNonProxyHost("pattern", null));
        assertFalse(ApacheHttpClient.matchNonProxyHost(null, null));
    }


    /**
     * A request made after {@link ApacheHttpClient#close()} must not strand a rebuilt client.
     *
     * <p>The instance deliberately stays usable after close — {@code apply()} relies on the lazy
     * rebuild, and so does real usage that outlives a scenario: an {@code afterFeature} hook runs
     * after the last scenario released its client, and a closure returned from a called feature
     * can be invoked later. But by then the owner is gone, so nothing would call {@code release()}
     * a second time and the rebuilt {@code CloseableHttpClient} — with its connection manager and
     * pooled sockets — would be abandoned to the collector. That is the leak the release contract
     * was added to remove, reintroduced through the back door.
     *
     * <p>Asserted on the private field because the property is precisely "no client is being held
     * afterwards"; a behavioural assertion would only show the request succeeded, which it did
     * before the fix too.
     */
    @Test
    void testARequestAfterCloseDoesNotStrandARebuiltClient() throws Exception {
        MockServer server = MockServer.featureString("""
                Feature: echo

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """).port(0).start();
        try {
            String url = "http://localhost:" + server.getPort() + "/ping";
            ApacheHttpClient client = new ApacheHttpClient();
            java.lang.reflect.Field field = ApacheHttpClient.class.getDeclaredField("httpClient");
            field.setAccessible(true);

            HttpRequest first = new HttpRequest();
            first.setUrl(url);
            first.setMethod("GET");
            assertEquals(200, client.invoke(first).getStatus());
            assertNotNull(field.get(client), "a live client should be held between requests");

            client.close();
            assertNull(field.get(client), "close() must drop the client it closed");

            HttpRequest late = new HttpRequest();
            late.setUrl(url);
            late.setMethod("GET");
            assertEquals(200, client.invoke(late).getStatus(),
                    "a post-close request must still work — hooks and escaped closures rely on it");
            assertNull(field.get(client),
                    "a client rebuilt after release has no owner to hand it back to, so the "
                            + "request that caused it must close it rather than leave an orphan");
        } finally {
            server.stopAndWait();
        }
    }

    /**
     * The orphan close must also happen when the post-close request <em>fails</em>.
     *
     * <p>It sits in a {@code finally}, so this should hold by construction — but "by construction"
     * is what was said about several things in this area that turned out to be false, and a
     * request to a dead port is a one-line way to pin it. Without the finally, a failing
     * post-release request would strand a client every time, which is the shape of an outage
     * retry loop.
     */
    @Test
    void testAFailingRequestAfterCloseAlsoClosesItsRebuiltClient() throws Exception {
        ApacheHttpClient client = new ApacheHttpClient();
        java.lang.reflect.Field field = ApacheHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        client.close();

        HttpRequest doomed = new HttpRequest();
        // Port 1 on loopback: nothing listens, so this fails fast with connection refused.
        doomed.setUrl("http://127.0.0.1:1/nope");
        doomed.setMethod("GET");
        assertThrows(RuntimeException.class, () -> client.invoke(doomed));
        assertNull(field.get(client),
                "a rebuilt client must be closed even when the request that caused it failed");
    }

    /**
     * A slow response is bounded by {@code readTimeout}, never by {@code connectTimeout}.
     *
     * <p>Worth pinning because the two are configured in two places that must agree.
     * {@code ConnectionConfig.socketTimeout} is applied to the leased connection, and
     * {@code SocketConfig.soTimeout} is SO_TIMEOUT on the socket — both are the read timeout, and
     * the second was being fed {@code connectTimeout}. The first wins, so that never showed;
     * it also meant nothing would have shown if the library's preference between them changed,
     * except every read quietly capping at the connect timeout. A read timeout firing early looks
     * like a slow server rather than a client bug, so it is the kind of regression that gets
     * investigated in the wrong place for a long time.
     *
     * <p><b>This test passes against the mistake as well, and that is not a defect in it.</b>
     * Verified by putting {@code connectTimeout} back: still green, because the connection config
     * wins today. There is no assertion that could distinguish them while one of them is inert, so
     * this pins the semantics rather than guarding the edit — it is here to fail the day the
     * precedence changes, which is the only day it can matter.
     *
     * <p>The server accepts at once and stalls before replying, so connect is fast and only the
     * read is slow — which is what separates the two timeouts.
     */
    @Test
    void testASlowResponseIsBoundedByReadTimeoutNotConnectTimeout() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.getInputStream().read(new byte[2048]);
                    Thread.sleep(1200);
                    java.io.OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                            + "Connection: close\r\n\r\nhi").getBytes());
                    out.flush();
                } catch (Exception ignored) {
                    // the socket closes with the server when the test ends
                }
            });
            responder.setDaemon(true);
            responder.start();

            ApacheHttpClient client = new ApacheHttpClient();
            io.karatelabs.core.KarateConfig config = new io.karatelabs.core.KarateConfig();
            config.configure("readTimeout", 10000);
            config.configure("connectTimeout", 250);
            client.apply(config);

            HttpRequest request = new HttpRequest();
            request.setUrl("http://localhost:" + server.getLocalPort() + "/slow");
            request.setMethod("GET");
            assertEquals(200, client.invoke(request).getStatus(),
                    "the response took longer than connectTimeout but well under readTimeout, "
                            + "so only readTimeout may bound it");
        }
    }
}

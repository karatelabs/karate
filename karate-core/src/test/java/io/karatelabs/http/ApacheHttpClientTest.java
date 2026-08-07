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
}

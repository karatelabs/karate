package io.karatelabs.http;

import io.karatelabs.markup.RootResourceResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-request memoization via {@code request.cache(key, fn)}.
 *
 * <p>Backed by {@code demo/api/cache.js} which exercises the contract:
 * <ul>
 *   <li>First call with a key invokes the fn and stores the result.</li>
 *   <li>Subsequent calls with the same key return the cached value.</li>
 *   <li>Different keys cache independently.</li>
 *   <li>A {@code null} return value is still a cache hit (no re-invoke).</li>
 *   <li>Cache is scoped to a single request — separate requests get fresh stores.</li>
 * </ul>
 */
class RequestCacheTest {

    static InMemoryTestHarness harness;

    @BeforeAll
    static void beforeAll() {
        ServerConfig config = new ServerConfig()
                .sessionStore(new InMemorySessionStore())
                .apiPrefix("/api/")
                .devMode(true)
                .csrfEnabled(false);
        RootResourceResolver resolver = new RootResourceResolver("classpath:demo");
        ServerRequestHandler handler = new ServerRequestHandler(config, resolver);
        harness = new InMemoryTestHarness(handler);
    }

    @Test
    void testCacheMemoizesWithinRequest() {
        HttpResponse r = harness.get("/api/cache");
        assertEquals(200, r.getStatus(), "handler must complete cleanly: " + r.getBodyString());
        String body = r.getBodyString();
        assertTrue(body.contains("\"totalCalls\":2"),
                "fn invoked once per distinct key: " + body);
        assertTrue(body.contains("\"fooFirst\":1"), body);
        assertTrue(body.contains("\"fooSecond\":1"), body);
        assertTrue(body.contains("\"fooFinal\":1"), body);
        assertTrue(body.contains("\"bar\":2"), body);
        assertTrue(body.contains("\"fooSameRef\":true"),
                "cached object identity preserved across reads: " + body);
    }

    @Test
    void testCacheStoresNullAsHit() {
        HttpResponse r = harness.get("/api/cache");
        String body = r.getBodyString();
        assertTrue(body.contains("\"nullCallsAfterTwo\":1"),
                "null return value is still a cache hit; fn must not re-invoke: " + body);
        assertTrue(body.contains("\"nulFirst\":null"), body);
        assertTrue(body.contains("\"nulSecond\":null"), body);
    }

    @Test
    void testCacheIsolatedAcrossRequests() {
        HttpResponse r1 = harness.get("/api/cache");
        HttpResponse r2 = harness.get("/api/cache");
        assertTrue(r1.getBodyString().contains("\"totalCalls\":2"));
        assertTrue(r2.getBodyString().contains("\"totalCalls\":2"),
                "second request must see a fresh cache, not 4 calls: " + r2.getBodyString());
    }
}

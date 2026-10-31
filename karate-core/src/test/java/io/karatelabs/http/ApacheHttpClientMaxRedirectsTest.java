package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientMaxRedirectsTest {

    static HttpServer server;
    static String base;

    @BeforeAll
    static void start() {
        // /r/N redirects to /r/N-1, so /r/N is a chain of N redirects ending in a 200
        server = HttpServer.start(0, request -> {
            int n = Integer.parseInt(request.getPath().substring("/r/".length()));
            HttpResponse response = HttpResponse.json("{}");
            if (n > 0) {
                response.setStatus(302);
                response.setHeader("Location", "/r/" + (n - 1));
            }
            return response;
        });
        base = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    static HttpResponse get(KarateConfig config, int redirects) throws Exception {
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            client.apply(config);
            HttpRequest request = new HttpRequest();
            request.setUrl(base + "/r/" + redirects);
            request.setMethod("GET");
            return client.invoke(request);
        }
    }

    static KarateConfig maxRedirects(int max) {
        KarateConfig config = new KarateConfig();
        config.configure("maxRedirects", max);
        return config;
    }

    @Test
    void defaultFollowsFiftyRedirects() throws Exception {
        assertEquals(50, new KarateConfig().getMaxRedirects());
        assertEquals(200, get(new KarateConfig(), 50).getStatus());
        RuntimeException e = assertThrows(RuntimeException.class, () -> get(new KarateConfig(), 51));
        assertTrue(e.getMessage().contains("Maximum redirects (50) exceeded"), e.getMessage());
    }

    @Test
    void aChainUpToTheLimitIsFollowed() throws Exception {
        assertEquals(200, get(maxRedirects(3), 3).getStatus());
    }

    @Test
    void aChainLongerThanTheLimitFails() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> get(maxRedirects(3), 4));
        assertTrue(e.getMessage().contains("Maximum redirects (3) exceeded"), e.getMessage());
    }

    @Test
    void zeroFailsOnTheFirstRedirect() throws Exception {
        assertEquals(200, get(maxRedirects(0), 0).getStatus());
        RuntimeException e = assertThrows(RuntimeException.class, () -> get(maxRedirects(0), 1));
        assertTrue(e.getMessage().contains("Maximum redirects (0) exceeded"), e.getMessage());
    }

    @Test
    void followRedirectsFalseStillReturnsTheRedirect() throws Exception {
        KarateConfig config = maxRedirects(1);
        config.configure("followRedirects", false);
        assertEquals(302, get(config, 5).getStatus());
    }

    @Test
    void aNegativeLimitIsRefused() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> new KarateConfig().configure("maxRedirects", -1));
        assertEquals("configure 'maxRedirects' expects a non-negative integer, got: -1", e.getMessage());
    }

}

package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApacheHttpClientResponseLimitsTest {

    static HttpServer server;
    static String base;

    @BeforeAll
    static void start() {
        // /bytes/N answers N bytes; /header/N a header whose value is N characters
        server = HttpServer.start(0, request -> {
            String[] parts = request.getPath().split("/");
            int n = Integer.parseInt(parts[2]);
            HttpResponse response = new HttpResponse();
            response.setStatus(200);
            if ("bytes".equals(parts[1])) {
                response.setBody(new byte[n], null);
            } else {
                response.setHeader("X-Big", "x".repeat(n));
            }
            return response;
        });
        base = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    static HttpResponse get(String key, Object max, String path) throws Exception {
        KarateConfig config = new KarateConfig();
        if (key != null) {
            config.configure(key, max);
        }
        try (ApacheHttpClient client = new ApacheHttpClient()) {
            client.apply(config);
            HttpRequest request = new HttpRequest();
            request.setUrl(base + path);
            request.setMethod("GET");
            return client.invoke(request);
        }
    }

    @Test
    void noLimitByDefault() throws Exception {
        assertEquals(-1, new KarateConfig().getMaxResponseSize());
        assertEquals(-1, new KarateConfig().getMaxHeaderSize());
        assertEquals(100_000, get(null, null, "/bytes/100000").getBodyBytes().length);
        assertEquals(5_000, get(null, null, "/header/5000").getHeader("X-Big").length());
        assertEquals(20_000, get("maxHeaderSize", 131072, "/header/20000").getHeader("X-Big").length(), "a line as long as the limit");
    }

    @Test
    void aBodyUpToTheLimitIsRead() throws Exception {
        assertEquals(10, get("maxResponseSize", 10, "/bytes/10").getBodyBytes().length);
    }

    @Test
    void aBodyOverTheLimitFailsTheRequest() {
        RuntimeException e = assertThrows(RuntimeException.class, () -> get("maxResponseSize", 9, "/bytes/10"));
        assertTrue(e.getMessage().contains("Maximum response size reached"), e.getMessage());
        e = assertThrows(RuntimeException.class, () -> get("maxResponseSize", 0, "/bytes/1"));
        assertTrue(e.getMessage().contains("Maximum response size reached"), e.getMessage());
    }

    @Test
    void headersOverTheLimitFailTheRequest() throws Exception {
        assertEquals(200, get("maxHeaderSize", 8192, "/header/500").getStatus());
        RuntimeException e = assertThrows(RuntimeException.class, () -> get("maxHeaderSize", 512, "/header/600"));
        assertTrue(e.getMessage().contains("Header overflow"), e.getMessage());
    }

    @Test
    void aNegativeLimitIsRefusedAndNullClearsIt() {
        KarateConfig config = new KarateConfig();
        assertThrows(RuntimeException.class, () -> config.configure("maxResponseSize", -1));
        config.configure("maxResponseSize", 5);
        assertEquals(5L, config.jsGet("maxResponseSize"));
        config.configure("maxResponseSize", null);
        assertNull(config.jsGet("maxResponseSize"));
    }

}
